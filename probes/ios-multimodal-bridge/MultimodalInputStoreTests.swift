import Foundation
import XCTest

@MainActor final class MultimodalInputStoreTests: XCTestCase {
  func testPrivateCopyIsIndependentOfSourceAndReferenceIsOpaque() throws {
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

  func testRetirementAndCloseWaitForEveryIdempotentLease() throws {
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

  func testInvalidSourcesAndUnknownReferencesDoNotTouchForeignFiles() throws {
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

  func testFreshDirectoryOwnershipNeverAdoptsOrDeletesForeignSentinel() throws {
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

  private func makeRoot() throws -> URL {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
    return root
  }
}
