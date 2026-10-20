import XCTest
import ODAIKitCore
@testable import ODAIKitSpeech

private actor ControlledMicrophoneTask: SpeechMicrophoneRecognitionTask {
    private var outcome: Result<SpeechRecognitionResult, Error>?
    private var waiter: CheckedContinuation<SpeechRecognitionResult, Error>?
    private let ignoresCancellation: Bool
    let cancelled: XCTestExpectation?
    private(set) var resultCalls = 0
    private(set) var finishCalls = 0
    private(set) var cancelCalls = 0

    init(ignoresCancellation: Bool = false, cancelled: XCTestExpectation? = nil) {
        self.ignoresCancellation = ignoresCancellation
        self.cancelled = cancelled
    }
    func result() async throws -> SpeechRecognitionResult {
        resultCalls += 1
        if let outcome { return try outcome.get() }
        return try await withCheckedThrowingContinuation { waiter = $0 }
    }
    func finishInput() async { finishCalls += 1 }
    func cancel() async {
        cancelCalls += 1
        cancelled?.fulfill()
        if !ignoresCancellation { complete(.failure(CancellationError())) }
    }
    func complete(_ value: Result<SpeechRecognitionResult, Error>) {
        guard outcome == nil else { return }
        outcome = value
        waiter?.resume(with: value)
        waiter = nil
    }
}

private actor ControlledMicrophoneBackend: SpeechMicrophoneRecognitionBackend {
    nonisolated let backendId: String
    nonisolated let capability: SpeechCapability = .asr
    nonisolated let supportedInputs: [SpeechRecognitionInputKind] = [.audioFile, .microphone]
    private let tasks: [ControlledMicrophoneTask]
    private let available: Bool
    private let availabilityEntered: XCTestExpectation?
    private let startEntered: XCTestExpectation?
    private let drainEntered: XCTestExpectation?
    private var availabilityWaiter: CheckedContinuation<Void, Never>?
    private var startWaiter: CheckedContinuation<Void, Never>?
    private var drainWaiters: [CheckedContinuation<Void, Never>] = []
    private var heldAvailability = false
    private var holdDrain: Bool
    private(set) var startCalls = 0
    private(set) var closeCalls = 0

    init(_ backendId: String = "test.mic", tasks: [ControlledMicrophoneTask], available: Bool = true,
         availabilityEntered: XCTestExpectation? = nil, startEntered: XCTestExpectation? = nil,
         drainEntered: XCTestExpectation? = nil, holdDrain: Bool = false) {
        self.backendId = backendId; self.tasks = tasks; self.available = available
        self.availabilityEntered = availabilityEntered; self.startEntered = startEntered
        self.drainEntered = drainEntered; self.holdDrain = holdDrain
    }
    func availability() async -> CapabilityAvailability {
        if !heldAvailability, let availabilityEntered {
            heldAvailability = true
            await withCheckedContinuation { availabilityWaiter = $0; availabilityEntered.fulfill() }
        }
        return CapabilityAvailability(status: available ? .available : .unavailable,
                                      reason: available ? nil : .permissionDenied,
                                      backendId: backendId, minOS: "15.0", offline: true)
    }
    func recognize(_ request: SpeechRecognitionRequest) async throws -> SpeechRecognitionResult {
        SpeechRecognitionResult(backendId: backendId, transcript: "file fixture")
    }
    func startMicrophone(_ request: SpeechMicrophoneRecognitionRequest) async throws
        -> any SpeechMicrophoneRecognitionTask {
        let index = startCalls
        startCalls += 1
        if index == 0, let startEntered {
            await withCheckedContinuation { startWaiter = $0; startEntered.fulfill() }
        }
        return tasks[index]
    }
    func drain() async {
        if holdDrain {
            await withCheckedContinuation { drainWaiters.append($0); drainEntered?.fulfill() }
        }
    }
    func close() async { closeCalls += 1 }
    func releaseAvailability() { availabilityWaiter?.resume(); availabilityWaiter = nil }
    func releaseStart() { startWaiter?.resume(); startWaiter = nil }
    func releaseDrain() {
        holdDrain = false
        let pending = drainWaiters; drainWaiters.removeAll()
        pending.forEach { $0.resume() }
    }
}

private actor FileOnlyMicrophoneFixture: SpeechRecognitionBackend {
    nonisolated let backendId = "test.file"
    nonisolated let capability: SpeechCapability = .asr
    func availability() async -> CapabilityAvailability {
        CapabilityAvailability(status: .available, backendId: backendId)
    }
    func recognize(_ request: SpeechRecognitionRequest) async throws -> SpeechRecognitionResult {
        SpeechRecognitionResult(backendId: backendId, transcript: "file fixture")
    }
    func drain() async {}
    func close() async {}
}

