import Foundation
import CryptoKit
import Darwin
import AVFoundation
import SwiftUI
import UIKit
import ODAIKitSpeech
import ODAIKitAppleSpeech

// W8 TTS power workload probe — isolated standalone candidate.
// Constraints honored (see RUNBOOK.md / context/agent-pitfalls.md):
//  - Uses ONLY supplied real APIs (AppleAVSpeechBackend, SpeechSynthesisClient) and the
//    existing f11 mach_task_basic_info.resident_size sampler (reused unchanged below).
//  - 1 cycle, each >=30s monotonic (>=30000ms paced run); fresh REAL backend+client per cycle.
//  - One warm-up (cycle 0 only) excluded from measured samples; 2 measured speaks in the single cycle.
//  - en-US voice chosen ONCE, fail-closed if unavailable (no arbitrary locale fallback).
//  - Foreground + lifecycle observation (willResignActive/didEnterBackground) for the running
//    batch with a STICKY failure; checked after EVERY await (incl. final wait/close/1s RSS).
//  - requestedIdleMs = remaining duration AFTER work/close (NOT a fixed 30s); actualIdleMs is
//    MEASURED via systemUptime immediately before/after the await (never the computed sleep).
//    Full cycle duration is recorded AFTER the wait; completedCycles counts only FINISHED cycles.
//  - Per-cycle closeRSS captured IMMEDIATELY after client.close() and +1s, with monotonic
//    offsets; the final process RSS is reported SEPARATELY (not relabeled as close RSS).
//  - Failed measured request rows are PRESERVED in the report with the partial cycle; successful
//    requests are counted separately; a report-write failure at ANY checkpoint => failed.
//  - Real client.close + sampler cancel/await on EVERY path; partial honest failed report.
//  - No mic/ASR/Photos; public fixed text only; no audio persistence; budgets:false; productionReady:false.
//  - Sampled RSS != true/GPU peak, energy or zero-leak; raw timings cannot invent budgets.

@main
struct W8TTSSustainedApp: App {
    @StateObject private var model = W8TTSSustainedModel()
    var body: some Scene {
        WindowGroup {
            VStack(spacing: 16) {
                Text("W8 TTS Power Workload Probe").font(.title2)
                Text(model.status).accessibilityIdentifier("w8-tts-status")
                Button("Run TTS power workload") { model.run() }
                    .disabled(model.busy)
                    .accessibilityIdentifier("w8-tts-run")
                if let path = model.reportPath {
                    Text(path).font(.caption).textSelection(.enabled)
                }
            }
            .padding()
        }
    }
}

@MainActor
final class W8TTSSustainedModel: NSObject, ObservableObject {
    @Published private(set) var status = "Ready"
    @Published private(set) var busy = false
    @Published private(set) var reportPath: String?

    private let fixedText = "OnDevice AI Kit iOS speech probe"
    private let memoryMethod = "mach_task_basic_info.resident_size sampled approximately every 10 ms via a detached Task; sampled peak, not true peak; no GPU or energy measurement"
    private let kind = "w8-tts-power-workload-probe"
    private let schemaVersion = 1
    private let backendId = "apple.avspeech"
    private let language = "en-US"
    private let cycleCount = 1
    private let speaksPerCycle = 2
    private let cycleTargetSeconds = 30.0
    private let sampleIntervalNanoseconds: UInt64 = 10_000_000
    private let reportFileName = "w8-tts-power-report.json"

    // Sticky lifecycle flag for the running batch (set by UIApplication observers).
    private var lifecycleEventOccurred = false
    private var lifecycleObservers: [NSObjectProtocol] = []

