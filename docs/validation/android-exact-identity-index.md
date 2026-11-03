# Android exact cosine Identity index

2026-11-03 functional continuation after the [OpenCV prototype](android-opencv-identity-demo.md).
Android now has a real encoded-image 1:N index/query mechanism. Recognition
calibration and real-model identification acceptance remain open.

`ExactCosineIdentityIndex.build` implements the existing `GalleryIndexBuilder`
contract. Its versioned payload binds the full canonical embedding space,
template count, dimension and ordered little-endian Float32 vectors. The engine
parses that payload, validates it against every current authorized template
lease, calculates exact cosine scores, then reuses existing subject-max
aggregation, threshold filtering, deterministic ordering and topK. Bounds are
4096 templates and 4096 dimensions; empty/zero-norm vectors and unsupported
formats are rejected. Cancellation checks run during construction and search.
Legacy fixture payloads remain accepted only for internal fixture probes.

Use `buildIndex = ExactCosineIdentityIndex::build` with `IndexStore.open`.
The OpenCV Demo session now uses this builder. Index vectors stay in the derived
in-memory cache; this change adds no plaintext index file, public score-query
escape hatch or dependency. Durable gallery metadata and encrypted templates
retain their existing ownership, deletion, revocation and rebuild gates.

Neither an exact index nor a successful native embedding creates a verified
operating policy. The OpenCV prototype keeps both policies `UNVERIFIED`;
gallery creation/publication and verification return `THRESHOLD_UNVERIFIED`.
No guessed tutorial threshold or fabricated calibrated dataset was introduced.
The current Demo still exposes local enrollment/deletion only; usable multi-user
gallery controls and real-model recognition outcomes require the separate
calibration/authorized-data work.

## Verification

Ignored evidence: `artifacts/verification/android-exact-identity-index-20261103/`.
The initial RED log records the missing exact-index symbols. The first test
run then found a test-backend delegation error: Kotlin delegated the signal
overloads to the fixture backend. Explicit test overrides corrected that
harness; no production fallback was added.

- Android Identity **327/327**, zero failures, including encoded-image probe
  query with multiple subjects/templates, exact score order, stale/corrupt/foreign
  payload rejection, zero-norm rejection, cancellation and unverified-policy
  rejection. Existing authorization/revocation/deletion coverage also passed.
- OpenCV backend **6/6**, three-option Demo **53/53**; target/test APK, Demo lint
  and Identity lint passed. This test backend is synthetic and does not prove
  SFace recognition accuracy.
- A review hypothesis about norm overflow was checked against Double arithmetic:
  values convert before multiplication, and the bounded maximum squared norm is
  below `4.75e80`, well within Double range. A dedicated passing test covers both
  `Float.MAX_VALUE` and `Float.MIN_VALUE`; finite extreme vectors retain valid
  cosine scores.

Default Demo **48/48**, APK/lint and Identity release AAR/POM passed. The final
bounded independent review withdrew the arithmetic hypothesis after checking
the numeric bound and test; it reported zero findings and **APPROVE** for this
slice, not the entire foundation branch.

Final-source TECNO KM9/API35 run `329669f2-2921-4bd6-abdc-2f0cf130bd40` passed
the prior real detection/alignment/embedding/bridge-enrollment/deletion flow,
plus explicit `THRESHOLD_UNVERIFIED` rejection of gallery creation. It used the
same separately approved hash-bound NASA fixture and no camera/Activity/unlock.
It does not test real-model 1:N decisions because no verified policy exists.

- Installed target APK SHA-256:
  `04df86c11822eb0b38adf7247090fed0b5b610e42fc89337b5b9bf2ee08ad828`.
- Installed test APK SHA-256:
  `d5262ee53335dec3b65277931e1828f64cd9cb827ec091d91dff1ee219fb11c8`.
- `report-final.json` SHA-256:
  `90fcd08e9038ff46b13e906e1c61736b77eaab91e283026597f98b0f97a78ba5`.

Both installed APK hashes matched immutable snapshots before the run. An initial
streamed install stalled with an unresponsive USB shell; only this validation's
client was terminated, and a serial-scoped reconnect followed by sequential
non-streamed target/test installation succeeded. No global adb restart or device
reboot was used. `tests.json`, XML snapshots, installed identity and original
logs remain in the ignored evidence directory.

Apple real Identity, PAD, calibrated quality,
actual picker/consent/gallery UI, minimum-OS and sustained-resource acceptance
remain open. No push, merge or release is implied.
