import Foundation
import UIKit
import CryptoKit
import ODAIKitDemo
import ODAIKitCore
import ODAIKitFace
import ODAIKitAppleVision

final class SwiftBridgeTask: NSObject, BridgeTask {
    private let action: () -> Void
    init(_ action: @escaping () -> Void) { self.action = action }
    func cancel() { action() }
}
final class IOSFaceBridge: NSObject, FaceBridge {
    let detector = VisionFaceDetector()
    struct InputEntry { let image: CGImage; let finished: () -> Void }
    var inputs: [String: InputEntry] = [:]
    var onResult: ((FaceResult) -> Void)?
    var jobs: [UUID: Task<Void, Never>] = [:]
    private var closed = false
    func backendId() -> String { "apple.vision.face" }
    func availability() -> BridgeAvailability { BridgeAvailability(available: !closed, reason: closed ? "closed" : nil, backendId: backendId(), minimumOS: "15.0") }
    func detect(inputId: String, callback: FaceCallback) -> BridgeTask {
        guard !closed, let entry = inputs.removeValue(forKey: inputId) else { callback.onError(message: closed ? "closed" : "missingInput"); return SwiftBridgeTask({}) }
        let id = UUID()
        let task = Task { @MainActor in
            defer { self.jobs.removeValue(forKey: id); entry.finished() }
            do {
                let start = DispatchTime.now().uptimeNanoseconds
                let native = try await self.detector.detect(input: ImageInput(image: entry.image), options: FaceOptions())
                try Task.checkCancellation()
                let elapsed = Double(DispatchTime.now().uptimeNanoseconds - start) / 1_000_000
                self.onResult?(native)
                let faces = native.faces.map { face in
                    BridgeFace(box: BridgeBox(x: face.box.x, y: face.box.y, width: face.box.width, height: face.box.height), yaw: face.yaw.map { KotlinDouble(value: $0) }, pitch: face.pitch.map { KotlinDouble(value: $0) }, roll: face.roll.map { KotlinDouble(value: $0) }, quality: face.quality.map { KotlinDouble(value: $0) }, confidence: face.confidence.map { KotlinDouble(value: $0) }, landmarks: face.landmarks.map { name, points in BridgeLandmark(name: name, points: points.map { BridgePoint(x: $0.x, y: $0.y) }) }, topology: face.landmarkTopology)
                }
                callback.onResult(result: BridgeFaceResult(frameId: native.frameId, width: Int32(native.orientedWidth), height: Int32(native.orientedHeight), backendId: native.backendId, faces: faces, elapsedMs: elapsed, benchmarkJson: nil))
            } catch is CancellationError { callback.onCancelled() }
            catch { callback.onError(message: String(describing: error)) }
        }
        jobs[id] = task
        return SwiftBridgeTask { task.cancel() }
    }
    func close() {
        guard !closed else { return }; closed = true
        inputs.removeAll(); jobs.values.forEach { $0.cancel() }
        Task { await detector.close() }
    }
    func stopTasks() { jobs.values.forEach { $0.cancel() } }
}
