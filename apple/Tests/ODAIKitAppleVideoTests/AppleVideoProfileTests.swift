import Foundation
import XCTest
@testable import ODAIKitAppleVideo

final class AppleVideoProfileTests: XCTestCase {
    func testAcceptsSequentialBT709FramesAndRejectsWrongTimingOrFrameLimit() throws {
        for index in 0..<48 {
            XCTAssertNoThrow(try AppleVideoProfile.validate(frameIndex: index, timestampSeconds: Double(index) / 24,
                                                          width: 320, height: 180, colorTags: ["ITU_R_709_2", "ITU_R_709_2", "ITU_R_709_2"]))
        }
        for (index, time) in [(0, 1.0), (1, 0.0), (48, 2.0), (0, Double.nan), (1, 1.0 / 30)] {
            XCTAssertThrowsError(try AppleVideoProfile.validate(frameIndex: index, timestampSeconds: time,
                                                               width: 320, height: 180, colorTags: ["ITU_R_709_2", "ITU_R_709_2", "ITU_R_709_2"]))
        }
    }

    func testRejectsUnspecifiedHDRConflictingColorAndWrongDimensions() {
        for tags in [[], ["ITU_R_709_2"], ["ITU_R_709_2", "SMPTE_ST_2084_PQ", "ITU_R_709_2"], ["P3_D65", "ITU_R_709_2", "ITU_R_709_2"]] {
            XCTAssertThrowsError(try AppleVideoProfile.validate(frameIndex: 0, timestampSeconds: 0, width: 320, height: 180, colorTags: tags))
        }
        XCTAssertThrowsError(try AppleVideoProfile.validate(frameIndex: 0, timestampSeconds: 0, width: 180, height: 320,
                                                           colorTags: ["ITU_R_709_2", "ITU_R_709_2", "ITU_R_709_2"]))
    }

    func testRejectsMissingEmptyDirectoryAndOversizedFilesWithoutReadingTheirContents() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("video-profile-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
        defer { try? FileManager.default.removeItem(at: root) }
        let input = root.appendingPathComponent("input.mp4")
        XCTAssertThrowsError(try AppleVideoProfile.validateFile(input))
        XCTAssertThrowsError(try AppleVideoProfile.validateFile(root))
        try Data().write(to: input)
        XCTAssertThrowsError(try AppleVideoProfile.validateFile(input))
        try Data([1]).write(to: input)
        XCTAssertNoThrow(try AppleVideoProfile.validateFile(input))
        let handle = try FileHandle(forWritingTo: input)
        try handle.truncate(atOffset: 33 * 1024 * 1024)
        try handle.close()
        XCTAssertThrowsError(try AppleVideoProfile.validateFile(input))
    }
}
