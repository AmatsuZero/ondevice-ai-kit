# Face Identity and Liveness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the P10 face verification, identification, and liveness contracts as independently testable Apple and Android SDK capabilities, with strict authorization/revocation, immutable template/gallery lifecycle, fail-closed policy gates, typed KMP/Demo integration, and evidence-backed local distribution.

**Architecture:** Keep `ODAIKitIdentity`/`android:identity` separate from the existing face detector and from `ODAIKitModelStore`/`android:model-store`; the latter manages model assets and leases only. Build shared semantic contracts first, then native `IdentityStore`/`IndexStore`, deterministic test backends, and finally a model-bound local backend that remains `unverified` until a later model/data/calibration gate. `FaceVerification`, `FaceIdentification`, and `FaceLiveness` have separate request/result/session types and terminal-state machines; KMP and Demo consume those typed APIs without exposing business subject IDs.

**Tech Stack:** Swift 5.9 / iOS 15 Swift Package, Foundation/CryptoKit actors and XCTest; Kotlin 2.4 / Android API 26 library, coroutines, JUnit/Kotlin test; Kotlin Multiplatform bridge and Compose Demo; existing local XCFramework/Maven distribution scripts; JSON contract fixtures.

**Spec:** `docs/superpowers/specs/2026-10-17-face-identity-liveness-design.md`

## Global Constraints

- Implement the three capabilities independently: `FaceVerification` (1:1), `FaceIdentification` (1:N), and `FaceLiveness` (continuous session); there is no generic `FaceResult` or implicit detection-to-identity path.
- SDK-facing identity references are opaque (`TemplateRef`, `GalleryRef`, `CandidateRef`, `LivenessProof`); business subject IDs stay in the host database and never enter SDK logs or results.
- `IdentityStore` and `IndexStore` are independent of `ModelStore`; encrypted template envelopes use host-injected secure key storage, and raw images/vectors/business mappings never enter general logs, reports, or backups.
- Revocation is the logical linearization point: persist tombstone and epoch, reject new admission, cancel in-flight work, and suppress any result whose publication loses the revoke/publish serial gate. Deletion is idempotent two-phase (`revoked` -> `purgePending` -> `purged`/`purgeFailed`).
- `Default` and `Explicit(backendId)` selection is strict. An unavailable explicit backend never falls back. Backend/model binding and immutable `EmbeddingSpaceId` must match before any comparison.
- Unverified or incompatible identity/PAD policies fail closed with stable capability-scoped `thresholdUnverified`, `embeddingSpaceMismatch`, `reEnrollmentRequired`, or `rebuildRequired`; no SDK default threshold, synthetic accuracy claim, or fabricated production model is allowed.
- Every task/session has at most one active run; cancellation and close are idempotent; Core terminal outcome is exactly `completed`, `failed`, or `cancelled`. Revocation-originated termination is `failed/authorizationRevoked`, not caller cancellation.
- 1:N aggregates same-subject templates before de-duplication, sorting, and `topK` (default max 32) with stable opaque-byte tie-breaks. Liveness uses nonce-bound challenges, monotonic timestamps, bounded latest-frame buffering, and one terminal outcome.
- Do not modify or delete the existing dirty `demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj` or untracked `docs/validation/face-identity.md`; keep model selection, production thresholds, authorized datasets, and device accuracy as explicit later gates.

## Review Focus

- Caller-forged or stale opaque references/grants: tests must reject mismatched issuer metadata, expired grants, wrong scope/purpose, and stale authorization epochs without revealing business IDs (Tasks 1–2).
- Revocation racing with admission/result publication: tests must prove the serial gate gives one deterministic winner, cancels work, and never emits a post-revoke identity result (Task 2 and Task 5).
- Compatibility and policy drift: tests must reject model/space/format mismatch and every unverified threshold/PAD policy before score/outcome fields are populated (Tasks 1, 4, 5, 6, 7).
- Gallery mutation and lease races: tests must pin immutable snapshots, compare-and-swap conflicts, whole-lineage delete, read leases, and rollback to the old revision on index-build failure (Task 3).
- Liveness pressure and late input: tests must cover nonce replay, pre-challenge frames, accepted/dropped/stale counts, timeout/cancel/close, and late frames after terminal state (Task 7).

---

### Task 1: Shared identity contracts, clocks, references, and fixtures

