# iOS Demo camera owner release

2026-10-27 用户授权更新：已验证改动可分批提交并同步 `origin` + `jihu` 的现有 foundation 分支；覆盖本计划此前的暂不提交约束。受保护文件和未完成线程取证保留本地；不包含合并、tag 或发布。

Restore the existing P4 owner-destruction boundary without changing pause/resume behavior. Scope: `DemoController`, `CameraSource`, a dedicated simulator XCTest harness, and the P4 validation ledger. Preserve the dirty worktree and both protected files. Do not commit, publish, change signing, or claim that this fixes the observed Compose/Skia `GrOp` leaks.

## Task 1: Close the face bridge when its Demo owner is destroyed

- Add a real-controller regression: retain its bridge, release the controller, and require bridge admission to close and pending input references to be released.
- Run the test against the current code and observe a behavioral failure.
- Add owner teardown on the main actor; cancel owner tasks and the pending photo submission, close the frame queue, and close the face bridge.
- Re-run the regression and confirm native detector availability becomes closed.

## Task 2: Drain and detach the camera on terminal owner close

- Keep `stop()` as a resumable pause. Add an idempotent asynchronous terminal `close()` on the existing serial capture queue.
- Test with a real `AVCaptureSession`/video output: remove outputs and delegate, release the callback closure, and wait for previously queued frame work before close returns. Exercise repeated close.
- Implement the terminal gate and teardown; invoke it from owner destruction. Do not move camera work onto the main actor.
- Run the full dedicated simulator suite, existing lifecycle/photo-submission checks, the default Demo simulator build, and `git diff --check`; independently review the bounded diff.

## Evidence and stop condition

Record RED/GREEN logs and xcresults under ignored `artifacts/verification/p4-owner-release-20261026/`. A passing simulator suite proves the exercised owner/session boundaries only. Real capture inputs/hardware, PHPicker EXIF 2/6/7, and a fresh same-device Leaks trace remain required before closing P4.

## Task 3: Physical capture release validation

- Device lock-state inspection now reports no passcode required. Extend only the isolated test generator with a hosted device mode using the existing Demo app/test bundle identifiers and installed signing assets; no provisioning updates.
- Exercise real capture callbacks without saving images, then assert terminal close stops the session, removes hardware input/output, drains callbacks, and rejects restart. Run the owner-release tests on the same device.
- Restore the ordinary Demo app after the temporary native test host. Preserve its existing container and the protected project file; do not uninstall or clear application data.
- Keep PHPicker EXIF and same-device Leaks as separate evidence gates. A physical XCTest result alone does not prove zero leaks.

## Task 4: Protect frame callback registration across terminal close

- Investigation of the existing Swift concurrency warning found an unsynchronized mutable callback shared by the owner, capture callbacks, and close. Close currently permits later callback registration to retain new owners after terminal teardown.
- Add a real lifetime regression: close the camera, register a callback retaining a token, drop the caller's token, and require the camera to reject registration and release the token. Observe runtime RED before editing production code.
- Protect callback reads, replacement, and terminal admission with a lock. Never invoke callbacks or release replaced callback captures while holding the lock. Seal callback admission before draining the frame queue. Keep start/stop/session state on the existing capture serial queue.
- Exercise concurrent replacement/close and reentrant capture destruction, then run the complete lifecycle suite and existing Apple verification. Keep iOS15 compatibility and the protected project file.
- This fixes callback synchronization, not the entire Demo's Swift6 migration. Do not add a blanket unchecked Sendable conformance or treat warning suppression as a fix; report remaining strict-concurrency diagnostics separately.

## Task 5: Sustained physical controller/bridge/Vision pipeline

