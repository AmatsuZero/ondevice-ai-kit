# W3 Apple natural local-file ASR — bounded acceptance

## Verdict

**PASS — only the bounded Apple local file-ASR sub-item is closable.**  The
record proves that an isolated, signed alias app ran three supplied public
English WAV files on the real iPhone through the current production Apple
Speech file-recognition path, with on-device recognition required and input
integrity checked after `drain()` and `close()`.  It does not close W3 as a
whole and is not production, release, quality, microphone, or network-
isolation acceptance.

## Claim and acceptance boundary

The claim accepted here is deliberately finite:

1. Build and run an isolated iPhone app that imports the production
   `ODAIKitSpeech` and `ODAIKitAppleSpeech` packages rather than recreating
   their APIs.
2. Transcribe the three frozen, public LibriSpeech-derived WAV files with the
   explicit `apple.speech.recognizer` backend and required on-device mode.
3. Complete client drain/close and demonstrate that each input has the same
   SHA-256 after close.
4. Leave the ordinary app inventory and protected production inputs intact.

## Evidence

- `artifacts/verification/w3-apple-natural-file-20261108-2013/build.json` —
  `xcodebuild build` exited 0 in **12.701 s**, with
  `CODE_SIGNING_ALLOWED=NO`, disabled automatic package resolution / skipped
  package updates, and `helperSourceUnmodified: true`.
- `.../W3AppleFileApp.swift` and `project.yml` — the isolated alias
  `io.ondeviceai.probe.w3natural20261108` imports production
  `ODAIKitCore`, `ODAIKitSpeech`, and `ODAIKitAppleSpeech`; it explicitly
  selects `AppleSpeechRecognizerBackend` through `SpeechRecognitionClient`.
  It contains no microphone or `AVAudioSession` use, requires availability to
  be both `.available` and `offline == true` before every request, invokes
  `client.drain()` then `client.close()`, and SHA-256-checks every WAV after
  cleanup.
- Current source corroborates the actual SDK distinction: in
  `apple/Sources/ODAIKitAppleSpeech/AppleSpeechRecognizerBackend.swift`, the
  file path creates `SFSpeechURLRecognitionRequest`, sets
  `requiresOnDeviceRecognition`, and the production backend passes `true` for
  every file request.  In `apple/Sources/ODAIKitSpeech/SpeechClient.swift`,
  `drain()` waits on backend drains and `close()` closes the backend registry.
  This is a production URL/file-ASR path, not the microphone bridge.
- `.../inputs.json` plus a fresh
  `git diff --quiet 6d503dd9e389f3c2b8ef56be4d613f77fd0cddbe HEAD --` over the
  four recorded Apple speech source files — no source difference since the
  reported SDK commit.  Fresh SHA-256 values of those four files match the
  receipt's `speechSources` mapping.  `inputs.json` preserves its historical
  `sourceCommit` of `230e981b10c78b9a518b097d280167ba8f60fcd2`, while its
  separately recorded `headAtBuild` is
  `d2306eb0b52c7a546155b6e023ade8ad09364ed6`; neither old field is asserted
  to be the current HEAD.  The relevant result is instead the empty four-file
  diff from reported SDK commit `6d503dd9e389f3c2b8ef56be4d613f77fd0cddbe`.
- `.../install-result.json`, `launch-result.json`, and `copy-report-result.json`
  — each device operation exited 0: install the signed alias, launch it on the
  device, and copy `Documents/w3-file-report.json` back from that alias.
  `verify-signature-result.json` additionally records successful strict deep
  signature verification.  `signed-receipt.json` records the alias/profile
  receipt, `fixtureHashesMatch: true`, `protectedFilesMatch: true`, and
  `installed: false` at the signing-preparation observation; this preserves
  the existing signing boundary rather than changing signing assets.  The
  later teardown proof is the `apps-after` inventory together with
  `parent-acceptance.json`'s `temporaryAppRemoved: true`.
