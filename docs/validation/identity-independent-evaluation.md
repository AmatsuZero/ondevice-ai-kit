# Identity Independent Evaluation — Verification-only develop/evaluate

Scope: the `tools.identity_independent_evaluation` module orchestrates the **existing**
bound tools (`identity_calibration_manifest`, `identity_calibration_scores`, curves,
statistics). It implements **no** extraction, curve/FAR/FRR math, statistics,
inference, consent handling or production threshold activation. It is the
verification task only: identification and passive-PAD are rejected as unsupported.

All reads go through `load_document` (strict JSON: duplicate keys, `NaN`/`Infinity`,
non-bytes rejected). All writes are exclusive to a new regular file (`O_CREAT|O_EXCL|
O_NOFOLLOW`). Reports contain aggregates only — no private identifiers, sample/row
IDs, file paths or roots. Public functions never mutate their inputs.

## Public API

```python
develop(manifest, calibration_scores, spec, root) -> policy_artifact
evaluate(policy, eval_manifest, test_scores, root, *, expected_policy_digest) -> evaluation_report
```

- `develop` validates the spec, the calibration manifest (verification, calibration
  split), summarizes the calibration scores, runs the **complete enrollment×probe
  matrix + coverage** check, then selects a single inclusive threshold from the
  calibration empirical curve and returns an evidence-only, **unverified** candidate
  policy (`calibrationStatus='unverified'`, `productionReady=False`,
  `productionPolicyGenerated=False`, with a self-consistent `policyArtifactDigest`).
- `evaluate` fully re-validates the frozen policy (fields, semantics, **and digest**),
  then the evaluation manifest and the **test** scores. The threshold is **frozen**: the
  test scores must carry the identical `threshold`; there is no reselection.

### Integrity pin — `expected_policy_digest` (mandatory, keyword-only)

`evaluate` requires `expected_policy_digest`: the **original** 64-lowercase-hex
`policyArtifactDigest` captured by the evaluator at successful `develop` and **retained
separately, out of band**. It is NEVER derived from, or recomputed against, the received
policy — doing so would let a tampered-and-rehashed policy pass its own digest check.

Two independent bindings enforce this:

1. The received policy still recomputes to its own embedded `policyArtifactDigest`
   (catches tampering that was *not* rehashed → `policy digest tampered`).
2. The embedded digest equals the externally pinned original digest (catches a policy
   that *was* mutated and rehashed to match → `policy digest does not match pinned anchor`).

The pin is an **integrity binding only**; it is not legal, consent, or release authority
(those remain unverified). The evaluator must capture and store the pin *before* any
independent evaluation and must not inline-recompute it from the untrusted policy it is
about to evaluate.

## Running the tests

From the repository root (the directory containing the `tools` package):

```bash
python -m unittest tools.tests.test_identity_independent_evaluation
# or
python -m pytest tools/tests/test_identity_independent_evaluation.py
```

Tests reuse `tools.tests.test_identity_calibration_manifest.CalibrationManifestTests`
`setUp`: synthetic files, two disjoint subjects per split (`subject-{0,1}`),
enrollment ids `0/1` and probe ids `2/3`, and the full 2×2 matrix. Every output is a
temp file under the test sandbox; no production artifact is produced. The tests assert
desired strict invariants only — they are not a pass claim for any production use.

## Two-phase CLI (synthetic files only)

```bash
# Phase 1 — select the frozen candidate policy
python -m tools.identity_independent_evaluation develop \
  --manifest manifest.json \
  --calibration-scores calibration_scores.json \
  --spec spec.json \
  --output policy.json

# Phase 2 — evaluate the independent test split at the frozen threshold.
# --expected-policy-digest is MANDATORY: pass the policyArtifactDigest that
# develop emitted and that you retained separately. Do NOT recompute it from
# policy.json at evaluation time.
python -m tools.identity_independent_evaluation evaluate \
  --policy policy.json \
  --manifest eval_manifest.json \
  --test-scores test_scores.json \
  --expected-policy-digest <64-hex-digest-from-develop> \
  --output report.json
```

Both phases exit `2` on any rejected request (OSError/ValueError/TypeError), write
nothing to stdout on failure, and never echo private values. The output path must not
already exist, must not be a symlink, and must not be a FIFO; otherwise the write is
refused and any pre-existing bytes are preserved.

### Output path normalization (system-volume aliases)

A single leading macOS system-volume alias is substituted before the O_NOFOLLOW walk:
`/var -> /private/var` and `/tmp -> /private/tmp`. Only the exact known alias is
trusted; a custom leading symlink (or any inner symlink component) is left in place and
refused by the walk, so an arbitrary/inner parent symlink as output parent is rejected.
On Linux, where `/tmp` and `/var` are real directories, they are used as-is. Writing to
`/var/tmp`, `/tmp` and `/private/tmp` (where writable) succeeds and is exercised by the
system-prefix CLI test.

