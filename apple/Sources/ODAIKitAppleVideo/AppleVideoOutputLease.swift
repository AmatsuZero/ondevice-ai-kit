import Foundation
import ODAIKitVideo

/// Owns only the unique directory it creates, never the supplied root or input.
struct AppleVideoOutputWorkspace: Sendable {
    let directory: URL
    var file: URL { directory.appendingPathComponent("output.mov") }

    init(root: URL) throws {
        guard root.isFileURL else { throw VideoClientError.invalidInput("workspace root must be local") }
        directory = root.appendingPathComponent("odai-video-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false,
                                                attributes: [.posixPermissions: 0o700])
    }

    func remove() throws {
        do { try FileManager.default.removeItem(at: directory) }
        catch let error as CocoaError where error.code == .fileNoSuchFile { return }
    }
}

/// The caller owns a delivered lease. Close never releases it on the caller's behalf.
actor AppleVideoOutputLease: VideoOutputLease {
    nonisolated let file: URL
    nonisolated let info: VideoOutputInfo
    private let workspace: AppleVideoOutputWorkspace
    private var released = false

    init(workspace: AppleVideoOutputWorkspace, info: VideoOutputInfo) {
        self.workspace = workspace
        self.file = workspace.file
        self.info = info
    }

    func release() throws {
        guard !released else { return }
        try workspace.remove()
        released = true
    }
}
