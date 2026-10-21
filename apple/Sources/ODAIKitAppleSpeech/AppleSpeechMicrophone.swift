import Foundation
import Speech
import AVFAudio
import ODAIKitSpeech

@MainActor
internal protocol AppleSpeechMicrophoneEngine: AnyObject {
    func hasValidInputFormat() -> Bool
    func installTap(deliver: @escaping @Sendable (AVAudioPCMBuffer) -> Void) throws
    func prepareAndStart() throws
    func stop()
    func removeTap()
}

@MainActor
internal final class AppleSpeechMicrophoneCapture {
    private let engine: any AppleSpeechMicrophoneEngine
    private let delivery: AppleSpeechBufferDelivery
    private let endAudio: @Sendable () -> Void
    private var tapInstalled = false
    private var stopTask: Task<Void, Never>?

    init(engine: any AppleSpeechMicrophoneEngine,
         append: @escaping @Sendable (AVAudioPCMBuffer) -> Void,
         endAudio: @escaping @Sendable () -> Void) {
        self.engine = engine
        self.delivery = AppleSpeechBufferDelivery(append: append)
        self.endAudio = endAudio
    }

    func start() async throws {
        guard engine.hasValidInputFormat() else {
            await stop()
            throw SpeechRecognitionError.platform(domain: "AVAudioEngine.InputFormat", code: -1)
        }
        do {
            try engine.installTap { [delivery] buffer in delivery.deliver(buffer) }
            tapInstalled = true
            try engine.prepareAndStart()
        } catch {
            await stop()
            throw error
        }
    }

    func stop() async {
        if stopTask == nil {
            delivery.closeAdmission()
            engine.stop()
            if tapInstalled {
                engine.removeTap()
                tapInstalled = false
            }
            stopTask = Task { [delivery, endAudio] in
                await delivery.drain()
                endAudio()
            }
        }
        await stopTask?.value
    }
}

/// Admission and in-flight registration share one lock, so a callback paused
/// before append cannot slip behind the stop barrier. PCM is consumed on the
/// tap callback's thread before the engine can reuse the buffer.
private final class AppleSpeechBufferDelivery: @unchecked Sendable {
    private let lock = NSLock()
    private let append: @Sendable (AVAudioPCMBuffer) -> Void
    private var accepting = true
    private var inFlight = 0
    private var waiters: [CheckedContinuation<Void, Never>] = []

    init(append: @escaping @Sendable (AVAudioPCMBuffer) -> Void) { self.append = append }

    func deliver(_ buffer: AVAudioPCMBuffer) {
        lock.lock()
        guard accepting else { lock.unlock(); return }
        inFlight += 1
        lock.unlock()
        append(buffer)
        lock.lock()
        inFlight -= 1
        let ready = inFlight == 0 ? waiters : []
        if inFlight == 0 { waiters.removeAll() }
        lock.unlock()
        ready.forEach { $0.resume() }
    }

    func closeAdmission() {
        lock.lock(); accepting = false; lock.unlock()
    }

    func drain() async {
        await withCheckedContinuation { continuation in
            lock.lock()
            if inFlight == 0 {
                lock.unlock()
                continuation.resume()
            } else {
                waiters.append(continuation)
                lock.unlock()
            }
        }
    }
}

@MainActor
internal protocol AppleSpeechMicrophonePlatformBridge: AnyObject {
    var isRecordingAuthorized: Bool { get }
    func metadata() -> AppleSpeechMetadata
    func start(requiresOnDeviceRecognition: Bool,
               callback: @escaping @Sendable (AppleSpeechPlatformEvent) -> Void) async throws -> AppleSpeechTaskHandle
    func stopCapture(_ handle: AppleSpeechTaskHandle, endAudio: Bool) async throws
    func cancel(_ handle: AppleSpeechTaskHandle)
    func state(of handle: AppleSpeechTaskHandle) -> AppleSpeechTaskState
    func release(_ handle: AppleSpeechTaskHandle)
}

