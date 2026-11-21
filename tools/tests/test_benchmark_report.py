import importlib.util, json, os, subprocess, sys, tempfile, unittest
from pathlib import Path
SPEC = importlib.util.spec_from_file_location("benchmark_report", Path(__file__).resolve().parents[1]/"benchmark_report.py")
if SPEC is None or SPEC.loader is None: raise RuntimeError("missing report module")
report = importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(report)
def run(device="phone",os="15.0",samples=None,run_id="run-1"):
    return dict(schemaVersion=1,runId=run_id,device=device,OS=os,sdkCommit="abc",capability="face",actualBackend="apple.vision.face",modelRevision=None,preprocessRevision="upright-v1",fixtureHash="f"*64,units="ms",timingsMs=dict(coldStart=[],modelLoad=[],warmup=[],inference=[] if samples is None else samples),peakMemory=None,energy=None,energyStatus="unmeasured",thermal=None,measurementMethod="native-monotonic",validationStatus="unit-fixture",quality={})
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
        self.assertEqual(len(report.summarize([run("a"),run("b",run_id="run-2"),run("a","26",run_id="run-3")])["groups"]),3)
    def testDuplicateRunIdsAreRejectedBeforeGrouping(self):
        for changed in ({}, {"device": "other"}, {"actualBackend": "other.backend"},
                        {"peakMemory": {"bytes": 128, "method": "other-instrument"}},
                        {"timingsMs": {"coldStart": [], "modelLoad": [], "warmup": [], "inference": [900]}}):
            with self.subTest(changed=changed):
                duplicate = run(samples=[1, 2, 3])
                duplicate.update(changed)
                with self.assertRaisesRegex(ValueError, "duplicate runId"):
                    report.summarize([run(samples=[1, 2, 3]), duplicate])
    def testDistinctRunsRetainTheirSamplesAndEvidence(self):
        summary = report.summarize([run(samples=[1, 2, 3]), run(samples=[4, 100], run_id="run-2")])["groups"][0]
        self.assertEqual(summary["timingsMs"]["inference"], {"count": 5, "p50": 3, "p95": 100})
        self.assertEqual([entry["runId"] for entry in summary["runs"]], ["run-1", "run-2"])
        equal = report.summarize([run(samples=[7]), run(samples=[7], run_id="run-2")])["groups"][0]
        self.assertEqual(equal["timingsMs"]["inference"]["count"], 2)
    def testInvalidSamplesUnitsAndEnergyAreRejected(self):
        for bad in [-1,float('nan'),float('inf'),True]:
            with self.assertRaises(ValueError):report.summarize([run(samples=[bad])])
        invalid=run();invalid['units']='seconds'
        with self.assertRaises(ValueError):report.summarize([invalid])
        invalid=run();invalid['energy']=123
        with self.assertRaises(ValueError):report.summarize([invalid])

class ReportCliTests(unittest.TestCase):
    def invoke(self, inputs, output):
        return subprocess.run([sys.executable, str(Path(report.__file__)),
                               *map(str, inputs), "--output", str(output)],
                              capture_output=True, text=True, timeout=10)

    def testDuplicateInputDoesNotReplaceAnExistingReport(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, output = root / "run.json", root / "report.json"
            source.write_text(json.dumps(run(samples=[1, 2, 3])))
            output.write_bytes(b"previous report\n")
            result = self.invoke([source, source], output)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("duplicate runId", result.stderr)
            self.assertEqual(output.read_bytes(), b"previous report\n")

    def testOutputCannotOverwriteInputThroughFileAliases(self):
        for alias in ("same-path", "symlink", "hardlink"):
            with self.subTest(alias=alias), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                source = root / "run.json"
                original = json.dumps(run(samples=[1, 2, 3])).encode()
                source.write_bytes(original)
                output = source if alias == "same-path" else root / "output.json"
                if alias == "symlink":
                    output.symlink_to(source)
                elif alias == "hardlink":
                    os.link(source, output)
                result = self.invoke([source], output)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(source.read_bytes(), original, "Raw evidence must survive rejected output aliases")

    def testCliReportsDistinctRunsAndKeepsRawInputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            inputs = [root / "first.json", root / "second.json"]
            for source, data in zip(inputs, [run(samples=[1, 2, 3]), run(samples=[4, 100], run_id="run-2")]):
                source.write_text(json.dumps(data))
            originals = [source.read_bytes() for source in inputs]
            output = root / "nested" / "report.json"
            result = self.invoke(inputs, output)
            self.assertEqual(result.returncode, 0, result.stderr)
            summary = json.loads(output.read_text())["groups"][0]
            self.assertEqual(summary["timingsMs"]["inference"], {"count": 5, "p50": 3, "p95": 100})
            self.assertEqual([source.read_bytes() for source in inputs], originals)
            # A distinct existing report remains a valid regeneration target.
            result = self.invoke(inputs, output)
            self.assertEqual(result.returncode, 0, result.stderr)

if __name__=="__main__":unittest.main()
