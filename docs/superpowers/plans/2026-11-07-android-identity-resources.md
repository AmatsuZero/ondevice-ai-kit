# Android Identity Resource Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add real fixed-input Android Identity representation-pipeline resource samples to P11 while retaining calibration/PAD/quality gaps.

**Architecture:** Existing backend and pinned local models; test-only instrumentation in a frozen-source copy; strict pure reducer and existing grouped-report adapter. The parent owns integration/device/restoration; cbc hy4free supplies a bounded Kotlin candidate.

**Tech Stack:** Existing Kotlin/Android/OpenCV 4.12.0 and Python standard library.

**Spec:** `probes/android-identity-resources/README.md`.

## Global Constraints

- Exact 636a6eb source / dfccaa95 main APK; build AndroidTest only, preserve 1046 original source files and nine protected paths/Apple lock.
- No downloads, camera/microphone, enrollment, journals, embeddings on disk, threshold changes, upload or push.
- 5 warmups / 30 measurements; 20ms intended RSS sampling, actual intervals reported; no forced GC.
- Backend uses a new owned cache ModelStore only; the foreground-controlled test may create ordinary MainActivity only with empty ordinary model staging proven immediately before launch and unchanged private-state gates afterward. No ordinary IdentityStore/session. closeAndWait/remove before exact cleanup. Preserve 25 private hashes, original image, four SR paths, App/test/runner/settings.
- Quota/auth routes remain stopped; no helper task timeout. Never replay an issued inference after a lost response.

## Review Focus

- Bool/float integer aliases, nonfinite/abnormal norms and changed exact input/model/commit identity must reject (Task 1).
- Warmups, duplicates, altered timing/peak and sampling coverage must not become accepted measurements (Task 1).
- Missing/failed/nonterminal reports must remain recoverable and never pass (Tasks 1/2).
- Ordinary journals/models and unknown cleanup paths must remain protected (Task 2).
- Source-kind relabeling, summary/hash mismatches and output/input aliases must reject without changing old groups (Task 3).

### Task 1: Strict report reduction

Files: create `tools/probes/android_identity_resource_report.py`, `tools/tests/test_android_identity_resource_report.py`.
Interface: `summarize(report: dict) -> dict`; consumes Schema 1 and returns `android-identity-resource-summary`, nearest-rank duration and RSS diagnostics with `qualityVerdict=unverified`.
- [x] Write rejecting fixtures for all five input classes above; run and observe missing reducer RED.
- [x] Implement exact identities/strict numeric types/order/window/peak/privacy/close validation; run focused GREEN.

### Task 2: One physical resource run and recovery

Files: create `probes/android-identity-resources/IdentityResourceInstrumentation.kt`; own isolated `artifacts/verification/android-identity-resources-20261107/` protocol/build/raw evidence.
- [x] Review helper candidate against the backend/model/probe contracts and privacy/ownership constraints.
- [x] Offline test-only build; inspect manifest/DEX/signature and all original source/main APK hashes.
- [x] Adapt tested TTS staging/restore boundaries to this distinct report/work directory, with offline failure/ownership checks.
- [x] Fresh device preflight, rollback/hash staging, one 35-sample run, raw collection/validation; do not replay on USB loss.
- [x] Independently retain reports, restore original packages/private state/settings and exact-clean owned paths regardless of semantic pass.

### Task 3: P11 integration and handoff

Files: modify `tools/p11_grouped_report.py`, related grouped tests, `benchmark/p11/grouped-report-manifest.json` and owned validation/handoff documents.
Interface: source kind `identity-android-resource`, capability `face-identity-liveness`; strict raw/summary/source-manifest/input binding.
- [x] Add adapter rejection/identity fixtures; observe RED then implement and pass focused checks.
- [x] If physical data passes, add only the new actual source; regenerate readiness/grouped output, prove prior groups unchanged and gates remain open/not-ready.
- [x] Freeze evidence, run required affected checks, update pitfalls/roadmap/handoff and locally commit owned files; no publication.

## Evidence-backed protocol amendment

Original run01 naturally completed35rows after manual foreground recovery of the same PID;
never replayed, excluded from clean benchmark. Originals restored and exact cleanup verified.
Copilot42.21s source-bound review supports distinct controlled foreground measurement, with
explicit UI process scope,72 RESUMED checkpoints, pre-open RSS and strict schema2.
Reducer/adapter51focused,34protocol checks and531tools pass. Test-only397task build, controlled35row run, exact restoration and30sample P11 admission are complete under
`artifacts/verification/android-identity-foreground-resources-20261107/`. No system freezer
setting, SDK APK, models, inputs, enrollment/camera/microphone or production threshold changes.
