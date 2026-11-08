# W1 / W5 closure audit — 2026-11-08

This is a read-only acceptance audit of the evidence already retained under
`artifacts/verification/`. It does not alter production code, the production
lock, old evidence, or the task ledger.

## Verdict

| scope | verdict | bounded conclusion |
| --- | --- | --- |
| W1 / original F01 real Photos-provider exit | **PASS** | The finite PHPicker provider requirement is closed on isolated iOS simulators, which the user explicitly allowed for this work. |
| W5 process-backed public-session safety boundary | **PARTIAL** | Normal success/cancel/close/reopen evidence proves the exact-Binder-death barrier. A delayed-death-after-30s-timeout path retains an orphaned local cache/ModelStore lease, so eventual cleanup/re-entry is not yet proved for that fault path. |

Neither conclusion claims W8/P11 resources, GPU reclaim/energy, release approval,
or natural-quality acceptance.

## W1 / F01

The historical F01 exit criterion is explicit and finite:
`git show 3c708ef1:docs/validation/foundation-followups.md`, row 23 requires
three actual selections of designated synthetic assets, reproducible dimensions
and mirrored pixels, and a successful `apple.vision.face` result. It says real
`PHPicker`; it does **not** require a physical device. The user permitted
simulators for this follow-up, so an iOS simulator's system Photos provider is
the accepted provider boundary. This does not use private Photos data.

The rebuilt validation host contains the existing production `DemoController`
and observes its chained bridge result; the test itself selects an exact,
uniquely-labelled item from the system picker and contains no injected
`useImage` call or fake callback. Source and host references are retained in
`artifacts/verification/w1-provider-closure-20261108-1940/PhotoPickerHost.swift`
and `PhotoPickerOrientationUITests.swift`.

Fresh evidence:

| case | provider/device evidence | observed assertions | result |
| --- | --- | --- | --- |
| EXIF2 | existing isolated simulator `6DD43029-…`, filtered real PHPicker run | `80x40:white,black,white,black`; `apple.vision.face:80x40:faces=0`; count `1` | `complete-exif2.log`: EXIF2 test passed |
| cancel → reselect | same real PHPicker run | initial real result; next-picker Cancel preserves pixel/Vision/count `1`; five-second no-spurious-result window; reselect reaches fresh count `2` | `complete-exif2.log`: test passed |
| EXIF6 | newly created simulator `376C9A71-…`; exact public DCIM byte SHA `1dc0f63d…010bab`; shutdown receipt retained | `40x80:black,black,white,white`; `apple.vision.face:40x80:faces=0`; count `1` | `exif6-complete.json`, `exif6-complete.stdout`: one test / zero failures |
| EXIF7 | newly created simulator `A650A4F0-…`; exact public DCIM byte SHA `5d4555b0…ed4cf`; shutdown receipt retained | `40x80:white,white,black,black`; `apple.vision.face:40x80:faces=0`; count `1` | `exif7-complete.json`, `exif7-complete.stdout`: one test / zero failures |

The combined EXIF2/cancel command exited `0` after 48.3 seconds and its log
reports two tests / zero failures; EXIF6 and EXIF7 exited `0` after 30.487 and
30.951 seconds respectively. `complete-tests-build.json` records a clean
14.641-second `build-for-testing`, SHA-256
`73d2e252b5997085d052ac0d220c0387474f09dc71d73fdc0aadde001b6f7f87`,
and identifies the source as the existing full helper candidate. The worktree
HEAD is `230e981`; production fix `cae21ed` is an ancestor.

Therefore W1/F01 may close. The remaining limits are deliberately outside this
package: physical-device equivalence, P4 native-resource investigation, and
W8/P11 quality/resource/release gates. They must not be reintroduced as W1
blockers.

## W5 / exact Binder death versus old native join

The old LiteRT-LM 0.18.1 same-process native cancel/join investigation remains
accurate for that architecture, but it is not the current public
`createProcessBackend` shutdown mechanism. The process-backed service is
declared in `android/backend-litertlm/src/main/AndroidManifest.xml` with
`android:process=":odai_litertlm"`.

For the public process backend, the actual ordering is:

1. `LiteRtLmProcessClient.performConnect` registers a death recipient on the
   original Binder before HELLO (`LiteRtLmProcessClient.kt:183-190`).
2. Once HELLO might have started, `LiteRtLmProcessSession.runFinalize` sends
   best-effort CANCEL and RETIRE and waits on `awaitOwnershipReleased()`
   (`LiteRtLmProcessSession.kt:271-282`). RETIRE reply, terminal status,
   disconnection, PID lookup and timeout never complete that barrier.
3. Only `handleExactBinderDeath` clears the exact coordinator claim and completes
   `ownershipReleased` (`LiteRtLmProcessClient.kt:318-330`).
4. `LiteRtLmGenerationSession` waits for that completion before deleting its
   private cache and closing the ModelStore lease
   (`LiteRtLmGenerationSession.kt:130-142`).

This is an OS process-termination barrier: the dedicated service intentionally
does not call the uncertain asynchronous `conversation.close()` / `engine.close()`
after native async work, and self-terminates only its own exact PID
(`LiteRtLmProcessService.kt:73-89`). It proves no graceful native join and no
GPU-driver-memory reclamation, but it does establish that the remote process
which could access the model/input/cache has died before local file/lease
release.

The evidence covers both protocol ordering and the public path:

- `LiteRtLmProcessModelStoreInstrumentation` holds RETIRE, confirms a live
  ModelStore lease and pending generation, fires only the registered Binder
  death, then requires cache removal and successful `ModelStore.remove`
  (`LiteRtLmProcessModelStoreInstrumentation.kt:153-165`).
- `artifacts/verification/w5-bridge-runner-20261108-1906/parent-acceptance.json`
  records one 186.140-second real production chain:
  `LiteRtLmDemoBackend → Client → AndroidMultimodalBridge → public
  createProcessBackend/session → Service/native`. It observed two successes,
  one post-update cancellation, close→CLOSED, same-ModelStore reopen, removal
  to ABSENT, no owned cache/input files and no remaining native process.

Thus the tested normal/cancel/reopen public-session safety need is met by the
new process architecture. The stale continuation text in
`docs/validation/android-litertlm-drain-contract.md` must not state that the
factory, consumer, and real device inference are all absent; it should instead
separate the legacy in-process unsupported join from this exact-death barrier.

### Remaining W5 fault-path gap

There is a precise liveness defect if exact Binder death is delayed beyond the
30-second local wait:

1. A request has passed HELLO/START and `LiteRtLmGenerationSession.run` calls
   `cancelProcess()` followed by `awaitTerminated()` inside
   `withTimeout(30_000L)`.
2. CANCEL/RETIRE or process death is delayed past 30 seconds.
3. The `unsafeNative` catch path throws `DrainFailure` before cache deletion and
   `lease.close()` (`LiteRtLmGenerationSession.kt:125-145`). The old backend is
   poisoned.
4. If the process then dies later, the session lifecycle coroutine can clear the
   coordinator, but no continuation owns the abandoned local cache/lease;
   `ModelStore.remove` may remain blocked even though a fresh coordinator claim
   can eventually be made.

This is conservative—there is no premature file/lease release—and does not
invalidate the proven normal process-death boundary. It prevents a full
eventual-cleanup/re-entry claim for the delayed-death fault sequence. The
minimal follow-up is a single exact-death continuation that cleans the owned
cache and closes the lease exactly once after a late death, plus a regression
which holds death past a configurable test timeout and then proves cache
removal, `ModelStore.remove`, and a fresh backend generation.
