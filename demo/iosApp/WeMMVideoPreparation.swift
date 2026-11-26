import CoreGraphics
import Darwin
import Foundation
import ImageIO
import UniformTypeIdentifiers

// Keep AVFoundation's legacy concurrency annotations compatible with Swift 6.
@preconcurrency import AVFoundation

@available(iOS 16.0, macOS 13.0, *)
enum WeMMVideoPreparation {
    static let frameCount = 8
    static let pairCount = 4
    static let maxFrameDimension = 512

    static func withRequest<T: Sendable>(
        video: URL,
        question: String,
        maxOutputTokens: Int,
        _ body: @Sendable (URL) async throws -> T
    ) async throws -> T {
        try Task.checkCancellation()
        try validate(video: video, question: question, maxOutputTokens: maxOutputTokens)
        try Task.checkCancellation()

        let scratch = FileManager.default.temporaryDirectory
            .appendingPathComponent("WeMMVideoPreparation-\(UUID().uuidString)", isDirectory: true)
        let mkdirResult = scratch.path.withCString { Darwin.mkdir($0, mode_t(S_IRWXU)) }
        guard mkdirResult == 0 else {
            throw NSError(domain: NSPOSIXErrorDomain, code: Int(errno))
        }

        let preparation: Result<T, Error>
        do {
            preparation = .success(try await prepare(
                video: video,
                question: question,
                maxOutputTokens: maxOutputTokens,
                in: scratch,
                body: body
            ))
        } catch {
            preparation = .failure(error)
        }

        do {
            try FileManager.default.removeItem(at: scratch)
        } catch {
            let preparationDescription: String
            switch preparation {
            case .success:
                preparationDescription = "Video preparation completed."
            case .failure(let error):
                preparationDescription = error.localizedDescription
            }
            throw CleanupError(preparation: preparationDescription, cleanup: error.localizedDescription)
        }

        switch preparation {
        case .success(let result):
            return result
        case .failure(let error):
            throw error
        }
    }

    private static func validate(video: URL, question: String, maxOutputTokens: Int) throws {
        guard video.isFileURL,
              try video.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile == true else {
            throw PreparationError.invalidInput("Video must be a local regular file URL.")
        }
        guard !question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !question.contains("\0") else {
            throw PreparationError.invalidInput("Question must be nonblank and contain no NUL characters.")
        }
        let markers = ["<|im_start|>", "<|im_end|>", "<|", "|>", "<think>", "</think>", "<__media__>"]
        guard !markers.contains(where: question.contains) else {
            throw PreparationError.invalidInput("Question contains a reserved template control marker.")
        }
        guard maxOutputTokens > 0, maxOutputTokens <= Int(Int32.max) else {
            throw PreparationError.invalidInput("Output token budget must be positive and fit in Int32.")
        }
    }

    private static func videoURLWithRecognizedExtension(video: URL, in directory: URL) throws -> URL {
        guard video.isFileURL, video.pathExtension.isEmpty else { return video }

        let handle = try FileHandle(forReadingFrom: video)
        let header: Data
        do {
            header = try handle.read(upToCount: 12) ?? Data()
        } catch {
            try? handle.close()
            throw error
        }
        try handle.close()

        guard header.count >= 8 else { return video }
        let bytes = [UInt8](header)
        let boxSize = UInt32(bytes[0]) << 24 | UInt32(bytes[1]) << 16 | UInt32(bytes[2]) << 8 | UInt32(bytes[3])
        guard let atomType = String(bytes: bytes[4..<8], encoding: .ascii) else { return video }

        let extensionHint: String?
        if atomType == "ftyp" {
            guard header.count >= 12, boxSize >= 16,
                  let brand = String(bytes: bytes[8..<12], encoding: .ascii) else { return video }
            if brand == "qt  " {
                extensionHint = "mov"
            } else if ["isom", "iso2", "iso3", "iso4", "iso5", "iso6", "iso7", "iso8", "iso9", "mp41", "mp42", "avc1", "M4V ", "M4VH", "M4VP"].contains(brand) {
                extensionHint = "mp4"
            } else {
                extensionHint = nil
            }
        } else if boxSize >= 8, ["moov", "mdat", "free", "wide"].contains(atomType) {
            extensionHint = "mov"
        } else {
            extensionHint = nil
        }

        guard let extensionHint else { return video }
        let hintedURL = directory.appendingPathComponent("\(UUID().uuidString).\(extensionHint)")
        try FileManager.default.linkItem(at: video, to: hintedURL)
        return hintedURL
    }

