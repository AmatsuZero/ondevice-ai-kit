// W3AppleFileApp.swift
//
// Tiny, isolated SwiftUI iOS app for W3: on-device FILE transcription only,
// using the PRODUCTION public APIs from the ODAIKit packages:
//   - SpeechRecognitionClient          (ODAIKitSpeech, actor)
//   - SpeechRecognitionRequest/Result/Error (ODAIKitSpeech)
//   - AppleSpeechRecognizerBackend     (ODAIKitAppleSpeech) — production impl
//     always sets requiresOnDeviceRecognition=true and has no fallback.
//   - Speech framework is used ONLY for authorizationStatus / requestAuthorization.
//
// This file does NOT recreate any of those APIs and never touches the microphone,
// AVAudioSession, or RECORD_AUDIO. All work runs @MainActor async and is driven
// by a single explicit button ("Run local file ASR"); it never auto-runs.
//
// Receipt model is bounded and uses JSONSerialization dictionaries only.

import SwiftUI
import Speech
import CommonCrypto
import ODAIKitCore
import ODAIKitSpeech
import ODAIKitAppleSpeech

// MARK: - Bundle manifest model (read-only, frozen by parent)

private struct ManifestClip {
    let id: String
    let reference: String
    let wavPath: String
    let expectedBytes: Int
    let expectedSha256: String
}

private struct ParsedManifest {
    let schemaVersion: Int
    let datasetSourceRevision: String
    let dataset: String
    let license: String
    let manifestSha256: String
    let clips: [ManifestClip]
    let parseError: [String: Any]?
}

// MARK: - Model

@MainActor
final class W3FileModel: ObservableObject {
    @Published private(set) var phase: String = "idle"
    @Published private(set) var status: String = "tap to run"
    @Published private(set) var reportFilename: String = "w3-file-report.json"
    @Published private(set) var lastError: String?
    @Published private(set) var isRunning: Bool = false
    @Published private(set) var hasRun: Bool = false

    var runButtonDisabled: Bool { isRunning || hasRun }

    // SDK/source commit of the ODAIKit packages — KEPT SEPARATE from any
    // System Speech model revision/hash, which are NOT exposed by these APIs.
    private let sdkSourceCommit = "6d503dd9e389f3c2b8ef56be4d613f77fd0cddbe"
    private let backendId = "apple.speech.recognizer"

