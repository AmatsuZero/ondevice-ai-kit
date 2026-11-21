import Foundation

/// Reuses the existing private-copy/ref-count store; importAudio itself accepts any regular file.
@MainActor
final class VideoInputStore: IOSVideoInputProvider {
    private let files: SpeechInputStore
    init(directory: URL = FileManager.default.temporaryDirectory
        .appendingPathComponent("odai-video-input-" + UUID().uuidString, isDirectory: true)) throws {
        files = try SpeechInputStore(directory: directory)
    }
    func importVideo(from source: URL) throws -> String { try files.importAudio(from: source) }
    func acquire(reference: String) throws -> any IOSVideoInputLease { Lease(try files.acquire(reference: reference)) }
    func remove(reference: String) { files.remove(reference: reference) }
    func close() { files.close() }
    private final class Lease: IOSVideoInputLease {
        private let underlying: any IOSSpeechAudioInputLease
        var url: URL { underlying.url }
        init(_ underlying: any IOSSpeechAudioInputLease) { self.underlying = underlying }
        func release() { underlying.release() }
    }
}
