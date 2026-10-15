import Foundation
import CoreGraphics
import ImageIO
import ODAIKitCore
/// The retained CGImage stays owned by this value for the entire backend call.
public struct ImageInput: @unchecked Sendable {
    public let image: CGImage
    public let orientation: CGImagePropertyOrientation
    public let frameId: String
    public let timestampNs: UInt64
    public init(image: CGImage, orientation: CGImagePropertyOrientation = .up, frameId: String = UUID().uuidString, timestampNs: UInt64 = DispatchTime.now().uptimeNanoseconds) {
        self.image = image; self.orientation = orientation; self.frameId = frameId; self.timestampNs = timestampNs
    }
    public var orientedWidth: Int { [.left, .right, .leftMirrored, .rightMirrored].contains(orientation) ? image.height : image.width }
    public var orientedHeight: Int { [.left, .right, .leftMirrored, .rightMirrored].contains(orientation) ? image.width : image.height }
}
public struct FaceOptions: Sendable {
    public let landmarks: Bool
    public init(landmarks: Bool = true) { self.landmarks = landmarks }
}
public protocol FaceDetector: Sendable {
    func availability() async -> CapabilityAvailability
    func detect(input: ImageInput, options: FaceOptions) async throws -> FaceResult
    func close() async
}
