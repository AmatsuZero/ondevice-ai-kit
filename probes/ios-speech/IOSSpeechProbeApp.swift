import AVFoundation
import Foundation
import Speech
import SwiftUI
import UIKit

@main
struct IOSSpeechProbeApp: App {
    @StateObject private var model = SpeechProbeModel()
    var body: some Scene {
        WindowGroup {
            VStack(spacing: 12) {
                Text("iOS Speech Probe").font(.title2)
                Text(model.status).accessibilityIdentifier("speech-status")
                Text(model.reportPath ?? "")
                    .font(.caption).textSelection(.enabled)
            }
            .padding()
            .onAppear { model.run() }
        }
    }
}

@MainActor
final class SpeechProbeModel: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    @Published private(set) var status = "Ready"
    @Published private(set) var reportPath: String?
    private var ran = false
    private let synthesizer = AVSpeechSynthesizer()
    private var synthesisContinuation: CheckedContinuation<String, Never>?

    func run() {
        guard !ran else { return }
        ran = true
        synthesizer.delegate = self
        Task { await execute() }
    }

    private func execute() async {
        status = "Running"
        let voices = AVSpeechSynthesisVoice.speechVoices()
        let voice = voices.first(where: { $0.language == "en-US" }) ?? voices.first
        let ttsStatus: String
        let ttsError: String?
        if let voice {
            let utterance = AVSpeechUtterance(string: "OnDevice AI Kit iOS speech probe")
            utterance.voice = voice
            utterance.rate = AVSpeechUtteranceDefaultSpeechRate
            let result = await withCheckedContinuation { (continuation: CheckedContinuation<String, Never>) in
                synthesisContinuation = continuation
                synthesizer.speak(utterance)
            }
            ttsStatus = result
            ttsError = nil
        } else {
            ttsStatus = "unavailable"
            ttsError = "no system voice"
        }
        let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
        let authorization: String
        switch SFSpeechRecognizer.authorizationStatus() {
        case .authorized: authorization = "authorized"
        case .denied: authorization = "denied"
        case .restricted: authorization = "restricted"
        case .notDetermined: authorization = "notDetermined"
        @unknown default: authorization = "unknown"
        }
        let report: [String: Any] = [
            "schemaVersion": 1,
            "runId": UUID().uuidString,
            "device": UIDevice.current.model,
            "OS": UIDevice.current.systemVersion,
            "validationStatus": "device",
            "tts": [
                "backendId": "apple.avspeech",
                "networkBehavior": "unmeasured",
                "voiceCount": voices.count,
                "selectedVoice": voice?.identifier ?? "",
                "selectedLocale": voice?.language ?? "",
                "status": ttsStatus,
                "error": ttsError ?? NSNull() as Any
            ],
            "asr": [
                "backendId": "apple.speech.recognizer",
                "offlineCapability": recognizer?.supportsOnDeviceRecognition ?? false,
                "isAvailable": recognizer?.isAvailable ?? false,
                "authorization": authorization,
                "transcription": "not-run"
            ]
        ]
        do {
            let data = try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys])
            let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let file = documents.appendingPathComponent("ios-speech-probe-\(UUID().uuidString).json")
            try data.write(to: file, options: .atomic)
            reportPath = file.lastPathComponent
            status = "Complete"
        } catch {
            status = "Failed: \(error)"
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in
            synthesisContinuation?.resume(returning: "success")
            synthesisContinuation = nil
        }
    }
    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in
            synthesisContinuation?.resume(returning: "cancelled")
            synthesisContinuation = nil
        }
    }
}
