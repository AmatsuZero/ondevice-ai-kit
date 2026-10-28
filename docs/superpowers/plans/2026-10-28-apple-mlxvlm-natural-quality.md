# Apple MLXVLM Natural Video Quality Evidence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a hash-bound, conservative semantic rubric report for repeated physical-device MLXVLM runs on the fixed public natural video.

**Architecture:** Keep raw device reports immutable. Normalize only the fields required by the quality contract, then run a pure Python validator that checks identity and lexical category coverage and writes a separate aggregate summary. Documentation links the evidence without changing P11 gate status.

**Tech Stack:** Python 3 standard library, JSON, existing standalone Swift MLXVLM consumer, existing public fixture and model revisions.

**Spec:** `docs/superpowers/specs/2026-10-28-apple-mlxvlm-natural-quality-design.md`

## Global Constraints

- Do not modify production SDK, model, Demo, signing project, or protected dirty files.
- Do not write Photos or read private media.
- Preserve raw per-run JSON and logs; reports must include exact fixture/model/backend identity.
- Keep natural temporal quality, GPU/energy, sustained resources, physical touch, and minimum-OS claims unverified.
- Do not add a grouped benchmark sample from this rubric-only report.

## Review Focus

- Wrong fixture or model revision: validator rejects before scoring; covered by identity-negative tests.
- Missing/empty output: validator records a failed run and never treats it as a pass; covered by malformed-input tests.
- Generic scene-only output: validator requires all three categories; covered by category-negative tests.
- Stale or duplicate run IDs: validator rejects duplicates; covered by duplicate-ID tests.
- Rubric drift: report includes a fixed rubric revision and manifest hash; covered by deterministic-output tests.

### Task 1: Define the normalized quality contract

**Files:**
- Create: `tools/mlxvlm_natural_quality.py`
- Create: `tools/tests/test_mlxvlm_natural_quality.py`
- Modify: `tools/probes/natural_video_manifest.json` only if the existing fixture identity is missing from the contract; otherwise leave it unchanged.

**Interfaces:**
- `normalize_run(raw: dict, *, expected: QualityIdentity) -> dict`
- `score_output(output: str, rubric: QualityRubric) -> dict`
- `build_summary(runs: list[dict], *, expected: QualityIdentity, rubric: QualityRubric) -> dict`
- CLI: `python3 tools/mlxvlm_natural_quality.py validate <report.json> --expected <identity.json> --output <summary.json>`

- [x] Write RED tests for valid output, missing identity, wrong fixture hash, empty output, missing category, duplicate run ID, and deterministic summary ordering.
- [x] Run `python3 -m unittest tools.tests.test_mlxvlm_natural_quality`; confirm RED for the absent module/contract.
- [x] Implement the immutable identity and conservative lexical rubric from the design: subject `{birds,swifts}` (plural is required by the reference frames), action `{fly,flying,flight,in flight,soar,soaring}`, scene requiring `sky` plus one of `{blue,cloud,clouds}`.
- [x] Run the focused tests; expect all tests green.
- [x] Run `python3 -m py_compile tools/mlxvlm_natural_quality.py`.

### Task 2: Normalize retained physical-device runs

**Files:**
- Create: `artifacts/verification/apple-mlxvlm-natural-video-20261028/quality/expected-identity.json`
- Create: `artifacts/verification/apple-mlxvlm-natural-video-20261028/quality/raw-runs.json`
- Create: `artifacts/verification/apple-mlxvlm-natural-video-20261028/quality/README.md`

**Interfaces:**
- Consume the existing fixed fixture and model identity from `device-report.json` and the existing physical-device logs.
- Produce a raw-run manifest that preserves source report paths and hashes; do not rewrite existing raw reports.

- [x] Freeze expected identity to fixture SHA-256 `97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`, model revision `fa57db46815177fbdfd65cc85a2b3416a8332268`, runtime revision `6e338e5df49424fc0db7c6d58184d622f92ed18a`, and prompt text.
- [x] Select the retained physical-device successful natural-video run, give its normalized row a source-qualified ID, and record the exact source path/hash. This initial evidence has `expectedRuns=1`; Task 3 replaces it with five new distinct device runs.
- [x] Validate the manifest with the Task 1 CLI before any aggregate report is generated.

### Task 3: Execute bounded device repetitions

**Files:**
- Use existing ignored standalone consumer under `artifacts/verification/apple-mlxvlm-natural-video-20261028/`; no production source files.
- Create: `artifacts/verification/apple-mlxvlm-natural-video-20261028/quality/device-runs/` raw logs/reports.

- [x] Verify device availability, source commit, model lease, fixture hash, and no protected-file drift.
- [x] Run the fixed natural-video prompt on the connected iPhone exactly five times, retaining each raw report and distinct run ID; then change `expectedRuns` from 1 to 5.
- [x] Do not claim quality completion from execution alone; classify output only through the Task 1 validator.

### Task 4: Generate and verify the aggregate report

**Files:**
- Create: `artifacts/verification/apple-mlxvlm-natural-video-20261028/quality/summary.json`
- Modify: `docs/validation/multimodal-video-demo.md`
- Modify: `docs/validation/foundation-followups.md`
- Modify: `docs/validation/p11-status-matrix-20261027.md`

- [x] Generate the aggregate summary from the immutable raw-run manifest.
- [x] Re-run generation and compare outputs byte-for-byte.
- [x] Record per-category counts and keep complete natural quality, `naturalTemporalQuality`, `gpuEnergy`, and `sustainedResources` explicit as incomplete/unverified.
- [x] Run focused tests, `git diff --check`, and readiness validation; update P11 evidence only if the new report is a valid non-benchmark evidence path.
- [x] Commit only the already-present validator/tests and precise documentation/manifest changes; preserve all protected dirty files.
