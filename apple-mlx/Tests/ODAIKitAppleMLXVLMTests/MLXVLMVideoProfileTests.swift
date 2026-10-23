import XCTest
import Foundation
@testable import ODAIKitAppleMLXVLM

/// Real container/decode rejection coverage; not arbitrary-video or device evidence.
final class MLXVLMVideoProfileTests: XCTestCase {
    func testH264PQTransferIsRejectedAndPrivateCopyRemoved() async throws {
        try await assertRejected("video-pq")
    }

    func testOffsetEditedTrackIsRejectedAndPrivateCopyRemoved() async throws {
        try await assertRejected("video-offset")
    }

    func testTwoActualFramesCannotBeDuplicatedToSatisfyEightSamples() async throws {
        try await assertRejected("video-two-frames")
    }

    func testExternalURLDataReferenceContainerIsRejectedAndPrivateCopyRemoved() async throws {
        // Structural dref rejection, not a claim of exhaustive external-I/O safety.
        try await assertRejected("video-external-reference")
    }

    private func assertRejected(_ name: String, file: StaticString = #filePath, line: UInt = #line) async throws {
        let input = Bundle.module.url(forResource: name, withExtension: "mp4", subdirectory: "Fixtures")!
        let original = try Data(contentsOf: input)
        let trace = ProfileCopyTrace()
        do {
            let snapshot = try await MLXVLMVideoSnapshot.load(input, observer: { trace.record($0) })
            snapshot.close()
            XCTFail("unsupported profile admitted: \(name)", file: file, line: line)
        } catch { XCTAssertTrue(error is MLXVLMVideoSnapshotError, file: file, line: line) }
        let copy = try XCTUnwrap(trace.file, file: file, line: line)
        XCTAssertFalse(FileManager.default.fileExists(atPath: copy.deletingLastPathComponent().path), file: file, line: line)
        XCTAssertEqual(try Data(contentsOf: input), original, file: file, line: line)
    }
}

private final class ProfileCopyTrace: @unchecked Sendable {
    private let lock = NSLock()
    private var copied: URL?
    var file: URL? { lock.lock(); defer { lock.unlock() }; return copied }
    func record(_ event: MLXVLMVideoSnapshot.Event) {
        guard case .copied(let url) = event else { return }
        lock.lock(); defer { lock.unlock() }; copied = url
    }
}
