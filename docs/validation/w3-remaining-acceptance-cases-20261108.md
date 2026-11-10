# W3 Acceptance Matrix — Remaining Cases (Corrected)

Latest physical observation 2026-11-10 11:52 +08: Apple FILE ASR completed one frozen
20-cycle/40-measured-recognition batch in626.212s including593.928s paced idle;428parent
checks, double terminal-read equality and44original-App restoration passed. This advances
R5's Apple measurement, not its budget: system Speech-service memory/true peak/GPU/energy
remain unmeasured and no production threshold exists. No TTS/Sherpa rerun. See
[physical ASR acceptance](w8-apple-asr-sustained-20261110.md).
R4's reproducible public evidence and pending decision are in
[the quality packet](w3-quality-disposition-ready-20261110.md); R4/R5 remain open.

Latest scope disposition 2026-11-10: the user explicitly accepted the current TECNO KM9/API35
system-ASR limitation within W3 acceptance. R1 remains attempted/failed; R2 and Android R3 remain
not executed. Their current TECNO scope dispositions are complete as accepted known limitations,
not functional passes or a general Android exclusion. No retry, unlock wait, download or capture task
remains active for these rows. See [scope decision](w3-tecno-system-asr-scope-disposition-20261110.md).
R4 quality and R5/W8 resource budgets remain open. The approved resource request returned actual
error15, readiness unverified; the consumed one-shot authorization is unchanged. See
[observed resource result](w3-system-resource-provision-observed-20261110.md).

## Current execution boundary — 2026-11-10

Fixed-prefix diagnosis is complete. One explicitly selected system microphone request failed
MISSING_ASSETS; R1 remains attempted/failed and Android R2/R3 were not executed. The subsequent
user scope decision accepts these current TECNO limitations; it does not supply recognition evidence.
AMI's original fail-fast batches remain failed, but a new frozen single-clip contract covered only
the three previously unattempted pairs. Six platform/clip pairs now have observations across distinct
runs: Android1/3 recognized and2 no-speech; Apple2/3 recognized and1 platform1110. No replay,
new capture, parameter sweep or quality pass. See [current W3](../roadmap/W3-speech.md) and
[AMI supplement](w3-ami-unattempted-observed-20261110.md). R5 still references W8.

## Historical execution boundary — 2026-11-09 20:25 +08

W3 remains open. Do not sweep models. Only run a discriminating check when the
source, license, reference text, and exact input hashes are fixed. The three
existing Apple WAV payloads have now been verified byte-identical to the Android
PCM inputs after decoding; the parent receipt is
`artifacts/verification/w3-cross-platform-input-parity-20261109/parent-acceptance.json`.
The existing CER values and backend/segmentation limits remain unchanged. The
older “single-clip parity candidate is running” statement below is superseded:
the candidate is terminal and accepted, base experiment is terminal and rejected,
and the current queue reports no active helper or W3 task to poll/relaunch. See
the latest queue（本机留存：`.omx/artifacts/current-helper-queue-20261109-2025.json`）
and latest progress（本机留存：`docs/validation/progress-20261109-2025.md`）.

Scope: on-device speech recognition acceptance. The original matrix was a planning
artifact; the execution-status update below records subsequent bounded results.
This doc is NOT a quality approval.

## Historical bounded acceptance update — 2026-11-09

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
  remains accepted. The later primary-owned sustained Sherpa probe is now built,
  physically exercised and accepted in `c213a4b`:20cycles/40calls/600.503s paced,
  265parentchecks, second raw read exact, aliases/stage removed and ordinary paths
  preserved. It supersedes the earlier hy4free no-file candidate status; its
  failure remains historical. The ordinary App also returned to the foreground
  after the post-restoration ADB authorization gap. See [sustained acceptance](w8-sherpa-sustained-20261109.md).
  GPU/energy/production budgets remain open; no equivalent ASR batch is needed.
- R1/R2 and Android R3 still require applicable system speech resources and a fresh
  microphone start cue. Keep the reconciled prior iPhone lifecycle evidence;
  do not repeat already accepted file runs, fixtures, scorers or resource tools.
