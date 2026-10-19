import XCTest
import CryptoKit
import ODAIKitCore
@testable import ODAIKitIdentity

final class FaceVerificationTests: XCTestCase {
    func testSingleFaceMatchPublishesScoreAndBindingMetadata() async throws {
        let fixture = try await fixture()
        let backend = VerificationBackend(base: fixture.backend, quality: try IdentityQuality(faceCount: 1, score: 1), vector: [1, 2])
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: backend.backendId, backends: [backend])
        let task = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant,
                                           options: fixture.options, backend: .default)
        let result = try await task.result()
        XCTAssertEqual(result.outcome, .match)
        XCTAssertEqual(result.scoreFields.metric, "cosine")
        XCTAssertEqual(result.scoreFields.threshold, 0.8)
        XCTAssertEqual(result.quality.faceCount, 1)
        XCTAssertEqual(result.templateRevision, fixture.template.revision)
        XCTAssertEqual(result.backendId, backend.backendId)
        XCTAssertEqual(result.modelBinding, backend.modelBinding)
        XCTAssertFalse(result.sessionId.isEmpty); XCTAssertFalse(result.runId.isEmpty)
        XCTAssertEqual(task.terminal, .completed)
        task.cancel()
        await task.close()
        let repeated = try await task.result()
        XCTAssertEqual(repeated.outcome, .match)
        XCTAssertEqual(task.terminal, .completed)
        await engine.close(); try await fixture.identity.close()
    }

    func testSingleFaceNonMatchUsesTheSameScoreContract() async throws {
        let fixture = try await fixture()
        let backend = VerificationBackend(base: fixture.backend, quality: try IdentityQuality(faceCount: 1, score: 1), vector: [2, -1])
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: backend.backendId, backends: [backend])
        let verification = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant,
                                                    options: fixture.options, backend: .default)
        let result = try await verification.result()
        XCTAssertEqual(result.outcome, .nonMatch)
        XCTAssertNotNil(result.scoreFields.score)
        XCTAssertEqual(result.scoreFields.metric, "cosine")
        await engine.close(); try await fixture.identity.close()
    }

    func testQualityRejectsBeforeEmbeddingAndLeavesScoreFieldsNull() async throws {
        let fixture = try await fixture()
        let backend = VerificationBackend(base: fixture.backend, quality: try IdentityQuality(faceCount: 1, score: 0.2), vector: [1, 2])
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: backend.backendId, backends: [backend])
        let verification = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant,
                                                    options: fixture.options, backend: .default)
        let result = try await verification.result()
        XCTAssertEqual(result.outcome, .insufficientQuality)
        XCTAssertNil(result.scoreFields.score); XCTAssertNil(result.scoreFields.metric); XCTAssertNil(result.scoreFields.threshold)
        XCTAssertFalse(backend.didEmbed)
        await engine.close(); try await fixture.identity.close()
    }

    func testMultiFaceAndUnverifiedPolicyFailClosed() async throws {
        let fixture = try await fixture()
        let multi = VerificationBackend(base: fixture.backend, quality: try IdentityQuality(faceCount: 2, score: 1), vector: [1, 2])
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: multi.backendId, backends: [multi])
        let task = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant,
                                            options: fixture.options, backend: .default)
        do { _ = try await task.result(); XCTFail("multi-face input accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .multipleFaces) }
        let unverified = try ThresholdPolicy(id: "verify-draft", revision: "1", embeddingSpace: fixture.backend.embeddingSpace,
            scoreMetric: "cosine", scoreDirection: .higherIsBetter, calibrationStatus: .unverified, qualityFloor: 0.5)
        let options = try VerificationOptions(policy: unverified, revision: "1")
        do {
            _ = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant, options: options, backend: .default)
            XCTFail("unverified policy accepted")
        } catch { XCTAssertEqual((error as? IdentityError)?.code, .thresholdUnverified) }
        await engine.close(); try await fixture.identity.close()
    }

    func testExplicitUnavailableBackendDoesNotFallbackToDefault() async throws {
        let fixture = try await fixture()
        let optional = try FixtureIdentityBackend.optionalUnavailable()
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: fixture.backend.backendId, backends: [fixture.backend, optional])
        do {
            _ = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant,
                                        options: fixture.options, backend: .explicit(optional.backendId))
            XCTFail("unavailable explicit backend fell back")
        } catch { XCTAssertEqual((error as? IdentityError)?.code, .backendUnavailable) }
        await engine.close(); try await fixture.identity.close()
    }

    func testCancelAndCloseProduceOneCancelledTerminalOutcome() async throws {
        let fixture = try await fixture()
        let entered = DispatchSemaphore(value: 0)
        let backend = VerificationBackend(base: fixture.backend, quality: try IdentityQuality(faceCount: 1, score: 1), vector: [1, 2], blocks: true, onEmbedStart: { entered.signal() })
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: backend.backendId, backends: [backend])
        let task = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant, options: fixture.options, backend: .default)
        XCTAssertEqual(entered.wait(timeout: .now() + 1), .success)
        task.cancel(); await task.close(); await task.close()
        do { _ = try await task.result(); XCTFail("cancelled task produced a result") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .cancelled) }
        await engine.close(); try await fixture.identity.close()
    }

    func testRevocationRacingBackendResultWinsOverPublication() async throws {
        let fixture = try await fixture()
        let entered = DispatchSemaphore(value: 0)
        let backend = VerificationBackend(base: fixture.backend, quality: try IdentityQuality(faceCount: 1, score: 1), vector: [1, 2], blocks: true, onEmbedStart: { entered.signal() })
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: backend.backendId, backends: [backend])
        let task = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant, options: fixture.options, backend: .default)
        XCTAssertEqual(entered.wait(timeout: .now() + 1), .success)
        _ = try await fixture.identity.revokeGrant(fixture.grant.grantId, ownership: fixture.owner)
        do { _ = try await task.result(); XCTFail("revoked operation published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        XCTAssertEqual(task.terminal, .failed)
        await task.close(); await engine.close(); try await fixture.identity.close()
    }

    func testCloseDrainsAdmissionBeforeClosingTheRegisteredBackend() async throws {
        let fixture = try await fixture()
        let entered = expectation(description: "verification authorization entered")
        let backendClosed = expectation(description: "backend must stay open until admission drains")
        backendClosed.isInverted = true
        let release = DispatchSemaphore(value: 0)
        fixture.authority.onNextEpoch {
            entered.fulfill()
            XCTAssertEqual(release.wait(timeout: .now() + 3), .success)
        }
        let backend = VerificationBackend(base: fixture.backend, quality: try IdentityQuality(faceCount: 1, score: 1), vector: [1, 2], onClose: { backendClosed.fulfill() })
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: backend.backendId, backends: [backend])
        let admission = Task { try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant, options: fixture.options) }
        await fulfillment(of: [entered], timeout: 1)
        let closing = Task { await engine.close() }
        await fulfillment(of: [backendClosed], timeout: 0.05)
        release.signal()
        let task = try await admission.value
        do { _ = try await task.result(); XCTFail("closed admission published a decision") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        await closing.value; try await fixture.identity.close()
    }

    func testCancelWaitsForNativeStopDeliveryBeforeResultAndEngineClose() async throws {
        let fixture = try await fixture()
        let embedStarted = DispatchSemaphore(value: 0)
        let stopEntered = DispatchSemaphore(value: 0)
        let allowStopReturn = DispatchSemaphore(value: 0)
        let resultReturned = DispatchSemaphore(value: 0)
        let backendClosed = DispatchSemaphore(value: 0)
        let backend = VerificationBackend(base: fixture.backend,
            quality: try IdentityQuality(faceCount: 1, score: 1), vector: [1, 2], blocks: true,
            onEmbedStart: { embedStarted.signal() },
            onStop: {
                stopEntered.signal()
                _ = allowStopReturn.wait(timeout: .now() + 3)
            },
            onClose: { backendClosed.signal() })
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: backend.backendId, backends: [backend])
        let task = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant,
                                           options: fixture.options, backend: .default)
        let waiter = Task {
            _ = try? await task.result()
            resultReturned.signal()
        }
        XCTAssertEqual(embedStarted.wait(timeout: .now() + 1), .success)
        task.cancel()
        XCTAssertEqual(stopEntered.wait(timeout: .now() + 1), .success)
        XCTAssertEqual(resultReturned.wait(timeout: .now() + 0.05), .timedOut)
        let closing = Task {
            await engine.close()
            backendClosed.signal()
        }
        XCTAssertEqual(backendClosed.wait(timeout: .now() + 0.05), .timedOut)
        allowStopReturn.signal()
        XCTAssertEqual(resultReturned.wait(timeout: .now() + 2), .success)
        XCTAssertEqual(backendClosed.wait(timeout: .now() + 2), .success)
        await waiter.value
        XCTAssertEqual(task.terminal, .cancelled)
        await closing.value
        try await fixture.identity.close()
    }
    func testRevocationWaitsForNativeStopDeliveryBeforeResultAndEngineClose() async throws {
        let fixture = try await fixture()
        let embedStarted = DispatchSemaphore(value: 0)
        let stopEntered = DispatchSemaphore(value: 0)
        let allowStopReturn = DispatchSemaphore(value: 0)
        let resultReturned = DispatchSemaphore(value: 0)
        let backendClosed = DispatchSemaphore(value: 0)
        let backend = VerificationBackend(base: fixture.backend,
            quality: try IdentityQuality(faceCount: 1, score: 1), vector: [1, 2], blocks: true,
            onEmbedStart: { embedStarted.signal() },
            onStop: {
                stopEntered.signal()
                _ = allowStopReturn.wait(timeout: .now() + 3)
            },
            onClose: { backendClosed.signal() })
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: backend.backendId, backends: [backend])
        let task = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant,
                                           options: fixture.options, backend: .default)
        let waiter = Task {
            _ = try? await task.result()
            resultReturned.signal()
        }
        XCTAssertEqual(embedStarted.wait(timeout: .now() + 1), .success)
        let revoking = Task { try await fixture.identity.revokeGrant(fixture.grant.grantId, ownership: fixture.owner) }
        XCTAssertEqual(stopEntered.wait(timeout: .now() + 1), .success)
        XCTAssertEqual(resultReturned.wait(timeout: .now() + 0.05), .timedOut)
        let closing = Task {
            await engine.close()
            backendClosed.signal()
        }
        XCTAssertEqual(backendClosed.wait(timeout: .now() + 0.05), .timedOut)
        allowStopReturn.signal()
        XCTAssertEqual(resultReturned.wait(timeout: .now() + 2), .success)
        XCTAssertEqual(backendClosed.wait(timeout: .now() + 2), .success)
        await waiter.value
        _ = try await revoking.value
        XCTAssertEqual(task.terminal, .failed)
        await closing.value
        try await fixture.identity.close()
    }
    func testPolicyFailureIncludesResolvedBackendAndModelMetadata() async throws {
        let fixture = try await fixture()
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: fixture.backend.backendId, backends: [fixture.backend])
        let unverified = try ThresholdPolicy(id: "draft-policy", revision: "1", embeddingSpace: fixture.backend.embeddingSpace,
            scoreMetric: "cosine", scoreDirection: .higherIsBetter, calibrationStatus: .unverified)
        do {
            _ = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant,
                                        options: VerificationOptions(policy: unverified, revision: "1"))
            XCTFail("unverified policy admitted")
        } catch let error as IdentityError {
            XCTAssertEqual(error.code, .thresholdUnverified)
            XCTAssertEqual(error.backendId, "system.fixture")
            XCTAssertEqual(error.modelBinding, fixture.backend.modelBinding)
            XCTAssertEqual(error.policyId, "draft-policy")
            XCTAssertNil(error.sessionId)
        }
        await engine.close(); try await fixture.identity.close()
    }

    func testBusyCloseAndNativeStopReentryPreserveOneTerminal() async throws {
        let fixture = try await fixture()
        let entered = DispatchSemaphore(value: 0)
        let holder = VerificationReentry()
        let backend = VerificationBackend(base: fixture.backend, quality: try IdentityQuality(faceCount: 1, score: 1),
            vector: [1, 2], blocks: true, onEmbedStart: { entered.signal() }, onStop: { holder.stop() })
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: backend.backendId, backends: [backend])
        let task = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant, options: fixture.options)
        holder.install {
            let returned = DispatchSemaphore(value: 0)
            Task { await task.close(); await engine.close(); returned.signal() }
            XCTAssertEqual(returned.wait(timeout: .now() + 1), .success)
        }
        XCTAssertEqual(entered.wait(timeout: .now() + 1), .success)
        do {
            _ = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant, options: fixture.options)
            XCTFail("active verification accepted a second run")
        } catch { XCTAssertEqual((error as? IdentityError)?.code, .busy) }
        task.cancel()
        await engine.close()
        await engine.close()
        do { _ = try await task.result(); XCTFail("cancelled task published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .cancelled) }
        XCTAssertEqual(task.terminal, .cancelled)
        do {
            _ = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant, options: fixture.options)
            XCTFail("closed engine admitted a task")
        } catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        try await fixture.identity.close()
    }

    func testFinalAuthorizationGateRechecksCallerCancellation() async throws {
        let fixture = try await fixture()
        let gateEntered = DispatchSemaphore(value: 0)
        let releaseGate = DispatchSemaphore(value: 0)
        let backend = VerificationBackend(base: fixture.backend, quality: try IdentityQuality(faceCount: 1, score: 1), vector: [1, 2],
            onEmbedStart: {
                fixture.authority.onNextEpoch {
                    gateEntered.signal()
                    _ = releaseGate.wait(timeout: .now() + 3)
                }
            })
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: backend.backendId, backends: [backend])
        let task = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant, options: fixture.options)
        XCTAssertEqual(gateEntered.wait(timeout: .now() + 1), .success)
        task.cancel()
        releaseGate.signal()
        do { _ = try await task.result(); XCTFail("cancellation during final gate was ignored") }
        catch let error as IdentityError {
            XCTAssertEqual(error.code, .cancelled)
            XCTAssertEqual(error.policyId, "verify-policy")
            XCTAssertEqual(error.sessionId, task.runId)
        }
        XCTAssertEqual(task.terminal, .cancelled)
        await engine.close(); try await fixture.identity.close()
    }

    func testRevocationBeforeFinalConsumeWinsInBothCancelRequestOrders() async throws {
        for cancelFirst in [true, false] {
            let fixture = try await fixture()
            let entered = DispatchSemaphore(value: 0)
            let allowReturn = DispatchSemaphore(value: 0)
            let backend = VerificationBackend(base: fixture.backend, quality: try IdentityQuality(faceCount: 1, score: 1),
                vector: [1, 2], onEmbedStart: {
                    entered.signal()
                    _ = allowReturn.wait(timeout: .now() + 3)
                })
            let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: backend.backendId, backends: [backend])
            let task = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant, options: fixture.options)
            XCTAssertEqual(entered.wait(timeout: .now() + 1), .success)
            if cancelFirst { task.cancel() }
            _ = try await fixture.identity.revokeGrant(fixture.grant.grantId, ownership: fixture.owner)
            if !cancelFirst { task.cancel() }
            allowReturn.signal()
            do { _ = try await task.result(); XCTFail("revoked task published") }
            catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
            XCTAssertEqual(task.terminal, .failed)
            task.cancel(); await task.close()
            XCTAssertEqual(task.terminal, .failed)
            await engine.close(); try await fixture.identity.close()
        }
    }

    func testCompletedVerificationIsNotRetractedByLaterRevocation() async throws {
        let fixture = try await fixture()
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: fixture.backend.backendId, backends: [fixture.backend])
        let task = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant, options: fixture.options)
        let value = try await task.result()
        _ = try await fixture.identity.revokeGrant(fixture.grant.grantId, ownership: fixture.owner)
        task.cancel()
        let repeated = try await task.result()
        XCTAssertEqual(repeated.runId, value.runId)
        XCTAssertEqual(repeated.outcome, .match)
        XCTAssertEqual(task.terminal, .completed)
        await engine.close(); try await fixture.identity.close()
    }

    func testMissingTemplateAndExpiredGrantFailAtAdmissionWithResolvedMetadata() async throws {
        let fixture = try await fixture()
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: fixture.backend.backendId, backends: [fixture.backend])
        let missing = try TemplateRef(id: "missing-template", embeddingSpaceId: fixture.template.embeddingSpaceId,
            authorizationEpoch: 0, revision: fixture.template.revision)
        let expired = try AuthorizationGrant(grantId: "expired-query", revocationDomainId: "verify-domain", scope: [.verification],
            purpose: "identity", issuedAtMillis: 0, expiresAtMillis: 50, authorizationEpoch: 0, evidenceRef: "fixture")
        for (ref, grant, expected) in [(missing, fixture.grant, IdentityErrorCode.templateNotFound),
                                       (fixture.template, expired, .authorizationExpired)] {
            do {
                _ = try await engine.verify(probe: fixture.probe, templateRef: ref, grant: grant, options: fixture.options)
                XCTFail("invalid admission accepted")
            } catch let error as IdentityError {
                XCTAssertEqual(error.code, expected)
                XCTAssertEqual(error.backendId, "system.fixture")
                XCTAssertEqual(error.modelBinding, fixture.backend.modelBinding)
                XCTAssertEqual(error.policyId, "verify-policy")
                XCTAssertNil(error.sessionId)
            }
        }
        await engine.close(); try await fixture.identity.close()
    }

    func testCorruptProbePreservesInvalidInputAndReleasesTheRun() async throws {
        let fixture = try await fixture()
        let engine = try FaceVerification(identityStore: fixture.identity, defaultBackendId: fixture.backend.backendId, backends: [fixture.backend])
        let invalid = try IdentityProbe(encodedImage: Data([0]), revision: "corrupt-image")
        let task = try await engine.verify(probe: invalid, templateRef: fixture.template, grant: fixture.grant, options: fixture.options)
        do { _ = try await task.result(); XCTFail("corrupt input accepted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidInput) }
        XCTAssertEqual(task.terminal, .failed)
        let next = try await engine.verify(probe: fixture.probe, templateRef: fixture.template, grant: fixture.grant, options: fixture.options)
        let value = try await next.result()
        XCTAssertEqual(value.outcome, .match)
        await engine.close(); try await fixture.identity.close()
    }

    private func fixture() async throws -> VerificationFixture {
        let value = try await VerificationFixture()
        let root = value.root
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        return value
    }
}

private struct VerificationFixture {
    let root: URL
    let identity: IdentityStore; let keys: VerificationKeys; let authority: VerificationAuthority
    let grant: AuthorizationGrant; let owner: OwnershipAuthorization; let template: TemplateRef
    let backend: FixtureIdentityBackend; let probe: IdentityProbe; let options: VerificationOptions
    init() async throws {
        keys = VerificationKeys(); authority = VerificationAuthority(); root = try Self.root()
        identity = try IdentityStore(root: root, purpose: "identity", authorizationProvider: authority, keyProvider: keys,
                                     wallClock: VerificationWallClock(), monotonicClock: VerificationMono())
        grant = try AuthorizationGrant(grantId: "verify-grant", revocationDomainId: "verify-domain", scope: [.enrollment, .verification],
                                       purpose: "identity", issuedAtMillis: 0, expiresAtMillis: nil, authorizationEpoch: 0, evidenceRef: "fixture")
        backend = try FixtureIdentityBackend.system()
        let candidate = try await identity.createCandidate(grant)
        let enrollment = try await identity.enroll(IdentityProbe(fixtureID: "probe", vector: [1, 2]), candidate: candidate, grant: grant,
            options: try EnrollmentOptions(qualityFloor: 0.5, qualityPolicyId: "quality", revision: "1"), backend: backend)
        template = try XCTUnwrap(enrollment.templateRef)
        probe = try IdentityProbe(fixtureID: "query", vector: [1, 2])
        let policy = try ThresholdPolicy(id: "verify-policy", revision: "1", embeddingSpace: backend.embeddingSpace,
            scoreMetric: "cosine", scoreDirection: .higherIsBetter, calibrationStatus: .verified, kind: .verification,
            threshold: 0.8, qualityFloor: 0.5, operatingPoint: "fixture", datasetRevision: "fixture", enrollmentRule: "one", effectiveAuthorizationEpoch: 0)
        options = try VerificationOptions(policy: policy, revision: "1")
        owner = try OwnershipAuthorization(storeId: identity.storeId, evidenceRef: "owner")
    }
    static func root() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("face-verification-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true); return url
    }
}

private final class VerificationBackend: IdentityBackend, @unchecked Sendable {
    let base: FixtureIdentityBackend; let quality: IdentityQuality; let vector: [Float]; let blocks: Bool
    let onEmbedStart: (@Sendable () -> Void)?
    let onStop: (@Sendable () -> Void)?
    let onClose: (@Sendable () -> Void)?
    private var embedded = false
    var didEmbed: Bool { lock.lock(); defer { lock.unlock() }; return embedded }
    private let lock = NSLock(); private var release = DispatchSemaphore(value: 0)
    init(base: FixtureIdentityBackend, quality: IdentityQuality, vector: [Float], blocks: Bool = false,
         onEmbedStart: (@Sendable () -> Void)? = nil, onStop: (@Sendable () -> Void)? = nil,
         onClose: (@Sendable () -> Void)? = nil) {
        self.base = base; self.quality = quality; self.vector = vector; self.blocks = blocks
        self.onEmbedStart = onEmbedStart; self.onStop = onStop; self.onClose = onClose
    }
    var backendId: String { base.backendId }; var backendVersion: String { base.backendVersion }; var modelBinding: ModelBinding { base.modelBinding }; var embeddingSpace: EmbeddingSpaceId { base.embeddingSpace }
    func availability() -> CapabilityAvailability { base.availability() }
    func embed(_ probe: IdentityProbe) throws -> IdentityEmbedding { onEmbedStart?(); if blocks { _ = release.wait(timeout: .now() + 3) }; lock.lock(); embedded = true; lock.unlock(); return try IdentityEmbedding(embeddingSpace: embeddingSpace, values: vector) }
    func evaluateQuality(_ probe: IdentityProbe) throws -> IdentityQuality { quality }
    func close() { release.signal(); onClose?() }
    func embed(_ probe: IdentityProbe, stopSignal: IdentityStopSignal) throws -> IdentityEmbedding {
        let registration = stopSignal.registerStopHandler {
            self.release.signal()
            self.onStop?()
        }; defer { registration.close() }
        try stopSignal.check(); let result = try embed(probe); try stopSignal.check(); return result
    }
}

private final class VerificationAuthority: IdentityAuthorizationProvider, @unchecked Sendable {
    private let lock = NSLock(); private var nextEpoch: (@Sendable () -> Void)?
    func onNextEpoch(_ callback: @escaping @Sendable () -> Void) { lock.lock(); nextEpoch = callback; lock.unlock() }
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 {
        lock.lock(); let callback = nextEpoch; nextEpoch = nil; lock.unlock()
        callback?(); return grant.authorizationEpoch
    }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {}
}
private final class VerificationKeys: IdentityKeyProvider, @unchecked Sendable {
    private var values: [String: SymmetricKey] = [:]; private let lock = NSLock()
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey { lock.lock(); defer { lock.unlock() }; if let value = values[id] { return value }; guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }; let value = SymmetricKey(size: .bits256); values[id] = value; return value }
    func deleteKey(id: String) throws { lock.lock(); values.removeValue(forKey: id); lock.unlock() }
}
private struct VerificationWallClock: IdentityWallClock { var nowMillis: Int64 { 100 } }
private struct VerificationMono: IdentityMonotonicClock { var nowNanos: Int64 { 1 } }

private final class VerificationReentry: @unchecked Sendable {
    private let lock = NSLock()
    private var callback: (@Sendable () -> Void)?
    func install(_ callback: @escaping @Sendable () -> Void) { lock.lock(); self.callback = callback; lock.unlock() }
    func stop() { lock.lock(); let action = callback; callback = nil; lock.unlock(); action?() }
}