internal final class AppleSpeechMicrophoneTaskHandleImpl: SpeechMicrophoneRecognitionTask, @unchecked Sendable {
    private let resultOperation: @Sendable () async throws -> SpeechRecognitionResult
    private let finishOperation: @Sendable () async -> Void
    private let cancelOperation: @Sendable () async -> Void

    init(result: @escaping @Sendable () async throws -> SpeechRecognitionResult,
         finish: @escaping @Sendable () async -> Void,
         cancel: @escaping @Sendable () async -> Void) {
        resultOperation = result
        finishOperation = finish
        cancelOperation = cancel
    }

    func result() async throws -> SpeechRecognitionResult { try await resultOperation() }
    func finishInput() async { await finishOperation() }
    func cancel() async { await cancelOperation() }
}

#if os(iOS)

/// A route notification can arrive after the host's own session setup. Only
/// category changes matching this admitted, healthy configuration are benign.
/// Port identities stay internal and are never included in diagnostics.
internal struct AppleSpeechMicrophoneRouteSnapshot: Equatable, Sendable {
    let category: String
    let mode: String
    let options: UInt
    let inputPorts: [String]
    let outputPorts: [String]
    let sampleRate: Double
    let inputChannels: Int

    static func current() -> Self {
        let session = AVAudioSession.sharedInstance()
        func ports(_ ports: [AVAudioSessionPortDescription]) -> [String] {
            ports.map { "\($0.uid):\($0.selectedDataSource?.dataSourceID.stringValue ?? "-")" }
        }
        return .init(category: session.category.rawValue, mode: session.mode.rawValue,
            options: session.categoryOptions.rawValue, inputPorts: ports(session.currentRoute.inputs),
            outputPorts: ports(session.currentRoute.outputs), sampleRate: session.sampleRate,
            inputChannels: session.inputNumberOfChannels)
    }

    func requiresInvalidation(_ notification: Notification, current: Self) -> Bool {
        let reason = (notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? NSNumber)?.uintValue
        guard reason == AVAudioSession.RouteChangeReason.categoryChange.rawValue,
              self == current, !inputPorts.isEmpty, inputChannels > 0,
              sampleRate.isFinite, sampleRate > 0 else { return true }
        return false
    }
}

@MainActor
internal final class AppleSpeechMicrophonePlatformBridgeImpl: AppleSpeechMicrophonePlatformBridge {
    var isRecordingAuthorized: Bool { AVAudioSession.sharedInstance().recordPermission == .granted }
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

