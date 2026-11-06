# Android offline ASR: fixed-input resource measurement

On2026-11-07, the retained ordinary `afed156b…926c461` APK completed five warmups
and30 measured SDK requests on TECNO KM9/Android API35 in PID8577. All35 fixed
synthetic-English matches, backend identities and caller-input checks passed.
No microphone, recording, new model, download or ordinary-App replacement.

| Measurement | Observed value |
| --- | --- |
| SDK transcribe wall p50 / p95 | 1807.033 /1827.525ms |
| Measured request min /max |1772.597 /1856.137ms |
| Per-request sampled process RSS peak range |471,339,008–481,583,104B |
| Measured post-idle RSS first /last |294,150,144 /296,546,304B |
| Before requests /after close RSS |242,225,152 /298,418,176B |
| RSS after one-second post-close wait |298,418,176B |
| Raw VmRSS points /median gap /max gap |2919 /22.464ms /34.601ms |

The timed call includes model hash verification, VAD, native decoder construction,
decode, result and release. It excludes sampler join, report persistence and
200ms inter-request idle. Every actual timestamp/value remains in the154,363-byte
raw JSON, SHA256 `fccd037718e284ff49719200ba50d1f5275de93453e286cd83519108e3fc7a26`.
The requested20ms sampling interval is not an observed exact schedule. Thermal
status was0 for all35 rows. No forced GC or cache policy change was used.

The original test APK/Identity runner was restored immediately; ordinary App hash,
25 private model/journal hashes, four original SR files, exact permission strings
and TTS setting matched the preflight snapshot. The two staged test APKs and the
exact report were removed after raw-byte collection, and the ordinary App returned.

## Provenance and remaining limits

Only the206,086-byte test APK was new. Its offline build executed397 tasks; all953
original source files and14 frozen external inputs were rechecked. Six retained
Sherpa production source files match the original ordinary App's recorded source
hashes. `e0438c0` identifies the source used to compile the test APK; it is **not**
asserted as the installed whole App's commit. The installed APK is independently
bound by its full SHA256. This run does not validate the newer Sherpa cleanup
change, which was not installed.

The report reducer checks35 sequential rounds and recomputes nearest-rank
percentiles from only the30 measured requests. The cbc hy3 candidate completed
in54.378s, but11 negative subcases exposed numeric booleans/floats accepted in
integer fields. Parent strict-type corrections pass10 offline test methods.
The P11 adapter additionally rejects changed input, summary/provenance mismatches,
aliases and duplicate sample identities; the combined focused suite passes40 tests.
Full tools-suite and local-review receipts are in the evidence directory.

This adds one Android ASR resource group: P11 now has6 reported capabilities/9
groups, including30 new measured samples. The5 warmups are excluded; previous
groups are unchanged. Fixed synthetic matches are not natural CER/WER. VmRSS is
whole-process sampled RSS, not actual peak, GPU memory, SDK allocation/free
attribution, leak freedom, energy or a sustained production resource budget.
The approximately56MB pre-request-to-post-close increase is an observation, not
a cause assignment. Strong-noise/Chinese quality, actual capture/background
lifecycle, minimum-OS devices and production resource acceptance remain open.
Foundation remains10/12; P4/P11 and release remain `open/not-ready`.

Local ignored evidence: `artifacts/verification/android-asr-resources-20261107/`,
including raw JSON, exact test/source/APK provenance, summary, restore/cleanup
receipts, negative checks and `freeze.py --verify`. The current readiness/grouped
entry is always `benchmark/p11/grouped-report-manifest.json`. The raw fixed PCM
and reports are not committed media. Probe source and its safety boundary are in
[the probe README](../../probes/android-asr-resources/README.md).
