# P10 identity model selection: prerequisite evidence

Date: 2026-10-17. Status: **blocked-unverified; no production model selected**.

This is a prerequisite research record for Task 9 of the [identity implementation plan](../superpowers/plans/2026-10-17-face-identity-liveness.md). It does not complete that task, approve its implementation, or approve any biometric dataset. No model weights were downloaded, tracked, redistributed or executed for this research.

## Candidate evidence

| Candidate and fixed source | Evidence retrieved from upstream | Missing gates / disposition |
| --- | --- | --- |
| InsightFace, source `81929474ec02e54e3655f6841bced800009c592d` | README separates MIT code (commercial use allowed) from non-commercial-research-only training data and pretrained models, including manually or automatically downloaded model packages. It directs commercial recognition-model licensing inquiries to the project. | The code license does not grant commercial redistribution of the distributed weights. Keep unavailable unless an applicable weight/model license is obtained and recorded. Runtime, preprocessing, mobile resources, authorized data and calibration have not been validated. |
| OpenCV Zoo SFace directory, `47534e27c9851bb1128ccc0102f1145e27f23f98` | Directory README states all files in the directory are Apache-2.0 and identifies MobileFaceNet/SFace provenance and conversion from the original SFace codebase. The directory lists full, INT8 and block-quantized ONNX variants. OpenCV documents five-landmark alignment, feature extraction and cosine/normalized-L2 comparison. | A plausible candidate for further evaluation, not a selected backend. Training-data terms/provenance beyond the upstream note, exact model asset hashes, tensor signature, preprocessing constants, supported mobile runtime conversion, latency/memory, approved dataset and calibration remain unverified. No tutorial threshold is adopted. |
| Original FaceNet implementation, `096ed770f163957c1e56efa7feeb194773920f6e` | MIT software license; README links separate pretrained downloads and names CASIA-WebFace/VGGFace2 training data. It describes fixed image standardization and a TensorFlow-r1.7-era environment. | No distinct affirmative commercial weight-redistribution grant was found in these sources. Repackager code licenses do not resolve it. Do not download or integrate weights on this evidence. Modern mobile runtime/maintenance and all data/calibration gates remain open. |

The SFace directory-wide license statement is evidence about that exact upstream distribution. It does not by itself document training-data rights, approve deployment conditions, establish accuracy or prove Core ML/LiteRT/ONNX Runtime support on iOS 15 and Android API 26. No dependency has been added and no winner is selected.

## Primary sources

