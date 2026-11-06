"""Build the fixed, isolated Apple MLXVLM static closure for local validation."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import shutil
import subprocess
import zipfile

from tools.inspect_mlxvlm_xcframework import inspect_xcframework
from tools.package_mlxvlm_static_slices import framework_slice
from tools.prepare_mlxvlm_binary_snapshot import EXPECTED_LOCK_SHA256
from tools.validate_mlxvlm_binary_artifact import _sha256_file as sha, _sha256_tree as tree, validate_manifest


ROOT = Path(__file__).resolve().parents[1]
CANDIDATE = ROOT / "docs/validation/f09-compatibility-candidate"
CANDIDATE_SHA256 = "46b40b43a0d70c3561c473d9e981ddaddf433861001cdfcefa49a5a77b6586de"
PACKAGE_MODULES = {
    "eventsource": ["EventSource"], "mlx-swift": ["MLX", "MLXNN", "MLXOptimizers"],
    "mlx-swift-lm": ["MLXLMCommon", "MLXVLM"],
    "swift-collections": ["InternalCollectionsUtilities", "OrderedCollections"],
    "swift-crypto": ["Crypto"], "swift-huggingface": ["HuggingFace"], "swift-jinja": ["Jinja"],
    "swift-numerics": ["ComplexModule", "Numerics", "RealModule"],
    "swift-transformers": ["Hub", "Tokenizers"],
}
SDK_MODULES = ["ODAIKitAppleMLXVLM", "ODAIKitCore", "ODAIKitModelStore", "ODAIKitMultimodal"]
MODULES = sorted(SDK_MODULES + [m for modules in PACKAGE_MODULES.values() for m in modules])
NATIVE_OBJECTS = {"MLX": ["Cmlx", "cLogSupport"], "RealModule": ["_NumericsShims"], "Hub": ["yyjson"]}
C_HEADERS = {
    "Cmlx": ("mlx-swift/Source/Cmlx/include", "MLX"),
    "cLogSupport": ("mlx-swift/Source/cLogSupport/include", "MLX"),
    "_NumericsShims": ("swift-numerics/Sources/_NumericsShims/include", "RealModule"),
    "yyjson": ("yyjson/src", "Hub"),
}


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def _json(path: Path, payload: dict) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")


def _output(*args: str, cwd: Path | None = None) -> str:
    environment = {**os.environ, "GIT_NO_LAZY_FETCH": "1", "GIT_TERMINAL_PROMPT": "0"}
    return subprocess.check_output(["rtk", "proxy", *args], cwd=cwd, env=environment, text=True).strip()


def _run(log: Path, *args: str) -> None:
    with log.open("w") as stream:
        subprocess.run(["rtk", "proxy", *args], stdout=stream, stderr=subprocess.STDOUT,
                       env={**os.environ, "GIT_NO_LAZY_FETCH": "1", "GIT_TERMINAL_PROMPT": "0"}, check=True)


def validate_snapshot(snapshot: Path) -> dict:
    """Reject modified snapshot bytes before invoking a compiler or creating output."""
    snapshot = Path(snapshot).resolve(strict=True)
    payload = json.loads((snapshot / "source-inventory.json").read_text())
    _require(isinstance(payload, dict) and payload.get("kind") == "apple-mlxvlm-committed-source-snapshot",
             "invalid snapshot inventory")
    _require(isinstance(payload.get("sourceCommit"), str)
             and re.fullmatch(r"[0-9a-f]{40}", payload["sourceCommit"]), "invalid snapshot sourceCommit")
    _require(payload.get("lockSHA256") == EXPECTED_LOCK_SHA256, "snapshot production lock mismatch")
    entries = payload.get("files")
    _require(isinstance(entries, list) and entries, "snapshot files are required")
    digest = hashlib.sha256()
    seen = set()
    for entry in entries:
        _require(isinstance(entry, dict) and isinstance(entry.get("path"), str), "invalid snapshot path")
        relative = Path(entry["path"])
        _require(not relative.is_absolute() and ".." not in relative.parts
                 and str(relative) == entry["path"] and relative.parts[0] == "packages"
                 and entry["path"] not in seen, "unsafe or duplicate snapshot path")
        seen.add(entry["path"])
        path = snapshot / relative
        _require(not any(p.is_symlink() for p in [path, *path.parents] if p.is_relative_to(snapshot)),
                 "snapshot symlink is unsupported")
        _require(path.is_file() and sha(path) == entry.get("sha256"), f"snapshot source drift: {relative}")
        digest.update(str(relative).encode()); digest.update(b"\0")
        digest.update(path.read_bytes()); digest.update(b"\0")
    _require(digest.hexdigest() == payload.get("treeSHA256"), "snapshot tree hash drift")
    actual = {str(p.relative_to(snapshot)) for p in (snapshot / "packages").rglob("*")
              if p.is_file() or p.is_symlink()}
    _require(actual == seen, "snapshot source inventory contains missing or extra files")
    return payload


def validate_cache(cache: Path, snapshot: Path) -> dict:
    """Accept only the existing fixed revisions plus the four adopted patched files."""
    candidate = json.loads((CANDIDATE / "manifest.json").read_text())
    _require(sha(CANDIDATE / "manifest.json") == CANDIDATE_SHA256, "compatibility manifest drift")
    for name, digest in candidate["patchSha256"].items():
        _require(sha(CANDIDATE / (name + ".patch")) == digest, f"compatibility patch drift: {name}")
    lock_path = snapshot / "packages/apple-mlx/Package.resolved"
    _require(sha(lock_path) == EXPECTED_LOCK_SHA256, "snapshot lock bytes drift")
    lock = json.loads(lock_path.read_text())
    patched: dict[str, dict[str, str]] = {}
    for entry in candidate["files"]:
        patched.setdefault(entry["package"], {})[entry["path"]] = entry["patchedSha256"]
    for pin in lock["pins"]:
        name, revision = pin["identity"], pin["state"]["revision"]
        repo = cache / "checkouts" / name
        _require(not repo.is_symlink() and repo.resolve().is_relative_to(cache), f"cache path escape: {name}")
        _require(_output("git", "-C", str(repo), "rev-parse", "HEAD") == revision, f"cache revision drift: {name}")
        changed = set(_output("git", "-C", str(repo), "diff", "--name-only", "HEAD").splitlines())
        _require(changed == set(patched.get(name, {})), f"unexpected cache edits: {name}")
        _require(not _output("git", "-C", str(repo), "ls-files", "--others", "--exclude-standard"),
                 f"untracked cache inputs: {name}")
        for path, digest in patched.get(name, {}).items():
            _require(sha(repo / path) == digest, f"patched cache file drift: {name}/{path}")
    mlx = cache / "checkouts/mlx-swift"
    for name in ("mlx", "mlx-c"):
        path = f"Source/Cmlx/{name}"
        expected = _output("git", "-C", str(mlx), "ls-tree", "HEAD", path).split()[2]
        _require(_output("git", "-C", str(mlx / path), "rev-parse", "HEAD") == expected,
                 f"native source revision drift: {name}")
        _require(not _output("git", "-C", str(mlx / path), "status", "--porcelain", "--untracked-files=all"),
                 f"native source edits: {name}")
    return lock


def _notices(distribution: Path, cache: Path, lock: dict, source_commit: str, snapshot: Path) -> tuple[list, list]:
    directory = distribution / "notices"; directory.mkdir()
    attributions = []
    for pin in lock["pins"]:
        name = pin["identity"]
        licenses = [p for p in (cache / "checkouts" / name).iterdir() if p.is_file()
                    and p.name.upper() in {"LICENSE", "LICENSE.TXT", "LICENSE.MD", "NOTICE", "NOTICE.TXT", "COPYING"}]
        _require(bool(licenses), f"missing source license: {name}")
        for path in licenses:
            target = directory / name / path.name; target.parent.mkdir(exist_ok=True)
            shutil.copyfile(path, target)
        kind = "Apache-2.0" if any("Apache License" in p.read_text() for p in licenses) else "MIT"
        for module in PACKAGE_MODULES.get(name, []):
            attributions.append({"module": module, "source": pin["location"],
                                 "revision": pin["state"]["revision"], "license": kind})
    for name in ["metal-cpp/LICENSE.txt", "fmt/LICENSE", "mlx-c/LICENSE", "json/LICENSE.MIT", "mlx/LICENSE"]:
        target = directory / "mlx-native" / name; target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(cache / "checkouts/mlx-swift/Source/Cmlx" / name, target)
    for source in sorted(CANDIDATE.glob("*.patch")):
        shutil.copyfile(source, directory / source.name)
    project_license = snapshot / "packages/LICENSE"
    license_reference = "Project license unspecified; local validation only"
    license_status = "Local validation only; no SDK source redistribution license is established.\n"
    if project_license.is_file():
        target = directory / "ondevice-ai-kit/LICENSE"
        target.parent.mkdir()
        shutil.copyfile(project_license, target)
        license_reference = "See notices/ondevice-ai-kit/LICENSE"
        license_status = "Local validation only; project license terms are included in notices/ondevice-ai-kit/LICENSE.\n"
    (directory / "LOCAL-VALIDATION-ONLY.txt").write_text(
        license_status +
        "The two exact compatibility patches modify only isolated dependency copies.\n"
        "OrderedCollections: target-scoped textual interface selector workaround.\n"
        "MLXLMCommon/Gemma4: five unknown future attention-mask cases intentionally trap.\n")
    for module in SDK_MODULES:
        attributions.append({"module": module, "source": "https://github.com/AmatsuZero/ondevice-ai-kit",
                             "revision": source_commit, "license": license_reference})
    notices = [{"path": str(p.relative_to(distribution)), "sha256": sha(p)}
               for p in sorted(directory.rglob("*")) if p.is_file()]
    return attributions, notices


def copy_public_c_module(source: Path, name: str, target: Path) -> None:
    """Copy public headers and reproduce SwiftPM's implicit C module maps."""
    target.mkdir(parents=True)
    for file in sorted(source.rglob("*")):
        if file.is_file() and (file.suffix in {".h", ".hpp"} or file.name == "module.modulemap"):
            _require(not file.is_symlink(), f"header symlink unsupported: {file}")
            destination = target / file.relative_to(source); destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(file, destination)
    if not (target / "module.modulemap").exists():
        header = "yyjson.h" if name == "yyjson" else "cLogSupport.h" if name == "cLogSupport" else None
        _require(header is not None and (target / header).is_file(), f"missing module map: {name}")
        (target / "module.modulemap").write_text(f'module {name} {{ header "{header}" export * }}\n')


