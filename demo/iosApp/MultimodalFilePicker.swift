import UIKit
import UniformTypeIdentifiers

@MainActor
final class MultimodalFilePicker: NSObject, UIDocumentPickerDelegate {
  private let presentation: (UIDocumentPickerViewController) -> Bool
  private var reply: ((URL?) -> Void)?
  private var controller: UIDocumentPickerViewController?
  private var closed = false
  var isPresenting: Bool { reply != nil }

  init(present: ((UIDocumentPickerViewController) -> Bool)? = nil) {
    presentation = present ?? Self.show
    super.init()
  }

  func present(reply: @escaping (URL?) -> Void) {
    guard !closed, self.reply == nil else {
      reply(nil)
      return
    }
    let controller = UIDocumentPickerViewController(
      forOpeningContentTypes: [.png, .jpeg], asCopy: false)
    controller.allowsMultipleSelection = false
    controller.delegate = self
    self.controller = controller
    self.reply = reply
    if !presentation(controller) { finish(controller, url: nil) }
  }

  func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL])
  {
    finish(controller, url: urls.first)
  }
  func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
    finish(controller, url: nil)
  }
  func close() {
    guard !closed else { return }
    closed = true
    if let controller {
      controller.dismiss(animated: false)
      finish(controller, url: nil)
    }
  }

  private func finish(_ controller: UIDocumentPickerViewController, url: URL?) {
    guard self.controller === controller else { return }
    let callback = reply
    reply = nil
    self.controller = nil
    callback?(url)
  }

  private static func show(_ picker: UIDocumentPickerViewController) -> Bool {
    var presenter = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
      .flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController
    while let next = presenter?.presentedViewController { presenter = next }
    guard let presenter, presenter.viewIfLoaded?.window != nil, !presenter.isBeingDismissed else {
      return false
    }
    presenter.present(picker, animated: true)
    return true
  }
}
