# Android OpenCV Identity prototype

Continue the approved P10 architecture with functional inference first. The user
approved optional OpenCV 4.12.0 and official YuNet/SFace weights for local prototype
validation on 2026-11-03. This does not approve production deployment, calibration
data, PAD models, or redistribution of biometric samples.

## Design and decisions

Use the official `org.opencv:opencv:4.12.0` Maven AAR, CPU YuNet detection and its
five landmarks, then upstream `FaceRecognizerSF.alignCrop/feature`. Reusing these
operations avoids introducing a second implementation of YuNet decoding and
SFace alignment. ONNX Runtime was considered but would require implementing those
operations separately. Default Demo remains free of the optional runtime/weights.

Pin the two model hashes in one ModelStore manifest and immutable embedding-space
binding. Decode task-scoped upright encoded images with bounded dimensions,
require exactly one detected face for embedding, normalize the 128 floats to L2,
and serialize access to native networks. Check cancellation before and after
blocking native inference; close must wait for admitted calls before releasing
the model lease. OpenCV Java network finalization is a separate resource limit,
not a claim of immediate native deallocation.

Expose model ZIP import and local enrollment in the existing Identity page.
The host grants enrollment only after an explicit local prototype action; use
encrypted IdentityStore with the platform key provider and opaque references.
No guessed recognition threshold: verification/identification and PAD keep their
existing unavailable/unverified outcomes. No raw images or vectors in reports.

## Work and verification

- [x] Add backend manifest/vector/profile tests; observe RED, implement, observe GREEN.
- [x] Add optional ModelStore-backed OpenCV backend and local AAR publication.
- [x] Wire opt-in Demo import, explicit local enrollment setup, and existing bridge.
- [x] Run targeted tests, default/opt-in build and lint, and shared iOS compile.
- [x] Run real Android native inference with non-biometric synthetic input; retain
  actual detection/alignment acceptance as unverified unless an authorized face
  fixture is available. Synthetic runs cannot establish recognition/PAD quality.
- [x] Review, document evidence/gaps, preserve protected files, local commit only.

The user separately approved the NASA public-domain astronaut portrait for local
flow testing. Final-source TECNO KM9/API35 run
`b542f467-4f2a-4a09-920a-15bd06fc89bb` passed actual detection, alignment,
128-float normalized embedding, bridge enrollment, unverified-policy rejection,
deletion/close and lease release. This is not calibration or PAD acceptance.
Evidence and remaining gates: [validation](../../validation/android-opencv-identity-demo.md).

The original roadmap remains active after this slice; Apple real Identity, real
PAD, calibrated recognition, Android multimodal weights and UI/device acceptance
are not replaced by this prototype.
