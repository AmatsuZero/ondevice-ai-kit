import XCTest
import ODAIKitCore
@testable import ODAIKitSpeech

private actor QueryTtsBackend: SpeechSynthesisBackend {
    nonisolated let backendId: String
    nonisolated let capability: SpeechCapability = .tts
    let available: Bool
    let availabilityStarted: XCTestExpectation?
    var availabilityContinuation: CheckedContinuation<Void, Never>?
    private(set) var speakCount = 0
    private(set) var closeCount = 0

    init(_ id: String, available: Bool = true, availabilityStarted: XCTestExpectation? = nil) {
        backendId = id
        self.available = available
        self.availabilityStarted = availabilityStarted
    }

    func availability() async -> CapabilityAvailability {
        if let availabilityStarted {
            await withCheckedContinuation { continuation in
                availabilityContinuation = continuation
                availabilityStarted.fulfill()
            }
        }
        return CapabilityAvailability(status: available ? .available : .unavailable,
                                      reason: available ? nil : .unsupportedDevice,
                                      backendId: backendId, minOS: "15.0", offline: !backendId.contains("cloud"))
    }

    func releaseAvailability() { availabilityContinuation?.resume(); availabilityContinuation = nil }
    func speak(_ request: SpeechSynthesisRequest) -> SpeechSynthesisResult {
        speakCount += 1
        return SpeechSynthesisResult(backendId: backendId)
    }
    func close() { closeCount += 1 }
}

private actor QueryAsrBackend: SpeechRecognitionBackend {
    nonisolated let backendId: String
    nonisolated let capability: SpeechCapability = .asr
    private(set) var recognizeCount = 0

    init(_ id: String) { backendId = id }
    func availability() -> CapabilityAvailability {
        CapabilityAvailability(status: .available, backendId: backendId)
    }
    func recognize(_ request: SpeechRecognitionRequest) -> SpeechRecognitionResult {
        recognizeCount += 1
        return SpeechRecognitionResult(backendId: backendId, transcript: "fixture")
    }
    func drain() {}
    func close() {}
}

final class SpeechBackendQueryTests: XCTestCase {
    func testListsOnlyOwnCapabilitySortedById() async throws {
        let z = QueryTtsBackend("z.tts")
        let a = QueryTtsBackend("a.tts")
        let tts = try SpeechSynthesisClient(defaultBackendId: a.backendId, backends: [z, a])
        let asrBackend = QueryAsrBackend("a.asr")
        let asr = try SpeechRecognitionClient(defaultBackendId: asrBackend.backendId, backends: [asrBackend])

        let ttsDescriptors = try await tts.listBackends()
        let asrDescriptors = try await asr.listBackends()
        XCTAssertEqual(ttsDescriptors.map(\.backendId), ["a.tts", "z.tts"])
        XCTAssertEqual(ttsDescriptors.map(\.capability), [.tts, .tts])
        XCTAssertEqual(asrDescriptors.map(\.backendId), ["a.asr"])
        XCTAssertEqual(asrDescriptors.map(\.capability), [.asr])
        let zSpeakCount = await z.speakCount
        let aSpeakCount = await a.speakCount
        let recognizeCount = await asrBackend.recognizeCount
        XCTAssertEqual(zSpeakCount, 0)
        XCTAssertEqual(aSpeakCount, 0)
        XCTAssertEqual(recognizeCount, 0)
        await tts.close()
        await asr.close()
    }

    func testQueryDoesNotChangeDefaultOrFallback() async throws {
        let local = QueryTtsBackend("a.tts")
        let unavailable = QueryTtsBackend("z.cloud.tts", available: false)
        let client = try SpeechSynthesisClient(defaultBackendId: local.backendId, backends: [unavailable, local])
        let descriptors = try await client.listBackends()
        XCTAssertEqual(descriptors.map(\.backendId), ["a.tts", "z.cloud.tts"])
        XCTAssertEqual(descriptors[1].availability.status, .unavailable)
        XCTAssertEqual(descriptors[1].availability.reason, .unsupportedDevice)
        XCTAssertEqual(descriptors[1].availability.minOS, "15.0")
        XCTAssertFalse(descriptors[1].availability.offline)

        _ = try await client.speak(.init(text: "hello", locale: "en-US"))
        let localSpeakCount = await local.speakCount
        XCTAssertEqual(localSpeakCount, 1)
        do {
            _ = try await client.speak(.init(text: "hello", locale: "en-US"), backend: .explicit(unavailable.backendId))
            XCTFail("unavailable explicit backend must not fall back")
        } catch {
            XCTAssertEqual(error as? SpeechClientError,
                           .backendUnavailable(capability: .tts, backendId: unavailable.backendId, reason: .unsupportedDevice))
        }
        let unavailableSpeakCount = await unavailable.speakCount
        XCTAssertEqual(unavailableSpeakCount, 0)
        await client.close()
    }

    func testCloseWhileAvailabilitySuspendedRejectsQuery() async throws {
        let started = expectation(description: "availability started")
        let backend = QueryTtsBackend("suspended.tts", availabilityStarted: started)
        let client = try SpeechSynthesisClient(defaultBackendId: backend.backendId, backends: [backend])
        let query = Task { try await client.listBackends() }
        await fulfillment(of: [started], timeout: 1)
        await client.close()
        await backend.releaseAvailability()

        do {
            _ = try await query.value
            XCTFail("closed client must reject an in-flight query")
        } catch {
            XCTAssertEqual(error as? SpeechClientError, .closed)
        }
    }

    func testQueryCancellationDoesNotCloseClient() async throws {
        let backend = QueryTtsBackend("cancel.tts")
        let client = try SpeechSynthesisClient(defaultBackendId: backend.backendId, backends: [backend])
        let query = Task { try await client.listBackends() }
        query.cancel()
        _ = try? await query.value
        _ = try await client.speak(.init(text: "still open", locale: "en-US"))
        let closeCount = await backend.closeCount
        XCTAssertEqual(closeCount, 0)
        await client.close()
    }
}
