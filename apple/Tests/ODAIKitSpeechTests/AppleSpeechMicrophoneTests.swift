import XCTest
import ODAIKitCore
@testable import ODAIKitAppleSpeech
@testable import ODAIKitSpeech

@MainActor
final class AppleSpeechMicrophoneTests: XCTestCase {
    private let request = try! SpeechMicrophoneRecognitionRequest(locale: "en-US")

    func testBackendCanBeUsedThroughPublicMicrophoneClientContract() async throws {
        let fixture = try makeBackend()
        let conformer: any SpeechMicrophoneRecognitionBackend = fixture.backend
        XCTAssertEqual(conformer.supportedInputs, [.audioFile, .microphone])
    }

    func testCompletedTaskRetainsItsResultAfterCallerReleasesBackend() async throws {
        let microphone = MicrophonePlatformBridge()
        let clock = MicrophoneClock()
        var backend: AppleSpeechRecognizerBackend? = try AppleSpeechRecognizerBackend(
            locale: "en-US", bridge: MicrophoneFileBridge(), microphoneBridge: microphone,
            audioSessionProvider: MicrophoneSessionProvider(leases: [MicrophoneSessionLease()]), clock: clock)
        let task = try await backend!.startMicrophone(request)
        await task.finishInput()
        microphone.emit(.final(text: "retained", segments: []))
        microphone.complete()
        await clock.advance(by: 0.02)
        _ = try await task.result()
        backend = nil
        for _ in 0..<1_000 { await Task.yield() }
        let repeatedResult = try await task.result()
        XCTAssertEqual(repeatedResult.transcript, "retained")
    }

    func testLocaleAndRecordPermissionRejectBeforeSessionAcquire() async throws {
        let fixture = try makeBackend()
        do {
            _ = try await fixture.backend.startMicrophone(.init(locale: "fr-FR"))
            XCTFail("locale mismatch")
        } catch { XCTAssertEqual(error as? KitError, .invalidInput("locale does not match backend")) }
        fixture.microphone.isRecordingAuthorized = false
        do { _ = try await fixture.backend.startMicrophone(request); XCTFail("record permission denied") }
        catch {
            XCTAssertEqual(error as? SpeechClientError,
                           .backendUnavailable(capability: .asr, backendId: fixture.backend.backendId,
                                               reason: .permissionDenied))
        }
        let acquires = await fixture.provider.acquireCount
        XCTAssertEqual(acquires, 0)
        XCTAssertEqual(fixture.microphone.startCount, 0)
        let fileAvailability = await fixture.backend.availability()
        XCTAssertEqual(fileAvailability.status, .available)
    }

