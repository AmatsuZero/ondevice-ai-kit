import Foundation

/// Strict, bounded mono PCM16 WAV import. Canonicalizes chunks without changing samples.
enum VoiceEffectsWave {
    enum Failure: Error { case unsupportedOrInvalidWAV }
    static let maximumBytes = 4 * 1024 * 1024
    static func normalize(url: URL) throws -> Data {
        let file = try FileHandle(forReadingFrom: url)
        defer { try? file.close() }
        var bytes = Data()
        while bytes.count <= maximumBytes {
            guard let chunk = try file.read(upToCount: min(8192, maximumBytes + 1 - bytes.count)), !chunk.isEmpty else { break }
            bytes.append(chunk)
        }
        return try normalize(bytes)
    }
    static func normalize(_ bytes: Data) throws -> Data {
        func reject(_ condition: Bool) throws { if !condition { throw Failure.unsupportedOrInvalidWAV } }
        try reject(bytes.count >= 44 && bytes.count <= maximumBytes)
        func tag(_ at: Int) -> String { String(decoding: bytes[at..<at+4], as: UTF8.self) }
        func u16(_ at: Int) -> Int { Int(bytes[at]) | Int(bytes[at+1]) << 8 }
        func u32(_ at: Int) -> Int { u16(at) | u16(at+2) << 16 }
        try reject(tag(0) == "RIFF" && tag(8) == "WAVE" && u32(4) + 8 == bytes.count)
        var offset = 12, rate: Int?, pcm: Data?
        while offset < bytes.count {
            try reject(offset <= bytes.count - 8)
            let count = u32(offset+4), end = offset + 8 + count
            try reject(end <= bytes.count)
            switch tag(offset) {
            case "fmt ":
                try reject(rate == nil && count >= 16)
                let sampleRate = u32(offset+12)
                try reject(u16(offset+8) == 1 && u16(offset+10) == 1 && [16000,44100,48000].contains(sampleRate)
                    && u32(offset+16) == sampleRate * 2 && u16(offset+20) == 2 && u16(offset+22) == 16)
                rate = sampleRate
            case "data":
                try reject(pcm == nil); pcm = bytes.subdata(in: offset+8..<end)
            default: break
            }
            offset = end + count % 2
            try reject(offset <= bytes.count)
        }
        guard let rate, let pcm, !pcm.isEmpty, pcm.count % 2 == 0, pcm.count <= rate * 60 else { throw Failure.unsupportedOrInvalidWAV }
        var output = Data()
        func text(_ string: String) { output.append(contentsOf: string.utf8) }
        func number(_ value: Int, _ count: Int) { for i in 0..<count { output.append(UInt8((value >> (i*8)) & 255)) } }
        text("RIFF"); number(36+pcm.count,4); text("WAVEfmt "); number(16,4)
        number(1,2); number(1,2); number(rate,4); number(rate*2,4); number(2,2); number(16,2)
        text("data"); number(pcm.count,4); output.append(pcm)
        return output
    }
    static func store(_ data: Data, directory: URL) throws -> URL {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("input-\(UUID().uuidString).wav")
        do { try data.write(to: url, options: .withoutOverwriting); return url }
        catch {
            let failure = error as NSError
            if failure.domain != NSCocoaErrorDomain || failure.code != NSFileWriteFileExistsError {
                try? FileManager.default.removeItem(at: url)
            }
            throw error
        }
    }
}
