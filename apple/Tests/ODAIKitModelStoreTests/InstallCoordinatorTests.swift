import XCTest
@testable import ODAIKitModelStore
actor TestSignal {
    var signalled = false; var waiters: [CheckedContinuation<Void,Never>] = []
    func signal() { signalled = true; let list = waiters; waiters = []; list.forEach { $0.resume() } }
    func wait() async { if !signalled { await withCheckedContinuation { waiters.append($0) } } }
}
actor FixtureTransport: ModelDownloadTransport {
    let data: Data; let release = TestSignal(); let started = TestSignal()
    private(set) var count = 0
    init(data: Data) { self.data = data }
    func download(file: ModelFile, to destination: URL, progress: @escaping @Sendable (Int64) -> Void) async throws {
        count += 1; await started.signal(); await release.wait(); try Task.checkCancellation()
        try data.write(to: destination); progress(Int64(data.count))
    }
}
actor CancellableTransport: ModelDownloadTransport {
    let started = TestSignal(); let cancelled = TestSignal(); let release = TestSignal()
    func download(file: ModelFile, to destination: URL, progress: @escaping @Sendable (Int64) -> Void) async throws {
        try await withTaskCancellationHandler(operation: {
            await started.signal(); await release.wait(); try Task.checkCancellation()
        }, onCancel: { Task { await self.cancelled.signal() } })
    }
}
final class InstallCoordinatorTests: XCTestCase {
    func testCleanupFailureAfterCommitIsReportedWithOwnedDirectory() async throws {
        let (root, source, manifest) = try ModelStoreTests().environment()
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try ModelStore(root: root.appendingPathComponent("store"), compatibility: .fixture)
        let transport = FixtureTransport(data: try Data(contentsOf: source.appendingPathComponent("weights.bin")))
        let downloads = root.appendingPathComponent("downloads")
        let coordinator = InstallCoordinator(store: store, downloadRoot: downloads, transport: transport, cleanup: { _ in throw CocoaError(.fileWriteNoPermission) })
        let task = Task { try await coordinator.install(manifest: manifest) }
        await transport.started.wait(); await transport.release.signal()
        do { _ = try await task.value; XCTFail("cleanup failure must be visible") }
        catch let error as ModelCleanupError {
            XCTAssertEqual(error.directory.deletingLastPathComponent().standardizedFileURL, downloads.standardizedFileURL)
            XCTAssertTrue(FileManager.default.fileExists(atPath: error.directory.path))
            XCTAssertEqual((error.underlyingError as NSError).code, CocoaError.fileWriteNoPermission.rawValue)
        }
        let status = await coordinator.status(manifest.ref); XCTAssertEqual(status, .installed)
    }
    func testExplicitCancellationReportsCleanupFailure() async throws {
        try await checkCancelledCleanupFailure(cancelCaller: false)
    }
    func testCallerCancellationReportsCleanupFailure() async throws {
        try await checkCancelledCleanupFailure(cancelCaller: true)
    }
    private func checkCancelledCleanupFailure(cancelCaller: Bool) async throws {
        let (root, _, manifest) = try ModelStoreTests().environment()
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try ModelStore(root: root.appendingPathComponent("store"), compatibility: .fixture)
        let transport = CancellableTransport(); let downloads = root.appendingPathComponent("downloads")
        let coordinator = InstallCoordinator(store: store, downloadRoot: downloads, transport: transport, cleanup: { _ in throw CocoaError(.fileWriteNoPermission) })
        let id = UUID(); let task = Task { try await coordinator.install(manifest: manifest, observerId: id) }
        await transport.started.wait()
        if cancelCaller { task.cancel() } else { await coordinator.cancel(observerId: id) }
        await transport.cancelled.wait(); await transport.release.signal()
        do { _ = try await task.value; XCTFail("cleanup failure must survive cancellation") }
        catch let error as ModelCleanupError {
            XCTAssertEqual(error.directory.deletingLastPathComponent().standardizedFileURL, downloads.standardizedFileURL)
            XCTAssertTrue(FileManager.default.fileExists(atPath: error.directory.path))
        }
        let status = await coordinator.status(manifest.ref); XCTAssertEqual(status, .absent)
    }
    func testObserversShareInstallAndCancelIndependently() async throws {
        let helper = ModelStoreTests(); let (root, source, manifest) = try helper.environment()
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try ModelStore(root: root.appendingPathComponent("store"), compatibility: .fixture)
        let transport = FixtureTransport(data: try Data(contentsOf: source.appendingPathComponent("weights.bin")))
        let coordinator = InstallCoordinator(store: store, downloadRoot: root.appendingPathComponent("downloads"), transport: transport)
        let firstId = UUID(); let secondAttached = TestSignal()
        let first = Task { try await coordinator.install(manifest: manifest, observerId: firstId) }
        await transport.started.wait()
        let second = Task { try await coordinator.install(manifest: manifest, progress: { _ in Task { await secondAttached.signal() } }) }
        await secondAttached.wait(); await coordinator.cancel(observerId: firstId)
        do { _ = try await first.value; XCTFail("cancelled observer") } catch is CancellationError { }
        await transport.release.signal(); let installed = try await second.value
        XCTAssertEqual(installed.ref, manifest.ref); let count = await transport.count; XCTAssertEqual(count, 1)
    }
    func testDiskFailureDoesNotPublish() async throws {
        let (root, source, manifest) = try ModelStoreTests().environment(); defer { try? FileManager.default.removeItem(at: root) }
        let store = try ModelStore(root: root.appendingPathComponent("store"), compatibility: .fixture)
        let transport = FixtureTransport(data: try Data(contentsOf: source.appendingPathComponent("weights.bin")))
        let coordinator = InstallCoordinator(store: store, downloadRoot: root.appendingPathComponent("downloads"), transport: transport, freeBytes: { _ in 0 })
        do { _ = try await coordinator.install(manifest: manifest); XCTFail("disk") }
        catch { XCTAssertEqual(error as? ModelStoreError, .diskSpace) }
        let status = await store.status(manifest.ref); XCTAssertEqual(status, .absent)
    }
    func testLastObserverCancellationWaitsForTransportAndCleansOwnedFiles() async throws {
        let (root, source, manifest) = try ModelStoreTests().environment(); defer { try? FileManager.default.removeItem(at: root) }
        let store = try ModelStore(root: root.appendingPathComponent("store"), compatibility: .fixture)
        let transport = FixtureTransport(data: try Data(contentsOf: source.appendingPathComponent("weights.bin")))
        let downloads = root.appendingPathComponent("downloads")
        let coordinator = InstallCoordinator(store: store, downloadRoot: downloads, transport: transport)
        let id = UUID(); let task = Task { try await coordinator.install(manifest: manifest, observerId: id) }
        await transport.started.wait(); await coordinator.cancel(observerId: id)
        let state = await coordinator.status(manifest.ref); XCTAssertEqual(state, .installing)
        await transport.release.signal()
        do { _ = try await task.value; XCTFail("cancelled") } catch is CancellationError { }
        let final = await store.status(manifest.ref); XCTAssertEqual(final, .absent)
        XCTAssertTrue(try FileManager.default.contentsOfDirectory(atPath: downloads.path).isEmpty)
    }
    func testHTTPSPolicyRejectsPlaintextAndForeignHost() throws {
        let policy = HTTPSDownloadPolicy(allowedHosts: ["models.example"])
        XCTAssertThrowsError(try policy.validate(URL(string:"http://models.example/file")!))
        XCTAssertThrowsError(try policy.validate(URL(string:"https://other.example/file")!))
        XCTAssertNoThrow(try policy.validate(URL(string:"https://models.example/file")!))
        for count in 0..<5 { XCTAssertNoThrow(try policy.validateRedirect(URL(string:"https://models.example/file")!, count: count)) }
        XCTAssertThrowsError(try policy.validateRedirect(URL(string:"https://models.example/file")!, count: 5))
    }
    func testRedirectDelegateAllowsFiveHopsAndRejectsSixth() throws {
        let url = URL(string: "https://models.example/file")!
        let file = ModelFile(path: "weights.bin", url: url, bytes: 7, sha256: String(repeating: "0", count: 64))
        let operation = DownloadOperation(file: file, destination: URL(fileURLWithPath: "/unused"), policy: HTTPSDownloadPolicy(allowedHosts: ["models.example"]), progress: { _ in })
        let session = URLSession(configuration: .ephemeral)
        let task = session.downloadTask(with: url)
        defer { session.invalidateAndCancel() }
        let response = HTTPURLResponse(url: url, statusCode: 302, httpVersion: nil, headerFields: nil)!
        for hop in 1...6 {
            var accepted: Bool?
            operation.urlSession(session, task: task, willPerformHTTPRedirection: response, newRequest: URLRequest(url: url)) { accepted = $0 != nil }
            XCTAssertEqual(accepted, hop <= 5, "hop \(hop)")
        }
    }
}
