"""Parent two-stage acceptance; fake model and fake ASR runtime only."""
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import types
import numpy as np

candidate = Path(sys.argv[1]).resolve()
spec = importlib.util.spec_from_file_location('two_pass_candidate', candidate)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
results = []
scenario = ''
admit_text = 'speech'
observed = []
output = None
snapshots = []
actual_calls = 0


class Stream:
    def __init__(self, stage):
        self.stage = stage
        self.result = types.SimpleNamespace(text=None)

    def set_option(self, key, value):
        assert key == 'language'
        self.language = value
        if scenario == 'setter-' + str(self.stage):
            raise ValueError('original setter failure ' + str(self.stage))

    def accept_waveform(self, rate, samples):
        assert rate == 16000
        snapshots.append(samples)
        if len(snapshots) == 2:
            assert snapshots[0] is snapshots[1]
        assert np.array_equal(samples, np.arange(160, dtype=np.float32) / 32768.0)
        if scenario == 'accept-' + str(self.stage):
            raise ValueError('original accept failure ' + str(self.stage))


class Recognizer:
    def __init__(self):
        self.stage = 0

    def create_stream(self):
        self.stage += 1
        if scenario == 'create-' + str(self.stage):
            raise ValueError('original create failure ' + str(self.stage))
        return Stream(self.stage)

    def decode_stream(self, stream):
        global actual_calls
        actual_calls += 1
        row = json.loads(output.read_bytes())['rows'][0]
        assert row['status'] == 'running'
        assert row['nativeDecodeCalls'] == actual_calls
        if stream.stage == 2:
            assert row['admission']['rawText'] == admit_text
            assert row['admission']['status'] == 'recognized'
            assert row['text'] is None
        observed.append(stream.language)
        if scenario == 'decode-' + str(stream.stage):
            raise ValueError('original decode failure ' + str(stream.stage))
        stream.result.text = admit_text if stream.stage == 1 else 'final raw text'


class Factory:
    @staticmethod
    def from_qwen3_asr(**kwargs):
        return Recognizer()


sys.modules['sherpa_onnx'] = types.SimpleNamespace(
    __version__='2.0.1', OfflineRecognizer=Factory)

with tempfile.TemporaryDirectory() as temporary:
    root = Path(temporary).resolve()
    model = root / 'model'
    model.mkdir()
    files = {}
    for relative in module.MANDATORY:
        path = model / relative
        path.parent.mkdir(exist_ok=True)
        path.write_bytes(b'fake')
        files[relative] = {'bytes': 4, 'sha256': hashlib.sha256(b'fake').hexdigest()}
    pcm = root / 'input.pcm'
    pcm.write_bytes(np.arange(160, dtype='<i2').tobytes())
    protocol = {'schemaVersion': 1, 'runtimeVersion': '2.0.1', 'runId': 'acceptance',
                'languageMode': 'admit-auto-then-forced',
                'model': {'directory': str(model), 'files': files},
                'cases': [{'id': 'case', 'cohort': 'mandarin', 'locale': 'zh-CN',
                           'reference': 'public', 'samples': 160, 'pcmPath': str(pcm),
                           'sha256': hashlib.sha256(pcm.read_bytes()).hexdigest()}]}
    source = root / 'protocol.json'

    def run(name):
        global output, actual_calls
        output = root / (name + '.json')
        source.write_text(json.dumps(protocol))
        observed.clear()
        snapshots.clear()
        actual_calls = 0
        sys.argv = [str(candidate), '--protocol', str(source), '--output', str(output)]
        code = module.main()
        report = json.loads(output.read_bytes())
        return code, report['rows'][0]

    def check(name, operation):
        try:
            operation()
            results.append({'name': name, 'passed': True})
        except Exception as error:
            results.append({'name': name, 'passed': False,
                            'error': type(error).__name__ + ': ' + str(error)})

    for locale, language in [('zh-CN', 'Chinese'), ('en-US', 'English')]:
        protocol['cases'][0]['locale'] = locale
        for text in ['', 'speech', ' ']:
            admit_text = text
            name = locale + '-' + repr(text)

            def nominal():
                code, row = run(name)
                assert code == 0 and row['status'] == 'recognized'
                assert observed == ([''] if text == '' else ['', language])
                assert row['nativeDecodeCalls'] == len(observed)
                assert row['admissionRejected'] == (text == '')
                assert row['text'] == ('' if text == '' else 'final raw text')
                assert row['languageOption'] == ('' if text == '' else language)
                assert all(not np.any(s) for s in snapshots)

            check(name, nominal)

    admit_text = 'speech'
    for stage in [1, 2]:
        for operation in ['create', 'setter', 'accept', 'decode']:
            scenario = operation + '-' + str(stage)

            def fault():
                code, row = run(scenario)
                assert code == 1 and row['status'] == 'error' and row['text'] is None
                assert row['error_type'] == 'ValueError'
                assert row['error_message'] == 'original ' + operation + ' failure ' + str(stage)
                assert row['nativeDecodeCalls'] == actual_calls, (row['nativeDecodeCalls'], actual_calls)
                assert all(not np.any(s) for s in snapshots)

            check(scenario, fault)

receipt = {'candidateSHA256': hashlib.sha256(candidate.read_bytes()).hexdigest(),
           'realNativeCalls': 0, 'results': results}
(Path(__file__).parent / ('two-pass-check-' + receipt['candidateSHA256'][:12] + '.json')).write_text(
    json.dumps(receipt, indent=2) + '\n')
print(json.dumps(receipt, indent=2))
raise SystemExit(any(not row['passed'] for row in results))
