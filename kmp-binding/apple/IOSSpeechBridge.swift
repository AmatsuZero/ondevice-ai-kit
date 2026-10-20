import Foundation
import ODAIKitCore
import ODAIKitSpeech
#if ODAI_DEMO_BINDING
@preconcurrency import ODAIKitDemo
#else
@preconcurrency import ODAIKitBinding
#endif

/// Takes exclusive ownership of the supplied clients. All admission and callbacks run on main.
@MainActor
public final class IOSSpeechBridge: NSObject, @preconcurrency SpeechBridge {
    private let tts: SpeechSynthesisClient?
    private let asr: SpeechRecognitionClient?
    private let inputs: any IOSSpeechAudioInputProvider
    private var tasks: [UUID: IOSSpeechTask] = [:]
    private var audio: UUID?
    private var closed = false
    private var closeFinished = false
    private var closeWaiters: [any SpeechBridgeCloseCallback] = []
    private var closeWorker: Task<Void, Never>?

    public init(tts: SpeechSynthesisClient?, asr: SpeechRecognitionClient?, inputs: any IOSSpeechAudioInputProvider) {
        self.tts = tts; self.asr = asr; self.inputs = inputs
    }

    public func queryBackends(capability: SpeechBridgeCapability, callback: any SpeechBridgeBackendsCallback) -> any BridgeTask {
        if let failure = admission(audio: false) { callback.onFailure(failure: failure); return IOSSpeechTask() }
        guard capability == .tts ? tts != nil : asr != nil else {
            callback.onFailure(failure: fail(.backendUnavailable, "backendNotLinked")); return IOSSpeechTask()
        }
        return submit(audio: false, operation: {
            try Task.checkCancellation()
            let descriptors: [SpeechBackendDescriptor]
            if capability == .tts, let tts = self.tts { descriptors = try await tts.listBackends() }
            else if let asr = self.asr { descriptors = try await asr.listBackends() }
            else { throw KitError.closed }
            return descriptors.map { descriptor in
                let availability = descriptor.availability
                return SpeechBridgeBackend(
                    capability: capability, backendId: descriptor.backendId,
                    availability: BridgeAvailability(available: availability.status == .available,
                        reason: availability.reason?.rawValue, backendId: descriptor.backendId,
                        minimumOS: availability.minOS),
                    supportedInputs: descriptor.supportedInputs.map {
                        $0 == .microphone ? .microphone : .audioFile
                    }, offline: availability.offline)
            }
        }, result: { callback.onResult(backends: $0) }, failure: { callback.onFailure(failure: $0) },
           cancelled: { callback.onCancelled() })
    }

    public func speak(request: SpeechBridgeTtsRequest, selection: any SpeechBridgeSelection,
                      callback: any SpeechBridgeTtsCallback) -> any BridgeTask {
        if let failure = admission(audio: true) { callback.onFailure(failure: failure); return IOSSpeechTask() }
        guard !request.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !request.locale.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let selected = native(selection) else {
            callback.onFailure(failure: fail(.invalidInput)); return IOSSpeechTask()
        }
        guard let tts else {
            callback.onFailure(failure: fail(.backendUnavailable, "backendNotLinked")); return IOSSpeechTask()
        }
        return submit(audio: true, operation: {
            try Task.checkCancellation()
            let value = try await tts.speak(.init(text: request.text, locale: request.locale, voiceId: request.voiceId),
                                             backend: selected)
            return SpeechBridgeTtsResult(backendId: value.backendId, voiceId: value.voiceId)
        }, result: { callback.onResult(synthesis: $0) }, failure: { callback.onFailure(failure: $0) },
           cancelled: { callback.onCancelled() })
    }