- [InsightFace pinned README and license boundary](https://github.com/deepinsight/insightface/blob/81929474ec02e54e3655f6841bced800009c592d/README.md#license).
- [OpenCV Zoo pinned SFace README](https://github.com/opencv/opencv_zoo/blob/47534e27c9851bb1128ccc0102f1145e27f23f98/models/face_recognition_sface/README.md) and [directory license](https://github.com/opencv/opencv_zoo/blob/47534e27c9851bb1128ccc0102f1145e27f23f98/models/face_recognition_sface/LICENSE).
- [OpenCV face tutorial at `9b6287ec500ef7b327ac74c4f6c9cd84a2cbc9cd`](https://github.com/opencv/opencv/blob/9b6287ec500ef7b327ac74c4f6c9cd84a2cbc9cd/doc/tutorials/dnn/dnn_face/dnn_face.markdown) and [FaceRecognizerSF API](https://github.com/opencv/opencv/blob/9b6287ec500ef7b327ac74c4f6c9cd84a2cbc9cd/modules/objdetect/include/opencv2/objdetect/face.hpp).
- [FaceNet pinned software license](https://github.com/davidsandberg/facenet/blob/096ed770f163957c1e56efa7feeb194773920f6e/LICENSE.md) and [pretrained model documentation](https://github.com/davidsandberg/facenet/blob/096ed770f163957c1e56efa7feeb194773920f6e/README.md#pre-trained-models).

The sources were retrieved by a read-only researcher lane. The comparative dependency-expert lane returned service HTTP429 and produced no selection verdict; that failed attempt is not a review approval.

## Required before enabling production outcomes

1. Approve one exact model/runtime path with evidence covering commercial weight use and redistribution, asset hash, notice obligations, input/output/preprocess revisions, iOS 15/API 26 support and measured resource envelope.
2. Supply and approve development/validation data with an explicit authorized use; do not treat public benchmark names as permission to collect or redistribute their data.
3. Fix separate 1:1 and 1:N operating points, dataset revisions, enrollment rules and gallery-size strata. Record metric denominators and confidence intervals. Tutorial/demo thresholds are not production policies.
4. Bind verified policies to immutable model/embedding-space/preprocess revisions. Until then return `thresholdUnverified` or typed backend unavailable, with no score, match or identification outcome.
5. Run correctness, cancellation, lease/revocation, device and grouped accuracy checks on the selected implementation. Detection/tracking results are not identity embeddings.

These gates are part of the roadmap and remain incomplete. No registration, licensing inquiry, credential operation or external write was performed.

## Task 9 gate audit (2026-10-19)

Task 8 now provides the model-bound lease adapter used by a future selected
backend. It does not select a model or authorize an inference runtime. The
selection work package remains **blocked-unverified** because the record still
has no approved redistributable model revision, model hash/manifest, authorized
development or validation dataset, operating point, enrollment/gallery strata,
or device resource matrix. Consequently no Task 9 backend module, model asset,
verified `ThresholdPolicy`, accuracy harness, or production registration was
added. Identity engines continue to return typed `backendUnavailable` or
`thresholdUnverified` outcomes under these conditions.

## Pinned SFace runtime feasibility evidence (2026-10-19)

A read-only dependency review inspected the pinned OpenCV Zoo SFace directory
at revision `47534e27c9851bb1128ccc0102f1145e27f23f98`. The directory contains
three Git LFS pointer assets; no model was downloaded or executed:

| Asset | LFS SHA-256 | Model size in pointer |
| --- | --- | ---: |
| `face_recognition_sface_2021dec.onnx` | `0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79` | 38,696,353 B |
| `face_recognition_sface_2021dec_int8.onnx` | `2b0e941e6f16cc048c20aee0c8e31f569118f65d702914540f7bfdc14048d78a` | 9,896,933 B |
| `face_recognition_sface_2021dec_int8bq.onnx` | `fb143eea07838aa532d1c95df5f69899974ea0140e1fba05e94204be13ed74ee` | 10,667,852 B |

The pinned OpenCV implementation documents five landmarks (right eye, left
eye, nose, right mouth corner, left mouth corner), similarity alignment to
`112×112`, `blobFromImage` scale `1`, `swapRB=true`, and cosine/L2 matching.
It does not establish ONNX tensor names, output dimension, target-device
latency, or quantized runtime compatibility. OpenCV Zoo marks the exact
directory Apache-2.0; future distribution must retain the license and
applicable notices. This does not establish training-data provenance or
production authorization.

The recommended next experiment is a non-production parity harness comparing
ONNX Runtime CPU and OpenCV DNN on iOS 15 and Android API 26, after the model
experiment and test data are approved. It must record the final asset hash,
tensor metadata, aligned-pixel/embedding parity, latency, memory, binary size
and teardown. ONNX Runtime remains a feasibility candidate rather than a
selected dependency; exact package versions, deployment floors and execution
providers still need proof. No Task 9 model, runtime, weight, threshold, or
accuracy result is approved by this note.

Evidence: [pinned directory listing](https://api.github.com/repos/opencv/opencv_zoo/contents/models/face_recognition_sface?ref=47534e27c9851bb1128ccc0102f1145e27f23f98),
[float LFS pointer](https://raw.githubusercontent.com/opencv/opencv_zoo/47534e27c9851bb1128ccc0102f1145e27f23f98/models/face_recognition_sface/face_recognition_sface_2021dec.onnx),
[INT8 pointer](https://raw.githubusercontent.com/opencv/opencv_zoo/47534e27c9851bb1128ccc0102f1145e27f23f98/models/face_recognition_sface/face_recognition_sface_2021dec_int8.onnx),
[block-quantized pointer](https://raw.githubusercontent.com/opencv/opencv_zoo/47534e27c9851bb1128ccc0102f1145e27f23f98/models/face_recognition_sface/face_recognition_sface_2021dec_int8bq.onnx),
and [pinned preprocessing implementation](https://github.com/opencv/opencv/blob/9b6287ec500ef7b327ac74c4f6c9cd84a2cbc9cd/modules/objdetect/src/face_recognize.cpp).
Further retrieval encountered proxy refusal and DNS failure; package-version
claims are therefore left unverified rather than inferred from current docs.
