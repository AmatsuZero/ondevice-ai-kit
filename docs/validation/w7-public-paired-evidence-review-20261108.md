# W7 public paired-probe evidence review — 2026-11-08

## Code Review Summary

**Scope:** evidence-only acceptance review for the isolated Apple public-fixture
host/probe and the paired Android/iOS public contract. This is not a production
release, calibration, policy, PAD, or pixel-intermediate review. No private
image, identity, embedding, or score was read.

**Files Reviewed:** 9
**Total Issues:** 0

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

### Evidence accepted for this bounded scope

- The Apple host statically accepts only the fixed public-fixture SHA, forces
  `publicFixtureOnly: true`, and starts the probe at most once
  (`.omx/artifacts/cbc-hy3-w7-apple-host-access-fix-20261108-2317/candidate/IdentityParityHostApp.swift:17-40,45,59-74,82-90`).
  Its one-line access-control repair compiled for `generic/platform=iOS` in
  2.633 seconds with exit 0
  (`artifacts/verification/w7-paired-probes-20261108-2245/apple-caller-access-fix-build-result.json:1-18`).
- The Apple physical-device terminal report is completed and clean: its cleanup
  fields confirm close/drain, model-store removal, owned-work removal, and
  source preservation; it records zero camera/mic/enrollment/threshold/
  verification decisions and keeps feature values out of the report
  (`artifacts/verification/w7-paired-probes-20261108-2245/apple-public-run/terminal-confirm.report.json:1-94`).
  The parent acceptance receipt separately records 26 passing checks, the
  repeated same raw terminal report, public-only input, and local artifacts
  (`artifacts/verification/w7-paired-probes-20261108-2245/apple-public-run/parent-acceptance.json:1-43`).
- The Android counterpart’s acceptance receipt records all 28 scoped checks,
  including terminal completion, clean cleanup, source preservation, zero
  sensitive actions, local-only reports, and a finite normalized 128-vector
  (`artifacts/verification/w7-paired-probes-20261108-2245/android-public-run-02/parent-acceptance.json:1-42`).
- The paired contract precommits to exact IEEE-754 float32 comparison and says
  that a difference must be recorded without an equality claim or
  observation-derived tolerance
  (`artifacts/verification/w7-paired-probes-20261108-2245/paired-public-contract/comparison-policy.json:1-12`).
  The result confirms all declared public-contract and terminal-cleanup checks,
  records `exactFloat32Bits: false`, and preserves the resulting values solely
  as descriptive metrics; it does not tune a tolerance
  (`artifacts/verification/w7-paired-probes-20261108-2245/paired-public-contract/comparison-result.json:1-45`).
  Therefore `strictNumericalPassed: false` is neither an SDK-bug finding nor
  numerical/production-calibration acceptance.
- The isolated alias’s signature verification completed successfully, and its
  receipt records protected-file/Mach-O-section matching
  (`artifacts/verification/w7-paired-probes-20261108-2245/apple-public-run/verify-signature-result.json:1-12`,
  `artifacts/verification/w7-paired-probes-20261108-2245/apple-public-run/signed-receipt.json:1-16`).

### Issues

None confirmed within the stated public-functional and public-contract scope.
The evidence does not introduce a fallback that hides failed cleanup: the
terminal receipts explicitly attest to each release/removal step and the source
remains fail-closed on a prior cleanup failure
(`.omx/artifacts/cbc-hy3-w7-parity-cleanup-correction-20261108-2256/candidate/IdentityParityProbe.swift:194-224`).

### Boundaries and pending evidence

- `strictNumericalPassed: false` remains an observed cross-platform difference
  for one public fixture only. No tolerance was fitted, and no numerical parity,
  accuracy, threshold, or production calibration claim is accepted.
- Pixel/intermediate-stage parity, PAD, multi-person behavior, and all
  production policy/threshold outcomes remain **UNVERIFIED**.
- The Apple acceptance receipt deliberately records `restorationPending: true`.
  Returning the temporary alias/host target to the ordinary app is parent-owned
  work and is not evidenced or approved by this report.

### Recommendation

**APPROVE** — for the narrow public-functional Apple slice and paired public
contract evidence only. This is not release approval or production identity
acceptance.

## Parent restoration addendum

The subsequent parent-owned `apple-public-run/restoration.json` proves only the newly installed alias was removed, its exact owned PID6346 stopped, all original App install URLs preserved, and ordinary Combined returned. Protected9 and production lock remain unchanged. This discharges the restoration-pending note above; it does not change numerical/calibration/PAD boundaries.
