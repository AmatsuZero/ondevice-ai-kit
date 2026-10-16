import AVFoundation
import CoreImage
import ODAIKitCore
final class CameraFrame: @unchecked Sendable { let image: CGImage; init(_ image: CGImage) { self.image = image } }
final class CameraSource: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    private let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "io.ondeviceai.camera")
    private let frameQueue = DispatchQueue(label: "io.ondeviceai.camera.frames")
    private let context = CIContext()
    var onFrame: ((CGImage) -> Void)?
    func start() {
        queue.async {
            guard !self.session.isRunning else { return }
            if self.session.inputs.isEmpty {
                guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front), let input = try? AVCaptureDeviceInput(device: camera) else { return }
                self.session.beginConfiguration(); self.session.sessionPreset = .vga640x480
                guard self.session.canAddInput(input) else { self.session.commitConfiguration(); return }; self.session.addInput(input)
                let output = AVCaptureVideoDataOutput(); output.alwaysDiscardsLateVideoFrames = true
                output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
                output.setSampleBufferDelegate(self, queue: self.frameQueue)
                guard self.session.canAddOutput(output) else { self.session.removeInput(input); self.session.commitConfiguration(); return }; self.session.addOutput(output)
                if let connection = output.connection(with: .video) {
                    if connection.isVideoOrientationSupported { connection.videoOrientation = .portrait }
                    if connection.isVideoMirroringSupported { connection.automaticallyAdjustsVideoMirroring = false; connection.isVideoMirrored = false }
                }
                self.session.commitConfiguration()
            }
            self.session.startRunning()
        }
    }
    func stop() { queue.async { if self.session.isRunning { self.session.stopRunning() } } }
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let image = CIImage(cvPixelBuffer: buffer)
        if let image = context.createCGImage(image, from: image.extent) { onFrame?(image) }
    }
}
