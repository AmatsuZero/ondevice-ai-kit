import Foundation
import XCTest
import ODAIKitCore
import ODAIKitMultimodal

final class MultimodalVideoClientTests: XCTestCase {
    private var video: MultimodalVideoRequest {
        try! .init(videoFile: URL(fileURLWithPath: "/nonexistent/video.mp4"), prompt: "What changes?", maxOutputTokens: 37)
    }
    private var image: MultimodalRequest {
        try! .init(imageFile: URL(fileURLWithPath: "/nonexistent/image.png"), prompt: "Describe.")
    }

    func testVideoRequestValidatesValuesWithoutReadingFileOrChangingImageSemantics() throws {
        let url = URL(fileURLWithPath: "/nonexistent/video.mp4")
        let request = try MultimodalVideoRequest(videoFile: url, prompt: " Video ")
        XCTAssertEqual(request.videoFile, url)
        XCTAssertEqual(request.prompt, " Video ")
        XCTAssertEqual(request.maxOutputTokens, 128)
        XCTAssertThrowsError(try MultimodalVideoRequest(videoFile: URL(string: "https://example.invalid/video.mp4")!, prompt: "x"))
        for prompt in ["", " \n"] { XCTAssertThrowsError(try MultimodalVideoRequest(videoFile: url, prompt: prompt)) }
        for budget in [0, -1] { XCTAssertThrowsError(try MultimodalVideoRequest(videoFile: url, prompt: "x", maxOutputTokens: budget)) }
        XCTAssertEqual(image.maxOutputTokens, 128)
    }

    func testVideoExplicitSelectionUnknownUnavailableAndLegacyNeverFallback() async throws {
        let a = VideoFixtureBackend(id: "a"), b = VideoFixtureBackend(id: "b")
        let unavailable = VideoFixtureBackend(id: "unavailable", available: false)
        let legacy = LegacyImageFixtureBackend()
        let client = try MultimodalGenerationClient(defaultBackendId: "a", backends: [a, b, unavailable, legacy])
        let selected = try await client.generate(video, backend: .explicit("b"))
        XCTAssertEqual(selected.backendId, "b")
        XCTAssertEqual(selected.output, VideoFixtureBackend.output)
        let submitted = await b.videos, drains = await b.drains
        XCTAssertEqual(submitted, [video]); XCTAssertEqual(drains, 1)
        for (selection, expected) in [
            (MultimodalBackendSelection.explicit("missing"), MultimodalClientError.backendNotRegistered("missing")),
            (.explicit("unavailable"), .backendUnavailable("unavailable", .missingAssets)),
            (.explicit("legacy"), .invalidInput("selected backend does not support video"))
        ] {
            do { _ = try await client.generate(video, backend: selection); XCTFail("must reject selected backend") }
            catch { XCTAssertEqual(error as? MultimodalClientError, expected) }
        }
        let aStarts = await a.starts, unavailableStarts = await unavailable.starts
        let legacyStarts = await legacy.starts, legacyQueries = await legacy.queries
        XCTAssertEqual(aStarts, 0); XCTAssertEqual(unavailableStarts, 0)
        XCTAssertEqual(legacyStarts, 0); XCTAssertEqual(legacyQueries, 0)
        // Availability of an image-only backend must not promise video support.
        let list = try await client.listBackends()
        XCTAssertEqual(list.first { $0.backendId == "legacy" }?.availability.status, .available)
        await client.close()
    }

