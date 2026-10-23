# Apple MLXVLM source distribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline, solo, task-by-task. Preserve the workspace and logs.

**Goal:** A movable fixed-lock MLXVLM source consumer and an evidence-backed binary feasibility boundary.

**Architecture:** Add an opt-in tracked-source snapshot to the existing generator. Build the relocated snapshot offline, then inspect a separate package archive attempt.

**Tech Stack:** Python unittest, XcodeGen, SwiftPM, cached Xcode iOS toolchain.

**Spec:** `docs/superpowers/specs/2026-10-23-apple-mlxvlm-source-distribution-design.md`

## Global Constraints

Solo/local commits only; no downloads, signing changes, private media or protected-file edits. Exact existing apple-mlx lock. Base iOS15/macOS12/API26 unchanged. Preserve scratch/logs. No source-to-binary or build-to-inference acceptance substitution.

## Review Focus

- Relocated package references must not fall back to the original checkout.
- Untracked media/build products must not enter a source bundle.
- Symlink or nonempty output must fail before overwriting files.
- Canonical metadata and dependency lock must survive the snapshot unchanged.
- Archive exit status alone must not imply a usable enhanced XCFramework.

### Task 1: Portable tracked-source generation

**Files:** Modify `tools/generate_mlxvlm_consumer.py`; create `tools/tests/test_mlxvlm_source_bundle.py`.

**Interfaces:** Consumes existing example, Git-tracked package files and fixed lock. Produces `generate(output: Path, *, portable_source: bool = False)` and CLI `--portable-source`, relative `packages/` references and `source-bundle.json`.

- [ ] Write real-generator tests: relocation/project dependency paths, exact lock/resource/hash inventory, untracked sentinel exclusion, nonempty/symlink refusal.
- [ ] Run `rtk proxy python3 -m unittest tools.tests.test_mlxvlm_source_bundle -v`; Expected: missing flag/behavior RED.
- [ ] Implement minimum portable branch; preserve default behavior.
- [ ] Run new tests plus `tools.tests.test_apple_mlx_consumer`; Expected: all pass. Run full tools with explicit Ruby; Expected: all pass.
- [ ] Author scope review, exact-stage and local commit.

### Task 2: Relocated offline build and binary probe

**Files:** Generated artifacts/logs only under `artifacts/verification/apple-mlxvlm-source-distribution-20261023/` and this plan's workspace.

**Interfaces:** Consumes Task1 portable output. Produces build/archive logs and inspected products, without SDK/runtime changes.

- [ ] Generate portable consumer, relocate into a fresh path with a space, verify hashes and project references.
- [ ] Build unsigned `MLXVLMConsumer`, generic iOS, exact cached SourcePackages and all three no-resolution/update flags. Expected: BUILD SUCCEEDED; unchanged lock and actual relocated source/resource paths.
- [ ] Attempt Release archive of `ODAIKitAppleMLXVLM`, same cache/flags, `BUILD_LIBRARY_FOR_DISTRIBUTION=YES SKIP_INSTALL=NO`. Expected: either inspectable framework with dependencies/resources, or explicit recorded compiler/product-layout barrier. Neither source build nor empty archive passes binary acceptance.
- [ ] Author evidence review; record exact result, preserve failed logs.

### Task 3: Validation and recovery checkpoint

**Files:** Create `docs/validation/apple-mlxvlm-distribution.md`; update backend validation, roadmap and next-session handoff; update this plan and local ledger/final-review.

**Interfaces:** Consumes tested generator and fresh build/archive evidence. Produces honest source/binary statuses and a bounded next entry.

- [ ] Document reproducible commands, snapshot/lock hashes, counts, diagnostics and remaining binary requirements.
- [ ] Run Python static compilation and `git diff --check`; recheck exact protected hashes and index.
- [ ] Final separate author review, local docs commit, no remote writes.
