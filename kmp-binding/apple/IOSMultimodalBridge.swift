import Foundation
import ODAIKitMultimodal

#if ODAI_DEMO_BINDING
  @preconcurrency import ODAIKitDemo
#else
  @preconcurrency import ODAIKitBinding
#endif

/// Owns the client; admission, providers and callbacks stay on MainActor.
@MainActor
public final class IOSMultimodalBridge: NSObject, @preconcurrency MultimodalVideoBridge {
  private let client: MultimodalGenerationClient?
  private let inputs: any IOSMultimodalInputProvider
  private let videoInputs: (any IOSMultimodalInputProvider)?
  private enum NativeRequest {
    case image(MultimodalRequest)
    case video(MultimodalVideoRequest)
  }
  private var tasks: [UUID: IOSMultimodalTask] = [:]
  private var active: IOSMultimodalTask?
  private var closed = false
  private var closeFinished = false
  private var closeWaiters: [any MultimodalBridgeCloseCallback] = []
  private var closeWorker: Task<Void, Never>?

  public init(
    client: MultimodalGenerationClient?, inputs: any IOSMultimodalInputProvider,
    videoInputs: (any IOSMultimodalInputProvider)? = nil
  ) {
    self.client = client
    self.inputs = inputs
    self.videoInputs = videoInputs
  }

  public func queryMultimodalBackends(callback: any MultimodalBridgeBackendsCallback)
    -> any BridgeTask
  {
    if let error = admission(generate: false) {
      callback.onMultimodalFailure(failure: error)
      return IOSMultimodalTask()
    }
    guard let client else {
      callback.onMultimodalFailure(failure: fail(.backendUnavailable, "backendNotLinked"))
      return IOSMultimodalTask()
    }
    return submit(
      operation: {
        try Task.checkCancellation()
        return try await client.listBackends().map { descriptor in
          let a = descriptor.availability
          return MultimodalBridgeBackend(
            backendId: descriptor.backendId,
            availability: BridgeAvailability(
              available: a.status == .available,
              reason: a.reason?.rawValue, backendId: descriptor.backendId, minimumOS: a.minOS))
        }
      }, result: { callback.onMultimodalBackends(backends: $0) },
      failure: { callback.onMultimodalFailure(failure: $0) },
      cancelled: { callback.onMultimodalCancelled() })
  }

  public func generateMultimodal(
    request: MultimodalBridgeRequest, selection: any MultimodalBridgeSelection,
    callback: any MultimodalBridgeGenerateCallback
  ) -> any BridgeTask {
    generate(
      reference: request.inputReference, provider: inputs, selection: selection, callback: callback
    ) { url in
      .image(
        try MultimodalRequest(
          imageFile: url, prompt: request.prompt,
          maxOutputTokens: Int(request.maxOutputTokens)))
    }
  }

  public func generateMultimodalVideo(
    request: MultimodalBridgeVideoRequest, selection: any MultimodalBridgeSelection,
    callback: any MultimodalBridgeGenerateCallback
  ) -> any BridgeTask {
    generate(
      reference: request.inputReference, provider: videoInputs, selection: selection,
      callback: callback
    ) { url in
      .video(
        try MultimodalVideoRequest(
          videoFile: url, prompt: request.prompt,
          maxOutputTokens: Int(request.maxOutputTokens)))
    }
  }

  private func generate(
    reference: String, provider: (any IOSMultimodalInputProvider)?,
    selection: any MultimodalBridgeSelection, callback: any MultimodalBridgeGenerateCallback,
    makeRequest: (URL) throws -> NativeRequest
  ) -> any BridgeTask {
    if let error = admission(generate: true) {
      callback.onMultimodalFailure(failure: error)
      return IOSMultimodalTask()
    }
    guard let client else {
      callback.onMultimodalFailure(failure: fail(.backendUnavailable, "backendNotLinked"))
      return IOSMultimodalTask()
    }
    guard let selected = native(selection) else {
      callback.onMultimodalFailure(failure: fail(.invalidInput))
      return IOSMultimodalTask()
    }
    let lease: any IOSMultimodalInputLease
    let nativeRequest: NativeRequest
    guard let provider else {
      callback.onMultimodalFailure(failure: fail(.invalidInput))
      return IOSMultimodalTask()
    }
    do {
      let acquired = try provider.acquire(reference: reference)
      do {
        nativeRequest = try makeRequest(acquired.url)
      } catch {
        acquired.release()
        throw error
      }
      lease = acquired
    } catch {
      callback.onMultimodalFailure(failure: fail(.invalidInput))
      return IOSMultimodalTask()
    }

    let handle = IOSMultimodalTask()
    return submit(
      generate: true, handle: handle,
      operation: {
        let outcome: Result<MultimodalGenerationResult, Error>
        do {
          try Task.checkCancellation()
          let update: @Sendable (MultimodalTextUpdate) -> Void = {
            [weak bridge = self] update in
            Task { @MainActor in
              guard let bridge, bridge.active === handle, !bridge.closed, handle.acceptsUpdates,
                update.sequence > handle.lastSequence,
                let sequence = Int32(exactly: update.sequence)
              else { return }
              handle.lastSequence = update.sequence
              callback.onMultimodalUpdate(
                update: MultimodalBridgeUpdate(sequence: sequence, text: update.text))
            }
          }
          switch nativeRequest {
          case .image(let image):
            outcome = .success(try await client.generate(image, backend: selected, update: update))
          case .video(let video):
            outcome = .success(try await client.generate(video, backend: selected, update: update))
          }
        } catch { outcome = .failure(error) }
        // Uncancelled drain precedes synchronous lease release, even on immediate cancel.
        await Task.detached { await client.drain() }.value
        lease.release()
        return try outcome.get()
      },
      result: { value in
        let model = value.output.model
        callback.onMultimodalResult(
          output: MultimodalBridgeOutput(
            backendId: value.backendId, text: value.output.text,
            model: MultimodalBridgeIdentity(
              modelId: model.modelId, modelRevision: model.modelRevision,
              runtimeRevision: model.runtimeRevision)))
      }, failure: { callback.onMultimodalFailure(failure: $0) },
      cancelled: { callback.onMultimodalCancelled() })
  }

