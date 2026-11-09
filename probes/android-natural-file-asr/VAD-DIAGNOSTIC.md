# W3 isolated VAD boundary diagnostic

This opt-in probe measures the existing production gate without changing its
classification, padding, chunking, models, locale or scoring. It does not enable
a production policy or establish Chinese recognition quality. Do not apply the
replacement plan to production files in place.

`vad-diagnostic-replacements.json` contains two exact old/new blocks. Copy the
nine production sources into a new isolated build, require each old block to
occur exactly once, apply the replacements only there, and verify that reversing
them reproduces the original source bytes. Record original and isolated hashes,
the actual source fingerprint, model/input hashes and APK hashes.

The unchanged buffer may stop inside a 1,600-sample offered chunk after one
second without detected speech. Caller offset plus chunk length cannot prove
actual consumption. The diagnostic snapshot reads existing counters only; it
never invokes the classifier. Successful prepare logs one `ODAI_W3_VAD` JSON
with input/consumed sample counts, first/last speech positions, speech count,
prepared start/end/length and whether input remained unconsumed. Offsets are
relative to that request slice; add the unchanged caller slice offset to express
clip-global bounds. Cropping alone does not prove that omitted audio was speech
or caused a recognition error.

`UtteranceBufferDiagnosticChecks.kt` is a plain Kotlin/JVM check entry point.
Compile it in the same package with the isolated buffer and the existing
`SpeechNotDetectedException`. Run with `diagnostics`. It covers partial chunks,
padding, full160000 bounds, ShortArray input, no-speech/clear, unchanged output
and classifier-call count. The baseline run without the argument passes13
checks; the original source with `diagnostics` compiles then fails because the
snapshot is absent; the isolated diagnostic source passes37 checks. No new
dependency or downloaded toolchain is needed.

Parent evidence: `artifacts/verification/w3-vad-durable-diagnostic-20261109-0945/`.
The first log-only run completed recognition but supplied no diagnostic logs in
three retained queries; its raw files remain in the earlier `w3-vad-diagnostic-
20261109-0920/` directory. The cause of absent logs is unresolved. The retained
plan now adds an alias-owned `w3-vad-observations.jsonl` append + fsync before
returning prepared audio, while preserving optional numeric logcat output.
Diagnostic I/O is inside transcribe duration: do not use it as a performance run.

Require the durable output file to be ABSENT before invoking a new unique
batch. Collect the same-run report and numeric JSONL, verify both file hashes,
require exactly five observations, bind their sequential request slices and
check every bound. A missing/corrupt file is an observation failure; preserve it
and do not silently replay the same runId. Restore only new aliases/exact stage.

The durable protocol built in14.586s and completed one physical TECNO/API35
batch. All25 parent checks passed, five observations were retained and aliases/
stage restored. Three requests stopped before input end. Prepared ranges alone
cannot prove removed speech or root cause; unchanged primary CER remains44/92.
See `docs/validation/w3-vad-diagnostic-parent-20261109.md` for the actual ranges.
A distinct frozen no-VAD control may test that causal hypothesis without changing
models, PCM, slices, locale or scoring. No production patch/quality pass is implied.
No private employee media is used.
