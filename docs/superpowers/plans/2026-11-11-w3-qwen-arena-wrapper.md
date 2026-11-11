# W3 arena wrapper implementation plan

> **For agentic workers:** Use the existing external-candidate workflow; CBC writes implementation, primary owns integration/verification/device operations. Do not start a native child or retry the old native429.

**Goal:** Connect the accepted exact CPU arena-off policy to a distinct isolated FILE wrapper, preserving the whole65input quality contract and failed old run.

**Architecture:** New `QwenArenaInstrumentation` stays in the existing qwenfile Kotlin package so it consumes the unchanged Activity/input/report/core modules. It adds the accepted qwenarena policy only, with truthful snapshot metadata and preconstructor verification. Parent host scaffolding reuses existing faults; a distinct Gradle application ID owns the new sandbox/APK. No model execution occurs during preparation.

**Tech Stack:** Existing Sherpa2.0.1/Kotlin2.2.20/API36compile,target35,min26/JDK21, fixed65assets and6models. No new dependency.

**Spec:** `docs/superpowers/specs/2026-11-11-w3-qwen-arena-policy-design.md`, extended by exact wrapper contract below. The user's autonomous execution instructions cover this local preparation; no new device run is frozen.

## Global constraints

- Preserve old FILE implementation/build/APKs/report/runId and ordinary Apps/models, source branch unpushed.
- Only allocation policy changes: exact `EnableCpuMemArena=0\n`; existing model/input/decoding/quality contract and1.5GiB/512MiB/120s stop gates persist.
- No native/device/network/microphone/AiAi/download or production/default change during this plan.

## Wrapper contract

New file `probes/android-qwen-arena/QwenArenaInstrumentation.kt`, package`io.ondeviceai.acceptance.qwenfile`, class`QwenArenaInstrumentation`; fixed report child`w3-qwen-arena`. Consume unchanged QwenFileActivity/QwenFileInputs/QwenFileReport/TwoStageCaseRunner/QwenModelFiles. Keep their cleanup/errors/counters and exclusive lifecycle behavior.

After model/input verification and before constructor: `QwenArenaPolicy.prepare(targetContext.noBackupFilesDir.canonicalFile)` once; set phase`allocator-policy`; persist running snapshot with receipt, then verify it again and check fresh foreground before native constructor. Only after all succeed may `actualConstructors` increment. Pass receipt.provider to actual OfflineModelConfig; all other config fields retain their literals. No fallback/retry. Failed policy/persistence/tampering must leave0constructors/streams/decodes and preserve first error.

Every snapshot (including initial/failed/terminal) uses `probeVariant="cpu-arena-off-v1"`, `nativePolicyEffectVerified=false`, `allocatorPolicy`=null until prepared then exactly the receipt provider/configPath/bytes/sha256. Override snapshot`config.provider` to null until prepared then actual receipt.provider; set`config.requestedEnableCpuMemArena=false`, leaving memory-pattern absent/default. This is requested configuration, not a verified native effect. Do not modify shared report source.

## Review focus

1. Config from report must equal actual constructor provider/file bytes (task1).
2. Collision/policy write failure cannot reach native (task1).
3. Tampering after policy snapshot must be rejected before constructor (task1).
4. Policy snapshot persistence failure must block constructor and retain error (task1).
5. Existing failure/cleanup/full-PCM/counter cases must remain valid (task1); packaged runner/application identity must remain distinct (task2).

## Task1: External wrapper and parent fault checks

**Files:** external`QwenArenaInstrumentation.kt`; parent`check_wrapper.py`/`ArenaWrapperChecks.kt`.

- [ ] Run absent-wrapper RED, retain unchanged baseline host scaffold hashes.
- [ ] One moderate external hy4free candidate owns only new wrapper file, tested task-directory write sandbox and immutable full context. No quota retry/paid fallback.
- [ ] Run actual candidate+accepted policy with original24groups plus policy-specific faults. Preserve RED and send any correction as a distinct small task.
- [ ] Integrate external bytes unchanged and record exact hashes.

## Task2: Distinct package and independent review

**Files:** parent preparation/package scripts and validation report.

- [ ] Prepare new isolated Gradle application/test IDs`io.ondeviceai.acceptance.qwenarena[.test]`, explicit runner`io.ondeviceai.acceptance.qwenfile.QwenArenaInstrumentation`, unchanged nonexported Activity class. Reuse frozen actual assets/native artifacts.
- [ ] Offline build/lint; inspect actual DEX, runner/target manifest, zero permissions, all65packaged PCM,4arm64native hashes and debug certificate. No install/run command in this task.
- [ ] Bounded independent review of accepted exact source/package checks; report scope limits and blockers.
- [ ] Commit exact files/hunks, refresh W3/handoff/issues/wiki with deduplicated prepared-only result. Keep native effect/savings/quality and W3open.

Self-review: all5review failure classes map to host or real package validation; full-scope Android quality still requires a separately frozen future diagnostic contract. This plan does not authorize replaying any consumed run or relaxing acceptance.
