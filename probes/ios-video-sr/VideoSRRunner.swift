#if !targetEnvironment(simulator)
import AVFoundation
import CoreVideo
import CoreImage
import Foundation
import VideoToolbox

@available(iOS 26.0, *)
enum VideoSRRunner {
    enum RunnerError: LocalizedError {
        case noVideoTrack, readerFailed(String), writerFailed(String), invalidFrame, processingFailed(String)
        var errorDescription: String? {
            switch self {
            case .noVideoTrack: return "video track missing"
            case .readerFailed(let value): return "reader failed: \(value)"
            case .writerFailed(let value): return "writer failed: \(value)"
            case .invalidFrame: return "pixel buffer could not be wrapped as IOSurface frame"
            case .processingFailed(let value): return "processor failed: \(value)"
            }
        }
    }

    static func run(inputURL: URL, outputURL: URL, configuration: VTSuperResolutionScalerConfiguration) async throws -> [String: Any] {
        let asset = AVURLAsset(url: inputURL)
        guard let track = try await asset.loadTracks(withMediaType: .video).first else { throw RunnerError.noVideoTrack }
        let reader = try AVAssetReader(asset: asset)
        let outputSettings: [String: Any] = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferIOSurfacePropertiesKey as String: [:]
        ]
        let readerOutput = AVAssetReaderTrackOutput(track: track, outputSettings: outputSettings)
        readerOutput.alwaysCopiesSampleData = false
        guard reader.canAdd(readerOutput) else { throw RunnerError.readerFailed("cannot add track output") }
        reader.add(readerOutput)

