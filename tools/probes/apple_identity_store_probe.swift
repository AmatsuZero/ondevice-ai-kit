// Debug-only iOS device acceptance host. This source is intentionally kept out of
// the SDK targets; the generated probe project enables testing for ODAIKitIdentity.
#if DEBUG && os(iOS)
import CryptoKit
import Foundation
import Security
import SwiftUI

@testable import ODAIKitIdentity

@main
struct AppleIdentityStoreProbeApp: App {
    @StateObject private var runner = AppleIdentityStoreProbeRunner()

    var body: some Scene {
        WindowGroup {
            Text(runner.status)
                .padding()
                .task { await runner.runOnce() }
        }
    }
}

@MainActor
private final class AppleIdentityStoreProbeRunner: ObservableObject {
    @Published private(set) var status = "Identity Store probe starting"
    private var didStart = false

    func runOnce() async {
        guard !didStart else { return }
        didStart = true
        let arguments = ProcessInfo.processInfo.arguments
        let runID = arguments.first(where: { $0.hasPrefix("--run-id=") }).map { String($0.dropFirst(9)) } ?? UUID().uuidString
        if let stage = arguments.first(where: { $0.hasPrefix("--restart-stage=") }) {
            let outcome = await AppleIdentityRestartProbe(runID: runID, stage: String(stage.dropFirst(16))).run()
            status = outcome ? "Identity restart checkpoint ready" : "Identity restart probe failed"
            return
        }
        let outcome = await AppleIdentityStoreProbe(forceFailure: arguments.contains("--force-failure"), runID: runID).run()
        status = outcome ? "Identity Store probe passed" : "Identity Store probe failed"
    }
}

/// Exercises an isolated, synthetic, same-process recovery path only. It neither
/// enumerates Keychain entries nor emits vectors, key material, or business IDs.
private struct AppleIdentityStoreProbe {
    let forceFailure: Bool
    let runID: String

    func run() async -> Bool {
        let namespace = "probe." + UUID().uuidString.lowercased()
        let fileManager = FileManager.default
        let root = applicationSupportRoot(fileManager: fileManager)
        let reportURL = documentsReportURL(fileManager: fileManager)
        // Replace any prior fixed-name report before touching the Store. If the
        // process is killed, an exporter sees this run's `running` marker rather
        // than stale success from an earlier run.
        do { if fileManager.fileExists(atPath: reportURL.path) { try fileManager.removeItem(at: reportURL) } }
        catch { return false }
        do {
            try writeReport(url: reportURL, status: "running", flags: [:], counts: [:],
                            errorCode: "inProgress", forceFailure: forceFailure, runID: runID)
        } catch { return false }
        let authority = ProbeAuthority()
        let keyIDs = ExactKeyTracker()
        var handles: [IdentityStore] = []
        var leases: [IdentityTemplateLease] = []
        var flags: [String: Bool] = [:]
        var counts: [String: Int] = [:]
        var errorCode: String?
        var cleanupOK = true

        func makeKeys() throws -> TrackingKeyProvider {
            TrackingKeyProvider(delegate: try KeychainIdentityKeyProvider(namespace: namespace), tracker: keyIDs)
        }
        func openStore() throws -> IdentityStore {
            let store = try IdentityStore(root: root, purpose: "probe", authorizationProvider: authority,
                                          keyProvider: makeKeys(), wallClock: SystemIdentityWallClock(),
                                          monotonicClock: SystemIdentityMonotonicClock())
            handles.append(store)
            return store
        }
        func grant(_ id: String, epoch: Int64) throws -> AuthorizationGrant {
            try AuthorizationGrant(grantId: id, revocationDomainId: "probe.domain",
                                   scope: [.enrollment, .verification, .identification], purpose: "probe",
                                   issuedAtMillis: 0, expiresAtMillis: nil, authorizationEpoch: epoch,
                                   evidenceRef: "synthetic")
        }
        func enroll(_ store: IdentityStore, candidate: CandidateRef, grant: AuthorizationGrant,
                    backend: FixtureIdentityBackend) async throws -> TemplateRef {
            let result = try await store.enroll(IdentityProbe(fixtureID: "synthetic", vector: [1, 2]),
                                                candidate: candidate, grant: grant,
                                                options: EnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "probe.quality", revision: "1"),
                                                backend: backend)
            guard let ref = result.templateRef else { throw ProbeFailure.assertion }
            return ref
        }