    func testPermissionsChangedDuringSessionAcquireNeverStartCapture() async throws {
        let microphone = MicrophonePlatformBridge()
        let lease = MicrophoneSessionLease()
        let provider = MicrophoneSessionProvider(leases: [lease], holdAcquire: true)
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: MicrophoneFileBridge(),
            microphoneBridge: microphone, audioSessionProvider: provider)
        let start = Task { try await backend.startMicrophone(request) }
        await provider.waitUntilAcquireEntered()
        microphone.isRecordingAuthorized = false
        await provider.releaseAcquire()
        // Stop any incorrectly admitted task so a RED run leaves no live capture.
        do {
            let task = try await start.value
            await task.cancel()
            microphone.complete()
            microphone.emit(.cancelled)
            await backend.drain()
            XCTFail("revoked record permission must reject late admission")
        } catch {
            XCTAssertEqual(error as? SpeechClientError,
                           .backendUnavailable(capability: .asr, backendId: backend.backendId, reason: .permissionDenied))
        }
        let releases = await lease.count()
        XCTAssertEqual(releases, 1)
        XCTAssertEqual(microphone.startCount, 0)
    }

    func testSessionAcquireFailureDoesNotConsumeAudioSlot() async throws {
        let fixture = try makeBackend()
        let failure = SpeechRecognitionError.platform(domain: "fixture.acquire", code: 4)
        await fixture.provider.setAcquireError(failure)
        do { _ = try await fixture.backend.startMicrophone(request); XCTFail("acquire must fail") }
        catch { XCTAssertEqual(error as? SpeechRecognitionError, failure) }
        XCTAssertEqual(fixture.microphone.startCount, 0)
        await fixture.provider.setAcquireError(nil)
        let retry = try await fixture.backend.startMicrophone(request)
        await retry.cancel()
        fixture.microphone.complete()
        fixture.microphone.emit(.cancelled)
        await assertCancellation(retry)
    }

    func testStartupRollbackSurfacesLeaseReleaseFailure() async throws {
        let cleanup = SpeechRecognitionError.platform(domain: "fixture.cleanup", code: 51)
        for denied in [true, false] {
            let microphone = MicrophonePlatformBridge()
            let lease = MicrophoneSessionLease(releaseError: cleanup)
            let provider = MicrophoneSessionProvider(leases: [lease], holdAcquire: true)
            let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: MicrophoneFileBridge(),
                microphoneBridge: microphone, audioSessionProvider: provider)
            let start = Task { try await backend.startMicrophone(request) }
            await provider.waitUntilAcquireEntered()
            if denied { microphone.isRecordingAuthorized = false }
            else { microphone.startError = SpeechRecognitionError.platform(domain: "fixture.start", code: 9) }
            await provider.releaseAcquire()
            do { _ = try await start.value; XCTFail("rollback must surface cleanup failure") }
            catch { XCTAssertEqual(error as? SpeechRecognitionError, cleanup) }
            let releases = await lease.count()
            XCTAssertEqual(releases, 1)
            await backend.close()
        }
    }

    func testInvalidatedNativeStartSurfacesLeaseReleaseFailureAfterDrain() async throws {
        let cleanup = SpeechRecognitionError.platform(domain: "fixture.cleanup", code: 52)
        for closing in [false, true] {
            let fixture = try makeBackend(lease: MicrophoneSessionLease(releaseError: cleanup))
            fixture.microphone.holdStart = true
            let start = Task { try await fixture.backend.startMicrophone(request) }
            await waitUntil { fixture.microphone.startCount == 1 }
            let close: Task<Void, Never>?
            if closing {
                close = Task { await fixture.backend.close() }
                for _ in 0..<1_000 {
                    if await fixture.backend.availability().reason == .closed { break }
                    await Task.yield()
                }
            } else { close = nil; start.cancel() }
            fixture.microphone.releaseStart()
            await waitUntil { fixture.microphone.cancelCount == 1 }
            fixture.microphone.complete()
            await fixture.clock.advance(by: 0.02)
            do { _ = try await start.value; XCTFail("cleanup failure must remain visible") }
            catch { XCTAssertEqual(error as? SpeechRecognitionError, cleanup) }
            await close?.value
        }
    }

    func testNaturalFinalStopsCaptureWithoutExplicitFinish() async throws {
        let fixture = try makeBackend()
        let task = try await fixture.backend.startMicrophone(request)
        fixture.microphone.emit(.final(text: "natural", segments: []))
        fixture.microphone.complete()
        await fixture.clock.advance(by: 0.02)
        await waitUntil { fixture.microphone.stopCount == 1 }
        let result = try await task.result()
        XCTAssertEqual(result.transcript, "natural")
    }

    func testMicrophoneConstructorAdvertisesInputAndStartFailureReleasesLease() async throws {
        let fileBridge = MicrophoneFileBridge()
        let microphone = MicrophonePlatformBridge()
        let lease = MicrophoneSessionLease()
        let provider = MicrophoneSessionProvider(leases: [lease])
        let fileOnly = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: fileBridge)
        XCTAssertEqual(fileOnly.supportedInputs, [.audioFile])

        let backend = try AppleSpeechRecognizerBackend(
            locale: "en-US", bridge: fileBridge, microphoneBridge: microphone,
            audioSessionProvider: provider)
        XCTAssertEqual(backend.supportedInputs, [.audioFile, .microphone])
        microphone.startError = SpeechRecognitionError.platform(domain: "fixture.start", code: 3)
        do { _ = try await backend.startMicrophone(request); XCTFail("start must fail") }
        catch { XCTAssertEqual(error as? SpeechRecognitionError, .platform(domain: "fixture.start", code: 3)) }
        let releases = await lease.count()
        XCTAssertEqual(releases, 1)
        XCTAssertEqual(microphone.startCount, 1)
    }

    func testFinishStopsDeliveryEndsAudioOnceAndPublishesFinalAfterCleanup() async throws {
        let fixture = try makeBackend()
        let task = try await fixture.backend.startMicrophone(request)
        XCTAssertEqual(fixture.microphone.requiresOnDevice, true)
        await task.finishInput()
        await task.finishInput()
        XCTAssertEqual(fixture.microphone.stopCount, 1)
        XCTAssertEqual(fixture.microphone.endAudioCount, 1)
        fixture.microphone.emit(.final(text: "fixture", segments: [.init(confidence: 0.8)]))
        fixture.microphone.complete()
        await fixture.clock.advance(by: 0.02)
        let result = try await task.result()
        XCTAssertEqual(result.transcript, "fixture")
        XCTAssertEqual(result.confidence, 0.8)
        let releases = await fixture.lease.count()
        XCTAssertEqual(releases, 1)
        XCTAssertEqual(fixture.microphone.releaseCount, 1)
    }

    func testFinishWaitsForInFlightBufferBarrierBeforeLeaseRelease() async throws {
        let fixture = try makeBackend()
        fixture.microphone.holdStop = true
        let task = try await fixture.backend.startMicrophone(request)
        let finishing = Task { await task.finishInput() }
        await waitUntil { fixture.microphone.stopEntered }
        fixture.microphone.emit(.final(text: "fixture", segments: []))
        fixture.microphone.complete()
        await fixture.clock.advance(by: 0.02)
        let releasesBeforeBarrier = await fixture.lease.count()
        XCTAssertEqual(releasesBeforeBarrier, 0)
        fixture.microphone.releaseStop()
        await finishing.value
        await fixture.clock.advance(by: 0.02)
        _ = try await task.result()
        let releasesAfterBarrier = await fixture.lease.count()
        XCTAssertEqual(releasesAfterBarrier, 1)
    }

    func testCancelOverridesLateFinalAndIsIdempotent() async throws {
        let fixture = try makeBackend()
        let task = try await fixture.backend.startMicrophone(request)
        await task.cancel()
        await task.cancel()
        await task.finishInput()
        fixture.microphone.emit(.final(text: "late", segments: []))
        fixture.microphone.complete()
        await fixture.clock.advance(by: 0.02)
        await assertCancellation(task)
        XCTAssertEqual(fixture.microphone.stopCount, 1)
        XCTAssertEqual(fixture.microphone.endAudioCount, 1)
        XCTAssertEqual(fixture.microphone.cancelCount, 1)
        let releases = await fixture.lease.count()
        XCTAssertEqual(releases, 1)
    }

    func testDocumentedNoSpeechErrorReturnsNoResultAndReleasesSession() async throws {
        for (domain, code, expected) in [
            ("kAFAssistantErrorDomain", 1110, SpeechRecognitionError.noResult),
            ("fixture.other", 1110, .platform(domain: "fixture.other", code: 1110)),
            ("kAFAssistantErrorDomain", 1101, .platform(domain: "kAFAssistantErrorDomain", code: 1101))
        ] {
            let fixture = try makeBackend()
            let task = try await fixture.backend.startMicrophone(request)
            await task.finishInput()
            fixture.microphone.emit(.failed(domain: domain, code: code))
            fixture.microphone.complete()
            await fixture.clock.advance(by: 0.02)
            do { _ = try await task.result(); XCTFail("recognition must fail") }
            catch { XCTAssertEqual(error as? SpeechRecognitionError, expected) }
            let releases = await fixture.lease.count()
            XCTAssertEqual(releases, 1)
            XCTAssertEqual(fixture.microphone.releaseCount, 1)
        }
    }

    func testExplicitCancelRemainsCancelledWhenPlatformReportsNoSpeech() async throws {
        let fixture = try makeBackend()
        let task = try await fixture.backend.startMicrophone(request)
        await task.cancel()
        fixture.microphone.emit(.failed(domain: "kAFAssistantErrorDomain", code: 1110))
        fixture.microphone.complete()
        await fixture.clock.advance(by: 0.02)
        await assertCancellation(task)
    }

    func testPartialOnlyAfterFinishReturnsNoResult() async throws {
        let fixture = try makeBackend()
        let task = try await fixture.backend.startMicrophone(request)
        await task.finishInput()
        fixture.microphone.emit(.partial(text: "partial", segments: []))
        fixture.microphone.emit(.finished)
        fixture.microphone.complete()
        await fixture.clock.advance(by: 0.02)
        do { _ = try await task.result(); XCTFail("partial is not final") }
        catch { XCTAssertEqual(error as? SpeechRecognitionError, .noResult) }
    }

    func testTimeoutAndCaptureInvalidationDiscardLateFinal() async throws {
        let timeoutFixture = try makeBackend(timeout: 1)
        let timedTask = try await timeoutFixture.backend.startMicrophone(request)
        let timedResult = Task { try await timedTask.result() }
        await timeoutFixture.clock.waitForSleep(seconds: 1)
        await timeoutFixture.clock.advance(by: 1)
        await waitUntil { timeoutFixture.microphone.cancelCount == 1 }
        timeoutFixture.microphone.emit(.final(text: "late", segments: []))
        timeoutFixture.microphone.complete()
        await timeoutFixture.clock.advance(by: 0.02)
        do { _ = try await timedResult.value; XCTFail("timeout") }
        catch { XCTAssertEqual(error as? SpeechRecognitionError, .timedOut) }

        for code in [10, 20, 30] {
            let fixture = try makeBackend()
            let task = try await fixture.backend.startMicrophone(request)
            fixture.microphone.emit(.captureInvalidated(domain: "fixture.capture", code: code))
            await waitUntil { fixture.microphone.cancelCount == 1 }
            fixture.microphone.emit(.final(text: "late", segments: []))
            fixture.microphone.complete()
            await fixture.clock.advance(by: 0.02)
            do { _ = try await task.result(); XCTFail("capture invalidation") }
            catch { XCTAssertEqual(error as? SpeechRecognitionError,
                                   .platform(domain: "fixture.capture", code: code)) }
            XCTAssertEqual(fixture.microphone.cancelCount, 1)
        }
    }

    func testCompletedNativeTaskIsNotCancelledWhenInvalidationArrivesBeforeFinal() async throws {
        let fixture = try makeBackend()
        let task = try await fixture.backend.startMicrophone(request)
        fixture.microphone.emit(.captureInvalidated(domain: "fixture.capture", code: 10))
        fixture.microphone.emit(.final(text: "late", segments: []))
        fixture.microphone.complete()
        await fixture.clock.advance(by: 0.02)
        do { _ = try await task.result(); XCTFail("invalidation must discard final") }
        catch { XCTAssertEqual(error as? SpeechRecognitionError, .platform(domain: "fixture.capture", code: 10)) }
        XCTAssertEqual(fixture.microphone.cancelCount, 0)
        XCTAssertEqual(fixture.microphone.stopCount, 1)
    }

    func testFileAndMicrophoneShareOneSlotAndOldCallbackCannotPolluteReentry() async throws {
        let fixture = try makeBackend()
        let first = try await fixture.backend.startMicrophone(request)
        let file = try SpeechRecognitionRequest(locale: "en-US", audioFile: makeTemporaryFile())
        do { _ = try await fixture.backend.recognize(file); XCTFail("file must see busy microphone") }
        catch { XCTAssertEqual(error as? KitError, .busy) }
        let oldCallback = fixture.microphone.callback
        await first.finishInput()
        fixture.microphone.emit(.final(text: "first", segments: []))
        fixture.microphone.complete()
        await fixture.clock.advance(by: 0.02)
        _ = try await first.result()

        await fixture.provider.append(MicrophoneSessionLease())
        let second = try await fixture.backend.startMicrophone(request)
        oldCallback?(.failed(domain: "old", code: 1))
        await second.finishInput()
        fixture.microphone.emit(.final(text: "second", segments: []))
        fixture.microphone.complete()
        await fixture.clock.advance(by: 0.02)
        let secondResult = try await second.result()
        XCTAssertEqual(secondResult.transcript, "second")
    }

    func testCloseJoinsPendingSessionAcquireAndReleasesLateLease() async throws {
        let fileBridge = MicrophoneFileBridge()
        let microphone = MicrophonePlatformBridge()
        let lease = MicrophoneSessionLease()
        let provider = MicrophoneSessionProvider(leases: [lease], holdAcquire: true)
        let backend = try AppleSpeechRecognizerBackend(
            locale: "en-US", bridge: fileBridge, microphoneBridge: microphone,
            audioSessionProvider: provider)
        let start = Task { try await backend.startMicrophone(request) }
        await provider.waitUntilAcquireEntered()
        let close = Task { await backend.close() }
        XCTAssertEqual(microphone.startCount, 0)
        await provider.releaseAcquire()
        await close.value
        do { _ = try await start.value; XCTFail("closed start") }
        catch { XCTAssertEqual(error as? KitError, .closed) }
        let releases = await lease.count()
        XCTAssertEqual(releases, 1)
        XCTAssertEqual(microphone.startCount, 0)
    }

    func testCloseOrCallerCancellationDuringNativeStartDrainsLateTask() async throws {
        for closing in [false, true] {
            let fixture = try makeBackend()
            fixture.microphone.holdStart = true
            let start = Task { try await fixture.backend.startMicrophone(request) }
            await waitUntil { fixture.microphone.startCount == 1 }
            var closeReturned = false
            let close: Task<Void, Never>?
            if closing {
                close = Task { await fixture.backend.close(); closeReturned = true }
                for _ in 0..<1_000 {
                    if await fixture.backend.availability().reason == .closed { break }
                    await Task.yield()
                }
            } else {
                close = nil
                start.cancel()
            }
            fixture.microphone.releaseStart()
            await waitUntil { fixture.microphone.cancelCount == 1 }
            XCTAssertFalse(closeReturned)
            let releasesBeforeCompletion = await fixture.lease.count()
            XCTAssertEqual(releasesBeforeCompletion, 0)
            fixture.microphone.complete()
            await fixture.clock.advance(by: 0.02)
            do { _ = try await start.value; XCTFail("invalidated start cannot publish task") }
            catch {
                if closing { XCTAssertEqual(error as? KitError, .closed) }
                else { XCTAssertTrue(error is CancellationError) }
            }
            await close?.value
            let releases = await fixture.lease.count()
            XCTAssertEqual(releases, 1)
        }
    }

    func testSessionCleanupFailureCannotReturnRecognitionSuccess() async throws {
        let cleanupError = SpeechRecognitionError.platform(domain: "fixture.session", code: 9)
        let lease = MicrophoneSessionLease(releaseError: cleanupError)
        let fixture = try makeBackend(lease: lease)
        let task = try await fixture.backend.startMicrophone(request)
        await task.finishInput()
        fixture.microphone.emit(.final(text: "must not escape", segments: []))
        fixture.microphone.complete()
        await fixture.clock.advance(by: 0.02)
        do { _ = try await task.result(); XCTFail("cleanup failure must win") }
        catch { XCTAssertEqual(error as? SpeechRecognitionError, cleanupError) }
    }

    private func makeBackend(timeout: TimeInterval = 30,
                             lease: MicrophoneSessionLease = MicrophoneSessionLease()) throws -> MicrophoneFixture {
        let file = MicrophoneFileBridge()
        let microphone = MicrophonePlatformBridge()
        let provider = MicrophoneSessionProvider(leases: [lease])
        let clock = MicrophoneClock()
        let backend = try AppleSpeechRecognizerBackend(
            locale: "en-US", timeout: timeout, bridge: file,
            microphoneBridge: microphone, audioSessionProvider: provider, clock: clock)
        return .init(backend: backend, microphone: microphone, provider: provider, lease: lease, clock: clock)
    }

    private func makeTemporaryFile() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("odai-mic-\(UUID().uuidString).wav")
        try Data([0, 1]).write(to: url)
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        return url
    }

    private func waitUntil(_ predicate: @escaping @MainActor () -> Bool) async {
        for _ in 0..<1_000 where !predicate() { await Task.yield() }
        XCTAssertTrue(predicate())
    }

    private func assertCancellation(_ task: any SpeechMicrophoneRecognitionTask) async {
        do { _ = try await task.result(); XCTFail("expected cancellation") }
        catch is CancellationError {}
        catch { XCTFail("unexpected error: \(error)") }
    }
}

