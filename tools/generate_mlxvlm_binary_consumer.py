"""Generate a relocatable binary-only consumer; never resolve packages or build."""

from __future__ import annotations

import argparse
import ctypes
import errno
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tempfile

from tools.inspect_mlxvlm_xcframework import inspect_xcframework
from tools.validate_mlxvlm_binary_artifact import validate_manifest, _sha256_tree


REPORT_WRITER = '''enum ReportWriteFailure: Error { case missingDirectory }

func persistResourceReport(_ report: [String: String], directory: URL?) -> [String: String] {
    do {
        guard let directory else { throw ReportWriteFailure.missingDirectory }
        let data = try JSONSerialization.data(withJSONObject: report, options: [.sortedKeys])
        try data.write(to: directory.appendingPathComponent("binary-resource-report.json"), options: .atomic)
        return report
    } catch {
        var failure = report
        failure["status"] = "failed-report-write"
        // Only the error type is exposed, never paths or payloads.
        failure["errorType"] = String(reflecting: type(of: error))
        return failure
    }
}
'''


APP = '''import SwiftUI
import Foundation
import CryptoKit
import Metal
import ODAIKitAppleMLXVLM
import ODAIKitMultimodal

// Compiled against binary interfaces, never called by the resource-only smoke.
func imageAPI(_ backend: MLXVLMBackend, file: URL) async throws {
    let request = try MultimodalRequest(imageFile: file, prompt: "binary interface probe")
    _ = try await backend.generate(request, update: nil)
}
func videoAPI(_ backend: MLXVLMBackend, file: URL) async throws {
    let request = try MultimodalVideoRequest(videoFile: file, prompt: "binary interface probe")
    _ = try await backend.generateVideo(request, update: nil)
}

enum ResourceFailure: Error { case missingBundle, hashMismatch, unavailableMetal }

func resource(bundle name: String, file: String, ext: String, expected: String) throws -> URL {
    guard let root = Bundle.main.url(forResource: name, withExtension: "bundle"),
          let bundle = Bundle(url: root),
          let url = bundle.url(forResource: file, withExtension: ext) else { throw ResourceFailure.missingBundle }
    let digest = SHA256.hash(data: try Data(contentsOf: url)).map { String(format: "%02x", $0) }.joined()
    guard digest == expected else { throw ResourceFailure.hashMismatch }
    return url
}

func resourceSmoke() -> [String: String] {
    var report = ["scope": "binary-resource-only", "inference": "not-run", "status": "failed",
                  "artifactManifestSHA256": ARTIFACT_MANIFEST_HASH]
    do {
        _ = try resource(bundle: MODEL_BUNDLE, file: "smolvlm2-model", ext: "json", expected: MODEL_HASH)
        let manifest = try SmolVLM2Model.manifest() // Actual SDK Bundle.module lookup.
        // Manifest modelId is the local store key, not the upstream repository ID.
        guard manifest.upstreamURL.path == "/" + SmolVLM2Model.identity.modelId,
              manifest.upstreamRevision == SmolVLM2Model.identity.modelRevision,
              manifest.backendId == SmolVLM2Model.backendId else { throw ResourceFailure.hashMismatch }
        report["modelBundleLookup"] = "passed"
        let metal = try resource(bundle: METAL_BUNDLE, file: "default", ext: "metallib", expected: METAL_HASH)
        guard let device = MTLCreateSystemDefaultDevice() else { throw ResourceFailure.unavailableMetal }
        _ = try device.makeLibrary(URL: metal)
        report["metalLibraryLoad"] = "passed"
        report["status"] = "passed"
    } catch {
        // Error type only: no filesystem paths or user payloads.
        report["errorType"] = String(reflecting: type(of: error))
    }
    return report
}

REPORT_WRITER

@main struct MLXVLMBinaryConsumerApp: App {
    @State private var status = "Resource smoke not run"
    var body: some Scene {
        WindowGroup {
            Text(status).padding().task {
                let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
                let report = persistResourceReport(resourceSmoke(), directory: directory)
                status = report["status"] ?? "failed"
            }
        }
    }
}
'''.replace("REPORT_WRITER", REPORT_WRITER)


def _publish_exclusive(source: Path, destination: Path) -> None:
    """Publish in one exclusive rename operation; never fall back to overwrite."""
    library = ctypes.CDLL(None, use_errno=True)
    paths = (os.fsencode(source), os.fsencode(destination))
    try:
        if sys.platform == "darwin":
            rename = library.renamex_np
            rename.argtypes = [ctypes.c_char_p, ctypes.c_char_p, ctypes.c_uint]
            arguments = (*paths, 0x00000004)  # Apple RENAME_EXCL.
        elif sys.platform.startswith("linux"):
            rename = library.renameat2
            rename.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_int,
                               ctypes.c_char_p, ctypes.c_uint]
            arguments = (-100, paths[0], -100, paths[1], 1)  # AT_FDCWD, RENAME_NOREPLACE.
        else:
            raise OSError(errno.ENOTSUP, "exclusive directory publication is unsupported")
    except AttributeError as error:
        raise OSError(errno.ENOTSUP, "exclusive rename API is unavailable") from error
    rename.restype = ctypes.c_int
    if rename(*arguments) != 0:
        code = ctypes.get_errno()
        raise OSError(code, os.strerror(code), str(destination))


