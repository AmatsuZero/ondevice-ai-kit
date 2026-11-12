# W3 terminal observation implementation plan

> **For agentic workers:** Follow the existing external-helper implementation lane. The primary agent plans, integrates and runs acceptance checks; native children remain read-only reviewers and the previous native 429 is not retried.

**Goal:** Prevent future probe exit from racing the required final resource observation without changing past results or lowering acceptance gates.

**Architecture:** A pure Kotlin bounded ACK wait and a pure Python report/resource validator share an exact ASCII ACK contract. A separately named future wrapper/driver will wire their IO and preserve all old artifacts.

**Tech Stack:** Existing Kotlin/JVM compiler, Java standard library and Python standard library; no dependencies or runtime/model downloads.

**Spec:** `docs/superpowers/specs/2026-11-12-w3-terminal-observation-design.md`

## Global constraints

- Old `w3-qwen-heap-20261111-01` is consumed, immutable and rejected.
- Native/model/microphone/AiAi/device runs are outside the pure-component task.
- Maximum ACK wait 30000 ms; exact deadline is failure; strict ASCII ACK.
- Existing RSS <=1610612736 B and available >=536870912 B remain inclusive.
- External helpers provide actual implementation candidates; no paid route or native retry.

## Review focus

- Callback failure or clock regression must stop waiting without a fabricated ACK.
- A matching ACK returned after the deadline must still fail.
- A changed report or reused PID must prevent host acknowledgement.
- bool/float/overflow/missing numbers must not coerce to valid observations.
- Valid observation of a failed terminal report must not become quality acceptance.

### Task 1: Pure device barrier

Files: `probes/android-terminal-observation/TerminalObservationBarrier.kt` and `TerminalObservationChecks.kt`.

Interface: immutable binding; `ackBytes()`; `awaitAck(readAck, nowMillis, sleepMillis, timeoutMillis=30000)` returns Unit only for timely exact ACK, otherwise throws the original callback failure or a typed validation/timeout failure.

- [x] External hy3 supplies tests first, covering valid/delayed/absent/mismatched/oversize ACK, exact deadline, timeout bounds, clock failure/regression, callback/interrupt failures and invalid binding.
- [x] Primary compiles/runs the test-first acceptance and records the expected missing-feature failure; no Kotlin implementation is integrated before this check.
- [x] External hy3 supplies only the barrier implementation; primary reviews and integrates exact candidate bytes.
- [x] Primary compiles with `-Werror`, runs the same tests and independently adds acceptance cases if evidence exposes a gap.

### Task 2: Pure host validator

Files: `probes/android-terminal-observation/terminal_observer.py` and `test_terminal_observer.py`.

Interface: `acknowledgement(first: bytes, second: bytes, binding: dict, observation: dict) -> bytes`. Observation carries exact PID/startTicks before/after, report-read times and sample interval/RSS/available. The return is observation ACK only.

- [x] External hy3 supplies negative and positive tests before implementation.
- [x] Primary records RED, then receives and integrates an exact independent implementation candidate.
- [x] Primary runs Python tests, Kotlin cross-language ACK fixture comparison, syntax checks and old frozen hash/verdict checks.
- [x] External supplied-context code review assesses both components; primary records limitations and fixes actual findings through the external candidate lane.

### Task 3: Future independent IO integration

- [x] Implement bounded local file primitives separately from the actual device sampling coordinator. A combined API that accepts previously collected observation times while hiding new report reads cannot establish the required ordering; do not integrate that rejected interface. Local file checks 45/45 and existing validator checks 22/22 pass; actual device IO remains separate.
- [ ] Publish ACK atomically only after its bytes and the actual observation receipt are durably written. A device must not see a newly created empty/partial ACK while a writer is still filling it; exclusive creation alone is insufficient. Preserve inode ownership during failure cleanup.
- [x] Reproduce and document postpublication directory-fsync and staging-unlink failures: a complete target can remain visible despite an exception. Future callers stop and retain the same-operation evidence, never automatically retry or infer durability from visibility.
- [x] Resolve the final review's medium error-signal finding in code: typed postpublication error, exact boolean flags and original cause; two missing-type RED cases become GREEN with the full 67-test suite.
- [x] Verify a local coordinator's actual read/sample/clock/write ordering and immutable report/receipt snapshots with injected callbacks; 35 coordinator methods and full Python 102 pass. Supplied-context source review accepts; its missing-binding KeyError claim is not reproducible (both fields already reject as ValueError before ACK/writes). This is local IO evidence; the Android publication item above remains open.
- [ ] Add the actual ADB process sampler (stat/status/meminfo/stat, fixed identity, bounded responses/timeouts, safely quoted remote command, no retries). Receive external tests first, record real RED, receive/review implementation, run regressions and keep injected-runner evidence distinct from device evidence.
- [ ] Derive new wrapper/driver names, report paths and unconsumed run identity; do not execute the old driver.
- [ ] Wire local bounded ACK reads after release/teardown/terminal persistence; stale ACK refusal precedes native calls.
- [ ] Reproduce the old exit race and IO-failure cases using the actual new host loop and wrapper fault doubles, then build/lint/check real APK contents and obtain source/parent review.
- [ ] Freeze a separate complete contract with inputs, hashes, budgets and restoration. Run only if the current Android channel, identity and unlock prerequisites are verified.
- [ ] Collect exact same-run terminal evidence and restore the new alias/staging. Never infer device acceptance from pure components.

### Task 4: Evidence and delivery

- [x] Save source/hash/test/review receipts, preserve protected dirty files, update the handoff and W3 current-state note.
- [x] Commit only exact accepted files. Synchronize substantive results to the existing Issue/Wiki using a fresh idempotency marker; keep source push separate.
