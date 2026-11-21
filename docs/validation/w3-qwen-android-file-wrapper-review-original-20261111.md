# Independent bounded source/acceptance review — W3 Android two-stage FILE ASR wrapper

- **Scope reviewed (supplied bytes only):** `QwenFileInstrumentation.kt`, `QwenFileActivity.kt`, `QwenFileInputs.kt`, `QwenFileReport.kt`, `QwenTwoStageCore.kt`, `QwenLoadCore.kt`; `WrapperChecks.kt`/`check_wrapper.py`; `prepare_build.py`/`build_acceptance.py`; `AndroidManifest.xml`/`build.gradle.kts`; `wrapper-host-result.json`/`report-host-result.json`/`package-verified.json`; `candidate-accepted.json`/`run-plan.json`/`build-result.json`; `main.apk.manifest.txt`/`main.apk.permissions.txt`/`test.apk.permissions.txt`.
- **Frozen receipts taken as supplied, NOT re-run:** sourceFingerprint `ef466519…`, input manifest `8d1cfd74…`, parent host wrapper 24grp/364, report 22, build 25.378s/lint 0err 7warn, package-verified (13 classes / 4 arm64 libs / 0 perms / non-exported / debug cert). I did not run tests, build, APK, or device.
- **Target:** readiness for ONE new physical-file run (never production/release). Load-only already consumed; no replay.

## Critical findings
**None.**

## Important findings
**None in the fixed source bytes.** The two defects the plan/pitfalls record as historically missed — outer-error serialization and failed-case binding — are present and correct in the supplied bytes:
- `err(t)` (`QwenFileInstrumentation.kt:65-70`, outer catch `:318`) preserves the first real exception’s class+message into `state.errorClass/errorMessage` and never overwrites an earlier core/wrapper error.
- Failed-case binding is retained: `activeCaseIndex`/`caseStartedElapsedRealtimeNanos` are cleared only after a completed case (`:289-293`); on failure they stay bound (confirmed by the decode-failure host test expecting `activeCaseIndex==0` and a `planned` row 1). The 24grp/364 wrapper receipt covers these.

## Minor findings
- **M1 — Reservation TOCTOU (narrow).** Collision check (`QwenFileInstrumentation.kt:144-146`, `.new`/`.bak`/base existence + symlink) precedes `createNewFile` (`:147`) without an atomic claim across the three variants. Risk only under a concurrent writer. The parent’s exclusive alias reservation (`w3-qwen-file-20261111-01`) precludes that for this single run; `createNewFile` itself is atomic per file. No change required for this bounded run.
- **M2 — 5s resume/destroy latches (run boundary).** `resumeArrived.await(5s)` (`:165`) and `destroyArrived.await(5s)` (`:347`). On a memory/CPU-pressured device during the first case these could trip, flipping `teardownAck=false` → `failed` (correct, *not* false-completed) and aborting. Not a source defect; note as an operational boundary for the real device.

## Boundaries & declined-to-judge
- **Real-device lifecycle threading.** `runAll` executes entirely on the instrumentation main thread (`onCreate→onStart→runAll`) and calls `runOnMainSync` (`fgGate` `:82`, teardown `:346`) and `startActivitySync` (`:162`) from that same thread. Host doubles stubbed `runOnMainSync`/activity start and did not exercise same-thread semantics. AOSP `runOnMainSync` runs the runnable inline when already on the main looper, and `startActivitySync` pumps the queue while waiting, so a deadlock is unlikely; this is **not** an executable blocker under documented behavior, but it is unconfirmed on the target API35 arm64 device. Recommended (not required): a short device smoke (one admitted + one silence case) before the full 65.
- **Model/asset staging is parent-owned.** Native model path `targetContext.noBackupFilesDir/…/qwen3-asr-0.6b-int8-393f8a14` and `context.assets` PCM are provisioned externally. `QwenModelFiles.verify` fails closed (throws on missing/corrupt/symlink), so a missing model cannot reach native; `QwenFileInputs.verifyAll`/`readSamples` likewise block. Not a source defect.
- **External stop gates.** Parent RSS>1.5GiB / avail<512MiB / case>120s are external. An interrupted dispatch leaves a partial `running` report, never a false `completed` (`allOk` gates on `rows.size==65` + `allRowsCompleted` + `constructorReturned` + `recognizerReleaseSucceeded` + `teardownAck` + zero error classes, `:372-378`). Consistent with the “partial undercount, never exact final” caveat.
- **I did not re-verify APK/model/PCM or re-run tests**; parent receipts are accepted as evidence, per task constraints.

## Consistency cross-checks (bytes vs receipts)
- `sourceFingerprint` `ef466519…` identical in `candidate-accepted.json`, `run-plan.json`, `package-verified.json`. Input manifest `8d1cfd74…` identical in `run-plan.json`, `QwenFileInputs.kt:27` (`MANIFEST_SHA256`), and `cases.json` hash.
- Package ids: `io.ondeviceai.acceptance.qwenfile` (main) / `.test` (test) match `build.gradle.kts:10,13` and `package-verified.json`; `AndroidManifest.xml:3` Activity `exported="false"` matches `main.apk.manifest.txt` (`0x0`).
- Exactly-one recognizer: constructor count `:205` + single `OfflineRecognizer` (`:207`); `finally` release exactly once (`:322-340`); allOk requires `recognizerReleaseAttempts==1` + `recognizerReleaseSucceeded`. Matches host “constructor failure counts attempt without release” and “release failure cannot complete” receipts.
- Decode counter placement: `decodeAttempts++` (`QwenTwoStageCore.kt:174`) immediately before `stream.decode()` (`:175`); `QwenFileReport` derives `nativeDecodeCalls` from `stage.decodeAttempts`. Matches host “decode failure preserves actual count” (==1) and `run-plan` 130/117 envelope.
- Strict-empty vs whitespace: `autoText == ""` admission gate (`QwenTwoStageCore.kt:205`) admits on any non-empty (incl. whitespace) raw text, entering forced stage — consistent with plan “no trim” and the `strict empty auto uses only one decode` host case.
- Initial durable report (`:152`) lands before `sysAwake` gate (`:156`), Activity (`:159`), model/input verify (`:170-183`), and constructor (`:185`); reservation (`:136-148`) precedes it. Matches focus-1 ordering.

## Readiness
The fixed source bytes are internally consistent and close the previously-missed outer-error-serialization and failed-case-binding defects; no Critical/Important source blocker for ONE new physical-file run is identified. Residual items (M1, M2, threading smoke) are boundaries/declined-to-judge, not executable blockers. Recommended before the full 65-case sequential run: the short device smoke noted under Boundaries to confirm same-thread instrumentation semantics on the target API35 arm64 device.

*Delivered file only. No implementation, no build/test/APK re-execution, no edits to supplied source or context.*
