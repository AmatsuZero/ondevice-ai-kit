import Foundation
import CryptoKit

private enum Apple65CheckError: Error {
    case setup(String)
}

private final class AssertionRecorder {
    private(set) var failures: [String] = []

    func pass(_ message: String) {
        print("PASS: \(message)")
    }

    func fail(_ message: String) {
        failures.append(message)
        print("FAIL: \(message)")
    }

    func expect(_ condition: Bool, _ message: String) {
        if condition {
            pass(message)
        } else {
            fail(message)
        }
    }

    func expectNoThrow(_ message: String, _ body: () throws -> Void) {
        do {
            try body()
            pass(message)
        } catch {
            fail("\(message) (threw \(error))")
        }
    }

    func expectThrows(_ message: String, _ body: () throws -> Void) {
        do {
            try body()
            fail("\(message) (did not throw)")
        } catch {
            pass(message)
        }
    }
}

private func readJSONObject(_ data: Data) throws -> [String: Any] {
    let object = try JSONSerialization.jsonObject(with: data, options: [])
    guard let dictionary = object as? [String: Any] else {
        throw Apple65CheckError.setup("top-level JSON is not an object")
    }
    return dictionary
}

private func writeJSONObject(_ object: [String: Any]) throws -> Data {
    try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
}

private func mutateCaseField(at index: Int, field: String, value: Any, in object: inout [String: Any]) throws {
    guard var cases = object["cases"] as? [[String: Any]], index >= 0, index < cases.count else {
        throw Apple65CheckError.setup("case \(index) not found")
    }
    cases[index][field] = value
    object["cases"] = cases
}

private func mutateCaseField(id: String, field: String, value: Any, in object: inout [String: Any]) throws {
    guard var cases = object["cases"] as? [[String: Any]],
          let index = cases.firstIndex(where: { ($0["id"] as? String) == id }) else {
        throw Apple65CheckError.setup("case id \(id) not found")
    }
    cases[index][field] = value
    object["cases"] = cases
}

private func removeCaseField(at index: Int, field: String, in object: inout [String: Any]) throws {
    guard var cases = object["cases"] as? [[String: Any]], index >= 0, index < cases.count else {
        throw Apple65CheckError.setup("case \(index) not found")
    }
    cases[index].removeValue(forKey: field)
    object["cases"] = cases
}

private func replacingNumberToken(in data: Data, key: String, old: String, new: String, occurrence: Int) throws -> Data {
    var text = String(decoding: data, as: UTF8.self)
    let needle = "\"\(key)\":\(old)"
    let replacement = "\"\(key)\":\(new)"
    var searchStart = text.startIndex
    var found: Range<String.Index>?
    for _ in 0..<occurrence {
        guard let range = text.range(of: needle, range: searchStart..<text.endIndex) else {
            throw Apple65CheckError.setup("number token \(needle) not found")
        }
        found = range
        searchStart = range.upperBound
    }
    guard let range = found else {
        throw Apple65CheckError.setup("number token \(needle) not found")
    }
    text.replaceSubrange(range, with: replacement)
    return Data(text.utf8)
}

private func isLowerHex64(_ value: String) -> Bool {
    guard value.utf8.count == 64 else { return false }
    return value.utf8.allSatisfy { byte in
        (byte >= 48 && byte <= 57) || (byte >= 97 && byte <= 102)
    }
}

private func isValidID(_ id: String) -> Bool {
    guard !id.contains(".."),
          let regex = try? NSRegularExpression(pattern: "^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$") else {
        return false
    }
    let range = NSRange(id.startIndex..., in: id)
    return regex.numberOfMatches(in: id, range: range) == 1
}

private struct WaveParts {
    let riffSize: UInt32
    let declaredDataBytes: UInt32
    let audioFormat: UInt16
    let channels: UInt16
    let sampleRate: UInt32
    let byteRate: UInt32
    let blockAlign: UInt16
    let bitsPerSample: UInt16
}

private func fourCC(at offset: Int, length: Int = 4, in data: Data) throws -> String {
    guard offset >= 0, offset + length <= data.count else {
        throw Apple65CheckError.setup("fourCC out of bounds")
    }
    return data.withUnsafeBytes { rawBuffer in
        String(decoding: rawBuffer[offset..<(offset + length)], as: UTF8.self)
    }
}

