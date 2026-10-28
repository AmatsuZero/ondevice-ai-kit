@preconcurrency import AVFoundation
import CryptoKit
import Foundation

// Diagnostic only: observes the real exporter without changing its configuration.
final class ExportObservation: @unchecked Sendable {
    private let lock = NSLock()
    private var operation: Task<Int, Error>?
    private var statuses: [Int] = []
    private var cancellationStatus: Int?
    private var cancellationProgress: Float?
    private var returnedStatus: Int?
    private var returnedError: String?
    private var outputExistedOnReturn = false
    private var cancelledAt: UInt64?
    private var returnedAt: UInt64?

    func register(_ operation: Task<Int, Error>?) { lock.lock(); self.operation = operation; lock.unlock() }

    func observe(_ exporter: AVAssetExportSession) -> NSKeyValueObservation {
        exporter.observe(\.status, options: [.initial, .new]) { exporter, _ in
            let status = exporter.status
            self.lock.lock()
            self.statuses.append(status.rawValue)
            let shouldCancel = status == .exporting && self.cancellationStatus == nil
            let operation = shouldCancel ? self.operation : nil
            if shouldCancel {
                self.cancellationStatus = status.rawValue
                self.cancellationProgress = exporter.progress
                self.cancelledAt = DispatchTime.now().uptimeNanoseconds
            }
            self.lock.unlock()
            #if !EXPORT_CANCELLATION_NEGATIVE_CONTROL
            operation?.cancel()
            #endif
        }
    }

    func returned(_ exporter: AVAssetExportSession, output: URL, error: Error?) {
        lock.lock(); defer { lock.unlock() }
        returnedStatus = exporter.status.rawValue
        returnedError = error.map { String(reflecting: type(of: $0)) + ": " + String(describing: $0) }
        outputExistedOnReturn = FileManager.default.fileExists(atPath: output.path)
        returnedAt = DispatchTime.now().uptimeNanoseconds
    }

    func report() -> [String: Any] {
        lock.lock(); defer { lock.unlock() }
        return [
            "statuses": statuses, "cancellationStatus": cancellationStatus as Any? ?? NSNull(),
            "cancellationProgress": cancellationProgress as Any? ?? NSNull(),
            "returnedStatus": returnedStatus as Any? ?? NSNull(), "returnedError": returnedError as Any? ?? NSNull(),
            "outputExistedOnExportReturn": outputExistedOnReturn,
            "cancelToExportReturnNanoseconds": cancelledAt.flatMap { start in returnedAt.map { $0 - start } } as Any? ?? NSNull()
        ]
    }
}

enum ExportProbeContext {
    @TaskLocal static var observation: ExportObservation?
}

enum ProbeFailure: Error { case failed(String) }

enum ExportCancellationSuite {
    static func require(_ condition: Bool, _ reason: String) throws {
        if !condition { throw ProbeFailure.failed(reason) }
    }

    static func hash(_ url: URL) throws -> String {
        SHA256.hash(data: try Data(contentsOf: url)).map { String(format: "%02x", $0) }.joined()
    }

    private static func compressedAudio(_ url: URL) async throws -> Data {
        let asset = AVURLAsset(url: url)
        let tracks = try await asset.loadTracks(withMediaType: .audio)
        try require(tracks.count == 1, "one AAC track required")
        let reader = try AVAssetReader(asset: asset)
        let output = AVAssetReaderTrackOutput(track: tracks[0], outputSettings: nil)
        reader.add(output)
        try require(reader.startReading(), "AAC reader must start")
        var result = Data()
        while let sample = output.copyNextSampleBuffer() {
            guard let block = CMSampleBufferGetDataBuffer(sample) else { continue }
            var bytes = [UInt8](repeating: 0, count: CMBlockBufferGetDataLength(block))
            let status = bytes.withUnsafeMutableBytes {
                CMBlockBufferCopyDataBytes(block, atOffset: 0, dataLength: $0.count, destination: $0.baseAddress!)
            }
            try require(status == kCMBlockBufferNoErr, "AAC payload copy")
            result.append(contentsOf: bytes)
        }
        try require(reader.status == .completed, "AAC reader must reach completed")
        return result
    }

    static func run(fixtures: URL, root: URL) async throws -> [[String: Any]] {
        try require(!FileManager.default.fileExists(atPath: root.path), "refuse existing output directory")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
        let source = fixtures.appendingPathComponent("aac.mp4"), video = fixtures.appendingPathComponent("video-only.mov")
        let sourceHash = try hash(source), videoHash = try hash(video)
        var reports: [[String: Any]] = []
        for index in 0..<3 {
            let observation = ExportObservation()
            let output = root.appendingPathComponent("cancel-\(index).mov")
            let (start, continuation) = AsyncStream<Void>.makeStream()
            let operation = Task {
                for await _ in start { break }
                return try await ExportProbeContext.$observation.withValue(observation) {
                    try await AppleVideoAudioMuxer.preserve(source: source, processedVideo: video, output: output, videoDuration: 2)
                }
            }
            observation.register(operation)
            continuation.yield(()); continuation.finish()
            var sawCancellation = false
            do { _ = try await operation.value }
            catch is CancellationError { sawCancellation = true }
            catch { throw error }
            observation.register(nil)
            var report = observation.report()
            report["sourceSHA256"] = sourceHash
            report["processedVideoSHA256"] = videoHash
            report["callerCancellationError"] = sawCancellation
            report["outputAbsentAfterPreserve"] = !FileManager.default.fileExists(atPath: output.path)
            report["sourceUnchanged"] = try hash(source) == sourceHash
            report["processedVideoUnchanged"] = try hash(video) == videoHash
            reports.append(report)
            try JSONSerialization.data(withJSONObject: reports, options: [.prettyPrinted, .sortedKeys]).write(to: root.appendingPathComponent("cancel-runs.json"))
            try require(sawCancellation, "caller must receive CancellationError")
            try require(report["cancellationStatus"] as? Int == AVAssetExportSession.Status.exporting.rawValue, "must cancel during actual native exporting")
            try require(report["returnedStatus"] as? Int == AVAssetExportSession.Status.cancelled.rawValue, "overlay must await native cancelled terminal state")
            try require(report["outputAbsentAfterPreserve"] as? Bool == true, "cancelled output must be removed before caller return")
            try require(report["sourceUnchanged"] as? Bool == true && report["processedVideoUnchanged"] as? Bool == true, "inputs must be preserved")
            let reentry = root.appendingPathComponent("reentry-\(index).mov")
            let audioCount = try await AppleVideoAudioMuxer.preserve(source: source, processedVideo: video, output: reentry, videoDuration: 2)
            try require(audioCount == 1, "same-process reentry must preserve AAC")
            let sourceAudio = try await compressedAudio(source)
            let outputAudio = try await compressedAudio(reentry)
            try require(!sourceAudio.isEmpty && outputAudio == sourceAudio, "reentry AAC payload must be unchanged")
            try require(try hash(source) == sourceHash && hash(video) == videoHash, "reentry inputs unchanged")
            reports[index]["reentryAudioTrackCount"] = audioCount
            reports[index]["reentryAACPayloadSHA256"] = SHA256.hash(data: outputAudio).map { String(format: "%02x", $0) }.joined()
            reports[index]["reentryAACPayloadEqual"] = true
            try JSONSerialization.data(withJSONObject: reports, options: [.prettyPrinted, .sortedKeys]).write(to: root.appendingPathComponent("cancel-runs.json"))
        }
        return reports
    }
}
