import Foundation
import XCTest
import ODAIKitCore
import ODAIKitSpeech
#if ODAI_DEMO_BINDING
import ODAIKitDemo
#else
import ODAIKitBinding
#endif

@MainActor
final class SpeechHostTests: XCTestCase {
    private var microphoneRequest: SpeechBridgeAsrRequest {
        .init(locale: "en-US", input: SpeechBridgeInputMicrophone.shared)
    }

    func testLocaleRouterBuildsBackendForRequestedLocale() async throws {
        let recorder = HostLocaleBackendRecorder()
        let router = SpeechLocaleRoutingBackend(isAuthorized: { true }) { locale in
            recorder.make(locale: locale)
        }
        let request = try SpeechRecognitionRequest(
            locale: "zh-CN", audioFile: URL(fileURLWithPath: "/tmp/locale-routing-fixture.caf"))

        let result = try await router.recognize(request)

        XCTAssertEqual(result.transcript, request.locale)
        XCTAssertEqual(recorder.locales, ["zh_CN"])
        await router.close()
    }

    func testUnavailableEnglishDoesNotBlockChineseFileOrMicrophoneThroughClient() async throws {
        let router = SpeechLocaleRoutingBackend(isAuthorized: { true }) { locale in
            HostLocaleBackend(locale: locale, available: locale == "zh_CN")
        }
        let client = try SpeechRecognitionClient(defaultBackendId: router.backendId, backends: [router])
        let file = try SpeechRecognitionRequest(locale: "zh-CN",
            audioFile: URL(fileURLWithPath: "/tmp/locale-routing-fixture.caf"))
        do {
            let result = try await client.recognize(file, backend: .explicit("apple.speech.recognizer"))
            XCTAssertEqual(result.transcript, "zh_CN")
            let task = try await client.startMicrophone(.init(locale: "zh-CN"))
            await task.finishInput()
            let microphone = try await task.result()
            XCTAssertEqual(microphone.transcript, "zh_CN")
        } catch {
            XCTFail("An unavailable English recognizer must not reject a ready Chinese request: \(error)")
        }
        await client.close()
    }

    func testUnavailableRequestedLocaleNeverFallsBackForFileOrMicrophone() async throws {
        let recorder = HostLocaleBackendRecorder()
        recorder.unavailableLocales = ["fr_FR"]
        let router = SpeechLocaleRoutingBackend(isAuthorized: { true }) { recorder.make(locale: $0) }
        let client = try SpeechRecognitionClient(defaultBackendId: router.backendId, backends: [router])
        do {
            _ = try await client.recognize(.init(locale: "fr-FR",
                audioFile: URL(fileURLWithPath: "/tmp/locale-routing-fixture.caf")))
            XCTFail("Unavailable requested language must fail")
        } catch {
            XCTAssertEqual(error as? SpeechClientError,
                .backendUnavailable(capability: .asr, backendId: "apple.speech.recognizer", reason: .unsupportedDevice))
        }
        do {
            _ = try await client.startMicrophone(.init(locale: "fr_FR"), backend: .explicit("apple.speech.recognizer"))
            XCTFail("Microphone must not fall back to a different language")
        } catch {
            XCTAssertEqual(error as? SpeechClientError,
                .backendUnavailable(capability: .asr, backendId: "apple.speech.recognizer", reason: .unsupportedDevice))
        }
        XCTAssertEqual(recorder.locales, ["fr_FR"], "Canonical aliases must reuse the same requested backend")
        await client.close()
        XCTAssertEqual(recorder.closedLocales, ["fr_FR"])
    }

    func testLocaleRouterPermissionAndClosePreventNativeAdmission() async throws {
        let recorder = HostLocaleBackendRecorder()
        var authorized = false
        let router = SpeechLocaleRoutingBackend(isAuthorized: { authorized }) { recorder.make(locale: $0) }
        let client = try SpeechRecognitionClient(defaultBackendId: router.backendId, backends: [router])
        do {
            _ = try await client.startMicrophone(.init(locale: "zh-CN"))
            XCTFail("Speech permission must remain an admission gate")
        } catch {
            XCTAssertEqual(error as? SpeechClientError,
                .backendUnavailable(capability: .asr, backendId: "apple.speech.recognizer", reason: .permissionDenied))
        }
        XCTAssertTrue(recorder.locales.isEmpty)
        authorized = true
        let task = try await client.startMicrophone(.init(locale: "zh_CN"))
        _ = try await task.result()
        await client.close()
        let metadata = await router.availability()
        XCTAssertEqual(metadata.reason, .closed)
        do {
            _ = try await router.startMicrophone(.init(locale: "en-US"))
            XCTFail("A closed router must not create another backend")
        } catch { XCTAssertEqual(error as? KitError, .closed) }
        XCTAssertEqual(recorder.locales, ["zh_CN"])
        XCTAssertEqual(recorder.closedLocales, ["zh_CN"])
    }

