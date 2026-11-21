import XCTest
import ODAIKitCore
@testable import ODAIKitAppleSpeech
@testable import ODAIKitSpeech

@MainActor
final class AppleSpeechRecognizerBackendTests: XCTestCase {
    func testConstructorValidatesTimeoutAndCanonicalLocale() throws {
        let bridge = FakeAppleSpeechBridge()
        let backend = try AppleSpeechRecognizerBackend(locale: " en_us ", timeout: 1, bridge: bridge)
        XCTAssertEqual(backend.locale, "en_US")
        XCTAssertEqual(backend.timeout, 1)
        XCTAssertThrowsError(try AppleSpeechRecognizerBackend(locale: "en-US", timeout: 0, bridge: bridge))
        XCTAssertThrowsError(try AppleSpeechRecognizerBackend(locale: "en-US", timeout: 301, bridge: bridge))
        XCTAssertThrowsError(try AppleSpeechRecognizerBackend(locale: "en-US", timeout: .nan, bridge: bridge))
        XCTAssertThrowsError(try AppleSpeechRecognizerBackend(locale: "en-US", timeout: .infinity, bridge: bridge))
        XCTAssertNoThrow(try AppleSpeechRecognizerBackend(locale: "en-US", timeout: 300, bridge: bridge))
        XCTAssertThrowsError(try AppleSpeechRecognizerBackend(locale: "   ", bridge: bridge))
    }

