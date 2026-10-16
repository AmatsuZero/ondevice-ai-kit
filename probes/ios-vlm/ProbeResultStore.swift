import Foundation
import Darwin

struct ProbeResultStore: Sendable {
    let directory: URL

    func write(_ result: VLMRunResult) throws -> URL {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent("p9-run-\(UUID().uuidString).json")
        var system = utsname()
        uname(&system)
        let device = withUnsafePointer(to: &system.machine) {
            $0.withMemoryRebound(to: CChar.self, capacity: 1) { String(cString: $0) }
        }
        guard var object = try JSONSerialization.jsonObject(with: JSONEncoder().encode(result)) as? [String: Any] else {
            throw CocoaError(.fileWriteInapplicableStringEncoding)
        }
        object["schemaVersion"] = 1
        object["runId"] = UUID().uuidString
        object["device"] = device
        object["OS"] = ProcessInfo.processInfo.operatingSystemVersionString
        #if targetEnvironment(simulator)
        object["validationStatus"] = "simulator"
        #else
        object["validationStatus"] = "device"
        #endif
        let data = try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
        try data.write(to: file, options: .atomic)
        return file
    }
}
