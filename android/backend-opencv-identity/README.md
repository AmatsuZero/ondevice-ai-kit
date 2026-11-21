# OpenCV Identity image backend (experimental)

Optional Android API 26+ module using official OpenCV `4.12.0` CPU inference.
It implements `IdentityBackend` primitives and supplies no verified recognition
policy or PAD implementation.

Create a `ModelStore` with the backend/version, current OS and ABI; install
`OpenCvIdentityModel.manifest()` from the exact two-file directory; then call
`OpenCvIdentityBackend.open(store)`. The backend pins the verified lease until
`closeAndWait()`. Encoded images must already be upright: EXIF is ignored.
Use IdentityStore/FaceVerification for authorized enrollment/comparison rather
than treating the primitive embedding as an identity decision.

`open` optionally accepts `(OpenCvIdentityDiagnostic) -> Unit`. Its finite
stage/kind enums distinguish model-store/integrity, runtime linkage, decoder,
detector, alignment and feature errors without recording private payloads.
Caller diagnostics must remain local and must not change admission decisions.

See [Demo use](../../demo/androidApp/src/opencvidentity/README.md) and
[validation](../../docs/validation/android-opencv-identity-demo.md) for exact
asset hashes, licenses and remaining gates. Runtime/model redistribution and
training-data provenance are not approved by a successful local experiment.
