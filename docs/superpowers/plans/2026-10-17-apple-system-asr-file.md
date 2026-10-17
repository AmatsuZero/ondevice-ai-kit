# Apple System ASR File Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a testable, file-only Apple Speech framework ASR backend with strict backend selection and forced on-device recognition for P6.

**Architecture:** `ODAIKitSpeech` owns the typed request/result/backend/client contract and reuses the existing capability-scoped registry. `ODAIKitAppleSpeech` owns an injected platform bridge: production wraps `SFSpeechRecognizer` and `SFSpeechURLRecognitionRequest`, while tests use a fake bridge to prove cancellation, late-callback suppression, one-active-task admission, and close waiting. The SDK never requests speech permission, opens a microphone, or falls back to network recognition.

**Tech Stack:** Swift 5.9, Swift Package Manager, iOS 15+, `Speech`, XCTest, Swift actors/MainActor isolation, `CheckedContinuation`.

**Spec:** `docs/superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md` §5 and §7.2; `docs/superpowers/specs/2026-10-16-capability-backends-design.md` §§3–6; this plan is the bounded P6 file-only slice.

**Status:** Approved by the user on 2026-10-17. Tasks 1–3 implemented and verified; final whole-branch review remains. Execution is task-by-task with TDD and independent task-scoped review.

## Global Constraints

- This slice accepts a local audio file only. It does **not** replace the overall `AudioChunk`/`AudioFile` and `TranscriptEvent` streaming contract from the design; a later plan must map file recognition and streaming events without changing strict selection semantics.
- This Apple system backend sets `SFSpeechRecognitionRequest.requiresOnDeviceRecognition` to `true`; no network recognizer or silent fallback is permitted in this slice. The shared request type must not expose that Apple-only flag.
- The SDK does not call `SFSpeechRecognizer.requestAuthorization`, request microphone permission, configure an audio session, or capture microphone input.
- iOS 15 remains the minimum deployment target and `ODAIKitSpeech` must not import `Speech` or `AVFoundation`.
- Do not modify Android, KMP, Demo application behavior, signing assets, or unrelated dirty files. The only permitted distribution-surface changes are the necessary generator/project/source-probe updates listed in Task 3. Commit each completed task with the exact scoped paths; do not include unrelated dirty files.

## Review Focus

- `SpeechRecognitionRequest` rejects blank locale and non-file URL in its throwing initializer; the backend rejects missing, unreadable, or directory URLs immediately before platform start. The host retains the file until the platform task is observed in `.completed` and the backend releases its lease; after timeout/caller cancellation the host awaits `drain()` before deleting or replacing the file; the SDK never deletes it.
- `Default` and `.explicit(id)` select exactly one registered backend; unknown or unavailable explicit IDs never fall back to another backend.
- Authorization not determined/denied/restricted, recognizer unavailable, and on-device unsupported each map to unavailable capability state without prompting the user; fake bridge tests cover these mappings.
- Cancellation and platform callbacks racing across generations cannot resume an old continuation or complete a subsequent request; late callback tests pin token/handle identity.
- `close()` is idempotent, rejects new work, cancels one active task, and waits until the platform task is observed in `.completed` before release. A missing callback does not justify early release; cancelling a waiting caller does not reopen admission or release the backend-held task/delegate lease.
- Timeout is fixed at 30 seconds by default and accepted only in the bounded range 1...300 seconds. `KitError.invalidInput` covers request/constructor validation; `SpeechRecognitionError.timedOut`, `.noResult`, and `.platform(domain:code:)` are stable recognition failures. A platform timeout never claims resource release.

### Task 1: Freeze the typed file-only speech contract

**Files:**
- Modify: `apple/Sources/ODAIKitSpeech/SpeechClient.swift` (preserve existing TTS API)
- Test: `apple/Tests/ODAIKitSpeechTests/SpeechClientTests.swift`
- Modify only if required: `apple/Package.swift` (the existing `ODAIKitSpeech` target should remain Core-only)

