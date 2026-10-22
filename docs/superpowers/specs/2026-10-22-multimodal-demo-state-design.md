# Single-image Demo State Design

## Intent and scope

Advance P9 after the completed bridge package with an independently testable shared state machine. The eventual Demo needs one selected image, raw prompt, bounded generation, cancellation and transparent backend/model identity. This package delivers the state machine only. UI, navigation, native host/picker, real model wiring and devices follow in separate packages. No model/dependency downloads, media reads, signing changes or pushes. Keep iOS15/macOS12/API26 and optional apple-mlx isolation.

The user requests continuous inline design/plan/implementation without routine confirmation; retain that execution method and independent-review gap. Do not modify/stage protected files or clean scratch.

## Alternatives

1. **Chosen: a dedicated MultimodalViewModel using typed bridge**, following existing Video/Speech UI-serialized patterns. It can be tested without platform inference and holds no native leases. Cost: a later screen/host package must wire its lifecycle.
2. Add VM, screen, picker and MLX host together. Fewer handoffs but multiple independent interfaces and device gates; deferred.
3. Generalize all capability ViewModels into a coordinator. Unrelated refactor with divergent output/lease semantics; rejected.

## State and public interface

`MultimodalPhase`: IDLE/RUNNING/CANCELLING/SUCCEEDED/FAILED/CANCELLED. `MultimodalState` contains phase, backends/refreshing/queryFailure, strict selection, inputReference/displayName, raw prompt (default `Describe this image.`), tokenBudget text (default `32`), latest cumulative update, final output, operation failure, closed/closing/closeFailure.

`controlsEnabled` is false during generation/cancellation/closed; `canGenerate` also requires a nonblank reference/prompt and a positive parseable Kotlin Int budget. Keep tokenBudget as text so empty/malformed/overflow edits can be rejected without silently substituting a different number. Do not trim or otherwise rewrite prompt. Explicit backend bytes remain unchanged. Opaque references are never rendered as paths by this package.

`MultimodalViewModel(bridge: MultimodalBridge = UnavailableMultimodalBridge())` exposes `refreshBackends`, `selectBackend`, `selectImage(reference,displayName)`, `setPrompt`, `setTokenBudget`, `inputFailed`, `generate`, `cancel`, `leavePage`, `background`, `close`. All calls/callbacks are UI-serialized. The optional bridge defaults to unavailable, never a fabricated result/client factory.

## Concurrency and lifecycle

Generation admission validates input/prompt/budget before calling the bridge. One pending generation persists until one terminal; another generate cannot start while RUNNING/CANCELLING. Ticket plus per-operation pending/terminal checks handle callbacks delivered synchronously before the BridgeTask is returned and suppress stale/duplicate callbacks. Only increasing sequence updates while RUNNING affect state. A final output is authoritative and preserves backend/model identity.

cancel sets CANCELLING first and cancels the handle once; it does not clear the pending slot. A late success/failure after cancel is converted to CANCELLED without output/failure. Edits to input/prompt/budget/selection cancel an in-flight generation, clear its visible output/update/failure, retain the new editable values, and still wait for old terminal before allowing another generation. Repeating an identical edit is a no-op.

Queries have their own tickets and completion flags. Refresh cancels the previous query and ignores its late callbacks. Leave/background invalidate pending query delivery and cancel generation, but never start new work on return and do not own input release. No native lease exists in shared state.

close seals admission immediately, invalidates query callbacks, cancels generation and calls bridge.closeMultimodal once. It exposes closing until the close callback, records a private typed closeFailure on failure, ignores duplicate close callbacks and never reopens. Late generation callbacks after close completion cannot change state. Clear volatile update/output on close; result values previously copied by a caller remain valid.

## Validation and delivery

First write shared tests and observe missing types RED, then implement GREEN. Tests exercise real VM state transitions with controlled bridge callbacks, plus actual UnavailableMultimodalBridge. Cover raw request identity, malformed/overflow budget, synchronous callbacks, pending cancellation, strict selection, stale/regressive updates, stale/duplicate queries, edits, background/no restart and close join/failure/idempotency.

Run the full existing shared common simulator suite, common binding suite and both KMP framework links offline; compile Android shared and Demo APK/lint with cached dependencies. No UI/device success claim from compilation or unit tests. Each Task gets author scope review and exact staging/local commit. Independent review remains pending, not APPROVE; earlier P6/Video/Android ASR/minimum-system/natural-quality/Metal gaps stay open.
