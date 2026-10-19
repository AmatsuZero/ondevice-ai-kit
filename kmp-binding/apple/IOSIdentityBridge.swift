import Foundation
@preconcurrency import ODAIKitBinding
import ODAIKitIdentity

/// iOS host adapter. It borrows native stores and engines; only bridge tasks,
/// builders and sessions are owned by this object.
public final class IOSIdentityBridge: NSObject, IdentityBridge {
  private let store: IdentityStore
  private let indexes: IndexStore
  private let registry: IdentityBackendRegistry<any IdentityBackend>
  private let verification: FaceVerification
  private let identification: FaceIdentification
  private let liveness: FaceLiveness?
  private let probeResolver: (IdentityProbeReference) throws -> IdentityProbe
  private let callbackQueue: DispatchQueue
  private let lock = NSLock()
  private var closed = false
  private var tasks: [UUID: IOSIdentityTask] = [:]
  private var builders: [String: GalleryBuilder] = [:]
  private var sessions: [String: IOSLivenessSession] = [:]

  public init(
    store: IdentityStore, indexes: IndexStore,
    registry: IdentityBackendRegistry<any IdentityBackend>,
    verification: FaceVerification, identification: FaceIdentification,
    liveness: FaceLiveness? = nil,
    callbackQueue: DispatchQueue = .main,
    probeResolver: @escaping (IdentityProbeReference) throws -> IdentityProbe
  ) {
    self.store = store
    self.indexes = indexes
    self.registry = registry
    self.verification = verification
    self.identification = identification
    self.liveness = liveness
    self.callbackQueue = callbackQueue
    self.probeResolver = probeResolver
  }

  public func availability() -> IdentityBridgeAvailability {
    if isClosed { return IdentityBridgeAvailabilityUnavailable(reason: .closed) }
    do {
      let backend = try registry.select(.default)
      return backend.availability().status == .available
        ? IdentityBridgeAvailabilityAvailable(backendId: backend.backendId)
        : IdentityBridgeAvailabilityUnavailable(reason: .backendUnavailable)
    } catch { return IdentityBridgeAvailabilityUnavailable(reason: .backendUnavailable) }
  }

