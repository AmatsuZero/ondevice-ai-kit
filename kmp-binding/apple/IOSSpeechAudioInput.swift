import Foundation

/// File URLs stay in the native host. A lease outlives selection and caller cancellation.
@MainActor
public protocol IOSSpeechAudioInputLease: AnyObject {
    var url: URL { get }
    func release()
}

@MainActor
public protocol IOSSpeechAudioInputProvider: AnyObject {
    func acquire(reference: String) throws -> any IOSSpeechAudioInputLease
}
