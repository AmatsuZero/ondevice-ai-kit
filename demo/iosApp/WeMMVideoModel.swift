import Foundation
import ODAIKitMultimodal
import ODAIKitModelStore

@available(iOS 16.4, macOS 13, *)
enum WeMMVideoModel {
    static let backendId = "llama.wemm-video-2b"
    static let upstreamModelId = "tencent/WeMM-Video-2B-GGUF"
    static let pinnedRevision = "395e99f987a73915273943d4510e23004df7cede"
#if targetEnvironment(simulator)
    static let runtimeRevision = "llama.cpp-88aaf325a7026ac1127d0897394b0a3d6413dc2a-wemm-mixed-prefill-microbatch-128-v1-simulator-cpu"
#else
    static let runtimeRevision = "llama.cpp-88aaf325a7026ac1127d0897394b0a3d6413dc2a-wemm-mixed-prefill-microbatch-128-v1"
#endif

    static func manifest() throws -> ModelManifest {
        let languageBytes: Int64 = 2_512_305_792
        let projectorBytes: Int64 = 668_226_688
        let totalBytes = languageBytes + projectorBytes
        let base = "https://huggingface.co/\(upstreamModelId)"

        guard let upstreamURL = URL(string: base),
              let languageURL = URL(string: "\(base)/resolve/\(pinnedRevision)/language.gguf"),
              let projectorURL = URL(string: "\(base)/resolve/\(pinnedRevision)/mmproj.gguf"),
              let licenseURL = URL(string: "\(base)/blob/\(pinnedRevision)/LICENSE") else {
            throw ModelStoreError.invalidManifest
        }

        return ModelManifest(
            modelId: "wemm-video-2b-gguf",
            revision: pinnedRevision,
            upstreamURL: upstreamURL,
            upstreamRevision: pinnedRevision,
            capability: "multimodal",
            backendId: backendId,
            backendVersion: VersionRange(minimum: "1.0.0", maximumExclusive: "2.0.0"),
            minimumOS: "16.4",
            architectures: ["arm64"],
            formatVersion: "gguf-q4",
            files: [
                ModelFile(
                    path: "language.gguf",
                    url: languageURL,
                    bytes: languageBytes,
                    sha256: "13dd7d543d2192c3acbcac902805a30ec853e1548705eb11f0e12e04e053f179"
                ),
                ModelFile(
                    path: "mmproj.gguf",
                    url: projectorURL,
                    bytes: projectorBytes,
                    sha256: "817c7d64075056b30b00deb5fa2cabf674b4aec39d62c144faeb3a0e99ced481"
                )
            ],
            processing: ModelProcessing(
                inputSignature: "8 ordered video frames, quiet video, and a question prompt",
                outputSignature: "free-form UTF-8 text",
                preprocessRevision: "wemm-video-8-ordered-frames-v1",
                tokenizerRevision: nil,
                promptRevision: "closed-thinking-video-prompt-v1",
                maxInputElements: 8
            ),
            resources: ModelResources(
                downloadBytes: totalBytes,
                temporaryBytes: totalBytes,
                installedBytesLimit: totalBytes
            ),
            licenseIdentifier: "Apache-2.0",
            licenseURL: licenseURL,
            attribution: "Tencent WeMM-Video and llama.cpp contributors.",
            redistribution: "Local import only. Review upstream Apache-2.0 and third-party terms before redistribution; no distribution approval is claimed."
        )
    }

    static func identity() throws -> MultimodalModelIdentity {
        try MultimodalModelIdentity(
            modelId: upstreamModelId,
            modelRevision: pinnedRevision,
            runtimeRevision: runtimeRevision
        )
    }
}
