"""Parent host-receipt acceptance; imports candidate tools without building."""
from pathlib import Path
import hashlib
import importlib.util
import json
import os
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
CANDIDATES = Path(os.environ.get('W3_TERMINAL_BUILD_CANDIDATES', HERE)).resolve()


def load(name):
    spec = importlib.util.spec_from_file_location('terminal_candidate_' + name, CANDIDATES / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


PREPARE = load('prepare_build')
BUILD = load('build_acceptance')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class HostReceiptChecks(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='odai-terminal-prepare-', dir='/private/tmp')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        sources = [
            'probes/android-qwen-terminal/QwenTerminalInstrumentation.kt',
            'probes/android-terminal-observation/TerminalObservationBarrier.kt',
            'probes/android-terminal-observation/TerminalAckFile.kt',
            'probes/android-qwen-arena/QwenArenaPolicy.kt',
        ] + ['probes/android-qwen-file/' + name for name in (
            'QwenTwoStageCore.kt', 'QwenFileActivity.kt', 'QwenFileInputs.kt', 'QwenFileReport.kt')]
        self.adapters = [
            'probes/android-qwen-heap/check_wrapper.py',
            'probes/android-qwen-terminal/check_wrapper.py',
            'probes/android-qwen-terminal/TerminalWrapperChecks.kt',
        ]
        other = ['probes/android-qwen-file/check_wrapper.py', 'probes/android-qwen-heap/HeapWrapperChecks.kt']
        for name in sources + self.adapters + other:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('immutable fixture ' + name + '\n')
        self.receipt_path = self.root / 'host/result.json'
        self.receipt_path.parent.mkdir()
        self.receipt = {
            'compileExitCode': 0, 'runtimeExitCode': 0, 'realNativeCalls': 0, 'deviceOperations': 0,
            'realCaseCore': True, 'hostAndroidAndNativeDoubles': True, 'output': 'ASSERTIONS=12\n',
            'sourceSHA256': {str(self.root / name): digest(self.root / name) for name in sources},
        }
        self.base = {
            'scaffoldSHA256': digest(self.root / 'probes/android-qwen-file/check_wrapper.py'),
            'adapterSHA256': digest(self.root / 'probes/android-qwen-heap/check_wrapper.py'),
            'heapChecksSHA256': digest(self.root / 'probes/android-qwen-heap/HeapWrapperChecks.kt'),
            'oldProbeSourcesModified': False, 'selectedOriginalIndex': 25,
        }
        self.adapter_hashes = {name: digest(self.root / name) for name in self.adapters}
        self.save()

    def save(self):
        for path, value in [
            (self.receipt_path, self.receipt),
            (self.receipt_path.with_name('base-hashes.json'), self.base),
            (self.receipt_path.with_name('terminal-adapter-hashes.json'), self.adapter_hashes),
        ]:
            path.write_text(json.dumps(value))

    def verify(self):
        return PREPARE.verify_host(self.receipt_path, self.root)

    def rejected(self):
        with self.assertRaises((AssertionError, ValueError, OSError, KeyError, TypeError)):
            self.verify()
        self.assertFalse((self.root / 'source').exists())

    def test_valid_exact_host_receipt(self):
        self.verify()

    def test_failed_or_coerced_exit_fields_are_rejected(self):
        for key in ('compileExitCode', 'runtimeExitCode', 'realNativeCalls', 'deviceOperations'):
            for value in (False, 0.0, '0', 1):
                with self.subTest(key=key, value=value):
                    self.receipt[key] = value
                    self.save()
                    self.rejected()
            self.receipt[key] = 0
        self.save()

    def test_required_source_and_component_hashes_are_not_optional(self):
        for key in list(self.receipt['sourceSHA256']):
            with self.subTest(key=key):
                value = self.receipt['sourceSHA256'].pop(key)
                self.save()
                self.rejected()
                self.receipt['sourceSHA256'][key] = value

    def test_outside_source_even_with_correct_digest_is_rejected(self):
        outside = self.root.parent / (self.root.name + '-outside.kt')
        self.addCleanup(lambda: outside.unlink(missing_ok=True))
        outside.write_bytes(b'outside')
        self.receipt['sourceSHA256'][str(outside)] = digest(outside)
        self.save()
        self.rejected()

    def test_current_adapter_or_source_drift_is_rejected(self):
        for name in self.adapters + ['probes/android-qwen-terminal/QwenTerminalInstrumentation.kt']:
            with self.subTest(name=name):
                path = self.root / name
                before = path.read_bytes()
                path.write_bytes(before + b'changed')
                self.rejected()
                path.write_bytes(before)

    def test_adapter_receipt_exact_keys_and_old_source_flags(self):
        self.adapter_hashes['probes/unbound.py'] = '0' * 64
        self.save()
        self.rejected()
        self.adapter_hashes.pop('probes/unbound.py')
        for value in (True, None):
            self.base['oldProbeSourcesModified'] = value
            self.save()
            self.rejected()

    def test_prepare_output_outside_root_rejected_before_old_receipt_read(self):
        outside = self.root.parent / (self.root.name + '-outside-output')
        with self.assertRaises((ValueError, AssertionError)):
            PREPARE.main(self.receipt_path, root=self.root, out=outside, old=self.root / 'old')
        self.assertFalse(outside.exists())

    def test_prepare_old_source_outside_root_rejected_before_read(self):
        outside = self.root.parent / (self.root.name + '-outside-old')
        with self.assertRaises((ValueError, AssertionError)):
            PREPARE.main(self.receipt_path, root=self.root, out=self.root / 'new', old=outside)
        self.assertFalse((self.root / 'new').exists())

    def test_prepare_alias_paths_rejected_before_read_or_output_creation(self):
        output_link = self.root / 'new-link'
        output_link.symlink_to(self.root / 'new')
        old_link = self.root / 'old-link'
        old_link.symlink_to(self.root / 'old')
        host_link = self.receipt_path.with_name('host-link.json')
        host_link.symlink_to(self.receipt_path)
        for host_path, out, old in [
            (self.receipt_path, output_link, self.root / 'old'),
            (self.receipt_path, self.root / 'new', old_link),
            (host_link, self.root / 'new', self.root / 'old'),
        ]:
            with self.subTest(host_path=host_path, out=out, old=old):
                with self.assertRaises((ValueError, AssertionError)):
                    PREPARE.main(host_path, root=self.root, out=out, old=old)
                self.assertFalse((self.root / 'new').exists())

    def test_stale_dangling_receipt_rejected_before_old_receipt_read(self):
        out = self.root / 'new'
        out.mkdir()
        (out / 'candidate-accepted.json').symlink_to(out / 'absent.json')
        with self.assertRaises(FileExistsError):
            PREPARE.main(self.receipt_path, root=self.root, out=out, old=self.root / 'old')
        self.assertFalse((out / 'source').exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
