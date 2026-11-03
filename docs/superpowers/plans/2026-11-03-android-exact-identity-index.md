# Android exact Identity index Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline, preserving the authorized execution method.

**Goal:** Remove Android 1:N's fixture-only index/query dependency.

**Architecture:** A versioned bounded Float32 exact cosine matrix binds to the
full embedding space and current ordered template leases. FaceIdentification
keeps policy, authorization, cancellation and subject-max publication gates.

**Tech Stack:** Existing Kotlin/JVM, ByteBuffer and IndexStore; no dependencies.

**Spec:** ../specs/2026-11-03-android-exact-identity-index-design.md

## Global Constraints

- API26 floor; protect all nine pre-existing paths; no push/merge/release.
- No guessed VERIFIED real-model policy, raw-vector results or durable plaintext.
- Maximum 4096 templates and 4096 dimensions; reject zero/nonfinite vectors.

## Review Focus

Non-fixture encoded probes must work; stale/foreign payloads must fail closed;
subject aggregation precedes topK; cancellation stops search; unverified real
policies never publish outcomes. Existing revocation/deletion tests must pass.

### Task 1: Real index and query

Create `android/identity/src/main/kotlin/io/ondeviceai/identity/ExactCosineIdentityIndex.kt`;
modify `FaceIdentification.kt`; add `ExactCosineIdentityIndexTest.kt`.
Consumes current leased IdentityEmbeddings/stop signal; produces public
`ExactCosineIdentityIndex.build(List<IdentityEmbedding>, IdentityStopSignal): ByteArray`
and internal `scores(bytes, probe, retained, stopSignal): List<Double>`.

- [x] Add RED tests for cosine/ranking with encoded-image probe backend, malformed
  or wrong-space/snapshot index, zero vector and cancellation.
- [x] Implement minimal bounded codec/query; route FaceIdentification exact
  payloads here, preserve existing fixture format only for fixture inputs.
- [x] Run full `:android:identity:testDebugUnitTest`; zero failures required.

### Task 2: Wire and verify

Modify OpenCvPrototypeSession to use the builder; preserve UNVERIFIED policy.

- [x] Switch the builder and assert unverified gallery publication remains rejected.
- [x] Run optional Demo tests/build/lint and backend tests; verify protected hashes.
- [x] Bounded independent review, update validation/handoff and locally commit.

Final evidence: [validation](../../validation/android-exact-identity-index.md).
