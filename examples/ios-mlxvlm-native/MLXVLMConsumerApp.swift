import SwiftUI
import CryptoKit
import Darwin
import ODAIKitAppleMLXVLM
import ODAIKitModelStore
import ODAIKitMultimodal

@main
struct MLXVLMConsumerApp: App {
    @State private var status = "Ready. Provision the fixed public fixture and model before running."
    @State private var running = false
    var body: some Scene {
        WindowGroup {
            VStack(spacing: 16) {
                Text("MLXVLM SDK fixture consumer")
                Text(status)
                Button("Run fixed fixture suite") { run() }.disabled(running)
            }.padding().task {
                if ProcessInfo.processInfo.arguments.contains("--sdk-mlxvlm-benchmark") {
                    runBenchmark()
                } else if ProcessInfo.processInfo.arguments.contains("--sdk-mlxvlm-fixture-suite") { run() }
            }
        }
    }
    private func run() {
        guard !running else { return }
        running = true
        status = "Running fixed fixture suite…"
        Task {
            await FixtureSuite.run()
            status = "Finished. See sdk-mlxvlm-result.json in this app's Documents."
            running = false
        }
    }
    private func runBenchmark() {
        guard !running else { return }
        running = true
        status = "Running bounded fixed-image benchmark…"
        Task {
            await BenchmarkSuite.run()
            status = "Finished. See sdk-mlxvlm-benchmark.json in this app's Documents."
            running = false
        }
    }
}

private enum BenchmarkSuite {
    static let measuredIterations = 5
    static let prompt = "Describe this image in one brief sentence."
    static let fixtureSHA256 = "60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606"

    static func run() async {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let output = documents.appendingPathComponent("sdk-mlxvlm-benchmark.json")
        var report: [String: Any] = fixedMetadata()
        do {
            let fixture = documents.appendingPathComponent("fixture.png")
            guard try FixtureSuite.hash(fixture) == fixtureSHA256 else { throw BenchmarkError.fixture }
            let manifest = try SmolVLM2Model.manifest()
            let os = ProcessInfo.processInfo.operatingSystemVersion
            let store = try ModelStore(root: documents.appendingPathComponent("sdk-model-store"),
                compatibility: .init(backendId: SmolVLM2Model.backendId, backendVersion: "1.0.0",
                    osVersion: "\(os.majorVersion).\(os.minorVersion).\(os.patchVersion)", architecture: "arm64"))
            try await store.install(manifest: manifest, source: documents.appendingPathComponent("model"))
            let memory = ResidentMemorySampler()
            let memoryTask = Task.detached { await memory.run() }
            defer { memoryTask.cancel() }
            _ = try await iteration(store: store, manifest: manifest, fixture: fixture, index: 0)
            report["warmup"] = ["state": "success"]
            var iterations: [[String: Any]] = []
            for index in 1...measuredIterations {
                iterations.append(try await iteration(store: store, manifest: manifest, fixture: fixture, index: index))
            }
            memory.sample()
            memoryTask.cancel()
            await memoryTask.value
            report["iterations"] = iterations
            report["memory"] = memory.snapshot()
            try await store.remove(manifest.ref)
        } catch {
            report["errorType"] = String(describing: type(of: error))
        }
        if let data = try? JSONSerialization.data(withJSONObject: report, options: [.sortedKeys, .prettyPrinted]) {
            try? data.write(to: output, options: .atomic)
        }
    }

    private static func iteration(store: ModelStore, manifest: ModelManifest, fixture: URL,
                                  index: Int) async throws -> [String: Any] {
        let timing = BenchmarkTiming()
        let lease = try await store.acquire(manifest.ref)
        let backend: MLXVLMBackend
        do {
            backend = try MLXVLMBackend(lease: lease, observer: { event in
                if case .modelLoaded(let durationMs) = event { timing.recordLoad(durationMs) }
            })
        } catch { await lease.close(); throw error }
        let client: MultimodalGenerationClient
        do { client = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend]) }
        catch { await backend.close(); throw error }
        do {
            let request = try MultimodalRequest(imageFile: fixture, prompt: prompt, maxOutputTokens: 32)
            timing.start()
            let result = try await client.generate(request, update: { _ in timing.recordFirstUpdate() })
            timing.finish()
            await client.close()
            guard result.output.model == SmolVLM2Model.identity, let times = timing.snapshot() else {
                throw BenchmarkError.timing
            }
            let text = result.output.text
            let normalized = text.lowercased()
            return ["index": index, "loadMs": times.load, "firstUpdateMs": times.first, "totalMs": times.total,
                    "output": text, "quality": ["rubric": "synthetic-red-square-v1",
                                                   "passed": normalized.contains("red") && normalized.contains("square")]]
        } catch {
            await client.close()
            throw error
        }
    }

    private static func fixedMetadata() -> [String: Any] {
        let process = ProcessInfo.processInfo
        let sdkCommit = process.arguments.first { $0.hasPrefix("--sdk-commit=") }?.split(separator: "=", maxSplits: 1).last.map(String.init) ?? "unverified"
        return ["schemaVersion": 1, "runId": UUID().uuidString, "sdkCommit": sdkCommit,
                "device": machineIdentifier(), "OS": process.operatingSystemVersionString,
                "actualBackend": SmolVLM2Model.backendId, "modelId": SmolVLM2Model.identity.modelId,
                "modelRevision": SmolVLM2Model.identity.modelRevision,
                "runtimeRevision": SmolVLM2Model.identity.runtimeRevision,
                "fixtureHash": fixtureSHA256, "prompt": prompt, "maxOutputTokens": 32,
                "qualityScope": "synthetic-fixture-only",
                "unverified": ["metalGpuPeak", "energy", "naturalImageQuality"]]
    }

    private static func machineIdentifier() -> String {
        var size = 0
        sysctlbyname("hw.machine", nil, &size, nil, 0)
        var value = [CChar](repeating: 0, count: size)
        sysctlbyname("hw.machine", &value, &size, nil, 0)
        return String(cString: value)
    }

    private enum BenchmarkError: Error { case fixture, timing }
}