    private struct RunState {
        var runId = ""
        var pid = 0
        var osVersion = ""
        var deviceModel = ""
        var selectedVoiceId = ""
        var textSHA256 = ""
        var startUptime: Double = 0
        // True wall-clock bounds (Date.timeIntervalSince1970 seconds). Optional so a
        // failed setup never fabricates a start; only set at the ACTUAL run start and
        // after final RSS/cleanup, before the final report. These are NOT derived from
        // monotonic startUptime/globalWallWindowMs, which are NOT absolute wallclock.
        var wallStartUnixSeconds: Double?
        var wallEndUnixSeconds: Double?
        var requestedIdleMsTotal = 0
        var actualIdleMsTotal = 0
        var cycles: [[String: Any]] = []
        var completedCycles = 0
        var requests = 0
        var successfulRequests = 0
        var status = "running"
        var terminal = false
        var failReason: String?
        var finalProcessRSS: [String: Any]?
        var totalMonotonicMs: Double = 0
        var globalWallWindowMs: Double = 0
    }

    private var state = RunState()

    // One-run guard: button disabled while busy; never auto-runs onAppear.
    func run() {
        guard !busy else { return }
        busy = true
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

    // MARK: - Execute

    private func execute() async {
        state = RunState()
        lifecycleEventOccurred = false
        // Guard 1: keep the device from auto-locking during this one user-triggered
        // ~600s batch. Save the EXACT prior `isIdleTimerDisabled` value and restore
        // it on EVERY exit (incl. early no-voice / report-write failure) in the
        // defer below. Only this measured run holds the timer disabled — no global
        // auto-lock permission is touched and no playback/start is triggered
        // automatically. Manual lifecycle changes (willResignActive /
        // didEnterBackground) still trip the sticky fail-closed flag independently,
        // so a real lock/background event still fails the run rather than passing.
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
        state.textSHA256 = sha256Hex(fixedText)
        state.startUptime = ProcessInfo.processInfo.systemUptime
        // ACTUAL run start: capture true wall-clock basis per parent requirement.
        state.wallStartUnixSeconds = Date().timeIntervalSince1970
        state.status = "running"
        state.terminal = false

        // Choose the en-US voice ONCE, fail-closed, no locale fallback.
        let voices = AVSpeechSynthesisVoice.speechVoices()
        guard let selected = voices.first(where: { $0.language == language }) else {
            finishAndExit(failed: true, reason: "no-en-US-voice-available")
            return
        }
        state.selectedVoiceId = selected.identifier
        if !persistCheckpoint() {
            finishAndExit(failed: true, reason: "report-write-failed")
            return
        }

        var terminalFailed = false

        cycleLoop: for cycle in 0..<cycleCount {
            let cycleStart = ProcessInfo.processInfo.systemUptime
            let outcome = await runCycle(cycle: cycle, voiceId: state.selectedVoiceId)
            switch outcome {
            case .success(let requestRecords, let closeRSS):
                // Lifecycle check after the runCycle await.
                if lifecycleEventOccurred {
                    terminalFailed = true
                    state.status = "failed"; state.terminal = true
                    state.failReason = "lifecycle-event-during-batch"
                    appendCycleRow(cycle: cycle, records: requestRecords, closeRSS: closeRSS,
                                   requestedIdleMs: nil, actualIdleMs: nil, fullCycleMs: nil,
                                   failed: true, reason: "lifecycle-event-during-batch")
                    // Record the actually observed attempted/successful counts for
                    // this failed row, exactly like the other (.failure) branch.
                    // The normal-path counts below are skipped because we break here.
                    state.requests += requestRecords.count
                    state.successfulRequests += requestRecords.filter { ($0["status"] as? String) == "success" }.count
                    if !persistCheckpoint() { terminalFailed = true }
                    break cycleLoop
                }

                // requestedIdleMs = remaining duration AFTER work/close (NOT a fixed 30s).
                let workElapsed = ProcessInfo.processInfo.systemUptime - cycleStart
                let requestedRemaining = max(0, cycleTargetSeconds - workElapsed)
                let requestedIdleMs = Int(requestedRemaining * 1000)

                // Partial progress BEFORE the wait: persist observed requests + closeRSS so a
                // later failure still keeps an honest partial record.
                appendCycleRow(cycle: cycle, records: requestRecords, closeRSS: closeRSS,
                               requestedIdleMs: requestedIdleMs, actualIdleMs: nil,
                               fullCycleMs: nil, failed: false, reason: nil)
                state.requests += requestRecords.count
                state.successfulRequests += requestRecords.filter { ($0["status"] as? String) == "success" }.count
                if !persistCheckpoint() {
                    terminalFailed = true
                    break cycleLoop
                }

                // Measure actual idle via systemUptime immediately before/after the await.
                let idleStart = ProcessInfo.processInfo.systemUptime
                if requestedRemaining > 0 {
                    try? await Task.sleep(nanoseconds: UInt64(requestedRemaining * 1_000_000_000))
                }
                let idleEnd = ProcessInfo.processInfo.systemUptime
                let actualIdleMs = Int((idleEnd - idleStart) * 1000)

                // Lifecycle check after the idle wait.
                if lifecycleEventOccurred {
                    terminalFailed = true
                    state.status = "failed"; state.terminal = true
                    state.failReason = "lifecycle-event-during-batch"
                    updateLastCycleRow(actualIdleMs: actualIdleMs, fullCycleMs: nil)
                    if !persistCheckpoint() { terminalFailed = true }
                    break cycleLoop
                }

                // Actual full cycle duration AFTER waiting.
                let cycleEnd = ProcessInfo.processInfo.systemUptime
                let fullCycleMs = Int((cycleEnd - cycleStart) * 1000)
                updateLastCycleRow(actualIdleMs: actualIdleMs, fullCycleMs: fullCycleMs)
                state.requestedIdleMsTotal += requestedIdleMs
                state.actualIdleMsTotal += actualIdleMs

                // Each completed row must prove >= 30000ms.
                if fullCycleMs < 30000 {
                    terminalFailed = true
                    state.status = "failed"; state.terminal = true
                    state.failReason = "cycle-\(cycle)-below-30000ms: fullCycleMs=\(fullCycleMs)"
                    if !persistCheckpoint() { terminalFailed = true }
                    break cycleLoop
                }

                // Count completedCycles ONLY when the entire cycle (incl. wait) has finished.
                state.completedCycles += 1
                if !persistCheckpoint() {
                    terminalFailed = true
                    break cycleLoop
                }

            case .failure(let reason, let requestRecords, let partialCloseRSS):
                terminalFailed = true
                state.status = "failed"; state.terminal = true
                state.failReason = reason
                // Preserve actually observed request record(s) and the partial cycle honestly.
                appendCycleRow(cycle: cycle, records: requestRecords, closeRSS: partialCloseRSS,
                               requestedIdleMs: nil, actualIdleMs: nil, fullCycleMs: nil,
                               failed: true, reason: reason)
                state.requests += requestRecords.count
                state.successfulRequests += requestRecords.filter { ($0["status"] as? String) == "success" }.count
                if !persistCheckpoint() { terminalFailed = true }
                break cycleLoop
            }
        }

        // Final process RSS (distinct from per-close RSS): immediate and +1s. This is NOT
        // labeled as close RSS; per-cycle closeRSS is captured immediately after each close.
        let rssImmediate = f11ResidentMemoryBytes()
        try? await Task.sleep(nanoseconds: 1_000_000_000)
        // Lifecycle check after the final RSS wait.
        if lifecycleEventOccurred, !terminalFailed {
            terminalFailed = true
            state.status = "failed"; state.terminal = true
            state.failReason = "lifecycle-event-during-batch"
        }
        let rss1s = f11ResidentMemoryBytes()
        // Guard 3: a nil final-process-RSS sample (immediate or +1s) means the
        // measurement is incomplete/failed BEFORE the completion decision. Mark
        // the run failed (keeping any earlier failure reason) and never complete.
        // The raw nil fields remain NSNull below — no invented 0 is written.
        if (rssImmediate == nil || rss1s == nil), !terminalFailed {
            terminalFailed = true
            state.status = "failed"; state.terminal = true
            state.failReason = "final-process-rss-incomplete"
        }
        state.finalProcessRSS = [
            "immediateBytes": rssImmediate.map { $0 as Any } ?? NSNull(),
            "after1sBytes": rss1s.map { $0 as Any } ?? NSNull()
        ]

        let endUptime = ProcessInfo.processInfo.systemUptime
        state.totalMonotonicMs = (endUptime - state.startUptime) * 1000
        // globalWallWindowMs is monotonic-only; it is NOT an absolute wall-clock window.
        // Keep it equal to totalMonotonicMs for the monotonic view, but do NOT treat it
        // as wall-clock. The true window is wallStartUnixSeconds..wallEndUnixSeconds.
        state.globalWallWindowMs = state.totalMonotonicMs
        // ACTUAL run end: after final RSS + cleanup, before the final report.
        state.wallEndUnixSeconds = Date().timeIntervalSince1970

        if !terminalFailed {
            // Every full cycle must prove >= 30000ms; exact 1 cycle / 2 successful requests
            // AND total >= 30000ms; lifecycle must not have fired during the batch.
            let allCyclesOk = state.cycles.allSatisfy { row in
                guard let fc = row["fullCycleMs"] as? Int else { return false }
                return fc >= 30000
            }
            if state.completedCycles == cycleCount &&
               state.successfulRequests == cycleCount * speaksPerCycle &&
               state.totalMonotonicMs >= 30_000 &&
               allCyclesOk &&
               !lifecycleEventOccurred {
                state.status = "completed"
                state.terminal = true
            } else {
                state.status = "failed"; state.terminal = true
                state.failReason = "incomplete: cycles=\(state.completedCycles) successfulRequests=\(state.successfulRequests) monotonicMs=\(Int(state.totalMonotonicMs))"
            }
        }

        // Final write; if it fails, persistCheckpoint reverts to failed (never completed).
        _ = persistCheckpoint()
        status = state.status == "completed" ? "Completed" : "Failed"
        busy = false
    }

    private func finishAndExit(failed: Bool, reason: String) {
        if failed {
            state.status = "failed"
            state.terminal = true
            if state.failReason == nil { state.failReason = reason }
        }
        _ = persistCheckpoint()
        status = state.status == "completed" ? "Completed" : "Failed"
        busy = false
    }

    private enum CycleOutcome {
        case success(requests: [[String: Any]], closeRSS: [String: Any])
        case failure(reason: String, requests: [[String: Any]], partialCloseRSS: [String: Any]?)
    }

    // Fresh REAL AppleAVSpeechBackend + SpeechSynthesisClient per cycle, closed on every path.
    private func runCycle(cycle: Int, voiceId: String) async -> CycleOutcome {
        guard UIApplication.shared.applicationState == .active else {
            return .failure(reason: "inactive-at-cycle-boundary", requests: [], partialCloseRSS: nil)
        }
        let backend = AppleAVSpeechBackend()
        let client: SpeechSynthesisClient
        do {
            client = try SpeechSynthesisClient(defaultBackendId: backend.backendId, backends: [backend])
        } catch {
            return .failure(reason: "client-init-failed: \(error)", requests: [], partialCloseRSS: nil)
        }

        // One initial warm-up, excluded only on the first cycle (not counted in the 40).
        if cycle == 0 {
            do {
                _ = try await client.speak(SpeechSynthesisRequest(text: fixedText, locale: language, voiceId: voiceId))
            } catch {
                await client.close()
                return .failure(reason: "warmup-failed: \(error)", requests: [], partialCloseRSS: nil)
            }
        }

        var requestRecords: [[String: Any]] = []
        for _ in 0..<speaksPerCycle {
            guard UIApplication.shared.applicationState == .active else {
                await client.close()
                return .failure(reason: "inactive-before-speak", requests: requestRecords, partialCloseRSS: nil)
            }
            let (rec, ok, err) = await measureSpeak(client: client, voiceId: voiceId)
            requestRecords.append(rec)
            if !ok {
                await client.close()
                return .failure(reason: "request-failed: \(err ?? "unknown")", requests: requestRecords, partialCloseRSS: nil)
            }
            guard UIApplication.shared.applicationState == .active else {
                await client.close()
                return .failure(reason: "inactive-after-speak", requests: requestRecords, partialCloseRSS: nil)
            }
            // Sticky lifecycle check after every speak await.
            if lifecycleEventOccurred {
                await client.close()
                return .failure(reason: "lifecycle-event-during-batch", requests: requestRecords, partialCloseRSS: nil)
            }
        }

        await client.close() // real client close (returns Void, no fabricated throw)
        // Capture closeRSS IMMEDIATELY after close, then +1s, with monotonic offset.
        let closeUptime = ProcessInfo.processInfo.systemUptime
        let closeImm = f11ResidentMemoryBytes()
        if lifecycleEventOccurred {
            return .failure(reason: "lifecycle-event-during-batch", requests: requestRecords, partialCloseRSS: nil)
        }
        try? await Task.sleep(nanoseconds: 1_000_000_000)
        if lifecycleEventOccurred {
            return .failure(reason: "lifecycle-event-during-batch", requests: requestRecords, partialCloseRSS: nil)
        }
        let close1s = f11ResidentMemoryBytes()
        if closeImm == nil || close1s == nil {
            // Nil RSS means incomplete/failed; do not relabel or claim success.
            return .failure(reason: "close-rss-incomplete", requests: requestRecords, partialCloseRSS: nil)
        }
        let closeOffsetMs = Int((closeUptime - state.startUptime) * 1000)
        let closeRSS: [String: Any] = [
            "immediateBytes": closeImm.map { $0 as Any } ?? NSNull(),
            "after1sBytes": close1s.map { $0 as Any } ?? NSNull(),
            "closeOffsetMs": closeOffsetMs
        ]
        return .success(requests: requestRecords, closeRSS: closeRSS)
    }

    // MARK: - Cycle row helpers

    private func appendCycleRow(cycle: Int, records: [[String: Any]], closeRSS: [String: Any]?,
                                requestedIdleMs: Int?, actualIdleMs: Any?, fullCycleMs: Any?,
                                failed: Bool, reason: String?) {
        var row: [String: Any] = [
            "cycle": cycle,
            "warmupExcluded": cycle == 0,
            "status": failed ? "failed" : "running",
            "requestedIdleMs": requestedIdleMs.map { $0 as Any } ?? NSNull(),
            "actualIdleMs": actualIdleMs.map { $0 as Any } ?? NSNull(),
            "fullCycleMs": fullCycleMs.map { $0 as Any } ?? NSNull(),
            "closeRSS": closeRSS.map { $0 as Any } ?? NSNull(),
            "requests": records
        ]
        if let reason = reason { row["failReason"] = reason }
        state.cycles.append(row)
    }

    private func updateLastCycleRow(actualIdleMs: Int, fullCycleMs: Any?) {
        guard !state.cycles.isEmpty else { return }
        var row = state.cycles.removeLast()
        row["actualIdleMs"] = actualIdleMs
        row["fullCycleMs"] = fullCycleMs.map { $0 as Any } ?? NSNull()
        if let fc = fullCycleMs as? Int, fc >= 30000 {
            row["status"] = "completed"
        }
        state.cycles.append(row)
    }

    // One measured speak with the ~10ms f11 sampler; sampler cancelled+awaited on every path.
    private func measureSpeak(client: SpeechSynthesisClient, voiceId: String) async -> (rec: [String: Any], ok: Bool, err: String?) {
        let requestId = UUID().uuidString
        let rssStart = f11ResidentMemoryBytes()
        let started = ProcessInfo.processInfo.systemUptime
        let interval = sampleIntervalNanoseconds
        let sampler = Task.detached(priority: .utility) { () -> [UInt64] in
            var values: [UInt64] = []
            while !Task.isCancelled {
                if let v = f11ResidentMemoryBytes() { values.append(v) }
                try? await Task.sleep(nanoseconds: interval)
            }
            return values
        }
        var speakErr: String? = nil
        var speakStatus = "success"
        do {
            _ = try await client.speak(SpeechSynthesisRequest(text: fixedText, locale: language, voiceId: voiceId))
        } catch {
            speakStatus = "failed"
            speakErr = String(describing: error)
        }
        sampler.cancel()
        let samples = await sampler.value
        let rssEnd = f11ResidentMemoryBytes()
        let durationMs = (ProcessInfo.processInfo.systemUptime - started) * 1000
        let sampleMax = samples.max()
        // sampling nil => fail-closed, measurement incomplete; do NOT mark complete.
        let samplingComplete = (rssStart != nil) && (rssEnd != nil) && (!samples.isEmpty)
        let ok = (speakStatus == "success") && samplingComplete
        let errorValue: Any = speakErr.map { $0 as Any } ?? (samplingComplete ? NSNull() : "sampling-incomplete" as Any)
        let rec: [String: Any] = [
            "requestId": requestId,
            "status": ok ? "success" : "failed",
            "error": errorValue,
            "durationMs": durationMs,
            "voiceId": voiceId,
            "language": language,
            "sampleCount": samples.count,
            "rssStartBytes": rssStart.map { $0 as Any } ?? NSNull(),
            "rssEndBytes": rssEnd.map { $0 as Any } ?? NSNull(),
            "rssSampleMaxBytes": sampleMax.map { $0 as Any } ?? NSNull(),
            "memoryMethod": memoryMethod
        ]
        let finalErr = speakErr ?? (samplingComplete ? nil : "sampling-incomplete")
        return (rec, ok, finalErr)
    }

    private func sha256Hex(_ string: String) -> String {
        let data = Data(string.utf8)
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    // Pretty report saved atomically; returns false on write failure (never recorded as success).
    private func saveReport() -> Bool {
        let dict: [String: Any] = [
            "schemaVersion": schemaVersion,
            "kind": kind,
            "runId": state.runId,
            "PID": state.pid,
            "actualOS": state.osVersion,
            "device": state.deviceModel,
            "backendId": backendId,
            "selectedVoice": state.selectedVoiceId,
            "language": language,
            "textSHA256": state.textSHA256,
            "sourceCommit": "PARENT_ATTESTED_NOT_EMBEDDED",
            "lifecycleEventOccurred": lifecycleEventOccurred,
            // wallClock: true wall-clock bounds (Date.timeIntervalSince1970 seconds).
            // Parent must INDEPENDENTLY check elapsed wall vs monotonic to reject clock
            // jumps, and require the actual warmup/requests/close INSIDE the recorded
            // trace; durations alone cannot prove trace containment. No joule/budget claim.
            "wallClock": [
                "basis": "Date.timeIntervalSince1970 seconds",
                "wallStartUnixSeconds": state.wallStartUnixSeconds.map { $0 as Any } ?? NSNull(),
                "wallEndUnixSeconds": state.wallEndUnixSeconds.map { $0 as Any } ?? NSNull()
            ],
            "elapsed": [
                "startUptime": state.startUptime,
                "totalMonotonicMs": state.totalMonotonicMs,
                "globalWallWindowMs": state.globalWallWindowMs,
                "requestedCycleIdleMsTotal": state.requestedIdleMsTotal,
                "actualCycleIdleMsTotal": state.actualIdleMsTotal
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
            "budgets": false,
            "productionReady": false,
            "failReason": state.failReason.map { $0 as Any } ?? NSNull()
        ]
        do {
            let data = try JSONSerialization.data(withJSONObject: dict, options: [.prettyPrinted, .sortedKeys])
            let baseDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("w8ttspower")
            try FileManager.default.createDirectory(at: baseDir, withIntermediateDirectories: true)
            let file = baseDir.appendingPathComponent(reportFileName)
            try data.write(to: file, options: .atomic)
            reportPath = file.lastPathComponent
            return true
        } catch {
            status = "Failed: report write error"
            return false
        }
    }

    // Checkpoint persist: a write failure flips the run to failed (never completed later).
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

// Unchanged f11 sampler — reused exactly from the existing RSS sampler (ExistingTTSProbe.swift context).
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
