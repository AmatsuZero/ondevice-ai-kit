// Main-thread-only decision gate shared by the renderer and its deadline regression.
// Derived from the fixed engine revision recorded in RealtimeSRNotices/PROVENANCE.json.
struct UHPlayerFrameChoice {
  private(set) var generation: UInt64 = 0
  private(set) var attempted = false
  private(set) var waiting = false
  private(set) var deadline: Double = 0

  mutating func reset(deadline: Double) {
    generation &+= 1
    attempted = false
    waiting = false
    self.deadline = deadline
  }
  mutating func attempt() { attempted = true }
  mutating func didSubmit() { waiting = true }
  mutating func resolve(_ token: UInt64, now: Double, hasTexture: Bool) -> Bool? {
    guard token == generation, waiting else { return nil }
    waiting = false
    return hasTexture && now < deadline
  }
}