        do {
            let first = try openStore()
            // Observe the SDK's exclusion attribute rather than setting it in the harness.
            flags["applicationSupportRoot"] = root.path.hasPrefix(applicationSupportDirectory(fileManager: fileManager).path + "/")
            flags["backupExcluded"] = try root.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup == true
            try require(flags["applicationSupportRoot"] == true && flags["backupExcluded"] == true)

            let backend = try FixtureIdentityBackend.system()
            let originalGrant = try grant("initial", epoch: 0)
            let candidate = try await first.createCandidate(originalGrant)
            let template = try await enroll(first, candidate: candidate, grant: originalGrant, backend: backend)
            let storeID = first.storeId
            let journal = root.appendingPathComponent("identity-journal-store.bin")
            let payload = root.appendingPathComponent("identity-template-\(template.id).bin")
            let journalBytes = try Data(contentsOf: journal)
            let payloadBytes = try Data(contentsOf: payload)
            flags["encryptedFiles"] = fileManager.fileExists(atPath: journal.path) && fileManager.fileExists(atPath: payload.path)
                && journalBytes.range(of: Data("synthetic".utf8)) == nil
                && journalBytes.prefix(4) == Data("ODJ1".utf8) && payloadBytes.prefix(4) == Data("ODE1".utf8)
            try require(flags["encryptedFiles"] == true)
            flags["keychainAttributes"] = try keychainAttributesAreDeviceOnly(namespace: namespace, ids: keyIDs.snapshot())
            try require(flags["keychainAttributes"] == true)

            if forceFailure { throw ProbeFailure.forcedAfterPersistence }
            try await first.close()

            // The provider and actor are rebuilt in this process. This does not claim
            // process-death, reboot, filesystem-fault, or lock-background recovery.
            let recovered = try openStore()
            try require(recovered.storeId == storeID)
            let recoveredCandidate = try await recovered.recordedTemplateCandidate(template)
            try require(recoveredCandidate == candidate)
            let lease = try await recovered.acquireTemplateLease(template, grant: originalGrant, scope: .verification,
                                                                 embeddingSpace: template.embeddingSpaceId)
            leases.append(lease)
            try require(try lease.embedding().values == [1, 2])
            flags["sameProcessMembershipAndDecrypt"] = true
            let owner = try OwnershipAuthorization(storeId: storeID, evidenceRef: "owner")
            let deletion = try await recovered.deleteTemplate(template, ownership: owner)
            let pendingDeletion = try await recovered.awaitDeletion(deletion)
            try require(pendingDeletion == .purgePending)
            try require(fileManager.fileExists(atPath: payload.path))
            await lease.close()
            leases.removeAll()
            let purgedDeletion = try await recovered.awaitDeletion(deletion)
            try require(purgedDeletion == .purged)
            try require(!fileManager.fileExists(atPath: payload.path))
            let repeatDeletion = try await recovered.deleteTemplate(template, ownership: owner)
            try require(repeatDeletion == deletion)
            let templateKey = "template.\(storeID).\(template.id)"
            try await expectIdentityCode(.reEnrollmentRequired) { try makeKeys().key(id: templateKey, createIfMissing: false) }
            try await recovered.close()

            let tombstones = try openStore()
            let persistedDeletion = try await tombstones.awaitDeletion(deletion)
            try require(persistedDeletion == .purged)
            try await expectIdentityCode(.templateRevoked) { try await tombstones.recordedTemplateCandidate(template) }
            flags["receiptAndTombstonePersisted"] = true

            let domainTemplate = try await enroll(tombstones, candidate: candidate, grant: originalGrant, backend: backend)
            let domainLease = try await tombstones.acquireTemplateLease(domainTemplate, grant: originalGrant, scope: .verification,
                                                                       embeddingSpace: domainTemplate.embeddingSpaceId)
            leases.append(domainLease)
            let revocation = try await tombstones.revokeGrant(originalGrant.grantId, ownership: owner)
            let pendingRevocation = try await tombstones.awaitDeletion(revocation)
            try require(pendingRevocation == .purgePending)
            await domainLease.close()
            leases.removeAll()
            let purgedRevocation = try await tombstones.awaitDeletion(revocation)
            try require(purgedRevocation == .purged)
            try await tombstones.close()

            let revoked = try openStore()
            let persistedRevocation = try await revoked.awaitDeletion(revocation)
            try require(persistedRevocation == .purged)
            try await expectIdentityCode(.authorizationRevoked) { try await revoked.createCandidate(grant("old-epoch", epoch: 0)) }
            try await expectIdentityCode(.candidateRevoked) { try await revoked.recordedCandidateGrant(candidate) }
            authority.epoch = 1
            let fresh = try await revoked.createCandidate(grant("fresh", epoch: 1))
            try require(fresh.id != candidate.id)
            try await expectIdentityCode(.templateRevoked) { try await revoked.recordedTemplateCandidate(domainTemplate) }
            flags["domainRevocationPersisted"] = true
            counts["managedKeyCount"] = keyIDs.snapshot().count
        } catch {
            errorCode = probeErrorCode(error)
        }

