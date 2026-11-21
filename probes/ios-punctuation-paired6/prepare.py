#!/usr/bin/env python3
"""Prepare pinned local sources/fixtures; never sign, install or recognize."""
import hashlib
import json
from pathlib import Path

PROBE = Path(__file__).resolve().parent
ROOT = PROBE.parents[1]
TASK = ROOT / ".omx/artifacts/w3-punctuation-paired6-preparation-20261116"
OLD_FIXTURES = ROOT / ".omx/artifacts/w3-apple65-probe-preparation-20261116/AudioFixtures"
INSERTION = "\n        if #available(iOS 16.0, macOS 13.0, *) {\n            request.addsPunctuation = true\n        }"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write_once(path, data):
    if path.is_symlink():
        raise ValueError("refusing symlink: " + str(path))
    if path.exists():
        if path.read_bytes() != data:
            raise ValueError("preserving differing existing bytes: " + str(path))
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("xb") as stream:
        stream.write(data)


def main():
    contract = json.loads((PROBE / "execution-contract.json").read_bytes())
    binding = json.loads((PROBE / "runtime-source-binding.json").read_bytes())
    for row in binding["inputs"]:
        data = (ROOT / row["source"]).read_bytes()
        if sha(data) != row["sourceSHA256"]:
            raise ValueError("production source changed: " + row["source"])
        if row["destination"] == "RuntimeSources/PunctuationArmB/AppleSpeechRecognizerBackend.swift":
            needle = b"        request.shouldReportPartialResults = true"
            if data.count(needle) != 1:
                raise ValueError("ambiguous insertion")
            data = data.replace(needle, needle + INSERTION.encode(), 1)
        if sha(data) != row["preparedSHA256"]:
            raise ValueError("prepared source identity: " + row["destination"])
        write_once(TASK / row["destination"], data)

    old = (OLD_FIXTURES / "manifest.json").read_bytes()
    if sha(old) != "99584dec071c0e8a3b47f510443e002d80058482cb48d580122a0af88c86a1fb":
        raise ValueError("historical fixture manifest changed")
    manifest = json.loads(old)
    manifest["runId"] = contract["runId"]
    data = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode()
    if sha(data) != contract["manifestSHA256"]:
        raise ValueError("new manifest identity")
    write_once(TASK / "AudioFixtures/manifest.json", data)
    for clip in manifest["cases"]:
        data = (OLD_FIXTURES / clip["wavPath"]).read_bytes()
        if sha(data) != clip["wavSHA256"]:
            raise ValueError("WAV identity: " + clip["id"])
        write_once(TASK / "AudioFixtures" / clip["wavPath"], data)

    sources = sorted((TASK / "RuntimeSources").rglob("*.swift"))
    sources += [PROBE / name for name in ("Apple65Core.swift", "PunctuationPlan.swift", "W3Apple65App.swift", "project.yml")]
    identity = dict(schemaVersion=1, runId=contract["runId"], backendId="apple.speech.recognizer",
                    manifestSHA256=contract["manifestSHA256"],
                    inputBindingSHA256="b3838b23136aa1be8c760915b565a2f192976ae3299631fcd3470f8df5334e88",
                    sourceBaseCommit=binding["sourceBaseCommit"],
                    selectedSources=[dict(path=str(path.relative_to(ROOT)), sha256=sha(path.read_bytes())) for path in sources],
                    distributionBoundary="isolated-punctuation-paired6-not-production")
    write_once(PROBE / "source-identity-manifest.json", (json.dumps(identity, indent=2) + "\n").encode())
    print("Prepared 14 runtime source copies, 65 unchanged WAVs and 18 source/config pins. No device operation.")


if __name__ == "__main__":
    main()
