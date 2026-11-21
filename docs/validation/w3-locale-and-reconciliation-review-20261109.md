## Code Review Summary

**Files Reviewed:** 21
**Total Issues:** 0

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

### Review Scope

This is a public source and acceptance-receipt semantic review for the W3
locale/reporting and evidence-reconciliation slice. It does not independently
rerun the Android workload or inspect private media, names, scores, raw
microphone recordings, or private transcripts. It does not approve Chinese
quality, all interruption cases, PAD, resource budgets, or W3 as a whole.

### Evidence and Claim Assessment

- The current default path retains exactly three fixed public English clips.
  Their sample counts and the 160000-sample bound require one request for each
  of the first two clips and two for the third. The supplied default receipt
  records the resulting three rows/four completed requests, terminal completion,
  successful close, and zero microphone starts. (`probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:51-65`,
  `probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:246-343`,
  `artifacts/verification/w3-locale-gate-20261109-0118/parent-acceptance.json:1`)
- With no locale argument, the source selects `en-US` but emits no `locale`
  field; likewise it emits no resource-window field when that option is absent.
  The default-shape receipt confirms both absences and explicitly does not
  compare dynamic fields byte-for-byte across runs. This correctly limits
  “byte-equivalent” to default behavior/report shape, not run ID, process ID,
  timing, or other dynamic bytes. (`probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:98-109`,
  `probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:140-155`,
  `artifacts/verification/w3-locale-gate-20261109-0118/default-shape-parent.json:1`)
- `fr-FR`, `En-US`, and the empty value each have a retained public crash entry
  whose cause is the exact `IllegalArgumentException` at
  `NaturalAsrInstrumentation.onCreate` line 107. The corresponding receipt
  records an absent report, no model work in the rejected path, and explains
  that the host `am` exit code was zero despite the crash. The outcome is thus a
  typed pre-reservation rejection, not a false pass inferred from process exit.
  (`probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:98-109`,
  `artifacts/verification/w3-locale-gate-20261109-0118/invalid-locale-0-exact-error.log:1-17`,
  `artifacts/verification/w3-locale-gate-20261109-0118/invalid-locale-1-exact-error.log:1-17`,
  `artifacts/verification/w3-locale-gate-20261109-0118/invalid-locale-2-exact-error.log:1-17`,
  `artifacts/verification/w3-locale-gate-20261109-0118/invalid-locales-parent.json:1`)
- The prelaunch `verification.json` failure occurred before run reservation or
  instrumentation launch, and the receipt preserves it as such. The later
  parent-reported restoration removes only the two new aliases and exact stage,
  leaves ordinary app paths and protected sources/Apple lock unchanged, and
  retains the public device report. These are receipt-bound restoration claims,
  not an independent device audit by this reviewer.
  (`artifacts/verification/w3-locale-gate-20261109-0118/prelaunch-host-error.json:1`,
  `artifacts/verification/w3-locale-gate-20261109-0118/restoration.json:1`,
  `artifacts/verification/w3-locale-gate-20261109-0118/task-cleanup.json:1`)
- The reconciliation receipt records all seven specified Apple sources as
  byte-identical to the accepted `36567f2` and `c245d12` baselines. The older
  diagnostic copy differs only in the two stated files, and the retained diff
  shows additions of `f04CloseLog` calls/function writing diagnostic stderr,
  not a behavioral-source comparison. This supports historical evidence
  continuity only; it does not make the instrumented binary production or
  establish every lifecycle, authorization, TTS, accuracy, or isolation case.
  (`artifacts/verification/w3-gap-reconciliation-20261109-0735/git-source-continuity.json:1`,
  `artifacts/verification/w3-gap-reconciliation-20261109-0735/apple-source-diff.txt:1-88`,
  `docs/validation/w3-acceptance-reconciliation-20261109.md:15-31`)
- The locale-only integration receipt identifies runner SHA-256
  `9f6e64f36bddea5fa30744de9a6952d18b6ac27689c1f72416d68396048be25e`,
  no production-source change, and no Chinese fixture/acceptance. The
  reconciliation source receipt reports the nine designated Android sources as
  matching its frozen source manifest. (`artifacts/verification/w3-locale-gate-20261109-0118/integration.json:1`,
  `artifacts/verification/w3-gap-reconciliation-20261109-0735/w3-locale-gate-20261109-0118-source-check.json:1`)

### Root-Cause Guard

The locale gate rejects unsupported explicit values at `onCreate`, before it
can reserve a report or enter model work. The reviewed reporting preserves this
failure rather than routing it through an undocumented default or treating the
host command's exit status as success.

### Validation Limitations

The source exposes `zh-CN` as an explicit control, but this slice contains no
Chinese fixture or Chinese accuracy/quality result. Existing English clips do
not become Chinese acceptance when decoded under another locale. The reconciled
historical Apple evidence remains bounded to its prior observed cases; no
claim is made for all interruptions, microphone availability, PAD, physical
GPU/energy/sustained-resource budgets, or overall W3 completion.

### Recommendation

APPROVE_SLICE

Approval is limited to the strict locale/reporting semantics and the stated
public evidence-continuity/reconciliation boundary.
