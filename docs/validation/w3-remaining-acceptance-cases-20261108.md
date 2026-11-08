# W3 Acceptance Matrix — Remaining Cases (Corrected)

Scope: on-device speech recognition acceptance. The original matrix was a planning
artifact; the execution-status update below records subsequent bounded results.
This doc is NOT a quality approval.

## Reconciliation update — 2026-11-09 07:41 +08

- Free hy3 completed the independent public-evidence gap audit; the parent then
  verified current Swift source continuity and the historical status-receipt hashes.
  Existing iPhone lifecycle success is retained without a new recording. This
  does not pass every interruption/authorization/TTS case or a production budget.
- The Android locale prerequisite now has a completed default 3-clip/4-request
  regression plus exact native rejection for three malformed locale values.
  Temporary test aliases and staging were removed; Chinese quality remains open.
- A separate free-hy3 Apple file-probe locale candidate has been dispatched to
  prepare the Chinese file path. No source integration, new fixture, build or
  Chinese runtime pass is implied by dispatch.
- Latest ADB: real phone unauthorized; existing emulators available. W8 foreground
  candidate failed source review and is under scoped free-hy4free correction.
- See [parent reconciliation](w3-acceptance-reconciliation-20261109.md). Raw prior
  matrix notes below are historical; actual source/backend/input boundaries govern.

## Execution update — 2026-11-09 00:52 +08

- R4 is partially exercised: the same three public English synthetic-20-dB files
  completed on Android emulator/Sherpa and iPhone/Apple file recognition. Fixture
  source/tests and bounded evidence were accepted in `a39f3c9`; Chinese and
  real-world-noise validation remain open. See
  [bounded noise acceptance](w3-controlled-noise-accepted-20261109.md).
- R5 has an accepted emulator observer/window engineering slice in `8ae4a1b`.
  The physical attempt froze before completed requests, was not accepted and was
  restored. Physical resource/GPU/energy/budget gates remain open. See
  [observation-window acceptance](w8-asr-window-accepted-20261109.md).
- R1/R2 and the remaining Android microphone portions of R3 remain unverified
  and need a fresh explicit microphone start cue and applicable system resources.
  Prior iPhone cancel/reentry/background-cancel and one instrumented close/drain
  result are retained after source/receipt reconciliation; they are not
  classified as never executed. See the November 9 reconciliation below.

The original blocked-input and next-action notes below are historical planning
context; this update and the linked receipts govern their current execution status.

## Corrections vs the prior W3 doc
- Kept original removals: row 1 (observer path stabilization / 17-test retest) and
  row 8 (generic integration) are duplicate / non-actionable already-accepted work;
  no new path, tool, or download is opened here.
- API references corrected to the supplied source: `SpeechRecognitionClient` and
  `SpeechRecognitionRequest(locale)` (NO `audioFile` field). `SpeechBridgeAsrRequest`
  is NOT used. Swift `SpeechMicrophoneRecognitionRequest` exists on iOS only.
- System ASR backend (`android.speech.recognizer` / `AndroidSpeechRecognizerBackend`)
  is MICROPHONE-ONLY: `recognize` -> `RecognitionSession.recognize` ->
  `engine.start(locale)` -> `SpeechRecognizer.startListening(intent)`.
- File-fixture rows must NOT use the system Android backend; R4 uses the file-capable
  Sherpa backend (Android) or `AppleSpeechRecognizerBackend` file path (Apple).
- No generic new authorization is granted for file fixtures; no download/capture is
  authorized in this pass.

## Remaining concrete cases (<=5 rows)
| ID | Case | Input | Owner | Status | Primary assertion |
|----|------|-------|-------|--------|-------------------|
| R1 | On-device system en-US recognition | Real mic, user start cue | android-ondevice-asr | unrun (needs user) | System backend MICROPHONE-ONLY: `SpeechRecognitionRequest(locale)` (no audioFile); `SpeechRecognizer.startListening`; TECNO en-US resource unavailable/unverified; error13/MISSING_ASSETS vs nonempty transcript + exact backend; own engine cancel/destroy bounded; NO no-mic path |
| R2 | Mic: background speech handling | Real mic, user start cue | android-ondevice-asr | unrun (needs user) | Background speech handled per spec; no SHA of raw input (no file exists) |
| R3 | Mic: interruption / cancel / reentry | Real mic, fresh start cue for new capture | android-ondevice-asr / ios-ondevice-asr | partial: prior iPhone scoped lifecycle retained; Android capture/interruption gaps open | Android `SpeechRecognitionRequest(locale)` + session cancel/reentry; iOS Swift `SpeechMicrophoneRecognitionRequest`; reacts to interrupt/cancel, re-enters cleanly |
| R4 | zh-CN / noisy file quality | Offline file fixture (no mic) | android-ondevice-asr (Sherpa) / ios-ondevice-asr (Apple file) | partial: synthetic-English function accepted; Chinese/real-noise open | File transcription via file-capable Sherpa backend (Android) or `AppleSpeechRecognizerBackend` file path (Apple); NOT `AndroidSpeechRecognizerBackend` |
| R5 | W8 stable-path resource attach | Own App PID | android-ondevice-asr / ios-ondevice-asr | partial: emulator window accepted; physical budgets open | Observer attaches own App PID; Android run-as reads only permitted process; iOS uses existing mechanism |

## Preserved / blocked states (NOT runnable cases)
- TECNO en-US resources: unavailable / unverified, no live download performed. Blocked
  state preserved; no assertion that such resources exist. R1 is mic-only and waits
  on an authorized mic session + verified resources.
- Cross-language / noisy public fixtures: NOT supplied. Exact source/reference/licence
  must be frozen BEFORE any run. R4 stays blocked until then; uses file-capable backend.
- W8 staged ASR current 9 sources match the parent baseline; device vanished before
  install, so PENDING HARDWARE, not a nonexistent tool.
- Resource observer is Android-only (run-as). Apple attach uses existing iOS mechanism;
  no new implementation opened.

## Authorization / evidence notes
- Mic sessions (R1/R2/R3) require the actual user's participation and explicit start
  cue; an existing mic grant alone does NOT authorize recording. No download/capture is
  authorized now.
- File-fixture R4 needs NO microphone; it uses a file-capable backend, not the system
  `AndroidSpeechRecognizerBackend`. No generic new authorization is documented.
- No SHA original-input assertion for raw mic input, because no source file exists.
- Distinction maintained: framework resource failure (error13/MISSING_ASSETS, missing
  fixture, vanished device) is separate from an SDK defect (wrong behavior of the
  supplied API). Offline requirement (file cases run without network) is separate from
  measured network isolation.

## Minimal next action (primary)
Without a user present: primary owner runs W8 current-source9 physical ASR/Sherpa
publicPCM resource attach when the device returns — NOT the impossible system-file R1.
R1/R2/R3 wait on an authorized mic session and verified resources. R4 waits on a frozen
fixture; R5 waits on hardware return. No execution is included in these docs.
