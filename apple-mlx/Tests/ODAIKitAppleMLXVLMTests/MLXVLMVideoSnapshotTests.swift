import XCTest
import AVFoundation
@testable import ODAIKitAppleMLXVLM

final class MLXVLMVideoSnapshotTests: XCTestCase {
    func testCancellationAfterFirstDecodedFrameJoinsAndRemovesPrivateCopy() async throws {
        let trace = VideoSnapshotTrace()
        let input = fixture("video-sdr")
        let task = Task {
            try await MLXVLMVideoSnapshot.load(input, observer: { event in
                trace.record(event)
                if case .decodedFrame(0) = event { withUnsafeCurrentTask { $0?.cancel() } }
            })
        }
        do {
            _ = try await task.value
            XCTFail("active decode cancellation must not return a snapshot")
        } catch { XCTAssertTrue(error is CancellationError) }
        let file = try XCTUnwrap(trace.file)
        XCTAssertEqual(trace.frameCount, 1)
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.deletingLastPathComponent().path))
    }

    func testRepeatedCloseDoesNotDeleteRecreatedForeignDirectory() async throws {
        let snapshot = try await MLXVLMVideoSnapshot.load(fixture("video-sdr"))
        let directory = snapshot.file.deletingLastPathComponent()
        snapshot.close()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
        defer { try? FileManager.default.removeItem(at: directory) }
        let foreign = directory.appendingPathComponent("foreign.txt")
        try Data("foreign".utf8).write(to: foreign)
        snapshot.close()
        XCTAssertEqual(try Data(contentsOf: foreign), Data("foreign".utf8))
    }
    func testValidSnapshotDecodesEightBoundedFramesAndOwnsAnIndependentCopy() async throws {
        let original = try Data(contentsOf: fixture("video-sdr"))
        let input = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".mp4")
        try original.write(to: input)
        defer { try? FileManager.default.removeItem(at: input) }
        let snapshot = try await MLXVLMVideoSnapshot.load(input)
        let copy = snapshot.file
        XCTAssertNotEqual(copy, input)
        XCTAssertEqual(try Data(contentsOf: copy), original)
        try Data("mutated".utf8).write(to: input)
        XCTAssertEqual(try Data(contentsOf: copy), original)
        XCTAssertEqual(snapshot.frames.count, 8)
        XCTAssertEqual(snapshot.duration.seconds, 2, accuracy: 0.001)
        XCTAssertEqual(snapshot.frames.first?.actualTime, .zero)
        for (index, frame) in snapshot.frames.enumerated() {
            XCTAssertLessThanOrEqual(frame.image.width, 512)
            XCTAssertLessThanOrEqual(frame.image.height, 512)
            XCTAssertLessThan(frame.requestedTime.seconds, snapshot.duration.seconds)
            XCTAssertEqual(frame.actualTime.seconds, frame.requestedTime.seconds, accuracy: 1.0 / 24)
            if index > 0 { XCTAssertGreaterThan(frame.actualTime.seconds, snapshot.frames[index - 1].actualTime.seconds) }
        }
        snapshot.close()
        snapshot.close()
        XCTAssertFalse(FileManager.default.fileExists(atPath: copy.path))
        XCTAssertEqual(try Data(contentsOf: input), Data("mutated".utf8))
    }

    func testRejectsDurationCodecDimensionsPixelCountAndMultipleVideoTracks() async {
        for name in ["video-long", "video-hevc", "video-wide", "video-pixels", "video-multi"] {
            await assertInvalid { try await MLXVLMVideoSnapshot.load(self.fixture(name)) }
        }
    }

    func testRejectsInvalidAndOversizedEncodedFile() async throws {
        let input = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".mp4")
        defer { try? FileManager.default.removeItem(at: input) }
        try Data("not a movie".utf8).write(to: input)
        await assertInvalid { try await MLXVLMVideoSnapshot.load(input) }
        let file = try FileHandle(forWritingTo: input)
        try file.truncate(atOffset: UInt64(32 * 1024 * 1024 + 1))
        try file.close()
        await assertInvalid { try await MLXVLMVideoSnapshot.load(input) }
    }

    func testAlreadyCancelledTaskDoesNotAdmitInput() async throws {
        let input = fixture("video-sdr")
        let task = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return try await MLXVLMVideoSnapshot.load(input)
        }
        do { _ = try await task.value; XCTFail("cancelled input admitted") }
        catch { XCTAssertTrue(error is CancellationError) }
    }

    func testRejectsNonFileDirectoryAndSymlinkBeforeOpeningAsset() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }

        await assertInvalid { try await MLXVLMVideoSnapshot.load(URL(string: "https://example.invalid/video.mp4")!) }
        await assertInvalid { try await MLXVLMVideoSnapshot.load(directory) }

        let link = directory.deletingLastPathComponent().appendingPathComponent(UUID().uuidString + ".mp4")
        try FileManager.default.createSymbolicLink(at: link, withDestinationURL: directory)
        defer { try? FileManager.default.removeItem(at: link) }
        await assertInvalid { try await MLXVLMVideoSnapshot.load(link) }
    }

    func testProfileConstantsAreBoundedAndSeparateFromUpstreamModelDefaults() {
        XCTAssertEqual(MLXVLMVideoSnapshot.maxEncodedBytes, 32 * 1024 * 1024)
        XCTAssertEqual(MLXVLMVideoSnapshot.maxDurationSeconds, 5)
        XCTAssertEqual(MLXVLMVideoSnapshot.maxFrames, 8)
        XCTAssertEqual(MLXVLMVideoSnapshot.maxDimension, 1920)
        XCTAssertEqual(MLXVLMVideoSnapshot.maxPixels, 2_073_600)
    }

    private func assertInvalid(
        _ operation: @escaping () async throws -> MLXVLMVideoSnapshot
    ) async {
        do {
            _ = try await operation()
            XCTFail("expected invalid video input")
        } catch {
            XCTAssertTrue(error is MLXVLMVideoSnapshotError)
        }
    }

    private func fixture(_ name: String) -> URL {
        Bundle.module.url(forResource: name, withExtension: "mp4", subdirectory: "Fixtures")!
    }
}

private final class VideoSnapshotTrace: @unchecked Sendable {
    private let lock = NSLock()
    private var recordedFile: URL?
    private var count = 0
    var file: URL? { lock.lock(); defer { lock.unlock() }; return recordedFile }
    var frameCount: Int { lock.lock(); defer { lock.unlock() }; return count }
    func record(_ event: MLXVLMVideoSnapshot.Event) {
        lock.lock(); defer { lock.unlock() }
        switch event {
        case .copied(let file): recordedFile = file
        case .decodedFrame: count += 1
        }
    }
}