- Add an opt-in device-only XCTest that creates the actual DemoController and starts its camera through the production entry point. Use existing camera authorization and retain no screenshots or camera images.
- After at least3 successful native results, sample four30-second windows; each must add at least10 successful results, matching the existing sustained UI test's minimum. Require the host to remain foreground and the total active window to cover120seconds.
- Stop through the actual controller, await retained bridge jobs, and require no new result for5seconds. Release the controller and require the retained bridge/detector to close with no pending inputs/jobs.
- Record a per-run ID, PID, monotonic elapsed seconds, counts, foreground/thermal state and process RSS snapshots. RSS snapshots are not footprint, peak, GPU memory, energy, or a pass/fail leak budget.
- Keep this opt-in via ODAI_CAMERA_LIFECYCLE_SUSTAINED=1 and require device mode; default quick suites remain unchanged. Generate a fresh isolated project and restore the ordinary Demo after the device run.
- This is native controller/pipeline evidence, not full Compose rendering, UI controls, PHPicker, or Leaks attribution. Only fix product behavior if an actual defect is reproduced.

## Task 6: Rendered Demo sustained/post-stop resource recording

- Reuse the actual production App body, controller and Compose framework in an isolated generated device app. Add a validation call only in the generated App source; verify exactly one insertion site. Do not edit the protected project or product sources. Keep the same authorized bundle/signing and restore the ordinary Demo afterward.
- New probe `probes/ios-camera-lifecycle/CameraResourceValidation.swift` requires an explicit UUID launch argument and existing camera authorization. Record app-owned JSON only (no frames/screenshots), containing UUID/PID, wall/monotonic times, native success counts, visible view/controller class counts, Metal layer count, RSS/thermal and bridge pending counts.
- Wait for a run-specific app-private trigger file before capture; the operator copies that file only after a live Leaks recording has started for this exact installed PID. After a10-second baseline, require native warmup>=3, four30-second windows with>=10 successes, and30seconds post-stop with unchanged counts and no pending inputs/jobs. Keep the production last-image/Compose state intact.
- Generate via `scripts/generate-camera-resource-app.rb`; build the real device executable, validate no-argument inactivity, then record Leaks with explicit PID/time limit. Match launch path/PID, report runID, source hashes and trace timestamps before drawing conclusions. Preserve failing/partial evidence.
- This adds measurement, not a production fix: no manufactured RED. If a behavior defect is found, first reproduce it in a focused regression before changing product code. Existing blocked UI controls, full owner/render teardown, minimumOS and review remain separate. RSS or unavailable stacks cannot establish zero leaks or a root cause.

## Task 7: Resolve the newly symbolized allocation sites

- Read the existing PID27593 trace and fixed upstream allocator code before selecting a production change. Resolve exported frame references for the seven known addresses and retain allocation timestamps/thread IDs, not just the responsible-frame column.
- Verify the relevant TLS/new/delete/clear path in the current local validation binary, and record the difference between allocation evidence, allocator policy and a proved runtime owner. Keep unresolvable pre-recording objects open.
- Record current source/binary hashes and provenance limits. If evidence supports a cache hypothesis but does not establish release, continue with real page exit/reentry and same-process observation; do not relabel Leaks as false positives or upgrade dependencies from an allocation symbol.

## Task 8: Normal rendered page exit and reentry

- Extend the isolated generator with an explicit page-cycle mode. Move the unchanged production WindowGroup content and its StateObject into a generated page View; the generated root inserts/removes that page. Record all transformations in the source manifest and keep the default recording mode unchanged.
- Use a separate probe with weak references only to the page controller, bridge, Compose view controllers and render layers. Do not retain UIKit traversal roots or force GC, allocator cleanup, dependency changes or private APIs. Use three fresh page identities in the same process.
- Require an explicit run UUID and live-recording trigger. For each cycle, require visible Compose/Metal rendering, native warmup, 30 seconds of successful camera operation, stop/drain and five seconds with no new results. Remove the actual page, observe weak references and visible render state for 30 seconds, then reenter. Preserve unresolved references as evidence rather than silently treating them as released.
- Freeze source/framework hashes, generated manifest, PID/runID and all timestamps. Build before installation; require unlocked device and restore the verified ordinary Demo after the bounded run. Run the existing generator mode check as well as the new mode. No production RED is invented for this measurement-only work.
- Match the trace to every cycle before comparing allocation growth. A passing camera cycle, vanished view hierarchy, or weak Swift owner alone is not proof of zero native leaks. P4 stays open until its remaining gates are independently satisfied.

