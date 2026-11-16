import Foundation
import CryptoKit

private enum Apple65CoreError: Error, CustomStringConvertible {
    case terse(String)

    var description: String {
        switch self {
        case .terse(let label):
            return label
        }
    }
}

private let apple65ManifestMaxBytes = 256 * 1024

private let apple65RootKeys: Set<String> = [
    "schemaVersion", "runId", "backendId", "inputBindingSHA256",
    "sampleRate", "channels", "sampleWidth", "cases"
]

private let apple65CaseKeys: Set<String> = [
    "id", "cohort", "locale", "reference", "samples",
    "pcmSHA256", "wavPath", "wavBytes", "wavSHA256"
]

private let apple65ExpectedCohorts: [String: Int] = [
    "mandarin": 20,
    "english-near": 20,
    "english-far": 12,
    "nonspeech-noise": 9,
    "silence": 4
]

private let apple65RIFF: [UInt8] = [0x52, 0x49, 0x46, 0x46]
private let apple65WAVE: [UInt8] = [0x57, 0x41, 0x56, 0x45]
private let apple65FMT: [UInt8] = [0x66, 0x6D, 0x74, 0x20]
private let apple65DATA: [UInt8] = [0x64, 0x61, 0x74, 0x61]

struct Apple65Clip: Codable {
    let id: String
    let cohort: String
    let locale: String
    let reference: String
    let samples: Int
    let pcmSHA256: String
    let wavPath: String
    let wavBytes: Int
    let wavSHA256: String
}

struct Apple65Manifest: Codable {
    let schemaVersion: Int
    let runId: String
    let backendId: String
    let inputBindingSHA256: String
    let sampleRate: Int
    let channels: Int
    let sampleWidth: Int
    let cases: [Apple65Clip]
}

