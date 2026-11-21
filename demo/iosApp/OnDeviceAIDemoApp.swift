import SwiftUI
import ODAIKitDemo
import ODAIKitFace
@MainActor
struct SharedControls: UIViewControllerRepresentable {
    let controller: DemoController
    @MainActor final class Coordinator {
        let bindings: UnifiedDemoIOSBindings
        init(_ controller: DemoController) { bindings = UnifiedDemoIOSBindings(controller: controller) }
    }
    func makeCoordinator() -> Coordinator { Coordinator(controller) }
    func makeUIViewController(context: Context) -> UIViewController {
        let preview = UIHostingController(rootView: UnifiedFacePreview(controller: controller))
        let root = MainViewControllerKt.unifiedDemoViewController(bindings: context.coordinator.bindings,
            facePreview: preview, voiceEffects: context.coordinator.bindings.voiceController,
            playback: context.coordinator.bindings.playbackHost.viewController)
        context.coordinator.bindings.playbackHost.presentationController = root
        return root
    }
    func updateUIViewController(_ view: UIViewController, context: Context) {}
    static func dismantleUIViewController(_ view: UIViewController, coordinator: Coordinator) {
        coordinator.bindings.close()
    }
}

private struct UnifiedFacePreview: View {
    @ObservedObject var controller: DemoController
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color.black
                if let image = controller.image, let cg = image.cgImage {
                    Image(uiImage: image).resizable().scaledToFill()
                        .frame(width: geometry.size.width, height: geometry.size.height).clipped()
                    if let transform = try? ImageGeometry.preview(imageWidth: cg.width, imageHeight: cg.height,
                        viewWidth: geometry.size.width, viewHeight: geometry.size.height, mirrored: false) {
                        ForEach(Array(controller.faces.enumerated()), id: \.offset) { _, face in
                            let point = transform.apply(FacePoint(x: face.box.x, y: face.box.y))
                            Rectangle().stroke(Color.green, lineWidth: 2)
                                .frame(width: face.box.width * abs(transform.scaleX), height: face.box.height * transform.scaleY)
                                .position(x: point.x + face.box.width * abs(transform.scaleX) / 2,
                                    y: point.y + face.box.height * transform.scaleY / 2)
                        }
                    }
                } else { Text("选择图片或启动相机").foregroundColor(.white) }
            }.clipped()
        }.accessibilityIdentifier("face-preview")
    }
}

