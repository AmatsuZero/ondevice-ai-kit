# Reference Voice Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the current CMP voice demo with the approved reference voice flow so users can record or import a reference voice and a content recording, convert the content locally with that reference timbre, preview both waveforms, play the result, and export it on iOS and Android.

**Architecture:** Keep the existing fixed timbre, pitch, and realtime DSP paths intact. Add a temporary reference-input slot to the shared voice state and a custom-target OpenVoice path that extracts the reference embedding with the existing encoder, then runs the existing converter. Keep reference input, embedding/output files, and playback leases owned by each platform host and released on replacement, cancellation, page exit, and close.

**Tech Stack:** Kotlin Multiplatform Compose, Swift/UIKit, Android AudioRecord and document picker, AVAudioRecorder/AVAudioPlayer, C++ OpenVoice ONNX Runtime, existing platform bridges and test suites.

**Spec:** `docs/superpowers/specs/2026-11-28-reference-voice-conversion-design.md`

## Global Constraints

- Reference conversion is file based and runs after both recordings are ready.
- Reference input is temporary and is not saved as a persistent voice library.
- Existing fixed voice A/B/C, pitch effects, and realtime DSP roles remain available.
- Custom reference conversion uses the existing OpenVoice ONNX assets and local processing path.
- Results expose sample rate, frame count, duration, playback, and export through existing ownership rules.
- No new dependency or model format is introduced.

## Review Focus

- Replacing either input while a conversion or embedding extraction is running must retire late results and release the previous input.
- Leaving the page during recording, playback preparation, or conversion must stop work and remove temporary files.
- A reference and content file with different sample rates must be normalized before encoder/converter use.
- Fixed timbre, pitch, and realtime role paths must preserve their current controls and outputs.
- Output playback and export must hold the result until the consumer finishes, including cancellation and failure paths.

---

### Task 1: Shared reference voice state and CMP screen

**Files:**
- Modify: `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/VoiceEffectsViewModel.kt`
- Modify: `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/VoiceEffectsScreen.kt`
- Modify: `demo/shared/src/commonTest/kotlin/io/ondeviceai/demo/VoiceEffectsViewModelTest.kt`
- Modify: `demo/shared/src/commonTest/kotlin/io/ondeviceai/demo/VoiceEffectsScreenContractTest.kt` if the existing test file covers this screen

**Interfaces:**
- Add a temporary reference input to `VoiceEffectsState`.
- Extend `VoiceEffectsDemoActions` with reference-file selection, reference recording, reference stop, and reference playback actions.
- Extend `DemoVoiceTimbres` with a custom-target render entry that consumes the reference input and content input.

- [x] Add tests for reference input selection, replacement, cancellation, and custom-target process requests.
- [x] Implement state transitions and generation checks without changing fixed-effect behavior.
- [x] Add the approved numbered cards with fixed/reference submode, aligned controls, real PCM waveforms, playback/seek and comparison, result export, and existing realtime tab.
- [x] Run the focused common tests and inspect the generated Compose semantics tags.

### Task 2: OpenVoice custom target extraction and render API

**Files:**
- Modify: `demo/openvoice/OpenVoice.h`
- Modify: `demo/openvoice/OpenVoice.cpp`
- Modify: `demo/openvoice/AndroidOpenVoice.cpp` only if JNI signatures need an explicit custom-target entry
- Modify: `demo/openvoice/CMakeLists.txt`
- Modify: `demo/iosApp/RealtimeVoiceBridge.h` or the existing bridge declarations if required by the iOS host
- Test: `demo/realtimevoice/tests` or a focused OpenVoice native test fixture

**Interfaces:**
- Add a native entry that accepts canonical reference WAV plus content WAV and writes a canonical output WAV.
- Reuse the encoder for the reference WAV, retain its 256-float embedding only for the current render, and pass it as `tgt_se` to the existing converter.
- Preserve the fixed `openvoice_render` and Core ML profiling entry points.

- [x] Add a deterministic native test fixture that proves the custom target path reads the reference embedding and writes a non-empty WAV.
- [x] Refactor shared encoder execution into a reusable helper while preserving tensor shapes and sample-rate normalization.
- [x] Implement custom target rendering with the existing converter and deterministic noise/window handling.
- [x] Build the native targets and run the focused test binary.

### Task 3: iOS reference input and conversion host integration

**Files:**
- Modify: `demo/iosApp/VoiceEffectsHost.swift`
- Modify: `demo/iosApp/VoiceEffectsInputStore.swift` if two input leases need distinct cleanup handling
- Modify: `demo/iosApp/UITests/VoiceEffectsDemoUITests.swift`
- Modify: `demo/iosApp/VoiceEffectsWave.swift` only if waveform extraction is implemented natively

**Interfaces:**
- Reference input uses the same normalized WAV store as content input.
- OpenVoice host keeps a reference lease during embedding/conversion and releases it on every terminal callback.
- Existing AVAudioRecorder and document picker paths are reused for reference capture.

- [x] Add UI tests for reference mode controls, temporary input replacement, and result playback/export visibility.
- [x] Implement reference picker and recorder actions with generation guards.
- [x] Implement custom OpenVoice render and result ownership.
- [x] Build the iOS app target and run the included voice UI tests where the current environment supports them.

### Task 4: Android reference input and conversion host integration

