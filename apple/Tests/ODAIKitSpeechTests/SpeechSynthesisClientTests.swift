import XCTest
import ODAIKitCore
@testable import ODAIKitSpeech

private actor FakeTtsBackend: SpeechSynthesisBackend {
    nonisolated let backendId: String
    nonisolated let capability: SpeechCapability = .tts
    let available: Bool
    var spoken: [String] = []
    init(_ id: String, available: Bool = true) { backendId = id; self.available = available }
    func availability() -> CapabilityAvailability {
        CapabilityAvailability(status: available ? .available : .unavailable,
                               reason: available ? nil : .unsupportedDevice, backendId: backendId)
    }
    func speak(_ request: SpeechSynthesisRequest) async throws -> SpeechSynthesisResult {
        spoken.append(request.text)
        return SpeechSynthesisResult(backendId: backendId, voiceId: "test.voice")
    }
    func close() {}
}

final class SpeechSynthesisClientTests: XCTestCase {
    func testDefaultAndExplicitSelectionDispatchToExactlyOneBackend() async throws {
        let local = FakeTtsBackend("apple.avspeech")
        let cloud = FakeTtsBackend("test.cloud.tts")
        let client = try SpeechSynthesisClient(defaultBackendId: local.backendId, backends: [local, cloud])
        let request = SpeechSynthesisRequest(text: "hello", locale: "en-US")
        let defaultResult = try await client.speak(request)
        XCTAssertEqual(defaultResult.backendId, local.backendId)
        let explicitResult = try await client.speak(request, backend: .explicit(cloud.backendId))
        XCTAssertEqual(explicitResult.backendId, cloud.backendId)
        let localSpoken = await local.spoken, cloudSpoken = await cloud.spoken
        XCTAssertEqual(localSpoken, ["hello"])
        XCTAssertEqual(cloudSpoken, ["hello"])
        do { _ = try await client.speak(request, backend: .explicit("missing")); XCTFail("missing") }
        catch { XCTAssertEqual(error as? SpeechClientError, .backendNotRegistered(capability: .tts, backendId: "missing")) }
        await client.close()
    }

    func testUnavailableExplicitBackendDoesNotFallBack() async throws {
        let local = FakeTtsBackend("apple.avspeech")
        let cloud = FakeTtsBackend("test.cloud.tts", available: false)
        let client = try SpeechSynthesisClient(defaultBackendId: local.backendId, backends: [local, cloud])
        do { _ = try await client.speak(.init(text: "hello", locale: "en-US"), backend: .explicit(cloud.backendId)); XCTFail("unavailable") }
        catch { XCTAssertEqual(error as? SpeechClientError, .backendUnavailable(capability: .tts, backendId: cloud.backendId, reason: .unsupportedDevice)) }
        let localSpoken = await local.spoken
        XCTAssertTrue(localSpoken.isEmpty)
    }
}
