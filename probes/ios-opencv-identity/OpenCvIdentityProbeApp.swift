import SwiftUI
import CryptoKit
import ODAIKitIdentity
import ODAIKitModelStore
import ODAIKitOpenCVIdentity

@main struct OpenCvIdentityProbeApp: App {
    @State private var status = "Local Identity probe ready"
    var body: some Scene {
        WindowGroup {
            Text(status).task {
                guard ProcessInfo.processInfo.arguments.contains("--run-opencv-validation") else { return }
                let report = await Task.detached { await OpenCvIdentityProbe.run() }.value
                status = report
            }
        }
    }
}

private enum OpenCvIdentityProbe {
    struct CheckFailed: Error { let name: String }
    static func run() async -> String {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let assets = documents.appendingPathComponent("opencv-validation-input")
        let root = documents.appendingPathComponent("opencv-validation-" + UUID().uuidString)
        var checks: [String: Bool] = [:]
        var report: [String: Any] = ["runId": UUID().uuidString, "backend": OpenCvIdentityModel.backendID,
            "runtimeVersion": OpenCvIdentityModel.version, "modelRevision": OpenCvIdentityModel.ref.revision,
            "policyStatus": "UNVERIFIED", "pad": "absent", "acceptance": "headless-device-flow"]
        func require(_ value: Bool, _ name: String) throws {
            checks[name] = value
            if !value { throw CheckFailed(name: name) }
        }
        var backend: LocalIdentityBackend?
        var store: IdentityStore?
        var indexes: IndexStore?
        var stage = "input"
        do {
            let bytes = try Data(contentsOf: assets.appendingPathComponent("astronaut.png"))
            let hash = SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
            report["inputSHA256"] = hash
            try require(hash == "88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5", "approvedInput")
            stage = "modelStore"
            let os = ProcessInfo.processInfo.operatingSystemVersion
            let models = try ModelStore(root: root.appendingPathComponent("models"), compatibility: ModelCompatibility(
                backendId: OpenCvIdentityModel.backendID, backendVersion: OpenCvIdentityModel.version,
                osVersion: "\(os.majorVersion).\(os.minorVersion).\(os.patchVersion)",
                architecture: "arm64"))
            stage = "install"
            try await models.install(manifest: OpenCvIdentityModel.manifest(), source: assets.appendingPathComponent("models"))
            stage = "open"
            let opened = try await OpenCvIdentity.open(modelStore: models); backend = opened
            do { try await models.remove(OpenCvIdentityModel.ref); try require(false, "modelPinned") }
            catch let failure as ModelStoreError { try require(failure == .inUse, "modelPinned") }
            let probe = try IdentityProbe(encodedImage: bytes, revision: "approved-local-portrait")
            stage = "quality"
            let quality = try opened.evaluateQuality(probe)
            try require(quality.faceCount == 1 && quality.score == 1, "oneUsableFace")
            stage = "feature"
            let feature = try opened.embed(probe)
            try require(feature.values.count == 128 && abs(feature.values.reduce(0) { $0 + Double($1) * Double($1) } - 1) < 0.000001,
                "normalized128Features")
            let stop = IdentityStopSignal(); stop.requestStop(.cancelled)
            do { _ = try opened.embed(probe, stopSignal: stop); try require(false, "cancelled") }
            catch let error as IdentityError { try require(error.code == .cancelled, "cancelled") }
            let renderer = await MainActor.run { UIGraphicsImageRenderer(size: CGSize(width: 64, height: 64)).pngData { context in
                UIColor.white.setFill(); context.fill(CGRect(x: 0, y: 0, width: 64, height: 64))
            } }
            let blank = try IdentityProbe(encodedImage: renderer, revision: "blank")
            let blankQuality = try opened.evaluateQuality(blank)
            try require(blankQuality.faceCount == 0 && blankQuality.score == 0, "blankRejected")
            do { _ = try opened.embed(blank); try require(false, "blankEmbeddingRejected") }
            catch let error as IdentityError { try require(error.code == .invalidInput, "blankEmbeddingRejected") }
            for kind in [IdentityPolicyKind.verification, .identification] {
                do { try OpenCvIdentityModel.unverifiedPolicy(kind: kind).requireVerified(); try require(false, "policy-\(kind.rawValue)") }
                catch let error as IdentityError { try require(error.code == .thresholdUnverified, "policy-\(kind.rawValue)") }
            }
            let authority = ProbeAuthority()
            let keys = try KeychainIdentityKeyProvider(namespace: "opencv-local-probe")
            let identity = try IdentityStore(root: root.appendingPathComponent("identity"), purpose: "local-prototype",
                authorizationProvider: authority, keyProvider: keys,
                wallClock: SystemIdentityWallClock(), monotonicClock: SystemIdentityMonotonicClock())
            store = identity
            let index = try IndexStore(root: root.appendingPathComponent("indexes"), identityStore: identity,
                keyProvider: keys, buildIndex: { try ExactCosineIdentityIndex.build($0, stopSignal: $1) })
            indexes = index
            let grant = try AuthorizationGrant(grantId: UUID().uuidString, revocationDomainId: UUID().uuidString,
                scope: [.enrollment, .verification, .identification], purpose: "local-prototype", issuedAtMillis: 0,
                expiresAtMillis: nil, authorizationEpoch: 0, evidenceRef: "approved-local-flow-test")
            let candidate = try await identity.createCandidate(grant)
            let enrolled = try await identity.enroll(probe, candidate: candidate, grant: grant,
                options: EnrollmentOptions(qualityFloor: 1, qualityPolicyId: "geometric-only-uncalibrated", revision: "1"), backend: opened)
            try require(enrolled.outcome == .enrolled && enrolled.templateRef != nil, "encryptedEnrollment")
            let owner = try OwnershipAuthorization(storeId: identity.storeId, evidenceRef: "local-test-owner")
            let receipt = try await identity.deleteCandidate(candidate, ownership: owner)
            let status = try await identity.awaitDeletion(receipt)
            try require(status == .purged, "candidatePurged")
            // A second candidate deliberately has no UI-selected reference at teardown.
            _ = try await identity.createCandidate(grant)
            let all = try await identity.revokeGrant(grant.grantId, ownership: owner)
            let fullStatus = try await identity.awaitDeletion(all)
            try require(fullStatus == .purged, "grantPurged")
            await index.close(); indexes = nil
            try await identity.close(); store = nil
            let reopened = try IdentityStore(root: root.appendingPathComponent("identity"), purpose: "local-prototype",
                authorizationProvider: authority, keyProvider: keys,
                wallClock: SystemIdentityWallClock(), monotonicClock: SystemIdentityMonotonicClock())
            store = reopened
            let durable = try await reopened.awaitDeletion(all)
            try require(durable == .purged, "durablePurgeAfterReopen")
            try await reopened.close(); store = nil
            await opened.closeAndWait(); backend = nil
            stage = "session"
            for (name, passed) in try await IdentitySessionChecks.run(models: models, root: root.appendingPathComponent("session"), probe: probe) {
                try require(passed, name)
            }
            try await models.remove(OpenCvIdentityModel.ref)
            let modelStatus = await models.status(OpenCvIdentityModel.ref)
            try require(modelStatus == .absent, "modelReleased")
            report["status"] = "passed"
        } catch {
            report["status"] = "failed"
            report["stage"] = stage
            if let failure = error as? ModelStoreError { report["modelStoreError"] = String(describing: failure) }
            report["error"] = (error as? IdentityError)?.code.rawValue ?? (error as? CheckFailed)?.name ?? "runtime-or-io"
        }
        await backend?.closeAndWait()
        await indexes?.close()
        try? await store?.close()
        report["checks"] = checks
        if let data = try? JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]) {
            try? data.write(to: documents.appendingPathComponent("opencv-validation-report.json"), options: .atomic)
        }
        return report["status"] as? String ?? "failed"
    }
}

/// Only this isolated explicitly launched validation consumer uses this authority.
private struct ProbeAuthority: IdentityAuthorizationProvider {
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 { grant.authorizationEpoch }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {}
}