    func testCloseWhilePermissionPendingDoesNotRetainHost() throws {
        let f = try HostFixture()
        defer { try? FileManager.default.removeItem(at: f.root) }
        f.host.model.recognize(request: microphoneRequest, selection: SpeechBridgeSelectionDefault.shared)
        weak var released = f.host
        f.host.close()
        f.host = nil
        XCTAssertNil(released)
        f.permissionReply?(true)
        XCTAssertEqual(f.native.starts, 0)
    }

    func testMicrophoneAsksSpeechThenRecordAndDenialStillAllowsFile() throws {
        let f = try HostFixture()
        defer { f.shutdown() }
        f.host.model.recognize(request: microphoneRequest, selection: SpeechBridgeSelectionDefault.shared)
        XCTAssertEqual(f.speechPrompts, 1)
        XCTAssertEqual(f.recordPrompts, 0)
        f.permissionReply?(true)
        XCTAssertEqual(f.recordPrompts, 1)
        XCTAssertEqual(f.native.starts, 0)
        f.recordReply?(false)
        XCTAssertEqual(f.host.model.state.failure?.reason, "recordPermissionDenied")
        f.host.model.recognize(request: .init(locale: "en-US", input: SpeechBridgeInputAudioFile(reference: "file")),
            selection: SpeechBridgeSelectionDefault.shared)
        f.permissionReply?(true)
        XCTAssertEqual(f.native.starts, 1)
        XCTAssertEqual(f.recordPrompts, 1)
    }

    func testFinishWhileRecordPermissionPendingReachesNativeAndCancelDiscardsLateResult() throws {
        let f = try HostFixture()
        defer { f.shutdown() }
        f.native.synchronous = false
        f.host.model.recognize(request: microphoneRequest, selection: SpeechBridgeSelectionDefault.shared)
        f.host.model.finishInput()
        XCTAssertEqual(f.host.model.state.phase, .finishing)
        f.permissionReply?(true); f.recordReply?(true)
        XCTAssertEqual(f.native.finishes, 1)
        XCTAssertFalse(f.host.model.state.controlsEnabled)
        f.host.model.cancel()
        XCTAssertEqual(f.native.cancels, 1)
        f.native.callback?.onResult(output: .init(backendId: "fixture", transcript: "synthetic", confidence: nil))
        XCTAssertEqual(f.host.model.state.phase, .cancelled)
        XCTAssertNil(f.host.model.state.asrResult)
    }

    func testCancelRetrySharesPendingSpeechAndRecordPromptsWithoutRestartingOldTask() throws {
        let f = try HostFixture()
        defer { f.shutdown() }
        let selection = SpeechBridgeSelectionDefault.shared
        f.host.model.recognize(request: microphoneRequest, selection: selection)
        f.host.model.cancel()
        f.host.model.recognize(request: microphoneRequest, selection: selection)
        XCTAssertEqual(f.speechPrompts, 1)
        f.permissionReply?(true)
        XCTAssertEqual(f.recordPrompts, 1)
        f.host.model.cancel()
        f.host.model.recognize(request: microphoneRequest, selection: selection)
        f.permissionReply?(true)
        XCTAssertEqual(f.recordPrompts, 1)
        f.recordReply?(true)
        XCTAssertEqual(f.native.starts, 1)
        XCTAssertEqual(f.host.model.state.phase, .succeeded)
    }

    func testLateRecordGrantAfterBackgroundOrCloseNeverStartsAudio() throws {
        for close in [false, true] {
            let f = try HostFixture()
            defer { f.shutdown() }
            f.host.model.recognize(request: microphoneRequest, selection: SpeechBridgeSelectionDefault.shared)
            f.permissionReply?(true)
            XCTAssertNotNil(f.recordReply)
            if close { f.host.close() } else { f.host.background(); f.host.onForeground() }
            f.recordReply?(true)
            XCTAssertEqual(f.native.starts, 0)
            XCTAssertFalse(f.host.model.state.canFinishInput)
        }
    }

