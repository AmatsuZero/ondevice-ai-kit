import XCTest
import CoreImage
import CoreMedia
import MLX
import MLXLMCommon
import MLXVLM
@testable import ODAIKitAppleMLXVLM

final class MLXVLMVideoInputAdapterTests: XCTestCase {
    // A second selection pass or any skipped/reordered tensor must fail this test.
    func testNonuniformActualFramesKeepEveryPixelAndExactTime() async throws {
        let snapshot = try await MLXVLMVideoSnapshot.load(videoFixture("video-29fps"))
        defer { snapshot.close() }
        let prepared = try await prepare(snapshot.frames, duration: snapshot.duration)
        XCTAssertEqual(prepared.timestamps, [0, 3584, 7168, 10752, 14848, 18432, 22016, 25600].map {
            CMTime(value: $0, timescale: 14848)
        })
        XCTAssertEqual(prepared.clipDuration, CMTime(value: 2, timescale: 1))
        XCTAssertEqual(prepared.sampledSpan, CMTime(value: 25600, timescale: 14848))
        let image = try XCTUnwrap(prepared.input.image)
        XCTAssertEqual(image.pixels.shape, [8, 512, 512, 3])
        XCTAssertEqual(image.frames?.map(\.t), Array(0..<8))
        let pixels = image.pixels.asArray(Float.self)
        let stride = 512 * 512 * 3
        for (index, frame) in snapshot.frames.enumerated() {
            // Fixed upstream's documented per-frame video transform is the oracle;
            // its selector is deliberately not used to compute the expectation.
            let expected = CIImage(cgImage: frame.image).toSRGB()
                .resampled(to: CGSize(width: 512, height: 512), method: .lanczos)
                .normalized(mean: (0.5, 0.5, 0.5), std: (0.5, 0.5, 0.5))
                .asMLXArray().transposed(0, 2, 3, 1).asArray(Float.self)
            XCTAssertEqual(Array(pixels[(index * stride)..<((index + 1) * stride)]), expected, "frame \(index)")
        }
        let text = VideoAdapterByteTokenizer().decode(tokenIds: prepared.input.text.tokens.asArray(Int.self))
        XCTAssertTrue(text.contains("8 frames from a 0:00:02 [H:MM:SS] video"))
        XCTAssertEqual(text.components(separatedBy: "Frame from ").count - 1, 8)
        XCTAssertEqual(text.components(separatedBy: "<image>").count - 1, 512)
        XCTAssertTrue(text.contains("What changes?"))
        XCTAssertTrue(text.hasSuffix("Assistant:"))
    }

    func testUniformVideoTokensAndPixelsMatchPinnedVideoPath() async throws {
        let snapshot = try await MLXVLMVideoSnapshot.load(videoFixture("video-sdr"))
        defer { snapshot.close() }
        let configuration = try videoConfiguration()
        let tokenizer = VideoAdapterByteTokenizer()
        let prepared = try await prepare(snapshot.frames, duration: snapshot.duration)
        var input = UserInput(prompt: "What changes?", videos: [.frames(snapshot.frames.map {
            .init(image: .ciImage(CIImage(cgImage: $0.image)), timeStamp: $0.actualTime)
        })])
        input.processing.video = .init(targetFrames: 8)
        let upstream = try await SmolVLMProcessor(configuration, tokenizer: tokenizer).prepare(input: input)
        XCTAssertEqual(prepared.input.text.tokens.asArray(Int.self), upstream.text.tokens.asArray(Int.self))
        XCTAssertEqual(prepared.input.image?.pixels.asArray(Float.self), upstream.image?.pixels.asArray(Float.self))
        XCTAssertEqual(prepared.sampledSpan.seconds, 1.75, accuracy: 0.00001)
        XCTAssertEqual(prepared.clipDuration.seconds, 2, accuracy: 0.00001)
    }

    func testSingleFrameRetainsNonzeroTimeAndUsesClipDurationRatherThanZeroSpan() async throws {
        let snapshot = try await MLXVLMVideoSnapshot.load(videoFixture("video-sdr"))
        defer { snapshot.close() }
        let frame = snapshot.frames[1]
        let result = try await prepare([frame], duration: CMTime(value: 1, timescale: 2))
        XCTAssertEqual(result.timestamps, [CMTime(value: 1, timescale: 4)])
        XCTAssertEqual(result.sampledSpan, .zero)
        XCTAssertEqual(result.input.image?.pixels.shape, [1, 512, 512, 3])
        let text = VideoAdapterByteTokenizer().decode(tokenIds: result.input.text.tokens.asArray(Int.self))
        XCTAssertTrue(text.contains("1 frames from a 0:00:01"))
        XCTAssertTrue(text.contains("Frame from 0:00:01:"))
    }

