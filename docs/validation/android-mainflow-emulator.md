# Android Identity and video SR system UI — 2026-11-04

Bounded local acceptance on the isolated `ODAI_MainFlow_20261104` arm64 emulator,
Android 16 / API36. The connected TECNO device was not unlocked or used for this
UI run. Existing installed system-image bytes were reused; no new model, sample
or runtime was downloaded. This adds UI evidence to the earlier hardware
[Identity](android-opencv-identity-demo.md) and [video SR](android-video-sr-demo.md)
headless runs, without replacing hardware acceptance.

## Current three-opt-in candidate

A separate frozen candidate was built from commit
`71322085d61a80b92e9b30ac599feb284dc99b0f`, with249 tracked Android/Demo/KMP
build-input hashes and all three flags:
`enableLiteRtLm`, `enableLiteRtSr`, `enableOpenCvIdentity`.
APK SHA-256 `bd89bdbf3b39f5753879bb0faccff1dfc185dc3f132f215453c9f8b16fb16bd7`
(265,836,757 bytes); test APK SHA-256
`456cf13590cb322eaccc18725f6c3216ac5d3c9138d1af33d1729726c4876abd`.
DEX contains `CPU_TEXT_GPU_VISION` and the full source commit; arm64 libraries
include LiteRT LM, LiteRT SR and OpenCV. Runtime dependencies remain0.18.1,
1.4.2 and4.12.0 respectively. Imported SR/LM/Identity weights are absent from
the package; the four pre-existing bundled MLKit face assets remain present.

Demo56/56 tests actually executed; backend LM33/33, SR4/4 and Identity6/6
JUnit results were retained from Gradle `UP-TO-DATE`, not freshly executed.
`assembleDebug`, `assembleDebugAndroidTest` and `lintDebug` succeeded.
Initial invalid inherited Java paths and offline OpenCV cache-miss failures
are retained. Build recovery used the existing cached Temurin21 and restored
the repository's fixed OpenCV dependency, without upgrading declarations or
changing Apple caches/lock. An overbroad initial package check rejected all
model extensions; the corrected gate explicitly permits only the four
existing MLKit assets and preserves the failed-check explanation.

Only the existing isolated `ODAI_MainFlow_20261104` / emulator-5580 was updated
using `install -r`, preserving data; the older APK remains available for
rollback. The TECNO device still has its older APK. Ordinary system picker
selected `ODAI-combined-2frames-AAC.mp4`, exact host/private input SHA
`38cba1f9e37ece3fa13c14bbddb59013517181ac3fbe6c7bceb34813c6227f72`.
SR succeeded at128×96/two frames/one AAC track in PID5514. Original output:
7,724 bytes, SHA `0af7cab266fd8d73a0fb9d0768206c7a36f9ca5b6b5a4dbf36af1ed7abf32903`.
Independent full decode/PTS≤1µs/BT.709 and all four AAC packet hashes/PTS pass.
Ordinary UI input removal released private input/output; PID5514 remained.
An initial picker tap overlapping the emulator navigation bar returned Home;
after scrolling the button clear of that bar, selection succeeded. No new
process crash was observed; this is retained as an automation boundary.

Ignored `artifacts/verification/android-combined-current-20261104/` retains
frozen APKs,249 source bindings, build/JUnit receipts, install identity,
ordinary UI states and original media. Fresh `media-verify.py`: **301/301**,
including media, APK, source/protected/MLX lock and UI-cleanup receipts.
This current-package run verifies combined packaging and ordinary SR; it
does not claim current-package LM generation or Identity registration,
hardware GPU, natural quality, sustained resources or release readiness.
Historical UI evidence below belongs to its separately identified older APK.
The new bounded independent review returned service429 without a conclusion;
local verification does not constitute independent or full-branch approval.

## Candidate identity

Reused immutable combined APK from
`artifacts/verification/identity-gallery-demo-20261103/apks-terminal-final/`:
265,836,757 bytes, SHA-256
`dd610a4cfb7ad40a53b8320cd174e2d54b3bc0048108217c1b18b1f127eb6e7f`.
The installed `base.apk` hash matched. All seven prior source bindings matched;
`git diff cadfea8 HEAD -- android demo/androidApp demo/shared` was empty.
This run made no implementation changes or fresh build claim. All nine
protected dirty/untracked file hashes matched their existing guard.

## Observed ordinary UI flow

- DocumentsUI selected the existing YuNet/SFace ZIP; explicit local consent
  enabled `opencv.identity.android`. The system image picker's file browser
  selected only the previously approved NASA astronaut image. The shared page
  showed the selected identity input.
- Clicking register returned `ENROLLED · opencv.identity.android`; the displayed
  count changed from zero to one template. Verification showed
  `THRESHOLD_UNVERIFIED`; clicking gallery publication left the gallery
  unpublished with the same rejection. No calibrated decision was supplied.
- Deleting the current candidate showed zero users/zero templates and
  `删除：物理清理已完成`. Whole-grant teardown then showed
  `本次注册已删除并停用` and required fresh authorization configuration.
