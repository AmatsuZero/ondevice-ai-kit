#if os(iOS)
import XCTest
import Foundation
import CryptoKit
import ODAIKitCore
import ODAIKitIdentity
import ODAIKitModelStore
@testable import ODAIKitOpenCVIdentity

final class OpenCvIdentityRuntimeTests: XCTestCase {
    func testRealApprovedImageEnrollsAndReleasesModelLease() async throws {
        guard let sourcePath = ProcessInfo.processInfo.environment["ODAI_OPENCV_TEST_ASSETS"] else {
            throw XCTSkip("Explicit local approved asset directory is required; no bundled biometric fixture")
        }
        let source = URL(fileURLWithPath: sourcePath)
        let bytes = try Data(contentsOf: source.appendingPathComponent("astronaut.png"))
        XCTAssertEqual(SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined(),
            "88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5")
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("opencv-test-" + UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let models = try ModelStore(root: root.appendingPathComponent("models"), compatibility: ModelCompatibility(
            backendId: OpenCvIdentityModel.backendID, backendVersion: OpenCvIdentityModel.version, osVersion: "27.0", architecture: "arm64"))
        let manifest = try OpenCvIdentityModel.manifest()
        try await models.install(manifest: manifest, source: source.appendingPathComponent("models"))
        let backend = try await OpenCvIdentity.open(modelStore: models)
        do {
            do { try await models.remove(manifest.ref); XCTFail("live model lease was not retained") }
            catch let error as ModelStoreError { XCTAssertEqual(error, .inUse) }
            let probe = try IdentityProbe(encodedImage: bytes, revision: "approved-local-portrait")
            let quality = try backend.evaluateQuality(probe)
            XCTAssertEqual(quality.faceCount, 1); XCTAssertEqual(quality.score, 1)
            let feature = try backend.embed(probe)
            XCTAssertEqual(feature.values.count, 128)
            XCTAssertEqual(feature.values.reduce(0) { $0 + Double($1) * Double($1) }, 1, accuracy: 0.000001)
            let cancelled = IdentityStopSignal(); cancelled.requestStop(.cancelled)
            XCTAssertThrowsError(try backend.embed(probe, stopSignal: cancelled)) {
                XCTAssertEqual(($0 as? IdentityError)?.code, .cancelled)
            }
            let identity = try IdentityStore(root: root.appendingPathComponent("identity"), purpose: "local-prototype",
                authorizationProvider: OpenCvTestAuthority(), keyProvider: OpenCvTestKeys(),
                wallClock: SystemIdentityWallClock(), monotonicClock: SystemIdentityMonotonicClock())
            do {
                let grant = try AuthorizationGrant(grantId: UUID().uuidString, revocationDomainId: UUID().uuidString,
                    scope: [.enrollment, .verification, .identification], purpose: "local-prototype", issuedAtMillis: 0,
                    expiresAtMillis: nil, authorizationEpoch: 0, evidenceRef: "explicit-approved-local-flow-test")
                let candidate = try await identity.createCandidate(grant)
                let enrolled = try await identity.enroll(probe, candidate: candidate, grant: grant,
                    options: EnrollmentOptions(qualityFloor: 1, qualityPolicyId: "geometric-only-uncalibrated", revision: "1"), backend: backend)
                XCTAssertEqual(enrolled.outcome, .enrolled); XCTAssertNotNil(enrolled.templateRef)
                let owner = try OwnershipAuthorization(storeId: identity.storeId, evidenceRef: "local-test-owner")
                let receipt = try await identity.deleteCandidate(candidate, ownership: owner)
                let deletion = try await identity.awaitDeletion(receipt)
                XCTAssertEqual(deletion, .purged)
                try await identity.close()
            } catch { try? await identity.close(); throw error }
            await backend.closeAndWait()
            XCTAssertEqual(backend.availability().reason, .closed)
            try await models.remove(manifest.ref)
            let status = await models.status(manifest.ref)
            XCTAssertEqual(status, .absent)
        } catch { await backend.closeAndWait(); throw error }
    }
}

private struct OpenCvTestAuthority: IdentityAuthorizationProvider {
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 { grant.authorizationEpoch }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {}
}
private final class OpenCvTestKeys: IdentityKeyProvider, @unchecked Sendable {
    private let lock = NSLock(); private var keys: [String: SymmetricKey] = [:]
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        lock.lock(); defer { lock.unlock() }
        if let key = keys[id] { return key }
        guard createIfMissing else { throw IdentityError(.reEnrollmentRequired) }
        let key = SymmetricKey(size: .bits256); keys[id] = key; return key
    }
    func deleteKey(id: String) throws { lock.lock(); keys.removeValue(forKey: id); lock.unlock() }
}
#endif