    func testImageAndVideoOccupyOneSlotInEitherOrder() async throws {
        for videoFirst in [true, false] {
            let backend = VideoFixtureBackend(blockGeneration: true)
            let client = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend])
            let video = video, image = image
            let first = Task {
                if videoFirst { return try await client.generate(video) }
                return try await client.generate(image)
            }
            await backend.generationGates[0].waitForEntry()
            do { _ = try await client.generate(video); XCTFail("video must share active slot") }
            catch { XCTAssertEqual(error as? MultimodalClientError, .busy) }
            do { _ = try await client.generate(image); XCTFail("image must share active slot") }
            catch { XCTAssertEqual(error as? MultimodalClientError, .busy) }
            await backend.generationGates[0].open()
            _ = try await first.value
            await client.close()
        }
    }

    func testCancelledVideoAdmissionHoldsSlotAndNeverStartsBackend() async throws {
        let backend = VideoFixtureBackend(blockAvailability: true)
        let client = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend])
        let video = video
        let operation = Task { try await client.generate(video) }
        await backend.availabilityGate.waitForEntry()
        do { _ = try await client.generate(image); XCTFail("admission must occupy shared slot") }
        catch { XCTAssertEqual(error as? MultimodalClientError, .busy) }
        operation.cancel()
        await backend.availabilityGate.open()
        do { _ = try await operation.value; XCTFail("cancelled admission") }
        catch { XCTAssertTrue(error is CancellationError) }
        let starts = await backend.starts
        XCTAssertEqual(starts, 0)
        await client.close()
    }

    func testVideoCancellationWinsLateSuccessOrErrorAndJoinsDrainBeforeImageReentry() async throws {
        for fail in [true, false] {
            let backend = VideoFixtureBackend(blockGeneration: true, blockDrain: true, fail: fail)
            let client = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend])
            let video = video
            let operation = Task { try await client.generate(video) }
            await backend.generationGates[0].waitForEntry()
            operation.cancel()
            await backend.generationGates[0].open()
            await backend.drainGate.waitForEntry()
            do { _ = try await client.generate(image); XCTFail("drain must hold shared slot") }
            catch { XCTAssertEqual(error as? MultimodalClientError, .busy) }
            await backend.drainGate.open()
            do { _ = try await operation.value; XCTFail("cancellation wins late outcome") }
            catch { XCTAssertTrue(error is CancellationError) }
            await backend.setFailure(false)
            let result = try await client.generate(image)
            XCTAssertEqual(result.output, VideoFixtureBackend.output)
            let drains = await backend.drains
            XCTAssertEqual(drains, 2)
            await client.close()
        }
    }

    func testVideoErrorsDrainAndAllowAnotherVideo() async throws {
        let backend = VideoFixtureBackend(fail: true)
        let client = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend])
        for _ in 0..<2 {
            do { _ = try await client.generate(video); XCTFail("expected generation error") }
            catch { XCTAssertTrue(error is VideoFixtureFailure) }
        }
        let drains = await backend.drains, submitted = await backend.videos
        XCTAssertEqual(drains, 2); XCTAssertEqual(submitted, [video, video])
        await client.close()
    }

    func testVideoAndImageUpdatesShareOperationIdentityAndSequenceFiltering() async throws {
        let backend = VideoFixtureBackend(blockGeneration: true, blockSecond: true)
        let client = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend])
        let received = VideoUpdateRecorder()
        let firstUpdate = expectation(description: "video sequence2"), secondUpdate = expectation(description: "image sequence3")
        let video = video, image = image
        let first = Task { try await client.generate(video, update: {
            received.append($0.sequence)
            if $0.sequence == 2 { firstUpdate.fulfill() }
        }) }
        await backend.generationGates[0].waitForEntry()
        await backend.emit(index: 0, sequence: 2)
        await fulfillment(of: [firstUpdate], timeout: 2)
        await backend.emit(index: 0, sequence: 2)
        await backend.emit(index: 0, sequence: 1)
        first.cancel()
        await backend.emit(index: 0, sequence: 50)
        await backend.generationGates[0].open()
        _ = await first.result
        let second = Task { try await client.generate(image, update: {
            received.append($0.sequence)
            if $0.sequence == 3 { secondUpdate.fulfill() }
        }) }
        await backend.generationGates[1].waitForEntry()
        await backend.emit(index: 0, sequence: 99)
        await backend.emit(index: 1, sequence: 3)
        await fulfillment(of: [secondUpdate], timeout: 2)
        await backend.generationGates[1].open()
        _ = try await second.value
        await client.close()
        XCTAssertEqual(received.values, [2, 3])
    }

    func testCloseJoinsVideoDrainAndMetadataQueryThenClosesExactlyOnce() async throws {
        let backend = VideoFixtureBackend(blockGeneration: true, blockDrain: true)
        let client = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend])
        let video = video
        let operation = Task { try await client.generate(video) }
        await backend.generationGates[0].waitForEntry()
        await backend.availabilityGate.shut()
        let query = Task { try await client.listBackends() }
        await backend.availabilityGate.waitForEntry()
        let closing = Task { await client.close() }
        for _ in 0..<1000 {
            do { _ = try await client.generate(video) }
            catch MultimodalClientError.closed { break }
            catch { }
            await Task.yield()
        }
        do { _ = try await client.generate(video); XCTFail("close must seal admission") }
        catch { XCTAssertEqual(error as? MultimodalClientError, .closed) }
        let another = Task { await client.close() }
        await backend.generationGates[0].open()
        await backend.drainGate.waitForEntry()
        let before = await backend.closes
        XCTAssertEqual(before, 0)
        await backend.drainGate.open()
        do { _ = try await operation.value; XCTFail("close cancels video") }
        catch { XCTAssertTrue(error is CancellationError) }
        let afterDrain = await backend.closes
        XCTAssertEqual(afterDrain, 0, "metadata query remains admitted")
        await backend.availabilityGate.open()
        _ = await query.result
        await closing.value; await another.value
        let closes = await backend.closes
        XCTAssertEqual(closes, 1)
    }
}

