import XCTest
import ODAIKitCore
@testable import ODAIKitSpeech

private actor LegacyFileBackend: SpeechRecognitionBackend {
    nonisolated let backendId: String
    nonisolated let capability: SpeechCapability = .asr

    init(_ backendId: String) { self.backendId = backendId }

    func availability() async -> CapabilityAvailability {
        CapabilityAvailability(status: .available, backendId: backendId, minOS: "15.0", offline: true)
    }

    func recognize(_ request: SpeechRecognitionRequest) async throws -> SpeechRecognitionResult {
        SpeechRecognitionResult(backendId: backendId, transcript: "fixture")
    }

    func drain() async {}
    func close() async {}
}

private actor MultiProtocolTtsBackend: SpeechSynthesisBackend, SpeechRecognitionBackend {
    nonisolated let backendId: String
    nonisolated let capability: SpeechCapability = .tts
    nonisolated let supportedInputs: [SpeechRecognitionInputKind] = [.microphone]

    init(_ backendId: String) { self.backendId = backendId }

    func availability() async -> CapabilityAvailability {
        CapabilityAvailability(status: .available, backendId: backendId, minOS: "15.0", offline: true)
    }

    func speak(_ request: SpeechSynthesisRequest) async throws -> SpeechSynthesisResult {
        SpeechSynthesisResult(backendId: backendId)
    }

    func recognize(_ request: SpeechRecognitionRequest) async throws -> SpeechRecognitionResult {
        throw SpeechRecognitionInputError.unsupportedInput
    }

    func drain() async {}

    func close() async {}
}

private actor InputMetadataBackend: SpeechRecognitionBackend {
    nonisolated let backendId = "typed.inputs"
    nonisolated let capability: SpeechCapability = .asr
    nonisolated let supportedInputs: [SpeechRecognitionInputKind] = [.audioFile, .microphone]
    private var available = true

    func setAvailable(_ value: Bool) { available = value }
    func availability() async -> CapabilityAvailability {
        CapabilityAvailability(status: available ? .available : .unavailable,
                               reason: available ? nil : .permissionDenied,
                               backendId: backendId, minOS: "15.0", offline: true)
    }
    func recognize(_ request: SpeechRecognitionRequest) async throws -> SpeechRecognitionResult {
        SpeechRecognitionResult(backendId: backendId, transcript: "fixture")
    }
    func drain() async {}
    func close() async {}
}

final class SpeechMicrophoneContractTests: XCTestCase {
    func testMicrophoneRequestCanonicalizesAndRejectsBlankLocale() throws {
        let request = try SpeechMicrophoneRecognitionRequest(locale: " en-us ")
        XCTAssertEqual(request.locale, "en_US")
        XCTAssertThrowsError(try SpeechMicrophoneRecognitionRequest(locale: "   "))
    }

    func testLegacyFileRequestAndBackendRemainSourceCompatible() async throws {
        let backend = LegacyFileBackend("legacy.file")
        let client = try SpeechRecognitionClient(defaultBackendId: backend.backendId, backends: [backend])
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        FileManager.default.createFile(atPath: file.path, contents: Data())
        defer { try? FileManager.default.removeItem(at: file) }
        let result = try await client.recognize(try SpeechRecognitionRequest(locale: "en-US", audioFile: file))
        XCTAssertEqual(result.transcript, "fixture")
        let descriptors = try await client.listBackends()
        XCTAssertEqual(descriptors.first?.supportedInputs, [.audioFile])
        await client.close()
    }

    func testListAndSelectPreserveInputSupportEvenWhenUnavailable() async throws {
        let backend = InputMetadataBackend()
        let registry = try SpeechClient(capability: .asr, defaultBackendId: backend.backendId,
                                       registrations: [.init(capability: .asr, backend: backend)])
        let selected = try await registry.select(.explicit(backend.backendId))
        XCTAssertEqual(selected.supportedInputs, [.audioFile, .microphone])
        await backend.setAvailable(false)
        let descriptors = await registry.listBackends()
        XCTAssertEqual(descriptors.first?.supportedInputs, [.audioFile, .microphone])
        XCTAssertEqual(descriptors.first?.availability.reason, .permissionDenied)
        do {
            _ = try await registry.select(.explicit(backend.backendId))
            XCTFail("Input support must not bypass current availability")
        } catch {
            XCTAssertEqual(error as? SpeechClientError,
                           .backendUnavailable(capability: .asr, backendId: backend.backendId, reason: .permissionDenied))
        }
        await registry.close()
    }

    func testTtsDescriptorsDoNotAdvertiseRecognitionInputs() async throws {
        let backend = MultiProtocolTtsBackend("multi.tts")
        let client = try SpeechSynthesisClient(defaultBackendId: backend.backendId, backends: [backend])
        let descriptors = try await client.listBackends()
        XCTAssertEqual(descriptors.first?.supportedInputs, [])
        let registry = try SpeechClient(capability: .tts, defaultBackendId: backend.backendId,
                                       registrations: [.init(capability: .tts, backend: backend)])
        let selected = try await registry.select()
        XCTAssertEqual(selected.supportedInputs, [])
        await registry.close()
        await client.close()
    }
}