  public func closeMultimodal(callback: any MultimodalBridgeCloseCallback) {
    if closeFinished {
      callback.onMultimodalClosed()
      return
    }
    closed = true
    closeWaiters.append(callback)
    guard closeWorker == nil else { return }
    let pending = Array(tasks.values)
    for task in pending { task.cancel() }
    closeWorker = Task { @MainActor in
      for task in pending { await task.worker?.value }
      await client?.close()
      closeFinished = true
      closeWorker = nil
      let waiters = closeWaiters
      closeWaiters.removeAll()
      for waiter in waiters { waiter.onMultimodalClosed() }
    }
  }

  private func admission(generate: Bool) -> MultimodalBridgeFailure? {
    if closed { return fail(.closed) }
    if generate, active != nil { return fail(.busy) }
    return nil
  }
  private func native(_ selection: any MultimodalBridgeSelection) -> MultimodalBackendSelection? {
    if selection is MultimodalBridgeSelectionDefault { return .default }
    if let explicit = selection as? MultimodalBridgeSelectionExplicit {
      return .explicit(explicit.backendId)
    }
    return nil
  }

  private func submit<T>(
    generate: Bool = false, handle: IOSMultimodalTask = IOSMultimodalTask(),
    operation: @escaping @MainActor () async throws -> T,
    result: @escaping (T) -> Void, failure: @escaping (MultimodalBridgeFailure) -> Void,
    cancelled: @escaping () -> Void
  ) -> any BridgeTask {
    let id = UUID()
    tasks[id] = handle
    if generate { active = handle }
    handle.worker = Task { @MainActor in
      var outcome: Result<T, Error>
      do { outcome = .success(try await operation()) } catch { outcome = .failure(error) }
      let wasCancelled = handle.finish() || closed
      tasks.removeValue(forKey: id)
      if active === handle { active = nil }
      handle.worker = nil
      // Ownership cleanup precedes callbacks, including synchronous reentry.
      switch outcome {
      case .failure(let error)
      where !wasCancelled && !(error is CancellationError)
        && (error as? MultimodalClientError) != .cancelled:
        failure(map(error))
      case .failure: cancelled()
      case .success(let value): wasCancelled ? cancelled() : result(value)
      }
    }
    return handle
  }
  private func fail(_ code: MultimodalBridgeFailureCode, _ reason: String? = nil)
    -> MultimodalBridgeFailure
  {
    MultimodalBridgeFailure(code: code, reason: reason, domain: nil, platformCode: nil)
  }
  private func map(_ error: Error) -> MultimodalBridgeFailure {
    switch error {
    case MultimodalClientError.closed: return fail(.closed)
    case MultimodalClientError.busy: return fail(.busy)
    case MultimodalClientError.invalidInput: return fail(.invalidInput)
    case MultimodalClientError.backendNotRegistered: return fail(.backendNotRegistered)
    case MultimodalClientError.backendUnavailable(_, let reason):
      return fail(.backendUnavailable, reason.rawValue)
    default:
      return MultimodalBridgeFailure(
        code: .platform, reason: nil, domain: "ios.multimodal", platformCode: KotlinInt(int: 1))
    }
  }
}

/// Only cancellation arbitration and worker access are cross-thread; ordinals stay on MainActor.
private final class IOSMultimodalTask: NSObject, BridgeTask, @unchecked Sendable {
  private let lock = NSLock()
  private var didCancel = false, didFinish = false
  private var storedWorker: Task<Void, Never>?
  var lastSequence = -1
  var worker: Task<Void, Never>? {
    get { lock.withLock { storedWorker } }
    set {
      let cancel = lock.withLock {
        storedWorker = newValue
        return didCancel
      }
      if cancel { newValue?.cancel() }
    }
  }
  var acceptsUpdates: Bool { lock.withLock { !didCancel && !didFinish } }
  func cancel() {
    let task = lock.withLock { () -> Task<Void, Never>? in
      if didFinish { return nil }
      didCancel = true
      return storedWorker
    }
    task?.cancel()
  }
  func finish() -> Bool {
    lock.withLock {
      didFinish = true
      return didCancel
    }
  }
}
