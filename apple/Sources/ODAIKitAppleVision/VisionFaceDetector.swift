import Foundation
import Vision
import ODAIKitCore
import ODAIKitFace

public actor VisionFaceDetector: FaceDetector {
    public nonisolated let backendId = "apple.vision.face"
    public typealias Runner = @Sendable (ImageInput, FaceOptions) async throws -> [ODAIKitFace.FaceObservation]
    private let gate = TaskGate()
    private let runner: Runner
    private var work: Task<[ODAIKitFace.FaceObservation], Error>?
    private var closed = false
    public init() { runner = { input, options in try await Self.perform(input: input, options: options) } }
    init(runner: @escaping Runner) { self.runner = runner }
    public func availability() -> CapabilityAvailability {
        CapabilityAvailability(status: closed ? .unavailable : .available, reason: closed ? .closed : nil, backendId: backendId, minOS: "15.0")
    }
    public func detect(input: ImageInput, options: FaceOptions = FaceOptions()) async throws -> FaceResult {
        guard !closed else { throw KitError.closed }
        try Task.checkCancellation()
        let id = UUID(); try await gate.accept(id: id)
        guard !closed else { await gate.release(id: id); throw KitError.closed }
        let runner = self.runner
        let task = Task { try Task.checkCancellation(); let faces = try await runner(input, options); try Task.checkCancellation(); return faces }
        work = task
        do {
            let faces = try await withTaskCancellationHandler(operation: { try await task.value }, onCancel: { task.cancel() })
            try Task.checkCancellation()
            _ = await gate.finish(id: id, terminal: .completed)
            work = nil; await gate.release(id: id)
            let preview = try ImageGeometry.preview(imageWidth: input.orientedWidth, imageHeight: input.orientedHeight, viewWidth: Double(input.orientedWidth), viewHeight: Double(input.orientedHeight), mirrored: false)
            return FaceResult(frameId: input.frameId, timestampNs: input.timestampNs, orientedWidth: input.orientedWidth, orientedHeight: input.orientedHeight, previewTransform: preview, backendId: backendId, faces: faces)
        } catch {
            _ = await gate.finish(id: id, terminal: error is CancellationError ? .cancelled : .failed)
            work = nil; await gate.release(id: id); throw error
        }
    }
    public func close() async {
        closed = true; work?.cancel(); await gate.close()
    }
    private nonisolated static func perform(input: ImageInput, options: FaceOptions) async throws -> [ODAIKitFace.FaceObservation] {
        // Retains input until perform finishes, even if the outer task was cancelled.
        let task = Task.detached(priority: .userInitiated) { () throws -> [ODAIKitFace.FaceObservation] in
            try Task.checkCancellation()
            let request: VNImageBasedRequest = options.landmarks ? VNDetectFaceLandmarksRequest() : VNDetectFaceRectanglesRequest()
            let handler = VNImageRequestHandler(cgImage: input.image, orientation: input.orientation, options: [:])
            try handler.perform([request]); try Task.checkCancellation()
            return ((request.results as? [VNFaceObservation]) ?? []).map { face in
                let box = face.boundingBox
                var regions: [String: [FacePoint]] = [:]
                let points: [(String, VNFaceLandmarkRegion2D?)] = [
                    ("leftEye", face.landmarks?.leftEye), ("rightEye", face.landmarks?.rightEye),
                    ("nose", face.landmarks?.nose), ("outerLips", face.landmarks?.outerLips),
                    ("innerLips", face.landmarks?.innerLips), ("faceContour", face.landmarks?.faceContour),
                    ("leftEyebrow", face.landmarks?.leftEyebrow), ("rightEyebrow", face.landmarks?.rightEyebrow)]
                for (name, region) in points {
                    if let region { regions[name] = region.normalizedPoints.map { FacePoint(x: box.minX + Double($0.x)*box.width, y: 1-(box.minY + Double($0.y)*box.height)) } }
                }
                return ODAIKitFace.FaceObservation(box: ImageGeometry.visionRect(x: box.minX, y: box.minY, width: box.width, height: box.height), landmarks: regions, landmarkTopology: "vision.semantic-regions", yaw: face.yaw?.doubleValue, pitch: face.pitch?.doubleValue, roll: face.roll?.doubleValue, quality: nil, confidence: Double(face.confidence))
            }
        }
        return try await withTaskCancellationHandler(operation: { try await task.value }, onCancel: { task.cancel() })
    }
}
