import Foundation
import XCTest

@MainActor final class MultimodalHostPreparationTests: XCTestCase {
  func testFailureAllowsExplicitRetryButSuccessfulPreparationStaysOnceOnly() async throws {
    let host = try MultimodalHost(store: MultimodalInputStore(), picker: { $0(nil) })
    defer { host.close() }
    let owner = MultimodalHostPreparation()
    var attempts = 0
    var failures = 0
    var received: MultimodalHost?
    owner.start(
      make: {
        attempts += 1
        throw PreparationError.injected
      },
      ready: { value in
        XCTAssertNil(value)
        failures += 1
      })
    for _ in 0..<1000 where failures == 0 { await Task.yield() }
    XCTAssertEqual(failures, 1)

    owner.start(
      make: {
        attempts += 1
        return host
      }, ready: { received = $0 })
    for _ in 0..<1000 where received == nil { await Task.yield() }
    XCTAssertTrue(received === host)
    XCTAssertEqual(attempts, 2)
    owner.start(
      make: {
        XCTFail("successful preparation must not start again")
        return host
      }, ready: { _ in XCTFail("must publish once") })
    for _ in 0..<10 { await Task.yield() }
  }

  func testOwnerReleasedDuringPreparationClosesLateHostWithoutPublishing() async throws {
    let host = try MultimodalHost(store: MultimodalInputStore(), picker: { $0(nil) })
    let gate = PreparationGate()
    var published = 0
    var owner: MultimodalHostPreparation? = MultimodalHostPreparation()
    owner?.start(
      make: {
        await gate.wait()
        return host
      }, ready: { _ in published += 1 })
    await gate.started()
    owner = nil
    gate.release()
    for _ in 0..<1000 {
      if host.model.state.closed { break }
      await Task.yield()
    }
    XCTAssertTrue(host.model.state.closed)
    XCTAssertEqual(published, 0)
  }

  func testStartsOnceAndTransfersSuccessfulHost() async throws {
    let host = try MultimodalHost(store: MultimodalInputStore(), picker: { $0(nil) })
    let owner = MultimodalHostPreparation()
    var starts = 0
    var received: MultimodalHost?
    owner.start(
      make: {
        starts += 1
        return host
      }, ready: { received = $0 })
    owner.start(
      make: {
        starts += 1
        return host
      }, ready: { _ in XCTFail("duplicate") })
    for _ in 0..<1000 {
      if received != nil { break }
      await Task.yield()
    }
    XCTAssertEqual(starts, 1)
    XCTAssertTrue(received === host)
    XCTAssertFalse(host.model.state.closed)
    host.close()
  }

  func testFailurePublishesNilAndCancellationPublishesNothing() async {
    let owner = MultimodalHostPreparation()
    var failures = 0
    owner.start(
      make: { throw PreparationError.injected },
      ready: {
        XCTAssertNil($0)
        failures += 1
      })
    for _ in 0..<1000 {
      if failures != 0 { break }
      await Task.yield()
    }
    XCTAssertEqual(failures, 1)
    let cancelled = MultimodalHostPreparation()
    cancelled.start(make: { throw CancellationError() }, ready: { _ in XCTFail("cancelled") })
    for _ in 0..<10 { await Task.yield() }
  }
}

@MainActor private final class PreparationGate {
  private var continuation: CheckedContinuation<Void, Never>?
  func wait() async { await withCheckedContinuation { continuation = $0 } }
  func started() async { while continuation == nil { await Task.yield() } }
  func release() {
    continuation?.resume()
    continuation = nil
  }
}
private enum PreparationError: Error { case injected }
