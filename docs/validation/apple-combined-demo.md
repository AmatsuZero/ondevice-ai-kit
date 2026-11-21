# Apple combined Demo

2026-11-05: the existing Speech/Video/MLXVLM generator now accepts
`ODAI_OPENCV_IDENTITY_DEMO=1`. It builds one App with the existing shared UI,
real OpenCV Identity host and MLXVLM host, instead of requiring separate
Identity and multimodal Apps. Base configurations remain independent.

The Identity host retains explicit consent, local model import, encrypted
enrollment/deletion and the existing uncalibrated-policy rejection. This change
does not enable PAD or fabricate verification/identification thresholds.

Generate a fresh project after preparing the existing KMP frameworks and
`apple-opencv/Vendor/opencv2.xcframework`:

```sh
rtk proxy env ODAI_SPEECH_DEMO_DIR=artifacts/combined-demo \
  ODAI_VIDEO_DEMO=1 ODAI_MULTIMODAL_DEMO=1 ODAI_MULTIMODAL_MLX_DEMO=1 \
  ODAI_OPENCV_IDENTITY_DEMO=1 \
  /Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby scripts/generate-speech-demo.rb
```

Use scheme `SpeechDemo` in the generated project. With MLX enabled, the combined
variant requires iOS18 and uses bundle ID `io.ondeviceai.combined.demo`.
The current OpenCV binary lacks an arm64 simulator slice, so this optional
variant declares `SUPPORTED_PLATFORMS=iphoneos`. Identity can also be combined
with Speech alone, without requiring the MLX package or its lockfile.
The protected checked-in Xcode project is never rewritten.

The new combined unsigned arm64 device build passed with all four flags enabled,
fixed production pins, existing package/KMP/native inputs and no package update
or download. The same Mach-O contains the OpenCV native runtime, private Swift
Identity runtime, Identity host/bridge, MLXVLM backend and static KMP runtime/UI;
the MLX model descriptor and Metal library are present. OpenCV remains statically
linked. Generator tests and the full tools suite passed (304 tests).

Evidence is local ignored `artifacts/verification/apple-combined-demo-20261105/`.
It copies the original `e0438c0` source and records a generator overlay; this is
a **new combined configuration**, not the unchanged earlier exact candidate.
Build identity is `e0438c0…+combined-generator`. Read-only verification:

```sh
rtk proxy python3 artifacts/verification/apple-combined-demo-20261105/verify.py
```

This slice proves the reusable combined build and actual linkage/resources.
It has not been signed, installed or exercised through ordinary UI. Existing
iPhone automation failures were not retried. No device, model store, signing
asset or production lock was modified. Runtime/UI acceptance, independent
review, calibration and the original P4/P11 release gates remain open.
