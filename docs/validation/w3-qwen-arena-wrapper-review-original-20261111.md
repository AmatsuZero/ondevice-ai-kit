# Independent Review — CPU arena-off Qwen Android FILE wrapper

**Reviewer:** bounded independent review (read-only source + acceptance-evidence)
**Subject:** `probes/android-qwen-arena/QwenArenaInstrumentation.kt` (distinct wrapper) and parent host/build/package evidence.
**Scope frozen by task:** only the allocation-provider change (`EnableCpuMemArena=0`); original 65-input quality contract and the old consumed failed run are unchanged. No new native run or install occurred (`newNativeRunFrozen=false`, `deviceOperations=0` in every supplied report).
**Authoritative inputs read:** `plan.md`, `policy-design.md`, `QwenArenaInstrumentation.kt` (sha `bdcc9a5e…`, identical to `candidate-accepted.json` `test-src/…` and `build-result.json` `test-src/…` — byte-for-byte integrated file), `QwenArenaPolicy.kt` (`c86b96b6…`), `baseline-QwenFileInstrumentation.kt`, `check_wrapper.py`, `ArenaWrapperChecks.kt`, `prepare_build.py`, `build_acceptance.py`, `QwenFileReport.kt` (unchanged shared serializer), and the JSON/runtime/lint/package reports.

> Per the standing correction: `Instrumentation.start()` creates the thread that calls `onStart()`; `runOnMainSync` is for a non-App-main caller. This review does **not** re-assert `onStart` as the App main thread and does **not** recommend an extra device smoke merely to repeat that claim. The baseline lifecycle was package/device tested; the **new variant has no Android runtime evidence** (host uses Android/native doubles only).

---

## 1. Review-focus verification (verbatim from plan)

### Focus 1 — Config from report equals actual constructor provider/file bytes
- `QwenArenaInstrumentation.kt:225` `receipt = QwenArenaPolicy.prepare(policyRoot)`; `:226` `receipt = arenaReceipt`; `:244` `provider = arenaReceipt.provider` passed to `OfflineModelConfig`. No other assignment to the constructor provider.
- `arenaSnapshot` (`:150`) sets `config.provider = r.provider` from the same `receipt`.
- Host adapter (`check_wrapper.py:32-35`) installs a Native double that records `Runtime.capturedProvider = m.provider` and asserts `File(m.provider.removePrefix("cpu:")).readText() == "EnableCpuMemArena=0\n"`.
- `ArenaWrapperChecks.kt:1-14` asserts `p.getString("provider") == Runtime.capturedProvider`, `p.getLong("bytes")==20L`, and on-disk `f.readText()=="EnableCpuMemArena=0\n"`.
- **Evidence:** `host-result.json` PASS `arena policy receipt matches actual constructor and fixed exact config`; `ASSERTIONS=4113 FAILURES=0`.

**Verdict: SATISFIED. No defect.**

### Focus 2 — Collision / policy-write failure cannot reach native
- `QwenArenaPolicy.prepare` (`:121-124`) `child.mkdir()` returns false on pre-existing `cpu-arena-off` → throws `ArenaPolicyException`; no receipt, native constructor (`:253`) never reached.
- `ArenaWrapperChecks.kt:15-21` pre-places `…/cpu-arena-off/cpu.config` with `"preserve"`; host PASS `existing policy child blocks native and preserves existing content` with `creates==0 && streams==0 && decodes==0` and `f.readText()=="preserve"`.
- Partial/aborted config write (`:138-142`) throws; no receipt; no native. No fallback/retry/delete in the module (class note `:39-44`).
- **Evidence:** `host-result.json` PASS `existing policy child blocks native…` and `policy snapshot persistence failure blocks constructor`.

**Verdict: SATISFIED. No defect.**

### Focus 3 — Tampering after policy snapshot is rejected before constructor
- Tampering is caught by `QwenArenaPolicy.verify` (`:187-206`): bounded re-read, then bind `receipt.configPath`/`provider`/bytes/sha to the computed values and throw on any mismatch.
- Call site `QwenArenaInstrumentation.kt:228` runs `verify` immediately after the receipt snapshot (`:227`) and **before** the native constructor (`:253`).
- `ArenaWrapperChecks.kt:29-37` injects `EnableCpuMemArena=1\n` at `phase=="allocator-policy"`; host PASS `config changed after policy snapshot is rejected before constructor` with `creates/streams/decodes==0` and non-null `errorClass`.

**Verdict: SATISFIED, with a bounded limitation (see §3 Minor M1 / scope).**

### Focus 4 — Policy snapshot persistence failure blocks constructor and retains error
- `write()` helper (`QwenArenaInstrumentation.kt:118-124`) on any throw sets `state.wrapperPersistErrorClass` (first only) and rethrows.
- `ArenaWrapperChecks.kt:22-28` forces `failWhen` at `phase=="allocator-policy"`; host PASS `policy snapshot persistence failure blocks constructor` with `creates/streams/decodes==0` and non-null `wrapperPersistErrorClass`.
- `receipt` is already set at `:226` before the failing write at `:227`, so the terminal snapshot (`:430`) truthfully still carries the four receipt keys and `wrapperPersistErrorClass`. The error is retained, not overwritten.

