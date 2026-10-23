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
    func generateVideo(directory: URL, request: MultimodalVideoRequest,
                       observer: (@Sendable (MLXVLMGenerationEvent) -> Void)?,
                       update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> String {
        #if os(iOS)
        try Task.checkCancellation()
        let snapshot: MLXVLMVideoSnapshot
        do { snapshot = try await MLXVLMVideoSnapshot.load(request.videoFile) }
        catch { try Task.checkCancellation(); throw MultimodalClientError.invalidInput("video does not meet MLXVLM profile") }
        defer {
            snapshot.close()
            observer?(.videoSnapshotClosed(removed: !FileManager.default.fileExists(atPath: snapshot.file.deletingLastPathComponent().path)))
        }
        do {
            observer?(.videoDecoded(try MLXVLMVideoMetadata(snapshot: snapshot)))
            try Task.checkCancellation()
            let started = ContinuousClock.now
            let model = try await VLMModelFactory.shared.loadContainer(from: directory, using: LocalTokenizerLoader())
            observer?(.modelLoaded(durationMs: Self.milliseconds(since: started)))
            try Task.checkCancellation()
            let configuration = try JSONDecoder().decode(SmolVLMProcessorConfiguration.self,
                from: Data(contentsOf: directory.appendingPathComponent("preprocessor_config.json")))
            return try await model.perform { context in
                observer?(.videoPreparationStarted)
                try Task.checkCancellation()
                let started = ContinuousClock.now
                let prepared = try await MLXVLMVideoInputAdapter.prepare(frames: snapshot.frames,
                    clipDuration: snapshot.duration, prompt: request.prompt, configuration: configuration,
                    tokenizer: context.tokenizer)
                observer?(.videoPrepared(durationMs: Self.milliseconds(since: started), frameCount: prepared.timestamps.count))
                try Task.checkCancellation()
                return try await Self.generate(context: context, input: prepared.input,
                    maxOutputTokens: request.maxOutputTokens, update: update)
            }
        } catch is MLXVLMVideoInputAdapterError {
            try Task.checkCancellation()
            throw MultimodalClientError.invalidInput("video input preparation rejected")
        } catch { try Task.checkCancellation(); throw MLXVLMBackendError.runtimeFailure }
        #else
        throw MultimodalClientError.backendUnavailable(SmolVLM2Model.backendId, .unsupportedDevice)
        #endif
    }

    func generate(directory: URL, request: MultimodalRequest,
                  observer: (@Sendable (MLXVLMGenerationEvent) -> Void)?,
                  update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> String {
        #if os(iOS)
        try Task.checkCancellation()
        let snapshot = try MLXVLMImageSnapshot.load(request.imageFile)
        guard let source = CGImageSourceCreateWithData(snapshot.data as CFData, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary) else {
            throw MLXVLMBackendError.invalidImage
        }
        let loadStarted = ContinuousClock.now
        let model: ModelContainer
        do { model = try await VLMModelFactory.shared.loadContainer(from: directory, using: LocalTokenizerLoader()) }
        catch { try Task.checkCancellation(); throw MLXVLMBackendError.runtimeFailure }
        let loadElapsed = loadStarted.duration(to: .now)
        let loadMs = Double(loadElapsed.components.seconds) * 1000 + Double(loadElapsed.components.attoseconds) / 1e15
        observer?(.modelLoaded(durationMs: loadMs))
        try Task.checkCancellation()
        do {
            return try await model.perform { context in
                var input = UserInput(prompt: request.prompt,
                    images: [.ciImage(CIImage(cgImage: image).oriented(forExifOrientation: snapshot.orientation))])
                input.processing = .init(resize: CGSize(width: 512, height: 512))
                let prepared = try await context.processor.prepare(input: input)
                try Task.checkCancellation()
                return try await Self.generate(context: context, input: prepared,
                    maxOutputTokens: request.maxOutputTokens, update: update)
            }
        } catch { try Task.checkCancellation(); throw MLXVLMBackendError.runtimeFailure }
        #else
        throw MultimodalClientError.backendUnavailable(SmolVLM2Model.backendId, .unsupportedDevice)
        #endif
    }

    #if os(iOS)
    private static func milliseconds(since started: ContinuousClock.Instant) -> Double {
        let elapsed = started.duration(to: .now)
        return Double(elapsed.components.seconds) * 1000 + Double(elapsed.components.attoseconds) / 1e15
    }

    private static func generate(context: ModelContext, input: LMInput, maxOutputTokens: Int,
                                 update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> String {
        try Task.checkCancellation()
        let parameters = GenerateParameters(maxTokens: maxOutputTokens, temperature: 0)
        let iterator = try TokenIterator(input: input, model: context.model, parameters: parameters)
        let (stream, generation) = generateTask(promptTokenCount: input.text.tokens.size,
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
    #endif

}
