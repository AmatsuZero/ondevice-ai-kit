# Android SR: same-device codec-matched comparison

The isolated test package `io.ondeviceai.probe.srquality` runs nearest-neighbor,
Android filtered Bitmap/bilinear, and the existing ESRGAN in that order on
TECNO KM9 / API35. All use the same approved 48-frame CRF18 input, decoder,
RGB/YUV conversion, encoder settings and muxer. The ordinary Demo/test APKs,
models and production dependencies are preserved.

Input SHA256: `22b3e924ad5717a8cd278f92fdad3b39b1ed1886106a941e1d9b0287f6b026d8`.
Model SHA256: `1a380d3744103e11ef343534aaff54815cae40769dcd00c023652a7e5bc47f4b`.
Reference SHA256: `fadf8e529c1cd7688473f1e2f7bf6dc33194435bc0dabe388664a03354221a6a`.
The five production inputs are snapshotted with their hashes; four are copied
verbatim, while `AndroidSrMedia.kt` gains only a codec-name observer.
[Probe source preparation and run protocol](../../probes/android-sr-quality/README.md).

## Device execution and host boundary

The original headless test demonstrated persistent terminal failure reporting
for missing inputs. Its real processing run then stopped at nearest frame4.
The process remained active, every inspected thread had `do_freezer_trap`, and
the system explicitly logged Hiber freezing UID10317 at 13:37:20.865.
The cancel sentinel could not execute while all threads were frozen. Its
nonterminal report and partial MP4 were retained before force-stopping only
this probe. This was not a quality pass or a demonstrated codec deadlock.

A minimal non-exported foreground Activity, started by the instrumentation,
keeps the screen on while visible. No global power settings or media/model
code changed. The rebuilt APK SHA256 is
`260a33b06b04bb4c9c68ff318a413a9e9589ec3163b9c6f66d60863c1d043f97`.
Offline assemble/lint passed (0 errors, 4 warnings); both source-preparation
tests passed. The physical repaired run is `controlled-20261106-02`, PID3027.
An early empty host return did not stop that run; the same private report
continued advancing, so no second inference was started.

## Results

The repaired run persisted `terminal=true/status=completed` with all three
48-frame outputs. Host collection matched the report and device SHA256 values.

| Output vs the same reference | PSNR dB | SSIM |
| --- | ---: | ---: |
| Nearest + device H.264 | 44.957615 | 0.990077 |
| Android bilinear + device H.264 | 46.437468 | 0.992679 |
| ESRGAN + device H.264 | 43.646096 | 0.984451 |

All three paths use `c2.android.avc.decoder` and `c2.mtk.avc.encoder`; all
independently decode as H.264 Baseline, 1280×720/24fps, 48 frames, no audio,
with PTS error below 1 microsecond. All comparisons use the existing
`tools/probes/video_sr_quality.py`, which retains every paired-frame score.

ESRGAN has lower SSIM than each control in all48 paired frames. The complete
decoded pixel hash is `f9b1518786bce4c91502c5c2fff3bf054f160e12dde9158412a085f2c75127db`,
identical to the historical ordinary-App output even though the encoded MP4
bytes differ. The historical result is reproduced by this current production
media/model source snapshot. Different desktop/device codecs no longer
explain the control gap in this experiment.

Author inspection of six corresponding frames shows stronger cloud/bird
edge sharpening in the ESRGAN column. The reduced contact sheet does not
establish perceptual preference, subtle seams or temporal flicker. This is a
negative output-fidelity result for this fixed task, not evidence to change
tile halo, relax quality gates or claim universal model inferiority.

Even codec-matched output scores describe the scaling stage interacting with
lossy encoding, not intrinsic model-only quality or perceptual preference.
The original public source has unspecified color; BT.709 remains an
experimental interpretation. One fixed clip, one device and fixed run order
do not establish broader natural/temporal quality or resource budgets. No
latency/RSS/GPU/energy benchmark is claimed. `qualityVerdict=unverified`;
F06/F11, P4/P11 and release acceptance remain open, foundation remains10/12.

Ignored evidence: `artifacts/verification/android-sr-controlled-device-20261106/`
(preflight, missing-input terminal failure and original frozen run),
`artifacts/verification/android-sr-controlled-foreground-20261106/`
(repaired build, observations and collected output measurements).

The optional cancel-check host script first read a report before its creation
and then lost its observation process. Followup of that exact run ID found no
report directory, no target PID and no active instrumentation amid USB
interruptions. No cancellation outcome is inferred; no second inference was
launched to manufacture one. The successful comparison is independent of this
unverified optional check. After collecting results and confirming no active
test, the dedicated package and four hash-verified staging files were removed.
Post-cleanup verification matched both original APK hashes/installation paths,
the instrumentation registry and all25 preflight model/private-state hashes.
The nine protected workspace files and production Apple lock also match.
