# Android exact Identity index

Continue the authorized functionality-first roadmap in the existing worktree.
The Android Identity engine already handles immutable gallery metadata, template
leases, authorization/revocation, subject-max ranking and verified policies.
Its only query payload is currently a synthetic fixture format; encoded-image
probes are rejected even when a real backend is registered.

Use a bounded exact cosine matrix with a versioned header, full canonical
embedding-space identity, count/dimension and little-endian Float32 values.
Expose `ExactCosineIdentityIndex.build(embeddings, stopSignal)` as the existing
GalleryIndexBuilder implementation. Keep query parsing internal to the engine,
validate every stored vector against the current leased snapshot in order,
then compute cosine scores from the parsed values. Preserve synthetic fixture
compatibility only for internal fixture probes. Malformed or stale/foreign
index payloads fail closed. Check the operation signal during build/query.

This is my implementation choice: exact scan reuses the small-gallery contract
without dependencies or approximation. A new ANN/runtime adds configuration and
maintenance before the flow works; keeping fixture-only leaves a real feature
gap. Bounds remain 4096 templates and 4096 dimensions; no durable raw vector
file or public score-query escape hatch is introduced.

Use the real builder in the OpenCV session. Do not create a fake VERIFIED SFace
policy: actual gallery publication still returns THRESHOLD_UNVERIFIED until an
independently calibrated operating point exists. Synthetic unit policies test
the mechanism only. Apple/PAD, data calibration, ANN and resource optimization
are out of this slice. Verify build/query with non-fixture encoded-image probes,
multiple subjects/templates, immutable snapshot mismatch, cancellation and the
existing authorization/revocation suite.