final class SpeechMicrophoneClientTests: XCTestCase {
    private let request = try! SpeechMicrophoneRecognitionRequest(locale: "en-US")
    private let fixture = SpeechRecognitionResult(backendId: "test.mic", transcript: "fixture")

    func testExplicitMissingUnavailableAndFileOnlyNeverFallBack() async throws {
        let fallback = ControlledMicrophoneBackend(tasks: [ControlledMicrophoneTask()])
        let denied = ControlledMicrophoneBackend("denied.mic", tasks: [], available: false)
        let file = FileOnlyMicrophoneFixture()
        let client = try SpeechRecognitionClient(defaultBackendId: fallback.backendId, backends: [fallback, denied, file])
        for id in ["missing", denied.backendId, file.backendId] {
            do {
                _ = try await client.startMicrophone(request, backend: .explicit(id))
                XCTFail("Unsupported explicit admission must fail")
            } catch {
                if id == "missing" {
                    XCTAssertEqual(error as? SpeechClientError, .backendNotRegistered(capability: .asr, backendId: id))
                } else if id == denied.backendId {
                    XCTAssertEqual(error as? SpeechClientError,
                                   .backendUnavailable(capability: .asr, backendId: id, reason: .permissionDenied))
                } else {
                    XCTAssertEqual(error as? SpeechRecognitionInputError, .unsupportedInput)
                }
            }
        }
        let starts = await fallback.startCalls
        XCTAssertEqual(starts, 0)
        await client.close()
    }

    func testResultWaitersShareTaskAndFinishIsIdempotent() async throws {
        let native = ControlledMicrophoneTask()
        let backend = ControlledMicrophoneBackend(tasks: [native])
        let client = try SpeechRecognitionClient(defaultBackendId: backend.backendId, backends: [backend])
        let task = try await client.startMicrophone(request)
        let first = Task { try await task.result() }
        let second = Task { try await task.result() }
        first.cancel() // A result waiter does not own the ongoing capture.
        await task.finishInput(); await task.finishInput()
        await native.complete(.success(fixture))
        let firstResult = try await first.value
        let secondResult = try await second.value
        XCTAssertEqual(firstResult, fixture)
        XCTAssertEqual(secondResult, fixture)
        let counts = await (native.resultCalls, native.finishCalls, native.cancelCalls)
        XCTAssertEqual(counts.0, 1); XCTAssertEqual(counts.1, 1); XCTAssertEqual(counts.2, 0)
        await client.close()
    }

    func testCancellationOverridesLateSuccessAndWaitsForDrain() async throws {
        let draining = expectation(description: "native drain entered")
        let native = ControlledMicrophoneTask(ignoresCancellation: true)
        let backend = ControlledMicrophoneBackend(tasks: [native], drainEntered: draining, holdDrain: true)
        let client = try SpeechRecognitionClient(defaultBackendId: backend.backendId, backends: [backend])
        let task = try await client.startMicrophone(request)
        await task.cancel(); await task.cancel(); await task.finishInput()
        await native.complete(.success(fixture))
        await fulfillment(of: [draining], timeout: 1)
        let returned = expectation(description: "terminal must wait for drain")
        returned.isInverted = true
        let result = Task { let value = await Result { try await task.result() }; returned.fulfill(); return value }
        await fulfillment(of: [returned], timeout: 0.05)
        await backend.releaseDrain()
        let outcome = await result.value
        if case .failure(let error) = outcome { XCTAssertTrue(error is CancellationError) }
        else { XCTFail("Late native success must not replace cancellation") }
        let cancels = await native.cancelCalls
        let finishes = await native.finishCalls
        XCTAssertEqual(cancels, 1); XCTAssertEqual(finishes, 0)
        await client.close()
    }

    func testCallerCancelledDuringNativeStartDisposesLateTask() async throws {
        let starting = expectation(description: "native start entered")
        let native = ControlledMicrophoneTask()
        let backend = ControlledMicrophoneBackend(tasks: [native], startEntered: starting)
        let client = try SpeechRecognitionClient(defaultBackendId: backend.backendId, backends: [backend])
        let start = Task { try await client.startMicrophone(request) }
        await fulfillment(of: [starting], timeout: 1)
        start.cancel()
        await backend.releaseStart()
        do { _ = try await start.value; XCTFail("Cancelled start must not hand out a task") }
        catch { XCTAssertTrue(error is CancellationError) }
        let cancels = await native.cancelCalls
        XCTAssertEqual(cancels, 1)
        await client.close()
    }

