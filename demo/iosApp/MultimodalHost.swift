import Foundation
import ODAIKitDemo

@MainActor
final class MultimodalHost: NSObject, @preconcurrency MultimodalVideoDemoActions {
  let model: MultimodalViewModel
  private let store: MultimodalInputStore
  private let videoStore: MultimodalInputStore?
  private let videoPicker: ((@escaping (URL?) -> Void) throws -> Void)?
  private var selectedStore: MultimodalInputStore
  private let picker: (@escaping (URL?) -> Void) throws -> Void
  private var selected: String?
  private var pickerGeneration = 0
  private var pendingPicker: Int?
  private var foreground = true
  private var closed = false
  private let importInput: @MainActor (MultimodalInputStore, URL) throws -> Task<String, Error>
  private var importGeneration = 0
  private var importOperation: Task<String, Error>?
  private(set) var inputTask: Task<Void, Never>?

  init(
    store: MultimodalInputStore, bridge: any MultimodalBridge = UnavailableMultimodalBridge(),
    videoStore: MultimodalInputStore? = nil,
    videoPicker: ((@escaping (URL?) -> Void) throws -> Void)? = nil,
    importInput: @escaping @MainActor (MultimodalInputStore, URL) throws -> Task<String, Error> = {
      try $0.startImport(from: $1)
    },
    picker: @escaping (@escaping (URL?) -> Void) throws -> Void
  ) {
    self.store = store
    self.selectedStore = store
    self.videoStore = videoStore
    self.videoPicker = videoPicker
    self.model = MultimodalViewModel(bridge: bridge)
    self.picker = picker
    self.importInput = importInput
    super.init()
  }

  func selectImageFile() { selectInput(video: false) }
  func selectVideoFile() { selectInput(video: true) }
  private func selectInput(video: Bool) {
    guard !closed, foreground, model.state.controlsEnabled else { return }
    cancelImport()
    pendingPicker = nil
    guard let target = video ? videoStore : store,
      let picker = video ? videoPicker : picker
    else {
      model.inputFailed()
      return
    }
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
        self.beginImport(from: source, into: target, video: video)
      }
    } catch {
      if pendingPicker == ticket {
        pendingPicker = nil
        model.inputFailed()
      }
    }
  }

  private func beginImport(from source: URL, into target: MultimodalInputStore, video: Bool) {
    let ticket = importGeneration
    do {
      let operation = try importInput(target, source)
      importOperation = operation
      inputTask = Task { [weak self] in
        do {
          let token = try await operation.value
          guard let self, !self.closed, !self.model.state.closed,
            self.importGeneration == ticket, !operation.isCancelled
          else {
            target.remove(reference: token)
            return
          }
          self.importOperation = nil
          self.inputTask = nil
          let previous = self.selected
          let previousStore = self.selectedStore
          self.selected = token
          self.selectedStore = target
          if video {
            self.model.selectVideo(reference: token, displayName: source.lastPathComponent)
          } else {
            self.model.selectImage(reference: token, displayName: source.lastPathComponent)
          }
          if let previous { previousStore.remove(reference: previous) }
        } catch {
          guard let self, self.importGeneration == ticket, !self.closed,
            !self.model.state.closed
          else { return }
          self.importOperation = nil
          self.inputTask = nil
          if !(error is CancellationError) { self.model.inputFailed() }
        }
      }
    } catch {
      if !(error is CancellationError) { model.inputFailed() }
    }
  }

  private func cancelImport() {
    importGeneration += 1
    importOperation?.cancel()
    importOperation = nil
    inputTask = nil
  }

  deinit { importOperation?.cancel() }

  func clearImageFile() {
    guard !closed else { return }
    pendingPicker = nil
    cancelImport()
    model.selectImage(reference: nil, displayName: nil)
    if let selected { selectedStore.remove(reference: selected) }
    selected = nil
  }
  func leavePage() {
    pendingPicker = nil
    cancelImport()
    model.leavePage()
  }
  func leaveMultimodalPage() { leavePage() }
  func background(preservePicker: Bool = false) {
    foreground = false
    if !preservePicker {
      pendingPicker = nil
      cancelImport()
    }
    model.background()
  }
  func onForeground() { if !closed { foreground = true } }
  func close() {
    if !closed {
      closed = true
      pendingPicker = nil
      cancelImport()
      model.close()
      store.close()
      videoStore?.close()
      selected = nil
    } else {
      store.close()
      videoStore?.close()
    }
  }
}
