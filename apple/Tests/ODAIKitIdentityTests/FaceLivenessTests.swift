import XCTest
import CryptoKit
import ODAIKitCore
@testable import ODAIKitIdentity

final class FaceLivenessTests: XCTestCase {

    func testTimeoutRequestsNativeStopAndDrainsItsCallback() async throws {
        let f = try LivenessFixture()
        let stopped = expectation(description: "native stop")
        let release = DispatchSemaphore(value: 0)
        f.backend.evaluateHook = { _, signal in
            let registration = signal.registerStopHandler { stopped.fulfill(); release.signal() }
            defer { registration.close() }
            XCTAssertEqual(release.wait(timeout: .now() + 1), .success)
            return try LivenessDecision(outcome: .live, quality: IdentityQuality(faceCount: 1, score: 1))
        }
        let engine = try await f.engine()
        let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options(duration: 50_000_000))
        try session.start(); session.submitFrame(try f.frame())
        do { _ = try await session.result(); XCTFail("timeout live") } catch { XCTAssertEqual((error as? IdentityError)?.code, .sessionTimeout) }
        await fulfillment(of: [stopped], timeout: 0.1)
        XCTAssertEqual(session.terminal, .failed)
        await engine.close(); try await f.close()
    }


    func testActiveIdleExpiryUsesChallengeExpiredAndAuthorizationFailuresCarryMetadata() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        let expired = try AuthorizationGrant(grantId: "expired", revocationDomainId: "expired-domain", scope: [.liveness],
            purpose: "identity", issuedAtMillis: 0, expiresAtMillis: 50, authorizationEpoch: 0, evidenceRef: "fixture")
        do { _ = try await engine.openLivenessSession(grant: expired, options: f.options()); XCTFail("expired admission") }
        catch let e as IdentityError {
            XCTAssertEqual(e.code, .authorizationExpired); XCTAssertEqual(e.backendId, f.backend.backendId)
            XCTAssertEqual(e.modelBinding, f.backend.modelBinding); XCTAssertEqual(e.policyId, "pad")
        }
        let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options(mode: .active, duration: 10_000_000))
        try session.start()
        do { _ = try await session.result(); XCTFail("idle active timeout") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .challengeExpired) }
        await engine.close(); try await f.close()
    }


    func testFinalPublicationRejectsDeadlinePassedInsideAuthorizationProvider() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options())
        f.authority.hook = { f.clock.nanos = 1_000_000_001 }
        try session.start(); XCTAssertTrue(session.submitFrame(try f.frame()))
        do { _ = try await session.result(); XCTFail("expired result published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .sessionTimeout) }
        await engine.close(); try await f.close()
    }


    func testAdmissionDiagnosticsContainResolvedBackendAndPolicy() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        do { _ = try await engine.openLivenessSession(grant: f.grant(), options: f.options(status: .unverified)); XCTFail("policy") }
        catch let e as IdentityError {
            XCTAssertEqual(e.backendId, f.backend.backendId); XCTAssertEqual(e.modelBinding, f.backend.modelBinding); XCTAssertEqual(e.policyId, "pad")
        }
        await engine.close(); try await f.close()
    }

    func testCancelledThenRevokedWhileBackendBlockedPublishesOnlyRevocation() async throws {
        let f = try LivenessFixture(); let entered = expectation(description: "native"); let release = DispatchSemaphore(value: 0)
        f.backend.evaluateHook = { _, _ in entered.fulfill(); _ = release.wait(timeout: .now() + 3)
            return try LivenessDecision(outcome: .live, quality: IdentityQuality(faceCount: 1, score: 1)) }
        let engine = try await f.engine(); let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options())
        try session.start(); XCTAssertTrue(session.submitFrame(try f.frame())); await fulfillment(of: [entered], timeout: 2)
        session.cancel()
        _ = try await f.store.revokeGrant("live", ownership: OwnershipAuthorization(storeId: f.store.storeId, evidenceRef: "owner"))
        release.signal()
        do { _ = try await session.result(); XCTFail("revoked") } catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        XCTAssertEqual(session.terminal, .failed)
        await engine.close(); try await f.close()
    }

    func testDroppedFramesDoNotMeetMinimumCaptureCount() async throws {
        let f = try LivenessFixture(); let entered = expectation(description: "native"); let release = DispatchSemaphore(value: 0)
        f.backend.evaluateHook = { frame, _ in
            if frame.timestampNanos == 2 { entered.fulfill(); _ = release.wait(timeout: .now() + 3) }
            return try LivenessDecision(outcome: .live, quality: IdentityQuality(faceCount: 1, score: 1))
        }
        let engine = try await f.engine(); let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options(minimumFrames: 3))
        try session.start(); XCTAssertTrue(session.submitFrame(try f.frame())); await fulfillment(of: [entered], timeout: 2)
        XCTAssertTrue(session.submitFrame(try f.frame(timestamp: 3))); XCTAssertTrue(session.submitFrame(try f.frame(timestamp: 4)))
        session.finish(); release.signal()
        let result = try await session.result()
        XCTAssertEqual(result.outcome, .insufficientQuality); XCTAssertEqual(result.acceptedFrameCount, 3); XCTAssertEqual(result.droppedFrameCount, 1)
        XCTAssertNil(result.proof); await engine.close(); try await f.close()
    }

    func testCloseCreatedSessionAndStopCallbackReentryDrainWithoutDeadlock() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options())
        await session.close()
        XCTAssertEqual(session.terminal, .failed)
        do { _ = try await session.result(); XCTFail("closed") } catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        let next = try await engine.openLivenessSession(grant: f.grant(), options: f.options())
        let entered = expectation(description: "native")
        let reentered = expectation(description: "reentered")
        let nativeRelease = DispatchSemaphore(value: 0)
        let handlerRelease = DispatchSemaphore(value: 0)
        f.backend.evaluateHook = { _, signal in
            let registration = signal.registerStopHandler {
                Task {
                    await engine.close()
                    do { _ = try await next.result(); XCTFail("reentrant result") } catch { }
                    reentered.fulfill()
                    handlerRelease.signal(); nativeRelease.signal()
                }
                // Keep the actual handler scope alive until its reentrant child returns.
                XCTAssertEqual(handlerRelease.wait(timeout: .now() + 3), .success)
            }
            defer { registration.close() }
            entered.fulfill(); XCTAssertEqual(nativeRelease.wait(timeout: .now() + 3), .success)
            return try LivenessDecision(outcome: .live, quality: IdentityQuality(faceCount: 1, score: 1))
        }
        try next.start(); next.submitFrame(try f.frame())
        await fulfillment(of: [entered], timeout: 2)
        next.cancel(); await fulfillment(of: [reentered], timeout: 2)
        await engine.close(); try await f.close()
    }


    func testRevocationStopsIdleSessionAndUsesFailedTerminal() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options())
        try session.start()
        _ = try await f.store.revokeGrant("live", ownership: OwnershipAuthorization(storeId: f.store.storeId, evidenceRef: "owner"))
        do { _ = try await session.result(); XCTFail("revoked session") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        XCTAssertEqual(session.terminal, .failed)
        await engine.close(); try await f.close()
    }

    func testCompletedResultSurvivesRevokeButUnusedProofDoesNot() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options())
        try session.start(); XCTAssertTrue(session.submitFrame(try f.frame()))
        let result = try await session.result(); let proof = try XCTUnwrap(result.proof)
        _ = try await f.store.revokeGrant("live", ownership: OwnershipAuthorization(storeId: f.store.storeId, evidenceRef: "owner"))
        do { try await session.consumeProof(proof, captureDigest: result.captureDigest, purpose: "identity"); XCTFail("revoked proof") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked) }
        let retained = try await session.result()
        XCTAssertEqual(retained.outcome, .live); XCTAssertEqual(session.terminal, .completed)
        await engine.close(); try await f.close()
    }

    func testProofExpiryAndWrongPurposeDoNotConsumeValidProof() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options())
        try session.start(); XCTAssertTrue(session.submitFrame(try f.frame()))
        let result = try await session.result(); let proof = try XCTUnwrap(result.proof)
        do { try await session.consumeProof(proof, captureDigest: result.captureDigest, purpose: "other"); XCTFail("purpose") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidChallenge) }
        f.clock.nowMillis = proof.expiresAtMillis
        do { try await session.consumeProof(proof, captureDigest: result.captureDigest, purpose: "identity"); XCTFail("expiry") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidChallenge) }
        await engine.close(); try await f.close()
    }

    func testDeadlineExpiresWithoutIncomingFrames() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options(duration: 10_000_000))
        try session.start()
        do { _ = try await session.result(); XCTFail("idle timeout") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .sessionTimeout) }
        XCTAssertEqual(session.terminal, .failed)
        await engine.close(); try await f.close()
    }

    func testActiveChallengeExpiryRejectsFrameBeforeBackend() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options(mode: .active))
        try session.start(); _ = await session.nextEvent()
        guard case .challengeIssued(let challenge)? = await session.nextEvent() else { return XCTFail("challenge") }
        f.clock.nanos = challenge.expiresAtNanos
        let frame = try LivenessFrame(encodedFrame: Data([1]), timestampNanos: challenge.expiresAtNanos, revision: "frame", challenge: challenge)
        XCTAssertFalse(session.submitFrame(frame))
        do { _ = try await session.result(); XCTFail("expired challenge") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .challengeExpired) }
        await engine.close(); try await f.close()
    }

    func testMinimumFramesCannotBeSatisfiedByDroppedFrames() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options(minimumFrames: 2))
        try session.start(); XCTAssertTrue(session.submitFrame(try f.frame()))
        session.finish()
        let result = try await session.result()
        XCTAssertEqual(result.outcome, .insufficientQuality); XCTAssertNil(result.proof)
        await engine.close(); try await f.close()
    }

    func testQualityRejectionClearsAllScoreFieldsAndMultiFaceFails() async throws {
        for count in [1, 2] {
            let f = try LivenessFixture()
            f.backend.evaluateHook = { _, _ in try LivenessDecision(outcome: .live,
                quality: IdentityQuality(faceCount: count, score: 0.1),
                scoreFields: IdentityScoreFields(score: 0.9, metric: "pad", threshold: 0.5)) }
            let engine = try await f.engine(); let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options())
            try session.start(); XCTAssertTrue(session.submitFrame(try f.frame()))
            do {
                let result = try await session.result()
                XCTAssertEqual(count, 1); XCTAssertEqual(result.outcome, .insufficientQuality)
                XCTAssertEqual(result.scoreFields, try IdentityScoreFields()); XCTAssertNil(result.proof)
            } catch { XCTAssertEqual(count, 2); XCTAssertEqual((error as? IdentityError)?.code, .multipleFaces) }
            await engine.close(); try await f.close()
        }
    }

    func testCategoricalAndContinuousDecisionFieldsMustMatchPolicy() async throws {
        for continuous in [false, true] {
            let f = try LivenessFixture()
            f.backend.evaluateHook = { _, _ in try LivenessDecision(outcome: .live, quality: IdentityQuality(faceCount: 1, score: 1),
                scoreFields: IdentityScoreFields(score: 0.9, metric: "pad", threshold: 0.5)) }
            let engine = try await f.engine(); let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options(continuous: continuous))
            try session.start(); XCTAssertTrue(session.submitFrame(try f.frame()))
            do {
                let result = try await session.result()
                XCTAssertTrue(continuous); XCTAssertEqual(result.scoreFields.score, 0.9); XCTAssertEqual(result.scoreFields.threshold, 0.5)
            } catch { XCTAssertFalse(continuous); XCTAssertEqual((error as? IdentityError)?.code, .thresholdUnverified) }
            await engine.close(); try await f.close()
        }
    }

    func testSpoofAndChallengeFailedNeverIssueProof() async throws {
        for outcome in [LivenessOutcome.spoof, .challengeFailed] {
            let f = try LivenessFixture()
            f.backend.evaluateHook = { _, _ in try LivenessDecision(outcome: outcome, quality: IdentityQuality(faceCount: 1, score: 1)) }
            let engine = try await f.engine(); let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options())
            try session.start(); XCTAssertTrue(session.submitFrame(try f.frame()))
            let result = try await session.result()
            XCTAssertEqual(result.outcome, outcome); XCTAssertNil(result.proof); XCTAssertNil(result.scoreFields.score)
            await engine.close(); try await f.close()
        }
    }

    func testCloseWhileNativeWorkIsBlockedDrainsStopCallbackAndPreservesOneTerminal() async throws {
        let f = try LivenessFixture(); let entered = expectation(description: "native"); let stopEntered = expectation(description: "stop")
        let nativeRelease = DispatchSemaphore(value: 0); let stopRelease = DispatchSemaphore(value: 0)
        f.backend.evaluateHook = { _, signal in
            let registration = signal.registerStopHandler { stopEntered.fulfill(); _ = stopRelease.wait(timeout: .now() + 4) }
            defer { registration.close() }
            entered.fulfill(); _ = nativeRelease.wait(timeout: .now() + 4)
            return try LivenessDecision(outcome: .live, quality: IdentityQuality(faceCount: 1, score: 1))
        }
        let engine = try await f.engine(); let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options(duration: 5_000_000_000))
        try session.start(); XCTAssertTrue(session.submitFrame(try f.frame())); await fulfillment(of: [entered], timeout: 2)
        let closing = Task { await session.close() }; await fulfillment(of: [stopEntered], timeout: 2)
        nativeRelease.signal()
        XCTAssertNotEqual(session.currentState, .closed)
        stopRelease.signal(); await closing.value
        XCTAssertEqual(session.currentState, .closed); XCTAssertEqual(session.terminal, .failed)
        XCTAssertFalse(session.submitFrame(try f.frame(timestamp: 3)))
        var terminals = 0
        while let event = await session.nextEvent() {
            switch event { case .completed, .failed, .cancelled: terminals += 1; default: break }
        }
        XCTAssertEqual(terminals, 1)
        await engine.close(); await engine.close(); try await f.close()
    }

    func testBackendBindingMutationCannotPublishWithDifferentModel() async throws {
        let f = try LivenessFixture()
        f.backend.evaluateHook = { [backend = f.backend] _, _ in
            backend.modelBinding = try ModelBinding(kind: .system, id: "different", revision: "2")
            return try LivenessDecision(outcome: .live, quality: IdentityQuality(faceCount: 1, score: 1))
        }
        let engine = try await f.engine(); let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options())
        try session.start(); XCTAssertTrue(session.submitFrame(try f.frame()))
        do { _ = try await session.result(); XCTFail("changed model published") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .thresholdUnverified) }
        await engine.close(); try await f.close()
    }

    func testFinishWithoutFramesCompletesInsufficientQualityWithNullScoreFields() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options())
        try session.start(); session.finish()
        let result = try await session.result()
        XCTAssertEqual(result.outcome, .insufficientQuality)
        XCTAssertNil(result.scoreFields.score); XCTAssertNil(result.proof)
        await engine.close(); try await f.close()
    }

    func testActiveFrameWithoutIssuedChallengeCannotProduceLive() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options(mode: .active))
        XCTAssertFalse(session.submitFrame(try LivenessFrame(encodedFrame: Data([1]), timestampNanos: 2, revision: "frame")))
        try session.start()
        f.clock.nanos = 2
        XCTAssertFalse(session.submitFrame(try LivenessFrame(encodedFrame: Data([1]), timestampNanos: 2, revision: "frame")))
        do { _ = try await session.result(); XCTFail("unbound active frame returned live") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidChallenge) }
        await engine.close(); try await f.close()
    }

    func testProofIsCaptureBoundOneUseAndInvalidAfterClose() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options())
        try session.start(); XCTAssertTrue(session.submitFrame(try f.frame()))
        let result = try await session.result(); let proof = try XCTUnwrap(result.proof)
        do { try await session.consumeProof(proof, captureDigest: String(repeating: "0", count: 64), purpose: "identity"); XCTFail("wrong digest") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidChallenge) }
        try await session.consumeProof(proof, captureDigest: result.captureDigest, purpose: "identity")
        do { try await session.consumeProof(proof, captureDigest: result.captureDigest, purpose: "identity"); XCTFail("proof replay") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidChallenge) }
        await session.close()
        do { try await session.consumeProof(proof, captureDigest: result.captureDigest, purpose: "identity"); XCTFail("closed proof") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .closed) }
        XCTAssertEqual(session.terminal, .completed)
        await engine.close(); try await f.close()
    }

    func testActiveResponseRequiresSameSessionChallengeAndRejectsReplay() async throws {
        let f = try LivenessFixture(); let other = try LivenessFixture()
        let engine = try await f.engine(); let engineB = try await other.engine()
        let a = try await engine.openLivenessSession(grant: f.grant(), options: f.options(mode: .active))
        let b = try await engineB.openLivenessSession(grant: other.grant(), options: other.options(mode: .active))
        try a.start(); try b.start(); _ = await a.nextEvent(); _ = await b.nextEvent()
        guard case .challengeIssued(let ca)? = await a.nextEvent(), case .challengeIssued(let cb)? = await b.nextEvent() else { return XCTFail("challenge") }
        XCTAssertNotEqual(ca.nonce, cb.nonce)
        XCTAssertTrue(a.submitFrame(try f.frame(challenge: ca)))
        let result = try await a.result(); XCTAssertEqual(result.outcome, .live)
        XCTAssertFalse(a.submitFrame(try f.frame(timestamp: 3, challenge: ca)))
        XCTAssertFalse(b.submitFrame(try other.frame(challenge: ca)))
        do { _ = try await b.result(); XCTFail("wrong session") } catch { XCTAssertEqual((error as? IdentityError)?.code, .invalidChallenge) }
        await engine.close(); await engineB.close(); try await f.close(); try await other.close()
    }

    func testProcessingPlusLatestPendingCountersAndCaptureDigest() async throws {
        let f = try LivenessFixture(); let entered = expectation(description: "processing")
        let release = DispatchSemaphore(value: 0)
        f.backend.evaluateHook = { frame, _ in
            if frame.timestampNanos == 2 { entered.fulfill(); _ = release.wait(timeout: .now() + 3); return nil }
            return try LivenessDecision(outcome: .live, quality: IdentityQuality(faceCount: 1, score: 1))
        }
        let engine = try await f.engine(); let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options())
        try session.start(); XCTAssertTrue(session.submitFrame(try f.frame()))
        await fulfillment(of: [entered], timeout: 2)
        XCTAssertTrue(session.submitFrame(try f.frame(timestamp: 3)))
        XCTAssertFalse(session.submitFrame(try f.frame(timestamp: 3)))
        XCTAssertTrue(session.submitFrame(try f.frame(timestamp: 4)))
        release.signal(); let result = try await session.result()
        XCTAssertEqual(result.acceptedFrameCount, 3); XCTAssertEqual(result.droppedFrameCount, 1); XCTAssertEqual(result.staleFrameCount, 1)
        XCTAssertEqual(result.captureDigest, SHA256.hash(data: Data([2, 4])).map { String(format: "%02x", $0) }.joined())
        await engine.close(); try await f.close()
    }

    func testUnverifiedPADPolicyFailsBeforeSessionAdmission() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        do { _ = try await engine.openLivenessSession(grant: f.grant(), options: f.options(status: .unverified), backend: .default); XCTFail("unverified policy admitted") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .thresholdUnverified) }
        await engine.close(); try await f.close()
    }

    func testPassiveSingleFrameProducesLiveWithOpaqueProof() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options(), backend: .default)
        try session.start(); XCTAssertEqual(session.currentState, .running)
        f.clock.nanos = 2
        XCTAssertTrue(session.submitFrame(try LivenessFrame(encodedFrame: Data([1, 2, 3]), timestampNanos: 2, revision: "camera-1")))
        let result = try await session.result()
        XCTAssertEqual(result.outcome, .live); XCTAssertNotNil(result.proof); XCTAssertEqual(result.captureDigest.count, 64)
        XCTAssertEqual(session.currentState, .completed); await session.close(); await engine.close(); try await f.close()
    }

    func testActiveSessionIssuesUniqueChallengeBeforeAcceptingFrame() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine()
        let a = try await engine.openLivenessSession(grant: f.grant(id: "a"), options: f.options(mode: .active), backend: .default)
        let engineB = try await f.engine()
        let b = try await engineB.openLivenessSession(grant: f.grant(id: "b", domain: "other"), options: f.options(mode: .active), backend: .default)
        try a.start(); try b.start()
        _ = await a.nextEvent(); let ea = await a.nextEvent(); _ = await b.nextEvent(); let eb = await b.nextEvent()
        guard case .challengeIssued(let ca)? = ea else { return XCTFail("missing challenge") }
        guard case .challengeIssued(let cb)? = eb else { return XCTFail("missing challenge") }
        XCTAssertNotEqual(ca.nonce, cb.nonce); XCTAssertNotEqual(ca.id, cb.id)
        a.cancel(); b.cancel(); await a.close(); await b.close(); await engineB.close(); await engine.close(); try await f.close()
    }

    func testExplicitUnavailableBackendDoesNotFallBack() async throws {
        let f = try LivenessFixture(); let unavailable = try LivenessFixtureBackend(id: "vendor.pad", available: false); let engine = try await f.engine(backends: [f.backend, unavailable])
        do { _ = try await engine.openLivenessSession(grant: f.grant(), options: f.options(), backend: .explicit("vendor.pad")); XCTFail("fallback") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .backendUnavailable) }
        await engine.close(); try await f.close()
    }

    func testCancellationWinsOnlyBeforeFinalPublicationAndIsIdempotent() async throws {
        let f = try LivenessFixture(); let engine = try await f.engine(); let session = try await engine.openLivenessSession(grant: f.grant(), options: f.options(), backend: .default)
        try session.start(); session.cancel(); session.cancel()
        do { _ = try await session.result(); XCTFail("cancelled returned result") }
        catch { XCTAssertEqual((error as? IdentityError)?.code, .cancelled) }
        await session.close(); await engine.close(); try await f.close()
    }
}

