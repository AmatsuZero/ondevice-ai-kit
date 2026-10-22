#!/usr/bin/env python3
"""Generate a standalone local-source consumer in a fresh output directory; no resolution/download."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def generate(output: Path) -> None:
    output = output.absolute()
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        raise ValueError('consumer output already exists and is not empty')
    example = ROOT / 'examples/ios-mlxvlm-native'
    source = example / 'MLXVLMConsumerApp.swift'
    lock = ROOT / 'apple-mlx/Package.resolved'
    spec = (example / 'project.yml').read_text()
    spec = spec.replace('path: ../../apple-mlx', 'path: ' + json.dumps(str(ROOT / 'apple-mlx')))
    spec = spec.replace('path: ../../apple', 'path: ' + json.dumps(str(ROOT / 'apple')))
    if not source.is_file() or not lock.is_file():
        raise ValueError('consumer source or fixed package lock missing')
    output.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, output / source.name)
    (output / 'project.yml').write_text(spec)
    subprocess.run(['xcodegen', 'generate', '--spec', str(output / 'project.yml'), '--project', str(output)], check=True)
    pins = output / 'MLXVLMConsumer.xcodeproj/project.xcworkspace/xcshareddata/swiftpm'
    pins.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(lock, pins / lock.name)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    try:
        generate(args.output)
    except (ValueError, OSError) as error:
        parser.exit(1, str(error) + '\n')
