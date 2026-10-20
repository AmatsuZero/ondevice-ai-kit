/// A host-owned audio-session lease for one microphone recognition operation.
/// Implementations must make release idempotent and restore only state they own.
public protocol AppleSpeechAudioSessionLease: Sendable {
    func release() async throws
}

/// The host explicitly coordinates App-wide audio-session ownership.
public protocol AppleSpeechAudioSessionProvider: Sendable {
    func acquire() async throws -> any AppleSpeechAudioSessionLease
}
