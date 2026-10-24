# Typed video Demo input / state / host checkpoint

2026-10-24. Implemented in foundation feat/sdk-foundation from 8d2cd9c. User approved independent image/video stores and one shared state machine. Changes are local uncommitted; no push/merge/release. Author review only; independent review pending. Still 5/12, P9 active.

## Implementation

- Shared MultimodalInputKind IMAGE/VIDEO appended to state; selectVideo preserves raw prompt, opaque token, budget and strict selection. Same-token media switch cancels the pending request and waits for terminal. Video dispatch uses only optional MultimodalVideoBridge; legacy bridge rejects BACKEND_UNAVAILABLE, no image fallback. Existing close/cancel/update filtering retained.
- Optional MultimodalVideoDemoActions preserves old image-only conformers. UI adds video selection, experimental heading and profile disclaimer; old tags remain.
- Both hosts accept optional independent video store/picker. One picker generation and selected token across media; previous store retirement keeps independent operation leases alive. Old image call sites remain valid. Android Activity shares the guarded GetContent launcher using image/* or video/*, bounded initial IO staging and default unavailable backend.
- Apple picker uses .movie for video; default Demo and optional MLX factory configure distinct stores. Model host factory injects videoInputs and cleans image store if video-store creation fails.
- Android private copy is bounded to 32MiB and rejects empty input; Apple MultimodalInputStore now owns its registry directly with O_NOFOLLOW descriptor/fstat regular-file checks, exclusive private destination, 64KiB streaming, 32MiB cap, cancellation checkpoints, partial-copy retry ownership and idempotent leases. Neither store interprets token as path.

## Fresh verification

| Gate | Result |
| --- | --- |
| Shared new tests | Missing selectVideo/inputKind compile RED, then 64 tests / 5 suites, zero failures/errors/skips; 4 new VM tests |
| KMP Demo framework | iosSimulatorArm64 debug link passed |
| Android host/store | Missing optional host and maxBytes APIs compile RED; final Demo 29 tests / 6 suites, zero failures/errors/skips; 3 new tests |
| Android build/lint | offline testDebugUnitTest + assembleDebug + lintDebug successful, 539 tasks (24 executed, 515 up-to-date) |
| Swift Demo ABI | Missing host/store/picker/factory APIs each real compile RED; final 59 XCTest, zero failures; 4 new tests |
| Full base iOS Demo | Fresh generated SpeechDemo, multimodal/video enabled, generic simulator unsigned build successful |
| Tools | Standard launch first had PYTHONPATH/import errors; corrected path gave 133 tests with one manifest sandbox error. Process-local wrapper adds --disable-sandbox only to swift package dump-package: 133/133 passed. Repository tests unchanged for this workaround. Not an unqualified standard-command pass |
| Checks | changed core Swift strict format and git diff --check passed; index empty; both protected SHA256 unchanged |

Xcode initially failed sandbox-exec sandbox_apply Operation not permitted, including outside-sandbox retry. -IDEPackageSupportDisableManifestSandbox=YES allowed local base-package compilation, with automatic resolution disabled and skipPackageUpdates. No dependency download. Raw xcresults retain compile RED and an intermediate 59-test run with one old image test failure. That old test expected only [2], but asynchronous MainActor hops can legitimately deliver [1,2]. Test now asserts strictly increasing sequence ending at 2 and unchanged updates after terminal; no bridge product logic was changed. Final 59/59 passed.

Evidence: artifacts/verification/multimodal-video-demo-red-20261024/{build/Logs/Test,final-xcode-test.log,tools-no-manifest-sandbox.log,base-package.json}; full base app at multimodal-video-demo-app-20261024/build.log. Gradle XML: demo/shared/build/test-results/iosSimulatorArm64Test and demo/androidApp/build/test-results/testDebugUnitTest. RED Gradle outputs are session execution evidence, not separately archived raw log files.

Protected files remain original: project.pbxproj b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5; face-identity.md 6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822. Both un-staged.

## 10:10 connected-device follow-up

User confirmed both devices available. Fresh devicectl/adb confirmed physical iPhone16ProMax iOS27.0.1 and TECNO KM9 Android15/API35. No minimum-system conclusion.

- Android existing MultimodalScreenInstrumentation initially returned status=failed at unavailable-input-name because old test expected 尚未选择图片 but current UI says 尚未选择输入. Updated that assertion and extended the actual shared page test with video action, independent store token rejection, default BACKEND_UNAVAILABLE, clear and owned-file cleanup. Tiny [1,2,3] synthetic bytes are not a decodable video. Real API35 execution returned status=passed and videoInputActions=passed-video-select-isolation-unavailable-clear-cleanup-synthetic-bytes-only; existing opt-out, raw edits/cancel/drain, fixture output identity, failures, cross-page and image input checks passed. This is accessibility-action UI instrumentation, not physical touch or real system document selection. Raw: android-device-ui.log in this checkpoint artifact directory. Only project app/test APKs installed.
- Refreshed :demo:shared:linkDebugFrameworkIosArm64 offline. Fresh basic generated iOS device build succeeded with existing DEVELOPMENT_TEAM=4X996UV8LC, without provisioning-update flags or signing asset changes. Installed and launched io.ondeviceai.ondeviceaidemo on the connected iPhone. device-build.log is under multimodal-video-demo-app-20261024. No screenshot/private media/recording/model inference performed. Launch success is not UI/video validation.
- All code remains uncommitted and no remote writes. Next order remains staging responsiveness/cancellation -> enhanced fixed-cache build -> separately evidenced KMP video weight lifecycle. Remaining P4/P6/P7/P10/P11 gates do not disappear because modern devices are connected.

## Follow-up: Android cancellable store prerequisite

Fresh continuation from unchanged HEAD 8d2cd9c; all changes still local and uncommitted. Android import previously held its registry monitor across the entire bounded copy, so simply dispatching the call to IO could still block UI-side close/acquire/remove. MultimodalInputStore now reserves an in-flight import under its lock, copies outside that lock, checks cancellation/closed state at chunk boundaries, and publishes only under the lock while open. In-flight ownership keeps the private directory alive until cleanup completes. Failure retains owned partial files for deletion retry; operation leases are unchanged.

Two new deterministic tests cover cancellation after one written chunk with no partial file, and a latch-paused copy while close returns, an older operation lease remains valid/releasable, the directory stays alive for the worker, then the worker rejects publication and reclaims the final directory. Real compile RED: the old importImage API rejected the new checkpoint argument at both call sites. RED output is retained in session execution, not a separately archived raw file. GREEN and strengthened lease assertions: offline testDebugUnitTest + assembleDebug + lintDebug exit 0, JUnit 31 tests / 6 suites / 0 failures/errors/skips. Raw GREEN: artifacts/verification/multimodal-video-demo-red-20261024/android-store-cancellable-green.log.

This is not asynchronous host completion: both hosts still invoke their final copies synchronously. Android staging task and picker-temp-file lifetime/join, and Apple worker/MainActor registry split remain next. No new device test, iOS build, model inference, remote write, or independent review in this increment. Protected hashes rechecked unchanged.

## Follow-up: asynchronous host wiring and regression

User explicitly authorized parallel work. Initial iOS implementation dispatch hit 429; a later isolated iOS worker completed changes, subsequently inspected and rerun by the main session. Neither its claimed results nor its notification summaries were accepted without filesystem/log checks.

Android host now optionally accepts importScope/importDispatcher; the actual Activity supplies lifecycleScope and IO staging. The no-scope path remains synchronous for source-compatible embedded/test hosts. Imports have cancellation, generation-gated UI publication, stale-token retirement, and cleanup if withContext cancels after successful copy before delivery. clear/leave/non-preserved background/close invalidate imports. awaitImports joins worker completion; Activity holds the picker-owned temporary copy through a NonCancellable join before closing it. Tests cover queued cancellation/lifecycle boundaries and real IO success. Missing scope/dispatcher/awaitImports produced compile RED; final Demo JUnit 34/34, zero failure/error/skip. APK/lint passed after production wiring; the last additional lifecycle-boundary test also passed. Logs: multimodal-video-demo-red-20261024/android-host-async-{green,final}.log.

Apple store adds startImport with detached POSIX copy and MainActor registry completion, explicit worker cancellation forwarding, in-flight directory ownership, owned-destination tracking, and security-scoped source access held until worker termination. Synchronous importImage remains for compatibility; host defaults to asynchronous startImport. Host invalidates imports on lifecycle/selection changes and retires late successful tokens; stale failures cannot overwrite current state. Existing host/factory tests now await actual input tasks rather than assuming synchronous picker callback completion. Main session ran the original complete generated Demo ABI project: 67 tests, zero failures. Raw author-full.log and xcresult under artifacts/verification/multimodal-ios-async-20261024. Xcode emits SDK build-version diagnostic warnings; these are not test failures.

This closes the implementation/unit-regression increment, not P9 overall. Current asynchronous UI has not been rerun on physical devices/system pickers; enhanced MLX app build and KMP-to-fixed-weight video lifecycle remain pending. Shared 64 and previous device results are historical, not fresh tests of these host changes. No new commit/push; protected hashes unchanged; independent review remains pending.

## Remaining gates / next package

This is a source/demo lifecycle checkpoint, not end-to-end video acceptance. No model/media assets or device inference were run. Enhanced MLX factory source wiring has not been compiled in an enhanced app this session. Binding-only ABI and complete all-module Android regression were not rerun. No UI instrumentation/physical picker test or independent APPROVE.

Historical synchronous-copy limitation above is superseded by the asynchronous host follow-up: production Android Activity and Apple host now dispatch final copies off-main, while compatibility synchronous APIs remain. Physical UI responsiveness and real system picker validation are still pending; do not claim zero UI blocking or device acceptance from unit tests.

Next: independently review this diff, strengthen factory video generation/provider and failure-path tests, complete asynchronous host staging if required, compile enhanced app from fixed local caches, then separately verify KMP->fixed-weight video success/cancel/drain/reentry/close with generated fixture and exact source/hash provenance. Keep Android production backend, original upstream 8->7 failure, short-sample rejection, binary archive exit65, natural temporal quality, GPU/energy, minOS, real picker, old Video UI and P6 gates open.
