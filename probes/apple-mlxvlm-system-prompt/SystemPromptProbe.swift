import Foundation
import CoreGraphics
import CoreImage
import CoreMedia
import CryptoKit
import Darwin
import MLX
import MLXLMCommon
import MLXVLM

// MARK: - Diagnostic Error Types

enum ProbeError: Error, CustomStringConvertible, Sendable {
    case invalidArgument(String)
    case outputAlreadyExists(String)
    case directoryCreationFailed(String)
    case manifestMismatch(String)
    case fixtureValidationFailed(String)
    case modelConfigurationMissing(String)
    case adapterFailure(String)
    case tokenIntegrityFailure(String)
    case emptyGenerationOutput(String)
    case incompleteRows(String)
    case cleanupFailed(String)

    var description: String {
        switch self {
        case .invalidArgument(let msg): return "Invalid CLI argument: \(msg)"
        case .outputAlreadyExists(let msg): return "Output destination already exists: \(msg)"
        case .directoryCreationFailed(let msg): return "Directory creation failed: \(msg)"
        case .manifestMismatch(let msg): return "Manifest mismatch: \(msg)"
        case .fixtureValidationFailed(let msg): return "Fixture validation failed: \(msg)"
        case .modelConfigurationMissing(let msg): return "Model configuration missing: \(msg)"
        case .adapterFailure(let msg): return "Adapter failure: \(msg)"
        case .tokenIntegrityFailure(let msg): return "Token integrity check failed: \(msg)"
        case .emptyGenerationOutput(let msg): return "Empty generation output: \(msg)"
        case .incompleteRows(let msg): return "Incomplete generation rows: \(msg)"
        case .cleanupFailed(let msg): return "Snapshot cleanup failed: \(msg)"
        }
    }
}

struct FixtureFailureMetadata: Codable, Sendable {
    let errorType: String
    let errorDescription: String
}

struct FixtureOutcome: Sendable {
    let completedRows: [ObservationRow]
    let failure: FixtureFailureMetadata?
}

struct FixtureExecutionError: Error, Sendable, CustomStringConvertible {
    let errorType: String
    let errorDescription: String

    var description: String { errorDescription }
}

// MARK: - Sendable Codable Data Contracts

struct ModelIdentity: Codable, Sendable {
    let modelId: String
    let modelRevision: String
    let runtimeRevision: String
}

struct THWRecord: Codable, Sendable {
    let t: Int
    let h: Int
    let w: Int
}

struct CMTimeRecord: Codable, Sendable {
    let value: Int64
    let timescale: Int32
    let seconds: Double
}

struct ObservationRow: Codable, Sendable {
    let observationId: String
    let taskId: String
    let condition: String
    let requestedSystemText: String
    let prompt: String
    let fixtureHash: String
    let equalOriginalTokenAssertion: Bool
    let tokens: [Int]
    let canonicalHash: String
    let originalTokens: [Int]
    let neutralTokens: [Int]
    let originalTokensCanonicalHash: String
    let neutralTokensCanonicalHash: String
    let pixelsShape: [Int]
    let pixelsHash: String
    let thw: [THWRecord]
    let timestamps: [CMTimeRecord]
    let clipDuration: CMTimeRecord
    let modelIdentity: ModelIdentity
    let rawOutput: String
    let updates: Int
    let elapsedTime: Double
    let elapsedTimeSeconds: Double
    let generationJoined: Bool
}

struct SnapshotCleanupRecord: Codable, Sendable {
    let taskId: String
    let snapshotDirectory: String
    let closed: Bool
    let directoryAbsent: Bool
}

struct ProbeReport: Codable, Sendable {
    let schemaVersion: Int
    var state: String
    var terminal: Bool
    let scope: String
    let qualityVerdict: String
    let runId: String
    let sdkCommit: String
    let modelIdentity: ModelIdentity
    let manifestSha256: String
    let prompt: String
    let originalSystemPrompt: String
    let neutralSystemPrompt: String
    let device: String
    let os: String
    let pid: Int32
    var rows: [ObservationRow]
    var cleanups: [SnapshotCleanupRecord]
    var snapshotsRemoved: Bool
    var errorType: String?
    var errorDescription: String?
}