@MainActor
private struct MicrophoneFixture {
    let backend: AppleSpeechRecognizerBackend
    let microphone: MicrophonePlatformBridge
    let provider: MicrophoneSessionProvider
    let lease: MicrophoneSessionLease
    let clock: MicrophoneClock
}

private actor MicrophoneSessionLease: AppleSpeechAudioSessionLease {
    private let releaseError: Error?
    private(set) var releaseCount = 0
    init(releaseError: Error? = nil) { self.releaseError = releaseError }
    func release() async throws {
        guard releaseCount == 0 else { return }
        releaseCount += 1
        if let releaseError { throw releaseError }
    }
    func count() -> Int { releaseCount }
}

private actor MicrophoneSessionProvider: AppleSpeechAudioSessionProvider {
    private var leases: [MicrophoneSessionLease]
    private var holdAcquire: Bool
    private var acquireWaiter: CheckedContinuation<Void, Never>?
    private var enteredWaiters: [CheckedContinuation<Void, Never>] = []
    private var acquireEntered = false
    private(set) var acquireCount = 0
    private var acquireError: Error?

    init(leases: [MicrophoneSessionLease], holdAcquire: Bool = false) {
        self.leases = leases; self.holdAcquire = holdAcquire
    }
    func acquire() async throws -> any AppleSpeechAudioSessionLease {
        acquireCount += 1
        if let acquireError { throw acquireError }
        acquireEntered = true
        enteredWaiters.forEach { $0.resume() }; enteredWaiters.removeAll()
        if holdAcquire { await withCheckedContinuation { acquireWaiter = $0 } }
        return leases.removeFirst()
    }
    func append(_ lease: MicrophoneSessionLease) { leases.append(lease) }
    func setAcquireError(_ error: Error?) { acquireError = error }
    func waitUntilAcquireEntered() async {
        if acquireEntered { return }
        await withCheckedContinuation { enteredWaiters.append($0) }
    }
    func releaseAcquire() { holdAcquire = false; acquireWaiter?.resume(); acquireWaiter = nil }
}

