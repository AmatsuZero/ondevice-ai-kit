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
        let host = VoiceEffectsHost(prepareAudio: { [weak controller] in
            while let speech = controller?.speechHost, !speech.model.state.controlsEnabled {
                try Task.checkCancellation(); try await Task.sleep(nanoseconds: 20_000_000)
            }
        }, done: { [weak controller] in
            controller?.speechHost?.onForeground()
            #if ODAI_VIDEO_DEMO
            controller?.videoHost?.onForeground()
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
    @preconcurrency LiveVoiceOwner, AVAudioRecorderDelegate, AVAudioPlayerDelegate, UIDocumentPickerDelegate {
    private let inputs = VoiceEffectsInputStore()
    private let liveAudio: RealtimeVoiceAudio
    let liveSession: RealtimeVoiceSession
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
    private var timbres: DemoVoiceTimbres?
    private var playingReference: String?
    private var playbackID: UUID?
    private var preparingPlayback = Set<UUID>()
    private var playbackHolds: [UUID: (String?, (any IOSVoiceEffectsInputLease)?)] = [:]
    private var exportReference: String?
    private var pickerIsExport = false
    private weak var activePicker: UIDocumentPickerViewController?
    private var pickerTicket = 0
    private enum InputTarget: Equatable { case content, reference }
    private var pickerTarget: InputTarget = .content
    private var pickerReferenceMode = false
    private var recordingTarget: InputTarget = .content
    private var recordingReferenceMode = false
    private var recordingTicket = 0
    private var sessionOriginal: (AVAudioSession.Category, AVAudioSession.Mode, AVAudioSession.CategoryOptions)?
    init(embedded: Bool = false, prepareAudio: @escaping () async throws -> Void, done: @escaping () -> Void) {
        self.embedded = embedded
        self.preparation = prepareAudio; self.done = done
        let audio = RealtimeVoiceAudio()
        self.liveAudio = audio
        let session = RealtimeVoiceSession(port: audio)
        self.liveSession = session
        super.init(nibName: nil, bundle: nil)
        audio.attach(session: session)
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
                #if ODAI_OPENVOICE_DEMO
                let assets = Bundle.main.resourceURL!.appendingPathComponent("OpenVoice", isDirectory: true)
                let renderer = OpenVoiceTimbres(assets: assets, directory: directory, inputs: inputs)
                timbres = renderer
                #endif
                let model = VoiceEffectsViewModel(bridge: bridge, timbres: timbres); self.model = model
                #if ODAI_OPENVOICE_DEMO
                if let example = try? await Task.detached(operation: {
                    try VoiceEffectsWave.store(VoiceEffectsWave.normalize(url: assets.appendingPathComponent("original.wav")),
                                               directory: self.root.appendingPathComponent("inputs"))
                }).value {
                    guard !closed else { try? FileManager.default.removeItem(at: example); return }
                    model.setExample(reference: inputs.register(example))
                }
                #endif
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
        liveAudio.cancelAll()
    }
    @objc private func interrupted(_ notification: Notification) {
        guard let type = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              type == AVAudioSession.InterruptionType.began.rawValue else { return }
        background()
    }
    private func setInputBusy(_ value: Bool, target: InputTarget, recording: Bool = false) {
        guard let model else { return }
        if recording {
            if target == .reference {
                model.setReferenceRecording(value: value)
            } else {
                model.setRecording(value: value)
            }
        } else {
            switch target {
            case .content: model.setImporting(value: value)
            case .reference: model.setReferenceImporting(value: value)
            }
        }
    }

    nonisolated private func isValidReferenceWAV(_ data: Data) -> Bool {
        let bytes = [UInt8](data)
        guard bytes.count >= 44 else { return false }
        let rate = UInt32(bytes[24]) | (UInt32(bytes[25]) << 8) |
            (UInt32(bytes[26]) << 16) | (UInt32(bytes[27]) << 24)
        guard rate > 0 else { return false }
        let seconds = Double((bytes.count - 44) / 2) / Double(rate)
        return seconds >= 0.5 && seconds <= 30
    }

    func selectAudioFile() {
        guard let model, model.state.controlsEnabled, !closed else { return }
        pickerTarget = .content; pickerReferenceMode = model.state.referenceMode
        setInputBusy(true, target: .content)
        pickerIsExport = false
        generation += 1; pickerTicket = generation
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.wav], asCopy: false)
        picker.delegate = self; picker.allowsMultipleSelection = false; activePicker = picker
        present(picker, animated: true)
    }

    func selectReferenceAudioFile() {
        guard let model, model.state.controlsEnabled, !closed else { return }
        pickerTarget = .reference; pickerReferenceMode = true
        setInputBusy(true, target: .reference)
        pickerIsExport = false
        generation += 1; pickerTicket = generation
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.wav], asCopy: false)
        picker.delegate = self; picker.allowsMultipleSelection = false; activePicker = picker
        present(picker, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard controller === activePicker else { return }
        activePicker = nil
        if pickerIsExport { finishExport(); return }
        let target = pickerTarget, isReferenceMode = pickerReferenceMode, ticket = pickerTicket
        guard ticket == generation, let source = urls.first, let model, !closed else { return }
        generation += 1; let installTicket = generation; let directory = root.appendingPathComponent("inputs")
        let scoped = source.startAccessingSecurityScopedResource()
        Task { @MainActor in
            defer {
                if scoped { source.stopAccessingSecurityScopedResource() }
                if installTicket == generation { setInputBusy(false, target: target) }
            }
            do {
                let url: URL = try await Task.detached {
                    let data = try VoiceEffectsWave.normalize(url: source)
                    guard !isReferenceMode || self.isValidReferenceWAV(data) else {
                        throw VoiceEffectsWave.Failure.unsupportedOrInvalidWAV
                    }
                    return try VoiceEffectsWave.store(data, directory: directory)
                }.value
                if closed || installTicket != generation {
                    _ = await Task.detached { try? FileManager.default.removeItem(at: url) }.value
                    return
                }
                await install(url, name: source.lastPathComponent, target: target, isReferenceMode: isReferenceMode)
            } catch { if !closed, installTicket == generation { model.inputFailed(reason: "unsupportedOrInvalidWav") } }
        }
    }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        guard controller === activePicker else { return }
        activePicker = nil
        if pickerIsExport { finishExport() }
        else if pickerTicket == generation { setInputBusy(false, target: pickerTarget) }
    }
    func startRecording() { startCapture(target: .content) }
    func startReferenceRecording() { startCapture(target: .reference) }
    func stopRecording() { stopCapture(discard: false) }
    func stopReferenceRecording() { stopCapture(discard: false) }

    private func startCapture(target: InputTarget) {
        guard let model, model.state.controlsEnabled, !closed,
              UIApplication.shared.applicationState == .active else { return }
        let isReferenceMode = target == .reference || model.state.referenceMode
        setInputBusy(true, target: target)
        generation += 1; let ticket = generation
        recordingTarget = target; recordingReferenceMode = isReferenceMode; recordingTicket = ticket
        AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
            Task { @MainActor in
                guard let self, !self.closed, ticket == self.generation else { return }
                self.setInputBusy(false, target: target)
                guard granted else { model.inputFailed(reason: "microphonePermissionDenied"); return }
                guard UIApplication.shared.applicationState == .active else { return }
                await self.beginRecording(target: target, isReferenceMode: isReferenceMode, ticket: ticket)
            }
        }
    }

    private func beginRecording(target: InputTarget, isReferenceMode: Bool, ticket: Int) async {
        guard let model, !closed, ticket == generation else { return }
        setInputBusy(true, target: target)
        defer { if ticket == generation { setInputBusy(false, target: target) } }
        let directory = root.appendingPathComponent("recordings")
        do {
            try await Task.detached { try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true) }.value
            guard !closed, ticket == generation, UIApplication.shared.applicationState == .active, recorder == nil else { return }
            try acquireSession(recording: true)
            let url = directory.appendingPathComponent("record-\(UUID().uuidString).wav")
            let value = try AVAudioRecorder(url: url, settings: [AVFormatIDKey: kAudioFormatLinearPCM,
                AVSampleRateKey: 16000, AVNumberOfChannelsKey: 1, AVLinearPCMBitDepthKey: 16,
                AVLinearPCMIsFloatKey: false, AVLinearPCMIsBigEndianKey: false])
            value.delegate = self
            recordingURL = url
            guard value.record(forDuration: 30) else { throw VoiceEffectsWave.Failure.unsupportedOrInvalidWAV }
            recorder = value
            recordingTarget = target; recordingReferenceMode = isReferenceMode; recordingTicket = ticket
            discardRecording = false
            setInputBusy(true, target: target, recording: true)
        } catch {
            releaseSession()
            model.inputFailed(reason: "audioRecordingFailed")
            cleanupRecording()
        }
    }
    private func stopCapture(discard: Bool) {
        guard let recorder else { return }
        discardRecording = discardRecording || discard; recorder.stop()
    }
    nonisolated func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        Task { @MainActor [weak self] in self?.finishedRecording(recorder, successfully: flag) }
    }
    private func finishedRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        guard self.recorder === recorder else { return }
        let target = recordingTarget, isReferenceMode = recordingReferenceMode, ticket = recordingTicket
        self.recorder = nil; releaseSession()
        let source = recordingURL; recordingURL = nil
        if !flag && !discardRecording && !closed { model?.inputFailed(reason: "audioRecordingFailed") }
        let discard = discardRecording || closed || !flag
        let directory = root.appendingPathComponent("inputs")
        Task { @MainActor in
            defer { if generation == ticket { setInputBusy(false, target: target, recording: true) } }
            guard let source else { return }
            if closed || discard || generation != ticket {
                _ = await Task.detached { try? FileManager.default.removeItem(at: source) }.value
                return
            }
            do {
                let output: URL = try await Task.detached {
                    defer { try? FileManager.default.removeItem(at: source) }
                    let data = try VoiceEffectsWave.normalize(url: source)
                    guard !isReferenceMode || self.isValidReferenceWAV(data) else {
                        throw VoiceEffectsWave.Failure.unsupportedOrInvalidWAV
                    }
                    return try VoiceEffectsWave.store(data, directory: directory)
                }.value
                if closed || generation != ticket {
                    _ = await Task.detached { try? FileManager.default.removeItem(at: output) }.value
                } else {
                    await install(output, name: "录音.wav", target: target, isReferenceMode: isReferenceMode)
                }
            } catch VoiceEffectsWave.Failure.unsupportedOrInvalidWAV {
                if !closed && generation == ticket { model?.inputFailed(reason: "unsupportedOrInvalidWav") }
            } catch {
                if !closed && generation == ticket { model?.inputFailed(reason: "audioRecordingFailed") }
            }
        }
    }
    nonisolated func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) {
        Task { @MainActor [weak self] in
            guard let self, self.recorder === recorder else { return }
            let ticket = self.recordingTicket
            self.discardRecording = true; recorder.stop()
            self.finishedRecording(recorder, successfully: false)
            if !self.closed, self.generation == ticket { self.model?.inputFailed(reason: "audioRecordingFailed") }
        }
    }
    private func cleanupRecording() {
        if let url = recordingURL { Task.detached { try? FileManager.default.removeItem(at: url) } }
        recordingURL = nil
    }
    func playOriginal() {
        guard let reference = model?.state.inputReference, let lease = try? inputs.acquire(reference: reference) else { return }
        play(lease.url, reference: nil, input: lease, track: reference)
    }
    func playReference() {
        guard let reference = model?.state.referenceReference, let lease = try? inputs.acquire(reference: reference) else { return }
        play(lease.url, reference: nil, input: lease, track: reference)
    }
    func playVoiceAt(reference: String, fraction: Float) {
        let clamped = min(max(fraction, 0), 1)
        if !closed, playingReference == reference, let player = player,
           player.duration.isFinite, player.duration > 0 {
            if clamped >= 1 {
                stopPlayback()
                return
            } else {
                player.currentTime = TimeInterval(clamped) * player.duration
                if !player.isPlaying { _ = player.play() }
            }
            return
        }
        guard !closed, recorder == nil, exportReference == nil else { return }
        guard let model, UIApplication.shared.applicationState == .active else { return }
        if clamped >= 1 { stopPlayback(); return }
        let state = model.state
        let switching = playbackID != nil && !state.importing && !state.referenceImporting &&
            !state.recording && !state.referenceRecording &&
            state.phase != .running && state.phase != .cancelling
        guard switching || state.controlsEnabled else { return }
        stopPlayback()
        if reference == state.inputReference || reference == state.referenceReference {
            guard let lease = try? inputs.acquire(reference: reference) else { return }
            play(lease.url, reference: nil, input: lease, fraction: clamped, track: reference)
        } else if let url = outputURL(reference), model.holdOutput(reference: reference) {
            play(url, reference: reference, input: nil, fraction: clamped, track: reference)
        }
    }
    func playbackFraction(reference: String) -> Float {
        guard playingReference == reference, let player = player,
              player.duration.isFinite, player.duration > 0 else { return -1 }
        return Float(min(max(player.currentTime / player.duration, 0), 1))
    }
    func useExample() {
        guard let model, model.state.controlsEnabled,
              model.state.inputReference != model.state.exampleReference else { return }
        let old = model.state.inputReference
        model.useExample()
        if let old { Task { await inputs.retire(old) } }
    }
    func loadReferenceExample() {
        guard let model, model.state.controlsEnabled, !closed, let example = model.state.exampleReference else { return }
        #if ODAI_OPENVOICE_DEMO
        guard let assets = Bundle.main.resourceURL?.appendingPathComponent("OpenVoice", isDirectory: true) else { return }
        generation += 1; let ticket = generation
        model.setReferenceImporting(value: true)
        Task { @MainActor in
            defer { if ticket == generation { model.setReferenceImporting(value: false) } }
            do {
                let content = try await Task.detached {
                    try VoiceEffectsWave.store(VoiceEffectsWave.normalize(url: assets.appendingPathComponent("reference-content.wav")),
                                               directory: self.root.appendingPathComponent("inputs"))
                }.value
                guard !closed, ticket == generation else {
                    _ = await Task.detached { try? FileManager.default.removeItem(at: content) }.value
                    return
                }
                let newContent = inputs.register(content)
                let oldReference = model.state.referenceReference
                let oldContent = model.state.inputReference
                model.setReferenceMode(enabled: true)
                model.selectReferenceInput(reference: example, name: "内置普通话示例")
                model.selectInput(reference: newContent, name: "参考内容示例")
                for old in [oldReference, oldContent].compactMap({ $0 }) where old != example && old != model.state.exampleReference {
                    Task { await inputs.retire(old) }
                }
            } catch { if !closed { model.inputFailed(reason: "unsupportedOrInvalidWav") } }
        }
        #endif
    }
    func clearVoiceInput(referenceSlot: Bool) {
        guard let model, !closed else { return }
        generation += 1
        if referenceSlot {
            let old = model.state.referenceReference
            model.clearReference()
            if let old, old != model.state.exampleReference { Task { await inputs.retire(old) } }
        } else {
            let old = model.state.inputReference
            let wasReferenceMode = model.state.referenceMode
            model.clearInput()
            if !wasReferenceMode, model.state.inputReference == nil, model.state.exampleReference != nil { model.useExample() }
            if let old, old != model.state.exampleReference { Task { await inputs.retire(old) } }
        }
    }
    func readVoiceWave(reference: String, reply: any VoiceWaveReply) {
        guard let model, !closed else { reply.ready(wave: nil); return }
        var heldOutput = false
        var lease: (any IOSVoiceEffectsInputLease)?
        let url: URL?
        if let output = outputURL(reference), model.holdOutput(reference: reference) {
            heldOutput = true; url = output
        } else if let acquired = try? inputs.acquire(reference: reference) {
            lease = acquired; url = acquired.url
        } else {
            url = nil
        }
        guard let url else { reply.ready(wave: nil); return }
        Task { @MainActor [weak self] in
            let wave: VoiceWaveform?
            do {
                wave = try await Task.detached { VoiceWaveDataKt.decodeVoiceWave(data: try Data(contentsOf: url)) }.value
            } catch { wave = nil }
            if heldOutput { model.endOutputUse(reference: reference) }
            if let lease { await lease.release() }
            let alive = self.map { !$0.closed } ?? false
            reply.ready(wave: alive ? wave : nil)
        }
    }
    private func outputURL(_ reference: String) -> URL? {
        #if ODAI_OPENVOICE_DEMO
        if let url = (timbres as? OpenVoiceTimbres)?.outputURL(reference) { return url }
        #endif
        return bridge?.outputURL(reference: reference)
    }
    func playResult(reference: String) {
        guard let url = outputURL(reference), model?.holdOutput(reference: reference) == true else { return }
        play(url, reference: reference, input: nil)
    }
    private func play(_ url: URL, reference: String?, input: (any IOSVoiceEffectsInputLease)?,
                      fraction: Float = 0, track: String? = nil) {
        stopPlayback()
        guard let model, model.state.controlsEnabled, !closed, UIApplication.shared.applicationState == .active else {
            if let reference { self.model?.endOutputUse(reference: reference) }
            if let input { Task { await input.release() } }; return
        }
        let id = UUID(); playbackID = id; preparingPlayback.insert(id); playbackHolds[id] = (reference, input)
        playingReference = track ?? reference; model.setMediaBusy(value: true)
        Task { @MainActor in
            defer {
                preparingPlayback.remove(id)
                if playbackID != id { releasePlaybackHold(id) }
            }
            do {
                // File parsing happens off MainActor. No read starts after the
                // output hold ends: the hold remains until this preparation drains.
                let data = try await Task.detached { try Data(contentsOf: url) }.value
                guard !closed, UIApplication.shared.applicationState == .active, playbackID == id else {
                    if playbackID == id { stopPlayback() }
                    return
                }
                try startPlayer(data, fraction: fraction)
            } catch { if playbackID == id { stopPlayback(); model.inputFailed(reason: "audioPlaybackFailed") } }
        }
    }
    private func startPlayer(_ data: Data, fraction: Float) throws {
        let value = try AVAudioPlayer(data: data, fileTypeHint: AVFileType.wav.rawValue)
        try acquireSession(recording: false)
        if fraction >= 1 {
            stopPlayback()
            return
        } else if fraction > 0 {
            value.currentTime = TimeInterval(fraction) * value.duration
        }
        player = value; value.delegate = self
        guard value.play() else { throw VoiceEffectsWave.Failure.unsupportedOrInvalidWAV }
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
        guard let model, model.state.controlsEnabled, let url = outputURL(reference), model.holdOutput(reference: reference), !closed else { return }
        exportReference = reference; model.setMediaBusy(value: true); pickerIsExport = true
        generation += 1; pickerTicket = generation
        let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
        picker.delegate = self; activePicker = picker; present(picker, animated: true)
    }
    private func finishExport() {
        if let reference = exportReference { model?.endOutputUse(reference: reference) }
        exportReference = nil; model?.setMediaBusy(value: false)
    }
    private func install(_ url: URL, name: String, target: InputTarget, isReferenceMode: Bool) async {
        let old = target == .content ? model?.state.inputReference : model?.state.referenceReference
        let example = model?.state.exampleReference
        let reference = inputs.register(url)
        if target == .content {
            model?.selectInput(reference: reference, name: name)
        } else {
            model?.setReferenceMode(enabled: true)
            model?.selectReferenceInput(reference: reference, name: name)
        }
        if let old, old != example { await inputs.retire(old) }
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
            (model == nil || (model!.state.controlsEnabled && model!.state.pendingReleases == 0)) &&
            !liveSession.busy
    }
    func leaveEmbeddedPage() {
        generation += 1
        stopPlayback(); stopCapture(discard: true)
        let example = model?.state.exampleReference
        let oldIDs = [model?.state.inputReference, model?.state.referenceReference].compactMap({ $0 })
            .filter({ $0 != example })
        model?.leavePage()
        model?.clearReference()
        model?.clearInput()
        if let example { model?.selectInput(reference: example, name: "内置普通话示例") }
        model?.setImporting(value: false); model?.setReferenceImporting(value: false)
        model?.setRecording(value: false); model?.setReferenceRecording(value: false)
        if exportReference == nil { model?.setMediaBusy(value: false) }
        liveAudio.cancelAll(); liveSession.release()
        Task { @MainActor in
            for reference in oldIDs { await inputs.retire(reference) }
        }
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
        stopPlayback(); stopCapture(discard: true)
        model?.setImporting(value: false); model?.setReferenceImporting(value: false)
        model?.setRecording(value: false); model?.setReferenceRecording(value: false)
        if exportReference == nil { model?.setMediaBusy(value: false) }
        model?.close()
        liveAudio.cancelAll(); liveSession.release()
        // Document picker holds output until its own terminal callback, including
        // cancellation. It must not be released while UIKit is still copying it.
        Task { await inputs.close() }
        NotificationCenter.default.removeObserver(self)
    }
}