        func speechRecognitionTaskWasCancelled(_ task: SFSpeechRecognitionTask) { callback(.cancelled) }
    }

    @MainActor
    private final class Engine: AppleSpeechMicrophoneEngine {
        let engine = AVAudioEngine()
        func hasValidInputFormat() -> Bool {
            let format = engine.inputNode.outputFormat(forBus: 0)
            return format.sampleRate.isFinite && format.sampleRate > 0 && format.channelCount > 0
        }
        func installTap(deliver: @escaping @Sendable (AVAudioPCMBuffer) -> Void) throws {
            let input = engine.inputNode
            let format = input.outputFormat(forBus: 0)
            if #available(iOS 27, *) {
                try input.installAudioTap(onBus: 0, bufferSize: 1_024, format: format) { buffer, _ in
                    deliver(AVAudioPCMBuffer(copying: buffer))
                }
            } else {
                input.installTap(onBus: 0, bufferSize: 1_024, format: format) { buffer, _ in deliver(buffer) }
            }
        }
        func prepareAndStart() throws { engine.prepare(); try engine.start() }
        func stop() { engine.stop() }
        func removeTap() { engine.inputNode.removeTap(onBus: 0) }
    }

    private final class BufferRequest: @unchecked Sendable {
        let request = SFSpeechAudioBufferRecognitionRequest()
        func append(_ buffer: AVAudioPCMBuffer) { request.append(buffer) }
        func endAudio() { request.endAudio() }
    }

    private struct Entry {
        let task: SFSpeechRecognitionTask
        let delegate: Delegate
        let capture: AppleSpeechMicrophoneCapture
        let observers: [NSObjectProtocol]
    }

    private let recognizer: SFSpeechRecognizer?
    private var entries: [AppleSpeechTaskHandle: Entry] = [:]

    init(locale: String) { recognizer = SFSpeechRecognizer(locale: Locale(identifier: locale)) }

    func metadata() -> AppleSpeechMetadata {
        let authorization: AppleSpeechAuthorization
        switch SFSpeechRecognizer.authorizationStatus() {
        case .notDetermined: authorization = .notDetermined
        case .denied: authorization = .denied
        case .restricted: authorization = .restricted
        case .authorized: authorization = .authorized
        @unknown default: authorization = .restricted
        }
        return .init(authorization: authorization,
                     isAvailable: recognizer?.isAvailable ?? false,
                     supportsOnDeviceRecognition: recognizer?.supportsOnDeviceRecognition ?? false)
    }

    func start(requiresOnDeviceRecognition: Bool,
               callback: @escaping @Sendable (AppleSpeechPlatformEvent) -> Void) async throws -> AppleSpeechTaskHandle {
        guard let recognizer else {
            throw SpeechRecognitionError.platform(domain: "SFSpeechRecognizer", code: -1)
        }
        let bufferRequest = BufferRequest()
        let request = bufferRequest.request
        request.requiresOnDeviceRecognition = requiresOnDeviceRecognition
        request.shouldReportPartialResults = true
        let capture = AppleSpeechMicrophoneCapture(engine: Engine(),
            append: { bufferRequest.append($0) }, endAudio: { bufferRequest.endAudio() })
        let center = NotificationCenter.default
        let expectedRoute = AppleSpeechMicrophoneRouteSnapshot.current()
        let observers = [
            center.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: nil) { note in
                let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
                guard raw == AVAudioSession.InterruptionType.began.rawValue else { return }
                callback(.captureInvalidated(domain: "AVAudioSession.Interruption", code: 1))
            },
            center.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: nil) { note in
                guard expectedRoute.requiresInvalidation(note, current: .current()) else { return }
                callback(.captureInvalidated(domain: "AVAudioSession.RouteChange", code: 2))
            },
            center.addObserver(forName: AVAudioSession.mediaServicesWereResetNotification, object: nil, queue: nil) { _ in
                callback(.captureInvalidated(domain: "AVAudioSession.MediaServicesReset", code: 3))
            }
        ]
        do { try await capture.start() }
        catch {
            observers.forEach(center.removeObserver)
            throw error
        }
        let delegate = Delegate(callback: callback)
        let task = recognizer.recognitionTask(with: request, delegate: delegate)
        let handle = AppleSpeechTaskHandle()
        entries[handle] = Entry(task: task, delegate: delegate, capture: capture, observers: observers)
        return handle
    }

    func stopCapture(_ handle: AppleSpeechTaskHandle, endAudio: Bool) async throws {
        await entries[handle]?.capture.stop()
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
        guard state(of: handle) == .completed, let entry = entries.removeValue(forKey: handle) else { return }
        entry.observers.forEach(NotificationCenter.default.removeObserver)
    }
}

public extension AppleSpeechRecognizerBackend {
    convenience init(locale: String, audioSessionProvider: any AppleSpeechAudioSessionProvider,
                     timeout: TimeInterval = 30) throws {
        let canonical = try SpeechMicrophoneRecognitionRequest(locale: locale).locale
        try self.init(locale: canonical, timeout: timeout,
                      bridge: AppleSpeechPlatformBridgeImpl(locale: canonical),
                      microphoneBridge: AppleSpeechMicrophonePlatformBridgeImpl(locale: canonical),
                      audioSessionProvider: audioSessionProvider,
                      clock: SystemAppleSpeechClock())
    }
}
#endif
