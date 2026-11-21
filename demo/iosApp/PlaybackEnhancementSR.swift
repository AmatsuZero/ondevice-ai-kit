import QuartzCore
import CoreVideo
import CoreMedia
import Foundation
import Metal

/// Prepare off the render thread using an optional library containing cunny0/1/2.
/// The owner selects known <=30 fps streams. No model ships in the SDK by default.
private final class MetalLumaEngine {
    let width, height: Int
    let device: MTLDevice
    private let queue: MTLCommandQueue
    private let pipelines: [MTLComputePipelineState]
    private let cache: CVMetalTextureCache
    let backend: UHRealtimeSRBackend
    private let intermediates: (MTLTexture,MTLTexture)?
    private let worker = DispatchQueue(label: "UHPlayer.SR")
    // Bound output surfaces even if the render queue holds old frames for a long time.
    private let liveOutputs = NSHashTable<AnyObject>.weakObjects()
    private let slot = DispatchSemaphore(value: 1)

    init?(library: MTLLibrary,width:Int,height:Int,backend: UHRealtimeSRBackend = .cunny) {
        guard backend == .cunny || backend == .spatial else { return nil }
        self.backend = backend
        self.width = width; self.height = height
        let device = library.device
        let names = backend == .spatial ? ["spatial2x"] : ["cunny0", "cunny1", "cunny2"]
        let pipelines = names.compactMap { name in
            library.makeFunction(name: name).flatMap { try? device.makeComputePipelineState(function: $0) }
        }
        guard pipelines.count == names.count, pipelines.enumerated().allSatisfy({ $0.element.maxTotalThreadsPerThreadgroup >= ($0.offset == 1 ? 256 : 64) }),
              let queue = device.makeCommandQueue() else { return nil }
        let d = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba16Float,
                    width: width*2, height: height, mipmapped: false)
        d.storageMode = .private
        d.usage = [.shaderRead, .shaderWrite]
        var cache: CVMetalTextureCache?
        if backend == .cunny {
            guard let a = device.makeTexture(descriptor:d), let b = device.makeTexture(descriptor:d) else { return nil }
            intermediates = (a,b)
        } else { intermediates = nil }
        guard CVMetalTextureCacheCreate(nil, nil, device, nil, &cache) == kCVReturnSuccess,
              let cache else { return nil }
        self.device = device; self.queue = queue; self.pipelines = pipelines
        self.cache = cache
    }

    static func accepts(_ pb: CVPixelBuffer,width:Int,height:Int) -> Bool {
        guard CVPixelBufferGetWidth(pb) == width, CVPixelBufferGetHeight(pb) == height,
              CVPixelBufferGetPlaneCount(pb) == 2,
              CVPixelBufferGetPixelFormatType(pb) == kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
        else { return false }
        return [(kCVImageBufferColorPrimariesKey, kCVImageBufferColorPrimaries_ITU_R_709_2),
                (kCVImageBufferYCbCrMatrixKey, kCVImageBufferYCbCrMatrix_ITU_R_709_2),
                (kCVImageBufferTransferFunctionKey, kCVImageBufferTransferFunction_ITU_R_709_2)]
            .allSatisfy { key, value in (CVBufferCopyAttachment(pb,key,nil) as? String) == value as String }
    }

    /// No pending work queue: if occupied (including across views), use the original.
    func start(_ pb: CVPixelBuffer, deadline: Double, collectTiming: Bool, completion: @escaping (UHSRLuma?) -> Void) -> Bool {
        guard slot.wait(timeout: .now()) == .success else { return false }
        worker.async { [self] in
            let texture = CACurrentMediaTime() < deadline ? compute(pb, collectTiming: collectTiming) : nil
            slot.signal()
            DispatchQueue.main.async { completion(texture) }
        }
        return true
    }

    private func compute(_ pb: CVPixelBuffer, collectTiming: Bool) -> UHSRLuma? {
        var input: CVMetalTexture?
        guard CVMetalTextureCacheCreateTextureFromImage(nil,cache,pb,nil,.r8Unorm,width,height,0,&input) == kCVReturnSuccess,
              let input, let y = CVMetalTextureGetTexture(input), let cb = queue.makeCommandBuffer() else { return nil }
        let d = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .r8Unorm,width: width*2,height: height*2,mipmapped: false)
        d.storageMode = .private; d.usage = [.shaderRead,.shaderWrite]
        guard liveOutputs.allObjects.count < 2, let output = device.makeTexture(descriptor: d) else { return nil }
        liveOutputs.add(output)
        let stages = intermediates.map { a,b in [[y,a],[a,b],[b,y,output]] } ?? [[y,output]]
        for (stage, bindings) in stages.enumerated() {
            guard let encoder = cb.makeComputeCommandEncoder() else { return nil }
            encoder.setComputePipelineState(pipelines[stage])
            for (i, texture) in bindings.enumerated() { encoder.setTexture(texture,index:i) }
            let tile = stage == 1 ? 16 : 8
            encoder.dispatchThreadgroups(MTLSize(width:(width+tile-1)/tile,height:(height+tile-1)/tile,depth:1),
                threadsPerThreadgroup:MTLSize(width:tile,height:tile,depth:1))
            encoder.endEncoding()
        }
        // Only the serial compute worker waits; render submissions use an independent queue.
        cb.commit(); cb.waitUntilCompleted()
        withExtendedLifetime((pb,input)) {}
        guard cb.status == .completed else { return nil }
        var result = UHSRLuma(texture: output, backend: backend)
        if collectTiming {
            result.computeTiming = (cb.gpuStartTime, cb.gpuEndTime, CACurrentMediaTime())
        }
        return result
    }
}

