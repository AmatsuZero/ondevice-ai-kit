import Foundation
import XCTest

@MainActor final class MultimodalInputStoreTests: XCTestCase {
  func testBoundedCopyRejectsEmptyOversizedAndCancelledWithoutPartialFiles() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = root.appendingPathComponent("bounded.bin")
    let directory = root.appendingPathComponent("bounded")
    let store = try MultimodalInputStore(directory: directory, maxBytes: 4)
    try Data().write(to: source)
    XCTAssertThrowsError(try store.importImage(from: source))
    try Data([1, 2, 3, 4, 5]).write(to: source)
    XCTAssertThrowsError(try store.importImage(from: source))
    try Data([1, 2, 3, 4]).write(to: source)
    XCTAssertThrowsError(
      try store.importImage(from: source, checkpoint: { throw CancellationError() }))
    XCTAssertTrue(try FileManager.default.contentsOfDirectory(atPath: directory.path).isEmpty)
    XCTAssertFalse(try store.importImage(from: source).isEmpty)
    store.close()
  }

  func testPrivateCopyIsIndependentOfSourceAndReferenceIsOpaque() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = root.appendingPathComponent("synthetic.png")
    try Data([1, 2, 3]).write(to: source)
    let store = try MultimodalInputStore(directory: root.appendingPathComponent("copies"))
    let token = try store.importImage(from: source)
    XCTAssertNotEqual(token, source.path)
    XCTAssertFalse(token.contains("/"))
    let lease = try store.acquire(reference: token)
    XCTAssertNotEqual(lease.url, source)
    try Data([9]).write(to: source)
    XCTAssertEqual(try Data(contentsOf: lease.url), Data([1, 2, 3]))
    lease.release()
    XCTAssertTrue(FileManager.default.fileExists(atPath: lease.url.path))
    store.remove(reference: token)
    XCTAssertFalse(FileManager.default.fileExists(atPath: lease.url.path))
    XCTAssertEqual(try Data(contentsOf: source), Data([9]))
    store.close()
  }

  func testRetirementAndCloseWaitForEveryIdempotentLease() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = root.appendingPathComponent("synthetic.jpg")
    try Data([4, 5, 6]).write(to: source)
    let directory = root.appendingPathComponent("copies")
    let store = try MultimodalInputStore(directory: directory)
    let token = try store.importImage(from: source)
    let first = try store.acquire(reference: token)
    let second = try store.acquire(reference: token)
    store.remove(reference: token)
    XCTAssertThrowsError(try store.acquire(reference: token))
    store.close()
    store.close()
    XCTAssertThrowsError(try store.importImage(from: source))
    XCTAssertTrue(FileManager.default.fileExists(atPath: second.url.path))
    first.release()
    first.release()
    XCTAssertEqual(try Data(contentsOf: second.url), Data([4, 5, 6]))
    second.release()
    second.release()
    XCTAssertFalse(FileManager.default.fileExists(atPath: directory.path))
    XCTAssertEqual(try Data(contentsOf: source), Data([4, 5, 6]))
  }

  func testInvalidSourcesAndUnknownReferencesDoNotTouchForeignFiles() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = root.appendingPathComponent("foreign.png")
    try Data([7, 8]).write(to: source)
    let link = root.appendingPathComponent("link.png")
    try FileManager.default.createSymbolicLink(at: link, withDestinationURL: source)
    let store = try MultimodalInputStore(directory: root.appendingPathComponent("copies"))
    XCTAssertThrowsError(try store.importImage(from: root))
    XCTAssertThrowsError(try store.importImage(from: link))
    XCTAssertThrowsError(
      try store.importImage(from: URL(string: "https://example.invalid/image.png")!))
    XCTAssertThrowsError(try store.acquire(reference: source.path))
    XCTAssertThrowsError(try store.acquire(reference: "../foreign.png"))
    store.remove(reference: source.path)
    store.remove(reference: "../foreign.png")
    store.close()
    XCTAssertEqual(try Data(contentsOf: source), Data([7, 8]))
  }

  func testFreshDirectoryOwnershipNeverAdoptsOrDeletesForeignSentinel() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let sentinel = root.appendingPathComponent("keep")
    try Data([8, 9]).write(to: sentinel)
    XCTAssertThrowsError(try MultimodalInputStore(directory: root))
    XCTAssertEqual(try Data(contentsOf: sentinel), Data([8, 9]))
    let directory = root.appendingPathComponent("copies")
    let store = try MultimodalInputStore(directory: directory)
    let foreign = directory.appendingPathComponent("unowned")
    try Data([10]).write(to: foreign)
    store.close()
    store.close()
    XCTAssertEqual(try Data(contentsOf: foreign), Data([10]))
  }

  func testCloseBeforeCopyKeepsInflightDirectoryAndScopeUntilWorkerStops() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = root.appendingPathComponent("source")
    try Data([1, 2, 3]).write(to: source)
    let directory = root.appendingPathComponent("copies")
    let entered = expectation(description: "复制已进入后台")
    let resume = DispatchSemaphore(value: 0)
    defer { resume.signal() }
    var starts = 0
    var stops = 0
    let store = try MultimodalInputStore(
      directory: directory,
      checkpoint: { phase in
        if phase == .beforeSourceOpen {
          XCTAssertFalse(Thread.isMainThread)
          entered.fulfill()
          XCTAssertEqual(resume.wait(timeout: .now() + 5), .success)
        }
      },
      startAccess: { _ in
        starts += 1
        return true
      },
      stopAccess: { _ in stops += 1 })
    let existing = try store.importImage(from: source)
    let lease = try store.acquire(reference: existing)
    let operation = try store.startImport(from: source)
    XCTAssertEqual(starts, 2)
    await fulfillment(of: [entered], timeout: 2)
    let concurrentLease = try store.acquire(reference: existing)
    XCTAssertEqual(try Data(contentsOf: concurrentLease.url), Data([1, 2, 3]))
    concurrentLease.release()
    store.close()
    store.close()
    lease.release()
    XCTAssertTrue(FileManager.default.fileExists(atPath: directory.path))
    XCTAssertEqual(stops, 1)
    XCTAssertThrowsError(try store.startImport(from: source))
    resume.signal()
    do {
      _ = try await operation.value
      XCTFail("关闭后不得发布导入结果")
    } catch { XCTAssertTrue(error is CancellationError) }
    XCTAssertEqual(stops, 2)
    XCTAssertFalse(FileManager.default.fileExists(atPath: directory.path))
    XCTAssertEqual(try Data(contentsOf: source), Data([1, 2, 3]))
  }

  func testAsyncCancellationAfterWrittenChunkReclaimsPartialAndBalancesScope() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = root.appendingPathComponent("source")
    let bytes = Data(repeating: 7, count: 128 * 1024)
    try bytes.write(to: source)
    let directory = root.appendingPathComponent("copies")
    let entered = expectation(description: "首块写入后暂停")
    let resume = DispatchSemaphore(value: 0)
    defer { resume.signal() }
    var stops = 0
    let store = try MultimodalInputStore(
      directory: directory,
      checkpoint: { phase in
        if phase == .chunkWritten {
          entered.fulfill()
          XCTAssertEqual(resume.wait(timeout: .now() + 5), .success)
        }
      }, startAccess: { _ in true }, stopAccess: { _ in stops += 1 })
    let operation = try store.startImport(from: source)
    await fulfillment(of: [entered], timeout: 2)
    XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: directory.path).count, 1)
    operation.cancel()
    XCTAssertEqual(stops, 0)
    resume.signal()
    do {
      _ = try await operation.value
      XCTFail("取消导入必须失败")
    } catch { XCTAssertTrue(error is CancellationError) }
    XCTAssertEqual(stops, 1)
    XCTAssertTrue(try FileManager.default.contentsOfDirectory(atPath: directory.path).isEmpty)
    XCTAssertEqual(try Data(contentsOf: source), bytes)
    store.close()
  }

  func testAsyncSuccessAndFailureBalanceOnlyAcquiredScopes() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = root.appendingPathComponent("source")
    let directory = root.appendingPathComponent("copies")
    var stops = 0
    var scoped = true
    let store = try MultimodalInputStore(
      directory: directory, maxBytes: 4,
      startAccess: { _ in scoped }, stopAccess: { _ in stops += 1 })
    try Data([1, 2, 3]).write(to: source)
    let successful = try store.startImport(from: source)
    let token = try await successful.value
    let lease = try store.acquire(reference: token)
    XCTAssertEqual(try Data(contentsOf: lease.url), Data([1, 2, 3]))
    lease.release()
    store.remove(reference: token)
    XCTAssertEqual(stops, 1)
    for bytes in [Data(), Data(repeating: 1, count: 5)] {
      try bytes.write(to: source)
      let failed = try store.startImport(from: source)
      do {
        _ = try await failed.value
        XCTFail("无效输入必须失败")
      } catch {}
    }
    XCTAssertEqual(stops, 3)
    scoped = false
    try Data([4]).write(to: source)
    let unscoped = try store.startImport(from: source)
    _ = try await unscoped.value
    XCTAssertEqual(stops, 3)
    store.close()
    XCTAssertFalse(FileManager.default.fileExists(atPath: directory.path))
  }

  func testAsyncSourcesStillRejectSymlinksDirectoriesAndOversizedCopies() async throws {
    let root = try makeRoot()
    defer { try? FileManager.default.removeItem(at: root) }
    let source = root.appendingPathComponent("source")
    try Data([1, 2, 3]).write(to: source)
    let link = root.appendingPathComponent("link")
    try FileManager.default.createSymbolicLink(at: link, withDestinationURL: source)
    let directory = root.appendingPathComponent("copies")
    let store = try MultimodalInputStore(directory: directory, maxBytes: 2)
    for invalid in [source, link, root] {
      let operation = try store.startImport(from: invalid)
      do {
        _ = try await operation.value
        XCTFail("异步复制不得接受超限文件、符号链接或目录")
      } catch { XCTAssertFalse(error is CancellationError) }
      XCTAssertTrue(try FileManager.default.contentsOfDirectory(atPath: directory.path).isEmpty)
    }
    XCTAssertEqual(try Data(contentsOf: source), Data([1, 2, 3]))
    store.close()
  }

  private func makeRoot() throws -> URL {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
    return root
  }
}
