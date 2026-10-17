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
    func testRecognitionRequestTrimsAndCanonicalizesLocale() throws {
        let url = URL(fileURLWithPath: "/tmp/example.wav")
        let request = try SpeechRecognitionRequest(locale: "  en_us  ", audioFile: url)
        XCTAssertEqual(request.locale, "en_US")
        XCTAssertEqual(request.audioFile, url)
    }

    func testRecognitionRequestRejectsBlankLocale() {
        XCTAssertThrowsError(try SpeechRecognitionRequest(locale: " \n\t ", audioFile: URL(fileURLWithPath: "/tmp/example.wav"))) { error in
            guard case .invalidInput = error as? KitError else {
                return XCTFail("expected KitError.invalidInput, got \(error)")
            }
        }
    }

    func testRecognitionRequestRejectsNonFileURL() {
        XCTAssertThrowsError(try SpeechRecognitionRequest(locale: "en-US", audioFile: URL(string: "https://example.com/audio.wav")!)) { error in
            guard case .invalidInput = error as? KitError else {
                return XCTFail("expected KitError.invalidInput, got \(error)")
            }
        }
    }

    func testRecognitionClientDispatchesDefaultAndExplicitBackendsStrictly() async throws {
        let local = FakeRecognitionBackend("apple.speech.recognizer")
        let unavailable = FakeRecognitionBackend("microsoft.azure.speech", available: false)
        let client = try SpeechRecognitionClient(defaultBackendId: local.backendId, backends: [local, unavailable])
        let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: URL(fileURLWithPath: "/tmp/example.wav"))

        let defaultResult = try await client.recognize(request)
        XCTAssertEqual(defaultResult.backendId, local.backendId)
        XCTAssertEqual(defaultResult.transcript, "local transcript")
        let explicitResult = try await client.recognize(request, backend: .explicit(local.backendId))
        XCTAssertEqual(explicitResult.backendId, local.backendId)
        let localCount = await local.recognizeCount
        XCTAssertEqual(localCount, 2)

        do {
            _ = try await client.recognize(request, backend: .explicit(unavailable.backendId))
            XCTFail("unavailable backend must not be selected")
        } catch {
            XCTAssertEqual(error as? SpeechClientError,
                           .backendUnavailable(capability: .asr, backendId: unavailable.backendId, reason: .unsupportedDevice))
        }
        let unavailableCount = await unavailable.recognizeCount
        XCTAssertEqual(unavailableCount, 0)

        do {
            _ = try await client.recognize(request, backend: .explicit("missing"))
            XCTFail("missing backend must not fall back")
        } catch {
            XCTAssertEqual(error as? SpeechClientError,
                           .backendNotRegistered(capability: .asr, backendId: "missing"))
        }
    }

    func testRecognitionClientRejectsAfterClose() async throws {
        let backend = FakeRecognitionBackend("apple.speech.recognizer")
        let client = try SpeechRecognitionClient(defaultBackendId: backend.backendId, backends: [backend])
        let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: URL(fileURLWithPath: "/tmp/example.wav"))
        await client.close()

        do {
            _ = try await client.recognize(request)
            XCTFail("closed client must reject new recognition")
        } catch {
            XCTAssertEqual(error as? SpeechClientError, .closed)
        }
    }

    func testRecognitionClientDoesNotDispatchIfClosedDuringAvailability() async throws {
        let started = expectation(description: "availability suspended")
        let backend = FakeRecognitionBackend("apple.speech.recognizer", availabilityStarted: started)
        let client = try SpeechRecognitionClient(defaultBackendId: backend.backendId, backends: [backend])
        let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: URL(fileURLWithPath: "/tmp/example.wav"))
        let operation = Task { try await client.recognize(request) }
        await fulfillment(of: [started], timeout: 1)
        await client.close()
        await backend.resumeAvailability()
        do {
            _ = try await operation.value
            XCTFail("a pending selection must not dispatch after close")
        } catch {
            XCTAssertEqual(error as? SpeechClientError, .closed)
        }
        let count = await backend.recognizeCount
        XCTAssertEqual(count, 0)
    }

    func testRecognitionClientConcurrentCloseWaitsForBackendRelease() async throws {
        let started = expectation(description: "backend close started")
        let backend = FakeRecognitionBackend("apple.speech.recognizer", closeStarted: started)
        let client = try SpeechRecognitionClient(defaultBackendId: backend.backendId, backends: [backend])
        let first = Task { await client.close() }
        await fulfillment(of: [started], timeout: 1)
        let earlyReturn = expectation(description: "second close must wait")
        earlyReturn.isInverted = true
        let second = Task {
            await client.close()
            earlyReturn.fulfill()
        }
        await fulfillment(of: [earlyReturn], timeout: 0.05)
        await backend.resumeClose()
        await first.value
        await second.value
        let closeCount = await backend.closeCount
        XCTAssertEqual(closeCount, 1)
    }

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

private actor FakeRecognitionBackend: SpeechRecognitionBackend {
    nonisolated let backendId: String
    nonisolated let capability: SpeechCapability = .asr
    let available: Bool
    var recognizeCount = 0
    let availabilityStarted: XCTestExpectation?
    var availabilityContinuation: CheckedContinuation<Void, Never>?
    let closeStarted: XCTestExpectation?
    var closeContinuation: CheckedContinuation<Void, Never>?
    var closeCount = 0

    init(_ id: String, available: Bool = true, availabilityStarted: XCTestExpectation? = nil,
         closeStarted: XCTestExpectation? = nil) {
        backendId = id
        self.available = available
        self.availabilityStarted = availabilityStarted
        self.closeStarted = closeStarted
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
                                      backendId: backendId)
    }

    func resumeAvailability() { availabilityContinuation?.resume(); availabilityContinuation = nil }

    func recognize(_ request: SpeechRecognitionRequest) async throws -> SpeechRecognitionResult {
        recognizeCount += 1
        return SpeechRecognitionResult(backendId: backendId, transcript: "local transcript", confidence: 0.9)
    }

    func close() async {
        closeCount += 1
        if let closeStarted {
            await withCheckedContinuation { continuation in
                closeContinuation = continuation
                closeStarted.fulfill()
            }
        }
    }

    func resumeClose() { closeContinuation?.resume(); closeContinuation = nil }
}
