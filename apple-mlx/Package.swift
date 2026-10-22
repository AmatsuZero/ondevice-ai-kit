// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "OnDeviceAIKitAppleMLX",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [.library(name: "ODAIKitAppleMLXVLM", targets: ["ODAIKitAppleMLXVLM"])],
    dependencies: [.package(path: "../apple")],
    targets: [
        .target(name: "ODAIKitAppleMLXVLM", dependencies: [
            .product(name: "ODAIKitCore", package: "apple"),
            .product(name: "ODAIKitMultimodal", package: "apple"),
            .product(name: "ODAIKitModelStore", package: "apple")
        ], resources: [.copy("Resources/smolvlm2-model.json")]),
        .testTarget(name: "ODAIKitAppleMLXVLMTests", dependencies: ["ODAIKitAppleMLXVLM"])
    ]
)
