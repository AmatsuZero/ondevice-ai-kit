"""Read-only parent evaluation of one collected heap diagnostic, never full W3 acceptance."""
from pathlib import Path
import copy
import hashlib
import importlib.util
import json
import math

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/verification/w3-qwen-heap-20261111'
SELECTED_INDEX = 25
SELECTED_ID = 'fleurs-en_us-1776'
MAX_LONG = 2**63 - 1
BOUNDARIES = ('initial', 'constructor-before', 'constructor-returned', 'core-persist',
              'case-returned', 'recognizer-released', 'teardown')
STAGE_FIELDS = ('language', 'status', 'createAttempts', 'decodeAttempts', 'resultAttempts',
                'releaseAttempts', 'releaseSucceeded')
HEAP_FIELDS = ('nativeHeapSizeBytes', 'nativeHeapAllocatedBytes', 'nativeHeapFreeBytes')
SPEC_PATH = 'docs/superpowers/specs/2026-11-11-w3-qwen-heap-diagnostic.md'
PRIOR_REPORT_SHA256 = '3a5d9c4c13f91768b073fc98591b4bc88ecd5df7ab82c22bc1aa188d89d7140d'

spec = importlib.util.spec_from_file_location('heap_fixed_arena_binding', ROOT / 'probes/android-qwen-arena/verify_report.py')
arena = importlib.util.module_from_spec(spec)
spec.loader.exec_module(arena)
FROZEN_MANIFEST = json.loads((ROOT / 'probes/android-qwen-file/cases.json').read_bytes())


def integer(value, minimum=0, maximum=MAX_LONG):
    return type(value) is int and minimum <= value <= maximum


def failure(reason):
    return {'schemaVersion': 1, 'diagnosticAccepted': False, 'executionAccepted': False,
            'engineeringQualityAccepted': False, 'productionReady': False,
            'nativePolicyEffectVerified': False, 'truePeakMeasured': False,
            'countsAreExactTerminalEvidence': False, 'actualDecodeCallsObserved': None,
            'actualCreatedStreamsObserved': None, 'completedConsistentRows': 0,
            'plannedRows': 1, 'checks': {}, 'reason': reason}


def heap_evidence(report, labels, final_stages):
    events = report.get('heapEvents')
    if not isinstance(events, list) or not 8 <= len(events) <= 128:
        return False
    if (not isinstance(labels, dict) or set(labels) != set(BOUNDARIES)
            or not all(type(value) is str and value for value in labels.values())
            or len(set(labels.values())) != len(labels)):
        return False
    if 'heapTelemetryErrorClass' not in report or report['heapTelemetryErrorClass'] is not None:
        return False
    previous_time = -1
    previous_stages = []
    positions = {key: [] for key in BOUNDARIES}
    final_projection = [{key: stage.get(key) for key in STAGE_FIELDS} for stage in final_stages]
    last_core = None
    for index, event in enumerate(events):
        if not isinstance(event, dict):
            return False
        if not integer(event.get('seq')) or event['seq'] != index:
            return False
        time = event.get('elapsedRealtimeNanos')
        if not integer(time) or time < previous_time:
            return False
        previous_time = time
        if type(event.get('label')) is not str or not event['label']:
            return False
        if 'errorClass' not in event or event['errorClass'] is not None:
            return False
        if not all(integer(event.get(key)) for key in HEAP_FIELDS):
            return False
        if 'activeCaseIndex' not in event or not (event['activeCaseIndex'] is None
                or (type(event['activeCaseIndex']) is int and event['activeCaseIndex'] == 0)):
            return False
        if 'casePhase' not in event or not (event['casePhase'] is None
                or (type(event['casePhase']) is str and event['casePhase'])):
            return False
        stages = event.get('stages')
        if not isinstance(stages, list) or len(stages) > 2:
            return False
        for number, stage in enumerate(stages):
            if not isinstance(stage, dict) or stage.get('language') != ('', 'English')[number]:
                return False
            if stage.get('status') not in ('planned', 'running', 'completed', 'failed'):
                return False
            if not all(integer(stage.get(key), 0, 1) for key in STAGE_FIELDS[2:6]):
                return False
            if type(stage.get('releaseSucceeded')) is not bool:
                return False
            if (stage['resultAttempts'] > stage['decodeAttempts']
                    or stage['decodeAttempts'] > stage['createAttempts']
                    or stage['releaseAttempts'] > stage['createAttempts']
                    or (stage['releaseSucceeded'] and stage['releaseAttempts'] != 1)):
                return False
            if number < len(previous_stages) and any(
                    stage[key] < previous_stages[number][key] for key in STAGE_FIELDS[2:6]):
                return False
        if stages:
            if len(stages) < len(previous_stages):
                return False
            previous_stages = stages
        for key, label in labels.items():
            if event['label'] == label:
                positions[key].append(index)
                if key == 'core-persist':
                    last_core = [{field: stage.get(field) for field in STAGE_FIELDS} for stage in stages]
    if any(len(positions[key]) != 1 for key in BOUNDARIES if key != 'core-persist'):
        return False
    if not positions['core-persist'] or last_core != final_projection:
        return False
    order = [positions[key][0] for key in BOUNDARIES]
    return (order == sorted(order) and len(set(order)) == len(order)
            and positions['core-persist'][-1] < positions['case-returned'][0])


