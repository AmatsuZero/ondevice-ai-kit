import SwiftUI
import CryptoKit
import ODAIKitDemo

/// Explicit local validation build only. Exercises the real host/bridge/ViewModel; no touch claim.
@MainActor enum IdentityDemoChecks {
    private static var started = false
    private struct Failed: Error { let name: String }
    static func start(controller: DemoController) {
        guard !started, ProcessInfo.processInfo.arguments.contains("--run-opencv-demo-validation") else { return }
        started = true
        Task { await run(controller) }
    }
    private static func run(_ controller: DemoController) async {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let assets = documents.appendingPathComponent("opencv-validation-input")
        var checks: [String: Bool] = [:]
        var report: [String: Any] = ["runId": UUID().uuidString, "acceptance": "headless-demo-viewmodel",
            "status": "running", "policyStatus": "UNVERIFIED"]
        func save() {
            report["checks"] = checks
            if let bytes = try? JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]) {
                try? bytes.write(to: documents.appendingPathComponent("opencv-demo-report.json"), options: .atomic)
            }
        }
        func require(_ value: Bool, _ name: String) throws {
            checks[name] = value
            if !value { throw Failed(name: name) }
        }
        save()
        do {
            guard let host = controller.identityHost else { throw Failed(name: "missingHost") }
            try await wait { !host.busy }
            try require(!host.sessionReady && !controller.identityModel.configured, "explicitConsentRequired")
            host.installModels(from: assets.appendingPathComponent("models"))
            try await wait { !host.busy }
            try require(host.modelReady, "modelsImported")
            host.enableLocalEnrollment()
            try await wait { !host.busy }
            try require(host.sessionReady && controller.identityModel.configured, "sessionConfigured")
            let bytes = try Data(contentsOf: assets.appendingPathComponent("astronaut.png"))
            let hash = SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
            report["inputSHA256"] = hash
            try require(hash == "88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5", "approvedInput")
            guard let image = UIImage(data: bytes) else { throw Failed(name: "imageDecode") }
            controller.useImage(image)
            let model = controller.identityModel
            try require(model.inputReady, "opaqueProbeSelected")
            model.enrollConfigured(backend: IdentityBackendSelectionDefault.shared)
            try await wait { !model.state.busy }
            try require(model.state.enrollment?.outcome == .enrolled, "firstEnrollment")
            try require(model.state.enrollment?.metadata.backendId == "opencv.identity.apple", "realBackend")
            model.createCandidateConfigured()
            try await wait { !model.state.busy }
            try require(model.candidates.count == 2 && !model.inputReady, "secondCandidateRequiresNewInput")
            controller.useImage(image)
            model.enrollConfigured(backend: IdentityBackendSelectionDefault.shared)
            try await wait { !model.state.busy }
            try require(model.state.enrollment?.outcome == .enrolled, "secondEnrollment")
            model.verifyConfigured(backend: IdentityBackendSelectionDefault.shared)
            try await wait { !model.state.busy }
            try require((model.state.error as? IdentityBridgeFailureRejected)?.code == .thresholdUnverified,
                "verificationPolicyRejected")
            model.publishGalleryConfigured(backend: IdentityBackendSelectionDefault.shared)
            try await wait { !model.state.busy }
            try require((model.state.error as? IdentityBridgeFailureRejected)?.code == .thresholdUnverified,
                "galleryPolicyRejected")
            host.deleteLocalEnrollment()
            try await wait { !host.busy }
            try require(!host.sessionReady && !controller.identityModel.configured, "hostFullGrantDeletedAndReset")
            let blockedRoot = documents.appendingPathComponent("cleanup-obligation-" + UUID().uuidString)
            let obligation = IdentityOpenCvCleanupGuard(root: blockedRoot)
            try obligation.begin()
            let blockedHost = try IdentityOpenCvHost(controller: controller, root: blockedRoot)
            try await wait { !blockedHost.busy }
            try require(blockedHost.cleanupFailed && !blockedHost.sessionReady, "pendingCleanupBlocksRelaunch")
            blockedHost.enableLocalEnrollment()
            try require(!blockedHost.busy && !blockedHost.sessionReady, "pendingCleanupBlocksActivation")
            blockedHost.close()
            try require(obligation.isPending, "closePreservesPendingCleanup")
            try obligation.complete()
            report["status"] = "passed"
        } catch {
            report["status"] = "failed"
            report["error"] = (error as? Failed)?.name ?? "runtime-or-io"
        }
        save()
    }
    private static func wait(_ finished: () -> Bool) async throws {
        let deadline = DispatchTime.now().uptimeNanoseconds + 30_000_000_000
        while !finished() {
            if DispatchTime.now().uptimeNanoseconds >= deadline { throw Failed(name: "timeout") }
            try await Task.sleep(nanoseconds: 25_000_000)
        }
    }
}
