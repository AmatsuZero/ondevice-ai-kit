// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "OnDeviceAIKit", platforms: [.iOS(.v15), .macOS(.v12)], products: [
    .library(name: "ODAIKitCore", targets: ["ODAIKitCore"]),
    .library(name: "ODAIKitModelStore", targets: ["ODAIKitModelStore"]),
    .library(name: "ODAIKitFace", targets: ["ODAIKitFace"]),
    .library(name: "ODAIKitAppleVision", targets: ["ODAIKitAppleVision"])
], targets: [
    .target(name: "ODAIKitCore"),
    .target(name: "ODAIKitModelStore", dependencies: ["ODAIKitCore"]),
    .target(name: "ODAIKitFace", dependencies: ["ODAIKitCore"]),
    .target(name: "ODAIKitAppleVision", dependencies: ["ODAIKitFace", "ODAIKitCore"]),
    .testTarget(name: "ODAIKitFaceTests", dependencies: ["ODAIKitFace", "ODAIKitAppleVision"]),
    .testTarget(name: "ODAIKitCoreTests", dependencies: ["ODAIKitCore"]),
    .testTarget(name: "ODAIKitModelStoreTests", dependencies: ["ODAIKitModelStore"])
])
