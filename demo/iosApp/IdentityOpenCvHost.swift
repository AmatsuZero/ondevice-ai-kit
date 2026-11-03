import SwiftUI
import UniformTypeIdentifiers
import ODAIKitDemo
import ODAIKitIdentity
import ODAIKitModelStore
import ODAIKitOpenCVIdentity

/// Optional local host. Consent, model files and session resources never enter shared UI state.
@MainActor
final class IdentityOpenCvHost: NSObject, ObservableObject, UIDocumentPickerDelegate {
    @Published private(set) var busy = true
    @Published private(set) var status = "正在检查身份模型…"
    @Published private(set) var modelReady = false
    @Published private(set) var sessionReady = false
    @Published private(set) var cleanupFailed = false
    private weak var controller: DemoController?
    private let root: URL
    private let models: ModelStore
    private let cleanupGuard: IdentityOpenCvCleanupGuard
    private let probes = IdentityOpenCvProbes()
    private var session: IdentityOpenCvSession?
    private var bridge: IOSIdentityBridge?
    private var job: Task<Void, Never>?
    private var opening = false
    private var closed = false

    init(controller: DemoController, root: URL? = nil) throws {
        self.controller = controller
        self.root = try root ?? FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask,
            appropriateFor: nil, create: true).appendingPathComponent("opencv-identity", isDirectory: true)
        cleanupGuard = IdentityOpenCvCleanupGuard(root: self.root)
        let os = ProcessInfo.processInfo.operatingSystemVersion
        models = try ModelStore(root: self.root.appendingPathComponent("models"), compatibility: ModelCompatibility(
            backendId: OpenCvIdentityModel.backendID, backendVersion: OpenCvIdentityModel.version,
            osVersion: "\(os.majorVersion).\(os.minorVersion).\(os.patchVersion)", architecture: "arm64"))
        super.init()
        cleanupFailed = cleanupGuard.isPending
        job = Task { [weak self] in
            guard let self else { return }
            modelReady = await models.status(OpenCvIdentityModel.ref) == .installed
            status = cleanupFailed ? "上次会话清理未完成；本机注册已停用，自动恢复待实现"
                : modelReady ? "身份模型已安装；可启用本机注册" : "请导入固定版本 YuNet/SFace 模型文件夹"
            busy = false
        }
    }

    func importModels() {
        guard !closed, !busy, !sessionReady, !cleanupFailed else { return }
        busy = true
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.folder], asCopy: false)
        picker.delegate = self
        guard let presenter = presenter() else { busy = false; status = "无法打开模型选择器"; return }
        presenter.present(picker, animated: true)
    }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { busy = false }
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let folder = urls.first, !closed else { busy = false; return }
        installModels(from: folder)
    }
    func installModels(from folder: URL) {
        guard !closed, !sessionReady, !cleanupFailed else { return }
        busy = true
        status = "正在校验并安装 YuNet/SFace…"
        job = Task { [weak self] in
            guard let self else { return }
            let accessed = folder.startAccessingSecurityScopedResource()
            defer { if accessed { folder.stopAccessingSecurityScopedResource() }; busy = false }
            do {
                try await models.install(manifest: OpenCvIdentityModel.manifest(), source: folder)
                try Task.checkCancellation()
                guard !closed else { return }
                modelReady = true; status = "身份模型已安装；同意后启用本机注册"
            } catch { status = Self.safeMessage(error) }
        }
    }

    func enableLocalEnrollment() {
        guard !closed, !busy, modelReady, !sessionReady, !cleanupFailed else { return }
        busy = true; opening = true; status = "正在打开本机注册会话…"
        job = Task { [weak self] in
            guard let self else { return }
            var opened: IdentityOpenCvSession?
            defer { busy = false; opening = false }
            do {
                let next = try await IdentityOpenCvSession.open(
                    root: root.appendingPathComponent("sessions/" + UUID().uuidString), models: models)
                opened = next
                try cleanupGuard.begin()
                try Task.checkCancellation()
                guard !closed else { throw ODAIKitIdentity.IdentityError(.closed) }
                let registry = try IdentityBackendRegistry<any ODAIKitIdentity.IdentityBackend>(
                    defaultBackendId: next.backend.backendId,
                    registrations: [.init(backendId: next.backend.backendId, backend: next.backend)])
                let inputs = probes
                let adapter = IOSIdentityBridge(store: next.store, indexes: next.indexes, registry: registry,
                    verification: next.verification, identification: next.identification,
                    probeResolver: { try inputs.resolve($0) })
                let verifyPolicy = try OpenCvIdentityModel.unverifiedPolicy(kind: .verification)
                let identifyPolicy = try OpenCvIdentityModel.unverifiedPolicy(kind: .identification)
                let owner = try next.consent.ownership()
                let configuration = IdentityDemoConfiguration(
                    grant: IdentityAuthorizationGrant(canonicalValue: next.grant.canonicalJSON()),
                    candidate: IdentityCandidateReference(canonicalValue: next.candidate.canonicalJSON()),
                    template: nil, gallery: nil,
                    enrollmentOptions: IdentityEnrollmentOptions(qualityFloor: 1,
                        qualityPolicyId: "geometric-only-uncalibrated", revision: "1"),
                    verificationOptions: IdentityVerificationOptions(
                        policy: IdentityThresholdPolicy(canonicalValue: verifyPolicy.canonicalJSON()), revision: "1"),
                    identificationOptions: IdentityIdentificationOptions(topK: 32,
                        policy: IdentityPolicyReference(id: identifyPolicy.id, revision: identifyPolicy.revision), revision: "1"),
                    livenessOptions: nil,
                    ownership: IdentityOwnershipAuthorization(storeId: owner.storeId, evidenceRef: owner.evidenceRef),
                    galleryOptions: IdentityGalleryOptions(
                        policy: IdentityThresholdPolicy(canonicalValue: identifyPolicy.canonicalJSON()), aggregationPolicyId: "subject-max-v1"))
                controller?.configureIdentity(bridge: adapter, configuration: configuration,
                    probeFactory: { inputs.reference($0) })
                bridge = adapter; session = next; opened = nil
                sessionReady = true; status = "本机注册已启用（30 分钟）；请选择本人或获授权图片"
            } catch {
                if let opened, !(await finishCleanup(opened)) { return }
                status = Self.safeMessage(error)
            }
        }
    }

    func deleteLocalEnrollment() {
        guard !closed, !busy, let previous = session, !cleanupFailed else { return }
        busy = true
        controller?.resetIdentity()
        bridge?.close(); bridge = nil; probes.clear()
        job = Task { [weak self] in
            guard let self else { return }
            defer { busy = false }
            if await finishCleanup(previous) {
                session = nil; sessionReady = false; status = "本次所有注册已删除并停用"
            }
        }
    }
    private func finishCleanup(_ previous: IdentityOpenCvSession) async -> Bool {
        do {
            _ = try await previous.deleteAndClose()
            try cleanupGuard.complete()
            return true
        } catch {
            session = previous; sessionReady = false; cleanupFailed = true
            status = "删除未完成；会话已停用，自动恢复待实现"
            return false
        }
    }
    func stopOpening() { if opening { job?.cancel() } }
    func close() {
        guard !closed else { return }
        closed = true; job?.cancel()
        bridge?.close(); bridge = nil; probes.clear()
        let previous = session; session = nil
        let obligation = cleanupGuard
        Task.detached {
            guard let previous else { return }
            do { _ = try await previous.deleteAndClose(); try obligation.complete() }
            catch { /* Keep the durable obligation: the next launch reports and blocks enrollment. */ }
        }
    }
    private func presenter() -> UIViewController? {
        var current = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController
        while let presented = current?.presentedViewController { current = presented }
        return current
    }
    private static func safeMessage(_ error: Error) -> String {
        if error is CancellationError { return "本机注册准备已取消" }
        switch error as? ModelStoreError {
        case .integrity: return "模型哈希校验失败，请使用固定版本模型文件"
        case .invalidSource: return "请选择仅含固定版本 YuNet/SFace 两个文件的文件夹"
        case .incompatible, .invalidManifest: return "模型与设备或运行库不兼容"
        case .diskSpace: return "模型安装空间不足"
        default: return "本机注册准备失败，请检查模型文件与设备状态"
        }
    }
}

