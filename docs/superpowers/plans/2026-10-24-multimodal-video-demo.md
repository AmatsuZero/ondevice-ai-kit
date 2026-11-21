# Typed video Demo implementation plan

Approved design: ../specs/2026-10-24-multimodal-video-demo-design.md. Existing isolated foundation worktree; preserve two protected files. Solo implementation; no push.

1. Shared RED: extend demo/shared/src/commonTest/kotlin/io/ondeviceai/demo/MultimodalViewModelTest.kt with video raw request dispatch, same-token media switch cancellation, legacy rejection, synchronous video terminal and clear/closed tests. Run scripts/gradle-local.py --offline :demo:shared:iosSimulatorArm64Test. Preserve real failing output.
2. Shared GREEN: add media kind/selectVideo in MultimodalViewModel.kt; extract callback then dispatch through optional video bridge. Add optional actions in MultimodalDemoActions.kt; expose image/video selection and kind in MultimodalScreen.kt. Rerun common tests and framework link.
3. Android RED/GREEN: expand MultimodalHostTest.kt and store/copy tests for independent video store, wrong-provider rejection, cross-kind picker ticket and retire/lease semantics. Implement optional video store/picker in MultimodalHost.kt; wire MainActivity MIME and independent stores while preserving old calls. Run :demo:androidApp:testDebugUnitTest and lint/assemble offline.
4. Apple RED/GREEN: extend probes/ios-multimodal-bridge host/store tests for equivalent isolation, lifecycle and input limits. Implement optional host video inputs, bounded store staging, movie picker and MultimodalModelHostFactory provider wiring. Generate fresh test projects, never overwrite protected project. Run both Binding and Demo ABI XCTest as applicable.
5. Regression: tools tests with explicit Ruby3.4.11, common/frameworks/Android offline and fresh generated Swift project tests; changed-file format/diff checks. Record actual counts and failures, not inferred success. Check protected SHA256/status/index.
6. Record validation and handoff with unrun gates explicitly pending. Local commit only if verified and allowed by environment; never push. Keep evidence and builds. Present a concise completion report.

Safety invariant: native drain -> operation lease release -> terminal; media switch does not release operation input early. Unavailable optional configuration rejects rather than parsing through another provider.

## Follow-up: cancellable asynchronous staging prerequisites

Keep the approved independent registries and shared state machine. Do not move a UI-affine registry wholesale onto a worker. Deliver independently verified increments:

1. Android store RED: add deterministic checkpoint/latch tests to MultimodalInputStoreTest.kt. A blocked copy must not hold the registry lock; close returns before worker join, rejects eventual publication, and keeps its directory alive until in-flight copy cleanup. Checkpoint cancellation leaves no partial file. Existing operation leases remain valid while another copy is blocked. Run offline :demo:androidApp:testDebugUnitTest --tests io.ondeviceai.demo.MultimodalInputStoreTest.
2. Android store GREEN: reserve an in-flight import under lock; copy outside the lock with per-chunk cancellation/closed checkpoints; publish under lock only while open; on all exits decrement in-flight ownership and retry owned-file/directory cleanup. Never recursively delete. The synchronous API remains source-compatible and is a prerequisite, not proof of asynchronous host wiring.
3. Android host follow-up: inject a staging task with UI completion, cancel on latest selection/clear/leave/background/close, retire stale results, and keep the picker temporary source alive until worker completion. Cover cancellation/join and source lifetime before modifying MainActivity; then run all Demo unit tests and instrumentation.
4. Apple follow-up: separate Sendable worker copy from MainActor registry reservation/publication; retain security-scoped access and in-flight directory ownership through worker completion. Add deterministic cancellation/close/stale-result tests before host Task wiring; fresh generated XCTest project only.
5. Update validation with exact results and remaining gaps at each checkpoint. Full asynchronous dual-host completion requires steps 3 and 4; do not claim it from step 2 alone.
