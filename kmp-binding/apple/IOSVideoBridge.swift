import Foundation
import ODAIKitCore
import ODAIKitVideo
#if ODAI_DEMO_BINDING
@preconcurrency import ODAIKitDemo
#else
@preconcurrency import ODAIKitBinding
#endif

/// Owns the supplied client. Admission, resolvers and callbacks run on MainActor.
@MainActor
public final class IOSVideoBridge: NSObject, @preconcurrency VideoBridge {
    private let client: VideoProcessingClient?
    private let inputs: any IOSVideoInputProvider
    private var tasks: [UUID: IOSVideoTask] = [:]
    private var active: IOSVideoTask?
    private var closed = false
    private var closeFinished = false
    private var closeWaiters: [any VideoBridgeCloseCallback] = []
    private var closeWorker: Task<Void, Never>?
    private class Output {
        let lease: any VideoOutputLease
        var waiters: [any VideoBridgeReleaseCallback] = []
        var releasing = false
        init(_ lease: any VideoOutputLease) { self.lease = lease }
    }
    private var outputs: [String: Output] = [:]
    private let outputPrefix = "video-\(UUID().uuidString):"
    private var issuedOutputs: Int64 = 0

    public init(client: VideoProcessingClient?, inputs: any IOSVideoInputProvider) {
        self.client = client; self.inputs = inputs
    }

    public func queryBackends(callback: any VideoBridgeBackendsCallback) -> any BridgeTask {
        if let error = admission(process: false) { callback.onFailure(failure_: error); return IOSVideoTask() }
        guard let client else { callback.onFailure(failure_: fail(.backendUnavailable, "backendNotLinked")); return IOSVideoTask() }
        return submit(operation: {
            try Task.checkCancellation()
            return try await client.listBackends().map { descriptor in
                let availability = descriptor.availability
                return VideoBridgeBackend(backendId: descriptor.backendId,
                    availability: BridgeAvailability(available: availability.status == .available,
                        reason: availability.reason?.rawValue, backendId: descriptor.backendId, minimumOS: availability.minOS),
                    supportedScaleFactors: descriptor.supportedScaleFactors.compactMap { Int32(exactly: $0).map { KotlinInt(int: $0) } },
                    supportedAudioPolicies: descriptor.supportedAudioPolicies.map { $0 == .preserve ? .preserve : .discard })
            }
        }, result: { callback.onResult(backends_: $0) }, failure: { callback.onFailure(failure_: $0) },
           cancelled: { callback.onCancelled() })
    }

    public func process(request: VideoBridgeRequest, selection: any VideoBridgeSelection,
                        callback: any VideoBridgeProcessCallback) -> any BridgeTask {
        if let error = admission(process: true) { callback.onFailure(failure_: error); return IOSVideoTask() }
        guard let client else { callback.onFailure(failure_: fail(.backendUnavailable, "backendNotLinked")); return IOSVideoTask() }
        guard let selected = native(selection) else { callback.onFailure(failure_: fail(.invalidInput)); return IOSVideoTask() }
        let lease: any IOSVideoInputLease
        let nativeRequest: VideoProcessingRequest
        do {
            let acquired = try inputs.acquire(reference: request.inputReference)
            do {
                nativeRequest = try VideoProcessingRequest(inputFile: acquired.url, scaleFactor: Int(request.scaleFactor),
                    audioPolicy: request.audioPolicy == .preserve ? .preserve : .discard)
            } catch { acquired.release(); throw error }
            lease = acquired
        } catch { callback.onFailure(failure_: fail(.invalidInput)); return IOSVideoTask() }
        let handle = IOSVideoTask()
        return submit(process: true, handle: handle, operation: {
            let outcome: Result<VideoProcessingResult, Error>
            do {
                try Task.checkCancellation()
                outcome = .success(try await client.process(nativeRequest, backend: selected) { [weak self] update in
                    Task { @MainActor in
                        guard let self, self.active === handle, !self.closed, handle.acceptsProgress,
                              update.completedFrames >= handle.completedFrames,
                              let completed = Int32(exactly: update.completedFrames) else { return }
                        if let total = update.totalFrames, Int32(exactly: total) == nil { return }
                        handle.completedFrames = update.completedFrames
                        callback.onProgress(progress: VideoBridgeProgress(completedFrames: completed,
                            totalFrames: update.totalFrames.map { KotlinInt(int: Int32($0)) }))
                    }
                })
            } catch { outcome = .failure(error) }
            await Task { await client.drain() }.value
            lease.release()
            let value = try outcome.get()
            // Kotlin Int is 32 bit. Reject unrepresentable native metadata before transfer.
            let info = value.output.info
            guard [info.width, info.height, info.frameCount, info.audioTrackCount].allSatisfy({ Int32(exactly: $0) != nil }) else {
                try await Task { try await value.output.release() }.value
                throw VideoClientError.invalidInput("unrepresentable output metadata")
            }
            return value
        }, result: { value in
            self.issuedOutputs += 1
            let reference = self.outputPrefix + String(self.issuedOutputs)
            self.outputs[reference] = Output(value.output)
            let info = value.output.info
            callback.onResult(output_: VideoBridgeOutput(reference: reference, backendId: value.backendId,
                width: Int32(info.width), height: Int32(info.height), frameCount: Int32(info.frameCount),
                durationSeconds: info.durationSeconds, audioTrackCount: Int32(info.audioTrackCount)))
        }, failure: { callback.onFailure(failure_: $0) }, cancelled: { callback.onCancelled() },
           discard: { try await $0.output.release() })
    }

