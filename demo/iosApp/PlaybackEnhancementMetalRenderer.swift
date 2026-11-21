import AVFoundation
import MetalKit
import ODAIKitDemo

@MainActor
final class PlaybackEnhancementMetalRenderer: NSObject, MTKViewDelegate {
  let view: MTKView
  private let queue: MTLCommandQueue
  private let pipeline: MTLRenderPipelineState
  private let cache: CVMetalTextureCache
  private var engines: [UHRealtimeSRBackend: UHPlayerSuperResolution] = [:]
  private var epoch: UInt64 = 0
  private var preparationEpoch: UInt64 = 0
  private var preparedSize: String?
  private var preparingSize: String?
  private var choice = UHPlayerFrameChoice()
  private var lastFrame: (CVPixelBuffer, CMTime)?
  private var lastPresented = 0.0
  private var outstanding = 0
  private var pendingDraw: (CVPixelBuffer, UHSRLuma?, Bool, UInt64)?
  private(set) var selected: RealtimeSRMode = .original
  private(set) var presented: RealtimeSRMode?
  private(set) var fallback = false
  private(set) var status = "选择 BT.709 SDR 视频以体验 2× 超分"
  var supported: [RealtimeSRMode] {
    let modes: [(RealtimeSRMode, UHRealtimeSRBackend)] = [
      (.spatial, .spatial), (.cunny, .cunny), (.system, .system),
    ]
    return [.original, .auto_] + modes.compactMap { engines[$0.1] == nil ? nil : $0.0 }
  }

