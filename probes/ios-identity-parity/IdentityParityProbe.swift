// IdentityParityProbe.swift
// CANDIDATE / REVIEW-ONLY. Authored here; NEVER built, run, or executed on any device by
// this author. Physical-iOS-only, test-only async runner invoked by the EXISTING isolated
// alias host (no new @main app/project). Reuses production OpenCvIdentity.open and
// IdentityProbe/evaluateQuality/embed/close; no production backend/model/threshold/executor
// is modified. No real person's identity or image, and no feature values, are logged here.
#if os(iOS)
import Foundation
import Dispatch
import CryptoKit
import ODAIKitIdentity
import ODAIKitModelStore
import ODAIKitOpenCVIdentity

// Approved PUBLIC upright astronaut fixture (hash from the supplied public probe). Only
// permitted input for the first native validation; never a real person's identity/image.
private let kPublicFixtureSHA = "88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5"
private let kDetectorSHA = "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4"
private let kRecognizerSHA = "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79"
private let kProbeRevision = "parity-upright-public-fixture-v1"

private enum ParityError: String, Error {
    case invalidRequest = "invalid-request", platformGate = "platform-gate", preflight,
         inputHash = "input-hash", modelHash = "model-hash", fixtureGate = "fixture-gate",
         feature, artifact
}
private func token(_ s: String, _ p: String) -> Bool { s.range(of: "^\(p)$", options: .regularExpression) != nil }
private func digest(_ d: Data) -> String { SHA256.hash(data: d).map { String(format: "%02x", $0) }.joined() }
private func sha256(_ u: URL) throws -> String { digest(try Data(contentsOf: u)) }
private func symlink(_ u: URL) -> Bool { (try? u.resourceValues(forKeys: [.isSymbolicLinkKey]))?.isSymbolicLink == true }
private func present(_ u: URL) -> Bool { FileManager.default.fileExists(atPath: u.path) || symlink(u) }
private func directory(_ u: URL) throws {
    let v = try? u.resourceValues(forKeys: [.isDirectoryKey, .isSymbolicLinkKey])
    guard v?.isSymbolicLink != true, v?.isDirectory == true else { throw ParityError.preflight }
}
private func regular(_ u: URL) throws {
    let v = try? u.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey])
    guard v?.isSymbolicLink != true, v?.isRegularFile == true else { throw ParityError.preflight }
}
private func category(_ error: Error) -> String {
    if let e = error as? IdentityError { return "identity:" + e.code.rawValue }
    if let e = error as? ParityError { return "probe:" + e.rawValue }
    return "io-or-runtime"
}

public struct IdentityParityRequest: Sendable {
    public let taskRoot: URL
    public let runId: String
    public let expectedImageSHA256: String
    public let expectedSourceFingerprint: String
    public let publicFixtureOnly: Bool
    public init(taskRoot: URL, runId: String, expectedImageSHA256: String,
                expectedSourceFingerprint: String, publicFixtureOnly: Bool = true) throws {
        guard token(runId, "[A-Za-z0-9]{1,40}"),
              token(expectedImageSHA256, "[0-9a-f]{64}"),
              token(expectedSourceFingerprint, "[A-Za-z0-9._:-]{1,256}"),
              taskRoot.isFileURL,
              !taskRoot.pathComponents.contains("..") else { throw ParityError.invalidRequest }
        self.taskRoot = taskRoot.standardizedFileURL
        self.runId = runId
        self.expectedImageSHA256 = expectedImageSHA256
        self.expectedSourceFingerprint = expectedSourceFingerprint
        self.publicFixtureOnly = publicFixtureOnly
    }
}
public enum IdentityParityStatus: String, Sendable { case completed, failed }

public enum IdentityParityProbe {
    /// Test-only. Call from the existing isolated alias host on a physical iOS device.
    /// Writes LOCAL-ONLY artifacts inside taskRoot; never logs feature values.
    public static func run(_ request: IdentityParityRequest) async -> IdentityParityStatus {
        await Runner(request).execute()
    }
}

private struct Paths {
    let root: URL, input: URL, models: URL, work: URL, modelStore: URL
    let detector: URL, recognizer: URL, feature: URL, report: URL, failed: URL
    init(_ r: IdentityParityRequest) {
        root = r.taskRoot
        input = root.appendingPathComponent("input.png")
        models = root.appendingPathComponent("models")
        work = root.appendingPathComponent("work-" + r.runId)
        modelStore = work.appendingPathComponent("models")
        detector = models.appendingPathComponent(OpenCvIdentityModel.detector)
        recognizer = models.appendingPathComponent(OpenCvIdentityModel.recognizer)
        feature = root.appendingPathComponent("parity-feature-" + r.runId + ".json")
        report = root.appendingPathComponent("parity-report-" + r.runId + ".json")
        failed = root.appendingPathComponent("parity-failed-" + r.runId + ".json")
    }
}

