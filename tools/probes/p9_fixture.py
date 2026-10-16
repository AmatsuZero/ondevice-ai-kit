#!/usr/bin/env python3
"""Write the fixed non-personal P9 image probe fixture as a small PNG."""

import hashlib
import struct
import sys
import zlib
from pathlib import Path


def _chunk(kind: bytes, data: bytes) -> bytes:
    payload = kind + data
    return struct.pack(">I", len(data)) + payload + struct.pack(">I", zlib.crc32(payload))


def write_fixture(destination: Path) -> str:
    width = height = 64
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            rows.extend((220, 30, 40, 255) if 16 <= x < 48 and 16 <= y < 48
                        else (250, 250, 250, 255))
    image = (b"\x89PNG\r\n\x1a\n"
             + _chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
             + _chunk(b"IDAT", zlib.compress(bytes(rows), level=9))
             + _chunk(b"IEND", b""))
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(image)
    return hashlib.sha256(image).hexdigest()


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: p9_fixture.py OUTPUT.png")
    print(write_fixture(Path(sys.argv[1])))
