import Foundation
import ODAIKitCore
import ODAIKitMultimodal
import XCTest

#if ODAI_DEMO_BINDING
  import ODAIKitDemo
#else
  import ODAIKitBinding
#endif

@MainActor
final class IOSMultimodalBridgeTests: XCTestCase {
  func testSuccessPreservesInputSelectionIdentityUpdatesAndReentry() async throws {
    let a = Backend(id: "a")
    let b = Backend(id: "b", blockGenerate: true)
    let inputs = Inputs()
    let bridge = IOSMultimodalBridge(
      client: try MultimodalGenerationClient(defaultBackendId: "a", backends: [a, b]),
      inputs: inputs)
    let second = GenerateCapture()
    let capture = GenerateCapture { _ in
      XCTAssertEqual(inputs.releases, 1)
      _ = bridge.generateMultimodal(
        request: self.request, selection: self.selection, callback: second)
    }
    _ = bridge.generateMultimodal(
      request: request, selection: MultimodalBridgeSelectionExplicit(backendId: "b"),
      callback: capture)
    await fulfillment(of: [b.started, capture.updated], timeout: 3)
    await b.generateGate.open()
    await fulfillment(of: [capture.done, second.done], timeout: 3)
    XCTAssertEqual(capture.output?.backendId, "b")
    XCTAssertEqual(capture.output?.text, "A red square.")
    XCTAssertEqual(capture.output?.model.modelId, "fixture")
    XCTAssertEqual(capture.output?.model.modelRevision, "r1")
    XCTAssertEqual(capture.output?.model.runtimeRevision, "runtime1")
    XCTAssertEqual(capture.updates, [2])
    XCTAssertEqual(capture.texts, ["A red"])
    let nativeRequest = await b.lastRequest
    XCTAssertEqual(nativeRequest?.prompt, " Describe. \n")
    XCTAssertEqual(nativeRequest?.maxOutputTokens, 37)
    XCTAssertEqual(inputs.releases, 2)
    XCTAssertEqual(second.output?.backendId, "a")
    XCTAssertTrue(capture.onMain)
    let closing = CloseCapture()
    bridge.closeMultimodal(callback: closing)
    await fulfillment(of: [closing.done], timeout: 3)
    XCTAssertEqual(capture.output?.text, "A red square.")
  }

  func testCancelKeepsSlotAndLeaseUntilDrainAndRejectsLateUpdate() async throws {
    let f = try Fixture(blockGenerate: true, blockDrain: true)
    let capture = GenerateCapture()
    let task = f.bridge.generateMultimodal(
      request: request, selection: selection, callback: capture)
    await fulfillment(of: [f.backend.started, capture.updated], timeout: 3)
    await Task.detached { task.cancel() }.value
    await f.backend.generateGate.open()
    await fulfillment(of: [f.backend.draining], timeout: 3)
    XCTAssertEqual(f.inputs.releases, 0)
    XCTAssertFalse(capture.finished)
    let busy = GenerateCapture()
    _ = f.bridge.generateMultimodal(request: request, selection: selection, callback: busy)
    XCTAssertEqual(busy.failure?.code, .busy)
    await f.backend.drainGate.open()
    await fulfillment(of: [capture.done], timeout: 3)
    XCTAssertTrue(capture.cancelled)
    XCTAssertEqual(f.inputs.releases, 1)
    await f.backend.emit(sequence: 99)
    XCTAssertEqual(capture.updates, [2])
    await f.close(self)
  }

  func testImmediateCancelReleasesInputWithoutNativeStart() async throws {
    let f = try Fixture()
    let capture = GenerateCapture()
    f.bridge.generateMultimodal(request: request, selection: selection, callback: capture).cancel()
    await fulfillment(of: [capture.done], timeout: 3)
    XCTAssertTrue(capture.cancelled)
    let starts = await f.backend.starts
    XCTAssertEqual(f.inputs.releases, 1)
    XCTAssertEqual(starts, 0)
    await f.close(self)
  }

