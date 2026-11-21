import XCTest
import CoreImage
import MLX
import MLXLMCommon
import MLXVLM
@testable import ODAIKitAppleMLXVLM

/// Opt-in offline tests of the real fixed tokenizer/template, never model weights.
final class MLXVLMVideoTokenizerTests: XCTestCase {
    func testUniformPreparationMatchesPinnedProcessorWithRealTokenizer() async throws {
        let tokenizer = try await localTokenizer()
        let snapshot = try await MLXVLMVideoSnapshot.load(videoFixture("video-sdr"))
        defer { snapshot.close() }
        let config = try videoConfiguration()
        let prepared = try await MLXVLMVideoInputAdapter.prepare(frames: snapshot.frames,
            clipDuration: snapshot.duration, prompt: "What changes?", configuration: config, tokenizer: tokenizer)
        let upstream = try await SmolVLMProcessor(config, tokenizer: tokenizer).prepare(input: upstreamInput(snapshot))
        XCTAssertEqual(prepared.input.text.tokens.asArray(Int.self), upstream.text.tokens.asArray(Int.self))
        XCTAssertEqual(prepared.input.image?.pixels.asArray(Float.self), upstream.image?.pixels.asArray(Float.self))
        XCTAssertEqual(prepared.input.text.mask?.asArray(Int.self), upstream.text.mask?.asArray(Int.self))
        try assertVideoTokens(prepared.input, tokenizer: tokenizer, frames: 8)
    }

    func testNonuniformRealTokenizerKeepsEightFramesWhilePinnedProcessorStillLosesOne() async throws {
        let tokenizer = try await localTokenizer()
        let snapshot = try await MLXVLMVideoSnapshot.load(videoFixture("video-29fps"))
        defer { snapshot.close() }
        let config = try videoConfiguration()
        let prepared = try await MLXVLMVideoInputAdapter.prepare(frames: snapshot.frames,
            clipDuration: snapshot.duration, prompt: "What changes?", configuration: config, tokenizer: tokenizer)
        XCTAssertEqual(prepared.input.image?.pixels.shape, [8, 512, 512, 3])
        XCTAssertEqual(prepared.timestamps, snapshot.frames.map(\.actualTime))
        XCTAssertEqual(prepared.clipDuration.seconds, 2, accuracy: 0.000001)
        XCTAssertEqual(prepared.sampledSpan.seconds, 25600.0 / 14848, accuracy: 0.000001)
        try assertVideoTokens(prepared.input, tokenizer: tokenizer, frames: 8)
        let upstream = try await SmolVLMProcessor(config, tokenizer: tokenizer).prepare(input: upstreamInput(snapshot))
        XCTAssertEqual(upstream.image?.pixels.shape, [7, 512, 512, 3])
        try assertVideoTokens(upstream, tokenizer: tokenizer, frames: 7)
    }

    private func localTokenizer() async throws -> any MLXLMCommon.Tokenizer {
        guard let path = ProcessInfo.processInfo.environment["ODAI_VIDEO_MODEL_DIRECTORY"] else {
            throw XCTSkip("Requires opt-in ODAI_VIDEO_MODEL_DIRECTORY pointing to hash-verified local assets; no download")
        }
        let directory = URL(fileURLWithPath: path)
        XCTAssertEqual(try Data(contentsOf: directory.appendingPathComponent("preprocessor_config.json")),
                       try Data(contentsOf: videoConfigurationURL()))
        return try await LocalTokenizerLoader().load(from: directory)
    }

    private func upstreamInput(_ snapshot: MLXVLMVideoSnapshot) -> UserInput {
        var input = UserInput(prompt: "What changes?", videos: [.frames(snapshot.frames.map {
            .init(image: .ciImage(CIImage(cgImage: $0.image)), timeStamp: $0.actualTime)
        })])
        input.processing.video = .init(targetFrames: snapshot.frames.count)
        return input
    }

    private func assertVideoTokens(_ input: LMInput, tokenizer: any MLXLMCommon.Tokenizer,
                                   frames: Int, file: StaticString = #filePath, line: UInt = #line) throws {
        let tokens = input.text.tokens.asArray(Int.self)
        let imageID = try XCTUnwrap(tokenizer.convertTokenToId("<image>"), file: file, line: line)
        let boundaryID = try XCTUnwrap(tokenizer.convertTokenToId("<fake_token_around_image>"), file: file, line: line)
        let globalID = try XCTUnwrap(tokenizer.convertTokenToId("<global-img>"), file: file, line: line)
        XCTAssertEqual(tokens.filter { $0 == imageID }.count, frames * 64, file: file, line: line)
        XCTAssertEqual(tokens.filter { $0 == boundaryID }.count, frames * 2, file: file, line: line)
        XCTAssertEqual(tokens.filter { $0 == globalID }.count, frames, file: file, line: line)
        let text = tokenizer.decode(tokenIds: tokens, skipSpecialTokens: false)
        XCTAssertEqual(text.components(separatedBy: "Frame from ").count - 1, frames, file: file, line: line)
        XCTAssertTrue(text.contains("What changes?"), file: file, line: line)
        XCTAssertTrue(text.hasSuffix("Assistant:"), file: file, line: line)
    }
}
