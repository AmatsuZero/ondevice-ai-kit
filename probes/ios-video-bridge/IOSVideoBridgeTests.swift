import Foundation
import XCTest
import ODAIKitCore
import ODAIKitVideo
#if ODAI_DEMO_BINDING
import ODAIKitDemo
#else
import ODAIKitBinding
#endif

@MainActor
final class IOSVideoBridgeTests: XCTestCase {
    func testCancelledProcessingKeepsInputAndSlotUntilDrain() async throws {
        let f = try Fixture(blockProcess: true, blockDrain: true)
        let first = ProcessCapture()
        let task = f.bridge.process(request: request, selection: selection, callback: first)
        await fulfillment(of: [f.backend.started], timeout: 3)
        task.cancel()
        await f.backend.processGate.open()
        await fulfillment(of: [f.backend.draining], timeout: 3)
        XCTAssertEqual(f.inputs.releases, 0)
        XCTAssertEqual(first.terminals, 0)
        let busy = ProcessCapture()
        _ = f.bridge.process(request: request, selection: selection, callback: busy)
        XCTAssertEqual(busy.failure?.code, .busy)
        await f.backend.drainGate.open()
        await fulfillment(of: [first.done], timeout: 3)
        XCTAssertTrue(first.cancelled)
        XCTAssertEqual(first.terminals, 1)
        XCTAssertEqual(f.inputs.releases, 1)
        let releases = await f.backend.output.releases
        XCTAssertEqual(releases, 1)
        await f.close(self)
    }

    func testCancelBeforeWorkerStillReleasesInput() async throws {
        let f = try Fixture()
        let capture = ProcessCapture()
        f.bridge.process(request: request, selection: selection, callback: capture).cancel()
        await fulfillment(of: [capture.done], timeout: 3)
        XCTAssertTrue(capture.cancelled)
        XCTAssertEqual(f.inputs.releases, 1)
        let starts = await f.backend.starts
        XCTAssertEqual(starts, 0)
        await f.close(self)
    }

    func testSuccessfulOutputOutlivesCloseAndReleaseRetriesThenIsIdempotent() async throws {
        let f = try Fixture(releaseFailures: 1)
        let capture = ProcessCapture()
        _ = f.bridge.process(request: request, selection: selection, callback: capture)
        await fulfillment(of: [capture.done], timeout: 3)
        let output = try XCTUnwrap(capture.output)
        XCTAssertEqual(output.width, 1280); XCTAssertEqual(output.frameCount, 48)
        XCTAssertEqual(output.audioTrackCount, 1)
        XCTAssertEqual(output.durationSeconds, 2)
        XCTAssertEqual(f.inputs.releases, 1)
        XCTAssertEqual(f.bridge.outputURL(reference: output.reference), URL(fileURLWithPath: "/tmp/opaque-output.mov"))
        await f.close(self)
        let before = await f.backend.output.releases
        XCTAssertEqual(before, 0)
        let failed = ReleaseCapture()
        f.bridge.releaseOutput(reference: output.reference, callback: failed)
        await fulfillment(of: [failed.done], timeout: 3)
        XCTAssertEqual(failed.failure?.code, .platform)
        XCTAssertNotNil(f.bridge.outputURL(reference: output.reference))
        let release = ReleaseCapture()
        f.bridge.releaseOutput(reference: output.reference, callback: release)
        await fulfillment(of: [release.done], timeout: 3)
        XCTAssertTrue(release.released)
        XCTAssertNil(f.bridge.outputURL(reference: output.reference))
        let again = ReleaseCapture()
        f.bridge.releaseOutput(reference: output.reference, callback: again)
        XCTAssertTrue(again.released)
        let after = await f.backend.output.releases
        XCTAssertEqual(after, 2)
        let unknown = ReleaseCapture()
        f.bridge.releaseOutput(reference: output.reference + "0", callback: unknown)
        XCTAssertEqual(unknown.failure?.code, .invalidInput)
    }