**Files:**
- Create: `apple/Sources/ODAIKitIdentity/IdentityContracts.swift`
- Create: `apple/Sources/ODAIKitIdentity/IdentityErrors.swift`
- Create: `apple/Tests/ODAIKitIdentityTests/IdentityContractsTests.swift`
- Create: `android/identity/src/main/kotlin/io/ondeviceai/identity/IdentityContracts.kt`
- Create: `android/identity/src/main/kotlin/io/ondeviceai/identity/IdentityErrors.kt`
- Create: `android/identity/src/test/kotlin/io/ondeviceai/identity/IdentityContractsTest.kt`
- Create: `fixtures/contracts/face-identity.json`
- Modify: `apple/Package.swift`, `settings.gradle.kts`, root Gradle module registration

**Interfaces:**
- Produces equivalent Swift/Kotlin types: `TemplateRef(id:String, embeddingSpaceId:EmbeddingSpaceId, authorizationEpoch:UInt64/Long, revision:String)`, `GalleryRef(id:String, revision:String)`, `CandidateRef(id:String, storeId:String)`, `LivenessProof`, `AuthorizationGrant(grantId, revocationDomainId, scope, purpose, issuedAt, expiresAt, authorizationEpoch, evidenceRef)`, `OwnershipAuthorization`, `BackendSelection`, `TaskTerminal`, `IdentityError`, `EmbeddingSpaceId(supportedCapabilities, backendId, backendVersion, modelBinding, featureFormatVersion, dimension, dtype, endianness, normalization, preprocessRevision, scoreMetric, scoreDirection)`, `ThresholdPolicyRef`, `PADPolicyRef`, and monotonic/wall-clock abstractions.
- Produces `BackendRegistry<Backend>` with `select(.default|.explicit(id))` and no-fallback semantics; all public errors carry optional resolved backend/model/policy/session metadata only after admission resolves it.

- [ ] Write failing cross-platform fixture tests for opaque-reference validation, grant scope/purpose/expiry/epoch, `Default`/`Explicit` selection shape, nullable score fields, and every stable error: `backendNotRegistered`, `backendUnavailable`, `unsupportedOption`, `authorizationRequired`, `authorizationExpired`, `authorizationRevoked`, `candidateNotFound`, `candidateRevoked`, `templateNotFound`, `templateRevoked`, `galleryNotFound`, `galleryRevisionGone`, `galleryRevisionConflict`, `builderClosed`, `embeddingSpaceMismatch`, `featureFormatUnsupported`, `reEnrollmentRequired`, `rebuildRequired`, `indexUnavailable`, `indexBuilding`, `thresholdUnverified`, `invalidInput`, `multipleFaces`, `invalidChallenge`, `challengeExpired`, `sessionTimeout`, `busy`, `closed`, and `cancelled`.
- [ ] Run `rtk proxy swift test --package-path apple --filter ODAIKitIdentityTests` and `rtk proxy ./gradlew :android:identity:testDebugUnitTest`; confirm the new symbols/fixture assertions fail before implementation.
- [ ] Implement the exact value types and codecs; reject caller-supplied model revision, score, creation time, or epoch changes and use injected wall/monotonic clocks for expiry/deadline checks.
- [ ] Re-run the two targeted suites and assert PASS with Swift/Kotlin serialized fixtures byte-for-byte equivalent for shared error/reference fields.
- [ ] Commit: `rtk git add apple/Package.swift apple/Sources/ODAIKitIdentity apple/Tests/ODAIKitIdentityTests android/identity settings.gradle.kts fixtures/contracts/face-identity.json && rtk git commit -m "feat: add face identity shared contracts"`.

### Task 2: Encrypted IdentityStore, authorization epochs, leases, and deletion gate

**Files:**
- Create: `apple/Sources/ODAIKitIdentity/IdentityStore.swift`
- Create: `apple/Sources/ODAIKitIdentity/IdentityCrypto.swift`
- Create: `apple/Tests/ODAIKitIdentityTests/IdentityStoreTests.swift`
- Create: `android/identity/src/main/kotlin/io/ondeviceai/identity/IdentityStore.kt`
- Create: `android/identity/src/main/kotlin/io/ondeviceai/identity/IdentityCrypto.kt`
- Create: `android/identity/src/test/kotlin/io/ondeviceai/identity/IdentityStoreTest.kt`

**Interfaces:**
- Produces `IdentityStore.createCandidate(grant) -> CandidateRef`, `enroll(probe:candidate:grant:options:backend:) -> EnrollmentResult`, `revokeGrant(grantId:ownership:) -> RevocationReceipt`, `deleteTemplate`, `deleteCandidate`, `awaitDeletion`, `acquireTemplateLease`; internal `RevocationGate` serializes admission/result publication.
- `EnrollmentResult` is `enrolled(templateRef, embeddingSpace, quality)` or `insufficientQuality(templateRef:nil)`; no template is written for the latter. `DeletionStatus` is `revoked|purgePending|purged|purgeFailed`.

