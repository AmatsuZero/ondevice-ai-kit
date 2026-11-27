import UIKit
import AVFAudio
import AVFoundation
@preconcurrency import ODAIKitDemo

@MainActor
final class RealtimeVoiceAudio: NSObject, @preconcurrency LiveVoicePort {
    private weak var session: RealtimeVoiceSession?
    private var engine: OpaquePointer?
    private var generation: UInt64 = 0
    private var activeID: Int64?
    private var originalSession: (AVAudioSession.Category, AVAudioSession.Mode, AVAudioSession.CategoryOptions)?
    private var expectedOutputs: Set<String> = []
    private var activeDestination: LiveDestination = .headphones
    private var started = false
    private var transitioning = false
    private var guardTimer: Timer?
    private var observers: [NSObjectProtocol] = []

    deinit {
        observers.forEach { NotificationCenter.default.removeObserver($0) }
        observers.removeAll()
        guardTimer?.invalidate()
        guardTimer = nil
    }

    func attach(session: RealtimeVoiceSession) {
        self.session = session
        installObservers()
    }

    // MARK: - LiveVoicePort
    func start(id: Int64, role: LiveRole, destination: LiveDestination) {
        guard activeID == nil else {
            session?.ended(id: id, message: "上一次会话尚未结束")
            return
        }
        generation += 1
        let ticket = generation
        activeID = id

        let aSession = AVAudioSession.sharedInstance()
        if aSession.recordPermission == .granted {
            begin(id: id, role: role, destination: destination, ticket: ticket)
        } else if aSession.recordPermission == .undetermined {
            aSession.requestRecordPermission { [weak self] granted in
                Task { @MainActor [weak self] in
                    guard let self, self.generation == ticket, self.activeID == id else { return }
                    self.activeID = nil
                    self.session?.ended(id: id, message: granted ? "麦克风权限已开启，请再点一次开始" : "麦克风权限被拒绝")
                }
            }
        } else {
            activeID = nil
            session?.ended(id: id, message: "麦克风权限被拒绝")
        }
    }

    func stop(id: Int64) { finish(id: id, message: nil) }
    func refreshRoute() { evaluateRoute() }
    func cancelAll() {
        generation += 1
        if let id = activeID { finish(id: id, message: "已停止") }
    }

    // MARK: - setup (synchronous on main; DSP stays on the AudioUnit RT thread)
    private func begin(id: Int64, role: LiveRole, destination: LiveDestination, ticket: UInt64) {
        guard UIApplication.shared.applicationState == .active, !started else {
            fail(id: id, message: "暂时无法开始"); return
        }
        let aSession = AVAudioSession.sharedInstance()
        let beforePorts = currentOutputs()
        guard classify(beforePorts, for: destination, beforeSelection: true) else {
            fail(id: id, message: routeMessage(for: destination)); return
        }

        originalSession = (aSession.category, aSession.mode, aSession.categoryOptions)
        transitioning = true
        let speaker = destination == LiveDestination.builtinSpeaker
        let mode: AVAudioSession.Mode = speaker ? .voiceChat : .default
        var options: AVAudioSession.CategoryOptions = speaker ? [.defaultToSpeaker] : []
        // Headphones stay on the existing (wired) default route; only opt into
        // HFP when the connected device is known to expose a Bluetooth HFP port.
        if !speaker, beforePorts.contains(where: { $0.portType == .bluetoothHFP }) {
            options.insert(.allowBluetooth)
        }
        do {
            try aSession.setCategory(.playAndRecord, mode: mode, options: options)
            try aSession.setActive(true)
        } catch {
            transitioning = false
            restoreSession()
            fail(id: id, message: "音频设置失败，请关闭其他录音应用后重试")
            return
        }

        let afterPorts = currentOutputs()
        let beforeUIDs = Set(beforePorts.map { $0.uid })
        let afterUIDs = Set(afterPorts.map { $0.uid })
        let intentionalReceiverToSpeaker =
            destination == LiveDestination.builtinSpeaker && beforePorts.count == 1 && beforePorts.first?.portType == .builtInReceiver
        guard classify(afterPorts, for: destination, beforeSelection: false),
              afterUIDs == beforeUIDs || intentionalReceiverToSpeaker else {
            transitioning = false
            restoreSession()
            fail(id: id, message: routeMessage(for: destination))
            return
        }
        expectedOutputs = afterUIDs
        transitioning = false

        // Actual hardware sample rate, not max().
        let rate = Int32(aSession.sampleRate)
        let r = roleValue(role)
        let d = destinationValue(destination)
        guard let created = rvaudio_create(rate, r, d) else {
            restoreSession()
            fail(id: id, message: "音频引擎失败")
            return
        }
        engine = created
        rvaudio_set_muted(created, 1)
        if rvaudio_start(created) != 0 {
            rvaudio_set_muted(created, 1)
            rvaudio_destroy(created)
            engine = nil
            restoreSession()
            fail(id: id, message: "音频启动失败")
            return
        }
        started = true
        // Silent verification window: endpoint must still be the confirmed one.
        guard outputUIDs() == expectedOutputs, classify(currentOutputs(), for: destination, beforeSelection: false) else {
            finish(id: id, message: routeMessage(for: destination))
            return
        }
        rvaudio_set_muted(created, 0)
        startGuardTimer()
        session?.ready(id: id)
    }

