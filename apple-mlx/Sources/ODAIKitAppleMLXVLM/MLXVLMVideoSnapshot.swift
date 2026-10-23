import Foundation
import Darwin
import CoreGraphics
@preconcurrency import AVFoundation

enum MLXVLMVideoSnapshotError: Error {
    case invalidInput
}

/// Internal, experimental video admission. Public generation remains image-only.
/// Decoding is joined before load returns; frames are immutable and independent of file cleanup.
final class MLXVLMVideoSnapshot: @unchecked Sendable {
    static let maxEncodedBytes = 32 * 1024 * 1024
    static let maxDurationSeconds = 5.0
    static let maxFrames = 8
    static let maxDimension = 1920
    static let maxPixels = 2_073_600

    /// Synchronous diagnostics for the native decode phase, not a release barrier.
    enum Event: Sendable {
        case copied(URL)
        case decodedFrame(Int)
    }

    struct Frame: Sendable {
        let image: CGImage
        let requestedTime: CMTime
        let actualTime: CMTime
    }

    let file: URL
    let duration: CMTime
    let frames: [Frame]
    private let directory: URL
    private let cleanupLock = NSLock()
    private var closed = false

    private init(file: URL, duration: CMTime, frames: [Frame]) {
        self.file = file
        self.directory = file.deletingLastPathComponent()
        self.duration = duration
        self.frames = frames
    }

    static func load(_ input: URL,
                     observer: (@Sendable (Event) -> Void)? = nil) async throws -> MLXVLMVideoSnapshot {
        try Task.checkCancellation()
        let file = try copy(input)
        var transferred = false
        defer { if !transferred { try? FileManager.default.removeItem(at: file.deletingLastPathComponent()) } }
        observer?(.copied(file))
        try Task.checkCancellation()
        let asset = AVURLAsset(url: file, options: [
            AVURLAssetReferenceRestrictionsKey: AVAssetReferenceRestrictions.forbidAll.rawValue,
            AVURLAssetPreferPreciseDurationAndTimingKey: true
        ])
        do {
            let snapshot = try await withTaskCancellationHandler {
                let duration = try await validate(asset)
                try Task.checkCancellation()
                let frames = try await decode(asset, duration: duration, observer: observer)
                try Task.checkCancellation()
                return MLXVLMVideoSnapshot(file: file, duration: duration, frames: frames)
            } onCancel: { asset.cancelLoading() }
            transferred = true
            return snapshot
        } catch {
            try Task.checkCancellation()
            throw MLXVLMVideoSnapshotError.invalidInput
        }
    }

    func close() {
        cleanupLock.lock()
        defer { cleanupLock.unlock() }
        guard !closed else { return }
        closed = true
        try? FileManager.default.removeItem(at: directory)
    }

    deinit { close() }

