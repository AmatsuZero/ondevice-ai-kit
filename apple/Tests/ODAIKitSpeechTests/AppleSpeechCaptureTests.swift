import XCTest
import AVFAudio
import ODAIKitSpeech
@testable import ODAIKitAppleSpeech

@MainActor
final class AppleSpeechCaptureTests: XCTestCase {
    func testInvalidFormatAndTapFailureDoNotStartEngine() async throws {
        for invalidFormat in [true, false] {
            let engine = CaptureEngineFixture()
            engine.validFormat = !invalidFormat
            engine.tapError = invalidFormat ? nil : SpeechRecognitionError.platform(domain: "fixture.tap", code: 1)
            let sink = CaptureSinkFixture()
            let capture = AppleSpeechMicrophoneCapture(engine: engine,
                append: { sink.append($0) }, endAudio: { sink.end() })
            do { try await capture.start(); XCTFail("startup must fail") }
            catch { XCTAssertNotNil(error as? SpeechRecognitionError) }
            XCTAssertFalse(engine.events.contains("start"))
            XCTAssertFalse(engine.events.contains("remove"))
            XCTAssertEqual(sink.ended, 1)
        }
    }

    func testEngineStartFailureRemovesInstalledTapAndEndsRequest() async throws {
        let engine = CaptureEngineFixture()
        engine.startError = SpeechRecognitionError.platform(domain: "fixture.engine", code: 2)
        let sink = CaptureSinkFixture()
        let capture = AppleSpeechMicrophoneCapture(engine: engine,
            append: { sink.append($0) }, endAudio: { sink.end() })
        do { try await capture.start(); XCTFail("engine failure") }
        catch { XCTAssertEqual(error as? SpeechRecognitionError, .platform(domain: "fixture.engine", code: 2)) }
        XCTAssertEqual(engine.events, ["tap", "start", "stop", "remove"])
        XCTAssertEqual(sink.ended, 1)
        await capture.stop()
        XCTAssertEqual(engine.events, ["tap", "start", "stop", "remove"])
        XCTAssertEqual(sink.ended, 1)
    }

    func testStopClosesBufferAdmissionAndJoinsDeliveryBeforeEndAudio() async throws {
        let engine = CaptureEngineFixture()
        let sink = CaptureSinkFixture(blockAppend: true)
        let capture = AppleSpeechMicrophoneCapture(engine: engine,
            append: { sink.append($0) }, endAudio: { sink.end() })
        try await capture.start()
        let callback = try XCTUnwrap(engine.deliver)
        let entered = expectation(description: "buffer append entered")
        let delivered = Task.detached {
            sink.entered = { entered.fulfill() }
            callback(Self.buffer())
        }
        await fulfillment(of: [entered], timeout: 1)
        var stopped = 0
        let first = Task { await capture.stop(); stopped += 1 }
        let second = Task { await capture.stop(); stopped += 1 }
        for _ in 0..<1_000 where !engine.events.contains("remove") { await Task.yield() }
        XCTAssertEqual(engine.events, ["tap", "start", "stop", "remove"])
        XCTAssertEqual(sink.ended, 0)
        XCTAssertEqual(stopped, 0)
        callback(Self.buffer()) // A queued old callback cannot enter after stop.
        sink.unblock()
        await delivered.value
        await first.value; await second.value
        XCTAssertEqual(sink.appended, 1)
        XCTAssertEqual(sink.ended, 1)
        XCTAssertEqual(stopped, 2)
    }

    #if os(iOS)
    func testExpectedSessionCategoryNotificationDoesNotInvalidateHealthyCapture() {
        let expected = AppleSpeechMicrophoneRouteSnapshot(category: "record", mode: "measurement", options: 0,
            inputPorts: ["fixture.mic"], outputPorts: [], sampleRate: 48_000, inputChannels: 1)
        let notification = Notification(name: AVAudioSession.routeChangeNotification,
            userInfo: [AVAudioSessionRouteChangeReasonKey: AVAudioSession.RouteChangeReason.categoryChange.rawValue])
        XCTAssertFalse(expected.requiresInvalidation(notification, current: expected))
    }

