import Foundation
import XCTest
import Darwin
@testable import ODAIKitVoiceEffects

final class VoiceEffectsFailureTests: XCTestCase {
    private let ioFailure = VoiceEffectsError.platform(domain: "NSPOSIXErrorDomain", code: Int(EIO))

    func testWriteClosePublishAndBackendFailuresCleanOnlyOwnedFiles() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        let bytes = voiceWAV(); try files.write(bytes)
        let existing = files.outputs.appendingPathComponent("existing.wav")
        let sentinel = Data([7, 8, 9]); try sentinel.write(to: existing)
        for stage in ["write", "close", "publish", "render"] {
            var io = VoiceEffectsFileIO.live
            let failure = ioFailure
            if stage == "write" { io.write = { _, _ in throw failure } }
            if stage == "close" {
                io.close = { fd in try VoiceEffectsFileIO.live.close(fd); throw failure }
            }
            if stage == "publish" { io.publish = { _, _ in throw failure } }
            let backend = VoiceTestBackend(failure: stage == "render" ? failure : nil)
            let client = try VoiceEffectsClient(backend: backend, outputDirectory: files.outputs, fileIO: io)
            await voiceError(failure) { _ = try await client.process(files.request()) }
            await client.drain(); await client.close()
            XCTAssertEqual(files.outputEntries, ["existing.wav"])
            XCTAssertEqual(try Data(contentsOf: existing), sentinel)
            XCTAssertEqual(try Data(contentsOf: files.input), bytes)
        }
    }

    func testAtomicPublishCollisionPreservesExistingDestination() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        try files.write(voiceWAV())
        let collision = VoiceTestBox<URL?>(nil)
        let sentinel = Data([99, 98, 97])
        var io = VoiceEffectsFileIO.live
        io.publish = { source, destination in
            // Install a competing final file immediately before the actual primitive.
            try sentinel.write(to: destination, options: .withoutOverwriting)
            collision.access { $0 = destination }
            try VoiceEffectsFileIO.live.publish(source, destination)
        }
        let client = try VoiceEffectsClient(backend: VoiceTestBackend(), outputDirectory: files.outputs, fileIO: io)
        do { _ = try await client.process(files.request()); XCTFail("must not overwrite") }
        catch {
            XCTAssertEqual(error as? VoiceEffectsError,
                           .platform(domain: "NSPOSIXErrorDomain", code: Int(EEXIST)))
        }
        await client.close()
        let path = try XCTUnwrap(collision.access { $0 })
        XCTAssertEqual(try Data(contentsOf: path), sentinel)
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: files.outputs.path),
                       [path.lastPathComponent], "no partial file; foreign collision remains")
    }

    func testCancellationWinsBeforePublishAndAfterPublishBeforeDelivery() async throws {
        for blockAfterLink in [false, true] {
            let files = try VoiceTestFiles(); defer { files.remove() }
            let bytes = voiceWAV(); try files.write(bytes)
            let gate = VoiceTestGate(); defer { gate.open() }
            var io = VoiceEffectsFileIO.live
            io.publish = { source, destination in
                if !blockAfterLink { gate.wait() }
                try VoiceEffectsFileIO.live.publish(source, destination)
                if blockAfterLink { gate.wait() }
            }
            let client = try VoiceEffectsClient(backend: VoiceTestBackend(), outputDirectory: files.outputs, fileIO: io)
            let job = Task { try await client.process(files.request(.original)) }
            await fulfillment(of: [gate.entered], timeout: 5)
            job.cancel()
            await voiceError(.busy) { _ = try await client.process(files.request()) }
            gate.open()
            await voiceCancelled(job); await client.drain(); await client.close()
            XCTAssertTrue(files.outputEntries.isEmpty)
            XCTAssertEqual(try Data(contentsOf: files.input), bytes)
        }
    }

    func testCloseWinsAtPublishedButUndeliveredFile() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        try files.write(voiceWAV())
        let gate = VoiceTestGate(); defer { gate.open() }
        let backend = VoiceTestBackend()
        var io = VoiceEffectsFileIO.live
        io.publish = { source, destination in
            try VoiceEffectsFileIO.live.publish(source, destination); gate.wait()
        }
        let client = try VoiceEffectsClient(backend: backend, outputDirectory: files.outputs, fileIO: io)
        let job = Task { try await client.process(files.request()) }
        await fulfillment(of: [gate.entered], timeout: 5)
        let closing = Task { await client.close() }
        await voiceWaitForCancellation(backend)
        job.cancel() // close and caller cancellation join the same terminal operation.
        gate.open()
        await voiceCancelled(job); await closing.value
        XCTAssertTrue(files.outputEntries.isEmpty)
    }

    func testReleaseFailureRetainsOwnershipForRetryAcrossClose() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        try files.write(voiceWAV())
        let fail = VoiceTestBox(true)
        let failure = ioFailure
        var io = VoiceEffectsFileIO.live
        io.unlink = { url in
            if url.lastPathComponent.hasPrefix("odai-voice-"), url.pathExtension == "wav",
               fail.access({ $0 }) { throw failure }
            try VoiceEffectsFileIO.live.unlink(url)
        }
        let client = try VoiceEffectsClient(backend: VoiceTestBackend(), outputDirectory: files.outputs, fileIO: io)
        let output = try await client.process(files.request(.original))
        await client.close()
        await voiceError(failure) { try await client.releaseOutput(id: output.id) }
        XCTAssertTrue(FileManager.default.fileExists(atPath: output.audioFile.path))
        fail.access { $0 = false }
        try await client.releaseOutput(id: output.id)
        try await client.releaseOutput(id: output.id)
        XCTAssertTrue(files.outputEntries.isEmpty)
    }

    func testFailedCancellationCleanupReturnsPlatformAndKeepsBusyUntilDrainRetries() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        try files.write(voiceWAV())
        let gate = VoiceTestGate(); defer { gate.open() }
        let fail = VoiceTestBox(true)
        let failure = ioFailure
        var io = VoiceEffectsFileIO.live
        io.publish = { source, destination in
            try VoiceEffectsFileIO.live.publish(source, destination); gate.wait()
        }
        io.unlink = { url in
            if url.lastPathComponent.hasPrefix("odai-voice-"), url.pathExtension == "wav",
               fail.access({ $0 }) { throw failure }
            try VoiceEffectsFileIO.live.unlink(url)
        }
        let client = try VoiceEffectsClient(backend: VoiceTestBackend(), outputDirectory: files.outputs, fileIO: io)
        let job = Task { try await client.process(files.request(.original)) }
        await fulfillment(of: [gate.entered], timeout: 5)
        job.cancel(); gate.open()
        do { _ = try await job.value; XCTFail("cleanup failure must surface") }
        catch { XCTAssertEqual(error as? VoiceEffectsError, failure) }
        await voiceError(.busy) { _ = try await client.process(files.request()) }
        fail.access { $0 = false }
        await client.drain(); await client.close()
        XCTAssertTrue(files.outputEntries.isEmpty)
    }

    func testCancelAndCommitHaveOneExplicitWinner() throws {
        let cancelled = VoiceEffectsDecision()
        cancelled.cancel()
        XCTAssertFalse(cancelled.commit())
        XCTAssertThrowsError(try cancelled.check())
        let delivered = VoiceEffectsDecision()
        XCTAssertTrue(delivered.commit())
        delivered.cancel()
        XCTAssertNoThrow(try delivered.check())
        XCTAssertFalse(delivered.commit(), "only one ownership transfer")
    }

    func testCancellationDuringCloseFailureStillCleansBeforeTerminal() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        try files.write(voiceWAV())
        let gate = VoiceTestGate(); defer { gate.open() }
        let failure = ioFailure
        var io = VoiceEffectsFileIO.live
        io.close = { fd in
            try VoiceEffectsFileIO.live.close(fd)
            gate.wait()
            throw failure
        }
        let client = try VoiceEffectsClient(backend: VoiceTestBackend(), outputDirectory: files.outputs, fileIO: io)
        let job = Task { try await client.process(files.request(.original)) }
        await fulfillment(of: [gate.entered], timeout: 5)
        job.cancel()
        let closing = Task { await client.close() }
        await voiceWaitForDrainers(client, count: 1)
        gate.open()
        do { _ = try await job.value; XCTFail("close failure must surface") }
        catch { XCTAssertEqual(error as? VoiceEffectsError, failure) }
        await closing.value
        XCTAssertTrue(files.outputEntries.isEmpty)
    }

    func testBackendUnavailableAndRawFailuresArePathFree() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        try files.write(voiceWAV())
        let backend = VoiceTestBackend(failure: .backendUnavailable)
        let client = try VoiceEffectsClient(backend: backend, outputDirectory: files.outputs)
        await voiceError(.backendUnavailable) { _ = try await client.process(files.request()) }
        await client.close()
        let raw = NSError(domain: "/private/secret/input.wav", code: 123,
                          userInfo: [NSLocalizedDescriptionKey: "private path"])
        XCTAssertEqual(voiceEffectsSanitize(raw) as? VoiceEffectsError,
                       .platform(domain: NSOSStatusErrorDomain, code: -1))
        XCTAssertTrue(files.outputEntries.isEmpty)
    }

    func testBackendWrongFrameCountCannotPublish() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        let bytes = voiceWAV(); try files.write(bytes)
        let client = try VoiceEffectsClient(backend: VoiceTestBackend(wrongFrameCount: true),
                                            outputDirectory: files.outputs)
        await voiceError(.platform(domain: NSOSStatusErrorDomain, code: -1)) {
            _ = try await client.process(files.request())
        }
        await client.close()
        XCTAssertTrue(files.outputEntries.isEmpty)
        XCTAssertEqual(try Data(contentsOf: files.input), bytes)
    }

    func testConcurrentNoClobberPrimitiveHasOneWinnerAndPreservesBothSources() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        let a = files.root.appendingPathComponent("a.wav")
        let b = files.root.appendingPathComponent("b.wav")
        let final = files.root.appendingPathComponent("final.wav")
        let bytesA = Data([1, 2, 3]), bytesB = Data([4, 5, 6])
        try bytesA.write(to: a); try bytesB.write(to: b)
        let gate = VoiceTestGate(); gate.entered.expectedFulfillmentCount = 2
        defer { gate.open() }
        let first = Task.detached { gate.wait(); try VoiceEffectsFileIO.live.publish(a, final) }
        let second = Task.detached { gate.wait(); try VoiceEffectsFileIO.live.publish(b, final) }
        await fulfillment(of: [gate.entered], timeout: 5)
        gate.open()
        let results = [await first.result, await second.result]
        var wins = 0, losses = 0
        for result in results {
            switch result {
            case .success: wins += 1
            case .failure(let error):
                losses += 1
                XCTAssertEqual(error as? VoiceEffectsError,
                               .platform(domain: NSPOSIXErrorDomain, code: Int(EEXIST)))
            }
        }
        XCTAssertEqual(wins, 1); XCTAssertEqual(losses, 1)
        let published = try Data(contentsOf: final)
        XCTAssertTrue(published == bytesA || published == bytesB)
        XCTAssertEqual(try Data(contentsOf: a), bytesA)
        XCTAssertEqual(try Data(contentsOf: b), bytesB)
    }

    func testDirectoryFinalizationFailureRejectsBeforeDeliveryAndReleaseNeedsOnlyUnlink() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        try files.write(voiceWAV())
        let fail = VoiceTestBox(1)
        let failure = ioFailure
        var io = VoiceEffectsFileIO.live
        io.removeDirectory = { url in
            if fail.access({ count in
                if count > 0 { count -= 1; return true }
                return false
            }) { throw failure }
            try VoiceEffectsFileIO.live.removeDirectory(url)
        }
        let client = try VoiceEffectsClient(backend: VoiceTestBackend(), outputDirectory: files.outputs, fileIO: io)
        await voiceError(failure) { _ = try await client.process(files.request(.original)) }
        XCTAssertTrue(files.outputEntries.isEmpty)
        let output = try await client.process(files.request(.original))
        // If release tried to remove a directory after unlink, it would now fail.
        fail.access { $0 = 1 }
        try await client.releaseOutput(id: output.id)
        try await client.releaseOutput(id: output.id)
        await client.close()
        XCTAssertTrue(files.outputEntries.isEmpty)
    }
}
