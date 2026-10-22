import Foundation
import XCTest
import ODAIKitCore
import ODAIKitMultimodal

final class MultimodalGenerationClientTests: XCTestCase {
    private var request: MultimodalRequest {
        try! .init(imageFile: URL(fileURLWithPath: "/nonexistent/image.png"), prompt: " Describe. ", maxOutputTokens: 37)
    }

    func testRegistrationRejectsMissingDefaultDuplicateAndBlankIdentifiers() {
        let backend = FixtureBackend()
        XCTAssertThrowsError(try MultimodalGenerationClient(defaultBackendId: "missing", backends: [backend]))
        XCTAssertThrowsError(try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend, backend]))
        XCTAssertThrowsError(try MultimodalGenerationClient(defaultBackendId: " \n", backends: [FixtureBackend(id: " \n")]))
        XCTAssertThrowsError(try MultimodalGenerationClient(defaultBackendId: "", backends: []))
    }

    func testListSortedAndExplicitUnknownUnavailableNeverFallback() async throws {
        let a = FixtureBackend(id: "a")
        let z = FixtureBackend(id: "z", available: false)
        let client = try MultimodalGenerationClient(defaultBackendId: "a", backends: [z, a])
        let list = try await client.listBackends()
        XCTAssertEqual(list.map(\.backendId), ["a", "z"])
        XCTAssertEqual(list[1].availability.reason, .missingAssets)
        for (selection, expected) in [
            (MultimodalBackendSelection.explicit("missing"), MultimodalClientError.backendNotRegistered("missing")),
            (.explicit("z"), .backendUnavailable("z", .missingAssets))
        ] {
            do { _ = try await client.generate(request, backend: selection); XCTFail("must reject selection") }
            catch { XCTAssertEqual(error as? MultimodalClientError, expected) }
        }
        let aStarts = await a.starts, zStarts = await z.starts
        XCTAssertEqual(aStarts, 0); XCTAssertEqual(zStarts, 0)
        await client.close()
    }

    func testSuccessRetainsSelectedBackendModelTextAndUnchangedRequestAfterClose() async throws {
        let a = FixtureBackend(id: "a"), b = FixtureBackend(id: "b")
        let client = try MultimodalGenerationClient(defaultBackendId: "a", backends: [a, b])
        let result = try await client.generate(request, backend: .explicit("b"))
        let submitted = await b.requests
        XCTAssertEqual(submitted, [request])
        XCTAssertEqual(result.backendId, "b")
        XCTAssertEqual(result.output, FixtureBackend.output)
        let drains = await b.drains, aStarts = await a.starts
        XCTAssertEqual(drains, 1); XCTAssertEqual(aStarts, 0)
        await client.close()
        XCTAssertEqual(result.output.text, "A red square.")
        do { _ = try await client.generate(request); XCTFail("closed admission") }
        catch { XCTAssertEqual(error as? MultimodalClientError, .closed) }
        do { _ = try await client.listBackends(); XCTFail("closed query") }
        catch { XCTAssertEqual(error as? MultimodalClientError, .closed) }
    }

    func testGenerationErrorStillDrainsAndAllowsReentry() async throws {
        let backend = FixtureBackend(failure: .generation)
        let client = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend])
        for _ in 0..<2 {
            do { _ = try await client.generate(request); XCTFail("backend error") }
            catch { XCTAssertEqual(error as? FixtureFailure, .generation) }
        }
        let starts = await backend.starts, drains = await backend.drains
        XCTAssertEqual(starts, 2); XCTAssertEqual(drains, 2)
        await client.close()
    }

    func testAvailabilityOccupiesSlotAndCancelledAdmissionCannotStartBackend() async throws {
        let backend = FixtureBackend(blockAvailability: true)
        let client = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend])
        let request = request
        let operation = Task { try await client.generate(request) }
        await backend.availabilityGate.waitForEntry()
        do { _ = try await client.generate(request); XCTFail("admission occupies slot") }
        catch { XCTAssertEqual(error as? MultimodalClientError, .busy) }
        operation.cancel()
        await backend.availabilityGate.open()
        do { _ = try await operation.value; XCTFail("cancelled availability") }
        catch { XCTAssertTrue(error is CancellationError) }
        let starts = await backend.starts
        XCTAssertEqual(starts, 0)
        await client.close()
    }

    func testCancellationWinsLateSuccessAndLateOrdinaryErrorAndWaitsForDrain() async throws {
        for failure in [nil, FixtureFailure.generation] {
            let backend = FixtureBackend(blockGeneration: true, blockDrain: true, failure: failure)
            let client = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend])
            let request = request
            let operation = Task { try await client.generate(request) }
            await backend.generationGates[0].waitForEntry()
            operation.cancel()
            await backend.generationGates[0].open()
            await backend.drainGate.waitForEntry()
            do { _ = try await client.generate(request); XCTFail("mandatory drain occupies slot") }
            catch { XCTAssertEqual(error as? MultimodalClientError, .busy) }
            await backend.drainGate.open()
            do { _ = try await operation.value; XCTFail("cancellation wins any late outcome") }
            catch { XCTAssertTrue(error is CancellationError, "actual: \(error)") }
            let drains = await backend.drains
            XCTAssertEqual(drains, 1)
            await client.close()
        }
    }

    func testDrainWaitsWithoutCancellingActiveGeneration() async throws {
        let backend = FixtureBackend(blockGeneration: true)
        let client = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend])
        let request = request
        let operation = Task { try await client.generate(request) }
        await backend.generationGates[0].waitForEntry()
        let draining = Task { await client.drain() }
        await backend.generationGates[0].open()
        let result = try await operation.value
        await draining.value
        XCTAssertEqual(result.output, FixtureBackend.output)
        await client.close()
    }

    func testUpdatesAreStrictlyIncreasingAndCancelledOrOldCallbacksCannotReachNextOperation() async throws {
        let backend = FixtureBackend(blockGeneration: true, blockSecondGeneration: true)
        let client = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend])
        let received = SnapshotRecorder()
        let firstDelivered = expectation(description: "sequence 2 delivered")
        let secondDelivered = expectation(description: "new operation delivered")
        let request = request
        let first = Task {
            try await client.generate(request, update: {
                received.append($0.sequence)
                if $0.sequence == 2 { firstDelivered.fulfill() }
            })
        }
        await backend.generationGates[0].waitForEntry()
        await backend.emit(index: 0, sequence: 2)
        await fulfillment(of: [firstDelivered], timeout: 2)
        await backend.emit(index: 0, sequence: 2)
        await backend.emit(index: 0, sequence: 1)
        first.cancel()
        await backend.emit(index: 0, sequence: 50)
        await backend.generationGates[0].open()
        _ = await first.result
        let second = Task {
            try await client.generate(request, update: {
                received.append($0.sequence)
                if $0.sequence == 3 { secondDelivered.fulfill() }
            })
        }
        await backend.generationGates[1].waitForEntry()
        await backend.emit(index: 0, sequence: 99)
        await backend.emit(index: 1, sequence: 3)
        await fulfillment(of: [secondDelivered], timeout: 2)
        await backend.generationGates[1].open()
        _ = try await second.value
        await client.close()
        XCTAssertEqual(received.values, [2, 3])
    }

    func testCloseCancelsGenerationSuppressesUpdatesAndConcurrentCloseRunsOnce() async throws {
        let backend = FixtureBackend(blockGeneration: true, blockDrain: true)
        let other = FixtureBackend(id: "other")
        let client = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend, other])
        let received = SnapshotRecorder()
        let request = request
        let operation = Task { try await client.generate(request, update: { received.append($0.sequence) }) }
        await backend.generationGates[0].waitForEntry()
        let closing = Task { await client.close() }
        try await waitUntilClosed(client)
        let another = Task { await client.close() }
        closing.cancel()
        await backend.emit(index: 0, sequence: 9)
        await backend.generationGates[0].open()
        await backend.drainGate.waitForEntry()
        let before = await backend.closes
        XCTAssertEqual(before, 0)
        await backend.drainGate.open()
        do { _ = try await operation.value; XCTFail("close cancels generation") }
        catch { XCTAssertTrue(error is CancellationError) }
        await closing.value; await another.value; await client.close()
        let closes = await backend.closes, otherCloses = await other.closes
        XCTAssertEqual(closes, 1); XCTAssertEqual(otherCloses, 1)
        XCTAssertEqual(received.values, [])
    }

    func testCloseJoinsAdmittedMetadataQueryBeforeClosingBackend() async throws {
        let backend = FixtureBackend(blockAvailability: true)
        let client = try MultimodalGenerationClient(defaultBackendId: backend.backendId, backends: [backend])
        let query = Task { try await client.listBackends() }
        await backend.availabilityGate.waitForEntry()
        let closing = Task { await client.close() }
        try await waitUntilClosed(client)
        let before = await backend.closes
        XCTAssertEqual(before, 0)
        await backend.availabilityGate.open()
        do { _ = try await query.value; XCTFail("closed query must not deliver") }
        catch { XCTAssertEqual(error as? MultimodalClientError, .closed) }
        await closing.value
        let after = await backend.closes
        XCTAssertEqual(after, 1)
    }

    private func waitUntilClosed(_ client: MultimodalGenerationClient) async throws {
        for _ in 0..<1000 {
            do { _ = try await client.listBackends() }
            catch MultimodalClientError.closed { return }
            await Task.yield()
        }
        XCTFail("close must seal admission")
    }
}

