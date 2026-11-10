from pathlib import Path
import hashlib
import importlib.util
import json
import sys
import tempfile
import types
import numpy as np

candidate = Path(sys.argv[1]).resolve()
spec = importlib.util.spec_from_file_location('admission', candidate)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
results = []
events = []
mode = 'ok'


def check(name, operation):
    try:
        operation()
        results.append({'name': name, 'passed': True})
    except Exception as error:
        results.append({'name': name, 'passed': False, 'error': type(error).__name__ + ': ' + str(error)})


with tempfile.TemporaryDirectory() as temporary:
    root = Path(temporary).resolve()
    model = root / 'model.onnx'
    model.write_bytes(b'fake')
    cases = []
    for i, n in enumerate([160, 512]):
        pcm = root / f'{i}.pcm'
        pcm.write_bytes(bytes(2*n))
        cases.append({'id': str(i), 'cohort': 'silence', 'locale': 'en-US', 'reference': '',
                      'pcmPath': str(pcm), 'samples': n, 'sha256': hashlib.sha256(pcm.read_bytes()).hexdigest()})
    protocol = {'runtimeVersion': '2.0.1', 'model': {'path': str(model), 'bytes': 4,
                'sha256': hashlib.sha256(b'fake').hexdigest()}, 'cases': cases}
    path = root / 'protocol.json'
    path.write_text(json.dumps(protocol))
    target = root / 'unused.json'

    class Config:
        def __init__(self):
            self.silero_vad = types.SimpleNamespace()

    class FakeVad:
        def __init__(self, config, buffer_size_in_seconds):
            events.append('constructor')
            current = json.loads(target.read_bytes())
            assert current['status'] == 'running'
            assert current['plannedIds'] == ['0', '1']
            assert config.silero_vad.threshold == 0.5
            assert config.silero_vad.min_speech_duration == 0.25
            assert config.silero_vad.min_silence_duration == 0.5
            assert config.silero_vad.max_speech_duration == 30
            assert config.silero_vad.window_size == 512
            if mode == 'constructor-error':
                raise ValueError('original constructor failure')
            self.ready = False

        def accept_waveform(self, window):
            assert len(window) == 512
            events.append('window')
            if mode == 'mutate':
                window[0] = 1

        def flush(self):
            self.ready = True

        def empty(self):
            return not self.ready

        @property
        def front(self):
            return types.SimpleNamespace(start=0, samples=np.zeros(100))

        def pop(self):
            self.ready = False

    sys.modules['sherpa_onnx'] = types.SimpleNamespace(__version__='2.0.1', VadModelConfig=Config, VoiceActivityDetector=FakeVad)

    def run(name):
        global target
        target = root / (name + '.json')
        sys.argv = [str(candidate), '--protocol', str(path), '--output', str(target)]
        code = module.main()
        return code, json.loads(target.read_bytes())

    def nominal():
        code, report = run('nominal')
        assert code == 0 and report['status'] == 'completed'
        assert report['asrCalls'] == 0
        assert [row['paddedSamples'] for row in report['cases']] == [352, 0]
        assert all(row['floatHashBefore'] == row['floatHashAfter'] for row in report['cases'])
        assert len(report['cases']) == 2

    check('nominal-padding-config-and-durable-progress', nominal)

    def bad_late_input():
        before = len(events)
        Path(cases[1]['pcmPath']).write_bytes(b'\x01'*1024)
        code, report = run('bad-second-pcm')
        assert code != 0 and report['status'] == 'failed'
        assert len(events) == before, events
        Path(cases[1]['pcmPath']).write_bytes(bytes(1024))

    check('last-input-invalid-blocks-all-native', bad_late_input)
    mode = 'constructor-error'

    def constructor_error():
        code, report = run('constructor-error')
        assert code != 0 and report['status'] == 'failed'
        assert all(row['error']['message'] == 'original constructor failure' for row in report['cases'])

    check('original-constructor-error-persisted', constructor_error)
    mode = 'mutate'

    def mutation():
        code, report = run('mutation')
        assert code != 0 and report['status'] == 'failed'
        assert report['cases'][1]['error'] is not None

    check('input-float-mutation-rejected', mutation)
    mode = 'ok'

    def preserved_output():
        before = len(events)
        saved = target.read_bytes()
        sys.argv = [str(candidate), '--protocol', str(path), '--output', str(target)]
        assert module.main() != 0
        assert target.read_bytes() == saved and len(events) == before

    check('existing-report-preserved-no-native', preserved_output)

receipt = {'candidateSHA256': hashlib.sha256(candidate.read_bytes()).hexdigest(), 'realVadCalls': 0,
           'asrCalls': 0, 'results': results}
(Path(__file__).parent / ('admission-check-' + receipt['candidateSHA256'][:12] + '.json')).write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps(receipt, indent=2))
raise SystemExit(any(not row['passed'] for row in results))
