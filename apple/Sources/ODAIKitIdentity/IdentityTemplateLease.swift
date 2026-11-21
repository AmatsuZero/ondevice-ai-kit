import Foundation

/// Internal lease over a decrypted template vector. The vector is never exposed
/// through a public identity result and becomes inaccessible after close.
final class IdentityTemplateLease: @unchecked Sendable {
    private let lock = NSLock()
    private var value: IdentityEmbedding?
    private var release: (@Sendable () async -> Void)?

    init(value: IdentityEmbedding, release: @escaping @Sendable () async -> Void) {
        self.value = value
        self.release = release
    }

    func embedding() throws -> IdentityEmbedding {
        lock.lock(); defer { lock.unlock() }
        guard let value else { throw IdentityError(.closed) }
        return value
    }

    func close() async {
        guard let release = takeRelease() else { return }
        await release()
    }

    private func takeRelease() -> (@Sendable () async -> Void)? {
        lock.lock(); defer { lock.unlock() }
        let callback = release
        release = nil; value = nil
        return callback
    }
}
