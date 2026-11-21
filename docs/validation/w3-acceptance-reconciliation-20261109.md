# W3 acceptance reconciliation — 2026-11-09

## Current scope — 2026-11-10 after TECNO disposition and Apple measurement

R1–R3 for the current TECNO system-ASR configuration are accepted known limitations,
following the user's explicit [scope decision](w3-tecno-system-asr-scope-disposition-20261110.md).
R1 remains failed and R2/Android R3 remain unexecuted; no resource or capture retry is pending.
The one authorized resource call returned actual error15, readiness unverified. Apple FILE ASR's
20-cycle/40-measured-call sustained observation is now accepted with its explicit sampling limits;
see [the physical receipt](w8-apple-asr-sustained-20261110.md). R4 quality and R5 missing-field/budget
dispositions remain open. Earlier resource blockers and next-action proposals below are historical.

## 2026-11-10 — AMI unattempted platform/clip pairs now observed

The fixed-prefix subtask remains accepted; system microphone R1 still failed MISSING_ASSETS.
Under a new frozen single-clip contract, only the original three unattempted platform/clip pairs
were run once: Android C-294 typed no-speech, Android B-146 recognized, Apple B-146 recognized.
All six pairs now have real observations across distinct runs: Android1/3 and Apple2/3 recognized.
The original fail-fast batches remain failed, with no replay of their attempted pairs. New reports
were double-read, released and restored; Apple now has a real pre-install/full-restored inventory.
No quality pass, numeric threshold, microphone or W8 resource pass is inferred. See
[new AMI observation](w3-ami-unattempted-observed-20261110.md) and [current W3](../roadmap/W3-speech.md).
AiAi is the actual system on-device service; it differs from the Google TTS voice default. Inspected
exported settings exposed only licenses; the language-pack Activity is non-exported and captions
Activity disabled. No agent download, settings change or capture was requested.

## 2026-11-10 09:28 +08 — prefix completed; microphone and AMI remain open

The explicitly authorized one-time local correction completed the isolated fixed-prefix experiment:
62 host assertions, offline build/lint, 97 parent checks and the two-arm device report were accepted.
Restoring 152 ms changed the local output from `也是新天章的开始` to `结束也是新天将的开始`;
there is no independent crop reference or overall CER-improvement claim. See [accepted prefix](w3-prefix-control-accepted-20261110.md).

The user then supplied a fresh microphone start cue. One ordinary-App en-US request, explicitly
selecting `android.speech.recognizer`, ended FAILED / BACKEND_UNAVAILABLE / MISSING_ASSETS.
Only state/error metadata was retained; no natural audio/transcript or actual platform numeric error
was recorded. R1 did not pass; active R2/R3 cases remain blocked. No automatic download or recapture.

The user selected official CC-BY-4.0 AMI far-field English. Three clips from ES2002a/Array1-01 were
frozen before inference (12.8 seconds total, identical decoded PCM across platforms). Android first
request returned SpeechNotDetectedException with zero successful rows; Apple first clip produced
`I'm marketing`, then clip2 failed kAFAssistantErrorDomain/1110. Both accepted runners stop on the
first error, leaving Android clips2/3 and Apple clip3 unattempted. The all-row functional gate is NOT
met; no full-cohort or paired quality score is asserted. Both terminal reports were read twice with
identical bytes; aliases/staging were removed and ordinary Apps restored. Apple currently reports
iOS27.2.1. The old hardcoded probe commit field and missing pre-install Apple app inventory are
explicit limitations, not silently repaired evidence. See [AMI observation](w3-ami-pilot-observed-20261110.md)
and [microphone contract/result](w3-mic-noise-next-20261110.md). W3 remains open/unverified; no model,
gain, VAD or prompt sweep and no replay of this frozen batch.

The older entries below retain their original timestamped failure state; they no longer mean that
the prefix correction route is unanswered or that the prefix experiment is unimplemented.

## 2026-11-10 07:18 +08 — reference drift and invalid reports confirmed

