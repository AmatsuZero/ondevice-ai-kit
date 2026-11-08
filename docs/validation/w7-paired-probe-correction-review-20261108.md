# W7 paired-probe correction re-review — 2026-11-08

## Code Review Summary

**Scope:** source-only re-review of the two corrected paired-probe candidates and
the new isolated Apple parity-host candidate. This review did not read private
images, identities, embeddings, or scores.

**Files Reviewed:** 6
**Total Issues:** 0

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

### Correction evidence

- The Android cleanup gate now initializes the obligations as satisfied only
  when the corresponding resource was never created, records a successful
  `closeAndWait`, requires `ModelStore.remove` followed by `ABSENT`, and deletes
  owned work only if both obligations are met. A close/drain or removal/status
  failure sets `degraded` and retains the work directory
  (`.omx/artifacts/cbc-hy3-w7-parity-cleanup-correction-20261108-2256/candidate/IdentityParityInstrumentation.kt:130-165`).
- The Apple counterpart has the same fail-closed predicate: it records the
  nonthrowing close only after it completes, verifies `.absent` after removal,
  and permits work deletion only when the resources that were created have been
  conclusively released
  (`.omx/artifacts/cbc-hy3-w7-parity-cleanup-correction-20261108-2256/candidate/IdentityParityProbe.swift:194-224`).
- Android no longer reconstructs the embedding-space schema. It calls the
  public authoritative API,
  `OpenCvIdentityModel.embeddingSpace.canonicalJSON()`, and accurately marks its
  provenance (`.omx/artifacts/cbc-hy3-w7-parity-cleanup-correction-20261108-2256/candidate/IdentityParityInstrumentation.kt:212-213`).
  That is consistent with the production public contract
  (`android/identity/src/main/kotlin/io/ondeviceai/identity/IdentityBindingContracts.kt:31-50`).
- The Apple host has one `@main`, rejects absent/malformed/duplicate required
  arguments and any SHA other than the fixed public astronaut fixture, always
  sets `publicFixtureOnly: true`, and uses a one-shot start guard
  (`.omx/artifacts/cbc-hy3-w7-apple-parity-host-20261108-2254/candidate/IdentityParityHostApp.swift:17-40,59-74,82-90`).
  It introduces no production threshold, PAD, enrollment, or decision path.

### Issues

None confirmed in the corrected source scope. The prior HIGH cleanup defect and
MEDIUM Android canonical-JSON defect are repaired without a fallback that masks
cleanup failure: failed cleanup remains visible as a degraded/failed terminal
result while retaining the owned work tree for investigation.

### Validation limits

This is a static correction review only. I did not build, install, execute,
operate a device/emulator, perform model inference, or inspect runtime
artifacts. In particular, Apple target-membership replacement and actual
compile/runtime behavior remain for the parent’s independent build and
public-fixture terminal-receipt validation; this report is not merge or device
acceptance evidence.

### Recommendation

**APPROVE_SLICE** — the two requested source corrections and the bounded Apple
host entry behavior are correct on inspection. Maintain the pending build and
public-fixture runtime gates before treating W7 as fully accepted.

## Parent fresh compilation evidence

Android corrected build and public emulator runtime passed. The Apple caller subsequently failed real iPhoneOS compilation at `IdentityParityHostApp.swift:45:33`: property must be declared fileprivate because its type uses a private type (`HostState`). The source-only approval did not establish compile correctness; Apple candidate remains unaccepted and a separate free hy3 minimal access-level correction is in progress. Raw: `artifacts/verification/w7-paired-probes-20261108-2245/apple-caller-build.log`.
