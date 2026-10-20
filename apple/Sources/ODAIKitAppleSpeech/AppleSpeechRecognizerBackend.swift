import Foundation
import Speech
import ODAIKitCore
import ODAIKitSpeech

internal enum AppleSpeechAuthorization: Sendable, Equatable {
    case notDetermined
    case denied
    case restricted
    case authorized
}

internal struct AppleSpeechMetadata: Sendable, Equatable {
    let authorization: AppleSpeechAuthorization
    let isAvailable: Bool
    let supportsOnDeviceRecognition: Bool
}

internal struct AppleSpeechSegment: Sendable, Equatable {
    let confidence: Float
    init(confidence: Float) { self.confidence = confidence }
}

internal struct AppleSpeechTaskHandle: Hashable, Sendable {
    let id: UUID
    init(id: UUID = UUID()) { self.id = id }
}

internal enum AppleSpeechTaskState: Sendable, Equatable {
    case running
    case canceling
    case completed
}

internal enum AppleSpeechPlatformEvent: Sendable, Equatable {
    case partial(text: String, segments: [AppleSpeechSegment])
    case final(text: String, segments: [AppleSpeechSegment])
    case cancelled
    case failed(domain: String, code: Int)
    case captureInvalidated(domain: String, code: Int)
    case finished
}

@MainActor
internal protocol AppleSpeechPlatformBridge: AnyObject {
    func metadata() -> AppleSpeechMetadata
    func start(fileURL: URL, requiresOnDeviceRecognition: Bool,
               callback: @escaping @Sendable (AppleSpeechPlatformEvent) -> Void) throws -> AppleSpeechTaskHandle
    func cancel(_ handle: AppleSpeechTaskHandle)
    func state(of handle: AppleSpeechTaskHandle) -> AppleSpeechTaskState
    func release(_ handle: AppleSpeechTaskHandle)
}

internal protocol AppleSpeechClock: Sendable {
    func sleep(seconds: TimeInterval) async throws
}

internal struct SystemAppleSpeechClock: AppleSpeechClock {
    func sleep(seconds: TimeInterval) async throws {
        try await Task.sleep(nanoseconds: UInt64(max(0, seconds) * 1_000_000_000))
    }
}

@MainActor
internal final class AppleSpeechPlatformBridgeImpl: AppleSpeechPlatformBridge {
    private final class Delegate: NSObject, SFSpeechRecognitionTaskDelegate {
        let callback: @Sendable (AppleSpeechPlatformEvent) -> Void
        init(callback: @escaping @Sendable (AppleSpeechPlatformEvent) -> Void) { self.callback = callback }

        func speechRecognitionTask(_ task: SFSpeechRecognitionTask,
                                   didHypothesizeTranscription transcription: SFTranscription) {
            callback(.partial(text: transcription.formattedString, segments: transcription.segments.map {
                AppleSpeechSegment(confidence: $0.confidence)
            }))
        }

        func speechRecognitionTask(_ task: SFSpeechRecognitionTask,
                                   didFinishRecognition recognitionResult: SFSpeechRecognitionResult) {
            let transcription = recognitionResult.bestTranscription
            callback(.final(text: transcription.formattedString, segments: transcription.segments.map {
                AppleSpeechSegment(confidence: $0.confidence)
            }))
        }

        func speechRecognitionTask(_ task: SFSpeechRecognitionTask,
                                   didFinishSuccessfully successfully: Bool) {
            guard !successfully else { callback(.finished); return }
            let error = task.error as NSError?
            callback(.failed(domain: error?.domain ?? "SFSpeechRecognitionTask", code: error?.code ?? -1))
        }

        func speechRecognitionTaskWasCancelled(_ task: SFSpeechRecognitionTask) {
            callback(.cancelled)
        }
    }

    private struct Entry {
        let task: SFSpeechRecognitionTask
        let delegate: Delegate
    }

    private let recognizer: SFSpeechRecognizer?
    private var entries: [AppleSpeechTaskHandle: Entry] = [:]

    init(locale: String) {
        recognizer = SFSpeechRecognizer(locale: Locale(identifier: locale))
    }

