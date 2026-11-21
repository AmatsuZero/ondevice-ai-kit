import Foundation
import CryptoKit
import Darwin
import SwiftUI
import UIKit
import Speech
import ODAIKitCore
import ODAIKitSpeech
import ODAIKitAppleSpeech

// W8 Apple FILE ASR sustained resource probe — isolated standalone candidate.
// Constraints honored (see RUNBOOK.md / context/agent-pitfalls.md):
//  - Uses ONLY supplied real APIs (AppleSpeechRecognizerBackend, SpeechRecognitionClient,
//    SpeechRecognitionRequest/Result) and the existing TTS mach_task_basic_info.resident_size
//    sampler (reused unchanged below). No API is reconstructed or invented.
//  - DISTINCT from W3 (quality) and W8 TTS (synthesis): this is a FILE ASR resource probe.
//  - 20 cycles, each >=30s monotonic (>=600s paced run); fresh REAL backend+client per cycle.
//  - One warm-up (cycle 0 only) excluded from measured samples; 2 measured recognitions/cycle = 40.
//  - en-US only, AppleSpeechRecognizerBackend(locale:timeout:30), SpeechRecognitionClient explicit
//    apple.speech.recognizer. Local on-device only; no mic, no network, no permission request.
//  - Check EXISTING Speech authorization == .authorized BEFORE backend creation; never request it.
//  - All 3 WAV full bytes/sha verified before first recognition and after each use/close.
//  - No translations, text rewriting, CER gates, or model-version claim: OS model hash NOT_REPORTED.
//  - Foreground + lifecycle observation (willResignActive/didEnterBackground) with a STICKY failure;
//    checked after EVERY await (incl. recognize/close/drain/idle/final 1s RSS).
//  - requestedIdleMs = remaining duration AFTER work/close (NOT a fixed 30s); actualIdleMs is
//    MEASURED via systemUptime immediately before/after the await. Active work vs idle tracked apart.
//  - Per-cycle closeRSS captured IMMEDIATELY after client.close() and +1s, with monotonic offsets;
//    the final process RSS is reported SEPARATELY (not relabeled as close RSS).
//  - Failed measured request rows PRESERVED in the report with the partial cycle; successful requests
//    counted separately; a report-write failure at ANY checkpoint => failed (never completed).
//  - Real client.drain + client.close on EVERY path (incl. setup/report/lifecycle failure); no retries.
//  - RSS sampled ~10ms with cancel AND await; raw samples are NOT stored — only aggregate
//    count/min/max/quantiles via a bounded reservoir (declared method). Counts are never faked.
//  - No mic/Photos; public fixed 3 LibriSpeech WAV clips only; budgets/energy/GPU:false; productionReady:false.
//  - Frozen prospective protocol (context/protocol.json) is PINNED in this source: dataset, revision
//    and the exact three clip IDs/byte lengths/SHA-256 must match the bundled manifest BEFORE any
//    backend or inference (no self-attested or fallback inputs).
//  - Info.plist ODAISourceIdentity must be trimmed and nonempty; a missing/empty value is a terminal
//    PRE-INFERENCE failure (no PARENT_NOT_SUPPLIED).
//  - Sticky lifecycle trip checked after EVERY await before any further inference (warm-up,
//    availability, recognition, drain/close, +1s RSS wait, idle); failure is sticky even if active.
//  - ONE cleanup path per cycle: drain/close first, then hash-check ALL 3 fixed WAV inputs on EVERY
//    success/failure path; original failure preserved, post-close integrity failure appended
//    distinctly. Failed measured rows stay in the report; no recognition retry.
//  - availability() is a SpeechBackend API on the backend (backend.availability()), NOT
//    client.availability(); if client init fails, close the backend directly once; once the client
//    exists, client.close() closes the registered backend (never close it twice).

// Frozen prospective protocol (context/protocol.json): pinned external dataset/revision and the
// three fixed clip IDs with byte lengths and SHA-256 digests. The bundled AudioFixtures/manifest.json
// is validated against these pinned records; it is NOT trusted as the source of its own expected
// hashes. This prevents an internally self-consistent report bound to the wrong workload.
private struct PinnedClip: Sendable {
    let id: String
    let bytes: Int
    let sha256: String
}
private let kPinnedDataset = "hf-internal-testing/librispeech_asr_dummy"
private let kPinnedDatasetRevision = "5be91486e11a2d616f4ec5db8d3fd248585ac07a"
private let kPinnedClips: [PinnedClip] = [
    PinnedClip(id: "1272-128104-0000", bytes: 187438,
               sha256: "6ba71ddda339c469a52fa48a375e17e2487a336f4434ae24ef5118637bbcb925"),
    PinnedClip(id: "1272-128104-0001", bytes: 154158,
               sha256: "86f4d398b91c99d38568efa94a0f9604a0edaa15bd675d90e556852ec598c914"),
    PinnedClip(id: "1272-128104-0002", bytes: 399598,
               sha256: "3165bb4372ed834ddf2dd440e83b8e523b474d3c0eb54951cda81f0ed332505d")
]

@main
struct W8AppleASRSustainedApp: App {
    @StateObject private var model = W8AppleASRSustainedModel()
    @State private var authStatus: SFSpeechRecognizerAuthorizationStatus = .notDetermined
    @State private var authInFlight = false