    func testStrictSelectionAndUnknownSwiftConformersNeverFallback() async throws {
        let f = try Fixture()
        for (choice, expected) in [(VideoBridgeSelectionExplicit(backendId: "missing") as any VideoBridgeSelection,
                                    VideoBridgeFailureCode.backendNotRegistered),
                                   (UnknownSelection(), .invalidInput)] {
            let capture = ProcessCapture()
            _ = f.bridge.process(request: request, selection: choice, callback: capture)
            await fulfillment(of: [capture.done], timeout: 3)
            XCTAssertEqual(capture.failure?.code, expected)
        }
        let starts = await f.backend.starts
        XCTAssertEqual(starts, 0)
        await f.close(self)
    }

    func testCloseJoinsQueryAndRejectsAdmissionImmediately() async throws {
        let f = try Fixture(blockQuery: true)
        let query = BackendsCapture()
        _ = f.bridge.queryBackends(callback: query)
        await fulfillment(of: [f.backend.queryStarted], timeout: 3)
        let first = CloseCapture(); let second = CloseCapture()
        f.bridge.close(callback_: first); f.bridge.close(callback_: second)
        let rejected = ProcessCapture()
        _ = f.bridge.process(request: request, selection: selection, callback: rejected)
        XCTAssertEqual(rejected.failure?.code, .closed)
        XCTAssertFalse(first.closed)
        let before = await f.backend.closes
        XCTAssertEqual(before, 0)
        await f.backend.queryGate.open()
        await fulfillment(of: [query.done, first.done, second.done], timeout: 3)
        XCTAssertTrue(query.cancelled)
        let after = await f.backend.closes
        XCTAssertEqual(after, 1)
    }

    func testQueryMapsMetadataAndErrorsArePrivate() async throws {
        let f = try Fixture(throwPrivateError: true)
        let query = BackendsCapture()
        _ = f.bridge.queryBackends(callback: query)
        await fulfillment(of: [query.done], timeout: 3)
        XCTAssertEqual(query.backends?.first?.supportedScaleFactors.map(\.intValue), [4])
        XCTAssertEqual(query.backends?.first?.supportedAudioPolicies, [.preserve, .discard])
        let capture = ProcessCapture()
        _ = f.bridge.process(request: request, selection: selection, callback: capture)
        await fulfillment(of: [capture.done], timeout: 3)
        XCTAssertEqual(capture.failure?.code, .platform)
        XCTAssertEqual(capture.failure?.domain, "ios.video")
        XCTAssertNil(capture.failure?.reason)
        await f.close(self)
    }

    private var request: VideoBridgeRequest { .init(inputReference: "input", scaleFactor: 4, audioPolicy: .preserve) }
    private var selection: any VideoBridgeSelection { VideoBridgeSelectionDefault.shared }
}

@MainActor
private final class Fixture {
    let backend: Backend
    let inputs = Inputs()
    let bridge: IOSVideoBridge
    init(blockProcess: Bool = false, blockDrain: Bool = false, blockQuery: Bool = false,
         releaseFailures: Int = 0, throwPrivateError: Bool = false) throws {
        backend = Backend(blockProcess: blockProcess, blockDrain: blockDrain, blockQuery: blockQuery,
                          releaseFailures: releaseFailures, throwPrivateError: throwPrivateError)
        bridge = IOSVideoBridge(client: try VideoProcessingClient(defaultBackendId: "fixture", backends: [backend]), inputs: inputs)
    }
    func close(_ test: XCTestCase) async {
        let capture = CloseCapture(); bridge.close(callback_: capture)
        await test.fulfillment(of: [capture.done], timeout: 3)
    }
}

private actor Gate {
    private var isOpen: Bool
    private var waiters: [CheckedContinuation<Void, Never>] = []
    init(open: Bool) { isOpen = open }
    func wait() async { if !isOpen { await withCheckedContinuation { waiters.append($0) } } }
    func open() { isOpen = true; let pending = waiters; waiters.removeAll(); pending.forEach { $0.resume() } }
}