    func metadata() -> AppleSpeechMetadata {
        let authorization: AppleSpeechAuthorization
        switch SFSpeechRecognizer.authorizationStatus() {
        case .notDetermined: authorization = .notDetermined
        case .denied: authorization = .denied
        case .restricted: authorization = .restricted
        case .authorized: authorization = .authorized
        @unknown default: authorization = .restricted
        }
        return AppleSpeechMetadata(authorization: authorization,
                                   isAvailable: recognizer?.isAvailable ?? false,
                                   supportsOnDeviceRecognition: recognizer?.supportsOnDeviceRecognition ?? false)
    }

    func start(fileURL: URL, requiresOnDeviceRecognition: Bool,
               callback: @escaping @Sendable (AppleSpeechPlatformEvent) -> Void) throws -> AppleSpeechTaskHandle {
        guard let recognizer else {
            throw SpeechRecognitionError.platform(domain: "SFSpeechRecognizer", code: -1)
        }
        let request = SFSpeechURLRecognitionRequest(url: fileURL)
        request.requiresOnDeviceRecognition = requiresOnDeviceRecognition
        request.shouldReportPartialResults = true
        let delegate = Delegate(callback: callback)
        let task = recognizer.recognitionTask(with: request, delegate: delegate)
        let handle = AppleSpeechTaskHandle()
        entries[handle] = Entry(task: task, delegate: delegate)
        return handle
    }

    func cancel(_ handle: AppleSpeechTaskHandle) { entries[handle]?.task.cancel() }

    func state(of handle: AppleSpeechTaskHandle) -> AppleSpeechTaskState {
        guard let task = entries[handle]?.task else { return .completed }
        switch task.state {
        case .starting, .running, .finishing: return .running
        case .canceling: return .canceling
        case .completed: return .completed
        @unknown default: return .running
        }
    }

    func release(_ handle: AppleSpeechTaskHandle) {
        guard state(of: handle) == .completed else { return }
        entries.removeValue(forKey: handle)
    }
}

@MainActor
public final class AppleSpeechRecognizerBackend: SpeechMicrophoneRecognitionBackend {
    public nonisolated let backendId = "apple.speech.recognizer"
    public nonisolated let capability: SpeechCapability = .asr
    internal let locale: String
    internal let timeout: TimeInterval
    public nonisolated let supportedInputs: [SpeechRecognitionInputKind]

    /// The framework callback and MainActor polling can run in either order.
    /// Buffer events before scheduling an actor hop, preserving callback order.
    private final class EventInbox: @unchecked Sendable {
        private let lock = NSLock()
        private var events: [AppleSpeechPlatformEvent] = []

        func append(_ event: AppleSpeechPlatformEvent) {
            lock.lock()
            events.append(event)
            lock.unlock()
        }

        func takeEvents() -> [AppleSpeechPlatformEvent] {
            lock.lock()
            defer { lock.unlock() }
            let ready = events
            events.removeAll(keepingCapacity: false)
            return ready
        }
    }

    private final class Operation {
        let generation: UInt64
        let handle: AppleSpeechTaskHandle
        let inbox: EventInbox
        var continuation: CheckedContinuation<SpeechRecognitionResult, Error>?
        var finalResult: SpeechRecognitionResult?
        var failure: Error?
        var callerSettled = false
        var receivedFinished = false
        var cancelRequested = false
        var pollTask: Task<Void, Never>?
        var timeoutTask: Task<Void, Never>?
        var closeWaiters: [CheckedContinuation<Void, Never>] = []

        init(generation: UInt64, handle: AppleSpeechTaskHandle, inbox: EventInbox) {
            self.generation = generation
            self.handle = handle
            self.inbox = inbox
        }
    }

    @MainActor
    private final class MicrophoneOperation {
        let generation: UInt64
        let handle: AppleSpeechTaskHandle
        let inbox: EventInbox
        let lease: any AppleSpeechAudioSessionLease
        var outcome: Result<SpeechRecognitionResult, Error>?
        var resultWaiters: [CheckedContinuation<SpeechRecognitionResult, Error>] = []
        var finalResult: SpeechRecognitionResult?
        var failure: Error?
        var receivedFinished = false
        var cancellationRequested = false
        var finishRequested = false
        var nativeCancelRequested = false
        var captureStopTask: Task<Error?, Never>?
        var captureStopped = false
        var cleanupStarted = false
        var pollTask: Task<Void, Never>?
        var timeoutTask: Task<Void, Never>?
        var closeWaiters: [CheckedContinuation<Void, Never>] = []

