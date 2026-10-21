import Foundation
import ODAIKitCore
import ODAIKitVideo

/// Internal boundary around the platform operation. Each call owns its resources until return.
protocol AppleVideoProcessingEngine: Sendable {
    func availability() async -> CapabilityAvailability
    func process(request: VideoProcessingRequest, output: URL,
                 progress: (@Sendable (VideoProcessingProgress) -> Void)?) async throws -> VideoOutputInfo
}

/// Experimental bounded SDR profile: 4x and explicit discard only. No model downloads.
public actor AppleVideoToolboxBackend: VideoSuperResolutionBackend {
    public nonisolated let backendId = "apple.videotoolbox.sr"
    public nonisolated let supportedScaleFactors = [4]
    public nonisolated let supportedAudioPolicies: [VideoAudioPolicy] = [.discard]
    private let root: URL
    private let engine: any AppleVideoProcessingEngine
    private var active: Task<any VideoOutputLease, Error>?
    private var generation: UUID?
    private var completedFrames = -1
    private var closed = false
    private var closeTask: Task<Void, Never>?
    private var idleWaiters: [CheckedContinuation<Void, Never>] = []

    public init() {
        root = FileManager.default.temporaryDirectory
        engine = makeAppleVideoEngine()
    }

    init(root: URL, engine: any AppleVideoProcessingEngine) {
        self.root = root; self.engine = engine
    }

    public func availability() async -> CapabilityAvailability {
        guard !closed else { return closedAvailability }
        let status = await engine.availability()
        return closed ? closedAvailability : status
    }

    private var closedAvailability: CapabilityAvailability {
        .init(status: .unavailable, reason: .closed, backendId: backendId, minOS: "iOS26", offline: true)
    }

    public func process(_ request: VideoProcessingRequest,
                        progress: (@Sendable (VideoProcessingProgress) -> Void)?) async throws -> any VideoOutputLease {
        guard !closed else { throw VideoClientError.closed }
        guard active == nil else { throw VideoClientError.busy }
        guard supportedScaleFactors.contains(request.scaleFactor) else { throw VideoClientError.unsupportedScaleFactor(request.scaleFactor) }
        guard supportedAudioPolicies.contains(request.audioPolicy) else { throw VideoClientError.unsupportedAudioPolicy(request.audioPolicy) }
        try Task.checkCancellation()
        let id = UUID()
        generation = id; completedFrames = -1
        let task = Task { try await self.run(request, generation: id, progress: progress) }
        active = task
        defer {
            active = nil; generation = nil
            let waiters = idleWaiters; idleWaiters.removeAll(); waiters.forEach { $0.resume() }
        }
        return try await withTaskCancellationHandler {
            let output = try await task.value
            if Task.isCancelled || task.isCancelled || closed {
                try await Task { try await output.release() }.value
                throw CancellationError()
            }
            return output
        } onCancel: { task.cancel() }
    }

    private func run(_ request: VideoProcessingRequest, generation id: UUID,
                     progress: (@Sendable (VideoProcessingProgress) -> Void)?) async throws -> any VideoOutputLease {
        let status = await engine.availability()
        try Task.checkCancellation()
        guard status.status == .available else {
            throw VideoClientError.backendUnavailable(backendId, status.reason ?? .unsupportedDevice)
        }
        let workspace = try AppleVideoOutputWorkspace(root: root)
        do {
            let info = try await engine.process(request: request, output: workspace.file, progress: { [weak self] update in
                Task { await self?.publish(update, generation: id, callback: progress) }
            })
            return AppleVideoOutputLease(workspace: workspace, info: info)
        } catch {
            // Removal is synchronous and remains mandatory on the cancelled worker.
            try workspace.remove()
            try Task.checkCancellation()
            throw error
        }
    }

    private func publish(_ update: VideoProcessingProgress, generation id: UUID,
                         callback: (@Sendable (VideoProcessingProgress) -> Void)?) {
        guard !closed, generation == id, let active, !active.isCancelled,
              update.completedFrames >= completedFrames else { return }
        completedFrames = update.completedFrames
        callback?(update)
    }

    public func drain() async {
        if active != nil { await withCheckedContinuation { idleWaiters.append($0) } }
    }

    public func close() async {
        if let closeTask { await closeTask.value; return }
        closed = true
        active?.cancel()
        let task = Task { await self.drain() }
        closeTask = task
        await task.value
    }
}

func makeAppleVideoEngine() -> any AppleVideoProcessingEngine {
    #if os(iOS) && !targetEnvironment(simulator)
    if #available(iOS 26, *) { return VideoToolboxSREngine() }
    return UnsupportedAppleVideoEngine(reason: .requiresOSVersion)
    #else
    return UnsupportedAppleVideoEngine()
    #endif
}

private struct UnsupportedAppleVideoEngine: AppleVideoProcessingEngine {
    var reason: UnavailableReason = .unsupportedDevice
    func availability() async -> CapabilityAvailability {
        .init(status: .unavailable, reason: reason, backendId: "apple.videotoolbox.sr", minOS: "iOS26", offline: true)
    }
    func process(request: VideoProcessingRequest, output: URL,
                 progress: (@Sendable (VideoProcessingProgress) -> Void)?) async throws -> VideoOutputInfo {
        throw VideoClientError.backendUnavailable("apple.videotoolbox.sr", reason)
    }
}
