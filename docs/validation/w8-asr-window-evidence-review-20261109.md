# W8 ASR resource-window evidence review — 2026-11-09

## Code Review Summary

**Scope:** acceptance evidence for the Android **emulator** ASR runner/window
only, plus classification of the separate physical-device interruption. No
build, test, device operation, private data access, or source modification was
performed in this review.

**Files Reviewed:** 11
**Total Issues:** 0

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

### Accepted emulator evidence

- The retained terminal report records one completed emulator run with three
  clips and four completed requests, zero microphone starts, unchanged caller
  inputs, successful final close, and the resource window enabled
  (`artifacts/verification/w8-emulator-asr-20261109-0015/device-report.json:1`).
- The bind plus successful-request holds total 45,007 ms. Each segment records
  requested/actual holds separately, while segment `durationNs` remains bounded
  by its own transcribe timestamps. Rows explicitly declare that their wall-clock
  duration can include a resource hold
  (`artifacts/verification/w8-emulator-asr-20261109-0015/device-report.json:1`).
- The external observer completed its 30-second interval collection against the
  same PID (7049) and retained 28 samples; it reports raw RSS, thread count, and
  CPU ticks only, rather than synthesizing a performance metric
  (`artifacts/verification/w8-emulator-asr-20261109-0015/resource-observation.json:1`).
- Parent acceptance cross-checks completion, 4/3 request-row cardinality,
  close, zero microphone use, requested hold, non-interruption, row disclosure,
  model preservation, observer identity/duration/samples, and keeps peak GPU,
  energy, and budget acceptance false
  (`artifacts/verification/w8-emulator-asr-20261109-0015/parent-acceptance.json:1-31`).
- The host result is successful and the observer wrapper records one inference
  invocation with no automatic replay
  (`artifacts/verification/w8-emulator-asr-20261109-0015/host-result.json:1-4`,
  `artifacts/verification/w8-emulator-asr-20261109-0015/observed-host-result.json:1-5`).
- Cleanup removed only the two newly installed aliases and staging path while
  retaining the terminal report and preserving ordinary app paths and protected
  source/lock state
  (`artifacts/verification/w8-emulator-asr-20261109-0015/restoration.json:1-9`).

### Physical-device attempt classification

The physical attempt is correctly excluded from acceptance. Its restoration
receipt says every thread froze before model work, records no proven SDK close,
retains the USB host-255 interruption separately, confirms no replay, and shows
only disposable aliases/staging were removed
(`artifacts/verification/w8-physical-asr-20261108-2345/frozen-attempt-restoration.json:1-17`).
The pre-thaw observation’s flat process samples and the narrowly scoped
single-PID thaw record are diagnostic evidence only, not normal ASR completion
or resource acceptance
(`resource-observation.json:1`, `exact-process-thaw.json:1-9`).

### Boundaries

This approves runner/window mechanics on the emulator only. The observer samples
are not phase-attributed continuous telemetry and do not prove peak resource use,
GPU behavior, energy, sustained throughput, budget compliance, physical-device
behavior, production accuracy, or W8 completion. The explicit idle holds mean
this is not a pure-continuous-inference measurement.

### Recommendation

**APPROVE_SLICE** — emulator runner/window function and its limited cleanup
evidence are accepted. The physical attempt remains rejected for acceptance;
all broader W8 gates remain open.