    func testAvailabilityDoesNotPromptAndMapsBoundLocaleMetadata() async throws {
        let bridge = FakeAppleSpeechBridge(metadata: .init(authorization: .notDetermined,
                                                             isAvailable: true,
                                                             supportsOnDeviceRecognition: true))
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge)
        let availability = await backend.availability()
        XCTAssertEqual(availability.status, .unavailable)
        XCTAssertEqual(availability.reason, .permissionDenied)
        XCTAssertEqual(availability.backendId, "apple.speech.recognizer")
    }

    func testRecognizeRejectsLocaleMismatchAndInvalidFilesBeforeStart() async throws {
        let bridge = FakeAppleSpeechBridge()
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge)
        let mismatchFile = try makeTemporaryFile()
        let mismatch = try SpeechRecognitionRequest(locale: "fr-FR", audioFile: mismatchFile)
        do {
            _ = try await backend.recognize(mismatch)
            XCTFail("locale mismatch must be rejected")
        } catch { XCTAssertEqual(error as? KitError, .invalidInput("locale does not match backend")) }
        XCTAssertEqual(bridge.startCount, 0)

        let missing = try SpeechRecognitionRequest(locale: "en-US", audioFile: URL(fileURLWithPath: "/tmp/odai-missing-\(UUID().uuidString).wav"))
        do {
            _ = try await backend.recognize(missing)
            XCTFail("missing file must be rejected")
        } catch {
            guard case .invalidInput = error as? KitError else { return XCTFail("unexpected error: \(error)") }
        }
        XCTAssertEqual(bridge.startCount, 0)
    }

    func testRecognizeForcesOnDeviceAndReturnsFinalOnlyAfterCompleted() async throws {
        let bridge = FakeAppleSpeechBridge()
        let clock = ManualAppleSpeechClock()
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge, clock: clock)
        let file = try makeTemporaryFile()
        let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: file)
        let operation = Task { try await backend.recognize(request) }
        await waitUntil { bridge.startCount == 1 }
        XCTAssertEqual(bridge.lastRequiresOnDeviceRecognition, true)
        bridge.emit(.final(text: "hello", segments: [.init(confidence: 0.2), .init(confidence: 0.8)]))
        await clock.waitForSleep(seconds: 0.02)
        for _ in 0..<100 { await Task.yield() }
        XCTAssertEqual(bridge.releaseCount, 0)
        XCTAssertEqual(bridge.releaseCount, 0)
        bridge.complete()
        await clock.advance(by: 0.02)
        let result = try await operation.value
        XCTAssertEqual(result.transcript, "hello")
        XCTAssertEqual(result.confidence, 0.5)
        XCTAssertEqual(bridge.releaseCount, 1)
    }

    func testCancelAndCloseRetainLeaseUntilCompleted() async throws {
        let bridge = FakeAppleSpeechBridge()
        let clock = ManualAppleSpeechClock()
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge, clock: clock)
        let file = try makeTemporaryFile()
        let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: file)
        let first = Task { try await backend.recognize(request) }
        await waitUntil { bridge.startCount == 1 }
        first.cancel()
        bridge.emit(.cancelled)
        await assertThrowsCancellation(first)
        XCTAssertEqual(bridge.releaseCount, 0)
        await clock.waitForSleep(seconds: 0.02)
        bridge.complete()
        await clock.advance(by: 0.02)
        await backend.close()
        XCTAssertEqual(bridge.releaseCount, 1)

        let second = Task { try await backend.recognize(request) }
        do { _ = try await second.value; XCTFail("closed backend must reject") }
        catch { XCTAssertEqual(error as? KitError, .closed) }
    }

    func testAllUnavailableStatesRejectAdmission() async throws {
        for metadata in [
            AppleSpeechMetadata(authorization: .denied, isAvailable: true, supportsOnDeviceRecognition: true),
            .init(authorization: .restricted, isAvailable: true, supportsOnDeviceRecognition: true),
            .init(authorization: .authorized, isAvailable: false, supportsOnDeviceRecognition: true),
            .init(authorization: .authorized, isAvailable: true, supportsOnDeviceRecognition: false)
        ] {
            let bridge = FakeAppleSpeechBridge(metadata: metadata)
            let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge)
            let available = await backend.availability()
            XCTAssertEqual(available.status, .unavailable)
            let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: makeTemporaryFile())
            do { _ = try await backend.recognize(request); XCTFail("unavailable") }
            catch { XCTAssertNotNil(error as? SpeechClientError) }
            XCTAssertEqual(bridge.startCount, 0)
        }
    }

    func testDirectoryAndUnreadableFilesNeverStartRecognition() async throws {
        let bridge = FakeAppleSpeechBridge()
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge)
        let file = try makeTemporaryFile()
        try FileManager.default.setAttributes([.posixPermissions: 0], ofItemAtPath: file.path)
        for url in [file, FileManager.default.temporaryDirectory] {
            let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: url)
            do { _ = try await backend.recognize(request); XCTFail("invalid file") }
            catch { XCTAssertNotNil(error as? KitError) }
        }
        XCTAssertEqual(bridge.startCount, 0)
    }

    func testTimeoutCancelsOnceButBlocksAdmissionUntilRelease() async throws {
        let bridge = FakeAppleSpeechBridge()
        let clock = ManualAppleSpeechClock()
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", timeout: 1, bridge: bridge, clock: clock)
        let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: makeTemporaryFile())
        let operation = Task { try await backend.recognize(request) }
        await clock.waitForSleep(seconds: 1)
        await clock.advance(by: 1)
        do { _ = try await operation.value; XCTFail("timeout") }
        catch { XCTAssertEqual(error as? SpeechRecognitionError, .timedOut) }
        XCTAssertEqual(bridge.cancellationCount, 1)
        XCTAssertEqual(bridge.releaseCount, 0)
        do { _ = try await backend.recognize(request); XCTFail("busy") }
        catch { XCTAssertEqual(error as? KitError, .busy) }
        await clock.waitForSleep(seconds: 0.02)
        bridge.complete()
        await clock.advance(by: 0.02)
        await backend.close()
        XCTAssertEqual(bridge.releaseCount, 1)
        XCTAssertEqual(bridge.cancellationCount, 1)
    }

    func testCompletedWithoutFinalFailsAndAllowsReentryIgnoringOldCallback() async throws {
        let bridge = FakeAppleSpeechBridge()
        let clock = ManualAppleSpeechClock()
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge, clock: clock)
        let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: makeTemporaryFile())
        let first = Task { try await backend.recognize(request) }
        await clock.waitForSleep(seconds: 0.02)
        let oldCallback = bridge.callback
        bridge.emit(.partial(text: "partial", segments: []))
        bridge.emit(.finished)
        bridge.complete()
        await clock.advance(by: 0.02)
        do { _ = try await first.value; XCTFail("partial is not final") }
        catch { XCTAssertEqual(error as? SpeechRecognitionError, .noResult) }

        let second = Task { try await backend.recognize(request) }
        await waitUntil { bridge.startCount == 2 }
        oldCallback?(.failed(domain: "old", code: 1))
        bridge.emit(.final(text: "new transcript", segments: [.init(confidence: .nan)]))
        await clock.waitForSleep(seconds: 0.02)
        bridge.complete()
        await clock.advance(by: 0.02)
        let result = try await second.value
        XCTAssertEqual(result.transcript, "new transcript")
        XCTAssertNil(result.confidence)
        XCTAssertEqual(bridge.releaseCount, 2)
    }

    func testSynchronousFinalCallbackDuringStartIsNotLost() async throws {
        let bridge = FakeAppleSpeechBridge()
        bridge.synchronousEvent = .final(text: "synchronous", segments: [])
        let clock = ManualAppleSpeechClock()
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge, clock: clock)
        let request = try SpeechRecognitionRequest(locale: "en_US", audioFile: makeTemporaryFile())
        let operation = Task { try await backend.recognize(request) }
        await clock.waitForSleep(seconds: 0.02)
        bridge.complete()
        await clock.advance(by: 0.02)
        let result = try await operation.value
        XCTAssertEqual(result.transcript, "synchronous")
    }

    func testCompletedStateDoesNotDiscardFinalCallbackStillInFlight() async throws {
        let bridge = FakeAppleSpeechBridge()
        let clock = ManualAppleSpeechClock()
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge, clock: clock)
        let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: makeTemporaryFile())
        let operation = Task { try await backend.recognize(request) }
        await clock.waitForSleep(seconds: 0.02)
        bridge.complete()
        await clock.advance(by: 0.02)
        for _ in 0..<100 { await Task.yield() }
        bridge.emit(.final(text: "late final", segments: []))
        let result = try await operation.value
        XCTAssertEqual(result.transcript, "late final")
        XCTAssertEqual(bridge.releaseCount, 1)
    }

    func testPlatformErrorWaitsForCompletedAndPreservesDomainCode() async throws {
        let bridge = FakeAppleSpeechBridge()
        let clock = ManualAppleSpeechClock()
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge, clock: clock)
        let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: makeTemporaryFile())
        let operation = Task { try await backend.recognize(request) }
        await clock.waitForSleep(seconds: 0.02)
        bridge.emit(.failed(domain: "fixture.error", code: 102))
        await Task.yield()
        XCTAssertEqual(bridge.releaseCount, 0)
        bridge.complete()
        await clock.advance(by: 0.02)
        do { _ = try await operation.value; XCTFail("platform error") }
        catch { XCTAssertEqual(error as? SpeechRecognitionError, .platform(domain: "fixture.error", code: 102)) }
    }

    func testCloseCancelsActiveTaskAndAllCloseWaitersAwaitRelease() async throws {
        let bridge = FakeAppleSpeechBridge()
        let clock = ManualAppleSpeechClock()
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge, clock: clock)
        let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: makeTemporaryFile())
        let recognition = Task { try await backend.recognize(request) }
        await clock.waitForSleep(seconds: 0.02)
        var returned = 0
        let first = Task { await backend.close(); returned += 1 }
        let second = Task { await backend.close(); returned += 1 }
        await waitUntil { bridge.cancellationCount == 1 }
        XCTAssertEqual(returned, 0)
        first.cancel()
        XCTAssertEqual(bridge.releaseCount, 0)
        bridge.complete()
        await clock.advance(by: 0.02)
        await first.value
        await second.value
        await assertThrowsCancellation(recognition)
        XCTAssertEqual(returned, 2)
        XCTAssertEqual(bridge.releaseCount, 1)
        XCTAssertEqual(bridge.cancellationCount, 1)
    }

    func testConfidenceFiltersInvalidSegmentsAndRetainsConcreteFinalConfidence() async throws {
        let bridge = FakeAppleSpeechBridge()
        let clock = ManualAppleSpeechClock()
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge, clock: clock)
        let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: makeTemporaryFile())
        let recognition = Task { try await backend.recognize(request) }
        await clock.waitForSleep(seconds: 0.02)
        bridge.emit(.final(text: "first", segments: [0.2, 0.8, .nan, -1, 2].map { .init(confidence: $0) }))
        for _ in 0..<100 { await Task.yield() }
        bridge.emit(.final(text: "latest", segments: [.init(confidence: .nan)]))
        for _ in 0..<10 { await Task.yield() }
        bridge.complete()
        await clock.advance(by: 0.02)
        let result = try await recognition.value
        XCTAssertEqual(result.transcript, "latest")
        XCTAssertEqual(result.confidence, 0.5)
    }

    func testPreCancelledCallStartsNoPlatformTask() async throws {
        let bridge = FakeAppleSpeechBridge()
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge)
        let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: makeTemporaryFile())
        let recognition = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return try await backend.recognize(request)
        }
        await assertThrowsCancellation(recognition)
        XCTAssertEqual(bridge.startCount, 0)
    }

    func testDrainAllowsCancellationReleaseThenBackendReuse() async throws {
        let bridge = FakeAppleSpeechBridge()
        let clock = ManualAppleSpeechClock()
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge, clock: clock)
        let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: makeTemporaryFile())
        let first = Task { try await backend.recognize(request) }
        await clock.waitForSleep(seconds: 0.02)
        first.cancel()
        await assertThrowsCancellation(first)
        var drained = false
        let drain = Task { await backend.drain(); drained = true }
        for _ in 0..<100 { await Task.yield() }
        XCTAssertFalse(drained)
        bridge.complete()
        await clock.advance(by: 0.02)
        await drain.value
        XCTAssertEqual(bridge.releaseCount, 1)
        bridge.synchronousEvent = .final(text: "reused", segments: [])
        let next = Task { try await backend.recognize(request) }
        await clock.waitForSleep(seconds: 0.02)
        bridge.complete()
        await clock.advance(by: 0.02)
        let result = try await next.value
        XCTAssertEqual(result.transcript, "reused")
    }

    func testStartFailureDoesNotConsumeAdmission() async throws {
        let bridge = FakeAppleSpeechBridge()
        bridge.startError = SpeechRecognitionError.platform(domain: "fixture.start", code: 1)
        let clock = ManualAppleSpeechClock()
        let backend = try AppleSpeechRecognizerBackend(locale: "en-US", bridge: bridge, clock: clock)
        let request = try SpeechRecognitionRequest(locale: "en-US", audioFile: makeTemporaryFile())
        do { _ = try await backend.recognize(request); XCTFail("start failure") }
        catch { XCTAssertEqual(error as? SpeechRecognitionError, .platform(domain: "fixture.start", code: 1)) }
        bridge.startError = nil
        bridge.synchronousEvent = .final(text: "retried", segments: [])
        let retry = Task { try await backend.recognize(request) }
        await clock.waitForSleep(seconds: 0.02)
        do { _ = try await backend.recognize(request); XCTFail("busy must reject") }
        catch { XCTAssertEqual(error as? KitError, .busy) }
        XCTAssertEqual(bridge.cancellationCount, 0)
        bridge.complete()
        await clock.advance(by: 0.02)
        let retryResult = try await retry.value
        XCTAssertEqual(retryResult.transcript, "retried")
    }

    private func makeTemporaryFile() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("odai-asr-\(UUID().uuidString).wav")
        try Data([0, 1, 2]).write(to: url)
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        return url
    }

    private func waitUntil(_ predicate: @escaping @MainActor () -> Bool) async {
        for _ in 0..<100 where !predicate() { await Task.yield() }
        XCTAssertTrue(predicate())
    }

    private func assertThrowsCancellation<T>(_ task: Task<T, Error>) async {
        do { _ = try await task.value; XCTFail("expected cancellation") }
        catch is CancellationError {}
        catch { XCTFail("unexpected error: \(error)") }
    }
}

