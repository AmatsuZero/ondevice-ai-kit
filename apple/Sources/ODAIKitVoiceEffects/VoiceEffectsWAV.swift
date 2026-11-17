import Foundation
import Darwin

struct VoiceEffectsWAV: Sendable {
    let bytes: Data
    let input: VoiceEffectsPCMInput
    static let maximumBytes = 44 + 48000 * 30 * 2

    static func read(_ url: URL, check: () throws -> Void) throws -> VoiceEffectsWAV {
        guard url.isFileURL, url.host == nil || url.host == "" || url.host == "localhost",
              !url.path.utf8.contains(0) else { throw VoiceEffectsError.invalidInput(.file) }
        try check()
        // NONBLOCK prevents a FIFO open from hanging before fstat can reject it.
        let fd = open(url.path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK)
        guard fd >= 0 else { throw VoiceEffectsError.invalidInput(.file) }
        var needsClose = true
        defer { if needsClose { _ = Darwin.close(fd) } }
        var before = stat()
        guard fstat(fd, &before) == 0, (before.st_mode & S_IFMT) == S_IFREG else {
            throw VoiceEffectsError.invalidInput(.file)
        }
        guard before.st_size <= maximumBytes else { throw VoiceEffectsError.unsupportedInput(.duration) }
        guard before.st_size >= 44 else { throw VoiceEffectsError.invalidInput(.malformedWAV) }
        var data = Data(); data.reserveCapacity(Int(before.st_size))
        var chunk = [UInt8](repeating: 0, count: 16384)
        while true {
            try check()
            // One byte past the bound detects concurrent growth without unbounded allocation.
            let count = min(chunk.count, maximumBytes + 1 - data.count)
            let readCount = Darwin.read(fd, &chunk, count)
            if readCount < 0 {
                if errno == EINTR { continue }
                throw VoiceEffectsError.invalidInput(.file)
            }
            if readCount == 0 { break }
            data.append(contentsOf: chunk.prefix(readCount))
            guard data.count <= maximumBytes else { throw VoiceEffectsError.unsupportedInput(.duration) }
        }
        var after = stat()
        guard fstat(fd, &after) == 0, before.st_size == after.st_size,
              after.st_size == data.count, before.st_mtimespec.tv_sec == after.st_mtimespec.tv_sec,
              before.st_mtimespec.tv_nsec == after.st_mtimespec.tv_nsec,
              before.st_ctimespec.tv_sec == after.st_ctimespec.tv_sec,
              before.st_ctimespec.tv_nsec == after.st_ctimespec.tv_nsec else {
            throw VoiceEffectsError.invalidInput(.malformedWAV)
        }
        needsClose = false
        guard Darwin.close(fd) == 0 else { throw VoiceEffectsError.invalidInput(.file) }
        try check()
        return try parse(data)
    }

    static func parse(_ bytes: Data) throws -> VoiceEffectsWAV {
        guard bytes.count >= 44 else { throw VoiceEffectsError.invalidInput(.malformedWAV) }
        func tag(_ index: Int, _ text: String) -> Bool {
            bytes[index..<(index + 4)].elementsEqual(text.utf8)
        }
        func number(_ index: Int, _ count: Int) -> Int {
            (0..<count).reduce(0) { $0 | (Int(bytes[index + $1]) << ($1 * 8)) }
        }
        guard tag(0, "RIFF"), tag(8, "WAVE"), number(4, 4) == bytes.count - 8 else {
            throw VoiceEffectsError.invalidInput(.malformedWAV)
        }
        guard tag(12, "fmt "), number(16, 4) == 16, tag(36, "data") else {
            throw VoiceEffectsError.unsupportedInput(.nonCanonicalWAV)
        }
        guard number(20, 2) == 1, number(22, 2) == 1, number(34, 2) == 16 else {
            throw VoiceEffectsError.unsupportedInput(.encoding)
        }
        let rate = number(24, 4)
        guard [16000, 44100, 48000].contains(rate) else {
            throw VoiceEffectsError.unsupportedInput(.sampleRate)
        }
        let length = number(40, 4)
        guard number(28, 4) == rate * 2, number(32, 2) == 2,
              length == bytes.count - 44, length > 0, length % 2 == 0 else {
            throw VoiceEffectsError.invalidInput(.malformedWAV)
        }
        guard length / 2 <= rate * 30 else { throw VoiceEffectsError.unsupportedInput(.duration) }
        return .init(bytes: bytes, input: .init(pcm16LittleEndian: Data(bytes.dropFirst(44)), sampleRate: rate))
    }

    func replacingPCM(_ pcm: Data) throws -> Data {
        guard pcm.count == input.pcm16LittleEndian.count else {
            throw VoiceEffectsError.platform(domain: NSOSStatusErrorDomain, code: -1)
        }
        var output = Data(bytes.prefix(44)); output.append(pcm)
        return output
    }
}
