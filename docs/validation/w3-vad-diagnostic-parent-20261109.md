# W3 VAD diagnostic parent receipt — 2026-11-09

The isolated diagnostic engineering slice passed source/host/build and actual
physical measurement checks. **W3 quality remains open.** The user selected the
primary agent to implement this diagnostic after hy4free returned an upstream
empty response/no files. No helper retry, paid call or native review was made.

The opt-in [exact replacement plan](../../probes/android-natural-file-asr/vad-diagnostic-replacements.json)
changes two copied source files only; production9 sources, protected9 files and
Apple lock remain unchanged. Each block occurs once and reverses exactly.
`diagnosticSnapshot` reads counters without calling the classifier; the prepare
path writes one numeric JSONL record and fsyncs in the new alias's model directory.
That diagnostic I/O is inside transcribe timing, so the run is not a latency
performance comparison. No model weights, PCM, slices, zh-CN, padding or scoring
were changed. No private data or microphone was used.

Baseline13checks; original-source RED compiled then failed for the missing
snapshot; diagnostic GREEN37checks and fresh37checks. Cases include real
partial-chunk stops, padding, ShortArray,160000bound, clear/no-speech and
unchanged output/classifier calls. First isolated APK build15.261s passed after
a retained sandbox Gradle lock failure. Its physical five-request report completed
and hypotheses matched baseline, but default-PID/all-buffer-PID/tag logcat queries
were empty. The cause of absent logs is unresolved. That observation failed,
was retained in `w3-vad-diagnostic-20261109-0920/measurement-disposition.json`,
and its aliases/stage were removed. It is not counted as measured VAD evidence.

A separately frozen durable-observation protocol repaired retention: new runId,
new APK/source fingerprint, absent output file required before invocation, one
single five-request batch. Build14.586s passed. Parent read the persistent report
and numeric JSONL with SHA checks, all25 checks passed, and removed only the
new aliases and exact stage; ordinary App paths remained unchanged. The four
model hashes and frozen input hashes matched before/after the run.

## Actual ranges

Offsets below are relative to each request slice, in16kHz samples.

| Clip / slice offset | Input | Consumed | Prepared range | Prepared length |
| --- | ---: | ---: | --- | ---: |
|1906 /0|83040|83040|[29056,83040)|53984|
|1906 /83040|83040|49280|[2432,36480)|34048|
|2006 /0|132480|121472|[15744,108672)|92928|
|1883 /0|110880|110880|[38784,110880)|72096|
|1883 /110880|110880|106624|[0,93824)|93824|

Three requests stop before input end; all prepared ranges are shorter than their
caller slices. This proves the input transformation, **not** that removed audio
contains useful speech or caused a transcription error. Output hypotheses and
primary CER remain baseline44/92 (27 substitutions/17deletions/0insertions).
A separately frozen same-PCM/no-VAD control is the next discrimination step;
there is no production change or quality-pass claim from these ranges.

Evidence: `artifacts/verification/w3-vad-durable-diagnostic-20261109-0945/`:
`protocol-freeze.json`, `host` results in the original directory,
`build-result.json`, `source-manifest.json`, `parent-acceptance.json`,
`vad-durable.raw.jsonl`, `vad-observations.json`, `parent-analysis.json`,
`metrics.json`, `device-report.json`, `restoration.json`.
Independent review remains unperformed; this is the primary-agent receipt.
