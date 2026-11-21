import XCTest
import ODAIKitCore
@testable import ODAIKitIdentity

final class IdentityStopSignalTests: XCTestCase {
    func testStopIsIdempotentAndLateHandlersRunImmediately() throws {
        let signal = IdentityStopSignal()
        let calls = LockedValue(0)
        let registration = signal.registerStopHandler { calls.update { $0 += 1 } }
        signal.requestStop(.authorizationRevoked)
        signal.requestStop(.cancelled)
        XCTAssertEqual(calls.value, 1)
        XCTAssertThrowsError(try signal.check()) { error in
            XCTAssertEqual((error as? IdentityError)?.code, .authorizationRevoked)
        }
        let late = LockedValue(0)
        _ = signal.registerStopHandler { late.update { $0 += 1 } }
        XCTAssertEqual(late.value, 1)
        registration.close(); registration.close()
    }

    func testClosedRegistrationDoesNotRunAndCallbackCanReenter() {
        let signal = IdentityStopSignal()
        let called = LockedValue(0)
        let registration = signal.registerStopHandler {
            called.update { $0 += 1 }
            _ = signal.registerStopHandler { called.update { $0 += 10 } }
            do { try signal.check(); XCTFail("stop check should throw") } catch {}
        }
        registration.close()
        signal.requestStop(.closed)
        XCTAssertEqual(called.value, 0)

        let active = IdentityStopSignal()
        let reentered = LockedValue(false)
        _ = active.registerStopHandler {
            reentered.update { $0 = true }
            active.requestStop(.cancelled)
        }
        active.requestStop(.cancelled)
        XCTAssertTrue(reentered.value)
    }

    func testDefaultBackendAdapterChecksBeforeAndAfterPrimitive() throws {
        let probe = try IdentityProbe(fixtureID: "probe", vector: [1, 0])
        let backend = try BlockingIdentityBackend()
        let before = IdentityStopSignal(); before.requestStop(.cancelled)
        XCTAssertThrowsError(try backend.embed(probe, stopSignal: before)) { error in
            XCTAssertEqual((error as? IdentityError)?.code, .cancelled)
        }
        XCTAssertEqual(backend.embedCalls, 0)

        let after = IdentityStopSignal()
        let started = expectation(description: "primitive started")
        backend.started = { started.fulfill() }
        let result = expectation(description: "primitive returned")
        let resultError = LockedValue<IdentityError?>(nil)
        DispatchQueue.global().async {
            do { _ = try backend.embed(probe, stopSignal: after) }
            catch { let identityError = error as? IdentityError; resultError.update { $0 = identityError } }
            result.fulfill()
        }
        wait(for: [started], timeout: 1)
        after.requestStop(.authorizationRevoked)
        backend.release()
        wait(for: [result], timeout: 1)
        XCTAssertEqual(resultError.value?.code, .authorizationRevoked)
    }

    func testActiveBackendErrorIsPreserved() throws {
        let probe = try IdentityProbe(fixtureID: "probe", vector: [1, 0])
        let backend = try FailingIdentityBackend()
        let signal = IdentityStopSignal()
        XCTAssertThrowsError(try backend.embed(probe, stopSignal: signal)) { error in
            XCTAssertEqual((error as? IdentityError)?.code, .invalidInput)
        }
    }

    func testAllStopReasonsAndConcurrentRequestsAreStable() throws {
        for (reason, code) in [(IdentityStopReason.cancelled, IdentityErrorCode.cancelled),
                               (.authorizationRevoked, .authorizationRevoked), (.closed, .closed)] {
            let signal = IdentityStopSignal()
            let calls = LockedValue(0)
            let registration = signal.registerStopHandler { calls.update { $0 += 1 } }
            DispatchQueue.concurrentPerform(iterations: 64) { _ in signal.requestStop(reason) }
            XCTAssertEqual(calls.value, 1)
            XCTAssertThrowsError(try signal.check()) { XCTAssertEqual(($0 as? IdentityError)?.code, code) }
            registration.close()
        }
    }

    func testStopSupersedesBackendFailureForBothPrimitives() throws {
        let probe = try IdentityProbe(fixtureID: "probe", vector: [1, 0])
        let backend = try FailingIdentityBackend()
        for quality in [false, true] {
            let signal = IdentityStopSignal()
            backend.started = { signal.requestStop(.authorizationRevoked) }
            XCTAssertThrowsError(try quality ? backend.evaluateQuality(probe, stopSignal: signal).faceCount
                                            : backend.embed(probe, stopSignal: signal).values.count) {
                XCTAssertEqual(($0 as? IdentityError)?.code, .authorizationRevoked)
            }
        }
    }