**Verdict: SATISFIED. No defect.**

### Focus 5a — Existing failure / cleanup / full-PCM / counter cases remain valid
- Wrapper body is structurally identical to `baseline-QwenFileInstrumentation.kt` except the policy block (`:217-229`) and the snapshot augmentation (`:131-154`). All cleanup/error/counter paths — recognizer release-once (`:367-386`), Activity teardown after cleanup (`:388-415`), `samples?.fill(0f)` (`:361`), `actualConstructors` increment placed immediately before the native ctor (`:251`), `err()` first-error preservation (`:68-73`) — are preserved verbatim.
- `host-result.json` lists all 24 original groups PASS (complete65 with unchanged PCM, strict empty auto, invalid argument, reserved variant/.new/.bak, report symlink, initial write/lock blocks, model/asset corruption, lock after validation, constructor-failure counts attempt, decode-failure/stream-release/case-clock/elapsed-clock/primary-error preservation, persistence-failure after ctor/decode, recognizer-release-failure, destroy-ack-missing, terminal-persistence-failure) plus the 6 arena groups = **30 groups / 4113 assertions**.
- `candidate-accepted.json` shows all 65 PCM asset hashes equal the old `sourceFingerprint` (`d3d1a84…`); `realCaseCore=true`, `hostAndroidAndNativeDoubles=true`, `realNativeCalls=0`.

**Verdict: SATISFIED. No defect.**

### Focus 5b — Packaged runner / application identity remains distinct
- `build.gradle.kts:6,10,13,14`: `namespace`/`applicationId` `io.ondeviceai.acceptance.qwenarena`, `testApplicationId` `io.ondeviceai.acceptance.qwenarena.test`, `testInstrumentationRunner` `io.ondeviceai.acceptance.qwenfile.QwenArenaInstrumentation`.
- `main-apk-manifest.txt:5,17`: package `io.ondeviceai.acceptance.qwenarena`, activity `io.ondeviceai.acceptance.qwenfile.QwenFileActivity` `exported=0`.
- `test-apk-manifest.txt:5,13,14`: package `io.ondeviceai.acceptance.qwenarena.test`, instrumentation `io.ondeviceai.acceptance.qwenfile.QwenArenaInstrumentation` targeting `io.ondeviceai.acceptance.qwenarena`.
- `package-verified.json`: `exactRunnerVerified = io.ondeviceai.acceptance.qwenfile.QwenArenaInstrumentation`, 16 classes, 65 test assets, 4 arm64 native hashes, **zero permissions** on both APKs, `commonSigningCertificateSHA256` shared (debug) — distinct app IDs while reusing the unchanged `QwenFileActivity` class.
- `prepare_build.py:31-34` derives the arena package/runner from the frozen FILE source by exact string replacement; no new model/native/asset introduced.

**Verdict: SATISFIED. No defect.**

---

## 2. "Also check" verification (all PASS)

| Check | Code/Evidence | Result |
|---|---|---|
| All snapshot attempts `probeVariant=cpu-arena-off-v1`, `nativePolicyEffectVerified=false` | `arenaSnapshot:133-134` sets both unconditionally on every one of the 10 `write(...)` calls (`:184,199,214,227,262,295,323,340,430`) | PASS |
| null receipt/provider **before** preparation; exact four receipt keys **after** | `:136-151`: `NULL` when `receipt==null`, else `{provider,configPath,bytes,sha256}`; verified by `ArenaWrapperChecks.kt:45-69` (first snapshot `isNull`, keyset == the four) | PASS |
| `requestedEnableCpuMemArena=false` is requested-policy only | `:152` always `false` in `config` (not inside `allocatorPolicy`); checks assert `!config.getBoolean("requestedEnableCpuMemArena")` | PASS |
| Fresh gate after policy persistence/verify | `:229` `if (!fgGate()) throw …` after `verify` at `:228`; exercised by `ArenaWrapperChecks.kt:38-44` (fresh lock at `allocator-policy` blocks all native) | PASS |
| Unchanged other config literals / model / input / core bytes / cleanup / errors | `:233-247` identical to baseline `:187-201` except `provider`; 65 PCM hashes unchanged; cleanup/errors verbatim | PASS |
| No fallback / retry / policy deletion | `QwenArenaPolicy` throws only, no bare-`cpu` fallback (`:207`), no retry loop; wrapper single prepare/verify/ctor | PASS |
| All initial/failed/terminal writes go through local augmented snapshot helper | Confirmed all 10 `write(...)` calls wrap `arenaSnapshot` (none call `QwenFileReport.snapshot` directly) | PASS |
| Host uses real policy/core/65PCM with Android/native doubles: 30 g / 4113 a; policy 13 / 41 | `host-result.json` (30/4113, `realNativeCalls=0`), `policy-result.json` (13/41, `nativeCalls=0`) | PASS |
| Build 27.240454667s; lint 0 error / 7 warning | `build-result.json:3` `seconds=27.24045466700045`; `lint-results.xml` 7× `severity="Warning"`, 0 errors | PASS |
| Actual package: 16 classes / 65PCM / 4 arm64 hashes / no permissions / same debug cert | `package-verified.json` | PASS |

