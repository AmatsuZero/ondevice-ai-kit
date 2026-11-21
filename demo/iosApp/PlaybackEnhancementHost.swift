import AVFoundation
import CoreImage
import QuartzCore
import UIKit
import UniformTypeIdentifiers
import ODAIKitDemo

/// Demo-only playback host. CMP owns the controls and copy; this host owns
/// decoding, frame sampling, preview processing, and the embedded render view.
/// The enhanced path is intentionally a Core Image preview, not production SR.
@MainActor
final class PlaybackEnhancementHost: NSObject, PlaybackEnhancementDemoActions {
    let playerAvailable = true
    let controlsAvailable = true
    let playerDescription = "iOS AVPlayer + AVPlayerItemVideoOutput 已接入；原生部分只提供渲染面板，控制项由 CMP 统一呈现。增强模式是 Core Image 锐化示意。"
    let viewController: UIViewController
    private let playerController: PlaybackEnhancementViewController

    override init() {
        playerController = PlaybackEnhancementViewController()
        viewController = playerController
        super.init()
    }

    func openPlayer() { playerController.chooseVideo() }
    func togglePlayback() { playerController.togglePlaybackFromCMP() }
    func showOriginal() { playerController.setMode(.original) }
    func showEnhancedPreview() { playerController.setMode(.enhanced) }
    func background() { playerController.pauseForBackground() }
    func foreground() { playerController.resumeAfterForeground() }
    func close() { playerController.stopPlayback() }
}

@MainActor
private final class PlaybackEnhancementViewController: UIViewController, UIDocumentPickerDelegate {
    enum Mode { case original, enhanced }

    private let imageView = UIImageView()
    private let player = AVPlayer()
    private let context = CIContext(options: [.useSoftwareRenderer: false])
    private var output: AVPlayerItemVideoOutput?
    private var displayLink: CADisplayLink?
    private var currentMode: Mode = .original
    private var hasVideo = false
    private var wasPlaying = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        imageView.contentMode = .scaleAspectFit
        imageView.clipsToBounds = true
        imageView.accessibilityIdentifier = "playback-preview"
        imageView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(imageView)
        NSLayoutConstraint.activate([
            imageView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            imageView.topAnchor.constraint(equalTo: view.topAnchor),
            imageView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    func chooseVideo() {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.movie], asCopy: true)
        picker.delegate = self
        picker.allowsMultipleSelection = false
        present(picker, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first else { return }
        open(url: url)
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {}

    func togglePlaybackFromCMP() {
        guard hasVideo else { return }
        if player.timeControlStatus == .playing { player.pause() } else { player.play() }
    }

    func setMode(_ mode: Mode) { currentMode = mode }

    private func open(url: URL) {
        stopOutput()
        let item = AVPlayerItem(url: url)
        let output = AVPlayerItemVideoOutput(pixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
        ])
        item.add(output)
        self.output = output
        player.replaceCurrentItem(with: item)
        hasVideo = true
        startOutput()
        player.play()
    }

    private func startOutput() {
        displayLink?.invalidate()
        let link = CADisplayLink(target: self, selector: #selector(displayLinkTick(_:)))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    @objc private func displayLinkTick(_ link: CADisplayLink) {
        guard let output, player.currentItem != nil else { return }
        let itemTime = output.itemTime(forHostTime: CACurrentMediaTime())
        guard itemTime.isNumeric,
              output.hasNewPixelBuffer(forItemTime: itemTime),
              let pixelBuffer = output.copyPixelBuffer(forItemTime: itemTime, itemTimeForDisplay: nil) else { return }

        let source = CIImage(cvPixelBuffer: pixelBuffer)
        let image: CIImage
        if currentMode == .original {
            image = source
        } else {
            let filter = CIFilter(name: "CISharpenLuminance")
            filter?.setValue(source, forKey: kCIInputImageKey)
            filter?.setValue(0.6, forKey: kCIInputSharpnessKey)
            image = filter?.outputImage ?? source
        }
        guard let cgImage = context.createCGImage(image, from: image.extent) else { return }
        imageView.image = UIImage(cgImage: cgImage)
    }

    func pauseForBackground() {
        wasPlaying = player.timeControlStatus == .playing
        player.pause()
        displayLink?.isPaused = true
    }

    func resumeAfterForeground() {
        displayLink?.isPaused = false
        if wasPlaying { player.play() }
    }

    func stopPlayback() {
        wasPlaying = false
        player.pause()
        player.replaceCurrentItem(with: nil)
        stopOutput()
        imageView.image = nil
        hasVideo = false
        currentMode = .original
    }

    private func stopOutput() {
        displayLink?.invalidate()
        displayLink = nil
        if let item = player.currentItem, let output { item.remove(output) }
        output = nil
    }

    deinit { displayLink?.invalidate() }
}
