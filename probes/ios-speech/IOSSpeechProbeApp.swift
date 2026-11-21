import Foundation
import Speech
import SwiftUI
import UIKit
import ODAIKitAppleSpeech
import ODAIKitSpeech

@main
struct IOSSpeechProbeApp: App {
    @StateObject private var model = SpeechProbeModel()
    @StateObject private var asr = ASRFileProbe()
    @State private var didAutoRunASR = false
    var body: some Scene {
        WindowGroup {
            VStack(spacing: 12) {
                Text("iOS Speech Probe").font(.title2)
                Text(model.status).accessibilityIdentifier("speech-status")
                Text(model.reportPath ?? "").font(.caption).textSelection(.enabled)
                Divider()
                Text(asr.status).accessibilityIdentifier("asr-status")
                Button("Run ASR file probe") { asr.run() }
                    .disabled(asr.busy).accessibilityIdentifier("asr-run")
                Button("Cancel ASR") { asr.cancel() }
                    .disabled(!asr.busy).accessibilityIdentifier("asr-cancel")
                Text(asr.reportPath ?? "").font(.caption).textSelection(.enabled)
            }.padding().onAppear {
                model.run()
                if CommandLine.arguments.contains("--run-asr"), !didAutoRunASR {
                    didAutoRunASR = true
                    asr.run()
                }
            }
        }
    }
}

@MainActor
final class SpeechProbeModel: NSObject, ObservableObject {
    @Published private(set) var status = "Ready"
    @Published private(set) var reportPath: String?
    private var ran = false
    private var backend: AppleAVSpeechBackend?
    private var client: SpeechSynthesisClient?

    func run() {
        guard !ran else { return }
        ran = true
        do {
            let backend = AppleAVSpeechBackend()
            self.backend = backend
            self.client = try SpeechSynthesisClient(defaultBackendId: backend.backendId, backends: [backend])
            Task { await execute() }
        } catch { status = "Failed: \(error)" }
    }

    private func execute() async {
        status = "Running"
        let voices = AVSpeechSynthesisVoice.speechVoices()
        let voice = voices.first(where: { $0.language == "en-US" }) ?? voices.first
        let request = SpeechSynthesisRequest(text: "OnDevice AI Kit iOS speech probe", locale: voice?.language ?? "en-US", voiceId: voice?.identifier)
        let ttsStatus: String
        let ttsError: String?
        do {
            let result = try await client?.speak(request)
            ttsStatus = result == nil ? "unavailable" : "success"
            ttsError = result == nil ? "client missing" : nil
        } catch {
            ttsStatus = "failed"
            ttsError = String(describing: error)
        }
        let cancellationStatus: String
        if let client {
            let longText = String(repeating: "This is a cancellation test. ", count: 150)
            let task = Task { try await client.speak(.init(text: longText, locale: "en-US")) }
            try? await Task.sleep(nanoseconds: 150_000_000)
            task.cancel()
            switch await task.result {
            case .success: cancellationStatus = "completed-before-cancel"
            case .failure(let error): cancellationStatus = error is CancellationError || String(describing: error).contains("cancelled") ? "cancelled" : "failed: \(error)"
            }
        } else { cancellationStatus = "client-missing" }
        await client?.close()
        let afterCloseStatus: String
        do {
            _ = try await client?.speak(.init(text: "after close", locale: "en-US"))
            afterCloseStatus = "unexpected-success"
        } catch { afterCloseStatus = String(describing: error) }
        let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
        let authorization: String
        switch SFSpeechRecognizer.authorizationStatus() {
        case .authorized: authorization = "authorized"
        case .denied: authorization = "denied"
        case .restricted: authorization = "restricted"
        case .notDetermined: authorization = "notDetermined"
        @unknown default: authorization = "unknown"
        }
        #if targetEnvironment(simulator)
        let environment = "simulator"
        #else
        let environment = "device"
        #endif
        let report: [String: Any] = [
            "schemaVersion": 1, "runId": UUID().uuidString, "device": UIDevice.current.model,
            "OS": UIDevice.current.systemVersion, "validationStatus": environment,
            "tts": ["backendId": backend?.backendId ?? "apple.avspeech", "networkBehavior": "unmeasured", "voiceCount": voices.count, "selectedVoice": voice?.identifier ?? "", "selectedLocale": voice?.language ?? "", "status": ttsStatus, "error": ttsError ?? NSNull(), "cancellationStatus": cancellationStatus, "afterCloseStatus": afterCloseStatus],
            "asr": ["backendId": "apple.speech.recognizer", "offlineCapability": recognizer?.supportsOnDeviceRecognition ?? false, "isAvailable": recognizer?.isAvailable ?? false, "authorization": authorization, "transcription": "not-run"]
        ]
        do {
            let data = try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys])
            let file = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("ios-speech-probe-\(UUID().uuidString).json")
            try data.write(to: file, options: .atomic)
            reportPath = file.lastPathComponent; status = "Complete"
        } catch { status = "Failed: \(error)" }
    }
}