@MainActor
private final class MicrophoneFileBridge: AppleSpeechPlatformBridge {
    var stateValue: AppleSpeechTaskState = .running
    var callback: (@Sendable (AppleSpeechPlatformEvent) -> Void)?
    func metadata() -> AppleSpeechMetadata {
        .init(authorization: .authorized, isAvailable: true, supportsOnDeviceRecognition: true)
    }
    func start(fileURL: URL, requiresOnDeviceRecognition: Bool,
               callback: @escaping @Sendable (AppleSpeechPlatformEvent) -> Void) throws -> AppleSpeechTaskHandle {
        self.callback = callback; stateValue = .running; return AppleSpeechTaskHandle()
    }
    func cancel(_ handle: AppleSpeechTaskHandle) { stateValue = .canceling }
    func state(of handle: AppleSpeechTaskHandle) -> AppleSpeechTaskState { stateValue }
    func release(_ handle: AppleSpeechTaskHandle) {}
}

@MainActor
private final class MicrophonePlatformBridge: AppleSpeechMicrophonePlatformBridge {
    var isRecordingAuthorized = true
    var callback: (@Sendable (AppleSpeechPlatformEvent) -> Void)?
    var stateValue: AppleSpeechTaskState = .running
    var startError: Error?
    var startCount = 0
    var stopCount = 0
    var endAudioCount = 0
    var cancelCount = 0
    var releaseCount = 0
    var requiresOnDevice: Bool?
    var holdStop = false
    var stopEntered = false
    var holdStart = false
    private var startWaiter: CheckedContinuation<Void, Never>?
    private var stopWaiter: CheckedContinuation<Void, Never>?

