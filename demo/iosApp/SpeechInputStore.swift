import Foundation
import Darwin

/// Owns only private copies; selected references and native operation leases are independent.
@MainActor
final class SpeechInputStore: IOSSpeechAudioInputProvider {
    private struct Entry {
        let url: URL
        var selected = true
        var leases = 0
    }
    private let directory: URL
    private var entries: [String: Entry] = [:]
    private var closed = false

    /// The directory must be fresh: another store's files are never adopted or removed.
    init(directory: URL = FileManager.default.temporaryDirectory
        .appendingPathComponent("odai-speech-" + UUID().uuidString, isDirectory: true)) throws {
        self.directory = directory
        try FileManager.default.createDirectory(at: directory.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
    }

    func importAudio(from source: URL) throws -> String {
        guard !closed, source.isFileURL else { throw InputError.invalidReference }
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }
        let values = try source.resourceValues(forKeys: [.isRegularFileKey])
        guard values.isRegularFile == true else { throw InputError.invalidReference }
        let token = UUID().uuidString.lowercased()
        let copy = directory.appendingPathComponent(token).appendingPathExtension(source.pathExtension)
        do {
            try FileManager.default.copyItem(at: source, to: copy)
        } catch {
            try? FileManager.default.removeItem(at: copy)
            throw error
        }
        entries[token] = Entry(url: copy)
        return token
    }

    func acquire(reference: String) throws -> any IOSSpeechAudioInputLease {
        guard !closed, var entry = entries[reference], entry.selected else { throw InputError.invalidReference }
        entry.leases += 1
        entries[reference] = entry
        return Lease(url: entry.url) { self.release(reference) }
    }

    func remove(reference: String) {
        guard entries[reference] != nil else { return }
        entries[reference]?.selected = false
        collect(reference)
    }

    func close() {
        closed = true
        for reference in Array(entries.keys) { remove(reference: reference) }
        removeEmptyDirectory()
    }

    private func release(_ reference: String) {
        guard entries[reference] != nil else { return }
        entries[reference]?.leases -= 1
        collect(reference)
    }

    private func collect(_ reference: String) {
        guard let entry = entries[reference], !entry.selected, entry.leases == 0 else { return }
        // Retain failed deletions for a subsequent close() retry.
        do { try FileManager.default.removeItem(at: entry.url); entries.removeValue(forKey: reference) }
        catch { return }
        removeEmptyDirectory()
    }

    private func removeEmptyDirectory() {
        if closed && entries.isEmpty {
            // Atomic, nonrecursive removal: never delete a file this store does not own.
            directory.withUnsafeFileSystemRepresentation { path in
                if let path { _ = rmdir(path) }
            }
        }
    }

    private enum InputError: Error { case invalidReference }

    private final class Lease: IOSSpeechAudioInputLease {
        let url: URL
        private var releaseAction: (() -> Void)?
        init(url: URL, release: @escaping () -> Void) { self.url = url; releaseAction = release }
        func release() {
            let action = releaseAction
            releaseAction = nil
            action?()
        }
    }
}
