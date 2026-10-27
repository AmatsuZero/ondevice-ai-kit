# Android LiteRT-LM Backend Implementation Plan

2026-10-27 用户授权更新：已验证改动可分批提交并同步 `origin` + `jihu` 的现有 foundation 分支；覆盖本计划此前的暂不提交约束。受保护文件和未完成线程取证保留本地；不包含合并、tag 或发布。

**2026-10-27 contract correction:** The original `terminalCallbackThenDeadConversation` assumption is invalid for the pinned AAR. `isAlive` changes before native deletion returns; it is not a drain barrier. This plan withdraws that predicate without enabling a replacement protocol. See [evidence and local correction](../../validation/android-litertlm-drain-contract.md). Production construction stays unavailable.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional Kotlin-only Android LiteRT-LM image backend that uses verified local model assets, has a proved cancel-to-drain lifecycle, and keeps video unavailable until a separate video contract is implemented.

**Architecture:** Add `android/backend-litertlm` as an opt-in Android library depending on `android:multimodal`, `android:model-store`, and `com.google.ai.edge.litertlm:litertlm-android:0.18.1`; keep `android:multimodal` and the default Demo free of the runtime dependency. Hide LiteRT-LM classes behind an internal engine/session seam, make each generation own its `ModelLease → Engine → Conversation`, and fail closed into a poisoned terminal when native termination cannot be proved. Use deterministic fake sessions for JVM lifecycle tests and a separate Kotlin Android probe for the real AAR callback and drain contract.

**Tech Stack:** Kotlin 2.4.0, Android Gradle Plugin 9.1.0, Android API 26 library baseline, kotlinx-coroutines 1.10.2, LiteRT-LM Android 0.18.1, Kotlin/JUnit tests, Android instrumentation, Python standard-library artifact inspection.

**Spec:** `docs/superpowers/specs/2026-10-26-android-litertlm-backend-design.md`

## Global Constraints

- Keep `android:multimodal` free of LiteRT-LM and do not add LiteRT-LM to the default Demo dependency graph; callers opt in to `android/backend-litertlm`.
- New backend and probe source is Kotlin; do not introduce Java demo/backend/test source.
- Pin `com.google.ai.edge.litertlm:litertlm-android:0.18.1`; do not download models, modify upstream, or put model weights in Git/LFS.
- Keep `backendId="litertlm.android"`, `capability="multimodal.image"`, `formatVersion="litertlm-model-v1"`, `inputSignature="image-file+text-prompt-v1"`, `runtimeRevision="litertlm-android:0.18.1"`, `maxOutputTokens=512`, `maxNumImages=1`, `maxImageBytes=16 MiB`, and `CPU_ONLY` exact.
- `supportsVideo=false`; do not implement `MultimodalVideoGenerationBackend` or translate video requests into image requests.
- Acquire and hold `ModelLease` through image reads, callback/Flow collection, native drain, conversation close, engine close, and cache cleanup; never release on timeout without a drain proof.
- A valid drain proof requires independently established native completion semantics; no supported production barrier is established for 0.18.1. Terminal callbacks and `isAlive=false` do not prove drain. Failed/missing proof enters poisoned terminal and returns `LiteRtLmError.DrainFailure` without native close, lease release, or cache deletion. The 30-second owner deadline is verified only for cancellable fake waits, not blocking JNI.
- Availability must not initialize LiteRT-LM; missing/unfrozen model assets remain `REQUIRES_ASSETS/MISSING_ASSETS`, and an unproved AAR/device drain contract remains `UNAVAILABLE/UNSUPPORTED_DEVICE`.
- Do not alter the two protected files named in the handoff: `demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj` and `docs/validation/face-identity.md`.
- Preserve all existing dirty, staged, and untracked work; do not reset, clean, stash, commit, push, merge, publish, or release as part of implementation unless separately authorized.

## Review Focus

