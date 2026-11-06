# F10 calibration manifest implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox syntax for tracking.

**Goal:** Deliver read-only dataset, declared-use and split preflight for subsequent F10 offline calibration tools.

**Architecture:** One standard-library module validates a strict manifest and local file digests. Its CLI emits a bounded aggregate report, never a verified policy. The primary agent implements and integrates; cbc hy3 supplies a short independent edge-case checklist, while the existing hy4free instrumentation lane continues.

**Tech Stack:** Python standard library, unittest, synthetic byte fixtures only.

**Spec:** `docs/superpowers/specs/2026-11-07-calibration-manifest-design.md`

## Global Constraints

- No production policy/model/dependency/lock changes, data downloads, capture, uploads or device operations.
- Authorization declarations and evidence hashes do not constitute independent permission approval.
- Preserve all existing Identity resource work and protected dirty/untracked paths.
- Every command starts with `rtk`; use unique retained evidence under `artifacts/verification/f10-calibration-manifest-20261107/`.
- Existing helper tasks keep their handles; no duration timeout, duplicate dispatch or retry of failed providers.

## Review Focus

1. Boolean/float aliases, duplicate keys or non-finite JSON must not pass a strict manifest.
2. File references must not read symlinks, special files or paths outside the declared root.
3. Genuine subject overlap is valid inside a split; calibration/test or enrollment/probe session leakage is rejected.
4. Non-distribution local use must not acquire a spurious redistribution requirement.
5. Reports must remain unverified and avoid exporting per-sample pseudonyms or paths.

### Task 1: manifest validator and CLI

**Files:** Create `tools/identity_calibration_manifest.py`, `tools/tests/test_identity_calibration_manifest.py`, `docs/validation/f10-calibration-manifest.md`.

**Interfaces:** Consumes Schema 1 in the spec. Produces `validate_manifest(document: dict, root: Path) -> dict` and the module CLI described there. No shared interfaces with the independent Android instrumentation lane.

- [x] Write synthetic success, purpose/digest/leakage/schema/path/CLI rejection tests.
- [x] Run `rtk proxy python3 -m unittest tools.tests.test_identity_calibration_manifest -v`; retain actual RED proving the missing implementation.
- [x] Implement strict validation and read-only aggregate reporting against the spec.
- [x] Run the same focused suite; expected all pass. Reproduce any newly discovered defect with RED before correcting it.
- [x] Obtain one bounded supplied-context review; preserve its exact scope and assess findings before changes. Known native/provider failures are not retried.
- [x] Run focused tests, compile check and diff check after corrections. Broaden to the tools suite once, using the documented Ruby3.4 environment and approved host Swift-cache access when required.
- [x] Document invocation, declaration boundary and remaining score/metrics/policy tasks; update the handoff and followups, verify protected hashes, commit only owned F10 files.

## Ledger

See `artifacts/verification/f10-calibration-manifest-20261107/progress.md` for task evidence and rulings. Preserve this evidence rather than deleting it at skill cleanup; it is part of the project's audit handoff.
