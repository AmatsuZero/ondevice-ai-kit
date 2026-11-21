import Foundation
import XCTest
import Darwin
@testable import ODAIKitVoiceEffects

final class VoiceTestBox<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Value
    init(_ value: Value) { self.value = value }
    func access<T>(_ body: (inout Value) throws -> T) rethrows -> T {
        lock.lock(); defer { lock.unlock() }
        return try body(&value)
    }
}

/// Blocks only the detached backend worker. Tests observe entry before opening it.
final class VoiceTestGate: @unchecked Sendable {
    let entered = XCTestExpectation(description: "worker reached barrier")
    private let condition = NSCondition()
    private var opened = false
    func wait() {
        condition.lock(); defer { condition.unlock() }
        entered.fulfill()
        while !opened { condition.wait() }
    }
    func open() { condition.lock(); opened = true; condition.broadcast(); condition.unlock() }
}

final class VoiceTestBackend: VoiceEffectsBackend, @unchecked Sendable {
    let backendId = "apple.time-pitch"
    let gate: VoiceTestGate?
    let failure: VoiceEffectsError?
    let wrongFrameCount: Bool
    let state = VoiceTestBox((starts: 0, mainThread: false,
                              cancellation: Optional<@Sendable () throws -> Void>.none))
    init(gate: VoiceTestGate? = nil, failure: VoiceEffectsError? = nil, wrongFrameCount: Bool = false) {
        self.gate = gate; self.failure = failure; self.wrongFrameCount = wrongFrameCount
    }
    func render(_ input: VoiceEffectsPCMInput, preset: VoiceEffectsPreset,
                checkCancellation: @escaping @Sendable () throws -> Void) throws -> Data {
        state.access { $0.starts += 1; $0.mainThread = Thread.isMainThread; $0.cancellation = checkCancellation }
        gate?.wait()
        if let failure { throw failure }
        if wrongFrameCount { return Data(input.pcm16LittleEndian.dropLast(2)) }
        // Deliberately return late success even after cancellation: the client must discard it.
        return input.pcm16LittleEndian
    }
    var cancellationAccepted: Bool {
        guard let check = state.access({ $0.cancellation }) else { return false }
        do { try check(); return false } catch { return true }
    }
}

struct VoiceTestFiles {
    let root: URL
    let input: URL
    let outputs: URL
    init() throws {
        root = FileManager.default.temporaryDirectory.appendingPathComponent("voice-test-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
        input = root.appendingPathComponent("input.wav")
        outputs = root.appendingPathComponent("outputs")
        try FileManager.default.createDirectory(at: outputs, withIntermediateDirectories: false)
    }
    func remove() { try? FileManager.default.removeItem(at: root) }
    func write(_ data: Data) throws { try data.write(to: input) }
    func request(_ preset: VoiceEffectsPreset = .high) -> VoiceEffectsRequest {
        .init(inputFile: input, preset: preset)
    }
    var outputEntries: [String] { (try? FileManager.default.contentsOfDirectory(atPath: outputs.path)) ?? [] }
}

func voiceWAV(rate: Int = 16000, frames: Int = 1600, frequency: Double = 440, silent: Bool = false) -> Data {
    var result = Data()
    func text(_ value: String) { result.append(contentsOf: value.utf8) }
    func word(_ value: Int, bytes: Int) {
        for i in 0..<bytes { result.append(UInt8(truncatingIfNeeded: value >> (i * 8))) }
    }
    text("RIFF"); word(36 + frames * 2, bytes: 4); text("WAVEfmt "); word(16, bytes: 4)
    word(1, bytes: 2); word(1, bytes: 2); word(rate, bytes: 4); word(rate * 2, bytes: 4)
    word(2, bytes: 2); word(16, bytes: 2); text("data"); word(frames * 2, bytes: 4)
    for i in 0..<frames {
        let x = silent ? 0 : Int((sin(2 * .pi * frequency * Double(i) / Double(rate)) * 12000).rounded())
        word(x, bytes: 2)
    }
    return result
}

func voiceSet(_ bytes: inout Data, at offset: Int, value: Int, count: Int) {
    for i in 0..<count { bytes[offset + i] = UInt8(truncatingIfNeeded: value >> (8 * i)) }
}

extension XCTestCase {
    func voiceError(_ expected: VoiceEffectsError, _ body: () async throws -> Void,
                    file: StaticString = #filePath, line: UInt = #line) async {
        do { try await body(); XCTFail("expected rejection", file: file, line: line) }
        catch { XCTAssertEqual(error as? VoiceEffectsError, expected, file: file, line: line) }
    }
    func voiceCancelled<T>(_ task: Task<T, Error>, file: StaticString = #filePath, line: UInt = #line) async {
        do { _ = try await task.value; XCTFail("late output delivered", file: file, line: line) }
        catch { XCTAssertTrue(error is CancellationError, file: file, line: line) }
    }
    /// Scheduler handoffs only, with a bound. No timing delay chooses the winner.
    func voiceWaitForCancellation(_ backend: VoiceTestBackend) async {
        for _ in 0..<100_000 {
            if backend.cancellationAccepted { return }
            await Task.yield()
        }
        XCTFail("close did not signal the active worker")
    }
    func voiceWaitForDrainers(_ client: VoiceEffectsClient, count: Int) async {
        for _ in 0..<100_000 {
            if await client.waiters.count >= count { return }
            await Task.yield()
        }
        XCTFail("drain/close returned before joining actual worker completion")
    }
}
