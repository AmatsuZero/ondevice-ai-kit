## Code Review Summary

**Files Reviewed:** 21
**Total Issues:** 0

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

### Review Boundary

This is a read-only review of public source and parent-produced acceptance
receipts. It does not independently operate a device, rerun a build/parser, or
inspect private/user/employee data or microphone raw media. Receipt-backed
facts below are not independent device auditing by this reviewer.

### W8 Physical Foreground/Resource Observation

**Slice verdict: APPROVE_SLICE**

- The integrated Android source keeps the foreground Activity opt-in, starts it
  before asset/model work, requires a public lifecycle acknowledgement, and
  fails closed on unsuccessful start/resume. It separates `foregroundFinishing`
  from `foregroundDestroyed` and refuses completion after foreground teardown
  failure. (`probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:177-233`,
  `probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:409-430`,
  `probes/android-natural-file-asr/ForegroundProbeActivity.kt:22-65`)
- The build, integration, target-manifest, and parent receipts collectively
  report offline APK builds, exact candidate-block integration, target-main-APK
  placement, non-exported Activity, no record-audio permission, and the passed
  four-request/three-row/close/mic-zero functional checks. This accepts that
  bounded evidence chain, not a new production-source change.
  (`artifacts/verification/w8-foreground-physical-20261109-0810/build-result.json:1`,
  `artifacts/verification/w8-foreground-physical-20261109-0810/integration.json:1`,
  `artifacts/verification/w8-foreground-physical-20261109-0810/target-manifest-parent.json:1`,
  `artifacts/verification/w8-foreground-physical-20261109-0810/parent-acceptance.json:1`)
- The observer receipt binds 24 samples to PID `29744` and starttime
  `204721376`, across configured 30 seconds and actual offsets 0.206–28.945
  seconds. The parent summary records sampled RSS 223408128–411172864 bytes,
  36–45 threads, raw CPU-tick delta 1273, explicit 45003 ms idle holds, and
  four-request inference-duration sum 9.022632847 seconds. These are sampled
  process observations, not continuous peaks, normalized CPU, or a budget.
  (`artifacts/verification/w8-foreground-physical-20261109-0810/resource-observation.json:1`,
  `artifacts/verification/w8-foreground-physical-20261109-0810/foreground-resource-parent.json:1`)
- The opt-in Activity has resume/start/finish evidence, while its final receipt
  explicitly retains `foregroundDestroyed=false`. The separate `topResumed`
  evidence is one captured snapshot only. It supports a bounded activity-state
  observation; it does not establish completed destruction or continuous
  visibility.
  (`artifacts/verification/w8-foreground-physical-20261109-0810/device-report.json:1`,
  `artifacts/verification/w8-foreground-physical-20261109-0810/during-activity-parent.json:1`)
- The restoration receipt reports removal only of the two new aliases and exact
  stage while retaining the device report and preserving ordinary app paths and
  protected sources/lock. This is a parent receipt claim, not independently
  re-executed cleanup validation here.
  (`artifacts/verification/w8-foreground-physical-20261109-0810/restoration.json:1`)

This slice does not prove a unique OS-freezer root cause, GPU allocation or
reclaim, energy/sustained-resource budgets, leak freedom, all lifecycle paths,
Chinese/real-world-noise quality, or W8 closure. The run documentation preserves
those limits. (`docs/validation/w8-physical-asr-observation-20261109.md:18-39`)

### W3 Apple Explicit-Locale Prerequisite

**Slice verdict: APPROVE_SLICE**

- The integrated source resolves an exact optional locale flag, reports a
  malformed value through the bounded failure path before backend/client/auth
  work, and directs a valid selected value to both backend and request. The
  parser itself permits only exact `en-US` and `zh-CN` and ignores unrelated
  arguments. (`probes/ios-natural-file-asr/W3AppleFileApp.swift:62-153`,
  `probes/ios-natural-file-asr/W3AppleFileApp.swift:331-366`)
- Receipts record exact seven-block integration, unchanged production speech
  sources/fixture identities, an unsigned iPhoneOS build completing in 10.251
  seconds, and 12 host-run exact-parser cases. The parser host isolates only
  that pure method and removes private visibility for the wrapper; it is not an
  App, authorization, backend, or device run.
  (`artifacts/verification/w3-apple-locale-20261109-0814/integration.json:1`,
  `artifacts/verification/w3-apple-locale-20261109-0814/build-result.json:1`,
  `artifacts/verification/w3-apple-locale-20261109-0814/locale-parser-source.json:1`,
  `artifacts/verification/w3-apple-locale-20261109-0814/locale-parser-result.json:1`)
- The parent acceptance receipt and public prerequisite document explicitly
  leave App runtime, persisted failure-report behavior, authorization, valid
  locale device paths, Chinese input, Chinese quality, and W3 closure
  unverified. The `zh-CN` argument is a prerequisite control, not a Chinese
  fixture or quality result.
  (`artifacts/verification/w3-apple-locale-20261109-0814/parent-acceptance.json:1`,
  `docs/validation/w3-apple-locale-prerequisite-20261109.md:17-32`)

### Root-Cause and Reporting Guard

Neither slice turns an incomplete primary claim into a fallback success: the
Android candidate records only bounded lifecycle/sample observations, and the
Apple candidate separates parser/build evidence from App runtime evidence.
Dynamic process/activity observations and host-parser execution must retain
their distinct scopes.

### Recommendation

APPROVE_SLICE

The W8 physical observation and W3 Apple locale prerequisite evidence are
accepted only within the documented boundaries above. They do not constitute
full W8 or W3 acceptance.
