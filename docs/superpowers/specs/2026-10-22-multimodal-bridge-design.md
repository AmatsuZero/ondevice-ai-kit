# Single-image Multimodal Bridge Design

## Goal and boundary

Expose the existing single-image native MultimodalGenerationClient to KMP hosts through typed callbacks, without adding a Demo screen, video, downloads, a model family, or a real Android inference backend. The base iOS15/macOS12/API26 floors and optional apple-mlx isolation stay unchanged. Hosts explicitly supply clients and input providers; this package does not create/install/load a model.

## Approaches

1. **Chosen: callback bridge with native adapters**, following Speech/Video. Shared DTOs contain opaque input references, a prompt/budget, strict selection, cumulative updates and immutable result identity. Native adapters own supplied clients and file leases. This fits existing lifecycle/UI dispatch contracts and leaves MLX outside KMP.
2. A common coroutine inference coordinator with expect/actual engines would duplicate the native clients' admission/drain state and expose native-resource details to common code. Not chosen.
3. Linking MLX into the default KMP/Demo package would break optional runtime/deployment isolation. Not chosen.

## Public contract

`MultimodalBridgeSelection.Default/Explicit`, `MultimodalBridgeRequest(inputReference,prompt,maxOutputTokens)`, `MultimodalBridgeBackend`, `MultimodalBridgeUpdate(sequence,text)`, `MultimodalBridgeIdentity`, `MultimodalBridgeOutput(backendId,text,model)` are plain values. References are host-owned opaque tokens, not paths/URLs. Prompt bytes are preserved, not trimmed; blank prompts and nonpositive budgets are invalid. Backend-specific bounds remain native backend admission rules. No video or multi-image fields are reserved.

`MultimodalBridge.queryMultimodalBackends`, `generateMultimodal`, `closeMultimodal` use typed callbacks. Entry points and callback method names are uniquely prefixed for this capability (`onMultimodalBackends/Update/Result/Failure/Cancelled/Closed`) to avoid changing existing exported Objective-C/Swift selector collision numbering. Actual header inspection found that prefixing callbacks alone still renamed old Video close/query parameter labels; prefixing entry points is required too. BridgeTask.cancel is safe from any thread; all other admission/provider/callback/registry work is on the platform UI dispatcher. A callback can synchronously reenter. Unknown Swift selection conformers are invalid, never defaulted.

Failure codes: CLOSED, BUSY, INVALID_INPUT, BACKEND_NOT_REGISTERED, BACKEND_UNAVAILABLE, PLATFORM. Only platform failures carry a stable adapter domain/code; raw exceptions, media paths and prompts do not enter errors. UnavailableBridge returns backendNotLinked without acquiring input or synthetic generation and closes idempotently.

## Native ownership and concurrency

Each adapter takes exclusive ownership of an optional native client and an input provider. Without a client it reports unavailable; no hidden factory/download/fallback. With a client it lists real descriptors, preserves explicit selection and actual result backend/model identity. One generate occupies the adapter slot through native drain and input lease release. Queries are tracked too, so close joins in-flight queries as well as generation.

Acquire input only after closed/busy/client/selection admission, construct native request, then start a tracked worker. Every path after acquisition releases once, including immediate cancellation before worker dispatch. Native generate is followed by uncancelled drain before lease release. Cancellation wins a late ordinary error; terminal callback occurs once, after ownership/slot cleanup. Close marks closed immediately, cancels/joins all tracked tasks, closes the native client once and shares completion across repeated/concurrent callers. Immutable text output outlives close; no output file lease is needed.

Updates preserve native cumulative text and sequence, suppress non-increasing/stale/after-cancel/after-terminal delivery, and execute on UI. Final output is authoritative; queued UI updates need not all be delivered if terminal completion overtakes them.

Android uses the existing native multimodal module with injected fixture backends for host tests; this is not an Android production backend. Detached cleanup scope survives cancellation of the host Job. Apple adapter imports only base Multimodal/Core plus the chosen KMP ABI, not apple-mlx. Input provider is a small MainActor protocol with URL and idempotent release.

## Validation and delivery

Task1 common tests prove validation/unavailable/close privacy contracts and export both Binding/Demo framework ABIs without selector regressions. Task2 Android injected-client tests prove strict selection, updates/result identity, cancellation/drain/input retention, immediate cancellation, query/close join, stable errors and no-client behavior. Task3 generated native Swift tests against real Multimodal client and KMP headers prove the same lifecycle plus unknown Swift conformers; test project generation is nonoverwriting and uses only cached dependencies. Every task uses RED→GREEN, scope author review and local commit. No subagents; independent review remains pending, not APPROVE.

Modern-device MLX execution remains the prior backend/benchmark evidence, not new KMP end-to-end evidence. No Demo UI, real Android inference, minimum-system device execution, distribution repackage, or independent review acceptance is claimed by this package.