  func testStrictUnknownSelectionAndUnavailableNeverFallback() async throws {
    let f = try Fixture(available: false)
    let unknown = GenerateCapture()
    _ = f.bridge.generateMultimodal(
      request: request, selection: UnknownSelection(), callback: unknown)
    XCTAssertEqual(unknown.failure?.code, .invalidInput)
    XCTAssertEqual(f.inputs.acquires, 0)
    let missing = GenerateCapture()
    _ = f.bridge.generateMultimodal(
      request: request, selection: MultimodalBridgeSelectionExplicit(backendId: "missing"),
      callback: missing)
    await fulfillment(of: [missing.done], timeout: 3)
    XCTAssertEqual(missing.failure?.code, .backendNotRegistered)
    let unavailable = GenerateCapture()
    _ = f.bridge.generateMultimodal(request: request, selection: selection, callback: unavailable)
    await fulfillment(of: [unavailable.done], timeout: 3)
    XCTAssertEqual(unavailable.failure?.code, .backendUnavailable)
    let starts = await f.backend.starts
    XCTAssertEqual(starts, 0)
    await f.close(self)
  }

  func testCloseJoinsQueryAndRejectsAdmission() async throws {
    let f = try Fixture(blockQuery: true)
    let query = BackendsCapture()
    _ = f.bridge.queryMultimodalBackends(callback: query)
    await fulfillment(of: [f.backend.queryStarted], timeout: 3)
    let one = CloseCapture()
    let two = CloseCapture()
    f.bridge.closeMultimodal(callback: one)
    f.bridge.closeMultimodal(callback: two)
    let before = await f.backend.closes
    XCTAssertFalse(one.finished)
    XCTAssertEqual(before, 0)
    let rejected = GenerateCapture()
    _ = f.bridge.generateMultimodal(request: request, selection: selection, callback: rejected)
    XCTAssertEqual(rejected.failure?.code, .closed)
    await f.backend.queryGate.open()
    await fulfillment(of: [query.done, one.done, two.done], timeout: 3)
    let after = await f.backend.closes
    XCTAssertTrue(query.cancelled)
    XCTAssertEqual(after, 1)
    let again = CloseCapture()
    f.bridge.closeMultimodal(callback: again)
    XCTAssertTrue(again.finished)
  }

  func testPrivateErrorsMapToStablePlatformFailure() async throws {
    let f = try Fixture(privateError: true)
    let capture = GenerateCapture()
    _ = f.bridge.generateMultimodal(request: request, selection: selection, callback: capture)
    await fulfillment(of: [capture.done], timeout: 3)
    XCTAssertEqual(capture.failure?.code, .platform)
    XCTAssertEqual(capture.failure?.domain, "ios.multimodal")
    XCTAssertEqual(capture.failure?.platformCode?.intValue, 1)
    XCTAssertNil(capture.failure?.reason)
    await f.close(self)
  }

  func testCancelWinsLateOrdinaryError() async throws {
    let f = try Fixture(blockGenerate: true, privateError: true)
    let capture = GenerateCapture()
    let task = f.bridge.generateMultimodal(
      request: request, selection: selection, callback: capture)
    await fulfillment(of: [f.backend.started], timeout: 3)
    task.cancel()
    await f.backend.generateGate.open()
    await fulfillment(of: [capture.done], timeout: 3)
    XCTAssertTrue(capture.cancelled)
    XCTAssertNil(capture.failure)
    XCTAssertEqual(capture.terminals, 1)
    XCTAssertEqual(f.inputs.releases, 1)
    await f.close(self)
  }

  func testCloseJoinsGenerationDrainBeforeLeaseAndNativeClose() async throws {
    let f = try Fixture(blockGenerate: true, blockDrain: true)
    let capture = GenerateCapture()
    _ = f.bridge.generateMultimodal(request: request, selection: selection, callback: capture)
    await fulfillment(of: [f.backend.started], timeout: 3)
    let closing = CloseCapture()
    f.bridge.closeMultimodal(callback: closing)
    await f.backend.generateGate.open()
    await fulfillment(of: [f.backend.draining], timeout: 3)
    XCTAssertFalse(closing.finished)
    XCTAssertEqual(f.inputs.releases, 0)
    await f.backend.drainGate.open()
    await fulfillment(of: [capture.done, closing.done], timeout: 3)
    XCTAssertTrue(capture.cancelled)
    XCTAssertEqual(f.inputs.releases, 1)
    let closes = await f.backend.closes
    XCTAssertEqual(closes, 1)
  }

