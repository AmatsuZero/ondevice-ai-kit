import Foundation
/// Keep this lease alive while the backend reads any model file; close after backend completion.
public final class ModelLease: @unchecked Sendable {
    public let directory: URL
    private let store: ModelStore
    private let ref: ModelRef
    private let token: UUID
    init(directory: URL, store: ModelStore, ref: ModelRef, token: UUID) { self.directory = directory; self.store = store; self.ref = ref; self.token = token }
    public func close() async { await store.release(ref, token: token) }
    deinit { let store = store, ref = ref, token = token; Task { await store.release(ref, token: token) } }
}
