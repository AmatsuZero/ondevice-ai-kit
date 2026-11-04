# Android learned video SR Demo — 2026-11-03

2026-11-04 update: isolated API36 emulator system model/video pickers, real 4×
processing, reentry, player Activity handoff and share chooser are now verified.
Both exported copies passed independent packet/PTS/decode checks. See
[UI evidence and remaining playback/hardware boundaries](android-mainflow-emulator.md).
The earlier real-device runs below remain headless evidence; the bounded
TECNO ordinary UI section below adds separate physical evidence.

2026-11-04 natural-input update: the same isolated emulator ordinary Demo also
completed a 320×180, 48-frame Common swifts clip → 1280×720 H.264 output. The
input was explicitly re-encoded to a supported High profile after the original
High 4:4:4 Predictive profile failed platform decoder discovery. All 48 output
frames fully decoded; PTS matched the derived input within 1 µs, with BT.709
limited-range metadata and no audio. Removing the input released this run's
private input/output files. See [input identity, evidence and quality
boundaries](android-mainflow-emulator.md#natural-video-main-flow).

Implemented optional `android:backend-litert-sr` and Demo `-PenableLiteRtSr=true` main flow: system picker → bounded private input store → AndroidVideoBridge/VideoProcessingClient → real ESRGAN CPU tiles → H.264 MP4/AAC mux → owned output → private FileProvider playback/share copy. The default APK has neither SR nor LM vendor runtime; both opt-ins can be combined. No model is packaged or downloaded by Gradle.

## 2026-11-04 TECNO complete 48-frame natural request

A subsequent ordinary system-picker request on TECNO KM9/API35 completed the
**full 48-frame** H.264 High/CRF18 Common swifts derivative. The earlier OEM
configuration-change interruption below remains a separate failed attempt.
No APK, model, system power setting or production source was changed for this
request. The installed APK remains `405d64e1…082c0`, predating the latest LM
policy; this is SR evidence. The fixed ESRGAN model is unchanged.

The actual selected filename was `ODAI-common-swifts-H264-High-48f.mp4`.
Host and private input SHA-256 matched
`22b3e924ad5717a8cd278f92fdad3b39b1ed1886106a941e1d9b0287f6b026d8`.
PID26205 persisted from processing through the captured success state:
`SUCCEEDED / 1280×720 · 48 frames · 2.0 seconds · audio0`.
The original output was extracted before background handoff:1,833,519 bytes,
SHA-256 `5ea2bb2b3198074492e354cc6ccecdf2d0d15530e604e2ef334bb1fadd053264`.
Independent FFmpeg complete decode and ffprobe confirm48 H.264 frames,
320×180→1280×720, every PTS matching within1µs, BT.709 limited range and no
audio. This derived input is not the original lossless quality reference.

The playback button opened the resolver; clicking its Visha recommendation
launched `com.transsion.magicshow/com.transsion.magicvideo.activities.VideoPlayerActivity`
directly. Automation then used a stale resolver reference intended for
“Just once”; the next observed screen was an unexpected episode selector.
BACK exited and the Demo was relaunched. No visible video frame was captured,
so actual visual playback remains unverified. Project-scoped logs bind the
player's exact FileProvider URI basename `a8c04c8a-d557-49db-be2a-3514a799cf6b.mp4`
to an export with the same original output SHA; the player log records
`playPosition=2000`. That log does not replace visual acceptance.

At14:06:00 the system recorded `Killing26205 … remove task`; the actor/cause
of task removal is not established. Relaunch used PID31417 and no selected
input. Original output had retired, but the exact private input remained
orphaned after process death. Its SHA was rechecked and only that file/empty
parent were manually removed. This is **manual recovery**, not ordinary UI
cleanup acceptance or a same-PID playback return. Three export copies remain
under the existing one-day retention policy. Process-death recovery remains a
deferred corner case; no lifecycle workaround was added.

Ignored evidence: `artifacts/verification/android-sr-full48-physical-20261104/`;
fresh `verify.py` **49/49 passed**, covering full media/input/output identity,
processing PID, exact player URI/export, explicit process-death/manual-cleanup
boundary, unchanged APK,12 SR source bindings,9 protected files and MLX lock.
This closes physical ordinary-UI48-frame completion. Visual playback, quality,
performance/energy, minimum API26, sustained resources and release remain open.
For the separate latest-source APK, see
[combined candidate](android-mainflow-emulator.md#current-three-opt-in-candidate).
The new bounded independent review returned service429 without a conclusion;
its review gap remains open despite the local evidence checks passing.

## 2026-11-04 TECNO ordinary UI, bounded natural and AAC sequence

Fresh device identity: TECNO KM9, Android15/API35, arm64-v8a, serial
`15365375C9007986`. The ordinary Demo remained in PID26205 throughout this
sequence. No production source, runtime dependency or APK was changed/rebuilt; the fixed
existing model weights were installed through the ordinary importer.
Installed APK SHA-256 is
`405d64e1bf67d1879ff96db386282066e7cda612a2b77134377e070feec082c0`,
the preserved three-opt-in identity-gallery APK. It predates the latest LM
policy changes; this run does not validate those changes or claim whole-current
APK/source equivalence. Twelve SR backend/host/shared source hashes still match
the earlier SR ledger; supplemental current Activity/manifest/Gradle hashes
are recorded separately without pretending that every file has an APK-build
hash receipt. Private installed ESRGAN bytes match the fixed model digest.

- Ordinary system DocumentsUI model import: cancel, reenter, select the exact
  existing model; the backend became available. Video picker cancellation and
  reentry also passed. Only explicitly prepared project fixtures were selected.
- The original 320×180/48-frame natural clip was selected with the system picker.
  The first request was cancelled after a nonzero completed-frame update;
  `CANCELLED` and deletion of partial output were observed. The intermediate
  `CANCELLING` state was not captured. Same-input reentry advanced to nonzero
  processing (shutdown logs record22 encoded frames), then **was interrupted**, not successful: at13:13:10
  charging reached75%, the OEM exited power-save mode and changed UI mode.
  System logs show `Config changes=200`/`CONFIG_UI_MODE`, MainActivity recreation
  in the same PID, codec shutdown and private input/output cleanup. No current
  PID exit/new crash was recorded. Configuration-state restoration remains
  deferred; no manifest or lifecycle workaround was added.
- To finish a bounded functional sequence, stream-copy the first **4 frames** of
  that same approved H.264 High/CRF18 derivative, without further re-encoding.
  Input SHA `59046e7ce4279f3666d6d971f005a26628e11b2964e2c6406ea123c68ceff53f`;
  actual system picker selection and a new processing request produced
  `SUCCEEDED / 1280×720 · 4 frames · audio0`. Output191,636 bytes,
  SHA `21b0c5f081c22bd639cc3323ba7a34b9c0068a09ed8e4dda646f63a98ea54c45`.
  This is a four-frame derived natural clip, not a completed physical48-frame
  request or the original lossless quality reference.
- The existing32×24/two-frame AAC fixture completed **twice on the same selected
  private input and PID**, with distinct output paths:128×96, two frames, one
  AAC track. Each output fully decoded; all four AAC packet hashes and PTS
  matched the source. Three outputs' frame PTS matched within1µs and carried
  BT.709 limited metadata. These are external ffprobe/FFmpeg checks.
- Sharing the second AAC result opened the OEM chooser (`正在分享视频`), then
  BACK returned to the Demo. No recipient/target was selected and no file sent.
  Its FileProvider copy matched the second original output byte-for-byte.
- Playing the natural result opened the Android resolver. Files was chosen with
  **Just once**; the actual `ACTION_VIEW` and project `videooutputs/video-results`
  URI reached `com.google.android.apps.nbu.files/.tos.TosActivity`. Files showed
  first-use terms, which were not accepted; BACK returned to the Demo. This
  proves resolver/external-Activity handoff and exact exported bytes, **not
  visible playback or player acceptance**. The input remained until explicitly
  removed; the original output had already retired on backgrounding.
- Removing the input left no run-owned private input/output files. Two exact
  FileProvider exports remain under their existing one-day retention policy.
  Public input/model fixtures remain available for reproduction.

Ignored evidence: `artifacts/verification/android-sr-physical-ui-20261104/`.
Fresh `verify.py`: **108/108 checks passed**, including input/output/model/APK
identity, three full decodes/PTS/color, two AAC packet comparisons, distinct
same-input reentry outputs, cancellation cleanup, external UI boundaries,
nine protected files and the production MLX lock. OEM recreation diagnostics
are retained as time/project-scoped lines; the original broad diagnostic
capture was not retained. Independent final review confirmed the bounded core
flow and manually linked both actual handoff URI basenames to the export hashes;
those links are now validator-enforced too. Its missing retained21-frame UI
observation was corrected to the actual shutdown log's22 encoded frames. The
AAC captures retain the same private input path and system filename, but did not
record a private-input SHA before retirement; exact private AAC-copy identity
is an explicit evidence gap, distinct from the exact output audio packet checks.
This is a bounded evidence review, not full-branch/release approval.

This closes the bounded physical ordinary model/video-picker, processing,
cancellation/reentry, chooser and external-Activity handoff subitems. Physical
48-frame completion was subsequently verified above; actual external playback, arbitrary providers/manual
human touch, comparative natural/tile/temporal quality, performance/energy,
minimum API26, sustained resources, distribution and release gates remain open.
F06/P11/release remain open/not-ready; no push/merge/release.

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
emulator as linked above; the bounded TECNO section adds physical ordinary UI
evidence while actual external playback and full-profile acceptance remain open. The device's
historical locked/asleep state is not a claim about its current state. Native
headless inference is distinct from UI acceptance. Natural/tile-seam/temporal
quality, broad input profiles, AAC priming/edit lists, minimum API26 device,
GPU/energy, sustained resources, binary consumers and full release review remain
open. Corner/resource matrices are deferred per user direction;
F06/P11/release remain open/not-ready. Nine protected modifications remain
untouched. No push/merge/release.
