# Android SR: retained natural-output fidelity

The retained TECNO/API35 ordinary-App output now has a full 48-frame decoded
comparison against the existing Common swifts 720p reference. This uses the
historical `405d64e1…082c0` APK and fixed ESRGAN model, not a newly executed
candidate. No inference, device access, download or media capture was needed.

The actual selected input was the supported H.264 High/CRF18 derivative,
SHA-256 `22b3e924ad5717a8cd278f92fdad3b39b1ed1886106a941e1d9b0287f6b026d8`.
Its recorded preparation binds it to the existing 180p input and 720p reference.
The retained output SHA-256 is
`a21c0fcf5940aaf5513102f87b1d5cd2f074729a6fcc7b16aeba84f8716e47ea`.
The original encoded files were read without modifying historical evidence.

| Output vs the same 720p reference | PSNR dB | SSIM | Per-frame SSIM range |
| --- | ---: | ---: | ---: |
| Android ESRGAN + device H.264 encoder | 43.646096 | 0.984451 | 0.982334–0.987377 |
| FFmpeg bicubic from the same CRF18 input, lossless FFV1 output | 47.334832 | 0.993559 | 0.992024–0.994548 |

Both comparisons validate 48 decoded frame pairs, 1280×720/24fps, constant
cadence and matching BT.709 limited-range metadata. The SR output has the lower
SSIM in all 48 pairs; its lowest SSIM occurs at frame 29. These are output
fidelity scores. The baseline deliberately avoids another output compression
loss, while the device output is H.264. Different decoders, resizing/conversion
and encoders remain confounded, so the difference cannot be assigned to the
model alone or interpreted as a fair device-backend performance comparison.
The original public source has unspecified color; BT.709 remains the recorded
experimental interpretation, not a newly established source transfer function.

Author inspection of six evenly spaced side-by-side frames finds stronger
sharpening around birds/clouds in the SR output. This reduced contact sheet does
not establish a perceptual improvement or rule out subtle seams/flicker. A first
frame diagnostic samples luma jumps at the actual 136-output-pixel tile grid;
natural edges and H.264 block boundaries confound those values. It does not
justify changing the model halo or establish a seam verdict. No production
model, preprocessing, tile placement or encoder was changed.

`tools/probes/video_sr_quality.py` now retains paired per-frame PSNR/SSIM in an
additive `frames` array, including JSON-safe infinite PSNR. Previously only
aggregate scores survived. A real-media regression damages frame 24 and checks
that exactly that frame has reduced fidelity while the other 47 remain exact;
the pre-change report failed the test because no frame scores were retained.
Existing cadence/count and different-container timebase tests remain in place.

Ignored evidence: `artifacts/verification/f11-android-sr-fidelity-20261106/`, with
hash-bound inputs, baseline command, full per-frame reports, contact sheet,
boundary diagnostic and tools-test result. This adds a new historical-output
quality measurement, not new device latency/resource samples or a subjective
quality pass. `qualityVerdict=unverified`, F06/F11 and P4/P11 remain open.
Next useful quality experiment needs a controlled same-device baseline and
broader authorized natural scenes; repeating this same comparison will not
resolve the encoder/model or subjective-quality boundary.