enum Apple65Core {
    static func sha256(_ data: Data) -> String {
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    static func loadManifest(_ data: Data, expectedSHA256: String) throws -> Apple65Manifest {
        guard apple65IsLowerHex64(expectedSHA256) else {
            throw Apple65CoreError.terse("digestPin:badExpected")
        }
        guard Apple65Core.sha256(data) == expectedSHA256 else {
            throw Apple65CoreError.terse("digestPin:mismatch")
        }
        guard data.count <= apple65ManifestMaxBytes else {
            throw Apple65CoreError.terse("manifest:tooLarge")
        }

        let rawObject: Any
        do {
            rawObject = try JSONSerialization.jsonObject(with: data, options: [])
        } catch {
            throw Apple65CoreError.terse("json:malformed")
        }
        guard let root = rawObject as? [String: Any], Set(root.keys) == apple65RootKeys else {
            throw Apple65CoreError.terse("manifest:rootKeys")
        }

        guard let schemaVersion = apple65StrictInt(root["schemaVersion"]), schemaVersion == 1 else {
            throw Apple65CoreError.terse("schemaVersion")
        }
        let runId = try apple65String(root, "runId")
        guard runId == "w3-apple-punctuation-paired6-20261116-01" else {
            throw Apple65CoreError.terse("runId")
        }
        let backendId = try apple65String(root, "backendId")
        guard backendId == "apple.speech.recognizer" else {
            throw Apple65CoreError.terse("backendId")
        }
        let inputBinding = try apple65String(root, "inputBindingSHA256")
        guard apple65IsLowerHex64(inputBinding) else {
            throw Apple65CoreError.terse("inputBindingSHA256")
        }
        guard let sampleRate = apple65StrictInt(root["sampleRate"]), sampleRate == 16000 else {
            throw Apple65CoreError.terse("sampleRate")
        }
        guard let channels = apple65StrictInt(root["channels"]), channels == 1 else {
            throw Apple65CoreError.terse("channels")
        }
        guard let sampleWidth = apple65StrictInt(root["sampleWidth"]), sampleWidth == 2 else {
            throw Apple65CoreError.terse("sampleWidth")
        }

        guard let rawCases = root["cases"] as? [[String: Any]], rawCases.count == 65 else {
            throw Apple65CoreError.terse("cases:count")
        }
        for rawCase in rawCases {
            guard Set(rawCase.keys) == apple65CaseKeys else {
                throw Apple65CoreError.terse("case:keys")
            }
            _ = try apple65String(rawCase, "id")
            _ = try apple65String(rawCase, "cohort")
            _ = try apple65String(rawCase, "locale")
            _ = try apple65String(rawCase, "reference")
            _ = try apple65String(rawCase, "pcmSHA256")
            _ = try apple65String(rawCase, "wavPath")
            _ = try apple65String(rawCase, "wavSHA256")
            guard let samples = apple65StrictInt(rawCase["samples"]), samples >= 48000, samples <= 160000 else {
                throw Apple65CoreError.terse("case:samples")
            }
            guard let wavBytes = apple65StrictInt(rawCase["wavBytes"]), wavBytes == samples * 2 + 44 else {
                throw Apple65CoreError.terse("case:wavBytes")
            }
        }

        let manifest: Apple65Manifest
        do {
            manifest = try JSONDecoder().decode(Apple65Manifest.self, from: data)
        } catch {
            throw Apple65CoreError.terse("manifest:decode")
        }
        guard manifest.cases.count == 65 else {
            throw Apple65CoreError.terse("cases:count")
        }

        var seenIDs = Set<String>()
        var seenPaths = Set<String>()
        var cohortCounts: [String: Int] = [:]
        var silenceLocales: [String: Int] = [:]

        for clip in manifest.cases {
            guard apple65IsValidID(clip.id), seenIDs.insert(clip.id).inserted else {
                throw Apple65CoreError.terse("case:id")
            }
            guard clip.wavPath == "audio/\(clip.id).wav", seenPaths.insert(clip.wavPath).inserted else {
                throw Apple65CoreError.terse("case:wavPath")
            }
            guard apple65IsLowerHex64(clip.pcmSHA256), apple65IsLowerHex64(clip.wavSHA256) else {
                throw Apple65CoreError.terse("case:digestFormat")
            }
            guard clip.samples >= 48000, clip.samples <= 160000 else {
                throw Apple65CoreError.terse("case:samples")
            }
            let expectedWavBytes = clip.samples * 2 + 44
            guard clip.wavBytes == expectedWavBytes else {
                throw Apple65CoreError.terse("case:wavBytes")
            }

            cohortCounts[clip.cohort, default: 0] += 1
            switch clip.cohort {
            case "mandarin":
                guard clip.locale == "zh-CN", !clip.reference.isEmpty else {
                    throw Apple65CoreError.terse("case:mandarin")
                }
            case "english-near", "english-far":
                guard clip.locale == "en-US", !clip.reference.isEmpty else {
                    throw Apple65CoreError.terse("case:english")
                }
            case "nonspeech-noise":
                guard clip.locale == "en-US", clip.reference.isEmpty else {
                    throw Apple65CoreError.terse("case:noise")
                }
            case "silence":
                guard clip.locale == "zh-CN" || clip.locale == "en-US", clip.reference.isEmpty else {
                    throw Apple65CoreError.terse("case:silence")
                }
                silenceLocales[clip.locale, default: 0] += 1
            default:
                throw Apple65CoreError.terse("case:cohort")
            }
        }

        guard cohortCounts == apple65ExpectedCohorts else {
            throw Apple65CoreError.terse("case:distribution")
        }
        guard silenceLocales["zh-CN"] == 2, silenceLocales["en-US"] == 2 else {
            throw Apple65CoreError.terse("case:silenceLocales")
        }
        return manifest
    }

    static func validateWave(_ data: Data, clip: Apple65Clip) throws {
        guard clip.samples >= 48000, clip.samples <= 160000 else {
            throw Apple65CoreError.terse("wave:samples")
        }
        let payloadBytes = clip.samples * 2
        let expectedWavBytes = payloadBytes + 44
        guard clip.wavBytes == expectedWavBytes else {
            throw Apple65CoreError.terse("wave:wavBytes")
        }
        guard data.count == expectedWavBytes else {
            throw Apple65CoreError.terse("wave:dataLength")
        }
        guard apple65IsLowerHex64(clip.pcmSHA256), apple65IsLowerHex64(clip.wavSHA256) else {
            throw Apple65CoreError.terse("wave:digestFormat")
        }
        guard Apple65Core.sha256(data) == clip.wavSHA256 else {
            throw Apple65CoreError.terse("wave:wavDigest")
        }
        try apple65ValidateWaveHeader(data, payloadBytes: payloadBytes)
        let payload = try apple65DataSlice(data, offset: 44, count: payloadBytes)
        guard payload.count == payloadBytes else {
            throw Apple65CoreError.terse("wave:payloadLength")
        }
        guard Apple65Core.sha256(payload) == clip.pcmSHA256 else {
            throw Apple65CoreError.terse("wave:pcmDigest")
        }
    }
}

private func apple65ValidateWaveHeader(_ data: Data, payloadBytes: Int) throws {
    guard payloadBytes >= 0, payloadBytes <= Int.max - 44 else {
        throw Apple65CoreError.terse("wave:payloadRange")
    }
    let expectedTotal = payloadBytes + 44
    guard data.count == expectedTotal else {
        throw Apple65CoreError.terse("wave:headerLength")
    }
    guard try apple65ASCIIEquals(data, at: 0, apple65RIFF),
          try apple65ASCIIEquals(data, at: 8, apple65WAVE),
          try apple65ASCIIEquals(data, at: 12, apple65FMT),
          try apple65ASCIIEquals(data, at: 36, apple65DATA) else {
        throw Apple65CoreError.terse("wave:chunkIDs")
    }
    guard let expectedRIFFSize = UInt32(exactly: expectedTotal - 8),
          let expectedDataSize = UInt32(exactly: payloadBytes) else {
        throw Apple65CoreError.terse("wave:sizeRange")
    }
    guard try apple65UInt32LE(data, at: 4) == expectedRIFFSize else {
        throw Apple65CoreError.terse("wave:riffSize")
    }
    guard try apple65UInt32LE(data, at: 16) == 16 else {
        throw Apple65CoreError.terse("wave:fmtSize")
    }
    guard try apple65UInt16LE(data, at: 20) == 1 else {
        throw Apple65CoreError.terse("wave:audioFormat")
    }
    guard try apple65UInt16LE(data, at: 22) == 1 else {
        throw Apple65CoreError.terse("wave:channels")
    }
    guard try apple65UInt32LE(data, at: 24) == 16000 else {
        throw Apple65CoreError.terse("wave:sampleRate")
    }
    guard try apple65UInt32LE(data, at: 28) == 32000 else {
        throw Apple65CoreError.terse("wave:byteRate")
    }
    guard try apple65UInt16LE(data, at: 32) == 2 else {
        throw Apple65CoreError.terse("wave:blockAlign")
    }
    guard try apple65UInt16LE(data, at: 34) == 16 else {
        throw Apple65CoreError.terse("wave:bitsPerSample")
    }
    guard try apple65UInt32LE(data, at: 40) == expectedDataSize else {
        throw Apple65CoreError.terse("wave:dataSize")
    }
}

private func apple65DataSlice(_ data: Data, offset: Int, count: Int) throws -> Data {
    guard offset >= 0, count >= 0, offset <= data.count, count <= data.count - offset else {
        throw Apple65CoreError.terse("data:sliceBounds")
    }
    let start = data.index(data.startIndex, offsetBy: offset)
    let end = data.index(start, offsetBy: count)
    return Data(data[start..<end])
}

private func apple65ASCIIEquals(_ data: Data, at offset: Int, _ expected: [UInt8]) throws -> Bool {
    guard offset >= 0, expected.count <= data.count - offset else {
        throw Apple65CoreError.terse("data:asciiBounds")
    }
    var index = 0
    while index < expected.count {
        if try apple65Byte(data, at: offset + index) != expected[index] {
            return false
        }
        index += 1
    }
    return true
}

private func apple65UInt16LE(_ data: Data, at offset: Int) throws -> UInt16 {
    guard offset >= 0, offset <= data.count - 2 else {
        throw Apple65CoreError.terse("data:uint16Bounds")
    }
    let b0 = try apple65Byte(data, at: offset)
    let b1 = try apple65Byte(data, at: offset + 1)
    return UInt16(b0) | (UInt16(b1) << 8)
}

private func apple65UInt32LE(_ data: Data, at offset: Int) throws -> UInt32 {
    guard offset >= 0, offset <= data.count - 4 else {
        throw Apple65CoreError.terse("data:uint32Bounds")
    }
    let b0 = try apple65Byte(data, at: offset)
    let b1 = try apple65Byte(data, at: offset + 1)
    let b2 = try apple65Byte(data, at: offset + 2)
    let b3 = try apple65Byte(data, at: offset + 3)
    return UInt32(b0) | (UInt32(b1) << 8) | (UInt32(b2) << 16) | (UInt32(b3) << 24)
}

private func apple65Byte(_ data: Data, at offset: Int) throws -> UInt8 {
    guard offset >= 0, offset < data.count else {
        throw Apple65CoreError.terse("data:byteBounds")
    }
    return data[data.index(data.startIndex, offsetBy: offset)]
}

private func apple65String(_ object: [String: Any], _ key: String) throws -> String {
    guard let value = object[key] as? String else {
        throw Apple65CoreError.terse("field:\(key)")
    }
    return value
}

private func apple65StrictInt(_ value: Any?) -> Int? {
    guard let value = value else { return nil }
    if let number = value as? NSNumber {
        let type = String(cString: number.objCType)
        switch type {
        case "i", "s", "l", "q":
            return Int(exactly: number.int64Value)
        case "I", "S", "L", "Q":
            return Int(exactly: number.uint64Value)
        default:
            return nil
        }
    }
    if let int = value as? Int {
        return int
    }
    if let uint = value as? UInt, let int = Int(exactly: uint) {
        return int
    }
    return nil
}

private func apple65IsValidID(_ id: String) -> Bool {
    guard !id.contains("..") else { return false }
    let bytes = Array(id.utf8)
    guard bytes.count >= 1, bytes.count <= 128 else { return false }
    guard apple65IsASCIIAlnum(bytes[0]) else { return false }
    for byte in bytes.dropFirst() {
        guard apple65IsASCIIAlnum(byte) || byte == 0x2E || byte == 0x5F || byte == 0x2D else {
            return false
        }
    }
    return true
}

private func apple65IsASCIIAlnum(_ byte: UInt8) -> Bool {
    (byte >= 0x41 && byte <= 0x5A) || (byte >= 0x61 && byte <= 0x7A) || (byte >= 0x30 && byte <= 0x39)
}

private func apple65IsLowerHex64(_ value: String) -> Bool {
    let bytes = Array(value.utf8)
    guard bytes.count == 64 else { return false }
    for byte in bytes {
        let isDigit = byte >= 0x30 && byte <= 0x39
        let isLowerHexLetter = byte >= 0x61 && byte <= 0x66
        if !isDigit && !isLowerHexLetter { return false }
    }
    return true
}
