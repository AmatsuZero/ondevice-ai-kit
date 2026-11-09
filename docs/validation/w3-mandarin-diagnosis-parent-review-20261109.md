# W3 Mandarin diagnosis — parent disposition, 2026-11-09

CBC hy4free actually delivered three files after 552.204 s (exit 0). Files and recorded SHA256 match. The parent accepts source/evidence observations only; W3 and Mandarin quality remain open.

Accepted: `zh-CN` maps to `zh` and the constructor's `en` value is overwritten before decode; no proven English-locale bug. The second clip is unsliced yet has errors, so splitting cannot explain every error. The caller buffer integrity check does not prove the VAD-prepared waveform covers the complete slice. Whitespace joining does not change the frozen primary CER. Original Android **44/92** is retained.

Limits/corrections: the helper Read tool truncated a single long JSON line; the underlying complete report exists. The next task supplies pretty-printed reports with identical parsed content. An internal space already exists in the unsliced second transcript, so attributing its word insertion to `joinToString` is unsupported. The suggested minimum 19 representation-only errors has not been independently established against the scorer's exact alignment; it is not accepted as a causal lower bound. Repeated outputs differing does not alone prove API-state failure; identical outputs do not rule it out. A shifted-cut experiment must specify its cut before execution. No blanket adoption of its three proposed controls.

Apple same-input observation is now complete: [3/3 clips, 32 checks, CER16/92](w3-apple-mandarin-observed-20261109.md). Different backend and whole-clip processing prevent isolated model/SDK attribution. `UtteranceBuffer` source now shows early completion may occur inside a 1600-sample append, with 200 ms padding around speech. That is a testable preprocessing hypothesis, not a demonstrated error in these clips.

Next bounded task: isolated-source, read-only VAD metadata measurement using unchanged samples/model/locale/scoring, with exact consumed/prepared boundaries and five sequential-request events. Free hy4free owns the diagnostic candidate; parent owns build/device acceptance. No model download, production preprocessing change, primary rescoring, or repetitive boundary sweep is authorized by this diagnosis.

Local originals: `.omx/artifacts/cbc-hy4free-w3-mandarin-quality-diagnosis-20261109-0840/{result.json,candidate/DIAGNOSIS.md,candidate/CONTROL-PLAN.json,candidate/DELIVERY.md}`. No fresh independent native approval: the previous native lane failed429 and remains stopped.
