"""Inspect structural gates for one Apple MLXVLM closure XCFramework."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import plistlib
import re
import subprocess


def _version(value: str) -> tuple[int, ...]:
    try:
        return tuple(int(part) for part in value.split("."))
    except ValueError as error:
        raise ValueError(f"invalid minimum OS version: {value}") from error


def _inspect_binary(binary: Path) -> dict:
    """Read architecture and deployment target from the actual Mach-O binary."""

    try:
        file_output = subprocess.check_output(["file", "-b", str(binary)], text=True)
        load_commands = subprocess.check_output(["otool", "-l", str(binary)], text=True)
    except (OSError, subprocess.CalledProcessError) as error:
        raise ValueError(f"unable to inspect Mach-O binary {binary}: {error}") from error
    if "Mach-O" not in file_output:
        raise ValueError(f"not a Mach-O binary: {binary}")
    architectures = sorted(set(re.findall(r"\b(?:arm64|arm64e|x86_64|i386)\b", file_output)))
    if not architectures:
        raise ValueError(f"Mach-O architecture is unavailable: {binary}")
    platforms = set(re.findall(r"\bplatform\s+(\d+)\b", load_commands))
    versions = re.findall(r"\bminos\s+(\d+(?:\.\d+){1,2})\b", load_commands)
    if not versions:
        raise ValueError(f"Mach-O deployment target is unavailable: {binary}")
    return {"architectures": architectures, "minimumOS": min(versions, key=_version), "platforms": platforms}


def _reject_symlink_escapes(path: Path, root: Path) -> None:
    for item in path.rglob("*"):
        if item.is_symlink():
            try:
                item.resolve().relative_to(root)
            except ValueError as error:
                raise ValueError(f"symlink escape in XCFramework: {item}") from error


def inspect_xcframework(path: Path, module: str, minimum_os: str) -> dict:
    """Validate declared iOS slices, interfaces, module maps and resources."""

    path = Path(path).resolve(strict=True)
    if path.suffix != ".xcframework":
        raise ValueError(f"not an XCFramework path: {path}")
    metadata = plistlib.loads((path / "Info.plist").read_bytes())
    _reject_symlink_escapes(path, path)
    libraries = metadata.get("AvailableLibraries")
    if not isinstance(libraries, list) or not libraries:
        raise ValueError(f"{path}: no available libraries")

    found: dict[str, dict] = {}
    bundles: list[str] = []
    for library in libraries:
        if not isinstance(library, dict):
            raise ValueError(f"{path}: invalid library entry")
        identifier = library.get("LibraryIdentifier")
        library_path = library.get("LibraryPath")
        if not isinstance(identifier, str) or not isinstance(library_path, str):
            raise ValueError(f"{path}: library identifier/path missing")
        if identifier in found:
            raise ValueError(f"{path}: duplicate slice {identifier}")
        framework = (path / identifier / library_path).resolve(strict=True)
        if not framework.is_relative_to(path):
            raise ValueError(f"{path}: slice escapes artifact: {identifier}")
        if framework.name != f"{module}.framework" or not framework.is_dir():
            raise ValueError(f"{path}: unexpected framework in slice {identifier}")
        if not (framework / module).is_file():
            raise ValueError(f"{path}: missing binary in slice {identifier}")
        binary_info = _inspect_binary(framework / module)
        if library.get("SupportedPlatform") != "ios":
            raise ValueError(f"{path}: non-iOS slice {identifier}")
        variant = library.get("SupportedPlatformVariant")
        if variant not in (None, "simulator"):
            raise ValueError(f"{path}: unsupported iOS variant {variant}")
        if "arm64" not in binary_info["architectures"]:
            raise ValueError(f"{path}: missing arm64 architecture in {identifier}")
        expected_platform = "7" if variant == "simulator" else "2"
        if expected_platform not in binary_info["platforms"]:
            raise ValueError(f"{path}: Mach-O platform does not match slice {identifier}")
        floor = binary_info["minimumOS"]
        if _version(floor) < _version(minimum_os):
            raise ValueError(f"{path}: Mach-O minimum OS below {minimum_os} in {identifier}")
        interfaces = sorted((framework / "Modules" / f"{module}.swiftmodule").glob("*.swiftinterface"))
        public_interfaces = [item for item in interfaces if not item.name.endswith(".private.swiftinterface")]
        if not public_interfaces:
            raise ValueError(f"{path}: missing public Swift interface in {identifier}")
        module_map = framework / "Modules/module.modulemap"
        if not module_map.is_file():
            raise ValueError(f"{path}: missing C module map in {identifier}")
        for bundle in sorted(framework.rglob("*.bundle")):
            if bundle.is_dir():
                bundles.append(str(bundle.relative_to(path)))
        found[identifier] = {
            "variant": "simulator" if variant == "simulator" else "device",
            "architectures": binary_info["architectures"],
            "minimumOS": floor,
            "interfaces": {str(item.relative_to(path)): hashlib.sha256(item.read_bytes()).hexdigest()
                           for item in public_interfaces},
            "moduleMapSHA256": hashlib.sha256(module_map.read_bytes()).hexdigest(),
        }

    if not any(item["variant"] == "device" for item in found.values()):
        raise ValueError(f"{path}: missing device slice")
    if not any(item["variant"] == "simulator" for item in found.values()):
        raise ValueError(f"{path}: missing simulator slice")
    return {
        "module": module,
        "minimumOS": minimum_os,
        "slices": sorted(found),
        "sliceDetails": found,
        "hasCModuleMap": True,
        "resourceBundles": sorted(bundles),
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", type=Path)
    parser.add_argument("module")
    parser.add_argument("minimum_os")
    args = parser.parse_args()
    try:
        print(json.dumps(inspect_xcframework(args.path, args.module, args.minimum_os), indent=2))
    except (OSError, ValueError, plistlib.InvalidFileException) as error:
        parser.exit(1, f"{error}\n")