**Files:**
- Modify: `demo/androidApp/src/main/kotlin/io/ondeviceai/demo/VoiceEffectsHost.kt`
- Modify: `demo/androidApp/src/main/kotlin/io/ondeviceai/demo/OpenVoiceAudio.kt`
- Modify: `demo/androidApp/src/androidTest` or the existing voice instrumentation tests

**Interfaces:**
- Reference input uses the existing `VoiceEffectsWave.normalize` and cache ownership rules.
- JNI exposes the custom target render using the same bundled OpenVoice assets.
- Android host keeps both leases until the native render terminal callback.

- [x] Add instrumentation coverage for sample selection, clear/reload, real conversion, playback/seek, existing mode entry points and leave/reopen cleanup.
- [ ] Complete physical-device picker/recording and interruption acceptance; shared state tests cover cancellation and late callback behavior, while those device combinations remain unverified.
- [x] Implement reference document picker and recorder paths.
- [x] Implement custom-target JNI invocation and output publication.
- [x] Build the Android Demo and run the focused JVM/instrumentation tests available on the connected device.

### Task 5: Cross-platform verification and handoff

**Files:**
- Modify: `docs/superpowers/specs/2026-11-28-reference-voice-conversion-design.md` only for measured evidence
- Create: `.omx/artifacts/reference-voice-demo-2026-11-28/verification.md`

- [x] Run common, native, iOS, and Android checks appropriate to the changed files.
- [x] Record sample rates, input durations, output duration, backend, conversion timing, cleanup evidence, and unverified physical listening boundaries.
- [x] Confirm the prototype-only files remain separate from production demo files.
- [x] Report changed files, validation commands, evidence, and remaining gaps.

## Execution record — 2026-11-28

- Continue the existing authorized CMP implementation in the current checkout; preserve the prior prototype changes. External `hy4flash-free` candidates supply implementation, with primary review/integration and local verification.
- Native reference render is implemented and passed real-model deterministic repeat, changed-reference, duration rejection, nonempty PCM and fixed-short-input checks. Evidence: `.omx/artifacts/reference-voice-demo-20261128/native-verified.log`.
- Fixed A/B/C render outputs match pre-change `HEAD` byte for byte for the bundled input. Evidence: `.omx/artifacts/reference-voice-demo-20261128/fixed-differential.json`.
- Shared and platform implementation is integrated. Both mobile preview builds and simulator UI acceptance passed, with the physical-device gaps kept below. App evidence is separate from Mac native checks.
- Ruling: reference embeddings stay in memory for a single conversion, with no embedding file or persistent voice library. Both input leases survive native cancellation until the terminal callback.
- Ruling: use actual PCM peaks and a common duration/dBFS scale for comparisons. Do not use metadata placeholders or independently normalized decorative waves.
- Ruling: use isolated preview bundle/package for device validation, preserving the ordinary installed Demo and its models. No commit, merge or push is included in this feature step.

## Final acceptance record — 2026-11-28

- 127 shared simulator unit tests and 108 Android unit tests passed; Android lint and both preview builds passed. Tested core source hashes remained unchanged during UI-driver corrections.
- iOS fixed A/B/C conversion/playback (46.092 s), reference flow with waveform seek/clear/reload/leave (57.330 s), and realtime mode/file output preservation (39.660 s) each passed. They ran in separate focused invocations; earlier combined failing bundles are retained.
- Android's 12-step reference UI flow passed. Additional system-save verification returned a byte-identical WAV; a separate cache inventory proved the newly loaded temporary input disappeared on leave while pre-existing files were preserved.
- Real output recovered from both app containers: 22,050 Hz / 228,864 frames / 10.3793 s. Cross-platform PCM max difference is 1, with 99.9913% samples identical. This is numeric agreement, not subjective speaker-similarity acceptance.
- Ruling: fix the test drivers' stale accessibility trees and nested-scroll targeting without changing production behavior. Android service content events plus API 34+ cache refresh resolved the stale sample tree. iOS uses the actual embedded viewport and scrolls to the top before revisiting top controls after content height changes. Assertions remain in place.
- Physical recording/listening, reference-mode system-picker imports, iOS system-save and mobile resource/performance distributions remain unverified. No physical-device recording was started.
- Final report: `docs/validation/reference-voice-demo-20261128.md`; machine-readable summary and screenshots: `.omx/artifacts/reference-voice-demo-20261128/`.
- Prototype changes and ordinary installed Demo/models remain preserved. No commit, merge or push was performed.

## Authorized main integration and Wiki update — 2026-11-28

- User subsequently requested local main integration, then explicitly authorized pushing code and publishing the Wiki with physical screenshots.
- Fast-forwarded main to both remotes' `3cef6a3`; the four CI commits are preserved. Revalidated 235 mobile unit tests, Android lint and the iOS arm64 framework; the new local offline CI gate passed 574 tests.
- Physical iPhone 16 Pro Max / iOS 27.2.1 reference sample flow passed: 1 test, 77.932 seconds including UI operations. Final simulator driver regression passed: 1 test, 61.954 seconds. No new product behavior was changed during this acceptance.
- The first physical run's clear-state failure is retained. A bounded top reset before querying clear, plus stronger enabled/name-reset assertions, resolved this run. The exact CBC candidate and first failure remain available; the framework's internal cause is not claimed as proven.
- New Wiki screenshots are raw PNG attachments from the passing physical run. Physical microphone double-recording, subjective similarity, system picker/import, iOS system save and resource distributions remain open.
- See `docs/validation/reference-voice-demo-20261128.md` and `reference-voice-release-20261128.json` for publication evidence and limits.
