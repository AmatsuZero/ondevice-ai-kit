# Optional Apple OpenCV Identity

`ODAIKitOpenCVIdentity` supplies the pinned OpenCV 4.12.0 CPU YuNet/SFace backend for local iOS prototype use. The default `apple/` package has no OpenCV dependency. Recognition thresholds remain **UNVERIFIED** and PAD is absent. Enrollment is supported; this package supplies no calibrated match or identification decision.

The runtime is iOS-only (package floor iOS 15). The official archive provides arm64 device and x86_64 simulator code, **not arm64 simulator code**. Host-only manifest/input/normalization tests do not run OpenCV.

## Prepare the local runtime

Download the official [4.12.0 iOS archive](https://github.com/opencv/opencv/releases/download/4.12.0/opencv-4.12.0-ios-framework.zip), then run from the repository root:

```sh
rtk proxy python3 scripts/prepare-opencv-ios.py \
  --archive /absolute/path/opencv-4.12.0-ios-framework.zip \
  --output /absolute/path/new-sdk-directory
rtk proxy mkdir -p apple-opencv/Vendor
rtk proxy ln -s /absolute/path/new-sdk-directory/opencv2.xcframework apple-opencv/Vendor/opencv2.xcframework
```

The script checks size and SHA-256 before extracting. It preserves the official static archive, thins the supported architectures and packages them as a static-library XCFramework. The legacy framework cannot be embedded as a dynamic iOS framework. Output must be a new directory. `Vendor/` is ignored; binaries, model weights and test portraits are not committed.

## Open a backend

Install `OpenCvIdentityModel.manifest()` through `ModelStore` from a directory containing exactly its two pinned files. Then:

```swift
let backend = try await OpenCvIdentity.open(modelStore: models)
// Use IdentityStore / FaceVerification / FaceIdentification with this backend.
await backend.closeAndWait() // Join before removing the model revision.
```

The factory verifies the complete manifest and exact runtime version, keeps a model lease during initialization and transfers ownership to `LocalIdentityBackend`. Native work is serialized. Stop requests are checked between blocking decode/detect/align/feature stages; close drains the current call. Call these synchronous primitives away from the UI thread. Native errors contain stable codes/stage names only. OpenCV's process-wide logger is set to silent to prevent native ONNX errors from logging file paths.

Inputs are upright encoded images; EXIF is ignored. Limits: 10 MiB, axes 16–4096 and at most 4,194,304 pixels. BGR decode and linear resize to long edge 640 precede YuNet (.9 confidence, .3 NMS, top 5000), official SFace alignment and 128-component L2-normalized features. Quality is binary landmark geometry, not calibrated recognition quality. The Apple embedding-space ID is distinct from Android's; cross-platform feature compatibility is not claimed.

## Validate and run the optional Demo

```sh
rtk proxy env ODAI_OPENCV_CONTRACTS_ONLY=1 swift test --package-path apple-opencv
rtk proxy env ODAI_OPENCV_APP_DIR=artifacts/opencv-demo \
  ODAI_OPENCV_APP_KIND=demo ruby scripts/generate-opencv-identity-app.rb
```

Build `:demo:shared:linkDebugFrameworkIosArm64` first. The generator creates an isolated device project; it never rewrites the checked-in Demo project. Sign the generated project with an already configured local development team. The Demo imports a model **folder**, requires explicit 30-minute local consent, and uses the shared candidate/template/gallery controls through `IOSIdentityBridge`. Full-session deletion revokes the whole grant and awaits purge, including candidates whose UI replies were cancelled.

A durable cleanup marker is written before session activation and cleared only after successful purge. A failed or interrupted cleanup blocks later registration and reports an incomplete cleanup. Automatic recovery/retry is deferred; it must not be described as successful deletion.

The standalone probe (`ODAI_OPENCV_APP_KIND=probe`) reads approved assets transferred separately into its sandbox at `Documents/opencv-validation-input/{astronaut.png,models/}`. Launch only with `--run-opencv-validation`. It writes `Documents/opencv-validation-report.json`; no portrait is bundled. `ODAI_OPENCV_VALIDATE_HOST=1` adds an explicit Demo validation build, launched with `--run-opencv-demo-validation`, which writes `opencv-demo-report.json` and exercises the real host/bridge/ViewModel without claiming touch/picker acceptance. Ordinary Demo builds omit this harness.

The optional XCTest real-runtime case requires `ODAI_OPENCV_TEST_ASSETS`; absent assets cause an explicit skip. A skipped test is not inference acceptance. See [validation evidence](../docs/validation/apple-opencv-identity.md) for actual device results and remaining gates. Upstream runtime/model notices are retained in `Notices/`; production calibration, data provenance and redistribution review remain open.
