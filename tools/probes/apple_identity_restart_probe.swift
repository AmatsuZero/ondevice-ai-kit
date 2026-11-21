// Debug-only process-death acceptance helper; never linked into SDK products.
#if DEBUG && os(iOS)
import CryptoKit
import Foundation
import Darwin
@testable import ODAIKitIdentity

/// The host must SIGKILL the reported PID between checkpoints. This helper never
/// claims process death from its own state; the external launch/kill evidence does.
@MainActor
struct AppleIdentityRestartProbe {
    let runID: String
    let stage: String
    private var manager: FileManager { .default }
    private var documents: URL { manager.urls(for: .documentDirectory, in: .userDomainMask)[0] }
    private var support: URL { manager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0] }
    // UUID-only input prevents host parameters from escaping the private directory.
    private var directory: URL { support.appendingPathComponent("identity-restart-" + runID, isDirectory: true) }
    private var root: URL { directory.appendingPathComponent("store", isDirectory: true) }
    private var report: URL { documents.appendingPathComponent("identity-restart-probe.json") }
    private var state: URL { directory.appendingPathComponent("state.json") }
    private var keysFile: URL { directory.appendingPathComponent("keys.json") }
    private var namespace: String { "restart." + runID }

    func run() async -> Bool {
        guard UUID(uuidString: runID) != nil, ["create", "delete", "recover", "cleanup"].contains(stage) else { return false }
        var store: IdentityStore?
        var lease: IdentityTemplateLease?
        do {
            try write(status: "running", flags: [:])
            if stage == "create" {
                guard !manager.fileExists(atPath: directory.path) else { throw RestartFailure.assertion }
                try manager.createDirectory(at: directory, withIntermediateDirectories: true)
                var attributes = URLResourceValues(); attributes.isExcludedFromBackup = true
                var ownDirectory = directory; try ownDirectory.setResourceValues(attributes)
            }
            let provider = try RestartKeys(namespace: namespace, ledger: keysFile)
            if stage == "cleanup" {
                for id in try provider.ids() {
                    try provider.deleteKey(id: id)
                    do { _ = try provider.key(id: id, createIfMissing: false); throw RestartFailure.assertion }
                    catch let error as IdentityError where error.code == .reEnrollmentRequired { }
                }
                try manager.removeItem(at: directory)
                try write(status: "pass", flags: ["cleanup": !manager.fileExists(atPath: directory.path)])
                return true
            }
            let authority = RestartAuthority()
            let opened = try IdentityStore(root: root, purpose: "probe", authorizationProvider: authority,
                keyProvider: provider, wallClock: SystemIdentityWallClock(), monotonicClock: SystemIdentityMonotonicClock())
            store = opened
            let grant = try AuthorizationGrant(grantId: "restart", revocationDomainId: "restart.domain",
                scope: [.enrollment, .verification], purpose: "probe", issuedAtMillis: 0,
                expiresAtMillis: nil, authorizationEpoch: 0, evidenceRef: "synthetic")
            if stage == "create" {
                let candidate = try await opened.createCandidate(grant)
                let enrollment = try await opened.enroll(IdentityProbe(fixtureID: "synthetic", vector: [1, 2]),
                    candidate: candidate, grant: grant,
                    options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "probe.quality", revision: "1"),
                    backend: try FixtureIdentityBackend.system())
                guard let template = enrollment.templateRef else { throw RestartFailure.assertion }
                let checkpoint = RestartState(storeID: opened.storeId, candidate: candidate.canonicalJSON(),
                    template: template.canonicalJSON(), receipt: nil)
                try JSONEncoder().encode(checkpoint).write(to: state, options: .atomic)
                try write(status: "ready", flags: ["enrollmentCommitted": true, "writerStillOpen": true])
            } else {
                var checkpoint = try JSONDecoder().decode(RestartState.self, from: Data(contentsOf: state))
                try check(opened.storeId == checkpoint.storeID)
                let template = try TemplateRef.decodeJSON(checkpoint.template)
                let candidate = try CandidateRef.decodeJSON(checkpoint.candidate)
                let payload = root.appendingPathComponent("identity-template-\(template.id).bin")
                if stage == "delete" {
                    try check(try await opened.recordedTemplateCandidate(template) == candidate)
                    let held = try await opened.acquireTemplateLease(template, grant: grant,
                        scope: .verification, embeddingSpace: template.embeddingSpaceId)
                    lease = held
                    try check(try held.embedding().values == [1, 2])
                    let receipt = try await opened.deleteTemplate(template,
                        ownership: OwnershipAuthorization(storeId: opened.storeId, evidenceRef: "owner"))
                    try check(try await opened.awaitDeletion(receipt) == .purgePending)
                    try check(manager.fileExists(atPath: payload.path))
                    checkpoint.receipt = receipt.canonicalJSON()
                    try JSONEncoder().encode(checkpoint).write(to: state, options: .atomic)
                    try write(status: "ready", flags: ["membershipRecovered": true, "decryptRecovered": true,
                        "deletionCommitted": true, "leaseHeld": true, "payloadPending": true])
                } else {
                    guard let receiptJSON = checkpoint.receipt else { throw RestartFailure.assertion }
                    let receipt = try DeletionReceipt.decodeJSON(receiptJSON)
                    try check(try await opened.awaitDeletion(receipt) == .purged)
                    try check(!manager.fileExists(atPath: payload.path))
                    do { _ = try await opened.recordedTemplateCandidate(template); throw RestartFailure.assertion }
                    catch let error as IdentityError where error.code == .templateRevoked { }
                    do {
                        _ = try provider.key(id: "template.\(opened.storeId).\(template.id)", createIfMissing: false)
                        throw RestartFailure.assertion
                    } catch let error as IdentityError where error.code == .reEnrollmentRequired { }
                    let repeated = try await opened.deleteTemplate(template,
                        ownership: OwnershipAuthorization(storeId: opened.storeId, evidenceRef: "owner"))
                    try check(repeated == receipt)
                    try await opened.close(); store = nil
                    try write(status: "pass", flags: ["tombstoneRecovered": true, "receiptStable": true,
                        "payloadPurged": true, "templateKeyPurged": true, "closed": true])
                    return true
                }
            }
            // Keep both the writer and any lease alive until an external SIGKILL.
            // There is deliberately no graceful close on the successful ready path.
            while true {
                try await Task.sleep(nanoseconds: 1_000_000_000)
                // Referenced after every suspension, so neither owner can be
                // released while the external controller waits to SIGKILL us.
                withExtendedLifetime((store, lease)) { }
            }
        } catch {
            if let lease { await lease.close() }
            if let store { try? await store.close() }
            try? write(status: "fail", flags: [:])
            return false
        }
    }

    private func write(status: String, flags: [String: Bool]) throws {
        let fields: [String: Any] = ["probe": "identityRestart", "runID": runID, "stage": stage,
            "pid": getpid(), "status": status, "flags": flags]
        try JSONSerialization.data(withJSONObject: fields, options: [.sortedKeys]).write(to: report, options: .atomic)
    }
    private func check(_ condition: Bool) throws { if !condition { throw RestartFailure.assertion } }
}