The retained CBC and local plan candidates omit the two canonical FLEURS
quotation marks. With the same unchanged scorer and existing full-clip
hypothesis this changes 15/28 to 13/26; it is reference drift, not improved
recognition. Separately, a byte-identical pure extraction of the original
serializer compiled and ran locally, and both success/failure outputs failed
strict JSON parsing at a trailing comma. The rejected Android candidate was
not repaired or compiled; no recognizer/device was run. Exact crop hashes and
shared-suffix preservation were independently rechecked. These are additional
acceptance blockers beyond the earlier source/lifecycle findings. Preserve the
full canonical reference/hash and strictly parse both report branches on the
next authorized correction; do not score partial crops against a full-clip
reference as evidence of quality improvement. See [contract review](w3-prefix-candidate-contract-review-20261110.md).

## 2026-11-10 06:40 +08 — local candidate corrections remain rejected

The fixed FLEURS discriminator is unchanged; no parameter sweep or recognition
run occurred. Four complete local Ollama requests (`qwen3-coder:30b`) produced
candidate artifacts, but none met acceptance: the first patch inserted an
import inside the header comment and only asserted hash constants; the next
plan-file candidate added streaming size/SHA validation but omitted tests for
actual disk bytes; the first test file attempted to instantiate a Kotlin
`object` and did not alter file contents; its one focused correction used
`assert(false)` for expected failure (unsafe when JVM assertions are disabled)
and still lacked `finally` cleanup. All originals and parent dispositions are
retained under `.omx/artifacts/ollama-w3-{prefix-fix,plan-hash-correction,
model-hash-tests,model-hash-test-fix}-20261110-*`. No candidate was integrated,
built, tested, installed, or run. Stop further local model retries for this
slice pending a viable authorized implementation route. This remains the
single fixed public-source discriminator; no production VAD/pre-roll change.

## 2026-11-10 06:02 +08 — fixed-slice discriminator remains unimplemented

The only retained discriminator is still the fixed Google FLEURS 1906 second
slice `83040,166080)`: baseline `[2432,36480)`, candidate `[0,36480)`, with
the public source/license/reference and distinct full-clip/slice hashes bound
as recorded below. No existing probe supplies this exact two-arm crop control.

