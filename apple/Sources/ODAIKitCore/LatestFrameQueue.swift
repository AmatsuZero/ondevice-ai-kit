import Foundation
/// Synchronous admission prevents camera callbacks spawning an unbounded queue of Tasks.
public final class LatestFrameQueue<Frame: Sendable>: @unchecked Sendable {
    private let lock = NSLock()
    private var processing: Frame?
    private var pending: Frame?
    private var closed = false
    private var dropped = 0
    private let onRelease: @Sendable (Frame) -> Void
    public var droppedCount: Int { lock.lock(); defer { lock.unlock() }; return dropped }
    public init(onRelease: @escaping @Sendable (Frame) -> Void = { _ in }) { self.onRelease = onRelease }
    public func offer(_ frame: Frame) {
        lock.lock()
        if closed { lock.unlock(); onRelease(frame); return }
        let old = pending; if old != nil { dropped += 1 }; pending = frame
        lock.unlock(); if let old { onRelease(old) }
    }
    public func next() -> Frame? {
        lock.lock(); defer { lock.unlock() }
        guard !closed, processing == nil else { return nil }
        processing = pending; pending = nil; return processing
    }
    public func complete() {
        lock.lock(); let old = processing; processing = nil; lock.unlock()
        if let old { onRelease(old) }
    }
    public func close() {
        lock.lock(); closed = true; let old = pending; pending = nil; lock.unlock()
        if let old { onRelease(old) }
    }
}
