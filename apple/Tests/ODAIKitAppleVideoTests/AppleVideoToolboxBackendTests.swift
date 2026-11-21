import Foundation
import XCTest
import ODAIKitCore
import ODAIKitVideo
@testable import ODAIKitAppleVideo

final class AppleVideoToolboxBackendTests: XCTestCase {
    private var request: VideoProcessingRequest {
        try! .init(inputFile: URL(fileURLWithPath: "/tmp/video-fixture.mp4"), scaleFactor: 4, audioPolicy: .discard)
    }

    func testUnavailableMetadataAndUnsupportedOptionsStartNothing() async throws {
        let root = try makeRoot(); defer { try? FileManager.default.removeItem(at: root) }
        let engine = ControlledVideoEngine(status: .requiresAssets)
        let backend = AppleVideoToolboxBackend(root: root, engine: engine)
        let client = try VideoProcessingClient(defaultBackendId: backend.backendId, backends: [backend])
        let metadata = try await client.listBackends()
        XCTAssertEqual(metadata[0].supportedScaleFactors, [4])
        XCTAssertEqual(metadata[0].supportedAudioPolicies, [.preserve, .discard])
        XCTAssertEqual(metadata[0].availability.reason, .missingAssets)
        do { _ = try await client.process(request); XCTFail("no model") }
        catch { XCTAssertEqual(error as? VideoClientError, .backendUnavailable(backend.backendId, .missingAssets)) }
        await client.close()
        let ready = AppleVideoToolboxBackend(root: root, engine: ControlledVideoEngine())
        do { _ = try await ready.process(.init(inputFile: request.inputFile, scaleFactor: 2, audioPolicy: .discard), progress: nil); XCTFail("no scale fallback") }
        catch { XCTAssertEqual(error as? VideoClientError, .unsupportedScaleFactor(2)) }
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: root.path), [])
        await ready.close()
    }

    func testDefaultPreserveIsAdvertisedAndDeliveredByClient() async throws {
        let root = try makeRoot(); defer { try? FileManager.default.removeItem(at: root) }
        let engine = ControlledVideoEngine()
        let backend = AppleVideoToolboxBackend(root: root, engine: engine)
        let client = try VideoProcessingClient(defaultBackendId: backend.backendId, backends: [backend])
        let descriptors = try await client.listBackends()
        XCTAssertEqual(descriptors[0].supportedAudioPolicies, [.preserve, .discard])
        let result = try await client.process(.init(inputFile: request.inputFile, scaleFactor: 4))
        let policy = await engine.lastAudioPolicy
        XCTAssertEqual(policy, .preserve)
        XCTAssertEqual(result.output.info.audioTrackCount, 1)
        await client.close()
        XCTAssertTrue(FileManager.default.fileExists(atPath: result.output.file.path))
        try await result.output.release()
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: root.path), [])
    }

    func testCancelledLateOutputIsRemovedBeforeDrainAndReentry() async throws {
        let root = try makeRoot(); defer { try? FileManager.default.removeItem(at: root) }
        let engine = ControlledVideoEngine(block: true)
        let backend = AppleVideoToolboxBackend(root: root, engine: engine)
        let client = try VideoProcessingClient(defaultBackendId: backend.backendId, backends: [backend])
        let request = request
        let operation = Task { try await client.process(request) }
        await engine.gate.waitForEntry()
        operation.cancel()
        do { _ = try await client.process(request); XCTFail("slot must remain busy") }
        catch { XCTAssertEqual(error as? VideoClientError, .busy) }
        await engine.gate.open()
        do { _ = try await operation.value; XCTFail("cancelled output must not escape") }
        catch { XCTAssertTrue(error is CancellationError) }
        await backend.drain()
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: root.path), [])
        let second = try await client.process(request)
        await client.close()
        XCTAssertTrue(FileManager.default.fileExists(atPath: second.output.file.path))
        try await second.output.release()
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: root.path), [])
    }

    func testConcurrentCloseWaitsLateWorkerAndRejectsAdmission() async throws {
        let root = try makeRoot(); defer { try? FileManager.default.removeItem(at: root) }
        let engine = ControlledVideoEngine(block: true)
        let backend = AppleVideoToolboxBackend(root: root, engine: engine)
        let request = request
        let operation = Task { try await backend.process(request, progress: nil) }
        await engine.gate.waitForEntry()
        let closing = Task { await backend.close() }
        for _ in 0..<1000 {
            if await backend.availability().reason == .closed { break }
            await Task.yield()
        }
        let state = await backend.availability()
        XCTAssertEqual(state.reason, .closed)
        let other = Task { await backend.close() }
        do { _ = try await backend.process(request, progress: nil); XCTFail("closed") }
        catch { XCTAssertEqual(error as? VideoClientError, .closed) }
        await engine.gate.open()
        _ = await operation.result
        await closing.value; await other.value
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: root.path), [])
        let starts = await engine.starts
        XCTAssertEqual(starts, 1)
    }

    func testProcessingFailureCleansWorkspaceAndPreservesInput() async throws {
        let root = try makeRoot(); defer { try? FileManager.default.removeItem(at: root) }
        let input = root.appendingPathComponent("keep.mp4")
        try Data([1, 3, 5]).write(to: input)
        let backend = AppleVideoToolboxBackend(root: root, engine: ControlledVideoEngine(fail: true))
        do { _ = try await backend.process(request, progress: nil); XCTFail("engine failure") }
        catch { XCTAssertEqual(error as? EngineFailure, .processing) }
        await backend.close()
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: root.path), ["keep.mp4"])
        XCTAssertEqual(try Data(contentsOf: input), Data([1, 3, 5]))
    }

    func testCancellationDominatesLateProcessingFailureAfterCleanup() async throws {
        let root = try makeRoot(); defer { try? FileManager.default.removeItem(at: root) }
        let engine = ControlledVideoEngine(block: true, fail: true)
        let backend = AppleVideoToolboxBackend(root: root, engine: engine)
        let request = request
        let operation = Task { try await backend.process(request, progress: nil) }
        await engine.gate.waitForEntry()
        operation.cancel()
        await engine.gate.open()
        do { _ = try await operation.value; XCTFail("cancelled processing") }
        catch { XCTAssertTrue(error is CancellationError, "late native errors must not turn cancellation into failure") }
        await backend.close()
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: root.path), [])
    }

    func testPublicFactoryIsUnavailableOnHostWithoutStartingProcessing() async throws {
        #if !os(iOS) || targetEnvironment(simulator)
        let backend = AppleVideoToolboxBackend()
        let availability = await backend.availability()
        XCTAssertEqual(availability.status, .unavailable)
        XCTAssertEqual(availability.reason, .unsupportedDevice)
        await backend.close()
        #endif
    }

    private func makeRoot() throws -> URL {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("odai-backend-test-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
        return root
    }
}