  public func createCandidate(grant: IdentityAuthorizationGrant, callback: IdentityCallback)
    -> IdentityBridgeTask
  {
    submit(callback) { task in
      let native = try await self.store.createCandidate(
        try AuthorizationGrant.decodeJSON(grant.canonicalValue))
      return IdentityCandidateReference(canonicalValue: native.canonicalJSON())
    }
  }
  public func enroll(
    probe: IdentityProbeReference, candidate: IdentityCandidateReference,
    grant: IdentityAuthorizationGrant, options: IdentityEnrollmentOptions,
    backend: ODAIKitBinding.IdentityBackendSelection, callback: IdentityCallback
  ) -> IdentityBridgeTask {
    submit(callback) { task in
      let native = try await self.store.enroll(
        try self.probeResolver(probe),
        candidate: try CandidateRef.decodeJSON(candidate.canonicalValue),
        grant: try AuthorizationGrant.decodeJSON(grant.canonicalValue),
        options: try EnrollmentOptions(
          qualityFloor: options.qualityFloor, qualityPolicyId: options.qualityPolicyId,
          revision: options.revision),
        backend: try self.selectBackend(backend), stopSignal: task.signal)
      return native.bridgeValue()
    }
  }
  public func verify(
    probe: IdentityProbeReference, template: IdentityTemplateReference,
    grant: IdentityAuthorizationGrant, options: IdentityVerificationOptions,
    backend: ODAIKitBinding.IdentityBackendSelection, callback: IdentityCallback
  ) -> IdentityBridgeTask {
    submit(callback) { task in
      let nativeTask = try await self.verification.verify(
        probe: try self.probeResolver(probe),
        templateRef: try TemplateRef.decodeJSON(template.canonicalValue),
        grant: try AuthorizationGrant.decodeJSON(grant.canonicalValue),
        options: try VerificationOptions(
          policy: ThresholdPolicy.decodeJSON(options.policy.canonicalValue),
          revision: options.revision),
        backend: backend.native())
      task.setNativeCancel { nativeTask.cancel() }
      if task.isCancelled { nativeTask.cancel() }
      return try await nativeTask.result().bridgeValue()
    }
  }
  public func createGalleryBuilder(
    baseGallery: IdentityGalleryReference?, grant: IdentityAuthorizationGrant,
    options: IdentityGalleryOptions, backend: ODAIKitBinding.IdentityBackendSelection,
    callback: IdentityCallback
  ) -> IdentityBridgeTask {
    submit(callback) { task in
      let native = try await self.indexes.createGalleryBuilder(
        baseGalleryRef: baseGallery.map { try GalleryRef.decodeJSON($0.canonicalValue) },
        grant: try AuthorizationGrant.decodeJSON(grant.canonicalValue),
        policy: try ThresholdPolicy.decodeJSON(options.policy.canonicalValue),
        backend: try self.selectBackend(backend), aggregationPolicyId: options.aggregationPolicyId)
      let id = UUID().uuidString.lowercased()
      let accepted = self.lock.withLock {
        if self.closed { return false }
        self.builders[id] = native
        return true
      }
      guard accepted else {
        await native.close()
        throw IdentityError(.closed)
      }
      return IdentityGalleryBuilderReference(canonicalValue: id)
    }
  }
  public func addTemplate(
    builder: IdentityGalleryBuilderReference, template: IdentityTemplateReference,
    grant: IdentityAuthorizationGrant, callback: IdentityCallback
  ) -> IdentityBridgeTask {
    submit(callback) { task in
      let native = try self.builder(builder)
      try await native.addTemplate(
        try TemplateRef.decodeJSON(template.canonicalValue),
        grant: try AuthorizationGrant.decodeJSON(grant.canonicalValue))
      return IdentityBridgeValues.shared.unitValue
    }
  }
  public func removeTemplate(
    builder: IdentityGalleryBuilderReference, template: IdentityTemplateReference,
    grant: IdentityAuthorizationGrant, callback: IdentityCallback
  ) -> IdentityBridgeTask {
    submit(callback) { task in
      let native = try self.builder(builder)
      try await native.removeTemplate(
        try TemplateRef.decodeJSON(template.canonicalValue),
        grant: try AuthorizationGrant.decodeJSON(grant.canonicalValue))
      return IdentityBridgeValues.shared.unitValue
    }
  }
  public func publishGallery(builder: IdentityGalleryBuilderReference, callback: IdentityCallback)
    -> IdentityBridgeTask
  {
    submit(callback) { task in
      let native = try self.builder(builder)
      task.setNativeCancel { Task { await native.close() } }
      return IdentityGalleryReference(canonicalValue: try await native.publish().canonicalJSON())
    }
  }
  public func closeGalleryBuilder(
    builder: IdentityGalleryBuilderReference, callback: IdentityCallback
  ) -> IdentityBridgeTask {
    submit(callback) { task in
      let native = self.takeBuilder(builder)
      await native?.close()
      return IdentityBridgeValues.shared.unitValue
    }
  }
  public func identify(
    probe: IdentityProbeReference, gallery: IdentityGalleryReference,
    grant: IdentityAuthorizationGrant, options: IdentityIdentificationOptions,
    backend: ODAIKitBinding.IdentityBackendSelection, callback: IdentityCallback
  ) -> IdentityBridgeTask {
    submit(callback) { task in
      let nativeTask = try await self.identification.identify(
        probe: try self.probeResolver(probe),
        galleryRef: try GalleryRef.decodeJSON(gallery.canonicalValue),
        grant: try AuthorizationGrant.decodeJSON(grant.canonicalValue),
        options: try IdentificationOptions(
          topK: Int(options.topK),
          policy: options.policy.map { try ThresholdPolicyRef(id: $0.id, revision: $0.revision) },
          revision: options.revision),
        backend: backend.native())
      task.setNativeCancel { nativeTask.cancel() }
      if task.isCancelled { nativeTask.cancel() }
      return try await nativeTask.result().bridgeValue()
    }
  }
  public func openLivenessSession(
    grant: IdentityAuthorizationGrant, options: IdentityLivenessOptions,
    backend: ODAIKitBinding.IdentityBackendSelection, callback: IdentityCallback
  ) -> IdentityBridgeTask {
    submit(callback) { task in
      guard let engine = self.liveness else { throw IdentityError(.backendUnavailable) }
      let native = try await engine.openLivenessSession(
        grant: try AuthorizationGrant.decodeJSON(grant.canonicalValue),
        options: try LivenessOptions(
          policy: PADPolicy.decodeJSON(options.policy.canonicalValue), purpose: options.purpose,
          revision: options.revision),
        backend: backend.native())
      let session = IOSLivenessSession(
        native: native, bridge: self, callbackQueue: self.callbackQueue)
      let accepted = self.lock.withLock {
        if self.closed { return false }
        self.sessions[native.sessionId] = session
        return true
      }
      guard accepted else {
        await native.close()
        throw IdentityError(.closed)
      }
      return session
    }
  }
  public func revokeGrant(
    grantId: String, ownership: IdentityOwnershipAuthorization, callback: IdentityCallback
  ) -> IdentityBridgeTask {
    submit(callback) { task in
      let native = try await self.store.revokeGrant(
        grantId,
        ownership: OwnershipAuthorization(
          storeId: ownership.storeId, evidenceRef: ownership.evidenceRef))
      return IdentityRevocationReceipt(canonicalValue: native.canonicalJSON())
    }
  }
  public func deleteTemplate(
    reference: IdentityTemplateReference, ownership: IdentityOwnershipAuthorization,
    callback: IdentityCallback
  ) -> IdentityBridgeTask {
    submit(callback) { task in
      let native = try await self.store.deleteTemplate(
        try TemplateRef.decodeJSON(reference.canonicalValue),
        ownership: OwnershipAuthorization(
          storeId: ownership.storeId, evidenceRef: ownership.evidenceRef))
      return IdentityDeletionReceipt(canonicalValue: native.canonicalJSON(), store: .identity)
    }
  }
  public func deleteCandidate(
    reference: IdentityCandidateReference, ownership: IdentityOwnershipAuthorization,
    callback: IdentityCallback
  ) -> IdentityBridgeTask {
    submit(callback) { task in
      let native = try await self.store.deleteCandidate(
        try CandidateRef.decodeJSON(reference.canonicalValue),
        ownership: OwnershipAuthorization(
          storeId: ownership.storeId, evidenceRef: ownership.evidenceRef))
      return IdentityDeletionReceipt(canonicalValue: native.canonicalJSON(), store: .identity)
    }
  }
  public func deleteGallery(
    reference: IdentityGalleryReference, ownership: IdentityOwnershipAuthorization,
    callback: IdentityCallback
  ) -> IdentityBridgeTask {
    submit(callback) { task in
      let native = try await self.indexes.deleteGallery(
        try GalleryRef.decodeJSON(reference.canonicalValue),
        ownership: OwnershipAuthorization(
          storeId: ownership.storeId, evidenceRef: ownership.evidenceRef))
      return IdentityDeletionReceipt(canonicalValue: native.canonicalJSON(), store: .index)
    }
  }
  public func awaitDeletion(
    receipt: IdentityDeletionReceipt, options: IdentityDeletionWaitOptions,
    callback: IdentityCallback
  ) -> IdentityBridgeTask {
    submit(callback) { task in
      let native = try DeletionReceipt.decodeJSON(receipt.canonicalValue)
      let status: DeletionStatus
      if let duration = options.durationNanos {
        let deadline = try IdentityDeadline(
          clock: SystemIdentityMonotonicClock(), durationNanos: duration.int64Value)
        if receipt.store == .identity {
          status = try await self.store.awaitDeletion(
            native, deadline: deadline, stopSignal: task.signal)
        } else {
          status = try await self.indexes.awaitDeletion(
            native, deadline: deadline, stopSignal: task.signal)
        }
      } else if receipt.store == .identity {
        status = try await self.store.awaitDeletion(native)
      } else {
        status = try await self.indexes.awaitDeletion(native)
      }
      return status.bridgeValue
    }
  }
  public func awaitRevocation(
    receipt: IdentityRevocationReceipt, options: IdentityDeletionWaitOptions,
    callback: IdentityCallback
  ) -> IdentityBridgeTask {
    submit(callback) { task in
      let native = try RevocationReceipt.decodeJSON(receipt.canonicalValue)
      let status: DeletionStatus
      if let duration = options.durationNanos {
        let deadline = try IdentityDeadline(
          clock: SystemIdentityMonotonicClock(), durationNanos: duration.int64Value)
        status = try await self.store.awaitDeletion(
          native, deadline: deadline, stopSignal: task.signal)
      } else {
        status = try await self.store.awaitDeletion(native)
      }
      return status.bridgeValue
    }
  }

