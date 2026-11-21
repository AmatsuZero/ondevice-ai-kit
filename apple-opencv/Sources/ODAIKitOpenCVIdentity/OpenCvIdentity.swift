#if os(iOS)
import Foundation
import ODAIOpenCVNative
import ODAIKitCore
import ODAIKitIdentity
import ODAIKitModelStore

public enum OpenCvIdentity {
    /// Opens only the fixed, hash-verified prototype manifest. Call off the UI path.
    public static func open(modelStore: ModelStore,
                            diagnostic: (@Sendable (OpenCvIdentityDiagnostic) -> Void)? = nil) async throws -> LocalIdentityBackend {
        try Task.checkCancellation()
        let lease = try await modelStore.acquire(OpenCvIdentityModel.ref)
        var runtime: OpenCvIdentityRuntime?
        var backend: LocalIdentityBackend?
        do {
            let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys]
            guard try encoder.encode(lease.manifest) == encoder.encode(OpenCvIdentityModel.manifest()) else {
                throw IdentityError(.embeddingSpaceMismatch)
            }
            try Task.checkCancellation()
            let opened = try OpenCvIdentityRuntime(directory: lease.directory, diagnostic: diagnostic)
            runtime = opened
            try Task.checkCancellation()
            let owned = try await LocalIdentityBackend.open(modelStore: modelStore,
                modelRef: OpenCvIdentityModel.ref, runtime: opened)
            backend = owned
            try Task.checkCancellation()
            await lease.close()
            return owned
        } catch {
            if let backend { await backend.closeAndWait() } else { runtime?.close() }
            await lease.close()
            throw error
        }
    }
}

/// Serializes CPU primitives and drains them before destroying native networks.
private final class OpenCvIdentityRuntime: IdentityBackend, @unchecked Sendable {
    let embeddingSpace = OpenCvIdentityModel.embeddingSpace
    var backendId: String { embeddingSpace.backendId }
    var backendVersion: String { embeddingSpace.backendVersion }
    var modelBinding: ModelBinding { embeddingSpace.modelBinding }
    private let state = NSLock()
    private let gate = NSLock()
    private var closed = false
    private var native: ODAIOpenCVRuntime?
    private let diagnostic: (@Sendable (OpenCvIdentityDiagnostic) -> Void)?

    init(directory: URL, diagnostic: (@Sendable (OpenCvIdentityDiagnostic) -> Void)?) throws {
        self.diagnostic = diagnostic
        guard ODAIOpenCVRuntime.runtimeVersion() == OpenCvIdentityModel.version else {
            throw IdentityError(.backendUnavailable)
        }
        do {
            native = try ODAIOpenCVRuntime(detectorPath: directory.appendingPathComponent(OpenCvIdentityModel.detector).path,
                recognizerPath: directory.appendingPathComponent(OpenCvIdentityModel.recognizer).path)
        } catch {
            throw OpenCvIdentityNativeFailure(error).identityError(backendId: backendId,
                modelBinding: modelBinding, diagnostic: diagnostic)
        }
    }
    private var isClosed: Bool { state.lock(); defer { state.unlock() }; return closed }
    private func failure(_ code: IdentityErrorCode) -> IdentityError {
        IdentityError(code, backendId: backendId, modelBinding: modelBinding)
    }
    func availability() -> CapabilityAvailability {
        .init(status: isClosed ? .unavailable : .available, reason: isClosed ? .closed : nil, backendId: backendId)
    }
    func evaluateQuality(_ probe: IdentityProbe) throws -> IdentityQuality {
        try evaluateQuality(probe, stopSignal: IdentityStopSignal())
    }
    func evaluateQuality(_ probe: IdentityProbe, stopSignal: IdentityStopSignal) throws -> IdentityQuality {
        let result = try process(probe, feature: false, signal: stopSignal)
        return try IdentityQuality(faceCount: result.faceCount, score: result.usableGeometry ? 1 : 0)
    }
    func embed(_ probe: IdentityProbe) throws -> IdentityEmbedding { try embed(probe, stopSignal: IdentityStopSignal()) }
    func embed(_ probe: IdentityProbe, stopSignal: IdentityStopSignal) throws -> IdentityEmbedding {
        let result = try process(probe, feature: true, signal: stopSignal)
        guard let data = result.feature, data.count == 128 * MemoryLayout<Float>.size else { throw failure(.invalidInput) }
        var values = [Float](repeating: 0, count: 128)
        _ = values.withUnsafeMutableBytes { data.copyBytes(to: $0) }
        return try IdentityEmbedding(embeddingSpace: embeddingSpace, values: OpenCvIdentityFeatures.normalized(values))
    }
    private func process(_ probe: IdentityProbe, feature: Bool, signal: IdentityStopSignal) throws -> ODAIOpenCVResult {
        do { return try processLocked(probe, feature: feature, signal: signal) }
        catch let nativeFailure as OpenCvIdentityNativeFailure {
            // The native gate is released before calling client code; reentry/close cannot deadlock.
            let error = nativeFailure.identityError(backendId: backendId, modelBinding: modelBinding,
                diagnostic: diagnostic)
            try signal.check()
            guard !isClosed else { throw failure(.closed) }
            throw error
        }
    }
    private func processLocked(_ probe: IdentityProbe, feature: Bool, signal: IdentityStopSignal) throws -> ODAIOpenCVResult {
        guard !isClosed else { throw failure(.closed) }
        gate.lock(); defer { gate.unlock() }
        guard !isClosed, let native else { throw failure(.closed) }
        try signal.check()
        guard let bytes = probe.encodedImage else { throw failure(.invalidInput) }
        try OpenCvIdentityInput.validate(bytes)
        do {
            let result = try native.process(bytes, includeFeature: feature, shouldStop: { [self] in
                isClosed || (try? signal.check()) == nil
            })
            try signal.check()
            guard !isClosed else { throw failure(.closed) }
            return result
        } catch {
            if let error = error as? IdentityError { throw error }
            throw OpenCvIdentityNativeFailure(error)
        }
    }
    func close() {
        state.lock(); closed = true; state.unlock()
        gate.lock(); native = nil; gate.unlock()
    }
}
#endif
