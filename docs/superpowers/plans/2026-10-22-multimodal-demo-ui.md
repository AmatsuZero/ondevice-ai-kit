# Single-image Demo Screen/UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a shared single-image screen consuming `MultimodalViewModel` and an opt-in entry while preserving the default demo surface.

**Architecture:** Compose UI binds directly to the existing shared state machine. An optional model parameter gates navigation; no picker, host, model or lease logic enters this package.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, existing Kotlin tests and offline Gradle/Xcode toolchain.

**Spec:** `docs/superpowers/specs/2026-10-22-multimodal-demo-ui-design.md`

**Execution checkpoint:** Task1 committed98a3891; Task2 validation/handoff checkpoint accompanies `docs/validation/multimodal-demo-ui.md`. Independent review pending.

## Global Constraints

- Default `UnavailableMultimodalBridge` remains explicit and visible.
- Do not modify `demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj` or `docs/validation/face-identity.md`.
- No media reads, picker, native model, Android inference backend, dependency/model downloads, signing changes, push or publication.
- Preserve old Face/Speech/Video behavior and exported labels.

## Review Focus

- Missing optional model must compile and render the old surface with no multimodal tab.
- Selecting the multimodal tab must leave an active Speech/Video page before showing the new screen.
- Raw prompt and malformed budget edits must reach the existing VM unchanged and leave generation disabled as the VM dictates.
- Unavailable query/generation failures must be rendered as typed unavailable state rather than success.
- Leaving the page must call `leavePage` and must not restart work on return.

### Task 1: Shared screen and opt-in Compose routing

**Files:**
- Create `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/MultimodalScreen.kt`.
- Modify `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/FaceScreen.kt` and `demo/shared/src/iosMain/kotlin/io/ondeviceai/demo/MainViewController.kt`.
- Create `demo/androidApp/src/androidTest/kotlin/io/ondeviceai/demo/MultimodalScreenInstrumentation.kt`.

**Interfaces:** Consumes `MultimodalViewModel`; produces `MultimodalScreen` and optional `FaceScreen(..., multimodal: MultimodalViewModel?)` plus `multimodalMainViewController`.

- [x] Write real accessibility instrumentation for absent/present opt-in route, unavailable display, raw prompt and malformed budget edits, cumulative update/result identity, typed platform failure and cancellation on leave. Build Android test APK offline; expect missing multimodal FaceScreen argument RED.
- [x] Implement the screen, stable tags, backend/identity/failure rendering, and optional route. Existing callers retain old signatures through defaults.
- [x] Run the instrumentation on the connected owned Android device, requiring status=passed and named scenario evidence; run shared simulator suite. Inspect generated Kotlin/Native header for old entry labels and the new opt-in entry. Android command: `rtk proxy python3 scripts/gradle-local.py :demo:androidApp:assembleDebug :demo:androidApp:assembleDebugAndroidTest -PodaiTestRunner=io.ondeviceai.demo.MultimodalScreenInstrumentation --offline --console=plain`; install both APKs with adb -r and run the named instrumentation.
- [x] Review scope, stage only new/modified UI files and test, and commit `feat: add multimodal demo screen`.

### Task 2: Platform compile and durable checkpoint

**Files:** Update `docs/validation/multimodal-demo-ui.md`, `docs/ROADMAP.md`, `docs/HANDOFF-2026-10-20-speech-demo-bridge.md`, `.superpowers/sdd/2026-10-22-multimodal-demo-ui/{progress,final-review}.md`.

- [x] Run shared framework links, Android APK/lint, Python tools, and protected-file hash/index checks.
- [x] Run a separate author review against all five focus items; record UI/device/model/independent-review gaps explicitly.
- [x] Stage only named docs/source files and commit `docs: checkpoint multimodal demo screen`.
