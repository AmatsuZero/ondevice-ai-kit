// swift-tools-version: 5.9
import PackageDescription
import Foundation

// Explicit host-only contract lane; it never provides or validates the native runtime.
let contractsOnly = ProcessInfo.processInfo.environment["ODAI_OPENCV_CONTRACTS_ONLY"] == "1"
let nativeTargets: [Target] = contractsOnly ? [] : [
    .binaryTarget(name: "opencv2", path: "Vendor/opencv2.xcframework"),
    .target(name: "ODAIOpenCVNative", dependencies: ["opencv2"], publicHeadersPath: "include",
        cxxSettings: [.unsafeFlags(["-fobjc-arc"])],
        linkerSettings: [.linkedFramework("Accelerate"), .linkedFramework("AVFoundation"),
            .linkedFramework("CoreMedia"), .linkedFramework("CoreVideo"),
            .linkedFramework("CoreGraphics"), .linkedFramework("Foundation"),
            .linkedFramework("UIKit"), .linkedLibrary("z")])
]
let nativeDependency: [Target.Dependency] = contractsOnly ? [] : [
    .target(name: "ODAIOpenCVNative", condition: .when(platforms: [.iOS]))
]

let package = Package(name: "OnDeviceAIKitOpenCV", platforms: [.iOS(.v15), .macOS(.v12)],
    products: [.library(name: "ODAIKitOpenCVIdentity", targets: ["ODAIKitOpenCVIdentity"])],
    dependencies: [.package(path: "../apple")],
    targets: nativeTargets + [
        .target(name: "ODAIKitOpenCVIdentity", dependencies: nativeDependency + [
            .product(name: "ODAIKitCore", package: "apple"),
            .product(name: "ODAIKitIdentity", package: "apple"),
            .product(name: "ODAIKitModelStore", package: "apple")]),
        .testTarget(name: "ODAIKitOpenCVIdentityTests", dependencies: ["ODAIKitOpenCVIdentity"])
    ], cxxLanguageStandard: .cxx17)
