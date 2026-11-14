"""Independent acceptance for a future freeze, using synthetic local fixtures only."""
from pathlib import Path
from contextlib import contextmanager
import copy
import hashlib
import importlib.util
import json
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / 'probes/android-qwen-terminal/terminal_device_contract.py'
OUTPUT = 'artifacts/verification/w3-qwen-terminal-20261114'
FP = 'ff5b57b0b1ff08d8d8c984efd04dfbb86f62a5cc4cab02700c5804a0c7c0c3c5'
APP = 'io.ondeviceai.acceptance.qwenterminal'
RUN = 'w3-qwen-terminal-20261114-01'
RUNNER = APP + '.test/io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation'
BUDGETS = dict(maxRssBytes=1610612736, minAvailableBytes=536870912,
               minInitialAvailableBytes=2147483648, phaseSeconds=120,
               totalSeconds=240, startupSeconds=20, afterAckSeconds=15, ackMillis=30000)
PLAN = dict(runId=RUN, sourceFingerprint=FP, caseCount=1, maxNativeDecodeCalls=2,
            maxStreamsCreated=2, maxConstructors=1, selectedOriginalIndex=25,
            order=['fleurs-en_us-1776'],
            manifestSHA256='8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f',
            sampledRssStopAboveBytes=1610612736, deviceAvailableStopBelowBytes=536870912,
            caseStopAboveSeconds=120, totalStopAboveSeconds=240, terminalAckTimeoutMillis=30000,
            automaticRetry=False, microphoneStarts=0, systemAsrRequests=0,
            productionChanges=False, currentUser='0', preciseClockAlignmentVerified=False)
BINDINGS = {
    'probes/android-qwen-terminal/QwenTerminalInstrumentation.kt': 'test-src/QwenTerminalInstrumentation.kt',
    'probes/android-terminal-observation/TerminalObservationBarrier.kt': 'test-src/TerminalObservationBarrier.kt',
    'probes/android-terminal-observation/TerminalAckFile.kt': 'test-src/TerminalAckFile.kt',
    'probes/android-qwen-arena/QwenArenaPolicy.kt': 'main-src/QwenArenaPolicy.kt',
    'probes/android-qwen-load/QwenLoadCore.kt': 'main-src/QwenLoadCore.kt',
}
BINDINGS.update({'probes/android-qwen-file/'+n: 'main-src/'+n for n in
                 ('QwenTwoStageCore.kt', 'QwenFileActivity.kt', 'QwenFileInputs.kt', 'QwenFileReport.kt')})
OBS = ('terminal_observer.py', 'terminal_file_io.py', 'terminal_observation_coordinator.py',
       'adb_process_sampler.py', 'adb_terminal_report_reader.py',
       'adb_terminal_ack_publisher.py', 'adb_terminal_observation.py', 'terminal_polling.py')
DRIVERS = ('device_acceptance.py', 'terminal_device_contract.py', 'terminal_device_lifecycle.py',
           'terminal_source_adapter.py', 'terminal_device_prepare.py', 'terminal_device_restore.py', 'terminal_device_collect.py', 'terminal_device_policy.py')
REQUIRED = set(BINDINGS) | {'probes/android-terminal-observation/'+n for n in OBS}
REQUIRED |= {'probes/android-qwen-terminal/'+n for n in DRIVERS}
REQUIRED |= {'probes/android-qwen-terminal/'+n for n in
             ('prepare_build.py', 'build_acceptance.py', 'check_wrapper.py', 'TerminalWrapperChecks.kt')}
REQUIRED |= {'probes/android-qwen-file/device_acceptance.py', 'probes/android-qwen-file/cases.json',
             '.omx/artifacts/w3-model-reselection-20261110/model-manifest.json',
             '.omx/artifacts/w3-model-reselection-20261110/android-target-local.json',
             'docs/superpowers/specs/2026-11-12-w3-terminal-observation-design.md',
             'docs/superpowers/plans/2026-11-12-w3-terminal-observation.md'}
REVIEWS = ('docs/validation/w3-qwen-terminal-driver-source-review-20261114.json',
           'docs/validation/w3-qwen-terminal-driver-parent-review-20261114.json')
REVIEWED = {'probes/android-qwen-terminal/'+n for n in DRIVERS}
REVIEWED |= {'probes/android-terminal-observation/'+n for n in OBS}
LOCAL = ('main.apk', 'test.apk', 'package-verified.json', 'candidate-accepted.json',
         'run-plan.json', 'production-before.json', 'source/test-assets/cases.json')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')


