import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).parents[1] / "probes"))
import video_sr_quality


class VideoSRQualityTests(unittest.TestCase):
    def test_different_container_timebases_compare_each_frame_once(self):
        with tempfile.TemporaryDirectory() as directory:
            reference = Path(directory) / "reference.mkv"
            output = Path(directory) / "output.mp4"
            subprocess.run([
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi",
                "-i", "testsrc2=size=64x64:rate=24", "-frames:v", "48", "-an",
                "-c:v", "ffv1", "-pix_fmt", "yuv420p", str(reference),
            ], check=True)
            subprocess.run([
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(reference),
                "-vf", "setpts=PTS+0.04/TB", "-c:v", "libx264", "-crf", "0",
                "-video_track_timescale", "600", "-an", str(output),
            ], check=True)
            report = video_sr_quality.compare(reference, output)
            self.assertEqual(48, report["comparedFrames"])
            self.assertEqual(1.0, report["metrics"]["ssim"])
            self.assertTrue(report["metrics"]["psnrInfinite"])
            self.assertIsNone(report["metrics"]["psnrDb"])
            self.assertEqual("unverified", report["colorComparability"])
            json.dumps(report, allow_nan=False)

    def test_missing_frames_are_rejected_before_scoring(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / f"video-{count}.mkv" for count in (48, 47)]
            for path, count in zip(paths, (48, 47)):
                subprocess.run([
                    "ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi",
                    "-i", "testsrc2=size=64x64:rate=24", "-frames:v", str(count),
                    "-an", "-c:v", "ffv1", str(path),
                ], check=True)
            with self.assertRaisesRegex(ValueError, "frame count"):
                video_sr_quality.compare(*paths)


if __name__ == "__main__":
    unittest.main()