- DocumentsUI imported the exact existing ESRGAN file; the page showed
  `litert.esrgan.x4 · 可用`. System video picker → More options → Browse →
  DocumentsUI selected the owned synthetic AAC MP4. The page retained its
  private imported input across the picker round trip.
- Clicking 4× processing returned `SUCCEEDED`: 32×24 source → 128×96 output,
  two frames, 0.083332 seconds, one audio track. After opening the output,
  system events recorded `com.android.gallery3d.app.MovieActivity` resumed and
  returned to the Demo. The clip is too short for a sustained visual playback
  observation. The independent export copy survived background cleanup of the
  original output, as designed.
- Processing the same input again succeeded. Clicking share opened the system
  `Sharing 1 file` chooser for the generated MP4. The chooser was dismissed;
  no recipient was selected and no file was sent.

## Independent validation and retained evidence

Ignored evidence: `artifacts/verification/android-mainflow-emulator-20261104/`.
Its `verify.py` rechecks APK/source/protected-file bindings, 11 saved UI state
observations, player/chooser events and both pulled FileProvider export copies.
Fresh result: `verification.json` reports `status=passed`.

Both exports fully decoded with FFmpeg. ffprobe independently confirmed H.264,
128×96, two frames with source-matching PTS and BT.709 metadata. All four AAC
packet SHA-256 values and PTS matched the input. Export SHA-256:

- Playback: `5e04028347da52ff4819f868394f1b96e4f22a5b83b87de21f1176a691cd9326`.
- Share: `22d40b596a8aee5e5055eebd13f2b39057d296b10611ee4f9a433bd46a9fc8bd`.

The directory retains UI text observations, success/share screenshots,
installed APK hash, API identity, external Activity events and both output
copies. Recheck from the foundation worktree with
`rtk proxy python3 artifacts/verification/android-mainflow-emulator-20261104/verify.py`.
This is evidence validation, not a replacement for driving the UI again.

## Natural video main flow

An additional ordinary-UI run reused the existing authorized Common swifts
material, ESRGAN model and the same immutable APK above. No production code,
model, runtime or APK changed. This APK predates the latest LM execution policy;
the additional run validates only the unchanged SR implementation. Eight SR
source bindings matched the prior SR manifest. The App stayed in PID `2523`
through processing and cleanup.

The original prepared input (`artifacts/P7/natural/prepared/input-180p-4x.mp4`,
SHA-256 `97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`)
is lossless H.264 High 4:4:4 Predictive, despite its yuv420p pixel format.
The first UI request returned `FAILED / PLATFORM`. A separate Android
`MediaExtractor`/`MediaCodecList` probe found zero exact-format decoder
candidates (profile 64); the original failure is retained.

An explicitly derived copy uses libx264 High, CRF18, yuv420p, no B frames and
complete BT.709 limited-range VUI. It preserves the same 320×180, 24 fps,
48-frame/two-second content without audio; this is a lossy re-encoding, not the
original quality reference. Its SHA-256 is
`22b3e924ad5717a8cd278f92fdad3b39b1ed1886106a941e1d9b0287f6b026d8`.
The same native probe found three exact decoder candidates (profile 8).

The system picker selected `ODAI-common-swifts-H264-High-48f.mp4`. Actual CPU
ESRGAN processing advanced through visible frame progress and returned
`SUCCEEDED`, `1280×720 · 48 帧 · 2.0 秒 · 音轨 0`. The owned output was extracted
before leaving the page. Independent ffprobe/FFmpeg checks confirmed all 48
frames, matching PTS within 1 µs, H.264, BT.709 limited-range metadata, no audio
and full error-free decoding. Output SHA-256:
`ddce5b1dd8eeb3dbc9532efe2beb4a5334845bfa163301ff8ca9bc790e517ca5`
(2,240,688 bytes).

Visual inspection of frames 0/24/47 shows the expected birds, sky and clouds,
with different bird positions. This is a content sanity check; no comparative
SR, tile-seam or temporal-quality verdict is claimed. Clicking remove input
showed `尚未选择视频`; this run's private input and output files disappeared.
Only the same two prior FileProvider export files remained, as designed.

Ignored evidence root:
`artifacts/verification/android-sr-natural-20261104/`. It retains both codec
probes, original failure, derived-input command/hash, UI progress/success and
cleanup states, screenshot, PID-filtered native log, output, decoded contact
sheet and external verification. Recheck with
`rtk proxy python3 artifacts/verification/android-sr-natural-20261104/verify.py`.
This validates retained evidence, rather than rerunning inference. It also
checks the eight SR source hashes, nine protected files and production MLX lock.

## Remaining boundaries

This closes the isolated-emulator system-picker/register/delete and
model-import/video-process/player-handoff/share-chooser subitems. Real-device
Android touch/pickers, sustained visual/audio playback, arbitrary providers,
minimum API26, natural/temporal SR quality, sustained resources, calibration,
independent PAD and release acceptance remain open. No new review verdict is
claimed. P4/P11 and release remain `open/not-ready`; corner/resource work stays
deferred under the user's functional-priority instruction.