- [ ] Add race tests for candidate ownership, same-domain epoch invalidation, reauthorization tombstones, scope/expiry rejection, idempotent deletion, lease-held purge, and revoke-vs-publish ordering.
- [ ] Run `rtk proxy swift test --package-path apple --filter IdentityStoreTests` and `rtk proxy ./gradlew :android:identity:testDebugUnitTest --tests '*IdentityStoreTest'`; observe failures for missing store/gate/crypto implementation.
- [ ] Implement actor/Mutex-backed persistence using AES-256-GCM envelopes and a host-injected key provider (Apple Keychain provider; Android Keystore `AES/GCM/NoPadding` provider), reverse grant/domain indexes, tombstones, crash-safe recovery journal, backup-exclusion attributes, and finally-based lease release; keep raw image input task-scoped.
- [ ] Re-run the two Task 2 commands, including process-restart recovery fixtures proving persisted epochs/tombstones are authoritative and monotonic clocks are not used for cross-restart expiry.
- [ ] Commit: `rtk git add apple/Sources/ODAIKitIdentity/IdentityStore.swift apple/Sources/ODAIKitIdentity/IdentityCrypto.swift apple/Tests/ODAIKitIdentityTests/IdentityStoreTests.swift android/identity/src/main/kotlin/io/ondeviceai/identity/IdentityStore.kt android/identity/src/main/kotlin/io/ondeviceai/identity/IdentityCrypto.kt android/identity/src/test/kotlin/io/ondeviceai/identity/IdentityStoreTest.kt && rtk git commit -m "feat: add encrypted identity store lifecycle"`.

### Task 3: Immutable IndexStore, gallery builder, snapshots, and copy-on-write cleanup

**Files:**
- Create: `apple/Sources/ODAIKitIdentity/IndexStore.swift`
- Create: `apple/Tests/ODAIKitIdentityTests/IndexStoreTests.swift`
- Create: `android/identity/src/main/kotlin/io/ondeviceai/identity/IndexStore.kt`
- Create: `android/identity/src/test/kotlin/io/ondeviceai/identity/IndexStoreTest.kt`

**Interfaces:**
- Produces `createGalleryBuilder(baseGalleryRef: GalleryRef?, grant:, policy:, backend:) -> GalleryBuilder`, `addTemplate`, `removeTemplate`, `publishGallery -> GalleryRef`, `deleteGallery`, and `acquireIndexReadLease`.
- `GalleryBuilder` is single-writer/single-use (`open -> publishing -> published|discarded`), pins base revision, target `EmbeddingSpaceId`, aggregation/threshold policies, operation domain, and member epoch map; publish uses base-revision CAS and returns `galleryRevisionConflict` on lineage races.

- [ ] Write tests for immutable revision summaries, member-domain epoch maps, add/remove authorization, builder close/cancel, CAS conflicts, index-build failure rollback, whole-lineage gallery deletion, and old-revision lease retention.
- [ ] Run `rtk proxy swift test --package-path apple --filter IndexStoreTests` and `rtk proxy ./gradlew :android:identity:testDebugUnitTest --tests '*IndexStoreTest'`; confirm failures before implementation.
- [ ] Implement copy-on-write snapshots and derived-index leases; invalidate an entire old snapshot when any member domain is revoked, never silently filter it.
- [ ] Re-run tests and verify a failed new index leaves the prior published revision queryable.
- [ ] Commit: `rtk git add apple/Sources/ODAIKitIdentity/IndexStore.swift apple/Tests/ODAIKitIdentityTests/IndexStoreTests.swift android/identity/src/main/kotlin/io/ondeviceai/identity/IndexStore.kt android/identity/src/test/kotlin/io/ondeviceai/identity/IndexStoreTest.kt && rtk git commit -m "feat: add immutable identity gallery store"`.

### Task 4: Strict backend registry, embedding-space binding, policies, and deterministic test backend

**Files:**
- Create: `apple/Sources/ODAIKitIdentity/IdentityBackend.swift`
- Create: `apple/Sources/ODAIKitIdentity/TestIdentityBackend.swift`
- Create: `apple/Tests/ODAIKitIdentityTests/BackendPolicyTests.swift`
- Create: `android/identity/src/main/kotlin/io/ondeviceai/identity/IdentityBackend.kt`
- Create: `android/identity/src/main/kotlin/io/ondeviceai/identity/TestIdentityBackend.kt`
- Create: `android/identity/src/test/kotlin/io/ondeviceai/identity/BackendPolicyTest.kt`

