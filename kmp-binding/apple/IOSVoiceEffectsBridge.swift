import Foundation
import ODAIKitVoiceEffects
#if ODAI_DEMO_BINDING
@preconcurrency import ODAIKitDemo
#else
@preconcurrency import ODAIKitBinding
#endif

@MainActor public protocol IOSVoiceEffectsInputLease: AnyObject {
    var url: URL { get }
    func release() async
}
@MainActor public protocol IOSVoiceEffectsInputProvider: AnyObject {
    func acquire(reference: String) throws -> any IOSVoiceEffectsInputLease
}

@MainActor
public final class IOSVoiceEffectsBridge: NSObject, @preconcurrency VoiceEffectsBridge {
    private let client: VoiceEffectsClient?
    private let inputs: any IOSVoiceEffectsInputProvider
    private var active: IOSVoiceEffectsTask?
    private var closed = false
    private var closeFinished = false
    private var closeWorker: Task<Void, Never>?
    private var closeWaiters: [any VoiceEffectsBridgeCloseCallback] = []
    private let prefix = "voice-\(UUID().uuidString):"
    private var issued: Int64 = 0
    private final class Output {
        let value: VoiceEffectsOutput
        var releasing = false
        var waiters: [any VoiceEffectsBridgeReleaseCallback] = []
        init(_ value: VoiceEffectsOutput) { self.value = value }
    }
    private var outputs: [String: Output] = [:]
    public init(client: VoiceEffectsClient?, inputs: any IOSVoiceEffectsInputProvider) {
        self.client = client; self.inputs = inputs
    }
    public func process(request: VoiceEffectsBridgeRequest, callback: any VoiceEffectsBridgeProcessCallback) -> any BridgeTask {
        if closed { callback.onFailure(failure__: fail(.closed)); return IOSVoiceEffectsTask() }
        if active != nil { callback.onFailure(failure__: fail(.busy)); return IOSVoiceEffectsTask() }
        guard let client else { callback.onFailure(failure__: fail(.backendUnavailable, "backendNotLinked")); return IOSVoiceEffectsTask() }
        let input: any IOSVoiceEffectsInputLease
        do { input = try inputs.acquire(reference: request.inputReference) }
        catch { callback.onFailure(failure__: fail(.invalidInput)); return IOSVoiceEffectsTask() }
        let handle = IOSVoiceEffectsTask(); active = handle
        let preset: VoiceEffectsPreset = request.preset == .high ? .high : request.preset == .low ? .low : .original
        let nativeRequest = VoiceEffectsRequest(inputFile: input.url, preset: preset)
        handle.retry = { [weak self, weak handle] in
            guard let self, let handle else { return }
            Task { @MainActor in try? await self.recover(handle) }
        }
        handle.worker = Task { @MainActor in
            var outcome: Result<VoiceEffectsOutput, Error>
            do { try Task.checkCancellation(); outcome = .success(try await client.process(nativeRequest)) }
            catch { outcome = .failure(error) }
            // A fresh task does not inherit caller cancellation. Keep the lease
            // until the native worker has actually relinquished the file.
            await Task { await client.drain() }.value
            await input.release()
            let cancelled = handle.finish() || closed
            if cancelled, case .success(let output) = outcome {
                handle.discarded = output
                await withCheckedContinuation { handle.discardWaiter = $0
                    Task { @MainActor in try? await self.recover(handle) }
                }
                outcome = .failure(CancellationError())
            }
            active = nil; handle.worker = nil; handle.retry = nil
            switch outcome {
            case .success(let value):
                issued += 1
                let reference = prefix + String(issued); outputs[reference] = Output(value)
                callback.onResult(output__: VoiceEffectsBridgeOutput(reference: reference, backendId: value.backendId,
                    sampleRate: Int32(value.sampleRate), frameCount: value.frameCount, durationSeconds: value.durationSeconds))
            case .failure(let error):
                if cancelled || error is CancellationError { callback.onCancelled() }
                else { callback.onFailure(failure__: map(error)) }
            }
        }
        return handle
    }
    private func recover(_ handle: IOSVoiceEffectsTask) async throws {
        await client?.drain()
        if let output = handle.discarded {
            try await client?.releaseOutput(id: output.id)
            handle.discarded = nil
            handle.discardWaiter?.resume(); handle.discardWaiter = nil
        }
    }
    public func outputURL(reference: String) -> URL? {
        guard let output = outputs[reference], !output.releasing else { return nil }
        return output.value.audioFile
    }
    public func releaseOutput(reference: String, callback_: any VoiceEffectsBridgeReleaseCallback) {
        guard let output = outputs[reference] else {
            let suffix = String(reference.dropFirst(prefix.count))
            if reference.hasPrefix(prefix), let number = Int64(suffix), number > 0, number <= issued, suffix == String(number) { callback_.onReleased() }
            else { callback_.onFailure(failure__: fail(.invalidInput)) }
            return
        }
        output.waiters.append(callback_)
        guard !output.releasing else { return }
        output.releasing = true
        Task { @MainActor in
            var failure: VoiceEffectsBridgeFailure?
            do { try await client?.releaseOutput(id: output.value.id); outputs.removeValue(forKey: reference) }
            catch { output.releasing = false; failure = map(error) }
            let waiters = output.waiters; output.waiters.removeAll()
            waiters.forEach { if let failure { $0.onFailure(failure__: failure) } else { $0.onReleased() } }
        }
    }
    public func close(callback__: any VoiceEffectsBridgeCloseCallback) {
        closed = true
        if closeFinished { callback__.onClosed(); return }
        closeWaiters.append(callback__)
        active?.cancel()
        guard closeWorker == nil else { return }
        closeWorker = Task { @MainActor in
            await client?.close()
            var failure: VoiceEffectsBridgeFailure?
            do {
                if let handle = active { try await recover(handle); await handle.worker?.value }
                closeFinished = true
            } catch { failure = map(error) }
            closeWorker = nil
            let waiters = closeWaiters; closeWaiters.removeAll()
            waiters.forEach { if let failure { $0.onFailure(failure__: failure) } else { $0.onClosed() } }
        }
    }
    private func fail(_ code: VoiceEffectsBridgeFailureCode, _ reason: String? = nil) -> VoiceEffectsBridgeFailure {
        VoiceEffectsBridgeFailure(code: code, reason: reason, domain: nil, platformCode: nil)
    }
    private func map(_ error: Error) -> VoiceEffectsBridgeFailure {
        switch error {
        case VoiceEffectsError.closed: return fail(.closed)
        case VoiceEffectsError.busy: return fail(.busy)
        case VoiceEffectsError.invalidInput: return fail(.invalidInput)
        case VoiceEffectsError.unsupportedInput: return fail(.unsupportedInput)
        case VoiceEffectsError.backendUnavailable: return fail(.backendUnavailable, "backendNotLinked")
        default: return VoiceEffectsBridgeFailure(code: .platform, reason: nil, domain: "ios.voiceeffects", platformCode: KotlinInt(int: 1))
        }
    }
}

private final class IOSVoiceEffectsTask: NSObject, BridgeTask, @unchecked Sendable {
    private let lock = NSLock()
    private var cancelled = false
    private var finished = false
    private var storedWorker: Task<Void, Never>?
    // MainActor-only ownership below; cancel schedules recovery onto that actor.
    @MainActor var retry: (() -> Void)?
    @MainActor var discarded: VoiceEffectsOutput?
    @MainActor var discardWaiter: CheckedContinuation<Void, Never>?
    var worker: Task<Void, Never>? {
        get { lock.withLock { storedWorker } }
        set { let cancelled = lock.withLock { storedWorker = newValue; return self.cancelled }; if cancelled { newValue?.cancel() } }
    }
    var isCancelled: Bool { lock.withLock { cancelled } }
    func finish() -> Bool { lock.withLock { finished = true; return cancelled } }
    func cancel() {
        let task = lock.withLock { () -> Task<Void, Never>? in
            guard !finished else { return nil }; cancelled = true; return storedWorker
        }
        task?.cancel()
        Task { @MainActor in self.retry?() }
    }
}
