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

Pending.

## Task 3 — Apple adapter / final checkpoint

Pending.
