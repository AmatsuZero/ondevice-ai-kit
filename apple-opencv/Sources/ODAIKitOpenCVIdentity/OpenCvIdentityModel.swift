import Foundation
import ODAIKitIdentity
import ODAIKitModelStore

/// Pinned local prototype assets; recognition and PAD policies remain unverified.
public enum OpenCvIdentityModel {
    public static let backendID = "opencv.identity.apple"
    public static let version = "4.12.0"
    public static let upstream = "47534e27c9851bb1128ccc0102f1145e27f23f98"
    public static let preprocess = "opencv-4.12-yunet640-sface112-rgb-l2-v1"
    public static let detector = "face_detection_yunet_2023mar.onnx"
    public static let recognizer = "face_recognition_sface_2021dec.onnx"
    public static let ref = ModelRef(modelId: "opencv-yunet-sface", revision: "zoo-47534e27-v1")
    public static let embeddingSpace = try! EmbeddingSpaceId(
        supportedCapabilities: [.verification, .identification], backendId: backendID, backendVersion: version,
        modelBinding: ModelBinding(kind: .model, id: ref.modelId, revision: ref.revision),
        featureFormatVersion: "1", dimension: 128, dtype: .float32, endianness: .little,
        normalization: "l2", preprocessRevision: preprocess, scoreMetric: "cosine", scoreDirection: .higherIsBetter)

    public static func manifest() throws -> ModelManifest {
        func asset(_ folder: String, _ name: String, _ bytes: Int64, _ hash: String) -> ModelFile {
            ModelFile(path: name,
                url: URL(string: "https://media.githubusercontent.com/media/opencv/opencv_zoo/\(upstream)/models/\(folder)/\(name)")!,
                bytes: bytes, sha256: hash)
        }
        return ModelManifest(modelId: ref.modelId, revision: ref.revision,
            upstreamURL: URL(string: "https://github.com/opencv/opencv_zoo")!, upstreamRevision: upstream,
            capability: "face-identity", backendId: backendID, backendVersion: VersionRange(minimum: version, maximumExclusive: "4.13.0"),
            minimumOS: "15.0", architectures: ["arm64", "x86_64"], formatVersion: "onnx-yunet-sface-v1",
            files: [asset("face_detection_yunet", detector, 232_589, "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4"),
                    asset("face_recognition_sface", recognizer, 38_696_353, "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79")],
            processing: ModelProcessing(inputSignature: "upright-encoded-image-bgr-yunet-long-edge-640", outputSignature: "float32-128-l2",
                preprocessRevision: preprocess, tokenizerRevision: nil, promptRevision: nil, maxInputElements: 640 * 640 * 3),
            resources: ModelResources(downloadBytes: 38_928_942, temporaryBytes: 77_857_884, installedBytesLimit: 38_928_942),
            licenseIdentifier: "MIT AND Apache-2.0", licenseURL: URL(string: "https://github.com/opencv/opencv_zoo/tree/\(upstream)/models")!,
            attribution: "OpenCV Zoo YuNet (MIT) and SFace (Apache-2.0); retain both upstream licenses.",
            redistribution: "Local prototype approved; production use, training-data provenance and redistribution review remain open.")
    }

    public static func unverifiedPolicy(kind: IdentityPolicyKind) throws -> ThresholdPolicy {
        try ThresholdPolicy(id: "opencv-prototype-\(kind.rawValue)", revision: "unverified-v1", embeddingSpace: embeddingSpace,
            scoreMetric: "cosine", scoreDirection: .higherIsBetter, calibrationStatus: .unverified, kind: kind)
    }
}

internal enum OpenCvIdentityFeatures {
    static func normalized(_ raw: [Float]) throws -> [Float] {
        guard raw.count == 128, raw.allSatisfy({ $0.isFinite }) else { throw IdentityError(.invalidInput) }
        let norm = sqrt(raw.reduce(0) { $0 + Double($1) * Double($1) })
        guard norm.isFinite, norm > 0 else { throw IdentityError(.invalidInput) }
        return raw.map { Float(Double($0) / norm) }
    }
}
