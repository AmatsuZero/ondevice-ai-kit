import Foundation
import Darwin

/// Internal syscall seam for deterministic failure tests. No global hook/state.
struct VoiceEffectsFileIO: Sendable {
    var makeDirectory: @Sendable (URL) throws -> Void
    var open: @Sendable (URL) throws -> Int32
    var write: @Sendable (Int32, Data) throws -> Void
    var close: @Sendable (Int32) throws -> Void
    var publish: @Sendable (URL, URL) throws -> Void
    var unlink: @Sendable (URL) throws -> Void
    var removeDirectory: @Sendable (URL) throws -> Void

    static func failure() -> VoiceEffectsError {
        .platform(domain: NSPOSIXErrorDomain, code: Int(errno))
    }
    static let live = VoiceEffectsFileIO(
        makeDirectory: { url in
            guard mkdir(url.path, 0o700) == 0 else { throw failure() }
        },
        open: { url in
            let fd = Darwin.open(url.path, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0o600)
            guard fd >= 0 else { throw failure() }
            return fd
        },
        write: { fd, data in
            try data.withUnsafeBytes { raw in
                guard let start = raw.baseAddress else { return }
                var offset = 0
                while offset < raw.count {
                    let count = Darwin.write(fd, start.advanced(by: offset), min(16384, raw.count - offset))
                    if count < 0 && errno == EINTR { continue }
                    guard count > 0 else { throw failure() }
                    offset += count
                }
            }
        },
        close: { fd in
            // Never retry close on the same integer: it may already have been reused.
            guard Darwin.close(fd) == 0 else { throw failure() }
        },
        publish: { source, destination in
            // link is one atomic create-if-absent operation on the same filesystem.
            // EEXIST leaves destination untouched. No check-then-rename sequence.
            guard Darwin.link(source.path, destination.path) == 0 else { throw failure() }
        },
        unlink: { url in
            guard Darwin.unlink(url.path) == 0 || errno == ENOENT else { throw failure() }
        },
        removeDirectory: { url in
            guard rmdir(url.path) == 0 || errno == ENOENT else { throw failure() }
        }
    )
}

/// Mutated by one worker at a time; actor transfers it only after joining that worker.
/// Deletes exact files it created, never recursively removes a directory or caller input.
final class VoiceEffectsWorkspace: @unchecked Sendable {
    let directory: URL
    let temporary: URL
    let final: URL
    let io: VoiceEffectsFileIO
    private var ownsDirectory = false
    private var ownsTemporary = false
    private var ownsFinal = false
    private var foreignCollision = false

    init(root: URL, io: VoiceEffectsFileIO) {
        self.io = io
        let name = "odai-voice-\(UUID().uuidString)"
        let directory = root.appendingPathComponent(name, isDirectory: true)
        self.directory = directory
        temporary = directory.appendingPathComponent("partial.wav")
        // Deliver only a file, with no directory-finalization work left for release.
        final = root.appendingPathComponent(name + ".wav")
    }

    func writeAndPublish(_ bytes: Data, check: () throws -> Void) throws {
        try check()
        try io.makeDirectory(directory); ownsDirectory = true
        let fd: Int32
        do { fd = try io.open(temporary); ownsTemporary = true }
        catch {
            if error as? VoiceEffectsError == .platform(domain: NSPOSIXErrorDomain, code: Int(EEXIST)) {
                foreignCollision = true
            }
            throw error
        }
        var writeFailure: Error?
        do { try io.write(fd, bytes) } catch { writeFailure = error }
        // Close always executes, even on write/cancellation failure, before publication.
        try io.close(fd)
        if let writeFailure { throw writeFailure }
        try check()
        do {
            try io.publish(temporary, final); ownsFinal = true
        } catch {
            if error as? VoiceEffectsError == .platform(domain: NSPOSIXErrorDomain, code: Int(EEXIST)) {
                foreignCollision = true
            }
            throw error
        }
        try io.unlink(temporary); ownsTemporary = false
        try io.removeDirectory(directory); ownsDirectory = false
        try check()
    }

    func cleanup() throws {
        // Keep ownership flags until each deletion actually succeeds; retry is safe.
        if ownsFinal { try io.unlink(final); ownsFinal = false }
        if ownsTemporary { try io.unlink(temporary); ownsTemporary = false }
        if ownsDirectory {
            do { try io.removeDirectory(directory); ownsDirectory = false }
            catch {
                // A pre-existing competitor belongs to its creator. Keep it and its
                // directory, while discarding this operation's ownership of the directory.
                if foreignCollision,
                   error as? VoiceEffectsError == .platform(domain: NSPOSIXErrorDomain, code: Int(ENOTEMPTY)) {
                    ownsDirectory = false
                } else { throw error }
            }
        }
    }
}

actor VoiceEffectsOutputLease {
    private let workspace: VoiceEffectsWorkspace
    private var released = false
    private var releasing: Task<Void, Error>?
    init(_ workspace: VoiceEffectsWorkspace) { self.workspace = workspace }
    func release() async throws {
        if released { return }
        if let releasing { try await releasing.value; return }
        let workspace = self.workspace
        let task = Task.detached { try workspace.cleanup() }
        releasing = task
        do {
            try await task.value
            released = true; releasing = nil
        } catch {
            releasing = nil
            throw voiceEffectsSanitize(error)
        }
    }
}