**Interfaces:**
- Produces `public init(locale: String, audioFile: URL) throws` for `SpeechRecognitionRequest: Sendable, Equatable`, storing the trimmed locale after separator normalization (`-` to `_`) and `Locale.canonicalIdentifier(from:)` and the file URL. It rejects a trimmed-empty locale and non-file URL. It has no `requiresOnDeviceRecognition` property: that flag is an Apple adapter invariant, not a shared DTO option for future network backends.
- Produces final-only `SpeechRecognitionResult: Sendable, Equatable` with `backendId: String`, `transcript: String`, and `confidence: Float?`; it has no caller-settable `isFinal` flag because this file-only operation returns only one terminal result. `SpeechRecognitionError: Error, Equatable, Sendable` has `.timedOut`, `.noResult`, and `.platform(domain: String, code: Int)`.
- Produces `SpeechRecognitionBackend: SpeechBackend` with `recognize(_ request: SpeechRecognitionRequest) async throws -> SpeechRecognitionResult` and `drain() async` to wait for the current input lease without closing admission.
- Produces actor `SpeechRecognitionClient` with `init(defaultBackendId: String, backends: [any SpeechRecognitionBackend]) throws`, `recognize(_ request: SpeechRecognitionRequest, backend: SpeechBackendSelection = .default) async throws -> SpeechRecognitionResult`, `drain() async`, and idempotent `close() async`. Constructors for request/result values are public; stored fields are immutable.

- [x] **Step 1: Write the failing tests** for throwing request validation (trimmed blank locale, non-file URL), result/backend identity, default selection, strict explicit missing/unavailable backend, and closed-client rejection.
- [x] **Step 2: Run the RED contract tests**

  Run: `rtk proxy swift test --package-path apple --filter ODAIKitSpeechTests.SpeechClientTests`

  Expected: FAIL because the recognition types/client are not yet defined.

- [x] **Step 3: Implement the minimal typed contract** in `SpeechClient.swift`, delegating selection and lifecycle to the existing `SpeechClient` registry and never selecting another backend after resolution.
- [x] **Step 4: Re-run the contract tests**

  Run: `rtk proxy swift test --package-path apple --filter ODAIKitSpeechTests.SpeechClientTests`

  Expected: PASS, with all existing TTS tests still passing.

- [x] **Step 5: Commit the contract**: stage only `apple/Sources/ODAIKitSpeech/SpeechClient.swift`, `apple/Tests/ODAIKitSpeechTests/SpeechClientTests.swift`, and `apple/Package.swift` if changed; commit as `feat: add typed file speech recognition contract`.

### Task 2: Add an injectable Apple Speech platform bridge and backend lifecycle

**Files:**
- Create: `apple/Sources/ODAIKitAppleSpeech/AppleSpeechRecognizerBackend.swift`
- Test: `apple/Tests/ODAIKitSpeechTests/AppleSpeechRecognizerBackendTests.swift`
- Modify only if required: `apple/Package.swift` (keep `ODAIKitAppleSpeech` as the optional `Speech`-importing target)