Source inspection of `probes/android-natural-file-asr/NaturalAsrInstrumentation.kt`
confirmed that `computeSlices` accepts only the full clip (one whole slice up to
160,000 samples, otherwise an integer midpoint split), and each segment is sent
through `SherpaAsrBackend.transcribe`. The production backend calls
`WhisperSpeechGate.prepare` before decoding. Therefore that probe cannot be
reused to isolate the 2,432-sample prefix by changing only invocation arguments;
using it would reapply VAD and confound the comparison. No source or device run
was started. The isolated helper candidate from 2026-11-10 was rejected after
source review for compile, model-hash verification and lifecycle-report defects;
the correction path and current authorization boundary are in
[06:06 progress（本机留存：`docs/validation/progress-20261110-0606.md`）. W3 remains open and no sweep or
production VAD change is planned.

## Offline error alignment — 2026-11-09 19:20 +08

An offline re-alignment of the existing fixed FLEURS reports reproduced every
per-clip CER count. Tiny remains 44/92 (12 punctuation-related, 2 digit-related,
30 other edits); no-VAD remains 53/92 (12, 1, 40); rejected base remains 43/92
(11, 1, 31); Apple Speech remains 16/92 (11, 1, 4). These are descriptive
categories from deterministic Levenshtein alignments; punctuation/digits remain
in the unchanged scorer. This does not explain root cause or establish quality.
The report binds dataset revision/license, per-platform input hashes and source
artifact hashes: [alignment record](w3-mandarin-error-alignment-20261109.md).
No model/device run occurred. W3 remains open and future quality attribution
requires a newly approved, representative fixed audio/reference set; no sweep
or repeated existing run is planned.

### VAD crop-edge acoustic check — 2026-11-09 22:52 +08

Read-only analysis of the same fixed Google FLEURS inputs and the durable VAD
receipt tested whether every removed edge was effectively silent. No recognizer,
VAD, model, device, audio playback, score or threshold was run or changed. The
three PCM SHA-256 values remain `8c899634…4912a` (1906),
`333699e4…f02b9` (2006), and `b5176ee7…ae33c` (1883), as fully recorded in the
[frozen input table](w3-mandarin-error-alignment-20261109.md).

The 1906 second-slice VAD crop removed its first 2,432 samples (152 ms). That
region's RMS is 130.083 / int16, close to 139.882 in the retained range, so it
cannot be described as silence from amplitude alone. A fixed 25 ms frame / 10
ms hop mean-removed autocorrelation check found periodic peaks of 0.50–0.62 at
80–400 Hz in several frames of the removed region. This is **voiced-like
acoustic activity, not verified speech or word alignment**; the heuristic cannot
establish which phoneme/text, if any, was removed. Other cropped edges were
substantially lower energy than their retained ranges (for example, 2006 head
RMS 3.495 vs 203.102; 1883 first-slice head 29.218 vs 1299.433), but low energy
also does not prove silence.

For fixed clip 2006, whose VAD preparation discards 0.984 s at the head and 1.49
s at the tail, the existing no-VAD run returns the exact same transcript and
14/24 errors as the VAD run. Thus those crops do not explain or repair the
observed 2006 errors. On split clips 1906 and 1883 the no-VAD hypotheses differ
and corpus CER worsens, so the existing experiment does not isolate the
possible 1906 seam-prefix effect. Current evidence narrows the next useful
discriminator to a single fixed-input control restoring only that 152 ms prefix
while retaining the same suffix crop, model, locale and all other settings.
Until such a control is accepted, W3 cause remains unisolated; this check makes
no quality-pass or production-change claim.

### Existing same-waveform boundary evidence — 2026-11-09 20:10 +08

Clip `fleurs-cmn-test-2006` is 132,480 samples, below Android's 160,000-sample
limit, and Android used one request. Tiny and no-VAD produce identical
transcripts at 14/24; the accepted same-prepared SDK/direct-native test also
produces that same transcript. Apple whole-file Speech returns a different
transcript at 6/24. Thus Android's explicit multi-slice wrapper cannot explain
the platform difference on this clip, and there is no evidence of extra SDK
orchestration errors here. Backend/model/preprocessing remain confounded; this
is not root-cause or production-quality proof. The base variant scores 12/24 on
this clip but remains rejected overall. No recognition was repeated. Evidence:
[Android control](w3-no-vad-control-20261109.md),
[same-prepared SDK/native control](w3-native-parity-control-20261109.md), and
[full alignment](w3-mandarin-error-alignment-20261109.md).

### Frozen cross-platform input parity — 2026-11-09 19:47 +08

Read-only parsing of the three existing Apple WAVs confirmed mono / 16 kHz /
16-bit PCM, and each decoded payload is byte-for-byte identical to its Android
raw PCM input (same SHA per clip). This strengthens the fixed-input comparison
for the recorded Apple 16/92 and Android tiny 44/92 results. It still does not
isolate backend/model quality because the Apple and Android recognizers and
segmentation strategies differ. No inference was repeated. See the per-file
hash and byte-count table in the [alignment record](w3-mandarin-error-alignment-20261109.md).
Machine receipt: `artifacts/verification/w3-cross-platform-input-parity-20261109/parent-acceptance.json`.

## 2026-11-09 15:30 +08 — base candidate rejected

The single pinned Whisper base int8 candidate completed one frozen three-clip,
five-request physical comparison. It scored 43/92 total errors versus tiny's
44/92, but the first clip regressed (18/12/13 errors versus 15/14/15). The
pre-run rule required a lower total and no per-clip regression; the candidate is
rejected, production default unchanged, and no additional model sweep planned.
This closes the experiment only; W3 quality remains open. See
[candidate disposition](w3-whisper-base-candidate-20261109.md).

The remaining count is unchanged: W3 is open. This update removes duplicate
acceptance work and records a bounded locale regression; it does not approve
Chinese/real-world-noise quality or system microphone availability.

## Latest bounded diagnosis — 2026-11-09 10:00 +08

Apple same-input Mandarin3/3 and32checks/restoration were subsequently accepted
(CER16/92); Android baseline3clips/5requests CER44/92 remains measured, not quality
approval. Durable VAD diagnostics now passed25physical checks and recorded all
five preparation ranges; three stopped early. The isolated diagnostic patch and
host37checks were committed as `36d0583`; production sources were unchanged.
A separately frozen one-line no-VAD file control worsened CER to53/92 while the
unsliced second clip's output stayed exact. Removing VAD is not accepted as the
production fix. Both new batches have been restored. See [VAD measurement](w3-vad-diagnostic-parent-20261109.md)
and [same-input control](w3-no-vad-control-20261109.md). All earlier “Apple not
launched/Chinese source not yet frozen” statements below are historical.
W8 sustained/GPU/energy/budget, Android system capture/resource and remaining
W3 quality gaps stay open; no new microphone recording was authorized or used.

## Current bounded acceptance update — 2026-11-09 08:24 +08

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

## Existing evidence retained

Free CBC hy3 delivered `GAP-MATRIX.md` and `DELIVERY.md` in 144.319 seconds in
`.omx/artifacts/cbc-hy3-w3-existing-evidence-gap-audit-20261109-0735/`.
The parent accepted the distinction between prior Apple microphone lifecycle,
Android Sherpa file/native lifecycle, and Android system microphone cases.
The helper received only public source/documentation and no private media.

The parent then rechecked the November 3 iPhone status receipt and background
receipt against their recorded log SHA-256 values. Both match. The close receipt
matches `88c12afa…fe64e4`. Of seven relevant production Swift sources, all seven
match the later accepted Git baselines `36567f2` and `c245d12` byte-for-byte.
The old diagnostic close copy matches five files exactly; its other two contain
only the recorded added diagnostic logging, as shown in the retained diff.
These checks preserve the prior observed cancel/reentry/background-cancel and
single explicit close/drain result. They do not turn the instrumented binary
into a production binary or approve every interruption, long-duration leak,
authorization recovery, TTS interaction, accuracy or network-isolation case.

Parent disposition of helper limitations: its source inventory had 13 files,
not the 14 stated in DELIVERY; unavailable supplied hashes were a context gap,
not evidence of a product defect. Its blanket classification of all other iOS
follow-ups as outside W3 is not adopted. The actual acceptance contract decides
remaining interruption/authorization/TTS coverage. Current public file-run source
hashes were separately verified; they need no equivalent ASR rerun.

Evidence: `artifacts/verification/w3-gap-reconciliation-20261109-0735/`.

## Locale prerequisite regression

The free-hy3 exact locale candidate was previously source-reviewed and built.
The parent now executed one default/absent-locale run on the existing API36
ARM64 emulator: three fixed public English synthetic-noise clips, four requests,
terminal completed, successful backend close, unchanged caller arrays and four
model hashes. The default report contains neither `locale` nor `resourceWindow`.
This checks report shape and behavior, not byte equality of dynamic run fields.

Three separate invalid arguments (`fr-FR`, `En-US`, and empty string) each failed
with the exact `IllegalArgumentException` from `onCreate`, before reserving their
report. The host `am` exit code was zero despite a process crash; exact native
error text and absence of the report establish rejection. The first narrow host
observation lacked that error; the parent recovered the same run's crash-buffer
entry without replay. A missing parent `verification.json` had earlier stopped
orchestration before reserving or launching inference; it was restored only after
checking all PCM and compiled-asset hashes. Both observation failures are kept.

Explicit valid `en-US`/`zh-CN` mappings are source-reviewed; this run did not
exercise Chinese audio or establish Chinese accuracy. It opened no microphone.
Afterward only the two new test aliases and exact transfer staging were removed;
ordinary App paths and the protected sources/Apple lock remain unchanged.

Evidence: `artifacts/verification/w3-locale-gate-20261109-0118/`:
`parent-acceptance.json`, `default-shape-parent.json`, `invalid-locales-parent.json`,
`restoration.json`, and `task-cleanup.json`.

## Remaining ownership

- Android system microphone en-US resources and applicable capture/background/
  interruption/cancel-reentry cases remain unverified; a fresh explicit user
  microphone start cue and available resources are required for capture.
- Chinese natural-speech and real-world-noise file quality can be independent
  helper work after an exact source/license/reference/input freeze. Locale-only
  changes and existing synthetic samples cannot satisfy that gate.
- Physical GPU/energy/sustained resource budgets remain W8. The new foreground
  host candidate failed source review; free CBC hy4free owns only its three
  confirmed source corrections. Device execution stays with the parent.
