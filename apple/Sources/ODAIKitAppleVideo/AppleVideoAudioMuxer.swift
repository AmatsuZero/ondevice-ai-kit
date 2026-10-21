@preconcurrency import AVFoundation
import Foundation

/// Internal file-only passthrough. All URLs belong to the caller until success.
enum AppleVideoAudioMuxer {
    static func validateInput(source: URL, videoDuration: Double) async throws {
        do { _ = try await audioTrack(source: source, videoDuration: videoDuration) }
        catch is CancellationError { throw CancellationError() }
        catch let error as AppleVideoError { throw error }
        catch { throw AppleVideoError.unsupportedInput("readable audio metadata required") }
    }

    static func preserve(source: URL, processedVideo: URL, output: URL, videoDuration: Double) async throws -> Int {
        try Task.checkCancellation()
        guard source.isFileURL, processedVideo.isFileURL, output.isFileURL,
              output.standardizedFileURL != source.standardizedFileURL,
              output.standardizedFileURL != processedVideo.standardizedFileURL,
              !FileManager.default.fileExists(atPath: output.path) else {
            throw AppleVideoError.unsupportedInput("new local mux output required")
        }
        do {
            let audio = try await audioTrack(source: source, videoDuration: videoDuration)
            try Task.checkCancellation()
            guard let audio else {
                try await validateMuxedVideo(output: processedVideo, videoDuration: videoDuration)
                try FileManager.default.moveItem(at: processedVideo, to: output)
                return 0
            }
            defer { withExtendedLifetime(audio.asset) {} }
            let videoAsset = AVURLAsset(url: processedVideo)
            defer { withExtendedLifetime(videoAsset) {} }
            let videoTracks = try await videoAsset.loadTracks(withMediaType: .video)
            guard videoTracks.count == 1, let video = videoTracks.first else { throw AppleVideoError.invalidFrame }
            let videoRange = try await video.load(.timeRange)
            guard abs(videoRange.start.seconds) < 0.000001,
                  abs(videoRange.duration.seconds - videoDuration) < 0.000001 else {
                throw AppleVideoError.unsupportedInput("processed video timeline mismatch")
            }
            let audioRange = try await audio.track.load(.timeRange)
            let composition = AVMutableComposition()
            guard let composedVideo = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid),
                  let composedAudio = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) else {
                throw AppleVideoError.processingFailed
            }
            composedVideo.naturalTimeScale = try await video.load(.naturalTimeScale)
            composedAudio.naturalTimeScale = try await audio.track.load(.naturalTimeScale)
            try composedVideo.insertTimeRange(videoRange, of: video, at: .zero)
            try composedAudio.insertTimeRange(audioRange, of: audio.track, at: audioRange.start)
            guard await AVAssetExportSession.compatibility(ofExportPreset: AVAssetExportPresetPassthrough, with: composition, outputFileType: .mov),
                  let exporter = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetPassthrough),
                  exporter.supportedFileTypes.contains(.mov) else {
                throw AppleVideoError.unsupportedInput("audio passthrough to MOV unsupported")
            }
            try Task.checkCancellation()
            // The SDK overlay cancels the session and awaits its terminal completion callback.
            try await exporter.export(to: output, as: .mov)
            try Task.checkCancellation()
            try await validateMuxedVideo(output: output, videoDuration: videoDuration)
            let resultAudio = try await audioTrack(source: output, videoDuration: videoDuration)
            guard let resultAudio else { throw AppleVideoError.processingFailed }
            let resultRange = try await resultAudio.track.load(.timeRange)
            guard abs(resultRange.start.seconds - audioRange.start.seconds) < 0.000002,
                  abs(resultRange.duration.seconds - audioRange.duration.seconds) < 0.000002 else {
                throw AppleVideoError.processingFailed
            }
            try Task.checkCancellation()
            return 1
        } catch {
            if FileManager.default.fileExists(atPath: output.path) { try FileManager.default.removeItem(at: output) }
            try Task.checkCancellation()
            if let stable = error as? AppleVideoError { throw stable }
            throw AppleVideoError.processingFailed
        }
    }

    static func validateMuxedVideo(output: URL, videoDuration: Double) async throws {
        try Task.checkCancellation()
        let asset = AVURLAsset(url: output)
        defer { withExtendedLifetime(asset) {} }
        let tracks = try await asset.loadTracks(withMediaType: .video)
        guard tracks.count == 1, let track = tracks.first else { throw AppleVideoError.invalidFrame }
        let (range, size, formats) = try await (track.load(.timeRange), track.load(.naturalSize), track.load(.formatDescriptions))
        guard videoDuration.isFinite, videoDuration > 0, range.start.isNumeric, range.duration.isNumeric,
              abs(range.start.seconds) < 0.000002, abs(range.duration.seconds - videoDuration) < 0.000002,
              size == CGSize(width: 1280, height: 720), formats.count == 1,
              CMFormatDescriptionGetMediaSubType(formats[0]) == kCMVideoCodecType_HEVC else {
            throw AppleVideoError.invalidFrame
        }
    }

    private static func audioTrack(source: URL, videoDuration: Double) async throws -> (asset: AVURLAsset, track: AVAssetTrack)? {
        try Task.checkCancellation()
        guard source.isFileURL, videoDuration.isFinite, videoDuration > 0 else {
            throw AppleVideoError.unsupportedInput("finite positive video timeline required")
        }
        try AppleVideoProfile.validateFile(source)
        let asset = AVURLAsset(url: source)
        defer { withExtendedLifetime(asset) {} }
        let tracks = try await asset.loadTracks(withMediaType: .audio)
        guard tracks.count <= 1 else { throw AppleVideoError.unsupportedInput("at most one audio track supported") }
        guard let track = tracks.first else { return nil }
        let range = try await track.load(.timeRange)
        let segments = try await track.load(.segments)
        guard range.start.isNumeric, range.duration.isNumeric, range.start.seconds == 0,
              abs(range.duration.seconds - videoDuration) < 0.000001,
              !segments.isEmpty, !segments.contains(where: { $0.isEmpty }) else {
            throw AppleVideoError.unsupportedInput("zero-origin full-duration audio without empty edits required")
        }
        let formats = try await track.load(.formatDescriptions)
        guard formats.count == 1, let format = formats.first,
              CMFormatDescriptionGetMediaSubType(format) == kAudioFormatMPEG4AAC,
              let basic = CMAudioFormatDescriptionGetStreamBasicDescription(format)?.pointee,
              [44_100.0, 48_000.0].contains(basic.mSampleRate), (1...2).contains(basic.mChannelsPerFrame) else {
            throw AppleVideoError.unsupportedInput("one mono/stereo 44.1/48kHz AAC format required")
        }
        try Task.checkCancellation()
        return (asset, track)
    }
}