private actor Barrier {
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

private enum FixtureFailure: Error, Equatable { case generation }

private actor FixtureBackend: MultimodalGenerationBackend {
    nonisolated let backendId: String
    nonisolated static let output = try! MultimodalTextOutput(
        text: "A red square.", model: .init(modelId: "fixture", modelRevision: "r1", runtimeRevision: "runtime1"))
    let available: Bool
    let failure: FixtureFailure?
    let availabilityGate: Barrier, drainGate: Barrier
    let generationGates: [Barrier]
    var starts = 0, closes = 0, drains = 0, queries = 0
    var requests: [MultimodalRequest] = []
    var callbacks: [(@Sendable (MultimodalTextUpdate) -> Void)?] = []
    init(id: String = "fixture.multimodal", available: Bool = true, blockAvailability: Bool = false,
         blockGeneration: Bool = false, blockSecondGeneration: Bool = false, blockDrain: Bool = false,
         failure: FixtureFailure? = nil) {
        backendId = id; self.available = available; self.failure = failure
        availabilityGate = Barrier(open: !blockAvailability)
        generationGates = [Barrier(open: !blockGeneration), Barrier(open: !blockSecondGeneration)]
        drainGate = Barrier(open: !blockDrain)
    }
    func availability() async -> CapabilityAvailability {
        queries += 1
        if queries == 1 { await availabilityGate.wait() }
        return .init(status: available ? .available : .requiresAssets,
                     reason: available ? nil : .missingAssets, backendId: backendId)
    }
    func generate(_ request: MultimodalRequest,
                  update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> MultimodalTextOutput {
        let index = starts
        starts += 1; requests.append(request); callbacks.append(update)
        await generationGates[index].wait() // Deliberately ignores cancellation.
        if let failure { throw failure }
        return Self.output
    }
    func emit(index: Int, sequence: Int) { callbacks[index]?(try! .init(sequence: sequence, text: "snapshot")) }
    func drain() async { drains += 1; await drainGate.wait() }
    func close() { closes += 1 }
}

private final class SnapshotRecorder: @unchecked Sendable {
    private let lock = NSLock()
    private var stored: [Int] = []
    var values: [Int] { lock.lock(); defer { lock.unlock() }; return stored }
    func append(_ value: Int) { lock.lock(); stored.append(value); lock.unlock() }
}
