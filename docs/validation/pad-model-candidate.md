# Independent RGB PAD model candidate

2026-11-04 candidate research, followed by the user's explicit approval for the
fixed official model and existing NASA portrait local raw-score experiment. This
is not a calibrated PAD decision policy. The initial research downloaded no model
or sample; the subsequent authorized smoke downloads only the fixed model.

## Pinned candidate

- Open Model Zoo `anti-spoof-mn3.onnx`, upstream tag `2023.3.0`, revision `e8fb4cd86a516ce5765290e9665f8afe87b79b2e`.
- [Official asset](https://storage.openvinotoolkit.org/repositories/open_model_zoo/public/2022.1/anti-spoof-mn3/anti-spoof-mn3.onnx): 12,270,179 bytes.
- Pinned checksum **SHA-384**: `6de4534964b723397b3e8c995cadcf43bc007cc2f9930b95ae25f76adccece5d1d4d058d0b15117b9e4a9f758424f92a`. Earlier text and the request incorrectly called this 96-hex value SHA-512; local hashing confirms SHA-384 exactly, with the same 12,270,179-byte binary.
- Downloaded binary SHA-256: `c4c99af04603b62d7e44f6f4daeb33e0daeccc696008c0b1d62f6f5cebbb3262`.
- [Pinned descriptor](https://raw.githubusercontent.com/openvinotoolkit/open_model_zoo/e8fb4cd86a516ce5765290e9665f8afe87b79b2e/models/public/anti-spoof-mn3/model.yml), [model card](https://docs.openvino.ai/2023.3/omz_models_model_anti_spoof_mn3.html), [accuracy configuration](https://raw.githubusercontent.com/openvinotoolkit/open_model_zoo/e8fb4cd86a516ce5765290e9665f8afe87b79b2e/models/public/anti-spoof-mn3/accuracy-check.yml).

The original ONNX input is `actual_input_1`, NCHW `1×3×128×128`, **RGB**. Normalize each channel as `(pixel - mean) / std`, mean `[151.2405,119.5950,107.8395]`, std `[63.0105,56.4570,55.0035]`. Output `output1` is `1×2`; index 0 denotes the upstream real-person class and index 1 the spoof-image class. The upstream card's BGR note applies to its converted OpenVINO representation, not an instruction to feed BGR to the original ONNX model.

It is a MobileNetV3 classifier trained on CelebA-Spoof. Upstream ACER is a reference dataset result, not this SDK's accuracy or an operating threshold. The repository's existing OpenCV 4.12 DNN can load ONNX and has relevant MobileNetV3 operator dispatch in [the pinned importer](https://github.com/opencv/opencv/blob/49486f61fb25722cbcf586b7f4320921d46fb38e/modules/dnn/src/onnx/onnx_importer.cpp); the exact asset has since passed the isolated iPhone raw-score smoke in [the runtime evidence](pad-raw-score-smoke.md). This does not validate a face-crop protocol or decision policy.

## Authorization and implementation boundary

The existing Identity asset approval explicitly excluded new PAD models. The
separate request, now explicitly approved, covers only this official binary and
reuse of the already approved NASA portrait for local raw-score smoke tests. It
does not authorize new biometric samples, calibration data, redistribution,
gated-license acceptance or production policy changes.

The model card labels the original model MIT while `model.yml` links Apache-2.0. Preserve both references and resolve the applicable binary notices before redistribution; do not silently replace one label with the other. Training-data terms and target-device/attack coverage remain separate open questions.

Execution order: verify exact bytes/SHA-384, calculate SHA-256 for binding, test
OpenCV load and RGB normalization/output shape on the target runtime, then
consider a separate optional raw-score adapter. Define crop/detection
preprocessing explicitly and bind its revision. The pinned accuracy config
specifies 128x128 CUBIC resize, without a detection/crop recipe. The import smoke
therefore explicitly uses the whole approved portrait with experimental revision
`whole-frame-cubic128-rgb-nchw-v1`; it does not assert a usable PAD face crop.
Do not infer a calibrated crop or threshold from the model card. Existing
FaceLiveness verified-policy admission stays intact: no `LIVE`/`SPOOF`, pass/fail
or proof from these experimental raw scores. Full PAD functionality still needs
an authorized evaluation/calibration set and policy.

## 2026-11-04 detector/crop protocol lookup

A bounded official-source review found two different reference paths. The fixed
[model card](https://docs.openvino.ai/2023.3/omz_models_model_anti_spoof_mn3.html),
[descriptor](https://github.com/openvinotoolkit/open_model_zoo/blob/e8fb4cd86a516ce5765290e9665f8afe87b79b2e/models/public/anti-spoof-mn3/model.yml)
and [accuracy config](https://github.com/openvinotoolkit/open_model_zoo/blob/e8fb4cd86a516ce5765290e9665f8afe87b79b2e/models/public/anti-spoof-mn3/accuracy-check.yml)
define tensors/RGB normalization/CUBIC128 resize; none fixes detection margin,
landmark alignment, box clipping or the SDK operating threshold.

- Original-author [live demo](https://github.com/kprokofi/light-weight-face-anti-spoofing/blob/82a2c26bacff5462c6f9320c34ae0363ccbed497/demo/demo.py) and [CelebA-Spoof loader](https://github.com/kprokofi/light-weight-face-anti-spoofing/blob/82a2c26bacff5462c6f9320c34ae0363ccbed497/datasets/celeba_spoof.py) crop the returned/annotated bounding box directly, without enlargement or landmark alignment.
- OMZ's [interactive demo](https://docs.openvino.ai/2023.3/omz_demos_interactive_face_detection_demo_cpp.html), pinned [main.cpp](https://github.com/openvinotoolkit/open_model_zoo/blob/e8fb4cd86a516ce5765290e9665f8afe87b79b2e/demos/interactive_face_detection_demo/cpp/main.cpp) and [detectors.cpp](https://github.com/openvinotoolkit/open_model_zoo/blob/e8fb4cd86a516ce5765290e9665f8afe87b79b2e/demos/interactive_face_detection_demo/cpp/detectors.cpp), instead makes a square face box, defaults to `bb_enlarge_coef=1.2`, `dx_coef=1`, `dy_coef=1`, clips to the frame, and feeds that ROI to the classifier without landmark alignment.

Thus a future SDK crop must be an explicitly application-owned revision,
binding the detector/version/coordinate mapping, square/enlarge/rounding/clipping
rules and resize/normalization. Neither reference proves equivalence for the
existing YuNet/SFace pipeline. Do not label one path as uniquely model-mandated,
reuse SFace alignment by assumption, or copy a demo threshold as calibration.

The existing `whole-frame-cubic128-rgb-nchw-v1` remains only the completed smoke.
Actual PAD SDK/Demo decisions still need a chosen capture/crop protocol and
an authorized genuine/attack evaluation set with an operating point. A scoped
async request for existing local authorized data/metrics is pending; no new
samples, corpus download or biometric collection is inferred from continuation.
