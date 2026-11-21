"""Parent-owned offline comparison of two consumed runs; never calls ADB."""
from pathlib import Path
import hashlib
import importlib.util
import json

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/verification/w3-qwen-kv288-emulator-20261111'
OLD = ROOT / 'artifacts/verification/w3-qwen-arena-emulator-20261111'


def read(path):
    return json.loads(path.read_bytes())


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    route = load('kv288_compare_route', ROOT / 'probes/android-qwen-kv288/device_acceptance.py')
    contract = route.verify_frozen_contract()
    assert len(contract['files']) == 448
    reports = {key: read(path / 'device-report.json') for key, path in [('512', OLD), ('288', OUT)]}
    manifest = read(OUT / 'source/test-assets/cases.json')['cases']
    metrics = load('kv288_fixed_metrics', ROOT / 'tools/speech_error_metrics.py')
    evidence = {}
    scores = {}
    for key, path in [('512', OLD), ('288', OUT)]:
        report = reports[key]
        assert report == read(path / 'device-report-confirm.json')
        assert (path / 'device-report.json').read_bytes() == (path / 'device-report-confirm.json').read_bytes()
        assert len(report['rows']) == 65 and len({row['id'] for row in report['rows']}) == 65
        assert report['config']['maxTotalLen'] == int(key) and report['config']['maxNewTokens'] == 128
        assert report['terminal'] and report['status'] == 'completed'
        assert report['actualConstructors'] == 1 and report['streamsCreated'] == report['nativeDecodeCalls'] == 117
        assert report['recognizerReleaseSucceeded'] and report['errorClass'] is None
        host = read(path / 'host-result.json')
        acceptance = read(path / 'parent-arena-acceptance.json')
        assert all(acceptance['checks'].values()) and len(acceptance['checks']) == 18
        assert acceptance['executionAccepted'] and acceptance['engineeringQualityAccepted']
        assert acceptance['reportSHA256'] == sha(path / 'device-report.json')
        collection = read(path / 'collection.json')
        assert collection['sha256'] == sha(path / 'device-report.json') and collection['twoReadsIdentical']
        assert all(collection['modelHashesAfterMatch'].values())
        samples = [json.loads(line) for line in (path / 'rss-samples.jsonl').read_text().splitlines()]
        assert len(samples) == host['samples']
        assert max(row['rssBytes'] for row in samples) == host['sampledMaxRssBytes']
        assert host['exitCode'] == 0 and host['stopReason'] is None and host['terminalObservationGap'] is None
        assert host['sampledMaxRssBytes'] <= 1610612736
        available = min(row['deviceAvailableBytes'] for row in samples)
        assert available >= 536870912
        rtf = [row['caseNanos'] / 1e9 / (row['samples'] / 16000) for row in report['rows']]
        scores[key] = {}
        for cohort, unit in [('mandarin', 'character'), ('english-near', 'word'), ('english-far', 'word')]:
            rows = [row for row in report['rows'] if row['cohort'] == cohort]
            score = metrics.score_corpus([{'id': row['id'], 'reference': row['reference'],
                                         'hypothesis': row['caseState']['rawText']} for row in rows], casefold=True)
            assert score['aggregate'][unit] == acceptance['cohorts'][cohort]['score']['aggregate'][unit]
            scores[key][cohort] = score['aggregate'][unit]
        empty = sum(row['caseState']['rawText'] == '' for row in report['rows'])
        assert empty == 13
        evidence[key] = {'runId': report['runId'], 'reportSHA256': sha(path / 'device-report.json'),
                         'constructors': 1, 'streams': 117, 'decodeCalls': 117, 'rows': 65,
                         'strictEmpty': empty, 'nonempty': 65-empty,
                         'hostSeconds': host['seconds'], 'loadSeconds': report['loadNanos']/1e9,
                         'releaseSeconds': report['recognizerReleaseNanos']/1e9,
                         'rssSamples': len(samples), 'sampledMaxRssBytes': host['sampledMaxRssBytes'],
                         'minimumAvailableBytes': available, 'caseRtfRange': [min(rtf), max(rtf)],
                         'truePeakMeasured': False}
    comparisons = []
    stage_count = 0
    for expected, old, new in zip(manifest, reports['512']['rows'], reports['288']['rows']):
        bindings = {key: old[key] == new[key] == expected[target]
                    for key, target in {'id': 'id', 'locale': 'locale', 'cohort': 'cohort',
                                        'samples': 'samples', 'reference': 'reference', 'pcmSha256': 'sha256'}.items()}
        assert all(bindings.values())
        assert old['status'] == new['status'] == 'completed'
        old_state, new_state = old['caseState'], new['caseState']
        # Compare every persisted case/stage field except the measured duration.
        assert {k: v for k, v in old_state.items() if k != 'stages'} == {k: v for k, v in new_state.items() if k != 'stages'}
        assert len(old_state['stages']) == len(new_state['stages'])
        for before, after in zip(old_state['stages'], new_state['stages']):
            assert {k: v for k, v in before.items() if k != 'decodeNanos'} == {k: v for k, v in after.items() if k != 'decodeNanos'}
            stage_count += 1
        comparisons.append({'id': old['id'], 'inputBindings': bindings, 'finalTextIdentical': True,
                            'admissionAndLifecycleIdentical': True, 'stagesIdenticalExceptDuration': len(old_state['stages'])})
    assert stage_count == 117 and scores['512'] == scores['288']
    policy = read(OUT / 'allocator-policy-collected.json')
    assert policy['receiptMatches'] and policy['fileBytesMatch'] and policy['reportSHA256'] == evidence['288']['reportSHA256']
    assert policy['boundedReadBytes'] == 20 and policy['readLimitBytes'] == 21
    assert policy['nativePolicyEffectVerified'] is False
    restore = read(OUT / 'restoration.json')
    assert restore['ordinaryAppsModelsUnchanged'] and restore['emulatorLauncherReturned']
    stopped = read(OUT / 'emulator-stopped-confirmed.json')
    assert stopped['pidExited'] and stopped['adbSerialAbsent'] and stopped['ordinaryMediumPhoneConfigUnchanged']
    ordinary = read(OUT / 'ordinary-avd-before.json')
    assert sha(Path(ordinary['path'])) == ordinary['sha256']
    protected = read(OUT / 'protected-before-run.json')
    drift = [{'path': name, 'beforeSHA256': wanted, 'currentSHA256': sha(ROOT/name) if (ROOT/name).exists() else None}
             for name, wanted in protected.items() if not (ROOT/name).exists() or sha(ROOT/name) != wanted]
    guards = {}
    for name, relative in [('arena108', 'probes/android-qwen-arena/device_acceptance.py'),
                           ('heap120', 'probes/android-qwen-heap/device_acceptance.py'),
                           ('emulator327', 'artifacts/verification/w3-qwen-arena-emulator-20261111/device_acceptance_emulator.py')]:
        guard = load('compare_guard_' + name, ROOT/relative)
        guards[name] = len(guard.verify_frozen_contract()['files'])
        if name == 'heap120':
            assert all(not (guard.OUT/n).exists() for n in ['prepared.json', 'run-started.json', 'collection.json'])
    assert guards == {'arena108': 108, 'heap120': 120, 'emulator327': 327}
    result = {'schemaVersion': 1, 'sameOrderedInputs': 65, 'identicalFinalStrings': 65,
              'identicalStagesExceptDuration': stage_count, 'rows': comparisons, 'observations': evidence,
              'sameScorerSHA256': sha(ROOT/'tools/speech_error_metrics.py'), 'freshlyRecomputedScores': scores,
              'sampledMaxRssDifference288Minus512Bytes': evidence['288']['sampledMaxRssBytes']-evidence['512']['sampledMaxRssBytes'],
              'sampledMemoryReductionObserved': False, 'controlledSpeedupEstablished': False,
              'contractSHA256': sha(OUT/'diagnostic-frozen.json'), 'frozenFileCount': 448, 'priorGuards': guards,
              'protectedBeforeCount': len(protected), 'protectedMatching': len(protected)-len(drift), 'protectedDrift': drift,
              'policyCollectedExactly': True, 'nativePolicyEffectVerified': False, 'restoredAndStopped': True,
              'physicalHeapRunConsumed': False, 'newInferenceCalls': 0,
              'supplementalEmulatorEvidenceOnly': True, 'productionReady': False, 'w3Complete': False}
    receipt = read(OUT/'kv512-comparison.json')
    volatile = {'protectedMatching', 'protectedDrift'}
    assert {k: v for k, v in receipt.items() if k not in volatile} == {k: v for k, v in result.items() if k not in volatile}
    assert receipt == read(ROOT/'docs/validation/w3-qwen-kv288-emulator-observed-20261111.json')
    final_review = read(ROOT/'docs/validation/w3-qwen-kv288-final-review-20261111.json')
    assert final_review['result']['verdict'] == 'accept' and final_review['toolsObserved'] == 0
    assert final_review['parentDisposition']['blockingCodeFindings'] == 0
    cleanup = read(OUT/'cleanup-receipt.json')
    assert len(cleanup['removed']) == 7 and cleanup['ordinaryAVDNotTargeted']
    assert cleanup['originalModelsAndReportsRetained']
    for entry in cleanup['removed']:
        path = Path(entry['path'])
        assert path.parent == OUT/'avd-home/W3_Qwen_Kv288_20261111.avd'
        assert not path.exists() and not path.is_symlink()
    assert read(OUT/'parent-arena-acceptance-final-check.json') == read(OUT/'parent-arena-acceptance.json')
    print(json.dumps({'accepted': True, 'inputs': 65, 'identicalFinalStrings': 65, 'identicalStages': 117,
                      'freshScoringMatches': True, 'sampledMemoryReductionObserved': False,
                      'frozenFiles': 448, 'priorGuards': guards, 'restoredAndStopped': True,
                      'taskDisksRemoved': 7, 'physicalHeapRunConsumed': False, 'productionReady': False}))


if __name__ == '__main__':
    main()