  public func close() {
    let values: ([IOSIdentityTask], [GalleryBuilder], [IOSLivenessSession]) = lock.withLock {
      guard !closed else { return ([], [], []) }
      closed = true
      let result = (Array(tasks.values), Array(builders.values), Array(sessions.values))
      tasks.removeAll()
      builders.removeAll()
      sessions.removeAll()
      return result
    }
    for task in values.0 { task.close() }
    for builder in values.1 { Task { await builder.close() } }
    for session in values.2 { session.close() }
  }

  fileprivate func removeSession(_ id: String) {
    _ = lock.withLock { sessions.removeValue(forKey: id) }
  }
  private var isClosed: Bool { lock.withLock { closed } }
  private func selectBackend(_ selection: ODAIKitBinding.IdentityBackendSelection) throws
    -> any IdentityBackend
  {
    let selected = try registry.select(selection.native())
    guard selected.availability().status == .available else {
      throw IdentityError(
        .backendUnavailable, backendId: selected.backendId, modelBinding: selected.modelBinding)
    }
    return selected
  }
  private func builder(_ ref: IdentityGalleryBuilderReference) throws -> GalleryBuilder {
    try lock.withLock {
      guard let value = builders[ref.canonicalValue] else { throw IdentityError(.builderClosed) }
      return value
    }
  }
  private func takeBuilder(_ ref: IdentityGalleryBuilderReference) -> GalleryBuilder? {
    lock.withLock { builders.removeValue(forKey: ref.canonicalValue) }
  }
  fileprivate func submit(
    _ callback: IdentityCallback, operation: @escaping (IOSIdentityTask) async throws -> Any?
  ) -> IdentityBridgeTask {
    let bridgeTask = IOSIdentityTask()
    guard
      lock.withLock({
        if closed { return false }
        tasks[bridgeTask.id] = bridgeTask
        return true
      })
    else {
      callbackQueue.async {
        callback.onFailure(
          error: IdentityBridgeFailureRejected(
            code: .closed, backendId: nil, modelBinding: nil, policyId: nil, sessionId: nil))
      }
      return bridgeTask
    }
    bridgeTask.start { [weak self, weak bridgeTask] in
      guard let self, let bridgeTask else { return }
      do {
        let value = try await operation(bridgeTask)
        await bridgeTask.awaitStopDelivery()
        await bridgeTask.signal.awaitDelivery()
        self.deliverSuccess(value, callback: callback, task: bridgeTask)
      } catch {
        await bridgeTask.awaitStopDelivery()
        await bridgeTask.signal.awaitDelivery()
        self.deliverFailure(error, callback: callback, task: bridgeTask)
      }
    }
    return bridgeTask
  }
  private func deliverSuccess(_ value: Any?, callback: IdentityCallback, task: IOSIdentityTask) {
    callbackQueue.async { [weak self] in
      defer { self?.remove(task) }
      callback.onSuccess(value: value)
    }
  }
  private func deliverFailure(_ error: Error, callback: IdentityCallback, task: IOSIdentityTask) {
    callbackQueue.async { [weak self] in
      defer { self?.remove(task) }
      callback.onFailure(error: error.bridgeFailure)
    }
  }
  private func remove(_ task: IOSIdentityTask) {
    _ = lock.withLock { tasks.removeValue(forKey: task.id) }
  }
}

