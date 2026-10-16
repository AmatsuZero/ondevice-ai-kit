import XCTest
@testable import VLMProbe

final class ProbeRunnerTests: XCTestCase {
    func testLaunchConfigurationResolvesContainerRelativePaths() {
        let documents = URL(fileURLWithPath: "/tmp/documents")
        let configuration = ProbeLaunchConfiguration.from(
            arguments: ["probe", "--p9-model-directory", "model", "--p9-input", "fixture.png", "--p9-auto-run"],
            documents: documents)
        XCTAssertEqual(configuration.modelDirectory.path, "/tmp/documents/model")
        XCTAssertEqual(configuration.input.path, "/tmp/documents/fixture.png")
        XCTAssertEqual(configuration.manifest.path, "/tmp/documents/p9-smolvlm-manifest.json")
        XCTAssertTrue(configuration.autoRun)
    }

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

    func testEmptyGenerationIsUnavailable() async throws {
        let result = try await VLMProbe(backend: EmptyBackend()).run(
            input: try fixture(), manifest: .testVerified)
        XCTAssertEqual(result.status, .unavailable)
        XCTAssertTrue(result.output.isEmpty)
        XCTAssertEqual(result.errorReason, "empty generation")
    }

    func testCancellationIsTerminal() async throws {
        let result = try await VLMProbe(backend: CancellingBackend()).run(
            input: try fixture(), manifest: .testVerified
        )
        XCTAssertEqual(result.status, .cancelled)
        XCTAssertTrue(result.cancelled)
        XCTAssertTrue(result.output.isEmpty)
    }

    func testCandidateCannotReachBackend() async throws {
        let backend = CountingBackend()
        do {
            _ = try await VLMProbe(backend: backend).run(input: try fixture(), manifest: .candidate)
            XCTFail("candidate must fail closed")
        } catch VLMProbeError.candidateManifest {
            let calls = await backend.calls
            XCTAssertEqual(calls, 0)
        }
    }

    func testTwoSessionsDoNotRetainBackendState() async throws {
        let backend = CountingBackend()
        let probe = VLMProbe(backend: backend)
        _ = try await probe.run(input: try fixture(), manifest: .testVerified)
        _ = try await probe.run(input: try fixture(), manifest: .testVerified)
        let calls = await backend.calls
        XCTAssertEqual(calls, 2)
    }

    func testUnavailableJSONExplicitlyMarksMemoryUnmeasured() async throws {
        let result = try await VLMProbe(backend: UnavailableBackend()).run(
            input: try fixture(), manifest: .testVerified)
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(result)) as? [String: Any])
        XCTAssertEqual(object["status"] as? String, "unavailable")
        XCTAssertTrue(object["peakMemory"] is NSNull)
        XCTAssertEqual(object["peakMemoryStatus"] as? String, "unmeasured")
    }
}

private actor CountingBackend: VLMBackend {
    let identifier = "fake"
    private(set) var calls = 0
    func generate(input: URL, manifest: VLMManifest) async throws -> VLMGeneration {
        calls += 1
        return VLMGeneration(output: "ok", firstTokenMs: 1)
    }
}

private struct EmptyBackend: VLMBackend {
    let identifier = "fake"
    func generate(input: URL, manifest: VLMManifest) async throws -> VLMGeneration {
        VLMGeneration(output: " ")
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