/// Explicit opt-in only. No file picker, network request, recording, or private media access.
private enum FixtureSuite {
    static func run() async {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        var report: [String: any Sendable] = ["state": "failed", "modelId": SmolVLM2Model.identity.modelId,
            "modelRevision": SmolVLM2Model.identity.modelRevision, "runtimeRevision": SmolVLM2Model.identity.runtimeRevision]
        var client: MultimodalGenerationClient?
        do {
            let fixture = documents.appendingPathComponent("fixture.png")
            let fixtureHash = try hash(fixture)
            guard fixtureHash == "60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606" else {
                throw SuiteError.fixture
            }
            report["fixtureHash"] = fixtureHash
            let manifest = try SmolVLM2Model.manifest()
            let os = ProcessInfo.processInfo.operatingSystemVersion
            let store = try ModelStore(root: documents.appendingPathComponent("sdk-model-store"),
                compatibility: .init(backendId: SmolVLM2Model.backendId, backendVersion: "1.0.0",
                    osVersion: "\(os.majorVersion).\(os.minorVersion).\(os.patchVersion)", architecture: "arm64"))
            try await store.install(manifest: manifest, source: documents.appendingPathComponent("model"))
            let lease = try await store.acquire(manifest.ref)
            let backend: MLXVLMBackend
            do { backend = try MLXVLMBackend(lease: lease) }
            catch { await lease.close(); throw error }
            let c: MultimodalGenerationClient
            do { c = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend]) }
            catch { await backend.close(); throw error }
            client = c
            let descriptors = try await c.listBackends()
            guard descriptors.count == 1, descriptors[0].availability.status == .available else { throw SuiteError.unavailable }
            do { try await store.remove(manifest.ref); throw SuiteError.lease }
            catch ModelStoreError.inUse { report["leaseProtected"] = true }
            let request = try MultimodalRequest(imageFile: fixture, prompt: "Describe this image in one brief sentence.", maxOutputTokens: 32)
            let started = ContinuousClock.now
            let first = try await c.generate(request)
            let elapsed = started.duration(to: .now)
            report["firstTotalMs"] = Double(elapsed.components.seconds) * 1000 + Double(elapsed.components.attoseconds) / 1e15
            guard first.output.model == SmolVLM2Model.identity else { throw SuiteError.identity }
            report["firstOutput"] = first.output.text
            let cancel = CancelOnUpdate()
            let cancelled = Task { try await c.generate(request, update: { _ in cancel.cancel() }) }
            cancel.arm(cancelled)
            do { _ = try await cancelled.value; throw SuiteError.cancel }
            catch is CancellationError { report["cancelledAfterUpdate"] = true }
            await c.drain()
            let reentry = try await c.generate(request)
            report["reentryOutput"] = reentry.output.text
            await c.close()
            client = nil
            try await store.remove(manifest.ref)
            report["leaseReleasedAfterClose"] = true
            do { _ = try await c.generate(request); throw SuiteError.closed }
            catch MultimodalClientError.closed { report["closedRejectsRequest"] = true }
            guard try hash(fixture) == fixtureHash else { throw SuiteError.fixture }
            report["state"] = "success"
        } catch {
            if let client { await client.close() }
            // Stable fixture-suite errors only; no media paths or prompts enter the report.
            report["errorType"] = String(describing: type(of: error))
        }
        if let data = try? JSONSerialization.data(withJSONObject: report, options: [.sortedKeys, .prettyPrinted]) {
            try? data.write(to: documents.appendingPathComponent("sdk-mlxvlm-result.json"), options: .atomic)
        }
    }
    fileprivate static func hash(_ url: URL) throws -> String {
        SHA256.hash(data: try Data(contentsOf: url)).map { String(format: "%02x", $0) }.joined()
    }
    private enum SuiteError: Error { case fixture, unavailable, lease, identity, cancel, closed }
}

private final class CancelOnUpdate: @unchecked Sendable {
    private let lock = NSLock()
    private var task: Task<MultimodalGenerationResult, Error>?
    private var requested = false
    func arm(_ task: Task<MultimodalGenerationResult, Error>) {
        lock.lock(); self.task = task; let shouldCancel = requested; lock.unlock()
        if shouldCancel { task.cancel() }
    }
    func cancel() {
        lock.lock(); requested = true; let task = task; lock.unlock()
        task?.cancel()
    }
}
