## Code Review Summary

**Files Reviewed:** 7
**Total Issues:** 0

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

### Review Scope

This is a source-only rereview of the test-only, opt-in Android foreground
observation candidate. It supersedes only the three source blockers recorded in
the prior review; no compile, package, device, lifecycle, foreground-visibility,
resource, or freezer-root-cause acceptance was performed.

### Evidence and Claim Assessment

- The replacement imports both `Intent` and `TimeUnit`, so it addresses the
  prior unresolved `Intent` reference. The Activity's acknowledgement state is
  test-owned and instance-local; it uses public lifecycle callbacks,
  `CountDownLatch`, `startActivitySync`, `runOnMainSync`, `isFinishing`, and
  `isDestroyed`, not hidden `Activity.isResumed`.
  (`.omx/artifacts/cbc-hy4free-w8-foreground-failclosed-correction-20261109-0732/candidate/runner-replacements.json:1`,
  `.omx/artifacts/cbc-hy4free-w8-foreground-failclosed-correction-20261109-0732/candidate/ForegroundProbeActivity.kt:22-65`)
- The exact `startActivitySync` result is stored immediately before the cast and
  resume acknowledgement wait. A failed start, failed cast, timeout, callback
  error, or unacknowledged resume records sanitized metadata and is rethrown to
  the existing outer catch/finally; the ASR/model/asset work below is skipped.
  (`.omx/artifacts/cbc-hy4free-w8-foreground-failclosed-correction-20261109-0732/candidate/runner-replacements.json:1`,
  `probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:159-170`,
  `probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:344-407`)
- Teardown operates only on the held, exact instance and clears that reference
  in `finally`. A teardown exception or an absence of every recorded
  finish/destroy observation sets `foregroundTeardownFailed`; completion then
  additionally requires that field to be false. Existing primary work and
  backend-cleanup error fields are not overwritten.
  (`.omx/artifacts/cbc-hy4free-w8-foreground-failclosed-correction-20261109-0732/candidate/runner-replacements.json:1`)
- The candidate records `foregroundFinishing` separately from
  `foregroundDestroyed`. A successful `finish()` request or `isFinishing` does
  not assert that `onDestroy` already occurred. The acknowledgement is bounded
  lifecycle observation only; it does not establish continuous visible state,
  focus, freezer/battery success, or a resolved W8 root cause.
  (`.omx/artifacts/cbc-hy4free-w8-foreground-failclosed-correction-20261109-0732/candidate/ForegroundProbeActivity.kt:22-29`,
  `.omx/artifacts/cbc-hy4free-w8-foreground-failclosed-correction-20261109-0732/candidate/runner-replacements.json:1`)
- The `foregroundWindow` gate is absent by default and accepts only exact
  `"true"` when supplied. Its replacement adds no `foreground*` report fields
  to the default path, retaining the existing runner report shape and behavior.
  The manifest declaration remains non-exported and the Activity itself contains
  only a static status view and lifecycle acknowledgements.
  (`.omx/artifacts/cbc-hy4free-w8-foreground-failclosed-correction-20261109-0732/candidate/runner-replacements.json:1`,
  `.omx/artifacts/cbc-hy4free-w8-foreground-failclosed-correction-20261109-0732/candidate/manifest-replacement.json:1`,
  `.omx/artifacts/cbc-hy4free-w8-foreground-failclosed-correction-20261109-0732/candidate/ForegroundProbeActivity.kt:8-20`)

### Root-Cause Guard

The corrected opt-in path does not downgrade failed launch, resume, or teardown
to a report-only warning. It uses a bounded public callback acknowledgement and
terminal failure semantics rather than treating `startActivitySync` as proof of
foreground visibility.

### Validation Limitations

This approval is limited to candidate source semantics. Parent-owned validation
must still confirm exact replacement application, compilation, target-main-APK
manifest placement, runtime context resolution, callback behavior, terminal
receipt behavior, and restoration. It does not show a destroyed Activity,
continuous visibility, physical resource behavior, or an OS freezer fix.

### Recommendation

APPROVE_SLICE

The three prior source blockers are repaired in this bounded candidate. Runtime
approval remains separate.
