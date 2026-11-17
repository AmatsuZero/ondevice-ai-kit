import Foundation

@MainActor final class VoiceEffectsInputStore: IOSVoiceEffectsInputProvider {
    private final class Entry {
        let url: URL; var readers = 0; var retired = false
        init(_ url: URL) { self.url = url }
    }
    private var entries: [String: Entry] = [:]
    func register(_ url: URL) -> String {
        let reference = "input-\(UUID().uuidString)"; entries[reference] = Entry(url); return reference
    }
    func url(_ reference: String?) -> URL? { reference.flatMap { entries[$0]?.url } }
    func acquire(reference: String) throws -> any IOSVoiceEffectsInputLease {
        guard let entry = entries[reference], !entry.retired else { throw VoiceEffectsWave.Failure.unsupportedOrInvalidWAV }
        entry.readers += 1
        return Lease(url: entry.url) { [self] in
            entry.readers -= 1; await cleanup(reference)
        }
    }
    func retire(_ reference: String?) async {
        guard let reference else { return }
        entries[reference]?.retired = true; await cleanup(reference)
    }
    func close() async { for reference in Array(entries.keys) { await retire(reference) } }
    private func cleanup(_ reference: String) async {
        guard let entry = entries[reference], entry.retired, entry.readers == 0 else { return }
        let url = entry.url
        let removed = await Task.detached {
            do { try FileManager.default.removeItem(at: url); return true }
            catch { return !FileManager.default.fileExists(atPath: url.path) }
        }.value
        if removed, entries[reference] === entry { entries.removeValue(forKey: reference) }
    }
    private final class Lease: IOSVoiceEffectsInputLease {
        let url: URL
        var done: (() async -> Void)?
        init(url: URL, done: @escaping () async -> Void) { self.url = url; self.done = done }
        func release() async { let action = done; done = nil; await action?() }
    }
}
