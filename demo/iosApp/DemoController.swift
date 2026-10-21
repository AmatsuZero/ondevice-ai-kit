import SwiftUI
import PhotosUI
import ImageIO
import UniformTypeIdentifiers
import AVFoundation
import CryptoKit
import ODAIKitDemo
import ODAIKitCore
import ODAIKitFace

final class DemoController: NSObject, ObservableObject, DemoActions, IdentityDemoActions, PHPickerViewControllerDelegate {
    let bridge = IOSFaceBridge()
    lazy var model = FaceViewModel(bridge: bridge)
    let identityModel: IdentityViewModel
    #if ODAI_SPEECH_DEMO
    private(set) var speechHost: SpeechHost?
    #endif
    #if ODAI_VIDEO_DEMO
    private(set) var videoHost: VideoHost?
    #endif
    private let identityProbeFactory: ((CGImage) -> IdentityProbeReference)?
    private var latestIdentityCameraFrame: CameraFrame?
    private var livenessCapture: Task<Void, Never>?
    private let camera = CameraSource()
    private let admissionLock = NSLock()
    private var frames = LatestFrameQueue<CameraFrame>()
    private var running = false
    private var cameraLifecycle = CameraLifecycle(foreground: UIApplication.shared.applicationState == .active)
    private var cameraStart: Task<Void, Never>?
    private var benchmark: Task<Void, Never>?
    private let photoSubmission = PhotoSubmission()
    @Published var image: UIImage?
    @Published var faces: [FaceObservation] = []
    @Published var report: String?
    @Published var message: String?
    @Published var cameraFrameCount = 0
    #if DEBUG
    @Published private(set) var orientationProbeStatus: String?
    @Published private(set) var orientationProbeSize: String?
    private var orientationProbePending = false
    #endif
    @Published private(set) var cameraPermissionDenied = false
    init(identityBridge: IdentityBridge = UnavailableIdentityBridge(reason: .unverified),
         identityConfiguration: IdentityDemoConfiguration? = nil,
         identityProbeFactory: ((CGImage) -> IdentityProbeReference)? = nil) {
        self.identityModel = IdentityViewModel(bridge: identityBridge)
        self.identityProbeFactory = identityProbeFactory
        super.init()
        #if ODAI_VIDEO_DEMO
        MainActor.assumeIsolated {
            do { videoHost = try VideoHost.make() }
            catch { message = "视频模块初始化失败" }
        }
        #endif
        #if ODAI_SPEECH_DEMO
        MainActor.assumeIsolated {
            do { speechHost = try SpeechHost.make() }
            catch { message = "语音模块初始化失败" }
        }
        NotificationCenter.default.addObserver(self, selector: #selector(speechBackground), name: UIApplication.didEnterBackgroundNotification, object: nil)
        #endif
        identityModel.configure(value: identityConfiguration)
        bridge.onResult = { [weak self] result in
            self?.faces = result.faces
            if self?.running == true { self?.cameraFrameCount += 1 }
            #if DEBUG
            if self?.orientationProbePending == true {
                self?.orientationProbePending = false
                self?.orientationProbeSize = "\(result.orientedWidth)×\(result.orientedHeight)"
                self?.orientationProbeStatus = "success"
            }
            #endif
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--smoke-benchmark") { self?.runBenchmark() }
            #endif
        }
        camera.onFrame = { [weak self] image in self?.offerFrame(image) }
        NotificationCenter.default.addObserver(self, selector: #selector(background), name: UIApplication.willResignActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(foreground), name: UIApplication.didBecomeActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(memoryWarning), name: UIApplication.didReceiveMemoryWarningNotification, object: nil)
    }
    @objc private func background() {
        livenessCapture?.cancel(); identityModel.cancel(); cameraLifecycle.pause(); pauseCamera(); photoSubmission.cancel(); model.cancel()
        #if ODAI_VIDEO_DEMO
        Task { @MainActor [weak self] in self?.videoHost?.background() }
        #endif
    }
    @objc private func foreground() {
        #if ODAI_VIDEO_DEMO
        Task { @MainActor [weak self] in self?.videoHost?.onForeground() }
        #endif
        #if ODAI_SPEECH_DEMO
        Task { @MainActor [weak self] in self?.speechHost?.onForeground() }
        #endif
        if AVCaptureDevice.authorizationStatus(for: .video) == .authorized { cameraPermissionDenied = false }
        if let token = cameraLifecycle.activate(), AVCaptureDevice.authorizationStatus(for: .video) == .authorized { resumeCamera(token) }
    }
    #if ODAI_SPEECH_DEMO
    @objc private func speechBackground() { Task { @MainActor [weak self] in self?.speechHost?.background() } }
    #endif
    @objc private func memoryWarning() { stopCamera(); model.cancel() }
    private func presenter() -> UIViewController? {
        var current = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController
        while let presented = current?.presentedViewController { current = presented }; return current
    }
    func selectPhoto() {
        identityModel.selectProbe(value: nil)
        stopCamera(); var config = PHPickerConfiguration(); config.filter = .images; config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config); picker.delegate = self; presenter()?.present(picker, animated: true)
    }
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let provider = results.first?.itemProvider else { return }
        provider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { [weak self] url, error in
            guard let url, let source = CGImageSourceCreateWithURL(url as CFURL, nil), let cg = Self.decodePhoto(source) else { DispatchQueue.main.async { self?.message = error?.localizedDescription ?? "无法读取图片" }; return }
            DispatchQueue.main.async { self?.useImage(UIImage(cgImage: cg)) }
        }
    }
    private static func decodePhoto(_ source: CGImageSource) -> CGImage? {
        CGImageSourceCreateThumbnailAtIndex(source, 0, [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: 2048
        ] as CFDictionary)
    }
    func useImage(_ selected: UIImage, smokeFixture: Bool = false) {
        model.cancel()
        // Rendering applies UIImage EXIF orientation once; Vision subsequently receives .up.
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        let normalized = UIGraphicsImageRenderer(size: selected.size, format: format).image { _ in selected.draw(in: CGRect(origin: .zero, size: selected.size)) }
        guard let cg = normalized.cgImage else { return }
        image = normalized; faces = []; report = nil
        identityModel.selectProbe(value: identityProbeFactory?(cg))
        let id = UUID().uuidString
        let pending = Array(bridge.jobs.values)
        photoSubmission.replace(after: pending) { [weak self] in
            guard let self else { return }
            self.bridge.inputs[id] = IOSFaceBridge.InputEntry(image: cg, finished: {}, fixture: smokeFixture ? "synthetic-white-64x64-upright-v1" : nil)
            self.model.detect(inputId: id, selection: BridgeBackendSelection(backendId: nil))
        }
    }

