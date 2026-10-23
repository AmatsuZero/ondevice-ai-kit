import XCTest
import Foundation
import Darwin
@testable import ODAIKitModelStore

final class ModelStoreMemoryTests: XCTestCase {
    // Retaining autoreleased read buffers for the whole file makes real integrity
    // verification exceed this bound. Synthetic sparse bytes, no model downloads.
    func testLargeFileIntegrityDoesNotRetainFileSizedReadBuffers() async throws {
        let fm = FileManager.default, bytes = 64 * 1024 * 1024
        let root = fm.temporaryDirectory.appendingPathComponent("odai-hash-memory-" + UUID().uuidString)
        defer { try? fm.removeItem(at: root) }
        let source = root.appendingPathComponent("source")
        try fm.createDirectory(at: source, withIntermediateDirectories: true)
        let file = source.appendingPathComponent("weights.bin")
        XCTAssertTrue(fm.createFile(atPath: file.path, contents: nil))
        let handle = try FileHandle(forWritingTo: file)
        try handle.truncate(atOffset: UInt64(bytes)); try handle.close()
        let manifest = ModelManifest.fixture(file: .init(path: "weights.bin", url: URL(string: "https://models.example/zero.bin")!,
            bytes: Int64(bytes), sha256: "3b6a07d0d404fab4e23b6d34bc6696a6a312dd92821332385e5af7c01c421351"))
        let store = try ModelStore(root: root.appendingPathComponent("store"), compatibility: .fixture)
        let sampler = try HashFootprintSampler()
        let sampling = Task.detached { await sampler.run() }
        defer { sampling.cancel() }
        try await store.install(manifest: manifest, source: source)
        let lease = try await store.acquire(manifest.ref)
        sampler.sample()
        sampling.cancel(); await sampling.value
        XCTAssertTrue(sampler.valid, "phys_footprint sampling failed")
        let delta = sampler.delta
        print("ModelStore64MiB integrity peak incremental phys_footprint bytes: \(delta)")
        XCTAssertLessThan(delta, 96 * 1024 * 1024, "integrity hashing retains file-sized temporary buffers")
        await lease.close(); try await store.remove(manifest.ref)
    }
}

private final class HashFootprintSampler: @unchecked Sendable {
    private let lock = NSLock()
    private let baseline: UInt64
    private var peak: UInt64
    private var failed = false
    init() throws {
        let value = try XCTUnwrap(Self.current())
        baseline = value; peak = value
    }
    func sample() {
        let value = Self.current()
        lock.lock(); defer { lock.unlock() }
        if let value { peak = max(peak, value) } else { failed = true }
    }
    var valid: Bool { lock.lock(); defer { lock.unlock() }; return !failed }
    var delta: UInt64 { lock.lock(); defer { lock.unlock() }; return peak - baseline }
    func run() async {
        while !Task.isCancelled { sample(); try? await Task.sleep(nanoseconds: 1_000_000) }
    }
    private static func current() -> UInt64? {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
        let status = withUnsafeMutablePointer(to: &info) { pointer in
            pointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }
        return status == KERN_SUCCESS ? info.phys_footprint : nil
    }
}
