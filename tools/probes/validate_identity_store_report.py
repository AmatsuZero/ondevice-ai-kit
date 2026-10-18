#!/usr/bin/env python3
"""Reject stale, incomplete, or unsuccessful iOS Store probe exports."""
import argparse
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--expect-forced-failure", action="store_true")
    args = parser.parse_args()
    try:
        report = json.loads(args.report.read_text())
        required_flags = {
            "applicationSupportRoot", "backupExcluded", "encryptedFiles",
            "keychainAttributes", "cleanup",
        }
        if not args.run_id or report.get("runID") != args.run_id:
            raise ValueError("report runID does not match this launch")
        if report.get("probe") != "identityStore" or report.get("sameProcessOnly") is not True:
            raise ValueError("unexpected probe contract")
        forced = args.expect_forced_failure
        if report.get("forceFailure") is not forced:
            raise ValueError("unexpected failure mode")
        if report.get("status") != ("fail" if forced else "pass"):
            raise ValueError("report has no expected terminal status")
        if report.get("errorCode") != ("forcedFailure" if forced else None):
            raise ValueError("unexpected error code")
        if not forced:
            required_flags.update({
                "sameProcessMembershipAndDecrypt", "receiptAndTombstonePersisted",
                "domainRevocationPersisted",
            })
            if report.get("counts", {}).get("managedKeyCount") != 3:
                raise ValueError("unexpected managed key count")
        if not all(report.get("flags", {}).get(flag) is True for flag in required_flags):
            raise ValueError("required lifecycle or cleanup flag is missing")
    except (OSError, ValueError, TypeError, AttributeError) as error:
        parser.exit(1, f"Store probe report rejected: {error}\n")
    print("Store probe report verified for the expected launch and outcome")


if __name__ == "__main__":
    main()