        init(generation: UInt64, handle: AppleSpeechTaskHandle, inbox: EventInbox,
             lease: any AppleSpeechAudioSessionLease) {
            self.generation = generation
            self.handle = handle
            self.inbox = inbox
            self.lease = lease
        }

        func result() async throws -> SpeechRecognitionResult {
            if let outcome { return try outcome.get() }
            return try await withCheckedThrowingContinuation { resultWaiters.append($0) }
        }
    }

    private let bridge: any AppleSpeechPlatformBridge
    private let clock: any AppleSpeechClock
    private let microphoneBridge: (any AppleSpeechMicrophonePlatformBridge)?
    private let audioSessionProvider: (any AppleSpeechAudioSessionProvider)?
    private var generation: UInt64 = 0
    private var active: Operation?
    private var activeMicrophone: MicrophoneOperation?
    private var pendingMicrophoneStarts = 0
    private var pendingMicrophoneWaiters: [CheckedContinuation<Void, Never>] = []
    private var closed = false

    public init(locale: String, timeout: TimeInterval = 30) throws {
        let canonical = try Self.canonicalLocale(locale)
        guard timeout.isFinite, (1...300).contains(timeout) else {
            throw KitError.invalidInput("timeout must be between 1 and 300 seconds")
        }
        self.locale = canonical
        self.timeout = timeout
        self.bridge = AppleSpeechPlatformBridgeImpl(locale: canonical)
        self.clock = SystemAppleSpeechClock()
        self.microphoneBridge = nil
        self.audioSessionProvider = nil
        self.supportedInputs = [.audioFile]
    }

    internal init(locale: String, timeout: TimeInterval = 30,
                  bridge: any AppleSpeechPlatformBridge,
                  microphoneBridge: (any AppleSpeechMicrophonePlatformBridge)? = nil,
                  audioSessionProvider: (any AppleSpeechAudioSessionProvider)? = nil,
                  clock: any AppleSpeechClock = SystemAppleSpeechClock()) throws {
        let canonical = try Self.canonicalLocale(locale)
        guard timeout.isFinite, (1...300).contains(timeout) else {
            throw KitError.invalidInput("timeout must be between 1 and 300 seconds")
        }
        self.locale = canonical
        self.timeout = timeout
        self.bridge = bridge
        self.clock = clock
        self.microphoneBridge = microphoneBridge
        self.audioSessionProvider = audioSessionProvider
        self.supportedInputs = microphoneBridge == nil || audioSessionProvider == nil
            ? [.audioFile] : [.audioFile, .microphone]
    }

