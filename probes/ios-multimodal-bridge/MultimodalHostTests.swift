import Foundation
import ODAIKitDemo
import XCTest

@MainActor final class MultimodalHostTests: XCTestCase {
  func testReplacementAndClearRetireOnlySelectionOwnership() throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = try makeSource(root)
    let store = try MultimodalInputStore(directory: root.appendingPathComponent("copies"))
    var reply: ((URL?) -> Void)?
    let host = MultimodalHost(store: store, picker: { reply = $0 })
    host.selectImageFile()
    reply?(source)
    let first = try XCTUnwrap(host.model.state.inputReference)
    XCTAssertEqual(host.model.state.inputDisplayName, source.lastPathComponent)
    let lease = try store.acquire(reference: first)
    host.selectImageFile()
    reply?(source)
    let second = try XCTUnwrap(host.model.state.inputReference)
    XCTAssertNotEqual(first, second)
    XCTAssertThrowsError(try store.acquire(reference: first))
    let secondLease = try store.acquire(reference: second)
    host.clearImageFile()
    XCTAssertNil(host.model.state.inputReference)
    XCTAssertThrowsError(try store.acquire(reference: second))
    secondLease.release()
    XCTAssertFalse(FileManager.default.fileExists(atPath: secondLease.url.path))
    host.close()
    XCTAssertEqual(try Data(contentsOf: lease.url), Data([1, 2, 3]))
    lease.release()
    XCTAssertFalse(FileManager.default.fileExists(atPath: lease.url.path))
    XCTAssertEqual(try Data(contentsOf: source), Data([1, 2, 3]))
  }

  func testLatestPickerReplyIsConsumedOnceIncludingCancellation() throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = try makeSource(root)
    var replies = [(URL?) -> Void]()
    let host = MultimodalHost(
      store: try MultimodalInputStore(directory: root.appendingPathComponent("copies")),
      picker: { replies.append($0) })
    host.selectImageFile()
    host.selectImageFile()
    replies[0](source)
    XCTAssertNil(host.model.state.inputReference)
    replies[1](source)
    let reference = host.model.state.inputReference
    replies[1](source)
    XCTAssertEqual(host.model.state.inputReference, reference)
    host.selectImageFile()
    replies[2](nil)
    replies[2](source)
    XCTAssertEqual(host.model.state.inputReference, reference)
    host.close()
  }

  func testInvalidSourceAndLaunchFailurePreserveSelectionWithTypedFailure() throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = try makeSource(root)
    var reply: ((URL?) -> Void)?
    var shouldThrow = false
    let host = MultimodalHost(
      store: try MultimodalInputStore(directory: root.appendingPathComponent("copies")),
      picker: { callback in
        if shouldThrow { throw NSError(domain: "private-details", code: 1) }
        reply = callback
      })
    host.selectImageFile()
    reply?(source)
    let selected = host.model.state.inputReference
    host.selectImageFile()
    reply?(root)
    XCTAssertEqual(host.model.state.inputReference, selected)
    XCTAssertEqual(host.model.state.failure?.code, .invalidInput)
    shouldThrow = true
    host.selectImageFile()
    XCTAssertEqual(host.model.state.inputReference, selected)
    XCTAssertEqual(host.model.state.failure?.code, .invalidInput)
    host.close()
  }

  func testClearLeaveBackgroundCloseInvalidateLateReplies() throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = try makeSource(root)
    let actions: [(MultimodalHost) -> Void] = [
      { $0.clearImageFile() }, { $0.leavePage() }, { $0.background() }, { $0.close() },
    ]
    for action in actions {
      var reply: ((URL?) -> Void)?
      var launches = 0
      let directory = root.appendingPathComponent(UUID().uuidString)
      let host = MultimodalHost(
        store: try MultimodalInputStore(directory: directory),
        picker: {
          launches += 1
          reply = $0
        })
      host.selectImageFile()
      action(host)
      reply?(source)
      XCTAssertNil(host.model.state.inputReference)
      if !host.model.state.closed {
        XCTAssertTrue(try FileManager.default.contentsOfDirectory(atPath: directory.path).isEmpty)
      }
      host.background()
      host.selectImageFile()
      XCTAssertEqual(launches, 1)
      host.close()
    }
  }

  func testPreservedExternalPickerBeforeForegroundNeverGenerates() throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let store = try MultimodalInputStore(directory: root.appendingPathComponent("copies"))
    let bridge = HostDrainingBridge(store: store)
    var reply: ((URL?) -> Void)?
    let host = MultimodalHost(store: store, bridge: bridge, picker: { reply = $0 })
    let source = try makeSource(root)
    host.selectImageFile()
    host.background(preservePicker: true)
    reply?(source)
    XCTAssertNotNil(host.model.state.inputReference)
    host.onForeground()
    XCTAssertEqual(bridge.starts, 0)
    host.selectImageFile()
    host.background(preservePicker: true)
    host.leavePage()
    let selected = host.model.state.inputReference
    reply?(source)
    XCTAssertEqual(host.model.state.inputReference, selected)
    host.close()
  }

  func testActiveCancelAndCloseWaitForDrainAndCloseOnlyOnce() throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let store = try MultimodalInputStore(directory: root.appendingPathComponent("copies"))
    let bridge = HostDrainingBridge(store: store)
    var reply: ((URL?) -> Void)?
    let host = MultimodalHost(store: store, bridge: bridge, picker: { reply = $0 })
    host.selectImageFile()
    reply?(try makeSource(root))
    host.model.generate()
    let lease = try XCTUnwrap(bridge.lease)
    XCTAssertEqual(host.model.state.phase, .running)
    host.background()
    host.onForeground()
    XCTAssertEqual(bridge.starts, 1)
    XCTAssertEqual(host.model.state.phase, .cancelling)
    host.clearImageFile()
    host.close()
    host.close()
    XCTAssertEqual(bridge.closes, 1)
    XCTAssertTrue(host.model.state.closing)
    XCTAssertTrue(FileManager.default.fileExists(atPath: lease.url.path))
    XCTAssertThrowsError(try store.acquire(reference: try XCTUnwrap(bridge.reference)))
    bridge.finishDrain()
    XCTAssertFalse(FileManager.default.fileExists(atPath: lease.url.path))
    XCTAssertFalse(host.model.state.closing)
    host.model.generate()
    XCTAssertEqual(host.model.state.failure?.code, .closed)
    XCTAssertEqual(bridge.starts, 1)
  }

  func testDefaultHostIsExplicitlyUnavailable() throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    var reply: ((URL?) -> Void)?
    let host = MultimodalHost(
      store: try MultimodalInputStore(directory: root.appendingPathComponent("copies")),
      picker: { reply = $0 })
    host.selectImageFile()
    reply?(try makeSource(root))
    host.model.refreshBackends()
    host.model.generate()
    XCTAssertEqual(host.model.state.queryFailure?.code, .backendUnavailable)
    XCTAssertEqual(host.model.state.failure?.code, .backendUnavailable)
    XCTAssertNil(host.model.state.output)
    host.close()
  }

  private func makeRoot() throws -> URL {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
    return root
  }
  private func makeSource(_ root: URL) throws -> URL {
    let source = root.appendingPathComponent("synthetic.png")
    try Data([1, 2, 3]).write(to: source)
    return source
  }
}

