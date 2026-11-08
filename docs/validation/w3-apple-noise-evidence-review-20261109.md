# W3 Apple synthetic-noise evidence review — 2026-11-09

## Code Review Summary

**Scope:** read-only acceptance review of the listed Apple file-only receipt
for the fixed public three-clip synthetic 20 dB fixture. No private image,
identity material, vector/score directory, build, test, device operation, or
source modification was accessed. This review applies the W3 WAV-schema and
independent-decoder cautions in `docs/agent-pitfalls.md:611,617` and preserves
the source/build/device separation in `docs/agent-pitfalls.md:610-617`.

**Files Reviewed:** 12
**Total Issues:** 0

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

### Accepted evidence

- The final terminal receipt has one run ID across exactly three distinct public
  clips. Each row binds the frozen reference and expected SHA to matching input
  and post-close SHA, uses `apple.speech.recognizer`, has finite positive
  elapsed time, and reports completed/drained/closed
  (`artifacts/verification/w3-apple-noisy-file-20261109-0038/w3-file-report.json:1`).
- The bundled manifest’s IDs, references, PCM metadata, and three derived SHA
  values match the canonical fixed-20-dB manifest. It also records fixed seed,
  20 dB target/realized SNR, and zero clipping
  (`artifacts/verification/w3-apple-noisy-file-20261109-0038/AudioFixtures/manifest.json:1-96`,
  `artifacts/verification/w3-controlled-noise-20261108-2300/fixed20db-fixture/manifest.json:1-96`).
- The final parent acceptance receipt passes all 25 bounded checks, including
  exact canonical/bundled fixture comparison, references, post-close integrity,
  build result, source/lock guards, notices, scoped restoration, and exact
  metric recomputation. It explicitly retains `qualityVerdict: unverified`,
  `productionReady: false`, unmeasured network isolation, and an unreported
  Apple model hash
  (`artifacts/verification/w3-apple-noisy-file-20261109-0038/parent-acceptance.json:1-63`).
- The initial report correctly captures the separate awaiting-authorization
  state. It is not treated as a completed recognition or cleanup proof; the
  final terminal receipt above supersedes it for this single run
  (`artifacts/verification/w3-apple-noisy-file-20261109-0038/initial-awaiting-speech-permission-report.json:1-61`).
- The unsigned isolated iPhoneOS build succeeded without source change
  (`artifacts/verification/w3-apple-noisy-file-20261109-0038/build.json:1-21`).
  Current production provenance is established by the four actual speech-source
  SHA-256 digests in `inputs.json`, not by the app’s literal historical
  `sdkSourceCommit` value `6d503dd…`; that literal is historical provenance,
  not a current-HEAD claim
  (`artifacts/verification/w3-apple-noisy-file-20261109-0038/inputs.json:1-27`,
  `W3AppleFileApp.swift:50-52`).
- The case-folded metrics are descriptive only: WER is 4/59 and CER is 13/268;
  that receipt independently labels quality unverified and production not ready
  (`artifacts/verification/w3-apple-noisy-file-20261109-0038/speech-error-metrics-casefold.json:1-85`).
- Restoration is bounded to the new alias and records preservation of ordinary
  app URLs and the combined return path
  (`artifacts/verification/w3-apple-noisy-file-20261109-0038/restoration.json:1-5`).

### Boundaries

This accepts only three public English synthetic-20-dB file recognitions and
their descriptive metrics. `requiresOnDeviceRecognition: true` is evidence of
the chosen recognition mode, but physical network isolation was not measured;
the model version/hash is `NOT_REPORTED`. This review does **not** approve
quality/accuracy, Chinese-language behavior, real-world noise robustness,
microphone behavior, W8 resources, calibration, production readiness, or W3 as
a whole.

### Recommendation

**APPROVE_SLICE** — the narrow Apple public synthetic-file functional receipt
is accepted with the stated evidence boundaries. All broader and unmeasured
gates remain open.