private final class LivenessFixture {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString); let keys = LivenessKeys(); let authority = LivenessAuthority(); let clock = LivenessClock()
    let store: IdentityStore; let backend: LivenessFixtureBackend
    init() throws { backend = try LivenessFixtureBackend(); store = try IdentityStore(root: root, purpose: "identity", authorizationProvider: authority, keyProvider: keys, wallClock: clock, monotonicClock: clock) }
    func engine(backends: [any LivenessBackend]? = nil) async throws -> FaceLiveness { try FaceLiveness(identityStore: store, backends: backends ?? [backend], monotonicClock: clock, wallClock: clock) }
    func grant(id: String = "live", domain: String = "live-domain") throws -> AuthorizationGrant { try AuthorizationGrant(grantId: id, revocationDomainId: domain, scope: [.liveness], purpose: "identity", issuedAtMillis: 0, expiresAtMillis: nil, authorizationEpoch: 0, evidenceRef: "fixture") }
    func options(mode: PADMode = .passive, status: CalibrationStatus = .verified, duration: Int64 = 1_000_000_000, minimumFrames: Int = 1, continuous: Bool = false) throws -> LivenessOptions {
        let policy = try PADPolicy(id: "pad", revision: "1", backendId: backend.backendId, backendVersion: backend.backendVersion, modelBinding: backend.modelBinding, calibrationStatus: status, mode: mode, challengePolicyRevision: mode == .active ? "challenge-1" : nil, minimumFrames: minimumFrames, maximumDurationNanos: duration, qualityFloor: 0.5, scoreMetric: continuous ? "pad" : nil, threshold: continuous ? 0.5 : nil, classificationPolicyRevision: continuous ? nil : "fixture-categorical", datasetRevision: "fixture", operatingPoint: "fixture", attackTypes: ["fixture"])
        return try LivenessOptions(policy: policy, purpose: "identity", revision: "options-1")
    }
    func frame(timestamp: Int64 = 2, challenge: LivenessChallenge? = nil) throws -> LivenessFrame {
        clock.nanos = timestamp
        return try LivenessFrame(encodedFrame: Data([UInt8(timestamp)]), timestampNanos: timestamp, revision: "camera-1", challenge: challenge)
    }
    func close() async throws { try await store.close(); try? FileManager.default.removeItem(at: root) }
}
private final class LivenessFixtureBackend: LivenessBackend, @unchecked Sendable {
    var evaluateHook: (@Sendable (LivenessFrame, IdentityStopSignal) throws -> LivenessDecision?)?
    let backendId: String; let backendVersion = "1"; var modelBinding: ModelBinding; let available: Bool
    init(id: String = "fixture.pad", available: Bool = true) throws { backendId=id; self.available=available; modelBinding=try ModelBinding(kind: .system, id: id, revision: "fixture") }
    func availability() -> CapabilityAvailability { CapabilityAvailability(status: available ? .available : .unavailable, reason: available ? nil : .backendNotLinked, backendId: backendId, offline: true) }
    func evaluate(frame: LivenessFrame, challenge: LivenessChallenge?, stopSignal: IdentityStopSignal) throws -> LivenessDecision? { try stopSignal.check(); if let evaluateHook { return try evaluateHook(frame, stopSignal) }; return try LivenessDecision(outcome: .live, quality: IdentityQuality(faceCount: 1, score: 1)) }
    func close() {}
}
private final class LivenessAuthority: IdentityAuthorizationProvider, @unchecked Sendable {
    var hook: (() -> Void)?
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 { hook?(); return grant.authorizationEpoch }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {}
}
private final class LivenessKeys: IdentityKeyProvider, @unchecked Sendable { var values: [String: SymmetricKey] = [:]; func key(id: String, createIfMissing: Bool) throws -> SymmetricKey { if let v=values[id] { return v }; guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }; let v=SymmetricKey(size: .bits256); values[id]=v; return v }; func deleteKey(id: String) throws { values[id]=nil } }
private final class LivenessClock: IdentityWallClock, IdentityMonotonicClock, @unchecked Sendable {
    private let lock = NSLock(); private var time: Int64 = 1; private var wall: Int64 = 100
    var nanos: Int64 { get { lock.lock(); defer { lock.unlock() }; return time } set { lock.lock(); time = newValue; lock.unlock() } }
    var nowMillis: Int64 { get { lock.lock(); defer { lock.unlock() }; return wall } set { lock.lock(); wall = newValue; lock.unlock() } }
    var nowNanos: Int64 { nanos }
}
