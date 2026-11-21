import Foundation

/// Native files remain in the host; an acquired lease outlives input replacement.
@MainActor
public protocol IOSVideoInputLease: AnyObject {
    var url: URL { get }
    func release()
}

@MainActor
public protocol IOSVideoInputProvider: AnyObject {
    func acquire(reference: String) throws -> any IOSVideoInputLease
}
