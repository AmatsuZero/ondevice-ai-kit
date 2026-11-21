import Foundation
/// A terminal event and completion of backend resource use are separate transitions.
public actor TaskGate {
    private var active: UUID?
    private var terminal: TaskTerminal?
    private var closed = false
    private var waiters: [CheckedContinuation<Void, Never>] = []
    public init() {}
    public func accept(id: UUID) throws {
        guard !closed else { throw KitError.closed }
        guard active == nil else { throw KitError.busy }
        active = id; terminal = nil
    }
    @discardableResult public func finish(id: UUID, terminal: TaskTerminal) -> Bool {
        guard active == id, self.terminal == nil else { return false }
        self.terminal = terminal; return true
    }
    public func release(id: UUID) {
        guard active == id else { return }
        active = nil
        let ready = waiters; waiters.removeAll()
        for waiter in ready { waiter.resume() }
    }
    public func close() async {
        closed = true
        if active != nil { await withCheckedContinuation { waiters.append($0) } }
    }
}
