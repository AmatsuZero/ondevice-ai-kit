# Apple System ASR File Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a testable, file-only Apple Speech framework ASR backend with strict backend selection and forced on-device recognition for P6.

**Architecture:** `ODAIKitSpeech` owns the typed request/result/backend/client contract and reuses the existing capability-scoped registry. `ODAIKitAppleSpeech` owns an injected platform bridge: production wraps `SFSpeechRecognizer` and `SFSpeechURLRecognitionRequest`, while tests use a fake bridge to prove cancellation, late-callback suppression, one-active-task admission, and close waiting. The SDK never requests speech permission, opens a microphone, or falls back to network recognition.

**Tech Stack:** Swift 5.9, Swift Package Manager, iOS 15+, `Speech`, XCTest, Swift actors/MainActor isolation, `CheckedContinuation`.

**Spec:** `docs/superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md` §5 and §7.2; `docs/superpowers/specs/2026-10-16-capability-backends-design.md` §§3–6; this plan is the bounded P6 file-only slice.

## Global Constraints

- This slice accepts a local audio file only. It does **not** replace the overall `AudioChunk`/`AudioFile` and `TranscriptEvent` streaming contract from the design; a later plan must map file recognition and streaming events without changing strict selection semantics.
- `SFSpeechRecognitionRequest.requiresOnDeviceRecognition` is always `true`; no network recognizer or silent fallback is permitted.
- The SDK does not call `SFSpeechRecognizer.requestAuthorization`, request microphone permission, configure an audio session, or capture microphone input.
- iOS 15 remains the minimum deployment target and `ODAIKitSpeech` must not import `Speech` or `AVFoundation`.
- Do not modify Android, KMP, Demo, distribution consumers, signing assets, or unrelated dirty files. Do not commit or push during implementation.

## Review Focus

- An empty locale, non-file URL, or missing file is rejected before a platform task starts; `requiresOnDeviceRecognition` is computed as `true` and therefore has no caller-supplied false path.
- `Default` and `.explicit(id)` select exactly one registered backend; unknown or unavailable explicit IDs never fall back to another backend.
- Authorization not determined/denied/restricted, recognizer unavailable, and on-device unsupported each map to unavailable capability state without prompting the user; fake bridge tests cover these mappings.
- Cancellation and platform callbacks racing across generations cannot resume an old continuation or complete a subsequent request; late callback tests pin token/handle identity.
- `close()` is idempotent, rejects new work, cancels one active task, and waits for the platform terminal callback before release. If the platform does not callback, cancellation of a waiting caller does not reopen admission or release the backend-held task/delegate lease.

### Task 1: Freeze the typed file-only speech contract

**Files:**
- Modify: `apple/Sources/ODAIKitSpeech/SpeechClient.swift` (preserve existing TTS API)
- Test: `apple/Tests/ODAIKitSpeechTests/SpeechClientTests.swift`
- Modify only if required: `apple/Package.swift` (the existing `ODAIKitSpeech` target should remain Core-only)

**Interfaces:**
- Produces `SpeechRecognitionRequest: Sendable, Equatable` with `locale: String`, `audioFile: URL`, and read-only computed `requiresOnDeviceRecognition: Bool { true }`; it is not a caller-controlled generic option. The initializer rejects blank locale and invalid/non-file URL input according to the chosen validation boundary.
- Produces `SpeechRecognitionResult: Sendable, Equatable` with `backendId: String`, `transcript: String`, `isFinal: Bool`, and `confidence: Float?`.
- Produces `SpeechRecognitionBackend: SpeechBackend` with `recognize(_ request: SpeechRecognitionRequest) async throws -> SpeechRecognitionResult`.
- Produces actor `SpeechRecognitionClient` with `recognize(_ request: SpeechRecognitionRequest, backend: SpeechBackendSelection = .default) async throws -> SpeechRecognitionResult` and idempotent `close()`.

- [ ] **Step 1: Write the failing tests** for request validation, result/backend identity, default selection, strict explicit missing/unavailable backend, and closed-client rejection.
- [ ] **Step 2: Run the RED contract tests**

  Run: `rtk proxy swift test --package-path apple --filter ODAIKitSpeechTests.SpeechClientTests`

  Expected: FAIL because the recognition types/client are not yet defined.

- [ ] **Step 3: Implement the minimal typed contract** in `SpeechClient.swift`, delegating selection and lifecycle to the existing `SpeechClient` registry and never selecting another backend after resolution.
- [ ] **Step 4: Re-run the contract tests**

  Run: `rtk proxy swift test --package-path apple --filter ODAIKitSpeechTests.SpeechClientTests`

  Expected: PASS, with all existing TTS tests still passing.

### Task 2: Add an injectable Apple Speech platform bridge and backend lifecycle

**Files:**
- Create: `apple/Sources/ODAIKitAppleSpeech/AppleSpeechRecognizerBackend.swift`
- Test: `apple/Tests/ODAIKitSpeechTests/AppleSpeechRecognizerBackendTests.swift`
- Modify only if required: `apple/Package.swift` (keep `ODAIKitAppleSpeech` as the optional `Speech`-importing target)