- **Callback semantics:** cumulative versus delta text, duplicate terminal callbacks, and callbacks arriving after cancellation must produce ordered cumulative snapshots without late delivery. Test in `LiteRtLmGenerationSessionTest` with both callback modes and a late-callback race.
- **Native drain failure:** a missing, throwing, or non-returning verified barrier must produce `DrainFailure`, retain lease/cache, and forbid the next acquire. Test in `LiteRtLmPoisonedTerminalTest` and assert neither close method nor lease release was called.
- **Asset identity and budgets:** manifest backend/capability/format/signature, model modalities/runtime floor, image size, prompt, and `1..512` output budget must be checked without silent clamping. Test in `LiteRtLmModelSpecTest` and `LiteRtLmBackendTest`.
- **Concurrency and re-entry:** availability may join with close, only one generation is admitted, and every successful generation gets a new conversation/KV state. Test close/query join, busy admission, and two-generation fake re-entry.
- **Optional dependency boundary:** `android:multimodal` and the default Demo must compile without LiteRT-LM while the new consumer links it explicitly. Test dependency graphs plus `:android:multimodal:testDebugUnitTest` and the new module's lint/consumer compile.

---

### Task 1: Real LiteRT-LM AAR API and drain probe

**Files:**
- Create: `probes/android-litertlm/build.gradle.kts`
- Create: `probes/android-litertlm/src/main/AndroidManifest.xml`
- Create: `probes/android-litertlm/src/main/kotlin/io/ondeviceai/probe/litertlm/MainActivity.kt`
- Create: `probes/android-litertlm/src/main/kotlin/io/ondeviceai/probe/litertlm/LiteRtLmProbe.kt`
- Modify: `settings.gradle.kts` to include `:probes:android-litertlm` only if the probe is built from the root project
- Create: raw probe logs under `artifacts/verification/android-litertlm-probe-20261026/`
- Modify: `docs/validation/mobile-vlm.md` only after the probe produces fresh evidence

**Interfaces:**
- Consumes: the verified `com.google.ai.edge.litertlm:litertlm-android:0.18.1` AAR/POM and a caller-supplied model path; no SDK production module.
- Produces: a Kotlin Android probe that compiles the actual `Engine`, `Conversation`, `Contents.of(Content.ImageFile, Content.Text)`, `Message.user`, `cancelProcess`, terminal callback, and `isAlive` calls, and emits redacted JSON with API, callback, and drain observations.

- [ ] **Step 1: Write the failing probe assertions** for text/image content mapping, non-empty model response, cumulative-versus-delta callback classification, terminal callback observations, and rejection of lifetime flags as native drain proof. Establish a supported native barrier before adding its success assertions.
- [ ] **Step 2: Run the probe compile task** `./gradlew :probes:android-litertlm:assembleDebug`; Expected: the task resolves exactly `litertlm-android:0.18.1` and initially fails until the API calls and manifest are present.
- [ ] **Step 3: Implement the Kotlin probe** with a fixed public fixture, explicit `EngineConfig`/CPU configuration, redacted callback logging, and a bounded cancellation run. Do not write model or prompt contents to logs.
- [ ] **Step 4: Install and run on the authorized TECNO KM9/API35** after refreshing ADB; save the raw JSON, AAR/POM hashes, device ABI/API, and exit code. Expected: raw callback/lifetime observations and a precise `unverified` blocker until a supported native barrier is established; schema 2 cannot certify drain. A compile/static pass alone is not a runtime pass.
- [ ] **Step 5: Gate production use**: only if the probe proves the contract, record the adapter assumptions for Task 4; otherwise make the production factory unavailable with `RuntimeUnavailable` and keep all device inference fields unverified.

### Task 2: Optional module wiring and model contract

**Files:**
- Create: `android/backend-litertlm/build.gradle.kts`
- Modify: `settings.gradle.kts` to include `:android:backend-litertlm`
- Create: `android/backend-litertlm/src/main/kotlin/io/ondeviceai/litertlm/LiteRtLmModelSpec.kt`
- Create: `android/backend-litertlm/src/main/kotlin/io/ondeviceai/litertlm/LiteRtLmErrors.kt`
- Create: `android/backend-litertlm/src/main/kotlin/io/ondeviceai/litertlm/LiteRtLmAvailability.kt`
- Create: `android/backend-litertlm/src/test/kotlin/io/ondeviceai/litertlm/LiteRtLmModelSpecTest.kt`
- Create: `android/backend-litertlm/src/test/kotlin/io/ondeviceai/litertlm/LiteRtLmAvailabilityTest.kt`

