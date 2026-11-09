# W3 Acceptance Matrix — Remaining Cases (Corrected)

Scope: on-device speech recognition acceptance. The original matrix was a planning
artifact; the execution-status update below records subsequent bounded results.
This doc is NOT a quality approval.

## Current bounded acceptance update — 2026-11-09 11:10 +08

- Apple explicit locale has now been built and exercised on the iPhone with the
  same frozen three public FLEURS Mandarin clips: 3/3 completed, 32 parent checks,
  CER 16/92. Android Sherpa completed five requests covering those three clips,
  CER 44/92. These are bounded observations, not a quality pass. Apple whole clips
  and Android slices/different backends do not establish the cause of the gap.
  See [Apple observation](w3-apple-mandarin-observed-20261109.md),
  [Android observation](w3-mandarin-android-observed-20261109.md) and
  [current reconciliation](w3-acceptance-reconciliation-20261109.md).
- The original scoring rule remains frozen: NFC/casefold, whitespace removed for
  CER, punctuation/digits/script distinctions retained. Removing VAD in one
  isolated control worsened CER to 53/92. It is not an accepted fix. Upstream
  v2.0.1 supports dynamic language; the constructor-only `zh` candidate was not
  run or integrated. See [native language check](w3-whisper-language-source-check-20261109.md).
- W8 current-source physical ASR observation (four requests, 24 same-PID samples)
  is accepted for its bounded scope. The distinct sustained Sherpa candidate ended
  with upstream `empty_model_response` and no files; it has not been built or run.
  The separately accepted TTS twenty-cycle result does not close ASR resources.
- R1/R2 and Android R3 still require applicable system speech resources and a fresh
  microphone start cue. Keep the reconciled prior iPhone lifecycle evidence;
  do not repeat already accepted file runs, fixtures, scorers or resource tools.
- W3 remains open. W1/W2/W4/W5 are closed; W6 is expert-owned, W7 has outstanding
  multiperson/PAD data gates, and W8 resource budgets remain open. Historical
  updates below do not override this current status.

## Historical bounded acceptance update — 2026-11-09 08:24 +08

- Apple explicit locale is now integrated, source-reviewed, unsigned-build checked
  and exercised by 12 host pure-parser cases. It has not been installed or launched;
  App failure-report/auth and Chinese recognition remain unverified. See
  [locale prerequisite](w3-apple-locale-prerequisite-20261109.md).
- W8 now has one accepted TECNO/API35 four-request current-source observation with
  24 PID-bound RSS/thread/raw-CPU samples and verified alias/stage restoration.
  GPU/energy/sustained budgets remain open. See
  [physical observation](w8-physical-asr-observation-20261109.md).
- Independent bounded evidence review approved both slices with zero issues.
  W3 remains open. Chinese natural-speech source freezing is next; neither a
  new Chinese profile nor Chinese quality has been accepted. Old running/blocked
  notes below are historical and superseded only for these exact slices.

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
| R4 | zh-CN / noisy file quality | Frozen public FLEURS / controlled-noise files (no mic) | android-ondevice-asr (Sherpa) / ios-ondevice-asr (Apple file) | partial: both platforms completed Mandarin and synthetic-English noise; Mandarin quality attribution/representative real noise open | Preserve CER44/92 Android vs16/92 Apple with backend/slicing limits; no-VAD53/92 is not a fix; file-capable backends only |
| R5 | W8 stable-path resource attach | Own App PID | android-ondevice-asr / ios-ondevice-asr | partial: emulator and four-request physical observation accepted; sustained Sherpa candidate not delivered; GPU/energy/budgets open | Preserve24physical samples and prior historical resources; no repeat accepted batch; own-PID binding and honest paced-idle scope |

## Preserved / blocked states (NOT runnable cases)
- TECNO en-US resources: unavailable / unverified, no live download performed. Blocked
  state preserved; no assertion that such resources exist. R1 is mic-only and waits
  on an authorized mic session + verified resources.
- Public Mandarin and controlled-noise fixtures have been frozen and run; do not
  reacquire them. Representative real-world noise and reliable Mandarin quality
  attribution remain open. Preserve source/reference/licence and scoring rules.
- W8 current-source four-request physical run has completed. Earlier vanished-device
  or unauthorized notes describe their original attempts, not current acceptance.
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
distinct sustained resource protocol after an actual candidate passes review/build;
the already accepted four-request attach is not repeated. R1/R2/Android R3 wait on
a fresh authorized mic session and verified resources. R4 continues quality attribution
and representative real noise; existing frozen Mandarin inputs are available. This
status-document correction itself performs no device run or quality acceptance.