    func runLocalFileASR() async {
        // Once-only concurrency guard; NEVER auto-run from onAppear.
        guard !hasRun, !isRunning else { return }
        isRunning = true
        hasRun = true
        self.reportFilename = "w3-file-report.json"

        // Resolve the optional explicit --odai-asr-locale argument (opt-in only).
        // Absent flag preserves en-US and existing default behavior. Unrelated
        // OS/test launch args are ignored. Malformed input yields a safe fixed
        // category; argument parsing never crashes app init.
        let (resolvedLocale, localeArgumentError) = Self.resolveLocaleArgument(CommandLine.arguments)

        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let reportURL = docs.appendingPathComponent(reportFilename)

        let runId = UUID().uuidString
        let manifest = parseManifest()

        // Build the initial clip receipts from the frozen manifest.
        var clipReceipts: [[String: Any]] = manifest.clips.map { c in
            [
                "schemaVersion": 1,
                "runId": runId,
                "clipId": c.id,
                "reference": c.reference,
                "sourceRevision": manifest.datasetSourceRevision,
                "inputHash": "",
                "expectedSha256": c.expectedSha256,
                "backendId": backendId,
                "transcript": "",
                "confidence": NSNull(),
                "elapsedSeconds": 0,
                "networkBehavior": "not measured",
                "postCloseSha256": "",
                "hashIntact": false
            ]
        }

        var report: [String: Any] = [
            "schemaVersion": 1,
            "runId": runId,
            "sdkSourceCommit": sdkSourceCommit,
            "datasetSourceRevision": manifest.datasetSourceRevision,
            "dataset": manifest.dataset,
            "license": manifest.license,
            "manifestSha256": manifest.manifestSha256,
            "modelSourceRevision": "NOT_REPORTED",
            "modelSourceHash": "NOT_REPORTED",
            "networkBehavior": "not measured",
            "requiresOnDeviceRecognition": true,
            "backendId": backendId,
            "locale": Self.normalizedLocale(resolvedLocale),
            "state": "running",
            "phase": "init",
            "status": "started",
            "clips": clipReceipts,
            "failure": NSNull(),
            "reportFilename": reportFilename
        ]

        // Persist running receipt ATOMICALLY BEFORE the Speech permission flow.
        // Data.write(to:options:.atomic) creates on first write and replaces on
        // later writes; no self-created temp files are left behind.
        let w0 = writeReport(report, to: reportURL)
        if !w0.ok {
            self.status = "report write failed"
            self.lastError = "initial report write failed; cannot start"
            self.isRunning = false
            return
        }

        var failure: [String: Any]?
        if let pe = manifest.parseError { failure = pe }

        // Explicit --odai-asr-locale argument was malformed: short-circuit with a
        // bounded failed receipt (safe fixed category) BEFORE any production
        // backend/client construction, Speech authorization, or recognition.
        if failure == nil, let locErr = localeArgumentError {
            failure = ["stage": "locale-argument",
                       "error": ["type": "input", "category": locErr]]
        }

        // One real production backend + one real client, explicit selection.
        var backend: AppleSpeechRecognizerBackend?
        var client: SpeechRecognitionClient?
        if failure == nil {
            do {
                let b = try AppleSpeechRecognizerBackend(locale: resolvedLocale, timeout: 30)
                backend = b
                let backs: [any SpeechRecognitionBackend] = [b]
                client = try SpeechRecognitionClient(defaultBackendId: backendId, backends: backs)
            } catch {
                failure = ["stage": "setup", "error": ["type": "setup", "category": safeCategory(error)]]
            }
        }

        // Speech authorization (Speech framework ONLY).
        if failure == nil {
            let auth = SFSpeechRecognizer.authorizationStatus()
            switch auth {
            case .notDetermined:
                let granted = await requestSpeechAuthorization()
                if !granted {
                    failure = ["stage": "speech-authorization",
                               "error": ["type": "unavailable", "reason": "denied"]]
                }
            case .denied, .restricted:
                failure = ["stage": "speech-authorization",
                           "error": ["type": "unavailable", "reason": "denied-or-restricted"]]
            case .authorized:
                break
            @unknown default:
                failure = ["stage": "speech-authorization",
                           "error": ["type": "unavailable", "reason": "unknown"]]
            }
        }

        // Sequential recognition of the 3 clips (no retries, no mic).
        if failure == nil, let client, let backend {
            for (index, clip) in manifest.clips.enumerated() {
                self.phase = "recognize-clip-\(index + 1)"
                self.status = "recognizing \(clip.id)"

                let audioURL = self.audioURL(for: clip)
                let verify = verifyClip(clip, at: audioURL)
                if let vFail = verify.failure {
                    failure = vFail
                    break
                }

                // Before EACH request: availability must be available AND offline.
                let av = await backend.availability()
                if av.status != .available || av.offline != true {
                    let reason = av.reason.map { "\($0)" } ?? "unsupportedDevice"
                    failure = ["stage": "availability",
                               "error": ["type": "unavailable", "reason": reason]]
                    break
                }

                let request: SpeechRecognitionRequest
                do {
                    // locale normalized by the actual API; en-US -> en_US.
                    request = try SpeechRecognitionRequest(locale: resolvedLocale, audioFile: audioURL)
                } catch {
                    failure = ["stage": "request-build",
                               "error": ["type": "input", "category": safeCategory(error)]]
                    break
                }

                let start = Date()
                do {
                    let result = try await client.recognize(request, backend: .explicit(backendId))
                    let elapsed = Date().timeIntervalSince(start)
                    let conf: Any = (result.confidence != nil && result.confidence!.isFinite)
                        ? NSNumber(value: result.confidence!) : NSNull()

                    var dict = clipReceipts[index]
                    dict["transcript"] = result.transcript
                    dict["confidence"] = conf
                    dict["backendId"] = result.backendId
                    dict["elapsedSeconds"] = elapsed
                    dict["inputHash"] = verify.observedSha256
                    dict["postCloseSha256"] = verify.observedSha256
                    dict["hashIntact"] = true
                    clipReceipts[index] = dict

                    report["clips"] = clipReceipts
                    report["phase"] = "progress-clip-\(index + 1)"
                    report["status"] = "recognized \(clip.id)"

                    // Persist progress after each clip.
                    let wp = writeReport(report, to: reportURL)
                    if !wp.ok {
                        failure = ["stage": "report-write",
                                   "error": ["type": "io", "category": "report-write-failed"]]
                        break
                    }
                } catch {
                    failure = classifyError(error, stage: "recognize-clip-\(index + 1)")
                    break
                }
            }
        }

        // ALWAYS await real client drain/close — even when Speech authorization,
        // availability or preflight failed. Cleanup is OUTSIDE the recognition loop.
        var cleanupDrained = false
        var cleanupClosed = false
        if let client {
            await client.drain()
            cleanupDrained = true
            await client.close()
            cleanupClosed = true
        }

        // Strict post-close integrity check of ALL 3 WAV inputs, AFTER drain/close.
        // Any missing/unreadable/changed input is a failure; nothing is skipped.
        for (index, clip) in manifest.clips.enumerated() {
            let audioURL = self.audioURL(for: clip)
            let verify = verifyClip(clip, at: audioURL)
            var dict = clipReceipts[index]
            dict["postCloseSha256"] = verify.observedSha256
            dict["hashIntact"] = (verify.failure == nil)
            clipReceipts[index] = dict
            // Preserve the primary failure; only attach integrity failure if none yet.
            if let vFail = verify.failure, failure == nil {
                failure = vFail
            }
        }
        report["clips"] = clipReceipts

        let clientExisted = (client != nil)

        // Finalize. 'completed' is strict and never asserted on quality/network.
        // A client must have existed AND close() must have returned.
        let allTranscripts = clipReceipts.compactMap {
            ($0["transcript"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let hashesIntact = clipReceipts.allSatisfy { ($0["hashIntact"] as? Bool) == true }
        let allBackendId = clipReceipts.allSatisfy { ($0["backendId"] as? String) == backendId }
        let completed = failure == nil
            && clientExisted && cleanupClosed
            && manifest.clips.count == 3
            && allTranscripts.count == 3
            && allTranscripts.allSatisfy { !$0.isEmpty }
            && hashesIntact
            && allBackendId

        report["clientExisted"] = clientExisted
        report["cleanup"] = ["drained": cleanupDrained, "closed": cleanupClosed]

        if completed {
            report["state"] = "completed"
            report["phase"] = "done"
            report["status"] = "all 3 clips recognized"
            report["failure"] = NSNull()
        } else {
            let failureType = (failure?["error"] as? [String: Any])?["type"] as? String
            let isUnavailable = ((failure?["stage"] as? String) ?? "").contains("authorization")
                || failureType == "unavailable"
            report["state"] = isUnavailable ? "unavailable" : "failed"
            report["phase"] = "finished-with-failure"
            if let failure { report["failure"] = failure }
            report["status"] = "did not complete; no quality assertion"
        }

        // Persist final receipt. If the write fails we must NOT claim success.
        let wf = writeReport(report, to: reportURL)
        if !wf.ok {
            self.status = "report write failed"
            self.lastError = "final report write failed; receipt not persisted"
            self.phase = "io-error"
            self.isRunning = false
            return
        }

        if let failure {
            self.lastError = "\(failure["stage"] as? String ?? "error"): see \(reportFilename)"
        } else if !completed {
            self.lastError = "incomplete; no quality assertion"
        }
        self.status = report["status"] as? String ?? "done"
        self.phase = report["phase"] as? String ?? "done"
        self.isRunning = false
    }

    // MARK: - Helpers

    private static func normalizedLocale(_ value: String) -> String {
        Locale.canonicalIdentifier(from: value.replacingOccurrences(of: "-", with: "_"))
    }

    /// Parse the optional explicit `--odai-asr-locale` launch argument (opt-in
    /// only). Absent flag preserves "en-US" and existing default behavior.
    /// Unrelated OS/test launch args are ignored (never rejected or reported).
    /// Duplicate flag, missing value, empty/unsupported/case variant are errors,
    /// each returned as a safe fixed category (never the arbitrary argument
    /// string). The locale is NEVER derived from phone settings or reinterpreted
    /// from English references.
    private static func resolveLocaleArgument(_ args: [String]) -> (locale: String, error: String?) {
        guard let idx = args.firstIndex(of: "--odai-asr-locale") else {
            return ("en-US", nil)
        }
        // Duplicate flag is an error.
        if args.dropFirst(idx + 1).contains("--odai-asr-locale") {
            return ("en-US", "odai-asr-locale-duplicate-flag")
        }
        let valueIdx = idx + 1
        guard valueIdx < args.count else {
            return ("en-US", "odai-asr-locale-missing-value")
        }
        let value = args[valueIdx]
        if value.isEmpty {
            return ("en-US", "odai-asr-locale-missing-value")
        }
        if value == "en-US" {
            return ("en-US", nil)
        }
        if value == "zh-CN" {
            return ("zh-CN", nil)
        }
        // Unsupported value or case variant are errors.
        return ("en-US", "odai-asr-locale-unsupported-value")
    }

    /// Parse the EXACT frozen manifest schema (schemaVersion 1):
    ///   source[revision, dataset, license] and
    ///   clips[id, reference, wavPath, pcmWav[bytes, sha256]].
    /// Exactly 3 clips, unique ids and wave paths, no traversal, no defaulted
    /// hashes/references. On any problem returns a parseError (caller writes a
    /// failed receipt; it never silently skips or substitutes values).
    private func parseManifest() -> ParsedManifest {
        let base = Bundle.main.resourceURL ?? Bundle.main.bundleURL
        let url = base.appendingPathComponent("AudioFixtures").appendingPathComponent("manifest.json")
        guard let data = try? Data(contentsOf: url) else {
            return ParsedManifest(schemaVersion: 0, datasetSourceRevision: "", dataset: "",
                                  license: "", manifestSha256: "", clips: [],
                                  parseError: ["stage": "manifest",
                                               "error": ["type": "unavailable", "reason": "missing-or-unreadable"]])
        }
        let manifestSha = sha256Hex(of: data)
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return ParsedManifest(schemaVersion: 0, datasetSourceRevision: "", dataset: "",
                                  license: "", manifestSha256: manifestSha, clips: [],
                                  parseError: ["stage": "manifest",
                                               "error": ["type": "unavailable", "reason": "invalid-json"]])
        }

        let schemaVersion = (obj["schemaVersion"] as? Int) ?? 0
        guard schemaVersion == 1 else {
            return ParsedManifest(schemaVersion: schemaVersion, datasetSourceRevision: "", dataset: "",
                                  license: "", manifestSha256: manifestSha, clips: [],
                                  parseError: ["stage": "manifest",
                                               "error": ["type": "unavailable", "reason": "unsupported-schemaVersion"]])
        }

        guard let source = obj["source"] as? [String: Any],
              let revision = (source["revision"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !revision.isEmpty,
              let dataset = (source["dataset"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !dataset.isEmpty,
              let license = (source["license"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !license.isEmpty else {
            return ParsedManifest(schemaVersion: 1, datasetSourceRevision: "", dataset: "",
                                  license: "", manifestSha256: manifestSha, clips: [],
                                  parseError: ["stage": "manifest",
                                               "error": ["type": "unavailable", "reason": "missing-source-fields"]])
        }

        guard let arr = obj["clips"] as? [[String: Any]], arr.count == 3 else {
            return ParsedManifest(schemaVersion: 1, datasetSourceRevision: revision, dataset: dataset,
                                  license: license, manifestSha256: manifestSha, clips: [],
                                  parseError: ["stage": "manifest",
                                               "error": ["type": "unavailable", "reason": "clips-not-exactly-3"]])
        }

        var clips: [ManifestClip] = []
        var seenIds = Set<String>()
        var seenPaths = Set<String>()
        for c in arr {
            guard let id = (c["id"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines), !id.isEmpty,
                  let reference = (c["reference"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !reference.isEmpty,
                  let wavPath = (c["wavPath"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !wavPath.isEmpty,
                  let pcm = c["pcmWav"] as? [String: Any],
                  let bytes = pcm["bytes"] as? Int, bytes > 0,
                  let sha = (pcm["sha256"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !sha.isEmpty
            else {
                return ParsedManifest(schemaVersion: 1, datasetSourceRevision: revision, dataset: dataset,
                                      license: license, manifestSha256: manifestSha, clips: [],
                                      parseError: ["stage": "manifest",
                                                   "error": ["type": "unavailable", "reason": "malformed-clip"]])
            }
            // Validate path shape: audio/<id>.wav, single segment, no traversal.
            let parts = wavPath.components(separatedBy: "/")
            let pathOk = wavPath.hasPrefix("audio/")
                && !wavPath.contains("..")
                && !wavPath.hasPrefix("/")
                && wavPath.hasSuffix(".wav")
                && parts.count == 2
            guard pathOk else {
                return ParsedManifest(schemaVersion: 1, datasetSourceRevision: revision, dataset: dataset,
                                      license: license, manifestSha256: manifestSha, clips: [],
                                      parseError: ["stage": "manifest",
                                                   "error": ["type": "unavailable", "reason": "unsafe-wavPath"]])
            }
            guard !seenIds.contains(id), !seenPaths.contains(wavPath) else {
                return ParsedManifest(schemaVersion: 1, datasetSourceRevision: revision, dataset: dataset,
                                      license: license, manifestSha256: manifestSha, clips: [],
                                      parseError: ["stage": "manifest",
                                                   "error": ["type": "unavailable", "reason": "duplicate-id-or-path"]])
            }
            seenIds.insert(id)
            seenPaths.insert(wavPath)
            clips.append(ManifestClip(id: id, reference: reference, wavPath: wavPath,
                                      expectedBytes: bytes, expectedSha256: sha))
        }

        return ParsedManifest(schemaVersion: 1, datasetSourceRevision: revision, dataset: dataset,
                              license: license, manifestSha256: manifestSha, clips: clips, parseError: nil)
    }

    private func audioURL(for clip: ManifestClip) -> URL {
        let base = Bundle.main.resourceURL ?? Bundle.main.bundleURL
        return base.appendingPathComponent("AudioFixtures").appendingPathComponent(clip.wavPath)
    }

    /// Verify a WAV SHA-256/bytes against the manifest's expected values. No network.
    private func verifyClip(_ clip: ManifestClip, at url: URL)
        -> (observedSha256: String, observedBytes: Int, failure: [String: Any]?) {
        guard let data = try? Data(contentsOf: url) else {
            return ("", 0, ["stage": "manifest-verify",
                            "error": ["type": "unavailable", "reason": "audio-file-missing"]])
        }
        let sha = sha256Hex(of: data)
        if data.count != clip.expectedBytes || sha != clip.expectedSha256 {
            return (sha, data.count, ["stage": "manifest-verify",
                                      "error": ["type": "integrity", "reason": "hash-or-bytes-mismatch"]])
        }
        return (sha, data.count, nil)
    }

    private func sha256Hex(of data: Data) -> String {
        var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        data.withUnsafeBytes { buf in
            _ = CC_SHA256(buf.baseAddress, CC_LONG(buf.count), &hash)
        }
        return hash.map { String(format: "%02x", $0) }.joined()
    }

    private func requestSpeechAuthorization() async -> Bool {
        await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                continuation.resume(returning: status == .authorized)
            }
        }
    }

    /// Write the report atomically. Creates the file on first write and replaces
    /// it on subsequent writes via Data.write(to:options:.atomic). No temp files
    /// are left behind. Returns (ok, error) — the error is the safe type name
    /// only (never the underlying message), so it can be shown to the UI.
    private func writeReport(_ report: [String: Any], to url: URL) -> (ok: Bool, error: String?) {
        do {
            let data = try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted])
            try data.write(to: url, options: .atomic)
            return (true, nil)
        } catch {
            return (false, String(describing: type(of: error)))
        }
    }

    /// Typed failure classification. Arbitrary errors are recorded by SAFE class
    /// name only — never their full message/localizedDescription.
    private func classifyError(_ error: Error, stage: String) -> [String: Any] {
        if let e = error as? SpeechRecognitionError {
            switch e {
            case .platform(let domain, let code):
                return ["stage": stage, "error": ["type": "platform", "domain": domain, "code": code]]
            case .timedOut:
                return ["stage": stage, "error": ["type": "timedOut"]]
            case .noResult:
                return ["stage": stage, "error": ["type": "noResult"]]
            }
        }
        if let e = error as? SpeechClientError {
            switch e {
            case .backendUnavailable(_, _, let reason):
                return ["stage": stage, "error": ["type": "unavailable", "reason": "\(reason)"]]
            case .closed:
                return ["stage": stage, "error": ["type": "closed"]]
            case .backendNotRegistered:
                return ["stage": stage, "error": ["type": "client", "category": "backendNotRegistered"]]
            case .invalidRegistration:
                // Classify by category only; never copy the arbitrary message.
                return ["stage": stage, "error": ["type": "client", "category": "invalidRegistration"]]
            }
        }
        return ["stage": stage, "error": ["type": "other", "category": safeCategory(error)]]
    }

    private func safeCategory(_ error: Error) -> String {
        String(describing: type(of: error))
    }
}

// MARK: - UI

struct ContentView: View {
    @EnvironmentObject var model: W3FileModel

    var body: some View {
        VStack(spacing: 16) {
            Text("ODAI W3 File ASR").font(.headline)
            Button("Run local file ASR") {
                Task { await model.runLocalFileASR() }
            }
            .accessibilityIdentifier("w3-file-run")
            .disabled(model.runButtonDisabled)
            .padding(.vertical, 6)

            Divider()
            LabeledText("Phase", model.phase)
            LabeledText("Status", model.status)
            if let err = model.lastError {
                LabeledText("Error", err).foregroundColor(.red)
            }
            LabeledText("Report", model.reportFilename)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct LabeledText: View {
    let title: String
    let value: String
    init(_ title: String, _ value: String) {
        self.title = title
        self.value = value
    }
    var body: some View {
        HStack(alignment: .top) {
            Text("\(title):").bold()
            Text(value).textSelection(.enabled)
        }
    }
}

// MARK: - App entry

@main
struct W3AppleFileApp: App {
    @StateObject private var model = W3FileModel()

    var body: some Scene {
        WindowGroup {
            ContentView().environmentObject(model)
        }
    }
}
