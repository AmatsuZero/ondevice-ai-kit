import Foundation
import SwiftUI
import Speech
import ODAIKitCore
import ODAIKitSpeech
import ODAIKitAppleSpeech
#if canImport(Mach)
import Mach
#endif

private enum Pins {
    static let runId = "w3-apple65-file-20261116-01"
    static let manifestSHA256 = "99584dec071c0e8a3b47f510443e002d80058482cb48d580122a0af88c86a1fb"
    static let inputBindingSHA256 = "b3838b23136aa1be8c760915b565a2f192976ae3299631fcd3470f8df5334e88"
    static let backendId = "apple.speech.recognizer"
    static let bundleId = "io.ondeviceai.probe.w3apple65file20261116"
    static let sourceBaseCommit = "8537933d2340a2bf1e3c1f3aa97780bca5973780"
    static let distributionBoundary = "isolated-unsigned-probe-not-production"
    static let timeout: TimeInterval = 30
    static let sampleIntervalNs: UInt64 = 100_000_000
    static let gapThresholdMs = 200.0
}

private enum ProbeError: Error {
    case config(String)
    case source(String)
    case input(String)
    case availability(String)
    case contract(String)
    case scene
    case reportIO(String)
}

private struct SourceSelection: Codable { let path: String; let sha256: String }
private struct SourceIdentity: Codable {
    let schemaVersion: Int
    let runId: String
    let backendId: String
    let manifestSHA256: String
    let inputBindingSHA256: String
    let sourceBaseCommit: String
    let selectedSources: [SourceSelection]
    let distributionBoundary: String
}

private struct SourceIdentityReport: Codable {
    let sha256: String
    let runId: String
    let backendId: String
    let manifestSHA256: String
    let inputBindingSHA256: String
    let sourceBaseCommit: String
    let distributionBoundary: String
    let selectedSourceCount: Int
    let rawJSON: String
}

private struct AppReport: Codable { let bundleId: String?; let version: String?; let build: String?; let os: String }
private struct ErrorRecord: Codable {
    let typedCategory: String
    let typedDomain: String?
    let typedCode: Int?
    let nsErrorDomain: String
    let nsErrorCode: Int
    let stage: String?
}
private struct CleanupReport: Codable {
    let target: String
    let drainSeconds: Double?
    let closeSeconds: Double?
    let cleanupCompleted: Bool
}
private struct RSSSample: Codable, Sendable { let offsetMs: Double; let bytes: UInt64? }
private struct RSSSamplerRaw: Sendable { let samples: [RSSSample]; let attempts: Int }
private struct RSSReport: Codable {
    let intervalMs: Double
    let samplingWindowStartNanos: UInt64
    let samplingWindowEndNanos: UInt64
    let durationSeconds: Double
    let sampleCount: Int
    let totalAttempts: Int
    let missingCount: Int
    let minBytes: UInt64?
    let maxBytes: UInt64?
    let samples: [RSSSample]
    let gapsMs: [Double]
    let maxGapMs: Double?
    let overThresholdGapCount: Int
    let gapThresholdMs: Double
    let leadingGapMs: Double?
    let trailingGapMs: Double?
}

private struct CaseReport: Codable {
    var index: Int
    var clip: Apple65Clip
    var status: String
    var terminal: Bool
    var recognitionAttempted: Bool
    var availability: CapabilityAvailability?
    var requestLocale: String?
    var resultBackendId: String?
    var transcript: String?
    var confidence: Float?
    var error: ErrorRecord?
    var inputPostcheckOK: Bool?
    var inputPostcheckError: ErrorRecord?
    var wholeRequestWallSeconds: Double?
    var recognitionDurationSeconds: Double?
    var admissionUptimeNanos: UInt64?
    var recognitionStartUptimeNanos: UInt64?
    var recognitionEndUptimeNanos: UInt64?
    var cleanup: CleanupReport?
    var rss: RSSReport?

    static func initial(index: Int, clip: Apple65Clip) -> CaseReport {
        CaseReport(index: index, clip: clip, status: "not-run", terminal: true,
                   recognitionAttempted: false, availability: nil, requestLocale: nil,
                   resultBackendId: nil, transcript: nil, confidence: nil, error: nil,
                   inputPostcheckOK: nil, inputPostcheckError: nil,
                   wholeRequestWallSeconds: nil, recognitionDurationSeconds: nil,
                   admissionUptimeNanos: nil, recognitionStartUptimeNanos: nil,
                   recognitionEndUptimeNanos: nil, cleanup: nil, rss: nil)
    }
}

