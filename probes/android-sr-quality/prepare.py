"""Prepare exact production SR sources with one test-only codec observation."""
import argparse
import hashlib
import json
from pathlib import Path


NAMES = ['AndroidSrMedia.kt', 'VideoColor.kt', 'EsrganInterpreter.kt', 'EsrganTiles.kt', 'LiteRtSrModel.kt']


def prepare(root, output):
    root, output = Path(root), Path(output)
    if output.exists() or output.is_symlink():
        raise ValueError('refusing to replace existing generated sources')
    directory = root / 'android/backend-litert-sr/src/main/kotlin/io/ondeviceai/litertsr'
    sources = {name: (directory / name).read_bytes() for name in NAMES}
    text = sources['AndroidSrMedia.kt'].decode()
    marker = '.name)\n    }\n    private fun readRgb'
    if text.count(marker) != 1:
        raise ValueError('production codec observation point changed')
    observed = text.replace(marker, '.name).also { SrCodecTrace.record(encoder, it.name) }\n    }\n    private fun readRgb')
    (output / 'kotlin').mkdir(parents=True)
    (output / 'assets').mkdir()
    for name, data in sources.items():
        (output / 'kotlin' / name).write_bytes(observed.encode() if name == 'AndroidSrMedia.kt' else data)
    inventory = {'scope': 'test-only codec observation; production sources unchanged', 'sources': {
        str((directory / name).relative_to(root)): hashlib.sha256(data).hexdigest()
        for name, data in sources.items()}}
    (output / 'assets/sr-source-inventory.json').write_text(json.dumps(inventory, indent=2, sort_keys=True) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    prepare(args.root, args.output)
