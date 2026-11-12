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
- [x] Publish ACK atomically only after its bytes and the actual observation receipt are durably written. A device must not see a newly created empty/partial ACK while a writer is still filling it; exclusive creation alone is insufficient. Preserve inode ownership during failure cleanup. The adapter and its real coordinator caller now satisfy the host integration gate; OEM/device behavior remains separately unverified.

  Next transport slice: external compact tests-first `AdbTerminalAckPublisher`
  candidate; exact stage readback after exec-in, file fsync, required before-link
  resource/journal callback, exclusive hardlink, directory fsync, retained stage
  and typed publication-uncertainty failures. AOSP toybox source review confirms
  syscall/error behavior; mksh noclobber source is captured. Device capability,
  full coordinator ordering and actual Android execution remain unverified.

  Ruling (2026-11-12): six primary real-subprocess/file publisher methods now
  directly fail with missing-module RED. Source generation may overlap the
  unchanged independent no-source-context external test request; integration
  still waits for delivered/reviewed external tests, their direct RED and both
  complete GREEN groups. This is separate file ownership, not a request restart.

  Adapter completion (2026-11-12): external8 +primary6 real-subprocess/file
  methods +primary4 boundary methods pass, with the complete Python157 suite.
  External and parent direct missing-module RED preceded integration. Original
  response exact-type, trailing-newline and huge-timeout failures were corrected
  by exact external patches; a separate external patch corrected test placeholder
  paths and the false rm-substring prohibition. Actual caller persistence and
  before-link same-process resource/journal integration are still open, as are
  OEM command capabilities and the complete new wrapper/driver.

  Source review completion: supplied-source free hy3 review accepted the exact
  corrected publisher with no findings; no review commands were executed and
  real OEM/fsync/hardware behavior remains unverified.
- [x] Reproduce and document postpublication directory-fsync and staging-unlink failures: a complete target can remain visible despite an exception. Future callers stop and retain the same-operation evidence, never automatically retry or infer durability from visibility.
- [x] Resolve the final review's medium error-signal finding in code: typed postpublication error, exact boolean flags and original cause; two missing-type RED cases become GREEN with the full 67-test suite.
- [x] Verify a local coordinator's actual read/sample/clock/write ordering and immutable report/receipt snapshots with injected callbacks; 35 coordinator methods and full Python 102 pass. Supplied-context source review accepts; its missing-binding KeyError claim is not reproducible (both fields already reject as ValueError before ACK/writes). This is local IO evidence; the Android publication item above remains open.
- [x] Add the actual ADB process sampler (stat/status/meminfo/stat, fixed identity, bounded responses/timeouts, safely quoted remote command, no retries). The external 17 and independent primary 9 methods pass, including real local fake-ADB subprocesses; complete Python 128 passes. Source review accepts, with one deferred stale test-header comment. Real Android sampling remains unverified; current ADB enumeration is empty. Test-response recovery, pseudo-Write rejection, 6fail/3error and syntax-fix evidence are retained.

  Ruling (2026-11-12): the external test-only request remains live and has no source context. Six independent primary real-subprocess acceptance methods already produced a direct missing-module RED. Source-candidate generation may overlap that unchanged test request to reduce waiting; source integration remains gated on receiving/reviewing the external tests, their missing-feature RED and both complete GREEN sets. These are separate owned files, not a retry, duplicate request or relaxation of the sampler contract.

  Completion: both groups satisfied RED before integration, all 26 passed after exact helper edits; full 128 and source review accepted. The earlier live request is terminal. Final: minor (deferred): test module header still describes its original missing-implementation stage; README and validation record current implementation/test state.
- [ ] Derive new wrapper/driver names, report paths and unconsumed run identity; do not execute the old driver.
- [x] Compose the three actual ADB adapters in the specified remote observation
  coordinator: two fresh reads enclosed by samples, durable local report/receipt/ACK,
  then third sample and durable before-link journal inside the remote publisher
  callback. External compact tests first, direct RED, primary actual subprocess
  acceptance, exact external source, full GREEN and source review precede any
  new wrapper/driver execution. Full polling/launch/restore is a separate gate.

  Ruling (2026-11-12): six primary real-subprocess composition methods directly
  fail with missing-module RED. Source generation may overlap the unchanged
  no-source-context external four-method test request; integration still waits
  for delivered/reviewed external tests, direct external RED and both complete
  GREEN groups. There is no test-request restart or implementation by primary.

  Completion (2026-11-12): external4 +parent10 GREEN, complete Python171/25AST,
  old120 frozen files/original failed verdict and96 protected paths unchanged.
  Six parent methods execute all actual adapters through local fake-ADB/shell
  and real hardlink/fsync, including process exit, journal failure before link
  and post-link directory-fsync failure. Source review's two findings were
  withdrawn after actual dependency/validator reproduction; final accept with
  no remaining findings, no source change for those opinions. Initial source
  preflight defect and external test transport/nonlocal defects were corrected
  by exact external candidates; original failures remain. No Android execution
  or full polling/launch/restore acceptance is inferred.
