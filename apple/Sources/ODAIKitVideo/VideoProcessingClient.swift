import Foundation
import ODAIKitCore

/// Coordinates one processing operation, including admission and cancellation drain.
public actor VideoProcessingClient {
    private struct Operation {
        let id: UUID
        let task: Task<VideoProcessingResult, Error>
        let progress: (@Sendable (VideoProcessingProgress) -> Void)?
        var completedFrames = -1
    }

    private let defaultBackendId: String
    private let backends: [String: any VideoSuperResolutionBackend]
    private var active: Operation?
    private var idleWaiters: [CheckedContinuation<Void, Never>] = []
    private var queryCount = 0
    private var queryWaiters: [CheckedContinuation<Void, Never>] = []
    private var closed = false
    private var closeTask: Task<Void, Never>?

    public init(defaultBackendId: String, backends: [any VideoSuperResolutionBackend]) throws {
        var registered: [String: any VideoSuperResolutionBackend] = [:]
        for backend in backends {
            guard !backend.backendId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  registered[backend.backendId] == nil else {
                throw VideoClientError.invalidRegistration("empty or duplicate backend ID")
            }
            registered[backend.backendId] = backend
        }
        guard registered[defaultBackendId] != nil else {
            throw VideoClientError.invalidRegistration("default backend is not registered")
        }
        self.defaultBackendId = defaultBackendId
        self.backends = registered
    }

    public func listBackends() async throws -> [VideoBackendDescriptor] {
        guard !closed else { throw VideoClientError.closed }
        queryCount += 1
        defer { finishQuery() }
        var descriptors: [VideoBackendDescriptor] = []
        for id in backends.keys.sorted() {
            let backend = backends[id]!
            let availability = await backend.availability()
            guard !closed else { throw VideoClientError.closed }
            try Task.checkCancellation()
            descriptors.append(.init(backendId: id, availability: availability,
                                     supportedScaleFactors: backend.supportedScaleFactors,
                                     supportedAudioPolicies: backend.supportedAudioPolicies))
        }
        return descriptors
    }

    public func process(_ request: VideoProcessingRequest, backend selection: VideoBackendSelection = .default,
                        progress: (@Sendable (VideoProcessingProgress) -> Void)? = nil) async throws -> VideoProcessingResult {
        guard !closed else { throw VideoClientError.closed }
        guard active == nil else { throw VideoClientError.busy }
        try Task.checkCancellation()
        let id: String
        switch selection {
        case .default: id = defaultBackendId
        case .explicit(let explicit): id = explicit
        }
        guard let backend = backends[id] else { throw VideoClientError.backendNotRegistered(id) }
        let generation = UUID()
        let task = Task { try await self.run(request, backend: backend, generation: generation) }
        active = Operation(id: generation, task: task, progress: progress)
        defer { finishOperation() }

        return try await withTaskCancellationHandler {
            let result = try await task.value
            // The driver may finish immediately before caller cancellation/close. Ownership
            // transfers only here; close waits for this cleanup through the active slot.
            if Task.isCancelled || task.isCancelled || closed {
                try await Task { try await result.output.release() }.value
                throw CancellationError()
            }
            return result
        } onCancel: {
            task.cancel()
        }
    }

    private func run(_ request: VideoProcessingRequest, backend: any VideoSuperResolutionBackend,
                     generation: UUID) async throws -> VideoProcessingResult {
        let availability = await backend.availability()
        try Task.checkCancellation()
        guard availability.status == .available else {
            throw VideoClientError.backendUnavailable(backend.backendId, availability.reason ?? .backendNotLinked)
        }
        guard backend.supportedScaleFactors.contains(request.scaleFactor) else {
            throw VideoClientError.unsupportedScaleFactor(request.scaleFactor)
        }
        guard backend.supportedAudioPolicies.contains(request.audioPolicy) else {
            throw VideoClientError.unsupportedAudioPolicy(request.audioPolicy)
        }
        let outcome: Result<any VideoOutputLease, Error>
        do {
            let output = try await backend.process(request, progress: { [weak self] update in
                Task { await self?.publish(update, generation: generation) }
            })
            outcome = .success(output)
        } catch { outcome = .failure(error) }
        // An unstructured task has its own cancellation state: drain is mandatory even
        // when the processing driver was cancelled or the backend threw.
        await Task { await backend.drain() }.value
        return VideoProcessingResult(backendId: backend.backendId, output: try outcome.get())
    }

    private func publish(_ update: VideoProcessingProgress, generation: UUID) {
        guard !closed, var operation = active, operation.id == generation,
              !operation.task.isCancelled, update.completedFrames >= operation.completedFrames else { return }
        operation.completedFrames = update.completedFrames
        active = operation
        operation.progress?(update)
    }

    public func drain() async {
        if active != nil { await withCheckedContinuation { idleWaiters.append($0) } }
    }

    public func close() async {
        if let closeTask { await closeTask.value; return }
        closed = true
        active?.task.cancel()
        let task = Task {
            await self.drain()
            await self.joinQueries()
            for id in self.backends.keys.sorted() { await self.backends[id]!.close() }
        }
        closeTask = task
        await task.value
    }

    private func finishOperation() {
        active = nil
        let waiters = idleWaiters
        idleWaiters.removeAll()
        waiters.forEach { $0.resume() }
    }

    private func finishQuery() {
        queryCount -= 1
        if queryCount == 0 {
            let waiters = queryWaiters
            queryWaiters.removeAll()
            waiters.forEach { $0.resume() }
        }
    }

    private func joinQueries() async {
        if queryCount != 0 { await withCheckedContinuation { queryWaiters.append($0) } }
    }
}