**Interfaces:**
- Public `init(locale: String, timeout: TimeInterval = 30) throws` on `AppleSpeechRecognizerBackend` canonicalizes and binds one locale; it rejects blank locale or timeout outside 1...300 seconds. An internal test initializer additionally accepts the fake bridge. `SpeechRecognitionRequest.locale` must canonicalize to the bound locale exactly; mismatch is `KitError.invalidInput`, with no language fallback.
- Internal bridge/fakes expose the bound locale's authorization status and recognizer metadata (`isAvailable`, `supportsOnDeviceRecognition`) plus these exact types: `AppleSpeechTaskHandle: Hashable, Sendable`; `AppleSpeechTaskState: Sendable` (`running`, `canceling`, `completed`); `AppleSpeechPlatformEvent: Sendable` (`partial(text: String, segments: [AppleSpeechSegment])`, `final(text: String, segments: [AppleSpeechSegment])`, `cancelled`, `failed(domain: String, code: Int)`, `finished`). The bridge method is `start(fileURL: URL, requiresOnDeviceRecognition: Bool, callback: @escaping @Sendable (AppleSpeechPlatformEvent) -> Void) throws -> AppleSpeechTaskHandle`; cancellation is `cancel(_ handle: AppleSpeechTaskHandle)` and release observation is `state(of handle: AppleSpeechTaskHandle) -> AppleSpeechTaskState`. Production creates `SFSpeechRecognizer(locale:)` and `SFSpeechURLRecognitionRequest(url:)`, sets the request flag to `true`, and maps delegate callbacks; it never calls authorization APIs. `availability()` maps closed/auth/isAvailable/supportsOnDeviceRecognition for the bound locale; file existence/readability/directory checks run immediately before start and map to `KitError.invalidInput`.
- Public `@MainActor final class AppleSpeechRecognizerBackend` has nonisolated immutable `backendId == "apple.speech.recognizer"` and `capability == .asr`, `availability() async -> CapabilityAvailability`, `recognize(...)`, `drain() async`, and idempotent `close() async`. `drain()` waits for the current platform lease to reach `.completed` without closing the backend, allowing reuse after timeout/caller cancellation. Its public initializer is `@MainActor`; callers construct it with the appropriate actor hop.
- `AppleSpeechSegment: Sendable` contains only `confidence: Float`. The bridge and its production implementation are `@MainActor`; use `AppleSpeechAuthorization` (`notDetermined`, `denied`, `restricted`, `authorized`) and immutable `AppleSpeechMetadata(authorization: AppleSpeechAuthorization, isAvailable: Bool, supportsOnDeviceRecognition: Bool)` returned by `metadata() -> AppleSpeechMetadata`. A nil recognizer is metadata with both Boolean fields false. Availability priority is closed → `.unavailable/.closed`, non-authorized → `.unavailable/.permissionDenied`, nil/unavailable/unsupported recognizer → `.unavailable/.unsupportedDevice`, otherwise `.available`; all carry this backend ID, minOS `15.0`, and `offline=true`. Runtime changes are checked again at admission.
- Each handle wraps a unique UUID. Production strongly retains the platform task and delegate; bridge state checks run on MainActor every 20ms while a lease is active, including after result callbacks and timeout/cancel, until `.completed` is observed. `release(_ handle: AppleSpeechTaskHandle)` removes the bridge-held task/delegate only after this observation; releasing a non-completed handle is a test failure. Callbacks synchronously append to a lock-protected inbox before hopping to MainActor; polling drains the inbox too. Completion without a result notification retains the lease until a terminal notification or the caller deadline, avoiding loss of an in-flight final callback. Callback delivery is enqueued onto MainActor. `start` and handle installation execute synchronously in one MainActor turn, with no suspension between them, so even a synchronous callback cannot beat installation. Use a monotonically increasing generation captured by the callback; reject stale generations before consulting current handle state. `AppleSpeechTaskState.running` covers Apple starting/running/finishing states; `canceling` and `completed` map directly.
- Internal `AppleSpeechClock: Sendable` exposes `sleep(seconds: TimeInterval) async throws`; production uses cancellable `Task.sleep`, and tests control wakeups. Inject it through the test initializer for both the 20ms release observer and recognition deadline. Admission checks `Task.checkCancellation()` before starting; caller cancellation maps to `CancellationError`, overlapping calls to `KitError.busy`, and closed calls to `KitError.closed`. Success and platform failure settle only after observed completion/release; caller cancellation and timeout may settle earlier, so the host must keep the input until a subsequent `drain()` or `close()` finishes on those paths. A saved final transcript is returned after release; completion without a final transcript/error is `.noResult`.

- [x] **Step 1: Write RED tests with a fake bridge** for bound-locale availability (authorization `.notDetermined`/denied/restricted without prompting, unavailable recognizer, unsupported on-device recognition), locale mismatch/no fallback, missing/unreadable/directory file validation, forced `requiresOnDeviceRecognition == true`, one-active-task/busy behavior, and successful final result.
- [x] **Step 1a: Pin constructor and result edge cases**: reject timeout NaN, infinity, 0, and 301; accept 1 and 300. Test equal canonical locale spellings, pre-cancelled calls starting no task, final confidence mean for `[0.2, 0.8, NaN, -1, 2]` equals 0.5, all invalid confidence yields nil, and no final-result success is delivered before release.
- [x] **Step 2: Write RED lifecycle tests** for cancellation, idempotent cancellation/close, public `drain()` reuse after timeout/caller cancellation, close rejection, final/error/cancel callbacks while state remains running/canceling, `.completed` reached without another callback, synchronous callback during start, failed start leaving admission reusable, and a late callback from generation N not affecting generation N+1. Inject a controllable polling clock in tests so these orderings require no wall-clock sleeps. Assert timeout cancels the platform once but blocks admission until completed; repeated close/drain callers all wait for the same release; partial-only completion fails with `.noResult` and never returns partial text.
- [x] **Step 3: Run backend RED tests**

  Run: `rtk proxy swift test --package-path apple --filter ODAIKitSpeechTests.AppleSpeechRecognizerBackendTests`

  Expected: FAIL before the backend/bridge implementation exists.

