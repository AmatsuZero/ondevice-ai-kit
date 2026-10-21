#if os(iOS) && !targetEnvironment(simulator)
@preconcurrency import AVFoundation
import CoreImage
import CoreVideo
import Foundation
import ODAIKitCore
import ODAIKitVideo
import VideoToolbox

/// Deliberately bounded experimental profile; the model's preferred transfer function is unverified.
@available(iOS 26.0, *)
struct VideoToolboxSREngine: AppleVideoProcessingEngine {
    private static let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
    private static let id = "apple.videotoolbox.sr"

    private func configuration() -> VTSuperResolutionScalerConfiguration? {
        guard VTSuperResolutionScalerConfiguration.isSupported,
              VTSuperResolutionScalerConfiguration.supportedScaleFactors.contains(4) else { return nil }
        return VTSuperResolutionScalerConfiguration(frameWidth: 320, frameHeight: 180, scaleFactor: 4,
                                                   inputType: .video, usePrecomputedFlow: false,
                                                   qualityPrioritization: .normal, revision: .revision1)
    }

    func availability() async -> CapabilityAvailability {
        guard let configuration = configuration() else {
            return .init(status: .unavailable, reason: .unsupportedDevice, backendId: Self.id, minOS: "iOS26", offline: true)
        }
        guard configuration.configurationModelStatus == .ready else {
            return .init(status: .requiresAssets, reason: .missingAssets, backendId: Self.id, minOS: "iOS26", offline: true)
        }
        return .init(status: .available, backendId: Self.id, minOS: "iOS26", offline: true)
    }

    func process(request: VideoProcessingRequest, output: URL,
                 progress: (@Sendable (VideoProcessingProgress) -> Void)?) async throws -> VideoOutputInfo {
        do { return try await processVideo(request: request, output: output, progress: progress) }
        catch is CancellationError { throw CancellationError() }
        catch let error as AppleVideoError { throw error }
        catch let error as VideoClientError { throw error }
        catch { throw AppleVideoError.processingFailed }
    }

    private func processVideo(request: VideoProcessingRequest, output: URL,
                              progress: (@Sendable (VideoProcessingProgress) -> Void)?) async throws -> VideoOutputInfo {
        try Task.checkCancellation()
        try AppleVideoProfile.validateFile(request.inputFile)
        guard request.audioPolicy == .discard else { throw VideoClientError.unsupportedAudioPolicy(request.audioPolicy) }
        guard request.scaleFactor == 4 else { throw VideoClientError.unsupportedScaleFactor(request.scaleFactor) }
        guard let configuration = configuration() else { throw VideoClientError.backendUnavailable(Self.id, .unsupportedDevice) }
        guard configuration.configurationModelStatus == .ready else { throw VideoClientError.backendUnavailable(Self.id, .missingAssets) }

        let asset = AVURLAsset(url: request.inputFile)
        let tracks = try await asset.loadTracks(withMediaType: .video)
        guard tracks.count == 1, let track = tracks.first else { throw AppleVideoError.unsupportedInput("one video track required") }
        let (size, transform, rate) = try await (track.load(.naturalSize), track.load(.preferredTransform), track.load(.nominalFrameRate))
        guard size == CGSize(width: 320, height: 180), transform == .identity, abs(rate - 24) < 0.001 else {
            throw AppleVideoError.unsupportedInput("unrotated 320x180 24fps track required")
        }
        let duration = try await track.load(.timeRange).duration
        guard duration.isNumeric, duration.seconds > 0, duration.seconds <= 2.001 else {
            throw AppleVideoError.unsupportedInput("video duration up to two seconds required")
        }
        try Task.checkCancellation()
        let reader = try AVAssetReader(asset: asset)
        let source = AVAssetReaderTrackOutput(track: track, outputSettings: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferIOSurfacePropertiesKey as String: [:]
        ])
        source.alwaysCopiesSampleData = false
        guard reader.canAdd(source) else { throw AppleVideoError.readerFailed }
        reader.add(source)

