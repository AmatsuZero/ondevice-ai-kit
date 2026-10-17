import Foundation

enum ASRProbeOutcome: Equatable {
    case transcript(String)
    case failure(domain: String, code: Int)
    case cancelled
    case timeout
}

/// Probe-local callback bridge; terminal results survive arrival before the waiter.
@MainActor
final class ASRProbeCompletion {
    private var result: ASRProbeOutcome?
    private var waiter: CheckedContinuation<ASRProbeOutcome, Never>?
    private var cancellation: (() -> Void)?
    private var cancellationRequested = false

    func installCancellation(_ action: @escaping () -> Void) {
        if cancellationRequested { action() }
        else if result == nil { cancellation = action }
    }

    func resolve(_ outcome: ASRProbeOutcome) {
        guard result == nil else { return }
        result = outcome
        cancellation = nil
        let ready = waiter
        waiter = nil
        ready?.resume(returning: outcome)
    }

    func cancel(_ outcome: ASRProbeOutcome) {
        guard result == nil else { return }
        cancellationRequested = true
        let stop = cancellation
        resolve(outcome)
        stop?()
    }

    func value() async -> ASRProbeOutcome {
        if let result { return result }
        return await withCheckedContinuation { waiter = $0 }
    }
}

enum ASRProbeText {
    static func matches(_ transcript: String, expected: String) -> Bool {
        let actual = normalize(transcript), wanted = normalize(expected)
        return !wanted.isEmpty && actual == wanted
    }

    private static func normalize(_ value: String) -> String {
        value.lowercased().filter { $0.isLetter || $0.isNumber }
    }
}