- [x] **Step 4: Implement production and fake bridge paths**. Guard every callback by active generation and task-handle identity; settle each continuation once. Cancellation marks the SDK operation cancelled and invokes platform cancellation, while cleanup waits for the platform task state to become `.completed` before releasing task/delegate state. Aggregate confidence from the final platform result's valid finite segment confidences (mean in 0...1); return `nil` when none are valid, and never overwrite an already concrete confidence with a later `nil` callback.
- [x] **Step 5: Define the bounded recognition terminal contract**. Recognition timeout is a bounded terminal failure for the caller, but it does not prove platform release: retain the active task/delegate lease and reject subsequent admission until `state(of:) == .completed`. Apple callback events such as `final`, `failed`, and successful-finish notifications are result notifications, not release proof; the Speech framework does not guarantee their order relative to `.completed`. `drain()` and `close()` await that state; `drain()` preserves admission after release, while `close()` remains closed and idempotent. If a caller cancels a drain/close wait, the backend does not reopen admission or discard the lease. Tests must exercise the fake delayed-terminal path.
- [x] **Step 6: Re-run backend and full speech tests**

  Run: `rtk proxy swift test --package-path apple --filter ODAIKitSpeechTests`

  Expected: PASS for Apple ASR, existing AVSpeech, and contract tests.

- [x] **Step 7: Commit the backend**: stage only `apple/Sources/ODAIKitAppleSpeech/AppleSpeechRecognizerBackend.swift`, `apple/Tests/ODAIKitSpeechTests/AppleSpeechRecognizerBackendTests.swift`, the shared `SpeechClient.swift`/`SpeechClientTests.swift` drain and locale fixes, this plan, and `apple/Package.swift` if changed; commit as `feat: add Apple on-device file recognition backend`.

### Task 3: Verify package/build boundaries and document the slice

**Files:**
- Modify: `scripts/generate-distribution-project.rb` (make the new AppleSpeech source inclusion explicit and fail generation if the expected ASR source is omitted)
- Regenerate: `apple/OnDeviceAIKit.xcodeproj/project.pbxproj` and `apple/OnDeviceAIKit.xcodeproj/xcshareddata/xcschemes/SDKDistribution.xcscheme` through the generator; do not hand-edit generated project files.
- Modify: `examples/ios-speech-native/ContractConsumer.swift` and `examples/ios-speech-native/SpeechConsumerApp.swift` for compile-only recognition symbol/type probes and explicit Apple backend construction.
- Modify only if required: `docs/validation/speech-backends.md` (record the file-only SDK boundary and test evidence; do not claim device authorization or microphone validation)
- Modify: `README.md` and `docs/ROADMAP.md` (correct the current SDK delivery boundary and next validation gates)
- Test: `tools/tests/test_distribution_project.py` (temporary-project generator regression checks)
- Do not modify Android/KMP/Demo application behavior or distribution artifacts beyond the necessary generator/project/consumer source updates.

- [x] **Step 1: Write the failing distribution inclusion check** that asserts `scripts/generate-distribution-project.rb` adds `AppleSpeechRecognizerBackend.swift` to `ODAIKitAppleSpeech` and links `Speech.framework` only to that target.
- [x] **Step 2: Run the check before generator changes** and confirm it fails because no Apple Speech framework linkage/ASR source assertion exists.
- [x] **Step 3: Update and run `scripts/generate-distribution-project.rb`**. Preserve its sorted source discovery, add an explicit required-source assertion for `Sources/ODAIKitAppleSpeech/AppleSpeechRecognizerBackend.swift`, add `Speech.framework` to `ODAIKitAppleSpeech` only, and regenerate the project/scheme. Re-run the inclusion check and confirm it passes; rerun the generator and assert the project/scheme diff is empty.
- [x] **Step 4: Extend the consumer type probes**. Keep `ContractConsumer.swift` limited to `ODAIKitCore` + `ODAIKitSpeech`, constructing the new request/client-facing values without importing `Speech`; make `SpeechConsumerApp.swift` construct `AppleSpeechRecognizerBackend(locale: "en-US")` and typed recognition client without executing recognition.

- [x] **Step 5: Run the requested targeted test command**

  Run: `rtk proxy swift test --package-path apple --filter ODAIKitSpeechTests`

  Expected: PASS; record the test count/output in the implementation report.

- [x] **Step 6: Run the requested unsigned iOS simulator build**

  Run: `rtk proxy xcodebuild -project apple/OnDeviceAIKit.xcodeproj -scheme SDKDistribution -configuration Debug -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`

  Expected: successful build with `AppleSpeechRecognizerBackend.swift` present in the compile log. An unavailable project/scheme is an unresolved distribution gate; package compilation alone cannot satisfy this step.