private struct RestartState: Codable {
    let storeID: String
    let candidate: String
    let template: String
    var receipt: String?
}
private enum RestartFailure: Error { case assertion }
private struct RestartAuthority: IdentityAuthorizationProvider {
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 { 0 }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws { }
}

/// Record exact owned key IDs before creation, so cleanup survives process death.
/// The ledger holds IDs only, never key bytes or vectors; it is in the excluded root.
private final class RestartKeys: IdentityKeyProvider, @unchecked Sendable {
    private let lock = NSLock()
    private let delegate: KeychainIdentityKeyProvider
    private let ledger: URL
    init(namespace: String, ledger: URL) throws {
        delegate = try KeychainIdentityKeyProvider(namespace: namespace)
        self.ledger = ledger
    }
    func ids() throws -> [String] {
        guard FileManager.default.fileExists(atPath: ledger.path) else { return [] }
        return try JSONDecoder().decode([String].self, from: Data(contentsOf: ledger))
    }
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        lock.lock(); defer { lock.unlock() }
        if createIfMissing {
            var owned = try ids()
            if !owned.contains(id) {
                owned.append(id)
                try JSONEncoder().encode(owned.sorted()).write(to: ledger, options: .atomic)
            }
        }
        return try delegate.key(id: id, createIfMissing: createIfMissing)
    }
    func deleteKey(id: String) throws { try delegate.deleteKey(id: id) }
}
#endif