@MainActor
final class UnifiedDemoIOSBindings: NSObject, @preconcurrency UnifiedDemoBindings {
    private let controller: DemoController
    let playbackHost: PlaybackEnhancementHost
    #if ODAI_VOICE_EFFECTS_DEMO
    private let voice: VoiceEffectsHost
    #endif
    init(controller: DemoController) {
        self.controller = controller
        self.playbackHost = PlaybackEnhancementHost()
        #if ODAI_VOICE_EFFECTS_DEMO
        self.voice = VoiceEffectsHost(embedded: true, prepareAudio: {}, done: {})
        #endif
        super.init()
    }
    var platformLabel: String { "iOS" }
    var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
    var faceModel: FaceViewModel { controller.model }
    var faceActions: any DemoActions { controller }
    var identityModel: IdentityViewModel? { controller.identityModel }
    var identityActions: (any IdentityDemoActions)? { controller }
    var identitySetup: (any IdentityModelSetupDemoActions)? {
        #if ODAI_OPENCV_IDENTITY_DEMO
        return controller.identityHost.map { UnifiedIdentityCleanup($0) }
        #else
        return nil
        #endif
    }
    var speechModel: SpeechViewModel? {
        #if ODAI_SPEECH_DEMO
        return controller.speechHost?.model
        #else
        return nil
        #endif
    }
    var speechActions: (any SpeechDemoActions)? {
        #if ODAI_SPEECH_DEMO
        return controller.speechHost
        #else
        return nil
        #endif
    }
    var videoModel: VideoViewModel? {
        #if ODAI_VIDEO_DEMO
        return controller.videoHost?.model
        #else
        return nil
        #endif
    }
    var videoActions: (any VideoDemoActions)? {
        #if ODAI_VIDEO_DEMO
        return controller.videoHost
        #else
        return nil
        #endif
    }
    var multimodalModel: MultimodalViewModel? {
        #if ODAI_MULTIMODAL_DEMO
        return controller.multimodalHost?.model
        #else
        return nil
        #endif
    }
    var multimodalActions: (any MultimodalDemoActions)? {
        #if ODAI_MULTIMODAL_DEMO
        return controller.multimodalHost
        #else
        return nil
        #endif
    }
    var voiceController: UIViewController? {
        #if ODAI_VOICE_EFFECTS_DEMO
        return voice
        #else
        return nil
        #endif
    }
    var voiceEffectsModel: VoiceEffectsViewModel? {
        #if ODAI_VOICE_EFFECTS_DEMO
        return voice.model
        #else
        return nil
        #endif
    }
    var voiceEffectsActions: (any VoiceEffectsDemoActions)? {
        #if ODAI_VOICE_EFFECTS_DEMO
        return voice
        #else
        return nil
        #endif
    }
    var playbackEnhancement: (any PlaybackEnhancementDemoActions)? { playbackHost }
    var realtimeSr: (any RealtimeSRDemoActions)? { playbackHost }
    func modelSetup(id: DemoCapabilityId) -> (any UnifiedDemoModelActions)? {
        #if ODAI_MULTIMODAL_MLX_DEMO
        if id == .imageUnderstanding { return UnifiedImageModelSetup(controller) }
        #endif
        return nil
    }
    func setRecordSink(sink: ((DemoCapabilityId, DemoRecordOutcome, String) -> Void)?) {
        faceModel.onTerminal = sink
        speechModel?.onTerminal = sink; videoModel?.onTerminal = sink; multimodalModel?.onTerminal = sink
        #if ODAI_VOICE_EFFECTS_DEMO
        voice.recordSink = sink
        #endif
    }
    func enterCapability(id: DemoCapabilityId) { if id == .playbackEnhancement { playbackHost.enter() } }
    func openVoiceEffects() {}
    func leaveCapability(id: DemoCapabilityId) {
        if id == .asr || id == .tts { speechActions?.leavePage() }
        if id == .faceDetection { controller.stopCamera(); controller.model.cancel() }
        if id == .imageUnderstanding { multimodalActions?.leaveMultimodalPage() }
        if id == .fileSuperResolution { videoActions?.leavePage() }
        if id == .playbackEnhancement { playbackHost.close() }
        if id == .identityLiveness { controller.identityModel.leavePage() }
        #if ODAI_VOICE_EFFECTS_DEMO
        if id == .voiceEffects { voice.leaveEmbeddedPage() }
        #endif
    }
    func isIdle() -> Bool {
        if !controller.identityModel.navigationIdle { return false }
        #if ODAI_OPENCV_IDENTITY_DEMO
        if controller.identityHost?.busy == true { return false }
        #endif
        #if ODAI_VOICE_EFFECTS_DEMO
        if !voice.idle { return false }
        #endif
        return (speechModel?.state.controlsEnabled ?? true) &&
            (videoModel?.state.controlsEnabled ?? true) && (videoModel?.state.pendingReleases ?? 0) == 0 &&
            (multimodalModel?.state.controlsEnabled ?? true) && controller.bridge.jobs.isEmpty
    }
    func retryCleanup() { videoModel?.retryReleases(); voiceEffectsModel?.retryReleases() }
    func status(id: DemoCapabilityId) -> DemoCapabilityStatus {
        if id == .videoUnderstanding || id == .identityLiveness {
            return DemoCapabilityStatus(kind: .frozenPreview, detail: "研发暂停")
        }
        if id == .playbackEnhancement {
            return DemoCapabilityStatus(kind: .ready, detail: "CMP 内嵌 AVPlayer + Metal 2× 超分；支持原画/自动/可用的系统、CuNNy 和 Spatial 后端")
        }
        if id == .faceDetection {
            let availability = controller.bridge.availability()
            return UnifiedDemoModelKt.demoAvailability(hostPresent: true, available: availability.available,
                queried: true, reason: controller.cameraPermissionDenied ? "permissionDenied" : availability.reason)
        }
        if id == .asr || id == .tts {
            guard let state = speechModel?.state else { return DemoCapabilityStatus(kind: .missingHost, detail: "当前构建未包含语音模块") }
            let backends = id == .asr ? state.asr : state.tts
            return UnifiedDemoModelKt.demoAvailability(hostPresent: true,
                available: backends.backends.contains { $0.availability.available },
                queried: !backends.backends.isEmpty || backends.failure != nil,
                reason: state.failure?.reason ?? backends.failure?.reason ?? backends.backends.first?.availability.reason)
        }
        if id == .fileSuperResolution {
            guard let state = videoModel?.state else { return DemoCapabilityStatus(kind: .missingHost, detail: "当前构建未包含视频模块") }
            return UnifiedDemoModelKt.demoAvailability(hostPresent: true,
                available: state.backends.contains { $0.availability.available }, queried: !state.backends.isEmpty || state.queryFailure != nil,
                reason: state.queryFailure?.reason ?? state.backends.first?.availability.reason)
        }
        if id == .imageUnderstanding {
            guard let state = multimodalModel?.state else { return DemoCapabilityStatus(kind: .missingHost, detail: "当前构建未包含图像理解模块") }
            #if ODAI_MULTIMODAL_MLX_DEMO
            if !controller.multimodalModelReady { return DemoCapabilityStatus(kind: .missingModel, detail: controller.multimodalSetupStatus) }
            #endif
            return UnifiedDemoModelKt.demoAvailability(hostPresent: true,
                available: state.backends.contains { $0.availability.available }, queried: !state.backends.isEmpty || state.queryFailure != nil,
                reason: state.queryFailure?.reason ?? state.backends.first?.availability.reason)
        }
        if id == .voiceEffects {
            #if ODAI_VOICE_EFFECTS_DEMO
            return UnifiedDemoModelKt.demoHostAvailability(state: voice.preparationState)
            #else
            return DemoCapabilityStatus(kind: .missingHost, detail: nil)
            #endif
        }
        return DemoCapabilityStatus(kind: .notQueried, detail: nil)
    }
    func close() {
        controller.stopCamera()
        speechActions?.leavePage(); videoActions?.leavePage(); multimodalActions?.leaveMultimodalPage()
        #if ODAI_VOICE_EFFECTS_DEMO
        voice.shutdown()
        #endif
        playbackHost.close()
        setRecordSink(sink: nil)
    }
}