**Interfaces:**
- Consumes: `MultimodalRequest`, `MultimodalGenerationBackend`, `CapabilityAvailability`, `ModelStore`, `ModelRef`, `ModelManifest`, and the Task 1 probe result.
- Produces: `internal enum class LiteRtLmBackendPolicy { CPU_ONLY }`, `internal data class LiteRtLmEngineConfig(...)`, `internal data class LiteRtLmModelSpec(...)`, stable `LiteRtLmError` variants (`InvalidModel`, `RuntimeFailure`, `NoResult`, `DrainFailure`, `RuntimeUnavailable`), and availability mapping without engine initialization.

- [ ] **Step 1: Write failing JVM tests** for all fixed spec constants, positive/negative manifest matching, API/ABI gates, missing/invalid assets, frozen-runtime unavailability, and stable error privacy (no path, prompt, or native exception text).
- [ ] **Step 2: Run** `./gradlew :android:backend-litertlm:testDebugUnitTest --tests 'io.ondeviceai.litertlm.LiteRtLmModelSpecTest'`; Expected: FAIL because the module and types are absent.
- [ ] **Step 3: Add the Android library module** with `minSdk = 26`, Java/Kotlin 17 toolchain, `api(project(":android:multimodal"))`, `api(project(":android:model-store"))`, coroutines, and `implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")`; do not alter either existing dependency graph.
- [ ] **Step 4: Implement model/spec validation** so `modelPathRelative` and metadata paths are lease-relative safe paths, manifest backend/capability/format/signature, license, attribution, redistribution, and required model/tokenizer/config files are checked, `backendVersion` covers `0.18.1`, vision modality/runtime floor are checked only after model open, and `maxOutputTokens` is validated in `1..512` without clamping.
- [ ] **Step 5: Implement availability** to check closed/API/ABI and `ModelStore.status`, perform only a short acquire/manifest/hash validation when assets are installed, and return `REQUIRES_ASSETS/MISSING_ASSETS` or `UNAVAILABLE/UNSUPPORTED_DEVICE` as specified without initializing LiteRT-LM.
- [ ] **Step 6: Run** the focused tests and `./gradlew :android:backend-litertlm:lintDebug`; Expected: PASS with no new lint errors and no LiteRT-LM classes reachable from `android:multimodal`.

### Task 3: Fake engine/session lifecycle and poisoned terminal

**Files:**
- Create: `android/backend-litertlm/src/main/kotlin/io/ondeviceai/litertlm/LiteRtLmEngineFactory.kt`
- Create: `android/backend-litertlm/src/main/kotlin/io/ondeviceai/litertlm/LiteRtLmGenerationSession.kt`
- Create: `android/backend-litertlm/src/main/kotlin/io/ondeviceai/litertlm/LiteRtLmBackend.kt`
- Create: `android/backend-litertlm/src/test/kotlin/io/ondeviceai/litertlm/FakeLiteRtLmEngine.kt`
- Create: `android/backend-litertlm/src/test/kotlin/io/ondeviceai/litertlm/LiteRtLmGenerationSessionTest.kt`
- Create: `android/backend-litertlm/src/test/kotlin/io/ondeviceai/litertlm/LiteRtLmPoisonedTerminalTest.kt`
- Create: `android/backend-litertlm/src/test/kotlin/io/ondeviceai/litertlm/LiteRtLmBackendTest.kt`

**Interfaces:**
- Consumes: Task 2 model/spec and availability types plus the existing `MultimodalGenerationBackend` contract.
- Produces: internal `LiteRtLmEngineFactory.open(modelPath: File, config: LiteRtLmEngineConfig): LiteRtLmSession`; internal session operations `generateImage`, `cancelProcess`, `awaitTerminated`, and idempotent `close`; public `LiteRtLmBackend` implementing image generation, `drain`, and `close` while deliberately not implementing the video backend subtype.