private struct Report: Codable {
    var schemaVersion: Int
    var runId: String
    var backendId: String
    var manifestSHA256: String
    var inputBindingSHA256: String
    var sourceBaseCommit: String
    var app: AppReport
    var sourceIdentity: SourceIdentityReport?
    var runStatus: String
    var startedAt: String
    var updatedAt: String
    var finishedAt: String?
    var fatal: ErrorRecord?
    var topology: String
    var measurementScope: String
    var gateConclusion: String
    var rows: [CaseReport]
}

private struct CaseOutcome {
    let row: CaseReport
    let stop: Bool
    let stopStatus: String?
    let fatal: ErrorRecord?
}

private let sourceRootKeys: Set<String> = [
    "schemaVersion", "runId", "backendId", "manifestSHA256",
    "inputBindingSHA256", "sourceBaseCommit", "selectedSources", "distributionBoundary"
]
private let sourceItemKeys: Set<String> = ["path", "sha256"]

private func probeStrictInt(_ value: Any?) -> Int? {
    guard let value = value else { return nil }
    if let number = value as? NSNumber {
        let kind = String(cString: number.objCType)
        switch kind {
        case "i", "s", "l", "q": return Int(exactly: number.int64Value)
        case "I", "S", "L", "Q": return Int(exactly: number.uint64Value)
        default: return nil
        }
    }
    if let int = value as? Int { return int }
    if let uint = value as? UInt, let int = Int(exactly: uint) { return int }
    return nil
}

private func f11ResidentMemoryBytes() -> UInt64? {
    var info = mach_task_basic_info()
    var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size / MemoryLayout<natural_t>.size)
    let result = withUnsafeMutablePointer(to: &info) { pointer in
        pointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
            task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), $0, &count)
        }
    }
    return result == KERN_SUCCESS ? UInt64(info.resident_size) : nil
}

@MainActor
final class RunModel: ObservableObject {
    @Published var message: String
    @Published var authorizationText: String
    @Published var reportPath: String
    @Published var reportExists: Bool
    @Published var busy = false
    @Published private(set) var sceneActive = true
    private var task: Task<Void, Never>?

    init() {
        let path = Self.reportURL.path
        message = "Ready. Speech authorization setup and the fixed FILE run are separate manual controls."
        authorizationText = ""
        reportPath = path
        reportExists = FileManager.default.fileExists(atPath: path)
        refreshAuthorization()
    }

    var isBusy: Bool { busy }
    var canRun: Bool { !busy && sceneActive }

    func refreshAuthorization() {
        authorizationText = "Speech: " + Self.authLabel(SFSpeechRecognizer.authorizationStatus())
    }

    func requestSpeechAuthorization() {
        let current = SFSpeechRecognizer.authorizationStatus()
        refreshAuthorization()
        guard current == .notDetermined else {
            message = "Speech authorization is " + Self.authLabel(current) + ". No run started."
            return
        }
        message = "Requesting Speech authorization only. The run remains manual."
        SFSpeechRecognizer.requestAuthorization { [weak self] status in
            Task { @MainActor in
                guard let self = self else { return }
                self.refreshAuthorization()
                self.message = "Speech authorization setup finished (" + Self.authLabel(status) + "). Run remains manual."
            }
        }
    }

    func start() {
        guard task == nil else { message = "A run is already active."; return }
        guard sceneActive else { message = "Scene is not active; refusing run."; return }
        guard SFSpeechRecognizer.authorizationStatus() == .authorized else {
            refreshAuthorization()
            message = "Speech authorization is not authorized. Use setup only, then press Run."
            return
        }
        busy = true
        message = "Starting fixed FILE run. Preflight precedes recognition."
        task = Task { @MainActor [weak self] in
            guard let self = self else { return }
            await self.execute()
            self.busy = false
            self.task = nil
        }
    }

    func cancel() {
        guard task != nil else { message = "No active run to cancel."; return }
        message = "Cancel requested. Awaiting always-awaited cleanup and finalization."
        task?.cancel()
    }