    private static func copy(_ input: URL) throws -> URL {
        guard input.isFileURL else { throw MLXVLMVideoSnapshotError.invalidInput }
        let fd = open(input.path, O_RDONLY | O_NOFOLLOW | O_NONBLOCK)
        guard fd >= 0 else { throw MLXVLMVideoSnapshotError.invalidInput }
        let source = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
        defer { try? source.close() }
        var info = stat()
        guard fstat(fd, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG,
              info.st_size > 0, info.st_size <= maxEncodedBytes else {
            throw MLXVLMVideoSnapshotError.invalidInput
        }
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("odai-video-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false,
                                               attributes: [.posixPermissions: 0o700])
        let file = directory.appendingPathComponent("input.mp4")
        do {
            guard FileManager.default.createFile(atPath: file.path, contents: nil,
                                                 attributes: [.posixPermissions: 0o600]) else {
                throw MLXVLMVideoSnapshotError.invalidInput
            }
            let output = try FileHandle(forWritingTo: file)
            defer { try? output.close() }
            var count = 0
            while let chunk = try source.read(upToCount: min(1024 * 1024, maxEncodedBytes + 1 - count)), !chunk.isEmpty {
                try Task.checkCancellation()
                count += chunk.count
                guard count <= maxEncodedBytes else { throw MLXVLMVideoSnapshotError.invalidInput }
                try output.write(contentsOf: chunk)
            }
            guard count > 0 else { throw MLXVLMVideoSnapshotError.invalidInput }
            return file
        } catch {
            try? FileManager.default.removeItem(at: directory)
            throw error
        }
    }

    private static func validate(_ asset: AVURLAsset) async throws -> CMTime {
        let duration = try await asset.load(.duration)
        guard duration.isNumeric, duration.seconds.isFinite, duration.seconds > 0,
              duration.seconds <= maxDurationSeconds else { throw MLXVLMVideoSnapshotError.invalidInput }
        let tracks = try await asset.loadTracks(withMediaType: .video)
        guard tracks.count == 1, let track = tracks.first else { throw MLXVLMVideoSnapshotError.invalidInput }
        let size = try await track.load(.naturalSize)
        let transform = try await track.load(.preferredTransform)
        let range = try await track.load(.timeRange)
        let segments = try await track.load(.segments)
        let formats = try await track.load(.formatDescriptions)
        guard size.width.isFinite, size.height.isFinite, size.width > 0, size.height > 0,
              size.width <= CGFloat(maxDimension), size.height <= CGFloat(maxDimension),
              size.width * size.height <= CGFloat(maxPixels),
              [transform.a, transform.b, transform.c, transform.d, transform.tx, transform.ty].allSatisfy({ $0.isFinite }),
              abs(transform.a * transform.d - transform.b * transform.c) > 0,
              range.start == .zero, range.duration == duration,
              segments.count == 1, let segment = segments.first, !segment.isEmpty,
              segment.timeMapping.source.start == .zero, segment.timeMapping.target.start == .zero,
              segment.timeMapping.source.duration == segment.timeMapping.target.duration,
              !formats.isEmpty else { throw MLXVLMVideoSnapshotError.invalidInput }
        let display = CGRect(origin: .zero, size: size).applying(transform).standardized
        guard display.width > 0, display.height > 0, display.width.isFinite, display.height.isFinite,
              display.width <= CGFloat(maxDimension), display.height <= CGFloat(maxDimension),
              display.width * display.height <= CGFloat(maxPixels) else { throw MLXVLMVideoSnapshotError.invalidInput }
        for format in formats {
            let transfer = CMFormatDescriptionGetExtension(format,
                extensionKey: kCMFormatDescriptionExtension_TransferFunction) as? String
            guard CMFormatDescriptionGetMediaSubType(format) == kCMVideoCodecType_H264,
                  transfer == kCMFormatDescriptionTransferFunction_ITU_R_709_2 as String else {
                throw MLXVLMVideoSnapshotError.invalidInput
            }
        }
        return duration
    }

    private static func decode(_ asset: AVURLAsset, duration: CMTime,
                               observer: (@Sendable (Event) -> Void)?) async throws -> [Frame] {
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 512, height: 512)
        generator.requestedTimeToleranceBefore = .zero
        generator.requestedTimeToleranceAfter = .zero
        // Exclude the end time: it is not a decodable frame timestamp.
        let step = duration.value / Int64(maxFrames)
        guard step > 0 else { throw MLXVLMVideoSnapshotError.invalidInput }
        let times = (0..<maxFrames).map { CMTime(value: Int64($0) * step, timescale: duration.timescale) }
        return try await withTaskCancellationHandler {
            defer { generator.cancelAllCGImageGeneration() }
            var frames: [Frame] = []
            var invalid = false
            for await result in generator.images(for: times) {
                // Consume every pending result, including cancelled ones, before returning
                // ownership of the snapshot directory to the cleanup path.
                if Task.isCancelled || invalid { generator.cancelAllCGImageGeneration(); continue }
                switch result {
                case .success(requestedTime: let requested, image: let image, actualTime: let actual):
                    guard actual.isNumeric, actual.seconds.isFinite, actual >= .zero, actual < duration,
                          image.width <= 512, image.height <= 512,
                          frames.last.map({ actual > $0.actualTime }) ?? true else {
                        invalid = true
                        generator.cancelAllCGImageGeneration()
                        continue
                    }
                    frames.append(Frame(image: image, requestedTime: requested, actualTime: actual))
                    observer?(.decodedFrame(frames.count - 1))
                case .failure:
                    invalid = true
                    generator.cancelAllCGImageGeneration()
                }
            }
            try Task.checkCancellation()
            guard !invalid, frames.count == times.count else { throw MLXVLMVideoSnapshotError.invalidInput }
            return frames
        } onCancel: { generator.cancelAllCGImageGeneration() }
    }
}