- [x] Add a fresh subprocess-capable report reader for the distinct `files/w3-qwen-terminal/report-<run_id>.json` namespace. External4 +parent7 methods and full Python139 pass; exact source review accepts. Original large test request ended CLI0/raw0/context400 with no source, retained; a distinct reduced4-method request delivered. Its direct missing-module RED and both full GREEN groups gate integration. Initial source3fail/9error strict-type defect and test path-adjective ambiguity are corrected through exact external edits, with original failures retained. Actual Android host-loop ordering/remote ACK publication remain separate gates.

  Ruling (2026-11-12): six independent parent real-subprocess report-reader methods directly fail with missing-module RED while the unchanged no-source-context external test request remains live. Source generation may overlap that independent test request. Integration still requires received/reviewed external tests, their direct missing-feature RED and both full GREEN groups. No request is duplicated and no tests-first gate is removed.
- [x] Wire local bounded ACK reads after release/teardown/terminal persistence; stale ACK refusal precedes native calls.

  Next actual wrapper: distinct QwenTerminalInstrumentation with a JVM no-arg
  constructor, bounded own-proc-stat validation, stale final/stage refusal before
  report/Activity/native, exact terminal bytes/hash after real cleanup, immutable
  terminal while awaiting the existing barrier, and explicit Bundle ACK result.
  Extend the accepted heap host scaffold and add ordering/fault cases before the
  external wrapper candidate; do not modify the old wrapper or frozen sources.

  Completion (2026-11-12): exact independently delivered platform/preflight/final
  fragments plus narrow glue corrections yield58hostgroups/783assertions;
  real JVM noargs, invalid ownstat, stale final/stage, failed terminal, ACKfaults
  and original-error/terminal-byte preservation pass. Initial whole request was
  CLI0/raw0/Empty stream; not reissued. Primary's inaccurate API/nullability
  fragment context caused6compileerrors, next external glue had1Fileconstructor
  error; all retained and corrected externally. Writer serialization returned
  inside its original error boundary. Tools10checks, cached offline75-task
  Android build/lint, actualAPK19classes/65assets/4exactruntime members pass.
  Independent diff-scoped wrapper +full tools source review accepts, no findings;
  unchanged teardown is outside that review but executed by parent host checks.
  Full launch/polling/restore driver, newfreeze and device proof remain open.

  Next slice: receive `TerminalAckFileChecks.kt` first for the specified `TerminalAckFile` interface; compile the direct missing-type RED before the external implementation. Reuse cached JVM/NIO patterns, fixed 1025-byte cap and final/stage stale checks; leave actual wrapper ordering and Android package/device evidence open until separately tested.

  Completion (2026-11-12): direct missing-type RED preceded source integration;
  13 external +6 reader/barrier +4 parent boundary checks and cached API33
  `-no-jdk` compilation pass. Initial missing-return and normalized-alias
  candidates were rejected. Final review's two findings were withdrawn after
  actual File-constructor representation and current-return-syntax evidence;
  adjudication accepts. No device IO, wrapper ordering, APK or old-gap recovery
  is inferred. The distinct remote-report tests remain live and source integration
  remains gated on their delivery/RED/GREEN.
