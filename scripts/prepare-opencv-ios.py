#!/usr/bin/env python3
"""Prepare the pinned official SDK locally; no downloads or model/sample bundling."""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--archive", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
if args.archive.stat().st_size != 221312528:
    raise SystemExit("Unexpected SDK archive size")
with args.archive.open("rb") as archive:
    digest = hashlib.file_digest(archive, "sha256").hexdigest()
if digest != "86b42c9f141cd9169b91be2fc380b0e556a88a95c98ccc8e7aef8349ab74cf70":
    raise SystemExit("Unexpected SDK archive hash")
output = args.output.resolve()
if output.exists():
    raise SystemExit("Refusing to replace existing output")
output.mkdir(parents=True)
subprocess.run(["ditto", "-xk", str(args.archive.resolve()), str(output / "upstream")], check=True)
source = output / "upstream/opencv2.framework"
libraries = []
for name, arch in [("device", "arm64"), ("simulator", "x86_64")]:
    directory = output / name
    directory.mkdir(parents=True)
    # Upstream is an ar archive in a legacy framework, not a dynamic framework.
    # Package it as a static library so consumers link it without embedding it.
    shutil.copytree(source / "Headers", directory / "include/opencv2", symlinks=False)
    subprocess.run(["xcrun", "lipo", str(source / "opencv2"), "-thin", arch,
                    "-output", str(directory / "libopencv2.a")], check=True)
    libraries += ["-library", str(directory / "libopencv2.a"), "-headers", str(directory / "include")]
subprocess.run(["xcodebuild", "-create-xcframework", *libraries,
                "-output", str(output / "opencv2.xcframework")], check=True)
print(output / "opencv2.xcframework")
