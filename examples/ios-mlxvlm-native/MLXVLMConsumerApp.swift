import SwiftUI
import CryptoKit
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
                if ProcessInfo.processInfo.arguments.contains("--sdk-mlxvlm-fixture-suite") { run() }
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
    private static func hash(_ url: URL) throws -> String {
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