    func testSynchronousMicTerminalAndOldPermissionTaskCannotFinishNewTask() throws {
        let f = try HostFixture()
        defer { f.shutdown() }
        let callback = HostAsrCapture()
        let old = try XCTUnwrap(f.host.recognize(request: microphoneRequest,
            selection: SpeechBridgeSelectionDefault.shared, callback: callback) as? any SpeechBridgeMicrophoneTask)
        f.permissionReply?(true); f.recordReply?(true)
        XCTAssertEqual(callback.results, 1)
        f.native.synchronous = false
        f.host.model.recognize(request: microphoneRequest, selection: SpeechBridgeSelectionDefault.shared)
        f.permissionReply?(true); f.recordReply?(true)
        old.finishInput(); old.cancel()
        XCTAssertEqual(f.native.finishes + f.native.cancels, 0)
        f.host.model.finishInput()
        XCTAssertEqual(f.native.finishes, 1)
    }
    func testClosedHostDoesNotRetainItselfThroughModel() throws {
        let f = try HostFixture()
        defer { try? FileManager.default.removeItem(at: f.root) }
        weak var released = f.host
        _ = f.host.model
        f.host.close()
        f.host = nil
        XCTAssertNil(released)
    }
    func testPermissionReplyAfterBackgroundDoesNotStartRecognition() throws {
        let f = try HostFixture()
        defer { f.shutdown() }
        f.host.model.recognize(request: .init(locale: "en-US", input: SpeechBridgeInputAudioFile(reference: "fixture")),
                               selection: SpeechBridgeSelectionDefault.shared)
        XCTAssertNotNil(f.permissionReply)
        f.host.background()
        f.permissionReply?(true)
        XCTAssertEqual(f.native.starts, 0)
        XCTAssertEqual(f.host.model.state.phase, .cancelled)
    }

    func testPickerCancelPreservesCurrentReference() throws {
        let f = try HostFixture()
        defer { f.shutdown() }
        f.host.selectAudioFile()
        f.pickerReply?(f.source)
        let token = try XCTUnwrap(f.host.model.state.audioReference)
        XCTAssertEqual(f.host.model.state.audioDisplayName, "source.wav")
        f.host.selectAudioFile()
        f.pickerReply?(nil)
        XCTAssertEqual(f.host.model.state.audioReference, token)
    }

    func testPickerReplyAfterCloseDisposesCopiedInput() throws {
        let f = try HostFixture()
        defer { f.shutdown() }
        f.host.selectAudioFile()
        let reply = f.pickerReply
        f.host.close()
        reply?(f.source)
        XCTAssertNil(f.host.model.state.audioReference)
        XCTAssertFalse(FileManager.default.fileExists(atPath: f.inputsDirectory.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: f.source.path))
    }

    func testOlderPickerCannotOverwriteNewerSelection() throws {
        let f = try HostFixture()
        defer { f.shutdown() }
        f.host.selectAudioFile()
        let old = f.pickerReply
        f.host.selectAudioFile()
        f.pickerReply?(f.source)
        let token = f.host.model.state.audioReference
        old?(f.source)
        XCTAssertEqual(f.host.model.state.audioReference, token)
    }

    func testImportFailurePreservesSelectionAndShowsStableFailure() throws {
        let f = try HostFixture()
        defer { f.shutdown() }
        f.host.selectAudioFile(); f.pickerReply?(f.source)
        let token = f.host.model.state.audioReference
        f.host.selectAudioFile(); f.pickerReply?(f.root.appendingPathComponent("missing.wav"))
        XCTAssertEqual(f.host.model.state.audioReference, token)
        XCTAssertEqual(f.host.model.state.failure?.code, .invalidInput)
        XCTAssertEqual(f.host.model.state.failure?.reason, "audioImportFailed")
    }
}

@MainActor
private final class HostLocaleBackendRecorder {
    private(set) var locales: [String] = []
    private(set) var closedLocales: [String] = []
    var unavailableLocales: Set<String> = []
    func make(locale: String) -> any SpeechMicrophoneRecognitionBackend {
        locales.append(locale)
        return HostLocaleBackend(locale: locale, available: !unavailableLocales.contains(locale),
            onClose: { [weak self] in self?.closedLocales.append(locale) })
    }
}