def _assemble(output: Path, snapshot: Path, cache: Path, inventory: dict, lock: dict,
              products_root: Path | None = None) -> Path:
    products_root = products_root or output
    distribution = output / "distribution"; distribution.mkdir()
    toolchain = {"xcode": _output("xcodebuild", "-version"), "swift": _output("swift", "--version")}
    payload = {"schemaVersion": 2, "kind": "apple-mlxvlm-xcframework-set", "linking": "static",
               "sourceCommit": inventory["sourceCommit"], "toolchain": toolchain, "moduleClosure": MODULES,
               "artifacts": [], "resources": [], "cModules": [], "linkSettings": {
                   "libraries": ["c++"], "frameworks": ["Accelerate", "Metal"]}}
    provenance = distribution / "provenance"; provenance.mkdir()
    shutil.copyfile(snapshot / "source-inventory.json", provenance / "source-inventory.json")
    shutil.copyfile(CANDIDATE / "manifest.json", provenance / "overlay-manifest.json")
    for platform, sdk in [("device", "iphoneos"), ("simulator", "iphonesimulator")]:
        products = products_root / f"derived-{platform}/Build/Products/Release-{sdk}"
        expected = {m + ".o" for m in MODULES} | {m + ".o" for objects in NATIVE_OBJECTS.values() for m in objects}
        _require({p.name for p in products.glob("*.o")} == expected, f"unexpected object closure: {platform}")
        for module in MODULES:
            framework_slice(products, module, output / "frameworks" / platform / (module + ".framework"),
                            NATIVE_OBJECTS.get(module, []))
        resource_root = distribution / "resources" / platform; resource_root.mkdir(parents=True)
        for bundle in sorted(products.glob("*.bundle")):
            shutil.copytree(bundle, resource_root / bundle.name)
        log = products_root / (platform + "-build.log")
        compiler_match = re.search(r"\n    (\S+/metal) -c ", log.read_text())
        _require(compiler_match is not None, f"missing actual Metal command: {platform}")
        compiler = Path(compiler_match.group(1))
        metal = resource_root / "mlx-swift_Cmlx.bundle/default.metallib"
        receipt = {"sourceCommit": inventory["sourceCommit"], "lockSHA256": inventory["lockSHA256"],
                   "toolchain": toolchain, "platform": platform,
                   "sdkVersion": _output("xcrun", "--sdk", sdk, "--show-sdk-version"),
                   "sdkBuild": _output("xcrun", "--sdk", sdk, "--show-sdk-build-version"),
                   "metalCompiler": _output(str(compiler), "--version"), "metalCompilerSHA256": sha(compiler),
                   "sourceInventorySHA256": sha(provenance / "source-inventory.json"),
                   "overlayManifestSHA256": sha(provenance / "overlay-manifest.json"),
                   "metalOutputSHA256": sha(metal), "buildLogSHA256": sha(log), "dependencyPins": lock["pins"]}
        receipt_path = provenance / (platform + ".json"); _json(receipt_path, receipt)
        for resource in sorted(resource_root.rglob("*")):
            if not resource.is_file():
                continue
            entry = {"path": str(resource.relative_to(distribution)), "sha256": sha(resource), "platform": platform}
            if resource.name == "default.metallib":
                entry["provenance"] = {"path": str(receipt_path.relative_to(distribution)), "sha256": sha(receipt_path)}
            payload["resources"].append(entry)
    for module in MODULES:
        artifact = distribution / "xcframeworks" / (module + ".xcframework"); artifact.parent.mkdir(exist_ok=True)
        _run(output / (module + "-xcframework.log"), "xcodebuild", "-create-xcframework",
             "-framework", str(output / "frameworks/device" / (module + ".framework")),
             "-framework", str(output / "frameworks/simulator" / (module + ".framework")), "-output", str(artifact))
        inspect_xcframework(artifact, module, "18.0")
        metadata = plistlib.loads((artifact / "Info.plist").read_bytes())
        payload["artifacts"].append({"path": str(artifact.relative_to(distribution)), "modules": [module],
            "minimumOS": "18.0", "linking": "static", "sha256": tree(artifact),
            "slices": [{"path": p["LibraryIdentifier"], "architectures": p["SupportedArchitectures"],
                        "platform": "iOS Simulator" if p.get("SupportedPlatformVariant") == "simulator" else "iOS"}
                       for p in metadata["AvailableLibraries"]],
            "interfaces": [{"path": str(p.relative_to(artifact)), "sha256": sha(p)}
                           for p in sorted(artifact.rglob("*.swiftinterface")) if not p.name.endswith(".private.swiftinterface")]})
    for name, (relative, owner) in C_HEADERS.items():
        source = cache / "checkouts" / relative; target = distribution / "CModules" / name
        copy_public_c_module(source, name, target)
        payload["cModules"].append({"name": name, "owner": owner, "path": str(target.relative_to(distribution)),
                                   "sha256": tree(target), "moduleMapSHA256": sha(target / "module.modulemap")})
    payload["sourceAttributions"], payload["notices"] = _notices(distribution, cache, lock, inventory["sourceCommit"], snapshot)
    manifest = distribution / "artifact-manifest.json"; _json(manifest, payload)
    validate_manifest(manifest, distribution)
    archives = output / "archives"; archives.mkdir()
    checksums = {}
    for module in MODULES:
        artifact = distribution / "xcframeworks" / (module + ".xcframework")
        archive = archives / (module + ".zip")
        with zipfile.ZipFile(archive, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=1) as stream:
            for file in sorted(artifact.rglob("*")):
                if file.is_file():
                    stream.write(file, str(file.relative_to(artifact.parent)))
        checksums[archive.name] = _output("swift", "package", "compute-checksum", str(archive))
        _require(checksums[archive.name] == sha(archive), "SwiftPM checksum mismatch")
    _json(output / "checksums.json", checksums)
    return manifest