- [ ] **Step 1: Write failing fake tests** for call order `generate → cancelProcess → abstract awaitTerminated barrier → conversation.close → engine.close → cache cleanup → lease.close` (fake ordering only; no isAlive-based production implementation), callback sequence assignment, duplicate/late callbacks, no update after cancellation/close, re-entry with a new fake conversation, and close/query join.
- [ ] **Step 2: Write failing poisoned-terminal tests** where `awaitTerminated` throws or does not return, or callback validation fails; assert `LiteRtLmError.DrainFailure`, no native close, retained lease/cache, `availability=UNAVAILABLE/UNSUPPORTED_DEVICE`, and rejection of subsequent generation.
- [ ] **Step 3: Run** `./gradlew :android:backend-litertlm:testDebugUnitTest --tests '*LiteRtLm*Test'`; Expected: FAIL until the seam, state machine, and fake are implemented.
- [ ] **Step 4: Implement the fake seam and session** with a single mutex/state machine, per-generation identity, serial callback adapter, `NonCancellable` cleanup on normal return and caller cancellation, the exact cache path `cacheRoot/litertlm/<modelId>/<revision>/<generationUUID>`, bounded drain only after the probe-approved barrier, and a terminal poisoned state that intentionally preserves native resources.
- [ ] **Step 5: Implement `LiteRtLmBackend` admission and mapping**: construct without acquiring a lease or engine, reject busy/closed/invalid input, acquire one lease per generation, read at most `maxImageBytes`, map callback text to cumulative `MultimodalTextUpdate`, create `MultimodalModelIdentity(modelId=manifest.modelId, modelRevision=manifest.upstreamRevision, runtimeRevision="litertlm-android:0.18.1")`, reject blank output, and map errors without leaking sensitive text.
- [ ] **Step 6: Run focused tests**; Expected: all fake lifecycle, poisoned-terminal, re-entry, close, privacy, budget, and availability tests pass, with no test counting fake output as device inference.

### Task 4: Production LiteRT-LM adapter

**Files:**
- Create: `android/backend-litertlm/src/main/kotlin/io/ondeviceai/litertlm/internal/LiteRtLmProductionEngineFactory.kt`
- Create: `android/backend-litertlm/src/main/kotlin/io/ondeviceai/litertlm/internal/LiteRtLmProductionSession.kt`
- Create: `android/backend-litertlm/src/test/kotlin/io/ondeviceai/litertlm/LiteRtLmProductionAdapterTest.kt`
- Modify: `android/backend-litertlm/src/main/kotlin/io/ondeviceai/litertlm/LiteRtLmBackend.kt` to expose an opt-in production factory constructor

**Interfaces:**
- Consumes: the Task 1 probe contract, Task 3 internal engine/session seam, and LiteRT-LM 0.18.1 classes.
- Produces: a production-only wrapper that constructs `EngineConfig` with `CPU_ONLY`, `maxNumTokens=512`, `maxNumImages=1`, and the Task 3 lease-private cache; calls `Engine.initialize()` only on generation open; checks `ModelInfo.from(modelPath)` for vision and runtime compatibility after open; creates a fresh `ConversationConfig(maxOutputToken=request.maxOutputTokens)` per generation; maps `Contents.of(Content.ImageFile(...), Content.Text(...))` into `Message.user(contents)`; and only emits `Content.Text` snapshots.

- [ ] **Step 1: Write failing adapter tests** against a compile-time fake of the LiteRT-LM callback surface for image/text mapping, cumulative snapshot normalization, no-video exposure, max budget propagation, and close idempotency.
- [ ] **Step 2: Run** `./gradlew :android:backend-litertlm:testDebugUnitTest --tests '*ProductionAdapterTest'`; Expected: FAIL until the adapter exists.
- [ ] **Step 3: Implement the production wrapper** in one internal file boundary so downstream code never imports LiteRT-LM types; run all blocking/native calls on `Dispatchers.IO`; preserve the Task 1 gate so an unproved probe cannot construct a production backend.
- [ ] **Step 4: Run** the full module JVM suite and `./gradlew :android:backend-litertlm:assembleDebug`; Expected: PASS, with production adapter compilation using the pinned AAR and no video implementation.

### Task 5: Consumer boundary, package verification, and static regression

**Files:**
- Modify: `examples/android-multimodal-native/build.gradle.kts` only if adding an explicit opt-in consumer variant
- Create: `examples/android-litertlm-native/build.gradle.kts`
- Create: `examples/android-litertlm-native/src/main/AndroidManifest.xml`
- Create: `examples/android-litertlm-native/src/main/kotlin/io/ondeviceai/example/litertlm/MainActivity.kt`
- Modify: `settings.gradle.kts` to include `:examples:android-litertlm-native`
- Create: `android/backend-litertlm/src/androidTest/kotlin/io/ondeviceai/litertlm/LiteRtLmUnavailableInstrumentation.kt` if a device-unavailable gate is useful
- Modify: `tools/tests/test_android_vlm_artifact.py` only for new probe/consumer evidence fields