    #if DEBUG
    func runOrientationProbe() {
        orientationProbeStatus = "pending"
        orientationProbeSize = nil
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 160, height: 80), format: format)
        let sourceImage = renderer.image { context in
            UIColor.systemBlue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 80, height: 80))
            UIColor.systemYellow.setFill()
            context.fill(CGRect(x: 80, y: 0, width: 80, height: 80))
        }
        guard let sourceCG = sourceImage.cgImage,
              let data = NSMutableData(capacity: 4096),
              let destination = CGImageDestinationCreateWithData(data, UTType.jpeg.identifier as CFString, 1, nil) else {
            orientationProbeStatus = "failure: encode"
            return
        }
        CGImageDestinationAddImage(destination, sourceCG, [kCGImagePropertyOrientation: 6] as CFDictionary)
        guard CGImageDestinationFinalize(destination),
              let imageSource = CGImageSourceCreateWithData(data as CFData, nil),
              let transformed = Self.decodePhoto(imageSource) else {
            orientationProbeStatus = "failure: transform"
            return
        }
        orientationProbePending = true
        useImage(UIImage(cgImage: transformed))
    }
    #endif
    func startCamera() {
        photoSubmission.cancel()
        let token = cameraLifecycle.requestStart()
        cameraPermissionDenied = false
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--simulate-camera-permission-denied") {
            cameraLifecycle.stop()
            cameraPermissionDenied = true
            model.permissionDenied()
            return
        }
        #endif
        AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
            DispatchQueue.main.async {
                guard let self else { return }
                guard self.cameraLifecycle.canStart(token) else { return }
                guard granted else { self.cameraLifecycle.stop(); self.cameraPermissionDenied = true; self.model.permissionDenied(); return }
                self.resumeCamera(token)
            }
        }
    }

    func openCameraSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString), UIApplication.shared.canOpenURL(url) else { return }
        UIApplication.shared.open(url)
    }
    private func resumeCamera(_ token: UInt64) {
        identityModel.selectProbe(value: nil)
        pauseCamera(); model.cancel()
        let pending = Array(bridge.jobs.values)
        cameraStart = Task { @MainActor in
            // Cancellation does not mean Vision has released its input or admission slot.
            for task in pending { await task.value }
            guard !Task.isCancelled, cameraLifecycle.canStart(token) else { return }
            replaceFrameQueue()
            running = true; camera.start()
        }
    }
    private func replaceFrameQueue() {
        admissionLock.lock(); frames = LatestFrameQueue<CameraFrame>(); admissionLock.unlock()
    }
    private func offerFrame(_ image: CGImage) {
        // Called by capture serial queue; synchronous 1+1 admission retains no per-frame Task backlog.
        admissionLock.lock(); let queue = frames; admissionLock.unlock()
        queue.offer(CameraFrame(image))
        if let frame = queue.next() { DispatchQueue.main.async { [weak self] in self?.consume(frame, queue: queue) } }
    }
    private func consume(_ frame: CameraFrame, queue: LatestFrameQueue<CameraFrame>) {
        guard running, queue === frames else { queue.complete(); return }
        latestIdentityCameraFrame = frame
        image = UIImage(cgImage: frame.image); let id = UUID().uuidString; bridge.inputs[id] = IOSFaceBridge.InputEntry(image: frame.image, finished: { [weak self] in self?.finishFrame(queue: queue) }); model.detectFrame(inputId: id, selection: BridgeBackendSelection(backendId: nil))
    }
    private func finishFrame(queue: LatestFrameQueue<CameraFrame>) {
        queue.complete()
        if running, queue === frames, let next = queue.next() { consume(next, queue: queue) }
    }
    func stopCamera() {
        livenessCapture?.cancel()
        identityModel.cancel()
        latestIdentityCameraFrame = nil
        cameraLifecycle.stop(); photoSubmission.cancel(); pauseCamera()
    }
    private func pauseCamera() {
        cameraStart?.cancel(); cameraStart = nil
        running = false; camera.stop(); frames.close(); bridge.stopTasks(); benchmark?.cancel(); benchmark = nil
    }
    func submitLivenessFrame() {
        let source = running ? latestIdentityCameraFrame?.image : image?.cgImage
        guard let source, let sessionId = identityModel.livenessSessionId, livenessCapture == nil else { return }
        let timestamp = running ? latestIdentityCameraFrame?.timestampNanos ?? 0 : Int64(clamping: DispatchTime.now().uptimeNanoseconds)
        let challenge = identityModel.livenessChallenge
        livenessCapture = Task { @MainActor in
            defer { livenessCapture = nil }
            let array = await Task.detached { () -> KotlinByteArray? in
                guard let bytes = UIImage(cgImage: source).pngData() else { return nil }
                let array = KotlinByteArray(size: Int32(bytes.count))
                for (index, value) in bytes.enumerated() { array.set(index: Int32(index), value: Int8(bitPattern: value)) }
                return array
            }.value
            guard !Task.isCancelled else { return }
            guard let array else { message = "无法编码活体帧"; return }
            let accepted = identityModel.submitLivenessFrame(frame: IdentityLivenessFrame(encodedFrame: array, timestampNanos: timestamp, revision: "demo-upright-frame-v1", challenge: challenge), sessionId: sessionId)
            message = accepted ? "活体帧已接收" : "活体帧未接收"
        }
    }
    func runBenchmark() {
        stopCamera(); model.cancel()
        guard let cg = image?.cgImage else { message = "先选择图片"; return }
        benchmark = Task { @MainActor in
            do {
                let pending = Array(bridge.jobs.values); for task in pending { await task.value }; try Task.checkCancellation()
                var warm: [Double] = []; var inference: [Double] = []
                for index in 0..<35 {
                    try Task.checkCancellation(); let start = DispatchTime.now().uptimeNanoseconds
                    _ = try await bridge.detector.detect(input: ImageInput(image: cg), options: FaceOptions())
                    let elapsed = Double(DispatchTime.now().uptimeNanoseconds-start)/1_000_000
                    if index < 5 { warm.append(elapsed) } else { inference.append(elapsed) }
                }
                let bytes = image?.pngData() ?? Data()
                let hash = SHA256.hash(data: bytes).map { String(format:"%02x", $0) }.joined()
                var system = utsname(); uname(&system)
                let device = withUnsafePointer(to: &system.machine) { $0.withMemoryRebound(to: CChar.self, capacity: 1) { String(cString: $0) } }
                #if targetEnvironment(simulator)
                let validation = "simulator"
                #else
                let validation = "device"
                #endif
                let run: [String: Any] = ["schemaVersion":1,"runId":UUID().uuidString,"device":device,"OS":UIDevice.current.systemVersion,"sdkCommit":Bundle.main.object(forInfoDictionaryKey:"ODAISDKCommit") as? String ?? "working-tree", "capability":"face","actualBackend":bridge.backendId(),"modelRevision":NSNull(),"preprocessRevision":"upright-v1","fixtureHash":hash,"units":"ms","timingsMs":["coldStart":[],"modelLoad":[],"warmup":warm,"inference":inference],"quality":[:],"peakMemory":NSNull(),"energy":NSNull(),"energyStatus":"unmeasured","thermal":String(describing: ProcessInfo.processInfo.thermalState),"measurementMethod":"native-detect-monotonic-includes-preprocess","validationStatus":validation]
                let data = try JSONSerialization.data(withJSONObject: run, options: [.prettyPrinted,.sortedKeys])
                report = String(data: data, encoding: .utf8)
                #if DEBUG
                if ProcessInfo.processInfo.arguments.contains("--smoke-benchmark") {
                    let documents = try FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                    try data.write(to: documents.appendingPathComponent("benchmark-smoke.json"), options: .atomic)
                }
                #endif
                message = "Benchmark 完成：5 次预热，30 次采样"
            } catch { message = error is CancellationError ? "Benchmark 已取消" : String(describing: error) }
        }
    }
    func exportReport(json: String) {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent("odai-benchmark-\(UUID().uuidString).json")
        do { try Data(json.utf8).write(to: file); let share = UIActivityViewController(activityItems: [file], applicationActivities: nil); share.popoverPresentationController?.sourceView = presenter()?.view; presenter()?.present(share, animated: true) }
        catch { message = String(describing: error) }
    }
    deinit {
        NotificationCenter.default.removeObserver(self)
        #if ODAI_VIDEO_DEMO
        let video = videoHost
        Task { @MainActor in video?.close() }
        #endif
        #if ODAI_SPEECH_DEMO
        let speech = speechHost
        Task { @MainActor in speech?.close() }
        #endif
    }
}
