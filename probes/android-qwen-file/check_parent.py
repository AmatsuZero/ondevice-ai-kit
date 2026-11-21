"""Parent guard and false-acceptance regressions. All reports here are synthetic."""
from pathlib import Path
import copy
import importlib.util
import json
import tempfile
import unittest


def module(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name+'.py'))
    obj = importlib.util.module_from_spec(spec); spec.loader.exec_module(obj); return obj


device = module('device_acceptance')
verify = module('verify_report')


class ParentChecks(unittest.TestCase):
    def setUp(self):
        root = Path(__file__).resolve().parents[2]
        self.manifest = json.loads((root/'probes/android-qwen-file/cases.json').read_bytes())
        self.report = {'sourceFingerprint':'a'*64, 'inputManifestSHA256':'8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f',
                       'terminal':True, 'status':'completed', 'actualConstructors':1,'constructorReturned':True,
                       'recognizerReleaseAttempts':1,'recognizerReleaseSucceeded':True,
                       'foreground':dict.fromkeys(('resumed','paused','destroyed','teardownAck'),True),
                       'microphoneStarts':0,'networkRequests':0,'rows':[],
                       'config':{'featureSampleRate':16000,'featureDim':128,'provider':'cpu','numThreads':2,
                                 'decodingMethod':'greedy_search','debug':False,'maxTotalLen':512,'maxNewTokens':128,
                                 'topP':.8,'temperature':1e-6,'seed':42,'languageMode':'admit-auto-then-forced'}}
        for c in self.manifest['cases']:
            admitted = c['cohort'] in ('mandarin','english-near','english-far')
            text = c['reference'] if admitted else ''
            langs = [''] + ([{'zh-CN':'Chinese','en-US':'English'}[c['locale']]] if admitted else [])
            stages=[]
            for language in langs:
                stages.append({'language':language,'status':'completed','createReturned':True,'releaseSucceeded':True,
                               **dict.fromkeys(('createAttempts','optionAttempts','acceptAttempts','decodeAttempts','resultAttempts','releaseAttempts'),1),
                               'rawText':text,'decodeNanos':1})
            state={'status':'completed','admitted':admitted,'rawText':text,'stages':stages}
            self.report['rows'].append({**{k:c[k] for k in ('id','locale','cohort','samples','reference')},
                                       'pcmSha256':c['sha256'],'status':'completed','caseState':state,'caseNanos':10})
        self.report['nativeDecodeCalls']=self.report['streamsCreated']=117
        self.report['modelsVerified']=[{'relativePath':name, **row} for name,row in verify.MODEL_SPECS.items()]
        self.host={'exitCode':0,'stopReason':None,'samples':2,'sampledMaxRssBytes':1024**3}

    def evaluate(self):
        return verify.evaluate(self.report,self.host,self.manifest,'a'*64)

    def test_complete_fixture_can_be_accepted(self):
        r=self.evaluate(); self.assertTrue(r['executionAccepted']);self.assertTrue(r['engineeringQualityAccepted'])
        self.assertFalse(r['productionReady'])

    def test_missing_collected_report_has_structured_failure_without_scoring(self):
        with tempfile.TemporaryDirectory(prefix='odai-qwen-missing-report-') as folder:
            root=Path(folder)
            verify.main(root)
            result=json.loads((root/'parent-file-acceptance.json').read_text())
            self.assertFalse(result['executionAccepted'])
            self.assertFalse(result['engineeringQualityAccepted'])
            self.assertEqual(result['reason'],'no device report collected')
            self.assertIsNone(result['actualDecodeCallsObserved'])
            self.assertEqual(result['completedConsistentRows'],0)

    def test_resource_boundaries(self):
        self.assertIsNone(device.budget_reason(1536*1024**2,512*1024**2,120))
        self.assertEqual(device.budget_reason(1536*1024**2+1,512*1024**2,120),'sampled_rss_above_1_5_gib')
        self.assertEqual(device.budget_reason(0,512*1024**2-1,120),'device_available_below_512_mib')
        self.assertEqual(device.budget_reason(0,512*1024**2,120.00001),'case_or_load_window_above_120_seconds')

    def test_stopped_host_cannot_accept_complete_report(self):
        self.host['stopReason']='sampled_rss_above_1_5_gib'
        self.assertFalse(self.evaluate()['engineeringQualityAccepted'])

    def test_nonterminal_persisted_zero_is_not_an_exact_decode_count(self):
        self.report.update(terminal=False,status='running',nativeDecodeCalls=0,streamsCreated=1)
        for row in self.report['rows']:
            row.update(status='planned',caseState=None,caseNanos=None)
        self.report['rows'][0].update(status='running',caseState={
            'status':'running','stages':[{'createReturned':True,'decodeAttempts':0}]})
        self.host['stopReason']='sampled_rss_above_1_5_gib'
        result=self.evaluate()
        self.assertIsNone(result['actualDecodeCallsObserved'])
        self.assertIsNone(result['actualCreatedStreamsObserved'])
        self.assertFalse(result['countsAreExactTerminalEvidence'])
        self.assertEqual(result['lastPersistedDecodeCalls'],0)
        self.assertEqual(result['lastPersistedCreatedStreams'],1)
        self.assertEqual(result['completedConsistentRows'],0)

    def test_inflight_case_never_scores_as_empty(self):
        row=self.report['rows'][0];row.update(status='running',caseState=None,caseNanos=None)
        r=self.evaluate();self.assertFalse(r['executionAccepted'])
        self.assertEqual(r['cohorts']['mandarin']['scored'],19)
        self.assertTrue(r['cohorts']['mandarin']['scoresAreSubsetOnly'])
        self.assertFalse(r['cohorts']['mandarin']['engineeringGatePassed'])

    def test_nonempty_silence_and_rejected_speech_cannot_accept_quality(self):
        for index in (0,64):
            with self.subTest(index=index):
                original=copy.deepcopy(self.report)
                s=self.report['rows'][index]['caseState'];s['rawText']='wrong'
                self.assertFalse(self.evaluate()['engineeringQualityAccepted']);self.report=original

    def test_binding_release_counter_and_terminal_failures_rejected(self):
        mutations=[lambda r:r.update(terminal=False), lambda r:r.update(nativeDecodeCalls=130),
                   lambda r:r.update(recognizerReleaseSucceeded=False), lambda r:r.update(wrapperPersistErrorClass='IO'),
                   lambda r:r['rows'][0].update(reference='changed'),
                   lambda r:r['rows'][0]['caseState']['stages'][0].update(releaseSucceeded=False),
                   lambda r:r['rows'][0]['caseState']['stages'][0].update(language='English'),
                   lambda r:r['rows'][0].update(caseNanos=120_000_000_001),
                   lambda r:r['modelsVerified'][0].update(sha256='0'*64),
                   lambda r:r['config'].update(numThreads=4)]
        for change in mutations:
            with self.subTest(change=change):
                original=copy.deepcopy(self.report);change(self.report)
                self.assertFalse(self.evaluate()['executionAccepted']);self.report=original


if __name__=='__main__':
    unittest.main(verbosity=2)