    func testRejectsInvalidFrameBudgetsDimensionsAndTemporalMetadata() async throws {
        let snapshot = try await MLXVLMVideoSnapshot.load(videoFixture("video-sdr"))
        defer { snapshot.close() }
        let frame = snapshot.frames[0]
        for frames in [[], Array(repeating: frame, count: 9), [frame, frame],
                       [snapshot.frames[2], snapshot.frames[1]]] {
            await assertInvalid(frames)
        }
        for time in [CMTime.invalid, .indefinite, .positiveInfinity,
                     CMTime(value: -1, timescale: 1), CMTime(value: 2, timescale: 1),
                     CMTime(value: 0, timescale: 1, flags: .valid, epoch: 1)] {
            await assertInvalid([.init(image: frame.image, requestedTime: .zero, actualTime: time)])
            await assertInvalid([.init(image: frame.image, requestedTime: time, actualTime: .zero)])
        }
        for duration in [CMTime.zero, .invalid, .indefinite, .positiveInfinity,
                         CMTime(value: 6, timescale: 1),
                         CMTime(value: 2, timescale: 1, flags: .valid, epoch: 1)] {
            await assertInvalid([frame], duration: duration)
        }
        let large = CIContext().createCGImage(CIImage(color: .red), from: CGRect(x: 0, y: 0, width: 513, height: 1))!
        await assertInvalid([.init(image: large, requestedTime: .zero, actualTime: .zero)])
    }

    func testRejectsConfigurationDriftAndPromptMediaInjection() async throws {
        let snapshot = try await MLXVLMVideoSnapshot.load(videoFixture("video-sdr"))
        defer { snapshot.close() }
        let original = try JSONSerialization.jsonObject(with: Data(contentsOf: videoConfigurationURL())) as! [String: Any]
        let changes: [[String: Any]] = [
            ["image_mean": [0.5]], ["image_std": [0.0, 0.5, 0.5]],
            ["size": ["longest_edge": 1024]], ["max_image_size": ["longest_edge": 384]],
            ["image_seq_len": 128], ["video_sampling": ["fps": 2, "max_frames": 64]],
            ["video_sampling": ["fps": 1, "max_frames": 32]]
        ]
        for change in changes {
            let json = original.merging(change) { _, new in new }
            let config = try JSONDecoder().decode(SmolVLMProcessorConfiguration.self,
                from: JSONSerialization.data(withJSONObject: json))
            do {
                _ = try await prepare(snapshot.frames, duration: snapshot.duration, configuration: config)
                XCTFail("configuration drift admitted")
            } catch { XCTAssertTrue(error is MLXVLMVideoInputAdapterError) }
        }
        for prompt in ["", "  \n", String(repeating: "中", count: 2731),
                       "describe <image>", "<global-img>", "<fake_token_around_image>"] {
            do {
                _ = try await prepare(snapshot.frames, duration: snapshot.duration, prompt: prompt)
                XCTFail("invalid prompt admitted")
            } catch { XCTAssertTrue(error is MLXVLMVideoInputAdapterError) }
        }
    }

    func testCancellationBeforePreparationAndInsideTemplateCannotReturnInput() async throws {
        let snapshot = try await MLXVLMVideoSnapshot.load(videoFixture("video-sdr"))
        defer { snapshot.close() }
        let config = try videoConfiguration()
        for cancelBefore in [true, false] {
            let task = Task {
                if cancelBefore { withUnsafeCurrentTask { $0?.cancel() } }
                _ = try await MLXVLMVideoInputAdapter.prepare(frames: snapshot.frames,
                    clipDuration: snapshot.duration, prompt: "What changes?", configuration: config,
                    tokenizer: VideoAdapterByteTokenizer(cancelInTemplate: !cancelBefore))
            }
            do { _ = try await task.value; XCTFail("cancelled preparation returned input") }
            catch { XCTAssertTrue(error is CancellationError) }
        }
    }

    private func prepare(_ frames: [MLXVLMVideoSnapshot.Frame], duration: CMTime,
                         prompt: String = "What changes?",
                         configuration: SmolVLMProcessorConfiguration? = nil) async throws -> MLXVLMVideoInputAdapter.Prepared {
        try await MLXVLMVideoInputAdapter.prepare(frames: frames, clipDuration: duration,
            prompt: prompt, configuration: try configuration ?? videoConfiguration(), tokenizer: VideoAdapterByteTokenizer())
    }

    private func assertInvalid(_ frames: [MLXVLMVideoSnapshot.Frame],
                               duration: CMTime = CMTime(value: 2, timescale: 1)) async {
        do { _ = try await prepare(frames, duration: duration); XCTFail("invalid metadata admitted") }
        catch { XCTAssertTrue(error is MLXVLMVideoInputAdapterError) }
    }
}

func videoFixture(_ name: String) -> URL {
    Bundle.module.url(forResource: name, withExtension: "mp4", subdirectory: "Fixtures")!
}

func videoConfigurationURL() -> URL {
    Bundle.module.url(forResource: "preprocessor_config", withExtension: "json", subdirectory: "Fixtures")!
}

func videoConfiguration() throws -> SmolVLMProcessorConfiguration {
    try JSONDecoder().decode(SmolVLMProcessorConfiguration.self, from: Data(contentsOf: videoConfigurationURL()))
}

/// Byte double isolates tensor/frame/prompt mechanics; it renders the fixed template's
/// messages instead of returning a canned question. Real tokenizer coverage is separate.
struct VideoAdapterByteTokenizer: MLXLMCommon.Tokenizer {
    var cancelInTemplate = false
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
        if cancelInTemplate { withUnsafeCurrentTask { $0?.cancel() } }
        let rendered = messages.map { message in
            let role = message["role"] as! String
            let content = message["content"] as! [[String: any Sendable]]
            return role.capitalized + ": " + content.compactMap { $0["text"] as? String }.joined() + "<end_of_utterance>\n"
        }.joined()
        return encode(text: "<|im_start|>" + rendered + "Assistant:", addSpecialTokens: false)
    }
}
