import importlib.util, unittest
from pathlib import Path
SPEC = importlib.util.spec_from_file_location("benchmark_report", Path(__file__).resolve().parents[1]/"benchmark_report.py")
if SPEC is None or SPEC.loader is None: raise RuntimeError("missing report module")
report = importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(report)
def run(device="phone",os="15.0",samples=None):
    return dict(schemaVersion=1,runId="run-1",device=device,OS=os,sdkCommit="abc",capability="face",actualBackend="apple.vision.face",modelRevision=None,preprocessRevision="upright-v1",fixtureHash="f"*64,units="ms",timingsMs=dict(coldStart=[],modelLoad=[],warmup=[],inference=[] if samples is None else samples),peakMemory=None,energy=None,energyStatus="unmeasured",thermal=None,measurementMethod="native-monotonic",validationStatus="unit-fixture",quality={})
class ReportTests(unittest.TestCase):
    def testEmptyAndSingleSamplePercentiles(self):
        self.assertIsNone(report.summarize([run()])["groups"][0]["timingsMs"]["inference"]["p50"])
        summary=report.summarize([run(samples=[7])])["groups"][0]
        self.assertEqual(summary["timingsMs"]["inference"]["p95"],7)
        self.assertIsNone(summary["runs"][0]["energy"])
    def testNearestRankUsesLiteralExpectedValues(self):
        summary=report.summarize([run(samples=[1,2,3,4,100])])["groups"][0]["timingsMs"]["inference"]
        self.assertEqual(summary["p50"],3);self.assertEqual(summary["p95"],100);self.assertEqual(summary["count"],5)
    def testDifferentDevicesAndSystemsAreNotMerged(self):
        self.assertEqual(len(report.summarize([run("a"),run("b"),run("a","26")])["groups"]),3)
    def testInvalidSamplesUnitsAndEnergyAreRejected(self):
        for bad in [-1,float('nan'),float('inf'),True]:
            with self.assertRaises(ValueError):report.summarize([run(samples=[bad])])
        invalid=run();invalid['units']='seconds'
        with self.assertRaises(ValueError):report.summarize([invalid])
        invalid=run();invalid['energy']=123
        with self.assertRaises(ValueError):report.summarize([invalid])
if __name__=="__main__":unittest.main()