// MARK: - Main Diagnostic Entrypoint

@main
struct SystemPromptProbe {
    private static let fixedPrompt = "Are the birds moving across the sky or staying still? Answer exactly MOVING or STILL."
    private static let originalSystemPrompt = "You are a helpful assistant that can understand videos. Describe what type of video this is and what's happening in it."
    private static let neutralSystemPrompt = "You are a helpful assistant."

    private static let expectedManifestSha256 = "666fbfebdc999aefa5b9db944a090024b9478c13107685af621fb842babe1694"

    private struct FixtureTarget: Sendable {
        let taskId: String
        let filename: String
        let expectedBytes: Int
        let expectedSha256: String
    }

    private static let admittedFixtures: [FixtureTarget] = [
        FixtureTarget(
            taskId: "motion-0-2",
            filename: "motion-0-2.mp4",
            expectedBytes: 514756,
            expectedSha256: "13e500259210416c925dac928eae3e0330f2687f987cddd3c9d86925c293ed25"
        ),
        FixtureTarget(
            taskId: "frozen-0-2",
            filename: "frozen-0-2.mp4",
            expectedBytes: 16617,
            expectedSha256: "23d39b285ded6d2440488e20d4d90033a87676f81faf7c1ca0402270c76d1cb4"
        )
    ]

    private static let fixedModelIdentity = ModelIdentity(
        modelId: "mlx-community/SmolVLM2-500M-Video-Instruct-mlx",
        modelRevision: "fa57db46815177fbdfd65cc85a2b3416a8332268",
        runtimeRevision: "6e338e5df49424fc0db7c6d58184d622f92ed18a"
    )

