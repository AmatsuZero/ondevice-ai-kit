# W5 late-cleanup final review — 2026-11-08

## Code Review Summary

**Scope:** static review only of the current W5 late-owned-cleanup changes in
`LiteRtLmGenerationSession.kt`, `LiteRtLmBackend.kt`, and
`LiteRtLmGenerationSessionTest.kt`.

**Files Reviewed:** 3
**Total Issues:** 1

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 1 (consider fixing)
- LOW: 0 (optional)

### Requirement evidence

- The caller gets `DrainFailure` after the bounded exact-death barrier timeout,
  and `LiteRtLmBackend` records that failure as poison
  (`LiteRtLmGenerationSession.kt:150-185`,
  `LiteRtLmBackend.kt:83-90`). The new owner captures the native session, cache,
  and lease before the caller exits (`LiteRtLmGenerationSession.kt:168-183`).
- The late owner waits again for exact termination, then performs exactly the
  required ordered sequence: native close, owned-cache deletion, lease close.
  Barrier/close/delete failure and a false cleaner result all return without
  releasing the lease; all owner exceptions are contained
  (`LiteRtLmGenerationSession.kt:172-182`).
- The timeout handler encloses only `cancelProcess` plus `awaitTerminated`.
  Timely `native.close` and ordinary cleanup execute outside that timeout and
  fall through the generic cleanup error path; they do not launch another owner
  (`LiteRtLmGenerationSession.kt:150-200`).
- F1's final `ModelStore.remove` assertion is now a bounded cooperative yield
  retry after the cache-delete signal, correctly synchronizing with the later
  `lease.close` rather than assuming cache-delete completion also proves lease
  release (`LiteRtLmGenerationSessionTest.kt:348-361`).
- The known `TimeoutCancellationException()` constructor error is corrected in
  the inspected test source with a literal message
  (`LiteRtLmGenerationSessionTest.kt:600-604`). It is not counted as a review
  finding. At review time, the supplied pre-correction compile artifact still
  records that old error, so it is not post-correction validation evidence.

### Issues

[MEDIUM] F2's negative assertion cannot deterministically distinguish the fixed
implementation from the old broad-timeout handler.

File: `android/backend-litertlm/src/test/kotlin/io/ondeviceai/litertlm/LiteRtLmGenerationSessionTest.kt:546-551,600-604`

`signals.closed` completes at the start of the first `native.close`, before that
method throws its synthetic `TimeoutCancellationException`. In the old behavior,
the broad timeout catch can then enqueue an erroneous surviving owner on
`Dispatchers.IO`; that owner can make a second close after the test has resumed
from `signals.closed.await()` and counted only one event. Thus the assertion can
pass against the old defect due to scheduling, rather than proving that no owner
was launched.

Impact: this regression test can produce a false green and does not establish the
claimed F2 negative property. This is a test-evidence defect, not a confirmed
production-core defect in the reviewed timeout narrowing.

Fix: introduce the smallest deterministic testability seam for the cleanup owner
(an internal dispatcher/scope parameter whose production default remains
`Dispatchers.IO`) and use a test dispatcher to drain scheduled work before
asserting the second-close count. A test-only second-close signal plus a timed
negative wait is not sufficient while production hard-codes `Dispatchers.IO`:
absence of scheduling before that timeout remains a race.

### Validation limits

- No tests, builds, lint, diagnostics, device operations, or private identity data
  were read or run in this review.
- The supplied `candidate-targeted.json`, `old-behavior-red.json`, and full-suite
  summaries describe pre-correction revisions; they are not accepted as evidence
  for the current test SHA. The referenced current targeted run was in progress
  and was not used as a pass claim.
- Existing accepted UI, Service, native, KMP, success/cancel/close/reopen, GPU, and
  energy evidence is outside this late-cleanup code-review scope.

### Recommendation

**REQUEST_CHANGES** — repair the deterministic F2 regression evidence before
approving this slice. No CRITICAL or HIGH production-core issue was found in the
static source review.
