import Foundation
import UIKit
import ODAIKitDemo
import ODAIKitModelStore

@MainActor
final class WeMMVideoDemoHost: NSObject, @preconcurrency UnifiedDemoModelActions {
  private(set) var host: MultimodalHost?
  private(set) var busy = false
  private(set) var status = "请选择包含 language.gguf 和 mmproj.gguf 的模型文件夹。"
  var canPrepare: Bool { !closed && !busy && host == nil }
  var ready: Bool { host != nil }

  private let inputPicker = MultimodalFilePicker()
  private var folderPicker = MultimodalFilePicker()
  private var preparation: MultimodalHostPreparation?
  private var generation = 0
  private var closed = false
  private lazy var modelStoreResult: Result<ModelStore, Error> = {
    do {
      let os = ProcessInfo.processInfo.operatingSystemVersion
      return .success(try ModelStore(
        root: Self.documentsDirectory().appendingPathComponent("wemm-model-store"),
        compatibility: .init(
          backendId: WeMMVideoModel.backendId, backendVersion: "1.0.0",
          osVersion: "\(os.majorVersion).\(os.minorVersion).\(os.patchVersion)",
          architecture: "arm64"
        )))
    } catch {
      return .failure(error)
    }
  }()

  func prepare() {
    guard canPrepare else { return }
    generation += 1
    let ticket = generation
    busy = true
    status = "请选择包含 language.gguf 和 mmproj.gguf 的模型文件夹。"
    folderPicker.presentModelFolder { [weak self] source in
      guard let self, !self.closed, self.generation == ticket else { return }
      guard let source else {
        self.busy = false
        self.status = "已取消选择模型文件夹。"
        return
      }
      self.startPreparation(source: source, ticket: ticket)
    }
  }

  func cancel() {
    guard !closed, busy else { return }
    generation += 1
    preparation = nil
    folderPicker.close()
    folderPicker = MultimodalFilePicker()
    busy = false
    status = "已取消模型准备。"
  }

  func restore() {
    guard canPrepare else { return }
    generation += 1
    busy = true
    status = "正在检查已安装的模型…"
    let documents = Self.documentsDirectory()
    startPreparation(source: documents.appendingPathComponent("wemm-model"), ticket: generation)
  }

  func background() {
    host?.background(preservePicker: inputPicker.isPresenting)
  }

  func foreground() {
    host?.onForeground()
  }

  func close() {
    guard !closed else { return }
    closed = true
    generation += 1
    preparation = nil
    busy = false
    folderPicker.close()
    inputPicker.close()
    host?.close()
    host = nil
  }

  private func startPreparation(source: URL, ticket: Int) {
    let picker = inputPicker
    let operation = MultimodalHostPreparation()
    let modelStoreResult = self.modelStoreResult
    preparation = operation
    status = "正在验证模型并准备视频输入…"
    operation.start(
      make: { [weak self, modelStoreResult] in
        do {
          return try await Self.makeHost(
            modelStore: try modelStoreResult.get(), source: source, picker: picker)
        } catch {
          if let self, !self.closed, self.generation == ticket {
            self.status = "模型准备失败。请确认所选文件夹包含 language.gguf 和 mmproj.gguf，且文件完整后重试。"
          }
          throw error
        }
      },
      ready: { [weak self] newHost in
        guard let self, !self.closed, self.generation == ticket else {
          newHost?.close()
          return
        }
        self.preparation = nil
        self.busy = false
        if let newHost {
          self.host?.close()
          self.host = newHost
          newHost.model.setPrompt(prompt: "Describe this video.")
          if UIApplication.shared.applicationState == .active {
            newHost.model.refreshBackends()
          } else {
            newHost.background()
          }
          self.status = "模型已就绪，可以选择视频。"
        } else if self.status.hasPrefix("正在") {
          self.status = "模型准备失败，请检查模型文件夹后重试。"
        }
      })
  }

  private static func makeHost(
    modelStore: ModelStore, source: URL, picker: MultimodalFilePicker
  ) async throws -> MultimodalHost {
    guard source.isFileURL else {
      throw NSError(domain: "WeMMVideoDemoHost", code: 1, userInfo: [
        NSLocalizedDescriptionKey: "模型来源必须是本地文件夹。"
      ])
    }
    let accessed = source.startAccessingSecurityScopedResource()
    defer { if accessed { source.stopAccessingSecurityScopedResource() } }

    let manifest = try WeMMVideoModel.manifest()

    return try await MultimodalModelHostFactory.make(
      modelStore: modelStore, manifest: manifest, source: source,
      inputDirectory: FileManager.default.temporaryDirectory.appendingPathComponent(
        "odai-wemm-video-input-" + UUID().uuidString),
      videoInputDirectory: FileManager.default.temporaryDirectory.appendingPathComponent(
        "odai-wemm-video-files-" + UUID().uuidString),
      videoPicker: { picker.presentVideo(reply: $0) },
      picker: { picker.present(reply: $0) },
      backend: { try WeMMVideoBackend(lease: $0) }
    )
  }

  private static func documentsDirectory() -> URL {
    FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
  }
}
