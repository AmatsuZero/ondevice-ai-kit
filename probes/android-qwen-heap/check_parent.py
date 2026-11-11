"""Synthetic report tests for the real parent evaluator; no device or inference."""
from pathlib import Path
import copy
import hashlib
import importlib.util
import json
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


base_checks = load('heap_fixture_file_checks', ROOT / 'probes/android-qwen-file/check_parent.py')
evaluator_path = Path(__file__).with_name('verify_report.py')
verify = load('heap_report_evaluator', evaluator_path) if evaluator_path.is_file() else None


class HeapParentChecks(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(verify, 'distinct heap report evaluator absent')
        fixture = base_checks.ParentChecks()
        fixture.setUp()
        self.manifest = fixture.manifest
        self.report = fixture.report
        self.report['rows'] = [self.report['rows'][25]]
        self.report.update(runId='heap-synthetic-only', nativeDecodeCalls=2, streamsCreated=2,
                           pid=123,
                           loadNanos=9_000_000_000, recognizerReleaseNanos=100_000_000,
                           selectedClipId='fleurs-en_us-1776', selectedOriginalIndex=25,
                           verifiedInputCount=65, diagnosticScope='one unattempted public file',
                           probeVariant='cpu-arena-off-heap-boundaries-v1',
                           nativePolicyEffectVerified=False, heapTelemetryErrorClass=None)
        self.private = '/data/user/0/io.ondeviceai.acceptance.qwenheap/no_backup'
        config_path = self.private + '/cpu-arena-off/cpu.config'
        self.report['allocatorPolicy'] = dict(provider='cpu:' + config_path, configPath=config_path,
                                              bytes=20, sha256=hashlib.sha256(b'EnableCpuMemArena=0\n').hexdigest())
        self.report['config'].update(provider='cpu:' + config_path, requestedEnableCpuMemArena=False)
        self.host = fixture.host
        self.host['minDeviceAvailableBytes'] = 536870912
        self.labels = {key: key for key in ('initial', 'constructor-before', 'constructor-returned',
                                           'core-persist', 'case-returned', 'recognizer-released', 'teardown')}
        fields = ('language', 'status', 'createAttempts', 'decodeAttempts', 'resultAttempts',
                  'releaseAttempts', 'releaseSucceeded')
        stages = [{key: stage[key] for key in fields} for stage in self.report['rows'][0]['caseState']['stages']]
        self.report['heapEvents'] = []
        for i, label in enumerate(('initial', 'constructor-before', 'constructor-returned',
                                    'core-persist', 'core-persist', 'case-returned',
                                    'recognizer-released', 'teardown')):
            self.report['heapEvents'].append(dict(
                seq=i, label=label, elapsedRealtimeNanos=100+i, activeCaseIndex=0 if i in (3, 4) else None,
                casePhase='completed' if i in (3, 4) else None,
                stages=copy.deepcopy(stages) if i in (3, 4) else [], errorClass=None,
                nativeHeapSizeBytes=1000, nativeHeapAllocatedBytes=600, nativeHeapFreeBytes=400))

    def evaluate(self):
        return verify.evaluate(self.report, self.host, self.manifest, 'a'*64,
                               'heap-synthetic-only', self.private, self.labels)

    def rejected(self):
        result = self.evaluate()
        self.assertFalse(result['diagnosticAccepted'])
        self.assertFalse(result['engineeringQualityAccepted'])
        self.assertFalse(result['productionReady'])
        return result

    def test_complete_single_case_accepts_diagnostic_only_and_never_mutates_inputs(self):
        before = copy.deepcopy((self.report, self.host, self.manifest, self.labels))
        result = self.evaluate()
        self.assertTrue(result['diagnosticAccepted'])
        self.assertFalse(result['engineeringQualityAccepted'])
        self.assertFalse(result['productionReady'])
        self.assertFalse(result['truePeakMeasured'])
        self.assertFalse(result['nativePolicyEffectVerified'])
        self.assertEqual(result['completedConsistentRows'], 1)
        self.assertEqual(result['actualDecodeCallsObserved'], 2)
        self.assertEqual(result['plannedRows'], 1)
        self.assertEqual((self.report, self.host, self.manifest, self.labels), before)

    def test_wrong_input_or_run_or_extra_rows_cannot_accept(self):
        for change in (
            lambda r: r.update(runId='another-run'),
            lambda r: r.update(sourceFingerprint='b'*64),
            lambda r: r.update(selectedOriginalIndex=24),
            lambda r: r.update(selectedClipId='fleurs-en_us-other'),
            lambda r: r.update(verifiedInputCount=1),
            lambda r: r.update(diagnosticScope=''),
            lambda r: r['rows'][0].update(pcmSha256='0'*64),
            lambda r: r['rows'][0].update(reference='changed'),
            lambda r: r['rows'].append(copy.deepcopy(r['rows'][0])),
        ):
            with self.subTest(change=change):
                original = copy.deepcopy(self.report); change(self.report)
                self.rejected(); self.report = original

    def test_missing_or_ambiguous_boundary_map_cannot_accept_arbitrary_labels(self):
        for labels in (None, {}, {'initial': 'initial'}, {key: 'same' for key in self.labels}):
            with self.subTest(labels=labels):
                original = self.labels; self.labels = labels
                self.rejected(); self.labels = original

    def test_missing_reordered_or_oversized_heap_evidence_is_rejected(self):
        for change in (
            lambda r: r.update(heapEvents=[]),
            lambda r: r.update(heapEvents=r['heapEvents']*17),
            lambda r: r['heapEvents'][1].update(seq=0),
            lambda r: r['heapEvents'][2].update(elapsedRealtimeNanos=99),
            lambda r: r['heapEvents'][6].update(label='not-release'),
            lambda r: r['heapEvents'][0].update(label='teardown'),
            lambda r: r['heapEvents'][3].update(activeCaseIndex=25),
            lambda r: r['heapEvents'][4]['stages'][1].update(decodeAttempts=0),
        ):
            with self.subTest(change=change):
                original = copy.deepcopy(self.report); change(self.report)
                self.rejected(); self.report = original

    def test_zero_and_non_atomic_heap_triplets_are_legal(self):
        for event in self.report['heapEvents']:
            event.update(nativeHeapSizeBytes=0, nativeHeapAllocatedBytes=9, nativeHeapFreeBytes=4)
        self.assertTrue(self.evaluate()['diagnosticAccepted'])

    def test_error_telemetry_never_accepts_or_erases_release_truth(self):
        self.report['heapTelemetryErrorClass'] = 'java.lang.IllegalStateException'
        event = self.report['heapEvents'][0]
        event.update(errorClass='java.lang.IllegalStateException', nativeHeapSizeBytes=None,
                     nativeHeapAllocatedBytes=None, nativeHeapFreeBytes=None)
        self.rejected()
        self.assertTrue(self.report['recognizerReleaseSucceeded'])
        self.assertEqual(self.report['heapTelemetryErrorClass'], 'java.lang.IllegalStateException')

    def test_bad_typed_metrics_and_counters_do_not_coerce_to_success(self):
        for change in (
            lambda r: r['heapEvents'][0].update(nativeHeapSizeBytes=-1),
            lambda r: r['heapEvents'][0].update(nativeHeapSizeBytes=True),
            lambda r: r['heapEvents'][0].update(nativeHeapSizeBytes=1000.0),
            lambda r: r['heapEvents'][0].pop('nativeHeapAllocatedBytes'),
            lambda r: r['heapEvents'][0].update(elapsedRealtimeNanos=True),
            lambda r: r['heapEvents'][0].update(elapsedRealtimeNanos=2**63),
            lambda r: r.update(actualConstructors=True),
            lambda r: r.update(nativeDecodeCalls=2.0),
            lambda r: r.update(streamsCreated=3),
            lambda r: r['rows'][0]['caseState']['stages'][0].update(decodeAttempts=True),
        ):
            with self.subTest(change=change):
                original = copy.deepcopy(self.report); change(self.report)
                self.rejected(); self.report = original

    def test_model_config_policy_release_and_teardown_failures_are_rejected(self):
        for change in (
            lambda r: r['modelsVerified'][0].update(sha256='0'*64),
            lambda r: r['config'].update(maxTotalLen=256),
            lambda r: r['config'].update(requestedEnableCpuMemArena=True),
            lambda r: r.update(nativePolicyEffectVerified=True),
            lambda r: r['allocatorPolicy'].update(bytes=21),
            lambda r: r.update(recognizerReleaseSucceeded=False),
            lambda r: r['foreground'].update(teardownAck=False),
            lambda r: r['rows'][0]['caseState']['stages'][1].update(releaseSucceeded=False),
            lambda r: r.update(microphoneStarts=1),
            lambda r: r.update(networkRequests=1),
        ):
            with self.subTest(change=change):
                original = copy.deepcopy(self.report); change(self.report)
                self.rejected(); self.report = original

    def test_resource_limits_are_inclusive_and_stopped_hosts_are_rejected(self):
        self.host.update(sampledMaxRssBytes=1610612736, minDeviceAvailableBytes=536870912)
        self.assertTrue(self.evaluate()['diagnosticAccepted'])
        for change in (
            lambda h: h.update(sampledMaxRssBytes=1610612737),
            lambda h: h.update(minDeviceAvailableBytes=536870911),
            lambda h: h.update(stopReason='sampled_rss_above_1_5_gib'),
            lambda h: h.update(samples=0),
        ):
            with self.subTest(change=change):
                original = copy.deepcopy(self.host); change(self.host)
                self.rejected(); self.host = original

    def test_missing_available_observation_and_terminal_observation_gap_are_rejected(self):
        for change in (lambda h: h.pop('minDeviceAvailableBytes'),
                       lambda h: h.update(terminalObservationGap='read failed'),
                       lambda h: h.update(exitCode=False)):
            with self.subTest(change=change):
                original = copy.deepcopy(self.host); change(self.host)
                self.rejected(); self.host = original

    def test_loading_time_and_typed_configuration_cannot_bypass_original_gates(self):
        self.report['loadNanos'] = 120_000_000_000
        self.assertTrue(self.evaluate()['diagnosticAccepted'])
        for change in (
            lambda r: r.update(loadNanos=120_000_000_001),
            lambda r: r.update(loadNanos=-1),
            lambda r: r.update(loadNanos=True),
            lambda r: r.pop('loadNanos'),
            lambda r: r['config'].update(numThreads=2.0),
            lambda r: r['config'].update(debug=0),
        ):
            with self.subTest(change=change):
                original = copy.deepcopy(self.report); change(self.report)
                self.rejected(); self.report = original

    def test_heap_event_limit_accepts_128_and_rejects_129(self):
        event = self.report['heapEvents'][-1]
        for i in range(8, 128):
            self.report['heapEvents'].append({**event, 'seq': i, 'label': 'checkpoint', 'elapsedRealtimeNanos': 100+i})
        self.assertTrue(self.evaluate()['diagnosticAccepted'])
        self.report['heapEvents'].append({**event, 'seq': 128, 'label': 'checkpoint', 'elapsedRealtimeNanos': 228})
        self.rejected()

    def test_nonterminal_counts_are_unknown_and_partial_error_is_preserved(self):
        self.report.update(terminal=False, status='failed', nativeDecodeCalls=0,
                           errorClass='java.lang.IllegalStateException', errorMessage='original-decode-error')
        self.host['stopReason'] = 'resource_observation_failed'
        result = self.rejected()
        self.assertIsNone(result['actualDecodeCallsObserved'])
        self.assertFalse(result['countsAreExactTerminalEvidence'])
        self.assertEqual(result['primaryErrorClass'], 'java.lang.IllegalStateException')
        self.assertEqual(result['primaryErrorMessage'], 'original-decode-error')

    def test_malformed_reports_fail_closed_without_unhandled_exception(self):
        for value in (None, [], {'config': []}, {'rows': [None]}, {'heapEvents': [None]}):
            with self.subTest(value=value):
                original = self.report; self.report = value
                self.rejected(); self.report = original

    def test_missing_collection_is_structured_and_existing_output_is_preserved(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-parent-') as folder:
            root = Path(folder)
            verify.main(root)
            path = root/'parent-heap-acceptance.json'
            original = path.read_bytes(); result = json.loads(original)
            self.assertFalse(result['diagnosticAccepted'])
            self.assertFalse(result['countsAreExactTerminalEvidence'])
            with self.assertRaises(FileExistsError):
                verify.main(root)
            self.assertEqual(path.read_bytes(), original)

    def write_collection_fixture(self, repo):
        """Schema fixture only: placeholder APK/source files do not prove package validity."""
        root = repo/'artifacts/verification/heap-synthetic-only'
        root.mkdir(parents=True)
        prefix = str(root.relative_to(repo))+'/'
        files = {
            **{prefix+name: b'synthetic placeholder' for name in ('main.apk', 'test.apk')},
            prefix+'package-verified.json': json.dumps({'sourceFingerprint': 'a'*64}).encode(),
            prefix+'candidate-accepted.json': b'{}', prefix+'production-before.json': b'{}',
            prefix+'source/test-assets/cases.json': json.dumps(self.manifest).encode(),
            prefix+'run-plan.json': json.dumps(dict(
                runId='heap-synthetic-only', sourceFingerprint='a'*64, caseCount=1,
                selectedOriginalIndex=25, order=['fleurs-en_us-1776'],
                maxNativeDecodeCalls=2, maxStreamsCreated=2, maxConstructors=1,
                sampledRssStopAboveBytes=1610612736, deviceAvailableStopBelowBytes=536870912,
                caseStopAboveSeconds=120, automaticRetry=False, microphoneStarts=0,
                systemAsrRequests=0, productionChanges=False, currentUser='0')).encode(),
        }
        for name in ('QwenHeapInstrumentation.kt', 'verify_report.py', 'check_parent.py',
                     'check_wrapper.py', 'HeapWrapperChecks.kt', 'device_acceptance.py',
                     'prepare_build.py', 'build_acceptance.py'):
            files['probes/android-qwen-heap/'+name] = b'synthetic source placeholder'
        for path in ('probes/android-qwen-file/verify_report.py', 'probes/android-qwen-arena/verify_report.py',
                     'probes/android-qwen-file/cases.json', 'tools/speech_error_metrics.py',
                     '.omx/artifacts/w3-model-reselection-20261110/model-manifest.json'):
            files[path] = b'synthetic inherited input'
        files[verify.SPEC_PATH] = b'synthetic spec placeholder'
        files['evidence/prior-report.json'] = (
            ROOT/'artifacts/verification/w3-qwen-arena-wrapper-20261111/device-report.json').read_bytes()
        for path, content in files.items():
            target = repo/path; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(content)
        contract = dict(
            runId='heap-synthetic-only', sourceFingerprint='a'*64,
            app='io.ondeviceai.acceptance.qwenheap',
            runner='io.ondeviceai.acceptance.qwenheap.test/io.ondeviceai.acceptance.qwenfile.QwenHeapInstrumentation',
            independentSourceReviewAccepted=True, independentParentReviewAccepted=True,
            automaticRetry=False, nativePolicyEffectVerified=False,
            heapBoundaryLabels=self.labels, priorReportPath='evidence/prior-report.json',
            priorReportSHA256=hashlib.sha256(files['evidence/prior-report.json']).hexdigest(),
            files={path: hashlib.sha256(content).hexdigest() for path, content in files.items()})
        raw = json.dumps(self.report).encode(); report_sha = hashlib.sha256(raw).hexdigest()
        samples = [dict(elapsedSeconds=1.0, pid=123, startTicks='456', rssBytes=1024**3,
                        deviceAvailableBytes=536870912, activeCaseIndex=0, windowAgeSeconds=1.0,
                        deviceUptimeReadBeforeNs=100_000_000_000,deviceUptimeReadAfterNs=100_010_000_000,
                        deviceUptimeWindowLowerNs=99_000_000_000,deviceUptimeWindowUpperNs=101_010_000_000,
                        preciseClockAlignmentVerified=False),
                   dict(elapsedSeconds=2.0, pid=123, startTicks='456', rssBytes=1024**3,
                        deviceAvailableBytes=536870912, activeCaseIndex=None, windowAgeSeconds=2.0,
                        deviceUptimeReadBeforeNs=101_000_000_000,deviceUptimeReadAfterNs=101_010_000_000,
                        deviceUptimeWindowLowerNs=100_000_000_000,deviceUptimeWindowUpperNs=102_010_000_000,
                        preciseClockAlignmentVerified=False)]
        host = {**self.host, 'pid': 123, 'startTicks': '456', 'terminalObservationGap': None}
        payloads = {
            'diagnostic-frozen.json': contract, 'host-result.json': host,
            'collection.json': dict(reportPresent=True, twoReadsIdentical=True, sha256=report_sha,
                                    modelHashesAfterMatch=dict.fromkeys([
                                        'conv_frontend.onnx', 'decoder.int8.onnx', 'encoder.int8.onnx',
                                        'tokenizer/merges.txt', 'tokenizer/tokenizer_config.json', 'tokenizer/vocab.json'], True)),
            'allocator-policy-collected.json': dict(privateRoot=self.private, receiptMatches=True,
                                                     fileBytesMatch=True, reportSHA256=report_sha),
        }
        for path, value in payloads.items():
            (root/path).write_text(json.dumps(value))
        (root/'device-report.json').write_bytes(raw)
        (root/'device-report-confirm.json').write_bytes(raw)
        (root/'rss-samples.jsonl').write_text(''.join(json.dumps(value)+'\n' for value in samples))
        return root

    def test_collected_evidence_binds_raw_reads_policy_and_frozen_artifacts(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-collected-') as folder:
            repo = Path(folder); root = self.write_collection_fixture(repo)
            with patch.object(verify, 'ROOT', repo):
                result = verify.collected_evidence(root)
            self.assertTrue(result['diagnosticAccepted'])
            self.assertFalse(result['engineeringQualityAccepted'])

    def test_collection_cannot_skip_essential_hashes_or_review_gates(self):
        for change in (
            lambda c: c.update(files={}),
            lambda c: c['files'].pop('probes/android-qwen-heap/device_acceptance.py'),
            lambda c: c['files'].pop('probes/android-qwen-file/verify_report.py'),
            lambda c: c.update(independentParentReviewAccepted=False),
            lambda c: c.update(independentSourceReviewAccepted=False),
            lambda c: c.update(automaticRetry=True),
            lambda c: c.update(sourceFingerprint='b'*64),
            lambda c: c.update(priorReportSHA256='0'*64),
        ):
            with self.subTest(change=change), tempfile.TemporaryDirectory(prefix='odai-heap-contract-') as folder:
                repo = Path(folder); root = self.write_collection_fixture(repo)
                path = root/'diagnostic-frozen.json'; contract = json.loads(path.read_text()); change(contract)
                path.write_text(json.dumps(contract))
                with patch.object(verify, 'ROOT', repo), self.assertRaises((AssertionError, ValueError)):
                    verify.collected_evidence(root)

    def test_corrupt_collection_and_forged_host_summary_are_rejected(self):
        for name, change in (
            ('device-report-confirm.json', lambda j: j.update(runId='changed')),
            ('collection.json', lambda j: j.update(twoReadsIdentical=False)),
            ('allocator-policy-collected.json', lambda j: j.update(reportSHA256='0'*64)),
            ('collection.json', lambda j: j.update(modelHashesAfterMatch=dict.fromkeys([str(i) for i in range(6)], True))),
            ('host-result.json', lambda j: j.update(sampledMaxRssBytes=512)),
            ('host-result.json', lambda j: j.update(startTicks='different-process')),
        ):
            with self.subTest(name=name, change=change), tempfile.TemporaryDirectory(prefix='odai-heap-corrupt-') as folder:
                repo = Path(folder); root = self.write_collection_fixture(repo)
                path = root/name; value = json.loads(path.read_text()); change(value); path.write_text(json.dumps(value))
                with patch.object(verify, 'ROOT', repo), self.assertRaises((AssertionError, ValueError)):
                    verify.collected_evidence(root)

    def test_rebound_attempted_input_and_relaxed_resource_contract_are_rejected(self):
        for relative, change in (
            ('evidence/prior-report.json', lambda j: j['rows'][25].update(status='running', caseState={})),
            ('artifacts/verification/heap-synthetic-only/run-plan.json',
             lambda j: j.update(sampledRssStopAboveBytes=2*1024**3)),
            ('artifacts/verification/heap-synthetic-only/run-plan.json',
             lambda j: j.update(order=['fleurs-en_us-other'])),
        ):
            with self.subTest(relative=relative), tempfile.TemporaryDirectory(prefix='odai-heap-rebound-') as folder:
                repo = Path(folder); root = self.write_collection_fixture(repo)
                path = repo/relative; value = json.loads(path.read_text()); change(value)
                path.write_text(json.dumps(value))
                contract_path = root/'diagnostic-frozen.json'; contract = json.loads(contract_path.read_text())
                contract['files'][relative] = hashlib.sha256(path.read_bytes()).hexdigest()
                if relative == 'evidence/prior-report.json':
                    contract['priorReportSHA256'] = contract['files'][relative]
                contract_path.write_text(json.dumps(contract))
                with patch.object(verify, 'ROOT', repo), self.assertRaises((AssertionError, ValueError)):
                    verify.collected_evidence(root)

    def test_raw_uptime_rounding_keeps_existing_minus_one_second_tolerance(self):
        with tempfile.TemporaryDirectory(prefix='odai-heap-uptime-') as folder:
            repo = Path(folder); root = self.write_collection_fixture(repo)
            path = root/'rss-samples.jsonl'; samples = [json.loads(line) for line in path.read_text().splitlines()]
            samples[0]['windowAgeSeconds'] = -0.005
            path.write_text(''.join(json.dumps(value)+'\n' for value in samples))
            with patch.object(verify, 'ROOT', repo):
                self.assertTrue(verify.collected_evidence(root)['diagnosticAccepted'])

    def test_corrupt_trace_cannot_raise_out_of_main_or_accept_missing_resource_evidence(self):
        for value in (None, 2**1024):
            with self.subTest(value_type=type(value).__name__), tempfile.TemporaryDirectory(prefix='odai-heap-rss-bad-') as folder:
                repo = Path(folder); root = self.write_collection_fixture(repo)
                path = root/'rss-samples.jsonl'; samples = [json.loads(line) for line in path.read_text().splitlines()]
                samples[0]['elapsedSeconds'] = value
                path.write_text(''.join(json.dumps(item)+'\n' for item in samples))
                with patch.object(verify, 'ROOT', repo):
                    verify.main(root)
                result = json.loads((root/'parent-heap-acceptance.json').read_text())
                self.assertFalse(result['diagnosticAccepted'])

    def test_missing_or_inverted_device_clock_read_interval_is_rejected(self):
        for change in (
            lambda sample:sample.pop('deviceUptimeReadBeforeNs'),
            lambda sample:sample.update(deviceUptimeReadBeforeNs=102_000_000_000),
            lambda sample:sample.update(preciseClockAlignmentVerified=True),
        ):
            with self.subTest(change=change),tempfile.TemporaryDirectory(prefix='odai-heap-clock-') as folder:
                repo=Path(folder);root=self.write_collection_fixture(repo);path=root/'rss-samples.jsonl'
                samples=[json.loads(line) for line in path.read_text().splitlines()];change(samples[0])
                path.write_text(''.join(json.dumps(item)+'\n' for item in samples))
                with patch.object(verify,'ROOT',repo),self.assertRaises((AssertionError,ValueError,KeyError)):
                    verify.collected_evidence(root)


if __name__ == '__main__':
    unittest.main(verbosity=2)