    func metadata() -> AppleSpeechMetadata {
        .init(authorization: .authorized, isAvailable: true, supportsOnDeviceRecognition: true)
    }
    func start(requiresOnDeviceRecognition: Bool,
               callback: @escaping @Sendable (AppleSpeechPlatformEvent) -> Void) async throws -> AppleSpeechTaskHandle {
        startCount += 1
        if let startError { throw startError }
        self.callback = callback; requiresOnDevice = requiresOnDeviceRecognition; stateValue = .running
        if holdStart { await withCheckedContinuation { startWaiter = $0 } }
        return AppleSpeechTaskHandle()
    }
    func stopCapture(_ handle: AppleSpeechTaskHandle, endAudio: Bool) async throws {
        stopCount += 1; if endAudio { endAudioCount += 1 }; stopEntered = true
        if holdStop { await withCheckedContinuation { stopWaiter = $0 } }
    }
    func cancel(_ handle: AppleSpeechTaskHandle) { cancelCount += 1; stateValue = .canceling }
    func state(of handle: AppleSpeechTaskHandle) -> AppleSpeechTaskState { stateValue }
    func release(_ handle: AppleSpeechTaskHandle) { releaseCount += 1 }
    func emit(_ event: AppleSpeechPlatformEvent) { callback?(event) }
    func complete() { stateValue = .completed }
    func releaseStop() { holdStop = false; stopWaiter?.resume(); stopWaiter = nil }
    func releaseStart() { holdStart = false; startWaiter?.resume(); startWaiter = nil }
}

