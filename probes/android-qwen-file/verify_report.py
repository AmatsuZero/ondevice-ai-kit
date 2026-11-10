"""Parent-only structural, coverage and fixed-threshold evaluation of collected reports."""
from pathlib import Path
import hashlib
import importlib.util
import json
import sys

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('speech_metrics', ROOT / 'tools/speech_error_metrics.py')
metrics = importlib.util.module_from_spec(spec)
spec.loader.exec_module(metrics)
MODEL_MANIFEST = json.loads((ROOT / '.omx/artifacts/w3-model-reselection-20261110/model-manifest.json').read_bytes())
MODEL_SPECS = {name: row for name, row in MODEL_MANIFEST['files'].items()
               if ('/' not in name and name.endswith('.onnx')) or name.startswith('tokenizer/')}


def evaluate(report, host, manifest, source_fingerprint):
    checks = {}
    checks['source'] = report.get('sourceFingerprint') == source_fingerprint
    checks['manifest'] = report.get('inputManifestSHA256') == '8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f'
    checks['terminal'] = report.get('terminal') is True and report.get('status') == 'completed'
    checks['host'] = host.get('exitCode') == 0 and host.get('stopReason') is None
    checks['constructor'] = report.get('actualConstructors') == 1 and report.get('constructorReturned') is True
    checks['release'] = report.get('recognizerReleaseAttempts') == 1 and report.get('recognizerReleaseSucceeded') is True
    checks['teardown'] = all(report.get('foreground', {}).get(k) is True for k in ('resumed', 'paused', 'destroyed', 'teardownAck'))
    checks['errors'] = all(report.get(k) is None for k in ('errorClass', 'errorMessage', 'recognizerReleaseErrorClass', 'wrapperPersistErrorClass', 'teardownErrorClass'))
    checks['sideEffects'] = report.get('microphoneStarts') == 0 and report.get('networkRequests') == 0
    model_rows = report.get('modelsVerified', [])
    checks['models'] = (len(model_rows) == 6 and {m.get('relativePath'): {'bytes':m.get('bytes'), 'sha256':m.get('sha256')}
                                               for m in model_rows} == MODEL_SPECS)
    config = report.get('config', {})
    wanted = {'featureSampleRate': 16000, 'featureDim': 128, 'provider': 'cpu', 'numThreads': 2,
              'decodingMethod': 'greedy_search', 'debug': False, 'maxTotalLen': 512, 'maxNewTokens': 128,
              'topP': .8, 'seed': 42, 'languageMode': 'admit-auto-then-forced'}
    checks['config'] = all(config.get(k) == v for k, v in wanted.items()) and abs(config.get('temperature', 0) - 1e-6) < 1e-12
    rows = report.get('rows', [])
    expected = manifest['cases']
    checks['rowCount'] = len(rows) == len(expected) == 65
    checks['rowBindings'] = checks['rowCount'] and all(
        all(row.get(k) == original[v] for k, v in {'id':'id', 'locale':'locale', 'cohort':'cohort', 'samples':'samples',
                                                   'pcmSha256':'sha256', 'reference':'reference'}.items())
        for row, original in zip(rows, expected))
    valid = []
    row_checks = []
    decode_sum = stream_sum = 0
    for index, row in enumerate(rows):
        binding_ok = index < len(expected) and all(row.get(k) == expected[index][v] for k, v in {
            'id':'id', 'locale':'locale', 'cohort':'cohort', 'samples':'samples', 'pcmSha256':'sha256', 'reference':'reference'}.items())
        state = row.get('caseState') or {}
        stages = state.get('stages', [])
        decode_sum += sum(s.get('decodeAttempts', 0) for s in stages)
        stream_sum += sum(s.get('createReturned') is True for s in stages)
        languages = [''] + ([{'zh-CN':'Chinese', 'en-US':'English'}.get(row.get('locale'))] if state.get('admitted') is True else [])
        ok = (binding_ok and row.get('status') == 'completed' and state.get('status') == 'completed'
              and type(state.get('admitted')) is bool and type(state.get('rawText')) is str
              and all(state.get(k) is None for k in ('errorClass','errorMessage','persistErrorClass'))
              and len(stages) == len(languages)
              and [s.get('language') for s in stages] == languages
              and all(s.get('status') == 'completed' and s.get('createReturned') is True
                      and all(s.get(k) == 1 for k in ('createAttempts','optionAttempts','acceptAttempts','decodeAttempts','resultAttempts','releaseAttempts'))
                      and s.get('releaseSucceeded') is True and s.get('errorClass') is None and s.get('releaseErrorClass') is None
                      and type(s.get('decodeNanos')) is int and s['decodeNanos'] >= 0 and type(s.get('rawText')) is str
                      for s in stages)
              and type(row.get('caseNanos')) is int and 0 <= row['caseNanos'] <= 120_000_000_000)
        if ok:
            ok = (state['admitted'] == (stages[0]['rawText'] != '')
                  and state['rawText'] == (stages[-1]['rawText'] if state['admitted'] else ''))
        row_checks.append({'id':row.get('id'), 'completeAndConsistent':ok})
        if ok:
            valid.append(row)
    checks['rowsComplete'] = len(valid) == 65
    checks['counters'] = (report.get('nativeDecodeCalls') == decode_sum and report.get('streamsCreated') == stream_sum
                          and 0 <= decode_sum <= 130 and 0 <= stream_sum <= 130)
    checks['rssSamples'] = (type(host.get('samples')) is int and host['samples'] > 0
                            and type(host.get('sampledMaxRssBytes')) is int
                            and 0 < host['sampledMaxRssBytes'] <= 1536*1024*1024)
    cohorts = {}
    for cohort, units, threshold in [('mandarin','character',.20), ('english-near','word',.20), ('english-far','word',.35)]:
        want = [c for c in expected if c['cohort'] == cohort]
        selected = [c for c in valid if c.get('cohort') == cohort]
        full = checks['rowBindings'] and len(selected) == len(want)
        scores = metrics.score_corpus([{'id':c['id'],'reference':c['reference'],'hypothesis':c['caseState']['rawText']} for c in selected], casefold=True) if selected else None
        rate = None if scores is None else scores['aggregate'][units]['rate']
        speech = all(c['caseState']['admitted'] is True and c['caseState']['rawText'] != '' for c in selected)
        cohorts[cohort] = {'expected':len(want), 'scored':len(selected), 'fullCoverage':full,
                           'scoresAreSubsetOnly':not full, 'metric':units, 'threshold':threshold,
                           'score':scores, 'engineeringGatePassed':full and speech and rate is not None and rate <= threshold}
    challenges = {}
    for cohort in ('silence','nonspeech-noise'):
        want = [c for c in expected if c['cohort'] == cohort]
        selected = [c for c in valid if c.get('cohort') == cohort]
        empty = [c for c in selected if c['caseState']['rawText'] == '' and c['caseState']['admitted'] is False]
        challenges[cohort] = {'expected':len(want), 'completed':len(selected), 'strictEmpty':len(empty),
                             'engineeringGatePassed':checks['rowBindings'] and len(empty)==len(want),
                             'humanListeningAudit':False if cohort=='nonspeech-noise' else None}
    execution = all(checks.values())
    quality = execution and all(c['engineeringGatePassed'] for c in cohorts.values()) and all(c['engineeringGatePassed'] for c in challenges.values())
    return {'schemaVersion':1, 'checks':checks, 'rowChecks':row_checks, 'executionAccepted':execution,
            'engineeringQualityAccepted':quality, 'cohorts':cohorts, 'challenges':challenges,
            'completedConsistentRows':len(valid), 'plannedRows':65,
            'actualDecodeCallsObserved':decode_sum, 'actualCreatedStreamsObserved':stream_sum,
            'truePeakMeasured':False, 'productionReady':False,
            'scope':'Fixed public file pipeline only; no microphone/system ASR/sustained GPU or energy acceptance.'}


