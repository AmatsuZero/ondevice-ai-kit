# W3 Android two-stage FILE ASR implementation plan

> **For agentic workers:** Use the existing external-candidate workflow. The primary agent plans, integrates and verifies; free CBC writes implementation candidates. Native children remain read-only and the previous native 429 is not retried.

**Goal:** Obtain Android execution, quality and sampled resource evidence for the already confirmed Qwen auto-admission then forced-language pipeline.

**Architecture:** A dependency-free Kotlin case runner owns at most two fresh streams, strict empty admission, actual dispatch counters, persistence gates and cleanup. A separate Android instrumentation wrapper owns frozen file/model verification, one recognizer, foreground lifecycle and atomic reports. Parent scripts own build/package verification, fixed public inputs, RSS/time stop gates, collection, scoring and restoration.

**Tech stack:** Existing Sherpa ONNX 2.0.1 AAR, Kotlin 2.2.20, Android API35 arm64 device, existing offline Gradle/JDK toolchain. No added production dependencies.

**Spec:** `docs/validation/w3-qwen-independent-protocol-20261110.md`; Android additions below retain its input, model, language and quality contract.

## Global constraints

- Preserve existing dirty/untracked paths, ordinary Apps/models, default speech service and global settings. No microphone, network request, AiAi, new capture or model download.
- Reuse the fixed six int8 model files and exact AAR; tokenizer is `modelRoot/tokenizer`.
- Auto stage uses language `""`; admit only when raw text is not exactly `""` (no trim). An admitted case uses a fresh stream and `Chinese` for zh-CN or `English` for en-US, with the entire identical original 16kHz PCM.
- CPU, 2 threads, 128 mel, greedy, seed42, temperature1e-6, topP0.8, maxTotalLen512, maxNewTokens128; no VAD/gain/crop/postprocessing.
- New aliases/run reservation only; never replay the consumed load-only run or old Whisper/Apple/AiAi batches. No retries or parameter sweep after a failed actual run.
- Parent stop gates remain sampled process RSS >1.5GiB, system available <512MiB, or a case >120s. This is a bounded engineering check, not true-peak/GPU/energy acceptance.
- Full quality confirmation retains all 65 frozen independent inputs, 52 speech and 13 challenges; thresholds remain CER20%/near WER20%/far WER35%. Unattempted/failed rows stay unscored. Annotation-negative intervals have no human listening audit.

## Review focus

1. Whitespace-only admission output must enter forced decoding, never be trimmed into silence (task1).
2. Create/set/accept/persist/gate failures must not inflate decode calls; thrown decode calls count as attempts (task1).
3. Release failure or timing/persistence failure cannot erase the original error or skip other acquired cleanup (tasks1–2).
4. Input mutation, symlink/corruption, report collision and foreground loss must stop further inference (tasks1–2).
5. Process disappearance, sampling gaps or a stop gate must retain partial evidence and restore exact aliases/staging, never synthesize success (task3).

## Task 1: Dependency-free two-stage case runner

**Files:** `probes/android-qwen-file/QwenTwoStageCore.kt`; parent-owned `check_core.py` and generated Kotlin acceptance checks.

**Interface:** package `io.ondeviceai.acceptance.qwenfile`. `CaseStream` supplies `setLanguage(String)`, `accept(FloatArray, Int)`, `decode()`, `text(): String`, `release()`. `TwoStageCaseRunner(factory: () -> CaseStream, gate: () -> Boolean, persist: (CaseState) -> Unit, now: () -> Long = System::nanoTime).execute(locale: String, samples: FloatArray): CaseState`.

- [x] Write parent acceptance checks and run against the absent candidate (RED).
- [x] Dispatch one bounded free CBC hy3 candidate for this single core file; immutable supplied context and tested task-directory write sandbox only.
- [x] Review actual bytes and tool/result events; compile and run real Kotlin fault-injection checks. Cover every boundary in both stages, strict empty vs whitespace, locale, fresh streams, whole PCM, no retries and errors surviving cleanup.
- [x] Integrate accepted candidate unchanged and checkpoint source/test evidence.

## Task 2: Android wrapper and package

**Files:** `probes/android-qwen-file/QwenFileActivity.kt`, `QwenFileInstrumentation.kt`, parent build/package acceptance and README.

- [x] Freeze the wrapper/report interface using the accepted core; write parent failure/lifecycle checks before the wrapper candidate.
- [x] Dispatch a separate small external candidate using the load-only lifecycle/model verifier as immutable context; new distinct package and reports.
- [x] Verify all input/model bytes before JNI, exclusive reservation, initial running report, foreground gates, one recognizer, actual stage/row evidence, explicit stream/recognizer release, Activity teardown and final write failure.
- [ ] Run targeted Kotlin checks, offline build/lint and inspect actual APK classes, permissions, ABI/native digests, manifest, signatures and source fingerprint. Independent bounded review before device execution.

