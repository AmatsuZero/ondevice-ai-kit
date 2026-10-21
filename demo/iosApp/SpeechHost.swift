import Foundation
import ODAIKitDemo
#if ODAI_SPEECH_DEMO || ODAI_DEMO_BINDING
import ODAIKitCore
import ODAIKitSpeech
#endif
#if ODAI_SPEECH_DEMO
import UIKit
import Speech
import AVFAudio
import ODAIKitAppleSpeech
#endif

@MainActor
final class SpeechHost: NSObject, @preconcurrency SpeechBridge, @preconcurrency SpeechDemoActions {
    private let inputs: SpeechInputStore
    private let bridge: any SpeechBridge
    private let speechAuthorization: SpeechPermissionGate
    private let recordAuthorization: SpeechPermissionGate
    private let presentPicker: (@escaping (URL?) -> Void) -> Void
    private let openSettings: () -> Void
    private var closed = false
    private var foreground = true
    private var pickerGeneration = 0
    private var permission: PermissionTask?
    lazy var model = SpeechViewModel(bridge: WeakSpeechHostBridge(self))

    init(inputs: SpeechInputStore, bridge: any SpeechBridge,
         requestAuthorization: @escaping (@escaping (Bool) -> Void) -> Void,
         requestRecordAuthorization: @escaping (@escaping (Bool) -> Void) -> Void = { $0(false) },
         presentPicker: @escaping (@escaping (URL?) -> Void) -> Void, openSettings: @escaping () -> Void) {
        self.inputs = inputs; self.bridge = bridge
        self.speechAuthorization = SpeechPermissionGate(request: requestAuthorization)
        self.recordAuthorization = SpeechPermissionGate(request: requestRecordAuthorization)
        self.presentPicker = presentPicker
        self.openSettings = openSettings
    }