---

## 3. Findings

### Critical
**None.** No supplied-code defect blocks the next preparation gate. All five review-focus classes and the "also check" list are substantiated by the host (30 groups/4113 assertions) and policy (13/41) evidence, and by distinct-package verification.

### Important
**None as code defects.** The next gate is **not** blocked by the wrapper source. Two scope-level items must be carried forward (they are limits of evidence, not defects):

- **I1 (scope, not defect):** The new variant has **no Android runtime/device evidence**. Lifecycle steps (`onCreate→start()`→`onStart()`→`runAll`, `startActivitySync`, `runOnMainSync` foreground gate, `finish`) are only exercised through host Android/native **doubles** (`realNativeCalls=0`, `deviceOperations=0`). W3 native consumption, RSS savings, true peak, quality, and production readiness are **unproven** (`nativePolicyEffectVerified=false`, `newNativeRunFrozen=false` in `host-result.json`, `policy-result.json`, `candidate-accepted.json`, `package-verified.json`). Per task instruction, **W3 and device/native effect are NOT marked passed.**

- **I2 (scope, not defect):** Tampering strictly between `verify` (`:228`) and the native constructor (`:253`) is not covered by an injected fault and relies on the module's documented exclusive-alias / TOCTOU assumption (`QwenArenaPolicy.kt:39-44`, `policy-design.md` §"TOCTOU limit"). This satisfies Focus 3 as specified (tampering is rejected before the constructor on the prepared/re-verified snapshot), but the verify→ctor window is an acknowledged, un-exercised gap. Acceptable under the stated design; flag for any future frozen contract, not a blocker now.

### Minor
- **M1:** `QwenArenaInstrumentation.kt:152` sets `config.requestedEnableCpuMemArena=false` on **every** snapshot, including the first pre-preparation running snapshot where `allocatorPolicy`/`config.provider` are still `NULL`. This is per `plan.md` (requested-only metadata) and is truthful, but a reader could momentarily read "requested=false" alongside `provider=null` as "applied". Recommend no code change; it is already documented as requested-only in the class header (`:39-41`). Report only for clarity.

- **M2 (inherited, not introduced by this wrapper):** `lint-results.xml` carries 7 warnings — `ChromeOsAbiSupport` (arm64-only `abiFilters`), `OldTargetApi` (target 35), 2× `GradleDependency`, `DataExtractionRules`, `MissingApplicationIcon`, `SetTextI18n` (in copied `QwenFileActivity.kt:39`). These originate from the unchanged shared `main-src`/gradle config, not from the arena change. None are errors; none block the build (`build-result.json` `exitCode=0`). No action required for this task.

- **M3:** The operative native mechanism is the `cpu:<canonical config path>` provider (`:244`, `:154`); `requestedEnableCpuMemArena=false` is a separate, truthful "requested" boolean. Two signals describe the same intent; consistent with `policy-design.md` ("REQUESTED configuration only"). No divergence; note to avoid future double-interpretation.

---

## 4. Unsupported / missing-context areas

- **No new device run / install evidence exists.** `package-verified.json` `deviceOperations=0`, `newNativeRunFrozen=false`. The new variant's on-device lifecycle (foreground gate, `runOnMainSync`, Activity destroy ack) is **unverified on hardware**.
- **Native policy effect unproven.** `nativePolicyEffectVerified=false` everywhere; no JNI execution (`realNativeCalls=0`). Cannot assert the `cpu:<path>` provider is consumed by Sherpa or that `EnableCpuMemArena=0` alters RSS/peak. The host Native double only checks the bytes of the file the provider points to.
- **True peak / production readiness not in scope.** Out of the supplied evidence; the consumed 1.5GiB FILE run remains the only peak observation and is immutable/unchanged.
- **TOCTOU window (I2)** is documented but not fault-injected; see above.
- Baseline host scaffolding hashes (`base-hashes.json`) were not in the supplied context index; the reuse claim rests on `check_wrapper.py:19-24` (it reads the frozen `probes/android-qwen-file/check_wrapper.py` + `WrapperChecks.kt` and only renames `QwenFileInstrumentation→QwenArenaInstrumentation` and `w3-qwen-file/→w3-qwen-arena/`). This is a faithful reuse, but the original scaffold hashes themselves were not provided for independent re-check in this sandbox.

---

## 5. Gate decision

- **Critical/Important code defect blocking the next preparation gate: NONE.**
- The wrapper source correctly implements all five review-focus classes and the requested-policy snapshot contract; host (30/4113) and policy (13/41) acceptance, build (27.24s, lint 0e/7w), and distinct-package verification all pass with the exact integrated bytes.
- The **next preparation gate is not blocked by code**. It remains gated by **scope limits**, not defects: new-variant device runtime evidence, native consumption/savings/quality, true peak, and production readiness are **open** and must not be declared passed. No automatic additional experiment authority follows from this review.
