import Foundation
import Darwin

/// Local consumer instrumentation only; never interpreted as GPU/Metal allocation.
final class ResidentMemorySampler: @unchecked Sendable {
    static let method = "process-resident-mach-task-basic-info-10ms"
    private let lock = NSLock()
    private let readMemory: @Sendable () -> Int?
    private var baseline: Int?
    private var peak: Int?
    private var failed = false

    init(readMemory: @escaping @Sendable () -> Int? = ResidentMemorySampler.current) {
        self.readMemory = readMemory
        sample()
    }
    func sample() {
        let bytes = readMemory()
        lock.lock(); defer { lock.unlock() }
        guard let bytes, bytes > 0 else { failed = true; return }
        baseline = baseline ?? bytes
        peak = max(peak ?? bytes, bytes)
    }
    func snapshot() -> [String: Any] {
        lock.lock(); defer { lock.unlock() }
        guard !failed, let baseline, let peak else {
            return ["method": Self.method, "state": "unverified", "reason": "task_info failed"]
        }
        return ["method": Self.method, "baselineBytes": baseline,
                "peakBytes": peak, "deltaBytes": peak - baseline]
    }
    func run() async {
        while !Task.isCancelled { sample(); try? await Task.sleep(for: .milliseconds(10)) }
    }
    private static func current() -> Int? {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size / MemoryLayout<natural_t>.size)
        let result = withUnsafeMutablePointer(to: &info) { pointer in
            pointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), $0, &count)
            }
        }
        return result == KERN_SUCCESS ? Int(info.resident_size) : nil
    }
}

final class BenchmarkTiming: @unchecked Sendable {
    private let lock = NSLock()
    private var started: ContinuousClock.Instant?
    private var loadMs: Double?
    private var firstUpdateMs: Double?
    private var totalMs: Double?
    private var invalid = false
    func start() { lock.lock(); started = .now; lock.unlock() }
    func recordLoad(_ value: Double) {
        lock.lock(); defer { lock.unlock() }
        if started == nil || loadMs != nil || firstUpdateMs != nil || totalMs != nil { invalid = true }
        loadMs = value
    }
    func recordFirstUpdate() {
        lock.lock(); defer { lock.unlock() }
        if loadMs == nil || totalMs != nil { invalid = true }
        if firstUpdateMs == nil, let started { firstUpdateMs = milliseconds(started.duration(to: .now)) }
    }
    func finish() {
        lock.lock(); defer { lock.unlock() }
        if let started { totalMs = milliseconds(started.duration(to: .now)) }
    }
    func snapshot() -> (load: Double, first: Double, total: Double)? {
        lock.lock(); defer { lock.unlock() }
        guard !invalid, let loadMs, let firstUpdateMs, let totalMs,
              loadMs.isFinite, firstUpdateMs.isFinite, totalMs.isFinite,
              loadMs >= 0, loadMs <= firstUpdateMs, firstUpdateMs <= totalMs else { return nil }
        return (loadMs, firstUpdateMs, totalMs)
    }
    private func milliseconds(_ duration: Duration) -> Double {
        Double(duration.components.seconds) * 1000 + Double(duration.components.attoseconds) / 1e15
    }
}
