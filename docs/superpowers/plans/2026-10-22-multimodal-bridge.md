# Single-image Multimodal Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline; no subagents. Execute task-by-task with RED→GREEN, scope review and local commit.

**Goal:** Bridge existing native single-image clients to KMP without integrating Demo/video/new models.

**Architecture:** Typed common callbacks and opaque input references; platform adapters own clients, input leases, worker admission and UI callback delivery. Native clients retain backend inference/selection/drain responsibility.

**Tech Stack:** Kotlin Multiplatform, existing coroutines1.10.2, Swift, XCTest, offline Gradle/Xcode and Ruby xcodeproj.

**Spec:** `docs/superpowers/specs/2026-10-22-multimodal-bridge-design.md`

## Global Constraints

- Keep base iOS15/macOS12/API26 and optional apple-mlx isolation; no new dependency/model downloads.
- No Demo screen, video, private media, recording, signing-asset changes, pushes or publication.
- Preserve two user-protected files; no modification or staging.
- Callbacks/admission/provider access on UI; cancel any-thread; terminal after drain/release exactly once.
- Capability-prefixed callback names avoid changing old exported selector labels.

### Task1: Common contract and unavailable implementation

**Files:** Create `kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/MultimodalBridge.kt` and `kmp-binding/src/commonTest/kotlin/io/ondeviceai/binding/MultimodalBridgeContractTest.kt`.

**Interfaces:** consumes BridgeTask/BridgeAvailability; produces DTOs and MultimodalBridge queryBackends/generate/close plus UnavailableMultimodalBridge for platform hosts.

- [ ] Write failing tests for blank reference/prompt/selection, nonpositive budget, invalid sequence/identity/output, backend identity mismatch and failure privacy. Example:
  ```kotlin
  assertFailsWith<IllegalArgumentException> { MultimodalBridgeRequest("input", " ", 32) }
  assertFailsWith<IllegalArgumentException> { MultimodalBridgeUpdate(-1, "") }
  ```
- [ ] Run `rtk proxy python3 scripts/gradle-local.py :kmp-binding:iosSimulatorArm64Test --offline --console=plain`; expect missing symbols RED.
- [ ] Implement DTO validation and synchronous unavailable/closed behavior:
  ```kotlin
  interface MultimodalBridge {
      fun queryBackends(callback: MultimodalBridgeBackendsCallback): BridgeTask
      fun generate(request: MultimodalBridgeRequest, selection: MultimodalBridgeSelection, callback: MultimodalBridgeGenerateCallback): BridgeTask
      fun close(callback: MultimodalBridgeCloseCallback)
  }
  ```
- [ ] Run common simulator tests and `:kmp-binding:linkDebugFrameworkIosSimulatorArm64 :demo:shared:linkDebugFrameworkIosSimulatorArm64 --offline`; inspect new selectors and confirm old Speech/Video labels unchanged.
- [ ] Scope review, precise stage and commit `feat: add single-image KMP bridge contract`.

### Task2: Android native-client adapter

**Files:** Create `kmp-binding/src/androidMain/kotlin/io/ondeviceai/binding/AndroidMultimodalBridge.kt`; modify `kmp-binding/build.gradle.kts`, `android/multimodal/build.gradle.kts`; create `android/multimodal/src/test/kotlin/io/ondeviceai/multimodal/AndroidMultimodalBridgeTest.kt`.

**Interfaces:** consumes Task1 and native MultimodalGenerationClient; produces `AndroidMultimodalBridge(client,inputs,scope)`, `AndroidMultimodalInputProvider.acquire(reference)` and lease file/release.

- [ ] Write injected-client tests for unavailable no acquire, exact prompt/budget/explicit backend/model output, cancelled/late update rejection, busy until drain/lease release, immediate cancel, repeated close and query join, private exception mapping. Test barrier:
  ```kotlin
  task.cancel()
  assertEquals(0, inputs.releases.get())
  finish.complete(Unit)
  assertEquals("cancelled", terminal.await())
  assertEquals(1, inputs.releases.get())
  ```
- [ ] Run `rtk proxy python3 scripts/gradle-local.py :android:multimodal:testDebugUnitTest --offline --console=plain`; expect missing adapter RED.
- [ ] Implement adapter with UI-owned worker registry, any-thread task cancel arbitration and cleanup outliving host Job:
  ```kotlin
  val outcome = try { Result.success(client.generate(nativeRequest, selected, update)) }
                catch (error: Exception) { Result.failure(error) }
  withContext(NonCancellable) { client.drain(); input.release() }
  ```
  Map only typed client errors; unknown failures use android.multimodal/code1, not exception text. Suppress updates after cancellation/terminal and non-increasing sequence; release slot before reentrant terminal callback.
- [ ] Run multimodal tests/lint, binding compile and existing Video/Speech adapter regression tests offline.
- [ ] Scope review and commit `feat: bridge Android multimodal clients`.

### Task3: Apple native adapter and final checkpoint

**Files:** Create `kmp-binding/apple/IOSMultimodalBridge.swift`, `kmp-binding/apple/IOSMultimodalInput.swift`, `probes/ios-multimodal-bridge/IOSMultimodalBridgeTests.swift`, `scripts/generate-multimodal-bridge-tests.rb`, `tools/tests/test_multimodal_bridge_project.py`; create `docs/validation/multimodal-bridge.md`; update ROADMAP/HANDOFF and this ledger.

**Interfaces:** consumes Task1 KMP framework and base Multimodal client; produces MainActor IOSMultimodalBridge(client:inputs:) and URL lease provider; generated Binding/Demo ABI native tests.

- [ ] Write tests exercising real native client with barrier backend for strict/default/unknown selection, prompt/update/output identity, any-thread/immediate cancel, lease after drain, close joining query and stable private errors. Example:
  ```swift
  task.cancel()
  XCTAssertEqual(inputs.releases, 0)
  await backend.drainGate.open()
  await fulfillment(of: [callback.done], timeout: 3)
  XCTAssertEqual(inputs.releases, 1)
  ```
- [ ] Generate standalone test project and run simulator test; expect missing adapter/provider RED. Generator tests must reject nonempty output and inspect actual linked products/framework, not grep source.
- [ ] Implement MainActor adapter mirroring Task2 lifecycle; use uncancelled drain before synchronous idempotent lease release, lock-backed any-thread handle, cancellation wins late error, close shares one worker. Do not import MLX or change protected default Demo project.
- [ ] Run generated Swift tests against both Binding/Demo simulator ABIs, common tests, targeted Android regressions, full Python tool suite; no new model/device gate required for injected adapter correctness. Record device integration/minimum-system/independent review as unverified/pending.
- [ ] Update validation/roadmap/handoff/ledger/final-review, verify protected hashes/staging, commit `feat: bridge Apple multimodal clients`.
