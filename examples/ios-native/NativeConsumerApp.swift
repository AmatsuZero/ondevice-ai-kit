import SwiftUI
import ODAIKitCore
import ODAIKitFace
import ODAIKitAppleVision
@main struct NativeConsumerApp: App {
    @State private var message = "OnDevice AI Kit native consumer"
    var body: some Scene { WindowGroup { Text(message).task {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 64, height: 64))
        let blank = renderer.image { context in UIColor.white.setFill(); context.fill(CGRect(x: 0, y: 0, width: 64, height: 64)) }
        guard let image = blank.cgImage else { return }
        let detector = VisionFaceDetector()
        do { let result = try await detector.detect(input: ImageInput(image: image)); message = "Native SDK: faces=\(result.faces.count), backend=\(result.backendId)" }
        catch { message = String(describing: error) }
        await detector.close()
    } } }
}
