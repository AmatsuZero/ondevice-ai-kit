import Foundation

/// The only cancellation/delivery linearization point. No suspension while locked.
final class VoiceEffectsDecision: @unchecked Sendable {
    private enum State { case running, cancelled, delivered }
    private let lock = NSLock()
    private var state: State = .running
    func cancel() {
        lock.lock(); defer { lock.unlock() }
        if case .running = state { state = .cancelled }
    }
    func check() throws {
        lock.lock(); defer { lock.unlock() }
        if case .cancelled = state { throw CancellationError() }
    }
    func commit() -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard case .running = state else { return false }
        state = .delivered
        return true
    }
}
