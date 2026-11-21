import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from tools.validate_p11_readiness import ReadinessValidationError, validate_summary


class P11ReadinessValidationTests(unittest.TestCase):
    def make_fixture(self):
        temp = tempfile.TemporaryDirectory()
        root = Path(temp.name)
        (root / "docs").mkdir()
        (root / "evidence.json").write_text("{}\n", encoding="utf-8")
        source = root / "docs" / "ROADMAP.md"
        source.write_text("P11\n", encoding="utf-8")
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        summary = {
            "schemaVersion": 1,
            "p11Stop": "Do not publish while gaps remain.",
            "requiredBeforeP11": ["freeze candidates"],
            "rows": [
                {
                    "capability": "demo",
                    "status": "pass-bounded",
                    "evidence": ["evidence.json"],
                    "unverified": ["minimum OS"],
                }
            ],
            "sourceHashes": {
                "docs/ROADMAP.md": digest,
                "evidence.json": hashlib.sha256(b"{}\n").hexdigest(),
            },
            "p11Gate": {
                "status": "open",
                "rowsWithUnverifiedGaps": 1,
                "rowsNotTerminalPass": 1,
                "finalReleaseConclusion": "not-ready",
            },
        }
        return temp, root, summary

    def test_validates_open_gate_with_unverified_rows(self):
        temp, root, summary = self.make_fixture()
        self.addCleanup(temp.cleanup)

        result = validate_summary(summary, root)

        self.assertEqual(result["rowsWithUnverifiedGaps"], 1)
        self.assertEqual(result["rowsNotTerminalPass"], 1)
        self.assertEqual(result["sourceHashesChecked"], 2)

    def test_rejects_evidence_without_a_source_hash(self):
        temp, root, summary = self.make_fixture()
        self.addCleanup(temp.cleanup)
        summary["sourceHashes"].pop("evidence.json")

        with self.assertRaisesRegex(ReadinessValidationError, "unhashed evidence"):
            validate_summary(summary, root)

    def test_rejects_changed_evidence_contents(self):
        temp, root, summary = self.make_fixture()
        self.addCleanup(temp.cleanup)
        (root / "evidence.json").write_text('{"changed": true}\n', encoding="utf-8")

        with self.assertRaisesRegex(ReadinessValidationError, "source hash mismatch"):
            validate_summary(summary, root)

    def test_rejects_stale_source_hash(self):
        temp, root, summary = self.make_fixture()
        self.addCleanup(temp.cleanup)
        summary["sourceHashes"]["docs/ROADMAP.md"] = "0" * 64

        with self.assertRaisesRegex(ReadinessValidationError, "source hash mismatch"):
            validate_summary(summary, root)

    def test_rejects_missing_evidence_path(self):
        temp, root, summary = self.make_fixture()
        self.addCleanup(temp.cleanup)
        summary["rows"][0]["evidence"] = ["missing.json"]

        with self.assertRaisesRegex(ReadinessValidationError, "missing evidence"):
            validate_summary(summary, root)

    def test_rejects_inconsistent_gate_counts(self):
        temp, root, summary = self.make_fixture()
        self.addCleanup(temp.cleanup)
        summary["p11Gate"]["rowsWithUnverifiedGaps"] = 0

        with self.assertRaisesRegex(ReadinessValidationError, "rowsWithUnverifiedGaps"):
            validate_summary(summary, root)

    def test_rejects_ready_conclusion_for_nonterminal_row_without_gap_text(self):
        temp, root, summary = self.make_fixture()
        self.addCleanup(temp.cleanup)
        summary["rows"][0]["status"] = "pass-bounded"
        summary["rows"][0]["unverified"] = []
        summary["p11Gate"] = {
            "status": "open",
            "rowsWithUnverifiedGaps": 0,
            "rowsNotTerminalPass": 1,
            "finalReleaseConclusion": "ready",
        }

        with self.assertRaisesRegex(ReadinessValidationError, "not-ready"):
            validate_summary(summary, root)

    def test_requires_explicit_stop_contract(self):
        temp, root, summary = self.make_fixture()
        self.addCleanup(temp.cleanup)
        summary["p11Stop"] = "Do not publish while gaps remain."
        summary["requiredBeforeP11"] = ["freeze candidates"]

        summary_without_stop = dict(summary)
        summary_without_stop.pop("p11Stop")
        with self.assertRaisesRegex(ReadinessValidationError, "p11Stop"):
            validate_summary(summary_without_stop, root)

        summary_without_requirements = dict(summary)
        summary_without_requirements["requiredBeforeP11"] = []
        with self.assertRaisesRegex(ReadinessValidationError, "requiredBeforeP11"):
            validate_summary(summary_without_requirements, root)


if __name__ == "__main__":
    unittest.main()