**Interfaces:**
- Produces `IdentityBackend.backendId`, `backendVersion`, `modelBinding`, `embeddingSpace`, `availability()`, `embed(probe)`, `evaluateQuality(probe)`, `close()`; `ThresholdPolicy` and `PADPolicy` are immutable and expose `calibrationStatus`.
- Test backend accepts only fixture vectors and returns deterministic quality/score; no production model asset or threshold is introduced.

- [ ] Write tests proving explicit unavailable/missing backends fail without fallback, same backend ID with different model binding mismatches, system model binding is stable metadata, and unverified policies return `thresholdUnverified` with no score/outcome.
- [ ] Run `rtk proxy swift test --package-path apple --filter BackendPolicyTests` and `rtk proxy ./gradlew :android:identity:testDebugUnitTest --tests '*BackendPolicyTest'`; confirm RED.
- [ ] Implement registry selection, immutable space comparison, and policy validation; keep `ModelStore` dependency limited to lease acquisition, never template persistence.
- [ ] Re-run tests and record the exact fixture backend IDs/space revisions in `fixtures/contracts/face-identity.json`.
- [ ] Commit: `rtk git add apple/Sources/ODAIKitIdentity/IdentityBackend.swift apple/Sources/ODAIKitIdentity/TestIdentityBackend.swift apple/Tests/ODAIKitIdentityTests/BackendPolicyTests.swift android/identity/src/main/kotlin/io/ondeviceai/identity/IdentityBackend.kt android/identity/src/main/kotlin/io/ondeviceai/identity/TestIdentityBackend.kt android/identity/src/test/kotlin/io/ondeviceai/identity/BackendPolicyTest.kt fixtures/contracts/face-identity.json && rtk git commit -m "feat: add strict identity backend policy"`.

### Task 5: 1:1 verification and enrollment engine

**Files:**
- Create: `apple/Sources/ODAIKitIdentity/FaceVerification.swift`
- Create: `apple/Tests/ODAIKitIdentityTests/FaceVerificationTests.swift`
- Create: `android/identity/src/main/kotlin/io/ondeviceai/identity/FaceVerification.kt`
- Create: `android/identity/src/test/kotlin/io/ondeviceai/identity/FaceVerificationTest.kt`

**Interfaces:**
- Produces `FaceVerification.verify(probe:templateRef:grant:options:backend:) -> VerificationTask`, whose `VerificationResult` completed outcomes are `match(score,metric,threshold)` / `nonMatch(score,metric,threshold)` / `insufficientQuality(score:nil,metric:nil,threshold:nil)`; each includes policy/backend/model/template/preprocess revisions, face count, quality summary, session/run ID, and duration.
- Multi-face, empty/corrupt input, revoked/expired grant, missing template, incompatible space, and unverified policy are stable errors; no first-face selection.

- [ ] Write RED tests for single-face success, multi-face rejection, quality-before-score nullability, threshold policy unverified fail-closed, explicit backend no-fallback, cancel/close single terminal, and revoke racing result publication.
- [ ] Run `rtk proxy swift test --package-path apple --filter FaceVerificationTests` and `rtk proxy ./gradlew :android:identity:testDebugUnitTest --tests '*FaceVerificationTest'`; confirm expected failures.
- [ ] Implement admission through `IdentityStore`/`RevocationGate`, acquire template/model leases with `defer/finally`, evaluate quality before comparison, and publish only through the serial gate.
- [ ] Re-run targeted tests and assert no business subject ID/raw vector appears in result or diagnostic payload.
- [ ] Commit: `rtk git add apple/Sources/ODAIKitIdentity/FaceVerification.swift apple/Tests/ODAIKitIdentityTests/FaceVerificationTests.swift android/identity/src/main/kotlin/io/ondeviceai/identity/FaceVerification.kt android/identity/src/test/kotlin/io/ondeviceai/identity/FaceVerificationTest.kt && rtk git commit -m "feat: add face verification engine"`.

### Task 6: 1:N identification, aggregation, topK, and reporting fields

**Files:**
- Create: `apple/Sources/ODAIKitIdentity/FaceIdentification.swift`
- Create: `apple/Tests/ODAIKitIdentityTests/FaceIdentificationTests.swift`
- Create: `android/identity/src/main/kotlin/io/ondeviceai/identity/FaceIdentification.kt`
- Create: `android/identity/src/test/kotlin/io/ondeviceai/identity/FaceIdentificationTest.kt`

