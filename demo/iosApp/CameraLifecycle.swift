/// UI-thread state shared by permission callbacks and foreground transitions.
/// A user stop invalidates earlier starts; a temporary pause preserves intent.
struct CameraLifecycle {
    private(set) var foreground: Bool
    private(set) var requested = false
    private var generation: UInt64 = 0
    mutating func requestStart() -> UInt64 {
        requested = true; generation += 1; return generation
    }
    mutating func pause() { foreground = false }
    mutating func activate() -> UInt64? {
        foreground = true; return requested ? generation : nil
    }
    mutating func stop() { requested = false; generation += 1 }
    func canStart(_ token: UInt64) -> Bool { requested && foreground && token == generation }
}