    public func startMicrophone(_ request: SpeechMicrophoneRecognitionRequest) async throws
        -> any SpeechMicrophoneRecognitionTask {
        guard !closed else { throw KitError.closed }
        guard active == nil, activeMicrophone == nil, pendingMicrophoneStarts == 0 else { throw KitError.busy }
        guard let microphoneBridge, let audioSessionProvider else {
            throw SpeechRecognitionInputError.unsupportedInput
        }
        guard microphoneBridge.isRecordingAuthorized else {
            throw SpeechClientError.backendUnavailable(capability: .asr, backendId: backendId, reason: .permissionDenied)
        }
        let metadata = microphoneBridge.metadata()
        guard metadata.authorization == .authorized else {
            throw SpeechClientError.backendUnavailable(capability: .asr, backendId: backendId, reason: .permissionDenied)
        }
        guard metadata.isAvailable, metadata.supportsOnDeviceRecognition else {
            throw SpeechClientError.backendUnavailable(capability: .asr, backendId: backendId, reason: .unsupportedDevice)
        }
        guard request.locale == locale else { throw KitError.invalidInput("locale does not match backend") }
        try Task.checkCancellation()
        pendingMicrophoneStarts += 1
        defer { finishPendingMicrophoneStart() }

        let lease: any AppleSpeechAudioSessionLease
        do { lease = try await audioSessionProvider.acquire() }
        catch { throw error }
        guard !closed else {
            try? await lease.release()
            throw KitError.closed
        }
        do { try Task.checkCancellation() }
        catch {
            try? await lease.release()
            throw error
        }

        // Acquiring the host's session can suspend while permissions change.
        // Revalidate admission before creating any native capture resources.
        let currentMetadata = microphoneBridge.metadata()
        guard microphoneBridge.isRecordingAuthorized, currentMetadata.authorization == .authorized else {
            try? await lease.release()
            throw SpeechClientError.backendUnavailable(capability: .asr, backendId: backendId, reason: .permissionDenied)
        }
        guard currentMetadata.isAvailable, currentMetadata.supportsOnDeviceRecognition else {
            try? await lease.release()
            throw SpeechClientError.backendUnavailable(capability: .asr, backendId: backendId, reason: .unsupportedDevice)
        }

        generation &+= 1
        let currentGeneration = generation
        let inbox = EventInbox()
        let handle: AppleSpeechTaskHandle
        do {
            handle = try await microphoneBridge.start(requiresOnDeviceRecognition: true) { [weak self, inbox] event in
                inbox.append(event)
                Task { @MainActor [weak self, inbox] in
                    self?.receiveMicrophoneEvents(generation: currentGeneration, inbox: inbox)
                }
            }
        } catch {
            try? await lease.release()
            throw error
        }
        let operation = MicrophoneOperation(generation: currentGeneration, handle: handle,
                                             inbox: inbox, lease: lease)
        activeMicrophone = operation
        operation.pollTask = Task { @MainActor [weak self] in
            await self?.pollMicrophoneUntilCompleted(operation)
        }
        operation.timeoutTask = Task { @MainActor [weak self] in
            do {
                try await self?.clock.sleep(seconds: self?.timeout ?? 30)
                if let self { await self.timeoutMicrophone(operation) }
            } catch { }
        }
        if closed || Task.isCancelled {
            await cancelMicrophone(operation, failure: CancellationError())
            if operation.outcome == nil {
                await withCheckedContinuation { operation.closeWaiters.append($0) }
            }
            if closed { throw KitError.closed }
            throw CancellationError()
        }
        let task = AppleSpeechMicrophoneTaskHandleImpl(
            result: { try await operation.result() },
            finish: { [weak self] in await self?.finishMicrophone(operation) },
            cancel: { [weak self] in await self?.cancelMicrophone(operation, failure: CancellationError()) })
        return task
    }

    private func finishPendingMicrophoneStart() {
        pendingMicrophoneStarts -= 1
        guard pendingMicrophoneStarts == 0 else { return }
        let waiters = pendingMicrophoneWaiters
        pendingMicrophoneWaiters.removeAll()
        waiters.forEach { $0.resume() }
    }

    private func finishMicrophone(_ operation: MicrophoneOperation) async {
        guard activeMicrophone === operation, operation.outcome == nil,
              !operation.cancellationRequested, !operation.finishRequested else { return }
        operation.finishRequested = true
        await stopMicrophoneCapture(operation, endAudio: true)
        finishMicrophoneIfReady(operation)
    }

    private func cancelMicrophone(_ operation: MicrophoneOperation, failure: Error) async {
        guard activeMicrophone === operation, operation.outcome == nil else { return }
        operation.cancellationRequested = true
        if operation.failure == nil { operation.failure = failure }
        await stopMicrophoneCapture(operation, endAudio: true)
        requestMicrophoneCancel(operation)
        finishMicrophoneIfReady(operation)
    }

    private func requestMicrophoneCancel(_ operation: MicrophoneOperation) {
        guard !operation.nativeCancelRequested else { return }
        operation.nativeCancelRequested = true
        if microphoneBridge?.state(of: operation.handle) != .completed {
            microphoneBridge?.cancel(operation.handle)
        }
    }

    private func stopMicrophoneCapture(_ operation: MicrophoneOperation, endAudio: Bool) async {
        guard !operation.captureStopped, let microphoneBridge else { return }
        let stopTask: Task<Error?, Never>
        if let existing = operation.captureStopTask {
            stopTask = existing
        } else {
            let created: Task<Error?, Never> = Task { @MainActor in
                do {
                    try await microphoneBridge.stopCapture(operation.handle, endAudio: endAudio)
                    return nil
                } catch { return error }
            }
            operation.captureStopTask = created
            stopTask = created
        }
        let stopError = await stopTask.value
        if let error = stopError {
            operation.failure = error
        }
        operation.captureStopTask = nil
        operation.captureStopped = true
    }

    private func invalidateMicrophone(_ operation: MicrophoneOperation, failure: Error) async {
        guard activeMicrophone === operation, operation.outcome == nil else { return }
        operation.cancellationRequested = true
        if operation.failure == nil { operation.failure = failure }
        await stopMicrophoneCapture(operation, endAudio: true)
        requestMicrophoneCancel(operation)
        finishMicrophoneIfReady(operation)
    }