**Interfaces:**
- Produces `FaceIdentification.identify(probe:galleryRef:grant:options:backend:) -> IdentificationTask` and `IdentificationResult` outcomes `candidates([CandidateRef,score,rank])`, `unknown`, `insufficientQuality`; fixed `galleryRevision`, content digest, `aggregationPolicyId`, `thresholdPolicyId`, gallery size, enrollment counts, and FPIR/FNIR grouping metadata.
- Enforce `topK` 1…32 (default 32), non-empty gallery, aggregate same-candidate templates first (max qualified score in the fixture policy), then de-duplicate/sort/tie-break by opaque bytes; below-threshold candidates never appear in `unknown`.

- [ ] Write RED tests for multi-template candidate aggregation, stable tie-break, topK after dedupe, unknown/no low-score leakage, empty/deleted gallery errors, quality nullability, and gallery lease/revocation races.
- [ ] Run `rtk proxy swift test --package-path apple --filter FaceIdentificationTests` and `rtk proxy ./gradlew :android:identity:testDebugUnitTest --tests '*FaceIdentificationTest'`; confirm RED.
- [ ] Implement query against immutable snapshot/index leases with strict space/policy checks and serial revoke/result publication.
- [ ] Re-run tests and verify report fields distinguish comparison-level metrics from end-to-end rates; do not call small fixtures NIST-scale evidence.
- [ ] Commit: `rtk git add apple/Sources/ODAIKitIdentity/FaceIdentification.swift apple/Tests/ODAIKitIdentityTests/FaceIdentificationTests.swift android/identity/src/main/kotlin/io/ondeviceai/identity/FaceIdentification.kt android/identity/src/test/kotlin/io/ondeviceai/identity/FaceIdentificationTest.kt && rtk git commit -m "feat: add face identification engine"`.

### Task 7: Independent liveness session state machine and PAD test backend

**Files:**
- Create: `apple/Sources/ODAIKitIdentity/FaceLiveness.swift`
- Create: `apple/Tests/ODAIKitIdentityTests/FaceLivenessTests.swift`
- Create: `android/identity/src/main/kotlin/io/ondeviceai/identity/FaceLiveness.kt`
- Create: `android/identity/src/test/kotlin/io/ondeviceai/identity/FaceLivenessTest.kt`

**Interfaces:**
- Produces `openLivenessSession(grant:options:backend:) -> LivenessSession` with `start()`, `submitFrame(frame)`, `nextEvent()`, `finish()`, `cancel()`, `close()`; states `created -> running -> evaluating` or `challengeIssued -> evaluating -> completed|failed|cancelled -> closed`.
- Completed outcomes are `live|spoof|insufficientQuality|challengeFailed`; `unavailable|sessionTimeout|invalidChallenge|authorizationRevoked|closed` are failed errors; `LivenessProof` is one-time, nonce/session/purpose/digest/policy/model/epoch/freshness bound.

- [ ] Write RED tests for passive/active transitions, secure nonce uniqueness/replay, pre-challenge frame rejection, timestamp window, accepted/dropped/stale counters, one-frame-processing plus latest-pending bound, timeout/cancel/close idempotence, late frame discard, and unverified PAD null score fields.
- [ ] Run `rtk proxy swift test --package-path apple --filter FaceLivenessTests` and `rtk proxy ./gradlew :android:identity:testDebugUnitTest --tests '*FaceLivenessTest'`; confirm RED.
- [ ] Implement actor/Mutex state machine using existing `TaskGate`/`LatestFrameQueue` semantics and secure random challenge provider; make revocation terminate as failed authorizationRevoked.
- [ ] Re-run tests and verify exactly one Core terminal outcome and proof invalidation after close/revoke.
- [ ] Commit: `rtk git add apple/Sources/ODAIKitIdentity/FaceLiveness.swift apple/Tests/ODAIKitIdentityTests/FaceLivenessTests.swift android/identity/src/main/kotlin/io/ondeviceai/identity/FaceLiveness.kt android/identity/src/test/kotlin/io/ondeviceai/identity/FaceLivenessTest.kt && rtk git commit -m "feat: add face liveness state machine"`.

### Task 8: Explicit model-bound local backend and calibration gate

**Files:**
- Create: `apple/Sources/ODAIKitIdentity/LocalIdentityBackend.swift`
- Create: `apple/Tests/ODAIKitIdentityTests/LocalIdentityBackendTests.swift`
- Create: `android/identity/src/main/kotlin/io/ondeviceai/identity/LocalIdentityBackend.kt`
- Create: `android/identity/src/test/kotlin/io/ondeviceai/identity/LocalIdentityBackendTest.kt`
- Modify: `apple/Sources/ODAIKitModelStore/ModelStore.swift` only for a narrow read-only lease adapter if required; modify Android model-store analog only if required

