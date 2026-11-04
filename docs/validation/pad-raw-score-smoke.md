# PAD fixed-model iPhone raw-score smoke

2026-11-04, base `d17641d`, foundation worktree. The latest explicit user
approval answers the previously pending request for this official model and
reuse of the existing NASA portrait. Scope is local raw-score experimentation;
no new biometric sample, calibration, model redistribution or production policy.

The separate `io.ondeviceai.pad.rawscore` probe uses the existing approved
OpenCV **4.12.0** arm64 static archive. It changes no SDK package, ordinary Demo,
FaceLiveness admission gate or dependency. Reproduction and host checks are in
[the probe README](../../probes/ios-opencv-pad/README.md).

## Fixed inputs and correction

The official [anti-spoof-mn3 asset](https://storage.openvinotoolkit.org/repositories/open_model_zoo/public/2022.1/anti-spoof-mn3/anti-spoof-mn3.onnx)
is **12,270,179 bytes**, SHA-256
`c4c99af04603b62d7e44f6f4daeb33e0daeccc696008c0b1d62f6f5cebbb3262`.
It exactly matches the previously pinned 96-hex checksum under **SHA-384**.
The candidate record and permission request had incorrectly labeled that value
SHA-512; this corrects the algorithm label without substituting a model. Full
SHA-384/SHA-512/SHA-256 are recorded in local `asset-verification.json`.

The existing approved NASA raster remains SHA-256
`88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5`.
Both inputs are transferred separately to the probe sandbox and checked before
native inference; neither is bundled in the App or committed to Git.

The [pinned accuracy configuration](https://raw.githubusercontent.com/openvinotoolkit/open_model_zoo/e8fb4cd86a516ce5765290e9665f8afe87b79b2e/models/public/anti-spoof-mn3/accuracy-check.yml)
was fetched and specifies **CUBIC resize 128x128**, with no detection/crop recipe.
The probe explicitly uses experimental `whole-frame-cubic128-rgb-nchw-v1`:
whole raster, original ONNX **RGB**, per-channel mean/std from the
[candidate record](pad-model-candidate.md), float32 `1x3x128x128`. There is no
assertion that this whole-frame input is a usable PAD face crop.

## Fresh verification

Evidence is ignored/local under
`artifacts/verification/pad-raw-score-20261104/`.

- Host preprocessing: RED missing helper, then GREEN under
  `c++ -std=c++17 -Wall -Wextra -Werror`. Asymmetric RGB values, planar channel
  offsets, padded row stride, final-pixel extent and finite normalization pass.
- Report persistence: RED absent writer, then host GREEN for successful round-trip,
  filesystem write failure and serialization failure; a failure returns
  `failed-report-write` in the probe UI instead of a passed status.
- Final signed iOS build: `review-fixed-build.log` contains `BUILD SUCCEEDED`.
  Earlier harness-only failures (C error-pointer calling convention, static
  library search path and C++ linkage) are retained, not counted as runtime
  failures. OpenCV header documentation warnings and existing Xcode linker/
  AppIntents/orientation warnings remain; no warning-free build claim.
- Immutable final App/source/archive inventory, terminal install success,
  separate input copy, then launch on iPhone16 Pro Max / iOS **27.0.1**.
- Final device **9/9** checks, runId `0C5B5BD9-F924-4486-BD82-90A64B79C689`:
  fixed model/image/runtime, upstream model SHA-384 checked on device, two native
  runs, finite double-score output, same-input repeatability, declared
  preprocessing and sanitized load-failure diagnostics. Actual output shape
  **1x2**, OpenCV terminal layer type `Identity`; model output values were
  `[0.9430258274, 0.0569741987]` in both runs. No extra softmax or threshold is
  applied. These numbers issue no liveness conclusion.
- The two native setInput/forward intervals were **12.2045 ms** and
  **3.308084 ms**. They exclude model load, decode and preprocessing and are
  smoke timings, not an end-to-end benchmark or performance acceptance.
- Final probe PID **37633** terminated and confirmed absent. Existing ordinary Demo
  returned without validation arguments; its executable path matches the
  previous immutable ordinary installation. No camera, microphone, Photos
  access or device unlock.
- `probes/ios-opencv-pad/verify.py` freshly recomputes App/source/input hashes,
  the device result and install/launch identity, termination, nine protected
  hashes and unchanged production MLX lock. Report SHA-256:
  `66c064f050ed5f966f82c026413c9b8f642287c9238cf38a4a6bdcb2e833e807`.

The earlier initial 7/7 run remains in `device-report.json`; it is not the final
candidate. Independent bounded code review returned **REQUEST CHANGES** with
three Medium findings: device-side upstream checksum attestation, discarded
native diagnostics and swallowed report-write failures. SHA-384 is now checked
on device and report persistence has host RED→GREEN coverage. Native diagnostics
have device RED (`sanitizedLoadFailure=false`) → GREEN with only
`modelLoad / opencv / -5` for a deliberately absent path; no new sample/model is
introduced. Standard and unknown exceptions have distinct categories.

Ruling: preserve the existing no-raw-path/no-exception-text report boundary
instead of the reviewer's suggested raw exception messages. Keep OpenCV logging
suppressed in this isolated process, retain stage/category/numeric code and pin
the exact input/model/runtime for reproduction. This costs detailed native
exception text if a different import failure appears; further diagnosis may
require a separate scoped local diagnostic capture. No second independent
approval is claimed; the author verified fixes with both host checks and the
final immutable device run. P4/P11/release approval is outside this slice.

## Remaining functionality

This closes exact-model **target-runtime import/inference** uncertainty.
It does not deliver an SDK/Demo PAD backend. A deliberate face preprocessing
and capture protocol, authorized attack/evaluation/calibration data and policy
are still needed before actual PAD decisions. Identity 1:1/1:N calibration,
Android LM weights/ASR resources and Apple enhanced binary distribution remain
separate gaps. Corner cases and long resource work stay deferred. P4/P11 and
release remain `open/not-ready`; the roadmap goal remains active.

Model-card MIT versus descriptor Apache-2.0 remains unresolved for
redistribution. An additional `model.yml` retrieval was not executed because
automatic approval review failed with **429**; this was a review-service error,
not an unsafe-action ruling. No bypass or retry was used. The independent
preprocessing research agent also failed 429; no fresh external research
conclusion is claimed from that agent.
