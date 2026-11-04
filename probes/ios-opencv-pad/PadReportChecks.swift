import Foundation

@main enum PadReportChecks {
    static func main() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let target = directory.appendingPathComponent("report.json")
        precondition(PadProbeReport.persist(["status": "passed", "checks": ["smoke": true]], to: target) == "passed")
        let stored = try JSONSerialization.jsonObject(with: Data(contentsOf: target)) as! [String: Any]
        precondition(stored["status"] as? String == "passed")
        precondition(PadProbeReport.persist(["status": "passed"], to: target.appendingPathComponent("impossible.json")) == "failed-report-write")
        precondition(PadProbeReport.persist(["status": "passed", "invalid": NSObject()], to: target) == "failed-report-write")
        print("PAD durable report success, filesystem failure and serialization failure checks passed")
    }
}
