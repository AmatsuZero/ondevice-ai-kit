# F11 iOS TTS Grouped Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the existing five-run iOS TTS resource probe as a truthful, hash-bound P11 grouped report source.

**Architecture:** Extend the current grouped report generator with one `tts-resource` source kind. The manifest pins both the raw device report and its existing bounded summary; the generator verifies the summary's raw hash before recomputing nearest-rank duration and sampled RSS statistics. The output retains explicit probe scope and all unverified quality, energy, and true-peak fields.

**Tech Stack:** Python 3 standard library and existing `tools/p11_grouped_report.py`/unittest suite.

**Spec:** `docs/validation/foundation-followups.md` F11 and `docs/validation/p11-grouped-benchmark.md`.

## Global Constraints

- Preserve current dirty/untracked files and all ignored raw device evidence.
- Do not change production SDK dependencies, add packages, or call probe data a production App performance result.
- Keep P11 `open/not-ready`; sampled RSS is not a true peak, and one fixed voice is not natural quality acceptance.

## Review Focus

- Reject a changed raw report or bounded summary before writing output.
- Reject a source kind mapped to the wrong P11 capability.
- Reject missing/duplicate run IDs, failed runs, and invalid duration/RSS fields.
- Reject output aliases of either input, including the nested summary.
- Preserve TTS source identity and scope without merging it into Face or MLXVLM timing definitions.

---

### Task 1: TTS source adapter

**Files:** `tools/p11_grouped_report.py`, `tools/tests/test_p11_grouped_report.py`

**Interfaces:** Manifest source kind `tts-resource` uses `path`, `sha256`, `summary: {path, sha256}`, and `p11Capability: speech-tts`. Output group carries five per-run IDs, duration p50/p95, sampled RSS p50/p95, fixed voice/locale/SDK commit, and bounded scope.

- [x] Add a failing test with five distinct successful raw runs and hash-linked summary; assert one `speech-tts` group and still-open P11 gate.
- [x] Add failing rejection tests for tampered summary, invalid run data, wrong capability, and output alias.
- [x] Implement only the TTS adapter and nested-summary validation/alias protection.
- [x] Run focused and full grouped-report tests.

### Task 2: Pin real evidence and document the result

**Files:** `benchmark/p11/grouped-report-manifest.json`, `docs/validation/p11-grouped-benchmark.md`, `docs/validation/p11-status-matrix-20261027.md`, `docs/HANDOFF-2026-10-23-next-session.md`

- [x] Pin the existing raw TTS report and summary SHA-256 in the manifest.
- [x] Regenerate the ignored grouped output from the real manifest and inspect all groups, coverage, and gate.
- [x] Record exact values and limits in validation docs and update the handoff.
- [x] Run `git diff --check`, targeted tests, and hash-bound generation; stage only this work package.

The broader `tools/tests` run reached 229 tests with 13 failures and 8 errors in unrelated Swift cache and system Ruby/RVM gem paths. The four relevant reporter/readiness suites passed 44/44. Full-suite environmental failures are not counted as a green gate.
