// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "OnDeviceAIKit", platforms: [.iOS(.v15), .macOS(.v12)], products: [
    .library(name: "ODAIKitCore", targets: ["ODAIKitCore"]),
    .library(name: "ODAIKitModelStore", targets: ["ODAIKitModelStore"]),
    .library(name: "ODAIKitFace", targets: ["ODAIKitFace"]),
    .library(name: "ODAIKitAppleVision", targets: ["ODAIKitAppleVision"]),
    .library(name: "ODAIKitSpeech", targets: ["ODAIKitSpeech"]),
    .library(name: "ODAIKitVideo", targets: ["ODAIKitVideo"]),
    .library(name: "ODAIKitAppleVideo", targets: ["ODAIKitAppleVideo"]),
    .library(name: "ODAIKitAppleSpeech", targets: ["ODAIKitAppleSpeech"]),
    .library(name: "ODAIKitIdentity", targets: ["ODAIKitIdentity"])
], targets: [
    .target(name: "ODAIKitCore"),
    .target(name: "ODAIKitIdentity", dependencies: ["ODAIKitCore", "ODAIKitModelStore"]),
    .target(name: "ODAIKitModelStore", dependencies: ["ODAIKitCore"]),
    .target(name: "ODAIKitFace", dependencies: ["ODAIKitCore"]),
    .target(name: "ODAIKitAppleVision", dependencies: ["ODAIKitFace", "ODAIKitCore"]),
    .target(name: "ODAIKitSpeech", dependencies: ["ODAIKitCore"]),
    .target(name: "ODAIKitVideo", dependencies: ["ODAIKitCore"]),
    .testTarget(name: "ODAIKitVideoTests", dependencies: ["ODAIKitVideo"]),
    .target(name: "ODAIKitAppleVideo", dependencies: ["ODAIKitCore", "ODAIKitVideo"]),
    .testTarget(name: "ODAIKitAppleVideoTests", dependencies: ["ODAIKitAppleVideo", "ODAIKitVideo"]),
    .target(name: "ODAIKitAppleSpeech", dependencies: ["ODAIKitSpeech", "ODAIKitCore"]),
    .testTarget(name: "ODAIKitFaceTests", dependencies: ["ODAIKitFace", "ODAIKitAppleVision"]),
    .testTarget(name: "ODAIKitIdentityTests", dependencies: ["ODAIKitIdentity", "ODAIKitModelStore"]),
    .testTarget(name: "ODAIKitCoreTests", dependencies: ["ODAIKitCore"]),
    .testTarget(name: "ODAIKitModelStoreTests", dependencies: ["ODAIKitModelStore"]),
    .testTarget(name: "ODAIKitSpeechTests", dependencies: ["ODAIKitSpeech", "ODAIKitAppleSpeech"])
])
