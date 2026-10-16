import XCTest
@testable import VLMProbe

final class ProbeRunnerTests: XCTestCase {
    private func fixture() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("p9-fixture.bin")
        try Data("fixture".utf8).write(to: url)
        return url
    }

    func testInputHashIsStable() async throws {
        let result = try await VLMProbe(backend: UnavailableBackend()).run(
            input: try fixture(), manifest: .testVerified
        )
        XCTAssertEqual(result.fixtureHash, "f16d05ec6b29248d2c61adb1e9263f78e4f7bace1b955014a2d17872cfe4064d")
    }

    func testMissingManifestFailsClosed() async throws {
        await XCTAssertThrowsErrorAsync {
            try await VLMProbe(backend: UnavailableBackend()).run(
                input: try self.fixture(), manifest: nil
            )
        }
    }

    func testUnavailableRuntimeHasTerminalStateAndNoOutput() async throws {
        let result = try await VLMProbe(backend: UnavailableBackend()).run(
            input: try fixture(), manifest: .testVerified
        )
        XCTAssertEqual(result.status, .unavailable)
        XCTAssertTrue(result.output.isEmpty)
        XCTAssertFalse(result.cancelled)
    }

    func testCancellationIsTerminal() async throws {
        let result = try await VLMProbe(backend: CancellingBackend()).run(
            input: try fixture(), manifest: .testVerified
        )
        XCTAssertEqual(result.status, .cancelled)
        XCTAssertTrue(result.cancelled)
        XCTAssertTrue(result.output.isEmpty)
    }
}

private extension VLMManifest {
    static let testVerified = VLMManifest(modelId: candidate.modelId, modelRevision: candidate.modelRevision,
        runtime: candidate.runtime, runtimeRevision: candidate.runtimeRevision,
        platformMin: candidate.platformMin, status: "verified")
}

private extension XCTestCase {
    func XCTAssertThrowsErrorAsync(_ expression: @escaping () async throws -> Void) async {
        do {
            try await expression()
            XCTFail("expected error")
        } catch {
            // expected
        }
    }
}