@MainActor
private final class FakeAppleSpeechBridge: AppleSpeechPlatformBridge {
    var metadataValue: AppleSpeechMetadata
    var startCount = 0
    var lastRequiresOnDeviceRecognition: Bool?
    var callback: (@Sendable (AppleSpeechPlatformEvent) -> Void)?
    var stateValue: AppleSpeechTaskState = .running
    var handle = AppleSpeechTaskHandle()
    var synchronousEvent: AppleSpeechPlatformEvent?
    var startError: Error?
    var cancellationCount = 0
    var releaseCount = 0

    init(metadata: AppleSpeechMetadata = .init(authorization: .authorized,
                                                isAvailable: true,
                                                supportsOnDeviceRecognition: true)) {
        metadataValue = metadata
    }

    func metadata() -> AppleSpeechMetadata { metadataValue }

    func start(fileURL: URL, requiresOnDeviceRecognition: Bool,
               callback: @escaping @Sendable (AppleSpeechPlatformEvent) -> Void) throws -> AppleSpeechTaskHandle {
        startCount += 1
        if let startError { throw startError }
        lastRequiresOnDeviceRecognition = requiresOnDeviceRecognition
        self.callback = callback
        stateValue = .running
        handle = AppleSpeechTaskHandle()
        if let synchronousEvent { callback(synchronousEvent) }
        return handle
    }

