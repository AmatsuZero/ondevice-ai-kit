/// The processing slot is retained until complete(), including during close().
public actor LatestFrameQueue<Frame: Sendable> {
    private var processing: Frame?
    private var pending: Frame?
    private var closed = false
    private let onRelease: @Sendable (Frame) -> Void
    public private(set) var droppedCount = 0
    public init(onRelease: @escaping @Sendable (Frame) -> Void = { _ in }) { self.onRelease = onRelease }
    public func offer(_ frame: Frame) {
        guard !closed else { onRelease(frame); return }
        if let old = pending { droppedCount += 1; onRelease(old) }
        pending = frame
    }
    public func next() -> Frame? {
        guard !closed, processing == nil else { return nil }
        processing = pending; pending = nil; return processing
    }
    public func complete() {
        if let old = processing { onRelease(old) }; processing = nil
    }
    public func close() {
        closed = true
        if let old = pending { onRelease(old) }; pending = nil
    }
}
