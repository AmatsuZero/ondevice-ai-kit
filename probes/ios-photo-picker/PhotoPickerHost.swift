import SwiftUI

@main struct PhotoPickerHost: App {
    var body: some Scene { WindowGroup { PhotoPickerView() } }
}

private struct PhotoPickerView: View {
    @StateObject private var controller = DemoController()
    @State private var signature = "no-selection"

    var body: some View {
        VStack(spacing: 20) {
            Button("Select synthetic image") { controller.selectPhoto() }
                .accessibilityIdentifier("select-synthetic-photo")
            Text(signature).accessibilityIdentifier("photo-pixel-signature")
        }
        .onReceive(controller.$image) { image in
            guard let image = image?.cgImage else { return }
            let xs = [image.width / 4, image.width * 3 / 4]
            let ys = [image.height / 4, image.height * 3 / 4]
            let corners = ys.flatMap { y in xs.map { x in color(image, x: x, y: y) } }
            signature = "\(image.width)x\(image.height):" + corners.joined(separator: ",")
        }
    }

    // Sample source coordinates with y measured from the top. This observes the
    // actual normalized output; it performs no EXIF transform or decode itself.
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
