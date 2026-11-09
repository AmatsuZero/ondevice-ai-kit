# W3 acceptance reconciliation — 2026-11-09

The remaining count is unchanged: W3 is open. This update removes duplicate
acceptance work and records a bounded locale regression; it does not approve
Chinese/real-world-noise quality or system microphone availability.

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
