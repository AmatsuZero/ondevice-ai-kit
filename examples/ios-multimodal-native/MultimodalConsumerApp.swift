import SwiftUI
import ODAIKitCore
import ODAIKitMultimodal

@main
struct MultimodalConsumerApp: App {
    @State private var message = "Multimodal SDK linking sample"

    var body: some Scene {
        WindowGroup {
            Text(message).task {
                let backend = UnavailableExampleBackend()
                do {
                    // Construction does not read this nonexistent file or run a model.
                    let request = try MultimodalRequest(
                        imageFile: URL(fileURLWithPath: "/nonexistent/linking-sample.png"),
                        prompt: "Describe this image.")
                    let client = try MultimodalGenerationClient(
                        defaultBackendId: backend.backendId, backends: [backend])
                    do {
                        let descriptors = try await client.listBackends()
                        _ = try await client.generate(request, backend: .explicit(backend.backendId))
                        message = "Unexpected generation; registered=\\(descriptors.count)"
                    } catch MultimodalClientError.backendUnavailable {
                        message = "SDK linked. No generation backend is installed."
                    } catch {
                        message = "SDK linking sample failed."
                    }
                    await client.close()
                } catch {
                    message = "SDK linking sample could not initialize."
                }
            }
        }
    }
}

private struct UnavailableExampleBackend: MultimodalGenerationBackend {
    let backendId = "example.multimodal.unavailable"
    func availability() async -> CapabilityAvailability {
        .init(status: .unavailable, reason: .backendNotLinked, backendId: backendId)
    }
    func generate(_ request: MultimodalRequest,
                  update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> MultimodalTextOutput {
        throw MultimodalClientError.backendUnavailable(backendId, .backendNotLinked)
    }
    func drain() async {}
    func close() async {}
}