private final class IOSIdentityTask: NSObject, IdentityBridgeTask, @unchecked Sendable {
  let id = UUID()
  let signal = IdentityStopSignal()
  private let lock = NSLock()
  private var worker: Task<Void, Never>?
  private var delivery: Task<Void, Never>?
  private var nativeCancel: (() -> Void)?
  private var cancelled = false
  var isCancelled: Bool { lock.withLock { cancelled } }
  func start(_ operation: @escaping @Sendable () async -> Void) {
    lock.withLock {
      worker = Task.detached(operation: operation)
      if cancelled { worker?.cancel() }
    }
  }
  func setNativeCancel(_ action: @escaping () -> Void) {
    let stopped = lock.withLock {
      nativeCancel = action
      return cancelled
    }
    if stopped { action() }
  }
  private func requestStop(_ reason: IdentityStopReason) {
    lock.withLock {
      guard !cancelled else { return }
      cancelled = true
      let running = worker
      let action = nativeCancel
      delivery = Task.detached { [signal] in
        signal.requestStop(reason)
        running?.cancel()
        action?()
      }
    }
  }
  func cancel() { requestStop(.cancelled) }
  func awaitStopDelivery() async {
    let pending = lock.withLock { delivery }
    await pending?.value
  }
  func close() { requestStop(.closed) }
}

