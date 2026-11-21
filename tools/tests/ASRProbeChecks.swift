import Foundation

@main
struct ASRProbeChecks {
    @MainActor static func main() async {
        // Completion must survive a callback or cancellation before a waiter exists.
        let early = ASRProbeCompletion()
        early.resolve(.transcript("hello"))
        let earlyValue = await early.value()
        precondition(earlyValue == .transcript("hello"))
        let cancelled = ASRProbeCompletion()
        cancelled.cancel(.cancelled)
        var cancellationCalls = 0
        cancelled.installCancellation { cancellationCalls += 1 }
        cancelled.resolve(.transcript("late"))
        let cancellation = await cancelled.value()
        precondition(cancellation == .cancelled && cancellationCalls == 1)
        cancelled.cancel(.timeout)
        precondition(cancellationCalls == 1)

        let pending = ASRProbeCompletion()
        let waiter = Task { await pending.value() }
        await Task.yield()
        pending.resolve(.transcript("first"))
        pending.resolve(.transcript("second"))
        let first = await waiter.value
        precondition(first == .transcript("first"))

        let timedOut = ASRProbeCompletion()
        var stopped = false
        timedOut.installCancellation { stopped = true }
        timedOut.cancel(.timeout)
        let timeout = await timedOut.value()
        precondition(timeout == .timeout && stopped)

        precondition(ASRProbeText.matches("The blue bicycle.", expected: "the BLUE bicycle"))
        precondition(!ASRProbeText.matches("", expected: "the blue bicycle"))
        precondition(!ASRProbeText.matches("the blue", expected: "the blue bicycle"))
        precondition(!ASRProbeText.matches("the red bicycle", expected: "the blue bicycle"))
        print("ASR probe checks passed: early/late completion, cancellation, timeout, exact nonempty match")
    }
}
