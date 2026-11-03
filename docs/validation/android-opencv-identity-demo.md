# Android OpenCV Identity prototype

2026-11-03 local functional slice. **Experimental; production recognition/PAD
and roadmap release acceptance remain incomplete.** The user explicitly approved
the optional OpenCV runtime/YuNet/SFace downloads, then separately approved the
NASA public-domain astronaut sample for local flow testing. That sample is not
calibration data and is neither tracked nor bundled.

## Implementation

- `android:backend-opencv-identity`: official OpenCV 4.12.0 CPU YuNet detection,
  upstream five-landmark `FaceRecognizerSF.alignCrop`, real SFace inference and
  L2-normalized 128-float embedding; exact combined ModelStore manifest/binding.
- Optional Demo `-PenableOpenCvIdentity=true`: exact ZIP import with bounded
  extraction and ModelStore SHA-256 checks; explicit local prototype consent;
  real AndroidIdentityBridge enrollment into encrypted IdentityStore/Keystore;
  deletion, stop/publication suppression and model lease drain.
- Default Demo excludes OpenCV. All three optional runtimes can compile together.
  Sanitized stage/category diagnostics preserve local failure distinctions.
- The enrollment quality score is binary valid landmark geometry, with explicit
  host floor 1. It is not calibrated recognition quality. The verification
  policy remains `UNVERIFIED` with no guessed numeric threshold. Real 1:N
  index/query implementation, its operating policies and PAD remain unfinished.

## Fixed upstream evidence

