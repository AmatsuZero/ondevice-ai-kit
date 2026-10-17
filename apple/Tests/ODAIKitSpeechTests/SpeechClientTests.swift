import XCTest
import ODAIKitCore
@testable import ODAIKitSpeech

private actor FakeSpeechBackend: SpeechBackend {
    nonisolated let backendId: String
    let capability: SpeechCapability
    let available: Bool
    var closeCount = 0
    init(_ id: String, capability: SpeechCapability, available: Bool = true) { backendId = id; self.capability = capability; self.available = available }
    func availability() -> CapabilityAvailability { CapabilityAvailability(status: available ? .available : .unavailable, reason: available ? nil : .unsupportedDevice, backendId: backendId, offline: !backendId.contains("cloud")) }
    func close() { closeCount += 1 }
}

final class SpeechClientTests: XCTestCase {
    func testTtsExplicitSelectionIsStrictAndCloseIsIdempotent() async throws {
        let local = FakeSpeechBackend("apple.avspeech", capability: .tts)
        let cloud = FakeSpeechBackend("microsoft.azure.speech", capability: .tts, available: false)
        let client = try SpeechClient(capability: .tts, defaultBackendId: local.backendId, registrations: [
            .init(capability: .tts, backend: local), .init(capability: .tts, backend: cloud)
        ])
        let result = try await client.select()
        XCTAssertEqual(result.backendId, local.backendId)
        let descriptors = await client.listBackends()
        XCTAssertEqual(descriptors.map(\.backendId), ["apple.avspeech", "microsoft.azure.speech"])
        do { _ = try await client.select(.explicit(cloud.backendId)); XCTFail("unavailable") }
        catch { XCTAssertEqual(error as? SpeechClientError, .backendUnavailable(capability: .tts, backendId: cloud.backendId, reason: .unsupportedDevice)) }
        do { _ = try await client.select(.explicit("missing")); XCTFail("missing") }
        catch { XCTAssertEqual(error as? SpeechClientError, .backendNotRegistered(capability: .tts, backendId: "missing")) }
        await client.close(); await client.close()
        do { _ = try await client.select(); XCTFail("closed") }
        catch { XCTAssertEqual(error as? SpeechClientError, .closed) }
        let localCloseCount = await local.closeCount; let cloudCloseCount = await cloud.closeCount
        XCTAssertEqual(localCloseCount, 1); XCTAssertEqual(cloudCloseCount, 1)
    }

    func testRegistrationRejectsDuplicateCapabilityAndDefaultMismatch() throws {
        let backend = FakeSpeechBackend("apple.avspeech", capability: .tts)
        let registration = SpeechBackendRegistration(capability: .tts, backend: backend)
        XCTAssertThrowsError(try SpeechClient(capability: .tts, defaultBackendId: backend.backendId, registrations: [registration, registration]))
        XCTAssertThrowsError(try SpeechClient(capability: .tts, defaultBackendId: backend.backendId, registrations: [.init(capability: .asr, backend: backend)]))
        let asr = FakeSpeechBackend("apple.speech.recognizer", capability: .asr)
        let asrClient = try SpeechClient(capability: .asr, defaultBackendId: asr.backendId, registrations: [.init(capability: .asr, backend: asr)])
        _ = asrClient
        XCTAssertThrowsError(try SpeechClient(capability: .asr, defaultBackendId: backend.backendId, registrations: [.init(capability: .asr, backend: backend)]))
    }
}