    public func recognize(request: SpeechBridgeAsrRequest, selection: any SpeechBridgeSelection,
                          callback: any SpeechBridgeAsrCallback) -> any BridgeTask {
        if let failure = admission(audio: true) { callback.onFailure(failure: failure); return IOSSpeechTask() }
        guard !request.locale.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let selected = native(selection) else {
            callback.onFailure(failure: fail(.invalidInput)); return IOSSpeechTask()
        }
        guard let asr else {
            callback.onFailure(failure: fail(.backendUnavailable, "backendNotLinked")); return IOSSpeechTask()
        }
        if request.input is SpeechBridgeInputMicrophone {
            let nativeRequest: SpeechMicrophoneRecognitionRequest
            do { nativeRequest = try SpeechMicrophoneRecognitionRequest(locale: request.locale) }
            catch { callback.onFailure(failure: fail(.invalidInput)); return IOSSpeechTask() }
            let handle = IOSSpeechMicrophoneTask()
            return submit(audio: true, handle: handle, operation: {
                try Task.checkCancellation()
                let native = try await asr.startMicrophone(nativeRequest, backend: selected)
                await handle.attach(native)
                // The managed native task publishes only after backend drain.
                let value = try await native.result()
                return SpeechBridgeAsrResult(backendId: value.backendId, transcript: value.transcript,
                    confidence: value.confidence.map { KotlinFloat(float: $0) })
            }, result: { callback.onResult(output: $0) }, failure: { callback.onFailure(failure: $0) },
               cancelled: { callback.onCancelled() })
        }
        guard let file = request.input as? SpeechBridgeInputAudioFile else {
            callback.onFailure(failure: fail(.unsupportedInput)); return IOSSpeechTask()
        }
        let lease: any IOSSpeechAudioInputLease
        let nativeRequest: SpeechRecognitionRequest
        do {
            let acquired = try inputs.acquire(reference: file.reference)
            do { nativeRequest = try SpeechRecognitionRequest(locale: request.locale, audioFile: acquired.url) }
            catch { acquired.release(); throw error }
            lease = acquired
        } catch {
            callback.onFailure(failure: fail(.invalidInput)); return IOSSpeechTask()
        }
        return submit(audio: true, operation: {
            let outcome: Result<SpeechRecognitionResult, Error>
            do {
                try Task.checkCancellation()
                outcome = .success(try await asr.recognize(nativeRequest, backend: selected))
            } catch { outcome = .failure(error) }
            // Unstructured cleanup is not cancelled with the request. Native return alone
            // does not prove that a cancelled recognizer has released its file.
            await Task { await asr.drain() }.value
            lease.release()
            let value = try outcome.get()
            return SpeechBridgeAsrResult(backendId: value.backendId, transcript: value.transcript,
                                         confidence: value.confidence.map { KotlinFloat(float: $0) })
        }, result: { callback.onResult(output: $0) }, failure: { callback.onFailure(failure: $0) },
           cancelled: { callback.onCancelled() })
    }

    public func close(callback: any SpeechBridgeCloseCallback) {
        if closeFinished { callback.onClosed(); return }
        closed = true
        closeWaiters.append(callback)
        guard closeWorker == nil else { return }
        let pending = Array(tasks.values)
        pending.forEach { $0.cancel() }
        closeWorker = Task { @MainActor in
            for task in pending { await task.worker?.value }
            await tts?.close()
            await asr?.close()
            closeFinished = true
            let waiters = closeWaiters
            closeWaiters.removeAll()
            closeWorker = nil
            waiters.forEach { $0.onClosed() }
        }
    }

    private func admission(audio: Bool) -> SpeechBridgeFailure? {
        if closed { return fail(.closed) }
        if audio && self.audio != nil { return fail(.busy) }
        return nil
    }

    private func native(_ selection: any SpeechBridgeSelection) -> SpeechBackendSelection? {
        if selection is SpeechBridgeSelectionDefault { return .default }
        if let selection = selection as? SpeechBridgeSelectionExplicit { return .explicit(selection.backendId) }
        return nil
    }

    private func submit<T>(audio: Bool, handle: IOSSpeechTask = IOSSpeechTask(),
                           operation: @escaping @MainActor () async throws -> T,
                           result: @escaping (T) -> Void, failure: @escaping (SpeechBridgeFailure) -> Void,
                           cancelled: @escaping () -> Void) -> any BridgeTask {
        let id = UUID()
        tasks[id] = handle
        if audio { self.audio = id }
        handle.worker = Task { @MainActor in
            let outcome: Result<T, Error>
            do { outcome = .success(try await operation()) }
            catch { outcome = .failure(error) }
            let wasCancelled = handle.finish()
            tasks.removeValue(forKey: id)
            if self.audio == id { self.audio = nil }
            // Remove ownership before notifying: a terminal callback may synchronously reenter.
            if wasCancelled { cancelled() }
            else {
                switch outcome {
                case .success(let value): result(value)
                case .failure(let error):
                    if error is CancellationError || (error as? KitError) == .cancelled { cancelled() }
                    else { failure(map(error)) }
                }
            }
        }
        return handle
    }

