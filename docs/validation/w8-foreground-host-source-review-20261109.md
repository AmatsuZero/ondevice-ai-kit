## Code Review Summary

**Files Reviewed:** 7
**Total Issues:** 3

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 3 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

### Review Boundary

This is a source-only review of the proposed test-only, opt-in foreground
Activity for the isolated natural-file ASR harness. No build, test, device,
installation, runtime observation, or freezer diagnosis was performed. The
candidate must not be represented as a fix for the OS freezer issue or as
device/resource acceptance evidence.

### Issues

[HIGH] Missing `Intent` import prevents compilation

File: `.omx/artifacts/cbc-hy3-w8-foreground-host-20261109-0710/candidate/runner-replacements.json:1`

The replacement adds `import android.app.Activity` but constructs `Intent` and
uses `Intent.FLAG_ACTIVITY_NEW_TASK`; neither the current runner imports nor its
package defines `Intent`. Kotlin does not implicitly import `android.content`.
The integrated runner therefore has an unresolved reference before its behavior
can be evaluated.

Fix: add `import android.content.Intent` to the exact import replacement, then
perform the parent-owned isolated compile/package check.

[HIGH] Resume assertion relies on a non-public SDK API

File: `.omx/artifacts/cbc-hy3-w8-foreground-host-20261109-0710/candidate/runner-replacements.json:1`

The proposed `started.isResumed` call does not compile against the harness's
`compileSdk = 36` public API surface. The platform source labels
`Activity.isResumed()` `@SystemApi`, and the method name is absent from the
Android 36 public `android.jar` Activity class, while the public
`isFinishing`/`isDestroyed` methods are present. The candidate's claim that it
uses a plain public getter is therefore incorrect.

Fix: replace `isResumed` with a test-owned, public-SDK-compatible resume
acknowledgement from `ForegroundProbeActivity` (with a bounded observation
mechanism), and treat failure to obtain that acknowledgement as a failed
foreground precondition. Do not use hidden/system APIs or weaken the claim to
`startActivitySync` return alone.

[HIGH] Foreground lifecycle failures are recorded but do not fail closed

File: `.omx/artifacts/cbc-hy3-w8-foreground-host-20261109-0710/candidate/runner-replacements.json:1`

Both the startup block and teardown block catch every exception, append
`foreground*Failed` metadata, and then continue. A false resume observation,
a start failure, a finish failure, or a false
`foregroundDestroyedOrFinishing` result can still reach the existing completed
status path when ASR work and backend close succeed. This contradicts the
required opt-in proof boundary: the report would claim completed ASR work
without successful foreground setup/cleanup.

Fix: when `foregroundWindow=true`, make unsuccessful start/resume acknowledgement
and unsuccessful teardown terminal precondition failures: record the sanitized
error, skip inference after failed startup, and force final status to `failed`
after failed teardown. Preserve the no-argument default path unchanged.

### Evidence Reviewed

- The current runner has no foreground behavior. Its current resource/locale
  gates reject invalid explicit arguments before reserving the report path, and
  its completed status depends on completed work plus a successful backend
  close. (`probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:76-155`,
  `probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:344-407`)
- The existing isolated harness compiles with Android public API level 36;
  `main-src` and `test-src` are separate source sets. The Activity must be
  integrated into `main-src` and the manifest replacement into the target APK,
  not merely added to the test APK. (`artifacts/verification/w3-locale-gate-20261109-0118/source/build.gradle.kts:5-35`,
  `artifacts/verification/w3-locale-gate-20261109-0118/source/AndroidManifest.xml:1-5`)
- `startActivitySync` and `runOnMainSync` are public Instrumentation APIs, and
  `isFinishing`/`isDestroyed` are public Activity APIs. The specific
  `isResumed` method is marked `@SystemApi` in local platform source and is not
  contained in the public Android 36 `Activity.class`; it cannot be substituted
  by a guess about framework visibility.
  (`/Users/daubert/Library/Android/sdk/sources/android-37.0/android/app/Instrumentation.java:486-493`,
  `/Users/daubert/Library/Android/sdk/sources/android-37.0/android/app/Instrumentation.java:563-602`,
  `/Users/daubert/Library/Android/sdk/sources/android-37.0/android/app/Activity.java:7501-7510`,
  `/Users/daubert/Library/Android/sdk/sources/android-37.0/android/app/Activity.java:9703-9707`)
- The proposed Activity remains otherwise appropriately bounded: non-exported
  manifest declaration, static `TextView`, and no inference/capture/service or
  global state behavior. (`.omx/artifacts/cbc-hy3-w8-foreground-host-20261109-0710/candidate/ForegroundProbeActivity.kt:7-31`,
  `.omx/artifacts/cbc-hy3-w8-foreground-host-20261109-0710/candidate/manifest-replacement.json:1`)

### Root-Cause Guard

Do not turn lifecycle failures into report-only diagnostics while allowing the
main workload to complete. The minimal repair is an explicit public-SDK resume
acknowledgement and terminal failure semantics for the requested foreground
mode, not a fallback that treats an Activity launch attempt as proof of
visibility.

### Recommendation

REQUEST CHANGES

The opt-in default-preservation intent is sound, but all three HIGH issues must
be repaired and parent-verified by compile/package and runtime acceptance before
the candidate can be approved. No conclusion is drawn here about the freezer
root cause or physical resource acceptance.
