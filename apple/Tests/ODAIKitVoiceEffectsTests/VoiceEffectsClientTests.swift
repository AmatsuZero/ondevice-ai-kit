import Foundation
import XCTest
@testable import ODAIKitVoiceEffects

final class VoiceEffectsClientTests: XCTestCase {
    func testOriginalIsIndependentExactCopyForAllRatesAndFrameBoundaries() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        let backend = VoiceTestBackend()
        let client = try VoiceEffectsClient(backend: backend, outputDirectory: files.outputs)
        for rate in [16000, 44100, 48000] {
            for frames in [1, rate, rate * 30] {
                let bytes = voiceWAV(rate: rate, frames: frames)
                try files.write(bytes)
                let output = try await client.process(files.request(.original))
                XCTAssertNotEqual(output.audioFile, files.input)
                XCTAssertEqual(try Data(contentsOf: output.audioFile), bytes)
                XCTAssertEqual(try Data(contentsOf: files.input), bytes)
                XCTAssertEqual(output.backendId, "apple.time-pitch")
                XCTAssertEqual(output.sampleRate, rate)
                XCTAssertEqual(output.frameCount, Int64(frames))
                XCTAssertEqual(output.durationSeconds, Double(frames) / Double(rate))
                try await client.releaseOutput(id: output.id)
            }
        }
        XCTAssertEqual(backend.state.access { $0.starts }, 0)
        XCTAssertTrue(files.outputEntries.isEmpty)
        await client.close()
    }

    func testCancellationRetainsBusyThroughActualWorkerExitAndDrain() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        let bytes = voiceWAV(); try files.write(bytes)
        let gate = VoiceTestGate(); defer { gate.open() }
        let backend = VoiceTestBackend(gate: gate)
        let client = try VoiceEffectsClient(backend: backend, outputDirectory: files.outputs)
        let job = Task { try await client.process(files.request()) }
        await fulfillment(of: [gate.entered], timeout: 5)
        job.cancel()
        await voiceError(.busy) { _ = try await client.process(files.request()) }
        let drained = VoiceTestBox(false)
        let draining = Task { await client.drain(); drained.access { $0 = true } }
        await voiceWaitForDrainers(client, count: 1)
        XCTAssertFalse(drained.access { $0 })
        gate.open()
        await voiceCancelled(job); await draining.value
        XCTAssertTrue(drained.access { $0 })
        XCTAssertTrue(files.outputEntries.isEmpty)
        XCTAssertEqual(try Data(contentsOf: files.input), bytes)
        let second = try await client.process(files.request(.original))
        await client.close()
        try await client.releaseOutput(id: second.id)
    }

    func testConcurrentCloseCancelsRenderAndAllCallersJoinCleanup() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        try files.write(voiceWAV())
        let gate = VoiceTestGate(); defer { gate.open() }
        let backend = VoiceTestBackend(gate: gate)
        let client = try VoiceEffectsClient(backend: backend, outputDirectory: files.outputs)
        let job = Task { try await client.process(files.request()) }
        await fulfillment(of: [gate.entered], timeout: 5)
        let closedCount = VoiceTestBox(0)
        let first = Task { await client.close(); closedCount.access { $0 += 1 } }
        await voiceWaitForCancellation(backend)
        let second = Task { await client.close(); closedCount.access { $0 += 1 } }
        await voiceWaitForDrainers(client, count: 2)
        XCTAssertEqual(closedCount.access { $0 }, 0)
        first.cancel(); second.cancel()
        let waiterCount = await client.waiters.count
        XCTAssertEqual(waiterCount, 2, "cancelling a close caller cannot drop the cleanup join")
        await voiceError(.closed) { _ = try await client.process(files.request()) }
        gate.open()
        await voiceCancelled(job); await first.value; await second.value
        XCTAssertEqual(closedCount.access { $0 }, 2)
        await client.close(); await client.drain()
        XCTAssertTrue(files.outputEntries.isEmpty)
    }

    func testDeliveryWinsThenCloseRetainsOutputAndReleaseIsIdempotent() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        let bytes = voiceWAV(); try files.write(bytes)
        let client = try VoiceEffectsClient(backend: VoiceTestBackend(), outputDirectory: files.outputs)
        let output = try await client.process(files.request(.original))
        async let a: Void = client.close()
        async let b: Void = client.close()
        await a; await b
        XCTAssertEqual(try Data(contentsOf: output.audioFile), bytes)
        async let c: Void = client.releaseOutput(id: output.id)
        async let d: Void = client.releaseOutput(id: output.id)
        try await c; try await d
        try await client.releaseOutput(id: output.id)
        XCTAssertFalse(FileManager.default.fileExists(atPath: output.audioFile.path))
        XCTAssertTrue(files.outputEntries.isEmpty)
    }

    func testUnknownAndOtherClientIdsNeverDeleteOutput() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        try files.write(voiceWAV())
        let a = try VoiceEffectsClient(backend: VoiceTestBackend(), outputDirectory: files.outputs)
        let b = try VoiceEffectsClient(backend: VoiceTestBackend(), outputDirectory: files.outputs)
        let output = try await a.process(files.request(.original))
        await voiceError(.invalidInput(.unknownOutput)) { try await b.releaseOutput(id: output.id) }
        await voiceError(.invalidInput(.unknownOutput)) { try await a.releaseOutput(id: "../input.wav") }
        XCTAssertTrue(FileManager.default.fileExists(atPath: output.audioFile.path))
        try await a.releaseOutput(id: output.id)
        await a.close(); await b.close()
    }

    @MainActor func testBackendDoesNotRunOnMainThread() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        try files.write(voiceWAV())
        let backend = VoiceTestBackend()
        let client = try VoiceEffectsClient(backend: backend, outputDirectory: files.outputs)
        let output = try await client.process(files.request())
        XCTAssertFalse(backend.state.access { $0.mainThread })
        try await client.releaseOutput(id: output.id); await client.close()
    }
}
