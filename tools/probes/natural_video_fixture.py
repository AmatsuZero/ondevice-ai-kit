#!/usr/bin/env python3
"""Acquire a pinned P7 source and prepare a fixed, explicitly tagged SDR fixture."""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import urllib.request
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).parent))
from video_sr_quality import stream_metadata
from video_sr_validate import decoded_sha256, validate


def file_identity(path: Path) -> dict:
    sha1, sha256, size = hashlib.sha1(), hashlib.sha256(), 0
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            sha1.update(chunk)
            sha256.update(chunk)
            size += len(chunk)
    return {"sha1": sha1.hexdigest(), "sha256": sha256.hexdigest(), "bytes": size}


def matches_manifest(identity: dict, manifest: dict) -> bool:
    return (identity["bytes"] == manifest["bytes"]
            and identity["sha1"] == manifest["sourceSha1"]
            and identity["sha256"] == manifest["sourceSha256"])


def download_source(manifest: dict, destination: Path) -> dict:
    """Check pinned size, SHA-1 and SHA-256 before atomic publication.

    Never overwrite an existing asset. HTTP errors are returned to the caller;
    rerun after Retry-After rather than repeatedly hitting a rate-limited host.
    """
    if destination.exists():
        if destination.is_file() and not destination.is_symlink():
            identity = file_identity(destination)
            if matches_manifest(identity, manifest):
                return identity
        raise FileExistsError(f"existing asset does not match manifest: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    descriptor, filename = tempfile.mkstemp(prefix=f".{destination.name}.", dir=destination.parent)
    temporary = Path(filename)
    try:
        with os.fdopen(descriptor, "wb") as output:
            request = urllib.request.Request(manifest["url"], headers={
                "User-Agent": "ondevice-ai-kit/P7 (public-domain validation fixture)",
            })
            with urllib.request.urlopen(request, timeout=60) as response:
                total = 0
                while chunk := response.read(1024 * 1024):
                    total += len(chunk)
                    if total > manifest["bytes"]:
                        raise ValueError("source exceeds pinned byte count")
                    output.write(chunk)
        identity = file_identity(temporary)
        if not matches_manifest(identity, manifest):
            raise ValueError("source size or hash mismatch")
        # A hard link publishes without overwriting a concurrently created asset.
        os.link(temporary, destination)
        return identity
    finally:
        temporary.unlink(missing_ok=True)


def publish_fixture(temporary: Path, destination: Path) -> None:
    """Claim a new directory exclusively; the completion report appears last.

    Consumers must require fixture.json. A failed publish removes only files this
    invocation linked, preserving any independently created entries.
    """
    destination.mkdir()
    published = []
    try:
        files = sorted(path for path in temporary.iterdir() if path.name != "fixture.json")
        files.append(temporary / "fixture.json")
        for path in files:
            target = destination / path.name
            os.link(path, target)
            published.append(target)
    except BaseException:
        for path in published:
            path.unlink(missing_ok=True)
        try:
            destination.rmdir()
        except OSError:
            pass  # preserve files created by another actor
        raise


def prepare_fixture(source: Path, destination: Path, manifest: dict, *, assume_bt709: bool = False) -> dict:
    """Prepare the first 2 seconds using a recorded source color interpretation.

    Unknown color requires an explicit experimental assumption. Known conflicting
    metadata is rejected even with that option; it needs a separate conversion.
    """
    if os.path.lexists(destination):
        raise FileExistsError(destination)
    if not matches_manifest(file_identity(source), manifest):
        raise ValueError("source size or hash does not match manifest")
    source_metadata = stream_metadata(source)
    color = {"color_range": "tv", "color_space": "bt709",
             "color_transfer": "bt709", "color_primaries": "bt709"}
    if all(source_metadata.get(key) == value for key, value in color.items()):
        color_basis = "source-metadata-bt709"
    elif (assume_bt709 and all(source_metadata.get(key) in (None, "unknown", "unspecified", value)
                              for key, value in color.items())):
        color_basis = "assumed-bt709-for-unspecified-source"
    else:
        raise ValueError("source color must explicitly declare limited-range SDR BT.709")
    if source_metadata["width"] < 1280 or source_metadata["height"] < 720:
        raise ValueError("source must be at least 1280x720")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix=f".{destination.name}.", dir=destination.parent))
    try:
        flags = ["-an", "-pix_fmt", "yuv420p", "-color_range", "tv", "-colorspace", "bt709",
                 "-color_trc", "bt709", "-color_primaries", "bt709"]

        def encode(origin: Path, name: str, filters: str, mp4: bool = False) -> None:
            codec = (["-c:v", "libx264", "-preset", "ultrafast", "-crf", "0", "-movflags", "+faststart"]
                     if mp4 else ["-c:v", "ffv1"])
            tagged = "setparams=range=tv:color_primaries=bt709:color_trc=bt709:colorspace=bt709,"
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(origin),
                            "-map", "0:v:0", "-vf", tagged + filters, "-frames:v", "48",
                            *flags, *codec, str(temporary / name)], check=True)

        encode(source, "reference-720p.mkv",
               "setpts=PTS-STARTPTS,fps=24,scale=1280:720:flags=lanczos,setsar=1")
        reference = temporary / "reference-720p.mkv"
        encode(reference, "input-180p-4x.mkv", "scale=320:180:flags=area")
        low_resolution = temporary / "input-180p-4x.mkv"
        encode(low_resolution, "bicubic-720p-4x.mkv", "scale=1280:720:flags=bicubic")
        encode(low_resolution, "input-180p-4x.mp4", "null", mp4=True)
        validate([reference, low_resolution, temporary / "bicubic-720p-4x.mkv"], scale=4, quiet=True)
        if decoded_sha256(low_resolution) != decoded_sha256(temporary / "input-180p-4x.mp4"):
            raise ValueError("MP4 transport changed decoded pixels")
        files = {path.name: {**file_identity(path), "decodedSha256": decoded_sha256(path)}
                 for path in sorted(temporary.iterdir())}
        if not matches_manifest(file_identity(source), manifest):
            raise ValueError("source size or hash changed during preparation")
        result = {"sourceSha256": manifest["sourceSha256"], "sourceManifest": manifest,
                  "sourceVerified": True, "sourceMetadata": source_metadata,
                  "colorBasis": color_basis, "startSeconds": 0,
                  "frames": 48, "fps": 24, "files": files,
                  "ffmpegVersion": subprocess.check_output(["ffmpeg", "-version"], text=True).splitlines()[0]}
        (temporary / "fixture.json").write_text(json.dumps(result, indent=2) + "\n")
        publish_fixture(temporary, destination)
        return result
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("fetch", "prepare"))
    parser.add_argument("source", type=Path, help="source manifest for fetch, video for prepare")
    parser.add_argument("destination", type=Path)
    parser.add_argument("--manifest", type=Path, help="required pinned source manifest for prepare")
    parser.add_argument("--assume-bt709", action="store_true",
                        help="interpret unspecified source color as limited-range BT.709; rejects conflicting tags")
    arguments = parser.parse_args()
    if arguments.mode == "fetch":
        result = download_source(json.loads(arguments.source.read_text()), arguments.destination)
    else:
        if arguments.manifest is None:
            parser.error("prepare requires --manifest")
        result = prepare_fixture(arguments.source, arguments.destination,
                                 json.loads(arguments.manifest.read_text()),
                                 assume_bt709=arguments.assume_bt709)
    print(json.dumps(result, indent=2))
