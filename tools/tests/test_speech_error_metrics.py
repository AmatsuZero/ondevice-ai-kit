import copy
import hashlib
import importlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


try:
    metrics = importlib.import_module("tools.speech_error_metrics")
except ModuleNotFoundError as exc:
    if exc.name != "tools.speech_error_metrics":
        raise
    metrics = None


class SpeechErrorMetricsTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(metrics, "speech evaluator missing")

    def pair(self, reference, hypothesis, **options):
        return metrics.score_pair(reference, hypothesis, **options)

    def corpus(self, pairs, **options):
        return metrics.score_corpus(pairs, **options)

    def test_exact_match_has_zero_errors(self):
        result = self.pair("we speak clearly", "we speak clearly")
        for metric in ("word", "character"):
            self.assertEqual(result[metric]["errors"], 0)
            self.assertEqual(result[metric]["rate"], 0)
            self.assertTrue(result[metric]["defined"])

    def test_single_substitution(self):
        self.assertEqual(self.pair("red blue", "red green")["word"]["substitutions"], 1)

    def test_single_deletion(self):
        self.assertEqual(self.pair("a b c", "a c")["word"]["deletions"], 1)

    def test_single_insertion(self):
        self.assertEqual(self.pair("a b", "a x b")["word"]["insertions"], 1)

    def test_traceback_prefers_diagonal_on_equal_cost(self):
        word = self.pair("a b", "b a")["word"]
        self.assertEqual((word["substitutions"], word["deletions"], word["insertions"]), (2, 0, 0))

    def test_english_words_split_on_whitespace(self):
        result = self.pair("one two", "onetwo")
        self.assertEqual(result["word"]["referenceUnits"], 2)
        self.assertEqual(result["word"]["errors"], 2)
        self.assertEqual(result["character"]["errors"], 0)

    def test_chinese_uses_codepoints_for_character_error(self):
        result = self.pair("你好世界", "你好世")
        self.assertEqual(result["character"]["referenceUnits"], 4)
        self.assertEqual(result["character"]["deletions"], 1)
        self.assertEqual(result["word"]["substitutions"], 1)

    def test_canonical_unicode_equivalents_match(self):
        result = self.pair("café", "cafe\u0301")
        self.assertEqual(result["character"]["errors"], 0)
        self.assertEqual(result["word"]["errors"], 0)

    def test_punctuation_is_not_removed(self):
        result = self.pair("hello!", "hello")
        self.assertEqual(result["word"]["substitutions"], 1)
        self.assertEqual(result["character"]["deletions"], 1)

    def test_accents_and_digits_are_not_removed(self):
        result = self.pair("café 123", "cafe 124")
        self.assertEqual(result["character"]["substitutions"], 2)

    def test_case_is_preserved_by_default(self):
        self.assertEqual(self.pair("Hello", "hello")["character"]["substitutions"], 1)

    def test_casefold_is_opt_in(self):
        result = self.pair("HELLO", "hello", casefold=True)
        self.assertEqual(result["word"]["errors"], 0)
        self.assertEqual(result["character"]["errors"], 0)

    def test_casefold_does_not_remove_punctuation(self):
        self.assertEqual(self.pair("HELLO!", "hello?", casefold=True)["character"]["errors"], 1)

    def test_unicode_whitespace_is_collapsed_for_words_and_ignored_for_characters(self):
        result = self.pair("\u00a0mañana\u2003世界\n", "mañana 世界")
        self.assertEqual(result["word"]["errors"], 0)
        self.assertEqual(result["character"]["errors"], 0)
        self.assertEqual(result["character"]["referenceUnits"], 8)

    def test_empty_reference_and_hypothesis_is_undefined(self):
        result = self.pair("", "")
        for metric in ("word", "character"):
            self.assertFalse(result[metric]["defined"])
            self.assertIsNone(result[metric]["rate"])
            self.assertEqual(result[metric]["errors"], 0)

    def test_empty_reference_retains_insertions_and_undefined_rate(self):
        result = self.pair("", "extra words")
        self.assertFalse(result["word"]["defined"])
        self.assertIsNone(result["word"]["rate"])
        self.assertEqual(result["word"]["insertions"], 2)
        self.assertEqual(result["word"]["errors"], 2)

    def test_rate_can_exceed_one(self):
        self.assertEqual(self.pair("a", "a b c")["word"]["rate"], 2)

    def test_corpus_uses_micro_average_not_mean_of_row_rates(self):
        pairs = [
            {"id": "short", "reference": "a", "hypothesis": ""},
            {
                "id": "long",
                "reference": "one two three four five six seven eight nine",
                "hypothesis": "one two three four five six seven eight",
            },
        ]
        result = self.corpus(pairs)
        self.assertEqual(result["aggregate"]["word"]["referenceUnits"], 10)
        self.assertEqual(result["aggregate"]["word"]["errors"], 2)
        self.assertEqual(result["aggregate"]["word"]["rate"], 0.2)

    def test_corpus_empty_transcripts_keep_undefined_aggregate(self):
        result = self.corpus([{"id": "empty", "reference": "", "hypothesis": ""}])
        self.assertFalse(result["aggregate"]["word"]["defined"])
        self.assertIsNone(result["aggregate"]["word"]["rate"])
        self.assertFalse(result["rows"][0]["word"]["defined"])

    def test_corpus_rejects_empty_pairs_list(self):
        with self.assertRaises(ValueError):
            self.corpus([])

    def test_corpus_rejects_duplicate_ids(self):
        pairs = [
            {"id": "same", "reference": "a", "hypothesis": "a"},
            {"id": "same", "reference": "b", "hypothesis": "b"},
        ]
        with self.assertRaises(ValueError):
            self.corpus(pairs)

    def test_input_pairs_are_not_mutated(self):
        pairs = [{"id": "x", "reference": "  A  ", "hypothesis": "A"}]
        original = copy.deepcopy(pairs)
        self.corpus(pairs, casefold=True)
        self.assertEqual(pairs, original)

    def test_corpus_hashes_original_un_normalized_strings(self):
        reference = "  cafe\u0301 \n"
        hypothesis = "CAFÉ"
        row = self.corpus(
            [{"id": "hash-case", "reference": reference, "hypothesis": hypothesis}]
        )["rows"][0]
        self.assertEqual(row["referenceSHA256"], hashlib.sha256(reference.encode("utf-8")).hexdigest())
        self.assertEqual(
            row["hypothesisSHA256"], hashlib.sha256(hypothesis.encode("utf-8")).hexdigest()
        )

    def test_corpus_report_has_required_status_and_no_transcripts(self):
        reference = "secret-reference-7f2a"
        hypothesis = "secret-hypothesis-9c31"
        result = self.corpus(
            [{"id": "private-id", "reference": reference, "hypothesis": hypothesis}]
        )
        self.assertEqual(result["schemaVersion"], 1)
        self.assertEqual(result["kind"], "speech-error-metrics")
        self.assertEqual(result["qualityVerdict"], "unverified")
        self.assertFalse(result["productionReady"])
        self.assertIsInstance(result["normalization"], dict)
        self.assertEqual(
            set(result["rows"][0]),
            {"id", "word", "character", "referenceSHA256", "hypothesisSHA256"},
        )
        serialized = json.dumps(result, ensure_ascii=False)
        self.assertNotIn(reference, serialized)
        self.assertNotIn(hypothesis, serialized)

    def test_pair_rejects_non_exact_string_and_boolean_types(self):
        class Text(str):
            pass

        with self.assertRaises(ValueError):
            self.pair(Text("a"), "a")
        with self.assertRaises(ValueError):
            self.pair("a", "a", casefold=1)

    def test_pair_rejects_surrogate_codepoints(self):
        with self.assertRaises(ValueError):
            self.pair("\ud800", "")

    def test_corpus_rejects_invalid_container_and_pair_keys(self):
        with self.assertRaises(ValueError):
            self.corpus(({"id": "x", "reference": "a", "hypothesis": "a"},))
        with self.assertRaises(ValueError):
            self.corpus([{"id": "x", "reference": "a", "hypothesis": "a", "extra": 1}])
        with self.assertRaises(ValueError):
            self.corpus([{"id": "x", "reference": "a"}])

    def test_corpus_requires_exact_boolean_option_and_pair_dict(self):
        class Pair(dict):
            pass

        with self.assertRaises(ValueError):
            self.corpus([{"id": "x", "reference": "a", "hypothesis": "a"}], casefold=1)
        with self.assertRaises(ValueError):
            self.corpus([Pair(id="x", reference="a", hypothesis="a")])

    def test_corpus_rejects_invalid_ids_and_field_types(self):
        for pair in (
            {"id": " ", "reference": "a", "hypothesis": "a"},
            {"id": "x", "reference": 3, "hypothesis": "a"},
            {"id": "x", "reference": "a", "hypothesis": False},
        ):
            with self.subTest(pair=pair), self.assertRaises(ValueError):
                self.corpus([pair])

    def run_cli(self, raw_input):
        with tempfile.TemporaryDirectory() as directory:
            input_path = Path(directory) / "input.json"
            input_path.write_bytes(raw_input)
            return subprocess.run(
                [
                    sys.executable,
                    "-m",
                    "tools.speech_error_metrics",
                    str(input_path),
                ],
                capture_output=True,
                text=True,
                check=False,
            )

    def test_cli_success_includes_hash_of_exact_input_bytes(self):
        raw_input = b'{"schemaVersion":1, "pairs":[{"id":"x","reference":"a","hypothesis":"a"}]}\n'
        completed = self.run_cli(raw_input)
        self.assertEqual(completed.returncode, 0, completed.stderr)
        result = json.loads(completed.stdout)
        self.assertEqual(result["inputSHA256"], hashlib.sha256(raw_input).hexdigest())
        self.assertEqual(result["kind"], "speech-error-metrics")

    def test_cli_invalid_json_schemas_fail_without_stdout(self):
        invalid_inputs = (
            b'{"schemaVersion":1,"schemaVersion":1,"pairs":[]}',
            b'{"schemaVersion":1,"pairs":[{"id":"x","reference":"a","hypothesis":NaN}]}',
            b'{"schemaVersion":true,"pairs":[]}',
            b'{"schemaVersion":1.0,"pairs":[]}',
            b'{"schemaVersion":1,"pairs":[],"extra":0}',
            b'{"schemaVersion":1,"pairs":[{"id":"x","reference":"a","hypothesis":"a"},{"id":"x","reference":"b","hypothesis":"b"}]}',
        )
        for raw_input in invalid_inputs:
            with self.subTest(raw_input=raw_input):
                completed = self.run_cli(raw_input)
                self.assertNotEqual(completed.returncode, 0)
                self.assertEqual(completed.stdout, "")
                self.assertTrue(completed.stderr.strip())


if __name__ == "__main__":
    unittest.main()