    func testCloseDuringAvailabilityNeverStartsMicrophone() async throws {
        let checking = expectation(description: "availability entered")
        let backend = ControlledMicrophoneBackend(tasks: [], availabilityEntered: checking)
        let client = try SpeechRecognitionClient(defaultBackendId: backend.backendId, backends: [backend])
        let start = Task { try await client.startMicrophone(request) }
        await fulfillment(of: [checking], timeout: 1)
        let close = Task { await client.close() }
        await assertClosedAdmission(client)
        await backend.releaseAvailability()
        await close.value
        do { _ = try await start.value; XCTFail("Closed client must not dispatch late start") }
        catch { XCTAssertEqual(error as? SpeechClientError, .closed) }
        let starts = await backend.startCalls
        XCTAssertEqual(starts, 0)
    }

    func testCloseDuringNativeStartJoinsLateTaskBeforeClosingBackend() async throws {
        let starting = expectation(description: "native start entered")
        let native = ControlledMicrophoneTask()
        let backend = ControlledMicrophoneBackend(tasks: [native], startEntered: starting)
        let client = try SpeechRecognitionClient(defaultBackendId: backend.backendId, backends: [backend])
        let start = Task { try await client.startMicrophone(request) }
        await fulfillment(of: [starting], timeout: 1)
        let close = Task { await client.close() }
        await assertClosedAdmission(client)
        let closesBeforeStartReleased = await backend.closeCalls
        XCTAssertEqual(closesBeforeStartReleased, 0)
        await backend.releaseStart()
        await close.value
        do { _ = try await start.value; XCTFail("Closed client must dispose late task") }
        catch { XCTAssertEqual(error as? SpeechClientError, .closed) }
        let cancels = await native.cancelCalls
        let closes = await backend.closeCalls
        XCTAssertEqual(cancels, 1); XCTAssertEqual(closes, 1)
    }

    func testConcurrentCloseJoinsActiveTaskAndSharedDrain() async throws {
        let draining = expectation(description: "drain entered")
        let cancelled = expectation(description: "native cancelled")
        let native = ControlledMicrophoneTask(cancelled: cancelled)
        let backend = ControlledMicrophoneBackend(tasks: [native], drainEntered: draining, holdDrain: true)
        let client = try SpeechRecognitionClient(defaultBackendId: backend.backendId, backends: [backend])
        _ = try await client.startMicrophone(request)
        let first = Task { await client.close() }
        await fulfillment(of: [cancelled, draining], timeout: 1)
        let returned = expectation(description: "concurrent close must wait")
        returned.isInverted = true
        let second = Task { await client.close(); returned.fulfill() }
        await fulfillment(of: [returned], timeout: 0.05)
        let earlyCloses = await backend.closeCalls
        XCTAssertEqual(earlyCloses, 0)
        await backend.releaseDrain()
        await first.value; await second.value
        let closes = await backend.closeCalls
        let cancels = await native.cancelCalls
        XCTAssertEqual(closes, 1); XCTAssertEqual(cancels, 1)
    }

    func testCompletedOldHandleCannotFinishOrCancelNextTask() async throws {
        let firstNative = ControlledMicrophoneTask()
        let secondNative = ControlledMicrophoneTask()
        let backend = ControlledMicrophoneBackend(tasks: [firstNative, secondNative])
        let client = try SpeechRecognitionClient(defaultBackendId: backend.backendId, backends: [backend])
        let first = try await client.startMicrophone(request)
        await firstNative.complete(.success(fixture))
        _ = try await first.result()
        let second = try await client.startMicrophone(request)
        await first.finishInput(); await first.cancel()
        let finishCalls = await secondNative.finishCalls
        let cancelCalls = await secondNative.cancelCalls
        XCTAssertEqual(finishCalls, 0); XCTAssertEqual(cancelCalls, 0)
        await second.finishInput()
        await secondNative.complete(.success(fixture))
        let output = try await second.result()
        XCTAssertEqual(output, fixture)
        await client.close()
    }

    private func assertClosedAdmission(_ client: SpeechRecognitionClient) async {
        for _ in 0..<1_000 {
            do { _ = try await client.listBackends() }
            catch SpeechClientError.closed { return }
            catch { XCTFail("Unexpected admission error"); return }
            await Task.yield()
        }
        XCTFail("Close must promptly reject new admission")
    }
}

private extension Result where Failure == Error {
    init(_ operation: () async throws -> Success) async {
        do { self = .success(try await operation()) }
        catch { self = .failure(error) }
    }
}