def main(root=None):
    root = root or ROOT / 'artifacts/verification/w3-qwen-file-20261111'
    report_path = root/'device-report.json'
    if not report_path.is_file():
        result = {'schemaVersion':1, 'executionAccepted':False, 'engineeringQualityAccepted':False,
                  'reason':'no device report collected', 'completedConsistentRows':0,
                  'actualDecodeCallsObserved':None, 'countsAreExactTerminalEvidence':False,
                  'productionReady':False}
        with (root/'parent-file-acceptance.json').open('x') as f:
            json.dump(result,f,indent=2); f.write('\n')
        print(json.dumps(result)); return
    report_bytes = report_path.read_bytes()
    report = json.loads(report_bytes)
    host = json.loads((root/'host-result.json').read_bytes())
    package = json.loads((root/'package-verified.json').read_bytes())
    manifest = json.loads((root/'source/test-assets/cases.json').read_bytes())
    result = evaluate(report, host, manifest, package['sourceFingerprint'])
    result['reportSHA256'] = hashlib.sha256(report_bytes).hexdigest()
    with (root/'parent-file-acceptance.json').open('x') as f:
        json.dump(result, f, ensure_ascii=False, indent=2); f.write('\n')
    print(json.dumps({k:result[k] for k in ('executionAccepted','engineeringQualityAccepted','completedConsistentRows','actualDecodeCallsObserved')}))


if __name__ == '__main__':
    main()
