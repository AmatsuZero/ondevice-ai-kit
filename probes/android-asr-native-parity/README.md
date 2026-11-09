# Native parity control — one-pass RUNBOOK (W3, 2026-11-09)

## Purpose (bounded)
Distinguish **SDK session/orchestration** (`SherpaAsrBackend.transcribe` →
`SherpaSession` → `WhisperSpeechGate.prepare` → `NativeWhisper` decoder) from a
**direct native Whisper** (`OfflineRecognizer`) for the one PUBLIC unsliced Mandarin
clip2006 whose errors persist with and without VAD (see supplied
`w3-no-vad-control-20261109.md` and `w3-vad-diagnostic-parent-20261109.md`).
Exactly ONE SDK call + ONE direct call. NOT a general scorer/observer, NOT a
production fix, NOT a quality approval, NOT a candidate picker, NOT a broader
rerun, NOT a language/model/crop sweep, no mic / network.

## Owned artifacts (candidate cwd)
- `NativeParityInstrumentation.kt` — `io.ondeviceai.demo` `Instrumentation`; 1 SDK + 1 direct call.
- `prepared-fingerprint-replacements.json` — ONE exact reversible old/new replacement for the FILE transcribe expression in an **isolated copy** of `SherpaAsrBackend.kt` (production source unchanged).
- `RUNBOOK.md`, `DELIVERY.md`.

## Parent-owned (NOT done here)
Build/install/device, applying and reversing the isolated-copy patch, invoking with
args, and collecting the durable report. Only the primary agent integrates and runs;
candidate delivery is source + plan + docs only.

## Fixed, frozen facts
- Input `context.assets/mandarin-2006.pcm`: 264960 bytes / 132480 s16le 16k mono
  samples; SHA-256 `333699e4542733689279698f52497c652dd23ed063f81a2ab861f6ab533f02b9`.
- modelDir (alias `noBackupFilesDir`): `sherpa-whisper-tiny-65176e2d-vad9e2449e1`;
  `SherpaWhisperModel.verify()` over the 4 existing files; no new model / download.
- Historical preparation `[15744,108672)` ⇒ 92928 float samples (VAD diagnostic
  parent receipt, clip 2006 / slice 0).
- Fingerprint file (exact new file, absent-before required):
  `odai-native-parity-prepared.txt` in the alias modelDir. Tiny numeric text, one
  line: `preparedFloat32LeSha256=<64 lowercase hex>|preparedSampleCount=<int>`.
  NO raw PCM / waveform persisted. float32 LE = `ByteBuffer.allocate(n*4)
  .order(LITTLE_ENDIAN).putFloat(each)`; SHA-256 of those bytes, lowercase hex.
- Report: `files/odai-native-parity-reports/<runId>.json`, `android.util.AtomicFile`,
  exclusive (`createNewFile()`, `.new`/`.bak` reserved), pretty `toString(2)`.

## One-pass acceptance (single invocation; no replay)
1. Apply the ONE replacement to an isolated copy of `SherpaAsrBackend.kt`; build the
   alias APK. Verify `prepared-fingerprint-replacements.json` `old` occurs exactly
   once, reverses exactly, and leaves zero residue. Production source unchanged.
2. Ensure the fingerprint file is **ABSENT** in the alias modelDir before
   `am instrument` (parent pre-run check). Supply `runId` (`[A-Za-z0-9-]{1,80}`),
   `sourceFingerprint`, `installedAppSha256` (both `[0-9a-f]{64}`).
3. ONE `am instrument` run. Read back the SAME runId terminal report; do NOT infer
   success from host exit code alone. No replay on non-terminal; keep any
   failed/partial report.
4. Verify in the report: `terminal=true`, `status="completed"` (or `"failed"` with a
   retained partial); `sdkAttempts=1`, `directAttempts=1`, `totalDecodeAttempts=2`;
   `microphoneStarts=0`, `audioPersisted=false`, `qualityAccepted=false`,
   `productionReady=false`; `callerSamplesUnchanged=true`;
   `fingerprintAbsentBefore=true`; `preparedFingerprintMatched=true` (BOTH
   `preparedSizeEqual` and `preparedShaEqual` true); `samePid=true`;
   `sdkCloseSucceeded=true`, `directStreamReleased=true`,
   `directRecognizerReleased=true`, `foregroundTeardownSucceeded=true`,
   `keepScreenOnRestored=true`; bounded elapsed fields present; BOTH hypotheses
   recorded; `match` / `parity` boolean present; `parityIsNotQualityProof=true`.
5. Confirm no third decode, no retry, no new model, no scorer/CER change, no
   production edit, no user settings change (only own Activity KEEP_SCREEN_ON bit).
6. Reverse the replacement on the isolated copy; remove only the new aliases/stage;
   ordinary App paths unchanged; model hashes and frozen PCM retained.

