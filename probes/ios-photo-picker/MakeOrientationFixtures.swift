import CoreGraphics
import CryptoKit
import Foundation
import ImageIO
import UniformTypeIdentifiers

// Public synthetic pixels; no photos or external assets are used.
@main struct MakeOrientationFixtures {
    static func main() throws {
        let directory = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
        let context = CGContext(data: nil, width: 80, height: 40, bitsPerComponent: 8,
                                bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(),
                                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.setFillColor(CGColor(gray: 0, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: 40, height: 40))
        context.setFillColor(CGColor(gray: 1, alpha: 1))
        context.fill(CGRect(x: 40, y: 0, width: 40, height: 40))
        let image = context.makeImage()!
        var manifest: [[String: Any]] = []
        for orientation in [2, 6, 7] {
            let url = directory.appendingPathComponent("exif-\(orientation).jpg")
            guard !FileManager.default.fileExists(atPath: url.path) else {
                throw NSError(domain: "EXIFFixture", code: 1)
            }
            let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString, 1, nil)!
            CGImageDestinationAddImage(destination, image, [
                kCGImagePropertyOrientation: orientation,
                kCGImageDestinationLossyCompressionQuality: 1.0
            ] as CFDictionary)
            precondition(CGImageDestinationFinalize(destination))
            let source = CGImageSourceCreateWithURL(url as CFURL, nil)!
            let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil)! as NSDictionary
            precondition(properties[kCGImagePropertyOrientation] as? Int == orientation)
            precondition(properties[kCGImagePropertyPixelWidth] as? Int == 80)
            precondition(properties[kCGImagePropertyPixelHeight] as? Int == 40)
            let data = try Data(contentsOf: url)
            manifest.append(["file": url.lastPathComponent, "orientation": orientation,
                             "width": 80, "height": 40, "sha256": SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()])
        }
        let data = try JSONSerialization.data(withJSONObject: manifest, options: [.prettyPrinted, .sortedKeys])
        try data.write(to: directory.appendingPathComponent("manifest.json"))
        print(String(decoding: data, as: UTF8.self))
    }
}
