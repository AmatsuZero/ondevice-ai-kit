import SwiftUI
import UIKit
import UniformTypeIdentifiers
import AVFAudio
import AVFoundation
import ODAIKitVoiceEffects
import ODAIKitAppleVoiceEffects
@preconcurrency import ODAIKitDemo

struct VoiceEffectsDemoSheet: UIViewControllerRepresentable {
    let controller: DemoController
    let dismiss: () -> Void
    func makeUIViewController(context: Context) -> UINavigationController {
        controller.stopCamera()
        controller.speechHost?.background()
        #if ODAI_VIDEO_DEMO
        controller.videoHost?.background()
        #endif
        let host = VoiceEffectsHost(prepareAudio: {
            while let speech = controller.speechHost, !speech.model.state.controlsEnabled {
                try Task.checkCancellation(); try await Task.sleep(nanoseconds: 20_000_000)
            }
        }, done: {
            controller.speechHost?.onForeground()
            #if ODAI_VIDEO_DEMO
            controller.videoHost?.onForeground()
            #endif
            dismiss()
        })
        return UINavigationController(rootViewController: host)
    }
    func updateUIViewController(_ view: UINavigationController, context: Context) {}
    static func dismantleUIViewController(_ view: UINavigationController, coordinator: ()) {
        (view.viewControllers.first as? VoiceEffectsHost)?.shutdown()
    }
}

