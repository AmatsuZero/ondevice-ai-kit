import Foundation
import ODAIKitIdentity
import ODAIKitModelStore
import ODAIKitOpenCVIdentity

/// Created only by explicit local consent. Owns all native resources for one Demo session.
final class IdentityOpenCvSession: @unchecked Sendable {
    let backend: LocalIdentityBackend
    let consent: IdentityOpenCvConsent
    let keys: KeychainIdentityKeyProvider
    let store: IdentityStore
    let indexes: IndexStore
    let verification: FaceVerification
    let identification: FaceIdentification
    let candidate: CandidateRef
    var grant: AuthorizationGrant { consent.grant }
    private let cleanupLock = NSLock()
    private var cleanup: Task<RevocationReceipt, Error>?

    private init(backend: LocalIdentityBackend, consent: IdentityOpenCvConsent,
                 keys: KeychainIdentityKeyProvider, store: IdentityStore, indexes: IndexStore,
                 verification: FaceVerification, identification: FaceIdentification, candidate: CandidateRef) {
        self.backend = backend; self.consent = consent; self.keys = keys; self.store = store
        self.indexes = indexes; self.verification = verification; self.identification = identification
        self.candidate = candidate
    }

    static func open(root: URL, models: ModelStore) async throws -> IdentityOpenCvSession {
        try Task.checkCancellation()
        let fm = FileManager.default
        try fm.createDirectory(at: root, withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.complete])
        var privateRoot = root
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try privateRoot.setResourceValues(values)
        let consent = try IdentityOpenCvConsent()
        let keys = try KeychainIdentityKeyProvider(namespace: "opencv-local-prototype")
        let backend = try await OpenCvIdentity.open(modelStore: models)
        var store: IdentityStore?
        var indexes: IndexStore?
        var opened: IdentityOpenCvSession?
        do {
            let identity = try IdentityStore(root: root.appendingPathComponent("identity"), purpose: consent.grant.purpose,
                authorizationProvider: consent, keyProvider: keys,
                wallClock: SystemIdentityWallClock(), monotonicClock: SystemIdentityMonotonicClock())
            store = identity
            consent.bindStore(identity.storeId)
            let index = try IndexStore(root: root.appendingPathComponent("indexes"), identityStore: identity,
                keyProvider: keys, buildIndex: { try ExactCosineIdentityIndex.build($0, stopSignal: $1) })
            indexes = index
            let verification = try FaceVerification(identityStore: identity,
                defaultBackendId: backend.backendId, backends: [backend])
            let identification = try FaceIdentification(identityStore: identity, indexStore: index,
                defaultBackendId: backend.backendId, backends: [backend])
            let candidate = try await identity.createCandidate(consent.grant)
            let session = IdentityOpenCvSession(backend: backend, consent: consent, keys: keys,
                store: identity, indexes: index, verification: verification, identification: identification, candidate: candidate)
            opened = session
            try Task.checkCancellation()
            return session
        } catch {
            if let opened { _ = try? await opened.deleteAndClose() }
            else {
                consent.close()
                await indexes?.close()
                try? await store?.close()
                await backend.closeAndWait()
            }
            throw error
        }
    }

    /// Detached cleanup survives a cancelled picker/host task and joins on repeated calls.
    func deleteAndClose() async throws -> RevocationReceipt { try await cleanupTask().value }
    private func cleanupTask() -> Task<RevocationReceipt, Error> {
        cleanupLock.lock(); defer { cleanupLock.unlock() }
        if let cleanup { return cleanup }
        consent.close()
        let task = Task.detached { [self] in
            do {
                // Includes every candidate in this grant, even if its UI callback was cancelled.
                let receipt = try await store.revokeGrant(grant.grantId, ownership: consent.ownership())
                await verification.close(); await identification.close()
                await backend.closeAndWait()
                let status = try await store.awaitDeletion(receipt,
                    deadline: IdentityDeadline(clock: SystemIdentityMonotonicClock(), durationNanos: 30_000_000_000))
                guard status == .purged else { throw IdentityError(.backendUnavailable) }
                await indexes.close(); try await store.close()
                return receipt
            } catch {
                await verification.close(); await identification.close()
                await backend.closeAndWait(); await indexes.close(); try? await store.close()
                throw error
            }
        }
        cleanup = task
        return task
    }
}

/// Host authority accepts only the declaration produced by the explicit consent action.
final class IdentityOpenCvConsent: IdentityAuthorizationProvider, @unchecked Sendable {
    let grant: AuthorizationGrant
    private let evidence = UUID().uuidString
    private let lock = NSLock()
    private var active = true
    private var storeID: String?
    init() throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        grant = try AuthorizationGrant(grantId: UUID().uuidString, revocationDomainId: UUID().uuidString,
            scope: [.enrollment, .verification, .identification], purpose: "local-identity-prototype",
            issuedAtMillis: now, expiresAtMillis: now + 30 * 60 * 1000, authorizationEpoch: 0,
            evidenceRef: evidence)
    }
    func bindStore(_ id: String) { lock.lock(); defer { lock.unlock() }; precondition(storeID == nil); storeID = id }
    func ownership() throws -> OwnershipAuthorization {
        lock.lock(); defer { lock.unlock() }
        guard let storeID else { throw IdentityError(.authorizationRequired) }
        return try OwnershipAuthorization(storeId: storeID, evidenceRef: evidence)
    }
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 {
        lock.lock(); defer { lock.unlock() }
        guard active, minimumEpoch == 0 else { throw IdentityError(.authorizationRevoked) }
        guard grant == self.grant, nowMillis >= grant.issuedAtMillis else { throw IdentityError(.authorizationRequired) }
        guard nowMillis < (grant.expiresAtMillis ?? 0) else { throw IdentityError(.authorizationExpired) }
        return 0
    }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {
        lock.lock(); defer { lock.unlock() }
        guard authorization.storeId == storeID, authorization.evidenceRef == evidence else { throw IdentityError(.authorizationRequired) }
    }
    func close() { lock.lock(); active = false; lock.unlock() }
}

/// Persisted before activation. A failed purge or interrupted session blocks new enrollment
/// on the next launch; automatic recovery is deliberately not offered by this prototype.
final class IdentityOpenCvCleanupGuard: @unchecked Sendable {
    private let root: URL
    private let marker: URL
    private let lock = NSLock()
    init(root: URL) { self.root = root; marker = root.appendingPathComponent("cleanup-pending") }
    var isPending: Bool {
        lock.lock(); defer { lock.unlock() }
        return FileManager.default.fileExists(atPath: marker.path)
    }
    func begin() throws {
        lock.lock(); defer { lock.unlock() }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.complete])
        try Data("local-identity-cleanup-required-v1".utf8).write(to: marker, options: [.atomic, .completeFileProtection])
    }
    func complete() throws {
        lock.lock(); defer { lock.unlock() }
        if FileManager.default.fileExists(atPath: marker.path) { try FileManager.default.removeItem(at: marker) }
    }
}