    private func stopMicrophoneAfterNaturalTerminal(_ operation: MicrophoneOperation) async {
        guard activeMicrophone === operation, operation.outcome == nil else { return }
        await stopMicrophoneCapture(operation, endAudio: true)
        finishMicrophoneIfReady(operation)
    }

    private func receiveMicrophoneEvents(generation: UInt64, inbox: EventInbox) {
        guard let operation = activeMicrophone,
              operation.generation == generation, operation.inbox === inbox,
              operation.outcome == nil else { return }
        for event in inbox.takeEvents() { receiveMicrophone(event, operation: operation) }
        finishMicrophoneIfReady(operation)
    }

    private func receiveMicrophone(_ event: AppleSpeechPlatformEvent, operation: MicrophoneOperation) {
        switch event {
        case .partial:
            break
        case .final(let text, let segments):
            guard !operation.cancellationRequested else { return }
            operation.finalResult = SpeechRecognitionResult(backendId: backendId, transcript: text,
                                                            confidence: Self.aggregateConfidence(segments))
            Task { @MainActor [weak self] in
                await self?.stopMicrophoneAfterNaturalTerminal(operation)
            }
        case .cancelled:
            operation.cancellationRequested = true
            if operation.failure == nil { operation.failure = CancellationError() }
            Task { @MainActor [weak self] in
                await self?.invalidateMicrophone(operation, failure: CancellationError())
            }
        case .failed(let domain, let code), .captureInvalidated(let domain, let code):
            let failure = SpeechRecognitionError.platform(domain: domain, code: code)
            operation.cancellationRequested = true
            if operation.failure == nil { operation.failure = failure }
            Task { @MainActor [weak self] in
                await self?.invalidateMicrophone(operation, failure: failure)
            }
        case .finished:
            operation.receivedFinished = true
            if !operation.captureStopped {
                Task { @MainActor [weak self] in
                    await self?.stopMicrophoneAfterNaturalTerminal(operation)
                }
            }
        }
    }

    private func pollMicrophoneUntilCompleted(_ operation: MicrophoneOperation) async {
        while activeMicrophone === operation {
            receiveMicrophoneEvents(generation: operation.generation, inbox: operation.inbox)
            finishMicrophoneIfReady(operation)
            guard activeMicrophone === operation else { return }
            do { try await clock.sleep(seconds: 0.02) }
            catch { return }
        }
    }

    private func timeoutMicrophone(_ operation: MicrophoneOperation) async {
        guard activeMicrophone === operation, operation.outcome == nil else { return }
        operation.cancellationRequested = true
        operation.failure = SpeechRecognitionError.timedOut
        await stopMicrophoneCapture(operation, endAudio: true)
        requestMicrophoneCancel(operation)
        finishMicrophoneIfReady(operation)
    }

    private func finishMicrophoneIfReady(_ operation: MicrophoneOperation) {
        guard activeMicrophone === operation,
              (operation.cancellationRequested || operation.finalResult != nil || operation.failure != nil || operation.receivedFinished),
              operation.captureStopped,
              microphoneBridge?.state(of: operation.handle) == .completed else { return }
        Task { @MainActor [weak self] in
            await self?.releaseMicrophone(operation)
        }
    }

    private func releaseMicrophone(_ operation: MicrophoneOperation) async {
        guard activeMicrophone === operation, !operation.cleanupStarted else { return }
        operation.cleanupStarted = true
        microphoneBridge?.release(operation.handle)
        operation.timeoutTask?.cancel()
        operation.pollTask?.cancel()
        var result: Result<SpeechRecognitionResult, Error>
        do {
            try await operation.lease.release()
            if operation.cancellationRequested {
                result = .failure(operation.failure ?? CancellationError())
            } else if let failure = operation.failure {
                result = .failure(failure)
            } else if let finalResult = operation.finalResult {
                result = .success(finalResult)
            } else {
                result = .failure(SpeechRecognitionError.noResult)
            }
        } catch {
            result = .failure(error)
        }
        operation.outcome = result
        activeMicrophone = nil
        for waiter in operation.resultWaiters { waiter.resume(with: result) }
        operation.resultWaiters.removeAll()
        let closeWaiters = operation.closeWaiters
        operation.closeWaiters.removeAll()
        closeWaiters.forEach { $0.resume() }
    }