    /// Only native hosts resolve files; shared state retains the opaque reference.
    public func outputURL(reference: String) -> URL? {
        guard let output = outputs[reference], !output.releasing else { return nil }
        return output.lease.file
    }

    public func releaseOutput(reference: String, callback: any VideoBridgeReleaseCallback) {
        guard let output = outputs[reference] else {
            if wasIssued(reference) { callback.onReleased() }
            else { callback.onFailure(failure_: fail(.invalidInput)) }
            return
        }
        output.waiters.append(callback)
        guard !output.releasing else { return }
        output.releasing = true
        Task { @MainActor in
            let failure: VideoBridgeFailure?
            do { try await output.lease.release(); outputs.removeValue(forKey: reference); failure = nil }
            catch { output.releasing = false; failure = map(error) }
            let waiters = output.waiters; output.waiters.removeAll()
            waiters.forEach { waiter in
                if let failure { waiter.onFailure(failure_: failure) } else { waiter.onReleased() }
            }
        }
    }

    public func close(callback_: any VideoBridgeCloseCallback) {
        if closeFinished { callback_.onClosed(); return }
        closed = true; closeWaiters.append(callback_)
        guard closeWorker == nil else { return }
        let pending = Array(tasks.values)
        pending.forEach { $0.cancel() }
        closeWorker = Task { @MainActor in
            for task in pending { await task.worker?.value }
            await client?.close()
            closeFinished = true
            let waiters = closeWaiters; closeWaiters.removeAll(); closeWorker = nil
            waiters.forEach { $0.onClosed() }
        }
    }

    private func admission(process: Bool) -> VideoBridgeFailure? {
        if closed { return fail(.closed) }
        if process && active != nil { return fail(.busy) }
        return nil
    }
    private func native(_ selection: any VideoBridgeSelection) -> VideoBackendSelection? {
        if selection is VideoBridgeSelectionDefault { return .default }
        if let selection = selection as? VideoBridgeSelectionExplicit { return .explicit(selection.backendId) }
        return nil
    }
    private func wasIssued(_ reference: String) -> Bool {
        guard reference.hasPrefix(outputPrefix) else { return false }
        let suffix = String(reference.dropFirst(outputPrefix.count))
        guard let number = Int64(suffix), number > 0, number <= issuedOutputs else { return false }
        return suffix == String(number)
    }

    private func submit<T>(process: Bool = false, handle: IOSVideoTask = IOSVideoTask(),
                           operation: @escaping @MainActor () async throws -> T,
                           result: @escaping (T) -> Void, failure: @escaping (VideoBridgeFailure) -> Void,
                           cancelled: @escaping () -> Void,
                           discard: @escaping @MainActor (T) async throws -> Void = { _ in }) -> any BridgeTask {
        let id = UUID(); tasks[id] = handle
        if process { active = handle }
        handle.worker = Task { @MainActor in
            var outcome: Result<T, Error>
            do { outcome = .success(try await operation()) } catch { outcome = .failure(error) }
            let wasCancelled = handle.finish() || closed
            if wasCancelled, case .success(let value) = outcome {
                do { try await Task { try await discard(value) }.value }
                catch { outcome = .failure(error) }
            }
            tasks.removeValue(forKey: id)
            if active === handle { active = nil }
            handle.worker = nil
            // All native cleanup and ownership changes precede reentrant callbacks.
            switch outcome {
            case .failure(let error) where !(error is CancellationError) && (error as? VideoClientError) != .cancelled:
                failure(map(error))
            case .failure: cancelled()
            case .success(let value): if wasCancelled { cancelled() } else { result(value) }
            }
        }
        return handle
    }
    private func fail(_ code: VideoBridgeFailureCode, _ reason: String? = nil) -> VideoBridgeFailure {
        VideoBridgeFailure(code: code, reason: reason, domain: nil, platformCode: nil)
    }
    private func map(_ error: Error) -> VideoBridgeFailure {
        switch error {
        case VideoClientError.closed: return fail(.closed)
        case VideoClientError.busy: return fail(.busy)
        case VideoClientError.invalidInput: return fail(.invalidInput)
        case VideoClientError.backendNotRegistered: return fail(.backendNotRegistered)
        case VideoClientError.backendUnavailable(_, let reason): return fail(.backendUnavailable, reason.rawValue)
        case VideoClientError.unsupportedScaleFactor: return fail(.unsupportedScaleFactor)
        case VideoClientError.unsupportedAudioPolicy: return fail(.unsupportedAudioPolicy)
        default: return VideoBridgeFailure(code: .platform, reason: nil, domain: "ios.video", platformCode: KotlinInt(int: 1))
        }
    }
}

/// cancel may arrive from any thread; adapter registries remain on MainActor.
private final class IOSVideoTask: NSObject, BridgeTask, @unchecked Sendable {
    private let lock = NSLock()
    private var didCancel = false
    private var didFinish = false
    private var storedWorker: Task<Void, Never>?
    var completedFrames = -1 // MainActor only
    var worker: Task<Void, Never>? {
        get { lock.withLock { storedWorker } }
        set {
            let cancel = lock.withLock { storedWorker = newValue; return didCancel }
            if cancel { newValue?.cancel() }
        }
    }
    var acceptsProgress: Bool { lock.withLock { !didCancel && !didFinish } }
    func cancel() {
        let task = lock.withLock { () -> Task<Void, Never>? in
            if didFinish { return nil }; didCancel = true; return storedWorker
        }
        task?.cancel()
    }
    func finish() -> Bool { lock.withLock { didFinish = true; return didCancel } }
}
