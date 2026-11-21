"""Parent acceptance checks; fake ASR only, no model execution."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import types

candidate = Path(sys.argv[1]).resolve()
spec = importlib.util.spec_from_file_location('language_candidate', candidate)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
events = []
results = []


class Stream:
    result = types.SimpleNamespace(text='unchanged result')

    def set_option(self, key, value):
        events.append(('language', key, value))

    def accept_waveform(self, rate, samples):
        events.append(('waveform', rate, samples.size))


class Recognizer:
    def create_stream(self):
        return Stream()

    def decode_stream(self, stream):
        events.append(('decode',))


class Factory:
    @staticmethod
    def from_qwen3_asr(**kwargs):
        events.append(('factory',))
        assert kwargs['hotwords'] == '' and kwargs['seed'] == 42
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
    pcm.write_bytes(bytes(320))
    cases = [
        {'id': locale, 'cohort': 'mandarin' if locale == 'zh-CN' else 'english-near',
         'locale': locale, 'pcmPath': str(pcm), 'samples': 160,
         'sha256': hashlib.sha256(pcm.read_bytes()).hexdigest(), 'reference': 'speech'}
        for locale in ['zh-CN', 'en-US']]
    base = {'schemaVersion': 1, 'runtimeVersion': '2.0.1', 'runId': 'acceptance',
            'model': {'directory': str(model), 'files': files}, 'cases': cases}

    def run(name, protocol):
        source = root / (name + '-protocol.json')
        output = root / (name + '-report.json')
        source.write_text(json.dumps(protocol))
        sys.argv = [str(candidate), '--protocol', str(source), '--output', str(output)]
        before = len(events)
        code = module.main()
        return code, json.loads(output.read_bytes()), events[before:]

    for name, mode in [('default', None), ('forced', 'forced'), ('auto', 'auto')]:
        protocol = copy.deepcopy(base)
        if mode is not None:
            protocol['languageMode'] = mode
        code, report, observed = run(name, protocol)
        expected = ['', ''] if mode == 'auto' else ['Chinese', 'English']
        assert code == 0 and report['config']['languageMode'] == (mode or 'forced')
        assert [e[2] for e in observed if e[0] == 'language'] == expected
        assert [r['languageOption'] for r in report['rows']] == expected
        assert all(r['text'] == 'unchanged result' for r in report['rows'])
        results.append({'name': name + '-actual-options-and-raw-text', 'passed': True})

    for index, mode in enumerate(['invalid', '', None, [], {}, 1, True]):
        protocol = copy.deepcopy(base)
        protocol['languageMode'] = mode
        code, report, observed = run('invalid-' + str(index), protocol)
        assert code == 1 and report['status'] == 'failed' and not observed
    results.append({'name': 'seven-invalid-mode-values-zero-native', 'passed': True})

    protocol = copy.deepcopy(base)
    protocol['languageMode'] = 'auto'
    protocol['cases'] = [dict(cases[0], cohort='nonspeech-noise', reference='')]
    code, report, observed = run('noise', protocol)
    assert code == 0 and report['rows'][0]['reference'] == ''
    assert ('language', 'language', '') in observed
    results.append({'name': 'noise-empty-reference-accepted', 'passed': True})
    protocol['cases'][0]['reference'] = 'must reject'
    code, report, observed = run('bad-noise', protocol)
    assert code == 1 and not observed and 'reference must be empty' in report['error']['message']
    results.append({'name': 'noise-nonempty-reference-zero-native', 'passed': True})

receipt = {'candidateSHA256': hashlib.sha256(candidate.read_bytes()).hexdigest(),
           'realNativeCalls': 0, 'results': results}
(Path(__file__).parent / ('language-check-' + receipt['candidateSHA256'][:12] + '.json')).write_text(
    json.dumps(receipt, indent=2) + '\n')
print(json.dumps(receipt, indent=2))
