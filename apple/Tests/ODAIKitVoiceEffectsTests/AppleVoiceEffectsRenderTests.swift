import Foundation
import XCTest
@testable import ODAIKitVoiceEffects
import ODAIKitAppleVoiceEffects

/// Executes the real offline AVAudioEngine/AVAudioUnitTimePitch path. No microphone,
/// audio session mutation, hardware playback, model, network, or mock backend.
final class AppleVoiceEffectsRenderTests: XCTestCase {
    func testRealAppleToneMatrixHasExactFramesAndSteadyPitchWithinTwoPercent() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        let client = try VoiceEffectsClient(backend: AppleVoiceEffectsBackend(), outputDirectory: files.outputs)
        for rate in [16000, 44100, 48000] {
            for frequency in [220.0, 440.0, 880.0] {
                for preset in VoiceEffectsPreset.allCases {
                    let bytes = voiceWAV(rate: rate, frames: rate * 2, frequency: frequency)
                    try files.write(bytes)
                    let output = try await client.process(files.request(preset))
                    let result = try Data(contentsOf: output.audioFile)
                    let wave = try VoiceEffectsWAV.parse(result)
                    XCTAssertEqual(output.sampleRate, rate)
                    XCTAssertEqual(output.frameCount, Int64(rate * 2))
                    XCTAssertEqual(wave.input.frameCount, output.frameCount)
                    XCTAssertEqual(try Data(contentsOf: files.input), bytes)
                    if preset == .original { XCTAssertEqual(result, bytes) }
                    let semitones = preset == .high ? 4.0 : (preset == .low ? -4.0 : 0)
                    let target = frequency * pow(2, semitones / 12)
                    let actual = try frequencyEstimate(wave.input.pcm16LittleEndian, rate: rate)
                    XCTAssertLessThanOrEqual(abs(actual / target - 1), 0.02,
                                             "rate=\(rate), tone=\(frequency), preset=\(preset)")
                    try await client.releaseOutput(id: output.id)
                }
            }
        }
        await client.close()
        XCTAssertTrue(files.outputEntries.isEmpty)
    }

    func testRealAppleSilenceOneFrameAndThirtySecondBoundaries() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        let client = try VoiceEffectsClient(backend: AppleVoiceEffectsBackend(), outputDirectory: files.outputs)
        for rate in [16000, 44100, 48000] {
            for frames in [1, rate, rate * 30] {
                for preset in VoiceEffectsPreset.allCases {
                    let bytes = voiceWAV(rate: rate, frames: frames, silent: true)
                    try files.write(bytes)
                    let output = try await client.process(files.request(preset))
                    let wave = try VoiceEffectsWAV.parse(Data(contentsOf: output.audioFile))
                    XCTAssertEqual(wave.input.sampleRate, rate)
                    XCTAssertEqual(wave.input.frameCount, Int64(frames))
                    XCTAssertTrue(wave.input.pcm16LittleEndian.allSatisfy { $0 == 0 })
                    XCTAssertEqual(try Data(contentsOf: files.input), bytes)
                    try await client.releaseOutput(id: output.id)
                }
            }
        }
        await client.close()
    }

    func testRealAppleBackendHonorsCancellationWithoutPublishing() async throws {
        let files = try VoiceTestFiles(); defer { files.remove() }
        let wave = try VoiceEffectsWAV.parse(voiceWAV(frames: 16000))
        let backend = AppleVoiceEffectsBackend()
        let calls = VoiceTestBox(0)
        do {
            _ = try backend.render(wave.input, preset: .high) {
                let count = calls.access { $0 += 1; return $0 }
                if count == 3 { throw CancellationError() }
            }
            XCTFail("must propagate cancellation")
        } catch { XCTAssertTrue(error is CancellationError) }
        XCTAssertTrue(files.outputEntries.isEmpty)
    }

    private func frequencyEstimate(_ pcm: Data, rate: Int) throws -> Double {
        let samples = stride(from: 0, to: pcm.count, by: 2).map {
            Double(Int16(bitPattern: UInt16(pcm[$0]) | UInt16(pcm[$0 + 1]) << 8))
        }
        let trim = rate / 5 // remove exactly 200ms at both ends
        var crossings: [Double] = []
        for i in (trim + 1)..<(samples.count - trim) {
            let a = samples[i - 1], b = samples[i]
            if a <= 0, b > 0 { crossings.append(Double(i - 1) - a / (b - a)) }
        }
        guard crossings.count >= 3 else {
            XCTFail("missing steady tone"); throw VoiceEffectsError.invalidInput(.malformedWAV)
        }
        let periods = zip(crossings.dropFirst(), crossings).map { $0.0 - $0.1 }.sorted()
        return Double(rate) / periods[periods.count / 2]
    }
}
