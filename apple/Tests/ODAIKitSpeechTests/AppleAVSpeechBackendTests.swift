import XCTest
import ODAIKitCore
@testable import ODAIKitAppleSpeech
@testable import ODAIKitSpeech

@MainActor
final class AppleAVSpeechBackendTests: XCTestCase {
    func testCloseRejectsNewSpeech() async {
        let backend = AppleAVSpeechBackend()
        XCTAssertEqual(backend.backendId, "apple.avspeech")
        await backend.close()
        do {
            _ = try await backend.speak(.init(text: "after close", locale: "en-US"))
            XCTFail("closed backend must reject speech")
        } catch { XCTAssertEqual(error as? KitError, .closed) }
    }

    func testEmptyTextIsRejectedBeforeVoiceLookup() async {
        let backend = AppleAVSpeechBackend()
        do {
            _ = try await backend.speak(.init(text: "", locale: "en-US"))
            XCTFail("empty text must be rejected")
        } catch { XCTAssertEqual(error as? KitError, .invalidInput("empty text")) }
        await backend.close()
    }
}