  func testUnlinkedAndInvalidNativeInputNeverSynthesizeOutput() async throws {
    let inputs = Inputs()
    let bridge = IOSMultimodalBridge(client: nil, inputs: inputs)
    let absent = GenerateCapture()
    _ = bridge.generateMultimodal(request: request, selection: selection, callback: absent)
    XCTAssertEqual(absent.failure?.reason, "backendNotLinked")
    XCTAssertEqual(inputs.acquires, 0)
    let closing = CloseCapture()
    bridge.closeMultimodal(callback: closing)
    await fulfillment(of: [closing.done], timeout: 3)
    let closed = GenerateCapture()
    _ = bridge.generateMultimodal(request: request, selection: selection, callback: closed)
    XCTAssertEqual(closed.failure?.code, .closed)

    let f = try Fixture()
    f.inputs.url = URL(string: "https://example.invalid/image.png")!
    let invalid = GenerateCapture()
    _ = f.bridge.generateMultimodal(request: request, selection: selection, callback: invalid)
    XCTAssertEqual(invalid.failure?.code, .invalidInput)
    XCTAssertEqual(f.inputs.releases, 1)
    await f.close(self)
  }

  func testQueryPreservesAvailabilityMetadata() async throws {
    let f = try Fixture(available: false)
    let capture = BackendsCapture()
    _ = f.bridge.queryMultimodalBackends(callback: capture)
    await fulfillment(of: [capture.done], timeout: 3)
    XCTAssertEqual(capture.backends?.first?.backendId, "fixture")
    XCTAssertEqual(capture.backends?.first?.availability.available, false)
    XCTAssertEqual(capture.backends?.first?.availability.reason, "missingAssets")
    XCTAssertEqual(capture.backends?.first?.availability.minimumOS, "15")
    await f.close(self)
  }

  private var request: MultimodalBridgeRequest {
    .init(inputReference: "input", prompt: " Describe. \n", maxOutputTokens: 37)
  }
  private var selection: any MultimodalBridgeSelection { MultimodalBridgeSelectionDefault.shared }
}