def evaluate(report, host, manifest, source_fingerprint, run_id, private_root, boundary_labels):
    if not isinstance(report, dict) or not isinstance(host, dict):
        return failure('report and host must be objects')
    try:
        normalized = copy.deepcopy(report)
        normalized.setdefault('config', {})['provider'] = 'cpu'
        selected = manifest['cases'][SELECTED_INDEX]
        base = arena.baseline.evaluate(normalized, host, {'cases': [selected]}, source_fingerprint)
        excluded = {'rowCount', 'rowBindings', 'rowsComplete', 'counters'}
        checks = {key: value for key, value in base['checks'].items() if key not in excluded}
        rows = report.get('rows', [])
        states = [(row.get('caseState') or {}) for row in rows]
        stages = [stage for state in states for stage in state.get('stages', [])]
        checks['runIdentity'] = (type(run_id) is str and bool(run_id) and report.get('runId') == run_id
                                 and type(source_fingerprint) is str and len(source_fingerprint) == 64
                                 and all(c in '0123456789abcdef' for c in source_fingerprint))
        checks['selectedInput'] = (manifest == FROZEN_MANIFEST and len(rows) == 1
                                  and report.get('selectedClipId') == SELECTED_ID
                                  and type(report.get('selectedOriginalIndex')) is int
                                  and report['selectedOriginalIndex'] == SELECTED_INDEX
                                  and type(report.get('verifiedInputCount')) is int
                                  and report['verifiedInputCount'] == 65
                                  and type(report.get('diagnosticScope')) is str and bool(report['diagnosticScope'])
                                  and selected['id'] == SELECTED_ID and selected['samples'] == 76800
                                  and selected['sha256'] == '2821c39358fc8049953945d8042d1833d86cb2245ca468cf5afc31ee1fe1a438'
                                  and base['completedConsistentRows'] == 1)
        checks['typedNativeCounters'] = (
            type(report.get('actualConstructors')) is int and report['actualConstructors'] == 1
            and type(report.get('recognizerReleaseAttempts')) is int and report['recognizerReleaseAttempts'] == 1
            and all(integer(report.get(key), 1, 2) for key in ('nativeDecodeCalls', 'streamsCreated'))
            and all(type(stage.get(key)) is int and stage[key] == 1 for stage in stages
                    for key in ('createAttempts', 'optionAttempts', 'acceptAttempts', 'decodeAttempts',
                                'resultAttempts', 'releaseAttempts'))
            and report['nativeDecodeCalls'] == sum(stage['decodeAttempts'] for stage in stages)
            and report['streamsCreated'] == len(stages))
        checks['typedResourceObservations'] = (
            integer(host.get('samples'), 1) and integer(host.get('sampledMaxRssBytes'), 1, 1610612736)
            and integer(host.get('minDeviceAvailableBytes'), 536870912)
            and type(host.get('exitCode')) is int and host['exitCode'] == 0
            and host.get('terminalObservationGap') is None
            and integer(report.get('loadNanos'), 0, 120_000_000_000))
        config = report['config']
        checks['typedConfiguration'] = (
            type(config.get('debug')) is bool
            and all(type(config.get(key)) is int for key in ('featureSampleRate', 'featureDim', 'numThreads',
                                                             'maxTotalLen', 'maxNewTokens', 'seed')))
        checks['typedSideEffects'] = all(type(report.get(key)) is int and report[key] == 0
                                         for key in ('microphoneStarts', 'networkRequests'))
        policy_report = copy.deepcopy(report)
        policy_report['probeVariant'] = 'cpu-arena-off-v1'
        checks['requestedArenaPolicy'] = (report.get('probeVariant') == 'cpu-arena-off-heap-boundaries-v1'
                                           and arena.policy_matches(policy_report, private_root))
        checks['heapBoundaryEvidence'] = heap_evidence(report, boundary_labels, stages)
        accepted = all(checks.values())
        exact = (base['countsAreExactTerminalEvidence'] and checks['typedNativeCounters']
                 and checks['selectedInput'] and checks['runIdentity'])
        result = failure('diagnostic accepted' if accepted else 'one or more diagnostic checks failed')
        result.update(checks=checks, diagnosticAccepted=accepted, executionAccepted=accepted,
                      completedConsistentRows=base['completedConsistentRows'], rowChecks=base['rowChecks'],
                      countsAreExactTerminalEvidence=exact,
                      actualDecodeCallsObserved=report['nativeDecodeCalls'] if exact else None,
                      actualCreatedStreamsObserved=report['streamsCreated'] if exact else None,
                      lastPersistedDecodeCalls=base['lastPersistedDecodeCalls'],
                      lastPersistedCreatedStreams=base['lastPersistedCreatedStreams'],
                      primaryErrorClass=report.get('errorClass'), primaryErrorMessage=report.get('errorMessage'),
                      heapTelemetryErrorClass=report.get('heapTelemetryErrorClass'),
                      heapEventCount=len(report.get('heapEvents', [])),
                      scope='One preselected public file and boundary counters only; no full quality, resource-budget or release acceptance.')
        return result
    except (KeyError, TypeError, ValueError, IndexError, AttributeError, OverflowError) as error:
        result = failure('malformed diagnostic data: ' + type(error).__name__)
        result.update(primaryErrorClass=report.get('errorClass'), primaryErrorMessage=report.get('errorMessage'),
                      heapTelemetryErrorClass=report.get('heapTelemetryErrorClass'))
        return result


