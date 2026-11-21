import Foundation
import XCTest
@testable import VoiceEffectsHostSupport

final class VoiceEffectsWaveTests: XCTestCase {
    func wave(_ rate: Int = 16000, _ frames: Int = 16) -> Data {
        var result = Data()
        func text(_ value: String) { result.append(contentsOf: value.utf8) }
        func number(_ value: Int, _ count: Int) { for i in 0..<count { result.append(UInt8((value >> (i*8)) & 255)) } }
        text("RIFF"); number(36+frames*2,4); text("WAVEfmt "); number(16,4)
        number(1,2); number(1,2); number(rate,4); number(rate*2,4); number(2,2); number(16,2)
        text("data"); number(frames*2,4); result.append(Data(repeating: 0, count: frames*2)); return result
    }
    func testCanonicalAndAdditionalChunksPreservePCM() throws {
        for rate in [16000,44100,48000] {
            let canonical = wave(rate)
            XCTAssertEqual(try VoiceEffectsWave.normalize(canonical), canonical)
            var extra = canonical.prefix(36) + Data([74,85,78,75,2,0,0,0,7,8]) + canonical.dropFirst(36)
            var size = UInt32(extra.count-8).littleEndian
            withUnsafeBytes(of: &size) { extra.replaceSubrange(4..<8, with: $0) }
            XCTAssertEqual(try VoiceEffectsWave.normalize(extra), canonical)
        }
    }
    func testRejectsTruncationTrailingBytesStereoAndDurationOverflow() {
        let canonical = wave()
        XCTAssertThrowsError(try VoiceEffectsWave.normalize(Data(canonical.dropLast())))
        XCTAssertThrowsError(try VoiceEffectsWave.normalize(canonical + Data([0])))
        var stereo = canonical; stereo[22] = 2
        XCTAssertThrowsError(try VoiceEffectsWave.normalize(stereo))
        XCTAssertThrowsError(try VoiceEffectsWave.normalize(wave(16000,480001)))
        XCTAssertThrowsError(try VoiceEffectsWave.normalize(wave(16000,0)))
        var chunkOverflow = canonical; chunkOverflow.replaceSubrange(40..<44, with: Data([255,255,255,255]))
        XCTAssertThrowsError(try VoiceEffectsWave.normalize(chunkOverflow))
    }
    func testOwnedFileAndBoundedRead() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let url = try VoiceEffectsWave.store(wave(), directory: root)
        XCTAssertEqual(try VoiceEffectsWave.normalize(url: url),wave())
        try Data(repeating:0,count:VoiceEffectsWave.maximumBytes+1).write(to:url)
        XCTAssertThrowsError(try VoiceEffectsWave.normalize(url:url))
    }
}