- [x] **Step 7: Run the generated binary consumer type/link probe**

  Package the current source into a fresh, unused artifact directory (the script rejects overwrite), then generate and build that consumer:

  ```bash
  ODAI_ARTIFACTS_DIR=artifacts/P6/apple-file-asr sh scripts/package-apple.sh
  ODAI_ARTIFACTS_DIR=artifacts/P6/apple-file-asr ruby scripts/generate-binary-consumer.rb --speech
  xcodebuild -project artifacts/P6/apple-file-asr/SpeechBinaryConsumer/SpeechBinaryConsumer.xcodeproj -scheme SpeechBinaryConsumer -destination 'generic/platform=iOS Simulator' -derivedDataPath artifacts/P6/apple-file-asr/SpeechBinaryBuild CODE_SIGNING_ALLOWED=NO build
  ```

  Prefix each command with `rtk proxy` (and use `rtk proxy env` for assignments). Build `ContractConsumer.swift` separately with only fresh Core/Speech framework search paths using the existing contract-consumer compile check. Inspect all fresh framework binaries with `otool -L`: the probes must compile the new public symbols, only `ODAIKitAppleSpeech` may directly depend on `Speech.framework`, and Core/Speech must remain free of that dependency. Do not reuse pre-ASR XCFrameworks.

- [x] **Step 8: Review the diff and `git diff --check`**; ensure no generated audio, credentials, or unrelated worktree files are included.
- [x] **Step 9: Record known validation limits**: no unit test or simulator build can prove speech authorization UX, system language assets, real-device transcription quality, microphone/audio-session behavior, network isolation under packet capture, file retention across a real platform release, or iOS 15 hardware support. These remain device-validation follow-ups using a fixed local file and must not be reported as covered by this plan.
- [x] **Step 10: Commit the scoped plan implementation**

  ```bash
  git add scripts/generate-distribution-project.rb apple/OnDeviceAIKit.xcodeproj examples/ios-speech-native docs/validation/speech-backends.md tools/tests/test_distribution_project.py README.md docs/ROADMAP.md
  git commit -m "feat: add Apple file ASR distribution probes"
  ```

## Self-Review / Coverage Gaps

- The plan covers the requested file-only API, strict selection, authorization-free availability, Apple-only forced on-device URL recognition, fake bridge testing with an explicit event/handle/state ABI, one-active generation, cancellation, late callbacks, close waiting, exact locale binding, stable errors, timeout bounds, generated project inclusion, and consumer symbol/link probes.
- The broader `AudioChunk`/`AudioFile`/`TranscriptEvent` streaming API remains intentionally unimplemented and must receive a separate plan; this slice must not claim streaming or microphone support.
- `CapabilityAvailability` currently has no request/file parameter. The plan binds a canonical locale in `AppleSpeechRecognizerBackend.init(locale:timeout:)` and keeps `availability()` focused on that locale's closed/auth/recognizer/on-device state; request file checks remain admission validation and adding `availability(for:)` would be a separate public API decision.
- A recognition timeout is a bounded caller failure, but a platform task that has not reached `.completed` remains leased and blocks admission. `final`, `failed`, successful-finish, and cancellation callbacks do not by themselves prove release; `drain()`/`close()` cannot claim resource release or reopen admission until the bridge state reports `.completed`. Any drain timeout is only a stable observable diagnostic/state.

## Official API Evidence

Verified against the installed Xcode 27.1 iPhoneOS SDK under `System/Library/Frameworks/Speech.framework/Headers/`:

- `SFSpeechRecognitionRequest.h`, `requiresOnDeviceRecognition`: honored only when the recognizer supports on-device recognition; availability and admission must both check that support before start.
- `SFSpeechRecognizer.h`, `recognitionTaskWithRequest:delegate:`: the task does not retain its delegate. The bridge must retain it until release.
- `SFSpeechRecognizer.h`, `queue`: controls task handlers/delegates, defaults to main queue, and does not control the authorization callback. This SDK never requests authorization.
- `SFSpeechRecognitionTask.h`, `SFSpeechRecognitionTaskState`: canceling can still have recording in progress; completed explicitly ends delivery and recording. Delegate callbacks carry no documented ordering/equivalence guarantee with completed.
- `SFSpeechRecognitionTask.h`, `finish`: has no effect for URL requests. The SDK does not infer an early safe file-deletion point from buffering behavior.
