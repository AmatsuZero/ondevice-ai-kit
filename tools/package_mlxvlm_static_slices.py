"""Form static framework slices from real SwiftPM target build products."""

from __future__ import annotations

import plistlib
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

from tools.inspect_mlxvlm_xcframework import _inspect_binary


def framework_slice(products: Path, module: str, output: Path, native_objects: list[str]) -> dict:
    """Copy verified textual interfaces and archive target objects, without rebuilding."""
    products, output = Path(products), Path(output)
    if output.exists() or output.is_symlink():
        raise ValueError(f"framework output exists: {output}")
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", module):
        raise ValueError("invalid module name")
    names = [module, *native_objects]
    if len(names) != len(set(names)) or any(not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", n) for n in names):
        raise ValueError("invalid or duplicate native object names")
    objects = [products / f"{name}.o" for name in names]
    interfaces = sorted((products / f"{module}.swiftmodule").glob("*.swiftinterface"))
    if not interfaces or not any(not p.name.endswith(".private.swiftinterface") for p in interfaces):
        raise ValueError(f"missing textual interface: {module}")
    if any(not p.is_file() for p in objects):
        raise ValueError(f"missing target object: {module}")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".static-framework-", dir=output.parent) as temporary:
        stage = Path(temporary) / output.name
        modules = stage / "Modules" / f"{module}.swiftmodule"
        modules.mkdir(parents=True)
        for path in interfaces:
            shutil.copyfile(path, modules / path.name)
        # No serialized swiftmodule: the independent consumer must load the
        # shipped textual interface, not a same-compiler cache artifact.
        (stage / "Modules/module.modulemap").write_text(f"framework module {module} {{ export * }}\n")
        subprocess.run(["xcrun", "libtool", "-static", "-o", str(stage / module),
                        *map(str, objects)], check=True, capture_output=True)
        result = _inspect_binary(stage / module)
        metadata = {
            "CFBundleExecutable": module, "CFBundleIdentifier": f"io.ondeviceai.binary.{module}",
            "CFBundleName": module, "CFBundlePackageType": "FMWK", "CFBundleVersion": "1",
            "CFBundleShortVersionString": "0.1.0", "MinimumOSVersion": result["minimumOS"],
            "CFBundleSupportedPlatforms": ["iPhoneSimulator" if result["platforms"] == {"7"} else "iPhoneOS"],
        }
        (stage / "Info.plist").write_bytes(plistlib.dumps(metadata))
        stage.rename(output)
    return result
