import XCTest
import Foundation
import ODAIKitModelStore
import ODAIKitMultimodal
@testable import ODAIKitAppleMLXVLM

final class MLXVLMVideoLifecycleTests: XCTestCase {
    private func video(tokens: Int = 17, prompt: String = "What changes?") throws -> MultimodalVideoRequest {
        try .init(videoFile: URL(fileURLWithPath: "/fixture.mp4"), prompt: prompt, maxOutputTokens: tokens)
    }
    private func image() throws -> MultimodalRequest {
        try .init(imageFile: URL(fileURLWithPath: "/fixture.png"), prompt: "Describe")
    }
    // Wrong runtime dispatch, altered request, model identity or relay sequence must fail.
    func testVideoDispatchPreservesRequestIdentityAndFiltersOldUpdates() async throws {
        let f = try await ModelFixture.make(), runtime = VideoSessionRuntime()
        let s = MLXVLMGenerationSession(lease: f.lease, runtime: runtime, identity: SmolVLM2Model.identity)
        let values = VideoSessionUpdates(), req = try video()
        let task = Task { try await s.generateVideo(req, update: { values.append($0) }) }
        await runtime.waitForStart()
        let received = await runtime.video
        XCTAssertEqual(received, req)
        await runtime.emit(2); await runtime.emit(1); await runtime.emit(2); await runtime.emit(3)
        await runtime.finish()
        let result = try await task.value
        XCTAssertEqual(result.text, "result"); XCTAssertEqual(result.model, SmolVLM2Model.identity)
        XCTAssertEqual(values.values, [2, 3])
        await runtime.emit(4)
        XCTAssertEqual(values.values, [2, 3])
        await s.close(); try await f.store.remove(f.manifest.ref)
    }
    // An independent video active slot would incorrectly admit the other modality.
    func testBothModalitiesShareOneRuntimeSlot() async throws {
        for firstVideo in [true, false] {
            let f = try await ModelFixture.make(), runtime = VideoSessionRuntime()
            let s = MLXVLMGenerationSession(lease: f.lease, runtime: runtime, identity: SmolVLM2Model.identity)
            let v = try video(), i = try image()
            let task = Task {
                if firstVideo { return try await s.generateVideo(v, update: nil) }
                return try await s.generate(i, update: nil)
            }
            await runtime.waitForStart()
            do { _ = try await s.generateVideo(v, update: nil); XCTFail("video overlap") }
            catch { XCTAssertEqual(error as? MultimodalClientError, .busy) }
            do { _ = try await s.generate(i, update: nil); XCTFail("image overlap") }
            catch { XCTAssertEqual(error as? MultimodalClientError, .busy) }
            await runtime.finish(); _ = try await task.value
            await s.close(); try await f.store.remove(f.manifest.ref)
        }
    }
    // These are session boundary gates, not actual native decode/processor/model tests.
    func testCancelInEachRuntimePhaseJoinsAndWinsLateSuccessOrError() async throws {
        for phase in ["decode", "prepare", "generation"] {
            for fail in [false, true] {
                let f = try await ModelFixture.make(), runtime = VideoSessionRuntime(fail: fail, phase: phase)
                let s = MLXVLMGenerationSession(lease: f.lease, runtime: runtime, identity: SmolVLM2Model.identity)
                let values = VideoSessionUpdates(), v = try video()
                let task = Task { try await s.generateVideo(v, update: { values.append($0) }) }
                await runtime.waitForStart(); task.cancel(); await runtime.emit(0)
                do { try await f.store.remove(f.manifest.ref); XCTFail("lease released during \(phase)") }
                catch { XCTAssertEqual(error as? ModelStoreError, .inUse) }
                do { _ = try await s.generate(try image(), update: nil); XCTFail("admitted before runtime join") }
                catch { XCTAssertEqual(error as? MultimodalClientError, .busy) }
                await runtime.finish()
                do { _ = try await task.value; XCTFail("cancel lost") }
                catch { XCTAssertTrue(error is CancellationError) }
                XCTAssertEqual(values.values, [])
                await runtime.setFailure(false)
                let next = Task { try await s.generateVideo(v, update: nil) }
                await runtime.waitForStart(); await runtime.finish(); _ = try await next.value
                await s.close(); try await f.store.remove(f.manifest.ref)
            }
        }
    }
    // Closing before runtime exit must neither release the lease nor reopen either API.
    func testCloseJoinsVideoAndReleasesLeaseExactlyAfterRuntimeReturn() async throws {
        let f = try await ModelFixture.make(), runtime = VideoSessionRuntime()
        let s = MLXVLMGenerationSession(lease: f.lease, runtime: runtime, identity: SmolVLM2Model.identity)
        let v = try video()
        let operation = Task { try await s.generateVideo(v, update: nil) }
        await runtime.waitForStart()
        let first = Task { await s.close() }
        while await s.availability(supported: true).reason != .closed { await Task.yield() }
        let second = Task { await s.close() }
        do { try await f.store.remove(f.manifest.ref); XCTFail("premature release") }
        catch { XCTAssertEqual(error as? ModelStoreError, .inUse) }
        await runtime.finish()
        do { _ = try await operation.value; XCTFail("close must cancel") }
        catch { XCTAssertTrue(error is CancellationError) }
        await first.value; await second.value
        try await f.store.remove(f.manifest.ref)
        do { _ = try await s.generateVideo(v, update: nil); XCTFail("closed video") }
        catch { XCTAssertEqual(error as? MultimodalClientError, .closed) }
        do { _ = try await s.generate(try image(), update: nil); XCTFail("closed image") }
        catch { XCTAssertEqual(error as? MultimodalClientError, .closed) }
    }
    // Runtime errors/no-result must release the shared slot for a later image request.
    func testErrorAndEmptyVideoAllowImageReentry() async throws {
        for output in ["", "result"] {
            let f = try await ModelFixture.make(), runtime = VideoSessionRuntime(fail: output == "result", output: output)
            let s = MLXVLMGenerationSession(lease: f.lease, runtime: runtime, identity: SmolVLM2Model.identity)
            let v = try video()
            let task = Task { try await s.generateVideo(v, update: nil) }
            await runtime.waitForStart(); await runtime.finish()
            do { _ = try await task.value; XCTFail("missing error") }
            catch { XCTAssertEqual(error as? MLXVLMBackendError, output == "" ? .noResult : .runtimeFailure) }
            await runtime.setFailure(false, output: "result")
            let i = try image(), next = Task { try await s.generate(i, update: nil) }
            await runtime.waitForStart(); await runtime.finish(); _ = try await next.value
            await s.close(); try await f.store.remove(f.manifest.ref)
        }
    }
    // Oversized or media-injecting video prompts must fail before touching input/model.
    func testVideoBudgetAndMediaPromptRejectBeforeRuntime() async throws {
        let f = try await ModelFixture.make(), runtime = VideoSessionRuntime()
        let s = MLXVLMGenerationSession(lease: f.lease, runtime: runtime, identity: SmolVLM2Model.identity)
        for req in [try video(tokens: 513), try video(prompt: String(repeating: "你", count: 2731)),
                    try video(prompt: "<image>"), try video(prompt: "<fake_token_around_image>"), try video(prompt: "<global-img>")] {
            do { _ = try await s.generateVideo(req, update: nil); XCTFail("unbounded/media prompt") }
            catch { guard case .invalidInput = error as? MultimodalClientError else { return XCTFail("wrong error") } }
        }
        let starts = await runtime.starts; XCTAssertEqual(starts, 0)
        await s.close(); try await f.store.remove(f.manifest.ref)
    }
}

