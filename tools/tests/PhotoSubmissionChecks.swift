import Foundation

@main struct PhotoSubmissionChecks {
    @MainActor static func main() async {
        var releasePrevious: CheckedContinuation<Void, Never>?
        let previous = Task { @MainActor in
            await withCheckedContinuation { releasePrevious = $0 }
        }
        while releasePrevious == nil { await Task.yield() }

        let submissions = PhotoSubmission()
        var accepted: [String] = []
        submissions.replace(after: [previous]) { accepted.append("old") }
        submissions.replace(after: [previous]) { accepted.append("latest") }
        await Task.yield()
        precondition(accepted.isEmpty, "new photo must wait for old inference cleanup")
        releasePrevious?.resume()
        await submissions.settle()
        precondition(accepted == ["latest"], "only the latest photo starts inference")

        submissions.replace(after: []) { accepted.append("cancelled") }
        submissions.cancel()
        await submissions.settle()
        precondition(accepted == ["latest"], "camera restart must invalidate a pending photo")
        print("Photo submission checks passed")
    }
}
