// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "OnDeviceAIKitAppleMLX",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [.library(name: "ODAIKitAppleMLXVLM", targets: ["ODAIKitAppleMLXVLM"])],
    dependencies: [
        .package(path: "../apple"),
        .package(url: "https://github.com/ml-explore/mlx-swift-lm.git", revision: "6e338e5df49424fc0db7c6d58184d622f92ed18a"),
        .package(url: "https://github.com/ml-explore/mlx-swift", exact: "0.32.3"),
        .package(url: "https://github.com/huggingface/swift-transformers.git", revision: "b38443e44d93eca770f2eb68e2a4d0fa100f9aa2")
    ],
    targets: [
        .target(name: "ODAIKitAppleMLXVLM", dependencies: [
            .product(name: "ODAIKitCore", package: "apple"),
            .product(name: "ODAIKitMultimodal", package: "apple"),
            .product(name: "ODAIKitModelStore", package: "apple"),
            .product(name: "MLXVLM", package: "mlx-swift-lm"),
            .product(name: "MLXLMCommon", package: "mlx-swift-lm"),
            .product(name: "MLX", package: "mlx-swift"),
            .product(name: "Tokenizers", package: "swift-transformers")
        ], resources: [.copy("Resources/smolvlm2-model.json")]),
        .testTarget(name: "ODAIKitAppleMLXVLMTests", dependencies: ["ODAIKitAppleMLXVLM",
                    .product(name: "MLXVLM", package: "mlx-swift-lm"),
                    .product(name: "MLXLMCommon", package: "mlx-swift-lm"),
                    .product(name: "MLX", package: "mlx-swift")],
                    resources: [.copy("Fixtures")])
    ]
)