**Interfaces:**
- Produces a backend that requires a declared `ModelRef`/`ModelLease` (or stable system binding), reports exact `backendVersion/modelBinding/EmbeddingSpaceId`, and refuses identity decisions when no authorized model, dataset revision, operating point, and calibrated policy are present.

- [ ] Write tests for model lease lifetime, binding mismatch, missing model/policy, and `calibrationStatus=unverified` fail-closed behavior; assert no “recognizable” result from synthetic image fixtures.
- [ ] Run `rtk proxy swift test --package-path apple --filter LocalIdentityBackendTests` and `rtk proxy ./gradlew :android:identity:testDebugUnitTest --tests '*LocalIdentityBackendTest'`; confirm RED.
- [ ] Implement only the adapter/contract and deterministic fixture embedding path needed by Tasks 5–7; do not add weights, cloud upload, production threshold, or accuracy claim.
- [ ] Re-run tests and record the explicit future gates (model selection, authorized dataset, threshold/PAD calibration, device matrix) in validation documentation without changing the untracked source draft.
- [ ] Commit: `rtk git add apple/Sources/ODAIKitIdentity/LocalIdentityBackend.swift apple/Tests/ODAIKitIdentityTests/LocalIdentityBackendTests.swift android/identity/src/main/kotlin/io/ondeviceai/identity/LocalIdentityBackend.kt android/identity/src/test/kotlin/io/ondeviceai/identity/LocalIdentityBackendTest.kt && rtk git commit -m "feat: add model-bound local identity backend"`.

### Task 9: Production identity model selection and calibrated backend gate

**Files:**
- Create: `docs/validation/p10-identity-model-selection.md`
- Create after selection: `fixtures/manifests/face-identity-model.json`
- Create after selection: `apple/Sources/ODAIKitLocalFaceIdentity/LocalFaceIdentityBackend.swift`
- Create after selection: `apple/Tests/ODAIKitLocalFaceIdentityTests/LocalFaceIdentityBackendTests.swift`
- Create after selection: `android/backend-local-face-identity/src/main/kotlin/io/ondeviceai/backend/identity/LocalFaceIdentityBackend.kt`
- Create after selection: `android/backend-local-face-identity/src/test/kotlin/io/ondeviceai/backend/identity/LocalFaceIdentityBackendTest.kt`
- Modify after selection: `apple/Package.swift`, `settings.gradle.kts`

**Interfaces:**
- Consumes Task 8's `IdentityBackend` adapter and produces a separately linked backend with one immutable `ModelBinding`, `EmbeddingSpaceId`, and verified 1:1/1:N `ThresholdPolicy` revisions.
- Gate inputs are a redistributable pinned model revision, host-approved authorized development/validation datasets, chosen operating points, gallery-size/enrollment strata, and documented FMR/FNMR/FAR/FRR plus FPIR/FNIR denominators. None are fixed by this plan.

- [ ] Write the selection record first, comparing only candidates whose license, redistribution, input/output signature, preprocessing, resource envelope, and device support are evidenced; mark the work package `blocked-unverified` if no candidate and authorized data are approved.
- [ ] After approval, write failing Apple/Android backend tests for manifest/model hash binding, preprocess revision, vector dimension/dtype/normalization, model lease lifetime, and calibrated policy acceptance; run `rtk proxy swift test --package-path apple --filter ODAIKitLocalFaceIdentityTests` and `rtk proxy ./gradlew :android:backend-local-face-identity:testDebugUnitTest` to confirm RED.
- [ ] Integrate the selected local runtime behind the Task 8 adapter; do not use Apple Vision/ML Kit face detection output as an identity embedding and do not enable outcomes for `unverified` policies.
- [ ] Re-run the targeted tests and an authorized-device accuracy harness; publish only grouped metrics with dataset/model/policy/device revisions and confidence intervals, never raw biometrics.
- [ ] Commit selected-model files only after the gate passes; otherwise commit the evidence record alone and leave the production backend unavailable.

### Task 10: Selected platform PAD backend and calibrated attack evaluation gate

**Files:**
- Create: `docs/validation/p10-pad-backend-selection.md`
- Create after selection: `apple/Sources/ODAIKitPlatformPAD/PlatformPADBackend.swift`
- Create after selection: `apple/Tests/ODAIKitPlatformPADTests/PlatformPADBackendTests.swift`
- Create after selection: `android/backend-platform-pad/src/main/kotlin/io/ondeviceai/backend/liveness/PlatformPADBackend.kt`
- Create after selection: `android/backend-platform-pad/src/test/kotlin/io/ondeviceai/backend/liveness/PlatformPADBackendTest.kt`
- Modify after selection: `apple/Package.swift`, `settings.gradle.kts`

