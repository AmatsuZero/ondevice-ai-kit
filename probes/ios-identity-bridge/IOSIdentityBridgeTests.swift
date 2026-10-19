import CryptoKit
import ODAIKitCore
import XCTest

@testable import ODAIKitIdentity

#if ODAI_DEMO_BINDING
  import ODAIKitDemo
#else
  import ODAIKitBinding
#endif

@MainActor
final class IOSIdentityBridgeTests: XCTestCase {
  private func fixture(block: Bool = false, blockStop: Bool = false) throws -> Fixture {
    let f = try Fixture(block: block, blockStop: blockStop)
    addTeardownBlock { await f.shutdown() }
    return f
  }
  private func invoke<T>(
    _ start: (IdentityCallback) -> IdentityBridgeTask, as type: T.Type = T.self
  ) async throws -> T {
    let callback = Capture()
    _ = start(callback)
    await fulfillment(of: [callback.done], timeout: 4)
    if let failure = callback.failure { throw CallbackFailure(value: failure) }
    return try XCTUnwrap(callback.value as? T)
  }
  private func failure(_ start: (IdentityCallback) -> IdentityBridgeTask) async
    -> IdentityBridgeFailure
  {
    let callback = Capture()
    _ = start(callback)
    await fulfillment(of: [callback.done], timeout: 4)
    if let failure = callback.failure { return failure }
    XCTFail("expected bridge failure")
    return IdentityBridgeFailureRejected(
      code: .invalidInput, backendId: nil, modelBinding: nil, policyId: nil, sessionId: nil)
  }
  func testNativeStoresRoundTripAllIdentityOperationsAndMetadata() async throws {
    let f = try fixture()
    let candidate: IdentityCandidateReference = try await invoke {
      f.bridge.createCandidate(grant: f.grantWire, callback: $0)
    }
    let enrolled: IdentityEnrollmentResult = try await invoke {
      f.bridge.enroll(
        probe: f.probe, candidate: candidate, grant: f.grantWire, options: f.enrollment,
        backend: f.defaultSelection, callback: $0)
    }
    let template = try XCTUnwrap(enrolled.template_)
    XCTAssertEqual(enrolled.metadata.optionsRevision, "enroll-v2")
    XCTAssertEqual(enrolled.metadata.preprocessingRevision, "fixture-p1")
    XCTAssertEqual(enrolled.embeddingSpace.canonicalValue, f.backend.embeddingSpace.canonicalJSON())
    let verified: IdentityVerificationResult = try await invoke {
      f.bridge.verify(
        probe: f.probe, template: template, grant: f.grantWire,
        options: try! f.verificationOptions(), backend: f.defaultSelection, callback: $0)
    }
    XCTAssertEqual(verified.outcome, .match)
    XCTAssertEqual(verified.scoreFields.score?.doubleValue, 1)
    XCTAssertEqual(verified.metadata.optionsRevision, "verify-v3")
    let builder: IdentityGalleryBuilderReference = try await invoke {
      f.bridge.createGalleryBuilder(
        baseGallery: nil, grant: f.grantWire, options: try! f.galleryOptions(),
        backend: f.defaultSelection, callback: $0)
    }
    let unit: AnyObject = try await invoke {
      f.bridge.addTemplate(builder: builder, template: template, grant: f.grantWire, callback: $0)
    }
    XCTAssertTrue(
      unit === IdentityBridgeValues.shared.unitValue as AnyObject,
      "Unit callback must contain real Kotlin Unit")
    let _: AnyObject = try await invoke {
      f.bridge.removeTemplate(
        builder: builder, template: template, grant: f.grantWire, callback: $0)
    }
    let _: AnyObject = try await invoke {
      f.bridge.addTemplate(builder: builder, template: template, grant: f.grantWire, callback: $0)
    }
    let gallery: IdentityGalleryReference = try await invoke {
      f.bridge.publishGallery(builder: builder, callback: $0)
    }
    let _: AnyObject = try await invoke {
      f.bridge.closeGalleryBuilder(builder: builder, callback: $0)
    }
    let _: AnyObject = try await invoke {
      f.bridge.closeGalleryBuilder(builder: builder, callback: $0)
    }
    let result: IdentityIdentificationResult = try await invoke {
      f.bridge.identify(
        probe: f.probe, gallery: gallery, grant: f.grantWire,
        options: IdentityIdentificationOptions(topK: 4, policy: nil, revision: "identify-v4"),
        backend: f.defaultSelection, callback: $0)
    }
    XCTAssertEqual(result.candidates.first?.candidate.canonicalValue, candidate.canonicalValue)
    XCTAssertEqual(result.galleryCandidateCount, 1)
    XCTAssertEqual(result.galleryTemplateCount, 1)
    XCTAssertEqual(result.grouping.topK, 4)
    XCTAssertEqual(result.grouping.datasetRevision, "synthetic-data")
    XCTAssertEqual(result.metadata.preprocessingRevision, "fixture-p1")
    XCTAssertEqual(result.galleryContentDigest.count, 64)
    let receipt: IdentityDeletionReceipt = try await invoke {
      f.bridge.deleteGallery(reference: gallery, ownership: f.owner, callback: $0)
    }
    XCTAssertEqual(receipt.store, .index)
    let status: IdentityBridgeDeletionStatus = try await invoke {
      f.bridge.awaitDeletion(receipt: receipt, options: f.poll, callback: $0)
    }
    XCTAssertEqual(status, .purged)
    let deleted: IdentityDeletionReceipt = try await invoke {
      f.bridge.deleteTemplate(reference: template, ownership: f.owner, callback: $0)
    }
    let purged: IdentityBridgeDeletionStatus = try await invoke {
      f.bridge.awaitDeletion(
        receipt: deleted,
        options: IdentityDeletionWaitOptions(durationNanos: KotlinLong(value: 1_000_000)),
        callback: $0)
    }
    XCTAssertEqual(purged, .purged)
    let _: IdentityDeletionReceipt = try await invoke {
      f.bridge.deleteCandidate(reference: candidate, ownership: f.owner, callback: $0)
    }
    let revoked: IdentityRevocationReceipt = try await invoke {
      f.bridge.revokeGrant(grantId: f.grant.grantId, ownership: f.owner, callback: $0)
    }
    let revokedStatus: IdentityBridgeDeletionStatus = try await invoke {
      f.bridge.awaitRevocation(receipt: revoked, options: f.poll, callback: $0)
    }
    XCTAssertEqual(revokedStatus, .purged)
  }
  func testExplicitBackendErrorsAndUnverifiedPolicyNeverFallback() async throws {
    let f = try fixture()
    let candidate: IdentityCandidateReference = try await invoke {
      f.bridge.createCandidate(grant: f.grantWire, callback: $0)
    }
    for (id, code) in [
      ("missing", IdentityBridgeErrorCode.backendNotRegistered),
      ("optional.fixture", .backendUnavailable),
    ] {
      let error = await failure {
        f.bridge.enroll(
          probe: f.probe, candidate: candidate, grant: f.grantWire, options: f.enrollment,
          backend: IdentityBackendSelectionExplicit(backendId: id), callback: $0)
      }
      XCTAssertEqual((error as? IdentityBridgeFailureRejected)?.code, code)
    }
    let error = await failure {
      f.bridge.createGalleryBuilder(
        baseGallery: nil, grant: f.grantWire, options: try! f.galleryOptions(),
        backend: IdentityBackendSelectionExplicit(backendId: "optional.fixture"), callback: $0)
    }
    XCTAssertEqual((error as? IdentityBridgeFailureRejected)?.code, .backendUnavailable)
    let malformed = await failure {
      f.bridge.createCandidate(
        grant: IdentityAuthorizationGrant(canonicalValue: "{}"), callback: $0)
    }
    XCTAssertEqual((malformed as? IdentityBridgeFailureRejected)?.code, .invalidInput)
    XCTAssertEqual(f.backend.embedCount, 0)
  }
  func testUnknownSwiftBackendSelectionNeverFallsBackToDefault() async throws {
    let f = try fixture()
    let candidate: IdentityCandidateReference = try await invoke {
      f.bridge.createCandidate(grant: f.grantWire, callback: $0)
    }
    let error = await failure {
      f.bridge.enroll(
        probe: f.probe, candidate: candidate, grant: f.grantWire,
        options: f.enrollment, backend: UnknownSelection(), callback: $0)
    }
    XCTAssertEqual((error as? IdentityBridgeFailureRejected)?.code, .unsupportedOption)
    XCTAssertEqual(f.backend.embedCount, 0)
  }
  func testUnverifiedPolicyRejectsVerificationAndGalleryPublication() async throws {
    let f = try fixture()
    let candidate: IdentityCandidateReference = try await invoke {
      f.bridge.createCandidate(grant: f.grantWire, callback: $0)
    }
    let enrolled: IdentityEnrollmentResult = try await invoke {
      f.bridge.enroll(
        probe: f.probe, candidate: candidate, grant: f.grantWire,
        options: f.enrollment, backend: f.defaultSelection, callback: $0)
    }
    let template = try XCTUnwrap(enrolled.template_)
    let policy = try ThresholdPolicy(
      id: "unverified", revision: "1",
      embeddingSpace: f.backend.embeddingSpace, scoreMetric: "cosine",
      scoreDirection: .higherIsBetter, calibrationStatus: .unverified)
    let wire = IdentityThresholdPolicy(canonicalValue: policy.canonicalJSON())
    let error = await failure {
      f.bridge.verify(
        probe: f.probe, template: template, grant: f.grantWire,
        options: IdentityVerificationOptions(policy: wire, revision: "unverified-options"),
        backend: f.defaultSelection, callback: $0)
    }
    XCTAssertEqual((error as? IdentityBridgeFailureRejected)?.code, .thresholdUnverified)
    XCTAssertEqual(f.backend.embedCount, 1)
    let galleryPolicy = try ThresholdPolicy(
      id: "unverified-gallery", revision: "1",
      embeddingSpace: f.backend.embeddingSpace, scoreMetric: "cosine",
      scoreDirection: .higherIsBetter, calibrationStatus: .unverified, kind: .identification)
    let galleryError = await failure {
      f.bridge.createGalleryBuilder(
        baseGallery: nil, grant: f.grantWire,
        options: IdentityGalleryOptions(
          policy: IdentityThresholdPolicy(canonicalValue: galleryPolicy.canonicalJSON()),
          aggregationPolicyId: "subject-max-v1"),
        backend: f.defaultSelection, callback: $0)
    }
    XCTAssertEqual((galleryError as? IdentityBridgeFailureRejected)?.code, .thresholdUnverified)
  }
  func testActivePADChallengeAndSingleUseProofUseNativeValidation() async throws {
    let f = try fixture()
    let session: IdentityLivenessSession = try await invoke {
      f.bridge.openLivenessSession(
        grant: f.grantWire, options: f.padOptions,
        backend: IdentityBackendSelectionExplicit(backendId: "pad.fixture"), callback: $0)
    }
    let _: AnyObject = try await invoke { session.start(callback: $0) }
    let started: IdentityLivenessEvent = try await invoke { session.nextEvent(callback: $0) }
    XCTAssertTrue(started is IdentityLivenessEventStarted)
    let event: IdentityLivenessEventChallengeIssued = try await invoke {
      session.nextEvent(callback: $0)
    }
    let challenge = try LivenessChallenge.decodeJSON(event.challenge.canonicalValue)
    XCTAssertEqual(challenge.sessionId, session.sessionId)
    let bytes = KotlinByteArray(size: 2)
    bytes.set(index: 0, value: 1)
    bytes.set(index: 1, value: 2)
    XCTAssertTrue(
      session.submitFrame(
        frame: IdentityLivenessFrame(
          encodedFrame: bytes, timestampNanos: SystemIdentityMonotonicClock().nowNanos,
          revision: "frame-v1", challenge: event.challenge)))
    let result: IdentityLivenessResult = try await invoke { session.result(callback: $0) }
    XCTAssertEqual(result.outcome, .live)
    XCTAssertEqual(result.acceptedFrameCount, 1)
    XCTAssertNil(result.metadata.preprocessingRevision)
    XCTAssertEqual(result.metadata.inputRevision, "frame-v1")
    let proof = try XCTUnwrap(result.proof)
    let _: AnyObject = try await invoke {
      session.consumeProof(
        proof: proof, captureDigest: result.captureDigest, purpose: "login", callback: $0)
    }
    let duplicate = await failure {
      session.consumeProof(
        proof: proof, captureDigest: result.captureDigest, purpose: "login", callback: $0)
    }
    XCTAssertEqual((duplicate as? IdentityBridgeFailureRejected)?.code, .invalidChallenge)
    session.close()
  }
  func testSessionCloseRejectsAdmissionImmediatelyAndBridgeCloseRejectsSessionCalls() async throws {
    let f = try fixture()
    let session: IdentityLivenessSession = try await invoke {
      f.bridge.openLivenessSession(
        grant: f.grantWire, options: f.padOptions,
        backend: IdentityBackendSelectionExplicit(backendId: "pad.fixture"), callback: $0)
    }
    session.close()
    let error = await failure { session.start(callback: $0) }
    XCTAssertEqual((error as? IdentityBridgeFailureRejected)?.code, .closed)
    let next: IdentityLivenessSession = try await invoke {
      f.bridge.openLivenessSession(
        grant: f.grantWire, options: f.padOptions,
        backend: IdentityBackendSelectionExplicit(backendId: "pad.fixture"), callback: $0)
    }
    f.bridge.close()
    let closed = await failure { next.nextEvent(callback: $0) }
    XCTAssertEqual((closed as? IdentityBridgeFailureRejected)?.code, .closed)
  }
  func testMalformedChallengeFrameIsRejectedWithoutPoisoningSession() async throws {
    let f = try fixture()
    let session: IdentityLivenessSession = try await invoke {
      f.bridge.openLivenessSession(
        grant: f.grantWire, options: f.padOptions,
        backend: IdentityBackendSelectionExplicit(backendId: "pad.fixture"), callback: $0)
    }
    let _: AnyObject = try await invoke { session.start(callback: $0) }
    let _: IdentityLivenessEvent = try await invoke { session.nextEvent(callback: $0) }
    let challenge: IdentityLivenessEventChallengeIssued = try await invoke {
      session.nextEvent(callback: $0)
    }
    let bytes = KotlinByteArray(size: 1)
    bytes.set(index: 0, value: 1)
    XCTAssertFalse(
      session.submitFrame(
        frame: IdentityLivenessFrame(
          encodedFrame: bytes, timestampNanos: SystemIdentityMonotonicClock().nowNanos,
          revision: "frame-v1", challenge: IdentityLivenessChallenge(canonicalValue: "{}"))))
    XCTAssertTrue(
      session.submitFrame(
        frame: IdentityLivenessFrame(
          encodedFrame: bytes, timestampNanos: SystemIdentityMonotonicClock().nowNanos,
          revision: "frame-v1", challenge: challenge.challenge)))
    let result: IdentityLivenessResult = try await invoke { session.result(callback: $0) }
    XCTAssertEqual(result.outcome, .live)
  }
  func testCancellingEventWaitClosesNativeSessionAndDeliversOnce() async throws {
    let f = try fixture()
    let session: IdentityLivenessSession = try await invoke {
      f.bridge.openLivenessSession(
        grant: f.grantWire, options: f.padOptions,
        backend: IdentityBackendSelectionExplicit(backendId: "pad.fixture"), callback: $0)
    }
    let _: AnyObject = try await invoke { session.start(callback: $0) }
    let _: IdentityLivenessEvent = try await invoke { session.nextEvent(callback: $0) }
    let _: IdentityLivenessEvent = try await invoke { session.nextEvent(callback: $0) }
    let callback = Capture()
    let task = session.nextEvent(callback: callback)
    task.cancel()
    await fulfillment(of: [callback.done], timeout: 1)
    XCTAssertEqual(callback.terminals, 1)
    let closed = await failure { session.start(callback: $0) }
    XCTAssertEqual((closed as? IdentityBridgeFailureRejected)?.code, .closed)
  }
  func testBridgeCloseStopsNativeWorkWithClosedErrorAndBorrowsStore() async throws {
    let f = try fixture(block: true)
    let candidate: IdentityCandidateReference = try await invoke {
      f.bridge.createCandidate(grant: f.grantWire, callback: $0)
    }
    let callback = Capture()
    _ = f.bridge.enroll(
      probe: f.probe, candidate: candidate, grant: f.grantWire, options: f.enrollment,
      backend: f.defaultSelection, callback: callback)
    let entered = await f.backend.waitEntered()
    XCTAssertTrue(entered)
    f.bridge.close()
    let stopped = await f.backend.waitStopped()
    XCTAssertTrue(stopped)
    f.backend.release.signal()
    await fulfillment(of: [callback.done], timeout: 2)
    XCTAssertEqual((callback.failure as? IdentityBridgeFailureRejected)?.code, .closed)
    XCTAssertEqual(callback.terminals, 1)
    _ = try await f.store.createCandidate(f.grant)
  }
  func testCancellationDrainsNativeWorkAndStopHandlerBeforeCallback() async throws {
    let f = try fixture(block: true, blockStop: true)
    let candidate: IdentityCandidateReference = try await invoke {
      f.bridge.createCandidate(grant: f.grantWire, callback: $0)
    }
    let callback = Capture()
    let task = f.bridge.enroll(
      probe: f.probe, candidate: candidate, grant: f.grantWire, options: f.enrollment,
      backend: f.defaultSelection, callback: callback)
    let entered = await f.backend.waitEntered()
    XCTAssertTrue(entered)
    task.cancel()
    let stopped = await f.backend.waitStopped()
    XCTAssertTrue(stopped)
    f.backend.release.signal()
    try await Task.sleep(nanoseconds: 60_000_000)
    XCTAssertEqual(callback.terminals, 0, "terminal callback overtook native stop drain")
    f.backend.stopRelease.signal()
    await fulfillment(of: [callback.done], timeout: 4)
    XCTAssertTrue(callback.failure is IdentityBridgeFailureCancelled)
    XCTAssertEqual(callback.terminals, 1)
    f.bridge.close()
    let closed = await failure { f.bridge.createCandidate(grant: f.grantWire, callback: $0) }
    XCTAssertEqual((closed as? IdentityBridgeFailureRejected)?.code, .closed)
    _ = try await f.store.createCandidate(f.grant)
  }
}

