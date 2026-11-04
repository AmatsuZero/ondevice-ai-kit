import Foundation

enum PadProbeReport {
    static func persist(_ report: [String: Any], to destination: URL) -> String {
        guard JSONSerialization.isValidJSONObject(report) else { return "failed-report-write" }
        do {
            let data = try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys])
            try data.write(to: destination, options: .atomic)
            return report["status"] as? String ?? "failed"
        } catch {
            return "failed-report-write"
        }
    }
}
