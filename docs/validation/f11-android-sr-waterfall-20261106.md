# Android SR: second-scene waterfall comparison

The existing licensed Kawaida Waterfall source (Lebu Ayiga, CC BY 4.0,
[Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Front_view_video_of_Kawaida_Waterfall,_Cianda,_Kiambu_County.webm))
was reused without downloading or capturing media. The native 480×848,
BT.709 limited source is cropped to 480×720 at (0,64), resampled to 24fps,
and limited to 48 frames. The reference is lossless FFV1, with no upscaling;
the input is area-downsampled to 120×180 and encoded H.264 High CRF18.
Full preparation commands and attribution are retained in `fixture.json`.

Input SHA256: `0903963764dc26c24c051ea1ab8e1e1974e4287bebfa9c61024784e24c139cc7`.
Reference SHA256: `3e201eb267149a1ae13a941c19112167e3dfb7a29833e81939ce033267b0bfe6`.
The existing ESRGAN model and five production media/model inputs are unchanged.
The isolated probe adds a fixed fixture whitelist and expected output size.
Offline assemble/lint passed: 332 tasks, 14 executed, 0 lint errors and 7
warnings (including three test Activity localization warnings).

## Results and interpretation

TECNO KM9 / API35 run `waterfall-20261106-01`, PID7242, persisted a completed
terminal report. All three outputs match device/report hashes, independently
decode to 48 H.264 frames at 480×720/24fps, and contain no audio. Their decoder
is `c2.android.avc.decoder`; encoder is `c2.android.avc.encoder`. The same
conversion, encoder settings and muxer are used across all three paths.

| Output vs the same reference | PSNR dB | SSIM |
| --- | ---: | ---: |
| Nearest + device H.264 | 30.444918 | 0.852670 |
| Android bilinear + device H.264 | 30.706528 | 0.861147 |
| ESRGAN + device H.264 | 29.970242 | 0.858508 |

ESRGAN SSIM is lower than nearest in 8/48 frames and lower than bilinear in
39/48 frames. Its average SSIM exceeds nearest but falls below bilinear;
its average PSNR is lower than both. This differs from the
[first scene](f11-android-sr-controlled-20261106.md), where ESRGAN SSIM was
lower than both controls in every frame. Six paired reduced frames show
stronger water/rock edge contrast than bilinear, without establishing
perceptual preference, fine seams or temporal quality.

These are encoded-output fidelity measurements for two fixed scenes, one
device and fixed run order. The waterfall encoder is software, unlike the
first scene's MTK encoder; do not compare cross-scene performance or infer
model-only quality from these scores. BT.709 is present in the waterfall
source metadata. No latency/RSS/GPU/energy benchmark is added.
`qualityVerdict=unverified`; foundation remains 10/12, P4/P11 open and release
not-ready. No quality gate, production dependency or model was changed.

## Collection and preservation

ADB briefly returned exit0 with empty output during collection. The same
completed run was read again; inference was never restarted. Only after all
three outputs were retained and no probe PID/active instrumentation remained,
the dedicated probe package and three hash-verified staging files were
removed. The original main/test APKs and paths, runner registry and 25 private
model/state hashes are unchanged; the ordinary App is foreground. Nine
protected files and the production Apple lock also match their retained hashes.

Evidence is local and ignored: `artifacts/verification/android-sr-waterfall-20261106/`.
Read-only verification: `rtk proxy python3
artifacts/verification/android-sr-waterfall-20261106/verify.py`.

The report validator was drafted by cbc hy3 and the observation reader by cbc
hy4free, then reviewed, amended and tested by the parent. Both are pure local
evidence tools; they are exercised against retained running/completed reports.
The tools suite passed 369 tests in 53.4 seconds. OpenCode free-model execution
failed with `provider.auth401/api_key_expired`; it supplied no accepted code.
