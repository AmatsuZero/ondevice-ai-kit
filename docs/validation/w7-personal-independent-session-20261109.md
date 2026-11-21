# W7 one-person cross-session feature trial

Three existing, authorized personal probe photographs completed one production
feature request each on the Android API 36 ARM64 emulator and the physical
iPhone. This accepts a single-person functional observation and descriptive
local comparisons. W7 production calibration and PAD remain open.

## Input and execution

The work-photo reference was already processed in the
[preceding paired trial](w7-personal-paired-function-20261109.md). The three probe
photographs come from one later capture session, distinct from that reference
session. They are three captures of one subject, not three independent sessions
or a multi-person test set. No new photograph or recording was taken in this run.

The original JPEGs are 1200 × 1600, with no orientation tag and an sRGB profile.
The parent used local `sips` conversion to PNG without resizing, preserved the
originals and checked their hashes afterward. Both platforms received identical
derived bytes per capture and the same fixed YuNet/SFace weights. The previously
accepted source and binaries were reused; no production backend or threshold
changed.

- Android: three completed requests; all 26 parent checks passed per capture.
- Apple: three completed requests; all 25 parent checks passed per capture.
- All three paired comparisons matched the nine declared contracts: input,
  model revision, preprocessing revision, runtime version, policy state,
  readiness, PAD state, weights and feature dimensions.
- All extracted features were finite, 128-dimensional and L2-normalized. The
  parent computed three reference-versus-probe cosine similarities per platform
  locally. Values and cross-platform numeric differences remain private;
  no threshold or numerical tolerance was fitted or approved.

The first Apple report read occurred before the terminal artifact was ready.
That failed collection is retained. A later read recovered the **same** run's
completed report; inference was not relaunched. The remaining two requests were
collected after observing their terminal filenames.

## State and privacy

Both platforms retained `UNVERIFIED`, `productionReady=false` and `PAD=absent`.
Camera/microphone starts, enrollments, verification decisions and threshold
decisions stayed zero. This was feature extraction and comparison, not a passed
identity authentication.

The parent removed all three Android personal run roots while preserving the
public test alias. Each completed Apple temporary process was stopped by its
exact identity; the new alias and personal staging were removed. The original
App inventory/URLs were unchanged and Combined was reopened. All nine protected
files and the production Apple lock matched the retained baseline.

Only the parent accessed private source maps, photographs, vectors and personal
scores. They were not supplied to helpers or Git. The native review approved
the semantics of the sanitized report only; it did not independently inspect
or authenticate private runtime evidence.

After acceptance, nine readback duplicates, totaling 122,916,217 nominal bytes,
were deleted after complete canonical SHA/inode/open-file checks. Original
JPEGs, unique PNG derivatives, canonical models, raw failures, reports and
features remain local. Concurrent filesystem free-space changes are recorded
separately and are not attributed exclusively to this deletion.

## Evidence and remaining gates

Public, sanitized receipts:
`artifacts/verification/w7-personal-session-20261109-0112/`, including
`sanitized-parent-acceptance.json` and `task-cleanup.json`.

Reporting review:
[APPROVE_REPORTING_SLICE](w7-personal-session-report-review-20261109.md).

This adds device-level evidence for the existing independent personal captures;
it does not repeat the previously completed 116-input host extraction or its
descriptive population comparisons. Multi-person independent probe/held-out
data, a development-stage numerical-tolerance freeze, production FAR/FRR and
threshold calibration remain missing. PAD additionally requires its separate
authorized/labeled genuine/attack data and a validated backend/preprocessing
policy. No production policy was enabled.