@MainActor
private final class HostLocaleBackend: SpeechMicrophoneRecognitionBackend {
    nonisolated let backendId = "apple.speech.recognizer"
    nonisolated let capability: SpeechCapability = .asr
    nonisolated let supportedInputs: [SpeechRecognitionInputKind] = [.audioFile, .microphone]
    private let locale: String
    private let available: Bool
    private let onClose: @MainActor () -> Void
    init(locale: String, available: Bool = true, onClose: @escaping @MainActor () -> Void = {}) {
        self.locale = locale; self.available = available; self.onClose = onClose
    }
    func availability() async -> CapabilityAvailability {
        .init(status: available ? .available : .unavailable, reason: available ? nil : .unsupportedDevice,
            backendId: backendId, minOS: "15.0", offline: true)
    }
    func recognize(_ request: SpeechRecognitionRequest) async throws -> SpeechRecognitionResult {
        guard request.locale == locale else { throw KitError.invalidInput("locale mismatch") }
        guard available else {
            throw SpeechClientError.backendUnavailable(capability: .asr, backendId: backendId, reason: .unsupportedDevice)
        }
        return .init(backendId: backendId, transcript: locale)
    }
    func startMicrophone(_ request: SpeechMicrophoneRecognitionRequest) async throws
        -> any SpeechMicrophoneRecognitionTask {
        guard request.locale == locale else { throw KitError.invalidInput("locale mismatch") }
        guard available else {
            throw SpeechClientError.backendUnavailable(capability: .asr, backendId: backendId, reason: .unsupportedDevice)
        }
        return HostLocaleMicrophoneTask(locale: locale)
    }
    func drain() async {}
    func close() async { onClose() }
}

private struct HostLocaleMicrophoneTask: SpeechMicrophoneRecognitionTask {
    let locale: String
    func result() async throws -> SpeechRecognitionResult {
        .init(backendId: "apple.speech.recognizer", transcript: locale)
    }
    func finishInput() async {}
    func cancel() async {}
}

@MainActor
private final class HostFixture {
    let root: URL, inputsDirectory: URL, source: URL
    let native = HostNativeBridge()
    var permissionReply: ((Bool) -> Void)?
    var recordReply: ((Bool) -> Void)?
    var speechPrompts = 0
    var recordPrompts = 0
    var pickerReply: ((URL?) -> Void)?
    var host: SpeechHost!
    init() throws {
        root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        inputsDirectory = root.appendingPathComponent("inputs")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        source = root.appendingPathComponent("source.wav")
        try Data([0, 1]).write(to: source)
        host = SpeechHost(inputs: try SpeechInputStore(directory: inputsDirectory), bridge: native,
            requestAuthorization: { [weak self] in self?.speechPrompts += 1; self?.permissionReply = $0 },
            requestRecordAuthorization: { [weak self] in self?.recordPrompts += 1; self?.recordReply = $0 },
            presentPicker: { [weak self] in self?.pickerReply = $0 }, openSettings: {})
    }
    func shutdown() { host.close(); try? FileManager.default.removeItem(at: root) }
}
private final class HostTask: NSObject, BridgeTask { func cancel() {} }
private final class HostMicTask: NSObject, SpeechBridgeMicrophoneTask {
    let onFinish: () -> Void, onCancel: () -> Void
    init(onFinish: @escaping () -> Void, onCancel: @escaping () -> Void) { self.onFinish = onFinish; self.onCancel = onCancel }
    func finishInput() { onFinish() }
    func cancel() { onCancel() }
}
private final class HostAsrCapture: NSObject, SpeechBridgeAsrCallback {
    var results = 0
    func onResult(output: SpeechBridgeAsrResult) { results += 1 }
    func onFailure(failure: SpeechBridgeFailure) { XCTFail("unexpected failure") }
    func onCancelled() {}
}
private final class HostNativeBridge: NSObject, SpeechBridge {
    var starts = 0
    var finishes = 0
    var cancels = 0
    var synchronous = true
    var callback: SpeechBridgeAsrCallback?
    func queryBackends(capability: SpeechBridgeCapability, callback: SpeechBridgeBackendsCallback) -> BridgeTask {
        callback.onResult(backends: []); return HostTask()
    }
    func speak(request: SpeechBridgeTtsRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeTtsCallback) -> BridgeTask {
        callback.onResult(synthesis: .init(backendId: "fixture", voiceId: nil)); return HostTask()
    }
    func recognize(request: SpeechBridgeAsrRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeAsrCallback) -> BridgeTask {
        starts += 1
        self.callback = callback
        if synchronous { callback.onResult(output: .init(backendId: "fixture", transcript: "fixture", confidence: nil)) }
        if request.input is SpeechBridgeInputMicrophone {
            return HostMicTask(onFinish: { [weak self] in self?.finishes += 1 }, onCancel: { [weak self] in self?.cancels += 1 })
        }
        return HostTask()
    }
    func close(callback: SpeechBridgeCloseCallback) { callback.onClosed() }
}