#if ODAI_OPENCV_IDENTITY_DEMO
@MainActor
private final class UnifiedIdentityCleanup: NSObject, @preconcurrency IdentityModelSetupDemoActions {
    let host: IdentityOpenCvHost
    init(_ host: IdentityOpenCvHost) { self.host = host }
    var busy: Bool { host.busy }
    var status: String { host.status }
    var modelReady: Bool { host.modelReady }
    var sessionReady: Bool { host.sessionReady }
    func importModels() {}
    func enableLocalEnrollment() {}
    func deleteLocalEnrollment() { host.deleteLocalEnrollment() }
}
#endif
#if ODAI_MULTIMODAL_MLX_DEMO
@MainActor
private final class UnifiedImageModelSetup: NSObject, @preconcurrency UnifiedDemoModelActions {
    let controller: DemoController
    init(_ controller: DemoController) { self.controller = controller }
    var busy: Bool { controller.multimodalPreparing }
    var canPrepare: Bool { !controller.multimodalModelReady }
    var status: String { controller.multimodalSetupStatus }
    func prepare() { controller.importMultimodalModels() }
    func cancel() { controller.cancelUnifiedMultimodalPreparation() }
}
#endif

@main struct OnDeviceAIDemoApp: App {
    @StateObject private var controller = DemoController()
    var body: some Scene {
        WindowGroup {
            VStack(spacing: 0) {
                SharedControls(controller: controller)
                if let message = controller.message { Text(message).font(.caption) }
                if controller.cameraPermissionDenied {
                    Button("打开设置") { controller.openCameraSettings() }
                        .accessibilityIdentifier("camera-permission-settings")
                }
                if let report = controller.report { Button("导出 Benchmark JSON") { controller.exportReport(json: report) } }
                #if DEBUG
                if let status = controller.orientationProbeStatus {
                    Text(status).accessibilityIdentifier("orientation-probe-status")
                    if let size = controller.orientationProbeSize {
                        Text(size).accessibilityIdentifier("orientation-probe-size")
                    }
                }
                if ProcessInfo.processInfo.arguments.contains("--ui-camera-test") {
                    Text(String(controller.cameraFrameCount)).accessibilityIdentifier("camera-frame-count")
                }
                #endif
            }
            .onDisappear { controller.stopCamera() }
            .onAppear {
                #if ODAI_OPENCV_IDENTITY_VALIDATION
                IdentityDemoChecks.start(controller: controller)
                #endif
                #if ODAI_MULTIMODAL_DEMO_VALIDATION
                MultimodalDemoValidation.start(preparing: { [weak controller] in controller?.multimodalPreparing ?? true },
                    host: { [weak controller] in controller?.multimodalHost })
                #endif
                #if DEBUG
                if ProcessInfo.processInfo.arguments.contains("--smoke-orientation-6") {
                    controller.runOrientationProbe()
                }
                #endif
                if ProcessInfo.processInfo.arguments.contains("--smoke-blank") {
                    let renderer = UIGraphicsImageRenderer(size: CGSize(width: 64, height: 64))
                    controller.useImage(renderer.image { context in UIColor.white.setFill(); context.fill(CGRect(x: 0, y: 0, width: 64, height: 64)) }, smokeFixture: true)
                }
            }
        }
    }
}
