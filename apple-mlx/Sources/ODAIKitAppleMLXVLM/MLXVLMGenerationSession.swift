import Foundation
import ODAIKitCore
import ODAIKitModelStore
import ODAIKitMultimodal

/// A runtime must not return until its model/preprocessing/generation work has actually stopped.
protocol MLXVLMRuntime: Sendable {
    func generateVideo(directory: URL, request: MultimodalVideoRequest,
                       observer: (@Sendable (MLXVLMGenerationEvent) -> Void)?,
                       update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> String
    func generate(directory: URL, request: MultimodalRequest,
                  observer: (@Sendable (MLXVLMGenerationEvent) -> Void)?,
                  update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> String
}

actor MLXVLMGenerationSession {
    private enum Request: Sendable {
        case image(MultimodalRequest)
        case video(MultimodalVideoRequest)
        var prompt: String { switch self { case .image(let r): r.prompt; case .video(let r): r.prompt } }
        var maxOutputTokens: Int { switch self { case .image(let r): r.maxOutputTokens; case .video(let r): r.maxOutputTokens } }
    }
    private let lease: ModelLease
    private let runtime: any MLXVLMRuntime
    private let identity: MultimodalModelIdentity
    private var closed = false
    private var active: Task<String, Error>?
    private var relay: MLXVLMUpdateRelay?
    private var closeTask: Task<Void, Never>?
    private var waiters: [CheckedContinuation<Void, Never>] = []
    private let observer: (@Sendable (MLXVLMGenerationEvent) -> Void)?

    init(lease: ModelLease, runtime: any MLXVLMRuntime, identity: MultimodalModelIdentity) {
        self.init(lease: lease, runtime: runtime, identity: identity, observer: nil)
    }

    init(lease: ModelLease, runtime: any MLXVLMRuntime, identity: MultimodalModelIdentity,
         observer: (@Sendable (MLXVLMGenerationEvent) -> Void)?) {
        self.lease = lease
        self.runtime = runtime
        self.identity = identity
        self.observer = observer
    }

    func availability(supported: Bool) -> CapabilityAvailability {
        .init(status: closed || !supported ? .unavailable : .available,
              reason: closed ? .closed : (supported ? nil : .unsupportedDevice),
              backendId: SmolVLM2Model.backendId, minOS: "iOS 18.0")
    }

    func generate(_ request: MultimodalRequest,
                  update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> MultimodalTextOutput {
        try await generate(.image(request), update: update)
    }

    func generateVideo(_ request: MultimodalVideoRequest,
                       update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> MultimodalTextOutput {
        try await generate(.video(request), update: update)
    }

    private func generate(_ request: Request,
                          update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> MultimodalTextOutput {
        guard !closed else { throw MultimodalClientError.closed }
        guard active == nil else { throw MultimodalClientError.busy }
        try Task.checkCancellation()
        guard request.prompt.utf8.count <= 8192, request.maxOutputTokens <= 512 else {
            throw MultimodalClientError.invalidInput("request exceeds MLXVLM budget")
        }
        if case .video = request,
           ["<image>", "<fake_token_around_image>", "<global-img>"].contains(where: request.prompt.contains) {
            throw MultimodalClientError.invalidInput("video prompt contains reserved media tokens")
        }
        let runtime = runtime, directory = lease.directory
        let relay = MLXVLMUpdateRelay(update)
        let task = Task {
            try Task.checkCancellation()
            switch request {
            case .image(let image):
                return try await runtime.generate(directory: directory, request: image, observer: observer,
                                                  update: { relay.publish($0) })
            case .video(let video):
                return try await runtime.generateVideo(directory: directory, request: video, observer: observer,
                                                       update: { relay.publish($0) })
            }
        }
        self.relay = relay
        active = task
        defer {
            relay.stop()
            self.relay = nil
            active = nil
            let pending = waiters
            waiters.removeAll()
            pending.forEach { $0.resume() }
        }
        return try await withTaskCancellationHandler {
            do {
                let text = try await task.value
                if Task.isCancelled || task.isCancelled || closed { throw CancellationError() }
                guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                    throw MLXVLMBackendError.noResult
                }
                return try MultimodalTextOutput(text: text, model: identity)
            } catch {
                if Task.isCancelled || task.isCancelled || closed { throw CancellationError() }
                throw error
            }
        } onCancel: { relay.stop(); task.cancel() }
    }

    func drain() async {
        if active != nil { await withCheckedContinuation { waiters.append($0) } }
    }

    func close() async {
        if let closeTask { await closeTask.value; return }
        closed = true
        relay?.stop()
        active?.cancel()
        let lease = lease
        let task = Task {
            await self.drain()
            await lease.close()
        }
        closeTask = task
        await task.value
    }
}

/// Serializes delivery with cancellation, including synchronous cancellation from a callback.
private final class MLXVLMUpdateRelay: @unchecked Sendable {
    private let lock = NSRecursiveLock()
    private var callback: (@Sendable (MultimodalTextUpdate) -> Void)?
    private var lastSequence = -1
    init(_ callback: (@Sendable (MultimodalTextUpdate) -> Void)?) { self.callback = callback }
    func publish(_ value: MultimodalTextUpdate) {
        lock.lock(); defer { lock.unlock() }
        guard value.sequence > lastSequence else { return }
        lastSequence = value.sequence
        callback?(value)
    }
    func stop() { lock.lock(); defer { lock.unlock() }; callback = nil }
}