    static func main() async {
        var reportURL: URL?
        var currentReport: ProbeReport?
        var cleanupsCompleted: [SnapshotCleanupRecord] = []

        do {
            // 1. Parse CLI arguments
            let parsedArgs = try parseCommandLineArguments()

            // 2. Validate and exclusively create output directory
            let outputDir = parsedArgs.outputRoot.appendingPathComponent(parsedArgs.runId)
            try createOutputDirectoryExclusively(at: outputDir)
            let repURL = outputDir.appendingPathComponent("report.json")
            reportURL = repURL

            // 3. Initialize and atomically persist running report before loading anything
            var report = ProbeReport(
                schemaVersion: 1,
                state: "running",
                terminal: false,
                scope: "macos-system-message-single-variable-diagnostic-only",
                qualityVerdict: "unverified",
                runId: parsedArgs.runId,
                sdkCommit: parsedArgs.sdkCommit,
                modelIdentity: fixedModelIdentity,
                manifestSha256: expectedManifestSha256,
                prompt: fixedPrompt,
                originalSystemPrompt: originalSystemPrompt,
                neutralSystemPrompt: neutralSystemPrompt,
                device: machineIdentifier(),
                os: ProcessInfo.processInfo.operatingSystemVersionString,
                pid: ProcessInfo.processInfo.processIdentifier,
                rows: [],
                cleanups: [],
                snapshotsRemoved: false,
                errorType: nil,
                errorDescription: nil
            )
            currentReport = report
            try writeReportAtomically(report, to: repURL)

            // 4. Validate fixture manifest and admitted video fixtures before model loading
            let resolvedFixtureURLs = try validateFixtures(at: parsedArgs.fixturesDir)

            // 5. Load model configuration and ModelContainer
            let configURL = parsedArgs.modelDir.appendingPathComponent("preprocessor_config.json")
            guard FileManager.default.fileExists(atPath: configURL.path) else {
                throw ProbeError.modelConfigurationMissing("preprocessor_config.json not found at \(configURL.path)")
            }
            let configData = try Data(contentsOf: configURL)
            let configuration = try JSONDecoder().decode(SmolVLMProcessorConfiguration.self, from: configData)
            let container = try await VLMModelFactory.shared.loadContainer(
                from: parsedArgs.modelDir,
                using: LocalTokenizerLoader()
            )

            // 6. Execute exactly 4 sequential fresh generations across the 2 admitted fixtures
            for target in admittedFixtures {
                guard let fixtureURL = resolvedFixtureURLs[target.taskId] else {
                    throw ProbeError.fixtureValidationFailed("Unresolved path for \(target.taskId)")
                }

                let snapshot = try await MLXVLMVideoSnapshot.load(fixtureURL)
                let snapshotDir = snapshot.file.deletingLastPathComponent()
                var snapshotManuallyClosed = false

                defer {
                    if !snapshotManuallyClosed {
                        snapshot.close()
                        let absent = !FileManager.default.fileExists(atPath: snapshotDir.path)
                        let record = SnapshotCleanupRecord(
                            taskId: target.taskId,
                            snapshotDirectory: snapshotDir.path,
                            closed: true,
                            directoryAbsent: absent
                        )
                        cleanupsCompleted.append(record)
                    }
                }

                let outcome: FixtureOutcome = await container.perform { context in
                    var completedRows: [ObservationRow] = []
                    do {
                        // Production adapter input preparation
                        let prepared = try await MLXVLMVideoInputAdapter.prepare(
                            frames: snapshot.frames,
                            clipDuration: snapshot.duration,
                            prompt: fixedPrompt,
                            configuration: configuration,
                            tokenizer: context.tokenizer
                        )

                        guard let preparedImage = prepared.input.image else {
                            throw ProbeError.adapterFailure("prepared.input.image is nil for \(target.taskId)")
                        }

                        guard let frames = preparedImage.frames else {
                            throw ProbeError.adapterFailure("preparedImage.frames is nil for \(target.taskId)")
                        }

                        eval(preparedImage.pixels)
                        let pixelValues = preparedImage.pixels.asArray(Float.self)
                        let pixelsShape = preparedImage.pixels.shape
                        let pixelsHash = sha256Hex(pixelValues)

                        let thwRecords = frames.map { THWRecord(t: $0.t, h: $0.h, w: $0.w) }
                        let timestampRecords = snapshot.frames.map {
                            CMTimeRecord(value: $0.actualTime.value, timescale: $0.actualTime.timescale, seconds: $0.actualTime.seconds)
                        }
                        let clipDurationRecord = CMTimeRecord(
                            value: snapshot.duration.value,
                            timescale: snapshot.duration.timescale,
                            seconds: snapshot.duration.seconds
                        )

                        // Reconstruct exact video block assembly from adapter
                        var block = "You are provided the following series of \(snapshot.frames.count) frames from a \(formatTime(snapshot.duration)) [H:MM:SS] video.\n"
                        let imageBlock = "<fake_token_around_image><global-img>"
                            + String(repeating: "<image>", count: 64) + "<fake_token_around_image>"
                        for frame in snapshot.frames {
                            block += "\nFrame from \(formatTime(frame.actualTime)):" + imageBlock
                        }
                        block += "\n\n"

                        let videoFrames = snapshot.frames.map {
                            UserInput.VideoFrame(image: .ciImage(CIImage(cgImage: $0.image)), timeStamp: $0.actualTime)
                        }
                        let input = UserInput(prompt: block + fixedPrompt, videos: [.frames(videoFrames)])

                        // Reconstruct original messages and tokens
                        var originalMessages = SmolVLM2MessageGenerator().generate(from: input)
                        originalMessages.insert([
                            "role": "system",
                            "content": [["type": "text", "text": originalSystemPrompt]]
                        ], at: 0)
                        let originalTokens = try context.tokenizer.applyChatTemplate(
                            messages: originalMessages,
                            tools: nil,
                            additionalContext: nil
                        )

                        // ASSERT whole array equality against adapter tokens before any generation
                        eval(prepared.input.text.tokens)
                        let adapterTokens = prepared.input.text.tokens.asArray(Int.self)
                        guard originalTokens == adapterTokens else {
                            throw ProbeError.tokenIntegrityFailure(
                                "Reconstructed original tokens do not match adapter prepared tokens for \(target.taskId). " +
                                "Reconstructed count: \(originalTokens.count), adapter count: \(adapterTokens.count)"
                            )
                        }

                        // Reconstruct neutral messages and tokens with only system text changed
                        var neutralMessages = SmolVLM2MessageGenerator().generate(from: input)
                        neutralMessages.insert([
                            "role": "system",
                            "content": [["type": "text", "text": neutralSystemPrompt]]
                        ], at: 0)
                        let neutralTokens = try context.tokenizer.applyChatTemplate(
                            messages: neutralMessages,
                            tools: nil,
                            additionalContext: nil
                        )

                        let originalTokensCanonicalHash = try canonicalDocumentHash(originalTokens)
                        let neutralTokensCanonicalHash = try canonicalDocumentHash(neutralTokens)

                        // Reuse EXACT prepared.input.image (pixels + THW) for both LMInputs
                        let originalTokensArray = MLXArray(originalTokens).expandedDimensions(axis: 0)
                        let originalLMInput = LMInput(
                            text: .init(tokens: originalTokensArray, mask: ones(like: originalTokensArray)),
                            image: prepared.input.image
                        )

                        let neutralTokensArray = MLXArray(neutralTokens).expandedDimensions(axis: 0)
                        let neutralLMInput = LMInput(
                            text: .init(tokens: neutralTokensArray, mask: ones(like: neutralTokensArray)),
                            image: prepared.input.image
                        )

                        // Fresh generation 1: Original system prompt
                        let originalGen = try await executeSingleGeneration(
                            context: context,
                            input: originalLMInput,
                            maxTokens: 32
                        )

                        let rowOriginal = ObservationRow(
                            observationId: "\(target.taskId)-original",
                            taskId: target.taskId,
                            condition: "original",
                            requestedSystemText: originalSystemPrompt,
                            prompt: fixedPrompt,
                            fixtureHash: target.expectedSha256,
                            equalOriginalTokenAssertion: true,
                            tokens: originalTokens,
                            canonicalHash: originalTokensCanonicalHash,
                            originalTokens: originalTokens,
                            neutralTokens: neutralTokens,
                            originalTokensCanonicalHash: originalTokensCanonicalHash,
                            neutralTokensCanonicalHash: neutralTokensCanonicalHash,
                            pixelsShape: pixelsShape,
                            pixelsHash: pixelsHash,
                            thw: thwRecords,
                            timestamps: timestampRecords,
                            clipDuration: clipDurationRecord,
                            modelIdentity: fixedModelIdentity,
                            rawOutput: originalGen.output,
                            updates: originalGen.updates,
                            elapsedTime: originalGen.durationSeconds,
                            elapsedTimeSeconds: originalGen.durationSeconds,
                            generationJoined: originalGen.joined
                        )
                        completedRows.append(rowOriginal)

                        // Fresh generation 2: Neutral system prompt
                        let neutralGen = try await executeSingleGeneration(
                            context: context,
                            input: neutralLMInput,
                            maxTokens: 32
                        )

                        let rowNeutral = ObservationRow(
                            observationId: "\(target.taskId)-neutral",
                            taskId: target.taskId,
                            condition: "neutral",
                            requestedSystemText: neutralSystemPrompt,
                            prompt: fixedPrompt,
                            fixtureHash: target.expectedSha256,
                            equalOriginalTokenAssertion: true,
                            tokens: neutralTokens,
                            canonicalHash: neutralTokensCanonicalHash,
                            originalTokens: originalTokens,
                            neutralTokens: neutralTokens,
                            originalTokensCanonicalHash: originalTokensCanonicalHash,
                            neutralTokensCanonicalHash: neutralTokensCanonicalHash,
                            pixelsShape: pixelsShape,
                            pixelsHash: pixelsHash,
                            thw: thwRecords,
                            timestamps: timestampRecords,
                            clipDuration: clipDurationRecord,
                            modelIdentity: fixedModelIdentity,
                            rawOutput: neutralGen.output,
                            updates: neutralGen.updates,
                            elapsedTime: neutralGen.durationSeconds,
                            elapsedTimeSeconds: neutralGen.durationSeconds,
                            generationJoined: neutralGen.joined
                        )
                        completedRows.append(rowNeutral)

                        return FixtureOutcome(completedRows: completedRows, failure: nil)
                    } catch {
                        let typeStr = String(describing: type(of: error))
                        let desc = (error as? ProbeError)?.description ?? error.localizedDescription
                        return FixtureOutcome(
                            completedRows: completedRows,
                            failure: FixtureFailureMetadata(errorType: typeStr, errorDescription: desc)
                        )
                    }
                }

                // Append completed rows and update currentReport outside perform before cleanup checks/throwing
                report.rows.append(contentsOf: outcome.completedRows)
                currentReport = report
                try writeReportAtomically(report, to: repURL)

                // Close snapshot and assert exact directory absent
                snapshot.close()
                snapshotManuallyClosed = true
                let dirAbsent = !FileManager.default.fileExists(atPath: snapshotDir.path)
                let cleanupRec = SnapshotCleanupRecord(
                    taskId: target.taskId,
                    snapshotDirectory: snapshotDir.path,
                    closed: true,
                    directoryAbsent: dirAbsent
                )
                cleanupsCompleted.append(cleanupRec)
                report.cleanups = cleanupsCompleted
                currentReport = report
                try writeReportAtomically(report, to: repURL)

                if let failure = outcome.failure {
                    throw FixtureExecutionError(errorType: failure.errorType, errorDescription: failure.errorDescription)
                }

                guard dirAbsent else {
                    throw ProbeError.cleanupFailed("Snapshot directory \(snapshotDir.path) was not removed after close()")
                }
            }

            // 7. Verify all 4 rows completed and both snapshot directories absent
            guard report.rows.count == 4 else {
                throw ProbeError.incompleteRows("Expected 4 observation rows, recorded \(report.rows.count)")
            }
            let allDirectoriesAbsent = cleanupsCompleted.count == admittedFixtures.count
                && cleanupsCompleted.allSatisfy { $0.directoryAbsent }
            guard allDirectoriesAbsent else {
                throw ProbeError.cleanupFailed("Not all snapshot directories were confirmed absent")
            }

            report.snapshotsRemoved = true
            report.state = "success"
            report.terminal = true
            currentReport = report
            try writeReportAtomically(report, to: repURL)

            // stdout prints report path only
            print(repURL.path)
            Darwin.exit(0)

        } catch {
            let errorType: String
            let errorDescription: String
            if let execErr = error as? FixtureExecutionError {
                errorType = execErr.errorType
                errorDescription = execErr.errorDescription
            } else {
                errorType = String(describing: type(of: error))
                errorDescription = (error as? ProbeError)?.description ?? error.localizedDescription
            }

            if let repURL = reportURL, var report = currentReport {
                report.state = "failed"
                report.terminal = true
                report.cleanups = cleanupsCompleted
                report.snapshotsRemoved = cleanupsCompleted.count == admittedFixtures.count
                    && cleanupsCompleted.allSatisfy { $0.directoryAbsent }
                report.errorType = errorType
                report.errorDescription = errorDescription

                // Persist terminal report with rows completed so far; never try? away failures
                do {
                    try writeReportAtomically(report, to: repURL)
                    print(repURL.path)
                } catch {
                    logStderr("Failed to persist terminal failure report: \(error.localizedDescription)")
                }
            } else {
                logStderr("Initialization failure before report creation: \(errorDescription)")
            }
            Darwin.exit(1)
        }
    }