- W3 remains open. W1/W2/W4/W5 are closed; W6 is expert-owned, W7 has outstanding
  multiperson/PAD data gates, and W8 resource budgets remain open. Historical
  updates do not override the newer current execution/scope boundary above.

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
| R1 | On-device system en-US recognition | Real mic, user start cue | android-ondevice-asr | current TECNO scope resolved: accepted known limitation; actual request FAILED/BACKEND_UNAVAILABLE/MISSING_ASSETS | No successful microphone recognition evidence; no new capture/download authorized; no retry required under accepted scope |
| R2 | Mic: background speech handling | Real mic, user start cue | android-ondevice-asr | current TECNO scope resolved: accepted known limitation; actual case not executed due to resources | No background-speech functional pass; synthetic noise does not replace this case |
| R3 | Mic: interruption / cancel / reentry | Real mic, fresh start cue for new capture | android-ondevice-asr / ios-ondevice-asr | current TECNO Android remainder resolved as accepted known limitation, not executed; prior iPhone scoped evidence retained | No active Android capture lifecycle pass invented; existing iPhone scope unchanged, no equivalent rerun |
| R4 | zh-CN / noisy file quality | Frozen public FLEURS / controlled noise / AMI far-field English (no mic) | android-ondevice-asr (Sherpa) / ios-ondevice-asr (Apple file) | observations complete for fixed AMI pairs across distinct runs, with errors; quality open | Preserve Chinese CER44/92 vs16/92 and backend/slicing limits; prefix diagnosis complete; AMI Android1/3/Apple2/3 recognized, no-speech/1110 retained; no approved quality threshold |
| R5 | W8 stable-path resource attach | Own App PID | android-ondevice-asr / ios-ondevice-asr | Apple20cycle/40call/626.212s andSherpa20cycle/40call/600.503s sustained observations accepted; missing-field/budget decisions open | Preserve all original windows,898Apple measured RSS samples and26222Sherpa RSS samples; no equivalent rerun; own-PID/OS-service boundary and paced-idle scope explicit |

## Preserved / blocked states (NOT runnable cases)
- Current TECNO en-US resource readiness is unverified after the approved one-shot SDK request
  returned error15. User accepted this configuration's system-ASR limitation for R1/R2/Android R3.
  Raw failed/not-executed states remain; no resource ready or mic pass is asserted. No unlock wait
  or repeat request remains in the active scope; the one-shot download authorization is consumed.
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

## Historical next action (superseded 2026-11-09 19:50 +08)
The sustained current-source Sherpa measurement is completed; do not redispatch
it or the accepted four-request attach. R1/R2/Android R3 wait on a fresh authorized
mic session and verified resources. R4 continues quality attribution and
representative real noise; existing frozen Mandarin inputs remain available.
The single-clip SDK/native parity task is terminal and accepted in
`w3-native-parity-control-20261109.md`; the fixed Whisper base comparison is
terminal and rejected for promotion. Do not poll or relaunch either. W8 follows
the newer user gate in the current queue: wait for an authorized physical-device
workflow and existing valid signing identity/profile before further Apple ASR
code or physical runs. This historical status block performs no inference.

## 2026-11-09 13:04: native parity control accepted

[One public unsliced2006 SDK/directnative experiment](w3-native-parity-control-20261109.md) passed44parent checks andrestoration. Actualpreparedfloat32LESHA/countmatchedindependenthost92928samples; exactsamehypothesiswithpriorSDKbaseline. Currentpath didnotaddtextdifferenceonthisclip; model-onlyrootcause/generalSDKcorrectnessnotproved. PrimaryrawCER44/92unchanged. Nextsinglecandidateanalysisfreehy3running; no production/scorer/modeldefaultchange.

Historical note only: the phrase “Nextsinglecandidateanalysisfreehy3running” records the 13:04 snapshot. The corresponding fixed base candidate later completed and was rejected at 14:26 (see [base disposition](w3-whisper-base-candidate-20261109.md)); no such W3 task remains active or should be restarted.
