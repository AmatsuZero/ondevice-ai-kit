# W3 Apple Mandarin same-input observation — 2026-11-09

W3 remains **open**. One file-only iPhone run recognized all three frozen public FLEURS clips. This accepts the bounded call/lifecycle observation, not Mandarin quality or production readiness.

The input is `google/fleurs`, `cmn_hans_cn/test`, revision `70bb2e84b976b7e960aa89f1c648e09c59f894dd`, CC-BY-4.0. Manifest SHA256 is `87610e63a23f9f0510cd1c35c70ea108e1928da3889f02f4b88d6161a81be9b8`. Parent checks verify that WAV payload PCM is identical to the Android raw PCM. No new media, model, reference or scoring rule was introduced.

| Clip | Apple CER errors / reference characters | Android CER errors / reference characters |
| --- | --- | --- |
| fleurs-cmn-test-1906 | 5/28 | 15/28 |
| fleurs-cmn-test-2006 | 6/24 | 14/24 |
| fleurs-cmn-test-1883 | 5/40 | 15/40 |
| Total | **16/92 = 17.3913%** (S4 D12 I0) | **44/92 = 47.8261%** (S27 D17 I0) |

The existing scorer uses NFC and casefold, excludes whitespace from character units, and preserves punctuation, digits and simplified/traditional distinctions. No after-the-fact threshold or alternate primary score is applied. Whitespace WER is not a language-aware Chinese word metric. Three clean clips do not establish representative quality or real-world-noise behavior.

The backends differ: Apple `apple.speech.recognizer` consumes whole clips; Android `sherpa.onnx.whisper` uses the pinned tiny/int8 model and the unchanged 160000-sample limit, splitting these clips into 2/1/2 requests. Apple does not report a model hash/revision. These results establish a difference; they do not isolate the model, VAD, segment boundaries or SDK as its cause.

The isolated iPhone alias ran once with `--odai-asr-locale zh-CN` (reported `zh_CN`) and `requiresOnDeviceRecognition=true`, run ID `74506118-862E-47B2-9698-5D6620113AD8`. It closed and drained; no microphone was used. Network isolation was not measured. Recognition durations sum to 0.9395641088485718 s, which is not end-to-end latency. The unsigned build passed in 11.8 s; 32 parent checks passed. Only the new alias was uninstalled, all 44 ordinary App installation URLs were retained, and the device returned to Combined.

Source identity is the seven actual Swift SHA256 entries in `inputs.json`; the report's historical `sdkSourceCommit` literal is not current HEAD. The existing native review lane failed with 429 and was not restarted; this is parent evidence verification, not fresh independent approval.

Receipts (local, Git-ignored): `artifacts/verification/w3-apple-mandarin-file-20261109-0845/{inputs.json,build-result.json,w3-file-report.json,speech-error-metrics-casefold.json,parent-acceptance.json,restoration.json}`. Original Android baseline: [Mandarin Android observation](w3-mandarin-android-observed-20261109.md).
