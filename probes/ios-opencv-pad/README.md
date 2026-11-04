# Fixed-model PAD raw-score probe

Independent iPhone research consumer, outside the SDK and ordinary Demo. The
user approved only the pinned official model download and reuse of the existing
NASA astronaut portrait for local raw-score smoke tests. Models and images stay
in ignored local evidence and are not bundled or redistributed.

The model and notices are recorded in
[the candidate record](../../docs/validation/pad-model-candidate.md). The pinned
checksum is SHA-384, correcting the earlier SHA-512 label. Mean/std apply to
**RGB**, with a float32 `1x3x128x128` tensor. The input has experimental revision
`whole-frame-cubic128-rgb-nchw-v1`: decode the approved 512x512 raster with no EXIF
orientation, CUBIC resize the whole raster, convert BGR to RGB and normalize.
This is an import/inference smoke, not a validated face crop or PAD pipeline.

Host contract checks (no OpenCV or new dependency):

```sh
rtk proxy c++ -std=c++17 -Wall -Wextra -Werror \
  probes/ios-opencv-pad/PadPreprocessingChecks.cpp -o /private/tmp/odai-pad-preprocessing
rtk proxy /private/tmp/odai-pad-preprocessing
rtk proxy swiftc probes/ios-opencv-pad/PadReportChecks.swift \
  probes/ios-opencv-pad/PadProbeReport.swift -o /private/tmp/odai-pad-report-checks
rtk proxy /private/tmp/odai-pad-report-checks
```

Prepare the existing approved `apple-opencv/Vendor/opencv2.xcframework` first,
then generate to a **new** ignored output directory:

```sh
rtk proxy env ODAI_PAD_APP_DIR=artifacts/pad-probe-app \
  ruby scripts/generate-pad-raw-score-app.rb
rtk proxy xcrun xcodebuild -project artifacts/pad-probe-app/PadRawScore.xcodeproj \
  -scheme PadRawScore -destination 'generic/platform=iOS' \
  -derivedDataPath artifacts/pad-probe-derived DEVELOPMENT_TEAM=<local-team> build
```

After terminal install success, copy exactly the approved `astronaut.png` and
`anti-spoof-mn3.onnx` into the app's `Documents/pad-input` via devicectl's
`appDataContainer` domain `io.ondeviceai.pad.rawscore`. Launch with
`--run-pad-raw-score`, then retrieve `Documents/pad-report.json`. The app checks
both SHA-256 values and the upstream model SHA-384 before native inference. With no argument it performs no
validation. There is no camera, microphone, Photos access or networking.

The report requires two finite `1x2` native outputs, same-input repeatability,
the fixed model/runtime and explicit preprocessing revision. Native exception
text and local paths stay out of the report; failures retain a stage, exception
category and numeric OpenCV code. A missing-model regression checks this path
without another model or sample. Report serialization/write failure returns
`failed-report-write` instead of passing. End only the PID returned by this
launch and return to the ordinary Demo without validation arguments.

Raw model values, even when they resemble probabilities, do not issue a
liveness verdict, threshold result or proof. Calibration, attack evaluation,
preprocessing selection and SDK/Demo PAD integration remain separate work.