private actor MicrophoneClock: AppleSpeechClock {
    private struct Sleeper { let deadline: TimeInterval; let continuation: CheckedContinuation<Void, Error> }
    private var now: TimeInterval = 0
    private var sleepers: [UUID: Sleeper] = [:]
    private var observers: [(TimeInterval, CheckedContinuation<Void, Never>)] = []
    private var durations: Set<TimeInterval> = []
    func sleep(seconds: TimeInterval) async throws {
        let id = UUID()
        try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                sleepers[id] = .init(deadline: now + seconds, continuation: continuation)
                durations.insert(seconds)
                let ready = observers.filter { $0.0 == seconds }
                observers.removeAll { $0.0 == seconds }
                ready.forEach { $0.1.resume() }
            }
        }, onCancel: { Task { await self.cancel(id) } })
    }
    func waitForSleep(seconds: TimeInterval) async {
        if durations.contains(seconds) { return }
        await withCheckedContinuation { observers.append((seconds, $0)) }
    }
    func advance(by seconds: TimeInterval) {
        now += seconds
        let ready = sleepers.filter { $0.value.deadline <= now }
        ready.forEach { sleepers.removeValue(forKey: $0.key)?.continuation.resume() }
        durations.removeAll()
    }
    private func cancel(_ id: UUID) { sleepers.removeValue(forKey: id)?.continuation.resume(throwing: CancellationError()) }
}
