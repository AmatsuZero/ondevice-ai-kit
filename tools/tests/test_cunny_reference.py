import math
import unittest
from pathlib import Path

from tools.probes.cunny_reference import upscale


_METAL_PATH = (
    Path(__file__).resolve().parents[2]
    / "examples/android-realtime-sr/reference/CuNNy.metal"
)


def _to_uint8(value):
    clipped = min(1.0, max(0.0, value))
    return int(round(clipped * 255.0))


def _flat_index_y(x):
    return x


def _bilinear_pixel_center(samples, scale=2):
    height = len(samples)
    width = len(samples[0])
    out_h = height * scale
    out_w = width * scale
    result = []
    for oy in range(out_h):
        sy = (oy + 0.5) / scale - 0.5
        y0 = math.floor(sy)
        wy = sy - y0
        ya = min(max(y0, 0), height - 1)
        yb = min(max(y0 + 1, 0), height - 1)
        row = []
        for ox in range(out_w):
            sx = (ox + 0.5) / scale - 0.5
            x0 = math.floor(sx)
            wx = sx - x0
            xa = min(max(x0, 0), width - 1)
            xb = min(max(x0 + 1, 0), width - 1)
            top = samples[ya][xa] * (1.0 - wx) + samples[ya][xb] * wx
            bottom = samples[yb][xa] * (1.0 - wx) + samples[yb][xb] * wx
            row.append(top * (1.0 - wy) + bottom * wy)
        result.append(row)
    return result


_SAMPLES_3X4 = [
    [0.0, 0.2, 0.4, 1.0],
    [0.1, 0.5, 0.3, 0.9],
    [0.8, 0.6, 0.05, 0.7],
]


class TestCunnyReference(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with _METAL_PATH.open("r", encoding="utf-8") as handle:
            cls.metal = handle.read()

    def _assert_valid(self, result, expected_h, expected_w):
        self.assertEqual(len(result), expected_h)
        for row in result:
            self.assertEqual(len(row), expected_w)
            for value in row:
                self.assertTrue(math.isfinite(value))
                self.assertGreaterEqual(value, 0.0)
                self.assertLessEqual(value, 1.0)

    def test_flat_zero_rounds_to_black(self):
        samples = [[0.0, 0.0, 0.0] for _ in range(3)]
        result = upscale(self.metal, samples)

        self._assert_valid(result, 6, 6)
        for row in result:
            for value in row:
                self.assertEqual(_to_uint8(value), 0)

    def test_nonflat_asymmetric_differs_from_bilinear(self):
        result = upscale(self.metal, _SAMPLES_3X4)

        self._assert_valid(result, 6, 8)

        reference = _bilinear_pixel_center(_SAMPLES_3X4, scale=2)
        differs = any(
            _to_uint8(result[i][j]) != _to_uint8(reference[i][j])
            for i in range(6)
            for j in range(8)
        )
        self.assertTrue(differs)

    def test_deterministic_repeat(self):
        first = upscale(self.metal, _SAMPLES_3X4)
        second = upscale(self.metal, _SAMPLES_3X4)
        self.assertEqual(first, second)

    def test_ragged_input_raises_value_error(self):
        samples = [
            [0.0, 0.1, 0.2],
            [0.3, 0.4],
            [0.5, 0.6, 0.7],
        ]
        with self.assertRaises(ValueError):
            upscale(self.metal, samples)


if __name__ == "__main__":
    unittest.main()
