import gc
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import types

candidate = Path(sys.argv[1]).resolve()
spec = importlib.util.spec_from_file_location('candidate', candidate)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
results = []


def check(name, operation):
    try:
        operation()
        results.append({'name': name, 'passed': True})
    except Exception as error:
        results.append({'name': name, 'passed': False, 'type': type(error).__name__, 'message': str(error)})


with tempfile.TemporaryDirectory() as directory:
    root = Path(directory).resolve()
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
    protocol = {'schemaVersion': 1, 'runtimeVersion': '2.0.1', 'runId': 'fault-check',
                'model': {'directory': str(model), 'files': files},
                'cases': [{'id': 'case', 'cohort': 'mandarin', 'locale': 'zh-CN',
                           'pcmPath': str(pcm), 'samples': 160,
                           'sha256': hashlib.sha256(bytes(320)).hexdigest(), 'reference': '公开文本'}]}

    check('valid-protocol', lambda: module.validate_protocol(protocol))

    def reject_link():
        (model / 'linked').symlink_to(model / 'conv_frontend.onnx')
        try:
            module.safe_join(str(model), 'linked')
        except ValueError:
            return
        raise AssertionError('internal symlink accepted')

    check('reject-internal-symlink', reject_link)

    def reject_ancestor_link():
        (root / 'alias-parent').symlink_to(root)
        try:
            module.safe_join(str(root / 'alias-parent' / 'model'), 'conv_frontend.onnx')
        except ValueError:
            return
        raise AssertionError('symlink model ancestor accepted')

    check('reject-model-ancestor-symlink', reject_ancestor_link)
    # Independent fault isolation only; does not alter candidate bytes.
    module.MANDATORY = set(module.MANDATORY)
    path = root / 'protocol.json'
    path.write_text(json.dumps(protocol, ensure_ascii=False))
    events = []

    class Stream:
        result = types.SimpleNamespace(text='原始文本。')

        def set_option(self, key, value):
            events.append(('language', key, value))
            if fail_language:
                raise ValueError('original language failure')

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
            assert kwargs['provider'] == 'cpu' and kwargs['num_threads'] == 2
            if fail_factory:
                raise RuntimeError('original constructor failure')
            return Recognizer()

    sys.modules['sherpa_onnx'] = types.SimpleNamespace(__version__='2.0.1', OfflineRecognizer=Factory)

    def main_case(output, expected_code):
        sys.argv = [str(candidate), '--protocol', str(path), '--output', str(output)]
        code = module.main()
        assert code == expected_code, code
        return json.loads(output.read_bytes())

    fail_language = False
    fail_factory = False

    def nominal():
        report = main_case(root / 'nominal.json', 0)
        assert report['status'] == 'completed'
        assert report['rows'][0]['text'] == '原始文本。'
        assert report['cleanup']['python_refs_dropped'] is True

    check('nominal-finalization', nominal)
    fail_language = True

    def language_error():
        report = main_case(root / 'error.json', 1)
        row = report['rows'][0]
        assert row['error_type'] == 'ValueError', row
        assert row['error_message'] == 'original language failure', row
        assert report['status'] == 'failed'

    check('original-language-error-preserved', language_error)
    fail_language = False
    fail_factory = True

    def model_failure():
        report = main_case(root / 'model-error.json', 1)
        assert report['error'] == {'type': 'RuntimeError', 'message': 'original constructor failure'}
        assert [row['id'] for row in report['rows']] == ['case'], report['rows']

    check('planned-cases-present-on-constructor-failure', model_failure)
    fail_factory = False

    def bad_model():
        before = len(events)
        (model / 'conv_frontend.onnx').write_bytes(b'evil')
        report = main_case(root / 'bad-model.json', 1)
        assert report['error']['type'] == 'ValueError', report
        assert 'model sha mismatch' in report['error']['message']
        assert len(events) == before
        (model / 'conv_frontend.onnx').write_bytes(b'fake')

    check('changed-model-blocks-native', bad_model)

    def bad_pcm():
        before = len(events)
        pcm.write_bytes(b'\x01' * 320)
        report = main_case(root / 'bad-pcm.json', 1)
        assert 'pcm sha mismatch' in report['error']['message']
        assert len(events) == before
        pcm.write_bytes(bytes(320))

    check('changed-pcm-blocks-native', bad_pcm)

    def no_overwrite():
        before = len(events)
        target = root / 'existing.json'
        target.write_bytes(b'original')
        sys.argv = [str(candidate), '--protocol', str(path), '--output', str(target)]
        assert module.main() == 2
        assert target.read_bytes() == b'original'
        assert len(events) == before

    check('existing-output-preserved-zero-native', no_overwrite)

receipt = {'candidate': str(candidate), 'sha256': hashlib.sha256(candidate.read_bytes()).hexdigest(),
           'realNativeCalls': 0, 'results': results}
(Path(__file__).parent / ('runner-check-' + receipt['sha256'][:12] + '.json')).write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps(receipt, indent=2))
raise SystemExit(any(not row['passed'] for row in results))
