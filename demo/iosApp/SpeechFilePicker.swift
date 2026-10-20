import UIKit
import UniformTypeIdentifiers

@MainActor
final class SpeechFilePicker: NSObject, UIDocumentPickerDelegate {
    private var reply: ((URL?) -> Void)?
    func present(reply: @escaping (URL?) -> Void) {
        guard self.reply == nil else { reply(nil); return }
        var presenter = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController
        while let next = presenter?.presentedViewController { presenter = next }
        guard let presenter else { reply(nil); return }
        self.reply = reply
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.audio], asCopy: false)
        picker.allowsMultipleSelection = false
        picker.delegate = self
        presenter.present(picker, animated: true)
    }
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        let callback = reply; reply = nil
        callback?(urls.first)
    }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        let callback = reply; reply = nil
        callback?(nil)
    }
}
