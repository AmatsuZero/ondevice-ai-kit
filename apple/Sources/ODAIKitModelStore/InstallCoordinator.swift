import Foundation
public struct InstalledModel: Sendable {
    public let ref: ModelRef
}
public actor InstallCoordinator {
    private struct Waiter {
        let continuation: CheckedContinuation<InstalledModel, Error>
        let progress: @Sendable (Int64) -> Void
    }
    private final class Job {
        let fingerprint: Data
        var waiters: [UUID: Waiter] = [:]
        var cancelled: [UUID: Waiter] = [:]
        var worker: Task<Void, Never>?
        var bytes: Int64 = 0
        init(fingerprint: Data) { self.fingerprint = fingerprint }
    }
    private let store: ModelStore
    private let downloadRoot: URL
    private let transport: any ModelDownloadTransport
    private let freeBytes: @Sendable (URL) throws -> Int64
    private var jobs: [ModelRef: Job] = [:]
    public init(store: ModelStore, downloadRoot: URL, transport: any ModelDownloadTransport, freeBytes: @escaping @Sendable (URL) throws -> Int64 = { url in
        (try FileManager.default.attributesOfFileSystem(forPath: url.path)[.systemFreeSize] as? NSNumber)?.int64Value ?? 0
    }) {
        self.store = store; self.downloadRoot = downloadRoot; self.transport = transport; self.freeBytes = freeBytes
    }
    public func status(_ ref: ModelRef) async -> ModelStatus { if jobs[ref] != nil { return .installing }; return await store.status(ref) }
    public func install(manifest: ModelManifest, observerId: UUID = UUID(), progress: @escaping @Sendable (Int64) -> Void = { _ in }) async throws -> InstalledModel {
        try await store.validate(manifest)
        let encoder = JSONEncoder(); encoder.outputFormatting = .sortedKeys
        let fingerprint = try encoder.encode(manifest)
        try Task.checkCancellation()
        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                if Task.isCancelled { continuation.resume(throwing: CancellationError()); return }
                if jobs.values.contains(where: { $0.waiters[observerId] != nil || $0.cancelled[observerId] != nil }) {
                    continuation.resume(throwing: ModelStoreError.inUse); return
                }
                let job: Job
                if let existing = jobs[manifest.ref] {
                    guard existing.fingerprint == fingerprint, existing.worker?.isCancelled != true else { continuation.resume(throwing: ModelStoreError.inUse); return }
                    job = existing
                } else { job = Job(fingerprint: fingerprint); jobs[manifest.ref] = job }
                job.waiters[observerId] = Waiter(continuation: continuation, progress: progress)
                progress(job.bytes)
                if job.worker == nil { job.worker = Task { await self.perform(manifest: manifest) } }
            }
        }, onCancel: { Task { await self.cancel(observerId: observerId) } })
    }
    public func cancel(observerId: UUID) {
        guard let job = jobs.values.first(where: { $0.waiters[observerId] != nil }), let waiter = job.waiters.removeValue(forKey: observerId) else { return }
        if job.waiters.isEmpty { job.cancelled[observerId] = waiter; job.worker?.cancel() }
        else { waiter.continuation.resume(throwing: CancellationError()) }
    }
    private func publish(_ ref: ModelRef, bytes: Int64) {
        guard let job = jobs[ref] else { return }; job.bytes = max(bytes, job.bytes)
        for waiter in job.waiters.values { waiter.progress(job.bytes) }
    }
    private func perform(manifest: ModelManifest) async {
        let directory = downloadRoot.appendingPathComponent(UUID().uuidString)
        let outcome: Result<InstalledModel, Error>
        do {
            try FileManager.default.createDirectory(at: downloadRoot, withIntermediateDirectories: true)
            let required = manifest.resources.downloadBytes.multipliedReportingOverflow(by: 2)
            guard !required.overflow, try freeBytes(downloadRoot) >= max(required.partialValue, manifest.resources.temporaryBytes) else { throw ModelStoreError.diskSpace }
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var completed: Int64 = 0
            for file in manifest.files {
                try Task.checkCancellation()
                let target = directory.appendingPathComponent(file.path)
                try FileManager.default.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
                let base = completed
                try await transport.download(file: file, to: target, progress: { bytes in Task { await self.publish(manifest.ref, bytes: base + min(bytes, file.bytes)) } })
                completed += file.bytes
            }
            try Task.checkCancellation()
            try await store.install(manifest: manifest, source: directory)
            outcome = .success(InstalledModel(ref: manifest.ref))
        } catch { outcome = .failure(error) }
        // Last cancellation is not delivered until all transport I/O and owned cleanup have stopped.
        try? FileManager.default.removeItem(at: directory)
        guard let job = jobs.removeValue(forKey: manifest.ref) else { return }
        for waiter in job.waiters.values { waiter.continuation.resume(with: outcome) }
        for waiter in job.cancelled.values { waiter.continuation.resume(throwing: CancellationError()) }
    }
}