### Task 8 follow-up: Last bridge without reentry

- The first run released page owners/renderers by the30-second sample but still held a bridge; earlier bridges were gone at the next warmup. Keep those original30-second observations and add idle-tail samples at nominal31/35/60seconds after the final removal, with actual monotonic page-absent durations. Do not create another page or force GC.
- Record bridge admission availability and pending input/job counts through a synchronous weak-reference snapshot only; no bridge may be retained across an await by the observer. This separates a closed-but-not-yet-deallocated wrapper from active resource retention.
- Rebuild in a fresh directory, repeat the same three-cycle protocol and300-second exact-PID recording, preserve the original run and restore the ordinary Demo. Validate the reviewed metadata/status changes on this actual build. A late-release result does not erase the native GrOp residuals or prove a cache plateau.

## Task 9: Lock pause/resume behavior before concurrency isolation changes

- Reproduce CameraSource diagnostics with Swift6/complete strict checking at the iOS15 deployment target; retain diagnostics as well as exit status. An exit0 that still contains Sendable warnings does not pass a warning-free gate.
- Extend physical capture validation with pause/quiet/resume using the same configured input/output and callback, then an immediate stop/start pair followed by fresh ongoing callbacks. Finally require terminal close to detach the session and leave no callbacks. This catches a pause accidentally made terminal and a serial executor migration that reorders stop/start. Always close on timeout/error; save counts and run identity only, never frames.
- Run existing device lifecycle regressions with the new test and restore the previously verified ordinary Demo. No production change is needed to characterize existing behavior; do not manufacture a failing runtime test if the current lifecycle passes.
- Decide isolation only after verifying deployment availability, synchronous callback admission, FIFO ordering and drain ownership. Do not wrap the same state in an unchecked box, silence warnings, introduce per-frame task backlogs, move blocking session work to the main actor or silently raise minimum OS. This task can close the new behavior-evidence substep while leaving the production concurrency migration explicitly open.

## Task 10: Isolate capture ownership and the frame handoff

- First compile a real Sendable transfer/registration contract against the current CameraSource and observe its failure. Existing lifecycle and physical pause/resume regressions remain the runtime gates.
- Keep CameraSource as an immutable, compiler-checked Sendable facade. Synchronously enqueue start/stop/close into one AsyncStream control mailbox; one consumer actor handles commands without suspension inside session mutations. A dedicated serial executor keeps blocking AVCaptureSession operations off the main actor and cooperative thread pool. Use the iOS13-compatible UnownedJob entrypoint; do not call assumeIsolated from platform callbacks.
- The capture actor exclusively owns the session and terminal flag. Initialization transfers the session with sending ownership. Close seals registration, stops/detaches, drains the frame delegate queue and clears its context before resuming the caller. Multiple close commands join the same ordered consumer. Finishing the mailbox on facade destruction must also close an unclosed session.
- Use an immutable Sendable delegate with Apple's Sendable CIContext. Its only shared mutable state is a private callback slot whose callbacks are explicitly Sendable and whose registration/terminal state is locked. On iOS15, NSLock needs a narrowly documented unchecked conformance on that slot; this is not a wrapper around CameraSource/session state. Never invoke a callback or destroy replaced captures under the lock. Keep the existing synchronous bounded frame admission.
- Isolate DemoController and its Kotlin action conformances to MainActor. Capture each generation's Sendable LatestFrameQueue in the callback; the owner only consumes on main. This removes the shared mutable queue pointer/admission lock. Decode-only helpers stay nonisolated. Detached image encoding returns Data; construct KotlinByteArray on the owner actor.
- Verify strict compilation of the actual sources/contract at the iOS15 deployment target, simulator lifecycle tests/TSan, default Demo build and physical lifecycle/sustained tests. Rebuild a matching ordinary Demo before replacing the device test host. Keep minimum-OS runtime, Compose/Skia native leaks, UI/EXIF and whole-P4 review separate. Do not promote artifact-only experiments to a production fix.

