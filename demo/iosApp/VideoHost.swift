import Foundation
import ODAIKitDemo
import ODAIKitVideo
import ODAIKitAppleVideo

@MainActor
final class VideoHost: NSObject, VideoDemoActions {
    let model: VideoViewModel
    private let store: VideoInputStore
    private let resolveOutput: (String) -> URL?
    private let picker: (@escaping (URL?) -> Void) -> Void
    private let presentVideo: (URL, @escaping () -> Void) -> Void
    private let shareVideoFile: (URL, @escaping () -> Void) -> Void
    private var selected: String?
    private var pickerGeneration = 0
    private var foreground = true
    private var closed = false

    init(bridge: any VideoBridge, store: VideoInputStore, resolveOutput: @escaping (String) -> URL?,
         picker: @escaping (@escaping (URL?) -> Void) -> Void,
         presentVideo: @escaping (URL, @escaping () -> Void) -> Void,
         shareVideo: @escaping (URL, @escaping () -> Void) -> Void) {
        self.model = VideoViewModel(bridge: bridge); self.store = store; self.resolveOutput = resolveOutput
        self.picker = picker; self.presentVideo = presentVideo; self.shareVideoFile = shareVideo
    }
    static func make() throws -> VideoHost {
        let store = try VideoInputStore()
        let backend = AppleVideoToolboxBackend()
        let client = try VideoProcessingClient(defaultBackendId: backend.backendId, backends: [backend])
        let bridge = IOSVideoBridge(client: client, inputs: store)
        let picker = VideoFilePicker()
        return VideoHost(bridge: bridge, store: store, resolveOutput: { bridge.outputURL(reference: $0) },
            picker: { picker.present(reply: $0) }, presentVideo: VideoFilePicker.play, shareVideo: VideoFilePicker.share)
    }
    func selectVideoFile() {
        guard !closed, foreground, model.state.controlsEnabled else { return }
        pickerGeneration += 1
        let generation = pickerGeneration
        picker { [weak self] source in
            guard let self, !closed, foreground, pickerGeneration == generation, let source else { return }
            do {
                let token = try store.importVideo(from: source)
                let previous = selected
                selected = token
                model.selectVideo(reference: token, displayName: source.lastPathComponent)
                if let previous { store.remove(reference: previous) }
            } catch { model.inputFailed() }
        }
    }
    func clearVideoFile() {
        pickerGeneration += 1
        model.selectVideo(reference: nil, displayName: nil)
        if let selected { store.remove(reference: selected) }
        selected = nil
    }
    func openVideo(reference: String) { present(reference, using: presentVideo) }
    func shareVideo(reference: String) { present(reference, using: shareVideoFile) }
    private func present(_ reference: String, using action: (URL, @escaping () -> Void) -> Void) {
        guard !closed, let source = resolveOutput(reference) else { return }
        do {
            let copy = try VideoPresentationCopy(source: source)
            action(copy.url) { copy.remove() }
        } catch { model.inputFailed() }
    }
    func leavePage() { pickerGeneration += 1; model.leavePage() }
    func background() { foreground = false; pickerGeneration += 1; model.background() }
    func onForeground() { if !closed { foreground = true } }
    func close() { if !closed { closed = true; pickerGeneration += 1; model.close(); store.close() } }
}

/// Playback/sharing have their own copy, independent of the VM's output lease.
private final class VideoPresentationCopy {
    let url: URL
    private let directory: URL
    init(source: URL) throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent("odai-video-presentation-" + UUID().uuidString)
        url = directory.appendingPathComponent("result.mov")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
        do { try FileManager.default.copyItem(at: source, to: url) }
        catch { try? FileManager.default.removeItem(at: directory); throw error }
    }
    func remove() { try? FileManager.default.removeItem(at: directory) }
    deinit { remove() }
}