// External model work is blocked; session, relay and real ModelStore lease are under test.
private actor VideoSessionRuntime: MLXVLMRuntime {
    var video: MultimodalVideoRequest?
    var starts = 0
    private var fail: Bool, output: String
    private let phase: String
    private var entered: [CheckedContinuation<Void, Never>] = []
    private var exit: CheckedContinuation<Void, Never>?
    private var callback: (@Sendable (MultimodalTextUpdate) -> Void)?
    init(fail: Bool = false, phase: String = "generation", output: String = "result") {
        self.fail = fail; self.phase = phase; self.output = output
    }
    func generate(directory: URL, request: MultimodalRequest, observer: (@Sendable (MLXVLMGenerationEvent) -> Void)?,
                  update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> String { try await run(update) }
    func generateVideo(directory: URL, request: MultimodalVideoRequest, observer: (@Sendable (MLXVLMGenerationEvent) -> Void)?,
                       update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> String {
        video = request; return try await run(update)
    }
    private func run(_ update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> String {
        starts += 1; callback = update
        await withCheckedContinuation { continuation in
            exit = continuation; let pending = entered; entered.removeAll(); pending.forEach { $0.resume() }
        }
        if fail { throw MLXVLMBackendError.runtimeFailure }; return output
    }
    func waitForStart() async { if exit == nil { await withCheckedContinuation { entered.append($0) } } }
    func emit(_ sequence: Int) { callback?(try! .init(sequence: sequence, text: phase)) }
    func finish() { exit?.resume(); exit = nil }
    func setFailure(_ fail: Bool, output: String = "result") { self.fail = fail; self.output = output }
}
private final class VideoSessionUpdates: @unchecked Sendable {
    private let lock = NSLock()
    private var storage: [Int] = []
    func append(_ update: MultimodalTextUpdate) { lock.lock(); defer { lock.unlock() }; storage.append(update.sequence) }
    var values: [Int] { lock.lock(); defer { lock.unlock() }; return storage }
}

// Old image-only test doubles explicitly reject video; no production default/fallback.
extension MLXVLMRuntime {
    func generateVideo(directory: URL, request: MultimodalVideoRequest,
                       observer: (@Sendable (MLXVLMGenerationEvent) -> Void)?,
                       update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> String {
        throw MultimodalClientError.invalidInput("test runtime is image-only")
    }
}
