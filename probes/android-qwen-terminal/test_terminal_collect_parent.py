"""Report collection through actual FILE callbacks and a real fake-ADB child."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
QT = ROOT / 'probes/android-qwen-terminal'
sys.path.insert(0, str(QT))
from terminal_prepare_fixture import PrepareFixture, APP, RUN, FP
import terminal_device_prepare
import terminal_device_lifecycle
import terminal_file_io
from terminal_observer import acknowledgement

TARGET = Path(os.environ.get('ODAI_COLLECT_SOURCE', str(QT / 'terminal_device_collect.py')))


class TerminalCollectParent(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TARGET.is_file(), 'missing bounded terminal collection implementation')
        spec = importlib.util.spec_from_file_location('terminal_collect_under_test', TARGET)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def fixture(self, status='completed'):
        f = PrepareFixture(self)
        f.call(terminal_device_prepare)
        source = f.adb.read_text().replace('import hashlib,json,os,shlex,shutil,sys',
                                          'import hashlib,json,os,shlex,shutil,sys,subprocess')
        source = source.replace(
            "if tokens[:2]==['cat','/proc/self/status']:",
            "if tokens[:2]==['sh','-c']:\n"
            " assert app==APP and len(tokens)==3\n"
            " (p/'report-read-count').write_text(str(int((p/'report-read-count').read_text())+1) if (p/'report-read-count').exists() else '1')\n"
            " if (p/'change-report-second').exists() and (p/'report-read-count').read_text()=='2':\n"
            "  report=device/'apps'/APP/'files/w3-qwen-terminal'/('report-'+RUN+'.json');report.write_bytes(report.read_bytes()+b' ')\n"
            " child=subprocess.run(['/bin/sh','-c',tokens[2]],cwd=device/'apps'/APP,stdout=subprocess.PIPE,stderr=subprocess.PIPE)\n"
            " sys.stdout.buffer.write(child.stdout);sys.stderr.buffer.write(child.stderr);sys.exit(child.returncode)\n"
            "if tokens[:2]==['cat','/proc/self/status']:")
        f.adb.write_text(source)
        report = dict(runId=RUN, sourceFingerprint=FP, pid=123, processStartTicks='456',
                      terminalObservationProtocol='ODAI-TERMINAL-ACK-v1',
                      probeVariant='cpu-arena-off-heap-terminal-ack-v1', terminalAckTimeoutMillis=30000,
                      terminal=True, status=status, phase='terminal', activeCaseIndex=None,
                      inputManifestSHA256=f.context['plan']['manifestSHA256'], actualConstructors=1,
                      nativeDecodeCalls=2, streamsCreated=2, microphoneStarts=0, networkRequests=0)
        f.report = report
        f.raw = json.dumps(report, separators=(',', ':')).encode()
        remote = f.device / 'apps' / APP / 'files/w3-qwen-terminal' / ('report-' + RUN + '.json')
        remote.parent.mkdir(parents=True)
        remote.write_bytes(f.raw)
        f.remote_report = remote
        self.bind_local(f)
        f.restore_start = len(f.mutations())
        f.calls_before = len((f.root / 'calls.jsonl').read_text().splitlines())
        f.guards_before = f.guards
        return f

    def bind_local(self, f):
        sha = hashlib.sha256(f.raw).hexdigest()
        binding = dict(runId=RUN, sourceFingerprint=FP, pid=123, startTicks='456', terminalReportSha256=sha)
        sample = dict(pid=123, startTicks='456', rssBytes=4096, deviceAvailableBytes=1073741824)
        observation = dict(pidBefore=123, pidAfter=123, startTicksBefore='456', startTicksAfter='456',
                           uptimeBeforeNs=10, firstReportReadNs=11, secondReportReadNs=12,
                           uptimeAfterNs=13, rssBytes=4096, deviceAvailableBytes=1073741824,
                           terminalObservationGap=None)
        receipt = dict(schemaVersion=1, binding=binding, terminalReportSha256=sha,
                       observation=observation, sampleBefore=sample, sampleAfter=sample,
                       clockDomain='host-monotonic-ns')
        before_link = dict(schemaVersion=1, binding=binding, terminalReportSha256=sha,
                           sampleBeforeLink=sample, sampleStartedNs=14, sampleFinishedNs=15,
                           clockDomain='host-monotonic-ns')
        (f.out / 'terminal-observed.json').write_bytes(f.raw)
        f.write_json(f.out / 'terminal-receipt.json', receipt)
        f.write_json(f.out / 'terminal-before-link.json', before_link)
        (f.out / 'terminal-ack.txt').write_bytes(acknowledgement(f.raw, f.raw, binding, observation))
        log = ('INSTRUMENTATION_RESULT: terminalAckAttempted=true\n'
               'INSTRUMENTATION_RESULT: terminalAckReceived=true\n'
               'INSTRUMENTATION_RESULT: terminalReportSha256=' + sha + '\n'
               'INSTRUMENTATION_CODE: ' + ('-1' if f.report['status'] == 'completed' else '0') + '\n').encode()
        (f.out / 'instrumentation.log').write_bytes(log)
        finish = terminal_device_lifecycle.parse_instrumentation_finish(
            log, expected_report_sha256=sha, terminal_status=f.report['status'])
        f.write_json(f.out / 'run-started.json', dict(runId=RUN, sourceFingerprint=FP, startedNs=0,
                     automaticRetry=False, plannedConstructors=1, maxPlannedStreams=2,
                     maxPlannedDecode=2, plannedMicrophone=0, inputCount=1))
        f.write_json(f.out / 'host-result.json', dict(runId=RUN, sourceFingerprint=FP,
                     finishVerified=True, finish=finish, observation=dict(observation=observation,
                     publication=dict(published=True, durability_verified=True, stage_retained=True,
                     final_relative='files/w3-qwen-terminal/ack-' + RUN + '.txt',
                     stage_relative='files/w3-qwen-terminal/ack-' + RUN + '.txt.stage'), pollSamples=1,
                     qualityAccepted=False, truePeakMeasured=False), qualityAccepted=False,
                     truePeakMeasured=False, automaticRetry=False))

    def collect(self, f):
        return self.module.collect(f.out, f.context, serial='serial-01')

    def assert_unchanged(self, f):
        self.assertEqual(f.mutations()[f.restore_start:], [])
        self.assertEqual(f.context['ordinary'](), f.before_ordinary)
        self.assertEqual(f.context['ordinary_models'](), f.before_models)
        self.assertEqual((f.out / 'terminal-observed.json').read_bytes(), f.raw)

    def test_actual_dual_bounded_reads_bind_terminal_and_models_without_resource_sampling(self):
        f = self.fixture()
        receipt = self.collect(f)
        self.assertIs(receipt['reportPresent'], True)
        self.assertIs(receipt['twoReadsIdentical'], True)
        self.assertIs(receipt['terminalAccepted'], True)
        self.assertEqual(receipt['sha256'], hashlib.sha256(f.raw).hexdigest())
        self.assertIs(receipt['qualityAccepted'], False)
        self.assertIs(receipt['truePeakMeasured'], False)
        self.assertIs(receipt['nativePolicyEffectVerified'], False)
        self.assertEqual(set(receipt['modelHashesAfterMatch']), set(f.context['models']))
        self.assertTrue(all(x is True for x in receipt['modelHashesAfterMatch'].values()))
        self.assertEqual((f.out / 'device-report.json').read_bytes(), f.raw)
        self.assertEqual((f.out / 'device-report-confirm.json').read_bytes(), f.raw)
        self.assertEqual(json.loads((f.out / 'collection.json').read_text()), receipt)
        calls = [json.loads(x) for x in (f.root / 'calls.jsonl').read_text().splitlines()][f.calls_before:]
        self.assertEqual(f.guards - f.guards_before, len(calls))
        self.assertEqual((f.root / 'report-read-count').read_text(), '2')
        self.assertFalse(any('/proc/' in arg or arg == 'pidof' or arg == 'instrument'
                             for call in calls for arg in call))
        self.assert_unchanged(f)

    def test_failed_terminal_is_collectible_without_quality_acceptance(self):
        f = self.fixture('failed')
        receipt = self.collect(f)
        self.assertIs(receipt['terminalAccepted'], True)
        self.assertEqual(receipt['status'], 'failed')
        self.assertIs(receipt['qualityAccepted'], False)
        self.assert_unchanged(f)

    def test_stale_or_linked_outputs_refuse_before_any_transport(self):
        for name in ['collection.json', 'device-report.json', 'device-report-confirm.json']:
            for linked in [False, True]:
                with self.subTest(name=name, linked=linked):
                    f = self.fixture()
                    path = f.out / name
                    if linked:
                        path.symlink_to(f.out / 'absent-protected')
                    else:
                        path.write_bytes(b'protected prior')
                    with self.assertRaises((OSError, ValueError, AssertionError)):
                        self.collect(f)
                    self.assertEqual(len((f.root / 'calls.jsonl').read_text().splitlines()), f.calls_before)

    def test_local_claim_or_ack_corruption_refuses_before_transport(self):
        for name in ['terminal-receipt.json', 'terminal-before-link.json', 'host-result.json',
                     'run-started.json', 'device-before.json', 'terminal-ack.txt']:
            with self.subTest(name=name):
                f = self.fixture()
                target = f.out / name
                if name.endswith('.json'):
                    row = json.loads(target.read_text())
                    row['runId'] = 'foreign' if name in ['host-result.json', 'run-started.json', 'device-before.json'] else row.get('runId')
                    if name in ['terminal-receipt.json', 'terminal-before-link.json']:
                        row['binding']['sourceFingerprint'] = 'a' * 64
                    target.write_text(json.dumps(row))
                else:
                    target.write_bytes(target.read_bytes() + b' ')
                with self.assertRaises((OSError, ValueError, AssertionError)):
                    self.collect(f)
                self.assertEqual(len((f.root / 'calls.jsonl').read_text().splitlines()), f.calls_before)

    def test_remote_second_read_change_preserves_local_terminal_and_records_failure(self):
        f = self.fixture()
        (f.root / 'change-report-second').touch()
        with self.assertRaises((OSError, ValueError, AssertionError, RuntimeError)):
            self.collect(f)
        receipt = json.loads((f.out / 'collection.json').read_text())
        self.assertIs(receipt['terminalAccepted'], False)
        self.assertTrue(receipt['errors'])
        self.assertEqual((f.root / 'report-read-count').read_text(), '2')
        self.assert_unchanged(f)

    def test_invalid_observation_or_journal_metadata_refuses_before_transport(self):
        for mode in ['schema-bool', 'gap', 'link-time', 'finish', 'duplicate']:
            with self.subTest(mode=mode):
                f = self.fixture()
                target = f.out / ('host-result.json' if mode == 'finish' else
                                  'terminal-before-link.json' if mode == 'link-time' else
                                  'terminal-receipt.json')
                row = json.loads(target.read_text())
                if mode == 'schema-bool':
                    row['schemaVersion'] = True
                elif mode == 'gap':
                    row['observation']['terminalObservationGap'] = 'retained gap'
                elif mode == 'link-time':
                    row['sampleStartedNs'] = 9
                elif mode == 'finish':
                    row['finish']['terminalReportSha256'] = 'a' * 64
                if mode == 'duplicate':
                    target.write_text(target.read_text()[:-1] + ',"schemaVersion":1}')
                else:
                    target.write_text(json.dumps(row))
                with self.assertRaises((OSError, ValueError, AssertionError)):
                    self.collect(f)
                self.assertEqual(len((f.root / 'calls.jsonl').read_text().splitlines()), f.calls_before)

    def test_private_model_and_ordinary_apk_drift_cannot_be_collected_as_success(self):
        for mode in ['model', 'ordinary']:
            with self.subTest(mode=mode):
                f = self.fixture()
                path = (f.device / 'ordinary.apk' if mode == 'ordinary' else
                        f.device / 'apps' / APP / 'no_backup/qwen3-asr-0.6b-int8-393f8a14/encoder.int8.onnx')
                path.write_bytes(b'changed protected bytes')
                with self.assertRaises((OSError, ValueError, AssertionError, RuntimeError)):
                    self.collect(f)
                receipt = json.loads((f.out / 'collection.json').read_text())
                self.assertIs(receipt['terminalAccepted'], False)
                self.assertTrue(receipt['errors'])
                self.assertEqual(path.read_bytes(), b'changed protected bytes')
                self.assertEqual(f.mutations()[f.restore_start:], [])

    def test_coherent_counter_and_manifest_violations_never_gain_observation_acceptance(self):
        for field, value in [('nativeDecodeCalls', 3), ('streamsCreated', True), ('actualConstructors', 2),
                             ('microphoneStarts', False), ('networkRequests', 1), ('inputManifestSHA256', 'a' * 64)]:
            with self.subTest(field=field, value=value):
                f = self.fixture()
                f.report[field] = value
                f.raw = json.dumps(f.report).encode()
                f.remote_report.write_bytes(f.raw)
                self.bind_local(f)
                with self.assertRaises((OSError, ValueError, AssertionError)):
                    self.collect(f)
                self.assertEqual(len((f.root / 'calls.jsonl').read_text().splitlines()), f.calls_before)

    def test_collection_interrupt_preserves_primary_and_secondary_journal_error(self):
        f = self.fixture()
        primary = KeyboardInterrupt('primary collect interruption')
        actual = f.context['call']
        def call(*args, **kwargs):
            result = actual(*args, **kwargs)
            if args[0] == 'exec-out':
                raise primary
            return result
        f.context['call'] = call
        real_write = terminal_file_io.write_new_durable
        def writer(root, relative, payload):
            if relative == 'collection.json':
                raise SystemExit('secondary collect journal interruption')
            return real_write(root, relative, payload)
        with patch.object(terminal_file_io, 'write_new_durable', side_effect=writer):
            with self.assertRaises(KeyboardInterrupt) as caught:
                self.collect(f)
        self.assertIs(caught.exception, primary)
        self.assertIn('secondary collect journal interruption', ' '.join(getattr(primary, '__notes__', [])))
        self.assertEqual(f.mutations()[f.restore_start:], [])

    def test_published_collection_failure_is_not_rewritten_or_retried(self):
        f = self.fixture()
        real_write = terminal_file_io.write_new_durable
        primary = terminal_file_io.PublishedButNotDurableError('final directory fsync uncertain')
        writes = []
        def writer(root, relative, payload):
            writes.append(relative)
            result = real_write(root, relative, payload)
            if relative == 'collection.json':
                raise primary
            return result
        with patch.object(terminal_file_io, 'write_new_durable', side_effect=writer):
            with self.assertRaises(terminal_file_io.PublishedButNotDurableError) as caught:
                self.collect(f)
        self.assertIs(caught.exception, primary)
        self.assertIs(caught.exception.published, True)
        self.assertIs(caught.exception.durability_verified, False)
        self.assertEqual(writes.count('collection.json'), 1)
        self.assertEqual((f.out / 'device-report.json').read_bytes(), f.raw)
        self.assertEqual((f.out / 'device-report-confirm.json').read_bytes(), f.raw)
        self.assertEqual(f.mutations()[f.restore_start:], [])

    def test_run_marker_and_nested_finish_types_are_exact_before_transport(self):
        for field, value in [('plannedConstructors', True), ('plannedMicrophone', False),
                             ('inputCount', True), ('startedNs', 14),
                             ('terminalAckReceived', 1), ('instrumentationCode', False),
                             ('qualityAccepted', 0)]:
            with self.subTest(field=field, value=value):
                f = self.fixture('failed')
                is_marker = field in ['plannedConstructors', 'plannedMicrophone', 'inputCount', 'startedNs']
                target = f.out / ('run-started.json' if is_marker else 'host-result.json')
                row = json.loads(target.read_text())
                (row if is_marker else row['finish'])[field] = value
                target.write_text(json.dumps(row))
                with self.assertRaises((OSError, ValueError, AssertionError)):
                    self.collect(f)
                self.assertEqual(len((f.root / 'calls.jsonl').read_text().splitlines()), f.calls_before)

    def test_sample_aggregate_mismatch_refuses_and_report_finite_metrics_remain_valid(self):
        f = self.fixture()
        target = f.out / 'terminal-receipt.json'
        row = json.loads(target.read_text())
        row['sampleAfter']['rssBytes'] += 1
        target.write_text(json.dumps(row))
        with self.assertRaises((OSError, ValueError, AssertionError)):
            self.collect(f)
        self.assertEqual(len((f.root / 'calls.jsonl').read_text().splitlines()), f.calls_before)

        f = self.fixture()
        f.report['elapsedSeconds'] = 0.5
        f.raw = json.dumps(f.report).encode()
        f.remote_report.write_bytes(f.raw)
        self.bind_local(f)
        self.assertIs(self.collect(f)['terminalAccepted'], True)


if __name__ == '__main__':
    unittest.main(verbosity=2)