**Interfaces:**
- Internal bridge protocol/fakes expose authorization status, locale recognizer metadata (`isAvailable`, `supportsOnDeviceRecognition`), `start(fileURL:requiresOnDeviceRecognition:callback:) -> platform task handle`, cancellation, and terminal/release observation. Production bridge creates `SFSpeechRecognizer(locale:)` and `SFSpeechURLRecognitionRequest(url:)`, sets `requiresOnDeviceRecognition = true`, and maps delegate callbacks; it never calls authorization APIs. `availability()` maps closed/auth/recognizer/on-device state; the mutable file-existence check is repeated by `recognize` immediately before start and maps to `KitError.invalidInput` rather than a stale capability result.
- Public `AppleSpeechRecognizerBackend` has `backendId == "apple.speech.recognizer"`, `capability == .asr`, `availability() async -> CapabilityAvailability`, `recognize(...)`, and idempotent `close()`.

- [ ] **Step 1: Write RED tests with a fake bridge** for authorization mapping (including `.notDetermined` without a prompt), unavailable recognizer, unsupported on-device recognition, missing file validation, forced on-device request flag, one-active-task/busy behavior, and successful final result.
- [ ] **Step 2: Write RED lifecycle tests** for cancellation, idempotent cancellation/close, close rejection, delayed platform terminal callback, resource release after terminal callback, and a late callback from generation N not affecting generation N+1.
- [ ] **Step 3: Run backend RED tests**

  Run: `rtk proxy swift test --package-path apple --filter ODAIKitSpeechTests.AppleSpeechRecognizerBackendTests`

  Expected: FAIL before the backend/bridge implementation exists.

- [ ] **Step 4: Implement production and fake bridge paths**. Guard every callback by active generation and task-handle identity; settle each continuation once. Cancellation marks the SDK operation cancelled and invokes platform cancellation, while cleanup waits for the platform terminal callback before releasing task/delegate state.
- [ ] **Step 5: Define the bounded recognition terminal contract**. Recognition timeout is a bounded terminal failure for the caller, but it does not prove platform release: retain the active task/delegate lease and reject subsequent admission until `task.state == .completed`/`didFinishSuccessfully` proves release. `close()` remains closed and idempotently awaits that release; if a caller cancels the wait, it does not reopen admission or discard the lease. Expose any drain timeout only as a stable diagnostic/state, never as successful cleanup. Tests must exercise the fake delayed-terminal path.
- [ ] **Step 6: Re-run backend and full speech tests**

  Run: `rtk proxy swift test --package-path apple --filter ODAIKitSpeechTests`

  Expected: PASS for Apple ASR, existing AVSpeech, and contract tests.

### Task 3: Verify package/build boundaries and document the slice

**Files:**
- Modify only if required: `docs/validation/speech-backends.md` (record the file-only SDK boundary and test evidence; do not claim device authorization or microphone validation)
- Do not modify: Android/KMP/Demo/distribution files unless a package target is demonstrably missing (the current `ODAIKitAppleSpeech` target already exists).

- [ ] **Step 1: Run the requested targeted test command**

  Run: `rtk proxy swift test --package-path apple --filter ODAIKitSpeechTests`

  Expected: PASS; record the test count/output in the implementation report.

- [ ] **Step 2: Run the requested unsigned iOS simulator build**

  Run: `rtk proxy xcodebuild -project apple/OnDeviceAIKit.xcodeproj -scheme SDKDistribution -configuration Debug -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`

  Expected: successful build. If the project/scheme is unavailable or command is not applicable, record the exact bounded reason and use the closest package-level compile check.

- [ ] **Step 3: Review the diff and `git diff --check`**; ensure no generated audio, credentials, unrelated worktree files, commit, or push is present.
- [ ] **Step 4: Record known validation limits**: no unit test can prove speech authorization UX, system language assets, real-device transcription quality, microphone/audio-session behavior, network isolation under packet capture, or iOS 15 hardware support. Those require a separately authorized device validation run using a fixed local file.

## Self-Review / Coverage Gaps

- The plan covers the requested file-only API, strict selection, authorization-free availability, forced on-device URL recognition, fake bridge testing, one-active generation, cancellation, late callbacks, close waiting, and requested commands.
- The broader `AudioChunk`/`AudioFile`/`TranscriptEvent` streaming API remains intentionally unimplemented and must receive a separate plan; this slice must not claim streaming or microphone support.
- `CapabilityAvailability` currently has no request/file parameter. The plan assigns file existence to request validation and keeps `availability()` focused on closed/auth/recognizer/on-device state; adding `availability(for:)` would be a separate public API decision.
- A recognition timeout is a bounded caller failure, but a platform task that has not terminally completed remains leased and blocks admission. `close()` cannot claim resource release or reopen admission until the platform callback proves it; any drain timeout is only a stable observable diagnostic/state.
