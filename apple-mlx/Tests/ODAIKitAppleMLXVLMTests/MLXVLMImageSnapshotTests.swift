import XCTest
import ImageIO
import UniformTypeIdentifiers
@testable import ODAIKitAppleMLXVLM

final class MLXVLMImageSnapshotTests: XCTestCase {
    func testSnapshotIsIndependentOfLaterSourceMutation() throws {
        let url = try image(width: 64)
        let snapshot = try MLXVLMImageSnapshot.load(url)
        try Data("changed".utf8).write(to: url)
        XCTAssertGreaterThan(snapshot.data.count, 7)
        XCTAssertEqual(snapshot.width, 64)
        XCTAssertEqual(snapshot.height, 1)
    }
    func testRejectsSymlinkDirectoryInvalidDataAndOversizedDimensions() throws {
        let url = try image(width: 64)
        let link = url.deletingLastPathComponent().appendingPathComponent(UUID().uuidString)
        try FileManager.default.createSymbolicLink(at: link, withDestinationURL: url)
        XCTAssertThrowsError(try MLXVLMImageSnapshot.load(link))
        XCTAssertThrowsError(try MLXVLMImageSnapshot.load(url.deletingLastPathComponent()))
        try Data("invalid".utf8).write(to: url)
        XCTAssertThrowsError(try MLXVLMImageSnapshot.load(url))
        XCTAssertThrowsError(try MLXVLMImageSnapshot.load(try image(width: 4097)))
    }
    private func image(width: Int) throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".png")
        let context = CGContext(data: nil, width: width, height: 1, bitsPerComponent: 8, bytesPerRow: width * 4,
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
        CGImageDestinationAddImage(dest, context.makeImage()!, nil)
        XCTAssertTrue(CGImageDestinationFinalize(dest))
        return url
    }
}