    public func availability() async -> CapabilityAvailability {
        if closed { return unavailable(.closed) }
        let metadata = bridge.metadata()
        switch metadata.authorization {
        case .notDetermined, .denied, .restricted: return unavailable(.permissionDenied)
        case .authorized: break
        }
        guard metadata.isAvailable, metadata.supportsOnDeviceRecognition else {
            return unavailable(.unsupportedDevice)
        }
        return CapabilityAvailability(status: .available, backendId: backendId, minOS: "15.0", offline: true)
    }

    /// Recognizes a host-owned file. After cancellation or timeout, await `drain()`
    /// before changing/deleting the file; the platform may still be using its audio.
    public func recognize(_ request: SpeechRecognitionRequest) async throws -> SpeechRecognitionResult {
        guard !closed else { throw KitError.closed }
        guard active == nil, activeMicrophone == nil, pendingMicrophoneStarts == 0 else { throw KitError.busy }
        guard request.locale == locale else { throw KitError.invalidInput("locale does not match backend") }
        try validateFile(request.audioFile)
        let metadata = bridge.metadata()
        guard metadata.authorization == .authorized else {
            throw SpeechClientError.backendUnavailable(capability: .asr, backendId: backendId, reason: .permissionDenied)
        }
        guard metadata.isAvailable, metadata.supportsOnDeviceRecognition else {
            throw SpeechClientError.backendUnavailable(capability: .asr, backendId: backendId, reason: .unsupportedDevice)
        }
        try Task.checkCancellation()

        generation &+= 1
        let currentGeneration = generation
        let inbox = EventInbox()
        let handle = try bridge.start(fileURL: request.audioFile, requiresOnDeviceRecognition: true) { [weak self, inbox] event in
            inbox.append(event)
            Task { @MainActor [weak self, inbox] in
                self?.receiveBufferedEvents(generation: currentGeneration, inbox: inbox)
            }
        }
        let operation = Operation(generation: currentGeneration, handle: handle, inbox: inbox)
        active = operation
        operation.pollTask = Task { @MainActor [weak self] in
            await self?.pollUntilCompleted(operation)
        }
        operation.timeoutTask = Task { @MainActor [weak self] in
            do {
                try await self?.clock.sleep(seconds: self?.timeout ?? 30)
                self?.deadlineReached(operation)
            } catch { }
        }

        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                guard self.active === operation else {
                    continuation.resume(throwing: KitError.closed)
                    return
                }
                operation.continuation = continuation
            }
        }, onCancel: {
            Task { @MainActor [weak self] in self?.cancelFromCaller(operation) }
        })
    }

    public func close() async {
        closed = true
        if pendingMicrophoneStarts > 0 {
            await withCheckedContinuation { pendingMicrophoneWaiters.append($0) }
        }
        if let microphone = activeMicrophone {
            await cancelMicrophone(microphone, failure: CancellationError())
            finishMicrophoneIfReady(microphone)
            if activeMicrophone === microphone {
                await withCheckedContinuation { microphone.closeWaiters.append($0) }
            }
        }
        guard let operation = active else { return }
        cancel(operation, failure: CancellationError())
        finishIfReady(operation)
        guard active === operation else { return }
        await withCheckedContinuation { operation.closeWaiters.append($0) }
    }

    /// Wait for the current platform task to reach `.completed`, preserving reuse.
    /// Cancellation of this wait does not release the platform task early.
    public func drain() async {
        if pendingMicrophoneStarts > 0 {
            await withCheckedContinuation { pendingMicrophoneWaiters.append($0) }
        }
        if let microphone = activeMicrophone {
            finishMicrophoneIfReady(microphone)
            if activeMicrophone === microphone {
                await withCheckedContinuation { microphone.closeWaiters.append($0) }
            }
        }
        guard let operation = active else { return }
        finishIfReady(operation)
        guard active === operation else { return }
        await withCheckedContinuation { operation.closeWaiters.append($0) }
    }

    private func receiveBufferedEvents(generation: UInt64, inbox: EventInbox) {
        guard let operation = active, operation.generation == generation, operation.inbox === inbox else { return }
        let events = inbox.takeEvents()
        for event in events where !operation.callerSettled { receive(event, operation: operation) }
        finishIfReady(operation)
    }

    private func receive(_ event: AppleSpeechPlatformEvent, operation: Operation) {
        switch event {
        case .partial:
            break
        case .final(let text, let segments):
            let confidence = Self.aggregateConfidence(segments)
            let retainedConfidence = confidence ?? operation.finalResult?.confidence
            operation.finalResult = SpeechRecognitionResult(backendId: backendId, transcript: text,
                                                            confidence: retainedConfidence)
        case .cancelled:
            operation.failure = CancellationError()
        case .failed(let domain, let code):
            operation.failure = SpeechRecognitionError.platform(domain: domain, code: code)
        case .captureInvalidated:
            break
        case .finished:
            operation.receivedFinished = true
        }
    }

    private func pollUntilCompleted(_ operation: Operation) async {
        while active === operation {
            receiveBufferedEvents(generation: operation.generation, inbox: operation.inbox)
            finishIfReady(operation)
            guard active === operation else { return }
            do { try await clock.sleep(seconds: 0.02) }
            catch { return }
        }
    }

    private func deadlineReached(_ operation: Operation) {
        guard active === operation, !operation.callerSettled else { return }
        operation.callerSettled = true
        operation.continuation?.resume(throwing: SpeechRecognitionError.timedOut)
        operation.continuation = nil
        requestCancel(operation)
        finishIfReady(operation)
    }

    private func cancelFromCaller(_ operation: Operation) {
        guard active === operation, !operation.callerSettled else { return }
        operation.callerSettled = true
        operation.continuation?.resume(throwing: CancellationError())
        operation.continuation = nil
        requestCancel(operation)
        finishIfReady(operation)
    }

    private func cancel(_ operation: Operation, failure: Error) {
        guard active === operation else { return }
        if !operation.callerSettled {
            operation.callerSettled = true
            operation.continuation?.resume(throwing: failure)
            operation.continuation = nil
        }
        requestCancel(operation)
    }

    private func requestCancel(_ operation: Operation) {
        guard !operation.cancelRequested else { return }
        operation.cancelRequested = true
        if bridge.state(of: operation.handle) != .completed { bridge.cancel(operation.handle) }
    }

    private func finishIfReady(_ operation: Operation) {
        guard active === operation,
              operation.callerSettled || operation.finalResult != nil || operation.failure != nil || operation.receivedFinished,
              bridge.state(of: operation.handle) == .completed else { return }
        finish(operation)
    }

    private func finish(_ operation: Operation) {
        guard active === operation, bridge.state(of: operation.handle) == .completed else { return }
        bridge.release(operation.handle)
        operation.timeoutTask?.cancel()
        operation.pollTask?.cancel()
        active = nil
        if !operation.callerSettled {
            operation.callerSettled = true
            if let failure = operation.failure { operation.continuation?.resume(throwing: failure) }
            else if let result = operation.finalResult { operation.continuation?.resume(returning: result) }
            else { operation.continuation?.resume(throwing: SpeechRecognitionError.noResult) }
            operation.continuation = nil
        }
        let waiters = operation.closeWaiters
        operation.closeWaiters.removeAll()
        for waiter in waiters { waiter.resume() }
    }

    private func unavailable(_ reason: UnavailableReason) -> CapabilityAvailability {
        CapabilityAvailability(status: .unavailable, reason: reason, backendId: backendId, minOS: "15.0", offline: true)
    }

    private static func canonicalLocale(_ value: String) throws -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw KitError.invalidInput("empty speech locale") }
        return Locale.canonicalIdentifier(from: trimmed.replacingOccurrences(of: "-", with: "_"))
    }

    private static func aggregateConfidence(_ segments: [AppleSpeechSegment]) -> Float? {
        let values = segments.map(\.confidence).filter { $0.isFinite && (0...1).contains($0) }
        guard !values.isEmpty else { return nil }
        return values.reduce(0, +) / Float(values.count)
    }

    private func validateFile(_ url: URL) throws {
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw KitError.invalidInput("audio file does not exist")
        }
        var isDirectory: ObjCBool = false
        guard FileManager.default.fileExists(atPath: url.path, isDirectory: &isDirectory), !isDirectory.boolValue else {
            throw KitError.invalidInput("audio URL is a directory")
        }
        let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
        let permissions = (attributes[.posixPermissions] as? NSNumber)?.uint16Value ?? 0
        guard permissions & 0o444 != 0, FileManager.default.isReadableFile(atPath: url.path) else {
            throw KitError.invalidInput("audio file is not readable")
        }
    }
}
