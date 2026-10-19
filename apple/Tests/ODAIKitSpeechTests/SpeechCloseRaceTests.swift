import XCTest
import ODAIKitCore
@testable import ODAIKitSpeech

private actor SuspendedSpeechBackend: SpeechSynthesisBackend {
    nonisolated let backendId = "test.suspended.tts"
    nonisolated let capability: SpeechCapability = .tts
    let availabilityStarted: XCTestExpectation?
    let closeStarted: XCTestExpectation?
    private var availabilityContinuation: CheckedContinuation<Void, Never>?
    private var closeContinuation: CheckedContinuation<Void, Never>?
    private(set) var speakCount = 0
    private(set) var closeCount = 0

    init(availabilityStarted: XCTestExpectation? = nil, closeStarted: XCTestExpectation? = nil) {
        self.availabilityStarted = availabilityStarted
        self.closeStarted = closeStarted
    }

    func availability() async -> CapabilityAvailability {
        if let availabilityStarted {
            await withCheckedContinuation {
                availabilityContinuation = $0
                availabilityStarted.fulfill()
            }
        }
        return CapabilityAvailability(status: .available, backendId: backendId)
    }

    func speak(_ request: SpeechSynthesisRequest) -> SpeechSynthesisResult {
        speakCount += 1
        return SpeechSynthesisResult(backendId: backendId)
    }

    func close() async {
        closeCount += 1
        if let closeStarted {
            await withCheckedContinuation {
                closeContinuation = $0
                closeStarted.fulfill()
            }
        }
    }

    func releaseAvailability() { availabilityContinuation?.resume(); availabilityContinuation = nil }
    func releaseClose() { closeContinuation?.resume(); closeContinuation = nil }
}

final class SpeechCloseRaceTests: XCTestCase {
    func testRegistryRejectsSelectionWhenCloseWinsDuringAvailability() async throws {
        let started = expectation(description: "availability started")
        let backend = SuspendedSpeechBackend(availabilityStarted: started)
        let client = try SpeechClient(capability: .tts, defaultBackendId: backend.backendId,
                                      registrations: [.init(capability: .tts, backend: backend)])
        let selection = Task { try await client.select() }
        await fulfillment(of: [started], timeout: 1)
        await client.close()
        await backend.releaseAvailability()
        do {
            _ = try await selection.value
            XCTFail("selection resumed after close must reject")
        } catch { XCTAssertEqual(error as? SpeechClientError, .closed) }
    }

    func testTtsDoesNotDispatchWhenClosedDuringAvailability() async throws {
        let started = expectation(description: "availability started")
        let backend = SuspendedSpeechBackend(availabilityStarted: started)
        let client = try SpeechSynthesisClient(defaultBackendId: backend.backendId, backends: [backend])
        let speech = Task { try await client.speak(.init(text: "hello", locale: "en-US")) }
        await fulfillment(of: [started], timeout: 1)
        await client.close()
        await backend.releaseAvailability()
        do {
            _ = try await speech.value
            XCTFail("closed client must not dispatch pending speech")
        } catch { XCTAssertEqual(error as? SpeechClientError, .closed) }
        let count = await backend.speakCount
        XCTAssertEqual(count, 0)
    }

    func testRegistryConcurrentCloseWaitsForSameBackendRelease() async throws {
        let started = expectation(description: "close started")
        let backend = SuspendedSpeechBackend(closeStarted: started)
        let client = try SpeechClient(capability: .tts, defaultBackendId: backend.backendId,
                                      registrations: [.init(capability: .tts, backend: backend)])
        let first = Task { await client.close() }
        await fulfillment(of: [started], timeout: 1)
        let returned = expectation(description: "second close returned early")
        returned.isInverted = true
        let second = Task { await client.close(); returned.fulfill() }
        await fulfillment(of: [returned], timeout: 0.05)
        await backend.releaseClose()
        await first.value
        await second.value
        let count = await backend.closeCount
        XCTAssertEqual(count, 1)
    }
}
