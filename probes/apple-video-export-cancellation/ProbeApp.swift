#if os(iOS)
import Foundation
import CryptoKit
import SwiftUI

@main struct ExportCancellationApp: App {
    @State private var status = "Ready"
    @State private var started = false

    var body: some Scene {
        WindowGroup {
            Text(status).padding().task {
                guard !started, CommandLine.arguments.contains("--export-cancellation-suite") else { return }
                started = true
                let id = UUID().uuidString
                let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
                let directory = documents.appendingPathComponent(id, isDirectory: true)
                var report: [String: Any] = ["schemaVersion": 1, "runId": id, "pid": getpid(),
                    "os": ProcessInfo.processInfo.operatingSystemVersionString,
                    "scope": "instrumented-current-mux-export-start-cancellation"]
                do {
                    let manifest = try Data(contentsOf: Bundle.main.url(forResource: "source-manifest", withExtension: "json")!)
                    report["sourceManifestSHA256"] = SHA256.hash(data: manifest).map { String(format: "%02x", $0) }.joined()
                    report["runs"] = try await ExportCancellationSuite.run(fixtures: Bundle.main.resourceURL!, root: directory)
                    report["state"] = "success"
                } catch {
                    report["state"] = "failed"
                    report["error"] = String(describing: error)
                }
                do {
                    let data = try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys])
                    try data.write(to: documents.appendingPathComponent("export-cancellation-\(id).json"), options: .withoutOverwriting)
                    status = report["state"] as? String ?? "failed"
                    print("ODAI_EXPORT_CANCELLATION \(String(decoding: data, as: UTF8.self))")
                } catch { status = "Report write failed"; NSLog("ODAI_EXPORT_CANCELLATION_REPORT_FAILED") }
            }
        }
    }
}
#endif
