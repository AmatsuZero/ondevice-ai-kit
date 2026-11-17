import Foundation

/// One offline operation per client. A backend instance should belong to one client.
/// The supplied output directory must already exist and be SDK-owned. Do not mutate
/// its private operation subdirectories. Delivered files remain valid across close.
public actor VoiceEffectsClient {
    private struct Prepared: Sendable {
        let output: VoiceEffectsOutput
        let workspace: VoiceEffectsWorkspace
    }
    private struct WorkResult: @unchecked Sendable {
        let result: Result<Prepared, Error>
        let cleanupDebt: VoiceEffectsWorkspace?
    }
    private struct Operation {
        let task: Task<WorkResult, Never>
        let decision: VoiceEffectsDecision
    }
    private let backend: any VoiceEffectsBackend
    private let root: URL
    private let fileIO: VoiceEffectsFileIO
    private var active: Operation?
    private var closed = false
    // Internal read access supports observation of actual drain admission in tests.
    private(set) var waiters: [CheckedContinuation<Void, Never>] = []
    private var cleanupDebt: VoiceEffectsWorkspace?
    private var cleanupTask: Task<Void, Never>?
    private var outputs: [String: VoiceEffectsOutputLease] = [:]
    private var released: Set<String> = []

    public init(backend: any VoiceEffectsBackend, outputDirectory: URL) throws {
        try self.init(backend: backend, outputDirectory: outputDirectory, fileIO: .live)
    }

    init(backend: any VoiceEffectsBackend, outputDirectory: URL, fileIO: VoiceEffectsFileIO) throws {
        guard outputDirectory.isFileURL,
              outputDirectory.host == nil || outputDirectory.host == "" || outputDirectory.host == "localhost",
              !outputDirectory.path.utf8.contains(0) else {
            throw VoiceEffectsError.invalidInput(.outputDirectory)
        }
        guard backend.backendId == "apple.time-pitch" else { throw VoiceEffectsError.backendUnavailable }
        self.backend = backend; root = outputDirectory; self.fileIO = fileIO
    }

    public func process(_ request: VoiceEffectsRequest) async throws -> VoiceEffectsOutput {
        guard !closed else { throw VoiceEffectsError.closed }
        guard active == nil, cleanupDebt == nil else { throw VoiceEffectsError.busy }
        try Task.checkCancellation()
        let decision = VoiceEffectsDecision()
        let backend = self.backend, root = self.root, io = self.fileIO
        let task = Task.detached {
            Self.run(request, backend: backend, root: root, io: io, decision: decision)
        }
        active = Operation(task: task, decision: decision)
        defer { finish() }
        return try await withTaskCancellationHandler {
            let outcome = await task.value
            switch outcome.result {
            case .failure(let error):
                cleanupDebt = outcome.cleanupDebt
                throw voiceEffectsSanitize(error)
            case .success(let prepared):
                // No suspension from commit through registration and return. Whichever
                // acquires the decision lock first owns the single terminal result.
                if decision.commit() {
                    outputs[prepared.output.id] = VoiceEffectsOutputLease(prepared.workspace)
                    return prepared.output
                }
                let failure = await Task.detached { () -> Error? in
                    do { try prepared.workspace.cleanup(); return nil }
                    catch { return voiceEffectsSanitize(error) }
                }.value
                if let failure { cleanupDebt = prepared.workspace; throw failure }
                throw CancellationError()
            }
        } onCancel: {
            // Synchronous acceptance; no actor-hop that can arrive after delivery.
            decision.cancel()
        }
    }

    private nonisolated static func run(_ request: VoiceEffectsRequest, backend: any VoiceEffectsBackend,
                                        root: URL, io: VoiceEffectsFileIO,
                                        decision: VoiceEffectsDecision) -> WorkResult {
        let workspace = VoiceEffectsWorkspace(root: root, io: io)
        do {
            let wave = try VoiceEffectsWAV.read(request.inputFile, check: decision.check)
            try decision.check()
            let bytes: Data
            if request.preset == .original { bytes = wave.bytes }
            else {
                let pcm = try autoreleasepool {
                    try backend.render(wave.input, preset: request.preset, checkCancellation: { try decision.check() })
                }
                try decision.check()
                bytes = try wave.replacingPCM(pcm)
            }
            try workspace.writeAndPublish(bytes, check: decision.check)
            let output = VoiceEffectsOutput(id: UUID().uuidString, audioFile: workspace.final,
                                            backendId: backend.backendId, sampleRate: wave.input.sampleRate,
                                            frameCount: wave.input.frameCount)
            return .init(result: .success(.init(output: output, workspace: workspace)), cleanupDebt: nil)
        } catch {
            let original = error
            do {
                try workspace.cleanup()
                return .init(result: .failure(voiceEffectsSanitize(original)), cleanupDebt: nil)
            } catch {
                // A failed cleanup must never be reported as completed cancellation.
                return .init(result: .failure(voiceEffectsSanitize(error)), cleanupDebt: workspace)
            }
        }
    }

    /// Waits for actual worker exit, closed input handles, and all undelivered-file
    /// cleanup. Nonthrowing drain/close cannot declare success on persistent I/O
    /// denial: they continue waiting, with a bounded retry cadence, until repaired.
    public func drain() async {
        if active != nil { await withCheckedContinuation { waiters.append($0) } }
        if let debt = cleanupDebt {
            let task: Task<Void, Never>
            if let cleanupTask { task = cleanupTask }
            else {
                task = Task.detached {
                    while true {
                        do { try debt.cleanup(); return }
                        catch { try? await Task.sleep(nanoseconds: 50_000_000) }
                    }
                }
                cleanupTask = task
            }
            await task.value
            if cleanupDebt === debt { cleanupDebt = nil; cleanupTask = nil }
        }
    }

    public func close() async {
        closed = true
        active?.decision.cancel()
        // All concurrent/repeated callers join the same active slot/cleanup task.
        // Caller cancellation cannot shortcut this resource completion boundary.
        await drain()
    }

    public func releaseOutput(id: String) async throws {
        if released.contains(id) { return }
        guard let lease = outputs[id] else { throw VoiceEffectsError.invalidInput(.unknownOutput) }
        try await lease.release()
        outputs[id] = nil; released.insert(id)
    }

    private func finish() {
        active = nil
        let ready = waiters; waiters.removeAll()
        ready.forEach { $0.resume() }
    }
}