    #if ODAI_SPEECH_DEMO
    static func make() throws -> SpeechHost {
        let inputs = try SpeechInputStore()
        do {
            let ttsBackend = AppleAVSpeechBackend()
            let audioSessionProvider = DemoSpeechAudioSessionProvider()
            let asrBackend = SpeechLocaleRoutingBackend(isAuthorized: {
                SFSpeechRecognizer.authorizationStatus() == .authorized
            }) { locale in
                try AppleSpeechRecognizerBackend(locale: locale, audioSessionProvider: audioSessionProvider)
            }
            let bridge = IOSSpeechBridge(
                tts: try SpeechSynthesisClient(defaultBackendId: ttsBackend.backendId, backends: [ttsBackend]),
                asr: try SpeechRecognitionClient(defaultBackendId: asrBackend.backendId, backends: [asrBackend]), inputs: inputs)
            let picker = SpeechFilePicker()
            return SpeechHost(inputs: inputs, bridge: bridge, requestAuthorization: { reply in
                if SFSpeechRecognizer.authorizationStatus() == .authorized { reply(true) }
                else if SFSpeechRecognizer.authorizationStatus() == .notDetermined {
                    SFSpeechRecognizer.requestAuthorization { status in DispatchQueue.main.async { reply(status == .authorized) } }
                } else { reply(false) }
            }, requestRecordAuthorization: { reply in
                let deliver: (Bool) -> Void = { granted in DispatchQueue.main.async { reply(granted) } }
                if #available(iOS 17, *) {
                    switch AVAudioApplication.shared.recordPermission {
                    case .granted: reply(true)
                    case .undetermined: AVAudioApplication.requestRecordPermission(completionHandler: deliver)
                    default: reply(false)
                    }
                } else {
                    let session = AVAudioSession.sharedInstance()
                    switch session.recordPermission {
                    case .granted: reply(true)
                    case .undetermined: session.requestRecordPermission(deliver)
                    default: reply(false)
                    }
                }
            }, presentPicker: { picker.present(reply: $0) }, openSettings: {
                if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            })
        } catch { inputs.close(); throw error }
    }
    #endif

    func queryBackends(capability: SpeechBridgeCapability, callback: SpeechBridgeBackendsCallback) -> BridgeTask {
        if closed { callback.onFailure(failure: failure(.closed)); return PermissionTask() }
        return bridge.queryBackends(capability: capability, callback: callback)
    }
    func speak(request: SpeechBridgeTtsRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeTtsCallback) -> BridgeTask {
        if closed { callback.onFailure(failure: failure(.closed)); return PermissionTask() }
        if permission != nil { callback.onFailure(failure: failure(.busy)); return PermissionTask() }
        return bridge.speak(request: request, selection: selection, callback: callback)
    }
    func recognize(request: SpeechBridgeAsrRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeAsrCallback) -> BridgeTask {
        if closed { callback.onFailure(failure: failure(.closed)); return PermissionTask() }
        if permission != nil { callback.onFailure(failure: failure(.busy)); return PermissionTask() }
        if !foreground { callback.onCancelled(); return PermissionTask() }
        let microphone = request.input is SpeechBridgeInputMicrophone
        guard microphone || request.input is SpeechBridgeInputAudioFile else {
            callback.onFailure(failure: failure(.unsupportedInput)); return PermissionTask()
        }
        let task: PermissionTask = microphone ? MicrophonePermissionTask() : PermissionTask()
        permission = task
        task.cancelPending = { [weak self, weak task] in
            guard let self, let task, self.permission === task else { return }
            self.permission = nil
            task.complete()
            callback.onCancelled()
        }
        let admitted = PermissionAsrCallback(task: task, callback: callback)
        let start: (Bool, String) -> Void = { [weak self, weak task] granted, reason in
            guard let self, let task, self.permission === task else { return }
            self.permission = nil
            task.cancelPending = nil
            if self.closed || !self.foreground { admitted.onCancelled() }
            else if !granted { admitted.onFailure(failure: self.failure(.backendUnavailable, reason)) }
            else { task.install(self.bridge.recognize(request: request, selection: selection, callback: admitted)) }
        }
        speechAuthorization.request { [weak self, weak task] granted in
            guard let self, let task, self.permission === task else { return }
            if !granted || !microphone { start(granted, "permissionDenied") }
            else {
                self.recordAuthorization.request { [weak self, weak task] allowed in
                    guard let self, let task, self.permission === task else { return }
                    start(allowed, "recordPermissionDenied")
                }
            }
        }
        return task
    }
    func selectAudioFile() {
        guard !closed, foreground else { return }
        pickerGeneration += 1
        let generation = pickerGeneration
        presentPicker { [weak self] url in
            guard let self, !self.closed, self.foreground, self.pickerGeneration == generation, let url else { return }
            do {
                let token = try self.inputs.importAudio(from: url)
                let previous = self.model.state.audioReference
                self.model.selectAudio(reference: token, displayName: url.lastPathComponent)
                if let previous { self.inputs.remove(reference: previous) }
            } catch {
                // Do not expose the source URL or file error in logs/UI.
                self.model.inputFailed()
            }
        }
    }
    func clearAudioFile() {
        pickerGeneration += 1
        let previous = model.state.audioReference
        model.selectAudio(reference: nil, displayName: nil)
        if let previous { inputs.remove(reference: previous) }
    }
    func openSpeechSettings() { if !closed { openSettings() } }
    func leavePage() { pickerGeneration += 1; permission?.cancel(); model.leavePage() }
    func background() { foreground = false; leavePage(); model.background() }
    func onForeground() { if !closed { foreground = true } }
    func close() { model.close() }
    func close(callback: SpeechBridgeCloseCallback) {
        closed = true
        pickerGeneration += 1
        permission?.cancel()
        inputs.close()
        bridge.close(callback: callback)
    }
    private func failure(_ code: SpeechBridgeFailureCode, _ reason: String? = nil) -> SpeechBridgeFailure {
        SpeechBridgeFailure(code: code, reason: reason, domain: nil, platformCode: nil)
    }
}

/// One stable Apple backend ID with request-scoped recognizer languages. The bridge
/// keeps the shared audio slot; this owner keeps every locale backend until close.
#if ODAI_SPEECH_DEMO || ODAI_DEMO_BINDING
@MainActor
final class SpeechLocaleRoutingBackend: SpeechMicrophoneRecognitionBackend {
    nonisolated let backendId = "apple.speech.recognizer"
    nonisolated let capability: SpeechCapability = .asr
    nonisolated let supportedInputs: [SpeechRecognitionInputKind] = [.audioFile, .microphone]

    private let isAuthorized: @MainActor () -> Bool
    private let makeBackend: @MainActor (String) throws -> any SpeechMicrophoneRecognitionBackend
    private var backends: [String: any SpeechMicrophoneRecognitionBackend] = [:]
    private var closed = false

    init(isAuthorized: @escaping @MainActor () -> Bool,
         makeBackend: @escaping @MainActor (String) throws -> any SpeechMicrophoneRecognitionBackend) {
        self.isAuthorized = isAuthorized
        self.makeBackend = makeBackend
    }

    func availability() async -> CapabilityAvailability {
        // The registry asks without a request locale. Report the shared permission
        // gate here; the requested backend checks its own on-device readiness.
        let reason: UnavailableReason? = closed ? .closed : (isAuthorized() ? nil : .permissionDenied)
        return .init(status: reason == nil ? .available : .unavailable,
            reason: reason, backendId: backendId, minOS: "15.0", offline: true)
    }

    func recognize(_ request: SpeechRecognitionRequest) async throws -> SpeechRecognitionResult {
        try await backend(for: request.locale).recognize(request)
    }