    private static func prepare<T: Sendable>(
        video: URL,
        question: String,
        maxOutputTokens: Int,
        in directory: URL,
        body: @Sendable (URL) async throws -> T
    ) async throws -> T {
        let assetURL = try videoURLWithRecognizedExtension(video: video, in: directory)
        let asset = AVURLAsset(url: assetURL, options: [
            AVURLAssetPreferPreciseDurationAndTimingKey: true,
            AVURLAssetReferenceRestrictionsKey: AVAssetReferenceRestrictions.forbidAll.rawValue
        ])

        let duration: CMTime
        do {
            duration = try await asset.load(.duration)
            try Task.checkCancellation()
        } catch {
            if Task.isCancelled { throw CancellationError() }
            throw error
        }

        guard duration.isValid, duration.isNumeric, duration.timescale > 0, duration.value > 0 else {
            throw PreparationError.invalidVideo("Video duration must be finite and positive.")
        }
        let durationSeconds = CMTimeGetSeconds(duration)
        guard durationSeconds.isFinite, durationSeconds > 0 else {
            throw PreparationError.invalidVideo("Video duration must be finite and positive.")
        }

        let tracks: [AVAssetTrack]
        do {
            tracks = try await asset.loadTracks(withMediaType: .video)
            try Task.checkCancellation()
        } catch {
            if Task.isCancelled { throw CancellationError() }
            throw error
        }
        guard !tracks.isEmpty else {
            throw PreparationError.invalidVideo("Video has no video track.")
        }
        try Task.checkCancellation()

        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(
            width: CGFloat(maxFrameDimension),
            height: CGFloat(maxFrameDimension)
        )
        generator.requestedTimeToleranceBefore = .zero
        generator.requestedTimeToleranceAfter = .zero

        let step = duration.value / 8

        var requestedTimes: [CMTime] = []
        requestedTimes.reserveCapacity(frameCount)
        for index in 0..<frameCount {
            // Preserve the integer-step schedule; use rational samples if truncation makes it zero.
            let time: CMTime
            if step == 0 {
                time = CMTimeMultiplyByRatio(duration, multiplier: Int32(index), divisor: Int32(frameCount))
            } else {
                time = CMTime(value: step * Int64(index), timescale: duration.timescale)
            }
            guard time.isValid, time.isNumeric else {
                throw PreparationError.invalidVideo("Requested frame time is not numeric.")
            }
            guard CMTimeCompare(time, .zero) >= 0,
                  CMTimeCompare(time, duration) < 0 else {
                throw PreparationError.invalidVideo("Requested frame time is outside the video duration.")
            }
            requestedTimes.append(time)
        }

        var actualTimes: [CMTime] = []
        actualTimes.reserveCapacity(frameCount)
        var imageWidth: Int?
        var imageHeight: Int?

        for index in 0..<frameCount {
            try Task.checkCancellation()

            let imageAndTime: (CGImage, CMTime)
            do {
                imageAndTime = try await withTaskCancellationHandler {
                    try await generator.image(at: requestedTimes[index])
                } onCancel: {
                    generator.cancelAllCGImageGeneration()
                }
            } catch {
                if Task.isCancelled { throw CancellationError() }
                throw error
            }
            try Task.checkCancellation()

            do {
                let image = imageAndTime.0
                let actualTime = imageAndTime.1

                guard actualTime.isValid, actualTime.isNumeric else {
                    throw PreparationError.invalidVideo("Decoded frame time is not numeric.")
                }
                guard CMTimeCompare(actualTime, .zero) >= 0,
                      CMTimeCompare(actualTime, duration) < 0 else {
                    throw PreparationError.invalidVideo("Decoded frame time is outside the video duration.")
                }
                let seconds = CMTimeGetSeconds(actualTime)
                guard seconds.isFinite else {
                    throw PreparationError.invalidVideo("Decoded frame time is not finite.")
                }
                if let previous = actualTimes.last,
                   CMTimeCompare(actualTime, previous) <= 0 {
                    throw PreparationError.invalidVideo("Decoded frame times are not strictly increasing.")
                }

                guard image.width > 0,
                      image.height > 0,
                      image.width <= maxFrameDimension,
                      image.height <= maxFrameDimension else {
                    throw PreparationError.invalidVideo("Decoded frame dimensions exceed the image-size bound.")
                }
                if let width = imageWidth, let height = imageHeight {
                    guard image.width == width, image.height == height else {
                        throw PreparationError.invalidVideo("Decoded frames have inconsistent dimensions.")
                    }
                } else {
                    imageWidth = image.width
                    imageHeight = image.height
                }

                let frameURL = directory.appendingPathComponent("frame-\(index).png")
                try writePNG(image, to: frameURL)
                actualTimes.append(actualTime)
            }

            try Task.checkCancellation()
        }

        let actualSeconds = actualTimes.map(CMTimeGetSeconds)
        let requestedSeconds = requestedTimes.map(CMTimeGetSeconds)
        guard requestedSeconds.allSatisfy(\.isFinite),
              actualSeconds.allSatisfy(\.isFinite),
              actualTimes.count == frameCount,
              let width = imageWidth,
              let height = imageHeight else {
            throw PreparationError.invalidVideo("Frame timing or dimensions are invalid.")
        }

        let prompt = try buildPrompt(actualSeconds: actualSeconds, question: question)
        var media: [[String: Any]] = []
        media.reserveCapacity(frameCount)
        for index in 0..<frameCount {
            let path = directory.appendingPathComponent("frame-\(index).png").path
            media.append(["path": path, "video_frame": true, "id": "hf-video+\(index)"])
        }
        let request: [String: Any] = [
            "native_input_protocol": "hf_uniform_video_v1",
            "native_prepared_media": media,
            "native_video_metadata": [
                "actual_times": actualSeconds,
                "requested_times": requestedSeconds,
                "duration": durationSeconds,
                "width": width,
                "height": height,
                "sampled_frames": frameCount
            ],
            "prompt": prompt,
            "media": [],
            "max_new_tokens": maxOutputTokens
        ]

        try Task.checkCancellation()
        let requestData = try JSONSerialization.data(withJSONObject: request, options: [.prettyPrinted, .sortedKeys])
        try requestData.write(to: directory.appendingPathComponent("request.json"), options: .atomic)
        try Task.checkCancellation()

        let result = try await body(directory)
        try Task.checkCancellation()
        return result
    }

