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