**Interfaces:**
- Consumes Task 7's `LivenessSession`/`PADPolicy`; produces an optional backend whose passive/active mode, challenge protocol, model/system binding, frame constraints, and categorical-or-continuous result shape are fixed in its selection record.
- Gate inputs are an approved backend/SDK, license and offline/privacy review, attack taxonomy and authorized PAD dataset, operating point, device matrix, and calibrated PAD policy. The design does not authorize APCER/BPCER/ACER or standards-compliance claims.

- [ ] Record backend capability, offline behavior, credential/region/upload absence or requirements, supported attacks, categorical/continuous scoring, and platform floors; if the evidence or authorized PAD data are absent, retain Task 7's test backend and mark production liveness unavailable.
- [ ] After approval, write failing tests for exact availability reasons, policy/model mismatch, challenge binding, timeout/cancellation, and categorical score/metric/threshold all-null behavior; run `rtk proxy swift test --package-path apple --filter ODAIKitPlatformPADTests` and `rtk proxy ./gradlew :android:backend-platform-pad:testDebugUnitTest` to confirm RED.
- [ ] Implement the selected backend as a separate product/artifact and feed events through Task 7's state machine; never bypass its nonce, authorization, frame-count, or single-terminal rules.
- [ ] Re-run the targeted suites and authorized device/attack harness; report attack and genuine-user reject counts, denominators, device/backend/policy revisions, and confidence intervals without claiming untested attack coverage.
- [ ] Commit selected PAD files only after the gate passes; otherwise commit the evidence record alone and keep the capability typed unavailable.

### Task 11: KMP typed bridge and Demo flows

**Files:**
- Modify: `kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/FaceBridge.kt` (split identity contracts into focused files if needed)
- Create/modify: `kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/IdentityBridge.kt`
- Create/modify: `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/FaceViewModel.kt` and `FaceScreen.kt`
- Create/modify: `demo/iosApp/IOSFaceBridge.swift`
- Modify only after reconciling and preserving the user's signing delta: `demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj`
- Create/modify: `demo/androidApp/src/main/kotlin/io/ondeviceai/demo/MainActivity.kt`
- Create: `demo/shared/src/commonTest/kotlin/io/ondeviceai/demo/IdentityViewModelTest.kt`

**Interfaces:**
- Produces typed bridge methods `createCandidate`, `enroll`, `verify`, `createGalleryBuilder/addTemplate/removeTemplate/publishGallery`, `identify`, `openLivenessSession`, and `revoke/delete/awaitDeletion`; callbacks/events carry opaque refs and stable errors only.
- Demo exposes separate enrollment/verify/identify/liveness actions, displays `unverified`/authorization/error states, and never treats face detection or tracking IDs as identity.

- [ ] Write RED common tests for generation-safe cancellation, stale callback suppression, explicit backend choice, opaque result rendering, liveness one-terminal handling, and no business-ID fields.
- [ ] Run `rtk proxy ./gradlew :kmp-binding:allTests :demo:shared:allTests :kmp-binding:compileKotlinIosSimulatorArm64`; confirm RED.
- [ ] Implement platform adapters and Compose/Swift demo wiring while preserving existing signing project changes and current detection flows.
- [ ] Re-run common tests plus `rtk proxy ./gradlew :demo:androidApp:assembleDebug` and `rtk proxy xcodebuild -project demo/iosApp/OnDeviceAIDemo.xcodeproj -scheme OnDeviceAIDemo -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`.
- [ ] Commit bridge/demo source and tests after the user-owned signing delta has an independently auditable base; stage only the identity-related hunk in `project.pbxproj`, never the pre-existing signing hunk or `docs/validation/face-identity.md`.

### Task 12: Package/distribution consumers, device evidence, and handoff documentation

**Files:**
- Modify: `scripts/generate-distribution-project.rb`, `scripts/package-apple.sh`, `scripts/generate-binary-consumer.rb`, `scripts/verify-apple.sh`, `scripts/verify-android.sh`
- Create: `examples/ios-identity-native/` and `examples/android-identity-native/` minimal consumers (or extend existing consumers without adding unrelated modules)
- Create: `docs/validation/p10-face-identity.md`
- Modify: `README.md` and `docs/ROADMAP.md` only to mark contract/backend/distribution gates with evidence

