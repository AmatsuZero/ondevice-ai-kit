import Foundation

public enum IdentityStopReason: Sendable, Equatable {
    case cancelled
    case authorizationRevoked
    case closed

    fileprivate var identityError: IdentityErrorCode {
        switch self {
        case .cancelled: return .cancelled
        case .authorizationRevoked: return .authorizationRevoked
        case .closed: return .closed
        }
    }
}

/// Cooperative stop boundary for one backend invocation. It requests the native
/// stop callback when available; it never claims that a synchronous primitive was
/// interrupted before it returns.
public final class IdentityStopSignal: @unchecked Sendable {
    private let lock = NSLock()
    private var reason: IdentityStopReason?
    private var handlers: [UUID: @Sendable () -> Void] = [:]

    public init() {}

    public func check() throws {
        lock.lock(); let stopped = reason; lock.unlock()
        if let stopped { throw IdentityError(stopped.identityError) }
    }

    @discardableResult
    public func registerStopHandler(_ handler: @escaping @Sendable () -> Void) -> IdentityStopRegistration {
        let id = UUID()
        lock.lock()
        if reason != nil {
            lock.unlock()
            Self.deliver(handler)
            return IdentityStopRegistration(signal: nil, id: id)
        }
        handlers[id] = handler
        lock.unlock()
        return IdentityStopRegistration(signal: self, id: id)
    }

    public func requestStop(_ requested: IdentityStopReason) {
        prepareStopDelivery(requested)?()
    }

    /// Commits the stop reason now, while letting an actor deliver native
    /// callbacks after leaving its isolation boundary.
    internal func prepareStopDelivery(_ requested: IdentityStopReason) -> (@Sendable () -> Void)? {
        lock.lock()
        guard reason == nil else { lock.unlock(); return nil }
        reason = requested
        let pending = Array(handlers.values)
        handlers.removeAll(keepingCapacity: false)
        lock.unlock()
        guard !pending.isEmpty else { return nil }
        return { for handler in pending { Self.deliver(handler) } }
    }

    private static func deliver(_ handler: @Sendable () -> Void) {
        let frame = IdentityStopHandlerFrame()
        IdentityStopHandlerContext.$current.withValue(frame) {
            defer { frame.finish() }
            handler()
        }
    }

    fileprivate func removeHandler(_ id: UUID) {
        lock.lock(); handlers.removeValue(forKey: id); lock.unlock()
    }
}

/// A handler's synchronous call frame. Child Tasks inherit the object, but the
/// frame is deactivated as soon as the handler returns, so escaped work cannot
/// bypass a later writer-drain wait.
internal final class IdentityStopHandlerFrame: @unchecked Sendable {
    private let lock = NSLock()
    private var active = true

    var isActive: Bool {
        lock.lock(); defer { lock.unlock() }
        return active
    }

    func finish() {
        lock.lock(); active = false; lock.unlock()
    }
}

internal enum IdentityStopHandlerContext {
    @TaskLocal static var current: IdentityStopHandlerFrame?
}

public final class IdentityStopRegistration: @unchecked Sendable {
    private weak var signal: IdentityStopSignal?
    private let id: UUID
    private let lock = NSLock()
    private var closed = false

    fileprivate init(signal: IdentityStopSignal?, id: UUID) { self.signal = signal; self.id = id }

    public func close() {
        lock.lock()
        guard !closed else { lock.unlock(); return }
        closed = true
        let signal = self.signal
        self.signal = nil
        lock.unlock()
        signal?.removeHandler(id)
    }

}

public extension IdentityBackend {
    func embed(_ probe: IdentityProbe, stopSignal: IdentityStopSignal) throws -> IdentityEmbedding {
        try stopSignal.check()
        do {
            let result = try embed(probe)
            try stopSignal.check()
            return result
        } catch {
            try stopSignal.check()
            throw error
        }
    }

    func evaluateQuality(_ probe: IdentityProbe, stopSignal: IdentityStopSignal) throws -> IdentityQuality {
        try stopSignal.check()
        do {
            let result = try evaluateQuality(probe)
            try stopSignal.check()
            return result
        } catch {
            try stopSignal.check()
            throw error
        }
    }
}
