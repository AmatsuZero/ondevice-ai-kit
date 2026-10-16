import tempfile
import unittest
from pathlib import Path

from tools.probes.p9_fixture import write_fixture


class FixtureTests(unittest.TestCase):
    def test_fixture_is_deterministic_png_with_expected_dimensions(self):
        with tempfile.TemporaryDirectory() as root:
            first = Path(root) / "first.png"
            second = Path(root) / "second.png"
            write_fixture(first)
            write_fixture(second)
            data = first.read_bytes()
            self.assertEqual(data, second.read_bytes())
            self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
            self.assertEqual(int.from_bytes(data[16:20], "big"), 64)
            self.assertEqual(int.from_bytes(data[20:24], "big"), 64)


if __name__ == "__main__":
    unittest.main()
