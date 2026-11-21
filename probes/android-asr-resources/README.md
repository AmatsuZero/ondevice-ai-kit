# Android fixed-input ASR resource probe

`AsrResourceInstrumentation.kt` is an operator-only test APK overlay. It does not
open the microphone, save audio/transcripts or initialize a missing model. It
uses the existing pinned English PCM (`fc0f3f5b…39df46`) and verified Whisper
tiny/Silero files, then runs five warmups and30 measured SDK `transcribe` calls.
Every request must match the expected normalized text and preserve caller input.

Elapsed realtime nanoseconds cover model hash verification, VAD, native decoder
construction, decoding, result and release. Sampling/join/persistence overhead
is outside the timed interval. `/proc/self/status` VmRSS is sampled approximately
every20ms; preserve the actual monotonic timestamps. This is sampled whole-process
RSS, not true peak memory, SDK allocation attribution or a resource-budget pass.

Reports are reserved by a unique runId and atomically persisted after every round
and before Binder finish. Keep failed/nonterminal bytes; do not rerun on a host
timeout or USB disconnect. `tools/probes/android_asr_resource_report.py` rejects
incomplete reports, mismatches, invalid timing/sample traces and numeric booleans.
`asr-android-resource` in the P11 reporter recomputes the30 measured values and
binds raw/summary/provenance/input hashes without promoting quality or release.

The completed2026-11-07 run, source/export plan and guarded
`preflight → install → run → collect → restore → cleanup` commands are retained in
`artifacts/verification/android-asr-resources-20261107/`. These are local ignored
receipts, not clone-distributed build scripts. Never rerun completed commands or
reuse its runId. A future run needs its own source/input/APK inventory and fresh
restoration plan. Replace only the test APK; keep a verified original test package
and preserve the ordinary App, private hashes, SR files, permissions and settings.

[Completed measurement and limits](../../docs/validation/android-asr-resources-20261107.md).
