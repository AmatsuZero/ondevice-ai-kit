import XCTest
import Foundation
import ODAIKitModelStore
import ODAIKitMultimodal
@testable import ODAIKitAppleMLXVLM

final class MLXVLMLifecycleTests: XCTestCase {
    func testRequestBudgetPromptAndOrderedCumulativeUpdates() async throws {
        let f = try await ModelFixture.make()
        let updates = Updates()
        let runtime = ControlledRuntime()
        let s = MLXVLMGenerationSession(lease: f.lease, runtime: runtime, identity: SmolVLM2Model.identity)
        let request = try MultimodalRequest(imageFile: URL(fileURLWithPath: "/fixture.png"), prompt: "原始 prompt", maxOutputTokens: 7)
        let task = Task.detached { try await s.generate(request, update: { updates.append($0) }) }
        await runtime.waitForStart()
        let actual = await runtime.request
        XCTAssertEqual(actual, request)
        await runtime.emit(sequence: 0, text: "a")
        await runtime.emit(sequence: 1, text: "ab")
        await runtime.emit(sequence: 0, text: "stale")
        await runtime.finish()
        let result = try await task.value
        XCTAssertEqual(result.text, "ab")
        XCTAssertEqual(result.model, SmolVLM2Model.identity)
        XCTAssertEqual(updates.values.map(\.text), ["a", "ab"])
        await s.close()
        try await f.store.remove(f.manifest.ref)
    }

    func testCancelSuppressesLateUpdatesAndWinsLateErrorAfterDrain() async throws {
        let f = try await ModelFixture.make()
        let updates = Updates()
        let runtime = ControlledRuntime(fail: true)
        let s = MLXVLMGenerationSession(lease: f.lease, runtime: runtime, identity: SmolVLM2Model.identity)
        let req = try request()
        let task = Task.detached { try await s.generate(req, update: { updates.append($0) }) }
        await runtime.waitForStart()
        task.cancel()
        await runtime.emit(sequence: 0, text: "late")
        do { try await f.store.remove(f.manifest.ref); XCTFail("released before runtime exit") }
        catch { XCTAssertEqual(error as? ModelStoreError, .inUse) }
        do { _ = try await s.generate(try request(), update: nil); XCTFail("overlap admitted") }
        catch { XCTAssertEqual(error as? MultimodalClientError, .busy) }
        await runtime.finish()
        do { _ = try await task.value; XCTFail("cancel lost") }
        catch { XCTAssertTrue(error is CancellationError) }
        XCTAssertEqual(updates.values.count, 0)
        await s.close()
        try await f.store.remove(f.manifest.ref)
    }

    func testConcurrentCloseWaitsForRuntimeThenReleasesLease() async throws {
        let f = try await ModelFixture.make()
        let runtime = ControlledRuntime()
        let s = MLXVLMGenerationSession(lease: f.lease, runtime: runtime, identity: SmolVLM2Model.identity)
        let req = try request()
        let task = Task.detached { try await s.generate(req, update: nil) }
        await runtime.waitForStart()
        let first = Task { await s.close() }
        while await s.availability(supported: true).reason != .closed { await Task.yield() }
        let second = Task { await s.close() }
        do { try await f.store.remove(f.manifest.ref); XCTFail("close did not join") }
        catch { XCTAssertEqual(error as? ModelStoreError, .inUse) }
        await runtime.finish()
        _ = await task.result
        await first.value
        await second.value
        try await f.store.remove(f.manifest.ref)
        do { _ = try await s.generate(try request(), update: nil); XCTFail("closed generation") }
        catch { XCTAssertEqual(error as? MultimodalClientError, .closed) }
    }

    func testOrdinaryRuntimeErrorIsPreservedAndNextRequestCanRun() async throws {
        let f = try await ModelFixture.make()
        let runtime = ControlledRuntime(fail: true)
        let s = MLXVLMGenerationSession(lease: f.lease, runtime: runtime, identity: SmolVLM2Model.identity)
        let req = try request()
        let task = Task.detached { try await s.generate(req, update: nil) }
        await runtime.waitForStart()
        await runtime.finish()
        do { _ = try await task.value; XCTFail("error lost") }
        catch { XCTAssertEqual(error as? MLXVLMBackendError, .runtimeFailure) }
        let nextReq = try request()
        let next = Task.detached { try await s.generate(nextReq, update: nil) }
        await runtime.waitForStart()
        await runtime.finish()
        _ = await next.result
        let starts = await runtime.starts
        XCTAssertEqual(starts, 2)
        await s.close()
    }

    func testRequestLimitRejectedWithoutLoading() async throws {
        let f = try await ModelFixture.make()
        let runtime = ImmediateRuntime()
        let s = MLXVLMGenerationSession(lease: f.lease, runtime: runtime, identity: SmolVLM2Model.identity)
        for request in [try request(tokens: 513), try request(prompt: String(repeating: "a", count: 8193))] {
            do { _ = try await s.generate(request, update: nil); XCTFail("unbounded request") }
            catch { guard case .invalidInput = error as? MultimodalClientError else { return XCTFail("wrong error") } }
        }
        let count = await runtime.count
        XCTAssertEqual(count, 0)
        await s.close()
    }

    private func request(tokens: Int = 8, prompt: String = "describe") throws -> MultimodalRequest {
        try MultimodalRequest(imageFile: URL(fileURLWithPath: "/fixture.png"), prompt: prompt, maxOutputTokens: tokens)
    }
}

private final class Updates: @unchecked Sendable {
    private let lock = NSLock()
    private var storage: [MultimodalTextUpdate] = []
    func append(_ update: MultimodalTextUpdate) { lock.lock(); defer { lock.unlock() }; storage.append(update) }
    var values: [MultimodalTextUpdate] { lock.lock(); defer { lock.unlock() }; return storage }
}

private actor ControlledRuntime: MLXVLMRuntime {
    private let fail: Bool
    private var started: [CheckedContinuation<Void, Never>] = []
    private var finishContinuation: CheckedContinuation<Void, Never>?
    private var callback: (@Sendable (MultimodalTextUpdate) -> Void)?
    var request: MultimodalRequest?
    var starts = 0
    init(fail: Bool = false) { self.fail = fail }
    func waitForStart() async {
        if finishContinuation != nil { return }
        await withCheckedContinuation { started.append($0) }
    }
    func generate(directory: URL, request: MultimodalRequest,
                  observer: (@Sendable (MLXVLMGenerationEvent) -> Void)?,
                  update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> String {
        self.request = request
        starts += 1
        callback = update
        await withCheckedContinuation { continuation in
            finishContinuation = continuation
            let pending = started
            started.removeAll()
            pending.forEach { $0.resume() }
        }
        if fail { throw MLXVLMBackendError.runtimeFailure }
        return "ab"
    }
    func emit(sequence: Int, text: String) { callback?(try! .init(sequence: sequence, text: text)) }
    func finish() { finishContinuation?.resume(); finishContinuation = nil }
}