#if ODAI_OPENVOICE_DEMO
@MainActor
private final class OpenVoiceTimbres: NSObject, @preconcurrency DemoCoreMLVoiceTimbres {
    private let assets: URL
    private let directory: URL
    private let inputs: VoiceEffectsInputStore
    private var outputs: [String: URL] = [:]

    init(assets: URL, directory: URL, inputs: VoiceEffectsInputStore) {
        self.assets = assets
        self.directory = directory
        self.inputs = inputs
    }

    func render(inputReference: String, key: String, callback: any VoiceEffectsBridgeProcessCallback) {
        render(inputReference: inputReference, key: key, callback: callback, coreML: false)
    }

    func renderCoreML(inputReference: String, key: String, callback: any VoiceEffectsBridgeProcessCallback) {
        render(inputReference: inputReference, key: key, callback: callback, coreML: true)
    }
    func renderReference(referenceInputReference: String, contentInputReference: String,
                         callback: any VoiceEffectsBridgeProcessCallback) {
        guard let referenceLease = try? inputs.acquire(reference: referenceInputReference) else {
            callback.onFailure(failure__: failure("无法读取参考音频"))
            return
        }
        guard let contentLease = try? inputs.acquire(reference: contentInputReference) else {
            Task { await referenceLease.release() }
            callback.onFailure(failure__: failure("无法读取当前音频"))
            return
        }
        let id = UUID().uuidString
        let output = directory.appendingPathComponent("reference-\(id).wav")
        let assets = self.assets, referenceURL = referenceLease.url, contentURL = contentLease.url
        Task {
            do {
                let (rate, frames) = try await Task.detached(priority: .userInitiated) {
                    guard openvoice_render_reference(assets.path, referenceURL.path, contentURL.path, output.path) == 0 else {
                        throw VoiceEffectsWave.Failure.unsupportedOrInvalidWAV
                    }
                    let file = try AVAudioFile(forReading: output)
                    return (Int32(file.fileFormat.sampleRate), file.length)
                }.value
                await referenceLease.release()
                await contentLease.release()
                outputs[id] = output
                callback.onResult(output__: VoiceEffectsBridgeOutput(reference: id, backendId: "openvoice.onnx",
                    sampleRate: rate, frameCount: frames, durationSeconds: Double(frames) / Double(rate)))
            } catch {
                try? FileManager.default.removeItem(at: output)
                await referenceLease.release()
                await contentLease.release()
                callback.onFailure(failure__: failure("参考音色转换失败，请使用 0.5 至 30 秒的单声道语音。"))
            }
        }
    }

