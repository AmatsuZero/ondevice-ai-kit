import Foundation
import AVFoundation

func convert(input: URL, output: URL, semitones: Int) throws {
    guard [0,4,-4].contains(semitones) else { throw CocoaError(.fileReadUnknown) }
    guard input.standardizedFileURL != output.standardizedFileURL else { throw CocoaError(.fileWriteNoPermission) }

    let attrs = try FileManager.default.attributesOfItem(atPath: input.path)
    guard let size = attrs[.size] as? NSNumber, size.intValue <= 2880044 else { throw CocoaError(.fileReadUnknown) }
    let data = try Data(contentsOf: input, options: .mappedIfSafe)
    guard data.count <= 2880044 else { throw CocoaError(.fileReadUnknown) }
    guard data.count >= 46 else { throw CocoaError(.fileReadUnknown) }

    func u16(_ index: Int) -> Int {
        return Int(data[index]) | (Int(data[index + 1]) << 8)
    }

    func u32(_ index: Int) -> Int {
        return Int(data[index]) | (Int(data[index + 1]) << 8) | (Int(data[index + 2]) << 16) | (Int(data[index + 3]) << 24)
    }

    guard data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46 else { throw CocoaError(.fileReadUnknown) }
    guard data[8] == 0x57 && data[9] == 0x41 && data[10] == 0x56 && data[11] == 0x45 else { throw CocoaError(.fileReadUnknown) }
    guard data[12] == 0x66 && data[13] == 0x6D && data[14] == 0x74 && data[15] == 0x20 else { throw CocoaError(.fileReadUnknown) }
    guard data[36] == 0x64 && data[37] == 0x61 && data[38] == 0x74 && data[39] == 0x61 else { throw CocoaError(.fileReadUnknown) }

    let count = data.count
    guard u32(4) == count - 8 else { throw CocoaError(.fileReadUnknown) }
    guard u32(16) == 16 else { throw CocoaError(.fileReadUnknown) }
    guard u16(20) == 1 else { throw CocoaError(.fileReadUnknown) }
    guard u16(22) == 1 else { throw CocoaError(.fileReadUnknown) }
    guard u16(34) == 16 else { throw CocoaError(.fileReadUnknown) }
    guard u16(32) == 2 else { throw CocoaError(.fileReadUnknown) }

    let rate = u32(24)
    guard [16000, 44100, 48000].contains(rate) else { throw CocoaError(.fileReadUnknown) }
    guard u32(28) == rate * 2 else { throw CocoaError(.fileReadUnknown) }

    let dataBytes = u32(40)
    guard dataBytes == count - 44 && dataBytes > 0 && dataBytes % 2 == 0 else { throw CocoaError(.fileReadUnknown) }
    guard dataBytes <= rate * 30 * 2 else { throw CocoaError(.fileReadUnknown) }

    let temp = output.deletingLastPathComponent().appendingPathComponent(UUID().uuidString + ".wav")
    defer {
        try? FileManager.default.removeItem(at: temp)
    }

    guard !FileManager.default.fileExists(atPath: output.path) else { throw CocoaError(.fileWriteNoPermission) }

    if semitones == 0 {
        try data.write(to: temp, options: .withoutOverwriting)
        try FileManager.default.moveItem(at: temp, to: output)
        return
    }

    let audioFile = try AVAudioFile(forReading: input)
    guard audioFile.length == dataBytes / 2 else { throw CocoaError(.fileReadUnknown) }

    let engine = AVAudioEngine()
    let player = AVAudioPlayerNode()
    let unit = AVAudioUnitTimePitch()

    engine.attach(player)
    engine.attach(unit)

    let fmt = audioFile.processingFormat
    engine.connect(player, to: unit, format: fmt)
    engine.connect(unit, to: engine.mainMixerNode, format: fmt)

    unit.pitch = Float(semitones * 100)
    unit.rate = 1.0

    try engine.enableManualRenderingMode(.offline, format: fmt, maximumFrameCount: 4096)

    player.scheduleFile(audioFile, at: nil)

    defer {
        player.stop()
        engine.stop()
    }

    try engine.start()
    player.play()

    let buffer = AVAudioPCMBuffer(pcmFormat: fmt, frameCapacity: 4096)!
    let settings: [String: Any] = [
        AVFormatIDKey: kAudioFormatLinearPCM,
        AVSampleRateKey: rate,
        AVNumberOfChannelsKey: 1,
        AVLinearPCMBitDepthKey: 16,
        AVLinearPCMIsFloatKey: false,
        AVLinearPCMIsBigEndianKey: false
    ]

    var writer: AVAudioFile?
    writer = try AVAudioFile(forWriting: temp, settings: settings, commonFormat: .pcmFormatFloat32, interleaved: false)

    defer {
        writer = nil
    }

    let inputFrames = audioFile.length
    var produced: Int64 = 0
    let maxAttempts = inputFrames / 4096 + 256
    var attempts = 0

    while produced < inputFrames && attempts < maxAttempts {
        attempts += 1
        let n = AVAudioFrameCount(min(Int64(buffer.frameCapacity), inputFrames - produced))

        let status = try engine.renderOffline(n, to: buffer)
        switch status {
        case .success:
            guard buffer.frameLength > 0 && buffer.frameLength <= n else { throw CocoaError(.fileReadUnknown) }
            try writer!.write(from: buffer)
            produced += Int64(buffer.frameLength)
        case .cannotDoInCurrentContext:
            continue
        case .error, .insufficientDataFromInputNode:
            throw CocoaError(.fileReadUnknown)
        @unknown default:
            throw CocoaError(.fileReadUnknown)
        }
    }

    guard produced == inputFrames else { throw CocoaError(.fileReadUnknown) }

    writer = nil
    try FileManager.default.moveItem(at: temp, to: output)
}

#if os(macOS)
let args = CommandLine.arguments
if args.count != 4 {
    fputs("Usage: \(args[0]) <input.wav> <output.wav> <semitones>\n", stderr)
    exit(1)
}

let input = URL(fileURLWithPath: args[1])
let output = URL(fileURLWithPath: args[2])
guard let semitones = Int(args[3]) else { exit(1) }

if input.pathExtension != "wav" || output.pathExtension != "wav" {
    fputs("Error: Only WAV files supported\n", stderr)
    exit(1)
}

if input == output {
    fputs("Error: Input and output must be different\n", stderr)
    exit(1)
}

do {
    try convert(input: input, output: output, semitones: semitones)
} catch {
    fputs("Error: \(error)\n", stderr)
    exit(1)
}
#endif