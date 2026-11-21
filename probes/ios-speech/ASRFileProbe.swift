import AVFoundation
import CryptoKit
import Speech
import SwiftUI
import UIKit

/// A bounded, file-only Speech framework probe. It never opens a microphone.
@MainActor
final class ASRFileProbe: ObservableObject {
    @Published private(set) var status = "ASR ready (fixed bundled audio)"
    @Published private(set) var busy = false
    @Published private(set) var reportPath: String?

    private let expectedText = "The blue bicycle is beside the green house."
    private var runTask: Task<Void, Never>?
    private var activeTask: SFSpeechRecognitionTask?
    private var activeDelegate: ASRProbeDelegate?

    func run() {
        guard !busy else { status = "ASR run already in progress"; return }
        if activeTask?.state == .completed { activeTask = nil; activeDelegate = nil }
        guard activeTask == nil else {
            status = "Previous cancellation is awaiting platform completion"
            return
        }
        busy = true
        runTask = Task {
            await execute()
            busy = false
            runTask = nil
        }
    }

    func cancel() {
        guard busy else { return }
        status = "Cancellation requested; awaiting platform completion"
        runTask?.cancel()
    }

    private func execute() async {
        #if targetEnvironment(simulator)
        let environment = "simulator"
        #else
        let environment = "device"
        #endif
        var report: [String: Any] = [
            "schemaVersion": 1,
            "runId": UUID().uuidString,
            "device": UIDevice.current.model, "OS": UIDevice.current.systemVersion,
            "backendId": "apple.speech.recognizer",
            "audioSource": "bundled-asr-fixture.caf",
            "expectedText": expectedText,
            "locale": "en-US",
            "requiresOnDeviceRecognition": true,
            "microphoneUsed": false,
            "validationStatus": environment,
            "networkBehavior": "unmeasured"
        ]
        defer { save(report) }

        status = "Checking Speech authorization"
        var authorization = SFSpeechRecognizer.authorizationStatus()
        if authorization == .notDetermined {
            authorization = await withCheckedContinuation { continuation in
                SFSpeechRecognizer.requestAuthorization { continuation.resume(returning: $0) }
            }
        }
        report["authorization"] = authorizationLabel(authorization)
        guard !Task.isCancelled else { report["status"] = "cancelled-before-recognition"; return }
        guard authorization == .authorized else {
            report["status"] = "unavailable"
            report["reason"] = "speech-authorization-\(authorizationLabel(authorization))"
            return
        }
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US")) else {
            report["status"] = "unavailable"; report["reason"] = "recognizer-locale-unavailable"; return
        }
        guard recognizer.isAvailable else {
            report["status"] = "unavailable"; report["reason"] = "recognizer-unavailable"; return
        }
        guard recognizer.supportsOnDeviceRecognition else {
            report["status"] = "unavailable"; report["reason"] = "on-device-recognition-unsupported"; return
        }
        recognizer.queue = .main
        guard let url = Bundle.main.url(forResource: "asr-fixture", withExtension: "caf") else {
            report["status"] = "failed"; report["reason"] = "bundled-fixture-missing"; return
        }
        do {
            let data = try Data(contentsOf: url)
            let audio = try AVAudioFile(forReading: url)
            let digest = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
            report["inputSHA256"] = digest
            report["inputFrames"] = audio.length
            report["sampleRate"] = audio.processingFormat.sampleRate
            report["channels"] = audio.processingFormat.channelCount
            guard digest == "ea1e97d99f859f4bb1eafb75f6090bb9fe98f89ab86bd903f7c325013f3dd98e" else {
                report["status"] = "failed"; report["reason"] = "bundled-fixture-sha256-mismatch"; return
            }
        } catch {
            report["status"] = "failed"; report["reason"] = "bundled-fixture-invalid"; return
        }

