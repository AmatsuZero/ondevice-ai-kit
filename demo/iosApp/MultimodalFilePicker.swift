import UIKit
import UniformTypeIdentifiers

@MainActor
final class MultimodalFilePicker: NSObject, UIDocumentPickerDelegate {
  private let presentation: (UIDocumentPickerViewController) -> Bool
  private var reply: ((URL?) -> Void)?
  private var controller: UIDocumentPickerViewController?
  private var finishing = false
  private var closed = false
  var isPresenting: Bool { reply != nil }

  init(present: ((UIDocumentPickerViewController) -> Bool)? = nil) {
    presentation = present ?? Self.show
    super.init()
  }

  func present(reply: @escaping (URL?) -> Void) { present(types: [.png, .jpeg], reply: reply) }
  func presentVideo(reply: @escaping (URL?) -> Void) { present(types: [.movie], reply: reply) }
  private func present(types: [UTType], reply: @escaping (URL?) -> Void) {
    guard !closed, self.reply == nil else {
      reply(nil)
      return
    }
    let controller = UIDocumentPickerViewController(
      forOpeningContentTypes: types, asCopy: false)
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
    guard self.controller === controller, !finishing else { return }
    let callback = reply
    finishing = true
    defer {
      reply = nil
      self.controller = nil
      finishing = false
    }
    callback?(url)
  }

  static func presenter(in windows: [UIWindow]) -> UIViewController? {
    let window = windows.first(where: \.isKeyWindow)
      ?? windows.first(where: { !$0.isHidden && $0.alpha > 0 && $0.windowLevel == .normal })
      ?? windows.first(where: { !$0.isHidden && $0.alpha > 0 })
    var presenter = window?.rootViewController
    while let next = presenter?.presentedViewController { presenter = next }
    guard let presenter, presenter.viewIfLoaded?.window != nil, !presenter.isBeingDismissed else {
      return nil
    }
    return presenter
  }

  private static func show(_ picker: UIDocumentPickerViewController) -> Bool {
    let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
      .flatMap(\.windows)
    guard let presenter = presenter(in: windows) else { return false }
    presenter.present(picker, animated: true)
    return true
  }
}
