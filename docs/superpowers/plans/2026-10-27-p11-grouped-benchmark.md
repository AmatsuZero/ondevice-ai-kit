# P11 Grouped Benchmark Snapshot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate a hash-bound, grouped P11 benchmark snapshot from existing raw face and Apple MLXVLM device reports while making every unsupported capability and measurement explicit.

**Architecture:** A tracked manifest identifies immutable raw inputs and the readiness snapshot. A Python tool delegates schema-specific calculations to the existing reporters, normalizes only the outer grouping/coverage envelope, and writes deterministic JSON after all validation succeeds.

**Tech Stack:** Python standard library, `unittest`, existing benchmark reporters.

**Spec:** `docs/superpowers/specs/2026-10-27-p11-grouped-benchmark-design.md`

## Global Constraints

- Preserve all eight protected dirty/untracked paths; do not reset, clean, stash, or stage them.
- No new dependency, model download, license acceptance, device run, merge, tag, or release.
- Only metrics recomputed from hash-matched raw JSON may be reported.
- Missing quality, memory, GPU peak, energy, minimum-OS, and production gates remain explicit.
- P11 stays `open/not-ready` unless every readiness row is terminal, which this plan does not attempt.

## Review Focus

- Duplicate `runId` values across source kinds must be rejected before any output write.
- A changed, missing, absolute, or repository-escaping source path must fail closed.
- An output same-path, symlink, or existing hard-link alias must not overwrite the manifest, readiness snapshot, or raw inputs.
- Empty face quality and unmeasured resources must remain unverified rather than becoming passes or zeroes.
- MLXVLM synthetic rubric success must stay scoped to the fixed fixture and must retain natural quality, Metal GPU peak, and energy gaps.

---

### Task 1: Raw-source grouped report generator

**Files:**
- Create: `tools/p11_grouped_report.py`
- Create: `tools/tests/test_p11_grouped_report.py`

**Interfaces:**
- Consumes: `generate(manifest: dict, repo_root: Path) -> dict`
- Produces: deterministic report schema v1 consumed by Task 2 documentation and evidence.

- [ ] Write focused tests for valid face/MLXVLM groups, readiness-derived gaps, bounded quality/resources, duplicate run IDs, source hash/path failures, and output aliases.
- [ ] Run `rtk proxy python3 -m unittest discover -s tools/tests -p 'test_p11_grouped_report.py' -v`; expect failures because the module is absent.
- [ ] Implement `tools/p11_grouped_report.py` with repository-contained path resolution, SHA-256 verification, source reporter delegation, global identity checks, deterministic groups/coverage, and write-last CLI behavior.
- [ ] Re-run the focused suite; expect all tests to pass.

### Task 2: Real manifest, evidence, and checkpoint

**Files:**
- Create: `benchmark/p11/grouped-report-manifest.json`
- Create: `docs/validation/p11-grouped-benchmark.md`
- Modify: `docs/validation/acceptance.md`
- Modify: `docs/validation/p11-status-matrix-20261027.md`
- Modify: `docs/HANDOFF-2026-10-23-next-session.md`
- Local evidence: `artifacts/verification/p11-grouped-benchmark-20261027/summary.json`

**Interfaces:**
- Consumes: Task 1 CLI and the current readiness/face/MLXVLM raw reports.
- Produces: a reviewable three-group snapshot plus seven explicit capability gaps.

- [ ] Add the manifest with exact current SHA-256 values and run the CLI to generate the local evidence snapshot.
- [ ] Assert three groups, two reported readiness capabilities, seven gaps, source hashes unchanged, and `open/not-ready` gate.
- [ ] Update validation documentation without upgrading Video SR, speech, identity, camera, LiteRT-LM, minimum-OS, quality, GPU, or energy status.
- [ ] Run the focused suite, full tools suite with explicit Ruby 3.4, P11 readiness validator, `py_compile`, `git diff --check`, protection hashes, and exact status/staging checks.
- [ ] Commit only the plan/spec/tool/tests/manifest/docs, sync `origin` and `jihu`, and verify both remote SHAs while the eight protected paths remain unchanged.
