import Foundation
public struct FacePoint: Codable, Sendable, Equatable {
    public let x: Double; public let y: Double
    public init(x: Double, y: Double) { self.x = x; self.y = y }
}
public struct FaceRect: Codable, Sendable, Equatable {
    public let x: Double; public let y: Double; public let width: Double; public let height: Double
    public init(x: Double, y: Double, width: Double, height: Double) { self.x = x; self.y = y; self.width = width; self.height = height }
}
public struct PreviewTransform: Codable, Sendable, Equatable {
    public let scaleX: Double; public let scaleY: Double; public let offsetX: Double; public let offsetY: Double
    public func apply(_ p: FacePoint) -> FacePoint { FacePoint(x: p.x * scaleX + offsetX, y: p.y * scaleY + offsetY) }
}
public struct FaceObservation: Codable, Sendable {
    public let box: FaceRect
    public let landmarks: [String: [FacePoint]]
    public let landmarkTopology: String
    public let yaw: Double?; public let pitch: Double?; public let roll: Double?
    public let quality: Double?; public let confidence: Double?
    public let trackingId: String?
    public init(box: FaceRect, landmarks: [String: [FacePoint]], landmarkTopology: String, yaw: Double?, pitch: Double?, roll: Double?, quality: Double?, confidence: Double?, trackingId: String? = nil) {
        self.box = box; self.landmarks = landmarks; self.landmarkTopology = landmarkTopology; self.yaw = yaw; self.pitch = pitch
        self.roll = roll; self.quality = quality; self.confidence = confidence; self.trackingId = trackingId
    }
}
public struct FaceResult: Codable, Sendable {
    public let frameId: String; public let timestampNs: UInt64
    public let orientedWidth: Int; public let orientedHeight: Int
    public let previewTransform: PreviewTransform
    public let backendId: String
    public let faces: [FaceObservation]
    public init(frameId: String, timestampNs: UInt64, orientedWidth: Int, orientedHeight: Int, previewTransform: PreviewTransform, backendId: String, faces: [FaceObservation]) {
        self.frameId = frameId; self.timestampNs = timestampNs; self.orientedWidth = orientedWidth; self.orientedHeight = orientedHeight
        self.previewTransform = previewTransform; self.backendId = backendId; self.faces = faces
    }
}
