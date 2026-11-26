import Foundation
import AVFoundation
import ODAIWeMMNative
import ODAIKitMultimodal
import ODAIKitModelStore
import ODAIKitCore

@available(iOS 16.4, macOS 13, *)
actor WeMMVideoBackend: MultimodalVideoGenerationBackend {
    nonisolated let backendId = WeMMVideoModel.backendId

    private struct ActiveWorker: Sendable {
        let id: UUID
        let task: Task<MultimodalTextOutput, Error>
    }

    private let lease: ModelLease
    private let languageURL: URL
    private let projectorURL: URL
    private let identity: MultimodalModelIdentity
    private var activeWorker: ActiveWorker?
    private var isClosed = false
    private var closeTask: Task<Void, Never>?

    init(lease: ModelLease) throws {
        let expected = try WeMMVideoModel.manifest()
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        guard try encoder.encode(expected) == encoder.encode(lease.manifest) else {
            throw ModelStoreError.invalidManifest
        }
        guard let language = expected.files.first(where: { $0.path == "language.gguf" }),
              let projector = expected.files.first(where: { $0.path == "mmproj.gguf" }) else {
            throw ModelStoreError.invalidManifest
        }

        self.lease = lease
        self.languageURL = lease.directory.appendingPathComponent(language.path, isDirectory: false)
        self.projectorURL = lease.directory.appendingPathComponent(projector.path, isDirectory: false)
        self.identity = try WeMMVideoModel.identity()
    }

    func availability() async -> CapabilityAvailability {
        if isClosed {
            return CapabilityAvailability(status: .unavailable, reason: .closed, backendId: backendId, minOS: "16.4")
        }
        return CapabilityAvailability(status: .available, backendId: backendId, minOS: "16.4")
    }

    func generate(_ request: MultimodalRequest,
                  update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> MultimodalTextOutput {
        guard !isClosed else { throw MultimodalClientError.closed }
        throw MultimodalClientError.invalidInput("video input required")
    }

    func generateVideo(_ request: MultimodalVideoRequest,
                       update: (@Sendable (MultimodalTextUpdate) -> Void)?) async throws -> MultimodalTextOutput {
        guard !isClosed else { throw MultimodalClientError.closed }
        guard activeWorker == nil else { throw MultimodalClientError.busy }

        try Task.checkCancellation()

        let workerID = UUID()
        let languageURL = self.languageURL
        let projectorURL = self.projectorURL
        let identity = self.identity
        let worker = Task.detached { [request, languageURL, projectorURL, identity, update] in
            try Task.checkCancellation()
            let text = try await WeMMVideoPreparation.withRequest(
                video: request.videoFile,
                question: request.prompt,
                maxOutputTokens: request.maxOutputTokens
            ) { requestDirectory in
                let callbacks = WeMMVideoCallbackContext(update: update)
                let requestPath = requestDirectory.path
                let languagePath = languageURL.path
                let projectorPath = projectorURL.path
                let result = withExtendedLifetime(callbacks) {
                    languagePath.withCString { language in
                        projectorPath.withCString { projector in
                            requestPath.withCString { directory in
                                odai_wemm_generate(
                                    language,
                                    projector,
                                    directory,
                                    Unmanaged.passUnretained(callbacks).toOpaque(),
                                    weMMVideoCheckpoint,
                                    weMMVideoResult
                                )
                            }
                        }
                    }
                }

                guard !callbacks.callbackFailed else {
                    throw KitError.backend("video generation failed")
                }
                switch result {
                case 0:
                    break
                case 2:
                    throw MultimodalClientError.busy
                case 3:
                    throw CancellationError()
                default:
                    throw KitError.backend("video generation failed")
                }
                try Task.checkCancellation()
                guard let finalData = callbacks.finalData else {
                    throw KitError.backend("video generation failed")
                }
                return String(decoding: finalData, as: UTF8.self)
            }

            try Task.checkCancellation()
            guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                throw KitError.backend("video generation returned empty output")
            }
            let output = try MultimodalTextOutput(text: text, model: identity)
            try Task.checkCancellation()
            return output
        }

        activeWorker = ActiveWorker(id: workerID, task: worker)
        do {
            let output = try await withTaskCancellationHandler {
                try await worker.value
            } onCancel: {
                worker.cancel()
            }
            try Task.checkCancellation()
            guard !worker.isCancelled else { throw CancellationError() }
            guard !isClosed else { throw MultimodalClientError.closed }
            finishWorker(id: workerID)
            return output
        } catch {
            finishWorker(id: workerID)
            let sourceError = error as NSError
            guard sourceError.domain == AVError.errorDomain,
                  sourceError.code == AVError.Code.decodeFailed.rawValue
                    || sourceError.code == AVError.Code.fileFailedToParse.rawValue else {
                throw error
            }
            throw MultimodalClientError.invalidInput("wemmVideoDecodeFailed")
        }
    }

    func drain() async {
        guard let activeWorker else { return }
        _ = await activeWorker.task.result
        finishWorker(id: activeWorker.id)
    }

    func close() async {
        if let closeTask {
            await closeTask.value
            return
        }

        isClosed = true
        let worker = activeWorker
        worker?.task.cancel()
        let lease = self.lease
        let task = Task {
            if let worker {
                _ = await worker.task.result
            }
            await lease.close()
        }
        closeTask = task
        await task.value
    }

    private func finishWorker(id: UUID) {
        guard activeWorker?.id == id else { return }
        activeWorker = nil
    }
}

