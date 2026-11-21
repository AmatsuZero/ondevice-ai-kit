import Foundation
import ImageIO
import ODAIKitIdentity

internal enum OpenCvIdentityInput {
    static func validate(_ bytes: Data) throws {
        guard !bytes.isEmpty, bytes.count <= 10 * 1024 * 1024,
              let source = CGImageSourceCreateWithData(bytes as CFData, [kCGImageSourceShouldCache: false] as CFDictionary),
              let fields = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = fields[kCGImagePropertyPixelWidth] as? NSNumber,
              let height = fields[kCGImagePropertyPixelHeight] as? NSNumber,
              (16...4096).contains(width.intValue), (16...4096).contains(height.intValue),
              width.intValue * height.intValue <= 4_194_304 else { throw IdentityError(.invalidInput) }
    }
}
