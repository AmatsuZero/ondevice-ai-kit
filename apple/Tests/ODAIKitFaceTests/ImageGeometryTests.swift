import XCTest
@testable import ODAIKitFace
final class ImageGeometryTests: XCTestCase {
    func testRotationsUseUprightTopLeftCoordinates() throws {
        let p = FacePoint(x: 0.2, y: 0.3)
        let cases: [(Int, Double, Double)] = [(0, 0.2, 0.3), (90, 0.7, 0.2), (180, 0.8, 0.7), (270, 0.3, 0.8)]
        for (angle,x,y) in cases {
            let out = try ImageGeometry.rotate(p, clockwiseDegrees: angle)
            XCTAssertEqual(out.x, x, accuracy: 1e-6); XCTAssertEqual(out.y, y, accuracy: 1e-6)
        }
    }
    func testPixelAndVisionBoxesNormalize() throws {
        let out = try ImageGeometry.pixelRect(x: 10, y: 20, width: 30, height: 40, imageWidth: 100, imageHeight: 200)
        XCTAssertEqual(out, FaceRect(x: 0.1, y: 0.1, width: 0.3, height: 0.2))
        let vision = ImageGeometry.visionRect(x: 0.1, y: 0.7, width: 0.3, height: 0.2)
        XCTAssertEqual(vision.y, 0.1, accuracy: 1e-6)
        XCTAssertThrowsError(try ImageGeometry.pixelRect(x: 0,y: 0,width: 1,height: 1,imageWidth: 0,imageHeight: 10))
    }
    func testAspectFillCropAndMirrorAffectPreviewOnly() throws {
        let transform = try ImageGeometry.preview(imageWidth: 100, imageHeight: 200, viewWidth: 200, viewHeight: 200, mirrored: true)
        let point = transform.apply(FacePoint(x: 0.1, y: 0.1))
        XCTAssertEqual(point.x, 180, accuracy: 1e-6); XCTAssertEqual(point.y, -60, accuracy: 1e-6)
    }
}
