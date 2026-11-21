import XCTest
import CoreMedia
@testable import ODAIKitAppleMLXVLM

final class MLXVLMVideoMetadataTests: XCTestCase {
    // Using source duration/span interchangeably, reducing/replacing actual CMTime,
    // hashing caller bytes or reordering times would break the evidence contract.
    func testMetadataBindsOwnedBytesExactTimesAndDistinctClipSpan() async throws {
        let url = videoFixture("video-29fps")
        let snapshot = try await MLXVLMVideoSnapshot.load(url)
        defer { snapshot.close() }
        let metadata = try MLXVLMVideoMetadata(snapshot: snapshot)
        XCTAssertEqual(metadata.snapshotSHA256, "9786524a69579ffd69d75c49048a566f31eaec090b1487b69aa4ce6d55e1ee51")
        XCTAssertEqual(metadata.actualTimes.map(\.value), [0,3584,7168,10752,14848,18432,22016,25600])
        XCTAssertEqual(metadata.actualTimes.map(\.timescale), Array(repeating: 14848, count: 8))
        XCTAssertEqual(metadata.requestedTimes.map { Double($0.value) / Double($0.timescale) }, [0,0.25,0.5,0.75,1,1.25,1.5,1.75])
        XCTAssertEqual(Double(metadata.clipDuration.value) / Double(metadata.clipDuration.timescale), 2)
        XCTAssertEqual(Double(metadata.sampledSpan.value) / Double(metadata.sampledSpan.timescale), 25600.0/14848, accuracy: 0.000001)
        XCTAssertEqual(metadata.widths, Array(repeating: 160, count: 8))
        XCTAssertEqual(metadata.heights, Array(repeating: 90, count: 8))
    }
}
