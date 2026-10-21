import AVFAudio
import ODAIKitCore
import ODAIKitAppleSpeech

struct DemoSpeechAudioSessionConfiguration: Equatable {
    let category: String
    let mode: String
    let options: UInt
}

@MainActor
protocol DemoSpeechAudioSessionControl: AnyObject {
    var configuration: DemoSpeechAudioSessionConfiguration { get }
    func configure(_ configuration: DemoSpeechAudioSessionConfiguration) throws
    func setActive(_ active: Bool) throws
}

/// One owner for the Demo's microphone session. The bridge admits only one
/// audio operation, and releases this lease after capture/native drain.
@MainActor
final class DemoSpeechAudioSessionProvider: AppleSpeechAudioSessionProvider {
    private let session: any DemoSpeechAudioSessionControl
    private var leased = false

    convenience init() { self.init(session: SystemDemoSpeechAudioSession()) }
    init(session: any DemoSpeechAudioSessionControl) { self.session = session }

    func acquire() async throws -> any AppleSpeechAudioSessionLease {
        guard !leased else { throw KitError.busy }
        leased = true
        let original = session.configuration
        var activationAttempted = false
        do {
            try session.configure(.init(category: AVAudioSession.Category.record.rawValue,
                mode: AVAudioSession.Mode.measurement.rawValue, options: 0))
            activationAttempted = true
            try session.setActive(true)
        } catch {
            if activationAttempted { try? session.setActive(false) }
            try? session.configure(original)
            leased = false
            throw error
        }
        return DemoSpeechAudioSessionLease { [self] in
            defer { leased = false }
            var failure: Error?
            do { try session.setActive(false) } catch { failure = error }
            do { try session.configure(original) } catch { if failure == nil { failure = error } }
            if let failure { throw failure }
        }
    }
}

@MainActor
private final class DemoSpeechAudioSessionLease: AppleSpeechAudioSessionLease {
    private var releaseOperation: (() throws -> Void)?
    init(release: @escaping () throws -> Void) { releaseOperation = release }
    func release() async throws {
        let operation = releaseOperation
        releaseOperation = nil
        try operation?()
    }
}

@MainActor
private final class SystemDemoSpeechAudioSession: DemoSpeechAudioSessionControl {
    private let session = AVAudioSession.sharedInstance()
    var configuration: DemoSpeechAudioSessionConfiguration {
        .init(category: session.category.rawValue, mode: session.mode.rawValue, options: session.categoryOptions.rawValue)
    }
    func configure(_ configuration: DemoSpeechAudioSessionConfiguration) throws {
        try session.setCategory(.init(rawValue: configuration.category), mode: .init(rawValue: configuration.mode),
            options: .init(rawValue: configuration.options))
    }
    func setActive(_ active: Bool) throws {
        try session.setActive(active, options: active ? [] : [.notifyOthersOnDeactivation])
    }
}
