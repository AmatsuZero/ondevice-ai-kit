# Nexus plan/archive consistency implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. The candidate helper owns text only; primary agent owns integration.

**Goal:** Prove the current immutable Maven plan matches the archived release bytes before any future authorized upload.

**Architecture:** One read-only standard-library checker streams the archive and verifies each planned member. Existing source/publication provenance and remote consumer gates stay separate.

**Tech Stack:** Python standard library, tarfile, hashlib, unittest.

**Spec:** `docs/superpowers/specs/2026-11-07-nexus-plan-archive-design.md`

## Global Constraints

- No network/authentication/uploads or disk extraction; preserve frozen plans/archives and production dependencies.
- Only owned checker/tests/docs may be committed; unrelated Identity resource edits are protected.
- Use synthetic fixtures for tests and one read-only .6 actual archive check. Do not rerun publication or consumers.

## Review Focus

- A refreshed whole-archive digest must not hide a wrong member digest.
- Tar duplicates/links/unsafe names cannot satisfy path-set equality.
- Bool/float aliases and mutable metadata cannot enter an immutable release plan.
- Special files and archive mutation must fail instead of hanging or reporting pass.
- Local consistency is not publication, remote acceptance, legal approval or no-overwrite enforcement.

### Task 1: checker and tests

**Files:** `tools/nexus_plan_archive.py`, `tools/tests/test_nexus_plan_archive.py`, `docs/validation/f02-nexus-delivery.md`.

**Interfaces:** Consumes the existing .6 upload-plan shape and archive; produces the exact bounded result from the spec. No shared interface with F10 or the Android resource instrumentation task.

- [x] Read complete Copilot candidate and actual event/exit receipt; retain original.
- [x] Apply tests only and observe missing-implementation RED.
- [x] Review/apply candidate code, reproduce concrete defects with RED, correct and run focused tests.
- [x] Run actual .6 archive/plan check once, retaining input/output hashes.
- [x] Run compile/diff checks and the tools suite with the F10 changes, record scope and update handoff, commit owned files.

Evidence and rulings: `artifacts/verification/f02-nexus-plan-archive-20261107/progress.md`.