    func cancel(_ handle: AppleSpeechTaskHandle) { cancellationCount += 1; stateValue = .canceling }
    func state(of handle: AppleSpeechTaskHandle) -> AppleSpeechTaskState { stateValue }
    func release(_ handle: AppleSpeechTaskHandle) { XCTAssertEqual(stateValue, .completed); releaseCount += 1 }
    func emit(_ event: AppleSpeechPlatformEvent) { callback?(event) }
    func complete() { stateValue = .completed }
}

private actor ManualAppleSpeechClock: AppleSpeechClock {
    private struct Sleeper {
        let deadline: TimeInterval
        let continuation: CheckedContinuation<Void, Error>
    }
    private var now: TimeInterval = 0
    private var sleepers: [UUID: Sleeper] = [:]
    private var sleepObservers: [(TimeInterval, CheckedContinuation<Void, Never>)] = []
    private var registeredDurations: Set<TimeInterval> = []

    func sleep(seconds: TimeInterval) async throws {
        let id = UUID()
        try Task.checkCancellation()
        try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                sleepers[id] = Sleeper(deadline: now + seconds, continuation: continuation)
                registeredDurations.insert(seconds)
                let ready = sleepObservers.filter { $0.0 == seconds }
                sleepObservers.removeAll { $0.0 == seconds }
                for (_, observer) in ready { observer.resume() }
            }
        }, onCancel: { Task { await self.cancel(id) } })
    }

    func waitForSleep(seconds: TimeInterval) async {
        if registeredDurations.contains(seconds) { return }
        await withCheckedContinuation { sleepObservers.append((seconds, $0)) }
    }

    func advance(by seconds: TimeInterval) {
        now += seconds
        let ready = sleepers.filter { $0.value.deadline <= now }
        for (id, sleeper) in ready { sleepers.removeValue(forKey: id); sleeper.continuation.resume() }
        registeredDurations.removeAll()
    }

    private func cancel(_ id: UUID) { sleepers.removeValue(forKey: id)?.continuation.resume(throwing: CancellationError()) }
}