def generate(artifact_root: Path, manifest_path: Path, output: Path) -> None:
    """Validate/copy explicit artifacts and generate a source-free Xcode project."""
    output = Path(output).absolute()
    if output.exists() or output.is_symlink():
        raise ValueError(f"consumer output already exists: {output}")
    root = Path(artifact_root).resolve(strict=True)
    manifest_path = Path(manifest_path).resolve(strict=True)
    manifest_bytes = manifest_path.read_bytes()
    manifest_hash = hashlib.sha256(manifest_bytes).hexdigest()
    payload = json.loads(manifest_bytes.decode("utf-8"))
    if not isinstance(payload, dict):
        raise ValueError("manifest must be an object")
    for field in ("artifacts", "resources"):
        entries = payload.get(field)
        if not isinstance(entries, list) or any(not isinstance(entry, dict) for entry in entries):
            raise ValueError(f"{field} must be an array of objects")
    for entry in payload.get("artifacts", []) + payload.get("resources", []):
        value = entry.get("path")
        if not isinstance(value, str) or Path(value).is_absolute() or ".." in Path(value).parts or str(Path(value)) != value:
            raise ValueError(f"artifact/resource path must be canonical and relative: {value}")
    # The validator accepts a path. Give it the same bytes used for the copy,
    # Swift constant and receipt even if the caller's manifest changes later.
    with tempfile.TemporaryDirectory(prefix="mlxvlm-manifest-") as directory:
        frozen_manifest = Path(directory) / "manifest.json"
        frozen_manifest.write_bytes(manifest_bytes)
        validate_manifest(frozen_manifest, root)
    static = payload.get("schemaVersion") == 2
    auxiliary = payload.get("cModules", []) + payload.get("notices", []) if static else []
    if static:
        auxiliary += [r["provenance"] for r in payload["resources"] if "provenance" in r]
    for entry in auxiliary:
        value = entry["path"]
        if Path(value).is_absolute() or ".." in Path(value).parts or str(Path(value)) != value:
            raise ValueError(f"auxiliary path must be canonical and relative: {value}")
        path = root / value
        if path.is_symlink() or any(p.is_symlink() for p in path.rglob("*")):
            raise ValueError(f"auxiliary symlink prevents relocation: {value}")
    if not {"ODAIKitAppleMLXVLM", "ODAIKitMultimodal"}.issubset(payload["moduleClosure"]):
        raise ValueError("binary consumer requires AppleMLXVLM and Multimodal modules")
    for entry in payload["artifacts"]:
        if len(entry["modules"]) != 1:
            raise ValueError("one framework module per artifact is required")
        for child in (root / entry["path"]).rglob("*"):
            if child.is_symlink() and child.readlink().is_absolute():
                raise ValueError(f"absolute artifact symlink prevents relocation: {child}")
        inspect_xcframework(root / entry["path"], entry["modules"][0], entry["minimumOS"])

    # SwiftPM bundle names are part of Bundle.module's runtime contract. Keep
    # bundles intact and copy them to the App's resource root, not loose files.
    bundles: dict[tuple[str, str], Path] = {}
    bundle_paths: set[Path] = set()
    canonical: dict[tuple[str, str], dict] = {}
    platform_bundles: dict[str, set[Path]] = {"device": set(), "simulator": set()}
    for resource in payload["resources"]:
        path = root / resource["path"]
        parents = [p for p in path.parents if p != root and p.is_relative_to(root) and p.suffix == ".bundle"]
        if not parents:
            raise ValueError(f"resource must reside in its runtime .bundle: {resource['path']}")
        bundle = parents[0]
        if not re.fullmatch(r"[A-Za-z0-9_.-]+", bundle.name):
            raise ValueError(f"unsupported resource bundle name: {bundle.name}")
        for child in bundle.rglob("*"):
            if child.is_symlink():
                raise ValueError(f"resource bundle symlinks are unsupported: {child}")
        platform = resource["platform"] if static else "all"
        key = (platform, bundle.name)
        previous = bundles.get(key)
        if previous is not None and _sha256_tree(previous) != _sha256_tree(bundle):
            raise ValueError(f"conflicting resource bundle copies: {bundle.name}")
        bundles[key] = bundle
        bundle_paths.add(bundle)
        if static:
            platform_bundles[platform].add(bundle)
        if path.name in {"smolvlm2-model.json", "default.metallib"}:
            if path.parent != bundle:
                raise ValueError(f"canonical resource must be at bundle root: {resource['path']}")
            canonical[(platform, path.name)] = {"bundle": bundle.stem, "sha256": resource["sha256"]}

    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".mlxvlm-binary-consumer-", dir=output.parent) as scratch:
        stage = Path(scratch) / "consumer"
        stage.mkdir()
        dependencies = []
        for entry in payload["artifacts"]:
            relative = Path("artifacts") / entry["path"]
            destination = stage / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(root / entry["path"], destination, symlinks=True)
            dependencies.append({"framework": str(relative), **({"embed": False} if static else {})})
        for bundle in sorted(bundle_paths):
            relative = Path("artifacts") / bundle.relative_to(root)
            destination = stage / relative
            if not destination.exists():
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copytree(bundle, destination)
        for entry in auxiliary:
            source_path = root / entry["path"]
            target = stage / "artifacts" / entry["path"]
            if not target.exists():
                target.parent.mkdir(parents=True, exist_ok=True)
                if source_path.is_dir():
                    shutil.copytree(source_path, target)
                else:
                    shutil.copyfile(source_path, target)
        resources = [] if static else [
            {"path": str(Path("artifacts") / bundle.relative_to(root)), "buildPhase": "resources", "type": "file"}
            for bundle in bundles.values()]
        (stage / "artifact-manifest.json").write_bytes(manifest_bytes)
        validate_manifest(stage / "artifact-manifest.json", stage / "artifacts")
        source = APP.replace("ARTIFACT_MANIFEST_HASH", json.dumps(manifest_hash))
        def constants(platform):
            return {
                "MODEL_BUNDLE": canonical[(platform, "smolvlm2-model.json")]["bundle"],
                "MODEL_HASH": canonical[(platform, "smolvlm2-model.json")]["sha256"],
                "METAL_BUNDLE": canonical[(platform, "default.metallib")]["bundle"],
                "METAL_HASH": canonical[(platform, "default.metallib")]["sha256"],
            }
        if static:
            declarations = "#if targetEnvironment(simulator)\n"
            declarations += "\n".join(f"let {k} = {json.dumps(v)}" for k, v in constants("simulator").items())
            declarations += "\n#else\n"
            declarations += "\n".join(f"let {k} = {json.dumps(v)}" for k, v in constants("device").items())
            declarations += "\n#endif\n\n"
            source = source.replace("// Compiled against", declarations + "// Compiled against", 1)
        else:
            for marker, value in constants("all").items():
                source = source.replace(marker, json.dumps(value))
        (stage / "ConsumerApp.swift").write_text(source)
        spec = {"name": "MLXVLMBinaryConsumer", "options": {"deploymentTarget": {"iOS": "18.0"}},
                "targets": {"MLXVLMBinaryConsumer": {
                    "type": "application", "platform": "iOS",
                    "sources": ["ConsumerApp.swift", *resources], "dependencies": dependencies,
                    "settings": {"base": {"PRODUCT_BUNDLE_IDENTIFIER": "io.ondeviceai.mlxvlmbinaryconsumer",
                        "SWIFT_VERSION": "6.0", "ARCHS": "arm64", "GENERATE_INFOPLIST_FILE": "YES",
                        "CODE_SIGNING_ALLOWED": "NO", "INFOPLIST_KEY_UILaunchScreen_Generation": "YES"}}}}}
        if static:
            target = spec["targets"]["MLXVLMBinaryConsumer"]
            c_paths = [str(Path("artifacts") / entry["path"]) for entry in payload["cModules"]]
            target["settings"]["base"].update({
                "SWIFT_INCLUDE_PATHS": ["$(SRCROOT)/" + p for p in sorted({str(Path(p).parent) for p in c_paths})],
                "HEADER_SEARCH_PATHS": ["$(SRCROOT)/" + p for p in c_paths],
                "OTHER_LDFLAGS": "$(inherited) -lc++ -framework Accelerate -framework Metal",
                "ENABLE_USER_SCRIPT_SANDBOXING": "NO",
            })
            script = 'set -eu\ncase "$PLATFORM_NAME" in\n'
            for platform, sdk in (("device", "iphoneos"), ("simulator", "iphonesimulator")):
                script += sdk + ")\n"
                for bundle in sorted(platform_bundles[platform]):
                    relative = str(Path("artifacts") / bundle.relative_to(root))
                    script += 'cp -R -- "$SRCROOT"/' + shlex.quote(relative) + ' "$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/"\n'
                script += ";;\n"
            script += '*) echo "Unsupported resource platform" >&2; exit 1 ;;\nesac\n'
            target["postBuildScripts"] = [{"name": "Copy platform resource bundles", "script": script,
                                            "basedOnDependencyAnalysis": False}]
        # JSON is a YAML subset understood by the already-installed XcodeGen.
        (stage / "project.json").write_text(json.dumps(spec, indent=2) + "\n")
        subprocess.run(["xcodegen", "generate", "--spec", str(stage / "project.json"), "--project", str(stage)],
                       check=True, capture_output=True, text=True)
        receipt = {"schemaVersion": 1, "kind": "apple-mlxvlm-binary-consumer",
                   "sourceCommit": payload["sourceCommit"], "artifactManifestSHA256": manifest_hash,
                   "buildStatus": "not-run", "runtimeStatus": "not-run", "sourcePackages": False}
        (stage / "consumer-inputs.json").write_text(json.dumps(receipt, indent=2) + "\n")
        _publish_exclusive(stage, output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact_root", type=Path)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    try:
        generate(args.artifact_root, args.manifest, args.output)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"{error}\n")
