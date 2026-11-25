"""Prepare the pinned OpenVoice demo models and native runtime for a mobile build."""
import argparse
import fcntl
import hashlib
import io
from pathlib import Path
import shutil
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
BUILD = ROOT / "build"
MODELS = "https://huggingface.co/moonbox-dev/OpenVoiceV2-ONNX/resolve/1be2e319a10a88ff2d268eb1ec2977740df729f2/int8/"
NUGET = "https://api.nuget.org/v3-flatcontainer/microsoft.ml.onnxruntime/1.23.2/microsoft.ml.onnxruntime.1.23.2.nupkg"
RUNTIME_HASH = "25fe172f7fcdf34f2b5b02c8b997f6b88abc282956849d4af4335af23d0c4e4a"


def fetch(name, url, digest):
    target = BUILD / "downloads" / name
    target.parent.mkdir(exist_ok=True)
    if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == digest:
        return target
    with tempfile.TemporaryDirectory(dir=target.parent) as stage:
        temp = Path(stage) / name
        with urllib.request.urlopen(url, timeout=120) as response, temp.open("wb") as output:
            shutil.copyfileobj(response, output)
        if hashlib.sha256(temp.read_bytes()).hexdigest() != digest:
            raise ValueError(f"SHA256 mismatch for {name}")
        temp.replace(target)
    return target


def prepare(platform, resources):
    resources.mkdir(parents=True, exist_ok=True)
    for name, digest in (
        ("converter.onnx", "17c254cb33ca437f192d80e3bdc9cfdde39620aeb995a472345352e393955b64"),
        ("ref_encoder.onnx", "2e8b8b34c880b6f5759b31e7a8ee4ac0a5eeeca2362ec5d1270c10793703ad02"),
    ):
        shutil.copy2(fetch(name, MODELS + name, digest), resources / name)
    for asset in (ROOT / "assets").iterdir():
        shutil.copy2(asset, resources / asset.name)
    package = fetch("ort-1.23.2.nupkg", NUGET, RUNTIME_HASH)
    destination = BUILD / platform
    marker = destination / ("ready-" + RUNTIME_HASH)
    with zipfile.ZipFile(package) as archive:
        for name in ("LICENSE", "ThirdPartyNotices.txt"):
            (resources / ("onnxruntime-" + name)).write_bytes(archive.read(name))
        if marker.is_file():
            return
        destination.mkdir(exist_ok=True)
        name = ("runtimes/ios/native/onnxruntime.xcframework.zip" if platform == "ios"
                else "runtimes/android/native/onnxruntime.aar")
        prefixes = (("onnxruntime.xcframework/ios-arm64/", "onnxruntime.xcframework/ios-arm64_x86_64-simulator/")
                    if platform == "ios" else ("headers/", "jni/"))
        with zipfile.ZipFile(io.BytesIO(archive.read(name))) as native:
            for member in native.namelist():
                if member.startswith(prefixes) and (platform == "ios" or member.startswith("headers/") or member.endswith("/libonnxruntime.so")):
                    native.extract(member, destination)
        marker.touch()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=["ios", "android"])
    parser.add_argument("resources", type=Path)
    args = parser.parse_args()
    BUILD.mkdir(exist_ok=True)
    with (BUILD / "prepare.lock").open("a+") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        prepare(args.platform, args.resources)
