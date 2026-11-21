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
    lazy var client = try! FaceDetectionClient(defaultBackendId: detector.backendId, registrations: [
        FaceDetectionRegistration(backendId: detector.backendId, detector: detector)
    ])
    struct InputEntry {
        let image: CGImage; let finished: () -> Void; var fixture: String? = nil
    }
    var inputs: [String: InputEntry] = [:]
    var onResult: ((FaceResult) -> Void)?
    var jobs: [UUID: Task<Void, Never>] = [:]
    private var closed = false
    func backendId() -> String { "apple.vision.face" }
    func availability() -> BridgeAvailability { BridgeAvailability(available: !closed, reason: closed ? "closed" : nil, backendId: backendId(), minimumOS: "15.0") }
    func detect(inputId: String, selection: BridgeBackendSelection, callback: FaceCallback) -> BridgeTask {
        guard !closed, let entry = inputs.removeValue(forKey: inputId) else { callback.onError(message: closed ? "closed" : "missingInput"); return SwiftBridgeTask({}) }
        let id = UUID()
        let task = Task { @MainActor in
            defer { self.jobs.removeValue(forKey: id); entry.finished() }
            do {
                let start = DispatchTime.now().uptimeNanoseconds
                let backend: BackendSelection = selection.backendId.map { .explicit($0) } ?? .default
                let native = try await self.client.detect(input: ImageInput(image: entry.image), backend: backend)
                try Task.checkCancellation()
                let elapsed = Double(DispatchTime.now().uptimeNanoseconds - start) / 1_000_000
                #if DEBUG
                self.writeSmokeReport(.success(native), fixture: entry.fixture, elapsedMs: elapsed)
                #endif
                self.onResult?(native)
                let faces = native.faces.map { face in
                    BridgeFace(box: BridgeBox(x: face.box.x, y: face.box.y, width: face.box.width, height: face.box.height), yaw: face.yaw.map { KotlinDouble(value: $0) }, pitch: face.pitch.map { KotlinDouble(value: $0) }, roll: face.roll.map { KotlinDouble(value: $0) }, quality: face.quality.map { KotlinDouble(value: $0) }, confidence: face.confidence.map { KotlinDouble(value: $0) }, landmarks: face.landmarks.map { name, points in BridgeLandmark(name: name, points: points.map { BridgePoint(x: $0.x, y: $0.y) }) }, topology: face.landmarkTopology)
                }
                callback.onResult(result: BridgeFaceResult(frameId: native.frameId, width: Int32(native.orientedWidth), height: Int32(native.orientedHeight), backendId: native.backendId, faces: faces, elapsedMs: elapsed, benchmarkJson: nil))
            } catch is CancellationError {
                #if DEBUG
                self.writeSmokeReport(.failure(CancellationError()), fixture: entry.fixture)
                #endif
                callback.onCancelled()
            } catch {
                #if DEBUG
                self.writeSmokeReport(.failure(error), fixture: entry.fixture)
                #endif
                callback.onError(message: String(describing: error))
            }
        }
        jobs[id] = task
        return SwiftBridgeTask { task.cancel() }
    }
    #if DEBUG
    private func writeSmokeReport(_ outcome: Result<FaceResult, Error>, fixture: String?, elapsedMs: Double? = nil) {
        guard ProcessInfo.processInfo.arguments.contains("--smoke-blank"), let fixture else { return }
        do {
            #if targetEnvironment(simulator)
            let validation = "simulator"
            #else
            let validation = "device"
            #endif
            var record: [String: Any] = [
                "schemaVersion": 1,
                "runId": ProcessInfo.processInfo.environment["ODAI_SMOKE_RUN_ID"] ?? UUID().uuidString,
                "OS": UIDevice.current.systemVersion,
                "sdkCommit": Bundle.main.object(forInfoDictionaryKey: "ODAISDKCommit") as? String ?? "working-tree",
                "validationStatus": validation,
                "fixture": fixture
            ]
            switch outcome {
            case .success(let result):
                record["status"] = "success"
                record["result"] = try JSONSerialization.jsonObject(with: JSONEncoder().encode(result))
                record["elapsedMs"] = elapsedMs
            case .failure(let error):
                let native = error as NSError
                record["status"] = "failure"
                record["error"] = ["domain": native.domain, "code": native.code, "description": String(describing: error)]
            }
            let data = try JSONSerialization.data(withJSONObject: record, options: [.prettyPrinted, .sortedKeys])
            let documents = try FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            try data.write(to: documents.appendingPathComponent("face-smoke.json"), options: .atomic)
            print("ODAI_FACE_SMOKE \(String(decoding: data, as: UTF8.self))")
        } catch { print("ODAI_FACE_SMOKE_EXPORT_FAILED \(error)") }
    }
    #endif
    func close() {
        guard !closed else { return }; closed = true
        inputs.removeAll(); jobs.values.forEach { $0.cancel() }
        Task { await client.close() }
    }
    func stopTasks() { jobs.values.forEach { $0.cancel() } }
}
