import Foundation

/// Client-owned coordination around one native microphone task. It consumes the
/// native result exactly once, drains the backend before publishing a terminal,
/// and shares that terminal with every result waiter.
internal actor SpeechMicrophoneTaskCoordinator {
    private let native: any SpeechMicrophoneRecognitionTask
    private let drain: @Sendable () async -> Void
    private let onTerminal: @Sendable () async -> Void
    private var outcome: Result<SpeechRecognitionResult, Error>?
    private var resultWaiters: [CheckedContinuation<SpeechRecognitionResult, Error>] = []
    private var terminalWaiters: [CheckedContinuation<Void, Never>] = []
    private var cancellationRequested = false
    private var finishRequested = false
    private var terminalPrepared = false
    private var driver: Task<Void, Never>?

    init(native: any SpeechMicrophoneRecognitionTask,
                 drain: @escaping @Sendable () async -> Void,
                 onTerminal: @escaping @Sendable () async -> Void) {
        self.native = native; self.drain = drain; self.onTerminal = onTerminal
    }

    func startDriver() {
        guard driver == nil, outcome == nil else { return }
        driver = Task { [weak self] in await self?.resolve() }
    }

    private func resolve() async {
        let nativeOutcome: Result<SpeechRecognitionResult, Error>
        do { nativeOutcome = .success(try await native.result()) }
        catch { nativeOutcome = .failure(error) }
        await drain()
        terminalPrepared = true
        let terminal: Result<SpeechRecognitionResult, Error>
        if cancellationRequested { terminal = .failure(CancellationError()) }
        else { terminal = nativeOutcome }
        await onTerminal()
        settle(terminal)
    }

    func result() async throws -> SpeechRecognitionResult {
        startDriver()
        if let outcome { return try outcome.get() }
        return try await withCheckedThrowingContinuation { resultWaiters.append($0) }
    }

    func finishInput() async {
        guard !terminalPrepared, outcome == nil, !cancellationRequested, !finishRequested else { return }
        finishRequested = true
        await native.finishInput()
    }

    func cancel() async {
        guard !terminalPrepared, outcome == nil, !cancellationRequested else { return }
        cancellationRequested = true
        await native.cancel()
    }

    func waitForTerminal() async {
        startDriver()
        guard outcome == nil else { return }
        await withCheckedContinuation { terminalWaiters.append($0) }
    }

    private func settle(_ value: Result<SpeechRecognitionResult, Error>) {
        guard outcome == nil else { return }
        outcome = value
        let resultWaiters = self.resultWaiters
        self.resultWaiters.removeAll()
        for waiter in resultWaiters { waiter.resume(with: value) }
        let terminalWaiters = self.terminalWaiters
        self.terminalWaiters.removeAll()
        for waiter in terminalWaiters { waiter.resume() }
        driver = nil
    }
}

internal final class SpeechMicrophoneTaskHandle: SpeechMicrophoneRecognitionTask {
    private let coordinator: SpeechMicrophoneTaskCoordinator
    init(coordinator: SpeechMicrophoneTaskCoordinator) { self.coordinator = coordinator }
    func result() async throws -> SpeechRecognitionResult { try await coordinator.result() }
    func finishInput() async { await coordinator.finishInput() }
    func cancel() async { await coordinator.cancel() }
    func waitForTerminal() async { await coordinator.waitForTerminal() }
}