private final class Runner {
    let request: IdentityParityRequest
    let paths: Paths
    var report: [String: Any]
    var stage = "preflight"
    let started = DispatchTime.now().uptimeNanoseconds

    init(_ r: IdentityParityRequest) {
        request = r; paths = Paths(r)
        report = [
            "reportVersion": 1, "kind": "paired-feature-parity-probe", "runId": r.runId, "platform": "ios",
            "sourceFingerprint": r.expectedSourceFingerprint,
            "sourceFingerprintAuthority": "caller-supplied-provenance-claim-only; not authority, not proof of compiled source or build",
            "expectedInputSHA256": r.expectedImageSHA256, "publicFixtureOnly": r.publicFixtureOnly,
            "modelRevision": OpenCvIdentityModel.ref.revision, "preprocessRevision": OpenCvIdentityModel.preprocess,
            "backendId": OpenCvIdentityModel.backendID, "backendVersion": OpenCvIdentityModel.version,
            "embeddingSpaceCanonicalJSON": OpenCvIdentityModel.embeddingSpace.canonicalJSON(),
            "embeddingSpaceCanonicalJSONSource": "native-EmbeddingSpaceId.canonicalJSON",
            "crossPlatformNote": "backendId intentionally differs across OS; embeddingSpaceCanonicalJSON is NOT forced identical",
            "parityBasis": ["dimension": 128, "dtype": "float32", "normalization": "l2",
                "scoreMetric": "cosine", "scoreDirection": "higherIsBetter", "preprocessRevision": OpenCvIdentityModel.preprocess,
                "modelWeightSHA256": ["detector": kDetectorSHA, "recognizer": kRecognizerSHA],
                "comparator": "local-later-explicit-mapping", "comparedHere": false],
            "intermediateStages": ["decodedPixels": "unobservable-via-public-api", "resizedPixels": "unobservable-via-public-api",
                "detectedFaces": "count-only-via-evaluateQuality", "faceCrop": "unobservable-via-public-api",
                "landmarks": "unobservable-via-public-api", "alignedCrop": "unobservable-via-public-api",
                "preprocessedPixels": "not-measured"],
            "pixelParityMeasured": false, "policyStatus": "UNVERIFIED", "productionReady": false, "pad": "absent",
            "cameraStarts": 0, "micStarts": 0, "enrollments": 0, "thresholdDecisions": 0, "verificationDecisions": 0,
            "featureValuesInReport": false, "featureValuesLogged": false,
            "execution": "candidate-code-not-executed-by-author"
        ]
    }