def resource_summary(root, host, report):
    """Recompute the summary from bounded raw samples, including omitted available minima."""
    with (root / 'rss-samples.jsonl').open('rb') as stream:
        raw = stream.read(4*1024*1024 + 1)
    assert len(raw) <= 4*1024*1024, 'RSS trace too large'
    lines = raw.splitlines()
    assert 0 < len(lines) <= 4096, 'RSS sample count out of bounds'
    samples = [json.loads(line) for line in lines]
    assert integer(host['pid'], 1) and type(report['pid']) is int and report['pid'] == host['pid']
    assert type(host['startTicks']) is str and host['startTicks'].isascii() and host['startTicks'].isdigit()
    previous = -1
    for sample in samples:
        assert type(sample['pid']) is int and sample['pid'] == host['pid'] and sample['startTicks'] == host['startTicks']
        for key, minimum in (('elapsedSeconds', 0), ('windowAgeSeconds', -1)):
            assert (type(sample[key]) in (int, float) and minimum <= sample[key] <= MAX_LONG
                    and math.isfinite(sample[key]))
        assert sample['elapsedSeconds'] >= previous
        previous = sample['elapsedSeconds']
        assert sample['windowAgeSeconds'] <= 120
        assert integer(sample['rssBytes']) and integer(sample['deviceAvailableBytes'])
        assert sample['activeCaseIndex'] is None or (type(sample['activeCaseIndex']) is int and sample['activeCaseIndex'] == 0)
        before=sample['deviceUptimeReadBeforeNs'];after=sample['deviceUptimeReadAfterNs']
        assert integer(before) and integer(after) and before<=after
        assert sample['deviceUptimeWindowLowerNs']==max(0,before-1_000_000_000)
        assert sample['deviceUptimeWindowUpperNs']==after+1_000_000_000
        assert sample['preciseClockAlignmentVerified'] is False
    maximum = max(sample['rssBytes'] for sample in samples)
    assert type(host['samples']) is int and host['samples'] == len(samples)
    assert type(host['sampledMaxRssBytes']) is int and host['sampledMaxRssBytes'] == maximum
    return {**host, 'minDeviceAvailableBytes': min(sample['deviceAvailableBytes'] for sample in samples)}