private actor Backend: VideoSuperResolutionBackend {
    nonisolated let backendId = "fixture"
    nonisolated let supportedScaleFactors = [4]
    nonisolated let supportedAudioPolicies: [VideoAudioPolicy] = [.preserve, .discard]
    nonisolated let started = XCTestExpectation(description: "process started")
    nonisolated let draining = XCTestExpectation(description: "drain entered")
    nonisolated let queryStarted = XCTestExpectation(description: "query entered")
    let processGate: Gate; let drainGate: Gate; let queryGate: Gate
    let output: Output
    var starts = 0; var closes = 0
    private let throwPrivateError: Bool
    init(blockProcess: Bool, blockDrain: Bool, blockQuery: Bool, releaseFailures: Int, throwPrivateError: Bool) {
        processGate = Gate(open: !blockProcess); drainGate = Gate(open: !blockDrain); queryGate = Gate(open: !blockQuery)
        output = Output(failures: releaseFailures); self.throwPrivateError = throwPrivateError
    }
    func availability() async -> CapabilityAvailability {
        queryStarted.fulfill(); await queryGate.wait()
        return .init(status: .available, backendId: backendId, minOS: "15", offline: true)
    }
    func process(_ request: VideoProcessingRequest, progress: (@Sendable (VideoProcessingProgress) -> Void)?) async throws -> any VideoOutputLease {
        starts += 1; started.fulfill(); progress?(try .init(completedFrames: 1, totalFrames: 48))
        await processGate.wait()
        if throwPrivateError { throw NSError(domain: "/private/media.mov", code: 7, userInfo: [NSLocalizedDescriptionKey: "private path"]) }
        return output
    }
    func drain() async { draining.fulfill(); await drainGate.wait() }
    func close() { closes += 1 }
}

private actor Output: VideoOutputLease {
    nonisolated let file = URL(fileURLWithPath: "/tmp/opaque-output.mov")
    nonisolated let info = try! VideoOutputInfo(width: 1280, height: 720, frameCount: 48, durationSeconds: 2, audioTrackCount: 1)
    var releases = 0
    private let failures: Int
    init(failures: Int) { self.failures = failures }
    func release() throws { releases += 1; if releases <= failures { throw NSError(domain: "private", code: 9) } }
}

@MainActor private final class Inputs: IOSVideoInputProvider {
    var releases = 0
    func acquire(reference: String) throws -> any IOSVideoInputLease {
        guard reference == "input" else { throw NSError(domain: "invalid", code: 1) }
        return Input(owner: self)
    }
}
@MainActor private final class Input: IOSVideoInputLease {
    let url = URL(fileURLWithPath: "/tmp/input.mov")
    let owner: Inputs
    private var released = false
    init(owner: Inputs) { self.owner = owner }
    func release() { if !released { released = true; owner.releases += 1 } }
}
private final class UnknownSelection: NSObject, VideoBridgeSelection {}
@MainActor private class Capture: NSObject {
    let done = XCTestExpectation(description: "terminal")
    var terminals = 0
    func end() { XCTAssertTrue(Thread.isMainThread); terminals += 1; done.fulfill() }
}
@MainActor private final class ProcessCapture: Capture, VideoBridgeProcessCallback {
    var output: VideoBridgeOutput?; var failure: VideoBridgeFailure?; var cancelled = false
    func onProgress(progress: VideoBridgeProgress) { XCTAssertTrue(Thread.isMainThread) }
    func onResult(output_: VideoBridgeOutput) { output = output_; end() }
    func onFailure(failure_: VideoBridgeFailure) { failure = failure_; end() }
    func onCancelled() { cancelled = true; end() }
}
@MainActor private final class ReleaseCapture: Capture, VideoBridgeReleaseCallback {
    var released = false; var failure: VideoBridgeFailure?
    func onReleased() { released = true; end() }
    func onFailure(failure_: VideoBridgeFailure) { failure = failure_; end() }
}
@MainActor private final class CloseCapture: Capture, VideoBridgeCloseCallback {
    var closed = false
    func onClosed() { closed = true; end() }
    func onFailure(failure_: VideoBridgeFailure) { end() }
}
@MainActor private final class BackendsCapture: Capture, VideoBridgeBackendsCallback {
    var backends: [VideoBridgeBackend]?; var cancelled = false
    func onResult(backends_: [VideoBridgeBackend]) { backends = backends_; end() }
    func onFailure(failure_: VideoBridgeFailure) { end() }
    func onCancelled() { cancelled = true; end() }
}