private final class IOSLivenessSession: NSObject, IdentityLivenessSession, @unchecked Sendable {
  private let native: LivenessSession
  private weak var bridge: IOSIdentityBridge?
  private let callbackQueue: DispatchQueue
  private let lock = NSLock()
  private var closing = false

  init(native: LivenessSession, bridge: IOSIdentityBridge, callbackQueue: DispatchQueue) {
    self.native = native
    self.bridge = bridge
    self.callbackQueue = callbackQueue
  }
  var sessionId: String { native.sessionId }
  func start(callback: IdentityCallback) -> IdentityBridgeTask {
    run(
      callback, cancel: { self.native.cancel() },
      operation: {
        try self.native.start()
        return IdentityBridgeValues.shared.unitValue
      })
  }
  func submitFrame(frame: IdentityLivenessFrame) -> Bool {
    guard !lock.withLock({ closing }) else { return false }
    do {
      let challenge = try frame.challenge.map {
        try LivenessChallenge.decodeJSON($0.canonicalValue)
      }
      return try native.submitFrame(
        LivenessFrame(
          encodedFrame: Data(frame.encodedFrame.toBytes()), timestampNanos: frame.timestampNanos,
          revision: frame.revision, challenge: challenge))
    } catch { return false }
  }
  func nextEvent(callback: IdentityCallback) -> IdentityBridgeTask {
    run(
      callback, cancel: { self.close() },
      operation: {
        await self.native.nextEvent().map { $0.bridgeValue() }
      })
  }
  func finish() { native.finish() }
  func cancel() { native.cancel() }
  func result(callback: IdentityCallback) -> IdentityBridgeTask {
    run(
      callback, cancel: { self.native.cancel() },
      operation: { try await self.native.result().bridgeValue() })
  }
  func consumeProof(
    proof: IdentityLivenessProof, captureDigest: String, purpose: String, callback: IdentityCallback
  ) -> IdentityBridgeTask {
    run(
      callback, cancel: { self.native.cancel() },
      operation: {
        try await self.native.consumeProof(
          try LivenessProof.decodeJSON(proof.canonicalValue), captureDigest: captureDigest,
          purpose: purpose)
        return IdentityBridgeValues.shared.unitValue
      })
  }
  func close() {
    let first = lock.withLock {
      if closing { return false }
      closing = true
      return true
    }
    guard first else { return }
    Task {
      await native.close()
      bridge?.removeSession(sessionId)
    }
  }
  private func run(
    _ callback: IdentityCallback, cancel: @escaping () -> Void,
    operation: @escaping () async throws -> Any?
  ) -> IdentityBridgeTask {
    guard let bridge else {
      callbackQueue.async { callback.onFailure(error: IdentityError(.closed).bridgeFailure) }
      return IOSIdentityTask()
    }
    return bridge.submit(callback) { task in
      guard !self.lock.withLock({ self.closing }) else { throw IdentityError(.closed) }
      task.setNativeCancel(cancel)
      return try await operation()
    }
  }
}

