# W3 native Whisper language source check — 2026-11-09

W3 stays open. No language configuration defect or quality fix is established. No new device batch or production source change was made.

After the negative no-VAD control, the parent checked version-matched official upstream source for the actual declared dependency version2.0.1. JNI reads whisper.language and forwards SetConfig; OfflineRecognizerWhisperImpl stores the supplied whisper config and passes it into decoder SetConfig at each DecodeStream; the greedy decoder uses the current language to select the multilingual initial token. This is positive source evidence for dynamic language support, rather than evidence that the constructor default stays effective.

Official references: [JNI](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/sherpa-onnx/jni/offline-recognizer.cc#L561), [recognizer](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/sherpa-onnx/csrc/offline-recognizer-whisper-impl.h#L77), [decoder](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/sherpa-onnx/csrc/offline-whisper-greedy-search-decoder.cc#L18). Context7 lookup preceded the exact official fetch. Retrieved bytes, URL/status/SHA and parent disposition are retained in `artifacts/verification/w3-whisper-native-config-20261109-1026/`.

Freehy3 completed a single-line constructor-zh isolated-copy replacement candidate in137.976s with3actualfiles. Parent SHA/unique old block/reversibility checks passed. Runtime execution was not scheduled after the above upstream evidence lowered its expected diagnostic value. Candidate remains local in `.omx/artifacts/cbc-hy3-w3-whisper-constructor-control-20261109-1027/`; it was not integrated into production or counted as a runtime pass.

Public tag source does not prove exact binary/source equivalence for every byte in the existing AAR. This check narrows the investigation; it does not establish the model as the root cause. Frozen Android44/92, no-VAD53/92 and Apple16/92 observations remain unchanged; different backends and segmentation prevent direct model/SDK attribution. No scoring/reference/threshold changes or model download were used.