    // MARK: - Generation Helper

    private static func executeSingleGeneration(
        context: ModelContext,
        input: LMInput,
        maxTokens: Int
    ) async throws -> (output: String, updates: Int, durationSeconds: Double, joined: Bool) {
        try Task.checkCancellation()
        let start = ContinuousClock.now
        let parameters = GenerateParameters(maxTokens: maxTokens, temperature: 0)
        let cache = try context.model.newCache(parameters: parameters)
        let iterator = try TokenIterator(input: input, model: context.model, cache: cache, parameters: parameters)
        let (stream, generation) = generateTask(
            promptTokenCount: input.text.tokens.size,
            modelConfiguration: context.configuration,
            tokenizer: context.tokenizer,
            iterator: iterator
        )

        let (text, count) = try await withTaskCancellationHandler {
            var accumulated = ""
            var updateCount = 0
            for await value in stream {
                if Task.isCancelled {
                    generation.cancel()
                    break
                }
                if let chunk = value.chunk {
                    accumulated += chunk
                    updateCount += 1
                }
            }
            await generation.value
            try Task.checkCancellation()
            return (accumulated, updateCount)
        } onCancel: {
            generation.cancel()
        }

        let elapsed = start.duration(to: ContinuousClock.now)
        let elapsedSeconds = Double(elapsed.components.seconds) + Double(elapsed.components.attoseconds) / 1e18

        guard !text.isEmpty else {
            throw ProbeError.emptyGenerationOutput("Raw generation output was unexpectedly empty")
        }

        return (output: text, updates: count, durationSeconds: elapsedSeconds, joined: true)
    }