def load_module():
    spec = importlib.util.spec_from_file_location('terminal_device_contract_under_test', TARGET)
    module = importlib.util.module_from_spec(spec)
    with patch('subprocess.run', side_effect=AssertionError('subprocess during freeze validation')), \
         patch('subprocess.Popen', side_effect=AssertionError('Popen during freeze validation')):
        spec.loader.exec_module(module)
    return module


@contextmanager
def fixture():
    with tempfile.TemporaryDirectory(prefix='w3-freeze-parent-') as temporary:
        root = Path(temporary).resolve(); out = root/OUTPUT
        accepted = json.loads((ROOT/OUTPUT/'candidate-accepted.json').read_bytes())
        for name, wanted in accepted['sourceFiles'].items():
            target = out/'source'/name; target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT/OUTPUT/'source'/name, target)
            assert digest(target) == wanted
        write_json(out/'candidate-accepted.json', accepted)
        for name in REQUIRED:
            target = root/name; target.parent.mkdir(parents=True, exist_ok=True)
            if name in BINDINGS:
                shutil.copyfile(out/'source'/BINDINGS[name], target)
            else:
                target.write_bytes(b'synthetic frozen evidence, never executed\n')
        package = json.loads((ROOT/OUTPUT/'package-verified.json').read_bytes())
        for name in ('main.apk', 'test.apk'):
            (out/name).write_bytes(('synthetic '+name).encode())
            package['files'][name].update(bytes=(out/name).stat().st_size, sha256=digest(out/name))
        write_json(out/'package-verified.json', package)
        write_json(out/'run-plan.json', PLAN)
        protected = root/'production/protected.kt'; protected.parent.mkdir(); protected.write_bytes(b'unchanged')
        write_json(out/'production-before.json', {'production/protected.kt': digest(protected)})
        host_files = {}
        for key, hash_key in (('path', 'sha256'), ('baseHashesPath', 'baseHashesSHA256'),
                              ('terminalAdapterHashesPath', 'terminalAdapterHashesSHA256')):
            name = accepted['hostReceipt'][key]
            target = root/name; target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT/name, target)
            assert digest(target) == accepted['hostReceipt'][hash_key]
            host_files[name] = digest(target)
        for name in REVIEWS:
            write_json(root/name, {'verdict': 'accept',
                                  'sourceSHA256': {n: digest(root/n) for n in REVIEWED}})
        files = {n: digest(root/n) for n in REQUIRED | set(REVIEWS) | {'production/protected.kt'}}
        files.update({OUTPUT+'/'+n: digest(out/n) for n in LOCAL})
        files.update({OUTPUT+'/source/'+n: wanted for n, wanted in accepted['sourceFiles'].items()})
        files.update(host_files)
        contract = dict(schemaVersion=1, runId=RUN, app=APP, runner=RUNNER, sourceFingerprint=FP,
                        independentSourceReviewAccepted=True, independentParentReviewAccepted=True,
                        automaticRetry=False, nativePolicyEffectVerified=False,
                        currentUser='0', budgets=copy.deepcopy(BUDGETS), files=files)
        write_json(out/'diagnostic-frozen.json', contract)
        yield root, out, contract