    func execute() async -> IdentityParityStatus {
        var backend: LocalIdentityBackend?
        var store: ModelStore?
        var ownsWork = false
        var values: [Float]?
        var cleanup: [String: Any] = ["backendClosed": false, "nativeDrained": false,
            "modelLeaseReleased": false, "modelStoreRemoved": false, "workDirectoryRemoved": false,
            "cacheCleared": false, "sourceInputPreserved": true, "sourceModelsPreserved": true, "outcome": "pending"]
        do {
            #if targetEnvironment(simulator)
            stage = "platform-gate"; throw ParityError.platformGate
            #endif
            stage = "preflight"
            try directory(paths.root)
            try directory(paths.models)
            try regular(paths.input)
            try regular(paths.detector)
            try regular(paths.recognizer)
            for url in [paths.work, paths.feature, paths.report, paths.failed] {
                guard !present(url) else { throw ParityError.preflight }
            }
            stage = "hash-validation"
            let inputData = try Data(contentsOf: paths.input)
            let inputSHA = digest(inputData)
            report["inputSHA256"] = inputSHA
            guard inputSHA == request.expectedImageSHA256 else { throw ParityError.inputHash }
            if request.publicFixtureOnly {
                guard request.expectedImageSHA256 == kPublicFixtureSHA else { throw ParityError.fixtureGate }
            }
            let detectorSHA = try sha256(paths.detector)
            let recognizerSHA = try sha256(paths.recognizer)
            report["modelWeightSHA256"] = ["detector": detectorSHA, "recognizer": recognizerSHA]
            guard detectorSHA == kDetectorSHA, recognizerSHA == kRecognizerSHA else { throw ParityError.modelHash }
            stage = "work-create"
            try FileManager.default.createDirectory(at: paths.work, withIntermediateDirectories: false)
            ownsWork = true
            stage = "model-store"
            let os = ProcessInfo.processInfo.operatingSystemVersion
            let models = try ModelStore(root: paths.modelStore, compatibility: ModelCompatibility(
                backendId: OpenCvIdentityModel.backendID, backendVersion: OpenCvIdentityModel.version,
                osVersion: "\(os.majorVersion).\(os.minorVersion).\(os.patchVersion)", architecture: "arm64"))
            store = models
            stage = "install"
            try await models.install(manifest: OpenCvIdentityModel.manifest(), source: paths.models)
            stage = "open"
            let opened = try await OpenCvIdentity.open(modelStore: models)
            backend = opened
            stage = "quality"
            let probe = try IdentityProbe(encodedImage: inputData, revision: kProbeRevision)
            let q = try opened.evaluateQuality(probe)
            report["faceCount"] = q.faceCount
            if let score = q.score { report["geometricQuality"] = score } else { report["geometricQuality"] = NSNull() }
            stage = "feature"
            let e = try opened.embed(probe)
            values = e.values
            guard e.values.count == 128, e.values.allSatisfy({ $0.isFinite }) else { throw ParityError.feature }
            let norm = sqrt(e.values.reduce(0.0) { $0 + Double($1) * Double($1) })
            guard norm.isFinite, abs(norm - 1.0) < 1e-4 else { throw ParityError.feature }
            report["feature"] = ["dimension": 128, "finite": true, "l2Normalized": true,
                                 "valuesInReport": false, "valuesLogged": false, "localOnlyArtifact": true]
            stage = "done"
        } catch {
            report["failureStage"] = stage
            report["failureCategory"] = category(error)
        }
        // Cleanup: close/drain -> release lease/store -> delete own work only. Fail-closed.
        var degraded = false
        var backendClosed = false
        var storeReleased = false
        if let opened = backend {
            // closeAndWait is nonthrowing; await then record the completed close/drain.
            await opened.closeAndWait()
            backendClosed = true
            cleanup["backendClosed"] = true; cleanup["nativeDrained"] = true
        }
        if let models = store {
            do {
                try await models.remove(OpenCvIdentityModel.ref)
                let s = await models.status(OpenCvIdentityModel.ref)
                if s == .absent {
                    storeReleased = true
                    cleanup["modelLeaseReleased"] = true; cleanup["modelStoreRemoved"] = true
                } else { degraded = true }
            } catch { degraded = true }
        }
        // Owned work may be deleted only after verified backend close/drain and a verified
        // ABSENT store release; if either prior obligation failed (or never ran because the
        // backend/store were never created), retain the work tree for investigation.
        let priorObligationsMet = (backend == nil || backendClosed) && (store == nil || storeReleased)
        if ownsWork && priorObligationsMet {
            do {
                try directory(paths.work)
                try FileManager.default.removeItem(at: paths.work)
                cleanup["workDirectoryRemoved"] = true; cleanup["cacheCleared"] = true
            } catch { degraded = true }
        }
        cleanup["outcome"] = degraded ? "degraded" : "clean"
        report["cleanup"] = cleanup
        if degraded && report["failureStage"] == nil {
            report["failureStage"] = "cleanup"; report["failureCategory"] = "probe:cleanup"
        }
        var status: IdentityParityStatus = report["failureStage"] == nil ? .completed : .failed
        report["durationMillis"] = Int((DispatchTime.now().uptimeNanoseconds - started) / 1_000_000)
        // LOCAL-ONLY feature artifact; report emitted only after the close final state.
        if status == .completed, let v = values {
            do {
                guard !present(paths.feature) else { throw ParityError.artifact }
                let payload: [String: Any] = ["runId": request.runId, "dimension": 128,
                    "normalization": "l2", "values": v.map { Double($0) }]
                let data = try JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])
                try data.write(to: paths.feature, options: .atomic)
            } catch {
                try? FileManager.default.removeItem(at: paths.feature)
                status = .failed
                report["failureStage"] = "feature-artifact"; report["failureCategory"] = "probe:artifact"
            }
        }
        report["status"] = status.rawValue
        report["terminal"] = true
        return persist(status)
    }

    private func persist(_ status: IdentityParityStatus) -> IdentityParityStatus {
        let target = status == .completed ? paths.report : paths.failed
        let data = try? JSONSerialization.data(withJSONObject: report,
            options: [.sortedKeys, .prettyPrinted, .withoutEscapingSlashes])
        if let data = data, !present(target) {
            do { try data.write(to: target, options: .atomic); return status } catch {}
        }
        report["status"] = "failed"
        if report["failureStage"] == nil { report["failureStage"] = "report-write" }
        if report["failureCategory"] == nil { report["failureCategory"] = "probe:artifact" }
        if let d = try? JSONSerialization.data(withJSONObject: report, options: [.sortedKeys, .prettyPrinted]),
           !present(paths.failed) { try? d.write(to: paths.failed, options: .atomic) }
        return .failed
    }
}
#endif