    // MARK: - Fixture and Manifest Validation

    private static func validateFixtures(at fixturesDir: URL) throws -> [String: URL] {
        let manifestURL = fixturesDir.appendingPathComponent("manifest.json")
        guard FileManager.default.fileExists(atPath: manifestURL.path) else {
            throw ProbeError.fixtureValidationFailed("manifest.json not found in \(fixturesDir.path)")
        }
        let manifestData = try Data(contentsOf: manifestURL)
        let computedManifestSha = sha256Hex(manifestData)
        guard computedManifestSha == expectedManifestSha256 else {
            throw ProbeError.manifestMismatch(
                "manifest.json SHA256 mismatch. Expected: \(expectedManifestSha256), got: \(computedManifestSha)"
            )
        }

        var relativePaths: [String: String] = [:]
        if let json = try? JSONSerialization.jsonObject(with: manifestData) as? [String: Any],
           let derivatives = json["derivatives"] as? [[String: Any]] {
            for item in derivatives {
                if let taskId = item["taskId"] as? String, let p = item["path"] as? String {
                    relativePaths[taskId] = p
                }
            }
        }

        var resolved: [String: URL] = [:]
        for target in admittedFixtures {
            let path = relativePaths[target.taskId] ?? target.filename
            let fileURL = fixturesDir.appendingPathComponent(path)
            guard FileManager.default.fileExists(atPath: fileURL.path) else {
                throw ProbeError.fixtureValidationFailed("Fixture file missing for \(target.taskId) at \(fileURL.path)")
            }
            let data = try Data(contentsOf: fileURL)
            guard data.count == target.expectedBytes else {
                throw ProbeError.fixtureValidationFailed(
                    "Fixture byte count mismatch for \(target.taskId). Expected: \(target.expectedBytes), got: \(data.count)"
                )
            }
            let hash = sha256Hex(data)
            guard hash == target.expectedSha256 else {
                throw ProbeError.fixtureValidationFailed(
                    "Fixture SHA256 mismatch for \(target.taskId). Expected: \(target.expectedSha256), got: \(hash)"
                )
            }
            resolved[target.taskId] = fileURL
        }

        return resolved
    }

