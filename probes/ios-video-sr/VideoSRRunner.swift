#if !targetEnvironment(simulator)
import AVFoundation
import CoreVideo
import CoreImage
import CoreImage.CIFilterBuiltins
import CryptoKit
import Foundation
import Darwin
import VideoToolbox

@available(iOS 26.0, *)
enum VideoSRRunner {
    // Experimental probe convention. The public SR headers do not specify a transfer function.
    private static let processingColorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
    private static let encoding: [String: Any] = [
        AVVideoCodecKey: AVVideoCodecType.hevc,
        AVVideoWidthKey: 1280,
        AVVideoHeightKey: 720,
        AVVideoColorPropertiesKey: [
            AVVideoColorPrimariesKey: AVVideoColorPrimaries_ITU_R_709_2,
            AVVideoTransferFunctionKey: AVVideoTransferFunction_ITU_R_709_2,
            AVVideoYCbCrMatrixKey: AVVideoYCbCrMatrix_ITU_R_709_2
        ],
        AVVideoCompressionPropertiesKey: [
            AVVideoAverageBitRateKey: 8_000_000,
            AVVideoExpectedSourceFrameRateKey: 24,
            AVVideoMaxKeyFrameIntervalKey: 48,
            AVVideoAllowFrameReorderingKey: false
        ]
    ]
    private enum ProcessingMode {
        case superResolution(VTSuperResolutionScalerConfiguration)
        case bicubic

