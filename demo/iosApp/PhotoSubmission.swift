import Foundation

/// Starts only the latest selected image after earlier native work releases its input.
@MainActor final class PhotoSubmission {
    private var generation: UInt64 = 0
    private var pending: Task<Void, Never>?

    func replace(after jobs: [Task<Void, Never>], perform: @MainActor @escaping () -> Void) {
        generation &+= 1
        let token = generation
        pending?.cancel()
        pending = Task { @MainActor in
            for job in jobs { await job.value }
            guard !Task.isCancelled, token == generation else { return }
            perform()
        }
    }

    func cancel() {
        generation &+= 1
        pending?.cancel()
        pending = nil
    }

    func settle() async { await pending?.value }
}