class TerminalDeviceContractParent(unittest.TestCase):
    def verify(self, module, root, out):
        with patch('subprocess.run', side_effect=AssertionError('transport from pure freeze')), \
             patch('subprocess.Popen', side_effect=AssertionError('launch from pure freeze')):
            return module.verify_frozen_contract(root, out)

    def test_source_adapter_cannot_be_coherently_excluded_from_freeze_and_reviews(self):
        module=load_module()
        with fixture() as (root,out,contract):
            self.verify(module,root,out)
            adapter='probes/android-qwen-terminal/terminal_source_adapter.py'
            del contract['files'][adapter]
            for name in REVIEWS:
                review=json.loads((root/name).read_text())
                del review['sourceSHA256'][adapter]
                write_json(root/name,review)
                contract['files'][name]=digest(root/name)
            write_json(out/'diagnostic-frozen.json',contract)
            with self.assertRaises((ValueError,AssertionError)):
                self.verify(module,root,out)

    def test_prepare_cannot_be_coherently_excluded_from_freeze_and_reviews(self):
        module=load_module()
        with fixture() as (root,out,contract):
            self.verify(module,root,out)
            adapter='probes/android-qwen-terminal/terminal_device_prepare.py'
            del contract['files'][adapter]
            for name in REVIEWS:
                review=json.loads((root/name).read_text())
                del review['sourceSHA256'][adapter]
                write_json(root/name,review)
                contract['files'][name]=digest(root/name)
            write_json(out/'diagnostic-frozen.json',contract)
            with self.assertRaises((ValueError,AssertionError)):
                self.verify(module,root,out)

    def test_restore_cannot_be_coherently_excluded_from_freeze_and_reviews(self):
        module=load_module()
        with fixture() as (root,out,contract):
            self.verify(module,root,out)
            adapter='probes/android-qwen-terminal/terminal_device_restore.py'
            del contract['files'][adapter]
            for name in REVIEWS:
                review=json.loads((root/name).read_text())
                del review['sourceSHA256'][adapter]
                write_json(root/name,review)
                contract['files'][name]=digest(root/name)
            write_json(out/'diagnostic-frozen.json',contract)
            with self.assertRaises((ValueError,AssertionError)):
                self.verify(module,root,out)

    def test_collect_cannot_be_coherently_excluded_from_freeze_and_reviews(self):
        module=load_module()
        with fixture() as (root,out,contract):
            self.verify(module,root,out)
            adapter='probes/android-qwen-terminal/terminal_device_collect.py'
            del contract['files'][adapter]
            for name in REVIEWS:
                review=json.loads((root/name).read_text())
                del review['sourceSHA256'][adapter]
                write_json(root/name,review)
                contract['files'][name]=digest(root/name)
            write_json(out/'diagnostic-frozen.json',contract)
            with self.assertRaises((ValueError,AssertionError)):
                self.verify(module,root,out)

    def test_policy_cannot_be_coherently_excluded_from_freeze_and_reviews(self):
        module=load_module()
        with fixture() as (root,out,contract):
            self.verify(module,root,out)
            dependency='probes/android-qwen-terminal/terminal_device_policy.py'
            del contract['files'][dependency]
            for name in REVIEWS:
                review=json.loads((root/name).read_text())
                del review['sourceSHA256'][dependency]
                write_json(root/name,review)
                contract['files'][name]=digest(root/name)
            write_json(out/'diagnostic-frozen.json',contract)
            with self.assertRaises((ValueError,AssertionError)):
                self.verify(module,root,out)

    def test_complete_synthetic_contract_accepts_without_device_or_writes(self):
        module = load_module()
        with fixture() as (root, out, contract):
            before = {str(p.relative_to(root)): digest(p) for p in root.rglob('*') if p.is_file()}
            result = self.verify(module, root, out)
            self.assertEqual(result, contract)
            self.assertEqual(before, {str(p.relative_to(root)): digest(p) for p in root.rglob('*') if p.is_file()})

    def test_required_frozen_files_cannot_be_omitted_even_if_rest_valid(self):
        module = load_module()
        with fixture() as (root, out, contract):
            names = REQUIRED | set(REVIEWS) | {OUTPUT+'/'+n for n in LOCAL}
            for name in sorted(names):
                with self.subTest(name=name):
                    altered = copy.deepcopy(contract); del altered['files'][name]
                    write_json(out/'diagnostic-frozen.json', altered)
                    with self.assertRaises((AssertionError, ValueError)):
                        self.verify(module, root, out)

    def test_exact_metadata_budgets_and_plan_types_are_required(self):
        module = load_module()
        with fixture() as (root, out, contract):
            changes = [('schemaVersion', True), ('runId', 'w3-qwen-heap-20261111-01'),
                       ('app', 'io.ondeviceai.demo'), ('runner', RUNNER+'x'),
                       ('sourceFingerprint', '0'*64), ('currentUser', 0),
                       ('independentSourceReviewAccepted', 1), ('independentParentReviewAccepted', False),
                       ('automaticRetry', 0), ('nativePolicyEffectVerified', True)]
            for key, value in changes:
                with self.subTest(key=key):
                    altered = copy.deepcopy(contract); altered[key] = value
                    write_json(out/'diagnostic-frozen.json', altered)
                    with self.assertRaises((AssertionError, ValueError)):
                        self.verify(module, root, out)
            for key, value in BUDGETS.items():
                for wrong in (value+1, float(value), True):
                    with self.subTest(budget=key, wrong=wrong):
                        altered = copy.deepcopy(contract); altered['budgets'][key] = wrong
                        write_json(out/'diagnostic-frozen.json', altered)
                        with self.assertRaises((AssertionError, ValueError)):
                            self.verify(module, root, out)
            for key, value in PLAN.items():
                with self.subTest(plan=key):
                    wrong = None if value is not None else 'wrong'
                    plan = copy.deepcopy(PLAN); plan[key] = wrong
                    write_json(out/'run-plan.json', plan)
                    altered = copy.deepcopy(contract); altered['files'][OUTPUT+'/run-plan.json'] = digest(out/'run-plan.json')
                    write_json(out/'diagnostic-frozen.json', altered)
                    with self.assertRaises((AssertionError, ValueError)):
                        self.verify(module, root, out)
                    write_json(out/'run-plan.json', PLAN)

    def test_changed_source_and_missing_transitive_prepared_file_refuse(self):
        module = load_module()
        with fixture() as (root, out, contract):
            source = json.loads((out/'candidate-accepted.json').read_bytes())
            name = next(iter(source['sourceFiles']))
            altered = copy.deepcopy(contract); del altered['files'][OUTPUT+'/source/'+name]
            write_json(out/'diagnostic-frozen.json', altered)
            with self.assertRaises((AssertionError, ValueError)):
                self.verify(module, root, out)
            write_json(out/'diagnostic-frozen.json', contract)
            (root/'probes/android-terminal-observation/terminal_polling.py').write_bytes(b'changed')
            with self.assertRaises((AssertionError, ValueError)):
                self.verify(module, root, out)

    def test_review_and_production_receipts_must_bind_actual_current_sources(self):
        module = load_module()
        with fixture() as (root, out, contract):
            for name in REVIEWS:
                original = (root/name).read_bytes()
                for change in ('verdict', 'missing-source', 'wrong-source'):
                    with self.subTest(name=name, change=change):
                        row = json.loads(original)
                        if change == 'verdict': row['verdict'] = 'revise'
                        elif change == 'missing-source': del row['sourceSHA256'][sorted(REVIEWED)[0]]
                        else: row['sourceSHA256'][sorted(REVIEWED)[0]] = '0'*64
                        write_json(root/name, row)
                        altered = copy.deepcopy(contract); altered['files'][name] = digest(root/name)
                        write_json(out/'diagnostic-frozen.json', altered)
                        with self.assertRaises((AssertionError, ValueError)):
                            self.verify(module, root, out)
                (root/name).write_bytes(original)
            altered = copy.deepcopy(contract); del altered['files']['production/protected.kt']
            write_json(out/'diagnostic-frozen.json', altered)
            with self.assertRaises((AssertionError, ValueError)):
                self.verify(module, root, out)

    def test_empty_hash_maps_unsafe_paths_and_symlinks_refuse(self):
        module = load_module()
        with fixture() as (root, out, contract):
            for path in ('../outside', '/etc/passwd', 'a/../b', './alias', 'a//b', 'a\\b'):
                with self.subTest(path=path):
                    altered = copy.deepcopy(contract); altered['files'][path] = '0'*64
                    write_json(out/'diagnostic-frozen.json', altered)
                    with self.assertRaises((AssertionError, ValueError)):
                        self.verify(module, root, out)
            altered = copy.deepcopy(contract); altered['files'] = {}
            write_json(out/'diagnostic-frozen.json', altered)
            with self.assertRaises((AssertionError, ValueError)):
                self.verify(module, root, out)
            write_json(out/'diagnostic-frozen.json', contract)
            target = root/sorted(REQUIRED)[0]; original = target.read_bytes(); target.unlink()
            other = root/'other'; other.write_bytes(original); target.symlink_to(other)
            with self.assertRaises((AssertionError, ValueError)):
                self.verify(module, root, out)

    def test_duplicate_json_key_and_nonfinite_constants_refuse(self):
        module = load_module()
        with fixture() as (root, out, contract):
            original = (out/'diagnostic-frozen.json').read_text()
            for suffix in ('"runId": "'+RUN+'"', '"extra": NaN', '"extra": 1e999'):
                (out/'diagnostic-frozen.json').write_text(original.rstrip()[:-1]+','+suffix+'}')
                with self.subTest(suffix=suffix), self.assertRaises((AssertionError, ValueError)):
                    self.verify(module, root, out)

    def test_root_and_output_aliases_refuse(self):
        module = load_module()
        with fixture() as (root, out, contract):
            for bad_root, bad_out in ((Path('.'), out), (root/'x/..', out), (root, out/'x/..'), (root, root)):
                with self.subTest(root=bad_root, out=bad_out), self.assertRaises((AssertionError, ValueError)):
                    self.verify(module, bad_root, bad_out)

    def test_package_receipt_semantics_are_not_replaced_by_hash_presence(self):
        module = load_module()
        with fixture() as (root, out, contract):
            package_path = out/'package-verified.json'
            original = json.loads(package_path.read_bytes())
            mutations = [
                lambda p: p.update(exactRunnerVerified='old.Runner'),
                lambda p: p.update(actualTestAssetsVerified=True),
                lambda p: p.update(sourceFingerprint='0'*64),
                lambda p: p.update(runtimeLibraries={}),
                lambda p: p.update(actualClassesVerified=[]),
                lambda p: p['permissions'].update({'main.apk':['android.permission.RECORD_AUDIO']}),
                lambda p: p['files']['main.apk'].update(package='io.ondeviceai.demo'),
                lambda p: p['files']['main.apk'].update(sha256='0'*64),
                lambda p: p['files']['main.apk'].update(bytes=True),
            ]
            for index, mutate in enumerate(mutations):
                with self.subTest(index=index):
                    row = copy.deepcopy(original); mutate(row); write_json(package_path, row)
                    altered = copy.deepcopy(contract)
                    altered['files'][OUTPUT+'/package-verified.json'] = digest(package_path)
                    write_json(out/'diagnostic-frozen.json', altered)
                    with self.assertRaises((AssertionError, ValueError)):
                        self.verify(module, root, out)

    def test_root_to_prepared_binding_and_intermediate_symlink_are_checked(self):
        module = load_module()
        with fixture() as (root, out, contract):
            name = sorted(BINDINGS)[0]; target = root/name; original = target.read_bytes()
            target.write_bytes(b'changed root Kotlin but unchanged prepared source')
            altered = copy.deepcopy(contract); altered['files'][name] = digest(target)
            # Even coherent review/freeze hashes cannot override the packaged source binding.
            if name in REVIEWED:
                for review_name in REVIEWS:
                    row = json.loads((root/review_name).read_bytes())
                    row['sourceSHA256'][name] = digest(target); write_json(root/review_name, row)
                    altered['files'][review_name] = digest(root/review_name)
            write_json(out/'diagnostic-frozen.json', altered)
            with self.assertRaises((AssertionError, ValueError)):
                self.verify(module, root, out)
            target.write_bytes(original)
            write_json(out/'diagnostic-frozen.json', contract)
            directory = root/'probes/android-qwen-load'; moved = root/'load-moved'
            directory.rename(moved); directory.symlink_to(moved, target_is_directory=True)
            with self.assertRaises((AssertionError, ValueError)):
                self.verify(module, root, out)

    def test_coherent_protection_receipt_does_not_require_synthetic_fixture_name(self):
        module = load_module()
        with fixture() as (root, out, contract):
            old = 'production/protected.kt'; new = 'production/actual-source.kt'
            (root/old).rename(root/new)
            altered = copy.deepcopy(contract); altered['files'][new] = altered['files'].pop(old)
            write_json(out/'production-before.json', {new: digest(root/new)})
            altered['files'][OUTPUT+'/production-before.json'] = digest(out/'production-before.json')
            write_json(out/'diagnostic-frozen.json', altered)
            self.assertEqual(self.verify(module, root, out), altered)

    def test_frozen_contract_file_itself_cannot_be_symlink(self):
        module = load_module()
        with fixture() as (root, out, contract):
            target = out/'diagnostic-frozen.json'; actual = root/'not-the-contract.json'
            target.rename(actual); target.symlink_to(actual)
            with self.assertRaises((AssertionError, ValueError)):
                self.verify(module, root, out)

    def test_fixed_package_profile_requires_19_unique_string_class_names(self):
        module = load_module()
        with fixture() as (root, out, contract):
            path = out/'package-verified.json'; original = json.loads(path.read_bytes())
            subset = ['QwenTerminalInstrumentation','TerminalAckFile','TerminalObservationBarrier']
            for classes in (subset, subset+['duplicate']*16,
                            subset+[str(i) for i in range(15)]+[0],
                            original['actualClassesVerified']+['extra']):
                with self.subTest(classes=classes):
                    row = copy.deepcopy(original); row['actualClassesVerified'] = classes
                    write_json(path, row); altered = copy.deepcopy(contract)
                    altered['files'][OUTPUT+'/package-verified.json'] = digest(path)
                    write_json(out/'diagnostic-frozen.json', altered)
                    with self.assertRaises((AssertionError, ValueError)):
                        self.verify(module, root, out)


if __name__ == '__main__':
    unittest.main(verbosity=2)