- [ ] Reproduce the old exit race and IO-failure cases using the actual new host loop and wrapper fault doubles, then build/lint/check real APK contents and obtain source/parent review.

  Wrapper fault checks/build/lint/APK and its diff-scoped source review are done;
  actual running-process loop/outer launcher-restorer remain separate work.
  Ruling (2026-11-12): six primary real-local-ADB polling acceptance methods have
  actual direct missing-module RED. The independent four-method no-source-context
  external test request remains unchanged. Source generation may overlap it;
  integration still waits for delivered/reviewed tests, their direct RED and
  all external/parent GREEN, followed by full suite and source review. Separate
  file ownership is not a duplicate/restart or removal of the tests-first gate.

  Polling completion (2026-11-12): after the terminal whole-request Empty stream,
  the user selected free hy3. Exact compact candidates and narrow external fixes
  are integrated; external4 +parent6 +boundary13 methods and full Python194/33AST
  pass. Old120 frozen files/original failed verdict,95 protected paths,81 prepared
  wrapper sources and both APKs stay unchanged. Initial19 methods41FAIL13ERROR,
  later21 methods2FAIL, ordering/type1FAIL1ERROR and three callback-crossing RED
  cases are retained. The final external patch checks the old120s window after
  poll/sample/read, before any new phase can reset it. Static source review accepts;
  its missed callback defect is covered by actual parent RED-to-GREEN adjudication.
  Final: minor (deferred): first phase begins at loop start, conservatively reducing
  its allowance by first-read overhead. No Android/OEM behavior is inferred.
  Full outer launch/collect/partial-prepare restore, complete new freeze and
  same-run device proof remain open; four outer routing checks have missing-driver
  RED only. See docs/validation/w3-terminal-polling-20261112.md.

  Follow-up (2026-11-12): while defining startup integration, first poll/sample/read
  advancing121s before anchor_phase was named bypassed the120s guard;three actual
  RED subcases and three inclusive120s successes are retained. Exact hy3 deletion
  of the phase-known conjunct from five existing guards makes both new methods
  GREEN. The loop-start anchor and all known-phase/API/protocol behavior remain.
  Next interface dependency: initial_progress must carry the first verified
  phase/index and conservative run-start hostns into polling so startup cannot
  reset the120s first window. That optional API extension is specified in the
  outer-driver brief, not implemented or device-verified yet.

  Completion (2026-11-12): `initial_progress` exact snapshot API integrated from
  free hy3 candidate. Five parent methods pass after correcting two test-input
  mistakes (legal None/default and legal complete dict); full observation201,
  guard13 and 37-file AST checks pass. Supplied-source review accepts. The
  outer driver still must obtain and pass the real startup snapshot.
- [x] Validate a future complete frozen contract locally before any device action.
  The separate terminal_device_contract.py candidate covers strict JSON/paths,
  exact source/APK/plan/budget bindings, production protections and both current
  review receipts. Initial10 methods0FAIL122ERROR from a wrong decoder API and
  isolated fixture-hardcode/top-contract-link defects are retained. Exact hy3
  five-replacement correction yields12 parent methods GREEN. Fresh supplied-source
  review prompted a fixed19-unique-string class profile: four actual RED subcases
  precede an exact external single-block fix; complete13 parent methods GREEN.
  Other findings are adjudicated against actual exception inheritance, package
  class/component identity and the declared11-Python-file review scope. Final
  parent decision accepts. Minor (deferred): duplicated correct runner literal
  and original OS error for invalid non-directory intermediates. No real freeze
  or device readiness is inferred from synthetic APKs/unexecuted placeholders.
- [ ] Freeze a separate complete contract with inputs, hashes, budgets and restoration. Run only if the current Android channel, identity and unlock prerequisites are verified.
- [x] Bridge actual startup/run/finish on host: external exact candidates and
  patches, real-subprocess 51 startup/finish/run methods, complete265 checks and
  41AST pass. Shared pre-Popen clock, consumed marker, strict transport bytes,
  terminal raw hash and interrupt-preserving recovery are verified. Supplemental
  source review accepts after actual coordinator/Kotlin/hash evidence corrects
  the original review's receipt misunderstanding. Commit13559ad; no source push.
- [ ] Complete adapter C: guarded source adaptation and live identity callbacks,
  prepare/collect/policy, durable pre-side-effect ownership claims and exact
  partial-prepare recovery. Do not use four routing checks as full acceptance.
  Source adaptation sub-slice accepted at f084bd0: pinned immutable FILE source,
  no old action execution/module launcher, per-call frozen hook, unchanged helper
  AST and8actualfakeADB parent methods. Mandatory source closure omission RED is
  fixed with external DRIVERS addition; full274/43AST pass, source review accept.
  Live callbacks/dispatcher/ownership/prepare/collect/restore remain unimplemented;
  this sub-slice does not complete the C item or authorize a new real freeze.
- [ ] Collect exact same-run terminal evidence and restore the new alias/staging. Never infer device acceptance from pure components.

### Task 4: Evidence and delivery

- [x] Save source/hash/test/review receipts, preserve protected dirty files, update the handoff and W3 current-state note.
- [x] Commit only exact accepted files. Synchronize substantive results to the existing Issue/Wiki using a fresh idempotency marker; keep source push separate.
