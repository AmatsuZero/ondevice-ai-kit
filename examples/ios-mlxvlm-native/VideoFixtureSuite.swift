import Foundation
import CryptoKit
import Darwin
import ODAIKitAppleMLXVLM
import ODAIKitModelStore
import ODAIKitMultimodal

/// Explicit synthetic opt-in; no picker, recording, network or natural media.
enum VideoFixtureSuite {
    static let fixtureHash = "9786524a69579ffd69d75c49048a566f31eaec090b1487b69aa4ce6d55e1ee51"
    static let prompt = "Describe what happens in this video in one brief sentence."
    static func run() async {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let process = ProcessInfo.processInfo
        let sourceCommit = process.arguments.first { $0.hasPrefix("--sdk-commit=") }?.dropFirst("--sdk-commit=".count).description ?? "unverified"
        var report: [String: Any] = ["schemaVersion": 1, "state": "failed", "runId": UUID().uuidString,
            "sourceCommit": sourceCommit, "device": machine(), "OS": process.operatingSystemVersionString,
            "pid": process.processIdentifier, "actualBackend": SmolVLM2Model.backendId,
            "modelId": SmolVLM2Model.identity.modelId, "modelRevision": SmolVLM2Model.identity.modelRevision,
            "runtimeRevision": SmolVLM2Model.identity.runtimeRevision, "fixtureHash": fixtureHash,
            "prompt": prompt, "maxOutputTokens": 32, "scope": "fixed-synthetic-video-lifecycle-only",
            "unverified": ["naturalVideoQuality", "metalGpuPeak", "energy", "minimumOS", "androidBackend"]]
        var client: MultimodalGenerationClient?
        var gates: [String: Bool] = [:], runs: [[String: Any]] = []
        do {
            let fixture = documents.appendingPathComponent("video-29fps.mp4")
            guard try hash(fixture).digest == fixtureHash else { throw VideoSuiteError.fixture }
            let before = try ownedSnapshots()
            let manifest = try SmolVLM2Model.manifest(), model = documents.appendingPathComponent("model")
            var files: [[String: Any]] = []
            for file in manifest.files {
                let actual = try hash(model.appendingPathComponent(file.path))
                guard actual.digest == file.sha256, actual.bytes == file.bytes else { throw VideoSuiteError.model }
                files.append(["path": file.path, "bytes": actual.bytes, "sha256": actual.digest])
            }
            report["modelFiles"] = files
            let os = process.operatingSystemVersion
            let store = try ModelStore(root: documents.appendingPathComponent("sdk-video-model-store"),
                compatibility: .init(backendId: SmolVLM2Model.backendId, backendVersion: "1.0.0",
                    osVersion: "\(os.majorVersion).\(os.minorVersion).\(os.patchVersion)", architecture: "arm64"))
            try await store.install(manifest: manifest, source: model)
            let lease = try await store.acquire(manifest.ref), router = VideoObservationRouter()
            let backend: MLXVLMBackend
            do { backend = try MLXVLMBackend(lease: lease, observer: { router.record($0) }) }
            catch { await lease.close(); throw error }
            let c: MultimodalGenerationClient
            do { c = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend]) }
            catch { await backend.close(); throw error }
            client = c
            do { try await store.remove(manifest.ref); throw VideoSuiteError.lease }
            catch ModelStoreError.inUse { gates["leaseProtectedBeforeGeneration"] = true }
            let request = try MultimodalVideoRequest(videoFile: fixture, prompt: prompt, maxOutputTokens: 32)
            for kind in ["success", "cancelled", "reentry"] {
                let run = await iteration(client: c, request: request, router: router, kind: kind)
                runs.append(run)
                guard run["state"] as? String == (kind == "cancelled" ? "cancelled" : "success") else { throw VideoSuiteError.generation }
                if kind == "cancelled" {
                    do { try await store.remove(manifest.ref); throw VideoSuiteError.lease }
                    catch ModelStoreError.inUse { gates["leaseProtectedAfterCancellation"] = true }
                }
            }
            await c.close(); client = nil
            try await store.remove(manifest.ref)
            gates["leaseReleasedAfterClose"] = true
            do { _ = try await c.generate(request); throw VideoSuiteError.closed }
            catch MultimodalClientError.closed { gates["closedRejectsVideo"] = true }
            let image = try MultimodalRequest(imageFile: fixture, prompt: "closed probe")
            do { _ = try await c.generate(image); throw VideoSuiteError.closed }
            catch MultimodalClientError.closed { gates["closedRejectsImage"] = true }
            gates["sourceUnchanged"] = try hash(fixture).digest == fixtureHash
            gates["noOwnedSnapshotsRemaining"] = try ownedSnapshots().subtracting(before).isEmpty
            guard gates.values.allSatisfy({ $0 }) else { throw VideoSuiteError.cleanup }
            report["state"] = "success"
        } catch {
            if let client { await client.close() }
            report["errorType"] = String(describing: type(of: error))
        }
        report["runs"] = runs; report["gates"] = gates
        if let data = try? JSONSerialization.data(withJSONObject: report, options: [.sortedKeys, .prettyPrinted]) {
            try? data.write(to: documents.appendingPathComponent("sdk-mlxvlm-video.json"), options: .atomic)
        }
    }

    private static func iteration(client: MultimodalGenerationClient, request: MultimodalVideoRequest,
                                  router: VideoObservationRouter, kind: String) async -> [String: Any] {
        let capture = VideoRunCapture(kind: kind), cancel = VideoCancelOnUpdate()
        router.select(capture)
        let task = Task {
            try await client.generate(request, update: { _ in
                capture.record("update")
                if kind == "cancelled" { cancel.cancel(capture: capture) }
            })
        }
        cancel.arm(task)
        var state = "failed", output = ""
        do {
            let result = try await task.value
            if kind != "cancelled", result.backendId == SmolVLM2Model.backendId,
               result.output.model == SmolVLM2Model.identity {
                state = "success"; output = result.output.text
            }
        } catch is CancellationError {
            if kind == "cancelled" { state = "cancelled" }
        } catch { capture.error(error) }
        capture.record("returned")
        await client.drain()
        capture.record("drained")
        var result = capture.snapshot()
        result["kind"] = kind; result["state"] = state; result["output"] = output
        result["pid"] = ProcessInfo.processInfo.processIdentifier
        return result
    }

    private static func hash(_ url: URL) throws -> (digest: String, bytes: Int64) {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var hash = SHA256(), bytes: Int64 = 0
        while let data = try handle.read(upToCount: 1024 * 1024), !data.isEmpty {
            bytes += Int64(data.count); hash.update(data: data)
        }
        return (hash.finalize().map { String(format: "%02x", $0) }.joined(), bytes)
    }
    private static func ownedSnapshots() throws -> Set<String> {
        Set(try FileManager.default.contentsOfDirectory(atPath: FileManager.default.temporaryDirectory.path).filter { $0.hasPrefix("odai-video-") })
    }
    private static func machine() -> String {
        var size = 0; sysctlbyname("hw.machine", nil, &size, nil, 0)
        var value = [CChar](repeating: 0, count: size)
        sysctlbyname("hw.machine", &value, &size, nil, 0)
        return String(cString: value)
    }
    private enum VideoSuiteError: Error { case fixture, model, lease, generation, closed, cleanup }
}