/// One replaceable task input; the Kotlin reference contains only an opaque random token.
private final class IdentityOpenCvProbes: @unchecked Sendable {
    private let lock = NSLock()
    private var input: (String, ODAIKitIdentity.IdentityProbe)?
    @MainActor func reference(_ image: CGImage) -> IdentityProbeReference {
        let id = UUID().uuidString
        let probe = UIImage(cgImage: image).pngData().flatMap {
            try? ODAIKitIdentity.IdentityProbe(encodedImage: $0, revision: "demo-upright-png-v1")
        }
        lock.lock(); input = probe.map { (id, $0) }; lock.unlock()
        return IdentityProbeReference(canonicalValue: id)
    }
    func resolve(_ reference: IdentityProbeReference) throws -> ODAIKitIdentity.IdentityProbe {
        lock.lock(); defer { lock.unlock() }
        guard let input, input.0 == reference.canonicalValue else { throw ODAIKitIdentity.IdentityError(.invalidInput) }
        return input.1
    }
    func clear() { lock.lock(); input = nil; lock.unlock() }
}

struct IdentityOpenCvSetupView: View {
    @ObservedObject var host: IdentityOpenCvHost
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(host.status).font(.caption)
            Text("本机原型：仅注册本人或获授权图片；特征加密保存。验证、识别和活体尚未校准。").font(.caption)
            HStack {
                Button("导入身份模型") { host.importModels() }.disabled(host.busy || host.sessionReady || host.cleanupFailed)
                Button("同意并启用本机注册") { host.enableLocalEnrollment() }
                    .disabled(host.busy || !host.modelReady || host.sessionReady || host.cleanupFailed)
            }
            if host.sessionReady {
                Button("删除本次所有注册并停用") { host.deleteLocalEnrollment() }.disabled(host.busy)
            }
        }.padding(.horizontal)
    }
}