        var backendId: String {
            switch self {
            case .superResolution: return "apple.videotoolbox.sr"
            case .bicubic: return "apple.coreimage.bicubic"
            }
        }
    }

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
        try await run(inputURL: inputURL, outputURL: outputURL, mode: .superResolution(configuration))
    }

    static func runBicubic(inputURL: URL, outputURL: URL) async throws -> [String: Any] {
        try await run(inputURL: inputURL, outputURL: outputURL, mode: .bicubic)
    }

    private static func run(inputURL: URL, outputURL: URL, mode: ProcessingMode) async throws -> [String: Any] {
        let inputFileSha256 = SHA256.hash(data: try Data(contentsOf: inputURL)).map { String(format: "%02x", $0) }.joined()
        var sourceAttributes: [String: Any] = [:]
        var destinationAttributes: [String: Any] = [:]
        if case .superResolution(let configuration) = mode {
            sourceAttributes = configuration.sourcePixelBufferAttributes
            destinationAttributes = configuration.destinationPixelBufferAttributes
        }
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
        let writerInput = AVAssetWriterInput(mediaType: .video, outputSettings: encoding)
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: writerInput, sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferIOSurfacePropertiesKey as String: [:]
        ])
        guard writer.canAdd(writerInput) else { throw RunnerError.writerFailed("cannot add video input") }
        writer.add(writerInput)
        guard reader.startReading() else { throw RunnerError.readerFailed(reader.error?.localizedDescription ?? "unknown") }

        let processor: VTFrameProcessor? = {
            if case .superResolution = mode { return VTFrameProcessor() }
            return nil
        }()
        let ciContext = CIContext(options: [.useSoftwareRenderer: false, .workingColorSpace: processingColorSpace])
        var startTime: CMTime?
        var previousSource: VTFrameProcessorFrame?
        var previousOutput: VTFrameProcessorFrame?
        var frameCount = 0
        var timestamps: [CMTime] = []
        var durations: [Double] = []
        var firstFrameColor: [String: Any] = [:]
        let baselineResidentBytes = try residentMemoryBytes()
        var peakResidentBytes = baselineResidentBytes
        guard let sample = readerOutput.copyNextSampleBuffer(), let firstBuffer = CMSampleBufferGetImageBuffer(sample) else {
            throw RunnerError.readerFailed("empty video")
        }
        let firstPTS = CMSampleBufferGetPresentationTimeStamp(sample)
        startTime = firstPTS
        guard let firstSource = try makeSourceFrame(from: firstBuffer, pts: firstPTS, context: ciContext, requirements: sourceAttributes) else { throw RunnerError.invalidFrame }
        firstFrameColor["decoded"] = bufferDescription(firstBuffer)
        firstFrameColor["processorSource"] = bufferDescription(firstSource.buffer)
        guard writer.startWriting() else { throw RunnerError.writerFailed(writer.error?.localizedDescription ?? "unknown") }
        writer.startSession(atSourceTime: firstPTS)
        if case .superResolution(let configuration) = mode {
            try processor?.startSession(configuration: configuration)
        }
        defer { processor?.endSession() }

        var current = firstSource
        while true {
            try Task.checkCancellation()
            let sourceFrame = current
            guard let destinationBuffer = makeDestinationBuffer(requirements: destinationAttributes) else { throw RunnerError.invalidFrame }
            let started = ProcessInfo.processInfo.systemUptime
            var destinationFrame: VTFrameProcessorFrame?
            switch mode {
            case .superResolution:
                guard let frame = VTFrameProcessorFrame(buffer: destinationBuffer, presentationTimeStamp: sourceFrame.presentationTimeStamp),
                      let parameters = VTSuperResolutionScalerParameters(sourceFrame: sourceFrame, previousFrame: previousSource, previousOutputFrame: previousOutput, opticalFlow: nil, submissionMode: previousSource == nil ? .random : .sequential, destinationFrame: frame),
                      let processor else { throw RunnerError.invalidFrame }
                destinationFrame = frame
                do { try await processor.process(parameters: parameters) }
                catch {
                    try Task.checkCancellation()
                    throw RunnerError.processingFailed(String(describing: error))
                }
            case .bicubic:
                guard let image = bicubicImage(from: sourceFrame.buffer) else { throw RunnerError.processingFailed("bicubic filter produced no output") }
                render(image, to: destinationBuffer, context: ciContext)
            }
            peakResidentBytes = max(peakResidentBytes, try residentMemoryBytes())
            durations.append(ProcessInfo.processInfo.systemUptime - started)
            guard let encodedBuffer = makeBuffer(width: 1280, height: 720, format: kCVPixelFormatType_32BGRA) else { throw RunnerError.invalidFrame }
            render(CIImage(cvPixelBuffer: destinationBuffer), to: encodedBuffer, context: ciContext)
            if frameCount == 0 {
                firstFrameColor["processorDestination"] = bufferDescription(destinationBuffer)
                firstFrameColor["encoderSource"] = bufferDescription(encodedBuffer)
            }
            while !writerInput.isReadyForMoreMediaData { try await Task.sleep(for: .milliseconds(2)) }
            guard adaptor.append(encodedBuffer, withPresentationTime: sourceFrame.presentationTimeStamp) else { throw RunnerError.writerFailed(writer.error?.localizedDescription ?? "append failed") }
            peakResidentBytes = max(peakResidentBytes, try residentMemoryBytes())
            timestamps.append(sourceFrame.presentationTimeStamp)
            frameCount += 1
            previousSource = sourceFrame
            previousOutput = destinationFrame
            guard let nextSample = readerOutput.copyNextSampleBuffer() else { break }
            guard let nextBuffer = CMSampleBufferGetImageBuffer(nextSample), let nextFrame = try makeSourceFrame(from: nextBuffer, pts: CMSampleBufferGetPresentationTimeStamp(nextSample), context: ciContext, requirements: sourceAttributes) else { throw RunnerError.invalidFrame }
            current = nextFrame
        }
        guard reader.status == .completed else { throw RunnerError.readerFailed(reader.error?.localizedDescription ?? "incomplete") }
        writerInput.markAsFinished()
        await writer.finishWriting()
        try Task.checkCancellation()
        guard writer.status == .completed else { throw RunnerError.writerFailed(writer.error?.localizedDescription ?? "finish failed") }
        let monotonic = zip(timestamps, timestamps.dropFirst()).allSatisfy { $0.1 > $0.0 }
        guard frameCount == 48, monotonic else { throw RunnerError.processingFailed("frame count or timestamps invalid") }
        let sorted = durations.sorted()
        let endingResidentBytes = try residentMemoryBytes()
        peakResidentBytes = max(peakResidentBytes, endingResidentBytes)
        completed = true
        return [
            "schemaVersion": 1, "validationStatus": "device", "backendId": mode.backendId, "state": "success",
            "inputFileSha256": inputFileSha256,
            "inputFrameCount": frameCount, "outputFrameCount": frameCount, "outputWidth": 1280, "outputHeight": 720,
            "timestampsMonotonic": monotonic,
            "p50Seconds": sorted.isEmpty ? NSNull() : sorted[sorted.count / 2],
            "p95Seconds": sorted.isEmpty ? NSNull() : sorted[min(sorted.count - 1, Int(Double(sorted.count) * 0.95))],
            "residentMemoryBaselineBytes": baselineResidentBytes, "residentMemoryPeakBytes": peakResidentBytes,
            "residentMemoryEndingBytes": endingResidentBytes, "residentMemoryPeakDeltaBytes": max(0, peakResidentBytes - baselineResidentBytes),
            "memoryMeasurement": "process resident_size sampled after each frame processing and encoding",
            "output": outputURL.lastPathComponent, "startTime": startTime.map { CMTimeGetSeconds($0) } ?? NSNull(),
            "firstFrameColor": firstFrameColor, "processingColorSpace": "sRGB", "encoding": encoding,
            "colorContract": "probe-sRGB-v1; SR model transfer function unverified",
            "bicubicParameters": ["B": 0.0, "C": 0.75, "scale": 4.0]
        ]
    }

    private static func makeSourceFrame(from buffer: CVPixelBuffer, pts: CMTime, context: CIContext, requirements: [String: Any]) throws -> VTFrameProcessorFrame? {
        let colorKeys = [kCVImageBufferColorPrimariesKey, kCVImageBufferTransferFunctionKey, kCVImageBufferYCbCrMatrixKey]
        guard CVPixelBufferGetWidth(buffer) == 320, CVPixelBufferGetHeight(buffer) == 180,
              colorKeys.allSatisfy({ (CVBufferCopyAttachment(buffer, $0, nil) as? String) == "ITU_R_709_2" }) else {
            throw RunnerError.processingFailed("probe requires tagged BT.709 SDR 320x180 input")
        }
        guard let converted = makeBuffer(width: 320, height: 180, format: kCVPixelFormatType_64RGBAHalf, requirements: requirements) else { return nil }
        render(CIImage(cvPixelBuffer: buffer), to: converted, context: context)
        return VTFrameProcessorFrame(buffer: converted, presentationTimeStamp: pts)
    }

    private static func makeDestinationBuffer(requirements: [String: Any]) -> CVPixelBuffer? {
        return makeBuffer(width: 1280, height: 720, format: kCVPixelFormatType_64RGBAHalf, requirements: requirements)
    }

    private static func bicubicImage(from buffer: CVPixelBuffer) -> CIImage? {
        let filter = CIFilter.bicubicScaleTransform()
        filter.inputImage = CIImage(cvPixelBuffer: buffer)
        filter.scale = 4
        filter.aspectRatio = 1
        filter.parameterB = 0
        filter.parameterC = 0.75
        return filter.outputImage
    }

    private static func render(_ image: CIImage, to buffer: CVPixelBuffer, context: CIContext) {
        context.render(image, to: buffer, bounds: CGRect(x: 0, y: 0, width: CVPixelBufferGetWidth(buffer), height: CVPixelBufferGetHeight(buffer)), colorSpace: processingColorSpace)
    }

    private static func bufferDescription(_ buffer: CVPixelBuffer) -> [String: Any] {
        var result: [String: Any] = [
            "pixelFormat": CVPixelBufferGetPixelFormatType(buffer),
            "width": CVPixelBufferGetWidth(buffer),
            "height": CVPixelBufferGetHeight(buffer)
        ]
        for key in [kCVImageBufferColorPrimariesKey, kCVImageBufferTransferFunctionKey, kCVImageBufferYCbCrMatrixKey] {
            result[key as String] = CVBufferCopyAttachment(buffer, key, nil).map { String(describing: $0) } ?? "unspecified"
        }
        result["bufferColorSpace"] = CVImageBufferGetColorSpace(buffer).map { $0.takeUnretainedValue().name.map { $0 as String } ?? "unnamed" } ?? "unspecified"
        result["ciImageColorSpace"] = CIImage(cvPixelBuffer: buffer).colorSpace.map { $0.name.map { $0 as String } ?? "unnamed" } ?? "unspecified"
        return result
    }

    private static func makeBuffer(width: Int, height: Int, format: OSType, requirements: [String: Any] = [:]) -> CVPixelBuffer? {
        let attrs: [String: Any] = [kCVPixelBufferIOSurfacePropertiesKey as String: [:], kCVPixelBufferWidthKey as String: width, kCVPixelBufferHeightKey as String: height, kCVPixelBufferPixelFormatTypeKey as String: format]
        var resolvedAttributes: CFDictionary?
        guard CVPixelBufferCreateResolvedAttributesDictionary(nil, [attrs, requirements] as CFArray, &resolvedAttributes) == kCVReturnSuccess else { return nil }
        var buffer: CVPixelBuffer?
        guard CVPixelBufferCreate(nil, width, height, format, resolvedAttributes, &buffer) == kCVReturnSuccess else { return nil }
        if let buffer {
            CVBufferSetAttachment(buffer, kCVImageBufferCGColorSpaceKey, processingColorSpace, .shouldPropagate)
            CVBufferSetAttachment(buffer, kCVImageBufferColorPrimariesKey, kCVImageBufferColorPrimaries_ITU_R_709_2, .shouldPropagate)
            CVBufferSetAttachment(buffer, kCVImageBufferTransferFunctionKey, kCVImageBufferTransferFunction_sRGB, .shouldPropagate)
            CVBufferSetAttachment(buffer, kCVImageBufferYCbCrMatrixKey, kCVImageBufferYCbCrMatrix_ITU_R_709_2, .shouldPropagate)
        }
        return buffer
    }

    private static func residentMemoryBytes() throws -> Int64 {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size / MemoryLayout<natural_t>.size)
        let result = withUnsafeMutablePointer(to: &info) { pointer in
            pointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), $0, &count)
            }
        }
        guard result == KERN_SUCCESS else { throw RunnerError.processingFailed("resident memory sampling failed: \(result)") }
        return Int64(info.resident_size)
    }

}
#endif
