import SwiftUI
import PhotosUI
import ImageIO
import UniformTypeIdentifiers
import AVFoundation
import CryptoKit
import ODAIKitDemo
import ODAIKitCore
import ODAIKitFace

final class DemoController: NSObject, ObservableObject, DemoActions, PHPickerViewControllerDelegate {
    let bridge = IOSFaceBridge()
    lazy var model = FaceViewModel(bridge: bridge)
    private let camera = CameraSource()
    private let admissionLock = NSLock()
    private var frames = LatestFrameQueue<CameraFrame>()
    private var running = false
    private var cameraLifecycle = CameraLifecycle(foreground: UIApplication.shared.applicationState == .active)
    private var cameraStart: Task<Void, Never>?
    private var benchmark: Task<Void, Never>?
    @Published var image: UIImage?
    @Published var faces: [FaceObservation] = []
    @Published var report: String?
    @Published var message: String?
    @Published var cameraFrameCount = 0
    override init() {
        super.init()
        bridge.onResult = { [weak self] result in
            self?.faces = result.faces
            if self?.running == true { self?.cameraFrameCount += 1 }
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--smoke-benchmark") { self?.runBenchmark() }
            #endif
        }
        camera.onFrame = { [weak self] image in self?.offerFrame(image) }
        NotificationCenter.default.addObserver(self, selector: #selector(background), name: UIApplication.willResignActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(foreground), name: UIApplication.didBecomeActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(memoryWarning), name: UIApplication.didReceiveMemoryWarningNotification, object: nil)
    }
    @objc private func background() { cameraLifecycle.pause(); pauseCamera(); model.cancel() }
    @objc private func foreground() {
        if let token = cameraLifecycle.activate(), AVCaptureDevice.authorizationStatus(for: .video) == .authorized { resumeCamera(token) }
    }
    @objc private func memoryWarning() { stopCamera(); model.cancel() }
    private func presenter() -> UIViewController? {
        var current = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController
        while let presented = current?.presentedViewController { current = presented }; return current
    }
    func selectPhoto() {
        stopCamera(); var config = PHPickerConfiguration(); config.filter = .images; config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config); picker.delegate = self; presenter()?.present(picker, animated: true)
    }
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let provider = results.first?.itemProvider else { return }
        provider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { [weak self] url, error in
            guard let url, let source = CGImageSourceCreateWithURL(url as CFURL, nil), let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, [kCGImageSourceCreateThumbnailFromImageAlways:true,kCGImageSourceCreateThumbnailWithTransform:true,kCGImageSourceThumbnailMaxPixelSize:2048] as CFDictionary) else { DispatchQueue.main.async { self?.message = error?.localizedDescription ?? "无法读取图片" }; return }
            DispatchQueue.main.async { self?.useImage(UIImage(cgImage: cg)) }
        }
    }
    func useImage(_ selected: UIImage, smokeFixture: Bool = false) {
        model.cancel()
        // Rendering applies UIImage EXIF orientation once; Vision subsequently receives .up.
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        let normalized = UIGraphicsImageRenderer(size: selected.size, format: format).image { _ in selected.draw(in: CGRect(origin: .zero, size: selected.size)) }
        guard let cg = normalized.cgImage else { return }
        image = normalized; faces = []; report = nil
        let id = UUID().uuidString
        bridge.inputs[id] = IOSFaceBridge.InputEntry(image: cg, finished: {}, fixture: smokeFixture ? "synthetic-white-64x64-upright-v1" : nil)
        model.detect(inputId: id)
    }
    func startCamera() {
        let token = cameraLifecycle.requestStart()
        AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
            DispatchQueue.main.async {
                guard let self else { return }
                guard self.cameraLifecycle.canStart(token) else { return }
                guard granted else { self.cameraLifecycle.stop(); self.model.permissionDenied(); return }
                self.resumeCamera(token)
            }
        }
    }
    private func resumeCamera(_ token: UInt64) {
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
        image = UIImage(cgImage: frame.image); let id = UUID().uuidString; bridge.inputs[id] = IOSFaceBridge.InputEntry(image: frame.image, finished: { [weak self] in self?.finishFrame(queue: queue) }); model.detectFrame(inputId: id)
    }
    private func finishFrame(queue: LatestFrameQueue<CameraFrame>) {
        queue.complete()
        if running, queue === frames, let next = queue.next() { consume(next, queue: queue) }
    }
    func stopCamera() {
        cameraLifecycle.stop(); pauseCamera()
    }
    private func pauseCamera() {
        cameraStart?.cancel(); cameraStart = nil
        running = false; camera.stop(); frames.close(); bridge.stopTasks(); benchmark?.cancel(); benchmark = nil
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
    deinit { NotificationCenter.default.removeObserver(self) }
}
