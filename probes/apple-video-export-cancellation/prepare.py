#!/usr/bin/env python3
"""Prepare an instrumented diagnostic copy; never edit the shipping mux."""
import hashlib
import json
import sys
from pathlib import Path


def prepare(destination: Path) -> None:
    repo = Path(__file__).resolve().parents[2]
    source = repo / "apple/Sources/ODAIKitAppleVideo/AppleVideoAudioMuxer.swift"
    profile = source.with_name("AppleVideoProfile.swift")
    anchor = "            try await exporter.export(to: output, as: .mov)"
    replacement = """            let diagnosticObservation = ExportProbeContext.observation
            let diagnosticToken = diagnosticObservation?.observe(exporter)
            defer { diagnosticToken?.invalidate() }
            do {
                try await exporter.export(to: output, as: .mov)
            } catch {
                diagnosticObservation?.returned(exporter, output: output, error: error)
                throw error
            }
            diagnosticObservation?.returned(exporter, output: output, error: nil)"""
    original = source.read_text()
    if original.count(anchor) != 1:
        raise ValueError("mux export anchor changed; inspect the current source before instrumenting")
    probe_sources = sorted(Path(__file__).resolve().parent.glob("*.swift"))
    inputs = [source, profile, *probe_sources,
              repo / "apple/Tests/ODAIKitAppleVideoTests/Fixtures/aac.mp4",
              repo / "apple/Tests/ODAIKitAppleVideoTests/Fixtures/video-only.mov"]
    hashes = {str(path.relative_to(repo)): hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs}
    destination.mkdir()  # Fails for any existing file, directory, or symlink.
    instrumented = original.replace(anchor, replacement)
    (destination / source.name).write_text(instrumented)
    (destination / profile.name).write_bytes(profile.read_bytes())
    for path in inputs[2:]:
        (destination / path.name).write_bytes(path.read_bytes())
    manifest = {"schemaVersion": 1, "sourceHashes": hashes, "replacement": replacement,
                "originalAnchor": anchor, "instrumentedSHA256": hashlib.sha256(instrumented.encode()).hexdigest()}
    (destination / "source-manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: prepare.py NEW_DIAGNOSTIC_DIRECTORY")
    prepare(Path(sys.argv[1]))
