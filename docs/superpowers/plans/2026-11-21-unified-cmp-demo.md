# Unified CMP Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the split Demo entry points with one shared CMP experience where users can explore all nine capability states, open a uniform detail flow, inspect model readiness, and review process-local records on Android and iOS.

**Architecture:** Add a shared application shell above the existing capability ViewModels. The shell owns navigation, catalog metadata, records, shared theme, and lifecycle routing; existing ViewModels and platform Hosts continue to own media, permissions, model preparation, playback, and native resources. Android and iOS each provide a thin Host binding for the shell and keep legacy exported factories as compatibility wrappers.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Compose Material3, existing KMP binding, existing Android/iOS Host implementations, Kotlin common tests.

**Spec:** `docs/superpowers/specs/2026-11-21-unified-cmp-demo-design.md`

## Global Constraints

- Use the existing CMP, Material3, KMP binding, and Host dependencies; do not add a navigation, player, model-download, or toolchain dependency.
- Keep Android and iOS on the same shared visual hierarchy; platform-specific system functions remain in Hosts.
- Preserve existing public Kotlin/Swift factories and dedicated automation entry points with compatibility wrappers.
- Records are process-local metadata/summary only, capped at 30 FIFO entries, with no media/template/output lease and no persistence.
- Frozen video understanding and identity/liveness are preview-only; do not add enrollment, calibration, training, or production claims.
- Real-time enhancement must use existing `RealtimeVideoSR` / `SelectableRealtimeSRHost` state and must distinguish supported, selected, preferred, and presented backends.
- Preserve current lifecycle cancellation, generation/session tokens, and late-callback rejection.
- Do not modify the separate UHPlayerSDK repository, publish artifacts, merge branches, or touch unrelated dirty files in this plan.

## Review Focus

- Leaving a capability while work is running must cancel/clean before the next page can run; test the route transition and stale callback boundary.
- Editing text, prompt, backend, or input must invalidate the prior result; test a completed result followed by an input mutation.
- Records must not retain media or identity leases and must evict oldest entries at 31 items; test both conditions.
- Missing Host, backend, permission, and model must render distinct availability states; test the mapping without using platform-name heuristics.
- Frozen identity/video entries must stay visibly preview-only and must not expose a success/verification claim; test the frozen state metadata.

### Task 1: Shared catalog, routing, records, and lifecycle contract

**Files:**
- Create: `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/UnifiedDemoModel.kt`
- Create: `demo/shared/src/commonTest/kotlin/io/ondeviceai/demo/UnifiedDemoModelTest.kt`
- Modify: `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/FaceViewModel.kt` only if the shared route needs an existing visibility hook

**Interfaces:**
- Produces `DemoTab`, `DemoCapabilityId`, `DemoCapabilityStatus`, `DemoCapabilityDescriptor`, `DemoRecord`, `DemoRecordsStore`, and `UnifiedDemoRoute`.
- `DemoRecordsStore.append(record: DemoRecord)`, `clear()`, `snapshot(): List<DemoRecord>`; `append` enforces 30-entry FIFO and stores only metadata/summary strings.
- `DemoCapabilityDescriptor` carries id, title, category, frozen flag, preview copy, and current `DemoCapabilityStatus`; no platform string determines availability.

- [ ] **Step 1: Write failing common tests** for the catalog’s nine IDs, record FIFO cap, clear, frozen preview copy, and route transitions.
- [ ] **Step 2: Run `./gradlew :demo:shared:allTests` and observe the new tests fail because the model types do not exist.**
- [ ] **Step 3: Implement the immutable catalog, route state, and bounded records store** in `UnifiedDemoModel.kt`; keep record fields limited to capability, platform label supplied by Host, timestamp, outcome, and summary.
- [ ] **Step 4: Run the focused common tests and verify they pass.**
- [ ] **Step 5: Commit only the model and tests** with `feat(demo): add unified capability model and records contract`.

### Task 2: Shared CMP shell and uniform detail scaffold

**Files:**
- Create: `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/UnifiedDemoScreen.kt`
- Create: `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/UnifiedDemoTheme.kt`
- Create: `demo/shared/src/commonTest/kotlin/io/ondeviceai/demo/UnifiedDemoScreenContractTest.kt`
- Modify: `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/FaceScreen.kt` to extract/reuse the face detail content without changing its public signature