    func scenePhaseChanged(_ phase: ScenePhase) {
        switch phase {
        case .active: sceneActive = true
        case .inactive, .background: sceneInactive()
        @unknown default: sceneInactive()
        }
    }

    func sceneInactive() {
        sceneActive = false
        if task != nil {
            message = "Scene inactive. Active run cancelled; cleanup is still awaited."
            task?.cancel()
        }
    }

    private static func authLabel(_ status: SFSpeechRecognizerAuthorizationStatus) -> String {
        switch status {
        case .notDetermined: return "not-determined"
        case .denied: return "denied"
        case .restricted: return "restricted"
        case .authorized: return "authorized"
        @unknown default: return "unknown"
        }
    }

    private static var reportsDirectory: URL {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("W3Apple65FileReports", isDirectory: true)
    }

    private static var reportURL: URL {
        reportsDirectory.appendingPathComponent(Pins.runId + ".json", isDirectory: false)
    }

    @discardableResult
    private func save(_ report: Report) -> Bool {
        do {
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            let data = try encoder.encode(report)
            try FileManager.default.createDirectory(at: Self.reportsDirectory, withIntermediateDirectories: true)
            try data.write(to: Self.reportURL, options: .atomic)
            reportPath = Self.reportURL.path
            reportExists = true
            return true
        } catch {
            let record = Self.errorRecord(error, stage: "reportWrite")
            message = "Report I/O failed: " + record.typedCategory + " " + record.nsErrorDomain + ":" + String(record.nsErrorCode)
            return false
        }
    }

