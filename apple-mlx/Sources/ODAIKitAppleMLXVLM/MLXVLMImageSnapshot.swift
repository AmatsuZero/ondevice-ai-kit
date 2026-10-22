import Foundation
import Darwin
import ImageIO
import UniformTypeIdentifiers

struct MLXVLMImageSnapshot: Sendable {
    let data: Data
    let width: Int
    let height: Int
    let orientation: Int32

    static func load(_ url: URL) throws -> Self {
        guard url.isFileURL else { throw MLXVLMBackendError.invalidImage }
        let fd = open(url.path, O_RDONLY | O_NOFOLLOW | O_NONBLOCK)
        guard fd >= 0 else { throw MLXVLMBackendError.invalidImage }
        let handle = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
        defer { try? handle.close() }
        var info = stat()
        let limit = 20 * 1024 * 1024
        guard fstat(fd, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG,
              info.st_size > 0, info.st_size <= limit else { throw MLXVLMBackendError.invalidImage }
        var data = Data()
        while let chunk = try handle.read(upToCount: min(1024 * 1024, limit + 1 - data.count)), !chunk.isEmpty {
            data.append(chunk)
            guard data.count <= limit else { throw MLXVLMBackendError.invalidImage }
        }
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              CGImageSourceGetCount(source) == 1,
              let type = CGImageSourceGetType(source) as String?,
              [UTType.png.identifier, UTType.jpeg.identifier].contains(type),
              let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let w = props[kCGImagePropertyPixelWidth] as? Int,
              let h = props[kCGImagePropertyPixelHeight] as? Int,
              (1...4096).contains(w), (1...4096).contains(h), w * h <= 16_777_216 else {
            throw MLXVLMBackendError.invalidImage
        }
        let orientation = (props[kCGImagePropertyOrientation] as? Int32) ?? 1
        guard (1...8).contains(orientation) else { throw MLXVLMBackendError.invalidImage }
        return Self(data: data, width: w, height: h, orientation: orientation)
    }
}