        // Always attempt every exact handle, key, and root cleanup. A failure here
        // overrides a prior success and never touches unrelated Keychain entries.
        for lease in leases { await lease.close() }
        for handle in handles.reversed() {
            do { try await handle.close() } catch { cleanupOK = false }
        }
        for id in keyIDs.snapshot() {
            do {
                let keys = try makeKeys()
                try keys.deleteKey(id: id)
                try await expectIdentityCode(.reEnrollmentRequired) { try keys.key(id: id, createIfMissing: false) }
            } catch { cleanupOK = false }
        }
        do {
            if fileManager.fileExists(atPath: root.path) { try fileManager.removeItem(at: root) }
            try require(!fileManager.fileExists(atPath: root.path))
        } catch { cleanupOK = false }
        flags["cleanup"] = cleanupOK
        if !cleanupOK { errorCode = "cleanupFailed" }

        let passed = errorCode == nil && cleanupOK
        do {
            try writeReport(url: reportURL, status: passed ? "pass" : "fail", flags: flags, counts: counts,
                            errorCode: errorCode, forceFailure: forceFailure, runID: runID)
            return passed
        } catch { return false }
    }
}

private final class ProbeAuthority: IdentityAuthorizationProvider, @unchecked Sendable {
    private let lock = NSLock()
    private var value: Int64 = 0
    var epoch: Int64 { get { lock.lock(); defer { lock.unlock() }; return value } set { lock.lock(); value = newValue; lock.unlock() } }
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 { epoch }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {}
}

private final class ExactKeyTracker: @unchecked Sendable {
    private let lock = NSLock(); private var ids = Set<String>()
    func record(_ id: String) { lock.lock(); ids.insert(id); lock.unlock() }
    func snapshot() -> [String] { lock.lock(); defer { lock.unlock() }; return ids.sorted() }
}

private struct TrackingKeyProvider: IdentityKeyProvider {
    let delegate: KeychainIdentityKeyProvider
    let tracker: ExactKeyTracker
    init(delegate: KeychainIdentityKeyProvider, tracker: ExactKeyTracker) { self.delegate = delegate; self.tracker = tracker }
    func key(id: String, createIfMissing: Bool) throws -> SymmetricKey {
        if createIfMissing { tracker.record(id) }
        return try delegate.key(id: id, createIfMissing: createIfMissing)
    }
    func deleteKey(id: String) throws { try delegate.deleteKey(id: id) }
}

private enum ProbeFailure: Error { case assertion, forcedAfterPersistence }

private func require(_ condition: @autoclosure () throws -> Bool) throws {
    guard try condition() else { throw ProbeFailure.assertion }
}

private func expectIdentityCode<T>(_ code: IdentityErrorCode, action: () async throws -> T) async throws {
    do { _ = try await action(); throw ProbeFailure.assertion }
    catch let error as IdentityError where error.code == code { }
}

private func probeErrorCode(_ error: Error) -> String {
    if let error = error as? IdentityError { return error.code.rawValue }
    if let error = error as? ProbeFailure, case .forcedAfterPersistence = error { return "forcedFailure" }
    return "probeAssertion"
}

private func applicationSupportDirectory(fileManager: FileManager) -> URL {
    fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
}

private func applicationSupportRoot(fileManager: FileManager) -> URL {
    applicationSupportDirectory(fileManager: fileManager).appendingPathComponent("identity-store-probe-" + UUID().uuidString.lowercased(), isDirectory: true)
}

private func documentsReportURL(fileManager: FileManager) -> URL {
    fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("identity-store-probe.json")
}

private func keychainAttributesAreDeviceOnly(namespace: String, ids: [String]) throws -> Bool {
    let service = "io.ondeviceai.identity." + namespace
    guard !ids.isEmpty else { return false }
    for id in ids {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: id,
                                    kSecAttrSynchronizable as String: kCFBooleanFalse!,
                                    kSecReturnAttributes as String: true,
                                    kSecMatchLimit as String: kSecMatchLimitOne]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let attributes = result as? [String: Any],
              (attributes[kSecAttrAccessible as String] as? String) == (kSecAttrAccessibleWhenUnlockedThisDeviceOnly as String) else { return false }
        if let synchronizable = attributes[kSecAttrSynchronizable as String] as? Bool, synchronizable { return false }
    }
    return true
}

private func writeReport(url: URL, status: String, flags: [String: Bool], counts: [String: Int], errorCode: String?, forceFailure: Bool, runID: String) throws {
    let report: [String: Any] = ["probe": "identityStore", "status": status,
                                 "runID": runID,
                                 "sameProcessOnly": true, "forceFailure": forceFailure,
                                 "flags": flags, "counts": counts, "errorCode": errorCode as Any? ?? NSNull()]
    let data = try JSONSerialization.data(withJSONObject: report, options: [.sortedKeys])
    try data.write(to: url, options: .atomic)
}
#endif