**Interfaces:**
- Consumes: the public `LiteRtLmBackend` and Task 4 production factory; the existing Demo remains unchanged by default.
- Produces: a Kotlin-only opt-in consumer that can list `litertlm.android` availability and reports stable unavailable states without silently generating or downloading a model.

- [ ] **Step 1: Write failing consumer/build assertions** that the default `:demo:androidApp` dependency graph has no LiteRT-LM runtime, the explicit consumer links `:android:backend-litertlm`, and the consumer displays `UNAVAILABLE/MISSING_ASSETS` when no model is installed.
- [ ] **Step 2: Run** `./gradlew :demo:androidApp:dependencies --configuration debugRuntimeClasspath` and the new consumer compile task; Expected: the default graph contains no `litertlm-android`, while the opt-in graph contains exactly `0.18.1`.
- [ ] **Step 3: Implement the small Kotlin consumer** with no automatic model download, no fixture output, and no video path; use the public backend availability and error contracts only.
- [ ] **Step 4: Run** `./gradlew :android:backend-litertlm:testDebugUnitTest :android:backend-litertlm:lintDebug :android:backend-litertlm:assembleDebug :examples:android-litertlm-native:assembleDebug :demo:androidApp:assembleDebug`; Expected: all tasks pass, with existing unrelated lint warnings retained and no new errors.
- [ ] **Step 5: Run static artifact checks** using the existing AAR verifier and `git diff --check`; Expected: pinned AAR/POM hashes, ZIP/path/symlink, manifest, ABI, ELF, and license/notice checks remain green.

### Task 6: Model asset and TECNO device gate

**Files:**
- Create: a fixed model manifest/asset directory only if an authorized LiteRT-LM model is supplied, under `artifacts/verification/android-litertlm-device-20261026/` (ignored evidence, never Git/LFS weights)
- Create: raw device JSON/logs under `artifacts/verification/android-litertlm-device-20261026/`
- Modify: `docs/validation/mobile-vlm.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/HANDOFF-2026-10-23-next-session.md`

**Interfaces:**
- Consumes: Tasks 1–5, a caller-authorized model manifest/asset, fixed public fixture, refreshed TECNO KM9/API35 ADB channel, and existing artifact verifier.
- Produces: separate evidence for model/hash identity, image generation, first/total latency, cancel-to-drain, same-process re-entry, close/cache cleanup, and explicit unverified fields for video, natural quality, GPU/NPU, energy, minimum OS, and missing assets.

- [ ] **Step 1: Refresh device/tool state and verify hashes** (`rtk proxy adb devices -l`, AAR/POM, model manifest, and fixture) without treating a connected device as inference evidence.
- [ ] **Step 2: If and only if an authorized model is installed**, build/install the opt-in consumer and run one fixed-fixture generation, cancellation, re-entry, and close/cleanup sequence on TECNO KM9; Expected: complete raw JSON or a precise external/model blocker.
- [ ] **Step 3: Validate the raw report** with the existing Python tool and reject identity drift, missing drain proof, fake output, or relabeled metrics; keep all failed/unverified gates explicit.
- [ ] **Step 4: Update validation, roadmap, and handoff** from validated artifacts only; use the audited original-milestone count `10/12` and keep the P9 Android extension active; production/device gates remain separate and may change only with their own evidence (see `docs/validation/roadmap-progress-audit-20261026.md`).
- [ ] **Step 5: Run final verification**: focused Kotlin tests, module/build/lint/consumer tasks, Python artifact tests, `git diff --check`, protected-file hashes, and dirty-worktree inventory. Do not commit or publish as part of this plan.

## Execution Notes

- Task 1 is a prerequisite for enabling the production adapter. If the real AAR probe cannot prove the drain contract on the available device, continue Tasks 2–3 with fake/lifecycle coverage but leave Task 4 production construction unavailable and record the exact blocker.
- A model asset is not yet frozen. Until a manifest, license, files, hashes, and model compatibility matrix are authorized and available, Tasks 2–5 may prove contracts and packaging only; they cannot claim Android VLM inference or P9 completion.
- Review the plan against the design before implementation. Choose either subagent-driven execution (fresh implementer/reviewer per task) or native execution (the current session implements task-by-task, followed by one independent whole-branch review); use the selected execution skill exactly as required by the header.