@MainActor private final class HostDrainingBridge: NSObject, @preconcurrency MultimodalBridge {
  let store: MultimodalInputStore
  var starts = 0
  var closes = 0
  var reference: String?
  var lease: (any IOSMultimodalInputLease)?
  private var generation: (any MultimodalBridgeGenerateCallback)?
  private var closing: (any MultimodalBridgeCloseCallback)?
  init(store: MultimodalInputStore) { self.store = store }
  func queryMultimodalBackends(callback: any MultimodalBridgeBackendsCallback) -> any BridgeTask {
    callback.onMultimodalBackends(backends: [])
    return HostTask()
  }
  func generateMultimodal(
    request: MultimodalBridgeRequest, selection: any MultimodalBridgeSelection,
    callback: any MultimodalBridgeGenerateCallback
  ) -> any BridgeTask {
    starts += 1
    reference = request.inputReference
    lease = try! store.acquire(reference: request.inputReference)
    generation = callback
    return HostTask()
  }
  func closeMultimodal(callback: any MultimodalBridgeCloseCallback) {
    closes += 1
    if generation == nil { callback.onMultimodalClosed() } else { closing = callback }
  }
  func finishDrain() {
    lease?.release()
    generation?.onMultimodalCancelled()
    generation = nil
    closing?.onMultimodalClosed()
    closing = nil
  }
}
private final class HostTask: NSObject, BridgeTask { func cancel() {} }