private func uint8(at offset: Int, in data: Data) throws -> UInt8 {
    guard offset >= 0, offset + 1 <= data.count else {
        throw Apple65CheckError.setup("uint8 out of bounds")
    }
    return data.withUnsafeBytes { rawBuffer in
        rawBuffer.load(fromByteOffset: offset, as: UInt8.self)
    }
}

private func uint16(at offset: Int, in data: Data) throws -> UInt16 {
    guard offset >= 0, offset + 2 <= data.count else {
        throw Apple65CheckError.setup("uint16 out of bounds")
    }
    return data.withUnsafeBytes { rawBuffer in
        rawBuffer.load(fromByteOffset: offset, as: UInt16.self).littleEndian
    }
}

private func uint32(at offset: Int, in data: Data) throws -> UInt32 {
    guard offset >= 0, offset + 4 <= data.count else {
        throw Apple65CheckError.setup("uint32 out of bounds")
    }
    return data.withUnsafeBytes { rawBuffer in
        rawBuffer.load(fromByteOffset: offset, as: UInt32.self).littleEndian
    }
}

private func setUInt8(_ value: UInt8, at offset: Int, in data: inout Data) throws {
    guard offset >= 0, offset + 1 <= data.count else {
        throw Apple65CheckError.setup("setUInt8 out of bounds")
    }
    data.withUnsafeMutableBytes { rawBuffer in
        rawBuffer.storeBytes(of: value, toByteOffset: offset, as: UInt8.self)
    }
}

private func setUInt16(_ value: UInt16, at offset: Int, in data: inout Data) throws {
    guard offset >= 0, offset + 2 <= data.count else {
        throw Apple65CheckError.setup("setUInt16 out of bounds")
    }
    data.withUnsafeMutableBytes { rawBuffer in
        rawBuffer.storeBytes(of: value.littleEndian, toByteOffset: offset, as: UInt16.self)
    }
}

private func setUInt32(_ value: UInt32, at offset: Int, in data: inout Data) throws {
    guard offset >= 0, offset + 4 <= data.count else {
        throw Apple65CheckError.setup("setUInt32 out of bounds")
    }
    data.withUnsafeMutableBytes { rawBuffer in
        rawBuffer.storeBytes(of: value.littleEndian, toByteOffset: offset, as: UInt32.self)
    }
}

private func setASCII(_ text: String, at offset: Int, length: Int, in data: inout Data) throws {
    let bytes = Array(text.utf8)
    guard bytes.count == length, offset >= 0, offset + length <= data.count else {
        throw Apple65CheckError.setup("setASCII out of bounds")
    }
    data.replaceSubrange(offset..<(offset + length), with: bytes)
}

private func parseWave(_ data: Data) throws -> WaveParts {
    guard data.count >= 44 else {
        throw Apple65CheckError.setup("WAV shorter than 44 bytes")
    }
    guard try fourCC(at: 0, in: data) == "RIFF",
          try fourCC(at: 8, in: data) == "WAVE",
          try fourCC(at: 12, in: data) == "fmt ",
          try fourCC(at: 36, in: data) == "data" else {
        throw Apple65CheckError.setup("WAV chunk identifiers are not exact")
    }
    let riffSize = try uint32(at: 4, in: data)
    let subchunk1Size = try uint32(at: 16, in: data)
    let audioFormat = try uint16(at: 20, in: data)
    let channels = try uint16(at: 22, in: data)
    let sampleRate = try uint32(at: 24, in: data)
    let byteRate = try uint32(at: 28, in: data)
    let blockAlign = try uint16(at: 32, in: data)
    let bitsPerSample = try uint16(at: 34, in: data)
    let declaredDataBytes = try uint32(at: 40, in: data)
    guard subchunk1Size == 16, audioFormat == 1, channels == 1,
          sampleRate == 16000, byteRate == 32000, blockAlign == 2,
          bitsPerSample == 16, Int(riffSize) == data.count - 8,
          Int(declaredDataBytes) == data.count - 44 else {
        throw Apple65CheckError.setup("WAV header values are not exact")
    }
    return WaveParts(riffSize: riffSize, declaredDataBytes: declaredDataBytes,
                     audioFormat: audioFormat, channels: channels,
                     sampleRate: sampleRate, byteRate: byteRate,
                     blockAlign: blockAlign, bitsPerSample: bitsPerSample)
}

private func wavePayload(_ data: Data) throws -> Data {
    _ = try parseWave(data)
    return data.subdata(in: 44..<data.count)
}