        let writer = try AVAssetWriter(outputURL: outputURL, fileType: .mov)
        var completed = false
        defer {
            if !completed {
                reader.cancelReading()
                writer.cancelWriting()
                try? FileManager.default.removeItem(at: outputURL)
            }
        }
        let writerInput = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.hevc,
            AVVideoWidthKey: 1280,
            AVVideoHeightKey: 720
        ])
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: writerInput, sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferIOSurfacePropertiesKey as String: [:]
        ])
        guard writer.canAdd(writerInput) else { throw RunnerError.writerFailed("cannot add video input") }
        writer.add(writerInput)
        guard reader.startReading() else { throw RunnerError.readerFailed(reader.error?.localizedDescription ?? "unknown") }

        let processor = VTFrameProcessor()
        let ciContext = CIContext(options: [.useSoftwareRenderer: false])
        var startTime: CMTime?
        var previousSource: VTFrameProcessorFrame?
        var previousOutput: VTFrameProcessorFrame?
        var frameCount = 0
        var timestamps: [CMTime] = []
        var durations: [Double] = []
        guard let sample = readerOutput.copyNextSampleBuffer(), let firstBuffer = CMSampleBufferGetImageBuffer(sample) else {
            throw RunnerError.readerFailed("empty video")
        }
        let firstPTS = CMSampleBufferGetPresentationTimeStamp(sample)
        startTime = firstPTS
        guard let firstSource = makeSourceFrame(from: firstBuffer, pts: firstPTS, context: ciContext) else { throw RunnerError.invalidFrame }
        guard writer.startWriting() else { throw RunnerError.writerFailed(writer.error?.localizedDescription ?? "unknown") }
        writer.startSession(atSourceTime: firstPTS)
        try processor.startSession(configuration: configuration)
        defer { processor.endSession() }

        var current = firstSource
        while true {
            try Task.checkCancellation()
            let sourceFrame = current
            guard let destinationBuffer = makeDestinationBuffer(configuration: configuration) else { throw RunnerError.invalidFrame }
            guard let destinationFrame = VTFrameProcessorFrame(buffer: destinationBuffer, presentationTimeStamp: sourceFrame.presentationTimeStamp),
                  let parameters = VTSuperResolutionScalerParameters(sourceFrame: sourceFrame, previousFrame: previousSource, previousOutputFrame: previousOutput, opticalFlow: nil, submissionMode: previousSource == nil ? .random : .sequential, destinationFrame: destinationFrame) else {
                throw RunnerError.invalidFrame
            }
            let started = ProcessInfo.processInfo.systemUptime
            do { try await processor.process(parameters: parameters) }
            catch {
                try Task.checkCancellation()
                throw RunnerError.processingFailed(String(describing: error))
            }
            durations.append(ProcessInfo.processInfo.systemUptime - started)
            guard let encodedBuffer = makeBuffer(width: 1280, height: 720, format: kCVPixelFormatType_32BGRA) else { throw RunnerError.invalidFrame }
            ciContext.render(CIImage(cvPixelBuffer: destinationBuffer), to: encodedBuffer)
            while !writerInput.isReadyForMoreMediaData { try await Task.sleep(for: .milliseconds(2)) }
            guard adaptor.append(encodedBuffer, withPresentationTime: sourceFrame.presentationTimeStamp) else { throw RunnerError.writerFailed(writer.error?.localizedDescription ?? "append failed") }
            timestamps.append(sourceFrame.presentationTimeStamp)
            frameCount += 1
            previousSource = sourceFrame
            previousOutput = destinationFrame
            guard let nextSample = readerOutput.copyNextSampleBuffer() else { break }
            guard let nextBuffer = CMSampleBufferGetImageBuffer(nextSample), let nextFrame = makeSourceFrame(from: nextBuffer, pts: CMSampleBufferGetPresentationTimeStamp(nextSample), context: ciContext) else { throw RunnerError.invalidFrame }
            current = nextFrame
        }
        guard reader.status == .completed else { throw RunnerError.readerFailed(reader.error?.localizedDescription ?? "incomplete") }
        writerInput.markAsFinished()
        await writer.finishWriting()
        try Task.checkCancellation()
        guard writer.status == .completed else { throw RunnerError.writerFailed(writer.error?.localizedDescription ?? "finish failed") }
        let monotonic = zip(timestamps, timestamps.dropFirst()).allSatisfy { $0.1 > $0.0 }
        guard frameCount == 48, monotonic else { throw RunnerError.processingFailed("frame count or timestamps invalid") }
        completed = true
        let sorted = durations.sorted()
        return ["schemaVersion": 1, "validationStatus": "device", "backendId": "apple.videotoolbox.sr", "state": "success", "inputFrameCount": frameCount, "outputFrameCount": frameCount, "outputWidth": 1280, "outputHeight": 720, "timestampsMonotonic": monotonic, "p50Seconds": sorted.isEmpty ? NSNull() : sorted[sorted.count / 2], "p95Seconds": sorted.isEmpty ? NSNull() : sorted[min(sorted.count - 1, Int(Double(sorted.count) * 0.95))], "output": outputURL.lastPathComponent, "startTime": startTime.map { CMTimeGetSeconds($0) } ?? NSNull()]
    }

    private static func makeSourceFrame(from buffer: CVPixelBuffer, pts: CMTime, context: CIContext) -> VTFrameProcessorFrame? {
        guard let converted = makeBuffer(width: 320, height: 180, format: kCVPixelFormatType_64RGBAHalf) else { return nil }
        context.render(CIImage(cvPixelBuffer: buffer), to: converted)
        return VTFrameProcessorFrame(buffer: converted, presentationTimeStamp: pts)
    }

    private static func makeDestinationBuffer(configuration: VTSuperResolutionScalerConfiguration) -> CVPixelBuffer? {
        _ = configuration
        return makeBuffer(width: 1280, height: 720, format: kCVPixelFormatType_64RGBAHalf)
    }

    private static func makeBuffer(width: Int, height: Int, format: OSType) -> CVPixelBuffer? {
        let attrs: [String: Any] = [kCVPixelBufferIOSurfacePropertiesKey as String: [:], kCVPixelBufferWidthKey as String: width, kCVPixelBufferHeightKey as String: height, kCVPixelBufferPixelFormatTypeKey as String: format]
        var buffer: CVPixelBuffer?
        guard CVPixelBufferCreate(nil, width, height, format, attrs as CFDictionary, &buffer) == kCVReturnSuccess else { return nil }
        return buffer
    }
}
#endif
