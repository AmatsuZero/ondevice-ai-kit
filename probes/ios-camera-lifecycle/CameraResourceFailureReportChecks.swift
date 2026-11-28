import Foundation

@main struct CameraResourceFailureReportChecks {
    private enum ExpectedFailure: Error, CustomStringConvertible {
        case reportWrite

        var description: String { "report-write-failed" }
    }

    static func main() {
        var messages: [String] = []
        CameraResourceFailureReport.persist(
            runID: "01234567-89AB-CDEF-0123-456789ABCDEF",
            probeFailure: String(repeating: "p", count: 600),
            recordFailure: { throw ExpectedFailure.reportWrite },
            logger: { messages.append($0) }
        )
        precondition(messages.count == 1)
        precondition(messages[0].hasPrefix("ODAI_CAMERA_RESOURCE_REPORT_FAILURE "))
        precondition(messages[0].contains("run=01234567-89AB-CDEF-0123-456789ABCDEF"))
        precondition(messages[0].contains("probe=" + String(repeating: "p", count: 512)))
        precondition(!messages[0].contains(String(repeating: "p", count: 513)))
        precondition(messages[0].contains("report=report-write-failed"))

        var recorded = false
        messages.removeAll()
        CameraResourceFailureReport.persist(
            runID: "run",
            probeFailure: "probe",
            recordFailure: { recorded = true },
            logger: { messages.append($0) }
        )
        precondition(recorded)
        precondition(messages.isEmpty)
        print("Camera resource failure-report fallback checks passed.")
    }
}