def collected_evidence(root):
    """Bind local collection to reviewed, hashed artifacts; this function never runs a device."""
    raw = (root / 'device-report.json').read_bytes()
    report = json.loads(raw)
    contract = json.loads((root / 'diagnostic-frozen.json').read_bytes())
    assert contract['independentSourceReviewAccepted'] is True
    assert contract['independentParentReviewAccepted'] is True
    assert contract['automaticRetry'] is False and contract['nativePolicyEffectVerified'] is False
    assert contract['app'] == 'io.ondeviceai.acceptance.qwenheap'
    assert contract['runner'] == 'io.ondeviceai.acceptance.qwenheap.test/io.ondeviceai.acceptance.qwenfile.QwenHeapInstrumentation'
    prefix = str(root.relative_to(ROOT)) + '/'
    required = {prefix + name for name in ('main.apk', 'test.apk', 'package-verified.json',
                                           'candidate-accepted.json', 'run-plan.json', 'production-before.json',
                                           'source/test-assets/cases.json')}
    required |= {SPEC_PATH, 'probes/android-qwen-heap/QwenHeapInstrumentation.kt',
                 'probes/android-qwen-heap/verify_report.py', 'probes/android-qwen-heap/check_parent.py',
                 'probes/android-qwen-heap/check_wrapper.py', 'probes/android-qwen-heap/HeapWrapperChecks.kt',
                 'probes/android-qwen-heap/device_acceptance.py', 'probes/android-qwen-heap/prepare_build.py',
                 'probes/android-qwen-heap/build_acceptance.py', contract['priorReportPath'],
                 'probes/android-qwen-file/verify_report.py', 'probes/android-qwen-arena/verify_report.py',
                 'probes/android-qwen-file/cases.json', 'tools/speech_error_metrics.py',
                 '.omx/artifacts/w3-model-reselection-20261110/model-manifest.json'}
    assert required <= set(contract['files']), 'required frozen artifact hashes absent'
    for relative, wanted in contract['files'].items():
        target = (ROOT / relative).resolve()
        assert target.is_relative_to(ROOT.resolve()) and hashlib.sha256(target.read_bytes()).hexdigest() == wanted
    prior_raw = (ROOT / contract['priorReportPath']).read_bytes()
    assert hashlib.sha256(prior_raw).hexdigest() == contract['priorReportSHA256'] == PRIOR_REPORT_SHA256
    prior_row = json.loads(prior_raw)['rows'][SELECTED_INDEX]
    assert prior_row['id'] == SELECTED_ID and prior_row['status'] == 'planned' and prior_row['caseState'] is None
    collection = json.loads((root / 'collection.json').read_bytes())
    assert collection['reportPresent'] is True and collection['twoReadsIdentical'] is True
    assert raw == (root / 'device-report-confirm.json').read_bytes()
    assert hashlib.sha256(raw).hexdigest() == collection['sha256']
    assert set(collection['modelHashesAfterMatch']) == set(arena.baseline.MODEL_SPECS)
    assert all(v is True for v in collection['modelHashesAfterMatch'].values())
    policy = json.loads((root / 'allocator-policy-collected.json').read_bytes())
    assert policy['receiptMatches'] is True and policy['fileBytesMatch'] is True
    assert policy['reportSHA256'] == collection['sha256']
    package = json.loads((root / 'package-verified.json').read_bytes())
    assert contract['sourceFingerprint'] == package['sourceFingerprint']
    plan = json.loads((root / 'run-plan.json').read_bytes())
    wanted = {'runId': contract['runId'], 'sourceFingerprint': contract['sourceFingerprint'],
              'caseCount': 1, 'selectedOriginalIndex': 25, 'order': [SELECTED_ID],
              'maxNativeDecodeCalls': 2, 'maxStreamsCreated': 2, 'maxConstructors': 1,
              'sampledRssStopAboveBytes': 1610612736, 'deviceAvailableStopBelowBytes': 536870912,
              'caseStopAboveSeconds': 120, 'automaticRetry': False, 'microphoneStarts': 0,
              'systemAsrRequests': 0, 'productionChanges': False, 'currentUser': '0'}
    for key, value in wanted.items():
        assert type(plan.get(key)) is type(value) and plan[key] == value, key
    host = resource_summary(root, json.loads((root / 'host-result.json').read_bytes()), report)
    result = evaluate(report, host,
                      json.loads((root / 'source/test-assets/cases.json').read_bytes()),
                      contract['sourceFingerprint'], contract['runId'], policy['privateRoot'],
                      contract['heapBoundaryLabels'])
    result['reportSHA256'] = collection['sha256']
    return result


def main(root=OUT):
    try:
        result = collected_evidence(root)
    except (OSError, KeyError, TypeError, ValueError, AssertionError, IndexError, AttributeError, OverflowError) as error:
        result = failure('collected evidence unavailable or invalid: ' + type(error).__name__)
    with (root / 'parent-heap-acceptance.json').open('x') as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    print(json.dumps({key: result[key] for key in ('diagnosticAccepted', 'engineeringQualityAccepted', 'countsAreExactTerminalEvidence')}))


if __name__ == '__main__':
    main()
