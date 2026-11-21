import CoreImage
import CoreVideo

/// The bounded SDR video profile has no transparency. Never mutate processor reference frames.
enum AppleVideoOpaqueFrame {
    static func image(from buffer: CVPixelBuffer) -> CIImage {
        let image = CIImage(cvPixelBuffer: buffer)
        return image.settingAlphaOne(in: image.extent)
    }
}
