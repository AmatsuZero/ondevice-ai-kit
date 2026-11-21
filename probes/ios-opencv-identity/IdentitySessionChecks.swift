import Foundation
import ODAIKitIdentity
import ODAIKitModelStore
import ODAIKitOpenCVIdentity

enum IdentitySessionChecks {
    struct Failed: Error { let name: String }
    static func run(models: ModelStore, root: URL, probe: IdentityProbe) async throws -> [String: Bool] {
        let obligation = IdentityOpenCvCleanupGuard(root: root.appendingPathComponent("cleanup-check"))
        guard !obligation.isPending else { throw Failed(name: "cleanupInitiallyClear") }
        try obligation.begin()
        guard IdentityOpenCvCleanupGuard(root: root.appendingPathComponent("cleanup-check")).isPending else {
            throw Failed(name: "cleanupSurvivesRestart")
        }
        try obligation.complete()
        guard !obligation.isPending else { throw Failed(name: "cleanupCompletionClearsMarker") }
        let session = try await IdentityOpenCvSession.open(root: root, models: models)
        do {
            let options = try EnrollmentOptions(qualityFloor: 1, qualityPolicyId: "geometric-only-uncalibrated", revision: "1")
            let enrolled = try await session.store.enroll(probe, candidate: session.candidate,
                grant: session.grant, options: options, backend: session.backend)
            guard let template = enrolled.templateRef else { throw Failed(name: "sessionEnrollment") }
            let other = try await session.store.createCandidate(session.grant)
            let second = try await session.store.enroll(probe, candidate: other,
                grant: session.grant, options: options, backend: session.backend)
            guard second.outcome == .enrolled else { throw Failed(name: "sessionSecondEnrollment") }
            do {
                _ = try await session.verification.verify(probe: probe, templateRef: template, grant: session.grant,
                    options: VerificationOptions(policy: OpenCvIdentityModel.unverifiedPolicy(kind: .verification), revision: "1"))
                throw Failed(name: "sessionUnverifiedPolicy")
            } catch let error as IdentityError {
                guard error.code == .thresholdUnverified else { throw error }
            }
            let receipt = try await session.deleteAndClose()
            // Idempotent cleanup must join the same completed operation.
            let repeated = try await session.deleteAndClose()
            guard receipt == repeated else { throw Failed(name: "sessionIdempotentCleanup") }
            let reopened = try IdentityStore(root: root.appendingPathComponent("identity"), purpose: session.grant.purpose,
                authorizationProvider: session.consent, keyProvider: session.keys,
                wallClock: SystemIdentityWallClock(), monotonicClock: SystemIdentityMonotonicClock())
            do {
                let status = try await reopened.awaitDeletion(receipt)
                guard status == .purged else { throw Failed(name: "sessionDurableFullGrantPurge") }
                do {
                    _ = try await reopened.createCandidate(session.grant)
                    throw Failed(name: "sessionRevokedAfterReopen")
                } catch let error as IdentityError {
                    guard error.code == .authorizationRevoked else { throw error }
                }
                try await reopened.close()
            } catch { try? await reopened.close(); throw error }
            return ["sessionTwoCandidatesEnrolled": true, "sessionUnverifiedPolicyRejected": true,
                "sessionDurableFullGrantPurge": true, "sessionIdempotentCleanup": true, "sessionRevokedAfterReopen": true,
                "cleanupObligationSurvivesRestart": true, "cleanupCompletionClearsMarker": true]
        } catch { _ = try? await session.deleteAndClose(); throw error }
    }
}
