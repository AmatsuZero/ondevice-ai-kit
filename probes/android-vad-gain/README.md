# Fixed AMI gain VAD diagnostic

Isolated test-only candidate: three existing public AMI PCM clips, each original
amplitude and fixed ×10 (+20 dB), six fresh Silero VAD instances. It does not
create a Whisper recognizer, record/play audio, request permissions or change
production sources/settings. Gain applies only to temporary classifier windows;
the waveform stored by the unchanged `UtteranceBuffer` remains original. This
tests detection sensitivity, not ASR quality or a production gain algorithm.

See [frozen protocol](../../docs/validation/w3-ami-gain-protocol-20261110.md),
[machine protocol](protocol-freeze.json), and
[offline acceptance](../../docs/validation/w3-ami-gain-offline-acceptance-20261110.json).
Current state: **55 host checks and Android build/lint passed; the single physical
six-arm run completed and was restored**. Original detected flags false/false/true
became true/false/true with ×10 gain. The fixed-gain candidate was rejected by the
prospective rule. See [observed result](../../docs/validation/w3-ami-gain-observed-20261110.md).

`VadGainMeasurement.kt` is pure Kotlin with an injected detector. Host checks use
the byte-identical production `UtteranceBuffer.kt`, a host-only exception double
with the production exception's name, and `VadGainMeasurementChecks.kt`. They
exercise endpoints/crop lengths, seven/eight speech windows, silence, gain,
source preservation, invalid inputs/probabilities, compute/release failures and
exact-once release even when the caller-input invariant fails. The first source
failed six case groups; the first correction failed one; accepted source passes
55 assertions across all ten groups.

`VadGainInstrumentation.kt` goes in the isolated androidTest source set. The pure
measurement and existing [foreground Activity](../android-natural-file-asr/ForegroundProbeActivity.kt)
go in its main source set, alongside byte-identical current production sources.
Use the existing natural-file harness build configuration, changing only the
application/test IDs to `io.ondeviceai.acceptance.vadgain` / `.test` and runner
to `io.ondeviceai.demo.VadGainInstrumentation`. Dependencies remain pinned to the
existing local Sherpa Maven runtime `2.0.1` and coroutines `1.10.2`.

The three `.pcm` assets named in the protocol belong in test assets; the single
pinned `silero_vad.onnx` belongs in the alias's
`no_backup/sherpa-whisper-tiny-65176e2d-vad9e2449e1/` directory. No Whisper model
files are needed. The runner validates actual asset bytes and model size/SHA
before native initialization. Start with exact `runId` and `sourceFingerprint`
instrumentation arguments; the parent must bind that fingerprint to actual
source/APK/asset/native-library receipts. The fingerprint is parent-attested,
not self-computed by the app.

Running requires a currently awake, unlocked device and no other active
instrumentation. Reserve a new run ID, persist running before starting, and do
not automatically retry. Reports live at
`files/w3-vad-gain/report-<runId>.json`; expected no-speech is a row, while other
failures stop the batch and retain partial results. A successful terminal report
requires six completed releases, valid public foreground observations and
successful persistence. Double-read the same terminal file, preserve all failed
runs, then restore only this alias/stage and verify ordinary app/model hashes.

The parent-owned build, raw helper responses, original failures, package
verification, host runner and prepared device orchestration remain locally in
`.omx/artifacts/w3-ami-gain-investigation-20261110/`. They are not uploaded as
audio/model artifacts. No quality, false-positive safety, latency or resource
budget approval follows from this probe's offline acceptance.
