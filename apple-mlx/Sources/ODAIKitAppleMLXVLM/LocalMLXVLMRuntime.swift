import Foundation
import ODAIKitMultimodal
#if os(iOS)
import CoreImage
import ImageIO
import MLXLMCommon
import MLXVLM
import Tokenizers
#endif

struct LocalMLXVLMRuntime: MLXVLMRuntime {
    func generate(directory: URL, request: MultimodalRequest,
                  update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> String {
        #if os(iOS)
        try Task.checkCancellation()
        let snapshot = try MLXVLMImageSnapshot.load(request.imageFile)
        guard let source = CGImageSourceCreateWithData(snapshot.data as CFData, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary) else {
            throw MLXVLMBackendError.invalidImage
        }
        let model: ModelContainer
        do { model = try await VLMModelFactory.shared.loadContainer(from: directory, using: LocalTokenizerLoader()) }
        catch { try Task.checkCancellation(); throw MLXVLMBackendError.runtimeFailure }
        try Task.checkCancellation()
        do {
            return try await model.perform { context in
                var input = UserInput(prompt: request.prompt,
                    images: [.ciImage(CIImage(cgImage: image).oriented(forExifOrientation: snapshot.orientation))])
                input.processing = .init(resize: CGSize(width: 512, height: 512))
                let prepared = try await context.processor.prepare(input: input)
                try Task.checkCancellation()
                let parameters = GenerateParameters(maxTokens: request.maxOutputTokens, temperature: 0)
                let iterator = try TokenIterator(input: prepared, model: context.model, parameters: parameters)
                let (stream, generation) = generateTask(promptTokenCount: prepared.text.tokens.size,
                    modelConfiguration: context.configuration, tokenizer: context.tokenizer, iterator: iterator)
                return try await withTaskCancellationHandler {
                    var text = ""
                    var sequence = 0
                    for await value in stream {
                        if Task.isCancelled { generation.cancel(); break }
                        if let chunk = value.chunk {
                            text += chunk
                            update?(try .init(sequence: sequence, text: text))
                            sequence += 1
                        }
                    }
                    // Stream termination is not a producer join.
                    await generation.value
                    try Task.checkCancellation()
                    return text
                } onCancel: { generation.cancel() }
            }
        } catch { try Task.checkCancellation(); throw MLXVLMBackendError.runtimeFailure }
        #else
        throw MultimodalClientError.backendUnavailable(SmolVLM2Model.backendId, .unsupportedDevice)
        #endif
    }
}