    private static func iso() -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: Date())
    }

    private static func appReport() -> AppReport {
        let info = Bundle.main.infoDictionary
        return AppReport(bundleId: Bundle.main.bundleIdentifier,
                         version: info?["CFBundleShortVersionString"] as? String,
                         build: info?["CFBundleVersion"] as? String,
                         os: ProcessInfo.processInfo.operatingSystemVersionString)
    }

    private static func makeReport(source: SourceIdentityReport?, rows: [CaseReport], status: String, error: ErrorRecord?) -> Report {
        let stamp = iso()
        return Report(schemaVersion: 1, runId: Pins.runId, backendId: Pins.backendId,
                      manifestSHA256: Pins.manifestSHA256, inputBindingSHA256: Pins.inputBindingSHA256,
                      sourceBaseCommit: Pins.sourceBaseCommit, app: appReport(), sourceIdentity: source,
                      runStatus: status, startedAt: stamp, updatedAt: stamp, finishedAt: nil,
                      fatal: error, topology: "all-65-fresh-backend-and-client-per-case-sequential-no-warmup-no-paced-idle",
                      measurementScope: "app-pid-rss-only-no-systemSpeech-truepeak-gpu-energy-thermal-claim",
                      gateConclusion: "open-no-quality-or-budget-pass", rows: rows)
    }

    private static func errorRecord(_ error: Error, stage: String? = nil) -> ErrorRecord {
        let fallback = error as NSError
        if let probe = error as? ProbeError {
            let category: String
            switch probe {
            case .config: category = "config"
            case .source: category = "sourceIdentity"
            case .input: category = "inputIntegrity"
            case .availability: category = "availability"
            case .contract: category = "contract"
            case .scene: category = "sceneCancelled"
            case .reportIO: category = "reportIO"
            }
            return ErrorRecord(typedCategory: category, typedDomain: nil, typedCode: nil,
                               nsErrorDomain: fallback.domain, nsErrorCode: fallback.code, stage: stage)
        }
        if let speech = error as? SpeechRecognitionError {
            var category = "SpeechRecognitionError.unknown"
            var domain: String? = nil
            var code: Int? = nil
            switch speech {
            case .timedOut: category = "SpeechRecognitionError.timedOut"
            case .noResult: category = "SpeechRecognitionError.noResult"
            case .platform(let d, let c): category = "SpeechRecognitionError.platform"; domain = d; code = c
            }
            return ErrorRecord(typedCategory: category, typedDomain: domain, typedCode: code,
                               nsErrorDomain: fallback.domain, nsErrorCode: fallback.code, stage: stage)
        }
        return ErrorRecord(typedCategory: String(reflecting: Swift.type(of: error)), typedDomain: nil,
                           typedCode: nil, nsErrorDomain: fallback.domain, nsErrorCode: fallback.code, stage: stage)
    }

    private func failEarly(source: SourceIdentityReport?, status: String, error: Error, stage: String) {
        var report = Self.makeReport(source: source, rows: [], status: status, error: Self.errorRecord(error, stage: stage))
        let stamp = Self.iso()
        report.finishedAt = stamp
        report.updatedAt = stamp
        if save(report) { message = status + "; fatal report written. Speech was not called." }
        else { message = status + "; fatal report write failed. Speech was not called." }
    }

    private func handleReportIO(_ report: Report, stage: String) {
        var fatal = report
        fatal.runStatus = "fatal-report-io"
        fatal.fatal = Self.errorRecord(ProbeError.reportIO(stage), stage: stage)
        let stamp = Self.iso()
        fatal.finishedAt = stamp
        fatal.updatedAt = stamp
        if save(fatal) { message = "Report I/O failed at " + stage + ". Final fatal report written; stopping." }
        else { message = "Report I/O failed at " + stage + ". Final fatal report also failed; stopping." }
    }

    private func failInputReport(_ report: Report, error: Error, stage: String) {
        var fatal = report
        fatal.runStatus = "fatal-input"
        fatal.fatal = Self.errorRecord(error, stage: stage)
        let stamp = Self.iso()
        fatal.finishedAt = stamp
        fatal.updatedAt = stamp
        if !save(fatal) { handleReportIO(fatal, stage: "fatal-input-write") }
        else { message = "Input verification failed at " + stage + ". Fatal input report written; stopping." }
    }

    private func finishReport(_ report: Report, status: String, message finalMessage: String) {
        var final = report
        final.runStatus = status
        let stamp = Self.iso()
        final.finishedAt = stamp
        final.updatedAt = stamp
        if !save(final) { handleReportIO(final, stage: "final") }
        else { message = finalMessage }
    }

    private func execute() async {
        let url = Self.reportURL
        reportPath = url.path
        if FileManager.default.fileExists(atPath: url.path) {
            reportExists = true
            message = "Refusing replay: fixed-run report already exists. No overwrite, deletion, or retry was attempted."
            return
        }
        guard Bundle.main.bundleIdentifier == Pins.bundleId else {
            message = "Bundle identifier mismatch. Expected " + Pins.bundleId + "; no run started."
            return
        }
        guard sceneActive else { message = "Scene is not active; refusing run."; return }
        guard SFSpeechRecognizer.authorizationStatus() == .authorized else {
            refreshAuthorization()
            message = "Speech authorization is not authorized. No run started."
            return
        }

        let identityReport: SourceIdentityReport
        do {
            let loaded = try Self.loadSourceIdentity()
            identityReport = Self.sourceReport(identity: loaded.0, rawData: loaded.1, sha: loaded.2)
        } catch {
            failEarly(source: nil, status: "fatal-source-identity", error: error, stage: "sourceIdentity")
            return
        }

        let manifest: Apple65Manifest
        do { manifest = try Self.loadPinnedManifest().0 }
        catch {
            failEarly(source: identityReport, status: "fatal-manifest", error: error, stage: "manifest")
            return
        }

        var report = Self.makeReport(source: identityReport,
                                     rows: manifest.cases.enumerated().map { CaseReport.initial(index: $0.offset + 1, clip: $0.element) },
                                     status: "initialized", error: nil)
        guard save(report) else { handleReportIO(report, stage: "initialReport"); return }

        do { try Self.validateAllWaves(manifest) }
        catch { failInputReport(report, error: error, stage: "preflight"); return }

        if Task.isCancelled || !sceneActive {
            finishReport(report, status: "stopped-cancelled", message: "Run cancelled before recognition. No Speech call was made.")
            return
        }

        var cancelledBeforeCase = false
        for (offset, clip) in manifest.cases.enumerated() {
            if Task.isCancelled || !sceneActive { cancelledBeforeCase = true; break }
            report.rows[offset].status = "in-progress"
            report.rows[offset].terminal = false
            report.runStatus = "running"
            report.updatedAt = Self.iso()
            guard save(report) else { handleReportIO(report, stage: "preRequest-" + clip.id); return }

            let outcome = await runCase(clip, index: offset + 1)
            report.rows[offset] = outcome.row
            guard save(report) else { handleReportIO(report, stage: "postRequest-" + clip.id); return }

            if outcome.stop {
                if let fatal = outcome.fatal { report.fatal = fatal }
                let status = outcome.stopStatus ?? "stopped"
                let finalMessage = status == "fatal-input" ? "Input integrity stopped the batch. Remaining rows stay not-run." : "Run stopped. Cleanup and final report are complete."
                finishReport(report, status: status, message: finalMessage)
                return
            }
        }

        if cancelledBeforeCase || Task.isCancelled || !sceneActive {
            finishReport(report, status: "stopped-cancelled", message: "Run cancelled. Cleanup and final report are complete.")
            return
        }

        do { _ = try Self.verifyAllInputs() }
        catch { failInputReport(report, error: error, stage: "finalVerification"); return }

        finishReport(report, status: "completed", message: "Fixed FILE alias completed. No scoring, quality pass, budget pass, or W3 completion is claimed.")
    }

    private func runCase(_ clip: Apple65Clip, index: Int) async -> CaseOutcome {
        var row = CaseReport.initial(index: index, clip: clip)
        let admission = DispatchTime.now().uptimeNanoseconds
        row.admissionUptimeNanos = admission
        let sampler = Self.startSampler(start: admission)
        var backend: AppleSpeechRecognizerBackend? = nil
        var client: SpeechRecognitionClient? = nil
        var status = "typed-error"
        var primaryError: ErrorRecord? = nil
        var stop = false
        var stopStatus: String? = nil
        var fatal: ErrorRecord? = nil
        var wholeEnd: UInt64? = nil
        var recognitionStart: UInt64? = nil
        var recognitionEnd: UInt64? = nil

        do {
            guard !Task.isCancelled else { throw ProbeError.scene }
            guard sceneActive else { throw ProbeError.scene }
            let url = try Self.safeAudioURL(clip)
            try Self.validateInput(clip, url: url)
            guard !Task.isCancelled, sceneActive else { throw ProbeError.scene }

            let madeBackend = try AppleSpeechRecognizerBackend(locale: clip.locale, timeout: Pins.timeout)
            backend = madeBackend
            guard madeBackend.supportedInputs == [SpeechRecognitionInputKind.audioFile] else {
                throw ProbeError.contract("supportedInputs")
            }
            let madeClient = try SpeechRecognitionClient(defaultBackendId: madeBackend.backendId,
                                                          backends: [madeBackend as any SpeechRecognitionBackend])
            client = madeClient
            let availability = await madeBackend.availability()
            row.availability = availability
            guard availability.status == .available && availability.offline else {
                throw ProbeError.availability("availability-not-available-offline")
            }
            let request = try SpeechRecognitionRequest(locale: clip.locale, audioFile: url)
            row.requestLocale = request.locale
            guard !Task.isCancelled, sceneActive else { throw ProbeError.scene }

            row.recognitionAttempted = true
            recognitionStart = DispatchTime.now().uptimeNanoseconds
            do {
                let result = try await madeClient.recognize(request, backend: .explicit(madeBackend.backendId))
                recognitionEnd = DispatchTime.now().uptimeNanoseconds
                row.resultBackendId = result.backendId
                row.transcript = result.transcript
                row.confidence = result.confidence
                status = result.transcript.isEmpty ? "completed-empty" : "recognized-nonempty"
            } catch {
                recognitionEnd = DispatchTime.now().uptimeNanoseconds
                primaryError = Self.errorRecord(error, stage: "recognize")
                if let typed = error as? SpeechRecognitionError {
                    if case .timedOut = typed { status = "timeout-or-cancel" }
                    else { status = "typed-error" }
                } else if error is CancellationError {
                    status = "timeout-or-cancel"
                } else {
                    status = "typed-error"
                }
                if Task.isCancelled || !sceneActive { stop = true }
            }
            wholeEnd = recognitionEnd
        } catch {
            wholeEnd = DispatchTime.now().uptimeNanoseconds
            let record = Self.errorRecord(error, stage: "preparation")
            primaryError = record
            switch error {
            case ProbeError.scene:
                stop = true
                stopStatus = "stopped-cancelled"
                status = "timeout-or-cancel"
            case ProbeError.input:
                stop = true
                stopStatus = "fatal-input"
                fatal = record
                status = "typed-error"
            default:
                stop = false
                status = "typed-error"
            }
        }

        let cleanup = await Self.cleanup(backend: backend, client: client)
        row.cleanup = cleanup
        sampler.cancel()
        let rawSamples = await sampler.value
        row.rss = Self.makeRSSReport(start: admission, raw: rawSamples)

        do {
            let postURL = try Self.safeAudioURL(clip)
            try Self.validateInput(clip, url: postURL)
            row.inputPostcheckOK = true
        } catch {
            let postError = Self.errorRecord(error, stage: "inputPostcheck")
            row.inputPostcheckOK = false
            row.inputPostcheckError = postError
            if row.error == nil { row.error = postError }
            stop = true
            stopStatus = "fatal-input"
            fatal = postError
            if row.recognitionAttempted == false || status == "typed-error" { status = "typed-error" }
        }

        if Task.isCancelled || !sceneActive {
            stop = true
            if stopStatus == nil { stopStatus = "stopped-cancelled" }
        }
        if let primaryError = primaryError { row.error = primaryError }
        row.status = status
        row.terminal = true
        row.wholeRequestWallSeconds = Self.elapsed(admission, wholeEnd)
        row.recognitionStartUptimeNanos = recognitionStart
        row.recognitionEndUptimeNanos = recognitionEnd
        row.recognitionDurationSeconds = Self.elapsed(recognitionStart, recognitionEnd)
        return CaseOutcome(row: row, stop: stop, stopStatus: stopStatus, fatal: fatal)
    }

    private static func cleanup(backend: AppleSpeechRecognizerBackend?, client: SpeechRecognitionClient?) async -> CleanupReport {
        if let client = client {
            let d0 = DispatchTime.now().uptimeNanoseconds
            await client.drain()
            let d1 = DispatchTime.now().uptimeNanoseconds
            await client.close()
            let d2 = DispatchTime.now().uptimeNanoseconds
            return CleanupReport(target: "client", drainSeconds: elapsed(d0, d1), closeSeconds: elapsed(d1, d2), cleanupCompleted: true)
        }
        if let backend = backend {
            let d0 = DispatchTime.now().uptimeNanoseconds
            await backend.drain()
            let d1 = DispatchTime.now().uptimeNanoseconds
            await backend.close()
            let d2 = DispatchTime.now().uptimeNanoseconds
            return CleanupReport(target: "backend", drainSeconds: elapsed(d0, d1), closeSeconds: elapsed(d1, d2), cleanupCompleted: true)
        }
        return CleanupReport(target: "none", drainSeconds: nil, closeSeconds: nil, cleanupCompleted: true)
    }

    private static func startSampler(start: UInt64) -> Task<RSSSamplerRaw, Never> {
        Task.detached(priority: .utility) {
            var samples: [RSSSample] = []
            var attempts = 0
            while !Task.isCancelled {
                let now = DispatchTime.now().uptimeNanoseconds
                let offset = now >= start ? Double(now - start) / 1_000_000.0 : 0
                attempts += 1
                samples.append(RSSSample(offsetMs: offset, bytes: f11ResidentMemoryBytes()))
                do { try await Task.sleep(nanoseconds: Pins.sampleIntervalNs) } catch { break }
            }
            return RSSSamplerRaw(samples: samples, attempts: attempts)
        }
    }

    private static func makeRSSReport(start: UInt64, raw: RSSSamplerRaw) -> RSSReport {
        let finalBytes = f11ResidentMemoryBytes()
        let end = DispatchTime.now().uptimeNanoseconds
        let endOffset = end >= start ? Double(end - start) / 1_000_000.0 : 0
        var samples = raw.samples
        samples.append(RSSSample(offsetMs: endOffset, bytes: finalBytes))
        let periodicOffsets = raw.samples.map { $0.offsetMs }
        var gaps: [Double] = []
        if let first = periodicOffsets.first { gaps.append(first) }
        if periodicOffsets.count > 1 {
            for i in 1..<periodicOffsets.count { gaps.append(periodicOffsets[i] - periodicOffsets[i - 1]) }
        }
        let trailing = periodicOffsets.last.map { max(0, endOffset - $0) } ?? endOffset
        gaps.append(trailing)
        let valid = samples.compactMap { $0.bytes }
        let over = gaps.filter { $0 > Pins.gapThresholdMs }
        return RSSReport(intervalMs: 100.0, samplingWindowStartNanos: start, samplingWindowEndNanos: end,
                         durationSeconds: end >= start ? Double(end - start) / 1_000_000_000.0 : 0,
                         sampleCount: samples.count, totalAttempts: raw.attempts + 1,
                         missingCount: samples.filter { $0.bytes == nil }.count,
                         minBytes: valid.min(), maxBytes: valid.max(), samples: samples, gapsMs: gaps,
                         maxGapMs: gaps.max(), overThresholdGapCount: over.count,
                         gapThresholdMs: Pins.gapThresholdMs,
                         leadingGapMs: periodicOffsets.first ?? endOffset, trailingGapMs: trailing)
    }

    private static func elapsed(_ start: UInt64?, _ end: UInt64?) -> Double? {
        guard let start = start, let end = end, end >= start else { return nil }
        return Double(end - start) / 1_000_000_000.0
    }

    private static func audioFixturesRoot() throws -> URL {
        guard let root = Bundle.main.url(forResource: "AudioFixtures", withExtension: nil) else {
            throw ProbeError.input("AudioFixtures-missing")
        }
        if isSymlink(root) { throw ProbeError.input("AudioFixtures-symlink") }
        return root
    }

    private static func isSymlink(_ url: URL) -> Bool {
        do { _ = try FileManager.default.destinationOfSymbolicLink(atPath: url.path); return true }
        catch { return false }
    }

    private static func safeAudioURL(_ clip: Apple65Clip) throws -> URL {
        guard clip.wavPath == "audio/" + clip.id + ".wav" else { throw ProbeError.input("wavPath") }
        let root = try audioFixturesRoot()
        let audioDir = root.appendingPathComponent("audio", isDirectory: true)
        let url = audioDir.appendingPathComponent(clip.id + ".wav", isDirectory: false)
        let rootPath = root.standardizedFileURL.path
        let candidatePath = url.standardizedFileURL.path
        guard candidatePath.hasPrefix(rootPath + "/") else { throw ProbeError.input("pathEscape") }
        if isSymlink(audioDir) || isSymlink(url) { throw ProbeError.input("wavSymlink") }
        var isDirectory: ObjCBool = false
        guard FileManager.default.fileExists(atPath: url.path, isDirectory: &isDirectory), !isDirectory.boolValue else {
            throw ProbeError.input("wavMissing")
        }
        return url
    }

    private static func validateInput(_ clip: Apple65Clip, url: URL) throws {
        let data: Data
        do {
            data = try Data(contentsOf: url)
        } catch {
            throw ProbeError.input("wave-read:" + clip.id)
        }
        do {
            try Apple65Core.validateWave(data, clip: clip)
        } catch {
            throw ProbeError.input("wave-validate:" + clip.id)
        }
    }

    private static func loadPinnedManifest() throws -> (Apple65Manifest, Data) {
        let root = try audioFixturesRoot()
        let url = root.appendingPathComponent("manifest.json", isDirectory: false)
        if isSymlink(url) { throw ProbeError.input("manifestSymlink") }
        let data = try Data(contentsOf: url)
        let manifest = try Apple65Core.loadManifest(data, expectedSHA256: Pins.manifestSHA256)
        guard manifest.runId == Pins.runId, manifest.backendId == Pins.backendId,
              manifest.inputBindingSHA256 == Pins.inputBindingSHA256 else {
            throw ProbeError.input("manifestPins")
        }
        return (manifest, data)
    }

    private static func validateAllWaves(_ manifest: Apple65Manifest) throws {
        for clip in manifest.cases {
            let url = try safeAudioURL(clip)
            try validateInput(clip, url: url)
        }
    }

    private static func verifyAllInputs() throws -> Apple65Manifest {
        let manifest = try loadPinnedManifest().0
        try validateAllWaves(manifest)
        return manifest
    }

    private static func isLowerHex64(_ value: String) -> Bool {
        let bytes = Array(value.utf8)
        guard bytes.count == 64 else { return false }
        for byte in bytes {
            let digit = byte >= 0x30 && byte <= 0x39
            let letter = byte >= 0x61 && byte <= 0x66
            if !digit && !letter { return false }
        }
        return true
    }

    private static func loadSourceIdentity() throws -> (SourceIdentity, Data, String) {
        guard let url = Bundle.main.url(forResource: "source-identity-manifest", withExtension: "json") else {
            throw ProbeError.source("identity-missing")
        }
        let data = try Data(contentsOf: url)
        guard let object = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any],
              Set(object.keys) == sourceRootKeys else { throw ProbeError.source("identity-rootKeys") }
        guard probeStrictInt(object["schemaVersion"]) == 1 else { throw ProbeError.source("identity-schemaVersion") }
        guard let runId = object["runId"] as? String, runId == Pins.runId else { throw ProbeError.source("identity-runId") }
        guard let backendId = object["backendId"] as? String, backendId == Pins.backendId else { throw ProbeError.source("identity-backendId") }
        guard let manifestSHA = object["manifestSHA256"] as? String, manifestSHA == Pins.manifestSHA256 else { throw ProbeError.source("identity-manifestSHA256") }
        guard let inputBinding = object["inputBindingSHA256"] as? String, inputBinding == Pins.inputBindingSHA256 else { throw ProbeError.source("identity-inputBindingSHA256") }
        guard let commit = object["sourceBaseCommit"] as? String, commit == Pins.sourceBaseCommit else { throw ProbeError.source("identity-sourceBaseCommit") }
        guard let boundary = object["distributionBoundary"] as? String, boundary == Pins.distributionBoundary else { throw ProbeError.source("identity-distributionBoundary") }
        guard let sources = object["selectedSources"] as? [[String: Any]], !sources.isEmpty else { throw ProbeError.source("identity-selectedSources") }
        for source in sources {
            guard Set(source.keys) == sourceItemKeys, let path = source["path"] as? String, !path.isEmpty,
                  let sha = source["sha256"] as? String, isLowerHex64(sha) else {
                throw ProbeError.source("identity-selectedSourceItem")
            }
        }
        let identity = try JSONDecoder().decode(SourceIdentity.self, from: data)
        return (identity, data, Apple65Core.sha256(data))
    }

    private static func sourceReport(identity: SourceIdentity, rawData: Data, sha: String) -> SourceIdentityReport {
        SourceIdentityReport(sha256: sha, runId: identity.runId, backendId: identity.backendId,
                             manifestSHA256: identity.manifestSHA256,
                             inputBindingSHA256: identity.inputBindingSHA256,
                             sourceBaseCommit: identity.sourceBaseCommit,
                             distributionBoundary: identity.distributionBoundary,
                             selectedSourceCount: identity.selectedSources.count,
                             rawJSON: String(data: rawData, encoding: .utf8) ?? rawData.base64EncodedString())
    }
}

private struct ContentView: View {
    @EnvironmentObject var model: RunModel
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("W3 Apple 65 FILE probe").font(.title2)
                Text("Isolated manual evaluation alias. No onAppear recognition, no microphone capture, and no automatic run.")
                Text(model.authorizationText).font(.footnote)
                HStack(spacing: 8) {
                    Button("Speech authorization setup") { model.requestSpeechAuthorization() }
                    Button("Run fixed FILE alias") { model.start() }.disabled(!model.canRun)
                    Button("Cancel") { model.cancel() }.disabled(!model.isBusy)
                }
                Text(model.message)
                Text("Report: " + model.reportPath).font(.footnote)
                Text(model.reportExists ? "Report exists: replay is refused." : "Report does not exist yet.").font(.footnote)
                Text("Report contents include per-case statuses, whole-request wall time, recognition duration, cleanup timing, App PID RSS sampling, and source/input identities. No scoring or W3 completion is claimed.").font(.footnote)
            }
            .padding()
        }
        .onChange(of: scenePhase) { phase in model.scenePhaseChanged(phase) }
    }
}

@main
struct W3Apple65FileApp: App {
    @StateObject private var model = RunModel()
    var body: some Scene {
        WindowGroup { ContentView().environmentObject(model) }
    }
}