public enum UHRealtimeSRBackend: String { case original, system, cunny, spatial }

struct UHSRLuma {
    let texture: MTLTexture
    let backend: UHRealtimeSRBackend
    var computeTiming: (start: Double, end: Double, workerReady: Double)? = nil
    var backing: (CVPixelBuffer,CVMetalTexture)? = nil
}

/// Prepare once per stream off the UI thread. System SR does not require a bundled model.
public final class UHPlayerSuperResolution {
    let device: MTLDevice
    private let width, height:Int
    private let metal: MetalLumaEngine?
    private var systemWork: ((CVPixelBuffer,CMTime,@escaping (UHSRLuma?) -> Void) -> Void)?
    private let queue = DispatchQueue(label:"UHPlayer.SystemSR")
    private let slot = DispatchSemaphore(value:1)
    private let lock = NSLock()
    private var systemPreferred: Bool
    public var preferredBackend: UHRealtimeSRBackend {
        lock.lock(); defer { lock.unlock() }
        return systemPreferred ? .system : (metal?.backend ?? .original)
    }
    private init(device:MTLDevice, metal:MetalLumaEngine?, system:Bool,width:Int,height:Int) {
        self.width = width; self.height = height
        self.device = device; self.metal = metal; systemPreferred = system
    }
    /// Explicit compatibility backend; useful for A/B comparison on modern devices.
    public convenience init?(library:MTLLibrary, width:Int = 1920, height:Int = 1080, backend: UHRealtimeSRBackend = .cunny) {
        guard (width == 1920 && height == 1080) || (width == 960 && height == 540),
              let metal = MetalLumaEngine(library:library,width:width,height:height,backend:backend) else { return nil }
        self.init(device:library.device,metal:metal,system:false,width:width,height:height)
    }
    public static func automatic(device:MTLDevice, compatibilityLibrary:MTLLibrary?, width:Int = 1920, height:Int = 1080) -> UHPlayerSuperResolution? {
        guard (width == 1920 && height == 1080) || (width == 960 && height == 540) else { return nil }
        let metal = compatibilityLibrary.flatMap { $0.device === device ? MetalLumaEngine(library:$0,width:width,height:height) : nil }
        #if !targetEnvironment(simulator)
        if #available(iOS 26, macOS 26, *), let system = UHSystemSR.prepare(device,width:width,height:height) {
            let engine = UHPlayerSuperResolution(device:device,metal:metal,system:true,width:width,height:height)
            engine.systemWork = system.frame
            return engine
        }
        #endif
        return metal.map { UHPlayerSuperResolution(device:device,metal:$0,system:false,width:width,height:height) }
    }
    func accepts(_ pb: CVPixelBuffer) -> Bool { MetalLumaEngine.accepts(pb,width:width,height:height) }
    func startFrame(_ pb: CVPixelBuffer, pts: CMTime, deadline: Double, collectTiming: Bool = false, completion:@escaping (UHSRLuma?)->Void) -> Bool {
        if preferredBackend == .system, let process = systemWork {
            guard slot.wait(timeout:.now()) == .success else { return false }
            queue.async { [self] in
                guard CACurrentMediaTime() < deadline else {
                    slot.signal(); DispatchQueue.main.async { completion(nil) }; return
                }
                process(pb,pts) { [self] result in
                    if result == nil && metal != nil {
                        lock.lock(); systemPreferred = false; lock.unlock()
                    }
                    slot.signal()
                    DispatchQueue.main.async { completion(result) }
                }
            }
            return true
        }
        return metal?.start(pb,deadline:deadline,collectTiming:collectTiming,completion:completion) ?? false
    }
}
