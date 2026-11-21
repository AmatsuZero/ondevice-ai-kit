# Apple exact cosine Identity index

2026-11-04 functional continuation after [Android exact index](android-exact-identity-index.md)
and [candidate/gallery Demo](identity-gallery-demo.md).

Apple now supports an exact small-gallery 1:N index/query path for public
encoded-image probes. `ExactCosineIdentityIndex.build(_:stopSignal:)` implements
the existing `GalleryIndexBuilder` seam. Configure `IndexStore` with:

```swift
buildIndex: { try ExactCosineIdentityIndex.build($0, stopSignal: $1) }
```

The versioned in-memory payload uses `ODAI-COSINE-IDX-1`, full canonical
embedding-space identity, count/dimension and ordered little-endian Float32
vectors. Bounds are 4096 templates, 4096 dimensions and 65536 identity bytes;
float32/little/cosine/higher-is-better only. Empty, zero-norm, mixed-space,
unsupported-format and oversized matrices are rejected. Internal search checks
exact header/length and every stored vector bit against currently authorized
template leases, then computes Double cosine and reuses subject-max ranking,
threshold filtering and topK. Build/search check cooperative cancellation.

Legacy fixture indices remain accepted only with internal fixture probes.
Authorization, quality, verified-policy, revocation and final read-lease
consumption gates are unchanged. There is no public raw-score API or new
plaintext file. The source is registered in both SwiftPM and the explicit
Apple distribution project; no dependency was introduced.

## Verification

Ignored evidence: `artifacts/verification/apple-exact-identity-index-20261104/`.
`codec-red.log` records the absent builder symbol. After the codec existed,
`codec-green-engine-red.log` records seven codec tests passing and the public
encoded query failing against the old fixture-only engine. After integration:

- Full `swift test --package-path apple`: **481/481**, zero failures, including
  Identity **322/322**, Speech **76/76** and Face **9/9**. `tests.json` records
  all eight suite counts. Synthetic vectors/encoded bytes test ordered cosine,
  multiple templates/subjects and topK, corrupt/truncated/reordered/foreign
  payload rejection, zero norms, format/bounds, finite Float extremes and stop.
  Existing engine cancel/origin-revoke coverage now also uses the exact index
  with an encoded-image probe. No model embedding behavior is claimed by the
  synthetic test backend.
- `swift build --package-path apple -c release --target ODAIKitIdentity`: passed.
- Unsigned `xcodebuild` Release `ODAIKitIdentity` framework for `iphoneos`:
  **BUILD SUCCEEDED**, including public Swift interface verification. This is
  a local build, not XCFramework packaging, publication or device inference.
- `git diff --check` and nine protected-file hashes: passed/unchanged.

Bounded independent review was requested, but its service returned **429 Too
Many Requests** after retry limit; no independent verdict was obtained. Local
author review checked all byte offsets against the validated bounded size,
Data buffer-relative access, full space/vector binding, Double numeric range,
fixture provenance and unchanged final gate. This does not replace independent
review or approve the foundation branch. Existing SDK concurrency/deprecation
warnings and Ruby environment extension warnings remain in the original logs.

Apple still has no real identity embedding adapter. `LocalIdentityBackend` is
a runtime/ModelStore lease boundary; Vision supplies detection/landmarks, not
identity embeddings. No weights, runtime, biometric data, calibrated policy or
PAD was added. Real Apple recognition, real-model 1:1/1:N policy/calibration,
actual Demo UI, minimum-OS runtime and sustained-resource acceptance remain
open. No push, merge or release.