@MainActor
final class VoiceEffectsHost: UIViewController, @preconcurrency VoiceEffectsDemoActions,
    AVAudioRecorderDelegate, AVAudioPlayerDelegate, UIDocumentPickerDelegate {
    private let inputs = VoiceEffectsInputStore()
    private var bridge: IOSVoiceEffectsBridge?
    private(set) var model: VoiceEffectsViewModel?
    private(set) var preparationState = DemoHostState.preparing
    var recordSink: ((DemoCapabilityId, DemoRecordOutcome, String) -> Void)? {
        didSet { model?.onTerminal = recordSink }
    }
    private let embedded: Bool
    private var preparingEmbedded = false
    private var prepare: Task<Void, Never>?
    private var preparation: () async throws -> Void
    private let done: () -> Void
    private let root = FileManager.default.temporaryDirectory.appendingPathComponent("voice-host-\(UUID().uuidString)", isDirectory: true)
    private var closed = false
    private var generation = 0
    private var recorder: AVAudioRecorder?
    private var recordingURL: URL?
    private var discardRecording = false
    private var player: AVAudioPlayer?
    private var playingReference: String?
    private var playbackID: UUID?
    private var preparingPlayback = Set<UUID>()
    private var playbackHolds: [UUID: (String?, (any IOSVoiceEffectsInputLease)?)] = [:]
    private var exportReference: String?
    private var pickerIsExport = false
    private var sessionOriginal: (AVAudioSession.Category, AVAudioSession.Mode, AVAudioSession.CategoryOptions)?
    init(embedded: Bool = false, prepareAudio: @escaping () async throws -> Void, done: @escaping () -> Void) {
        self.embedded = embedded
        self.preparation = prepareAudio; self.done = done
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad(); view.backgroundColor = .systemBackground
        let label = UILabel(); label.text = "正在等待音频任务结束并准备变声…"; label.numberOfLines = 0
        label.frame = view.bounds; label.autoresizingMask = [.flexibleWidth,.flexibleHeight]; view.addSubview(label)
        NotificationCenter.default.addObserver(self, selector: #selector(background), name: UIApplication.willResignActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(interrupted(_:)), name: AVAudioSession.interruptionNotification, object: nil)
        preparingEmbedded = true
        prepare = Task { @MainActor in
            defer { preparingEmbedded = false }
            do {
                try await preparation()
                let directory = root.appendingPathComponent("outputs", isDirectory: true)
                try await Task.detached { try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true) }.value
                guard !closed else { return }
                let client = try VoiceEffectsClient(backend: AppleVoiceEffectsBackend(), outputDirectory: directory)
                let bridge = IOSVoiceEffectsBridge(client: client, inputs: inputs); self.bridge = bridge
                let model = VoiceEffectsViewModel(bridge: bridge); self.model = model
                model.onTerminal = recordSink
                preparationState = .ready
                let child = embedded
                    ? VoiceEffectsViewControllerKt.voiceEffectsEmbeddedViewController(model: model, actions: self)
                    : VoiceEffectsViewControllerKt.voiceEffectsMainViewController(model: model, actions: self)
                label.removeFromSuperview(); addChild(child); view.addSubview(child.view)
                child.view.frame = view.bounds; child.view.autoresizingMask = [.flexibleWidth,.flexibleHeight]; child.didMove(toParent: self)
            } catch { if !closed { preparationState = .failed; label.text = "音频准备失败，请返回重试" } }
        }
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(exitPage))
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isBeingDismissed || navigationController?.isBeingDismissed == true { shutdown() }
    }
    @objc private func exitPage() { leavePage() }
    @objc private func background() {
        stopPlayback(); stopCapture(discard: true); model?.cancel()
    }
    @objc private func interrupted(_ notification: Notification) {
        guard let type = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              type == AVAudioSession.InterruptionType.began.rawValue else { return }
        background()
    }
    func selectAudioFile() {
        guard let model, model.state.controlsEnabled, !closed else { return }
        model.setImporting(value: true); pickerIsExport = false
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.wav], asCopy: false)
        picker.delegate = self; picker.allowsMultipleSelection = false; present(picker, animated: true)
    }
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        if pickerIsExport { finishExport(); return }
        guard let source = urls.first, let model, !closed else { model?.setImporting(value: false); return }
        generation += 1; let ticket = generation; let directory = root.appendingPathComponent("inputs")
        let scoped = source.startAccessingSecurityScopedResource()
        Task { @MainActor in
            defer { if scoped { source.stopAccessingSecurityScopedResource() }; model.setImporting(value: false) }
            do {
                let url = try await Task.detached {
                    try VoiceEffectsWave.store(VoiceEffectsWave.normalize(url: source), directory: directory)
                }.value
                if closed || ticket != generation { _ = await Task.detached { try? FileManager.default.removeItem(at: url) }.value; return }
                await install(url, name: source.lastPathComponent)
            } catch { if !closed { model.inputFailed(reason: "unsupportedOrInvalidWav") } }
        }
    }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        if pickerIsExport { finishExport() } else { model?.setImporting(value: false) }
    }
    func startRecording() {
        guard let model, model.state.controlsEnabled, !closed, UIApplication.shared.applicationState == .active else { return }
        model.setImporting(value: true); generation += 1; let ticket = generation
        AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
            Task { @MainActor in
                guard let self, !self.closed, ticket == self.generation else { return }
                model.setImporting(value: false)
                guard granted else { model.inputFailed(reason: "microphonePermissionDenied"); return }
                guard UIApplication.shared.applicationState == .active else { return }
                await self.beginRecording()
            }
        }
    }
    private func beginRecording() async {
        guard let model, !closed else { return }
        model.setImporting(value: true)
        defer { model.setImporting(value: false) }
        let directory = root.appendingPathComponent("recordings")
        do {
            try await Task.detached { try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true) }.value
            guard !closed, UIApplication.shared.applicationState == .active else { return }
            try acquireSession(recording: true)
            let url = directory.appendingPathComponent("record-\(UUID().uuidString).wav"); recordingURL = url
            let value = try AVAudioRecorder(url: url, settings: [AVFormatIDKey: kAudioFormatLinearPCM,
                AVSampleRateKey: 16000, AVNumberOfChannelsKey: 1, AVLinearPCMBitDepthKey: 16,
                AVLinearPCMIsFloatKey: false, AVLinearPCMIsBigEndianKey: false])
            value.delegate = self; recorder = value; discardRecording = false
            guard value.record(forDuration: 30) else { throw VoiceEffectsWave.Failure.unsupportedOrInvalidWAV }
            model.setRecording(value: true)
        } catch { recorder = nil; releaseSession(); model.inputFailed(reason: "audioRecordingFailed"); cleanupRecording() }
    }
    func stopRecording() { stopCapture(discard: false) }
    private func stopCapture(discard: Bool) {
        guard let recorder else { return }
        discardRecording = discardRecording || discard; recorder.stop()
    }
    nonisolated func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        Task { @MainActor [weak self] in self?.finishedRecording(recorder, successfully: flag) }
    }
    private func finishedRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        guard self.recorder === recorder else { return }
        self.recorder = nil; releaseSession()
        let source = recordingURL; recordingURL = nil
        if !flag && !discardRecording && !closed { model?.inputFailed(reason: "audioRecordingFailed") }
        let discard = discardRecording || closed || !flag
        let directory = root.appendingPathComponent("inputs")
        Task { @MainActor in
            defer { model?.setRecording(value: false) }
            guard let source else { return }
            do {
                let output: URL? = try await Task.detached {
                    defer { try? FileManager.default.removeItem(at: source) }
                    if discard { return nil }
                    return try VoiceEffectsWave.store(VoiceEffectsWave.normalize(url: source), directory: directory)
                }.value
                if let output {
                    if closed { _ = await Task.detached { try? FileManager.default.removeItem(at: output) }.value }
                    else { await install(output, name: "录音.wav") }
                }
            } catch { if !closed { model?.inputFailed(reason: "audioRecordingFailed") } }
        }
    }
    nonisolated func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) {
        Task { @MainActor [weak self] in
            guard let self, self.recorder === recorder else { return }
            self.discardRecording = true; recorder.stop()
            self.finishedRecording(recorder, successfully: false)
            self.model?.inputFailed(reason: "audioRecordingFailed")
        }
    }
    private func cleanupRecording() {
        if let url = recordingURL { Task.detached { try? FileManager.default.removeItem(at: url) } }
        recordingURL = nil
    }
    func playOriginal() {
        guard let reference = model?.state.inputReference, let lease = try? inputs.acquire(reference: reference) else { return }
        play(lease.url, reference: nil, input: lease)
    }
    func playResult(reference: String) {
        guard let url = bridge?.outputURL(reference: reference), model?.holdOutput(reference: reference) == true else { return }
        play(url, reference: reference, input: nil)
    }
    private func play(_ url: URL, reference: String?, input: (any IOSVoiceEffectsInputLease)?) {
        stopPlayback()
        guard let model, model.state.controlsEnabled, !closed, UIApplication.shared.applicationState == .active else {
            if let reference { self.model?.endOutputUse(reference: reference) }
            if let input { Task { await input.release() } }; return
        }
        let id = UUID(); playbackID = id; preparingPlayback.insert(id); playbackHolds[id] = (reference, input)
        playingReference = reference; model.setMediaBusy(value: true)
        Task { @MainActor in
            defer {
                preparingPlayback.remove(id)
                if playbackID != id { releasePlaybackHold(id) }
            }
            do {
                // File parsing happens off MainActor. No read starts after the
                // output hold ends: the hold remains until this preparation drains.
                let data = try await Task.detached { try VoiceEffectsWave.normalize(url: url) }.value
                guard !closed, UIApplication.shared.applicationState == .active, playbackID == id else {
                    if playbackID == id { stopPlayback() }
                    return
                }
                let value = try AVAudioPlayer(data: data, fileTypeHint: AVFileType.wav.rawValue)
                try acquireSession(recording: false); player = value; value.delegate = self
                guard value.play() else { throw VoiceEffectsWave.Failure.unsupportedOrInvalidWAV }
            } catch { if playbackID == id { stopPlayback(); model.inputFailed(reason: "audioPlaybackFailed") } }
        }
    }
    func stopPlayback() {
        player?.stop(); player = nil
        if let id = playbackID {
            playbackID = nil
            if !preparingPlayback.contains(id) { releasePlaybackHold(id) }
        }
        playingReference = nil
        if exportReference == nil { model?.setMediaBusy(value: false) }
        if recorder == nil { releaseSession() }
    }
    private func releasePlaybackHold(_ id: UUID) {
        guard let hold = playbackHolds.removeValue(forKey: id) else { return }
        if let reference = hold.0 { model?.endOutputUse(reference: reference) }
        if let input = hold.1 { Task { await input.release() } }
    }
    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor [weak self] in if self?.player === player { self?.stopPlayback() } }
    }
    nonisolated func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        Task { @MainActor [weak self] in
            if self?.player === player { self?.stopPlayback(); self?.model?.inputFailed(reason: "audioPlaybackFailed") }
        }
    }
    func exportAudio(reference: String) {
        guard let model, model.state.controlsEnabled, let url = bridge?.outputURL(reference: reference), model.holdOutput(reference: reference), !closed else { return }
        exportReference = reference; model.setMediaBusy(value: true); pickerIsExport = true
        let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
        picker.delegate = self; present(picker, animated: true)
    }
    private func finishExport() {
        if let reference = exportReference { model?.endOutputUse(reference: reference) }
        exportReference = nil; model?.setMediaBusy(value: false)
    }
    private func install(_ url: URL, name: String) async {
        let old = model?.state.inputReference
        let reference = inputs.register(url); model?.selectInput(reference: reference, name: name)
        await inputs.retire(old)
    }
    private func acquireSession(recording: Bool) throws {
        let session = AVAudioSession.sharedInstance()
        sessionOriginal = (session.category,session.mode,session.categoryOptions)
        do {
            try session.setCategory(recording ? .record : .playback, mode: recording ? .measurement : .default, options: [])
            try session.setActive(true)
        } catch { releaseSession(); throw error }
    }
    private func releaseSession() {
        guard let original = sessionOriginal else { return }
        let session = AVAudioSession.sharedInstance()
        try? session.setActive(false, options: .notifyOthersOnDeactivation)
        try? session.setCategory(original.0, mode: original.1, options: original.2); sessionOriginal = nil
    }
    var idle: Bool {
        !preparingEmbedded && recorder == nil && preparingPlayback.isEmpty &&
            (model == nil || (model!.state.controlsEnabled && model!.state.pendingReleases == 0))
    }
    func leaveEmbeddedPage() {
        generation += 1
        stopPlayback(); stopCapture(discard: true); model?.leavePage()
    }
    func leavePage() {
        if embedded { leaveEmbeddedPage(); return }
        guard !closed else { return }
        shutdown()
        Task { @MainActor in
            // Resume the existing speech host only after recorder/session and
            // pending playback preparation have actually stopped using audio.
            while recorder != nil || !preparingPlayback.isEmpty {
                try? await Task.sleep(nanoseconds: 10_000_000)
            }
            done()
        }
    }
    func shutdown() {
        guard !closed else { return }; closed = true; generation += 1; prepare?.cancel()
        stopPlayback(); stopCapture(discard: true); model?.close()
        // Document picker holds output until its own terminal callback, including
        // cancellation. It must not be released while UIKit is still copying it.
        Task { await inputs.close() }
        NotificationCenter.default.removeObserver(self)
    }
}
