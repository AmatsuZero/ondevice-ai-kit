import Foundation
import XCTest
import ODAIKitDemo

@MainActor final class VideoHostTests: XCTestCase {
    func testPickerCancelAndLateReplyDoNotReplaceInput() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
        defer { try? FileManager.default.removeItem(at: directory) }
        let source = directory.appendingPathComponent("fixture.mov")
        try Data([1, 2, 3]).write(to: source)
        var reply: ((URL?) -> Void)?
        let store = try VideoInputStore(directory: directory.appendingPathComponent("copies"))
        let host = VideoHost(bridge: UnavailableVideoBridge(), store: store, resolveOutput: { _ in nil },
                             picker: { reply = $0 }, presentVideo: { _, done in done() }, shareVideo: { _, done in done() })
        host.selectVideoFile(); reply?(source)
        let selected = host.model.state.inputReference
        XCTAssertNotNil(selected)
        host.selectVideoFile(); reply?(nil)
        XCTAssertEqual(host.model.state.inputReference, selected)
        host.selectVideoFile(); host.background(); reply?(source)
        XCTAssertEqual(host.model.state.inputReference, selected)
        host.close()
    }

    func testReplacedInputSurvivesWhileLeaseIsActive() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
        defer { try? FileManager.default.removeItem(at: directory) }
        let source = directory.appendingPathComponent("fixture.mov")
        try Data([1, 2, 3]).write(to: source)
        let store = try VideoInputStore(directory: directory.appendingPathComponent("copies"))
        let token = try store.importVideo(from: source)
        let lease = try store.acquire(reference: token)
        store.remove(reference: token); store.close()
        XCTAssertTrue(FileManager.default.fileExists(atPath: lease.url.path))
        XCTAssertThrowsError(try store.acquire(reference: token))
        lease.release(); lease.release()
        XCTAssertFalse(FileManager.default.fileExists(atPath: lease.url.path))
        XCTAssertEqual(try Data(contentsOf: source), Data([1, 2, 3]))
    }

    func testPresentationCopyOutlivesOriginalAndIsRemovedWhenDismissed() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
        defer { try? FileManager.default.removeItem(at: directory) }
        let source = directory.appendingPathComponent("result.mov")
        try Data([4, 5, 6]).write(to: source)
        var presented: URL?; var dismiss: (() -> Void)?
        let host = VideoHost(bridge: UnavailableVideoBridge(), store: try VideoInputStore(directory: directory.appendingPathComponent("copies")),
            resolveOutput: { _ in source }, picker: { _ in },
            presentVideo: { url, done in presented = url; dismiss = done }, shareVideo: { _, done in done() })
        host.openVideo(reference: "opaque")
        let copy = try XCTUnwrap(presented)
        XCTAssertNotEqual(copy, source)
        try FileManager.default.removeItem(at: source)
        host.background(); host.close()
        XCTAssertEqual(try Data(contentsOf: copy), Data([4, 5, 6]))
        dismiss?(); dismiss?()
        XCTAssertFalse(FileManager.default.fileExists(atPath: copy.path))
    }
}
