import AVFoundation
import Foundation
import XCTest
@testable import ODAIKitAppleVideo

final class AppleVideoAudioMuxerTests: XCTestCase {
    func testPreservesCompressedAACAndVideoTimeline() async throws {
        let root = try makeRoot(); defer { try? FileManager.default.removeItem(at: root) }
        let source = fixture("aac.mp4")
        let before = try Data(contentsOf: source)
        let output = root.appendingPathComponent("output.mov")
        let count = try await AppleVideoAudioMuxer.preserve(source: source, processedVideo: fixture("video-only.mov"), output: output, videoDuration: 2)
        XCTAssertEqual(count, 1)
        let asset = AVURLAsset(url: output)
        let audios = try await asset.loadTracks(withMediaType: .audio)
        let videos = try await asset.loadTracks(withMediaType: .video)
        XCTAssertEqual(audios.count, 1); XCTAssertEqual(videos.count, 1)
        let sourceAudio = try await AVURLAsset(url: source).loadTracks(withMediaType: .audio)[0]
        let sourceRange = try await sourceAudio.load(.timeRange)
        let actualRange = try await audios[0].load(.timeRange)
        XCTAssertEqual(actualRange.start.seconds, sourceRange.start.seconds, accuracy: 0.000002)
        XCTAssertEqual(actualRange.duration.seconds, sourceRange.duration.seconds, accuracy: 0.000002)
        let videoRange = try await videos[0].load(.timeRange)
        XCTAssertEqual(videoRange.start.seconds, 0, accuracy: 0.000002)
        XCTAssertEqual(videoRange.duration.seconds, 2, accuracy: 0.000002)
        let sourceBytes = try await compressedAudio(source: source)
        let outputBytes = try await compressedAudio(source: output)
        XCTAssertFalse(sourceBytes.isEmpty); XCTAssertEqual(outputBytes, sourceBytes)
        XCTAssertEqual(try Data(contentsOf: source), before)
    }

    func testRejectsOffsetEditsBeforeCreatingOutput() async throws {
        let root = try makeRoot(); defer { try? FileManager.default.removeItem(at: root) }
        let source = fixture("offset-aac.mov"), output = root.appendingPathComponent("offset.mov")
        do { try await AppleVideoAudioMuxer.validateInput(source: source, videoDuration: 2); XCTFail("offset admission") }
        catch { guard case AppleVideoError.unsupportedInput = error else { return XCTFail("reject before export") } }
        do { _ = try await AppleVideoAudioMuxer.preserve(source: source, processedVideo: fixture("video-only.mov"), output: output, videoDuration: 2); XCTFail("offset admission") }
        catch { guard case AppleVideoError.unsupportedInput = error else { return XCTFail("reject before export") } }
        XCTAssertFalse(FileManager.default.fileExists(atPath: output.path))
    }

    func testUnreadableSourceProducesStableErrorAndNoOutput() async throws {
        let root = try makeRoot(); defer { try? FileManager.default.removeItem(at: root) }
        let source = root.appendingPathComponent("missing.mp4"), output = root.appendingPathComponent("output.mov")
        do { try await AppleVideoAudioMuxer.validateInput(source: source, videoDuration: 2); XCTFail("missing input") }
        catch { XCTAssertTrue(error is AppleVideoError) }
        do { _ = try await AppleVideoAudioMuxer.preserve(source: source, processedVideo: fixture("video-only.mov"), output: output, videoDuration: 2); XCTFail("missing input") }
        catch { XCTAssertTrue(error is AppleVideoError) }
        XCTAssertFalse(FileManager.default.fileExists(atPath: output.path))
    }

    func testPreserveWithoutAudioMovesVideoUnchanged() async throws {
        let root = try makeRoot(); defer { try? FileManager.default.removeItem(at: root) }
        let intermediate = root.appendingPathComponent("video.mov")
        let before = try Data(contentsOf: fixture("video-only.mov"))
        try before.write(to: intermediate)
        let output = root.appendingPathComponent("output.mov")
        let count = try await AppleVideoAudioMuxer.preserve(source: fixture("video-only.mov"), processedVideo: intermediate, output: output, videoDuration: 2)
        XCTAssertEqual(count, 0)
        XCTAssertEqual(try Data(contentsOf: output), before)
        XCTAssertFalse(FileManager.default.fileExists(atPath: intermediate.path))
    }

    func testRejectsUnsupportedAudioAndRangesBeforeCreatingOutput() async throws {
        for file in ["pcm.wav", "long-aac.m4a", "two-aac.m4a"] {
            do { try await AppleVideoAudioMuxer.validateInput(source: fixture(file), videoDuration: 2); XCTFail(file) }
            catch { guard case AppleVideoError.unsupportedInput = error else { return XCTFail("stable unsupported error: \(error)") } }
        }
        for duration in [Double.nan, .infinity, 0, -1] {
            do { try await AppleVideoAudioMuxer.validateInput(source: fixture("aac.mp4"), videoDuration: duration); XCTFail("invalid duration") }
            catch { guard case AppleVideoError.unsupportedInput = error else { return XCTFail("stable error") } }
        }
    }

    func testExistingOutputRemainsUntouched() async throws {
        let root = try makeRoot(); defer { try? FileManager.default.removeItem(at: root) }
        let output = root.appendingPathComponent("keep.mov")
        try Data([3, 1, 4]).write(to: output)
        do { _ = try await AppleVideoAudioMuxer.preserve(source: fixture("aac.mp4"), processedVideo: fixture("video-only.mov"), output: output, videoDuration: 2); XCTFail("no overwrite") }
        catch { }
        XCTAssertEqual(try Data(contentsOf: output), Data([3, 1, 4]))
    }

    func testPreCancelledMuxCreatesNoOutput() async throws {
        let root = try makeRoot(); defer { try? FileManager.default.removeItem(at: root) }
        let source = fixture("aac.mp4"), video = fixture("video-only.mov"), output = root.appendingPathComponent("cancelled.mov")
        let operation = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return try await AppleVideoAudioMuxer.preserve(source: source, processedVideo: video, output: output, videoDuration: 2)
        }
        do { _ = try await operation.value; XCTFail("cancelled") }
        catch { XCTAssertTrue(error is CancellationError) }
        XCTAssertFalse(FileManager.default.fileExists(atPath: output.path))
    }

    private func fixture(_ name: String) -> URL { Bundle.module.resourceURL!.appendingPathComponent("Fixtures/" + name) }
    private func makeRoot() throws -> URL {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("odai-mux-test-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
        return root
    }
    private func compressedAudio(source: URL) async throws -> Data {
        let asset = AVURLAsset(url: source)
        let track = try await asset.loadTracks(withMediaType: .audio)[0]
        let reader = try AVAssetReader(asset: asset)
        let output = AVAssetReaderTrackOutput(track: track, outputSettings: nil)
        reader.add(output); XCTAssertTrue(reader.startReading())
        var data = Data()
        while let sample = output.copyNextSampleBuffer() {
            guard let block = CMSampleBufferGetDataBuffer(sample) else { continue }
            var bytes = [UInt8](repeating: 0, count: CMBlockBufferGetDataLength(block))
            let result = bytes.withUnsafeMutableBytes { CMBlockBufferCopyDataBytes(block, atOffset: 0, dataLength: $0.count, destination: $0.baseAddress!) }
            XCTAssertEqual(result, kCMBlockBufferNoErr); data.append(contentsOf: bytes)
        }
        XCTAssertEqual(reader.status, .completed)
        return data
    }
}
