# F11 SDK File ASR Grouped Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the retained five-run production SDK client fixed-file iOS ASR report to P11 grouped coverage.

**Architecture:** Introduce a distinct `asr-file-sdk-resource` source kind. Pin the raw report, bounded summary, source manifest, and synthetic CAF by SHA-256; validate their cross-references and five successful runs, then recompute nearest-rank duration and sampled RSS. Keep direct Speech framework probe data separate and preserve the release gaps.

**Tech Stack:** Python standard library, existing grouped reporter and unittest suite.

**Spec:** `docs/superpowers/specs/2026-10-27-p11-grouped-benchmark-design.md`, `docs/validation/speech-backends.md`, and `docs/validation/foundation-followups.md`.

## Global Constraints

- Preserve nine existing dirty/untracked files and ignored raw evidence.
- The fixed synthetic English CAF only validates the SDK file recognition path. Natural CER/WER, microphone, offline behavior, true peak/GPU, energy, and minimum OS remain unverified.
- Do not compare this SDK run to the separate Speech framework probe as an SDK overhead estimate.
- P11 and release remain `open/not-ready`.

## Review Focus

- Reject changed or aliased raw, summary, source manifest, or CAF files.
- Reject source manifest and bounded summary cross-reference mismatches.
- Reject duplicate/failed runs, transcript/backend mismatch, and invalid duration/RSS fields.
- Reject wrong P11 capability and duplicate IDs across other groups.
- Recompute statistics from runs without promoting synthetic transcript match to natural quality.

---

### Task 1: SDK file ASR source adapter

**Files:** `tools/tests/test_p11_grouped_report.py`, `tools/p11_grouped_report.py`.

**Interfaces:** Manifest kind `asr-file-sdk-resource` has `path`, `sha256`, `summary`, `sourceManifest`, `input`, and `p11Capability: speech-asr-file`. The output group contains five run IDs, nearest-rank duration and sampled RSS p50/p95, and explicit unverified gates.

- [x] Write and observe RED tests for recomputation, bad runs, cross-file identity, wrong capability, and output aliases.
- [x] Implement the adapter and alias protection without altering existing group outputs.
- [x] Run focused tests.

### Task 2: Real evidence and handoff

**Files:** `benchmark/p11/grouped-report-manifest.json`, `docs/validation/p11-grouped-benchmark.md`, `docs/validation/p11-status-matrix-20261027.md`, `docs/validation/foundation-followups.md`, `docs/HANDOFF-2026-10-23-next-session.md`.

- [x] Pin the four retained evidence files and refresh readiness for changed validation docs.
- [x] Generate a new ignored grouped snapshot and inspect group/coverage/gate values.
- [x] Record exact values and limitations in docs and handoff.
- [x] Run focused and full tools tests, `py_compile`, and `git diff --check`; stage only this package.

The focused reporter/readiness suite passed 36/36; the full tools suite passed 240/240 with Ruby 3.4 and an allowed SwiftPM environment. The refreshed readiness validated 97 evidence paths and 105 source hashes; the grouped output contains 5 reported capabilities and 8 groups while P11 remains open/not-ready.

Independent review found that `normalizedMatch=true` was trusted without recomputing the transcript. A wrong-transcript regression test failed, then passed after matching the probe's lowercase letters/numbers normalization. The full tools suite passed 240/240 again after the fix.
