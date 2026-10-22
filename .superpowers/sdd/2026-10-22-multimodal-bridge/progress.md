# Single-image Multimodal Bridge progress

Scope: common contract → Android adapter → Apple adapter; no Demo UI/video/model downloads/push. Solo execution; author reviews are not independent approval.

## Task 1 — common contract

- RED: `task-1-red.log`, missing contract symbols before implementation.
- GREEN: `task-1-green.log`; final offline common simulator tests + Binding/Demo framework links: `task-1-abi-final.log` (BUILD SUCCESSFUL).
- Export regression discovered by actual legacy Video tests (`task-1-old-video-abi.log`): generic entry points shifted Kotlin/Native collision suffixes. Capability-prefixing callbacks **and entry points** restores the old selectors. Header verified: Video `close(callback_:)` and `queryBackends(callback:)`; Speech `close(callback:)` and `queryBackends(capability:callback:)`.
- Fresh legacy Video simulator suite: 6/6, `task-1-old-video-abi-fixed.log` and retained xcresult under `artifacts/verification/video-bridge-tests-20261021/build-new/`.
- Scope self-review: validates blank prompt/reference/selection, budget, identity/output, update ordinal, backend identity and private platform failure; unavailable never acquires/generates, close is idempotent. No platform adapter or old contract changed.
- Protected files SHA256 rechecked unchanged (`b35bd984…8f5`, `6f130bd9…822`); not staged. Independent review pending.

## Task 2 — Android adapter

Completed implementation and scope author review.

- RED: `task-2-red.log`, missing Android adapter/input provider/lease symbols.
- GREEN: `task-2-green-final.log`, offline Multimodal native + bridge tests, lintDebug, binding compile and existing Video/Speech tests successful. Bridge 10/10 + native 12/12. Lint retains only existing pinned coroutine-version advisory (no dependency download/upgrade).
- Real native client with injected barrier backends verifies strict explicit/default selection, metadata/identity/prompt/budget, UI updates/callbacks, duplicate/stale/late suppression, any-thread and immediate cancellation, cancellation winning late ordinary errors (including lease error), slot/lease through drain and release, reentrant result, repeated close joining queries, detached cleanup after host cancellation and private stable failures.
- Self-review: no copied Video output lease/token machinery; immutable text only. `ATOMIC` starts cleanup on immediate cancellation; close seals admission synchronously, joins tracked workers, closes native once; registry/slot cleanup precedes terminal callback. No MLX/Demo/video changes or real Android inference claim. Protected files not staged. Independent review pending.

## Task 3 — Apple adapter / final checkpoint

Pending.
