"""Catch historical contamination, post-selection substitutions and evidence tampering."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

TOOLS = Path(__file__).resolve().parents[1]
SELECTOR = TOOLS / "w6_video_selection.py"
VERIFIER = TOOLS / "verify_w6_video_selection.py"
EXPECTED = [
    "video_1", "video_11", "video_20", "video_2", "video_12", "video_21",
    "video_100", "video_110", "video_122", "video_101", "video_112", "video_121",
    "video_201", "video_211", "video_220", "video_202", "video_210", "video_221",
    "video_302", "video_310", "video_322", "video_301", "video_312", "video_321",
    "video_401", "video_411", "video_420", "video_400", "video_410", "video_421",
    "video_501", "video_512", "video_520", "video_500", "video_511", "video_521",
]


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False) + "\n")
    return {"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}


class FreshVideoSelectionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        annotations, cuts, members = {}, {}, []
        categories = ["counterfactual", "memory", "abstraction", "physics", "semantics", "camera_motion"]
        for c, category in enumerate(categories):
            for label in range(3):
                for j in range(3):
                    vid = f"video_{c * 100 + label * 10 + j}"
                    annotations[vid] = {
                        "metadata": {"split": "valid", "video_id": vid, "num_frames": 300,
                                     "frame_rate": 30, "resolution": [720, 1280]},
                        "mc_question": [{"id": 0, "answer_id": label,
                                         "question": "Is the camera moving or static?" if category == "camera_motion" else f"Question {vid}",
                                         "options": ["one", "two", "three"],
                                         "area": category if category in ("memory", "abstraction", "physics") else "semantics",
                                         "reasoning": "counterfactual" if category == "counterfactual" else "descriptive",
                                         "tag": []}],
                    }
                    cuts[vid] = -1
                    members.append({"name": f"videos/{vid}.mp4", "bytes": 1000, "compressedBytes": 900,
                                    "crc32": 123, "method": 12, "flags": 0, "headerOffset": len(members) * 1100})
        self.annotations = annotations
        self.cuts = cuts
        self.index = {"url": "https://example.test/valid.zip", "archiveBytes": 1000000,
                      "etag": "test", "generation": "1", "members": members}
        self.history = {"items": [{"videoId": "video_9000"}], "trainItems": [{"videoId": "video_9001"}]}
        self.history_ref = write(self.root / "history.json", self.history)
        self.exclusions = {"schemaVersion": 1, "videoIds": ["video_9000", "video_9001"],
                           "sources": [{**self.history_ref, "rowKeys": ["items", "trainItems"]}]}
        self.contract = {"schemaVersion": 1, "sources": {
            "annotations": write(self.root / "annotations.json", annotations),
            "cutMapping": write(self.root / "cuts.json", cuts),
            "zipIndex": write(self.root / "index.json", self.index),
            "exclusions": write(self.root / "exclusions.json", self.exclusions),
        }}
        self.contract_path = self.root / "contract.json"
        write(self.contract_path, self.contract)
        self.output = self.root / "output"

    def run_cli(self, script, *args):
        return subprocess.run([sys.executable, str(script), "--contract", str(self.contract_path), *map(str, args)],
                              capture_output=True, text=True)

    def prepare(self):
        result = self.run_cli(SELECTOR, "--output", self.output)
        self.assertEqual(result.returncode, 0, result.stderr)
        return json.loads((self.output / "selection.json").read_text())

    def verify(self):
        return self.run_cli(VERIFIER, "--selection", self.output / "selection.json")

    def repin(self, key, value):
        self.contract["sources"][key] = write(Path(self.contract["sources"][key]["path"]), value)
        write(self.contract_path, self.contract)

    def test_selects_hand_checked_order_and_independent_verifier_accepts(self):
        selection = self.prepare()
        self.assertEqual([row["videoId"] for row in selection["items"]], EXPECTED)
        self.assertEqual(selection["selectedRawBytes"], 36000)
        self.assertEqual(selection["selectedCompressedBytes"], 32400)
        self.assertEqual([x["answerLetter"] for x in selection["items"]], list("ABCABC" * 6))
        result = self.verify()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["status"], "passed")

    def test_historical_video_is_excluded_even_when_it_has_best_rank(self):
        self.history["items"].append({"videoId": "video_1"})
        ref = write(self.root / "history.json", self.history)
        self.exclusions["sources"][0].update(ref)
        self.exclusions["videoIds"] = sorted([*self.exclusions["videoIds"], "video_1"])
        self.repin("exclusions", self.exclusions)
        self.assertNotIn("video_1", [x["videoId"] for x in self.prepare()["items"]])

    def test_rejects_changed_source_without_creating_output(self):
        (self.root / "annotations.json").write_text("{}")
        self.assertNotEqual(self.run_cli(SELECTOR, "--output", self.output).returncode, 0)
        self.assertFalse(self.output.exists())

    def test_source_digest_and_parsing_use_the_same_read(self):
        # A concurrent replacement after the hashed read must not become input.
        for script, function in ((SELECTOR, "pinned"), (VERIFIER, "source")):
            with self.subTest(script=script.name):
                spec = importlib.util.spec_from_file_location(script.stem, script)
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)
                path = self.root / "changing.json"
                ref = write(path, {"version": "pinned"})
                original = Path.read_bytes

                def replace_after_read(target):
                    raw = original(target)
                    if target == path:
                        path.write_text('{"version":"replacement"}')
                    return raw

                with patch.object(Path, "read_bytes", replace_after_read):
                    self.assertEqual(getattr(module, function)(ref), {"version": "pinned"})

    def test_refuses_existing_directory_without_changing_its_bytes(self):
        self.output.mkdir()
        sentinel = self.output / "selection.json"
        sentinel.write_bytes(b"protected")
        self.assertNotEqual(self.run_cli(SELECTOR, "--output", self.output).returncode, 0)
        self.assertEqual(sentinel.read_bytes(), b"protected")

    def test_rejects_incomplete_exclusion_union(self):
        self.exclusions["videoIds"].pop()
        self.repin("exclusions", self.exclusions)
        self.assertNotEqual(self.run_cli(SELECTOR, "--output", self.output).returncode, 0)

    def test_rejects_duplicate_exclusions(self):
        self.exclusions["videoIds"].append("video_9000")
        self.repin("exclusions", self.exclusions)
        self.assertNotEqual(self.run_cli(SELECTOR, "--output", self.output).returncode, 0)

    def test_rejects_integer_items_summary_as_history_rows(self):
        self.history["items"] = 2
        self.exclusions["sources"][0].update(write(self.root / "history.json", self.history))
        self.repin("exclusions", self.exclusions)
        self.assertNotEqual(self.run_cli(SELECTOR, "--output", self.output).returncode, 0)

    def test_rejects_insufficient_pool_instead_of_substituting_category(self):
        self.cuts.update({f"video_{j}": 0 for j in range(3)})
        self.repin("cutMapping", self.cuts)
        self.assertNotEqual(self.run_cli(SELECTOR, "--output", self.output).returncode, 0)
        self.assertFalse(self.output.exists())

    def test_nonnegative_cut_is_not_selected(self):
        self.cuts["video_1"] = 0
        self.repin("cutMapping", self.cuts)
        self.assertNotIn("video_1", [x["videoId"] for x in self.prepare()["items"]])

    def test_rejects_duplicate_archive_members(self):
        self.index["members"].append(copy.deepcopy(self.index["members"][0]))
        self.repin("zipIndex", self.index)
        self.assertNotEqual(self.run_cli(SELECTOR, "--output", self.output).returncode, 0)

    def test_rejects_unknown_contract_schema(self):
        self.contract["schemaVersion"] = 2
        write(self.contract_path, self.contract)
        self.assertNotEqual(self.run_cli(SELECTOR, "--output", self.output).returncode, 0)

    def test_verifier_rejects_tampered_evidence_fields(self):
        original = self.prepare()
        mutations = [
            ("historical video", lambda d: d["items"][0].update(videoId="video_9000")),
            ("duplicate video", lambda d: d["items"][1].update(videoId=d["items"][0]["videoId"])),
            ("label", lambda d: d["items"][0].update(answerIndex=2)),
            ("cut", lambda d: d["items"][0].update(cutFrame=1)),
            ("question", lambda d: d["items"][0].update(question="changed")),
            ("rank", lambda d: d["items"][0].update(selectionRankSHA256="0" * 64)),
            ("order", lambda d: d["items"].reverse()),
            ("total", lambda d: d.update(selectedRawBytes=1)),
            ("seed", lambda d: d.update(selectionSeed="other")),
            ("source", lambda d: d["sourceHashes"].update(annotations="0" * 64)),
            ("exclusions", lambda d: d.update(excludedPreviousVideoIds=[])),
            ("policy", lambda d: d["policy"].update(noSubstituteOnFailure=False)),
        ]
        for label, mutate in mutations:
            with self.subTest(label=label):
                value = copy.deepcopy(original)
                mutate(value)
                write(self.output / "selection.json", value)
                self.assertNotEqual(self.verify().returncode, 0)

    def test_verifier_rejects_consistent_but_lower_ranked_replacement(self):
        selection = self.prepare()
        # video_0 is eligible in the same bucket, but video_1 wins its fixed rank.
        row = selection["items"][0]
        row.update(videoId="video_0", question="Question video_0",
                   archiveMember="videos/video_0.mp4", archiveHeaderOffset=0,
                   id="pt-fresh36-video_0-q0",
                   selectionRankSHA256=hashlib.sha256(
                       b"w6-qwen35-4b-fresh36-v1|counterfactual|0|video_0|0").hexdigest())
        write(self.output / "selection.json", selection)
        self.assertNotEqual(self.verify().returncode, 0)


if __name__ == "__main__":
    unittest.main()
