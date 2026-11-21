# F10 bound score evaluation implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans; helper candidates are text only.

**Goal:** Bind offline scores to manifest/model/split metadata and compute explicit descriptive metrics without calibration approval.

**Architecture:** Primary owns manifest/protocol/decision checks. A bounded cbc hy3 candidate supplies pure counts with strict inputs; the primary tests and integrates it. Existing hy4free probe task is independent and is not restarted.

**Tech Stack:** Python standard library, unittest, synthetic fixtures.

**Spec:** `docs/superpowers/specs/2026-11-07-calibration-scores-design.md`

## Global Constraints

- No real-data import, downloads, capture, thresholds/policies/models/locks, uploads or device operations.
- All data and scores used for tests are synthetic. Every report remains unverified.
- Preserve all protected paths and existing Identity resource work. Commands use rtk.
- Evidence ledger: `artifacts/verification/f10-calibration-scores-20261107/progress.md`.

## Review Focus

- Bool/float aliases, duplicate JSON and huge/non-finite numeric scores must not pass.
- Nonresponses are reported separately; empty denominators never become zero error.
- Genuine overlap within a split is valid, cross-split/purpose/model reuse is rejected.
- 1:N trial is a probe search; gallery completeness and identity-level ties matter.
- Local caller-supplied descriptive results cannot produce a verified calibration or policy.

### Task 1: strict count primitives

**Files:** Create `tools/identity_calibration_statistics.py`; primary tests in `tools/tests/test_identity_calibration_scores.py`.

**Interfaces:** Consumes normalized binary/search records from spec; produces exact `binary_counts(records)` and `search_counts(records)` schemas for Task2.

- [x] Write direct hand-checked count/zero-denominator/malformed tests and observe missing-module RED.
- [x] Inspect full cbc candidate; apply only after RED; correct concrete issues using actual RED→GREEN.

### Task 2: bound score protocol and CLI

**Files:** Create `tools/identity_calibration_scores.py`; extend primary-owned tests; add `docs/validation/f10-calibration-scores.md`.

**Interfaces:** Consumes existing `validate_manifest` and Task1 functions; produces `summarize(manifest, scores, root)` plus CLI from spec.

- [x] Write verification/PAD/1:N success and refusal fixtures; observe missing-feature RED.
- [x] Implement strict manifest/split/binding/coverage checks and fixed-threshold decisions; no test-based threshold fitting.
- [x] Run targeted tests, inspect one bounded independent review and reproduce actual findings before fixing.
- [x] Run final focused/compile/diff and complete tools tests as justified by new code; preserve source hashes and protected paths.
- [x] Retain CLI smoke/report evidence, update handoff/followups and locally commit only owned files. Keep roadmap/P4/P11/release open.

Execution checkpoint: both hy4free Identity requests are terminal without usable source; primary takes over that separate plan. This F10 plan did not add resource samples. Final focused44, full tools510/510, and retained CLI3 success+1 binding refusal; helper findings/limitations and local commit are recorded in the handoff.