private final class VideoObservationRouter: @unchecked Sendable {
    private let lock = NSLock()
    private var capture: VideoRunCapture?
    func select(_ capture: VideoRunCapture) { lock.lock(); self.capture = capture; lock.unlock() }
    func record(_ event: MLXVLMGenerationEvent) {
        lock.lock(); let current = capture; lock.unlock(); current?.record(event)
    }
}

private final class VideoRunCapture: @unchecked Sendable {
    private let lock = NSLock(), started = ContinuousClock.now
    private var events: [[String: Any]] = [["name": "begin", "ms": 0.0]]
    private var data: [String: Any] = [:]
    private let progress: URL
    init(kind: String) {
        progress = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("sdk-mlxvlm-video-progress.json")
        data["kind"] = kind
        persist()
    }
    // Separate incomplete diagnostic file: never accepted by the success validator.
    private func persist() {
        var value = data
        value["events"] = events
        value["pid"] = ProcessInfo.processInfo.processIdentifier
        value["sourceCommit"] = ProcessInfo.processInfo.arguments.first { $0.hasPrefix("--sdk-commit=") }?
            .dropFirst("--sdk-commit=".count).description ?? "unverified"
        if let bytes = try? JSONSerialization.data(withJSONObject: value, options: [.sortedKeys, .prettyPrinted]) {
            try? bytes.write(to: progress, options: .atomic)
        }
    }
    func record(_ name: String) { lock.lock(); defer { lock.unlock() }; append(name) }
    private func append(_ name: String) {
        let elapsed = started.duration(to: .now)
        let ms = Double(elapsed.components.seconds) * 1000 + Double(elapsed.components.attoseconds) / 1e15
        events.append(["name": name, "ms": ms])
        persist()
    }
    func record(_ event: MLXVLMGenerationEvent) {
        lock.lock(); defer { lock.unlock() }
        switch event {
        case .modelLoaded(let durationMs): data["loadMs"] = durationMs; append("modelLoaded")
        case .videoDecoded(let m):
            func time(_ t: MLXVLMVideoMetadata.Time) -> [String: Any] { ["value": t.value, "timescale": t.timescale] }
            data["metadata"] = ["snapshotSHA256": m.snapshotSHA256,
                "requestedTimes": m.requestedTimes.map(time), "actualTimes": m.actualTimes.map(time),
                "clipDuration": time(m.clipDuration), "sampledSpan": time(m.sampledSpan),
                "widths": m.widths, "heights": m.heights]
            append("decoded")
        case .videoPreparationStarted: append("prepareStarted")
        case .videoPrepared(let durationMs, let frameCount):
            data["prepareMs"] = durationMs; data["preparedFrameCount"] = frameCount; append("prepared")
        case .videoSnapshotClosed(let removed): data["snapshotRemoved"] = removed; append("snapshotClosed")
        }
    }
    func error(_ error: Error) { lock.lock(); data["errorType"] = String(describing: type(of: error)); lock.unlock() }
    func snapshot() -> [String: Any] { lock.lock(); defer { lock.unlock() }; var result = data; result.removeValue(forKey: "kind"); result["events"] = events; return result }
}

private final class VideoCancelOnUpdate: @unchecked Sendable {
    private let lock = NSLock()
    private var task: Task<MultimodalGenerationResult, Error>?
    private var requested = false
    func arm(_ task: Task<MultimodalGenerationResult, Error>) {
        lock.lock(); self.task = task; let cancel = requested; lock.unlock()
        if cancel { task.cancel() }
    }
    func cancel(capture: VideoRunCapture) {
        lock.lock()
        guard !requested else { lock.unlock(); return }
        requested = true; let task = task
        // Record request before forwarding cancellation; cleanup must occur after it.
        capture.record("cancelRequested")
        lock.unlock(); task?.cancel()
    }
}
