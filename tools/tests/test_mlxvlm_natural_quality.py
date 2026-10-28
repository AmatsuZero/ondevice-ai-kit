import json
import tempfile
import unittest
from pathlib import Path

from tools.mlxvlm_natural_quality import (
    QualityIdentity,
    QualityValidationError,
    build_summary,
    normalize_run,
    score_output,
)


IDENTITY = QualityIdentity(
    fixture_sha256="fixture",
    backend="mlx.smolvlm2-500m",
    model_revision="model",
    runtime_revision="runtime",
    prompt="Describe what happens in this video in one brief sentence.",
    expected_runs=1,
)


def raw(run_id="run-1", output="Several birds are flying in a blue sky with clouds."):
    return {
        "runId": run_id,
        "device": "iPhone17,2",
        "OS": "Version 27.0.1",
        "sourceCommit": "abc",
        "actualBackend": "mlx.smolvlm2-500m",
        "modelRevision": "model",
        "runtimeRevision": "runtime",
        "fixtureHash": "fixture",
        "prompt": IDENTITY.prompt,
        "output": output,
        "durationMs": 2100.0,
        "qualityScope": "natural-video-semantic-rubric-v1",
    }


class NaturalQualityTests(unittest.TestCase):
    def test_score_requires_subject_action_and_scene(self):
        score = score_output("Several birds are flying in a blue sky with clouds.")
        self.assertEqual(score["categories"], {"subject": True, "action": True, "scene": True})
        self.assertTrue(score["semanticPass"])

    def test_generic_scene_only_output_fails_missing_categories(self):
        score = score_output("A blue sky with clouds.")
        self.assertFalse(score["semanticPass"])
        self.assertFalse(score["categories"]["subject"])
        self.assertFalse(score["categories"]["action"])

    def test_normalize_rejects_identity_mismatch_and_empty_output(self):
        with self.assertRaisesRegex(QualityValidationError, "fixture"):
            normalize_run({**raw(), "fixtureHash": "wrong"}, expected=IDENTITY)
        with self.assertRaisesRegex(QualityValidationError, "output"):
            normalize_run(raw(output="   "), expected=IDENTITY)
        with self.assertRaisesRegex(QualityValidationError, "backend"):
            normalize_run({**raw(), "actualBackend": "wrong"}, expected=IDENTITY)

    def test_duplicate_run_ids_are_rejected(self):
        with self.assertRaisesRegex(QualityValidationError, "duplicate"):
            build_summary([normalize_run(raw(), expected=IDENTITY), normalize_run(raw(), expected=IDENTITY)], expected=IDENTITY)

    def test_summary_is_deterministic_and_keeps_unverified_dimensions(self):
        run = normalize_run(raw(), expected=IDENTITY)
        first = build_summary([run], expected=IDENTITY)
        second = build_summary([run], expected=IDENTITY)
        self.assertEqual(json.dumps(first, sort_keys=True), json.dumps(second, sort_keys=True))
        self.assertEqual(first["rubricRevision"], "natural-video-semantic-rubric-v1")
        self.assertEqual(first["unverified"], ["naturalTemporalQuality", "gpuEnergy", "sustainedResources"])


if __name__ == "__main__":
    unittest.main()
