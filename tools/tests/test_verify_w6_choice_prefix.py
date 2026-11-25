"""Immediate final-answer constraints must not consume an open reasoning prefix."""
import importlib.util
from pathlib import Path
import unittest


SOURCE = Path(__file__).resolve().parents[1] / 'verify_w6_choice_prefix.py'


class ChoicePrefixTests(unittest.TestCase):
    def check_prefix(self, tokens):
        spec = importlib.util.spec_from_file_location('choice_prefix_guard', SOURCE)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.require_final_answer_prefix(tokens, 100, 101)

    def test_rejects_open_reasoning_prefix(self):
        with self.assertRaisesRegex(ValueError, 'open reasoning'):
            self.check_prefix([7, 100, 8])

    def test_rejects_reopened_reasoning_after_an_earlier_closed_turn(self):
        with self.assertRaisesRegex(ValueError, 'open reasoning'):
            self.check_prefix([100, 8, 101, 9, 100, 8])

    def test_accepts_explicitly_closed_reasoning_prefix(self):
        self.check_prefix([7, 100, 8, 101, 9])

    def test_accepts_prefix_without_reasoning_tokens(self):
        self.check_prefix([7, 8, 9])


if __name__ == '__main__':
    unittest.main()
