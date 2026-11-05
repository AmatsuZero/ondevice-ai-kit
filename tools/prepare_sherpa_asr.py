#!/usr/bin/env python3
"""Prepare pinned offline ASR inputs on the build host, never on the phone."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import urllib.request

VERSION = "2.0.1"
REVISION = "65176e2deb88badc814a94058666cadccc29b61c"
MODEL_BASE = f"https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/{REVISION}"
INPUTS = {
    "silero_vad.onnx": (
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        643854, "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"),
    "sherpa-onnx-2.0.1.aar": (
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar",
        49441341, "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"),
    "tiny-encoder.int8.onnx": (f"{MODEL_BASE}/tiny-encoder.int8.onnx", 12937772,
        "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"),
    "tiny-decoder.int8.onnx": (f"{MODEL_BASE}/tiny-decoder.int8.onnx", 89855401,
        "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925"),
    "tiny-tokens.txt": (f"{MODEL_BASE}/tiny-tokens.txt", 816730,
        "git:a4edf0b719c10d28cd83f8c294449f99ff5d5dc0"),
}


def verified(path, size, digest):
    if not path.is_file() or path.stat().st_size != size:
        return False
    if digest.startswith("git:"):
        actual = hashlib.sha1(f"blob {size}\0".encode() + path.read_bytes()).hexdigest()
        return actual == digest.removeprefix("git:")
    return hashlib.file_digest(path.open("rb"), "sha256").hexdigest() == digest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    downloads = out / "downloads"
    downloads.mkdir(parents=True, exist_ok=True)
    for name, (url, size, digest) in INPUTS.items():
        path = downloads / name
        if not verified(path, size, digest):
            temporary = path.with_suffix(path.suffix + ".part")
            print(f"Downloading {name} ({size} bytes)", flush=True)
            with urllib.request.urlopen(url, timeout=60) as response, temporary.open("wb") as target:
                shutil.copyfileobj(response, target)
            if not verified(temporary, size, digest):
                raise ValueError(f"artifact digest/size mismatch: {name}")
            temporary.replace(path)
        print(f"Verified {name}", flush=True)
    assets = out / "assets" / "sherpa-whisper-tiny"
    assets.mkdir(parents=True, exist_ok=True)
    for name in INPUTS:
        if name.endswith(".aar"):
            continue
        shutil.copyfile(downloads / name, assets / name)
    notices = {
        "SHERPA-LICENSE.txt": "https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v2.0.1/LICENSE",
        "WHISPER-LICENSE.txt": "https://raw.githubusercontent.com/openai/whisper/v20250625/LICENSE",
        "SILERO-LICENSE.txt": "https://raw.githubusercontent.com/snakers4/silero-vad/066409f3d9db45a30d21dfcbf9147826ffdd7052/LICENSE",
    }
    for name, url in notices.items():
        data = urllib.request.urlopen(url, timeout=30).read()
        if b"Permission is hereby granted" not in data and b"Apache License" not in data:
            raise ValueError(f"unrecognized license response: {name}")
        (assets / name).write_bytes(data)
    repo = out / "m2" / "com" / "k2fsa" / "sherpa" / "onnx" / "sherpa-onnx" / VERSION
    repo.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(downloads / "sherpa-onnx-2.0.1.aar", repo / "sherpa-onnx-2.0.1.aar")
    (repo / "sherpa-onnx-2.0.1.pom").write_text(
        '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
        '<groupId>com.k2fsa.sherpa.onnx</groupId><artifactId>sherpa-onnx</artifactId>'
        '<version>2.0.1</version><packaging>aar</packaging></project>\n')
    manifest = {"runtimeVersion": VERSION, "modelRevision": REVISION,
        "files": {p.name: {"size": p.stat().st_size,
            "sha256": hashlib.sha256(p.read_bytes()).hexdigest()}
            for p in sorted(assets.iterdir()) if p.is_file()}}
    (out / "prepared.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({"sherpaMavenRepo": str(out / "m2"),
        "sherpaAsrAssetsDir": str(out / "assets"), "manifest": manifest}, indent=2))


if __name__ == "__main__":
    main()
