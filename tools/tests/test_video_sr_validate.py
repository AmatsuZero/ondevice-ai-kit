import unittest
from pathlib import Path
import sys
import subprocess
import tempfile
import hashlib

sys.path.insert(0, str(Path(__file__).parents[1] / "probes"))
import video_sr_validate


class VideoSRValidateTests(unittest.TestCase):
    def test_fixture_includes_four_x_input_and_bicubic_baseline(self):
        with tempfile.TemporaryDirectory() as directory:
            subprocess.run([
                "sh", str(Path(__file__).parents[2] / "tools" / "probes" / "video_sr_fixture.sh"), directory,
            ], check=True, cwd=Path(__file__).parents[2], stdout=subprocess.DEVNULL)
            reference = Path(directory) / "reference-720p.mkv"
            input_four_x = Path(directory) / "input-180p-4x.mkv"
            bicubic_four_x = Path(directory) / "bicubic-720p-4x.mkv"
            self.assertTrue((Path(directory) / "input-180p-4x.mp4").exists())
            video_sr_validate.validate([reference, input_four_x, bicubic_four_x], scale=4)

    def test_decoded_sha256_is_stable_across_separate_mkv_encodes(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / f"sample-{index}.mkv" for index in range(2)]
            for path in paths:
                subprocess.run([
                    "ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi",
                    "-i", "testsrc2=size=64x64:rate=2", "-frames:v", "2", "-an",
                    "-c:v", "ffv1", "-pix_fmt", "yuv420p", "-y", str(path),
                ], check=True)
            self.assertNotEqual(
                hashlib.sha256(paths[0].read_bytes()).hexdigest(),
                hashlib.sha256(paths[1].read_bytes()).hexdigest(),
            )
            self.assertEqual(
                video_sr_validate.decoded_sha256(paths[0]),
                video_sr_validate.decoded_sha256(paths[1]),
            )


if __name__ == "__main__":
    unittest.main()