        let writer = try AVAssetWriter(outputURL: output, fileType: .mov)
        let processor = VTFrameProcessor()
        var sessionStarted = false
        var finished = false
        defer {
            if sessionStarted { processor.endSession() }
            if !finished { reader.cancelReading(); writer.cancelWriting() }
        }
        let sink = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.hevc, AVVideoWidthKey: 1280, AVVideoHeightKey: 720,
            AVVideoColorPropertiesKey: [AVVideoColorPrimariesKey: AVVideoColorPrimaries_ITU_R_709_2,
                                       AVVideoTransferFunctionKey: AVVideoTransferFunction_ITU_R_709_2,
                                       AVVideoYCbCrMatrixKey: AVVideoYCbCrMatrix_ITU_R_709_2],
            AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 8_000_000,
                                             AVVideoExpectedSourceFrameRateKey: 24,
                                             AVVideoMaxKeyFrameIntervalKey: 48,
                                             AVVideoAllowFrameReorderingKey: false]
        ])
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: sink, sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferIOSurfacePropertiesKey as String: [:]
        ])
        guard writer.canAdd(sink) else { throw AppleVideoError.writerFailed }
        writer.add(sink)
        guard reader.startReading() else { throw AppleVideoError.readerFailed }
        guard writer.startWriting() else { throw AppleVideoError.writerFailed }
        writer.startSession(atSourceTime: .zero)
        try processor.startSession(configuration: configuration)
        sessionStarted = true
        let context = CIContext(options: [.useSoftwareRenderer: false, .workingColorSpace: Self.colorSpace])
        var previousSource: VTFrameProcessorFrame?
        var previousOutput: VTFrameProcessorFrame?
        var count = 0
        while let sample = source.copyNextSampleBuffer() {
            try Task.checkCancellation()
            guard let decoded = CMSampleBufferGetImageBuffer(sample) else { throw AppleVideoError.invalidFrame }
            let timestamp = CMSampleBufferGetPresentationTimeStamp(sample)
            let tags = [kCVImageBufferColorPrimariesKey, kCVImageBufferTransferFunctionKey, kCVImageBufferYCbCrMatrixKey]
                .map { CVBufferCopyAttachment(decoded, $0, nil) as? String ?? "" }
            try AppleVideoProfile.validate(frameIndex: count, timestampSeconds: timestamp.seconds,
                                           width: CVPixelBufferGetWidth(decoded), height: CVPixelBufferGetHeight(decoded), colorTags: tags)
            let sourceBuffer = try Self.buffer(width: 320, height: 180, format: kCVPixelFormatType_64RGBAHalf,
                                               requirements: configuration.sourcePixelBufferAttributes)
            Self.render(CIImage(cvPixelBuffer: decoded), to: sourceBuffer, context: context)
            let destinationBuffer = try Self.buffer(width: 1280, height: 720, format: kCVPixelFormatType_64RGBAHalf,
                                                    requirements: configuration.destinationPixelBufferAttributes)
            guard let sourceFrame = VTFrameProcessorFrame(buffer: sourceBuffer, presentationTimeStamp: timestamp),
                  let destinationFrame = VTFrameProcessorFrame(buffer: destinationBuffer, presentationTimeStamp: timestamp),
                  let parameters = VTSuperResolutionScalerParameters(sourceFrame: sourceFrame, previousFrame: previousSource,
                     previousOutputFrame: previousOutput, opticalFlow: nil,
                     submissionMode: previousSource == nil ? .random : .sequential, destinationFrame: destinationFrame) else {
                throw AppleVideoError.invalidFrame
            }
            // Await the real completion before modifying/releasing referenced buffers.
            try await processor.process(parameters: parameters)
            try Task.checkCancellation()
            let encoded = try Self.buffer(width: 1280, height: 720, format: kCVPixelFormatType_32BGRA)
            Self.render(CIImage(cvPixelBuffer: destinationBuffer), to: encoded, context: context)
            let deadline = ProcessInfo.processInfo.systemUptime + 5
            while !sink.isReadyForMoreMediaData {
                guard writer.status == .writing, ProcessInfo.processInfo.systemUptime < deadline else { throw AppleVideoError.writerFailed }
                try await Task.sleep(nanoseconds: 2_000_000)
            }
            guard adaptor.append(encoded, withPresentationTime: timestamp) else { throw AppleVideoError.writerFailed }
            previousSource = sourceFrame; previousOutput = destinationFrame
            count += 1
            progress?(try .init(completedFrames: count))
        }
        try Task.checkCancellation()
        guard reader.status == .completed, count > 0 else { throw AppleVideoError.readerFailed }
        processor.endSession(); sessionStarted = false
        writer.endSession(atSourceTime: CMTime(value: Int64(count), timescale: 24))
        sink.markAsFinished()
        await writer.finishWriting()
        try Task.checkCancellation()
        guard writer.status == .completed else { throw AppleVideoError.writerFailed }
        let info = try VideoOutputInfo(width: 1280, height: 720, frameCount: count, durationSeconds: Double(count) / 24, audioTrackCount: 0)
        finished = true
        return info
    }

    private static func buffer(width: Int, height: Int, format: OSType, requirements: [String: Any] = [:]) throws -> CVPixelBuffer {
        let attributes: [String: Any] = [kCVPixelBufferIOSurfacePropertiesKey as String: [:],
            kCVPixelBufferWidthKey as String: width, kCVPixelBufferHeightKey as String: height,
            kCVPixelBufferPixelFormatTypeKey as String: format]
        var resolved: CFDictionary?
        guard CVPixelBufferCreateResolvedAttributesDictionary(nil, [attributes, requirements] as CFArray, &resolved) == kCVReturnSuccess else {
            throw AppleVideoError.invalidFrame
        }
        var buffer: CVPixelBuffer?
        guard CVPixelBufferCreate(nil, width, height, format, resolved, &buffer) == kCVReturnSuccess, let buffer else { throw AppleVideoError.invalidFrame }
        CVBufferSetAttachment(buffer, kCVImageBufferCGColorSpaceKey, colorSpace, .shouldPropagate)
        CVBufferSetAttachment(buffer, kCVImageBufferColorPrimariesKey, kCVImageBufferColorPrimaries_ITU_R_709_2, .shouldPropagate)
        CVBufferSetAttachment(buffer, kCVImageBufferTransferFunctionKey, kCVImageBufferTransferFunction_sRGB, .shouldPropagate)
        CVBufferSetAttachment(buffer, kCVImageBufferYCbCrMatrixKey, kCVImageBufferYCbCrMatrix_ITU_R_709_2, .shouldPropagate)
        return buffer
    }

    private static func render(_ image: CIImage, to buffer: CVPixelBuffer, context: CIContext) {
        context.render(image, to: buffer, bounds: CGRect(x: 0, y: 0, width: CVPixelBufferGetWidth(buffer), height: CVPixelBufferGetHeight(buffer)), colorSpace: colorSpace)
    }
}
#endif
