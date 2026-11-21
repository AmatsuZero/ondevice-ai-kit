import Foundation

public enum AppleVideoError: Error, Equatable, Sendable {
    case unsupportedInput(String)
    case readerFailed
    case writerFailed
    case invalidFrame
    case processingFailed
}

enum AppleVideoProfile {
    static func validateFile(_ input: URL) throws {
        guard input.isFileURL else { throw AppleVideoError.unsupportedInput("local input required") }
        let attributes: [FileAttributeKey: Any]
        do { attributes = try FileManager.default.attributesOfItem(atPath: input.path) }
        catch { throw AppleVideoError.unsupportedInput("readable regular file required") }
        guard attributes[.type] as? FileAttributeType == .typeRegular,
              let size = attributes[.size] as? NSNumber, size.int64Value > 0, size.int64Value <= 32 * 1024 * 1024 else {
            throw AppleVideoError.unsupportedInput("regular nonempty file up to 32MiB required")
        }
    }

    static func validate(frameIndex: Int, timestampSeconds: Double, width: Int, height: Int, colorTags: [String]) throws {
        guard (0..<48).contains(frameIndex), timestampSeconds.isFinite,
              abs(timestampSeconds - Double(frameIndex) / 24) < 1.0 / 24_000 else {
            throw AppleVideoError.unsupportedInput("1...48 continuous 24fps frames starting at zero required")
        }
        guard width == 320, height == 180, colorTags == ["ITU_R_709_2", "ITU_R_709_2", "ITU_R_709_2"] else {
            throw AppleVideoError.unsupportedInput("320x180 tagged BT.709 SDR required")
        }
    }
}