| Component | Source / identity |
| --- | --- |
| Runtime | [official Maven POM](https://repo.maven.apache.org/maven2/org/opencv/opencv/4.12.0/opencv-4.12.0.pom), Apache-2.0; AAR 117745028 B, SHA-256 `f71846a313388d9da667a59be1e921669fcf792d1ba264b3898253ca789bc3f0`, matches the upstream `.aar.sha256` |
| YuNet | [pinned model directory](https://github.com/opencv/opencv_zoo/tree/47534e27c9851bb1128ccc0102f1145e27f23f98/models/face_detection_yunet), MIT; 232589 B, SHA-256 `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4` |
| SFace | [pinned model directory](https://github.com/opencv/opencv_zoo/tree/47534e27c9851bb1128ccc0102f1145e27f23f98/models/face_recognition_sface), Apache-2.0; 38696353 B, SHA-256 `0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79` |
| Alignment/features | [OpenCV 4.12.0 implementation](https://github.com/opencv/opencv/blob/4.12.0/modules/objdetect/src/face_recognize.cpp): 112×112 similarity warp, blob scale 1, RGB swap; backend adds explicit L2 normalization |
| Local flow fixture | [scikit-image provenance](https://github.com/scikit-image/scikit-image/blob/v0.25.2/skimage/data/_fetchers.py): NASA Great Images astronaut photograph, public domain; pinned [registry hash](https://github.com/scikit-image/scikit-image/blob/v0.25.2/skimage/data/_registry.py) `88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5` |

OpenCV's official `.module` file contains a stale AAR size/hash (3557859 B /
`cb3d901...`) that disagrees with the actual official AAR and its separate
checksum. Preserve the original metadata; this experiment verifies the downloaded
AAR against the separate official checksum rather than rewriting upstream
metadata. The artifact contains arm64-v8a, armeabi-v7a, x86 and x86_64 native
libraries and declares minSdk 21; the project module floor remains API 26.
Only API 35 hardware has been exercised here. The failed initial registry-search
claim that no official OpenCV Maven artifact existed was superseded by direct
retrieval of that exact official POM/AAR.

Optional APK license assets retain OpenCV, YuNet and SFace license texts. They
do not complete a production/transitive notice or training-data rights review.

## Validation and open gates

Raw logs, immutable APK snapshots, downloaded assets, license/provenance sources
and reports live only under ignored
`artifacts/verification/android-opencv-identity-20261103/`. Unit-test RED logs
record absent symbols before implementation. The first backend build failed
while its AAR was still downloading; it is dependency setup failure, not RED.
The first streamed installation overlapped APK rebuilding and failed
`INSTALL_PARSE_FAILED_NOT_APK`; subsequent installs use immutable snapshots.

The initial procedural-only headless run `3661b986-80ac-409e-985d-655f2435fe88`
proved blank rejection, bridge insufficient-quality, real SFace 128 finite
nonzero outputs on a synthetic aligned raster and model lease cleanup. YuNet
found zero faces in the drawn portrait, so that run does **not** prove positive
detector/alignment/enrollment acceptance despite its historical `status=passed`.
The revised runner requires the hash-bound approved positive fixture and a real
backend embedding/enrollment before its end-to-end gate can pass.

Final-source validation on TECNO KM9/API 35 passed with run
`b542f467-4f2a-4a09-920a-15bd06fc89bb`. The runner required the approved fixture's
hash, exactly one detected face, real alignment/SFace extraction, a unit-norm
128-float embedding, bridge enrollment with an opaque template, blank rejection,
`THRESHOLD_UNVERIFIED` rejection, candidate purge, idempotent close and model
lease release. This used no camera or Activity launch. It proves the local flow,
not recognition accuracy, calibration or PAD.

- Backend tests **6/6**, default Demo **48/48**, OpenCV+SR+LM Demo **53/53** and
  shared iOS simulator **65/65**, all with zero failures. APK/test APK/lint,
  shared iOS arm64 compile and backend release AAR/POM passed. Default runtime
  dependency inspection excludes OpenCV, LiteRT-SR and LiteRT-LM.
- Final target APK SHA-256:
  `6d4c88b8350c883d330dc850bfdb218382faad33e1b14baf2e37b533f91cc3fd`;
  test APK: `5fd63abb1f649eedbdd43d76234a733a8ebd9eea843cef2d1feee4951787d605`.
  Both immutable snapshots were installed sequentially and their installed
  `base.apk` hashes matched before accepting the final run.
- `report-final-verified.json` SHA-256:
  `cdc30b7bc67bb4716cdf1a641e3a0ca8cafcb7860f0c08904290e3183db61216`.
  `final-verification.json` binds that report to installed APK identities and
  test counts. XML snapshots are under `test-results/`; original build logs are
  retained. `device-final.log` predates the successful final reinstall and is
  not final-source acceptance evidence.
- Independent review found two Medium issues (diagnostic preservation and a
  runner that could pass without positive detection). Both were fixed, then
  the runtime diagnostic presenter was connected to observable UI state with
  a RED→GREEN test. The final bounded re-review reported zero issues and
  `COMMENT`; it is not whole-branch approval or an independent device rerun.

For a local rerun, build with `-PenableOpenCvIdentity=true` and
`-PodaiTestRunner=io.ondeviceai.demo.IdentityModelInstrumentation`, snapshot the
finished target/test APKs, and install each only after the prior command exits
successfully. Stage the two hash-pinned models in the app-private
`files/identity-validation/models/` and the separately approved image in
`files/identity-validation/authorized-astronaut.png`. For each asset, `adb push`
to `/data/local/tmp/`, then `adb shell run-as io.ondeviceai.demo cp` to the exact
app-private destination; create those directories with `run-as ... mkdir -p`.
Run `adb shell am instrument -w
io.ondeviceai.demo.test/io.ondeviceai.demo.IdentityModelInstrumentation` and
retrieve `files/identity-validation/report.json` through `adb exec-out run-as`.
The runner refuses a missing or mismatched positive fixture. Obtain permission
for any new biometric fixture; this local NASA sample approval does not extend
to other people, data collection, packaging or calibration.

Actual DocumentsUI import, explicit consent/button behavior, repeated enrollment
UI, natural recognition/PAD quality, calibrated policies, 1:N production index,
Apple real model integration, default/minimum OS device matrix, long-term/native
GC resource behavior, process-death cleanup and binary consumers remain open.
No push, merge, publication or production registration is implied.
