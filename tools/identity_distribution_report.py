#!/usr/bin/env python3
"""Inspect a native Identity XCFramework; report packaging facts, never biometric quality."""
import argparse
import hashlib
import json
from pathlib import Path
import plistlib
import re

CONTRACTS = ["ModelBinding", "ThresholdPolicy", "PADPolicy"]
WEIGHT_SUFFIXES = {".safetensors", ".onnx", ".tflite", ".mlmodel", ".mlmodelc", ".mlpackage", ".pt", ".pth", ".gguf"}
NON_NATIVE_IMPORTS = {"ODAIKitDemo", "ODAIKitBinding", "Compose", "MLX", "MLXVLM", "Azure", "MicrosoftCognitiveServicesSpeech"}


def report(artifact):
    artifact = Path(artifact).resolve(strict=True)
    if any(path.suffix.lower() in WEIGHT_SUFFIXES for path in artifact.rglob("*")):
        raise ValueError("Model weights must not be bundled in the Identity contract artifact")
    metadata = plistlib.loads((artifact / "Info.plist").read_bytes())
    libraries = metadata.get("AvailableLibraries", [])
    if not libraries:
        raise ValueError("No artifact slices")
    slices = []
    for library in libraries:
        framework = (artifact / library["LibraryIdentifier"] / library["LibraryPath"]).resolve(strict=True)
        if not framework.is_relative_to(artifact):
            raise ValueError("Artifact slice escapes its root")
        interfaces = sorted(path for path in (framework / "Modules").rglob("*.swiftinterface")
                            if not path.name.endswith(".private.swiftinterface"))
        if not interfaces:
            raise ValueError("Missing public Swift interface")
        imports = set()
        hashes = {}
        for path in interfaces:
            source = path.read_text()
            for contract in CONTRACTS:
                if not re.search(r"public struct " + contract + r"\b", source):
                    raise ValueError("Missing binding contract: " + contract)
            imports.update(re.findall(r"^import ([A-Za-z_][A-Za-z_0-9]*)", source, re.MULTILINE))
            hashes[str(path.relative_to(artifact))] = hashlib.sha256(path.read_bytes()).hexdigest()
        if imports & NON_NATIVE_IMPORTS:
            raise ValueError("Non-native dependency: " + ", ".join(sorted(imports & NON_NATIVE_IMPORTS)))
        binary = framework / "ODAIKitIdentity"
        slices.append({"identifier": library["LibraryIdentifier"],
                       "platform": library["SupportedPlatform"],
                       "variant": library.get("SupportedPlatformVariant"),
                       "binarySha256": hashlib.sha256(binary.read_bytes()).hexdigest(),
                       "interfaceSha256": hashes, "imports": sorted(imports)})
    return {"schemaVersion": 1, "product": "ODAIKitIdentity", "bindingContracts": CONTRACTS,
            "selectedProductionModel": None, "calibrationEvidence": None,
            "bundledModelWeightFiles": [], "slices": slices}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", type=Path)
    args = parser.parse_args()
    print(json.dumps(report(args.artifact), indent=2, sort_keys=True))
