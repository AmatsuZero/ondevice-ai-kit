#if EXPORT_CANCELLATION_CLI
import Foundation

@main struct ExportCancellationCLI {
    static func main() async throws {
        guard CommandLine.arguments.count == 3 else { throw ProbeFailure.failed("fixture directory and NEW output directory required") }
        let fixtures = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
        let root = URL(fileURLWithPath: CommandLine.arguments[2], isDirectory: true)
        do {
            let runs = try await ExportCancellationSuite.run(fixtures: fixtures, root: root)
            print("PASS: \(runs.count) native export cancellations and same-process AAC reentries; see \(root.path)")
        } catch {
            print("FAIL: \(error)")
            exit(EXIT_FAILURE)
        }
    }
}
#endif
