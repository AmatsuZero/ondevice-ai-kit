import XCTest
import CoreImage
import MLX
import MLXLMCommon
import MLXVLM
@testable import ODAIKitAppleMLXVLM

/// Executes the pinned processor, without loading model weights. ByteTokenizer isolates
/// frame selection/prompt formatting; these tests do not validate real model tokenization.
final class MLXVLMVideoProcessorTests: XCTestCase {
    func testUniformActualFramesSurvivePinnedResamplingAndVideoPreparation() async throws {
        let snapshot = try await MLXVLMVideoSnapshot.load(fixture("video-sdr"))
        defer { snapshot.close() }
        let frames = snapshot.frames.map {
            UserInput.VideoFrame(image: .ciImage(CIImage(cgImage: $0.image)), timeStamp: $0.actualTime)
        }
        let sequence = try await MediaProcessing.asProcessedSequence(.frames(frames),
            processing: .init(targetFrames: frames.count), maxFrames: 64)
        XCTAssertEqual(sequence.frames.count, 8)
        XCTAssertEqual(sequence.timestamps, snapshot.frames.map(\.actualTime))
        XCTAssertEqual(sequence.totalDuration.seconds, 1.75, accuracy: 0.0001)
        XCTAssertEqual(snapshot.duration.seconds, 2, accuracy: 0.0001)
        // Check decoded pixel identity, not just counts or reported timestamps.
        for (index, frame) in frames.enumerated() {
            let expected = try frame.image.asCIImage().asMLXArray().asArray(UInt8.self)
            XCTAssertEqual(sequence.frames[index].asArray(UInt8.self), expected)
        }
        let config = try JSONDecoder().decode(SmolVLMProcessorConfiguration.self,
            from: Data(contentsOf: Bundle.module.url(forResource: "preprocessor_config", withExtension: "json", subdirectory: "Fixtures")!))
        let tokenizer = ByteTokenizer()
        let processor = SmolVLMProcessor(config, tokenizer: tokenizer)
        var input = UserInput(prompt: "Describe this video.", videos: [.frames(frames)])
        input.processing.video = .init(targetFrames: frames.count)
        let prepared = try await processor.prepare(input: input)
        let image = try XCTUnwrap(prepared.image)
        XCTAssertEqual(image.pixels.shape, [8, 512, 512, 3])
        XCTAssertEqual(image.frames?.count, 8)
        let prompt = tokenizer.decode(tokenIds: prepared.text.tokens.asArray(Int.self), skipSpecialTokens: false)
        XCTAssertEqual(prompt.components(separatedBy: "Frame from ").count - 1, 8)
        XCTAssertTrue(prompt.contains("Describe this video."))
    }

    func testPinnedNonuniformFramesLoseOneFrameAndFailTheVideoIntegrationGate() async throws {
        let snapshot = try await MLXVLMVideoSnapshot.load(fixture("video-29fps"))
        defer { snapshot.close() }
        XCTAssertEqual(snapshot.frames.count, 8)
        let frames = snapshot.frames.map {
            UserInput.VideoFrame(image: .ciImage(CIImage(cgImage: $0.image)), timeStamp: $0.actualTime)
        }
        let sequence = try await MediaProcessing.asProcessedSequence(.frames(frames),
            processing: .init(targetFrames: frames.count), maxFrames: 64)
        // Characterization of a known pinned upstream boundary, not a successful
        // integration gate. The original eight-frame assertion failed in the ledger.
        XCTAssertEqual(sequence.frames.count, 7)
        XCTAssertNotEqual(sequence.timestamps, snapshot.frames.map(\.actualTime))
        XCTAssertEqual(sequence.timestamps, [0, 1, 2, 3, 4, 5, 7].map { snapshot.frames[$0].actualTime })
    }

    private func fixture(_ name: String) -> URL {
        Bundle.module.url(forResource: name, withExtension: "mp4", subdirectory: "Fixtures")!
    }
}

private struct ByteTokenizer: MLXLMCommon.Tokenizer {
    var bosToken: String? { nil }
    var eosToken: String? { nil }
    var unknownToken: String? { nil }
    func encode(text: String, addSpecialTokens: Bool) -> [Int] { text.utf8.map(Int.init) }
    func decode(tokenIds: [Int], skipSpecialTokens: Bool) -> String {
        String(decoding: tokenIds.map { UInt8(clamping: $0) }, as: UTF8.self)
    }
    func convertTokenToId(_ token: String) -> Int? { token == "<image>" ? 49153 : nil }
    func convertIdToToken(_ id: Int) -> String? { id == 49153 ? "<image>" : nil }
    func applyChatTemplate(messages: [[String: any Sendable]], tools: [[String: any Sendable]]?,
                           additionalContext: [String: any Sendable]?) throws -> [Int] {
        encode(text: "User: Describe this video.\nAssistant:", addSpecialTokens: false)
    }
}
