import Foundation
import XCTest
import ODAIKitCore
@testable import ODAIKitVideo

final class VideoProcessingClientTests: XCTestCase {
    private var request: VideoProcessingRequest {
        try! .init(inputFile: URL(fileURLWithPath: "/tmp/video-fixture.mp4"), scaleFactor: 4)
    }

    func testRegistrationRejectsDuplicateEmptyAndMissingDefault() {
        let backend = VideoFixtureBackend()
        XCTAssertThrowsError(try VideoProcessingClient(defaultBackendId: "missing", backends: [backend]))
        XCTAssertThrowsError(try VideoProcessingClient(defaultBackendId: backend.backendId, backends: [backend, backend]))
        XCTAssertThrowsError(try VideoProcessingClient(defaultBackendId: "", backends: [VideoFixtureBackend(id: "")]))
    }

    func testListIsSortedAndUnavailableRetainsSupportWithoutStarting() async throws {
        let z = VideoFixtureBackend(id: "z", status: .requiresAssets)
        let a = VideoFixtureBackend(id: "a")
        let client = try VideoProcessingClient(defaultBackendId: "a", backends: [z, a])
        let list = try await client.listBackends()
        XCTAssertEqual(list.map(\.backendId), ["a", "z"])
        XCTAssertEqual(list[1].availability.reason, .missingAssets)
        XCTAssertEqual(list[1].supportedScaleFactors, [4])
        XCTAssertEqual(list[1].supportedAudioPolicies, [.preserve])
        let starts = await z.starts
        XCTAssertEqual(starts, 0)
        await client.close()
    }

    func testExplicitSelectionAndUnsupportedOptionsNeverFallback() async throws {
        let ready = VideoFixtureBackend(id: "ready")
        let absent = VideoFixtureBackend(id: "absent", status: .requiresAssets)
        let client = try VideoProcessingClient(defaultBackendId: "ready", backends: [ready, absent])
        for (selection, expected) in [
            (VideoBackendSelection.explicit("missing"), VideoClientError.backendNotRegistered("missing")),
            (.explicit("absent"), .backendUnavailable("absent", .missingAssets))
        ] {
            do { _ = try await client.process(request, backend: selection); XCTFail("must reject") }
            catch { XCTAssertEqual(error as? VideoClientError, expected) }
        }
        do {
            _ = try await client.process(.init(inputFile: request.inputFile, scaleFactor: 2))
            XCTFail("unsupported scale must reject")
        } catch { XCTAssertEqual(error as? VideoClientError, .unsupportedScaleFactor(2)) }
        do {
            _ = try await client.process(.init(inputFile: request.inputFile, scaleFactor: 4, audioPolicy: .discard))
            XCTFail("audio policy must not change")
        } catch { XCTAssertEqual(error as? VideoClientError, .unsupportedAudioPolicy(.discard)) }
        let starts = await ready.starts
        XCTAssertEqual(starts, 0)
        await client.close()
    }

    func testCancellationDrainsAndReleasesLateOutputBeforeReentry() async throws {
        let backend = VideoFixtureBackend(blockProcess: true, blockDrain: true)
        let client = try VideoProcessingClient(defaultBackendId: backend.backendId, backends: [backend])
        let request = request
        let operation = Task { try await client.process(request) }
        await backend.processGate.waitForEntry()
        operation.cancel()
        await backend.processGate.open()
        await backend.drainGate.waitForEntry()
        do { _ = try await client.process(request); XCTFail("drain must keep the slot") }
        catch { XCTAssertEqual(error as? VideoClientError, .busy) }
        await backend.drainGate.open()
        do { _ = try await operation.value; XCTFail("late success must be discarded") }
        catch { XCTAssertTrue(error is CancellationError) }
        let releases = await backend.outputs[0].releases
        XCTAssertEqual(releases, 1)
        let second = try await client.process(request)
        await client.close()
        let retained = await backend.outputs[1].releases
        XCTAssertEqual(retained, 0, "close cannot delete caller-owned output")
        try await second.output.release()
    }

    func testLateOutputReleaseFailureIsNotSwallowed() async throws {
        let backend = VideoFixtureBackend(blockProcess: true, cleanupFails: true)
        let client = try VideoProcessingClient(defaultBackendId: backend.backendId, backends: [backend])
        let request = request
        let operation = Task { try await client.process(request) }
        await backend.processGate.waitForEntry()
        operation.cancel()
        await backend.processGate.open()
        do { _ = try await operation.value; XCTFail("cleanup must fail") }
        catch { XCTAssertEqual(error as? VideoFixtureFailure, .cleanup) }
        await client.close()
    }

    func testCloseDuringAvailabilityRejectsLateStartAndJoinsConcurrentClose() async throws {
        let backend = VideoFixtureBackend(blockAvailability: true)
        let client = try VideoProcessingClient(defaultBackendId: backend.backendId, backends: [backend])
        let request = request
        let operation = Task { try await client.process(request) }
        await backend.availabilityGate.waitForEntry()
        let closing = Task { await client.close() }
        try await waitUntilClosed(client)
        let another = Task { await client.close() }
        await backend.availabilityGate.open()
        do { _ = try await operation.value; XCTFail("closed admission") }
        catch { XCTAssertTrue(error is CancellationError || error as? VideoClientError == .closed) }
        await closing.value; await another.value
        let starts = await backend.starts, closes = await backend.closes
        XCTAssertEqual(starts, 0); XCTAssertEqual(closes, 1)
    }

