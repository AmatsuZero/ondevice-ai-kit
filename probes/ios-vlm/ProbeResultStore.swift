import Foundation

struct ProbeResultStore: Sendable {
    let directory: URL

    func write(_ result: VLMRunResult) throws -> URL {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent("p9-run-\(UUID().uuidString).json")
        let data = try JSONEncoder().encode(result)
        try data.write(to: file, options: .atomic)
        return file
    }
}