- `.../w3-file-report.json` — all three clips completed with nonempty
  transcripts through `apple.speech.recognizer`,
  `requiresOnDeviceRecognition: true`, `cleanup.drained: true`,
  `cleanup.closed: true`, and each post-close input SHA-256 matching its
  frozen manifest hash.  `AudioFixtures/manifest.json` and
  `SOURCE-NOTICE.txt` bind the inputs to
  `hf-internal-testing/librispeech_asr_dummy`, `clean/validation`, revision
  `5be91486e11a2d616f4ec5db8d3fd248585ac07a`, CC BY 4.0.
- `.../speech-error-metrics-casefold.json` — descriptive casefolded,
  punctuation-preserving scores are 5/59 WER (0.0847457627) and 13/268 CER
  (0.0485074627), explicitly carrying `qualityVerdict: "unverified"`.
- `.../parent-acceptance.json` — records three completed clips, no measured
  physical network isolation, preserved production lock and nine protected
  paths, removed temporary alias, and restored ordinary app inventory.
  Independently, the recorded before/after app lists each contain 44 bundle
  identifiers, have the same sorted-list SHA-256
  `29808a53a2e4dd409edbbce8897af80db1ccccf01cbb9b21fb7460b20b139d8e`,
  and neither contains the temporary alias.
- [`canonical-integration.json`](../../artifacts/verification/w3-apple-natural-file-20261108-2013/canonical-integration.json)
  — records the subsequent byte-for-byte canonical archival of the verified
  source, project, public manifest/notice, and WAV fixtures under
  [`probes/ios-natural-file-asr`](../../probes/ios-natural-file-asr), including
  the same Swift source hash used by the iPhone receipt.  The canonical
  [`project.yml`](../../probes/ios-natural-file-asr/project.yml) changes only
  its local package reference to `../../apple`; the canonical Swift source is
  [`W3AppleFileApp.swift`](../../probes/ios-natural-file-asr/W3AppleFileApp.swift).
- [`canonical-build.json`](../../artifacts/verification/w3-apple-natural-file-20261108-2013/canonical-build.json)
  — the independently archived canonical path then completed its unsigned
  Xcode build with exit 0 in **16.238 s**.  This validates that canonical
  source path without rerunning or replacing the original iPhone acceptance.

## Explicit gaps and non-claims

- The verified source is now archived at
  [`probes/ios-natural-file-asr`](../../probes/ios-natural-file-asr), but the
  device evidence above predates that archival.  Its independent unsigned
  canonical-path build has passed, but the original iPhone acceptance was not
  rerun; do not attribute that device run to the later canonical path.
- `networkBehavior` is `not measured`; `physicalNetworkIsolationMeasured` is
  false.  Required on-device recognition and the backend's reported offline
  capability are not a measured physical network-isolation result.
- Apple does not report a System Speech model source revision or hash here:
  both report fields are `NOT_REPORTED`.
- Three public, one-speaker English clips are not a broad quality, language,
  device-matrix, latency, reliability, or calibration evaluation.  The WER/CER
  values are descriptive only, and the receipt explicitly says
  `productionReady: false`.
- This is file-only ASR.  It proves neither microphone permission/capture,
  natural live interaction, nor broad W3 lifecycle/release acceptance.

## Reproduction checks performed for this audit

```sh
rtk git diff --quiet 6d503dd9e389f3c2b8ef56be4d613f77fd0cddbe HEAD -- \
  apple/Sources/ODAIKitAppleSpeech/AppleSpeechMicrophone.swift \
  apple/Sources/ODAIKitAppleSpeech/AppleSpeechRecognizerBackend.swift \
  apple/Sources/ODAIKitAppleSpeech/AppleSpeechAudioSession.swift \
  apple/Sources/ODAIKitAppleSpeech/AppleAVSpeechBackend.swift
rtk shasum -a 256 apple/Sources/ODAIKitAppleSpeech/{AppleSpeechMicrophone,AppleSpeechRecognizerBackend,AppleSpeechAudioSession,AppleAVSpeechBackend}.swift
rtk git diff --check -- docs/validation/w3-apple-natural-file-20261108.md
```

The first command exits 0; the source SHA-256 values match `inputs.json`.
The last command is the document integrity gate for this bounded review.