        status = "Recognizing fixed file on device"
        let recognition = await recognize(url, using: recognizer)
        report["recognition"] = recognition
        report["status"] = recognition["status"] as? String ?? "failed"
        guard recognition["status"] as? String == "success", !Task.isCancelled else { return }
        status = "Checking cancellation and reentry"
        // The fixed URL is short enough for iOS to finish before a delayed
        // cancellation can run. Request cancellation immediately after task
        // creation so this path exercises the platform cancel lifecycle.
        let cancellation = await recognize(url, using: recognizer, cancelAfter: 0)
        report["cancellation"] = cancellation
        guard cancellation["taskCompleted"] as? Bool == true, !Task.isCancelled else {
            report["status"] = "incomplete"; return
        }
        let reentry = await recognize(url, using: recognizer)
        report["reentry"] = reentry
        report["status"] = cancellation["status"] as? String == "cancelled" && reentry["status"] as? String == "success" ? "success" : "incomplete"
    }

    private func recognize(_ url: URL, using recognizer: SFSpeechRecognizer, cancelAfter: UInt64? = nil) async -> [String: Any] {
        let completion = ASRProbeCompletion()
        let request = SFSpeechURLRecognitionRequest(url: url)
        request.requiresOnDeviceRecognition = true
        request.shouldReportPartialResults = false

        let delegate = ASRProbeDelegate { outcome in
            Task { @MainActor in completion.resolve(outcome) }
        }
        activeDelegate = delegate
        let task = recognizer.recognitionTask(with: request, delegate: delegate)
        activeTask = task
        completion.installCancellation { task.cancel() }

        let timeout = Task { @MainActor in
            do { try await Task.sleep(nanoseconds: 20_000_000_000) }
            catch { return }
            completion.cancel(.timeout)
        }
        let cancellation: Task<Void, Never>?
        if cancelAfter == 0 {
            completion.cancel(.cancelled)
            cancellation = nil
        } else {
            cancellation = cancelAfter.map { delay in
                Task { @MainActor in
                    do { try await Task.sleep(nanoseconds: delay) }
                    catch { return }
                    completion.cancel(.cancelled)
                }
            }
        }
        let outcome = await withTaskCancellationHandler(operation: {
            if Task.isCancelled { completion.cancel(.cancelled) }
            return await completion.value()
        }, onCancel: {
            Task { @MainActor in completion.cancel(.cancelled) }
        })
        timeout.cancel(); cancellation?.cancel()

        // Cancellation return is not evidence that the recognizer released its work.
        for _ in 0..<60 where task.state != .completed {
            await withCheckedContinuation { continuation in
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { continuation.resume() }
            }
        }

        let completed = task.state == .completed
        if completed { activeTask = nil; activeDelegate = nil }
        var result: [String: Any] = [
            "taskCompleted": completed,
            "platformCancelled": task.isCancelled,
            "platformState": task.state.rawValue,
            "resourceRelease": completed ? "confirmed" : "awaiting-platform-completion"
        ]
        if let error = task.error as NSError? {
            result["errorDomain"] = error.domain; result["errorCode"] = error.code
        }
        switch outcome {
        case .transcript(let transcript):
            let matched = ASRProbeText.matches(transcript, expected: expectedText)
            result["transcript"] = transcript; result["normalizedMatch"] = matched
            if transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                result["status"] = "failed"; result["reason"] = "empty-final-transcript"
            } else if !matched {
                result["status"] = "failed"; result["reason"] = "final-transcript-mismatch"
            } else if completed {
                result["status"] = "success"
            } else {
                result["status"] = "incomplete"; result["reason"] = "terminal-callback-not-observed"
            }
        case .failure(let domain, let code):
            result["status"] = "failed"; result["errorDomain"] = domain; result["errorCode"] = code
        case .cancelled:
            result["status"] = task.isCancelled && completed ? "cancelled" : "incomplete"
        case .timeout: result["status"] = "timeout"
        }
        return result
    }

    private func authorizationLabel(_ value: SFSpeechRecognizerAuthorizationStatus) -> String {
        switch value {
        case .authorized: return "authorized"
        case .denied: return "denied"
        case .restricted: return "restricted"
        case .notDetermined: return "not-determined"
        @unknown default: return "unknown"
        }
    }

    private func save(_ report: [String: Any]) {
        do {
            let data = try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys])
            let file = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("ios-asr-file-\(UUID().uuidString).json")
            try data.write(to: file, options: .atomic)
            reportPath = file.lastPathComponent; status = "ASR report: \(report["status"] ?? "unknown")"
        } catch { status = "ASR report failed: \(error.localizedDescription)" }
    }
}

private final class ASRProbeDelegate: NSObject, SFSpeechRecognitionTaskDelegate {
    private let complete: (ASRProbeOutcome) -> Void
    private var finalTranscript: String?

    init(complete: @escaping (ASRProbeOutcome) -> Void) { self.complete = complete }

    func speechRecognitionTask(_ task: SFSpeechRecognitionTask, didFinishRecognition result: SFSpeechRecognitionResult) {
        guard result.isFinal else { return }
        finalTranscript = result.bestTranscription.formattedString
    }

    func speechRecognitionTask(_ task: SFSpeechRecognitionTask, didFinishSuccessfully successfully: Bool) {
        if successfully { complete(.transcript(finalTranscript ?? "")) }
        else {
            let error = task.error as NSError?
            complete(.failure(domain: error?.domain ?? "SpeechProbe", code: error?.code ?? -1))
        }
    }
}
