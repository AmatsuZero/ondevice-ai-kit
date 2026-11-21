import XCTest
@testable import ODAIKitVideo

final class VideoContractTests: XCTestCase {
    func testRequestRejectsNonFileAndScaleBelowTwo() throws {
        XCTAssertThrowsError(try VideoProcessingRequest(inputFile: URL(string: "https://example.com/in.mp4")!, scaleFactor: 4))
        XCTAssertThrowsError(try VideoProcessingRequest(inputFile: URL(fileURLWithPath: "/tmp/in.mp4"), scaleFactor: 1))
    }

    func testRequestDefaultsToPreserveAudio() throws {
        let request = try VideoProcessingRequest(inputFile: URL(fileURLWithPath: "/tmp/in.mp4"), scaleFactor: 4)
        XCTAssertEqual(request.audioPolicy, .preserve)
    }

    func testOutputInfoRejectsInvalidValues() {
        XCTAssertThrowsError(try VideoOutputInfo(width: 0, height: 1, frameCount: 1, durationSeconds: 1, audioTrackCount: 0))
        XCTAssertThrowsError(try VideoOutputInfo(width: 1, height: 1, frameCount: 0, durationSeconds: 1, audioTrackCount: 0))
        XCTAssertThrowsError(try VideoOutputInfo(width: 1, height: 1, frameCount: 1, durationSeconds: .infinity, audioTrackCount: 0))
        XCTAssertThrowsError(try VideoOutputInfo(width: 1, height: 1, frameCount: 1, durationSeconds: 1, audioTrackCount: -1))
    }

    func testProgressRejectsInvalidCounts() {
        XCTAssertThrowsError(try VideoProcessingProgress(completedFrames: -1, totalFrames: 2))
        XCTAssertThrowsError(try VideoProcessingProgress(completedFrames: 3, totalFrames: 2))
        XCTAssertThrowsError(try VideoProcessingProgress(completedFrames: 0, totalFrames: 0))
    }
}
