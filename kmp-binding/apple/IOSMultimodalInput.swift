import Foundation

/// A host-owned image lease. Release is synchronous and idempotent on MainActor.
@MainActor
public protocol IOSMultimodalInputLease: AnyObject {
  var url: URL { get }
  func release()
}

@MainActor
public protocol IOSMultimodalInputProvider: AnyObject {
  func acquire(reference: String) throws -> any IOSMultimodalInputLease
}
