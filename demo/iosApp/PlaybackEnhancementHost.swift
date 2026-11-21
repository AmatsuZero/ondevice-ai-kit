import AVFoundation
import MetalKit
import ODAIKitDemo
import UIKit
import UniformTypeIdentifiers

/// Shared CMP controls, native AVPlayer clock/audio and a standalone Metal pixel renderer.
@MainActor
final class PlaybackEnhancementHost: NSObject, PlaybackEnhancementDemoActions, RealtimeSRDemoActions
{
  let controlsAvailable = true
  let playerDescription = "本地视频 · 540p/1080p、≤30 fps、无旋转 BT.709 SDR 可尝试 2× 超分；系统后端按设备支持开放。"
  let viewController: UIViewController
  private let controller: PlaybackEnhancementViewController
  var playerAvailable: Bool { true }
  var hasVideo: Bool { controller.hasVideo }
  var isPlaying: Bool { controller.isPlaying }
  var inputName: String? { controller.inputName }
  var playbackStatus: String { controller.playbackStatus }
  var supportedModes: [RealtimeSRMode] { controller.renderer?.supported ?? [.original] }
  var selectedMode: RealtimeSRMode { controller.renderer?.selected ?? .original }
  var presentedMode: RealtimeSRMode? { controller.renderer?.presented }
  var fallbackToOriginal: Bool { controller.renderer?.fallback ?? false }
  var presentationController: UIViewController? {
    get { controller.presentationControllerHost }
    set { controller.presentationControllerHost = newValue }
  }
  override init() {
    controller = PlaybackEnhancementViewController()
    viewController = controller
    super.init()
  }
  func openPlayer() { controller.chooseVideo() }
  func togglePlayback() { controller.togglePlayback() }
  func showOriginal() { _ = selectMode(mode: .original) }
  func showEnhancedPreview() { _ = selectMode(mode: .auto_) }
  func selectMode(mode: RealtimeSRMode) -> Bool { controller.select(mode) }
  func enter() { controller.active = true }
  func background() { controller.background() }
  func foreground() { controller.foreground() }
  func close() { controller.close() }
}

