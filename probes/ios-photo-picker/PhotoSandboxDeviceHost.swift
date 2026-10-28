import SwiftUI
import UIKit

@main struct PhotoSandboxDeviceHost: App {
    var body: some Scene { WindowGroup { PhotoSandboxView() } }
}

private struct PhotoSandboxView: View {
    @StateObject private var controller = DemoController()
    @State private var signature = "no-selection"
    @State private var vision = "not-run"
    @State private var status = "not-started"
    @State private var hooked = false

    var body: some View {
        VStack(spacing: 12) {
            Text(status).accessibilityIdentifier("sandbox-status")
            Text(signature).accessibilityIdentifier("photo-pixel-signature")
            Text(vision).accessibilityIdentifier("vision-result")
        }
        .onAppear {
            if !hooked {
                hooked = true
                let existing = controller.bridge.onResult
                controller.bridge.onResult = { result in
                    existing?(result)
                    Task { @MainActor in
                        vision = "\(result.backendId):\(result.orientedWidth)x\(result.orientedHeight):faces=\(result.faces.count)"
                    }
                }
            }
            runSandboxFixture()
        }
        .onReceive(controller.$image) { image in
            guard let image = image?.cgImage else { return }
            let xs = [image.width / 4, image.width * 3 / 4]
            let ys = [image.height / 4, image.height * 3 / 4]
            let corners = ys.flatMap { y in xs.map { x in color(image, x: x, y: y) } }
            signature = "\(image.width)x\(image.height):" + corners.joined(separator: ",")
        }
    }

    private func runSandboxFixture() {
        let arguments = ProcessInfo.processInfo.arguments
        guard let index = arguments.firstIndex(of: "--sandbox-exif"), arguments.indices.contains(index + 1),
              let orientation = Int(arguments[index + 1]), [2, 6, 7].contains(orientation) else {
            status = "missing-sandbox-exif-argument"
            return
        }
        guard let url = Bundle.main.url(forResource: "exif-\(orientation)", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else {
            status = "missing-fixture-\(orientation)"
            return
        }
        status = "sandbox-\(orientation)"
        // This deliberately exercises the production controller's post-picker
        // normalization and bridge path without requesting Photos access.
        controller.useImage(image)
    }

    private func color(_ image: CGImage, x: Int, y: Int) -> String {
        var pixel = [UInt8](repeating: 0, count: 4)
        let context = CGContext(data: &pixel, width: 1, height: 1, bitsPerComponent: 8,
                                bytesPerRow: 4, space: CGColorSpaceCreateDeviceRGB(),
                                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.draw(image, in: CGRect(x: -x, y: y - image.height + 1,
                                       width: image.width, height: image.height))
        if pixel[0] < 32 && pixel[1] < 32 && pixel[2] < 32 { return "black" }
        if pixel[0] > 223 && pixel[1] > 223 && pixel[2] > 223 { return "white" }
        return "unexpected-\(pixel[0])-\(pixel[1])-\(pixel[2])"
    }
}
