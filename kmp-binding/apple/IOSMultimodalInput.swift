import Foundation

/// A host-owned media lease. Release is synchronous and idempotent on MainActor.
/// Image and video providers are explicitly injected separately.
@MainActor
public protocol IOSMultimodalInputLease: AnyObject {
  var url: URL { get }
  func release()
}

@MainActor
public protocol IOSMultimodalInputProvider: AnyObject {
  func acquire(reference: String) throws -> any IOSMultimodalInputLease
}