@MainActor
private final class PlaybackEnhancementViewController: UIViewController, UIDocumentPickerDelegate {
  let player = AVPlayer()
  let renderer = PlaybackEnhancementMetalRenderer()
  private let originalLayer = AVPlayerLayer()
  private var output: AVPlayerItemVideoOutput?
  private var displayLink: CADisplayLink?
  private var epoch = 0
  private var wasPlaying = false
  private var eligible = false
  private var notice = "尚未选择视频"
  private var observers: [NSObjectProtocol] = []
  private var itemObservation: NSKeyValueObservation?
  private var endObserver: NSObjectProtocol?
  private var backgrounded = false
  private var ended = false
  weak var presentationControllerHost: UIViewController?
  var active = false
  private(set) var inputName: String?
  var hasVideo: Bool { player.currentItem != nil }
  var isPlaying: Bool { player.timeControlStatus == .playing }
  var playbackStatus: String { eligible ? (renderer?.status ?? "Metal 不可用，使用系统原画播放") : notice }

  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = .black
    originalLayer.player = player
    originalLayer.videoGravity = .resizeAspect
    view.layer.addSublayer(originalLayer)
    if let metal = renderer?.view {
      metal.isHidden = true
      view.addSubview(metal)
    }
    observers.append(
      NotificationCenter.default.addObserver(
        forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main
      ) { [weak self] _ in Task { @MainActor in self?.background() } })
    observers.append(
      NotificationCenter.default.addObserver(
        forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main
      ) { [weak self] _ in Task { @MainActor in self?.foreground() } })
  }
  override func viewDidLayoutSubviews() {
    super.viewDidLayoutSubviews()
    originalLayer.frame = view.bounds
    renderer?.view.frame = view.bounds
  }
  func chooseVideo() {
    guard active else { notice = "请重新进入播放页后选择视频"; return }
    guard let presenter = presentationControllerHost, presenter.viewIfLoaded?.window != nil else {
      notice = "播放窗口尚未就绪，请稍后重试"
      return
    }
    guard presenter.presentedViewController == nil else { return }
    notice = "正在选择视频…"
    let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.movie], asCopy: true)
    picker.delegate = self
    picker.allowsMultipleSelection = false
    presenter.present(picker, animated: true)
  }
  func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL])
  {
    guard active, let url = urls.first else { return }
    open(url)
  }
  func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {}
  func togglePlayback() {
    guard hasVideo else { return }
    if isPlaying {
      player.pause()
    } else {
      if ended {
        ended = false
        player.seek(to: .zero)
      }
      displayLink?.isPaused = false
      player.play()
    }
  }
  func select(_ mode: RealtimeSRMode) -> Bool { renderer?.select(mode) ?? (mode == .original) }
  private func open(_ url: URL) {
    closeMedia()
    active = true
    epoch += 1
    let token = epoch
    notice = "正在读取视频…"
    inputName = url.lastPathComponent
    let asset = AVURLAsset(url: url)
    Task { [weak self] in
      do {
        let tracks = try await asset.loadTracks(withMediaType: .video)
        guard let track = tracks.first else {
          throw NSError(
            domain: "DemoPlayback", code: 1, userInfo: [NSLocalizedDescriptionKey: "未找到视频轨道"])
        }
        let fps = try await track.load(.nominalFrameRate)
        let transform = try await track.load(.preferredTransform)
        guard let self, self.active, self.epoch == token else { return }
        self.eligible = fps > 0 && fps <= 30.01 && transform == .identity && self.renderer != nil
        self.notice = self.eligible ? "读取视频帧…" : "系统原画播放：帧率、旋转或设备不满足超分条件"
        let item = AVPlayerItem(asset: asset)
        let output = AVPlayerItemVideoOutput(pixelBufferAttributes: [
          kCVPixelBufferPixelFormatTypeKey as String:
            kCVPixelFormatType_420YpCbCr8BiPlanarFullRange,
          kCVPixelBufferMetalCompatibilityKey as String: true,
          kCVPixelBufferIOSurfacePropertiesKey as String: [:] as [String: Any],
        ])
        item.add(output)
        self.output = output
        self.player.replaceCurrentItem(with: item)
        self.endObserver = NotificationCenter.default.addObserver(
          forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main
        ) { [weak self] _ in
          Task { @MainActor in
            guard let self, self.epoch == token else { return }
            self.ended = true
            self.wasPlaying = false
            self.displayLink?.isPaused = true
          }
        }
        self.itemObservation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
          if item.status == .failed {
            Task { @MainActor in
              guard let self, self.epoch == token else { return }
              self.notice = item.error?.localizedDescription ?? "视频无法播放"
              self.eligible = false
              self.player.pause()
              self.displayLink?.isPaused = true
              self.renderer?.invalidate()
            }
          }
        }
        let link = CADisplayLink(target: self, selector: #selector(self.tick(_:)))
        link.add(to: .main, forMode: .common)
        self.displayLink = link
        if UIApplication.shared.applicationState == .active {
          self.player.play()
        } else {
          self.wasPlaying = true
          link.isPaused = true
        }
      } catch {
        guard let self, self.epoch == token else { return }
        self.notice = "读取失败：\(error.localizedDescription)"
      }
    }
  }
  @objc private func tick(_ link: CADisplayLink) {
    guard eligible, let output else { return }
    let time = output.itemTime(forHostTime: link.targetTimestamp)
    var pts = CMTime.invalid
    if output.hasNewPixelBuffer(forItemTime: time),
      let pb = output.copyPixelBuffer(forItemTime: time, itemTimeForDisplay: &pts)
    {
      renderer?.render(pb, pts: pts.isNumeric ? pts : time)
    }
  }
  func background() {
    guard !backgrounded else { return }
    backgrounded = true
    wasPlaying = isPlaying
    player.pause()
    displayLink?.isPaused = true
    renderer?.invalidate()
  }
  func foreground() {
    guard active, backgrounded else { return }
    backgrounded = false
    displayLink?.isPaused = false
    if wasPlaying { player.play() }
  }
  func close() {
    active = false
    epoch += 1
    wasPlaying = false
    backgrounded = false
    closeMedia()
    inputName = nil
    notice = "尚未选择视频"
  }
  private func closeMedia() {
    player.pause()
    displayLink?.invalidate()
    displayLink = nil
    itemObservation = nil
    if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
    endObserver = nil
    ended = false
    if let output, let item = player.currentItem { item.remove(output) }
    output = nil
    player.replaceCurrentItem(with: nil)
    renderer?.clear()
    eligible = false
  }
  deinit {
    displayLink?.invalidate()
    observers.forEach { NotificationCenter.default.removeObserver($0) }
    if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
  }
}