## Interpretations (diagnostic findings, NOT approvals)
- `parity=true` (exact SDK text == direct text): on THIS one prepared clip, the SDK
  session/orchestration layer is indistinguishable from a direct native decode. It
  does **not** prove SDK/model quality, accuracy, CER, or production fitness.
- `parity=false` (mismatch): a **completed experiment** (`status="completed"`,
  `terminal=true`) with a diagnostic difference between paths that still requires
  attribution; native state/configuration/determinism can also contribute. It is NOT an automatic quality
  approval and NOT a candidate-picked output. Keep both hypotheses verbatim.
- `preparedFingerprintMatched=false` (size OR float32LE SHA differs): **FAIL**. The
  observed prepared waveform did not equal `rawFloats.copyOfRange(15744,108672)`.
  The run terminates with a retained report and NEVER silently adjusts ranges or
  performs the native decode. Investigate gate/VAD/model identity; do not rerun with
  adjusted ranges.
- `status="failed"`: an execution error (input SHA/length, model verify, SDK call,
  fingerprint read/shape, direct decode, lifecycle, persistence). Retained partial
  report + `errorClass`; Binder failure. Not a parity result.
- Timing caveat: the fingerprint I/O is inside the SDK `transcribe` duration;
  elapsed bounds are evidence, NOT a latency/performance comparison. Parity is a
  path-equality control, not a W3 quality gate.

## Explicitly NOT implemented (already rejected as low-value)
- Optional crop-vs-VAD control: NOT implemented. The no-VAD bypass is rejected as a
  production fix; do not change slicing, scoring or thresholds.
- Constructor-`zh` control (`OfflineWhisperModelConfig(language="zh")` instead of
  `"en"` + `setConfig(language="zh")`): NOT implemented. Constructor language stays
  `"en"` matching production `NativeWhisper`; `setConfig(language="zh")` is applied.

## Pitfall sections applied (`context/agent-pitfalls.md`)
- §1 l.12: public-SDK foreground probe, fail-closed on start/resume/teardown; avoid
  `Activity.isResumed` (SystemApi) — use `ForegroundProbeActivity` flags.
- §5 l.242: catch errors INSIDE `runOnMainSync` lambda (AtomicReference), handle off
  Main; keep the outer catch for `runOnMainSync` itself.
- §5 l.239: Android JSON API — no `JSONObject.keySet()`; fingerprint is plain text,
  report uses `put` / `toString(2)` / `optString` only.
- §2 l.115 / §1 l.16: compile-ready Kotlin; no `val` reassigned across try/catch
  branches (`val cannot be reassigned`); no invented APIs.
- §16 l.509–511 / l.513–515: custom Instrumentation `onCreate.start`,
  `finish(code, Bundle())`, `java.util.concurrent.CountDownLatch`, errors attached,
  exact PID, passed/attempted separated, no stale snapshot overwrite.
- §9 l.313 / l.318: Binder success only after durable final AtomicFile write; read
  back same-run terminal report, not host exit 0.
- §16 l.632: pretty-printed report (no 2000-char single-line truncation).
- §1 l.578–583 / §16 l.599–601: byte-for-byte reversible old/new replacement,
  unique occurrence, zero residue, parent verifies count/context.
- §12 l.343: close-in-finally / session drain; release on every exit.
- §16 l.611 / l.503: no microphone; file fixture via Sherpa backend; prepared
  length within the ≤160000 gate.
- §16 l.638–641 / §9 l.640: numeric diagnostic in alias modelDir, absent-before,
  same-run SHA, fsync-inside-transcribe timing caveat; no "all errors = VAD" claim.

## Parent integration and actual acceptance — 2026-11-09

The retained Kotlin incorporates two separate free hy3 corrections after source review: final completion after teardown requires all close/lifecycle/fingerprint/samePID guards; report persistence error changes both the Kotlin variable and serialized JSON status to failed before a second write. Final source SHA256 `db1dfcafb3a691d801164379990399fefcf9ea99e3069af26febf783eb4297c6`.

Parent offline build: 15.702s,66tasks executed, main/test Kotlin compiled; APK signatures/target/runner/current9sourcehashes verified. Single TECNO API35 experiment PID15967 completed44parentchecks, preparedSHA `e1c4271fe26c52b536ce7fd569606defb5023d5363ac57559747c975b41af13c`,92928samples. Both outputs exactly equal the prior unsliced2006 baseline. Two terminalreads byte-identical; models and ordinary6packagepaths preserved; onlynewaliases/stage removed. Exact build/device/report source in `artifacts/verification/w3-native-parity-control-20261109-1234/`. Full result/limits in `docs/validation/w3-native-parity-control-20261109.md`.

This probe depends on the existing public `ForegroundProbeActivity` host used by the source-bound acceptance shell; production source is copied9files, and only `SherpaAsrBackend` copy gets the reversible fingerprint replacement. It does not modify production source or automatically select a model.