    private func render(inputReference: String, key: String, callback: any VoiceEffectsBridgeProcessCallback,
                        coreML: Bool) {
        guard let lease = try? inputs.acquire(reference: inputReference) else {
            callback.onFailure(failure__: failure("无法读取当前音频"))
            return
        }
        let id = UUID().uuidString
        let output = directory.appendingPathComponent("\(key)-\(id).wav")
        let assets = self.assets, source = lease.url
        let profiles = directory.appendingPathComponent("profile-" + id, isDirectory: true)
        Task {
            do {
                let (rate, frames, backend) = try await Task.detached(priority: .userInitiated) {
                    var backend = "openvoice.onnx"
                    if coreML {
                        try FileManager.default.createDirectory(at: profiles, withIntermediateDirectories: false)
                        defer { try? FileManager.default.removeItem(at: profiles) }
                        guard openvoice_render_coreml(assets.path, source.path, key, output.path, profiles.path) == 0 else {
                            throw RenderError.coreML
                        }
                        backend = Self.profileBackend(profiles)
                    } else if openvoice_render(assets.path, source.path, key, output.path) != 0 {
                        throw VoiceEffectsWave.Failure.unsupportedOrInvalidWAV
                    }
                    let file = try AVAudioFile(forReading: output)
                    return (Int32(file.fileFormat.sampleRate), file.length, backend)
                }.value
                await lease.release()
                outputs[id] = output
                callback.onResult(output__: VoiceEffectsBridgeOutput(reference: id, backendId: backend,
                    sampleRate: rate, frameCount: frames, durationSeconds: Double(frames) / Double(rate)))
            } catch {
                try? FileManager.default.removeItem(at: output)
                await lease.release()
                if error is RenderError {
                    callback.onFailure(failure__: VoiceEffectsBridgeFailure(code: .backendUnavailable,
                        reason: "Core ML 转换失败，可切换到 ONNX · CPU 再试。", domain: nil, platformCode: nil))
                } else {
                    callback.onFailure(failure__: failure("音色转换失败，请使用 0.05 至 30 秒的单声道语音。"))
                }
            }
        }
    }

