# W3 no-VAD fixed-input control — 2026-11-09

Removing the VAD gate is **not accepted as a production fix**. With the same
frozen Mandarin PCM, models, five slices, zh-CN and unchanged primary scorer,
corpus CER worsened from44/92=47.8261% to53/92=57.6087%. W3 quality remains open.

The separate diagnostic measured actual VAD preparation ranges, including3/5
early stops. That proved input transformation, not removed speech or root cause.
This next protocol was frozen before execution and changed exactly one line
only in an isolated copied `SherpaAsrBackend`: return `samples.copyOf()` instead
of `WhisperSpeechGate.prepare(model,samples)` for the FILE path. Model verification,
session/decoder/language/input ownership and cleanup remained. Production source
was unchanged; recording/microphone was not used. The copied array is still
cleared by the existing session finally block.

Exact reversible [control plan](../../probes/android-natural-file-asr/no-vad-control-replacements.json).
Offline build13.169s and actual signatures/runner/SHA passed. One new reserved
physical TECNO/API35 batch completed3rows/5requests,19parentchecks, successful
close, unchanged caller arrays, frozen PCM and4modelhashes. No resourceWindow
was requested; no score/threshold/reference was changed to improve the result.

| Clip | Baseline errors / reference characters | No-VAD errors | Observation |
| --- | --- | --- | --- |
|1906, two slices|15/28|19/28|Output changed|
|2006, one slice|14/24|14/24|Transcript exactly unchanged|
|1883, two slices|15/40|20/40|Output changed|
|Corpus|44/92|53/92|Worse with full slices|

The unchanged second clip is evidence that its current errors are not repaired
by removing VAD cropping. The changed split clips show sensitivity to the
intervention, not universal model/SDK root cause. This tiny sample does not
separate broader model adequacy from segmentation, decoding behavior or domain
coverage. The Apple comparison uses a different backend and whole clips, so its
16/92 cannot prove a Sherpa integration defect. Remaining work is model/segment
quality attribution against a prospectively defined quality contract and larger
representative evidence; do not change scoring or deploy the bypass.

Evidence: `artifacts/verification/w3-no-vad-control-20261109-0952/`:
`protocol-freeze.json`, `source-manifest.json`, `build-result.json`,
`parent-acceptance.json`, `device-report.json`, `metrics.json`,
`parent-comparison.json`, `restoration.json`. Original failed-log run and durable
VAD run are retained independently. Parent-only review, no independent approval.
