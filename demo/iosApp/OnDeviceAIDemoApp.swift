import SwiftUI
import ODAIKitDemo
import ODAIKitFace
struct SharedControls: UIViewControllerRepresentable {
    let controller: DemoController
    func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.mainViewController(model: controller.model, actions: controller) }
    func updateUIViewController(_ view: UIViewController, context: Context) {}
}
@main struct OnDeviceAIDemoApp: App {
    @StateObject private var controller = DemoController()
    var body: some Scene {
        WindowGroup {
            VStack(spacing: 0) {
                GeometryReader { geometry in
                    ZStack {
                        Color.black
                        if let image = controller.image, let cg = image.cgImage {
                            Image(uiImage: image).resizable().scaledToFill().frame(width: geometry.size.width,height: geometry.size.height).clipped()
                            if let transform = try? ImageGeometry.preview(imageWidth: cg.width,imageHeight: cg.height,viewWidth: geometry.size.width,viewHeight: geometry.size.height,mirrored: false) {
                                ForEach(Array(controller.faces.enumerated()), id: \.offset) { _,face in
                                    let point = transform.apply(FacePoint(x: face.box.x, y: face.box.y))
                                    Rectangle().stroke(Color.green,lineWidth: 2).frame(width: face.box.width * abs(transform.scaleX),height: face.box.height * transform.scaleY).position(x: point.x+face.box.width*abs(transform.scaleX)/2,y: point.y+face.box.height*transform.scaleY/2)
                                }
                            }
                        } else { Text("选择图片或启动相机").foregroundColor(.white) }
                    }.clipped()
                }
                SharedControls(controller: controller).frame(height: 280)
                if let message = controller.message { Text(message).font(.caption) }
                if controller.cameraPermissionDenied {
                    Button("打开设置") { controller.openCameraSettings() }
                        .accessibilityIdentifier("camera-permission-settings")
                }
                if let report = controller.report { Button("导出 Benchmark JSON") { controller.exportReport(json: report) } }
                #if DEBUG
                if ProcessInfo.processInfo.arguments.contains("--ui-camera-test") {
                    Text(String(controller.cameraFrameCount)).accessibilityIdentifier("camera-frame-count")
                }
                #endif
            }.onDisappear { controller.stopCamera() }
            .onAppear {
                if ProcessInfo.processInfo.arguments.contains("--smoke-blank") {
                    let renderer = UIGraphicsImageRenderer(size: CGSize(width: 64, height: 64))
                    controller.useImage(renderer.image { context in UIColor.white.setFill(); context.fill(CGRect(x: 0, y: 0, width: 64, height: 64)) }, smokeFixture: true)
                }
            }
        }
    }
}