**Interfaces:**
- Distribution exposes identity products/artifacts independently (`ODAIKitIdentity`, optional model/backend product; `io.ondeviceai:identity` and explicitly selected backend) and proves consumers do not pull KMP/Compose/MLX/cloud dependencies.
- Validation report records commit, exact commands, artifact hashes, Apple simulator/device and Android API/device evidence, backend/model/policy revisions, and unverified gaps; it never converts synthetic fixtures into biometric accuracy.

- [ ] Write failing packaging/consumer checks for product/module isolation, fresh-output refusal, typed API compilation, and artifact metadata containing model/policy binding without weights.
- [ ] Run targeted script tests and consumer compiles to establish RED where files are absent.
- [ ] Implement minimal distribution wiring and evidence capture; preserve signing/demo project and untracked validation draft, use fresh ignored artifact directories, and do not publish remote credentials or production assets.
- [ ] Run `rtk proxy swift test --package-path apple`, `rtk proxy ./scripts/verify-android.sh`, `rtk env ODAI_ARTIFACTS_DIR=artifacts/P10/identity sh scripts/package-apple.sh`, `rtk env ODAI_ARTIFACTS_DIR=artifacts/P10/identity ruby scripts/generate-binary-consumer.rb`, `rtk proxy xcodebuild -project artifacts/P10/identity/BinaryConsumer/BinaryConsumer.xcodeproj -scheme BinaryConsumer -destination 'generic/platform=iOS Simulator' -derivedDataPath artifacts/P10/identity/BinaryBuild CODE_SIGNING_ALLOWED=NO build`, `rtk proxy ./gradlew publishToMavenLocal :examples:android-identity-native:assembleDebug -PusePublishedSdk=true`, and `rtk proxy python3 -m unittest discover -s tools/tests -v`.
- [ ] Record true device gates separately: iOS 15/API 26 minimum devices, camera/permission behavior, model/data authorization, calibration and accuracy; stop before claiming production identity/liveness quality.
- [ ] Commit only owned package/example/scripts/docs changes: `rtk git add scripts/generate-distribution-project.rb scripts/package-apple.sh scripts/generate-binary-consumer.rb scripts/verify-apple.sh scripts/verify-android.sh examples/ios-identity-native examples/android-identity-native docs/validation/p10-face-identity.md README.md docs/ROADMAP.md && rtk git commit -m "feat: package face identity contracts"`; leave unrelated dirty files untouched.

## Verification and Stop Condition

After each task, run its targeted RED-GREEN tests before starting the next task; after Task 12 run the complete Apple/Kotlin suites, consumer builds, lint/package checks, and inspect `rtk git diff --check` plus `rtk git status --short`. Review the final diff for absence of raw images, vectors, business IDs, unapproved model weights, credentials, or unsupported calibrated-accuracy claims. Tasks 1–8 may complete the enforceable contracts, data lifecycle, deterministic test backend, and model-binding adapter; they do not satisfy the roadmap's production algorithm outcome. Tasks 9–10 are required to unlock real identity/liveness outcomes and fail closed when their model, authorized data, calibration, or device evidence is absent. Tasks 11–12 may still ship a typed unavailable/unverified Demo and distribution slice while those gates remain open, but the roadmap stays incomplete until both production gates and device acceptance pass.

## Self-Review Checklist (plan author)

- Spec coverage checked section-by-section: opaque refs/grants and epoch (Tasks 1–2), compatibility/model binding and errors (Tasks 1/4/8), 1:1 (5), 1:N metrics/aggregation (6), PAD/liveness state and proof (7), deletion/storage/leases/snapshots (2–3), strict backend selection (1/4), production identity/PAD selection and calibration (9–10), and KMP/Demo/distribution/device evidence (11–12).
- Step scan checked: every implementable task has a failing test, explicit command, minimal implementation boundary, passing verification, and handoff commit; Tasks 9–10 instead have explicit fail-closed gates because this specification does not select their production model, backend, dataset, or threshold.
- Type consistency checked: `TemplateRef`/`GalleryRef`/`CandidateRef`, `EmbeddingSpaceId`, `AuthorizationGrant`, `ThresholdPolicy`/`PADPolicy`, task terminal states, and backend selection names are reused consistently across Swift, Kotlin, and KMP boundaries.
- Review-focus cases are pinned to owning tasks above; no uncovered high-risk input class was found.
- Scope checked against the existing tree: Apple package targets, Android modules, Core task/queue primitives, ModelStore, KMP bridge, Demo, distribution scripts, and validation docs are named; existing dirty signing project and untracked validation draft are explicitly preserved.
