# W5 late exact-death cleanup — accepted

W5 is closed for the established opt-in Android image-only process-backend scope.
The accepted real Service/native, public factory, KMP bridge and ordinary Demo
success/cancel/close/reopen chain is retained. This correction closes the final
late Binder-death cleanup defect without repeating those device runs.

## Behavior

The caller still gets `DrainFailure` and the old backend is poisoned when the
30-second termination wait expires. An independent owner keeps the native session,
owned cache and model lease until the exact registered Binder-death barrier resolves.
Cleanup then runs `native.close` -> successful owned-cache deletion -> `lease.close`,
at most once. Barrier/close/delete failure retains the lease. Only the termination
wait can hand ownership to this coroutine; a timely close's own timeout cannot
relaunch close. Production dispatch remains `Dispatchers.IO`; an internal dispatcher
parameter permits deterministic testing.

## Fresh verification

- Targeted current candidate: 14 tests, zero failures, 19.724 seconds.
- Same test dispatcher seam with only the old broad timeout handler restored:
  14 tests, exactly one behavioral failure, 15.343 seconds. The timeout regression
  observed `expected:<1> but was:<2>` native-close calls. The other 13 tests passed.
- The candidate was restored byte-for-byte in `finally`; final source hashes match.
- Final full backend: 97 tests; Demo: 67 tests; zero failures/errors/skips.
- Both lint checks: zero errors; existing warning counts 2 and 11 respectively.
- Demo `assembleDebug` passed; full checks took 75.323 seconds.
- Independent native source re-review: `APPROVE_SLICE`, limited to this correction.
  It did not execute builds, tests or devices.

Receipts, failed compiles, exact helper patches, controlled RED/GREEN XML, lint and
build output are retained under
`artifacts/verification/w5-review-correction-20261108-2132/`; the authoritative
summary is `parent-acceptance.json`. Prior broader lifecycle receipts are listed
there. The original review's REQUEST_CHANGES report remains alongside the re-review.

## Remaining boundaries

GPU allocation, energy, sustained/cross-capability resource budgets and whole-branch
final approval remain W8 work. This W5 decision is not release approval and does not
claim Android video support or broad model quality. No publication or push occurred.

All nine protected files retain their recorded hashes. Post-task cleanup found no
new large disposable artifact; unique raw evidence, models and accepted build
products were kept. Disk space at acceptance was about 45 GiB.