extension ODAIKitBinding.IdentityBackendSelection {
  fileprivate func native() throws -> ODAIKitIdentity.IdentityBackendSelection {
    if let explicit = self as? IdentityBackendSelectionExplicit {
      return .explicit(explicit.backendId)
    }
    guard self is IdentityBackendSelectionDefault else { throw IdentityError(.unsupportedOption) }
    return .default
  }
}
extension IdentityError {
  fileprivate var bridgeFailure: IdentityBridgeFailure {
    if code == .cancelled { return IdentityBridgeFailureCancelled.shared }
    return IdentityBridgeFailureRejected(
      code: code.bridgeValue, backendId: backendId,
      modelBinding: modelBinding.map { IdentityModelBinding(canonicalValue: $0.canonicalJSON()) },
      policyId: policyId, sessionId: sessionId)
  }
}
extension ODAIKitIdentity.IdentityErrorCode {
  fileprivate var bridgeValue: ODAIKitBinding.IdentityErrorCode {
    switch self {
    case .backendNotRegistered: return .backendNotRegistered
    case .backendUnavailable: return .backendUnavailable
    case .unsupportedOption: return .unsupportedOption
    case .authorizationRequired: return .authorizationRequired
    case .authorizationExpired: return .authorizationExpired
    case .authorizationRevoked: return .authorizationRevoked
    case .candidateNotFound: return .candidateNotFound
    case .candidateRevoked: return .candidateRevoked
    case .templateNotFound: return .templateNotFound
    case .templateRevoked: return .templateRevoked
    case .galleryNotFound: return .galleryNotFound
    case .galleryRevisionGone: return .galleryRevisionGone
    case .galleryRevisionConflict: return .galleryRevisionConflict
    case .builderClosed: return .builderClosed
    case .embeddingSpaceMismatch: return .embeddingSpaceMismatch
    case .featureFormatUnsupported: return .featureFormatUnsupported
    case .reEnrollmentRequired: return .reEnrollmentRequired
    case .rebuildRequired: return .rebuildRequired
    case .indexUnavailable: return .indexUnavailable
    case .indexBuilding: return .indexBuilding
    case .thresholdUnverified: return .thresholdUnverified
    case .invalidInput: return .invalidInput
    case .multipleFaces: return .multipleFaces
    case .invalidChallenge: return .invalidChallenge
    case .challengeExpired: return .challengeExpired
    case .sessionTimeout: return .sessionTimeout
    case .busy: return .busy
    case .closed: return .closed
    case .cancelled: return .cancelled
    }
  }
}
extension IdentityQuality {
  fileprivate var bridgeValue: IdentityQualitySummary {
    IdentityQualitySummary(
      faceCount: Int32(faceCount), score: score.map { KotlinDouble(value: $0) })
  }
}
extension ODAIKitIdentity.IdentityScoreFields {
  fileprivate var bridgeValue: ODAIKitBinding.IdentityScoreFields {
    ODAIKitBinding.IdentityScoreFields(
      score: score.map { KotlinDouble(value: $0) }, metric: metric,
      threshold: threshold.map { KotlinDouble(value: $0) })
  }
}
extension EnrollmentResult {
  fileprivate func bridgeValue() -> IdentityEnrollmentResult {
    IdentityEnrollmentResult(
      outcome: outcome == .enrolled ? .enrolled : .insufficientQuality,
      template: templateRef.map { IdentityTemplateReference(canonicalValue: $0.canonicalJSON()) },
      embeddingSpace: IdentityEmbeddingSpace(canonicalValue: embeddingSpace.canonicalJSON()),
      quality: quality.bridgeValue, qualityPolicyId: qualityPolicyId, metadata: bridgeMetadata)
  }
}
extension VerificationResult {
  fileprivate func bridgeValue() -> IdentityVerificationResult {
    IdentityVerificationResult(
      outcome: outcome == .match ? .match : outcome == .nonMatch ? .nonMatch : .insufficientQuality,
      scoreFields: scoreFields.bridgeValue, quality: quality.bridgeValue,
      templateRevision: templateRevision,
      policy: IdentityPolicyReference(id: thresholdPolicyId, revision: thresholdPolicyRevision),
      metadata: bridgeMetadata)
  }
}
extension IdentificationResult {
  fileprivate func bridgeValue() -> IdentityIdentificationResult {
    IdentityIdentificationResult(
      outcome: outcome == .candidates
        ? .candidates : outcome == .unknown ? .unknown : .insufficientQuality,
      candidates: candidates.map {
        IdentityIdentificationCandidate(
          candidate: IdentityCandidateReference(canonicalValue: $0.candidateRef.canonicalJSON()),
          score: $0.score, rank: Int32($0.rank))
      }, scoreFields: scoreFields.bridgeValue,
      thresholdFields: thresholdFields.map {
        IdentityIdentificationThreshold(metric: $0.metric, threshold: $0.threshold)
      }, quality: quality.bridgeValue, galleryRevision: galleryRevision,
      galleryContentDigest: galleryContentDigest, aggregationPolicyId: aggregationPolicyId,
      policy: IdentityPolicyReference(id: thresholdPolicyId, revision: thresholdPolicyRevision),
      galleryCandidateCount: Int32(galleryCandidateCount),
      galleryTemplateCount: Int32(galleryTemplateCount),
      enrollmentCounts: enrollmentCounts.map {
        IdentityEnrollmentCount(
          candidate: IdentityCandidateReference(canonicalValue: $0.candidateRef.canonicalJSON()),
          templateCount: Int32($0.templateCount))
      },
      grouping: IdentityIdentificationGrouping(
        datasetRevision: fpirFnirGrouping.datasetRevision,
        operatingPoint: fpirFnirGrouping.operatingPoint,
        enrollmentRule: fpirFnirGrouping.enrollmentRule,
        minimumGallerySize: Int32(fpirFnirGrouping.gallerySizeRange.lowerBound),
        maximumGallerySize: Int32(fpirFnirGrouping.gallerySizeRange.upperBound),
        topK: Int32(fpirFnirGrouping.topK)), metadata: bridgeMetadata)
  }
}
extension EnrollmentResult {
  fileprivate var bridgeMetadata: ODAIKitBinding.IdentityRunMetadata {
    ODAIKitBinding.IdentityRunMetadata(
      backendId: backendId, backendVersion: backendVersion,
      modelBinding: ODAIKitBinding.IdentityModelBinding(
        canonicalValue: modelBinding.canonicalJSON()), inputRevision: inputRevision,
      optionsRevision: optionsRevision, preprocessingRevision: preprocessingRevision,
      sessionId: sessionId, runId: runId, durationNanos: durationNanos)
  }
}
extension VerificationResult {
  fileprivate var bridgeMetadata: ODAIKitBinding.IdentityRunMetadata {
    ODAIKitBinding.IdentityRunMetadata(
      backendId: backendId, backendVersion: backendVersion,
      modelBinding: ODAIKitBinding.IdentityModelBinding(
        canonicalValue: modelBinding.canonicalJSON()), inputRevision: inputRevision,
      optionsRevision: optionsRevision, preprocessingRevision: preprocessingRevision,
      sessionId: sessionId, runId: runId, durationNanos: durationNanos)
  }
}
extension IdentificationResult {
  fileprivate var bridgeMetadata: ODAIKitBinding.IdentityRunMetadata {
    ODAIKitBinding.IdentityRunMetadata(
      backendId: backendId, backendVersion: backendVersion,
      modelBinding: ODAIKitBinding.IdentityModelBinding(
        canonicalValue: modelBinding.canonicalJSON()), inputRevision: inputRevision,
      optionsRevision: optionsRevision, preprocessingRevision: preprocessingRevision,
      sessionId: sessionId, runId: runId, durationNanos: durationNanos)
  }
}
extension LivenessResult {
  fileprivate var bridgeMetadata: ODAIKitBinding.IdentityRunMetadata {
    ODAIKitBinding.IdentityRunMetadata(
      backendId: backendId, backendVersion: backendVersion,
      modelBinding: ODAIKitBinding.IdentityModelBinding(
        canonicalValue: modelBinding.canonicalJSON()), inputRevision: inputRevision,
      optionsRevision: optionsRevision, preprocessingRevision: nil, sessionId: sessionId,
      runId: runId, durationNanos: durationNanos)
  }
}
extension LivenessResult {
  fileprivate func bridgeValue() -> IdentityLivenessResult {
    IdentityLivenessResult(
      outcome: [
        .live: .live, .spoof: .spoof, .insufficientQuality: .insufficientQuality,
        .challengeFailed: .challengeFailed,
      ][outcome]!, scoreFields: scoreFields.bridgeValue, quality: quality.bridgeValue,
      policy: IdentityPolicyReference(id: policy.id, revision: policy.revision),
      acceptedFrameCount: Int32(acceptedFrameCount), droppedFrameCount: Int32(droppedFrameCount),
      staleFrameCount: Int32(staleFrameCount), captureDigest: captureDigest,
      proof: proof.map { IdentityLivenessProof(canonicalValue: $0.canonicalJSON()) },
      metadata: bridgeMetadata)
  }
}
extension LivenessEvent {
  fileprivate func bridgeValue() -> IdentityLivenessEvent {
    switch self {
    case .started: return IdentityLivenessEventStarted.shared
    case .challengeIssued(let c):
      return IdentityLivenessEventChallengeIssued(
        challenge: IdentityLivenessChallenge(canonicalValue: c.canonicalJSON()))
    case .frameAccepted: return IdentityLivenessEventFrameAccepted.shared
    case .frameDropped: return IdentityLivenessEventFrameDropped.shared
    case .frameStale: return IdentityLivenessEventFrameStale.shared
    case .completed(let r): return IdentityLivenessEventCompleted(result: r.bridgeValue())
    case .failed(let code): return IdentityLivenessEventFailed(code: code.bridgeValue)
    case .cancelled: return IdentityLivenessEventCancelled.shared
    case .closed: return IdentityLivenessEventClosed.shared
    }
  }
}
extension DeletionStatus {
  fileprivate var bridgeValue: ODAIKitBinding.IdentityDeletionStatus {
    switch self {
    case .revoked: return .revoked
    case .purgePending: return .purgePending
    case .purged: return .purged
    case .purgeFailed: return .purgeFailed
    }
  }
}
extension KotlinByteArray {
  fileprivate func toBytes() -> [UInt8] {
    (0..<size).map { UInt8(truncatingIfNeeded: get(index: Int32($0))) }
  }
}
extension NSLock {
  fileprivate func withLock<T>(_ body: () throws -> T) rethrows -> T {
    lock()
    defer { unlock() }
    return try body()
  }
}

extension Error {
  fileprivate var bridgeFailure: IdentityBridgeFailure {
    if let native = self as? IdentityError { return native.bridgeFailure }
    if self is CancellationError { return IdentityBridgeFailureCancelled.shared }
    return IdentityBridgeFailureRejected(
      code: .invalidInput, backendId: nil, modelBinding: nil, policyId: nil, sessionId: nil)
  }
}
