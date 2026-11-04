"""Generate a relocatable binary-only consumer; never resolve packages or build."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

from tools.inspect_mlxvlm_xcframework import inspect_xcframework
from tools.validate_mlxvlm_binary_artifact import validate_manifest, _sha256_tree


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
    var report = ["scope": "binary-resource-only", "inference": "not-run", "status": "failed"]
    do {
        _ = try resource(bundle: MODEL_BUNDLE, file: "smolvlm2-model", ext: "json", expected: MODEL_HASH)
        let manifest = try SmolVLM2Model.manifest() // Actual SDK Bundle.module lookup.
        guard manifest.modelId == SmolVLM2Model.identity.modelId else { throw ResourceFailure.hashMismatch }
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

@main struct MLXVLMBinaryConsumerApp: App {
    @State private var status = "Resource smoke not run"
    var body: some Scene {
        WindowGroup {
            Text(status).padding().task {
                let report = resourceSmoke()
                status = report["status"] ?? "failed"
                if let data = try? JSONSerialization.data(withJSONObject: report, options: [.sortedKeys]),
                   let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
                    try? data.write(to: directory.appendingPathComponent("binary-resource-report.json"), options: .atomic)
                }
            }
        }
    }
}
'''


def generate(artifact_root: Path, manifest_path: Path, output: Path) -> None:
    """Validate/copy explicit artifacts and generate a source-free Xcode project."""
    output = Path(output).absolute()
    if output.exists() or output.is_symlink():
        raise ValueError(f"consumer output already exists: {output}")
    root = Path(artifact_root).resolve(strict=True)
    manifest_path = Path(manifest_path).resolve(strict=True)
    payload = json.loads(manifest_path.read_text())
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
    validate_manifest(manifest_path, root)
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
    bundles: dict[str, Path] = {}
    bundle_paths: set[Path] = set()
    canonical: dict[str, dict] = {}
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
        previous = bundles.get(bundle.name)
        if previous is not None and _sha256_tree(previous) != _sha256_tree(bundle):
            raise ValueError(f"conflicting resource bundle copies: {bundle.name}")
        bundles[bundle.name] = bundle
        bundle_paths.add(bundle)
        if path.name in {"smolvlm2-model.json", "default.metallib"}:
            if path.parent != bundle:
                raise ValueError(f"canonical resource must be at bundle root: {resource['path']}")
            canonical[path.name] = {"bundle": bundle.stem, "sha256": resource["sha256"]}

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
            dependencies.append({"framework": str(relative)})
        for bundle in sorted(bundle_paths):
            relative = Path("artifacts") / bundle.relative_to(root)
            destination = stage / relative
            if not destination.exists():
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copytree(bundle, destination)
        resources = [{"path": str(Path("artifacts") / bundle.relative_to(root)),
                      "buildPhase": "resources", "type": "file"} for bundle in bundles.values()]
        (stage / "artifact-manifest.json").write_bytes(manifest_path.read_bytes())
        validate_manifest(stage / "artifact-manifest.json", stage / "artifacts")
        source = APP
        for marker, value in {
            "MODEL_BUNDLE": canonical["smolvlm2-model.json"]["bundle"],
            "MODEL_HASH": canonical["smolvlm2-model.json"]["sha256"],
            "METAL_BUNDLE": canonical["default.metallib"]["bundle"],
            "METAL_HASH": canonical["default.metallib"]["sha256"],
        }.items():
            source = source.replace(marker, json.dumps(value))
        (stage / "ConsumerApp.swift").write_text(source)
        spec = {"name": "MLXVLMBinaryConsumer", "options": {"deploymentTarget": {"iOS": "18.0"}},
                "targets": {"MLXVLMBinaryConsumer": {
                    "type": "application", "platform": "iOS",
                    "sources": ["ConsumerApp.swift", *resources], "dependencies": dependencies,
                    "settings": {"base": {"PRODUCT_BUNDLE_IDENTIFIER": "io.ondeviceai.mlxvlmbinaryconsumer",
                        "SWIFT_VERSION": "6.0", "ARCHS": "arm64", "GENERATE_INFOPLIST_FILE": "YES",
                        "CODE_SIGNING_ALLOWED": "NO", "INFOPLIST_KEY_UILaunchScreen_Generation": "YES"}}}}}
        # JSON is a YAML subset understood by the already-installed XcodeGen.
        (stage / "project.json").write_text(json.dumps(spec, indent=2) + "\n")
        subprocess.run(["xcodegen", "generate", "--spec", str(stage / "project.json"), "--project", str(stage)],
                       check=True, capture_output=True, text=True)
        receipt = {"schemaVersion": 1, "kind": "apple-mlxvlm-binary-consumer",
                   "sourceCommit": payload["sourceCommit"], "artifactManifestSHA256": hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
                   "buildStatus": "not-run", "runtimeStatus": "not-run", "sourcePackages": False}
        (stage / "consumer-inputs.json").write_text(json.dumps(receipt, indent=2) + "\n")
        stage.rename(output)


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
