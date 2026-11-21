#if !targetEnvironment(simulator)
import CoreVideo
import VideoToolbox
import Metal
import QuartzCore

@available(iOS 26, macOS 26, *)
final class UHSystemSR {
    let width, height: Int
    let processor = VTFrameProcessor()
    let cache: CVMetalTextureCache
    private let transfer: VTPixelTransferSession
    private let inputFormat: OSType
    private let inputPool, outputPool, displayPool: CVPixelBufferPool

    static func prepare(_ device: MTLDevice, width:Int, height:Int) -> UHSystemSR? {
        typealias Config = VTLowLatencySuperResolutionScalerConfiguration
        guard Config.isSupported, Config.supportedScaleFactors(frameWidth:width, frameHeight:height).contains(2) else { return nil }
        if #available(iOS 27, macOS 27, *) {
            guard (Config.maximumDimension(forSpatialScaleFactor:2) ?? 0) >= max(width,height),
                  (Config.maximumPixelCount(forSpatialScaleFactor:2) ?? 0) >= width*height else { return nil }
        }
        let config = Config(frameWidth:width, frameHeight:height, scaleFactor:2)
        return UHSystemSR(device, config,width,height)
    }

    private init?(_ device: MTLDevice, _ c: VTLowLatencySuperResolutionScalerConfiguration,_ width:Int,_ height:Int) {
        self.width = width; self.height = height
        let full = kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
        func format(_ attrs: [String:Any]) -> OSType? {
            let field = attrs[kCVPixelBufferPixelFormatTypeKey as String]
            if let list = field as? [NSNumber] {
                let formats = list.map(\.uint32Value)
                return formats.contains(full) ? full : formats.first
            }
            return (field as? NSNumber)?.uint32Value ?? (field == nil ? full : nil)
        }
        func pool(_ attrs:[String:Any], _ width:Int, _ height:Int, _ format:OSType) -> CVPixelBufferPool? {
            var a = attrs
            a[kCVPixelBufferPixelFormatTypeKey as String] = format
            a[kCVPixelBufferWidthKey as String] = width
            a[kCVPixelBufferHeightKey as String] = height
            a[kCVPixelBufferIOSurfacePropertiesKey as String] = [:] as [String:Any]
            a[kCVPixelBufferMetalCompatibilityKey as String] = true
            var result: CVPixelBufferPool?
            guard CVPixelBufferPoolCreate(nil,nil,a as CFDictionary,&result) == kCVReturnSuccess else { return nil }
            return result
        }
        var tc: CVMetalTextureCache?, x: VTPixelTransferSession?
        guard let i = format(c.sourcePixelBufferAttributes), let o = format(c.destinationPixelBufferAttributes),
              let ip = pool(c.sourcePixelBufferAttributes,width,height,i),
              let op = pool(c.destinationPixelBufferAttributes,width*2,height*2,o),
              let dp = pool([:],width*2,height*2,full),
              CVMetalTextureCacheCreate(nil,nil,device,nil,&tc) == kCVReturnSuccess, let tc,
              VTPixelTransferSessionCreate(allocator:nil,pixelTransferSessionOut:&x) == noErr, let x else { return nil }
        inputFormat = i; inputPool = ip; outputPool = op; displayPool = dp; transfer = x; cache = tc
        do { try processor.startSession(configuration:c) } catch { return nil }
    }

    deinit { processor.endSession() }

    private func allocate(_ pool:CVPixelBufferPool) -> CVPixelBuffer? {
        var pb: CVPixelBuffer?
        guard CVPixelBufferPoolCreatePixelBufferWithAuxAttributes(nil,pool,
            [kCVPixelBufferPoolAllocationThresholdKey:3] as CFDictionary,&pb) == kCVReturnSuccess else { return nil }
        return pb
    }

    // The processor sees one spatial frame at a time. Never wait on the render thread.
    func frame(_ pb: CVPixelBuffer, pts: CMTime, completion: @escaping (UHSRLuma?) -> Void) {
        var input = pb
        if CVPixelBufferGetPixelFormatType(pb) != inputFormat {
            guard let dst = allocate(inputPool), VTPixelTransferSessionTransferImage(transfer,from:pb,to:dst) == noErr else { completion(nil); return }
            input = dst
        }
        guard let output = allocate(outputPool),
              let src = VTFrameProcessorFrame(buffer:input,presentationTimeStamp:pts),
              let dst = VTFrameProcessorFrame(buffer:output,presentationTimeStamp:pts) else { completion(nil); return }
        let params = VTLowLatencySuperResolutionScalerParameters(sourceFrame:src,destinationFrame:dst)
        CVBufferPropagateAttachments(pb,output)
        processor.process(parameters:params) { [self] _,error in
            guard error == nil else { completion(nil); return }
            var display = output
            if CVPixelBufferGetPixelFormatType(output) != kCVPixelFormatType_420YpCbCr8BiPlanarFullRange {
                guard let converted = allocate(displayPool), VTPixelTransferSessionTransferImage(transfer,from:output,to:converted) == noErr else { completion(nil); return }
                display = converted
            }
            var cv: CVMetalTexture?
            guard CVMetalTextureCacheCreateTextureFromImage(nil,cache,display,nil,.r8Unorm,width*2,height*2,0,&cv) == kCVReturnSuccess,
                  let cv, let texture = CVMetalTextureGetTexture(cv) else { completion(nil); return }
            completion(UHSRLuma(texture:texture,backend:.system, backing:(display,cv)))
        }
    }
}

#endif