    func startMicrophone(_ request: SpeechMicrophoneRecognitionRequest) async throws
        -> any SpeechMicrophoneRecognitionTask {
        try await backend(for: request.locale).startMicrophone(request)
    }

    func drain() async {
        for backend in backends.values { await backend.drain() }
    }

    func close() async {
        guard !closed else { return }
        closed = true
        for backend in backends.values { await backend.close() }
    }

    private func backend(for locale: String) throws -> any SpeechMicrophoneRecognitionBackend {
        guard !closed else { throw KitError.closed }
        if let backend = backends[locale] { return backend }
        let backend = try makeBackend(locale)
        backends[locale] = backend
        return backend
    }
}
#endif

/// The controller owns the host; the Kotlin model must not retain its Swift owner.
@MainActor
private final class WeakSpeechHostBridge: NSObject, @preconcurrency SpeechBridge {
    private weak var host: SpeechHost?
    init(_ host: SpeechHost) { self.host = host }
    func queryBackends(capability: SpeechBridgeCapability, callback: SpeechBridgeBackendsCallback) -> BridgeTask {
        guard let host else { callback.onFailure(failure: closed); return PermissionTask() }
        return host.queryBackends(capability: capability, callback: callback)
    }
    func speak(request: SpeechBridgeTtsRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeTtsCallback) -> BridgeTask {
        guard let host else { callback.onFailure(failure: closed); return PermissionTask() }
        return host.speak(request: request, selection: selection, callback: callback)
    }
    func recognize(request: SpeechBridgeAsrRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeAsrCallback) -> BridgeTask {
        guard let host else { callback.onFailure(failure: closed); return PermissionTask() }
        return host.recognize(request: request, selection: selection, callback: callback)
    }
    func close(callback: SpeechBridgeCloseCallback) {
        guard let host else { callback.onClosed(); return }
        host.close(callback: callback)
    }
    private var closed: SpeechBridgeFailure {
        SpeechBridgeFailure(code: .closed, reason: nil, domain: nil, platformCode: nil)
    }
}

@MainActor
private class PermissionTask: NSObject, @preconcurrency BridgeTask {
    private(set) var native: BridgeTask?
    private(set) var completed = false
    private(set) var cancelled = false
    var cancelPending: (() -> Void)?
    func install(_ native: BridgeTask) {
        guard !completed else { return }
        self.native = native
        if cancelled { native.cancel() }
    }
    func complete() { completed = true; native = nil; cancelPending = nil }
    func cancel() {
        guard !completed, !cancelled else { return }
        cancelled = true
        if let native { native.cancel() }
        else { let action = cancelPending; cancelPending = nil; action?() }
    }
}

@MainActor
private final class MicrophonePermissionTask: PermissionTask, @preconcurrency SpeechBridgeMicrophoneTask {
    private var finishRequested = false
    func finishInput() {
        guard !completed, !cancelled, !finishRequested else { return }
        finishRequested = true
        (native as? any SpeechBridgeMicrophoneTask)?.finishInput()
    }
    override func install(_ native: BridgeTask) {
        super.install(native)
        if !completed, !cancelled, finishRequested { (native as? any SpeechBridgeMicrophoneTask)?.finishInput() }
    }
}

@MainActor
private final class PermissionAsrCallback: NSObject, @preconcurrency SpeechBridgeAsrCallback {
    private let task: PermissionTask
    private let callback: SpeechBridgeAsrCallback
    init(task: PermissionTask, callback: SpeechBridgeAsrCallback) { self.task = task; self.callback = callback }
    func onResult(output: SpeechBridgeAsrResult) {
        guard !task.completed else { return }
        let cancelled = task.cancelled
        task.complete()
        if cancelled { callback.onCancelled() } else { callback.onResult(output: output) }
    }
    func onFailure(failure: SpeechBridgeFailure) {
        guard !task.completed else { return }
        let cancelled = task.cancelled
        task.complete()
        if cancelled { callback.onCancelled() } else { callback.onFailure(failure: failure) }
    }
    func onCancelled() {
        guard !task.completed else { return }
        task.complete(); callback.onCancelled()
    }
}

@MainActor
private final class SpeechPermissionGate {
    private let prompt: (@escaping (Bool) -> Void) -> Void
    private var waiters: [(Bool) -> Void] = []
    init(request: @escaping (@escaping (Bool) -> Void) -> Void) { prompt = request }
    func request(_ reply: @escaping (Bool) -> Void) {
        waiters.append(reply)
        guard waiters.count == 1 else { return }
        prompt { [weak self] granted in
            guard let self else { return }
            // Permission APIs can reply on a background thread; host state is main-only.
            if Thread.isMainThread { self.resolve(granted) }
            else { DispatchQueue.main.async { self.resolve(granted) } }
        }
    }
    private func resolve(_ granted: Bool) {
        let pending = waiters
        waiters.removeAll()
        pending.forEach { $0(granted) }
    }
}
