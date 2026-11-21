# SDD ledger — plan: docs/superpowers/plans/2026-10-22-multimodal-bridge.md

Scope: common contract → Android adapter → Apple adapter; no Demo UI/video/model downloads/push. Solo execution; author reviews are not independent approval.

Pre-flight: Task1 typed contract is consumed by Tasks2/3; capability-prefixed selectors preserve old Speech/Video exports. Tasks2/3 use existing native clients with provider leases and callback cleanup contract. Platform implementations share no source files.

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

Implemented and verified; included in the Task3 checkpoint commit.

- RED: generator absent (`task-3-generator-red.log`); actual Xcode project missing adapter/provider/lease types (`task-3-apple-red.log`). An early implementation draft was discarded before the recorded RED; final implementation followed that RED. Later edge tests characterize existing GREEN behavior, not additional claimed RED cycles.
- Generator diagnostics: system Ruby2.6 mixed with Ruby3.4 json gems; use the existing Ruby3.4 binary, no install. Corrected actual framework inspection to allow Xcodeproj's default system Foundation link, then 3/3 GREEN (`task-3-generator-green-final.log`). Initial Swift test closure parameter omission caused compile failure; corrected test and reran.
- GREEN: Binding10/10 + Demo10/10 (`task-3-{binding,demo}-final.log`); common21/21/links + Android regressions successful (`task-3-regression-final.log`); tools101/101 (`task-3-tools-final.log`). XCTest17 and pre-existing Demo ICU18.5 version warnings retained; minimum-system execution unverified.
- Final static check initially reported three forEach style diagnostics; changed to equivalent for-in, strict Swift format lint exit0; post-format Binding10/10+Demo10/10 (`task-3-{binding,demo}-checkpoint.log`). Forced uncached common rerun21/21 (`task-3-common-rerun.log`), 10 tasks executed, success.
- Apple tests use real client and barrier backend: strict/default/unknown selection, selected identity, cumulative updates, reentrant generation, immediate/any-thread cancel, cancellation wins late error, lease until drain, close/query/generation join, closed admission, unavailable no acquire, invalid native URL release, stable private errors.
- Scope author review: adapter uses only base Multimodal, no MLX; detached drain is uncancelled, input release is once on every acquired path, registry/slot clear precedes callback, lock-backed cancel arbitrates terminal. Generator refuses any existing output/symlink, selects real Binding/Demo link, keeps iOS15 target. No native SDK source change or new device inference claim.
- User overrides: no subagents/independent verdict, no cleanup, keep local branch/worktree. These override skill reviewer dispatch/workspace deletion/integration menu; independent review pending, no APPROVE. Logs/artifacts preserved.

Task 1: complete (88c2fa9..2e96a26; common4 new/21 total, frameworks and legacy Video6/6).
Task 2: complete (2e96a26..b1e6873; bridge10/native12, lint/compile and Video17/Speech25).
Task 3: complete (base b1e6873, this checkpoint; Binding10/Demo10/common21/tools101; see docs/validation/multimodal-bridge.md).
