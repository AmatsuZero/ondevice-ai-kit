import SwiftUI
import ODAIKitSpeech
import ODAIKitAppleSpeech

@main
struct SpeechConsumerApp: App {
    @State private var message = "OnDevice AI Kit speech consumer"

    var body: some Scene {
        WindowGroup {
            Text(message).task {
                let backend = AppleAVSpeechBackend()
                do {
                    // Construction/type probe only: no permission prompt or ASR operation.
                    let recognizer = try await MainActor.run { try AppleSpeechRecognizerBackend(locale: "en-US") }
                    let recognitionClient = try SpeechRecognitionClient(defaultBackendId: recognizer.backendId,
                                                                         backends: [recognizer])
                    await recognitionClient.drain()
                    await recognitionClient.close()
                    let client = try SpeechSynthesisClient(defaultBackendId: backend.backendId, backends: [backend])
                    do {
                        let result = try await client.speak(.init(text: "speech consumer", locale: "en-US"), backend: .explicit(backend.backendId))
                        message = "Speech SDK: backend=\(result.backendId)"
                    } catch { message = String(describing: error) }
                    await client.close()
                } catch {
                    message = String(describing: error)
                    await backend.close()
                }
            }
        }
    }
}
