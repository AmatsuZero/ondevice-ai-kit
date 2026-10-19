# P10 local identity adapter

Date: 2026-10-19. Scope: Task 8 model-binding and resource-lifetime contracts.
Production identity and PAD status: **unverified / unavailable**.

## Adapter contract

Apple and Android expose `LocalIdentityBackend.open(modelStore, modelRef, runtime)`.
The host supplies the runtime; this adapter supplies no inference implementation,
model weights, default threshold, or automatic backend registration.

- Acquire a private, verified `ModelStore` lease. Missing or invalid assets fail
  with `backendUnavailable`.
- Pin the runtime's complete `EmbeddingSpaceId`, backend ID/version and model
  binding. Require model ID/revision, manifest backend, `face-identity` capability,
  supported backend version range and preprocess revision to match.
- Reject runtime binding changes and embeddings from a different space. A
  ModelStore configured for one runtime version does not authorize another
  runtime version outside the installed manifest's range.
- Forward cooperative stop signals to the runtime and check them independently
  before and after each call. Engines retain authorization, policy validation,
  revocation and final outcome publication.
- Successful open transfers runtime ownership to the adapter. A failed open
  releases any acquired lease and leaves runtime ownership with the caller.
- `close()` rejects new calls and requests runtime shutdown once. The private
  lease remains pinned until active synchronous calls and runtime close return.
  `closeAndWait()` joins the asynchronous lease release; call it before removing
  the model after shutting down engines.

`ModelLease.manifest` is a read-only verified manifest snapshot. ModelStore
continues to manage model assets only; identity templates and galleries remain
in their own encrypted stores. Identity now depends on the existing ModelStore
module for this lease API; no inference framework was added.

## Evidence and limits

Tests install a tiny synthetic text asset and supply a test-only runtime that
accepts internal vector fixtures. Synthetic encoded-image inputs are rejected.
These fixtures prove binding, lease retention, cancellation, drift rejection and
shutdown behavior. They are not biometric models or accuracy evidence.

Local regression gates passed: Apple full 366/366 (Identity308, Speech31,
ModelStore13, Face9, Core5); Android Identity306 + ModelStore10, zero
failures/errors/skips, both modules' `lintDebug` passed. New adapter tests are
8/8 per platform. Full logs: `/tmp/odai-local-backend-apple-full.log` and
`/tmp/odai-local-backend-android-all.log`. Runtime-version mismatch and ignored
stop-signal cases each failed behaviorally before the fixes on both platforms.

Unverified `ThresholdPolicy` values still return `thresholdUnverified` before
identity decisions. Model installation or a declared license string does not
approve model use, establish dataset authorization or calibrate a policy.

Required production gates remain:

1. Select and approve an exact commercially usable/redistributable model and
   runtime, with asset hashes and input/output/preprocess revisions. See
   [the model selection evidence](p10-identity-model-selection.md).
2. Approve authorized development and independent validation data.
3. Calibrate separate 1:1 / 1:N policies at specified operating points, enrollment
   conditions and gallery sizes; keep PAD model/challenge/attack calibration
   separate from identity thresholds.
4. Validate correctness, resources, cancellation and grouped accuracy on the
   supported device matrix, including the iOS 15 / Android API 26 minimums.

No weights were downloaded or added by Task 8. No camera, real biometric input,
cloud upload, distribution publication or production accuracy acceptance is
claimed here.