  init?(device: MTLDevice? = MTLCreateSystemDefaultDevice()) {
    guard let device, let library = device.makeDefaultLibrary(),
      let queue = device.makeCommandQueue()
    else { return nil }
    let d = MTLRenderPipelineDescriptor()
    d.vertexFunction = library.makeFunction(name: "playbackVertex")
    d.fragmentFunction = library.makeFunction(name: "playbackFragment")
    d.colorAttachments[0].pixelFormat = .bgra8Unorm
    guard let pipeline = try? device.makeRenderPipelineState(descriptor: d) else { return nil }
    var cache: CVMetalTextureCache?
    guard CVMetalTextureCacheCreate(nil, nil, device, nil, &cache) == kCVReturnSuccess, let cache
    else { return nil }
    self.queue = queue
    self.pipeline = pipeline
    self.cache = cache
    view = MTKView(frame: .zero, device: device)
    super.init()
    view.colorPixelFormat = .bgra8Unorm
    view.isPaused = true
    view.enableSetNeedsDisplay = false
    view.framebufferOnly = true
    view.clearColor = MTLClearColorMake(0, 0, 0, 1)
    view.delegate = self
    view.accessibilityIdentifier = "playback-preview"
    view.isAccessibilityElement = true
  }
  func select(_ mode: RealtimeSRMode) -> Bool {
    guard supported.contains(mode) else { return false }
    selected = mode
    invalidate()
    if let f = lastFrame { render(f.0, pts: f.1) }
    return true
  }
  func clear() {
    invalidate()
    lastFrame = nil
    selected = .original
    engines.removeAll()
    preparationEpoch &+= 1
    preparedSize = nil
    preparingSize = nil
    view.isHidden = true
  }
  func invalidate() {
    epoch &+= 1
    choice.reset(deadline: 0)
    presented = nil
    fallback = false
  }
  private func prepare(_ width: Int, _ height: Int) {
    guard preparedSize != "\(width)x\(height)", preparingSize != "\(width)x\(height)",
      let device = view.device
    else { return }
    preparingSize = "\(width)x\(height)"
    let token = preparationEpoch
    DispatchQueue.global(qos: .userInitiated).async { [weak self] in
      var r: [UHRealtimeSRBackend: UHPlayerSuperResolution] = [:]
      if let lib = device.makeDefaultLibrary() {
        r[.spatial] = UHPlayerSuperResolution(
          library: lib, width: width, height: height, backend: .spatial)
        r[.cunny] = UHPlayerSuperResolution(
          library: lib, width: width, height: height, backend: .cunny)
      }
      r[.system] = UHPlayerSuperResolution.automatic(
        device: device, compatibilityLibrary: nil, width: width, height: height)
      DispatchQueue.main.async {
        guard let self, self.preparationEpoch == token else { return }
        self.engines = r
        self.preparedSize = "\(width)x\(height)"
        self.preparingSize = nil
      }
    }
  }
  func render(_ buffer: CVPixelBuffer, pts: CMTime) {
    guard accepts(buffer) else {
      lastFrame = nil
      useSystemOriginal("片源不是 540p/1080p BT.709 full-range NV12", requested: selected != .original)
      return
    }
    lastFrame = (buffer, pts)
    let w = CVPixelBufferGetWidth(buffer)
    let h = CVPixelBufferGetHeight(buffer)
    prepare(w, h)
    let token = epoch
    let engine: UHPlayerSuperResolution? =
      selected == .auto_
      ? (engines[.system] ?? engines[.spatial] ?? engines[.cunny])
      : selected.backend.flatMap { engines[$0] }
    guard selected != .original, let engine, engine.accepts(buffer) else {
      draw(buffer, luma: nil, requested: selected != .original, epoch: token)
      return
    }
    let deadline = CACurrentMediaTime() + 0.018
    choice.reset(deadline: deadline)
    choice.attempt()
    choice.didSubmit()
    let generation = choice.generation
    guard
      engine.startFrame(
        buffer, pts: pts, deadline: deadline,
        completion: { [weak self] luma in
          guard let self, self.epoch == token,
            let use = self.choice.resolve(
              generation, now: CACurrentMediaTime(), hasTexture: luma != nil)
          else { return }
          self.draw(buffer, luma: use ? luma : nil, requested: true, epoch: token)
        })
    else {
      _ = choice.resolve(generation, now: CACurrentMediaTime(), hasTexture: false)
      draw(buffer, luma: nil, requested: true, epoch: token)
      return
    }
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.018) { [weak self] in
      guard let self, self.epoch == token,
        self.choice.resolve(generation, now: CACurrentMediaTime(), hasTexture: false) != nil
      else { return }
      self.draw(buffer, luma: nil, requested: true, epoch: token)
    }
  }
  private func accepts(_ pb: CVPixelBuffer) -> Bool {
    let w = CVPixelBufferGetWidth(pb)
    let h = CVPixelBufferGetHeight(pb)
    guard (w == 960 && h == 540) || (w == 1920 && h == 1080), CVPixelBufferGetPlaneCount(pb) == 2,
      CVPixelBufferGetPixelFormatType(pb) == kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
    else { return false }
    return [
      (kCVImageBufferColorPrimariesKey, kCVImageBufferColorPrimaries_ITU_R_709_2),
      (kCVImageBufferYCbCrMatrixKey, kCVImageBufferYCbCrMatrix_ITU_R_709_2),
      (kCVImageBufferTransferFunctionKey, kCVImageBufferTransferFunction_ITU_R_709_2),
    ].allSatisfy { (CVBufferCopyAttachment(pb, $0.0, nil) as? String) == $0.1 as String }
  }
  private func draw(_ pb: CVPixelBuffer, luma: UHSRLuma?, requested: Bool, epoch token: UInt64) {
    guard token == epoch else { return }
    pendingDraw = (pb, luma, requested, token)
    view.draw()
    pendingDraw = nil
  }
  func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
  func draw(in view: MTKView) {
    guard let (pb, luma, requested, token) = pendingDraw, token == epoch else { return }
    guard outstanding < 3, let drawable = view.currentDrawable,
      let pass = view.currentRenderPassDescriptor, let command = queue.makeCommandBuffer()
    else {
      useSystemOriginal("Metal 提交暂不可用", requested: requested)
      return
    }
    let w = CVPixelBufferGetWidth(pb)
    let h = CVPixelBufferGetHeight(pb)
    var y: CVMetalTexture?
    var uv: CVMetalTexture?
    guard
      CVMetalTextureCacheCreateTextureFromImage(nil, cache, pb, nil, .r8Unorm, w, h, 0, &y)
        == kCVReturnSuccess,
      CVMetalTextureCacheCreateTextureFromImage(
        nil, cache, pb, nil, .rg8Unorm, w / 2, h / 2, 1, &uv) == kCVReturnSuccess, let y, let uv,
      let yt = CVMetalTextureGetTexture(y), let uvt = CVMetalTextureGetTexture(uv),
      let enc = command.makeRenderCommandEncoder(descriptor: pass)
    else {
      useSystemOriginal("视频纹理暂不可用", requested: requested)
      return
    }
    enc.setRenderPipelineState(pipeline)
    let dw = Double(drawable.texture.width)
    let dh = Double(drawable.texture.height)
    let scale = min(dw / Double(w), dh / Double(h))
    enc.setViewport(
      MTLViewport(
        originX: (dw - Double(w) * scale) / 2,
        originY: (dh - Double(h) * scale) / 2, width: Double(w) * scale,
        height: Double(h) * scale, znear: 0, zfar: 1))
    enc.setFragmentTexture(luma?.texture ?? yt, index: 0)
    enc.setFragmentTexture(uvt, index: 1)
    enc.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4)
    enc.endEncoding()
    let backend = luma?.backend ?? .original
    view.isHidden = false
    outstanding += 1
    #if !targetEnvironment(simulator)
      drawable.addPresentedHandler { [weak self] d in
        DispatchQueue.main.async {
          guard let self, self.epoch == token, d.presentedTime > self.lastPresented else { return }
          self.lastPresented = d.presentedTime
          self.presented = backend.mode
          self.fallback = requested && backend == .original
          self.status = self.fallback ? "实际上屏：原画（超时/忙时逐帧回退）" : "实际上屏：\(backend.rawValue)"
        }
      }
    #endif
    command.addCompletedHandler { [weak self] command in
      withExtendedLifetime((pb, y, uv, luma)) {}
      DispatchQueue.main.async {
        guard let self else { return }
        self.outstanding -= 1
        guard self.epoch == token else { return }
        guard command.status == .completed else {
          self.useSystemOriginal("Metal 渲染失败", requested: requested)
          return
        }
        #if targetEnvironment(simulator)
          self.fallback = requested && backend == .original
          self.status = "模拟器渲染：\(backend.rawValue)；系统不提供上屏确认"
        #endif
      }
    }
    command.present(drawable)
    command.commit()
  }
  private func useSystemOriginal(_ reason: String, requested: Bool) {
    invalidate()
    view.isHidden = true
    fallback = requested
    status = "系统原画播放：\(reason)；无上屏确认"
  }
}
extension RealtimeSRMode {
  fileprivate var backend: UHRealtimeSRBackend? {
    switch self {
    case .system: return .system
    case .spatial: return .spatial
    case .cunny: return .cunny
    default: return nil
    }
  }
}
extension UHRealtimeSRBackend {
  fileprivate var mode: RealtimeSRMode {
    switch self {
    case .system: return .system
    case .spatial: return .spatial
    case .cunny: return .cunny
    default: return .original
    }
  }
}