## Task 3: New bounded Android file run and acceptance

**Files:** parent device acceptance/scoring scripts and `docs/validation/w3-qwen-android-file-*`.

- [ ] Freeze new run ID, source/APK/model/input fingerprints, 65-input order and parent stop conditions before any decode; recheck device state and exact restoration manifest.
- [ ] Prepare only the new aliases and exact staging; run once sequentially with parent RSS/time monitoring. Stop on failure without retrying.
- [ ] Collect same-run report twice, verify counters/rows/release/teardown and score only complete successful rows against unchanged references; report full coverage and failures separately.
- [ ] Restore ordinary App, verify protected App paths/model hashes, remove only exact temporary staging/aliases and collect cleanup receipt.
- [ ] Record and commit exact task files/hunks; update both existing issue/wiki entries with one deduplicated marker and remote readback. W3 remains open for any unmet Android/resource/license/release gates.

## Execution rulings and ledger

- User already requested autonomous continuation with external implementation candidates; no additional plan approval handoff. Skills are adapted to that explicit execution method.
- Retain immutable raw evidence and failure artifacts; do not apply skill workspace deletion to unique source/results.
- 2026-11-11: plan self-review found all existing independent quality and Android stop gates assigned above. No device inference in planning/core acceptance.

- Ruling: wrapper candidate generation may overlap core correction because it consumes the frozen public interface only. Integration/build/device remain dependent on accepted real core; no duplicate implementation or extra native child.
- Core initial 38 groups/224 assertions passed; added stage-status and post-stage foreground regressions exposed 16 failures in 42 groups. Original SHA98b24f36 retained; focused external correction running.
- Parent guard/scoring checks: 6 tests passed on synthetic reports only; no device inference or quality claim.

- Parent prompt correction identified before integration: fixed input IDs contain underscores and dots (FLEURS config / AMI segment IDs). The wrapper prompt overly narrowed IDs to `[A-Za-z0-9-]+`. Preserve its original prompt and frozen input IDs; after its terminal delivery inspect and, if needed, send a distinct external correction accepting safe basenames `[A-Za-z0-9][A-Za-z0-9._-]{0,159}` with exact `inputs/<id>.pcm` binding. Do not rename inputs, change manifest SHA, or mutate the active request.

- Task1 local core acceptance: final external candidate SHA2d14b1a3… integrated byte-for-byte; actual Kotlin42groups264assertions pass. Initial and both correction raw/results/errors retained. No real JNI/device calls. Whole wrapper/package review remains task2.

- Parent-script independent supplied-context hy3 review completed295.065s, no Critical/Important findings. A/B were declined after checking actual prior hash assertions/transitive equality; C is intentional fixed-six contract. D remains a minor recovery limitation: unknown staging is preserved, never broad-deleted; exact aliases may already be removed before mismatch receipt. E fixed by parent with missing-report structured rejection RED/GREEN; parent7tests now pass. Android wrapper bytes were not covered.

- Task2 decomposition ruling: original hy4free wrapper task naturally ended2119.316s, CLI0/resultsuccess but literal DSML tool marker, eight Read/zero Write/zero files; rejected and archived. No quota/error or supervisor timeout. Do not resubmit. New distinct small candidates: `QwenFileInputs.kt` (callback-based65asset verification/PCM conversion, no Android), `QwenFileActivity.kt` (minimal accepted Activity rename/status), then one thin `QwenFileInstrumentation.kt` consuming accepted modules. Inputs8behavioralgroups and Activity6lifecyclechecks were RED before dispatch. Parent build/wrapper acceptance now includes actual input module bytes/classes. No weakened input/model/quality/resource contract.

- Task2 module acceptance: external Inputs SHA02567c13 (8groups/406373assertions), Activity SHAdd859f76 (6assertions), Report SHA74cb2ab4 (22assertions) integrated unchanged. Report initial compiler RED for two omitted DTOs; distinct external correction only added declarations. Original helper failures and actual scope checks retained. Thin instrumentation hy3 task `/private/tmp/odai-hy3-w3-qwen-file-instrumentation-20261111/` is separate; whole wrapper/APK/device remains unaccepted.

- Task2 wrapper/package checkpoint: first wrapper21groups343pass, supplemental error/case-binding regressions RED5groups; focused external correction SHA7dc3eaa6 integrated unchanged and24groups364pass. Actual offline build25.378s/lint0error7warning; actual APK13classes/65assets/zero permissions/four native-library digests/common debug signing verified. Parent scaffold missing-bat failure preserved and POSIX prereqs fixed before source creation. Independent bounded hy3 wrapper/package review is running separately; no physical FILE install/constructor/decode yet. SourceFingerprint ef4665190423630afa548d4c6b4ea1a4706f98ab29761437887742a7872f8696; run remains w3-qwen-file-20261111-01.
