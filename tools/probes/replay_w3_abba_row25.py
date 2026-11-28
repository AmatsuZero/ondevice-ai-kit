#!/usr/bin/env python3
import hashlib
import importlib.util
import io
import json
import sys
import tempfile
import zipfile
from pathlib import Path

ZIP_SHA256 = "3170ead4fa64f7f65da6a72cc780df0429aed7c77aaffc41c8be73e7a115a3c0"
ARMS = ("a1", "b1", "b2", "a2")
SHARED = (
    "inputs", "sourceFingerprint", "apks", "models", "fixture",
    "sherpaJniSHA256", "onnxRuntimeSHA256", "caseManifest",
)


def check(condition, message):
    if not condition:
        raise SystemExit(message)


def main():
    if not __debug__:
        raise SystemExit("python -O is not permitted")
    check(len(sys.argv) == 2, "usage: replay_check.py ZIP")

    blob = Path(sys.argv[1]).read_bytes()
    check(hashlib.sha256(blob).hexdigest() == ZIP_SHA256, "ZIP SHA-256 mismatch")

    with tempfile.TemporaryDirectory(prefix="w3-abba-") as name:
        temp = Path(name)
        root = temp / "w3-paired-reuse-avd-20261123"
        with zipfile.ZipFile(io.BytesIO(blob)) as archive:
            archive.extractall(temp)

        canonical = root / "a2"
        launch = canonical / "launch"
        sys.path.insert(0, str(launch))
        analyzer_path = launch / "reuse_evidence.py"
        spec = importlib.util.spec_from_file_location(
            "w3_canonical_reuse_evidence", analyzer_path
        )
        check(spec is not None and spec.loader is not None, "canonical analyzer not found")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        analyze = module.analyze

        shared_values = None
        cases_bytes = None
        for arm in ARMS:
            base = root / arm
            run_id = f"w3-pair25-20261123-{arm}"
            contract_path = base / "contract.json"
            contract_bytes = contract_path.read_bytes()
            contract = json.loads(contract_bytes)

            check(contract["runId"] == run_id, f"{arm}: run id")
            check(contract["reuseEncoder"] is arm.startswith("b"), f"{arm}: reuse flag")

            values = tuple(contract[key] for key in SHARED)
            if shared_values is None:
                shared_values = values
            else:
                check(values == shared_values, f"{arm}: contract mismatch")

            for key, expected in contract["verificationFiles"].items():
                actual = hashlib.sha256((base / key).read_bytes()).hexdigest()
                check(actual == expected, f"{arm}: verification hash mismatch: {key}")
                if key.startswith("verification/") or key == "launch/phase_capture.py":
                    canonical_hash = hashlib.sha256((canonical / key).read_bytes()).hexdigest()
                    check(actual == canonical_hash, f"{arm}: canonical hash mismatch: {key}")

            case_bytes = (base / "project/test-assets/cases.json").read_bytes()
            if cases_bytes is None:
                cases_bytes = case_bytes
            else:
                check(case_bytes == cases_bytes, f"{arm}: cases.json differs")

            attempts = list((temp / run_id).glob("attempt-*"))
            check(len(attempts) == 1, f"{arm}: attempt count")
            attempt = attempts[0]

            preflight = json.loads((attempt / "preflight.json").read_text())
            check(
                preflight["contract"] == hashlib.sha256(contract_bytes).hexdigest()
                and preflight["runId"] == run_id
                and preflight["reuseEncoder"] is contract["reuseEncoder"],
                f"{arm}: preflight",
            )
            check(
                (attempt / "devices-delta.txt").read_text() == "added=\nremoved=\n",
                f"{arm}: devices",
            )

            observation = attempt / "observation"
            installed = json.loads((observation / "installed.json").read_text())
            expected_packages = {
                value["package"]: value["sha256"]
                for value in contract["apks"].values()
            }
            check(installed == expected_packages, f"{arm}: installed packages")

            resource = json.loads(
                (observation / "before-launch-resource.json").read_text()
            )
            minimum = contract["minimumInitialAvailableBytes"]
            check(
                resource["minimum"] == minimum
                and resource["availableBytes"] >= minimum,
                f"{arm}: launch resources",
            )

            result = analyze(contract, attempt)
            saved_name = "analysis-replay.json" if arm == "a1" else "analysis.json"
            saved_result = json.loads((attempt / saved_name).read_text())
            check(result == saved_result, f"{arm}: analysis")

            check(
                result["referenceMatch"]["bothExact"]
                and result["doubleRead"]
                and result["terminalAck"]
                and result["restored"]["packageListRestored"],
                f"{arm}: acceptance",
            )
            check(
                0 < result["rssPeakSampledBytes"]
                <= contract["sampledRssStopAboveBytes"],
                f"{arm}: RSS",
            )
            print(
                f"{run_id}: "
                f"{result['rssPeakSampledBytes'] / (1024 * 1024):.2f} MiB"
            )


if __name__ == "__main__":
    main()
