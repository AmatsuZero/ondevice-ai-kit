import Foundation
import ODAIKitDemo

@MainActor
final class MultimodalHost: NSObject, @preconcurrency MultimodalDemoActions {
  let model: MultimodalViewModel
  private let store: MultimodalInputStore
  private let picker: (@escaping (URL?) -> Void) throws -> Void
  private var selected: String?
  private var pickerGeneration = 0
  private var pendingPicker: Int?
  private var foreground = true
  private var closed = false

  init(
    store: MultimodalInputStore, bridge: any MultimodalBridge = UnavailableMultimodalBridge(),
    picker: @escaping (@escaping (URL?) -> Void) throws -> Void
  ) {
    self.store = store
    self.model = MultimodalViewModel(bridge: bridge)
    self.picker = picker
    super.init()
  }

  func selectImageFile() {
    guard !closed, foreground, model.state.controlsEnabled else { return }
    pickerGeneration += 1
    let ticket = pickerGeneration
    pendingPicker = ticket
    do {
      try picker { [weak self] source in
        guard let self, !self.closed, !self.model.state.closed, self.pendingPicker == ticket else {
          return
        }
        self.pendingPicker = nil
        guard let source else { return }
        do {
          let token = try self.store.importImage(from: source)
          let previous = self.selected
          self.selected = token
          self.model.selectImage(reference: token, displayName: source.lastPathComponent)
          if let previous { self.store.remove(reference: previous) }
        } catch { self.model.inputFailed() }
      }
    } catch {
      if pendingPicker == ticket {
        pendingPicker = nil
        model.inputFailed()
      }
    }
  }

  func clearImageFile() {
    guard !closed else { return }
    pendingPicker = nil
    model.selectImage(reference: nil, displayName: nil)
    if let selected { store.remove(reference: selected) }
    selected = nil
  }
  func leavePage() {
    pendingPicker = nil
    model.leavePage()
  }
  func leaveMultimodalPage() { leavePage() }
  func background(preservePicker: Bool = false) {
    foreground = false
    if !preservePicker { pendingPicker = nil }
    model.background()
  }
  func onForeground() { if !closed { foreground = true } }
  func close() {
    if !closed {
      closed = true
      pendingPicker = nil
      model.close()
      store.close()
      selected = nil
    } else {
      store.close()
    }
  }
}
