import Foundation
import ODAIKitDemo
import XCTest

@MainActor final class MultimodalHostTests: XCTestCase {
  func testVideoStoresAreIsolatedAndCrossMediaPickerIsLatestOnly() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = try makeSource(root)
    let images = try MultimodalInputStore(directory: root.appendingPathComponent("images"))
    let videos = try MultimodalInputStore(directory: root.appendingPathComponent("videos"))
    var imageReply: ((URL?) -> Void)?
    var videoReply: ((URL?) -> Void)?
    let host = MultimodalHost(
      store: images, videoStore: videos,
      videoPicker: { videoReply = $0 }, picker: { imageReply = $0 })
    host.selectImageFile()
    host.selectVideoFile()
    imageReply?(source)
    XCTAssertNil(host.model.state.inputReference)
    videoReply?(source)
    await host.inputTask?.value
    let token = try XCTUnwrap(host.model.state.inputReference)
    XCTAssertEqual(host.model.state.inputKind, .video)
    XCTAssertThrowsError(try images.acquire(reference: token))
    let lease = try videos.acquire(reference: token)
    host.selectImageFile()
    imageReply?(source)
    await host.inputTask?.value
    XCTAssertThrowsError(try videos.acquire(reference: token))
    XCTAssertEqual(try Data(contentsOf: lease.url), Data([1, 2, 3]))
    host.close()
    XCTAssertTrue(FileManager.default.fileExists(atPath: lease.url.path))
    lease.release()
    XCTAssertFalse(FileManager.default.fileExists(atPath: lease.url.path))
  }

  func testReplacementAndClearRetireOnlySelectionOwnership() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = try makeSource(root)
    let store = try MultimodalInputStore(directory: root.appendingPathComponent("copies"))
    var reply: ((URL?) -> Void)?
    let host = MultimodalHost(store: store, picker: { reply = $0 })
    host.selectImageFile()
    reply?(source)
    await host.inputTask?.value
    let first = try XCTUnwrap(host.model.state.inputReference)
    XCTAssertEqual(host.model.state.inputDisplayName, source.lastPathComponent)
    let lease = try store.acquire(reference: first)
    host.selectImageFile()
    reply?(source)
    await host.inputTask?.value
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

  func testLatestPickerReplyIsConsumedOnceIncludingCancellation() async throws {
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
    await host.inputTask?.value
    let reference = host.model.state.inputReference
    replies[1](source)
    XCTAssertEqual(host.model.state.inputReference, reference)
    host.selectImageFile()
    replies[2](nil)
    replies[2](source)
    XCTAssertEqual(host.model.state.inputReference, reference)
    host.close()
  }

  func testInvalidSourceAndLaunchFailurePreserveSelectionWithTypedFailure() async throws {
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
    await host.inputTask?.value
    let selected = host.model.state.inputReference
    host.selectImageFile()
    reply?(root)
    await host.inputTask?.value
    XCTAssertEqual(host.model.state.inputReference, selected)
    XCTAssertEqual(host.model.state.failure?.code, .invalidInput)
    shouldThrow = true
    host.selectImageFile()
    XCTAssertEqual(host.model.state.inputReference, selected)
    XCTAssertEqual(host.model.state.failure?.code, .invalidInput)
    host.close()
  }

  func testClearLeaveBackgroundCloseInvalidateLateReplies() async throws {
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

  func testPreservedExternalPickerBeforeForegroundNeverGenerates() async throws {
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
    await host.inputTask?.value
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

  func testPreservedPickerImportSurvivesBackgroundBeforeAndAfterReplyWithoutGenerating()
    async throws
  {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = try makeSource(root)
    for backgroundBeforeReply in [false, true] {
      let entered = expectation(description: "保留的导入已进入后台 worker")
      let resume = DispatchSemaphore(value: 0)
      defer { resume.signal() }
      let store = try MultimodalInputStore(
        directory: root.appendingPathComponent(UUID().uuidString),
        checkpoint: { phase in
          if phase == .beforeSourceOpen {
            XCTAssertFalse(Thread.isMainThread)
            entered.fulfill()
            XCTAssertEqual(resume.wait(timeout: .now() + 5), .success)
          }
        })
      let bridge = HostDrainingBridge(store: store)
      var reply: ((URL?) -> Void)?
      let host = MultimodalHost(store: store, bridge: bridge, picker: { reply = $0 })
      host.selectImageFile()
      if backgroundBeforeReply { host.background(preservePicker: true) }
      reply?(source)
      let delivery = try XCTUnwrap(host.inputTask)
      await fulfillment(of: [entered], timeout: 2)
      host.background(preservePicker: true)
      XCTAssertNotNil(host.inputTask)
      XCTAssertNil(host.model.state.inputReference)
      resume.signal()
      await delivery.value
      let token = try XCTUnwrap(host.model.state.inputReference)
      let lease = try store.acquire(reference: token)
      XCTAssertEqual(try Data(contentsOf: lease.url), Data([1, 2, 3]))
      lease.release()
      XCTAssertNil(host.model.state.failure)
      XCTAssertEqual(bridge.starts, 0)
      host.onForeground()
      XCTAssertEqual(bridge.starts, 0)
      host.close()
    }
  }

  func testActiveCancelAndCloseWaitForDrainAndCloseOnlyOnce() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let store = try MultimodalInputStore(directory: root.appendingPathComponent("copies"))
    let bridge = HostDrainingBridge(store: store)
    var reply: ((URL?) -> Void)?
    let host = MultimodalHost(store: store, bridge: bridge, picker: { reply = $0 })
    host.selectImageFile()
    reply?(try makeSource(root))
    await host.inputTask?.value
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

  func testDefaultHostIsExplicitlyUnavailable() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    var reply: ((URL?) -> Void)?
    let host = MultimodalHost(
      store: try MultimodalInputStore(directory: root.appendingPathComponent("copies")),
      picker: { reply = $0 })
    host.selectImageFile()
    reply?(try makeSource(root))
    await host.inputTask?.value
    host.model.refreshBackends()
    host.model.generate()
    XCTAssertEqual(host.model.state.queryFailure?.code, .backendUnavailable)
    XCTAssertEqual(host.model.state.failure?.code, .backendUnavailable)
    XCTAssertNil(host.model.state.output)
    host.close()
  }

  func testAsyncImportReturnsBeforeCopyAndClearCancelsWithoutFailure() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = try makeSource(root)
    let directory = root.appendingPathComponent("copies")
    let entered = expectation(description: "Host 复制在后台暂停")
    let resume = DispatchSemaphore(value: 0)
    defer { resume.signal() }
    let store = try MultimodalInputStore(
      directory: directory,
      checkpoint: { phase in
        if phase == .beforeSourceOpen {
          entered.fulfill()
          XCTAssertEqual(resume.wait(timeout: .now() + 5), .success)
        }
      })
    var reply: ((URL?) -> Void)?
    let host = MultimodalHost(store: store, picker: { reply = $0 })
    host.selectImageFile()
    reply?(source)
    XCTAssertNil(host.model.state.inputReference)
    await fulfillment(of: [entered], timeout: 2)
    let delivery = try XCTUnwrap(host.inputTask)
    host.clearImageFile()
    resume.signal()
    await delivery.value
    XCTAssertNil(host.model.state.inputReference)
    XCTAssertNil(host.model.state.failure)
    XCTAssertTrue(try FileManager.default.contentsOfDirectory(atPath: directory.path).isEmpty)
    host.close()
  }

  func testLateSuccessfulImportIsRetiredAfterClearLeaveBackgroundCloseAndOwnerRelease() async throws
  {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = try makeSource(root)
    let actions: [(MultimodalHost) -> Void] = [
      { $0.clearImageFile() }, { $0.leavePage() }, { $0.background() }, { $0.close() },
    ]
    for action in actions {
      let directory = root.appendingPathComponent(UUID().uuidString)
      let store = try MultimodalInputStore(directory: directory)
      let token = try store.importImage(from: source)
      let gate = HostImportGate()
      var reply: ((URL?) -> Void)?
      let host = MultimodalHost(
        store: store,
        importInput: { _, _ in
          Task {
            await gate.wait()
            return token
          }
        },
        picker: { reply = $0 })
      host.selectImageFile()
      reply?(source)
      await gate.waitForEntry()
      let delivery = try XCTUnwrap(host.inputTask)
      action(host)
      gate.open()
      await delivery.value
      XCTAssertNil(host.model.state.inputReference)
      XCTAssertNil(host.model.state.failure)
      XCTAssertThrowsError(try store.acquire(reference: token))
      if !host.model.state.closed {
        XCTAssertTrue(try FileManager.default.contentsOfDirectory(atPath: directory.path).isEmpty)
      }
      host.close()
    }
    let store = try MultimodalInputStore(directory: root.appendingPathComponent("released"))
    let token = try store.importImage(from: source)
    let gate = HostImportGate()
    var reply: ((URL?) -> Void)?
    var host: MultimodalHost? = MultimodalHost(
      store: store,
      importInput: { _, _ in
        Task {
          await gate.wait()
          return token
        }
      },
      picker: { reply = $0 })
    weak var weakHost = host
    host?.selectImageFile()
    reply?(source)
    await gate.waitForEntry()
    let delivery = try XCTUnwrap(host?.inputTask)
    host = nil
    XCTAssertNil(weakHost)
    gate.open()
    await delivery.value
    XCTAssertThrowsError(try store.acquire(reference: token))
    store.close()
  }

  func testOlderCrossMediaImportCannotPublishOrClearNewImportSlot() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = try makeSource(root)
    let images = try MultimodalInputStore(directory: root.appendingPathComponent("images"))
    let videos = try MultimodalInputStore(directory: root.appendingPathComponent("videos"))
    let old = try images.importImage(from: source)
    let current = try videos.importImage(from: source)
    let oldGate = HostImportGate()
    let newGate = HostImportGate()
    var imageReply: ((URL?) -> Void)?
    var videoReply: ((URL?) -> Void)?
    let host = MultimodalHost(
      store: images, videoStore: videos, videoPicker: { videoReply = $0 },
      importInput: { target, _ in
        Task {
          if target === images {
            await oldGate.wait()
            return old
          }
          await newGate.wait()
          return current
        }
      }, picker: { imageReply = $0 })
    host.selectImageFile()
    imageReply?(source)
    await oldGate.waitForEntry()
    let oldDelivery = try XCTUnwrap(host.inputTask)
    host.selectVideoFile()
    videoReply?(source)
    await newGate.waitForEntry()
    let newDelivery = try XCTUnwrap(host.inputTask)
    oldGate.open()
    await oldDelivery.value
    XCTAssertNil(host.model.state.inputReference)
    XCTAssertThrowsError(try images.acquire(reference: old))
    XCTAssertNotNil(host.inputTask)
    newGate.open()
    await newDelivery.value
    XCTAssertEqual(host.model.state.inputReference, current)
    XCTAssertEqual(host.model.state.inputKind, .video)
    XCTAssertNil(host.model.state.failure)
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

@MainActor private final class HostImportGate {
  private var entered = false
  private var continuation: CheckedContinuation<Void, Never>?
  private var observers: [CheckedContinuation<Void, Never>] = []
  func wait() async {
    entered = true
    let waiting = observers
    observers.removeAll()
    for observer in waiting { observer.resume() }
    await withCheckedContinuation { continuation = $0 }
  }
  func waitForEntry() async {
    if !entered { await withCheckedContinuation { observers.append($0) } }
  }
  func open() {
    continuation?.resume()
    continuation = nil
  }
}