private enum EngineFailure: Error, Equatable { case processing }
private actor ControlledVideoEngine: AppleVideoProcessingEngine {
    let gate: BackendVideoBarrier
    let status: CapabilityStatus
    let fail: Bool
    var starts = 0
    var lastAudioPolicy: VideoAudioPolicy?
    init(block: Bool = false, status: CapabilityStatus = .available, fail: Bool = false) {
        gate = BackendVideoBarrier(open: !block); self.status = status; self.fail = fail
    }
    func availability() -> CapabilityAvailability {
        .init(status: status, reason: status == .available ? nil : .missingAssets, backendId: "apple.videotoolbox.sr", minOS: "iOS26", offline: true)
    }
    func process(request: VideoProcessingRequest, output: URL,
                 progress: (@Sendable (VideoProcessingProgress) -> Void)?) async throws -> VideoOutputInfo {
        starts += 1
        lastAudioPolicy = request.audioPolicy
        await gate.wait()
        try Data([1, 2, 3]).write(to: output)
        if fail { throw EngineFailure.processing }
        return try .init(width: 1280, height: 720, frameCount: 48, durationSeconds: 2, audioTrackCount: request.audioPolicy == .preserve ? 1 : 0)
    }
}

private actor BackendVideoBarrier {
    private var opened: Bool
    private var entered = false
    private var waiters: [CheckedContinuation<Void, Never>] = []
    private var observers: [CheckedContinuation<Void, Never>] = []
    init(open: Bool) { opened = open }
    func wait() async {
        entered = true
        let observers = self.observers; self.observers.removeAll(); observers.forEach { $0.resume() }
        if !opened { await withCheckedContinuation { waiters.append($0) } }
    }
    func waitForEntry() async { if !entered { await withCheckedContinuation { observers.append($0) } } }
    func open() { opened = true; let waiters = self.waiters; self.waiters.removeAll(); waiters.forEach { $0.resume() } }
}
