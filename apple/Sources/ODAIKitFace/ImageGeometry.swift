import Foundation
import ODAIKitCore
public enum ImageGeometry {
    public static func rotate(_ p: FacePoint, clockwiseDegrees: Int) throws -> FacePoint {
        switch clockwiseDegrees {
        case 0: return p
        case 90: return FacePoint(x: 1-p.y, y: p.x)
        case 180: return FacePoint(x: 1-p.x, y: 1-p.y)
        case 270: return FacePoint(x: p.y, y: 1-p.x)
        default: throw KitError.invalidInput("rotation must be 0/90/180/270")
        }
    }
    public static func pixelRect(x: Double, y: Double, width: Double, height: Double, imageWidth: Int, imageHeight: Int) throws -> FaceRect {
        guard imageWidth > 0, imageHeight > 0 else { throw KitError.invalidInput("empty image") }
        return FaceRect(x: x/Double(imageWidth), y: y/Double(imageHeight), width: width/Double(imageWidth), height: height/Double(imageHeight))
    }
    public static func visionRect(x: Double, y: Double, width: Double, height: Double) -> FaceRect {
        FaceRect(x: x, y: 1-y-height, width: width, height: height)
    }
    public static func preview(imageWidth: Int, imageHeight: Int, viewWidth: Double, viewHeight: Double, mirrored: Bool) throws -> PreviewTransform {
        guard imageWidth > 0, imageHeight > 0, viewWidth > 0, viewHeight > 0, viewWidth.isFinite, viewHeight.isFinite else { throw KitError.invalidInput("empty preview") }
        let scale = max(viewWidth/Double(imageWidth), viewHeight/Double(imageHeight))
        let width = Double(imageWidth)*scale, height = Double(imageHeight)*scale
        return PreviewTransform(scaleX: mirrored ? -width : width, scaleY: height, offsetX: mirrored ? (viewWidth+width)/2 : (viewWidth-width)/2, offsetY: (viewHeight-height)/2)
    }
}
