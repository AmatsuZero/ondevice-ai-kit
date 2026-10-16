import Foundation

struct ProbeLaunchConfiguration: Sendable {
    let modelDirectory: URL
    let input: URL
    let manifest: URL
    let autoRun: Bool
    let cancelAfterMs: UInt64?

    static func from(arguments: [String], documents: URL) -> ProbeLaunchConfiguration {
        func value(after flag: String, default fallback: String) -> String {
            guard let index = arguments.firstIndex(of: flag), arguments.indices.contains(index + 1) else {
                return fallback
            }
            return arguments[index + 1]
        }

        func resolve(_ value: String) -> URL {
            return value.hasPrefix("/")
                ? URL(fileURLWithPath: value)
                : documents.appendingPathComponent(value)
        }

        let cancelAfterMs = UInt64(value(after: "--p9-cancel-after-ms", default: "0"))
            .flatMap { (1...60_000).contains($0) ? $0 : nil }

        return ProbeLaunchConfiguration(
            modelDirectory: resolve(value(after: "--p9-model-directory", default: "model")),
            input: resolve(value(after: "--p9-input", default: "fixture.png")),
            manifest: resolve(value(after: "--p9-manifest", default: "p9-smolvlm-manifest.json")),
            autoRun: arguments.contains("--p9-auto-run"), cancelAfterMs: cancelAfterMs)
    }
}
