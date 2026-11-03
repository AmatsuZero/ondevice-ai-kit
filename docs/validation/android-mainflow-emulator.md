# Android Identity and video SR system UI — 2026-11-04

Bounded local acceptance on the isolated `ODAI_MainFlow_20261104` arm64 emulator,
Android 16 / API36. The connected TECNO device was not unlocked or used for this
UI run. Existing installed system-image bytes were reused; no new model, sample
or runtime was downloaded. This adds UI evidence to the earlier hardware
[Identity](android-opencv-identity-demo.md) and [video SR](android-video-sr-demo.md)
headless runs, without replacing hardware acceptance.

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

## Remaining boundaries

This closes the isolated-emulator system-picker/register/delete and
model-import/video-process/player-handoff/share-chooser subitems. Real-device
Android touch/pickers, sustained visual/audio playback, arbitrary providers,
minimum API26, natural/temporal SR quality, sustained resources, calibration,
independent PAD and release acceptance remain open. No new review verdict is
claimed. P4/P11 and release remain `open/not-ready`; corner/resource work stays
deferred under the user's functional-priority instruction.