@main
struct Apple65CoreChecks {
    static func main() {
        let recorder = AssertionRecorder()
        defer {
            if recorder.failures.isEmpty {
                exit(0)
            } else {
                print("SUMMARY: \(recorder.failures.count) failure(s)")
                exit(1)
            }
        }

        guard CommandLine.arguments.count >= 2 else {
            recorder.fail("argv[1] must be the AudioFixtures root path")
            return
        }

        let rootURL = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
        let manifestURL = rootURL.appendingPathComponent("manifest.json")
        let expectedManifestDigest = "7e2133c9292e978ac45050349d03d4b1432f247da6dceda6facd328bb7a62453"

        let manifestData: Data
        do {
            manifestData = try Data(contentsOf: manifestURL)
        } catch {
            recorder.fail("read manifest.json failed: \(error)")
            return
        }

        let baseline: Apple65Manifest
        let baselineObject: [String: Any]
        do {
            baselineObject = try readJSONObject(manifestData)
            baseline = try JSONDecoder().decode(Apple65Manifest.self, from: manifestData)
        } catch {
            recorder.fail("decode baseline manifest failed: \(error)")
            return
        }

        guard !baseline.cases.isEmpty else {
            recorder.fail("baseline manifest has no cases")
            return
        }

        recorder.expect(Apple65Core.sha256(manifestData) == expectedManifestDigest,
                        "manifest.json SHA256 equals required pinned digest")
        recorder.expect(Apple65Core.sha256(Data("abc".utf8)) == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                        "SHA256 utility matches standard abc vector")

        let expectedRootKeys: Set<String> = ["schemaVersion", "runId", "backendId", "inputBindingSHA256", "sampleRate", "channels", "sampleWidth", "cases"]
        recorder.expect(Set(baselineObject.keys) == expectedRootKeys,
                        "manifest root has exactly expected fields")
        let expectedCaseKeys: Set<String> = ["id", "cohort", "locale", "reference", "samples", "pcmSHA256", "wavPath", "wavBytes", "wavSHA256"]
        let rawCases = baselineObject["cases"] as? [[String: Any]] ?? []
        recorder.expect(rawCases.count == 65 && rawCases.allSatisfy { Set($0.keys) == expectedCaseKeys },
                        "raw cases have exactly expected fields")

        recorder.expect(baseline.schemaVersion == 1, "baseline schemaVersion is 1")
        recorder.expect(baseline.runId == "w3-apple-punctuation-paired6-20261116-01", "baseline runId is exact")
        recorder.expect(baseline.backendId == "apple.speech.recognizer", "baseline backendId is exact")
        recorder.expect(baseline.sampleRate == 16000, "baseline sampleRate is 16000")
        recorder.expect(baseline.channels == 1, "baseline channels is 1")
        recorder.expect(baseline.sampleWidth == 2, "baseline sampleWidth is 2")
        recorder.expect(isLowerHex64(baseline.inputBindingSHA256), "inputBindingSHA256 is 64 lowercase hex")

        var loadedReal: Apple65Manifest?
        recorder.expectNoThrow("loadManifest accepts real manifest with pinned digest") {
            loadedReal = try Apple65Core.loadManifest(manifestData, expectedSHA256: expectedManifestDigest)
        }
        if let loaded = loadedReal {
            recorder.expect(loaded.cases.count == 65, "loadManifest returns 65 cases")
            recorder.expect(loaded.cases.map { $0.id } == baseline.cases.map { $0.id }, "loadManifest returns cases in source order")
            recorder.expect(loaded.cases.map { $0.reference } == baseline.cases.map { $0.reference }, "loadManifest leaves raw references unchanged")
        } else {
            recorder.fail("loadManifest returns 65 ordered cases")
            recorder.fail("loadManifest leaves raw references unchanged")
        }

        func mutated(_ label: String, _ transform: (inout [String: Any]) throws -> Void) -> Data? {
            do {
                var object = try readJSONObject(manifestData)
                try transform(&object)
                return try writeJSONObject(object)
            } catch {
                recorder.fail("test setup for \(label) failed: \(error)")
                return nil
            }
        }

        func rejectManifest(_ data: Data?, _ label: String) {
            guard let data = data else { return }
            recorder.expectThrows(label) {
                _ = try Apple65Core.loadManifest(data, expectedSHA256: Apple65Core.sha256(data))
            }
        }

        func acceptManifest(_ data: Data?, _ label: String) -> Apple65Manifest? {
            guard let data = data else { return nil }
            var accepted: Apple65Manifest?
            recorder.expectNoThrow(label) {
                accepted = try Apple65Core.loadManifest(data, expectedSHA256: Apple65Core.sha256(data))
            }
            return accepted
        }

        func decodedClip(_ data: Data?, id: String, label: String) -> Apple65Clip? {
            guard let data = data else { return nil }
            do {
                let decoded = try JSONDecoder().decode(Apple65Manifest.self, from: data)
                guard let clip = decoded.cases.first(where: { $0.id == id }) else {
                    throw Apple65CheckError.setup("decoded clip \(id) not found")
                }
                return clip
            } catch {
                recorder.fail("test setup for \(label) failed: \(error)")
                return nil
            }
        }

        let canonicalManifestData = try? writeJSONObject(baselineObject)
        func rawNumberData(_ label: String, key: String, old: String, new: String, occurrence: Int = 1) -> Data? {
            guard let canonical = canonicalManifestData else {
                recorder.fail("test setup for \(label) failed: canonical JSON unavailable")
                return nil
            }
            do {
                return try replacingNumberToken(in: canonical, key: key, old: old, new: new, occurrence: occurrence)
            } catch {
                recorder.fail("test setup for \(label) failed: \(error)")
                return nil
            }
        }

        recorder.expectThrows("wrong whole-manifest digest rejected") {
            _ = try Apple65Core.loadManifest(manifestData, expectedSHA256: String(repeating: "0", count: 64))
        }

        rejectManifest(mutated("schemaVersion wrong") { object in object["schemaVersion"] = 2 }, "wrong schemaVersion rejected")
        rejectManifest(mutated("runId wrong") { object in object["runId"] = "wrong" }, "wrong runId rejected")
        rejectManifest(mutated("backendId wrong") { object in object["backendId"] = "wrong" }, "wrong backendId rejected")
        rejectManifest(mutated("sampleRate wrong") { object in object["sampleRate"] = 44100 }, "wrong sampleRate rejected")
        rejectManifest(mutated("channels wrong") { object in object["channels"] = 2 }, "wrong channels rejected")
        rejectManifest(mutated("sampleWidth wrong") { object in object["sampleWidth"] = 4 }, "wrong sampleWidth rejected")
        rejectManifest(mutated("inputBinding uppercase") { object in object["inputBindingSHA256"] = baseline.inputBindingSHA256.uppercased() }, "uppercase inputBindingSHA256 rejected")
        rejectManifest(mutated("inputBinding short") { object in object["inputBindingSHA256"] = String(baseline.inputBindingSHA256.dropLast()) }, "short inputBindingSHA256 rejected")

        rejectManifest(mutated("schemaVersion boolean") { object in object["schemaVersion"] = NSNumber(value: true) }, "boolean schemaVersion rejected")
        rejectManifest(rawNumberData("schemaVersion 1.0", key: "schemaVersion", old: "1", new: "1.0"), "JSON 1.0 schemaVersion rejected")
        rejectManifest(mutated("sampleRate boolean") { object in object["sampleRate"] = NSNumber(value: true) }, "boolean sampleRate rejected")
        rejectManifest(rawNumberData("sampleRate double", key: "sampleRate", old: String(baseline.sampleRate), new: "\(baseline.sampleRate).0"), "JSON double sampleRate rejected")
        rejectManifest(mutated("samples boolean") { object in try mutateCaseField(at: 0, field: "samples", value: NSNumber(value: true), in: &object) }, "boolean case samples rejected")
        rejectManifest(rawNumberData("samples double", key: "samples", old: String(baseline.cases[0].samples), new: "\(baseline.cases[0].samples).0"), "JSON double case samples rejected")
        rejectManifest(mutated("wavBytes boolean") { object in try mutateCaseField(at: 0, field: "wavBytes", value: NSNumber(value: true), in: &object) }, "boolean case wavBytes rejected")
        rejectManifest(rawNumberData("wavBytes double", key: "wavBytes", old: String(baseline.cases[0].wavBytes), new: "\(baseline.cases[0].wavBytes).0"), "JSON double case wavBytes rejected")

        rejectManifest(Data("{\"schemaVersion\":".utf8), "malformed JSON rejected")
        rejectManifest(mutated("missing runId") { object in object.removeValue(forKey: "runId") }, "missing runId rejected")
        rejectManifest(mutated("missing cases") { object in object.removeValue(forKey: "cases") }, "missing cases rejected")
        rejectManifest(mutated("cases not array") { object in object["cases"] = "not an array" }, "non-array cases rejected")
        rejectManifest(mutated("case missing wavSHA256") { object in try removeCaseField(at: 0, field: "wavSHA256", in: &object) }, "case missing wavSHA256 rejected")
        rejectManifest(mutated("case id numeric") { object in try mutateCaseField(at: 0, field: "id", value: 123, in: &object) }, "numeric case id rejected")

        let hugeManifest = mutated("oversized manifest") { object in
            try mutateCaseField(at: 0, field: "reference", value: String(repeating: "x", count: 300 * 1024), in: &object)
        }
        if let hugeManifest = hugeManifest {
            recorder.expect(hugeManifest.count > 256 * 1024, "oversized manifest fixture is above 256KiB")
        }
        rejectManifest(hugeManifest, "manifest larger than 256KiB rejected")

        let cohortCounts = Dictionary(grouping: baseline.cases, by: { $0.cohort }).mapValues { $0.count }
        recorder.expect(baseline.cases.count == 65, "baseline has exactly 65 cases")
        recorder.expect(cohortCounts["mandarin"] == 20, "baseline has 20 mandarin cases")
        recorder.expect(cohortCounts["english-near"] == 20, "baseline has 20 english-near cases")
        recorder.expect(cohortCounts["english-far"] == 12, "baseline has 12 english-far cases")
        recorder.expect(cohortCounts["nonspeech-noise"] == 9, "baseline has 9 nonspeech-noise cases")
        recorder.expect(cohortCounts["silence"] == 4, "baseline has 4 silence cases")
        recorder.expect(Set(cohortCounts.keys) == Set(["mandarin", "english-near", "english-far", "nonspeech-noise", "silence"]), "baseline has no unexpected cohorts")

        rejectManifest(mutated("64 cases") { object in
            guard var cases = object["cases"] as? [[String: Any]] else { throw Apple65CheckError.setup("cases missing") }
            cases.removeLast()
            object["cases"] = cases
        }, "64 cases rejected")
        rejectManifest(mutated("66 cases") { object in
            guard var cases = object["cases"] as? [[String: Any]], let first = cases.first else { throw Apple65CheckError.setup("cases missing") }
            cases.append(first)
            object["cases"] = cases
        }, "66 cases rejected")
        if let mandarinIndex = baseline.cases.firstIndex(where: { $0.cohort == "mandarin" }) {
            rejectManifest(mutated("wrong cohort") { object in try mutateCaseField(at: mandarinIndex, field: "cohort", value: "english-near", in: &object) }, "wrong cohort distribution rejected")
        }

        recorder.expect(Set(baseline.cases.map { $0.id }).count == 65, "case IDs are unique")
        recorder.expect(baseline.cases.allSatisfy { isValidID($0.id) }, "case IDs match ASCII pattern and forbid double dot")
        rejectManifest(mutated("bad ID space") { object in try mutateCaseField(at: 0, field: "id", value: "bad id", in: &object) }, "ID with space rejected")
        rejectManifest(mutated("bad ID double dot") { object in try mutateCaseField(at: 0, field: "id", value: "bad..id", in: &object) }, "ID containing double dot rejected")
        rejectManifest(mutated("bad ID traversal") { object in try mutateCaseField(at: 0, field: "id", value: "../../etc/passwd", in: &object) }, "traversal ID rejected")
        rejectManifest(mutated("duplicate ID") { object in try mutateCaseField(at: 1, field: "id", value: baseline.cases[0].id, in: &object) }, "duplicate ID rejected")

        recorder.expect(baseline.cases.allSatisfy { $0.wavPath == "audio/\($0.id).wav" }, "wavPath is exactly audio/<id>.wav for every case")
        rejectManifest(mutated("wavPath mismatch") { object in try mutateCaseField(at: 0, field: "wavPath", value: "audio/other.wav", in: &object) }, "mismatched wavPath rejected")
        rejectManifest(mutated("wavPath absolute") { object in try mutateCaseField(at: 0, field: "wavPath", value: "/audio/absolute.wav", in: &object) }, "absolute wavPath rejected")
        rejectManifest(mutated("wavPath traversal") { object in try mutateCaseField(at: 0, field: "wavPath", value: "audio/../\(baseline.cases[0].id).wav", in: &object) }, "traversal wavPath rejected")
        rejectManifest(mutated("duplicate wavPath") { object in try mutateCaseField(at: 1, field: "wavPath", value: baseline.cases[0].wavPath, in: &object) }, "duplicate wavPath rejected")

        let speechCohorts: Set<String> = ["mandarin", "english-near", "english-far"]
        let nonSilenceLocalesOK = baseline.cases.filter { $0.cohort != "silence" }.allSatisfy { clip in
            switch clip.cohort {
            case "mandarin": return clip.locale == "zh-CN"
            case "english-near", "english-far", "nonspeech-noise": return clip.locale == "en-US"
            default: return false
            }
        }
        recorder.expect(nonSilenceLocalesOK, "non-silence cohorts use required locales")
        let silenceLocales = baseline.cases.filter { $0.cohort == "silence" }.map { $0.locale }
        recorder.expect(silenceLocales.filter { $0 == "zh-CN" }.count == 2 && silenceLocales.filter { $0 == "en-US" }.count == 2,
                        "silence has 2 zh-CN and 2 en-US cases")
        recorder.expect(baseline.cases.filter { speechCohorts.contains($0.cohort) }.allSatisfy { !$0.reference.isEmpty },
                        "speech references are nonempty")
        recorder.expect(baseline.cases.filter { !speechCohorts.contains($0.cohort) }.allSatisfy { $0.reference.isEmpty },
                        "negative and silence references are empty")

        if let firstSpeechIndex = baseline.cases.firstIndex(where: { speechCohorts.contains($0.cohort) }) {
            let rawReference = "   " + baseline.cases[firstSpeechIndex].reference + "   "
            let rawReferenceData = mutated("raw reference preservation") { object in
                try mutateCaseField(at: firstSpeechIndex, field: "reference", value: rawReference, in: &object)
            }
            if let loadedRaw = acceptManifest(rawReferenceData, "loadManifest accepts raw reference mutation with recomputed digest") {
                if loadedRaw.cases.count > firstSpeechIndex {
                    recorder.expect(loadedRaw.cases[firstSpeechIndex].reference == rawReference,
                                    "loadManifest preserves raw reference text")
                } else {
                    recorder.fail("loadManifest preserves raw reference text")
                }
            } else {
                recorder.fail("loadManifest preserves raw reference text")
            }
        }

        if let mandarinIndex = baseline.cases.firstIndex(where: { $0.cohort == "mandarin" }) {
            rejectManifest(mutated("mandarin wrong locale") { object in try mutateCaseField(at: mandarinIndex, field: "locale", value: "en-US", in: &object) }, "mandarin with en-US locale rejected")
            rejectManifest(mutated("mandarin empty reference") { object in try mutateCaseField(at: mandarinIndex, field: "reference", value: "", in: &object) }, "empty mandarin reference rejected")
        }
        if let englishFarIndex = baseline.cases.firstIndex(where: { $0.cohort == "english-far" }) {
            rejectManifest(mutated("english-far wrong locale") { object in try mutateCaseField(at: englishFarIndex, field: "locale", value: "zh-CN", in: &object) }, "english-far with zh-CN locale rejected")
            rejectManifest(mutated("english-far empty reference") { object in try mutateCaseField(at: englishFarIndex, field: "reference", value: "", in: &object) }, "empty english-far reference rejected")
        }
        if let noiseIndex = baseline.cases.firstIndex(where: { $0.cohort == "nonspeech-noise" }) {
            rejectManifest(mutated("noise nonempty reference") { object in try mutateCaseField(at: noiseIndex, field: "reference", value: "should be empty", in: &object) }, "nonempty nonspeech-noise reference rejected")
        }
        if let silenceIndex = baseline.cases.firstIndex(where: { $0.cohort == "silence" }) {
            rejectManifest(mutated("silence wrong locale") { object in try mutateCaseField(at: silenceIndex, field: "locale", value: "fr-FR", in: &object) }, "silence with unsupported locale rejected")
            rejectManifest(mutated("silence nonempty reference") { object in try mutateCaseField(at: silenceIndex, field: "reference", value: "should be empty", in: &object) }, "nonempty silence reference rejected")
        }

        recorder.expect(baseline.cases.allSatisfy { $0.samples >= 48000 && $0.samples <= 160000 },
                        "all sample counts are within 48000...160000")
        recorder.expect(baseline.cases.allSatisfy { $0.wavBytes == $0.samples * 2 + 44 },
                        "all wavBytes equal samples * 2 + 44")
        recorder.expect(baseline.cases.allSatisfy { isLowerHex64($0.pcmSHA256) && isLowerHex64($0.wavSHA256) },
                        "all case digests are 64 lowercase hex")
        rejectManifest(mutated("samples low") { object in try mutateCaseField(at: 0, field: "samples", value: 47999, in: &object) }, "samples below 48000 rejected")
        rejectManifest(mutated("samples high") { object in try mutateCaseField(at: 0, field: "samples", value: 160001, in: &object) }, "samples above 160000 rejected")
        rejectManifest(mutated("wavBytes mismatch") { object in try mutateCaseField(at: 0, field: "wavBytes", value: baseline.cases[0].wavBytes + 1, in: &object) }, "wavBytes inconsistent with samples rejected")
        rejectManifest(mutated("pcm digest uppercase") { object in try mutateCaseField(at: 0, field: "pcmSHA256", value: baseline.cases[0].pcmSHA256.uppercased(), in: &object) }, "uppercase pcmSHA256 rejected")
        rejectManifest(mutated("wav digest short") { object in try mutateCaseField(at: 0, field: "wavSHA256", value: String(baseline.cases[0].wavSHA256.dropLast()), in: &object) }, "short wavSHA256 rejected")

        for (index, clip) in baseline.cases.enumerated() {
            let wavURL = rootURL.appendingPathComponent(clip.wavPath)
            let wavData: Data
            do {
                wavData = try Data(contentsOf: wavURL)
            } catch {
                recorder.fail("fixture \(clip.id) WAV readable: \(error)")
                continue
            }
            recorder.expect(wavData.count == clip.wavBytes, "fixture \(clip.id) file bytes equal wavBytes")
            recorder.expect(Apple65Core.sha256(wavData) == clip.wavSHA256, "fixture \(clip.id) whole WAV SHA matches manifest")
            do {
                let parts = try parseWave(wavData)
                recorder.expect(parts.audioFormat == 1 && parts.channels == 1 && parts.sampleRate == 16000 && parts.byteRate == 32000 && parts.blockAlign == 2 && parts.bitsPerSample == 16 && Int(parts.riffSize) == wavData.count - 8 && Int(parts.declaredDataBytes) == wavData.count - 44,
                                "fixture \(clip.id) WAV header is exact 44-byte PCM")
                let payload = try wavePayload(wavData)
                recorder.expect(payload.count == clip.samples * 2, "fixture \(clip.id) payload frames match samples")
                recorder.expect(Apple65Core.sha256(payload) == clip.pcmSHA256, "fixture \(clip.id) payload PCM SHA matches manifest")
            } catch {
                recorder.fail("fixture \(clip.id) WAV structure: \(error)")
            }
            recorder.expectNoThrow("validateWave accepts real WAV \(index + 1)/65 \(clip.id)") {
                try Apple65Core.validateWave(wavData, clip: clip)
            }
        }

        let firstClip = baseline.cases[0]
        let firstID = firstClip.id
        let firstWavData: Data
        do {
            firstWavData = try Data(contentsOf: rootURL.appendingPathComponent(firstClip.wavPath))
        } catch {
            recorder.fail("read first WAV for negative validation failed: \(error)")
            return
        }

        func mutatedWave(_ label: String, _ transform: (inout Data) throws -> Void) -> Data? {
            do {
                var wave = firstWavData
                try transform(&wave)
                return wave
            } catch {
                recorder.fail("test setup for \(label) failed: \(error)")
                return nil
            }
        }

        func updatedClip(_ label: String, wavData: Data) -> Apple65Clip? {
            let data = mutated(label) { object in
                try mutateCaseField(at: 0, field: "wavSHA256", value: Apple65Core.sha256(wavData), in: &object)
                try mutateCaseField(at: 0, field: "wavBytes", value: wavData.count, in: &object)
            }
            return decodedClip(data, id: firstID, label: label)
        }

        if let pcmTampered = mutatedWave("PCM tamper", { wave in
            try setUInt8(try uint8(at: 44, in: firstWavData) ^ 0xFF, at: 44, in: &wave)
        }) {
            recorder.expect(Apple65Core.sha256(pcmTampered) != firstClip.wavSHA256,
                            "PCM tamper fixture has a different whole WAV hash")
            recorder.expectThrows("validateWave rejects tampered PCM payload") {
                try Apple65Core.validateWave(pcmTampered, clip: firstClip)
            }
        }

        if let truncated = mutatedWave("truncated WAV", { wave in wave.removeLast() }),
           let truncatedClip = updatedClip("truncated WAV expectation", wavData: truncated) {
            recorder.expect(truncatedClip.wavBytes == truncated.count && truncatedClip.wavSHA256 == Apple65Core.sha256(truncated),
                            "truncated WAV expectation uses updated wavBytes and wavSHA256")
            recorder.expectThrows("validateWave rejects truncated WAV with updated whole-file metadata") {
                try Apple65Core.validateWave(truncated, clip: truncatedClip)
            }
        }

        let wrongPCMSHAData = mutated("wrong PCM SHA expectation") { object in
            try mutateCaseField(at: 0, field: "pcmSHA256", value: String(repeating: "0", count: 64), in: &object)
        }
        if let wrongPCMClip = decodedClip(wrongPCMSHAData, id: firstID, label: "wrong PCM SHA expectation") {
            recorder.expect(Apple65Core.sha256(firstWavData) == firstClip.wavSHA256,
                            "wrong PCM SHA expectation keeps full WAV hash unchanged")
            recorder.expectThrows("validateWave rejects wrong PCM SHA while full WAV SHA is unchanged") {
                try Apple65Core.validateWave(firstWavData, clip: wrongPCMClip)
            }
        }

        let wrongWavSHAData = mutated("wrong WAV SHA expectation") { object in
            try mutateCaseField(at: 0, field: "wavSHA256", value: String(repeating: "1", count: 64), in: &object)
        }
        if let wrongWavSHAClip = decodedClip(wrongWavSHAData, id: firstID, label: "wrong WAV SHA expectation") {
            recorder.expectThrows("validateWave rejects wrong whole WAV SHA") {
                try Apple65Core.validateWave(firstWavData, clip: wrongWavSHAClip)
            }
        }

        let wrongWavBytesData = mutated("wrong WAV bytes expectation") { object in
            try mutateCaseField(at: 0, field: "wavBytes", value: firstClip.wavBytes + 2, in: &object)
        }
        if let wrongWavBytesClip = decodedClip(wrongWavBytesData, id: firstID, label: "wrong WAV bytes expectation") {
            recorder.expectThrows("validateWave rejects wrong whole WAV byte count") {
                try Apple65Core.validateWave(firstWavData, clip: wrongWavBytesClip)
            }
        }

        let payloadByteCount = firstWavData.count - 44
        let headerMutations: [(String, (inout Data) throws -> Void)] = [
            ("RIFF marker", { wave in try setASCII("RIFX", at: 0, length: 4, in: &wave) }),
            ("WAVE marker", { wave in try setASCII("WAVX", at: 8, length: 4, in: &wave) }),
            ("fmt marker", { wave in try setASCII("fmtX", at: 12, length: 4, in: &wave) }),
            ("subchunk1 size", { wave in try setUInt32(15, at: 16, in: &wave) }),
            ("audio format", { wave in try setUInt16(3, at: 20, in: &wave) }),
            ("channels", { wave in try setUInt16(2, at: 22, in: &wave) }),
            ("sampleRate", { wave in try setUInt32(44100, at: 24, in: &wave) }),
            ("byteRate", { wave in try setUInt32(64000, at: 28, in: &wave) }),
            ("blockAlign", { wave in try setUInt16(4, at: 32, in: &wave) }),
            ("bitsPerSample", { wave in try setUInt16(8, at: 34, in: &wave) }),
            ("data marker", { wave in try setASCII("datX", at: 36, length: 4, in: &wave) }),
            ("declared data length", { wave in try setUInt32(UInt32(payloadByteCount + 2), at: 40, in: &wave) }),
            ("declared RIFF length", { wave in try setUInt32(UInt32(firstWavData.count - 8 + 2), at: 4, in: &wave) })
        ]

        for (label, mutateHeader) in headerMutations {
            guard let wav = mutatedWave(label, mutateHeader),
                  let clip = updatedClip(label, wavData: wav) else { continue }
            recorder.expect(clip.wavSHA256 == Apple65Core.sha256(wav),
                            "\(label) expectation uses decoded clip with updated wavSHA256")
            recorder.expectThrows("validateWave rejects \(label) with updated wavSHA256") {
                try Apple65Core.validateWave(wav, clip: clip)
            }
        }
    }
}