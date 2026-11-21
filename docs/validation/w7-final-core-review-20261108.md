# W7 final core review — 2026-11-08

## Code Review Summary

**Scope:** `tools/identity_independent_evaluation.py` at SHA-256
`03c1eead59ca9296d6f4da9b626438c2405f2e52e79c06490eeabcb06d717f5c`.

**Files Reviewed:** 5

- `tools/identity_independent_evaluation.py` (complete source)
- `tools/identity_calibration_manifest.py` (reused manifest, leakage, and strict-load contract)
- `tools/identity_calibration_scores.py` (reused score/matrix/direction contract)
- `tools/identity_calibration_curves.py` (reused empirical-curve contract)
- `tools/tests/test_identity_independent_evaluation.py` (synthetic fixture/source contract only)

**Total Issues:** 0

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

### Requirement evidence

- Calibration-only selection is bounded to `develop`: it validates the calibration
  split, reuses the empirical curve, selects one feasible deterministic threshold,
  and emits only an unverified candidate policy
  (`tools/identity_independent_evaluation.py:224-272`). `evaluate` requires the
  test split and exact frozen threshold; it has no reselection branch
  (`tools/identity_independent_evaluation.py:320-374`).
- The policy is protected by two independent checks: a fresh canonical digest must
  equal the embedded digest, and that digest must equal the separately supplied
  fixed `expected_policy_digest` (`tools/identity_independent_evaluation.py:279-317`).
  Therefore changing and recomputing the policy's self-contained digest alone is
  still rejected. The caller remains responsible for retaining the supplied pin
  externally; the module does not claim that a caller-derived pin is independent.
- Model, model revision/hash, preprocessing, backend, score metric, and score
  direction are frozen via exact manifest binding equality; dataset id/revision and
  the purpose-excluded full dataset-contract digest are also checked
  (`tools/identity_independent_evaluation.py:354-365`). The underlying manifest
  validation rejects cross-split subject, media-group, and media-digest leakage
  (`tools/identity_calibration_manifest.py:121-155`).
- Both phases require the complete enrollment-by-probe matrix and enforce separate
  subject, genuine-response, impostor-response, and nonresponse limits
  (`tools/identity_independent_evaluation.py:110-143`). Existing score validation
  rejects wrong roles, duplicate pairs, and cross-split references before coverage
  is traversed (`tools/identity_calibration_scores.py:39-65,148-181`).
- No legacy extraction, curve, FAR/FRR, or metric implementation is duplicated:
  this module imports and orchestrates the bounded manifest/score/curve paths
  (`tools/identity_independent_evaluation.py:1-14,38-40,240-245,368-371`). The
  candidate and report explicitly remain unverified and non-production
  (`tools/identity_independent_evaluation.py:266-269,403-407`).
- Reports return aggregates, declared model/dataset metadata, canonical digests,
  and metrics, but no sample IDs, subject IDs, raw rows, or filesystem paths
  (`tools/identity_independent_evaluation.py:249-272,383-408`). CLI failures are
  sanitized to a generic non-sensitive message and exit 2
  (`tools/identity_independent_evaluation.py:538-576`). Strict JSON loading and
  finite-number checks are delegated/enforced before evaluation
  (`tools/identity_calibration_manifest.py:195-199`; `tools/identity_independent_evaluation.py:70-81,320-336`).
- Output serialization occurs before opening the destination. Output creation is
  exclusive, nonblocking, and no-follow; an existing regular file, symlink, or
  FIFO is refused rather than overwritten. The only accepted leading symlink
  aliases are verified `/tmp -> /private/tmp` and `/var -> /private/var`, including
  macOS's parent-relative `readlink` targets; every remaining parent is walked with
  `O_NOFOLLOW` (`tools/identity_independent_evaluation.py:415-535`).

### Issues

None found in the reviewed core slice. The root-cause/fallback guard is clear: all
invalid or insufficient inputs fail explicitly; there is no silent default,
compatibility bypass, digest fallback, threshold fallback, or production-enable path.

### Validation limits

This was a source-only review. I did **not** run tests, static diagnostics, builds,
device operations, or inspect real employee photos, names, embeddings, or individual
scores. The parent-reported seven API checks and eight output-path checks were not
rerun here. The known two current synthetic-fixture errors (lower-direction binding
not changed; a nonresponse reducing genuine responses below the configured floor)
are fixture corrections already assigned elsewhere; they do not demonstrate a core
production defect and are not counted as findings.

This approval is limited to the engineering verification scaffold. It is not a
biometric calibration, privacy/consent, release, device, or production-activation
approval.

### Recommendation

**APPROVE_SLICE** — no CRITICAL/HIGH/MEDIUM/LOW issue found in the specified core
source at the pinned SHA. This is a scoped single-reviewer result, not a substitute
for any separately required architecture or release review.