    private enum RenderError: Error { case coreML }

    private nonisolated static func profileBackend(_ directory: URL) -> String {
        let files = (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)) ?? []
        func stage(_ name: String) -> String {
            guard let file = files.first(where: { $0.lastPathComponent.hasPrefix(name + "_") && $0.pathExtension == "json" }),
                  let data = try? Data(contentsOf: file),
                  let events = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] else { return "unknown" }
            let providers = Set(events.compactMap { event -> String? in
                guard event["cat"] as? String == "Node", (event["name"] as? String)?.hasSuffix("_kernel_time") == true
                else { return nil }
                return (event["args"] as? [String: Any])?["provider"] as? String
            })
            switch providers {
            case ["CoreMLExecutionProvider"]: return "coreml"
            case ["CPUExecutionProvider"]: return "cpu"
            case ["CoreMLExecutionProvider", "CPUExecutionProvider"]: return "mixed"
            default: return "unknown"
            }
        }
        return "openvoice.\(stage("encoder")).\(stage("converter"))"
    }

    func outputURL(_ reference: String) -> URL? { outputs[reference] }
    func ownsOutput(reference: String) -> Bool { outputs[reference] != nil }
    func releaseOutput(reference: String, callback_ callback: any VoiceEffectsBridgeReleaseCallback) {
        do {
            if let url = outputs[reference], FileManager.default.fileExists(atPath: url.path) {
                try FileManager.default.removeItem(at: url)
            }
            outputs.removeValue(forKey: reference)
            callback.onReleased()
        } catch { callback.onFailure(failure__: failure("无法清理音色文件")) }
    }
    private func failure(_ reason: String) -> VoiceEffectsBridgeFailure {
        VoiceEffectsBridgeFailure(code: .invalidInput, reason: reason, domain: nil, platformCode: nil)
    }
}
#endif