    // MARK: - Filesystem & CLI Helpers

    private struct ParsedArguments {
        let runId: String
        let modelDir: URL
        let fixturesDir: URL
        let outputRoot: URL
        let sdkCommit: String
    }

    private static func parseCommandLineArguments() throws -> ParsedArguments {
        var rawRunId: String?
        var rawModel: String?
        var rawFixtures: String?
        var rawOutputRoot: String?
        var rawSdkCommit: String?

        for arg in CommandLine.arguments.dropFirst() {
            if arg.hasPrefix("--run-id=") {
                rawRunId = String(arg.dropFirst("--run-id=".count))
            } else if arg.hasPrefix("--model=") {
                rawModel = String(arg.dropFirst("--model=".count))
            } else if arg.hasPrefix("--fixtures=") {
                rawFixtures = String(arg.dropFirst("--fixtures=".count))
            } else if arg.hasPrefix("--output-root=") {
                rawOutputRoot = String(arg.dropFirst("--output-root=".count))
            } else if arg.hasPrefix("--sdk-commit=") {
                rawSdkCommit = String(arg.dropFirst("--sdk-commit=".count))
            }
        }

        guard let runId = rawRunId, !runId.isEmpty else {
            throw ProbeError.invalidArgument("--run-id is required and cannot be empty")
        }
        guard runId.range(of: "^[A-Za-z0-9_.-]+$", options: .regularExpression) != nil,
              runId != ".", runId != ".." else {
            throw ProbeError.invalidArgument("--run-id must be a safe nonempty identifier without path traversal")
        }

        guard let modelPath = rawModel, !modelPath.isEmpty else {
            throw ProbeError.invalidArgument("--model is required")
        }
        let modelDir = URL(fileURLWithPath: modelPath)
        var isDir: ObjCBool = false
        guard FileManager.default.fileExists(atPath: modelDir.path, isDirectory: &isDir), isDir.boolValue else {
            throw ProbeError.invalidArgument("--model must point to an existing directory")
        }

        guard let fixturesPath = rawFixtures, !fixturesPath.isEmpty else {
            throw ProbeError.invalidArgument("--fixtures is required")
        }
        let fixturesDir = URL(fileURLWithPath: fixturesPath)
        guard FileManager.default.fileExists(atPath: fixturesDir.path, isDirectory: &isDir), isDir.boolValue else {
            throw ProbeError.invalidArgument("--fixtures must point to an existing directory")
        }

        guard let outputRootPath = rawOutputRoot, !outputRootPath.isEmpty else {
            throw ProbeError.invalidArgument("--output-root is required")
        }
        let outputRoot = URL(fileURLWithPath: outputRootPath)
        guard FileManager.default.fileExists(atPath: outputRoot.path, isDirectory: &isDir), isDir.boolValue else {
            throw ProbeError.invalidArgument("--output-root must point to an existing directory")
        }

        guard let sdkCommit = rawSdkCommit, sdkCommit.count == 40,
              sdkCommit.range(of: "^[0-9a-fA-F]{40}$", options: .regularExpression) != nil else {
            throw ProbeError.invalidArgument("--sdk-commit must be 40 hex characters")
        }

        return ParsedArguments(
            runId: runId,
            modelDir: modelDir,
            fixturesDir: fixturesDir,
            outputRoot: outputRoot,
            sdkCommit: sdkCommit
        )
    }

