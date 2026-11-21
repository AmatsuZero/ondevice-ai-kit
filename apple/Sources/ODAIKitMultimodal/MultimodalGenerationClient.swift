import Foundation
import ODAIKitCore

/// Serializes backend admission and owns the registered backends.
public actor MultimodalGenerationClient {
    private enum Request: Sendable {
        case image(MultimodalRequest)
        case video(MultimodalVideoRequest)
    }

    private struct Operation {
        let id: UUID
        let task: Task<MultimodalTextOutput, Error>
        let update: (@Sendable (MultimodalTextUpdate) -> Void)?
        var lastSequence = -1
    }

    private let defaultBackendId: String
    private let backends: [String: any MultimodalGenerationBackend]
    private var active: Operation?
    private var idleWaiters: [CheckedContinuation<Void, Never>] = []
    private var queryCount = 0
    private var queryWaiters: [CheckedContinuation<Void, Never>] = []
    private var closed = false
    private var closeTask: Task<Void, Never>?

    public init(defaultBackendId: String,
                backends: [any MultimodalGenerationBackend]) throws {
        var registered: [String: any MultimodalGenerationBackend] = [:]
        for backend in backends {
            let id = backend.backendId
            guard !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  registered[id] == nil else {
                throw MultimodalClientError.invalidRegistration("empty or duplicate backend ID")
            }
            registered[id] = backend
        }
        guard !registered.isEmpty, registered[defaultBackendId] != nil else {
            throw MultimodalClientError.invalidRegistration("default backend is not registered")
        }
        self.defaultBackendId = defaultBackendId
        self.backends = registered
    }

    public func listBackends() async throws -> [MultimodalBackendDescriptor] {
        guard !closed else { throw MultimodalClientError.closed }
        queryCount += 1
        defer { finishQuery() }
        var descriptors: [MultimodalBackendDescriptor] = []
        for id in backends.keys.sorted() {
            let availability = await backends[id]!.availability()
            try Task.checkCancellation()
            guard !closed else { throw MultimodalClientError.closed }
            descriptors.append(.init(backendId: id, availability: availability))
        }
        return descriptors
    }

    public func generate(
        _ request: MultimodalRequest,
        backend selection: MultimodalBackendSelection = .default,
        update: (@Sendable (MultimodalTextUpdate) -> Void)? = nil
    ) async throws -> MultimodalGenerationResult {
        try await generate(.image(request), backend: selection, update: update)
    }

    public func generate(
        _ request: MultimodalVideoRequest,
        backend selection: MultimodalBackendSelection = .default,
        update: (@Sendable (MultimodalTextUpdate) -> Void)? = nil
    ) async throws -> MultimodalGenerationResult {
        try await generate(.video(request), backend: selection, update: update)
    }

    private func generate(
        _ request: Request,
        backend selection: MultimodalBackendSelection,
        update: (@Sendable (MultimodalTextUpdate) -> Void)?
    ) async throws -> MultimodalGenerationResult {
        guard !closed else { throw MultimodalClientError.closed }
        guard active == nil else { throw MultimodalClientError.busy }
        try Task.checkCancellation()

        let id: String
        switch selection {
        case .default: id = defaultBackendId
        case .explicit(let explicit): id = explicit
        }
        guard let backend = backends[id] else {
            throw MultimodalClientError.backendNotRegistered(id)
        }
        if case .video = request, !(backend is any MultimodalVideoGenerationBackend) {
            throw MultimodalClientError.invalidInput("selected backend does not support video")
        }

        let operationId = UUID()
        let task = Task { try await self.run(request, backend: backend, operationId: operationId) }
        active = Operation(id: operationId, task: task, update: update)
        defer { finishOperation() }

        return try await withTaskCancellationHandler {
            do {
                let output = try await task.value
                if Task.isCancelled || task.isCancelled || closed {
                    throw CancellationError()
                }
                return try MultimodalGenerationResult(backendId: backend.backendId, output: output)
            } catch {
                if Task.isCancelled || task.isCancelled || closed {
                    throw CancellationError()
                }
                throw error
            }
        } onCancel: {
            task.cancel()
        }
    }

    private func run(
        _ request: Request,
        backend: any MultimodalGenerationBackend,
        operationId: UUID
    ) async throws -> MultimodalTextOutput {
        let availability = await backend.availability()
        try Task.checkCancellation()
        guard availability.status == .available else {
            throw MultimodalClientError.backendUnavailable(
                backend.backendId, availability.reason ?? .backendNotLinked)
        }

        let outcome: Result<MultimodalTextOutput, Error>
        do {
            let update: @Sendable (MultimodalTextUpdate) -> Void = { [weak self] value in
                Task { await self?.publish(value, operationId: operationId) }
            }
            let output: MultimodalTextOutput
            switch request {
            case .image(let image):
                output = try await backend.generate(image, update: update)
            case .video(let video):
                guard let videoBackend = backend as? any MultimodalVideoGenerationBackend else {
                    throw MultimodalClientError.invalidInput("selected backend does not support video")
                }
                output = try await videoBackend.generateVideo(video, update: update)
            }
            outcome = .success(output)
        } catch {
            outcome = .failure(error)
        }

        await Task { await backend.drain() }.value
        return try outcome.get()
    }

    private func publish(_ value: MultimodalTextUpdate, operationId: UUID) {
        guard !closed, var operation = active, operation.id == operationId,
              !operation.task.isCancelled, value.sequence > operation.lastSequence else { return }
        operation.lastSequence = value.sequence
        active = operation
        operation.update?(value)
    }

    public func drain() async {
        if active != nil {
            await withCheckedContinuation { idleWaiters.append($0) }
        }
    }

    public func close() async {
        if let closeTask {
            await closeTask.value
            return
        }

        closed = true
        active?.task.cancel()
        let task = Task {
            await self.drain()
            await self.joinQueries()
            for id in self.backends.keys.sorted() {
                await self.backends[id]!.close()
            }
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
        if queryCount != 0 {
            await withCheckedContinuation { queryWaiters.append($0) }
        }
    }
}
