import Foundation
import XCTest
@testable import ODAIKitAppleVideo
import ODAIKitVideo

final class AppleVideoOutputLeaseTests: XCTestCase {
    private let info = try! VideoOutputInfo(width: 1280, height: 720, frameCount: 48, durationSeconds: 2, audioTrackCount: 0)

    func testUniqueWorkspacesAndConcurrentReleasePreserveSiblingAndInput() async throws {
        let root = try rootDirectory()
        defer { try? FileManager.default.removeItem(at: root) }
        let input = root.appendingPathComponent("input.mp4")
        try Data([1, 2, 3]).write(to: input)
        let first = try AppleVideoOutputWorkspace(root: root)
        let second = try AppleVideoOutputWorkspace(root: root)
        XCTAssertNotEqual(first.directory, second.directory)
        try Data([4]).write(to: first.file)
        try Data([5]).write(to: second.file)
        let lease = AppleVideoOutputLease(workspace: first, info: info)
        XCTAssertEqual(lease.file, first.file)
        async let a: Void = lease.release()
        async let b: Void = lease.release()
        try await a; try await b
        XCTAssertFalse(FileManager.default.fileExists(atPath: first.directory.path))
        XCTAssertEqual(try Data(contentsOf: second.file), Data([5]))
        XCTAssertEqual(try Data(contentsOf: input), Data([1, 2, 3]))
        try await lease.release()
        try second.remove()
    }

    func testAlreadyMissingWorkspaceReleaseIsIdempotent() async throws {
        let root = try rootDirectory()
        defer { try? FileManager.default.removeItem(at: root) }
        let workspace = try AppleVideoOutputWorkspace(root: root)
        let lease = AppleVideoOutputLease(workspace: workspace, info: info)
        try workspace.remove()
        try await lease.release()
        try await lease.release()
    }

    func testCleanupFailureIsThrownAndCanBeRetried() async throws {
        let root = try rootDirectory()
        defer {
            try? FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: root.path)
            try? FileManager.default.removeItem(at: root)
        }
        let workspace = try AppleVideoOutputWorkspace(root: root)
        try Data([8]).write(to: workspace.file)
        let lease = AppleVideoOutputLease(workspace: workspace, info: info)
        try FileManager.default.setAttributes([.posixPermissions: 0o500], ofItemAtPath: root.path)
        do { try await lease.release(); XCTFail("non-writable parent must not report successful release") }
        catch { /* The lease remains responsible for retrying cleanup. */ }
        try FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: root.path)
        try await lease.release()
        XCTAssertFalse(FileManager.default.fileExists(atPath: workspace.directory.path))
    }

    private func rootDirectory() throws -> URL {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("odai-video-test-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
        return root
    }
}
