# Identity Gallery Demo Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for ViewModel/session integration; a bounded executor owns the independent callback helper/tests.

**Goal:** Make multi-candidate enrollment and gallery management callable from the Demo.

**Architecture:** Shared ViewModel retains opaque candidate/template references;
bridge publication helper serializes builder/add/publish cleanup. Android session
teardown revokes/purges its full local grant. Real-model policy gates stay intact.

**Tech Stack:** Existing Kotlin/Compose/KMP/IdentityBridge, no new dependencies.

**Spec:** ../specs/2026-11-03-identity-gallery-demo-design.md

## Global Constraints

Protect all nine pre-existing paths; no push/merge/release. No raw vectors/images
in state or reports, no invented VERIFIED policy, no new biometric fixture.

## Review Focus

Selecting another candidate clears old input/template; multiple templates stay
with their owner; deletion reports actual status; late callbacks do not republish;
all session candidates are covered by native teardown revocation/purge.

### Task 1: Candidate/gallery shared actions

Files: IdentityViewModel.kt, FaceScreen.kt, IdentityViewModelTest.kt.

- [x] Add RED tests for add/select/multi-template/deletion/gallery operations.
- [x] Add optional ownership/gallery config and numbered candidate state; keep
  bridge open across selection; publish using `publishIdentityGallery` helper.
- [x] Add optional UI controls and status; run shared common suite.

### Task 2: Publication helper

Files: new IdentityGalleryPublication.kt and IdentityGalleryPublicationTest.kt.
Interface: `publishIdentityGallery(bridge, grant, options, backend, members,
callback): IdentityBridgeTask`; new gallery, serial additions and cleanup.

- [x] Capture RED then implement synchronous/async callback sequencing, failure,
  cancellation and late-builder cleanup; run common suite.

### Task 3: Android wiring and acceptance

Files: OpenCvPrototypeSession.kt, IdentityModelInstrumentation.kt, docs.

- [x] Supply ownership/gallery options; change teardown to revoke and purge all
  session candidates, preserving model lease drain and idempotent close.
- [x] Extend headless native runner to create/delete multiple candidate records;
  real fixture remains the same approved portrait and is not calibration data.
- [x] Run default/opt-in tests/APK/lint, shared simulator/iOS compile, device run,
  bounded review, protected hashes, documentation and local commit.