    private func finish(id: Int64, message: String?) {
        guard activeID == id else { return }
        generation += 1
        activeID = nil
        started = false
        transitioning = false
        stopGuardTimer()
        disposeEngine()
        restoreSession()
        session?.ended(id: id, message: message ?? "已停止")
        evaluateRoute()
    }

    private func fail(id: Int64, message: String) {
        guard activeID == id else { return }
        activeID = nil
        started = false
        stopGuardTimer()
        session?.ended(id: id, message: message)
    }

    private func disposeEngine() {
        guard let e = engine else { return }
        rvaudio_set_muted(e, 1)
        rvaudio_destroy(e)
        engine = nil
    }

    // MARK: - route reporting / monitoring
    private func evaluateRoute() {
        if started, outputUIDs() != expectedOutputs {
            finish(id: activeID ?? 0, message: "音频输出已变化，已停止")
            return
        }
        let ports = currentOutputs()
        let current: LiveDestination?
        if ports.count == 1, let port = ports.first {
            if isHeadphonePort(port) {
                current = LiveDestination.headphones
            } else if port.portType == .builtInSpeaker || port.portType == .builtInReceiver {
                current = LiveDestination.builtinSpeaker
            } else {
                current = nil
            }
        } else {
            current = nil
        }
        activeDestination = current ?? activeDestination
        session?.route(destination: current)
    }

    private func onRouteChange(_ note: Notification) {
        guard !transitioning else { return }
        if !started {
            evaluateRoute()
            return
        }
        // A real device change during a session stops regardless of UID,
        // including a return to the original endpoint after a transient switch.
        let raw = note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt
        let reason = raw.flatMap { AVAudioSession.RouteChangeReason(rawValue: $0) }
        if reason == .oldDeviceUnavailable || reason == .newDeviceAvailable {
            finish(id: activeID ?? 0, message: "音频输出已变化，已停止")
            return
        }
        // Initial startup category churn keeps the endpoint UID identical: ignore it.
        if reason == .categoryChange || outputUIDs() == expectedOutputs { return }
        finish(id: activeID ?? 0, message: "音频输出已变化，已停止")
    }

    private func onInterruption(_ note: Notification) {
        guard let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: raw), type == .began else { return }
        cancelAll()
    }

    private func installObservers() {
        removeObservers()
        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] note in
            Task { @MainActor [weak self] in self?.onRouteChange(note) }
        })
        observers.append(center.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            Task { @MainActor [weak self] in self?.onInterruption(note) }
        })
        observers.append(center.addObserver(forName: UIApplication.willResignActiveNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor [weak self] in self?.cancelAll() }
        })
    }

    private func removeObservers() {
        observers.forEach { NotificationCenter.default.removeObserver($0) }
        observers.removeAll()
    }

    // MARK: - guard polling
    private func startGuardTimer() {
        stopGuardTimer()
        let timer = Timer(timeInterval: 0.1, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, self.started, let e = self.engine else { return }
                if rvaudio_render_error(e) != 0 {
                    self.finish(id: self.activeID ?? 0, message: "音频渲染出错，已停止")
                } else if rvaudio_guard_trips(e) > 0 {
                    self.finish(id: self.activeID ?? 0, message: "检测到扬声器回授，已停止")
                }
            }
        }
        RunLoop.main.add(timer, forMode: .common)
        guardTimer = timer
    }

    private func stopGuardTimer() {
        guardTimer?.invalidate()
        guardTimer = nil
    }

    // MARK: - helpers
    private func currentOutputs() -> [AVAudioSessionPortDescription] {
        AVAudioSession.sharedInstance().currentRoute.outputs
    }
    private func outputUIDs() -> Set<String> { Set(currentOutputs().map { $0.uid }) }

    private func isHeadsetName(_ name: String) -> Bool {
        let lower = name.lowercased()
        return ["headphone", "headset", "earphone", "earbud", "airpods", "耳机", "耳麦"].contains { lower.contains($0) }
    }
    private func isHeadphonePort(_ port: AVAudioSessionPortDescription) -> Bool {
        if port.portType == .headphones { return true }
        if port.portType == .bluetoothHFP || port.portType == .bluetoothA2DP ||
           port.portType == .bluetoothLE || port.portType == .usbAudio {
            return isHeadsetName(port.portName)
        }
        return false
    }
    private func classify(_ ports: [AVAudioSessionPortDescription], for destination: LiveDestination, beforeSelection: Bool) -> Bool {
        guard ports.count == 1, let port = ports.first else { return false }
        if destination == LiveDestination.headphones { return isHeadphonePort(port) }
        if port.portType == .builtInSpeaker { return true }
        // Before selection the receiver is acceptable: .defaultToSpeaker will move it to the speaker.
        return beforeSelection && port.portType == .builtInReceiver
    }
    private func routeMessage(for destination: LiveDestination) -> String {
        if destination == LiveDestination.headphones { return "请连接耳机后重试" }
        return "请先断开耳机，再用内置扬声器重试"
    }
    private func restoreSession() {
        guard let original = originalSession else { return }
        let a = AVAudioSession.sharedInstance()
        try? a.setActive(false, options: .notifyOthersOnDeactivation)
        try? a.setCategory(original.0, mode: original.1, options: original.2)
        originalSession = nil
    }
    private func roleValue(_ role: LiveRole) -> Int32 {
        if role == LiveRole.announcer { return 2 }
        if role == LiveRole.girl { return 1 }
        return 0
    }
    private func destinationValue(_ d: LiveDestination) -> Int32 {
        if d == LiveDestination.builtinSpeaker { return 1 }
        return 0
    }
}
