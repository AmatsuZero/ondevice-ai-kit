import AVFoundation
import CoreImage
final class CameraFrame: Sendable {
    let image: CGImage
    let timestampNanos: Int64
    init(_ image: CGImage) {
        self.image = image
        timestampNanos = Int64(clamping: DispatchTime.now().uptimeNanoseconds)
    }
}

final class CameraSource: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate, Sendable {
    private let callbacks: CameraFrameCallbacks
    private let frameDelegate: CameraFrameDelegate
    private let commands: AsyncStream<CameraCommand>.Continuation
    private let worker: Task<Void, Never>

    var onFrame: (@Sendable (CGImage) -> Void)? {
        get { callbacks.current }
        set { callbacks.replace(with: newValue) }
    }

    init(session: sending AVCaptureSession = AVCaptureSession(),
         frameQueue: DispatchQueue = DispatchQueue(label: "io.ondeviceai.camera.frames")) {
        let callbacks = CameraFrameCallbacks()
        let frameDelegate = CameraFrameDelegate(callbacks: callbacks)
        let mailbox = AsyncStream<CameraCommand>.makeStream()
        let capture = CameraCaptureSession(session: session, frameQueue: frameQueue, delegate: frameDelegate)
        self.callbacks = callbacks
        self.frameDelegate = frameDelegate
        commands = mailbox.continuation
        worker = Task { await capture.consume(mailbox.stream) }
        super.init()
    }

    // One mailbox consumer preserves caller order without one Task per command.
    func start() { commands.yield(.start) }
    func stop() { commands.yield(.stop) }

    /// Terminal teardown. Pause/resume uses stop(); close also drains frame callbacks.
    func close() async {
        await withCheckedContinuation { continuation in
            commands.yield(.close(continuation))
        }
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        frameDelegate.captureOutput(output, didOutput: sampleBuffer, from: connection)
    }

    deinit { commands.finish() }
}

private enum CameraCommand: Sendable {
    case start, stop
    case close(CheckedContinuation<Void, Never>)
}

private final class CameraExecutor: SerialExecutor {
    private let queue = DispatchQueue(label: "io.ondeviceai.camera")
    func asUnownedSerialExecutor() -> UnownedSerialExecutor {
        UnownedSerialExecutor(ordinary: self)
    }
    // UnownedJob supports the iOS15 baseline; callbacks never assume actor isolation.
    func enqueue(_ job: UnownedJob) {
        queue.async { job.runSynchronously(on: self.asUnownedSerialExecutor()) }
    }
}

private actor CameraCaptureSession {
    private let executor = CameraExecutor()
    nonisolated var unownedExecutor: UnownedSerialExecutor { executor.asUnownedSerialExecutor() }
    private let session: AVCaptureSession
    private let frameQueue: DispatchQueue
    private let delegate: CameraFrameDelegate
    private var closed = false

    init(session: sending AVCaptureSession, frameQueue: DispatchQueue, delegate: CameraFrameDelegate) {
        self.session = session
        self.frameQueue = frameQueue
        self.delegate = delegate
    }

    func consume(_ commands: AsyncStream<CameraCommand>) async {
        for await command in commands {
            switch command {
            case .start: start()
            case .stop: stop()
            case .close(let continuation): close(); continuation.resume()
            }
        }
        // The worker does not retain the facade. Dropping it finishes the mailbox.
        close()
    }

    private func start() {
        guard !closed, !session.isRunning else { return }
        if session.inputs.isEmpty {
            guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front),
                  let input = try? AVCaptureDeviceInput(device: camera) else { return }
            session.beginConfiguration()
            session.sessionPreset = .vga640x480
            guard session.canAddInput(input) else { session.commitConfiguration(); return }
            session.addInput(input)
            let output = AVCaptureVideoDataOutput()
            output.alwaysDiscardsLateVideoFrames = true
            output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
            output.setSampleBufferDelegate(delegate, queue: frameQueue)
            guard session.canAddOutput(output) else { session.removeInput(input); session.commitConfiguration(); return }
            session.addOutput(output)
            if let connection = output.connection(with: .video) {
                if connection.isVideoOrientationSupported { connection.videoOrientation = .portrait }
                if connection.isVideoMirroringSupported { connection.automaticallyAdjustsVideoMirroring = false; connection.isVideoMirrored = false }
            }
            session.commitConfiguration()
        }
        session.startRunning()
    }

    private func stop() {
        if session.isRunning { session.stopRunning() }
    }

    private func close() {
        guard !closed else { return }
        closed = true
        delegate.callbacks.close()
        stop()
        session.beginConfiguration()
        for output in session.outputs {
            (output as? AVCaptureVideoDataOutput)?.setSampleBufferDelegate(nil, queue: nil)
            session.removeOutput(output)
        }
        for input in session.inputs { session.removeInput(input) }
        session.commitConfiguration()
        // No callback waits on this actor. Seal admission before draining frames.
        frameQueue.sync { delegate.clearCaches() }
    }
}

// iOS15 has no standard Mutex. Only this slot's two fields require a manually
// checked lock invariant; the session/terminal state above is actor-isolated.
// Payloads are Sendable, and invocation/capture destruction happen outside lock.
private final class CameraFrameCallbacks: @unchecked Sendable {
    private let lock = NSLock()
    private var closed = false
    private var callback: (@Sendable (CGImage) -> Void)?

    var current: (@Sendable (CGImage) -> Void)? {
        lock.lock(); defer { lock.unlock() }
        return callback
    }

    func replace(with newValue: (@Sendable (CGImage) -> Void)?) {
        lock.lock()
        guard !closed else { lock.unlock(); return }
        let previous = callback
        callback = newValue
        lock.unlock()
        withExtendedLifetime(previous) {}
    }

    func close() {
        lock.lock()
        closed = true
        let previous = callback
        callback = nil
        lock.unlock()
        withExtendedLifetime(previous) {}
    }
}

private final class CameraFrameDelegate: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate, Sendable {
    let callbacks: CameraFrameCallbacks
    private let context = CIContext()

    init(callbacks: CameraFrameCallbacks) { self.callbacks = callbacks }
    func clearCaches() { context.clearCaches() }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let image = CIImage(cvPixelBuffer: buffer)
        if let image = context.createCGImage(image, from: image.extent) { callbacks.current?(image) }
    }
}
