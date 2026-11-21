#!/usr/bin/env python3
"""Validate a pinned P9 model manifest before any asset is used."""
import json
import re
import sys
from pathlib import Path

SHA = re.compile(r"[0-9a-f]{40}\Z")
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
MODEL_ID = "mlx-community/SmolVLM2-500M-Video-Instruct-mlx"


def validate_manifest(path: Path) -> dict:
    data = json.loads(path.read_text())
    if data.get("modelId") != MODEL_ID:
        raise ValueError("unknown modelId")
    for key in ("modelRevision", "runtimeRevision"):
        if not SHA.fullmatch(data.get(key, "")):
            raise ValueError(f"floating or invalid {key}")
    if data.get("runtime") != "MLXVLM":
        raise ValueError("unknown runtime")
    if data.get("platformMin") != "iOS 18.0":
        raise ValueError("probe requires iOS 18.0")
    if data.get("license") != "apache-2.0":
        raise ValueError("unexpected license")
    if data.get("status") not in ("candidate", "verified"):
        raise ValueError("unknown status")
    files = data.get("files")
    if not isinstance(files, list) or not files:
        raise ValueError("files required")
    for item in files:
        name = item.get("path", "")
        if not name or Path(name).is_absolute() or ".." in Path(name).parts:
            raise ValueError("unsafe file path")
        if data["status"] == "verified":
            if not SHA256.fullmatch(item.get("sha256", "")):
                raise ValueError("verified file requires sha256")
            if not isinstance(item.get("bytes"), int) or item["bytes"] <= 0:
                raise ValueError("verified file bytes must be positive")
        elif item.get("bytes") is not None and (
            not isinstance(item.get("bytes"), int) or item["bytes"] < 0
        ):
            raise ValueError("candidate file bytes must be null or non-negative")
    return data


if __name__ == "__main__":
    try:
        result = validate_manifest(Path(sys.argv[1]))
    except (IndexError, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"invalid manifest: {error}", file=sys.stderr)
        raise SystemExit(1)
    print(f"valid {result['status']} manifest: {result['modelId']}@{result['modelRevision']}")