    private static func createOutputDirectoryExclusively(at url: URL) throws {
        var sb = stat()
        if lstat(url.path, &sb) == 0 {
            throw ProbeError.outputAlreadyExists("Destination \(url.path) already exists or is a symlink")
        }
        if mkdir(url.path, 0o755) != 0 {
            throw ProbeError.directoryCreationFailed("Exclusive mkdir failed at \(url.path) with errno \(errno)")
        }
    }

    private static func writeReportAtomically(_ report: ProbeReport, to url: URL) throws {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(report)
        try data.write(to: url, options: .atomic)
    }

    private static func logStderr(_ message: String) {
        if let data = (message + "\n").data(using: .utf8) {
            FileHandle.standardError.write(data)
        }
    }

    // MARK: - Formatting and Cryptographic Utilities

    private static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private static func sha256Hex(_ floats: [Float]) -> String {
        floats.withUnsafeBytes { rawBuffer in
            SHA256.hash(data: Data(rawBuffer)).map { String(format: "%02x", $0) }.joined()
        }
    }

    private static func canonicalDocumentHash(_ tokens: [Int]) throws -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(tokens)
        return sha256Hex(data)
    }

    private static func formatTime(_ time: CMTime) -> String {
        let seconds = Int(ceil(time.seconds))
        return String(format: "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    }

    private static func machineIdentifier() -> String {
        var size = 0
        sysctlbyname("hw.machine", nil, &size, nil, 0)
        var buffer = [CChar](repeating: 0, count: size)
        sysctlbyname("hw.machine", &buffer, &size, nil, 0)
        return String(cString: buffer)
    }
}
