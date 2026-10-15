import XCTest
@testable import ODAIKitCore
final class TaskGateTests: XCTestCase {
    func testBusyAndFirstTerminalWins() async throws {
        let gate = TaskGate()
        let id = UUID()
        try await gate.accept(id: id)
        do { try await gate.accept(id: UUID()); XCTFail("must reject busy") }
        catch { XCTAssertEqual(error as? KitError, .busy) }
        let first = await gate.finish(id: id, terminal: .completed)
        let second = await gate.finish(id: id, terminal: .cancelled)
        XCTAssertTrue(first); XCTAssertFalse(second)
    }
    func testCloseWaitsForBackendRelease() async throws {
        let gate = TaskGate(); let id = UUID()
        try await gate.accept(id: id)
        _ = await gate.finish(id: id, terminal: .cancelled)
        // Terminal delivery does not mean GPU/CPU completion.
        do { try await gate.accept(id: UUID()); XCTFail("still owned") }
        catch { XCTAssertEqual(error as? KitError, .busy) }
        await gate.release(id: id)
        await gate.close(); await gate.close()
        do { try await gate.accept(id: UUID()); XCTFail("closed") }
        catch { XCTAssertEqual(error as? KitError, .closed) }
    }
    func testTerminalRaceCommitsExactlyOnce() async throws {
        let gate = TaskGate(); let id = UUID(); try await gate.accept(id: id)
        let wins = await withTaskGroup(of: Bool.self, returning: Int.self) { group in
            for i in 0..<100 { group.addTask { await gate.finish(id: id, terminal: i % 2 == 0 ? .completed : .cancelled) } }
            var n = 0; for await value in group { if value { n += 1 } }; return n
        }
        XCTAssertEqual(wins, 1); await gate.release(id: id)
    }
    func testLatestPendingReplacesOldFrames() async {
        let queue = LatestFrameQueue<Int>()
        await queue.offer(0)
        let first = await queue.next(); XCTAssertEqual(first, 0)
        for i in 1...100 { await queue.offer(i) }
        let blocked = await queue.next(); XCTAssertNil(blocked)
        await queue.complete()
        let last = await queue.next(); let dropped = await queue.droppedCount
        XCTAssertEqual(last, 100); XCTAssertEqual(dropped, 99)
        await queue.complete(); await queue.close()
    }
}
