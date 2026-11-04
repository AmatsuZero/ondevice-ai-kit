# Android learned video SR Demo — 2026-11-03

2026-11-04 update: isolated API36 emulator system model/video pickers, real 4×
processing, reentry, player Activity handoff and share chooser are now verified.
Both exported copies passed independent packet/PTS/decode checks. See
[UI evidence and remaining playback/hardware boundaries](android-mainflow-emulator.md).
The earlier real-device runs below remain headless evidence.

2026-11-04 natural-input update: the same isolated emulator ordinary Demo also
completed a 320×180, 48-frame Common swifts clip → 1280×720 H.264 output. The
input was explicitly re-encoded to a supported High profile after the original
High 4:4:4 Predictive profile failed platform decoder discovery. All 48 output
frames fully decoded; PTS matched the derived input within 1 µs, with BT.709
limited-range metadata and no audio. Removing the input released this run's
private input/output files. See [input identity, evidence and quality
boundaries](android-mainflow-emulator.md#natural-video-main-flow).

Implemented optional `android:backend-litert-sr` and Demo `-PenableLiteRtSr=true` main flow: system picker → bounded private input store → AndroidVideoBridge/VideoProcessingClient → real ESRGAN CPU tiles → H.264 MP4/AAC mux → owned output → private FileProvider playback/share copy. The default APK has neither SR nor LM vendor runtime; both opt-ins can be combined. No model is packaged or downloaded by Gradle.

## Model/runtime and scope

User explicitly authorized optional official runtime dependencies and publicly licensed official example weights for local validation. Use standalone `com.google.ai.edge.litert:litert:1.4.2`; its POM declares Apache-2.0. The official TensorFlow example [README](https://github.com/tensorflow/examples/blob/master/lite/examples/super_resolution/android/README.md) identifies the [ESRGAN model card](https://github.com/tensorflow/tfhub.dev/blob/master/assets/docs/captain-pool/esrgan-tf2/1.md), which declares MIT for the model. This is separate from sample-code licensing. The initial dependency specialist proposed a generic ONNX route but failed with 429 before completing the official TFLite alternative; the author verified the official sample, model card, Google Maven POM, model tensor contract and storage hashes directly.

Model SHA-256 `1a380d3744103e11ef343534aaff54815cae40769dcd00c023652a7e5bc47f4b`, 4,993,712 bytes; storage MD5 `023aaf9c005dda701ab4ef770c2f27d0` matches. Native Interpreter checks exact input `[1,50,50,3]` and output `[1,200,200,3]` float32 shapes. RGB values are 0–255; 8-pixel halo/central 34-pixel tiles preserve full frame placement. No interpolation fallback, random weights or fixture output substitution exists. Installation verifies the exact model identity and atomically replaces only a validated copy. See [setup/profile](../../demo/androidApp/src/litertsr/README.md).

Supported prototype profile: even H.264 dimensions 16×16 through 320×180, BT.709 limited SDR, ≤2 seconds/48 frames/30 fps. AAC preserve supports at most one track with nonnegative packet timestamps within the clip. Negative AAC priming/edit-list timestamps are explicitly rejected; no silent audio truncation. Frame timestamps come from real decoder output. Codec discovery checks format support and prefers software codecs. Cancellation waits for the current blocking tile, then stops/releases codecs and removes partial output. Delivered output survives client close.

## Fresh local verification

Ignored evidence root: `artifacts/verification/android-video-sr-20261103/`.

- SR backend JVM **4/4**; existing video/bridge JVM **17/17**.
- Default Demo **43/43**; combined SR+LM Demo **47/47**; shared simulator tests **65/65**, zero skips/failures. Test XML snapshots retained.
- Default and combined APK/test APK builds, lint and shared `compileKotlinIosArm64` passed. Existing warnings retained. Default graph excludes `com.google.ai.edge.litert` and `com.google.ai.edge.litertlm`; combined graph pins 1.4.2 and 0.18.1 respectively.
- Backend release AAR and Maven POM generation passed; nothing was published or installed into a remote repository. Binary consumer/distribution acceptance remains open.
- Missing APIs observed RED before input store, selection retirement, export snapshot/open descriptor, tile assembly, color conversion, exact model gate and importing state implementation. Original RED and GREEN logs retained.

## Real Android CPU/media verification

TECNO KM9 / API35. Headless `VideoSrInstrumentation` does not launch an Activity, read private media or unlock the device. Final combined opt-in APK uses actual weights on an owned synthetic 64×48, two-frame, 24 fps, explicitly tagged BT.709 clip with four AAC packets.

Final run ID `55ae0b40-3d07-483f-a62f-d8a0dfd2b5cd`, raw `device-reviewed-final.log`: **status=passed / INSTRUMENTATION_CODE: 0**. Actual output is 256×192 H.264, two frames. Preserve/discard, first completed-frame cancellation + drain, same-client reentry, output survival after close and explicit deletion after release all passed. The 15,308 ms report covers this entire small validation sequence, not an inference/per-frame benchmark.

The runner reopens generated files to verify track presence/count, exact dimensions, sample PTS, audio removal and first-frame decoding. The ignored independent `validate.py` additionally uses ffprobe packet hashes/timestamps and full FFmpeg decoding of both outputs: two frame PTS match input; four preserved AAC packet SHA-256 and PTS match exactly; discard has no audio; both outputs decode completely and carry BT.709 metadata. Do not infer natural/temporal quality from synthetic media or successful decoding.

Preserved failures: the first fixture declared only BT.709 matrix, not full primaries/transfer; decoder reported nonstandard `color-standard=71` and correctly rejected it. Regenerated fixture explicitly sets all VUI color fields. Initial MediaTek encoder selection rejected 128×96; changed discovery to supported-format/software preference. A normal FFmpeg AAC fixture contains negative priming PTS −21,333 µs and is explicitly unsupported; the nonnegative AAC fixture separately proves the supported main flow. One attempted final run overlapped an unfinished app install and returned `Process crashed` (no new crash-buffer entry); installation was confirmed terminal, then the same runner passed. It is not counted as success. Earlier model transfer timeout resumed and validated storage MD5 before use.

## Review and remaining gates

Independent reviewer inspected this diff and found one medium evidence gap: the runner originally trusted backend metadata without reopening output. Fixed by runner postconditions plus fresh device and external packet/decode checks above. No production high/critical findings reported; no second independent APPROVE is claimed. Author review is separate.

Actual model/video pickers and player/share handoff are verified on the isolated
emulator as linked above; real-device UI acceptance remains open. The device's
historical locked/asleep state is not a claim about its current state. Native
headless inference is distinct from UI acceptance. Natural/tile-seam/temporal
quality, broad input profiles, AAC priming/edit lists, minimum API26 device,
GPU/energy, sustained resources, binary consumers and full release review remain
open. Corner/resource matrices are deferred per user direction;
F06/P11/release remain open/not-ready. Nine protected modifications remain
untouched. No push/merge/release.
