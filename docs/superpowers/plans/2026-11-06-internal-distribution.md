# Internal Distribution Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans, solo inline as required by the current handoff.

**Goal:** Carry the user's internal-only project license from an exact commit into F09 notices.
**Architecture:** Extend the existing committed snapshot and notice-copy paths; keep legacy snapshots explicitly unlicensed/local-only.
**Tech Stack:** Python standard library, Git, unittest; no new dependencies.
**Spec:** [Internal distribution design](../specs/2026-11-06-internal-distribution-design.md).

## Global Constraints

- Only internal closed-source use/distribution; no publication or external redistribution.
- Preserve third-party rights, existing artifacts, nine protected paths and production lock.
- No source rebuild, download, signing or device operation.

## Review Focus

- Dirty working-tree LICENSE must not enter a committed snapshot.
- Legacy commits without LICENSE remain local-validation-only.
- Third-party notice bytes must remain unchanged.
- Copied project license must have a manifest hash and SDK attribution reference.
- Modified copied notice bytes must be detectable through its recorded digest.

### Task 1: Project policy and committed snapshot

Files: `LICENSE`, `README.md`, `tools/prepare_mlxvlm_binary_snapshot.py`, `tools/tests/test_mlxvlm_binary_snapshot.py`.
Interface: existing `snapshot(root, source_commit, output) -> Path`; add `packages/LICENSE` when the exact commit contains root `LICENSE`.

- [x] Add selected internal-only terms and README reference.
- [x] RED: `test_project_license_uses_committed_bytes_and_is_inventoried` must fail when committed LICENSE is omitted.
- [x] Include root LICENSE in Git inventory and preserve optional absence for old commits.
- [x] Run snapshot suite; assert committed bytes/hash and untouched dirty license.

### Task 2: Exact-snapshot notices

Files: `tools/package_mlxvlm_binary.py`, `tools/tests/test_mlxvlm_packaging_inputs.py`.
Interface: `_notices(distribution, cache, lock, source_commit, snapshot) -> tuple[list, list]`; `_assemble` supplies its already-validated snapshot.

- [x] RED: project notice copy/reference/hash and legacy no-license cases; actual third-party text remains exact.
- [x] Copy `packages/LICENSE` to `notices/ondevice-ai-kit/LICENSE`; SDK attributions refer to that file, without guessing a public SPDX license.
- [x] Run both affected suites, then all tools using the installed Ruby 3.4 executable via `RUBY` (13 targeted; 309 full).
- [ ] Generate fresh notice-only evidence from a committed snapshot; do not rewrite old native artifacts.
- [ ] Update handoff/readiness, verify protected files, diff and commit locally.