    private var authStatusLabel: String {
        switch authStatus {
        case .notDetermined: return "notDetermined"
        case .authorized: return "authorized"
        case .denied: return "denied"
        case .restricted: return "restricted"
        @unknown default: return "unknown"
        }
    }

    var body: some Scene {
        WindowGroup {
            VStack(spacing: 16) {
                Text("W8 Apple File ASR Sustained Probe").font(.title2)
                Text(model.status).accessibilityIdentifier("w8-asr-status")
                ProgressView(value: model.progress)
                    .accessibilityIdentifier("w8-asr-progress")
                    .frame(maxWidth: 260)
                Text(authStatusLabel).accessibilityIdentifier("w8-asr-authorization-status")
                Button("Prepare file Speech authorization") {
                    let current = SFSpeechRecognizer.authorizationStatus()
                    authStatus = current
                    guard current == .notDetermined else { return }
                    authInFlight = true
                    SFSpeechRecognizer.requestAuthorization { status in
                        DispatchQueue.main.async {
                            authStatus = status
                            authInFlight = false
                        }
                    }
                }
                .disabled(model.busy || model.hasRun || authInFlight || authStatus != .notDetermined)
                .accessibilityIdentifier("w8-asr-authorize")
                Button("Run file ASR sustained") { Task { await model.run() } }
                    .disabled(model.busy || model.hasRun || authInFlight || authStatus != .authorized)
                    .accessibilityIdentifier("w8-asr-run")
                if let path = model.reportPath {
                    Text(path).font(.caption).textSelection(.enabled)
                }
            }
            .padding()
            .onAppear {
                authStatus = SFSpeechRecognizer.authorizationStatus()
            }
        }
    }
}

// Bounded RSS aggregator. Raw 10ms samples are never retained; only count/min/max (exact) and a
// bounded reservoir (capacity) used to approximate quantiles. Method is declared in the report.
private struct RssAggregate: Sendable {
    var sampleCount: Int = 0
    private var minBytes: UInt64 = .max
    private var maxBytes: UInt64 = 0
    private var reservoir: [UInt64] = []
    private let capacity: Int = 1024
    private var seen: UInt64 = 0

    mutating func add(_ v: UInt64) {
        sampleCount += 1
        seen += 1
        if v < minBytes { minBytes = v }
        if v > maxBytes { maxBytes = v }
        if reservoir.count < capacity {
            reservoir.append(v)
        } else {
            let j = UInt64.random(in: 0..<seen)
            if j < UInt64(capacity) {
                reservoir[Int(j)] = v
            }
        }
    }

    func quantiles() -> (p50: UInt64?, p95: UInt64?, p99: UInt64?) {
        guard !reservoir.isEmpty else { return (nil, nil, nil) }
        let sorted = reservoir.sorted()
        func q(_ p: Double) -> UInt64 {
            let idx = min(sorted.count - 1, max(0, Int((Double(sorted.count) - 1) * p)))
            return sorted[idx]
        }
        return (q(0.5), q(0.95), q(0.99))
    }

    func toDict() -> [String: Any] {
        let q = quantiles()
        return [
            "method": "bounded reservoir sampling, capacity \(capacity); 10ms interval; "
                      + "count/min/max exact, quantiles approximate over retained window",
            "sampleCount": sampleCount,
            "minBytes": minBytes == .max ? NSNull() : NSNumber(value: minBytes),
            "maxBytes": maxBytes == 0 ? NSNull() : NSNumber(value: maxBytes),
            "p50Bytes": q.p50.map { NSNumber(value: $0) } ?? NSNull(),
            "p95Bytes": q.p95.map { NSNumber(value: $0) } ?? NSNull(),
            "p99Bytes": q.p99.map { NSNumber(value: $0) } ?? NSNull()
        ]
    }
}

@MainActor
final class W8AppleASRSustainedModel: ObservableObject {
    @Published private(set) var status = "Ready"
    @Published private(set) var busy = false
    @Published private(set) var hasRun = false
    @Published private(set) var progress: Double = 0
    @Published private(set) var reportPath: String?

    private let kind = "w8-apple-file-asr-sustained-resource-probe"
    private let schemaVersion = 1
    private let backendId = "apple.speech.recognizer"
    private let language = "en-US"
    private let cycleCount = 20
    private let recognitionsPerCycle = 2
    private let cycleTargetSeconds = 30.0
    private let sampleIntervalNanoseconds: UInt64 = 10_000_000
    private let reportSubdir = "w8appleasrsustained"
    private let reportFileName = "report.json"
    private let memoryMethod = "mach_task_basic_info.resident_size sampled approximately every 10 ms via a detached Task; sampled peak/quantiles, not true peak; no GPU or energy measurement"

    // Sticky lifecycle flag for the running batch (set by UIApplication observers).
    private var lifecycleEventOccurred = false
    private var lifecycleObservers: [NSObjectProtocol] = []
    private var requestCounter = 0