## Fixed-threshold semantics

`develop` selects exactly one operating point from the calibration empirical curve
such that `farOnResponse <= targetFar` and `frrOnResponse <= maxFrr`, minimizing FRR
and, on ties, taking the **stricter** threshold (larger for higher-is-match, smaller
for lower-is-match). With the hand-computed separation genuine `.8/.9` and impostor
`.1/.2` (higher-is-match, `targetFar=maxFrr=0.5`) the selected threshold is `0.8`;
the lower-direction negation selects `-0.8`.

`evaluate` does **not** re-run selection. It requires `test_scores.threshold ==
policy.selectedThreshold`; the report's `fixedThreshold` equals the frozen value and
`selectionStatus` is `met`/`unmet` solely from the test outcomes at that threshold.
Changing test outcomes (e.g. pushing impostors above threshold) flips
`selectionStatus` to `unmet` but leaves `fixedThreshold` unchanged — this is the
enforced "no reselection" invariant. A different `test_scores.threshold` is rejected.

## Data-insufficiency boundary

The coverage check is mandatory on **both** splits and rejects:

- incomplete enrollment×probe matrix (missing/duplicate cell) — every
  (enrollment, probe) pair must be present exactly once;
- cross-split score cells (a row referencing a sample outside its split);
- `subjectCount < minSubjects`, `genuineResponses < minGenuineResponses`, or
  `impostorResponses < minImpostorResponses` (all floors `>= 2`), or `genuine==0` /
  `impostor==0`;
- `nonresponses > maxNonresponses` (floor `>= 0`).

If **no** calibration operating point simultaneously satisfies `targetFar` and
`maxFrr`, `develop` raises `no feasible candidate threshold`. `calibrationStatus`
stays `unverified` and no production policy is ever generated.

## Negative cases exercised (not a pass claim)

- cross-split subject / media-group / media-hash leakage (manifest-level, both phases);
- same-subject enrollment/probe session leakage;
- missing / duplicate / cross-split score cells (both phases);
- inadequate subject / genuine / impostor denominators;
- no feasible candidate threshold;
- excessive nonresponses (and admissibility once the preregistered cap is raised);
- missing / tampered license or media digest;
- model SHA / model revision / preprocess revision / score-direction mix, and dataset
  id/revision mismatch;
- policy digest tamper (field changed, embedded digest not rehashed);
- **threshold tamper with embedded digest rehashed, but the externally retained
  ORIGINAL pin kept** → rejected (`policy digest does not match pinned anchor`);
- **missing / malformed `expected_policy_digest`** (TypeError on omission, ValueError
  on non-hex / `None`) and **CLI `--expected-policy-digest` omission**;
- test threshold change (rejected — no reselection);
- unsupported task (identification) and calibration/test split mismatch;
- strict JSON bool / `NaN` / `Inf` / 400-digit integer on spec targets and on the
  frozen test threshold; duplicate JSON keys and non-finite spec via the CLI;
- **malformed score root (non-dict), malformed score rows (non-list / missing key), and
  huge-integer score/threshold values** must raise `ValueError`, never
  `AttributeError` / `KeyError` / `OverflowError`;
- CLI output that exists (regular file — bytes preserved) / is a symlink (unfollowed) /
  is a FIFO (refused, left in place) is refused and existing bytes are preserved;
- CLI output under arbitrary / inner parent symlinks is refused; CLI develop/evaluate
  succeed under the system-volume prefixes `/var/tmp`, `/tmp` and `/private/tmp`
  (where writable).

## Boundaries / residual observations (confirm against the running suite)

- The calibration-score `threshold` is a caller-supplied, unused-for-selection
  descriptor; it is type-checked (bool/`NaN`/`Inf`/huge-int rejected via
  `_finite_number`, which converts `OverflowError` to `ValueError`) but **not**
  range-checked against extreme integers (e.g. `10**400`). This is harmless here
  because threshold selection uses only the empirical curve, not the supplied
  calibration threshold.
- A pre-existing FIFO at the output path is refused by `O_EXCL` **before** any open
  (no blocking, and the FIFO is neither opened nor removed). A *non-existent* output
  path is created as an ordinary regular file by `O_CREAT|O_EXCL|O_NOFOLLOW`; it is
  never created as a FIFO. (Prior documentation claiming a non-existent FIFO output
  would be created and then block on write was incorrect and has been removed — the CLI
  emits a regular file, never a special file.)
- `evaluate` requires `expected_policy_digest` as a keyword-only argument; supplying it
  positionally or omitting it raises `TypeError`. A non-hex or `None` pin raises
  `ValueError` before any policy work.
- This module is engineering/test scaffolding for the verification task only. It is
  not biometric production calibration or PAD, and produces no production threshold
  or release decision.