@MainActor private final class Fixture {
  let backend: Backend
  let inputs = Inputs()
  let bridge: IOSMultimodalBridge
  init(
    blockGenerate: Bool = false, blockDrain: Bool = false, blockQuery: Bool = false,
    available: Bool = true, privateError: Bool = false
  ) throws {
    backend = Backend(
      blockGenerate: blockGenerate, blockDrain: blockDrain, blockQuery: blockQuery,
      available: available, privateError: privateError)
    bridge = IOSMultimodalBridge(
      client: try MultimodalGenerationClient(defaultBackendId: "fixture", backends: [backend]),
      inputs: inputs)
  }
  func close(_ test: XCTestCase) async {
    let c = CloseCapture()
    bridge.closeMultimodal(callback: c)
    await test.fulfillment(of: [c.done], timeout: 3)
  }
}
private actor Gate {
  var opened: Bool
  var waiters: [CheckedContinuation<Void, Never>] = []
  init(open: Bool) { opened = open }
  func wait() async { if !opened { await withCheckedContinuation { waiters.append($0) } } }
  func open() {
    opened = true
    let w = waiters
    waiters.removeAll()
    for waiter in w { waiter.resume() }
  }
}
private actor Backend: MultimodalGenerationBackend {
  nonisolated let backendId: String
  nonisolated let started = XCTestExpectation(description: "started")
  nonisolated let draining = XCTestExpectation(description: "draining")
  nonisolated let queryStarted = XCTestExpectation(description: "query")
  let generateGate: Gate
  let drainGate: Gate
  let queryGate: Gate
  let available: Bool
  let privateError: Bool
  var starts = 0
  var closes = 0
  var lastRequest: MultimodalRequest?
  private var callback: (@Sendable (MultimodalTextUpdate) -> Void)?
  init(
    id: String = "fixture", blockGenerate: Bool = false, blockDrain: Bool = false,
    blockQuery: Bool = false, available: Bool = true, privateError: Bool = false
  ) {
    backendId = id
    generateGate = Gate(open: !blockGenerate)
    drainGate = Gate(open: !blockDrain)
    queryGate = Gate(open: !blockQuery)
    self.available = available
    self.privateError = privateError
  }
  func availability() async -> CapabilityAvailability {
    queryStarted.fulfill()
    await queryGate.wait()
    return .init(
      status: available ? .available : .requiresAssets, reason: available ? nil : .missingAssets,
      backendId: backendId, minOS: "15", offline: true)
  }
  func generate(_ request: MultimodalRequest, update: (@Sendable (MultimodalTextUpdate) -> Void)?)
    async throws -> MultimodalTextOutput
  {
    starts += 1
    lastRequest = request
    callback = update
    emit(sequence: 2)
    emit(sequence: 2)
    emit(sequence: 1)
    started.fulfill()
    await generateGate.wait()
    if privateError { throw NSError(domain: "/private/image", code: 7) }
    return try .init(
      text: "A red square.",
      model: .init(modelId: "fixture", modelRevision: "r1", runtimeRevision: "runtime1"))
  }
  func emit(sequence: Int) { callback?(try! .init(sequence: sequence, text: "A red")) }
  func drain() async {
    XCTAssertFalse(Task.isCancelled)
    draining.fulfill()
    await drainGate.wait()
  }
  func close() { closes += 1 }
}
@MainActor private final class Inputs: IOSMultimodalInputProvider {
  var releases = 0
  var acquires = 0
  var url = URL(fileURLWithPath: "/tmp/input.png")
  func acquire(reference: String) throws -> any IOSMultimodalInputLease {
    XCTAssertTrue(Thread.isMainThread)
    guard reference == "input" else { throw NSError(domain: "invalid", code: 1) }
    acquires += 1
    return Input(owner: self, url: url)
  }
}
@MainActor private final class Input: IOSMultimodalInputLease {
  let url: URL
  let owner: Inputs
  init(owner: Inputs, url: URL) {
    self.owner = owner
    self.url = url
  }
  func release() {
    XCTAssertTrue(Thread.isMainThread)
    owner.releases += 1
  }
}
@MainActor private class Capture: NSObject {
  let done = XCTestExpectation(description: "done")
  var finished = false
  var cancelled = false
  var onMain = false
  var terminals = 0
  func end() {
    onMain = Thread.isMainThread
    XCTAssertTrue(onMain)
    terminals += 1
    finished = true
    done.fulfill()
  }
}
@MainActor
private final class GenerateCapture: Capture, @preconcurrency MultimodalBridgeGenerateCallback {
  let updated = XCTestExpectation(description: "update")
  let reenter: (GenerateCapture) -> Void
  var output: MultimodalBridgeOutput?
  var failure: MultimodalBridgeFailure?
  var updates = [Int32]()
  var texts = [String]()
  init(_ reenter: @escaping (GenerateCapture) -> Void = { _ in }) { self.reenter = reenter }
  func onMultimodalUpdate(update: MultimodalBridgeUpdate) {
    XCTAssertTrue(Thread.isMainThread)
    updates.append(update.sequence)
    texts.append(update.text)
    updated.fulfill()
  }
  func onMultimodalResult(output: MultimodalBridgeOutput) {
    self.output = output
    reenter(self)
    end()
  }
  func onMultimodalFailure(failure: MultimodalBridgeFailure) {
    self.failure = failure
    end()
  }
  func onMultimodalCancelled() {
    cancelled = true
    end()
  }
}
@MainActor
private final class BackendsCapture: Capture, @preconcurrency MultimodalBridgeBackendsCallback {
  var backends: [MultimodalBridgeBackend]?
  func onMultimodalBackends(backends: [MultimodalBridgeBackend]) {
    self.backends = backends
    end()
  }
  func onMultimodalFailure(failure: MultimodalBridgeFailure) { end() }
  func onMultimodalCancelled() {
    cancelled = true
    end()
  }
}
@MainActor private final class CloseCapture: Capture, @preconcurrency MultimodalBridgeCloseCallback
{
  func onMultimodalClosed() { end() }
  func onMultimodalFailure(failure: MultimodalBridgeFailure) {
    XCTFail("unexpected close failure")
    end()
  }
}
private final class UnknownSelection: NSObject, MultimodalBridgeSelection {}