@available(iOS 16.0, macOS 13.0, *)
private final class WeMMVideoCallbackContext {
    let update: (@Sendable (MultimodalTextUpdate) -> Void)?
    var lastOrdinal = -1
    var finalData: Data?
    var callbackFailed = false

    init(update: (@Sendable (MultimodalTextUpdate) -> Void)?) {
        self.update = update
    }
}

@available(iOS 16.0, macOS 13.0, *)
private func weMMVideoCheckpoint(
    _ rawContext: UnsafeMutableRawPointer?,
    _ stage: odai_wemm_stage,
    _ generated: Int32,
    _ bytes: UnsafePointer<CChar>?,
    _ length: Int
) -> Int32 {
    guard let rawContext else { return 1 }
    let context = Unmanaged<WeMMVideoCallbackContext>.fromOpaque(rawContext).takeUnretainedValue()
    guard !Task.isCancelled else { return 1 }
    guard stage == ODAI_WEMM_GENERATE else { return 0 }
    guard generated >= 0, length >= 0 else {
        context.callbackFailed = true
        return 1
    }
    guard length > 0 else { return 0 }
    guard let bytes else {
        context.callbackFailed = true
        return 1
    }

    let ordinal = Int(generated)
    guard ordinal > context.lastOrdinal else { return 0 }
    let data = Data(bytes: bytes, count: length)
    guard let text = String(data: data, encoding: .utf8), !text.isEmpty else { return 0 }

    do {
        let update = try MultimodalTextUpdate(sequence: ordinal, text: text)
        context.lastOrdinal = ordinal
        context.update?(update)
        return Task.isCancelled ? 1 : 0
    } catch {
        context.callbackFailed = true
        return 1
    }
}

@available(iOS 16.0, macOS 13.0, *)
private func weMMVideoResult(
    _ rawContext: UnsafeMutableRawPointer?,
    _ bytes: UnsafePointer<CChar>?,
    _ length: Int
) {
    guard let rawContext else { return }
    let context = Unmanaged<WeMMVideoCallbackContext>.fromOpaque(rawContext).takeUnretainedValue()
    guard context.finalData == nil, length >= 0 else { return }
    guard length == 0 || bytes != nil else {
        context.callbackFailed = true
        return
    }
    if length == 0 {
        context.finalData = Data()
    } else if let bytes {
        context.finalData = Data(bytes: bytes, count: length)
    }
}
