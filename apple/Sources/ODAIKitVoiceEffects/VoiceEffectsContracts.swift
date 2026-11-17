import Foundation

public enum VoiceEffectsPreset: String, Sendable, Equatable, Codable, CaseIterable {
    case original, high, low
}

public struct VoiceEffectsRequest: Sendable, Equatable {
    public let inputFile: URL
    public let preset: VoiceEffectsPreset
    public init(inputFile: URL, preset: VoiceEffectsPreset) {
        self.inputFile = inputFile; self.preset = preset
    }
}

public struct VoiceEffectsOutput: Sendable, Equatable {
    public let id: String
    public let audioFile: URL
    public let backendId: String
    public let sampleRate: Int
    public let frameCount: Int64
    public var durationSeconds: Double { Double(frameCount) / Double(sampleRate) }

    init(id: String, audioFile: URL, backendId: String, sampleRate: Int, frameCount: Int64) {
        self.id = id; self.audioFile = audioFile; self.backendId = backendId
        self.sampleRate = sampleRate; self.frameCount = frameCount
    }
}

/// Immutable validated input snapshot; no caller file handle crosses this boundary.
public struct VoiceEffectsPCMInput: Sendable {
    public let pcm16LittleEndian: Data
    public let sampleRate: Int
    public var frameCount: Int64 { Int64(pcm16LittleEndian.count / 2) }
    init(pcm16LittleEndian: Data, sampleRate: Int) {
        self.pcm16LittleEndian = pcm16LittleEndian; self.sampleRate = sampleRate
    }
}

/// Native injection seam, not a new cross-platform backend-selection contract.
/// Called on a detached worker, once per operation. Return/throw only after all
/// rendering and resource cleanup have finished. Do not retain input or callbacks,
/// spawn unjoined work, publish files, use hardware I/O, or mutate audio sessions.
/// Return exactly input.frameCount mono PCM16 little-endian samples. `original`
/// bypass is performed by the client and never calls this method.
public protocol VoiceEffectsBackend: Sendable {
    var backendId: String { get }
    func render(_ input: VoiceEffectsPCMInput, preset: VoiceEffectsPreset,
                checkCancellation: @escaping @Sendable () throws -> Void) throws -> Data
}

/// Fixed, path-free reasons map directly to the frozen bridge failure categories.
public enum VoiceEffectsInputFailure: String, Sendable, Equatable {
    case file, malformedWAV, unknownOutput, outputDirectory
}
public enum VoiceEffectsUnsupportedInput: String, Sendable, Equatable {
    case nonCanonicalWAV, encoding, sampleRate, duration
}
public enum VoiceEffectsError: Error, Sendable, Equatable {
    case closed
    case busy
    case invalidInput(VoiceEffectsInputFailure)
    case unsupportedInput(VoiceEffectsUnsupportedInput)
    case backendUnavailable
    case platform(domain: String, code: Int)
}

func voiceEffectsSanitize(_ error: Error) -> Error {
    if error is CancellationError { return CancellationError() }
    if let known = error as? VoiceEffectsError {
        if case .platform(let domain, let code) = known {
            let allowed = [NSPOSIXErrorDomain, NSCocoaErrorDomain, NSOSStatusErrorDomain]
            return VoiceEffectsError.platform(domain: allowed.contains(domain) ? domain : NSOSStatusErrorDomain,
                                             code: allowed.contains(domain) ? code : -1)
        }
        return known
    }
    // Never expose localizedDescription, userInfo, input paths or arbitrary domains.
    let ns = error as NSError
    let allowed = [NSPOSIXErrorDomain, NSCocoaErrorDomain, NSOSStatusErrorDomain]
    return VoiceEffectsError.platform(domain: allowed.contains(ns.domain) ? ns.domain : NSOSStatusErrorDomain,
                                     code: allowed.contains(ns.domain) ? ns.code : -1)
}
