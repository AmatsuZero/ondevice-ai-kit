import Foundation
import XCTest
import Darwin
@testable import ODAIKitVoiceEffects

final class VoiceEffectsInputTests: XCTestCase {
    func testMalformedAndUnsupportedInputsNeverStartBackendOrLeaveFiles() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        let valid = voiceWAV()
        let backend = VoiceTestBackend()
        let client = try VoiceEffectsClient(backend: backend, outputDirectory: files.outputs)
        var cases: [(Data, VoiceEffectsError)] = [
            (Data(), .invalidInput(.malformedWAV)),
            (Data(valid.prefix(43)), .invalidInput(.malformedWAV)),
            (voiceWAV(frames: 0), .invalidInput(.malformedWAV)),
            (voiceWAV(rate: 16000, frames: 480001), .unsupportedInput(.duration))
        ]
        for (offset, value, count, expected) in [
            (0, 0, 4, VoiceEffectsError.invalidInput(.malformedWAV)),
            (8, 0, 4, .invalidInput(.malformedWAV)),
            (4, valid.count, 4, .invalidInput(.malformedWAV)),
            (16, 18, 4, .unsupportedInput(.nonCanonicalWAV)),
            (36, 0, 4, .unsupportedInput(.nonCanonicalWAV)),
            (20, 3, 2, .unsupportedInput(.encoding)),
            (22, 2, 2, .unsupportedInput(.encoding)),
            (34, 8, 2, .unsupportedInput(.encoding)),
            (24, 22050, 4, .unsupportedInput(.sampleRate)),
            (28, 1, 4, .invalidInput(.malformedWAV)),
            (32, 4, 2, .invalidInput(.malformedWAV)),
            (40, 1, 4, .invalidInput(.malformedWAV))
        ] {
            var bytes = valid; voiceSet(&bytes, at: offset, value: value, count: count)
            cases.append((bytes, expected))
        }
        var trailing = valid; trailing.append(0)
        cases.append((trailing, .invalidInput(.malformedWAV)))
        for (bytes, expected) in cases {
            try files.write(bytes)
            await voiceError(expected) { _ = try await client.process(files.request()) }
            XCTAssertEqual(try Data(contentsOf: files.input), bytes)
            XCTAssertTrue(files.outputEntries.isEmpty)
        }
        XCTAssertEqual(backend.state.access { $0.starts }, 0)
        await client.close()
    }

    func testLocalReadableRegularFileOnlyIncludingFIFOWithoutBlocking() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        let client = try VoiceEffectsClient(backend: VoiceTestBackend(), outputDirectory: files.outputs)
        let fifo = files.root.appendingPathComponent("pipe")
        XCTAssertEqual(mkfifo(fifo.path, 0o600), 0)
        let link = files.root.appendingPathComponent("link.wav")
        try files.write(voiceWAV())
        try FileManager.default.createSymbolicLink(at: link, withDestinationURL: files.input)
        for url in [URL(string: "https://example.invalid/input.wav")!, files.root,
                    files.root.appendingPathComponent("missing"), fifo, link] {
            await voiceError(.invalidInput(.file)) {
                _ = try await client.process(.init(inputFile: url, preset: .original))
            }
        }
        try FileManager.default.setAttributes([.posixPermissions: 0o000], ofItemAtPath: files.input.path)
        defer { try? FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: files.input.path) }
        await voiceError(.invalidInput(.file)) { _ = try await client.process(files.request()) }
        await client.close()
    }

    func testPhysicalSizeBoundBeforeReadingOrAllocation() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        try files.write(voiceWAV())
        let fd = open(files.input.path, O_WRONLY)
        XCTAssertGreaterThanOrEqual(fd, 0)
        XCTAssertEqual(ftruncate(fd, 2_880_045), 0); XCTAssertEqual(Darwin.close(fd), 0)
        let client = try VoiceEffectsClient(backend: VoiceTestBackend(), outputDirectory: files.outputs)
        await voiceError(.unsupportedInput(.duration)) { _ = try await client.process(files.request()) }
        XCTAssertTrue(files.outputEntries.isEmpty)
        await client.close()
    }

    func testConcurrentGrowthAndTruncationAreRejectedAtActualReadBoundary() throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        for growing in [true, false] {
            try files.write(voiceWAV())
            var checkpoints = 0
            XCTAssertThrowsError(try VoiceEffectsWAV.read(files.input) {
                checkpoints += 1
                // First checkpoint is before open. Second is after fstat and before read.
                if checkpoints == 2 {
                    let fd = open(files.input.path, O_WRONLY)
                    guard fd >= 0 else { throw VoiceEffectsError.invalidInput(.file) }
                    defer { _ = Darwin.close(fd) }
                    XCTAssertEqual(ftruncate(fd, growing ? 2_880_045 : 45), 0)
                }
            }) { error in
                XCTAssertEqual(error as? VoiceEffectsError, growing ? .unsupportedInput(.duration)
                               : .invalidInput(.malformedWAV))
            }
        }
        XCTAssertTrue(files.outputEntries.isEmpty)
    }
}
