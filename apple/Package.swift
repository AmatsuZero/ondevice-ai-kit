// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "OnDeviceAIKit", platforms: [.iOS(.v15), .macOS(.v12)], products: [
    .library(name: "ODAIKitCore", targets: ["ODAIKitCore"]),
    .library(name: "ODAIKitModelStore", targets: ["ODAIKitModelStore"])
], targets: [
    .target(name: "ODAIKitCore"),
    .target(name: "ODAIKitModelStore", dependencies: ["ODAIKitCore"]),
    .testTarget(name: "ODAIKitCoreTests", dependencies: ["ODAIKitCore"]),
    .testTarget(name: "ODAIKitModelStoreTests", dependencies: ["ODAIKitModelStore"])
])