def package(snapshot: Path, cache: Path, output: Path) -> Path:
    """Build in fresh output only; never patch, fetch, publish or change input sources."""
    output = Path(output).absolute()
    _require(not output.exists() and not output.is_symlink(), f"package output exists: {output}")
    snapshot, cache = Path(snapshot).resolve(strict=True), Path(cache).resolve(strict=True)
    for source in (snapshot, cache):
        _require(not output.resolve().is_relative_to(source) and not source.is_relative_to(output.resolve()),
                 "package output overlaps an input")
    inventory = validate_snapshot(snapshot)
    lock = validate_cache(cache, snapshot)
    output.mkdir(parents=True)
    _json(output / "status.json", {"status": "building", "release": "not-ready"})
    try:
        host = output / "host"; host.mkdir()
        (host / "App.swift").write_text('import SwiftUI\nimport ODAIKitAppleMLXVLM\n@main struct Host: App { var body: some Scene { WindowGroup { Text("F09 Build Host") } } }\n')
        spec = {"name": "F09BuildHost", "options": {"deploymentTarget": {"iOS": "18.0"}},
                "packages": {"AppleMLX": {"path": str(snapshot / "packages/apple-mlx")}},
                "targets": {"F09BuildHost": {"type": "application", "platform": "iOS", "sources": ["App.swift"],
                    "dependencies": [{"package": "AppleMLX", "product": "ODAIKitAppleMLXVLM"}],
                    "settings": {"base": {"PRODUCT_BUNDLE_IDENTIFIER": "io.ondeviceai.f09buildhost",
                        "GENERATE_INFOPLIST_FILE": "YES", "SWIFT_VERSION": "6.0", "CODE_SIGNING_ALLOWED": "NO"}}}}}
        _json(host / "project.json", spec)
        _run(output / "generate.log", "xcodegen", "generate", "--spec", str(host / "project.json"), "--project", str(host))
        resolved = host / "F09BuildHost.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved"
        resolved.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(snapshot / "packages/apple-mlx/Package.resolved", resolved)
        for platform, destination in [("device", "generic/platform=iOS"), ("simulator", "generic/platform=iOS Simulator")]:
            _run(output / (platform + "-build.log"), "xcodebuild", "-project", str(host / "F09BuildHost.xcodeproj"),
                 "-scheme", "F09BuildHost", "-configuration", "Release", "-destination", destination,
                 "-derivedDataPath", str(output / ("derived-" + platform)), "-clonedSourcePackagesDirPath", str(cache),
                 "-disableAutomaticPackageResolution", "-onlyUsePackageVersionsFromResolvedFile", "-skipPackageUpdates",
                 "BUILD_LIBRARY_FOR_DISTRIBUTION=YES", "CODE_SIGNING_ALLOWED=NO", "ARCHS=arm64",
                 "IPHONEOS_DEPLOYMENT_TARGET=18.0", "build")
            print(platform + " source build passed", flush=True)
        _require(validate_snapshot(snapshot) == inventory and validate_cache(cache, snapshot) == lock,
                 "build inputs changed during build")
        manifest = _assemble(output, snapshot, cache, inventory, lock)
        _json(output / "status.json", {"status": "local-artifacts-built", "release": "not-ready",
                                      "artifactManifest": str(manifest.relative_to(output)),
                                      "artifactManifestSHA256": sha(manifest), "consumerBuild": "not-run",
                                      "runtime": "not-run", "publication": "not-run"})
        return manifest
    except Exception as error:
        _json(output / "status.json", {"status": "failed", "release": "not-ready", "errorType": type(error).__name__})
        raise


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot", type=Path, help="existing committed-source snapshot")
    parser.add_argument("cache", type=Path, help="existing isolated SourcePackages with adopted exact overlay")
    parser.add_argument("output", type=Path, help="fresh output directory")
    args = parser.parse_args()
    try:
        print(package(args.snapshot, args.cache, args.output))
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
