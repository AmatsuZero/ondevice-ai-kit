import Foundation
import XCTest
import ODAIKitCore
import ODAIKitSpeech
import ODAIKitAppleSpeech
import AVFAudio

@MainActor
final class SpeechAudioSessionTests: XCTestCase {
    private let recordingConfiguration = "configure:\(AVAudioSession.Category.record.rawValue)"
    func testLeaseIsExclusiveAndRestoresConfigurationAfterDeactivation() async throws {
        let session = TestSpeechAudioSession()
        let original = session.configuration
        let provider = DemoSpeechAudioSessionProvider(session: session)
        let lease = try await provider.acquire()
        XCTAssertEqual(session.events, [recordingConfiguration, "active:true"])
        XCTAssertEqual(session.configuration.mode, AVAudioSession.Mode.measurement.rawValue)
        XCTAssertEqual(session.configuration.options, 0)
        do { _ = try await provider.acquire(); XCTFail("must be exclusive") }
        catch { XCTAssertEqual(error as? KitError, .busy) }
        try await lease.release(); try await lease.release()
        XCTAssertEqual(session.configuration, original)
        XCTAssertEqual(session.events, [recordingConfiguration, "active:true", "active:false", "configure:playback"])
        let next = try await provider.acquire()
        try await next.release()
    }

    func testActivationFailureRollsBackAndAllowsRetry() async throws {
        let session = TestSpeechAudioSession()
        let original = session.configuration
        let provider = DemoSpeechAudioSessionProvider(session: session)
        session.failActivation = true
        do { _ = try await provider.acquire(); XCTFail("activation must fail") }
        catch { XCTAssertEqual(error as? SpeechRecognitionError, .platform(domain: "fixture.session", code: 1)) }
        XCTAssertEqual(session.events, [recordingConfiguration, "active:true", "active:false", "configure:playback"])
        XCTAssertEqual(session.configuration, original)
        session.failActivation = false
        let lease = try await provider.acquire()
        try await lease.release()
    }

    func testDeactivationFailureStillRestoresAndCannotPretendSuccess() async throws {
        let session = TestSpeechAudioSession()
        let original = session.configuration
        let provider = DemoSpeechAudioSessionProvider(session: session)
        let lease = try await provider.acquire()
        session.failDeactivation = true
        do { try await lease.release(); XCTFail("release failure must be visible") }
        catch { XCTAssertEqual(error as? SpeechRecognitionError, .platform(domain: "fixture.session", code: 2)) }
        XCTAssertEqual(session.configuration, original)
        XCTAssertEqual(session.events.suffix(2), ["active:false", "configure:playback"])
        try await lease.release()
        XCTAssertEqual(session.events.count, 4)
    }
}

@MainActor
private final class TestSpeechAudioSession: DemoSpeechAudioSessionControl {
    var configuration = DemoSpeechAudioSessionConfiguration(category: "playback", mode: "default", options: 1)
    var events: [String] = []
    var failActivation = false
    var failDeactivation = false
    func configure(_ configuration: DemoSpeechAudioSessionConfiguration) throws {
        self.configuration = configuration
        events.append("configure:\(configuration.category)")
    }
    func setActive(_ active: Bool) throws {
        events.append("active:\(active)")
        if active && failActivation { throw SpeechRecognitionError.platform(domain: "fixture.session", code: 1) }
        if !active && failDeactivation { throw SpeechRecognitionError.platform(domain: "fixture.session", code: 2) }
    }
}
