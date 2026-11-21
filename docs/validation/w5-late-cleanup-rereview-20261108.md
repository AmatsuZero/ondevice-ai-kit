# W5 late-cleanup re-review — 2026-11-08

## Code Review Summary

**Scope:** static re-review of the current W5 cleanup implementation and its
deterministic F2 regression test only.

**Pinned source SHA-256:**

- `LiteRtLmGenerationSession.kt` — `10e199002a2469d67f566c1e7027f7ad04db9ee456091b85324256b8c3424371`
- `LiteRtLmBackend.kt` — `4ffb2ca4faf410e1d51776dfed812de1241e3a2f5e7700cf979960a41b910b17`
- `LiteRtLmGenerationSessionTest.kt` — `33b833f3f8a8aaabb74c3adfa161af81c48f4a9c655a763029a340e53c23aaf9`

**Files Reviewed:** 3
**Total Issues:** 0

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

### Re-review evidence

- Production defaults remain a 30-second termination budget and `Dispatchers.IO`.
  `cleanupDispatcher` is an internal injected testability dependency, passed
  unchanged from backend to session; normal request execution remains on
  `Dispatchers.IO` (`LiteRtLmBackend.kt:24-38,86-87`,
  `LiteRtLmGenerationSession.kt:34-55`).
- The timeout is limited to cancel plus exact-death await. A late barrier timeout
  retains native/cache/lease references for the independent owner, returns
  `DrainFailure`, and backend poisoning remains intact. The owner performs only
  exact death -> native close -> successful owned-cache delete -> lease close;
  barrier, close, or delete failure/false leaves the lease retained and owner
  exceptions contained (`LiteRtLmGenerationSession.kt:145-204`).
- Timely `native.close` remains outside that timeout. Its failure reaches the
  regular fail-closed cleanup path and cannot relaunch a close owner
  (`LiteRtLmGenerationSession.kt:191-203`).
- F1 retains a bounded yield retry of `ModelStore.remove`, so its assertion now
  synchronizes with the actual post-delete `lease.close`, rather than treating
  cache deletion as release proof (`LiteRtLmGenerationSessionTest.kt:354-367`).
- F2's previously racy assertion is now deterministic. The test injects a
  `StandardTestDispatcher` solely for cleanup-owner launches, completes the first
  native-close signal, then calls `scheduler.runCurrent()` before counting closes
  (`LiteRtLmGenerationSessionTest.kt:524-568`). With the old broad timeout catch
  and this same seam, the wrongly launched owner is queued on that dispatcher;
  `runCurrent()` executes its second `awaitTerminated` (already released) and the
  second `native.close` entry event before that close suspends at its virtual
  1ms timeout (`LiteRtLmGenerationSessionTest.kt:605-625`). The count would
  therefore be two. With the narrowed current catch no owner is queued and one is
  a meaningful deterministic result. The fake obtains a real timeout through the
  supported suspend API rather than an inaccessible constructor.

### Issues

None found in this re-review. The prior F2 test-evidence finding is resolved in
the reviewed source; no fallback path silently releases the lease after a failed
barrier, close, or cache deletion.

### Validation limits

This was source/evidence review only. I did **not** run tests, builds, lint,
diagnostics, device operations, or inspect private identity data. The parent
targeted build session was still running at review time, so it is not a pass claim
here. Controlled old-behavior RED, current GREEN/full-suite/lint/build evidence
remain parent verification work. Existing accepted UI, Service, native, KMP, and
device lifecycle evidence was deliberately not duplicated; GPU/energy/budget work
is outside W5.

The live Session file was temporarily replaced by the parent solely for the
controlled old-behavior RED after this review began. The reviewed target source was
therefore re-anchored to the immutable
`artifacts/verification/w5-review-correction-20261108-2132/final-session-before-red.kt`,
whose SHA-256 exactly matches the pinned Session SHA above. That temporary RED
mutation is not treated as the candidate; parent restoration and final live-hash
verification remain separate evidence.

### Recommendation

**APPROVE_SLICE** — the current source resolves the prior deterministic-F2 review
block. This is an approval of source correctness within the stated slice, subject
to the separately pending parent runtime verification.