    private struct RunState {
        var runId = ""
        var pid = 0
        var osVersion = ""
        var deviceModel = ""
        var appInstallPath = ""
        var bundleIdentifier = ""
        var sourceIdentity = ""
        var manifestSha256 = ""
        var dataset = ""
        var datasetRevision = ""
        var datasetLicense = ""
        var startUptime: Double = 0
        var startUnix: Double = 0
        var endUnix: Double = 0
        var requestedCycleIdleMsTotal = 0
        var actualCycleIdleMsTotal = 0
        var activeWorkMsTotal = 0
        var idleMsTotal = 0
        var totalMonotonicMs: Double = 0
        var globalWallWindowMs: Double = 0
        var cycles: [[String: Any]] = []
        var completedCycles = 0
        var requests = 0
        var successfulRequests = 0
        var status = "running"
        var terminal = false
        var failReason: String?
        var finalProcessRSS: [String: Any]?
        var lifecycleEventOccurred = false
    }

    private var state = RunState()

    // One-run guard: button disabled while busy or after it has run (one-shot).
    func run() {
        guard !busy, !hasRun else { return }
        busy = true
        hasRun = true
        status = "Starting"
        Task { await execute() }
    }

    // MARK: - Lifecycle observers (running batch only)

    private func installLifecycleObservers() {
        let res = NotificationCenter.default.addObserver(
            forName: UIApplication.willResignActiveNotification, object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.lifecycleEventOccurred = true }
        }
        let bg = NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.lifecycleEventOccurred = true }
        }
        lifecycleObservers = [res, bg]
    }

    private func removeLifecycleObservers() {
        for o in lifecycleObservers { NotificationCenter.default.removeObserver(o) }
        lifecycleObservers.removeAll()
    }

    /// Sticky lifecycle trip checked after EVERY await before any further inference. A
    /// willResignActive/didEnterBackground event stays terminal even if the app returns to .active.
    private func lifecycleTripReason() -> String? {
        if lifecycleEventOccurred { return "lifecycle-event-during-batch" }
        if UIApplication.shared.applicationState != .active { return "inactive-after-await" }
        return nil
    }

    private func joinedFailures(_ a: String?, _ b: String?) -> String? {
        if let a, let b { return "\(a) + \(b)" }
        return a ?? b
    }

    // MARK: - Execute

    private func execute() async {
        state = RunState()
        lifecycleEventOccurred = false
        requestCounter = 0

        // Guard: keep the device from auto-locking during this one user-triggered ~600s batch.
        // Save the EXACT prior isIdleTimerDisabled value and restore it on EVERY exit.
        let priorIdleTimerDisabled = UIApplication.shared.isIdleTimerDisabled
        UIApplication.shared.isIdleTimerDisabled = true
        installLifecycleObservers()
        defer {
            removeLifecycleObservers()
            UIApplication.shared.isIdleTimerDisabled = priorIdleTimerDisabled
        }

        state.runId = UUID().uuidString
        state.pid = Int(ProcessInfo.processInfo.processIdentifier)
        state.osVersion = UIDevice.current.systemVersion
        state.deviceModel = UIDevice.current.model
        state.appInstallPath = Bundle.main.bundlePath
        state.bundleIdentifier = Bundle.main.bundleIdentifier ?? "unknown"
        // Parent-supplied source identity is REQUIRED: a missing/empty value is a terminal
        // pre-inference failure. Never invent one (no PARENT_NOT_SUPPLIED).
        let rawIdentity = Bundle.main.object(forInfoDictionaryKey: "ODAISourceIdentity") as? String
        let trimmedIdentity = rawIdentity?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !trimmedIdentity.isEmpty else {
            state.sourceIdentity = ""
            finishAndExit(failed: true, reason: "source-identity-missing-or-empty")
            return
        }
        state.sourceIdentity = trimmedIdentity
        state.startUptime = ProcessInfo.processInfo.systemUptime
        state.startUnix = Date().timeIntervalSince1970
        state.status = "running"
        state.terminal = false

        // Parse the EXACT frozen manifest schema; missing/incorrect => terminal fail BEFORE inference.
        let manifest = parseManifest()
        if let pe = manifest.parseError {
            finishAndExit(failed: true, reason: reasonFromError(pe))
            return
        }
        state.manifestSha256 = manifest.manifestSha256
        state.dataset = manifest.dataset
        state.datasetRevision = manifest.datasetSourceRevision
        state.datasetLicense = manifest.license

        // Check EXISTING Speech authorization == authorized BEFORE backend creation.
        // Do NOT request permission, mic, or any network fallback.
        let auth = SFSpeechRecognizer.authorizationStatus()
        guard auth == .authorized else {
            finishAndExit(failed: true, reason: "speech-authorization-not-authorized: \(auth)")
            return
        }

        // Verify all 3 WAV full bytes/sha BEFORE first recognition.
        var setupFailure: [String: Any]?
        for clip in manifest.clips {
            let verify = verifyClip(clip, at: audioURL(for: clip))
            if let vFail = verify.failure { setupFailure = vFail; break }
        }
        if let failure = setupFailure {
            finishAndExit(failed: true, reason: reasonFromError(failure))
            return
        }

        // Initial durable running receipt BEFORE work.
        if !persistCheckpoint() {
            finishAndExit(failed: true, reason: "report-write-failed")
            return
        }

        var terminalFailed = false

        cycleLoop: for cycle in 0..<cycleCount {
            let cycleStart = ProcessInfo.processInfo.systemUptime
            let outcome = await runCycle(cycle: cycle, clips: manifest.clips)
            switch outcome {
            case .success(let requestRecords, let warmupRecord, let closeRSS, let activeWorkMs):
                if let trip = lifecycleTripReason() {
                    terminalFailed = true
                    state.status = "failed"; state.terminal = true
                    state.failReason = trip
                    appendCycleRow(cycle: cycle, warmup: warmupRecord, records: requestRecords,
                                   closeRSS: closeRSS, requestedIdleMs: nil, actualIdleMs: nil,
                                   activeWorkMs: nil, fullCycleMs: nil, failed: true, reason: trip)
                    state.requests += requestRecords.count
                    state.successfulRequests += requestRecords.filter { ($0["status"] as? String) == "success" }.count
                    if !persistCheckpoint() { terminalFailed = true }
                    break cycleLoop
                }

                let workElapsed = ProcessInfo.processInfo.systemUptime - cycleStart
                let requestedRemaining = max(0, cycleTargetSeconds - workElapsed)
                let requestedIdleMs = Int(requestedRemaining * 1000)

                appendCycleRow(cycle: cycle, warmup: warmupRecord, records: requestRecords,
                               closeRSS: closeRSS, requestedIdleMs: requestedIdleMs, actualIdleMs: nil,
                               activeWorkMs: activeWorkMs, fullCycleMs: nil, failed: false, reason: nil)
                state.requests += requestRecords.count
                state.successfulRequests += requestRecords.filter { ($0["status"] as? String) == "success" }.count
                if !persistCheckpoint() { terminalFailed = true; break cycleLoop }

                let idleStart = ProcessInfo.processInfo.systemUptime
                if requestedRemaining > 0 {
                    try? await Task.sleep(nanoseconds: UInt64(requestedRemaining * 1_000_000_000))
                }
                let idleEnd = ProcessInfo.processInfo.systemUptime
                let actualIdleMs = Int((idleEnd - idleStart) * 1000)

                if let trip = lifecycleTripReason() {
                    terminalFailed = true
                    state.status = "failed"; state.terminal = true
                    state.failReason = trip
                    updateLastCycleRow(actualIdleMs: actualIdleMs, activeWorkMs: activeWorkMs, fullCycleMs: nil)
                    if !persistCheckpoint() { terminalFailed = true }
                    break cycleLoop
                }

                let cycleEnd = ProcessInfo.processInfo.systemUptime
                let fullCycleMs = Int((cycleEnd - cycleStart) * 1000)
                updateLastCycleRow(actualIdleMs: actualIdleMs, activeWorkMs: activeWorkMs, fullCycleMs: fullCycleMs)
                state.requestedCycleIdleMsTotal += requestedIdleMs
                state.actualCycleIdleMsTotal += actualIdleMs
                state.activeWorkMsTotal += activeWorkMs
                state.idleMsTotal += actualIdleMs

                if fullCycleMs < 30000 {
                    terminalFailed = true
                    state.status = "failed"; state.terminal = true
                    state.failReason = "cycle-\(cycle)-below-30000ms: fullCycleMs=\(fullCycleMs)"
                    if !persistCheckpoint() { terminalFailed = true }
                    break cycleLoop
                }

                state.completedCycles += 1
                progress = Double(state.completedCycles) / Double(cycleCount)
                if !persistCheckpoint() { terminalFailed = true; break cycleLoop }

            case .failure(let reason, let warmupRecord, let requestRecords, let partialCloseRSS, let activeWorkMs):
                terminalFailed = true
                state.status = "failed"; state.terminal = true
                state.failReason = reason
                appendCycleRow(cycle: cycle, warmup: warmupRecord, records: requestRecords,
                               closeRSS: partialCloseRSS, requestedIdleMs: nil, actualIdleMs: nil,
                               activeWorkMs: activeWorkMs, fullCycleMs: nil, failed: true, reason: reason)
                state.requests += requestRecords.count
                state.successfulRequests += requestRecords.filter { ($0["status"] as? String) == "success" }.count
                if !persistCheckpoint() { terminalFailed = true }
                break cycleLoop
            }
        }

        // Final process RSS (distinct from per-close RSS): immediate and +1s.
        let finalStart = ProcessInfo.processInfo.systemUptime
        let rssImmediate = f11ResidentMemoryBytes()
        if let trip = lifecycleTripReason(), !terminalFailed {
            terminalFailed = true
            state.status = "failed"; state.terminal = true
            state.failReason = trip
        }
        try? await Task.sleep(nanoseconds: 1_000_000_000)
        if let trip = lifecycleTripReason(), !terminalFailed {
            terminalFailed = true
            state.status = "failed"; state.terminal = true
            state.failReason = trip
        }
        let rss1s = f11ResidentMemoryBytes()
        if (rssImmediate == nil || rss1s == nil), !terminalFailed {
            terminalFailed = true
            state.status = "failed"; state.terminal = true
            state.failReason = "final-process-rss-incomplete"
        }
        state.finalProcessRSS = [
            "immediateBytes": rssImmediate.map { $0 as Any } ?? NSNull(),
            "after1sBytes": rss1s.map { $0 as Any } ?? NSNull(),
            "offsetMs": Int((finalStart - state.startUptime) * 1000)
        ]

        let endUptime = ProcessInfo.processInfo.systemUptime
        state.totalMonotonicMs = (endUptime - state.startUptime) * 1000
        state.globalWallWindowMs = state.totalMonotonicMs
        state.endUnix = Date().timeIntervalSince1970

        if !terminalFailed {
            let allCyclesOk = state.cycles.allSatisfy { row in
                guard let fc = row["fullCycleMs"] as? Int else { return false }
                return fc >= 30000
            }
            if state.completedCycles == cycleCount &&
               state.successfulRequests == cycleCount * recognitionsPerCycle &&
               state.totalMonotonicMs >= 600_000 &&
               allCyclesOk &&
               !lifecycleEventOccurred {
                state.status = "completed"
                state.terminal = true
            } else {
                state.status = "failed"; state.terminal = true
                state.failReason = "incomplete: cycles=\(state.completedCycles) successfulRequests=\(state.successfulRequests) monotonicMs=\(Int(state.totalMonotonicMs))"
            }
        }

        // A report-write failure must NEVER produce "completed": force the failed terminal state.
        if !persistCheckpoint() {
            state.status = "failed"
            state.terminal = true
            if state.failReason == nil { state.failReason = "report-write-failed" }
        }
        status = state.status == "completed" ? "Completed" : "Failed"
        busy = false
    }

    private func finishAndExit(failed: Bool, reason: String) {
        if failed {
            state.status = "failed"
            state.terminal = true
            if state.failReason == nil { state.failReason = reason }
        }
        state.lifecycleEventOccurred = lifecycleEventOccurred
        _ = persistCheckpoint()
        status = state.status == "completed" ? "Completed" : "Failed"
        busy = false
    }

    private enum CycleOutcome {
        case success(requests: [[String: Any]], warmup: [String: Any]?, closeRSS: [String: Any]?, activeWorkMs: Int)
        case failure(reason: String, warmup: [String: Any]?, requests: [[String: Any]],
                     partialCloseRSS: [String: Any]?, activeWorkMs: Int?)
    }

    // Fresh REAL AppleSpeechRecognizerBackend + SpeechRecognitionClient per cycle, closed once on
    // every path. Once the client exists, client.close() closes the registered backend
    // (SpeechClient.swift), so the backend is NEVER closed a second time.
    private func runCycle(cycle: Int, clips: [ManifestClip]) async -> CycleOutcome {
        guard !lifecycleEventOccurred, UIApplication.shared.applicationState == .active else {
            return .failure(reason: lifecycleEventOccurred ? "lifecycle-event-during-batch" : "inactive-at-cycle-boundary",
                            warmup: nil, requests: [], partialCloseRSS: nil, activeWorkMs: nil)
        }
        let cycleStart = ProcessInfo.processInfo.systemUptime

        let backend: AppleSpeechRecognizerBackend
        do {
            backend = try AppleSpeechRecognizerBackend(locale: language, timeout: 30)
        } catch {
            return .failure(reason: "backend-init-failed: \(safeCategory(error))", warmup: nil,
                            requests: [], partialCloseRSS: nil, activeWorkMs: nil)
        }
        let client: SpeechRecognitionClient
        do {
            client = try SpeechRecognitionClient(defaultBackendId: backendId, backends: [backend])
        } catch {
            // No client exists: close the constructed backend exactly once, directly.
            await backend.close()
            return .failure(reason: "client-init-failed: \(safeCategory(error))", warmup: nil,
                            requests: [], partialCloseRSS: nil, activeWorkMs: nil)
        }

        // From here there is exactly ONE cleanup path on every success/failure exit.
        let work = await performCycleWork(cycle: cycle, clips: clips, client: client, backend: backend)
        let cleanup = await closeAndVerify(client: client, clips: clips, cycleStart: cycleStart)

        var failures: [String] = []
        for candidate in [work.failure, cleanup.lifecycleFailure, cleanup.closeRSSFailure,
                          cleanup.integrityFailure].compactMap({ $0 }) {
            if !failures.contains(candidate) { failures.append(candidate) }
        }
        let combined = failures.isEmpty ? nil : failures.joined(separator: " + ")

        if let combined {
            return .failure(reason: combined, warmup: work.warmup, requests: work.records,
                            partialCloseRSS: cleanup.closeRSS, activeWorkMs: cleanup.activeWorkMs)
        }
        guard let closeRSS = cleanup.closeRSS else {
            return .failure(reason: "close-rss-missing", warmup: work.warmup, requests: work.records,
                            partialCloseRSS: nil, activeWorkMs: cleanup.activeWorkMs)
        }
        return .success(requests: work.records, warmup: work.warmup, closeRSS: closeRSS,
                        activeWorkMs: cleanup.activeWorkMs)
    }

    private struct WorkResult {
        var warmup: [String: Any]?
        var records: [[String: Any]]
        var failure: String?
    }

    // Performs all inference for one cycle; stops at the first failure (NO recognition retry).
    // The sticky lifecycle trip is checked after EVERY await before any further inference.
    private func performCycleWork(cycle: Int, clips: [ManifestClip],
                                  client: SpeechRecognitionClient,
                                  backend: AppleSpeechRecognizerBackend) async -> WorkResult {
        var result = WorkResult(warmup: nil, records: [], failure: nil)

        if cycle == 0 {
            // One warm-up, excluded from the 40 measured recognitions.
            let clip = clips[0]
            let rec = await runOneRecognition(client: client, clip: clip, requestIndex: -1, warmup: true)
            result.warmup = rec.rec
            let own = rec.ok ? nil : "warmup-failed: \(rec.err ?? "unknown")"
            if let combined = joinedFailures(own, lifecycleTripReason()) {
                result.failure = combined
                return result
            }
        }

        for _ in 0..<recognitionsPerCycle {
            if let trip = lifecycleTripReason() {
                result.failure = trip
                return result
            }
            let clip = clips[requestCounter % clips.count]
            let requestIndex = requestCounter
            requestCounter += 1

            // Verify the specific clip audio BEFORE this recognition.
            let preVerify = verifyClip(clip, at: audioURL(for: clip))
            if let vFail = preVerify.failure {
                result.failure = reasonFromError(vFail)
                return result
            }

            // Availability is a SpeechBackend API: backend.availability() (NOT client.availability()).
            let av = await backend.availability()
            let availabilityTrip = lifecycleTripReason()
            guard av.status == .available, av.offline == true else {
                let reason = av.reason.map { "\($0)" } ?? "unsupportedDevice"
                result.failure = joinedFailures("availability-unavailable: \(reason)", availabilityTrip)
                return result
            }
            if let availabilityTrip {
                result.failure = availabilityTrip
                return result
            }

            let rec = await runOneRecognition(client: client, clip: clip,
                                              requestIndex: requestIndex, warmup: false)
            result.records.append(rec.rec)
            let own = rec.ok ? nil : "request-failed: \(rec.err ?? "unknown")"
            if let combined = joinedFailures(own, lifecycleTripReason()) {
                result.failure = combined
                return result
            }
        }
        return result
    }

    private struct CleanupResult {
        var closeRSS: [String: Any]?
        var activeWorkMs: Int
        var closeRSSFailure: String?
        var integrityFailure: String?
        var lifecycleFailure: String?
    }

    // The SINGLE per-cycle cleanup path: drain/close first, capture immediate/+1s closeRSS, then
    // hash-check ALL three fixed WAV inputs. Runs on EVERY success and failure path once a client
    // exists. client.close() closes the registered backend exactly once (SpeechClient.swift); the
    // backend is NOT closed separately here.
    private func closeAndVerify(client: SpeechRecognitionClient, clips: [ManifestClip],
                                cycleStart: Double) async -> CleanupResult {
        var out = CleanupResult(closeRSS: nil, activeWorkMs: 0, closeRSSFailure: nil,
                                integrityFailure: nil, lifecycleFailure: nil)

        await client.drain()
        if let trip = lifecycleTripReason() { out.lifecycleFailure = trip }
        await client.close()
        if out.lifecycleFailure == nil, let trip = lifecycleTripReason() { out.lifecycleFailure = trip }

        let workEndUptime = ProcessInfo.processInfo.systemUptime
        out.activeWorkMs = Int((workEndUptime - cycleStart) * 1000)

        // Capture closeRSS IMMEDIATELY after close, then +1s, with monotonic offset.
        let closeUptime = ProcessInfo.processInfo.systemUptime
        let closeImm = f11ResidentMemoryBytes()
        try? await Task.sleep(nanoseconds: 1_000_000_000)
        if out.lifecycleFailure == nil, let trip = lifecycleTripReason() { out.lifecycleFailure = trip }
        let close1s = f11ResidentMemoryBytes()
        if closeImm == nil || close1s == nil {
            out.closeRSSFailure = "close-rss-incomplete"
        }
        let closeOffsetMs = Int((closeUptime - state.startUptime) * 1000)
        out.closeRSS = [
            "immediateBytes": closeImm.map { $0 as Any } ?? NSNull(),
            "after1sBytes": close1s.map { $0 as Any } ?? NSNull(),
            "closeOffsetMs": closeOffsetMs
        ]

        // Post-close integrity: hash-check ALL 3 WAV inputs after use/close on EVERY path.
        // The original failure is preserved by the caller; this is a DISTINCT appended failure.
        for clip in clips {
            let verify = verifyClip(clip, at: audioURL(for: clip))
            if let vFail = verify.failure {
                out.integrityFailure = "post-close-integrity-failure: clip=\(clip.id) \(reasonFromError(vFail))"
                break
            }
        }
        return out
    }

    // One measured (or warm-up) recognition with the ~10ms f11 sampler; sampler cancelled+awaited.
    // Raw samples are NOT stored; only the bounded aggregate is returned.
    private func runOneRecognition(client: SpeechRecognitionClient, clip: ManifestClip,
                                   requestIndex: Int, warmup: Bool) async
        -> (rec: [String: Any], ok: Bool, err: String?) {
        let requestId = UUID().uuidString
        let audioURL = self.audioURL(for: clip)
        let verify = verifyClip(clip, at: audioURL)
        let inputSHA = verify.observedSha256

        let request: SpeechRecognitionRequest
        do {
            request = try SpeechRecognitionRequest(locale: language, audioFile: audioURL)
        } catch {
            let safe = reasonFromError(classifyError(error, stage: "request-build"))
            let rec: [String: Any] = failedRec(requestId: requestId, requestIndex: requestIndex, clip: clip,
                                               inputSHA: inputSHA, err: safe)
            return (rec, false, safe)
        }

        let startUnix = Date().timeIntervalSince1970
        let started = ProcessInfo.processInfo.systemUptime
        let interval = sampleIntervalNanoseconds
        let sampler = Task.detached(priority: .utility) { () -> RssAggregate in
            var agg = RssAggregate()
            while !Task.isCancelled {
                if let v = f11ResidentMemoryBytes() { agg.add(v) }
                try? await Task.sleep(nanoseconds: interval)
            }
            return agg
        }

        var recStatus = "success"
        var recErr: String? = nil
        var hypothesis = ""
        var confidence: Any = NSNull()
        do {
            let result = try await client.recognize(request, backend: .explicit(backendId))
            hypothesis = result.transcript
            if let c = result.confidence, c.isFinite {
                confidence = NSNumber(value: c)
            }
        } catch {
            recStatus = "failed"
            recErr = reasonFromError(classifyError(error, stage: "recognize"))
        }
        sampler.cancel()
        let agg = await sampler.value
        let endUnix = Date().timeIntervalSince1970
        let durationMs = (ProcessInfo.processInfo.systemUptime - started) * 1000

        let samplingOk = agg.sampleCount > 0
        let ok = (recStatus == "success") && samplingOk
        let errorValue: Any = recErr.map { $0 as Any } ?? (samplingOk ? NSNull() : "sampling-incomplete" as Any)
        let finalErr = recErr ?? (samplingOk ? nil : "sampling-incomplete")

        let rec: [String: Any] = [
            "requestId": requestId,
            "requestIndex": requestIndex,
            "warmup": warmup,
            "clipId": clip.id,
            "inputSHA": inputSHA,
            "backendId": backendId,
            "locale": language,
            "hypothesis": hypothesis,
            "confidence": confidence,
            "startUnix": startUnix,
            "endUnix": endUnix,
            "durationMs": durationMs,
            "rss": agg.toDict(),
            "status": ok ? "success" : "failed",
            "error": errorValue
        ]
        return (rec, ok, finalErr)
    }

    private func failedRec(requestId: String, requestIndex: Int, clip: ManifestClip,
                           inputSHA: String, err: String) -> [String: Any] {
        [
            "requestId": requestId,
            "requestIndex": requestIndex,
            "clipId": clip.id,
            "inputSHA": inputSHA,
            "backendId": backendId,
            "locale": language,
            "hypothesis": "",
            "confidence": NSNull(),
            "startUnix": 0,
            "endUnix": 0,
            "durationMs": 0,
            "rss": NSNull(),
            "status": "failed",
            "error": err
        ]
    }

    // MARK: - Cycle row helpers

    private func appendCycleRow(cycle: Int, warmup: [String: Any]?, records: [[String: Any]],
                                closeRSS: [String: Any]?, requestedIdleMs: Int?, actualIdleMs: Any?,
                                activeWorkMs: Any?, fullCycleMs: Any?, failed: Bool, reason: String?) {
        var row: [String: Any] = [
            "cycle": cycle,
            "warmupExcluded": cycle == 0,
            "status": failed ? "failed" : "running",
            "requestedIdleMs": requestedIdleMs.map { $0 as Any } ?? NSNull(),
            "actualIdleMs": actualIdleMs.map { $0 as Any } ?? NSNull(),
            "activeWorkMs": activeWorkMs.map { $0 as Any } ?? NSNull(),
            "fullCycleMs": fullCycleMs.map { $0 as Any } ?? NSNull(),
            "closeRSS": closeRSS.map { $0 as Any } ?? NSNull(),
            "requests": records
        ]
        if let warmup { row["warmup"] = warmup }
        if let reason { row["failReason"] = reason }
        state.cycles.append(row)
    }

    private func updateLastCycleRow(actualIdleMs: Int, activeWorkMs: Int, fullCycleMs: Any?) {
        guard !state.cycles.isEmpty else { return }
        var row = state.cycles.removeLast()
        row["actualIdleMs"] = actualIdleMs
        row["activeWorkMs"] = activeWorkMs
        row["fullCycleMs"] = fullCycleMs.map { $0 as Any } ?? NSNull()
        if let fc = fullCycleMs as? Int, fc >= 30000 {
            row["status"] = "completed"
        }
        state.cycles.append(row)
    }

    // MARK: - Manifest parsing (EXACT frozen schema: schemaVersion 1, source[], clips[] w/ pcmWav)

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

    private func parseManifest() -> ParsedManifest {
        let base = Bundle.main.resourceURL ?? Bundle.main.bundleURL
        let url = base.appendingPathComponent("AudioFixtures").appendingPathComponent("manifest.json")
        guard let data = try? Data(contentsOf: url) else {
            return ParsedManifest(schemaVersion: 0, datasetSourceRevision: "", dataset: "", license: "",
                                  manifestSha256: "", clips: [],
                                  parseError: ["stage": "manifest",
                                               "error": ["type": "unavailable", "reason": "missing-or-unreadable"]])
        }
        let manifestSha = sha256Hex(of: data)
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return ParsedManifest(schemaVersion: 0, datasetSourceRevision: "", dataset: "", license: "",
                                  manifestSha256: manifestSha, clips: [],
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
            return ParsedManifest(schemaVersion: 1, datasetSourceRevision: "", dataset: "", license: "",
                                  manifestSha256: manifestSha, clips: [],
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
        // Bind the mutable bundled manifest to the PINNED frozen protocol records: exact dataset,
        // revision, and exactly the three fixed IDs with their byte lengths and SHA-256 values.
        // This must pass BEFORE any backend is created or inference begins.
        guard dataset == kPinnedDataset, revision == kPinnedDatasetRevision else {
            return ParsedManifest(schemaVersion: 1, datasetSourceRevision: revision, dataset: dataset,
                                  license: license, manifestSha256: manifestSha, clips: [],
                                  parseError: ["stage": "manifest",
                                               "error": ["type": "dataset-or-revision-mismatch"]])
        }
        guard clips.count == kPinnedClips.count else {
            return ParsedManifest(schemaVersion: 1, datasetSourceRevision: revision, dataset: dataset,
                                  license: license, manifestSha256: manifestSha, clips: [],
                                  parseError: ["stage": "manifest",
                                               "error": ["type": "fixed-input-count-mismatch"]])
        }
        for pinned in kPinnedClips {
            guard let match = clips.first(where: { $0.id == pinned.id }),
                  match.expectedBytes == pinned.bytes,
                  match.expectedSha256 == pinned.sha256,
                  match.wavPath == "audio/\(pinned.id).wav" else {
                return ParsedManifest(schemaVersion: 1, datasetSourceRevision: revision, dataset: dataset,
                                      license: license, manifestSha256: manifestSha, clips: [],
                                      parseError: ["stage": "manifest",
                                                   "error": ["type": "fixed-input-mismatch:\(pinned.id)"]])
            }
        }
        return ParsedManifest(schemaVersion: 1, datasetSourceRevision: revision, dataset: dataset,
                              license: license, manifestSha256: manifestSha, clips: clips, parseError: nil)
    }

    private func audioURL(for clip: ManifestClip) -> URL {
        let base = Bundle.main.resourceURL ?? Bundle.main.bundleURL
        return base.appendingPathComponent("AudioFixtures").appendingPathComponent(clip.wavPath)
    }

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
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private func reasonFromError(_ failure: [String: Any]) -> String {
        if let stage = failure["stage"] as? String,
           let err = failure["error"] as? [String: Any],
           let type = err["type"] as? String {
            return "\(stage):\(type)"
        }
        return "setup-failure"
    }

    /// Typed failure classification. Arbitrary errors are recorded by SAFE class name only.
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
                return ["stage": stage, "error": ["type": "client", "category": "invalidRegistration"]]
            }
        }
        return ["stage": stage, "error": ["type": "other", "category": safeCategory(error)]]
    }

    private func safeCategory(_ error: Error) -> String {
        String(describing: type(of: error))
    }

    // MARK: - Report persistence

    private func reportURL() -> URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir = docs.appendingPathComponent(reportSubdir)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent(reportFileName)
    }

    private func saveReport() -> Bool {
        let dict: [String: Any] = [
            "schemaVersion": schemaVersion,
            "kind": kind,
            "runId": state.runId,
            "PID": state.pid,
            "actualOS": state.osVersion,
            "device": state.deviceModel,
            "appInstallPath": state.appInstallPath,
            "bundleIdentifier": state.bundleIdentifier,
            "sourceIdentity": state.sourceIdentity,
            "backendId": backendId,
            "locale": language,
            "onDeviceOnly": true,
            "dataset": state.dataset,
            "datasetRevision": state.datasetRevision,
            "datasetLicense": state.datasetLicense,
            "manifestSha256": state.manifestSha256,
            "modelSourceHash": "NOT_REPORTED",
            "osModelHash": "NOT_REPORTED",
            "requestedCycleSeconds": cycleTargetSeconds,
            "cycleCount": cycleCount,
            "recognitionsPerCycle": recognitionsPerCycle,
            "measuredSuccessRequired": cycleCount * recognitionsPerCycle,
            "lifecycleEventOccurred": lifecycleEventOccurred,
            "timeCorrelation": [
                "startUnix": state.startUnix,
                "endUnix": state.endUnix,
                "startUptime": state.startUptime
            ],
            "elapsed": [
                "startUptime": state.startUptime,
                "totalMonotonicMs": state.totalMonotonicMs,
                "globalWallWindowMs": state.globalWallWindowMs,
                "requestedCycleIdleMsTotal": state.requestedCycleIdleMsTotal,
                "actualCycleIdleMsTotal": state.actualCycleIdleMsTotal,
                "activeWorkMsTotal": state.activeWorkMsTotal,
                "idleMsTotal": state.idleMsTotal
            ],
            "status": state.status,
            "terminal": state.terminal,
            "completedCycles": state.completedCycles,
            "requests": state.requests,
            "successfulRequests": state.successfulRequests,
            "cycles": state.cycles,
            "finalProcessRSS": state.finalProcessRSS.map { $0 as Any } ?? NSNull(),
            "microphoneStarts": 0,
            "memoryMethod": memoryMethod,
            "sampledRSSNotTruePeak": true,
            "energy": false,
            "gpu": false,
            "budgets": false,
            "productionReady": false,
            "failReason": state.failReason.map { $0 as Any } ?? NSNull()
        ]
        do {
            let data = try JSONSerialization.data(withJSONObject: dict, options: [.prettyPrinted, .sortedKeys])
            let file = reportURL()
            try data.write(to: file, options: .atomic)
            reportPath = (reportSubdir as NSString).appendingPathComponent(reportFileName)
            return true
        } catch {
            status = "Failed: report write error"
            return false
        }
    }

    private func persistCheckpoint() -> Bool {
        let ok = saveReport()
        if !ok {
            state.status = "failed"
            state.terminal = true
            if state.failReason == nil { state.failReason = "report-write-failed" }
        }
        return ok
    }
}

// Unchanged f11 sampler — reused exactly from the existing TTS RSS sampler.
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
