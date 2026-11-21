# W3 noisy-fixture runtime evidence review — 2026-11-09

## Code Review Summary

**Scope:** final evidence review for the fixed public three-clip, deterministic
20 dB synthetic fixture; the bounded test-import correction; and one Android
functional run. No command, test, device operation, implementation, or private
data access was performed in this review.

**Files Reviewed:** 9
**Total Issues:** 0

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

### Accepted evidence

- The final fixture correction test receipt exited 0, and default unittest
  discovery records all 40 tests passing. This includes the independent stdlib
  WAV-reader/header checks, deterministic output, SNR/clipping checks, strict
  malformed-input rejection, and source/destination preservation cases
  (`artifacts/verification/w3-controlled-noise-20261108-2300/final-correction-tests.json:1-6`,
  `artifacts/verification/w3-controlled-noise-20261108-2300/default-discovery-tests.log:1-123`).
- The parent’s actual public-fixture acceptance receipt confirms three
  deterministic derived files, `sampleWidthBytes: 2`, exact source-notice
  preservation, unchanged originals, independent stdlib validation, near-exact
  20 dB SNR, and zero clipping. It explicitly left ASR unrun at that stage
  (`artifacts/verification/w3-controlled-noise-20261108-2300/real-fixture-parent-acceptance.json:1-41`).
- The integrated test file contains only the local tools-root import-path setup
  and import move to `probes.prepare_noisy_fixture`; the supplied replacement
  receipt matches that narrow change, and the now-default discovery result
  above validates it (`tools/tests/test_prepare_noisy_fixture.py:28-45`,
  `.omx/artifacts/cbc-hy3-w3-test-import-20261109-0035/candidate/replacement.json:1-31`).
- One emulator run completed with three rows/four requests, zero microphone
  starts, unchanged caller inputs, and successful final close
  (`artifacts/verification/w3-noisy-asr-20261109-0030/device-report.json:1`).
  The parent receipt binds those checks to the current protected source and
  unchanged models, keeps peak/GPU/energy/budget acceptance false, and states
  no production-accuracy threshold
  (`artifacts/verification/w3-noisy-asr-20261109-0030/parent-acceptance.json:1-27`).
- Case-folded Unicode-whitespace metrics are explicitly descriptive: aggregate
  WER is 14/59 and CER is 28/268; the same report declares quality unverified
  and `productionReady: false`
  (`artifacts/verification/w3-noisy-asr-20261109-0030/speech-error-metrics-casefold.json:1-85`).
- Restoration removed only the newly created test aliases/stage and retained the
  device report while preserving ordinary paths and protected source/lock state
  (`artifacts/verification/w3-noisy-asr-20261109-0030/restoration.json:1-9`).

### Boundaries

This accepts only deterministic synthetic-English fixture integrity and one
Android functional/descriptive-metrics slice. It does **not** establish a
quality pass, production accuracy, Chinese-language behavior, microphone use,
real-world-noise robustness, Apple noisy-fixture behavior, calibration, or
publication/release approval.

### Recommendation

**APPROVE_SLICE** — the bounded fixture, test-import correction, and one noisy
Android functional observation are accepted with the stated limits. Apple noisy
validation and all broader quality/production gates remain open.