    private func fail(_ code: SpeechBridgeFailureCode, _ reason: String? = nil) -> SpeechBridgeFailure {
        SpeechBridgeFailure(code: code, reason: reason, domain: nil, platformCode: nil)
    }

    private func map(_ error: Error) -> SpeechBridgeFailure {
        switch error {
        case SpeechClientError.closed, KitError.closed: return fail(.closed)
        case KitError.busy: return fail(.busy)
        case KitError.invalidInput: return fail(.invalidInput)
        case SpeechRecognitionInputError.unsupportedInput: return fail(.unsupportedInput)
        case SpeechClientError.backendNotRegistered: return fail(.backendNotRegistered)
        case SpeechClientError.backendUnavailable(_, _, let reason): return fail(.backendUnavailable, reason.rawValue)
        case SpeechRecognitionError.timedOut: return fail(.timedOut)
        case SpeechRecognitionError.noResult: return fail(.noResult)
        case SpeechRecognitionError.platform(let domain, let code):
            return SpeechBridgeFailure(code: .platform, reason: nil, domain: domain,
                                       platformCode: KotlinInt(int: Int32(clamping: code)))
        default:
            return SpeechBridgeFailure(code: .platform, reason: nil, domain: "ios.speech",
                                       platformCode: KotlinInt(int: 1))
        }
    }
}

/// Cancellation may arrive from any thread; registry mutation stays on MainActor.
private class IOSSpeechTask: NSObject, BridgeTask {
    private let lock = NSLock()
    private var cancelled = false
    private var finished = false
    private var storedWorker: Task<Void, Never>?
    var worker: Task<Void, Never>? {
        get { lock.withLock { storedWorker } }
        set {
            let shouldCancel = lock.withLock { storedWorker = newValue; return cancelled }
            if shouldCancel { newValue?.cancel() }
        }
    }
    func cancel() {
        let task = lock.withLock { () -> Task<Void, Never>? in
            if finished { return nil }
            cancelled = true
            return storedWorker
        }
        task?.cancel()
    }
    func finish() -> Bool {
        lock.withLock { finished = true; storedWorker = nil; return cancelled }
    }
}

/// Records controls before asynchronous native admission returns. Each instance
/// owns one native task; completed handles cannot reach a later generation.
private final class IOSSpeechMicrophoneTask: IOSSpeechTask, SpeechBridgeMicrophoneTask, @unchecked Sendable {
    private let controlLock = NSLock()
    private var native: (any SpeechMicrophoneRecognitionTask)?
    private var finishRequested = false
    private var cancelRequested = false
    private var terminal = false

    func attach(_ task: any SpeechMicrophoneRecognitionTask) async {
        let intent = controlLock.withLock { () -> (Bool, Bool) in
            native = task
            return (cancelRequested, finishRequested)
        }
        if intent.0 { await task.cancel() }
        else if intent.1 { await task.finishInput() }
    }

    func finishInput() {
        let task = controlLock.withLock { () -> (any SpeechMicrophoneRecognitionTask)? in
            guard !terminal, !cancelRequested, !finishRequested else { return nil }
            finishRequested = true
            return native
        }
        if let task { Task { await task.finishInput() } }
    }

    override func cancel() {
        let task = controlLock.withLock { () -> (any SpeechMicrophoneRecognitionTask)? in
            guard !terminal, !cancelRequested else { return nil }
            cancelRequested = true
            return native
        }
        super.cancel()
        if let task { Task { await task.cancel() } }
    }

    override func finish() -> Bool {
        controlLock.withLock { terminal = true; native = nil }
        return super.finish()
    }
}
