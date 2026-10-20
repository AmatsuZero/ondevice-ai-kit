import Foundation
import XCTest
#if ODAI_DEMO_BINDING
import ODAIKitDemo
#else
import ODAIKitBinding
#endif

@MainActor
final class SpeechHostTests: XCTestCase {
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
private final class HostFixture {
    let root: URL, inputsDirectory: URL, source: URL
    let native = HostNativeBridge()
    var permissionReply: ((Bool) -> Void)?
    var pickerReply: ((URL?) -> Void)?
    var host: SpeechHost!
    init() throws {
        root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        inputsDirectory = root.appendingPathComponent("inputs")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        source = root.appendingPathComponent("source.wav")
        try Data([0, 1]).write(to: source)
        host = SpeechHost(inputs: try SpeechInputStore(directory: inputsDirectory), bridge: native,
            requestAuthorization: { [weak self] in self?.permissionReply = $0 },
            presentPicker: { [weak self] in self?.pickerReply = $0 }, openSettings: {})
    }
    func shutdown() { host.close(); try? FileManager.default.removeItem(at: root) }
}
private final class HostTask: NSObject, BridgeTask { func cancel() {} }
private final class HostNativeBridge: NSObject, SpeechBridge {
    var starts = 0
    func queryBackends(capability: SpeechBridgeCapability, callback: SpeechBridgeBackendsCallback) -> BridgeTask {
        callback.onResult(backends: []); return HostTask()
    }
    func speak(request: SpeechBridgeTtsRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeTtsCallback) -> BridgeTask {
        callback.onResult(synthesis: .init(backendId: "fixture", voiceId: nil)); return HostTask()
    }
    func recognize(request: SpeechBridgeAsrRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeAsrCallback) -> BridgeTask {
        starts += 1
        callback.onResult(output: .init(backendId: "fixture", transcript: "fixture", confidence: nil)); return HostTask()
    }
    func close(callback: SpeechBridgeCloseCallback) { callback.onClosed() }
}
