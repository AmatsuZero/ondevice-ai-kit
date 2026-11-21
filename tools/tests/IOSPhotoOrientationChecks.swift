import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

// Exercise the same ImageIO thumbnail options used by DemoController's picker.
// The source has a black left half and a white right half, making mirroring
// observable without storing a personal photo or depending on Vision results.
func sourceImage() -> CGImage {
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let context = CGContext(data: nil, width: 80, height: 40, bitsPerComponent: 8,
                            bytesPerRow: 0, space: colorSpace,
                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    context.setFillColor(CGColor(gray: 0, alpha: 1))
    context.fill(CGRect(x: 0, y: 0, width: 40, height: 40))
    context.setFillColor(CGColor(gray: 1, alpha: 1))
    context.fill(CGRect(x: 40, y: 0, width: 40, height: 40))
    return context.makeImage()!
}

func thumbnail(orientation: Int) throws -> CGImage {
    let data = NSMutableData()
    let destination = CGImageDestinationCreateWithData(data, UTType.tiff.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, sourceImage(),
                               [kCGImagePropertyOrientation: orientation] as CFDictionary)
    precondition(CGImageDestinationFinalize(destination))
    let source = CGImageSourceCreateWithData(data, nil)!
    let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil)! as NSDictionary
    precondition(properties[kCGImagePropertyOrientation] as? Int == orientation)
    guard let result = CGImageSourceCreateThumbnailAtIndex(source, 0, [
        kCGImageSourceCreateThumbnailFromImageAlways: true,
        kCGImageSourceCreateThumbnailWithTransform: true,
        kCGImageSourceThumbnailMaxPixelSize: 2048
    ] as CFDictionary) else { throw NSError(domain: "P4", code: 1) }
    return result
}

func dark(_ image: CGImage, x: Int, y: Int) -> Bool {
    var pixel = [UInt8](repeating: 0, count: 4)
    let context = CGContext(data: &pixel, width: 1, height: 1, bitsPerComponent: 8,
                            bytesPerRow: 4, space: CGColorSpaceCreateDeviceRGB(),
                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    context.draw(image, in: CGRect(x: -x, y: y - image.height + 1,
                                   width: image.width, height: image.height))
    return pixel[0] < 128
}

@main struct IOSPhotoOrientationChecks {
    static func main() throws {
        let upright = try thumbnail(orientation: 1)
        precondition(upright.width == 80 && upright.height == 40)
        precondition(dark(upright, x: 10, y: 20) && !dark(upright, x: 70, y: 20))

        let mirrored = try thumbnail(orientation: 2)
        precondition(mirrored.width == 80 && mirrored.height == 40)
        precondition(!dark(mirrored, x: 10, y: 20) && dark(mirrored, x: 70, y: 20))

        let rotated = try thumbnail(orientation: 6)
        precondition(rotated.width == 40 && rotated.height == 80)
        precondition(dark(rotated, x: 20, y: 10) != dark(rotated, x: 20, y: 70))

        let rotatedMirrored = try thumbnail(orientation: 7)
        precondition(rotatedMirrored.width == 40 && rotatedMirrored.height == 80)
        precondition(dark(rotatedMirrored, x: 20, y: 10) != dark(rotatedMirrored, x: 20, y: 70))
        precondition(dark(rotated, x: 20, y: 10) != dark(rotatedMirrored, x: 20, y: 10))
        print("iOS photo ImageIO orientation checks passed (1, 2, 6, 7)")
    }
}
