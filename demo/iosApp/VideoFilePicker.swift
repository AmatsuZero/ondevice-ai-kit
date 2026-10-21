import UIKit
import AVKit
import UniformTypeIdentifiers

@MainActor
final class VideoFilePicker: NSObject, UIDocumentPickerDelegate {
    private var reply: ((URL?) -> Void)?
    private static func presenter() -> UIViewController? {
        var presenter = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController
        while let next = presenter?.presentedViewController { presenter = next }
        return presenter
    }
    func present(reply: @escaping (URL?) -> Void) {
        guard self.reply == nil, let presenter = Self.presenter() else { reply(nil); return }
        self.reply = reply
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.movie], asCopy: false)
        picker.allowsMultipleSelection = false; picker.delegate = self
        presenter.present(picker, animated: true)
    }
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        let callback = reply; reply = nil; callback?(urls.first)
    }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        let callback = reply; reply = nil; callback?(nil)
    }
    static func play(_ url: URL, done: @escaping () -> Void) {
        guard let presenter = presenter() else { done(); return }
        let player = VideoPlayer(); player.cleanup = done; player.player = AVPlayer(url: url)
        presenter.present(player, animated: true) { player.player?.play() }
    }
    static func share(_ url: URL, done: @escaping () -> Void) {
        guard let presenter = presenter() else { done(); return }
        let share = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        share.completionWithItemsHandler = { _, _, _, _ in done() }
        share.popoverPresentationController?.sourceView = presenter.view
        share.popoverPresentationController?.sourceRect = CGRect(x: presenter.view.bounds.midX, y: presenter.view.bounds.midY, width: 1, height: 1)
        presenter.present(share, animated: true)
    }
}

@MainActor private final class VideoPlayer: AVPlayerViewController {
    var cleanup: (() -> Void)?
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isBeingDismissed || presentingViewController == nil { player?.pause(); cleanup?(); cleanup = nil }
    }
    deinit { cleanup?() }
}
