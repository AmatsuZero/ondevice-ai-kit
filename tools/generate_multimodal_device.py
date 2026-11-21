"""Generate a fresh fixed-lock programmatic KMP host/VM validation App; no resolution/download."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ["probes/ios-multimodal-device/MultimodalDeviceApp.swift", "probes/ios-multimodal-device/MultimodalDeviceSuite.swift", "demo/iosApp/MultimodalHost.swift", "demo/iosApp/MultimodalInputStore.swift", "demo/iosApp/SpeechInputStore.swift", "kmp-binding/apple/IOSMultimodalBridge.swift", "kmp-binding/apple/IOSMultimodalInput.swift", "kmp-binding/apple/IOSSpeechAudioInput.swift"]


def generate(output: Path, source_commit: str) -> None:
    if not re.fullmatch(r"[0-9a-f]{40}", source_commit):
        raise ValueError("full source commit required")
    output = output.absolute()
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        raise ValueError("refusing nonempty output")
    framework = ROOT / "demo/shared/build/bin/iosArm64/debugFramework/ODAIKitDemo.framework"
    lock = ROOT / "apple-mlx/Package.resolved"
    template = ROOT / "probes/ios-multimodal-device/project.yml"
    for path in [*(ROOT / s for s in SOURCES), ROOT / "apple", ROOT / "apple-mlx", framework, lock, template]:
        if not path.exists():
            raise ValueError("missing input: " + str(path))
    spec = template.read_text()
    replacements = {"__APPLE__": str(ROOT / "apple"), "__MLX__": str(ROOT / "apple-mlx"), "__FRAMEWORK__": str(framework), "__COMMIT__": source_commit}
    for key, value in replacements.items():
        spec = spec.replace(key, json.dumps(value))
    spec = spec.replace("__SOURCES__", json.dumps([{"path": str(ROOT / s)} for s in SOURCES]))
    output.mkdir(parents=True, exist_ok=True)
    (output / "project.yml").write_text(spec)
    subprocess.run(["xcodegen", "generate", "--spec", str(output / "project.yml"), "--project", str(output)], check=True, capture_output=True, text=True)
    pins = output / "MultimodalDevice.xcodeproj/project.xcworkspace/xcshareddata/swiftpm"
    pins.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(lock, pins / lock.name)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--source-commit", required=True)
    args = parser.parse_args()
    try:
        generate(args.output, args.source_commit)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, str(error) + "\n")
