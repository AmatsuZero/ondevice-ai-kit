// Run with swiftc demo/iosApp/PlaybackFrameChoice.swift scripts/tests/PlaybackFrameChoiceTests.swift -o <temporary executable>.
@main
struct PlaybackFrameChoiceTests {
  static func main() {
    var gate = UHPlayerFrameChoice()
    gate.reset(deadline: 10)
    let first = gate.generation
    precondition(gate.resolve(first, now: 1, hasTexture: true) == nil)
    gate.attempt()
    gate.didSubmit()
    precondition(gate.resolve(first, now: 9, hasTexture: true) == true)
    precondition(gate.resolve(first, now: 10, hasTexture: false) == nil)

    gate.reset(deadline: 20)
    gate.didSubmit()
    let timedOut = gate.generation
    precondition(gate.resolve(timedOut, now: 20, hasTexture: true) == false)
    precondition(gate.resolve(timedOut, now: 21, hasTexture: true) == nil)

    gate.reset(deadline: 30)
    gate.didSubmit()
    let cancelled = gate.generation
    gate.reset(deadline: 40)
    gate.didSubmit()
    precondition(gate.generation != cancelled)
    precondition(gate.resolve(cancelled, now: 29, hasTexture: true) == nil)
    precondition(gate.waiting)
    precondition(gate.resolve(gate.generation, now: 39, hasTexture: false) == false)

    gate.reset(deadline: 50)
    gate.didSubmit()
    let leaving = gate.generation
    gate.reset(deadline: 0)
    precondition(gate.resolve(leaving, now: 49, hasTexture: true) == nil)
    precondition(!gate.waiting && !gate.attempted)
    print("PlaybackFrameChoice: 11 checks passed")
  }
}
