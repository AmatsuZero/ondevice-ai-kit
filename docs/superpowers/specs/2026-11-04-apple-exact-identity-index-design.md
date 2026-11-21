# Apple exact cosine Identity index

The functional-priority continuation replaces Apple's fixture-only 1:N query limitation with an exact small-gallery index callable through the existing public IndexStore builder seam. User has opted for autonomous continuation and deferring corner/resource work; no new model, data or policy authorization is inferred.

Use a public `ExactCosineIdentityIndex.build(_:stopSignal:) -> Data`, mirroring Android's versioned `ODAI-COSINE-IDX-1` layout: full canonical embedding space, UInt32 little-endian identity length/count/dimension and ordered Float32 vectors. Bound to 4096 templates/dimensions and 65536 identity bytes, float32/little/cosine/higher-is-better only. Zero norms are invalid. Query validates every byte against the current leased template snapshot before exact Double cosine scoring; cancellation is checked through build/search. Raw scores remain internal to the authorized engine.

Approaches: choose the existing builder extension with a bounded exact codec (agent decision: no dependency, matches Android and small-gallery profile). A third-party approximate engine adds unnecessary dependency/quality decisions; retaining fixture-only lookup leaves the functional gap. This does not promise byte interchange across stores or plaintext persistence.

Keep synthetic fixture payloads accepted only for internal fixture probes. Preserve quality, verified-policy, subject-max ranking/topK, authorization, revocation and final lease-consumption gates. No real Apple embedding adapter exists; Vision detection is not an identity embedding. New tests use synthetic vectors and encoded bytes, without biometric data, runtime or weights. Actual Apple recognition/calibration/PAD remains open.

Preserve all nine existing protected paths and unrelated work; no push/merge/release. Success: encoded-image probe reaches public engine scoring with an injected test backend, bad/stale/foreign payloads fail closed, cancellation and numeric extremes pass, existing Apple suite passes or gaps are explicit, bounded review and local commit collected.