private struct CallbackFailure: Error { let value: IdentityBridgeFailure }
private final class Capture: NSObject, IdentityCallback {
  let done = XCTestExpectation(description: "identity callback")
  var value: Any?
  var failure: IdentityBridgeFailure?
  var terminals = 0
  func onSuccess(value: Any?) {
    XCTAssertTrue(Thread.isMainThread)
    self.value = value
    terminals += 1
    done.fulfill()
  }
  func onFailure(error: IdentityBridgeFailure) {
    XCTAssertTrue(Thread.isMainThread)
    failure = error
    terminals += 1
    done.fulfill()
  }
}
private final class Fixture: @unchecked Sendable {
  let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
  let store: IdentityStore
  let indexes: IndexStore
  let backend: BlockingBackend
  let verifier: FaceVerification
  let identifier: FaceIdentification
  let pad: FaceLiveness
  let bridge: IOSIdentityBridge
  let grant: AuthorizationGrant
  let grantWire: IdentityAuthorizationGrant
  let owner: IdentityOwnershipAuthorization
  let probe = IdentityProbeReference(canonicalValue: "host-owned-input")
  let defaultSelection = IdentityBackendSelectionDefault.shared
  let enrollment = IdentityEnrollmentOptions(
    qualityFloor: 0.5, qualityPolicyId: "quality-v1", revision: "enroll-v2")
  let poll = IdentityDeletionWaitOptions(durationNanos: nil)
  let padOptions: IdentityLivenessOptions
  init(block: Bool, blockStop: Bool) throws {
    let keys = TestKeys()
    store = try IdentityStore(
      root: root.appendingPathComponent("identity"), purpose: "login",
      authorizationProvider: TestAuthority(), keyProvider: keys, wallClock: TestWallClock(),
      monotonicClock: SystemIdentityMonotonicClock())
    backend = try BlockingBackend(block: block, blockStop: blockStop)
    indexes = try IndexStore(
      root: root.appendingPathComponent("index"), identityStore: store, keyProvider: keys,
      buildIndex: { values, signal in
        try signal.check()
        return try fixtureIdentificationIndex(values)
      })
    let backends: [any IdentityBackend] = [
      backend, try FixtureIdentityBackend.optionalUnavailable(),
    ]
    verifier = try FaceVerification(
      identityStore: store, defaultBackendId: backend.backendId, backends: backends)
    identifier = try FaceIdentification(
      identityStore: store, indexStore: indexes, defaultBackendId: backend.backendId,
      backends: backends)
    let padBackend = try TestPAD()
    pad = try FaceLiveness(identityStore: store, backends: [padBackend], wallClock: TestWallClock())
    let policy = try PADPolicy(
      id: "pad-policy", revision: "1", backendId: padBackend.backendId,
      backendVersion: padBackend.backendVersion, modelBinding: padBackend.modelBinding,
      calibrationStatus: .verified, mode: .active, challengePolicyRevision: "challenge-v1",
      minimumFrames: 1, maximumDurationNanos: 5_000_000_000, qualityFloor: 0.5,
      classificationPolicyRevision: "class-v1", datasetRevision: "synthetic-pad",
      operatingPoint: "synthetic-point", attackTypes: ["print"])
    padOptions = IdentityLivenessOptions(
      policy: IdentityPADPolicy(canonicalValue: policy.canonicalJSON()), purpose: "login",
      revision: "pad-options")
    grant = try AuthorizationGrant(
      grantId: "grant", revocationDomainId: "domain", scope: Set(IdentityScope.allCases),
      purpose: "login", issuedAtMillis: 0, expiresAtMillis: nil, authorizationEpoch: 0,
      evidenceRef: "evidence")
    grantWire = IdentityAuthorizationGrant(canonicalValue: grant.canonicalJSON())
    owner = IdentityOwnershipAuthorization(storeId: store.storeId, evidenceRef: "owner")
    bridge = IOSIdentityBridge(
      store: store, indexes: indexes,
      registry: try IdentityBackendRegistry(
        defaultBackendId: backend.backendId,
        registrations: backends.map {
          IdentityBackendRegistration(backendId: $0.backendId, backend: $0)
        }), verification: verifier, identification: identifier, liveness: pad,
      probeResolver: { reference in
        guard reference.canonicalValue == "host-owned-input" else {
          throw ODAIKitIdentity.IdentityError(.invalidInput)
        }
        return try IdentityProbe(fixtureID: "probe", vector: [1, 0])
      })
  }
  func policy(_ kind: IdentityPolicyKind) throws -> IdentityThresholdPolicy {
    let policy = try ThresholdPolicy(
      id: "policy", revision: "r1", embeddingSpace: backend.embeddingSpace, scoreMetric: "cosine",
      scoreDirection: .higherIsBetter, calibrationStatus: .verified, kind: kind, threshold: 0.8,
      qualityFloor: 0.5, operatingPoint: "synthetic-point", datasetRevision: "synthetic-data",
      gallerySizeRange: kind == .identification ? 1...32 : nil, enrollmentRule: "multi-template")
    return IdentityThresholdPolicy(canonicalValue: policy.canonicalJSON())
  }
  func verificationOptions() throws -> IdentityVerificationOptions {
    IdentityVerificationOptions(policy: try policy(.verification), revision: "verify-v3")
  }
  func galleryOptions() throws -> IdentityGalleryOptions {
    IdentityGalleryOptions(
      policy: try policy(.identification), aggregationPolicyId: "subject-max-v1")
  }
  func shutdown() async {
    backend.release.signal()
    backend.stopRelease.signal()
    bridge.close()
    await pad.close()
    await verifier.close()
    await identifier.close()
    await indexes.close()
    try? await store.close()
    try? FileManager.default.removeItem(at: root)
  }
}
private struct TestWallClock: IdentityWallClock { let nowMillis: Int64 = 10 }
private struct TestAuthority: IdentityAuthorizationProvider {
  func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws
    -> Int64
  { max(minimumEpoch, grant.authorizationEpoch) }
  func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {
    if authorization.evidenceRef != "owner" {
      throw ODAIKitIdentity.IdentityError(.authorizationRequired)
    }
  }
}
private final class TestKeys: IdentityKeyProvider, @unchecked Sendable {
  private let lock = NSLock()
  private var keys: [String: SymmetricKey] = [:]
  func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
    lock.lock()
    defer { lock.unlock() }
    if let key = keys[id] { return key }
    guard createIfMissing else { throw ODAIKitIdentity.IdentityError(.invalidInput) }
    let key = SymmetricKey(size: .bits256)
    keys[id] = key
    return key
  }
  func deleteKey(id: String) {
    lock.lock()
    keys.removeValue(forKey: id)
    lock.unlock()
  }
}
private final class BlockingBackend: IdentityBackend, @unchecked Sendable {
  let base: FixtureIdentityBackend
  let blocks: Bool
  let blockStop: Bool
  let entered = DispatchSemaphore(value: 0)
  let stopped = DispatchSemaphore(value: 0)
  let release = DispatchSemaphore(value: 0)
  let stopRelease = DispatchSemaphore(value: 0)
  private let lock = NSLock()
  private var count = 0
  var embedCount: Int {
    lock.lock()
    defer { lock.unlock() }
    return count
  }
  init(block: Bool, blockStop: Bool) throws {
    base = try FixtureIdentityBackend.system()
    blocks = block
    self.blockStop = blockStop
  }
  var backendId: String { base.backendId }
  var backendVersion: String { base.backendVersion }
  var modelBinding: ModelBinding { base.modelBinding }
  var embeddingSpace: EmbeddingSpaceId { base.embeddingSpace }
  func availability() -> CapabilityAvailability { base.availability() }
  func evaluateQuality(_ probe: IdentityProbe) throws -> IdentityQuality {
    try base.evaluateQuality(probe)
  }
  func embed(_ probe: IdentityProbe) throws -> IdentityEmbedding {
    lock.lock()
    count += 1
    lock.unlock()
    return try base.embed(probe)
  }
  func embed(_ probe: IdentityProbe, stopSignal: IdentityStopSignal) throws -> IdentityEmbedding {
    if blocks {
      let registration = stopSignal.registerStopHandler { [self] in
        stopped.signal()
        if blockStop { _ = stopRelease.wait(timeout: .now() + 4) }
      }
      entered.signal()
      _ = release.wait(timeout: .now() + 4)
      registration.close()
      try stopSignal.check()
    }
    return try embed(probe)
  }
  func waitEntered() async -> Bool { await Task.detached { self.wait(self.entered) }.value }
  func waitStopped() async -> Bool { await Task.detached { self.wait(self.stopped) }.value }
  private func wait(_ semaphore: DispatchSemaphore) -> Bool {
    semaphore.wait(timeout: .now() + 2) == .success
  }
  func close() { base.close() }
}
private final class TestPAD: LivenessBackend, @unchecked Sendable {
  let backendId = "pad.fixture"
  let backendVersion = "pad-v1"
  let modelBinding: ModelBinding
  init() throws {
    modelBinding = try ModelBinding(kind: .system, id: "pad.fixture", revision: "os-test")
  }
  func availability() -> CapabilityAvailability {
    CapabilityAvailability(status: .available, backendId: backendId)
  }
  func evaluate(frame: LivenessFrame, challenge: LivenessChallenge?, stopSignal: IdentityStopSignal)
    throws -> LivenessDecision?
  {
    try stopSignal.check()
    XCTAssertNotNil(challenge)
    return try LivenessDecision(outcome: .live, quality: IdentityQuality(faceCount: 1, score: 1))
  }
  func close() {}
}

private final class UnknownSelection: NSObject, IdentityBridgeBackendSelection {}
