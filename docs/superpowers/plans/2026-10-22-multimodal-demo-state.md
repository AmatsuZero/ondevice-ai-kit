# Single-image Demo State Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. No subagents. RED→GREEN, task-scoped author review, exact stage and local commit per Task.

**Goal:** Add a shared single-image generation state machine without UI or native host integration.

**Architecture:** UI-serialized MultimodalViewModel consumes the typed bridge; independent operation/query tickets filter stale callbacks. Native adapters continue to own inference and lease cleanup.

**Tech Stack:** Existing Kotlin Multiplatform/Compose runtime, Kotlin tests, offline cached Gradle.

**Spec:** `docs/superpowers/specs/2026-10-22-multimodal-demo-state-design.md`.

## Global Constraints

- Keep iOS15/macOS12/API26 and optional apple-mlx isolation.
- No new dependencies/models, UI/navigation/pickers/native host changes, device inference, media reads, signing changes or push.
- Preserve protected files and scratch/logs; inline author reviews only, independent review pending.
- State/admission/callbacks are UI-serialized; cancellation waits for bridge terminal before reentry.

## Review Focus

- A terminal can be synchronous before handle assignment: no stale cancellable handle or false busy.
- Budget text can be empty, non-integer, nonpositive or overflow: reject before bridge invocation.
- User edits input/selection/prompt while a cancelled native task drains: no reentry until terminal and no old text revival.
- Query/operation callbacks may repeat or arrive after background/close: no state revival or automatic restart.
- Close may fail or complete synchronously: one native close, admission remains sealed, private typed failure remains visible.

### Task 1: Shared state and generation lifecycle

**Files:** Create `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/MultimodalViewModel.kt`, `demo/shared/src/commonTest/kotlin/io/ondeviceai/demo/MultimodalViewModelTest.kt`.

**Interfaces:** Consumes existing MultimodalBridge DTOs/callbacks. Produces MultimodalPhase, MultimodalState and MultimodalViewModel(bridge = UnavailableMultimodalBridge()) with methods defined by the spec.

- [ ] Write tests: invalid request values call bridge zero times; raw prompt/ref/selection/budget unchanged; synchronous terminal permits second generation with no cancellation; cancellation remains CANCELLING and rejects new work until terminal, late output/error discarded; duplicate/regressive/stale updates ignored; each edit cancels and waits; query tickets/duplicate completion; background/no restart; synchronous query failure/default unavailable; close join/failure/idempotency and late events suppressed. Assert literal state/identity values, not fake internals alone.
- [ ] Run `rtk proxy python3 scripts/gradle-local.py :demo:shared:iosSimulatorArm64Test --offline --console=plain`; expect missing VM/state symbols RED.
- [ ] Implement VM with raw fields/defaults and UI-owned tickets/pending flags. Set state before bridge calls, attach task only if still pending, seal terminal before updating state, never issue synthetic generation. close has its own once/completion guard.
- [ ] Run full shared simulator tests plus binding tests, both framework links and shared Android compile offline. Expect all tests/link/compile success; inspect exported old entry points unchanged.
- [ ] Scope author review, exact stage and commit `feat: add single-image Demo state machine`.

### Task 2: Final validation and durable checkpoint

**Files:** Create `docs/validation/multimodal-demo-state.md`, update ROADMAP/HANDOFF; maintain `.superpowers/sdd/2026-10-22-multimodal-demo-state/{progress,final-review}.md`.

**Interfaces:** Consumes Task1 state API/evidence; produces durable handoff to a separate shared screen/UI package.

- [ ] Run full shared/binding tests and Android Demo APK/lint offline. Run Python tools suite for project/generator regressions with existing Ruby3.4 environment.
- [ ] Separate author final-review of full package against spec and all five Review Focus inputs. Fix blocking behavior only through a reproducing RED→GREEN test.
- [ ] Record exact counts/logs, distinguish cached build steps vs freshly executed tests, unchanged protected hashes, independent review pending and device/UI unverified. Update roadmap/handoff; choose separate single-image UI package, not simultaneous model/host/video integration.
- [ ] Verify diff/index/protected hashes; exact stage and commit `docs: checkpoint single-image Demo state`.