    func testInputLossUnknownReasonAndUnexpectedConfigurationStillInvalidateCapture() {
        let expected = AppleSpeechMicrophoneRouteSnapshot(category: "record", mode: "measurement", options: 0,
            inputPorts: ["fixture.mic"], outputPorts: [], sampleRate: 48_000, inputChannels: 1)
        for reason in [nil, 0, 1, 2, 4, 6, 7, 8, 999] as [UInt?] {
            let info: [AnyHashable: Any] = reason.map { [AVAudioSessionRouteChangeReasonKey: $0] } ?? [:]
            XCTAssertTrue(expected.requiresInvalidation(Notification(name: AVAudioSession.routeChangeNotification,
                userInfo: info), current: expected), "reason=\(String(describing: reason))")
        }
        let categoryChanged = Notification(name: AVAudioSession.routeChangeNotification,
            userInfo: [AVAudioSessionRouteChangeReasonKey: AVAudioSession.RouteChangeReason.categoryChange.rawValue])
        let changed = [
            AppleSpeechMicrophoneRouteSnapshot(category: "playback", mode: "measurement", options: 0,
                inputPorts: ["fixture.mic"], outputPorts: [], sampleRate: 48_000, inputChannels: 1),
            AppleSpeechMicrophoneRouteSnapshot(category: "record", mode: "default", options: 0,
                inputPorts: ["fixture.mic"], outputPorts: [], sampleRate: 48_000, inputChannels: 1),
            AppleSpeechMicrophoneRouteSnapshot(category: "record", mode: "measurement", options: 1,
                inputPorts: ["fixture.mic"], outputPorts: [], sampleRate: 48_000, inputChannels: 1),
            AppleSpeechMicrophoneRouteSnapshot(category: "record", mode: "measurement", options: 0,
                inputPorts: [], outputPorts: [], sampleRate: 48_000, inputChannels: 1),
            AppleSpeechMicrophoneRouteSnapshot(category: "record", mode: "measurement", options: 0,
                inputPorts: ["other.mic"], outputPorts: [], sampleRate: 48_000, inputChannels: 1),
            AppleSpeechMicrophoneRouteSnapshot(category: "record", mode: "measurement", options: 0,
                inputPorts: ["fixture.mic"], outputPorts: ["speaker"], sampleRate: 48_000, inputChannels: 1),
            AppleSpeechMicrophoneRouteSnapshot(category: "record", mode: "measurement", options: 0,
                inputPorts: ["fixture.mic"], outputPorts: [], sampleRate: 16_000, inputChannels: 1),
            AppleSpeechMicrophoneRouteSnapshot(category: "record", mode: "measurement", options: 0,
                inputPorts: ["fixture.mic"], outputPorts: [], sampleRate: 48_000, inputChannels: 0)
        ]
        for current in changed { XCTAssertTrue(expected.requiresInvalidation(categoryChanged, current: current)) }
        let invalid = AppleSpeechMicrophoneRouteSnapshot(category: "record", mode: "measurement", options: 0,
            inputPorts: [], outputPorts: [], sampleRate: 0, inputChannels: 0)
        XCTAssertTrue(invalid.requiresInvalidation(categoryChanged, current: invalid))
    }
    #endif

    nonisolated private static func buffer() -> AVAudioPCMBuffer {
        let format = AVAudioFormat(standardFormatWithSampleRate: 16_000, channels: 1)!
        let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 16)!
        buffer.frameLength = 16
        return buffer
    }
}

@MainActor
private final class CaptureEngineFixture: AppleSpeechMicrophoneEngine {
    var validFormat = true
    var tapError: Error?
    var startError: Error?
    var events: [String] = []
    var deliver: (@Sendable (AVAudioPCMBuffer) -> Void)?
    func hasValidInputFormat() -> Bool { validFormat }
    func installTap(deliver: @escaping @Sendable (AVAudioPCMBuffer) -> Void) throws {
        events.append("tap")
        if let tapError { throw tapError }
        self.deliver = deliver
    }
    func prepareAndStart() throws {
        events.append("start")
        if let startError { throw startError }
    }
    func stop() { events.append("stop") }
    func removeTap() { events.append("remove") }
}

private final class CaptureSinkFixture: @unchecked Sendable {
    private let lock = NSLock()
    private let gate = DispatchSemaphore(value: 0)
    private let blockAppend: Bool
    private var appendCount = 0
    private var endCount = 0
    var entered: (@Sendable () -> Void)?
    init(blockAppend: Bool = false) { self.blockAppend = blockAppend }
    var appended: Int { lock.lock(); defer { lock.unlock() }; return appendCount }
    var ended: Int { lock.lock(); defer { lock.unlock() }; return endCount }
    func append(_ buffer: AVAudioPCMBuffer) {
        entered?()
        if blockAppend { _ = gate.wait(timeout: .now() + 5) }
        lock.lock(); appendCount += 1; lock.unlock()
    }
    func end() { lock.lock(); endCount += 1; lock.unlock() }
    func unblock() { gate.signal() }
}