### Task 10 verification follow-ups

- The optional Speech/Video/Multimodal build exposed a real compile failure when the new MainActor controller's nonisolated deinit accessed Published multimodal state. Use `isolated deinit` and verify default/optional builds plus owner lifecycle again. The installed Swift6.4 SDK embeds `swift_task_deinitOnExecutorMainActorBackDeploy` for iOS13+, with an iOS18.4 availability branch; retain SDK and actual object-symbol evidence. This is deployment support evidence, not iOS15 device acceptance.
- Full host verification exposed an existing IdentityTemplateLeaseTests race: after retirement/finishOperation, its asynchronous stop delivery can still own the writer lock. Join the documented `store.close()` barrier before reopening in that test. Keep the revocation/error assertions unchanged and do not change Identity production behavior. Preserve the original469pass/1busy failure, unmodified focused pass, and complete post-fix host result.

## Task 11: Current-source Compose camera UI acceptance

- After Task10 native lifecycle acceptance, refresh device lock state and verify no parallel build/test writer. Build the existing protected Demo project without editing it, using installed signing assets and no provisioning updates.
- Run the two existing UI tests for stable controls and background/resume/user-stop against the actual Compose Demo. Select only these named tests; do not open the photo library, settings or unrelated apps. Retain text evidence, disable retention of automatic screenshots/recordings.
- If the UI runner starts successfully and both quick tests pass, run the existing two-minute UI test with four30-second windows and a five-second stopped-result observation. This closes the current-source UI subgate only; native teardown/Leaks and EXIF remain separate. Do not retry an initialization failure without new evidence.
- Export authoritative xcresult summaries and sustained text attachments, verify source/framework/protected hashes and absence of retained image/video attachments, then restore normal no-argument Demo launch without uninstalling or clearing its container. No product code change or manufactured RED is needed for this validation-only slice.

## Task 12: Calibrate allocation thread labels with own-task thread snapshots

- Existing allocation labels do not match kernel thread IDs, and the LLDB attach lane did not expose images. Do not repeat those attach variants. Use public own-process task_threads/THREAD_IDENTIFIER_INFO/pthread_from_mach_thread_np to record numeric IDs/handles only; release every returned send right and VM array, never dereference another thread's pthread storage or read stacks.
- First validate100macOS snapshots against the observer's real pthread/kernel ID, JSON serialization and unchanged send-right reference count. Cross-compile for the iOS15 target. These are observer checks, not device leak proof.
- In the generated page-cycle host only, opt in through an explicit argument. Capture bounded one-second/stage snapshots without retaining page owners. After the trace trigger allocate one explicitly owned4097-byte marker on the main thread, record its address/TID/pthread, and keep it live until host exit so the actual device trace can calibrate its exported label. Do not assume low32 bits represent the thread label before this check.
- Repeat the existing three-cycle protocol and exact-PID Leaks recording only with this new instrumentation. Correlate marker, trace binary/PID/time, allocation addresses and successful snapshot intervals. Keep non-atomic snapshot races, truncated-label collisions and unobserved intervals explicit; no direct TLS/destructor claim follows. Restore ordinary Demo and preserve all failures/artifacts.

### Task 12 completeness guard

- A missing pthread mapping must classify the non-atomic snapshot as partial, even if THREAD_IDENTIFIER_INFO and all releases succeeded. Reproduce this with a lookup seam returning nil before fixing the status/count; require unchanged send-right references for both full and partial snapshots. Observer ID lookup must also succeed for complete status.
- Rebuild from a fresh generated manifest after this correction. Before device installation freeze the actual app/debug binary and the current ordinary restore package, verify signing and no sharing overrides, and use the explicit thread-inventory opt-in. Retain the earlier compilation-only checkpoint as historical evidence.
