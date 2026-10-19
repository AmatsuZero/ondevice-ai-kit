import Foundation
import ODAIKitCore
import ODAIKitModelStore

/// Pins a verified local asset revision around a separately supplied runtime.
/// No runtime, weights, threshold, or recognition capability is supplied by this adapter.
/// A successful open transfers runtime ownership; do not reuse or mutate it afterward.
public final class LocalIdentityBackend: IdentityBackend, @unchecked Sendable {
    public let backendId: String
    public let backendVersion: String
    public let modelBinding: ModelBinding
    public let embeddingSpace: EmbeddingSpaceId
    private let runtime: any IdentityBackend
    private let lease: ModelLease
    private let lock = NSLock()
    private var closed = false
    private var runtimeClosed = false
    private var inFlight = 0
    private var releasing = false
    private var released = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    private init(runtime: any IdentityBackend, lease: ModelLease, space: EmbeddingSpaceId) {
        self.runtime = runtime; self.lease = lease; embeddingSpace = space
        backendId = space.backendId; backendVersion = space.backendVersion; modelBinding = space.modelBinding
    }

    public static func open(modelStore: ModelStore, modelRef: ModelRef, runtime: any IdentityBackend) async throws -> LocalIdentityBackend {
        let space = runtime.embeddingSpace
        let mismatch = IdentityError(.embeddingSpaceMismatch, backendId: space.backendId, modelBinding: space.modelBinding)
        guard space.modelBinding.kind == .model, space.modelBinding.id == modelRef.modelId, space.modelBinding.revision == modelRef.revision,
              runtime.backendId == space.backendId, runtime.backendVersion == space.backendVersion, runtime.modelBinding == space.modelBinding else { throw mismatch }
        let lease: ModelLease
        do { lease = try await modelStore.acquire(modelRef) }
        catch { throw IdentityError(.backendUnavailable, backendId: space.backendId, modelBinding: space.modelBinding) }
        do {
            guard lease.manifest.ref == modelRef, lease.manifest.backendId == space.backendId,
                  Self.isVersion(space.backendVersion, in: lease.manifest.backendVersion),
                  lease.manifest.capability == "face-identity", lease.manifest.processing.preprocessRevision == space.preprocessRevision else { throw mismatch }
            let backend = LocalIdentityBackend(runtime: runtime, lease: lease, space: space)
            try backend.checkBinding()
            guard runtime.availability().status == .available else { throw backend.error(.backendUnavailable) }
            try backend.checkBinding()
            return backend
        } catch {
            await lease.close()
            throw error
        }
    }

    private static func isVersion(_ value: String, in range: VersionRange) -> Bool {
        func parse(_ raw: String) -> [Int]? {
            let parts = raw.split(separator: ".", omittingEmptySubsequences: false)
            guard !parts.isEmpty, parts.allSatisfy({ !$0.isEmpty && $0.utf8.allSatisfy { (48...57).contains($0) } }) else { return nil }
            let values = parts.compactMap { Int($0) }
            return values.count == parts.count ? values : nil
        }
        func compare(_ lhs: [Int], _ rhs: [Int]) -> Int {
            for index in 0..<max(lhs.count, rhs.count) {
                let a = index < lhs.count ? lhs[index] : 0; let b = index < rhs.count ? rhs[index] : 0
                if a != b { return a < b ? -1 : 1 }
            }
            return 0
        }
        guard let current = parse(value), let minimum = parse(range.minimum), let maximum = parse(range.maximumExclusive) else { return false }
        return compare(current, minimum) >= 0 && compare(current, maximum) < 0
    }

    private func error(_ code: IdentityErrorCode) -> IdentityError { .init(code, backendId: backendId, modelBinding: modelBinding) }
    private func checkBinding() throws {
        guard runtime.backendId == backendId, runtime.backendVersion == backendVersion,
              runtime.modelBinding == modelBinding, runtime.embeddingSpace == embeddingSpace else { throw error(.embeddingSpaceMismatch) }
    }
    private func checkOpen() throws {
        lock.lock(); let value = closed; lock.unlock()
        if value { throw error(.closed) }
    }
    private func begin() throws {
        lock.lock(); defer { lock.unlock() }
        guard !closed else { throw error(.closed) }
        inFlight += 1
    }
    private func end() {
        lock.lock(); inFlight -= 1; lock.unlock()
        releaseIfDrained()
    }
    private func perform<T>(_ body: () throws -> T) throws -> T {
        try begin(); defer { end() }
        try checkBinding()
        let value = try body()
        try checkOpen(); try checkBinding()
        return value
    }

    public func availability() -> CapabilityAvailability {
        do {
            return try perform {
                let status = runtime.availability()
                guard status.backendId == backendId else { throw error(.embeddingSpaceMismatch) }
                return status
            }
        } catch {
            lock.lock(); let isClosed = closed; lock.unlock()
            return .init(status: .unavailable, reason: isClosed ? .closed : .missingAssets, backendId: backendId)
        }
    }
    public func embed(_ probe: IdentityProbe) throws -> IdentityEmbedding {
        try perform { try validate(runtime.embed(probe)) }
    }
    public func embed(_ probe: IdentityProbe, stopSignal: IdentityStopSignal) throws -> IdentityEmbedding {
        try perform { try withStop(stopSignal) { try validate(runtime.embed(probe, stopSignal: stopSignal)) } }
    }
    private func validate(_ embedding: IdentityEmbedding) throws -> IdentityEmbedding {
        guard embedding.embeddingSpace == embeddingSpace else { throw error(.embeddingSpaceMismatch) }
        return embedding
    }
    public func evaluateQuality(_ probe: IdentityProbe) throws -> IdentityQuality { try perform { try runtime.evaluateQuality(probe) } }
    public func evaluateQuality(_ probe: IdentityProbe, stopSignal: IdentityStopSignal) throws -> IdentityQuality {
        try perform { try withStop(stopSignal) { try runtime.evaluateQuality(probe, stopSignal: stopSignal) } }
    }
    private func withStop<T>(_ signal: IdentityStopSignal, _ body: () throws -> T) throws -> T {
        try signal.check()
        do { let value = try body(); try signal.check(); return value }
        catch { try signal.check(); throw error }
    }

    /// Stops admission immediately and requests runtime stop. The lease survives all active calls.
    public func close() {
        lock.lock()
        guard !closed else { lock.unlock(); return }
        closed = true; lock.unlock()
        runtime.close()
        lock.lock(); runtimeClosed = true; lock.unlock()
        releaseIfDrained()
    }
    private func releaseIfDrained() {
        lock.lock()
        guard runtimeClosed, inFlight == 0, !releasing else { lock.unlock(); return }
        releasing = true; lock.unlock()
        Task {
            await lease.close()
            finishRelease()
        }
    }
    private func finishRelease() {
        lock.lock(); released = true; let pending = waiters; waiters.removeAll(); lock.unlock()
        for waiter in pending { waiter.resume() }
    }
    /// Join this after engine shutdown before removing the model revision.
    public func closeAndWait() async {
        close()
        await withCheckedContinuation { continuation in
            lock.lock()
            if released { lock.unlock(); continuation.resume() }
            else { waiters.append(continuation); lock.unlock() }
        }
    }
}
