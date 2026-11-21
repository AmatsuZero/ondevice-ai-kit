# Apple file ASR sustained resource probe

This isolated probe retains the exact Swift candidate used for the 2026-11-10
physical file-only resource observation. It uses `AppleSpeechRecognizerBackend`
and `SpeechRecognitionClient`, with fresh instances in each of 20 cycles, two
measured recognitions per cycle and one excluded warmup. Each cycle lasts at least
30 seconds, including measured pacing idle. Recognition uses the pinned three
public LibriSpeech files, en-US and the on-device requirement.

The SwiftUI setup button requests app-specific Speech authorization before the
batch. It never starts recognition or requests a microphone. The original source
comments saying no authorization requests refer to the unchanged measured workload:
`run()` still requires an existing authorization and never requests it. Run remains
disabled until authorization is granted. The probe is not the ordinary Demo App.

`project.yml` uses the repository's Apple package and reuses the exact resources in
[`../ios-natural-file-asr/AudioFixtures`](../ios-natural-file-asr/AudioFixtures),
including its source/license notice. No audio or model is duplicated here. Use
XcodeGen to create the local project; generated project/build output is disposable.
The project deliberately builds unsigned, with an explicit Info.plist. Existing
approved signing and device operations remain separate execution steps.

The checked-in source-identity manifest, Info.plist identity and input protocol
belong to the frozen observation. Before any later install, verify every current
source/input hash against that manifest or generate and record a new identity.
Historical absolute input paths in the protocol identify the original freeze;
the clip IDs, byte counts, SHA-256 pins and bundled WAV bytes bind the workload.
Compilation alone does not validate this identity or establish runtime acceptance.

Before a device run: verify the actual bundle contains the expected source identity,
Speech usage description and the full AudioFixtures tree; preserve the original
App inventory, acquire the device slot and use a distinct bundle. Prepare Speech
authorization separately, then run only the approved batch. Never replay a batch
to repair missing observation. Recover the same report twice, verify its run/PID,
then restore only the added alias and return to the ordinary App.

RSS is sampled during recognition at about 10 ms. Counts/min/max are exact for the
observed samples; bounded-reservoir quantiles are approximate. Per-cycle close RSS
and final process RSS are separate. Pacing idle is included in elapsed time but
is not continuously sampled. RSS belongs to the probe App PID including its UI;
system Speech-service memory, true peak, GPU, energy and production budgets are
not measured or approved. Public transcripts are retained in the local report.

The completed observation and restoration are recorded in
[`accepted-observation.json`](accepted-observation.json) and the
[bounded acceptance report](../../docs/validation/w8-apple-asr-sustained-20261110.md).
The source archive was verified against the physically built source byte-for-byte;
its project changes only package/resource paths to reuse the same repository files.
