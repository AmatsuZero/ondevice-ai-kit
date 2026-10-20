import Foundation
import ODAIKitDemo
#if ODAI_SPEECH_DEMO
import UIKit
import Speech
import ODAIKitSpeech
import ODAIKitAppleSpeech
#endif

@MainActor
final class SpeechHost: NSObject, @preconcurrency SpeechBridge, @preconcurrency SpeechDemoActions {
    private let inputs: SpeechInputStore
    private let bridge: any SpeechBridge
    private let requestAuthorization: (@escaping (Bool) -> Void) -> Void
    private let presentPicker: (@escaping (URL?) -> Void) -> Void
    private let openSettings: () -> Void
    private var closed = false
    private var foreground = true
    private var pickerGeneration = 0
    private var permission: PermissionTask?
    lazy var model = SpeechViewModel(bridge: WeakSpeechHostBridge(self))

    init(inputs: SpeechInputStore, bridge: any SpeechBridge,
         requestAuthorization: @escaping (@escaping (Bool) -> Void) -> Void,
         presentPicker: @escaping (@escaping (URL?) -> Void) -> Void, openSettings: @escaping () -> Void) {
        self.inputs = inputs; self.bridge = bridge
        self.requestAuthorization = requestAuthorization; self.presentPicker = presentPicker
        self.openSettings = openSettings
    }

    #if ODAI_SPEECH_DEMO
    static func make() throws -> SpeechHost {
        let inputs = try SpeechInputStore()
        do {
            let ttsBackend = AppleAVSpeechBackend()
            let asrBackend = try AppleSpeechRecognizerBackend(locale: "en-US")
            let bridge = IOSSpeechBridge(
                tts: try SpeechSynthesisClient(defaultBackendId: ttsBackend.backendId, backends: [ttsBackend]),
                asr: try SpeechRecognitionClient(defaultBackendId: asrBackend.backendId, backends: [asrBackend]), inputs: inputs)
            let picker = SpeechFilePicker()
            return SpeechHost(inputs: inputs, bridge: bridge, requestAuthorization: { reply in
                if SFSpeechRecognizer.authorizationStatus() == .authorized { reply(true) }
                else if SFSpeechRecognizer.authorizationStatus() == .notDetermined {
                    SFSpeechRecognizer.requestAuthorization { status in DispatchQueue.main.async { reply(status == .authorized) } }
                } else { reply(false) }
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
        // Unsupported input is rejected without asking for an unrelated permission.
        guard request.input is SpeechBridgeInputAudioFile else {
            callback.onFailure(failure: failure(.unsupportedInput)); return PermissionTask()
        }
        let task = PermissionTask()
        permission = task
        task.cancelPending = { [weak self, weak task] in
            guard let self, let task, self.permission === task else { return }
            self.permission = nil
            callback.onCancelled()
        }
        requestAuthorization { [weak self, weak task] granted in
            guard let self, let task, self.permission === task else { return }
            self.permission = nil
            task.cancelPending = nil
            if self.closed || !self.foreground { callback.onCancelled() }
            else if !granted { callback.onFailure(failure: self.failure(.backendUnavailable, "permissionDenied")) }
            else { task.native = self.bridge.recognize(request: request, selection: selection, callback: callback) }
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
private final class PermissionTask: NSObject, @preconcurrency BridgeTask {
    var native: BridgeTask?
    var cancelPending: (() -> Void)?
    func cancel() {
        if let native { native.cancel() }
        else { let action = cancelPending; cancelPending = nil; action?() }
    }
}
