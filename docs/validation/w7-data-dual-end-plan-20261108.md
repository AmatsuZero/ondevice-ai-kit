# DATA-AND-PARITY — Local-Only Multi-Person Validation Plan (corrected W7)

## Current executable scope — user update, 2026-11-08

The user can supply only their own new data in the near term. Do not request the
illustrative eight-person pilot below as a prerequisite for engineering work.

- Reuse the user's existing locally held enrollment photo and three previously
  captured photos. Confirm local identity, distinct source hashes and capture
  sessions before counting any genuine comparison; do not ask for the same photos
  again merely because the active task changed.
- First validate the paired probes using the existing public approved astronaut
  fixture. Then, after probe acceptance, replay the same authorized personal file
  bytes locally on Apple and Android to check feature/decision contracts.
- A single subject can support a personal functional trial and cross-platform
  consistency checks. It cannot supply two subject-disjoint calibration/test
  cohorts or establish population FAR/FRR and production thresholds. Keep the
  existing executor's insufficient-data rejection and uncalibrated state.
- Prior authorization for the locally held employee-photo set remains recorded.
  Reusing a work photo or creating edited copies does not create independent new
  captures. No broader data availability is inferred from that authorization.
- PAD remains separate: no new PAD-use authorization, attack-labelled dataset,
  collection, or liveness acceptance is implied by this personal Identity trial.
- Photos, names, embeddings and personal scores remain local. External assistants
  receive source/contracts and public or synthetic fixtures only.

The multi-person sections below describe a future production-validation dependency,
not a current collection request or a completed gate. Current status: personal
functional/parity execution pending; multi-person calibration and PAD remain open.

## 0. Ownership & boundaries
- Local-only: employee photos, names, embeddings, personal scores never leave the local trust boundary.
- Retouch/modified copies are NOT independent capture; excluded from "new independent probe" counts.
- Do NOT develop a new executor. Reuse frozen policy pin -> independent test executor (88 tests) and accepted W7 local extraction (116/116). Tool/test passing != production calibration.
- All subjects below are placeholders (subject-A, ...). No private data supplied in this task.
- NOTE: `docs/agent-pitfalls.md` is referenced but ABSENT from task root; guidance taken from inline task excerpts only (missing source — see DELIVERY.md).

## 1. Cohort & split model (CORRECTION)
- Two SUBJECT-DISJOINT cohorts: calibration/develop and final test. The manifest enforces this at `subject_splits` (one split per subjectId, manifest line 149).
- Within EACH cohort, enrollment and probe are the SAME subjects in SEPARATE capture sessions. Genuine pairs REQUIRE same subject, different session (manifest `session_roles`, line 153: a (subject,session) maps to exactly one role).
- Impostor comparisons are OFF-DIAGONAL: a subject's enrollment matched against a DIFFERENT cohort-subject's probe.
- CORRECTION of prior error: prior doc "enrollment and heldout share no subjects" is WRONG. Cohorts are subject-disjoint; within a cohort, enrollment and probe SHARE subjects.

## 2. Data inventory
- Local read-only reference: existing employee photos authorized for LOCAL identity eval/calibration ONLY. Filename labels UNVERIFIED; modified copies not independent; cannot seed FAR/FRR numerators.
- NOT supplied: PAD genuine/print/screen labels (separate confirmation + data). Raw score != liveness pass.

## 3. Capture sessions & protocols
- Genuine-accept path: same subject, enrollment session1 -> probe session2+ (independent sessions).
- Impostor/FAR path: subject-X enrollment matched to subject-Y probe (off-diagonal).
- Each session recorded with manifest (see 5).

## 4. New independent photos — supplier & need
- Supplier: USER / INTERNAL CONSENTED COORDINATOR owns recruiting + roster/consent + identity confirmation. Primary owns local split/eval only; does NOT recruit.
- Exact new material: consented multi-person probe set, independent sessions, no retouch. Cloud carries only placeholder IDs.

## 5. Manifests / lineage / dedup
- Per capture: {subject_id(placeholder), session, device_os, device_model, capture_app_version, timestamp, source_hash, retouch_flag, consent_ref}.
- Lineage: enroll->probe linkage per subject; template digest recorded.
- Dedup: drop rows where source_hash repeats or retouch_flag=true for independent counts.

## 6. Pilot (ILLUSTRATIVE ONLY — not production adequate, not a required user number)
- 8 placeholder subjects split 4 calibration / 4 test (cohorts subject-disjoint).
- Each subject: 1 enrollment + 2 fresh probes in independent sessions = 3 images -> 24 images total.
- Option: existing authorized workphotos may serve as enrollment ONLY after local identity confirmation; then new capture could be the 16 fresh probe images (8x2). No retouch either way.
- Explicitly NOT sufficient for FAR/FRR claims.

## 7. Dual-end parity (Apple <-> Android) — CORRECTION
- Parity = REPLAY the SAME exact image bytes, SAME model digest, SAME preprocess contract on both OS ends. Do NOT force separate real captures per OS for parity.
- Model digest = `modelSHA256` of the WEIGHTS (manifest binding), a SEPARATE SHA from executor code commit 7877493 (the executor, not the weights). Do not claim the commit pins the model digest.
- Independent photo sessions concern genuine quality, not parity.
- Compare per stage: preprocessing output, orientation, alignment, embedding metric, decision.

## 8. Numerical tolerances — CORRECTION
- Tolerances MUST be established and FROZEN using synthetic / dev conformance data BEFORE any heldout run. NEVER derived from heldout (heldout stays untouched until validation).
- Tolerance fields are `{{MEASURED}}` placeholders until frozen on conformance data; no pass claimed.

## 9. Unverified optional integration needs (replaces prior invented G1-G5)
- Prior G1-G5 "missing glue" claims had NO source proof and are REMOVED. The manifest already enforces split isolation (line 149) and enrollment/probe session roles (line 153).
- Remaining unverified items (classify, no executor redevelop, no implementation): cross-OS manifest join-key availability, heldout allocator readiness, digest-equality gate, tolerance loader, PAD label ingestion. Each needs separate source confirmation before any claim.

## 10. PAD
- Separate consent and data required; NO active capture now. Raw score != liveness pass.

## 11. Operational targets & acceptance
- FAR/FRR/confidence targets are owned by USER/PROJECT; not set here. Correlated impostor pairs are NOT independent samples (do not inflate sample counts).
- No arbitrary thresholds. All machine actions PENDING/unrun (see next-actions.json). Never claim data/production/device acceptance.
