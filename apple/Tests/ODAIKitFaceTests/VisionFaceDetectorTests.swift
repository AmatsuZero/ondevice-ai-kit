import XCTest
import CoreGraphics
import ODAIKitFace
import ODAIKitCore
@testable import ODAIKitAppleVision
final class VisionFaceDetectorTests: XCTestCase {
    func image() -> ImageInput {
        let context = CGContext(data: nil, width: 64, height: 64, bitsPerComponent: 8, bytesPerRow: 256, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
        context.setFillColor(CGColor(gray: 1, alpha: 1)); context.fill(CGRect(x: 0,y: 0,width: 64,height: 64))
        return ImageInput(image: context.makeImage()!)
    }
    func testBlankImageUsesRealVisionAndReturnsSuccessfulEmptyResult() async throws {
        let detector = VisionFaceDetector()
        let result = try await detector.detect(input: image(), options: FaceOptions())
        XCTAssertTrue(result.faces.isEmpty); XCTAssertEqual(result.backendId, "apple.vision.face")
        await detector.close()
        do { _ = try await detector.detect(input: image(), options: FaceOptions()); XCTFail("closed") }
        catch { XCTAssertEqual(error as? KitError, .closed) }
    }
    func testBackendFailureIsNotNoFaces() async throws {
        let detector = VisionFaceDetector(runner: { _,_ in throw KitError.backend("test") })
        do { _ = try await detector.detect(input: image(), options: FaceOptions()); XCTFail("must fail") }
        catch { XCTAssertEqual(error as? KitError, .backend("test")) }
    }
}
