import hashlib
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from tools.probes.natural_video_temporal_fixture import (
    prepare_temporal_fixture,
    validate_temporal_manifest,
)


def identity(path: Path) -> dict:
    data = path.read_bytes()
    return {
        "bytes": len(data),
        "sourceSha1": hashlib.sha1(data).hexdigest(),
        "sourceSha256": hashlib.sha256(data).hexdigest(),
    }


def make_source(path: Path, *, seconds: int = 6) -> dict:
    subprocess.run(
        [
            "ffmpeg", "-hide_banner", "-loglevel", "error",
            "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=24",
            "-t", str(seconds), "-an", "-c:v", "ffv1", "-pix_fmt", "yuv420p",
            "-color_range", "tv", "-colorspace", "bt709", "-color_trc", "bt709",
            "-color_primaries", "bt709", "-y", str(path),
        ], check=True,
    )
    return identity(path)


class TemporalFixtureTests(unittest.TestCase):
    def manifest(self, source: Path) -> dict:
        return {
            "schemaVersion": 1,
            "source": identity(source),
            "windows": [
                {"taskId": "motion-0-2", "pairId": "pair-0", "role": "motion", "start": 0, "end": 2},
                {"taskId": "motion-2-4", "pairId": "pair-1", "role": "motion", "start": 2, "end": 4},
                {"taskId": "motion-4-6", "pairId": "pair-2", "role": "motion", "start": 4, "end": 6},
            ],
        }

    def test_prepare_derives_three_motion_windows_and_controls(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            source = root / "source.mkv"
            source_identity = make_source(source)
            result = prepare_temporal_fixture(source, root / "prepared", self.manifest(source))
            self.assertEqual(result["source"], source_identity)
            self.assertEqual([item["taskId"] for item in result["derivatives"]], [
                "motion-0-2", "frozen-0-2", "motion-2-4", "frozen-2-4", "motion-4-6", "frozen-4-6",
            ])
            self.assertTrue(all(item["metadata"]["frames"] == 48 for item in result["derivatives"]))
            self.assertTrue(all(item["metadata"]["width"] == 320 for item in result["derivatives"]))
            self.assertTrue(all(item["metadata"]["height"] == 180 for item in result["derivatives"]))
            self.assertTrue(all(item["metadata"]["fps"] == "24/1" for item in result["derivatives"]))
            self.assertTrue(all(item["metadata"]["hasBFrames"] == 0 for item in result["derivatives"]))
            self.assertTrue(all(item["metadata"]["audioStreams"] == 0 for item in result["derivatives"]))
            controls = [item for item in result["derivatives"] if item["role"] == "frozen-control"]
            self.assertEqual([item["controlOf"] for item in controls], ["motion-0-2", "motion-2-4", "motion-4-6"])
            motions = {item["taskId"]: item for item in result["derivatives"] if item["role"] == "motion"}
            self.assertTrue(all(item["decodedUniqueFrameCount"] == 1 for item in controls))
            self.assertTrue(all(
                item["decodedFirstFrameSha256"] == motions[item["controlOf"]]["decodedFirstFrameSha256"]
                for item in controls
            ))
            self.assertEqual(validate_temporal_manifest(root / "prepared" / "manifest.json", root / "prepared"), result)

    def test_source_hash_drift_and_destination_collision_are_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            source = root / "source.mkv"
            make_source(source)
            manifest = self.manifest(source)
            original = source.read_bytes()
            source.write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "source"):
                prepare_temporal_fixture(source, root / "prepared", manifest)
            source.write_bytes(original)
            destination = root / "prepared"
            destination.mkdir()
            with self.assertRaises(FileExistsError):
                prepare_temporal_fixture(source, destination, manifest)

    def test_manifest_rejects_pair_or_metadata_drift(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            source = root / "source.mkv"
            make_source(source)
            prepare_temporal_fixture(source, root / "prepared", self.manifest(source))
            path = root / "prepared" / "manifest.json"
            data = json.loads(path.read_text())
            data["derivatives"][1]["controlOf"] = "motion-2-4"
            path.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, "pair"):
                validate_temporal_manifest(path, root / "prepared")

    def test_manifest_is_deterministic(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            source = root / "source.mkv"
            make_source(source)
            first = prepare_temporal_fixture(source, root / "first", self.manifest(source))
            second = prepare_temporal_fixture(source, root / "second", self.manifest(source))
            self.assertEqual(json.dumps(first, sort_keys=True), json.dumps(second, sort_keys=True))


if __name__ == "__main__":
    unittest.main()