    func testQualityAdapterChecksBeforeAndAfterSuccess() throws {
        let probe = try IdentityProbe(fixtureID: "probe", vector: [1, 0])
        let backend = try BlockingIdentityBackend()
        let before = IdentityStopSignal(); before.requestStop(.closed)
        XCTAssertThrowsError(try backend.evaluateQuality(probe, stopSignal: before)) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .closed)
        }
        let after = IdentityStopSignal()
        backend.started = { after.requestStop(.cancelled) }
        XCTAssertThrowsError(try backend.evaluateQuality(probe, stopSignal: after)) {
            XCTAssertEqual(($0 as? IdentityError)?.code, .cancelled)
        }
    }

    func testNativeAdapterCanRegisterActualStopAndUnblockInFlight() throws {
        let signal = IdentityStopSignal()
        let started = expectation(description: "native operation active")
        let finished = expectation(description: "native operation stopped")
        let backend = try CooperativeIdentityBackend(started: { started.fulfill() })
        let resultError = LockedValue<IdentityError?>(nil)
        let probe = try IdentityProbe(fixtureID: "probe", vector: [1, 0])
        // Existential dispatch must reach the backend override, not the default adapter.
        let selected: any IdentityBackend = backend
        DispatchQueue.global().async {
            do { _ = try selected.embed(probe, stopSignal: signal) }
            catch { let failure = error as? IdentityError; resultError.update { $0 = failure } }
            finished.fulfill()
        }
        wait(for: [started], timeout: 1)
        signal.requestStop(.authorizationRevoked)
        wait(for: [finished], timeout: 1)
        XCTAssertEqual(backend.stopCalls.value, 1)
        XCTAssertEqual(resultError.value?.code, .authorizationRevoked)
    }
}

private class BlockingIdentityBackend: IdentityBackend, @unchecked Sendable {
    let backendId = "stop.fixture"
    let backendVersion = "1"
    let modelBinding: ModelBinding
    let embeddingSpace: EmbeddingSpaceId
    var embedCalls = 0
    var started: (() -> Void)?
    private let gate = DispatchSemaphore(value: 0)
    init() throws {
        modelBinding = try ModelBinding(kind: .model, id: "stop.fixture", revision: "1")
        embeddingSpace = try EmbeddingSpaceId(supportedCapabilities: [.verification], backendId: backendId, backendVersion: backendVersion,
                                              modelBinding: modelBinding, featureFormatVersion: "1", dimension: 2, dtype: .float32,
                                              endianness: .little, normalization: "none", preprocessRevision: "1",
                                              scoreMetric: "cosine", scoreDirection: .higherIsBetter)
    }
    func availability() -> CapabilityAvailability { CapabilityAvailability(status: .available, reason: nil, backendId: backendId) }
    func embed(_ probe: IdentityProbe) throws -> IdentityEmbedding {
        embedCalls += 1; started?(); _ = gate.wait(timeout: .now() + 1)
        return try IdentityEmbedding(embeddingSpace: embeddingSpace, values: [1, 0])
    }
    func evaluateQuality(_ probe: IdentityProbe) throws -> IdentityQuality { started?(); return try IdentityQuality(faceCount: 1, score: 1) }
    func release() { gate.signal() }
    func close() {}
}

private final class FailingIdentityBackend: BlockingIdentityBackend, @unchecked Sendable {
    override func embed(_ probe: IdentityProbe) throws -> IdentityEmbedding { started?(); throw IdentityError(.invalidInput) }
    override func evaluateQuality(_ probe: IdentityProbe) throws -> IdentityQuality { started?(); throw IdentityError(.invalidInput) }
}

private final class CooperativeIdentityBackend: IdentityBackend, @unchecked Sendable {
    let stopCalls = LockedValue(0)
    private let base: BlockingIdentityBackend
    private let started: @Sendable () -> Void
    private let completion = DispatchSemaphore(value: 0)
    init(started: @escaping @Sendable () -> Void) throws {
        base = try BlockingIdentityBackend(); self.started = started
    }
    var backendId: String { base.backendId }
    var backendVersion: String { base.backendVersion }
    var modelBinding: ModelBinding { base.modelBinding }
    var embeddingSpace: EmbeddingSpaceId { base.embeddingSpace }
    func availability() -> CapabilityAvailability { base.availability() }
    func embed(_ probe: IdentityProbe) throws -> IdentityEmbedding { throw IdentityError(.unsupportedOption) }
    func embed(_ probe: IdentityProbe, stopSignal: IdentityStopSignal) throws -> IdentityEmbedding {
        try stopSignal.check()
        let registration = stopSignal.registerStopHandler { [self] in
            stopCalls.update { $0 += 1 }; completion.signal()
        }
        defer { registration.close() }
        started()
        guard completion.wait(timeout: .now() + 2) == .success else { throw IdentityError(.sessionTimeout) }
        try stopSignal.check()
        return try IdentityEmbedding(embeddingSpace: embeddingSpace, values: [1, 0])
    }
    func evaluateQuality(_ probe: IdentityProbe) throws -> IdentityQuality { try base.evaluateQuality(probe) }
    func close() {}
}

private final class LockedValue<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var storage: Value
    init(_ value: Value) { storage = value }
    var value: Value { lock.lock(); defer { lock.unlock() }; return storage }
    func update(_ change: (inout Value) -> Void) { lock.lock(); defer { lock.unlock() }; change(&storage) }
}
