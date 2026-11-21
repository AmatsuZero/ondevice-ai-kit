import SwiftUI
import CryptoKit

@main struct PadRawScoreProbeApp: App {
    @State private var status = "PAD raw-score research ready"
    var body: some Scene {
        WindowGroup {
            Text(status).task {
                guard ProcessInfo.processInfo.arguments.contains("--run-pad-raw-score") else { return }
                status = await Task.detached { PadRawScoreChecks.run() }.value
            }
        }
    }
}

private enum PadRawScoreChecks {
    struct Failed: Error { let check: String }
    static func run() -> String {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let inputs = documents.appendingPathComponent("pad-input")
        var report: [String: Any] = ["runId": UUID().uuidString,
            "policyStatus": "UNVERIFIED", "acceptance": "fixed-model-raw-score-smoke",
            "decisionIssued": false, "proofIssued": false,
            "osVersion": ProcessInfo.processInfo.operatingSystemVersionString]
        var checks: [String: Bool] = [:]
        func require(_ value: Bool, _ name: String) throws {
            checks[name] = value
            if !value { throw Failed(check: name) }
        }
        do {
            let modelURL = inputs.appendingPathComponent("anti-spoof-mn3.onnx")
            let model = try Data(contentsOf: modelURL)
            let modelHash = SHA256.hash(data: model).map { String(format: "%02x", $0) }.joined()
            try require(model.count == 12_270_179 && modelHash ==
                "c4c99af04603b62d7e44f6f4daeb33e0daeccc696008c0b1d62f6f5cebbb3262", "fixedModel")
            report["modelSHA256"] = modelHash
            let upstreamHash = SHA384.hash(data: model).map { String(format: "%02x", $0) }.joined()
            try require(upstreamHash == "6de4534964b723397b3e8c995cadcf43bc007cc2f9930b95ae25f76adccece5d1d4d058d0b15117b9e4a9f758424f92a", "fixedModelSHA384")
            report["modelSHA384"] = upstreamHash
            let image = try Data(contentsOf: inputs.appendingPathComponent("astronaut.png"))
            let imageHash = SHA256.hash(data: image).map { String(format: "%02x", $0) }.joined()
            try require(imageHash == "88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5", "approvedInput")
            report["inputSHA256"] = imageHash
            // Exercise the load-failure diagnostic without another model or sample.
            var missingError: NSError?
            let missing = ODAIPadRawScores(inputs.appendingPathComponent("absent-model.onnx").path, image, &missingError)
            try require(missing == nil && missingError?.domain == "io.ondeviceai.pad.probe"
                && missingError?.userInfo["stage"] as? String == "modelLoad"
                && missingError?.userInfo["category"] as? String == "opencv"
                && (missingError?.userInfo["nativeCode"] as? Int ?? 0) < 0
                && Set(missingError?.userInfo.keys.map { $0 } ?? []) == Set(["stage", "category", "nativeCode"]),
                "sanitizedLoadFailure")
            report["loadFailureDiagnostic"] = missingError?.userInfo
            var nativeError: NSError?
            guard let native = ODAIPadRawScores(modelURL.path, image, &nativeError) else {
                throw nativeError ?? Failed(check: "nativeResult")
            }
            report["native"] = native
            let runs = native["runs"] as? [[String: Any]] ?? []
            try require(runs.count == 2, "twoNativeRuns")
            let scores = runs.compactMap { $0["scores"] as? [Double] }
            try require(scores.count == 2 && scores.allSatisfy { $0.count == 2 && $0.allSatisfy { $0.isFinite } }, "finiteTwoScores")
            try require(zip(scores[0], scores[1]).allSatisfy { abs($0 - $1) <= 0.000001 }, "repeatableRawScores")
            try require(native["runtimeVersion"] as? String == "4.12.0", "fixedRuntime")
            try require(native["preprocessingRevision"] as? String == "whole-frame-cubic128-rgb-nchw-v1", "declaredExperimentalPreprocessing")
            report["status"] = "passed"
        } catch {
            report["status"] = "failed"
            if let failure = error as? Failed { report["failedCheck"] = failure.check }
            let native = error as NSError
            if native.domain == "io.ondeviceai.pad.probe" {
                let stages = ["decode", "preprocessing", "modelLoad", "inference", "outputValidation"]
                if let stage = native.userInfo["stage"] as? String, stages.contains(stage) { report["nativeStage"] = stage }
                let categories = ["validation", "opencv", "standard", "unknown"]
                if let category = native.userInfo["category"] as? String, categories.contains(category) {
                    report["nativeCategory"] = category
                    report["nativeCode"] = native.userInfo["nativeCode"] as? Int
                }
            }
        }
        report["checks"] = checks
        return PadProbeReport.persist(report, to: documents.appendingPathComponent("pad-report.json"))
    }
}
