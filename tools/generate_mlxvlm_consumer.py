#!/usr/bin/env python3
"""Generate a standalone local-source consumer in a fresh output directory; no resolution/download."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def _tracked_package_files() -> list[Path]:
    result = subprocess.run(
        [
            "git", "ls-files", "-z", "--",
            "apple/Package.swift", "apple/Sources", "apple/Tests",
            "apple-mlx/Package.swift", "apple-mlx/Package.resolved", "apple-mlx/Sources", "apple-mlx/Tests",
        ],
        cwd=ROOT,
        check=True,
        capture_output=True,
    )
    return [Path(item) for item in result.stdout.decode().split("\0") if item]


def _copy_portable_packages(output: Path) -> dict:
    files = []
    for relative in _tracked_package_files():
        source = ROOT / relative
        if source.is_symlink() or not source.is_file():
            raise ValueError(f"portable source contains unsupported file: {relative}")
        destination = output / "packages" / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        data = source.read_bytes()
        destination.write_bytes(data)
        files.append({
            "path": str(Path("packages") / relative),
            "sha256": hashlib.sha256(data).hexdigest(),
        })
    lock = output / "packages/apple-mlx/Package.resolved"
    return {
        "formatVersion": 1,
        "kind": "local-source",
        "files": files,
        "lockSHA256": hashlib.sha256(lock.read_bytes()).hexdigest(),
    }


def generate(output: Path, *, portable_source: bool = False) -> None:
    output = output.absolute()
    if output.is_symlink() or (output.exists() and (not output.is_dir() or any(output.iterdir()))):
        raise ValueError('consumer output already exists and is not empty')
    example = ROOT / 'examples/ios-mlxvlm-native'
    source = example / 'MLXVLMConsumerApp.swift'
    measurements = example / 'BenchmarkMeasurements.swift'
    lock = ROOT / 'apple-mlx/Package.resolved'
    spec = (example / 'project.yml').read_text()
    if portable_source:
        spec = spec.replace('path: ../../apple-mlx', 'path: packages/apple-mlx')
        spec = spec.replace('path: ../../apple', 'path: packages/apple')
    else:
        spec = spec.replace('path: ../../apple-mlx', 'path: ' + json.dumps(str(ROOT / 'apple-mlx')))
        spec = spec.replace('path: ../../apple', 'path: ' + json.dumps(str(ROOT / 'apple')))
    if not source.is_file() or not measurements.is_file() or not lock.is_file():
        raise ValueError('consumer source or fixed package lock missing')
    output.mkdir(parents=True, exist_ok=True)
    inventory = _copy_portable_packages(output) if portable_source else None
    shutil.copyfile(source, output / source.name)
    shutil.copyfile(measurements, output / measurements.name)
    (output / 'project.yml').write_text(spec)
    subprocess.run(['xcodegen', 'generate', '--spec', str(output / 'project.yml'), '--project', str(output)], check=True)
    pins = output / 'MLXVLMConsumer.xcodeproj/project.xcworkspace/xcshareddata/swiftpm'
    pins.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(lock, pins / lock.name)
    if inventory is not None:
        (output / 'source-bundle.json').write_text(json.dumps(inventory, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--portable-source', action='store_true',
                        help='snapshot tracked local packages for a relocatable source consumer')
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    try:
        generate(args.output, portable_source=args.portable_source)
    except (ValueError, OSError) as error:
        parser.exit(1, str(error) + '\n')