    private static func buildPrompt(actualSeconds: [Double], question: String) throws -> String {
        var expansion = ""
        for index in 0..<pairCount {
            let midpoint = (actualSeconds[2 * index] + actualSeconds[2 * index + 1]) / 2.0
            guard midpoint.isFinite else {
                throw PreparationError.invalidVideo("Frame-pair timestamp is not finite.")
            }
            let stamp = String(format: "%.1f", midpoint)
            expansion += "<\(stamp) seconds><__media__><__media__>"
        }
        return "<|im_start|>system\nYou are a helpful assistant.<|im_end|>\n" +
            "<|im_start|>user\n" + expansion + "\n" + question +
            "<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"
    }

    private static func writePNG(_ image: CGImage, to url: URL) throws {
        guard let destination = CGImageDestinationCreateWithURL(
            url as CFURL,
            UTType.png.identifier as CFString,
            1,
            nil
        ) else {
            throw PreparationError.imageWrite("Could not create PNG destination.")
        }
        CGImageDestinationAddImage(destination, image, nil)
        guard CGImageDestinationFinalize(destination) else {
            throw PreparationError.imageWrite("Could not finalize PNG frame.")
        }
    }

    private enum PreparationError: LocalizedError {
        case invalidInput(String)
        case invalidVideo(String)
        case imageWrite(String)

        var errorDescription: String? {
            switch self {
            case .invalidInput(let message), .invalidVideo(let message), .imageWrite(let message):
                return message
            }
        }
    }

    private struct CleanupError: LocalizedError {
        let preparation: String
        let cleanup: String

        var errorDescription: String? {
            "Preparation failed: \(preparation) Cleanup failed: \(cleanup)"
        }
    }
}