**Interfaces:**
- Produces `@Composable fun UnifiedDemoScreen(model: UnifiedDemoModel, bindings: UnifiedDemoBindings, modifier: Modifier = Modifier)`.
- `UnifiedDemoBindings` supplies existing capability ViewModels/actions and Host availability callbacks; it does not own native media objects.
- Produces shared `Explore`, `Models`, `Records`, and `CapabilityDetail` routes with one theme, one bottom navigation, one status/availability renderer, and explicit back/leave callbacks.

- [ ] **Step 1: Write failing contract tests** for default Explore route, nine capability cards, bottom navigation, frozen grouping, and uniform detail state labels.
- [ ] **Step 2: Run focused tests and observe failure.**
- [ ] **Step 3: Implement the shared shell** using existing Material3 primitives and existing capability screens inside a single scroll/layout boundary; keep the prototype’s layout switch and platform/error review toggles out of production UI.
- [ ] **Step 4: Add lifecycle effects** so route changes call the existing `leavePage`/`background` actions and stale work cannot repopulate the detail result.
- [ ] **Step 5: Run common tests plus `:demo:shared:compileKotlinMetadata`.**
- [ ] **Step 6: Commit the shared shell and theme** with `feat(demo): add unified cmp application shell`.

### Task 3: Android Host wiring

**Files:**
- Modify: `demo/androidApp/src/main/kotlin/io/ondeviceai/demo/MainActivity.kt`
- Create: `demo/androidApp/src/main/kotlin/io/ondeviceai/demo/UnifiedDemoAndroidBindings.kt`
- Modify: `demo/androidApp/src/test` or existing Android test location only if a Host contract test location already exists

**Interfaces:**
- Produces an Android binding that maps current `FaceViewModel`, `SpeechHost`, `VideoHost`, `MultimodalHost`, identity runtime, and voice-effects Activity entry into `UnifiedDemoBindings`.
- Keeps current Activity Result contracts, permission launchers, and camera/player ownership in Android Host code.

- [ ] **Step 1: Add a failing binding test or deterministic mapper test** for missing Host/backend/model/permission status distinctions.
- [ ] **Step 2: Run the focused Android test and observe failure.**
- [ ] **Step 3: Wire `setContent` to the unified shell** and retain the existing preview renderer as a Host-owned media slot; remove only the duplicate top-level navigation/voice-effects button after the shell can open that capability.
- [ ] **Step 4: Verify Android compilation and focused tests.**
- [ ] **Step 5: Commit Android wiring** with `feat(demo): use unified cmp shell on android`.

### Task 4: iOS Host wiring and legacy factory compatibility

**Files:**
- Modify: `demo/shared/src/iosMain/kotlin/io/ondeviceai/demo/MainViewController.kt`
- Modify: `demo/iosApp/OnDeviceAIDemoApp.swift`
- Create: `demo/iosApp/UnifiedDemoBindings.swift`
- Modify: `demo/iosApp/UITests` only for stable accessibility identifiers required by the shared shell

**Interfaces:**
- Produces one `unifiedDemoViewController(...)` entry for the default app root.
- Existing `mainViewController`, `speechMainViewController`, `videoMainViewController`, and multimodal factories remain exported and delegate to the new shell or their legacy compatibility path.

- [ ] **Step 1: Add a failing Swift/Kotlin integration assertion** that the default app root returns the unified controller while legacy factory symbols remain available.
- [ ] **Step 2: Implement the iOS binding and root replacement**; keep camera, picker, AVFoundation, model setup, and identity cleanup in `DemoController`/Hosts.
- [ ] **Step 3: Add stable accessibility identifiers for Explore, Models, Records, capability cards, Run, Cancel, and Back.**
- [ ] **Step 4: Run the iOS shared compile and available UI/unit checks.**
- [ ] **Step 5: Commit iOS wiring** with `feat(demo): use unified cmp shell on ios`.

### Task 5: Cross-platform verification and merge candidate report

**Files:**
- Create: `docs/validation/unified-demo-implementation-20261121.md`
- Modify: none outside this plan’s owned files

- [ ] **Step 1: Run focused common tests, Android compile/lint applicable to changed modules, and iOS shared/framework checks.**
- [ ] **Step 2: Run the existing Demo UI smoke tests where available and record platform-specific Host/model gaps separately.**
- [ ] **Step 3: Verify protected dirty/untracked files remain byte-identical and the plan’s commits contain only owned paths.**
- [ ] **Step 4: Record real success, unavailable, and frozen-preview evidence for all nine capabilities; do not claim platform parity where a Host or model is absent.**
- [ ] **Step 5: Produce a reviewable merge-candidate report.** Main integration, push, signing, and distribution remain separate follow-up actions.
