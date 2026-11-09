# Paced Sherpa create/transcribe/close observation

This separate instrumentation harness uses current production `SherpaAsrBackend`,
`SherpaWhisperModel`, VAD and decoder sources without modifying them. It observes
twenty fresh backend instances, two fixed-file calls per instance and one initial
warm-up call. Each cycle lasts at least thirty monotonic seconds; the twenty-cycle
window lasts at least ten minutes **including pacing and close waits**. It is not
continuous speech, microphone testing or representative ASR quality acceptance.

Use the existing `ForegroundProbeActivity` in the target main APK, declared as
non-exported. Include both `SustainedAsrInstrumentation.kt` and
`SustainedRunContract.kt` in the test APK. The parent owns building, signing,
installation, source/model/PCM/APK hash binding and final acceptance. Use a fresh
application/test alias; never replace the ordinary App.

The only input is existing public synthetic `english.pcm`, SHA-256
`fc0f3f5bb9b06a4c21e12321d5fb0bc7d3e3151dca855e5ed9693907ce39df46`.
The parent stages the four already-frozen model files in the new alias's
`no_backup/sherpa-whisper-tiny-65176e2d-vad9e2449e1`. Their metadata is still checked
by the production model verifier. No new download, microphone permission, audio
or transcript persistence is needed. The transcript is compared only to the
existing synthetic phrase, recording a boolean rather than its text.

Launch once with instrumentation arguments `runId`, `sourceFingerprint` and
`installedAppSha256`. The last two are parent-attested lowercase 64-hex digests;
they are not internally reconstructed provenance. Reports go to
`files/odai-asr-sustained-reports/<runId>.json`. Existing files, AtomicFile siblings,
symlinks and directory escapes are rejected before model work. Do not restart a
run merely because a checkpoint has not appeared.

Checkpoints retain partial request/cycle counts before waiting and after every
full cycle. Final output includes the same PID, input digest, source/APK binding,
twenty complete cycles, forty measured attempts/successes, one warmup, unchanged
caller input, twenty successful close paths, foreground lifecycle health,
sampler join, and a durable terminal status. Sampler or persistence failures must
fail the run. Atomic final-write failure cannot produce Binder success.

RSS is `/proc/self/status` VmRSS, sampled every twenty milliseconds for this
process, including instrumentation/UI. It is not true peak or GPU memory. Cycle
close-immediate and close+at-least-one-second RSS are distinct from final-process
RSS; pacing idle and actual close waits are recorded separately. Transcribe wall
timings include production verification/VAD/native decode/release and result
postconditions. The first paced cycle includes the one initial warmup.

The exact Activity instance must acknowledge resume, never pause/destroy before
teardown, and be finished on every exit. Only that window's KEEP_SCREEN_ON flag
is added and its prior bit restored; no global setting or wakelock is changed.
The parent must read the exact terminal report twice/hash-bind it, verify model
and ordinary-App preservation, then remove only the new aliases and exact stage.

`SustainedContractChecks.kt` exercises the real completion predicate with valid,
partial, overlapping, short, failed-close, mutated-input, missing-warmup/count,
lifecycle, sampler, teardown and persistence cases. Host checks and compilation
are prerequisites, not physical acceptance. No result here approves production
thresholds, GPU/energy, zero leaks or a resource budget.