private actor VideoGate {
    private var opened: Bool
    private var entered = false
    private var waiters: [CheckedContinuation<Void, Never>] = []
    private var observers: [CheckedContinuation<Void, Never>] = []
    init(open: Bool = true) { opened = open }
    func wait() async {
        entered = true
        let observers = observers; self.observers.removeAll(); observers.forEach { $0.resume() }
        if !opened { await withCheckedContinuation { waiters.append($0) } }
    }
    func waitForEntry() async {
        if !entered { await withCheckedContinuation { observers.append($0) } }
    }
    func shut() { opened = false; entered = false }
    func open() {
        opened = true
        let waiters = waiters; self.waiters.removeAll(); waiters.forEach { $0.resume() }
    }
}

private enum VideoFixtureFailure: Error { case generation }

private actor VideoFixtureBackend: MultimodalVideoGenerationBackend {
    nonisolated let backendId: String
    nonisolated static let output = try! MultimodalTextOutput(text: "A moving square.",
        model: .init(modelId: "fixture", modelRevision: "r1", runtimeRevision: "runtime1"))
    let available: Bool
    let availabilityGate: VideoGate, drainGate: VideoGate
    let generationGates: [VideoGate]
    private var fail: Bool
    var starts = 0, drains = 0, closes = 0
    var videos: [MultimodalVideoRequest] = []
    var callbacks: [(@Sendable (MultimodalTextUpdate) -> Void)?] = []
    init(id: String = "video.fixture", available: Bool = true, blockAvailability: Bool = false,
         blockGeneration: Bool = false, blockSecond: Bool = false, blockDrain: Bool = false, fail: Bool = false) {
        backendId = id; self.available = available; self.fail = fail
        availabilityGate = VideoGate(open: !blockAvailability); drainGate = VideoGate(open: !blockDrain)
        generationGates = [VideoGate(open: !blockGeneration), VideoGate(open: !blockSecond)]
    }
    func availability() async -> CapabilityAvailability {
        await availabilityGate.wait()
        return .init(status: available ? .available : .requiresAssets,
                     reason: available ? nil : .missingAssets, backendId: backendId)
    }
    func generate(_ request: MultimodalRequest, update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> MultimodalTextOutput {
        try await run(update)
    }
    func generateVideo(_ request: MultimodalVideoRequest, update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> MultimodalTextOutput {
        videos.append(request)
        return try await run(update)
    }
    private func run(_ update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> MultimodalTextOutput {
        let index = starts; starts += 1; callbacks.append(update)
        await generationGates[min(index, 1)].wait()
        if fail { throw VideoFixtureFailure.generation }
        return Self.output
    }
    func setFailure(_ fail: Bool) { self.fail = fail }
    func emit(index: Int, sequence: Int) { callbacks[index]?(try! .init(sequence: sequence, text: "update")) }
    func drain() async { drains += 1; await drainGate.wait() }
    func close() async { closes += 1 }
}

private actor LegacyImageFixtureBackend: MultimodalGenerationBackend {
    nonisolated let backendId = "legacy"
    var starts = 0, queries = 0
    func availability() async -> CapabilityAvailability {
        queries += 1; return .init(status: .available, backendId: backendId)
    }
    func generate(_ request: MultimodalRequest, update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> MultimodalTextOutput {
        starts += 1; return VideoFixtureBackend.output
    }
    func drain() async {}
    func close() async {}
}

private final class VideoUpdateRecorder: @unchecked Sendable {
    private let lock = NSLock()
    private var snapshots: [Int] = []
    var values: [Int] { lock.lock(); defer { lock.unlock() }; return snapshots }
    func append(_ value: Int) { lock.lock(); defer { lock.unlock() }; snapshots.append(value) }
}