    func testCloseJoinsOutstandingMetadataQueryBeforeBackendClose() async throws {
        let backend = VideoFixtureBackend(blockAvailability: true)
        let client = try VideoProcessingClient(defaultBackendId: backend.backendId, backends: [backend])
        let query = Task { try await client.listBackends() }
        await backend.availabilityGate.waitForEntry()
        let closing = Task { await client.close() }
        try await waitUntilClosed(client)
        let before = await backend.closes
        XCTAssertEqual(before, 0)
        await backend.availabilityGate.open()
        do { _ = try await query.value; XCTFail("late query must not revive closed client") }
        catch { XCTAssertEqual(error as? VideoClientError, .closed) }
        await closing.value
        let after = await backend.closes
        XCTAssertEqual(after, 1)
    }

    func testOldProgressCannotReachNextOperation() async throws {
        let backend = VideoFixtureBackend(blockProcess: true)
        let client = try VideoProcessingClient(defaultBackendId: backend.backendId, backends: [backend])
        let received = VideoProgressRecorder()
        let request = request
        let first = Task { try await client.process(request, progress: { received.append($0.completedFrames) }) }
        await backend.processGate.waitForEntry()
        await backend.emit(index: 0, frames: 1)
        for _ in 0..<1000 { if received.values == [1] { break }; await Task.yield() }
        XCTAssertEqual(received.values, [1])
        first.cancel(); await backend.processGate.open()
        _ = await first.result
        let second = try await client.process(request, progress: { received.append($0.completedFrames) })
        await backend.emit(index: 0, frames: 99)
        await client.close()
        XCTAssertEqual(received.values, [1])
        try await second.output.release()
    }

    private func waitUntilClosed(_ client: VideoProcessingClient) async throws {
        for _ in 0..<1000 {
            do { _ = try await client.listBackends() }
            catch VideoClientError.closed { return }
            await Task.yield()
        }
        XCTFail("close must reject new metadata admission")
    }
}

private actor VideoBarrier {
    private var opened: Bool
    private var entered = false
    private var waiters: [CheckedContinuation<Void, Never>] = []
    private var observers: [CheckedContinuation<Void, Never>] = []
    init(open: Bool) { opened = open }
    func wait() async {
        entered = true
        let observers = self.observers; self.observers.removeAll()
        observers.forEach { $0.resume() }
        if !opened { await withCheckedContinuation { waiters.append($0) } }
    }
    func waitForEntry() async {
        if !entered { await withCheckedContinuation { observers.append($0) } }
    }
    func open() {
        opened = true
        let waiters = self.waiters; self.waiters.removeAll()
        waiters.forEach { $0.resume() }
    }
}

private enum VideoFixtureFailure: Error, Equatable { case cleanup }

private actor VideoFixtureOutput: VideoOutputLease {
    nonisolated let file = URL(fileURLWithPath: "/tmp/video-output-fixture.mov")
    nonisolated let info = try! VideoOutputInfo(width: 1280, height: 720, frameCount: 48, durationSeconds: 2, audioTrackCount: 0)
    var releases = 0
    let cleanupFails: Bool
    init(cleanupFails: Bool = false) { self.cleanupFails = cleanupFails }
    func release() throws {
        releases += 1
        if cleanupFails { throw VideoFixtureFailure.cleanup }
    }
}

private actor VideoFixtureBackend: VideoSuperResolutionBackend {
    nonisolated let backendId: String
    nonisolated let supportedScaleFactors = [4]
    nonisolated let supportedAudioPolicies: [VideoAudioPolicy] = [.preserve]
    let status: CapabilityStatus
    let availabilityGate: VideoBarrier, processGate: VideoBarrier, drainGate: VideoBarrier
    let outputs: [VideoFixtureOutput]
    var starts = 0, closes = 0, queries = 0
    var callbacks: [(@Sendable (VideoProcessingProgress) -> Void)?] = []
    init(id: String = "fixture.video", status: CapabilityStatus = .available, blockAvailability: Bool = false,
         blockProcess: Bool = false, blockDrain: Bool = false, cleanupFails: Bool = false) {
        backendId = id; self.status = status
        availabilityGate = VideoBarrier(open: !blockAvailability)
        processGate = VideoBarrier(open: !blockProcess); drainGate = VideoBarrier(open: !blockDrain)
        outputs = [VideoFixtureOutput(cleanupFails: cleanupFails), VideoFixtureOutput()]
    }
    func availability() async -> CapabilityAvailability {
        queries += 1
        if queries == 1 { await availabilityGate.wait() }
        return .init(status: status, reason: status == .available ? nil : .missingAssets, backendId: backendId, offline: true)
    }
    func process(_ request: VideoProcessingRequest,
                 progress: (@Sendable (VideoProcessingProgress) -> Void)?) async throws -> any VideoOutputLease {
        let index = starts
        starts += 1; callbacks.append(progress)
        await processGate.wait()
        return outputs[index]
    }
    func emit(index: Int, frames: Int) { callbacks[index]?(try! .init(completedFrames: frames)) }
    func drain() async { await drainGate.wait() }
    func close() { closes += 1 }
}

private final class VideoProgressRecorder: @unchecked Sendable {
    private let lock = NSLock()
    private var stored: [Int] = []
    var values: [Int] { lock.lock(); defer { lock.unlock() }; return stored }
    func append(_ value: Int) { lock.lock(); stored.append(value); lock.unlock() }
}
