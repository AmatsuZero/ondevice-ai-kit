import Foundation
import AVFoundation
import ODAIKitVoiceEffects

/// Offline, file-only PCM pitch shifting. No AVAudioSession or hardware I/O.
/// Each call owns its entire graph; no engine, buffer or callback outlives render.
public struct AppleVoiceEffectsBackend: VoiceEffectsBackend {
    public let backendId = "apple.time-pitch"
    public init() {}

    public func render(_ input: VoiceEffectsPCMInput, preset: VoiceEffectsPreset,
                       checkCancellation: @escaping @Sendable () throws -> Void) throws -> Data {
        do {
            return try autoreleasepool {
                try renderPCM(input, preset: preset, checkCancellation: checkCancellation)
            }
        } catch is CancellationError { throw CancellationError() }
        catch let error as VoiceEffectsError { throw error }
        catch {
            let ns = error as NSError
            // Never carry NSError userInfo/description or file paths across the SDK seam.
            let allowed = [NSPOSIXErrorDomain, NSCocoaErrorDomain, NSOSStatusErrorDomain]
            throw VoiceEffectsError.platform(domain: allowed.contains(ns.domain) ? ns.domain : NSOSStatusErrorDomain,
                                             code: allowed.contains(ns.domain) ? ns.code : -1)
        }
    }

    private func renderPCM(_ input: VoiceEffectsPCMInput, preset: VoiceEffectsPreset,
                           checkCancellation: @escaping @Sendable () throws -> Void) throws -> Data {
        try checkCancellation()
        guard [16000, 44100, 48000].contains(input.sampleRate),
              input.frameCount > 0, input.frameCount <= Int64(input.sampleRate * 30),
              input.pcm16LittleEndian.count % 2 == 0 else {
            throw VoiceEffectsError.invalidInput(.malformedWAV)
        }
        if preset == .original { return input.pcm16LittleEndian }
        guard let format = AVAudioFormat(commonFormat: .pcmFormatFloat32,
                                        sampleRate: Double(input.sampleRate), channels: 1, interleaved: false),
              let source = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(input.frameCount)),
              let sourceChannel = source.floatChannelData?[0],
              let rendered = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 4096) else {
            throw platformFailure
        }
        source.frameLength = AVAudioFrameCount(input.frameCount)
        let bytes = input.pcm16LittleEndian
        for frame in 0..<Int(input.frameCount) {
            if frame % 4096 == 0 { try checkCancellation() }
            let bits = UInt16(bytes[frame * 2]) | UInt16(bytes[frame * 2 + 1]) << 8
            sourceChannel[frame] = Float(Int16(bitPattern: bits)) / 32768
        }

        let engine = AVAudioEngine()
        let player = AVAudioPlayerNode()
        let pitch = AVAudioUnitTimePitch()
        engine.attach(player); engine.attach(pitch)
        engine.connect(player, to: pitch, format: format)
        engine.connect(pitch, to: engine.mainMixerNode, format: format)
        pitch.rate = 1
        pitch.pitch = preset == .high ? 400 : -400

        var manualMode = false
        defer {
            player.stop()
            engine.stop()
            if manualMode { engine.disableManualRenderingMode() }
        }
        try engine.enableManualRenderingMode(.offline, format: format, maximumFrameCount: rendered.frameCapacity)
        manualMode = true
        player.scheduleBuffer(source, at: nil, options: [], completionHandler: nil)
        try checkCancellation()
        try engine.start()
        player.play()

        var output = Data(); output.reserveCapacity(bytes.count)
        var produced: Int64 = 0
        var attempts = 0
        let maximumAttempts = Int(input.frameCount / 4096) + 256
        while produced < input.frameCount {
            try checkCancellation()
            attempts += 1
            guard attempts <= maximumAttempts else { throw platformFailure }
            let requested = AVAudioFrameCount(min(Int64(rendered.frameCapacity), input.frameCount - produced))
            switch try engine.renderOffline(requested, to: rendered) {
            case .success:
                guard rendered.frameLength > 0, rendered.frameLength <= requested,
                      let samples = rendered.floatChannelData?[0] else { throw platformFailure }
                for frame in 0..<Int(rendered.frameLength) {
                    let sample = samples[frame]
                    guard sample.isFinite else { throw platformFailure }
                    let scaled = min(32767, max(-32768, (Double(sample) * 32768).rounded()))
                    let bits = UInt16(bitPattern: Int16(scaled))
                    output.append(UInt8(truncatingIfNeeded: bits))
                    output.append(UInt8(truncatingIfNeeded: bits >> 8))
                }
                produced += Int64(rendered.frameLength)
            case .cannotDoInCurrentContext:
                continue
            case .insufficientDataFromInputNode, .error:
                throw platformFailure
            @unknown default:
                throw platformFailure
            }
        }
        try checkCancellation()
        guard output.count == bytes.count else { throw platformFailure }
        return output
    }

    private var platformFailure: VoiceEffectsError {
        .platform(domain: NSOSStatusErrorDomain, code: -1)
    }
}
