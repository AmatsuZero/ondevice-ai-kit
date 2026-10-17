import hashlib
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.probes.natural_video_fixture import download_source, prepare_fixture, publish_fixture


def source_manifest(path):
    data = path.read_bytes()
    return {"bytes": len(data), "sourceSha1": hashlib.sha1(data).hexdigest(),
            "sourceSha256": hashlib.sha256(data).hexdigest()}


class NaturalVideoFixtureTests(unittest.TestCase):
    def test_download_verifies_sha1_and_does_not_publish_corrupt_asset(self):
        with tempfile.TemporaryDirectory() as root:
            root_path = Path(root)
            source = root_path / "source.ogv"
            source.write_bytes(b"natural-video-fixture")
            manifest = source_manifest(source)
            manifest["url"] = source.as_uri()
            destination = root_path / "downloaded.ogv"
            self.assertEqual(download_source(manifest, destination)["sha256"],
                             hashlib.sha256(source.read_bytes()).hexdigest())
            self.assertEqual(destination.read_bytes(), source.read_bytes())
            destination.unlink()
            manifest["sourceSha1"] = "0" * 40
            with self.assertRaises(ValueError):
                download_source(manifest, destination)
            self.assertEqual(sorted(p.name for p in root_path.iterdir()), ["source.ogv"])

    def test_prepare_fixture_has_48_tagged_frames_and_no_audio(self):
        with tempfile.TemporaryDirectory() as root:
            root_path = Path(root)
            source = root_path / "source.mkv"
            subprocess.run([
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi",
                "-i", "testsrc2=size=1280x720:rate=24", "-t", "2", "-an",
                "-vf", "setparams=range=tv:color_primaries=bt709:color_trc=bt709:colorspace=bt709",
                "-c:v", "ffv1", "-pix_fmt", "yuv420p", "-color_range", "tv",
                "-colorspace", "bt709", "-color_trc", "bt709", "-color_primaries", "bt709",
                "-y", str(source),
            ], check=True)
            result = prepare_fixture(source, root_path / "prepared", source_manifest(source))
            self.assertEqual(result["sourceSha256"], hashlib.sha256(source.read_bytes()).hexdigest())
            self.assertEqual(result["colorBasis"], "source-metadata-bt709")
            self.assertTrue(result["sourceVerified"])
            self.assertEqual(result["sourceManifest"], source_manifest(source))
            for name, width, height in (
                ("reference-720p.mkv", 1280, 720),
                ("input-180p-4x.mp4", 320, 180),
                ("bicubic-720p-4x.mkv", 1280, 720),
            ):
                path = root_path / "prepared" / name
                probe = subprocess.check_output([
                    "ffprobe", "-v", "error", "-count_frames", "-show_streams",
                    "-of", "json", str(path),
                ], text=True)
                streams = json.loads(probe)["streams"]
                self.assertEqual(len(streams), 1, name)
                video = streams[0]
                self.assertEqual((video["width"], video["height"]), (width, height), name)
                self.assertEqual(int(video["nb_read_frames"]), 48, name)
                self.assertEqual(video.get("color_space"), "bt709", name)
                self.assertEqual(video.get("color_transfer"), "bt709", name)
                self.assertEqual(video.get("color_primaries"), "bt709", name)
                self.assertEqual(video.get("color_range"), "tv", name)
            from tools.probes.video_sr_validate import decoded_sha256, validate
            directory = root_path / "prepared"
            validate([directory / "reference-720p.mkv", directory / "input-180p-4x.mkv",
                      directory / "bicubic-720p-4x.mkv"], scale=4)
            self.assertEqual(decoded_sha256(directory / "input-180p-4x.mkv"),
                             decoded_sha256(directory / "input-180p-4x.mp4"))
            with self.assertRaises(FileExistsError):
                prepare_fixture(source, directory, source_manifest(source))

    def test_download_preserves_existing_asset_and_reuses_verified_file_offline(self):
        with tempfile.TemporaryDirectory() as root:
            destination = Path(root) / "source.ogv"
            destination.write_bytes(b"existing")
            manifest = {"url": "http://127.0.0.1:1/unreachable", "bytes": 8,
                        "sourceSha1": hashlib.sha1(b"existing").hexdigest(),
                        "sourceSha256": hashlib.sha256(b"existing").hexdigest()}
            self.assertEqual(download_source(manifest, destination)["bytes"], 8)
            manifest["sourceSha1"] = "0" * 40
            with self.assertRaises(FileExistsError):
                download_source(manifest, destination)
            self.assertEqual(destination.read_bytes(), b"existing")

    def test_download_rejects_wrong_size_and_sha256_without_publishing(self):
        with tempfile.TemporaryDirectory() as root:
            source = Path(root) / "source.ogv"
            source.write_bytes(b"payload")
            for overrides in ({"bytes": 8}, {"bytes": 6}, {"sourceSha256": "0" * 64}):
                manifest = {"url": source.as_uri(), "bytes": 7,
                            "sourceSha1": hashlib.sha1(b"payload").hexdigest(),
                            "sourceSha256": hashlib.sha256(b"payload").hexdigest(), **overrides}
                with self.assertRaises(ValueError):
                    download_source(manifest, Path(root) / "result.ogv")
                self.assertEqual(sorted(path.name for path in Path(root).iterdir()), ["source.ogv"])

    def test_prepare_rejects_unspecified_color_without_publishing(self):
        with tempfile.TemporaryDirectory() as root:
            source = Path(root) / "untagged.mkv"
            subprocess.run([
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi",
                "-i", "testsrc2=size=1280x720:rate=24", "-frames:v", "48", "-an",
                "-c:v", "ffv1", "-y", str(source),
            ], check=True)
            with self.assertRaisesRegex(ValueError, "color"):
                prepare_fixture(source, Path(root) / "prepared", source_manifest(source))
            self.assertFalse((Path(root) / "prepared").exists())
            result = prepare_fixture(source, Path(root) / "prepared", source_manifest(source), assume_bt709=True)
            self.assertEqual(result["colorBasis"], "assumed-bt709-for-unspecified-source")
            self.assertNotIn("color_transfer", result["sourceMetadata"])

    def test_explicit_assumption_cannot_relabel_hdr_as_bt709(self):
        with tempfile.TemporaryDirectory() as root:
            source = Path(root) / "hdr.mkv"
            subprocess.run([
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi",
                "-i", "testsrc2=size=1280x720:rate=24", "-frames:v", "1", "-an",
                "-vf", "setparams=range=tv:color_primaries=bt2020:color_trc=smpte2084:colorspace=bt2020nc",
                "-c:v", "ffv1", "-y", str(source),
            ], check=True)
            with self.assertRaisesRegex(ValueError, "color"):
                prepare_fixture(source, Path(root) / "prepared", source_manifest(source), assume_bt709=True)
            self.assertFalse((Path(root) / "prepared").exists())

    def test_prepare_short_source_does_not_publish_incomplete_fixture(self):
        with tempfile.TemporaryDirectory() as root:
            source = Path(root) / "short.mkv"
            subprocess.run([
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi",
                "-i", "testsrc2=size=1280x720:rate=24", "-frames:v", "1", "-an",
                "-vf", "setparams=range=tv:color_primaries=bt709:color_trc=bt709:colorspace=bt709",
                "-c:v", "ffv1", "-color_range", "tv", "-colorspace", "bt709",
                "-color_trc", "bt709", "-color_primaries", "bt709", "-y", str(source),
            ], check=True)
            with self.assertRaisesRegex(ValueError, "frame count"):
                prepare_fixture(source, Path(root) / "prepared", source_manifest(source))
            self.assertEqual(sorted(path.name for path in Path(root).iterdir()), ["short.mkv"])

    def test_prepare_rejects_changed_source_before_publishing(self):
        with tempfile.TemporaryDirectory() as root:
            source = Path(root) / "source.ogv"
            source.write_bytes(b"pinned")
            manifest = source_manifest(source)
            source.write_bytes(b"edited")
            with self.assertRaisesRegex(ValueError, "hash"):
                prepare_fixture(source, Path(root) / "prepared", manifest)
            self.assertFalse((Path(root) / "prepared").exists())

    def test_download_cleans_partial_data_after_read_failure(self):
        class InterruptedResponse:
            reads = 0

            def __enter__(self):
                return self

            def __exit__(self, *args):
                pass

            def read(self, size):
                self.reads += 1
                if self.reads == 1:
                    return b"partial"
                raise OSError("connection reset")

        with tempfile.TemporaryDirectory() as root:
            destination = Path(root) / "source.ogv"
            manifest = {"url": "https://example.invalid/fixture", "bytes": 100,
                        "sourceSha1": "0" * 40, "sourceSha256": "0" * 64}
            with patch("urllib.request.urlopen", return_value=InterruptedResponse()):
                with self.assertRaisesRegex(OSError, "connection reset"):
                    download_source(manifest, destination)
            self.assertEqual(list(Path(root).iterdir()), [])

    def test_prepare_preserves_dangling_symlink(self):
        with tempfile.TemporaryDirectory() as root:
            destination = Path(root) / "prepared"
            destination.symlink_to("missing")
            with self.assertRaises(FileExistsError):
                prepare_fixture(Path(root) / "absent.ogv", destination, {})
            self.assertEqual(destination.readlink(), Path("missing"))

    def test_publish_does_not_replace_directory_created_during_preparation(self):
        with tempfile.TemporaryDirectory() as root:
            stage = Path(root) / "stage"
            stage.mkdir()
            (stage / "fixture.json").write_text("complete")
            destination = Path(root) / "prepared"
            destination.mkdir()
            with self.assertRaises(FileExistsError):
                publish_fixture(stage, destination)
            self.assertEqual(list(destination.iterdir()), [])
            self.assertEqual((stage / "fixture.json").read_text(), "complete")

    def test_publish_failure_leaves_no_completion_report_or_partial_asset(self):
        with tempfile.TemporaryDirectory() as root:
            stage = Path(root) / "stage"
            stage.mkdir()
            (stage / "a.mkv").write_bytes(b"video")
            (stage / "fixture.json").write_text("complete")
            destination = Path(root) / "prepared"
            import os
            real_link = os.link

            def fail_report_link(source, target):
                if Path(source).name == "fixture.json":
                    raise OSError("disk unavailable")
                return real_link(source, target)

            with patch("os.link", side_effect=fail_report_link):
                with self.assertRaisesRegex(OSError, "disk unavailable"):
                    publish_fixture(stage, destination)
            self.assertFalse(destination.exists())
            self.assertTrue((stage / "a.mkv").exists())


if __name__ == "__main__":
    unittest.main()
