import Foundation
import XCTest
import ODAIKitCore
import ODAIKitSpeech
#if ODAI_DEMO_BINDING
import ODAIKitDemo
#else
import ODAIKitBinding
#endif

@MainActor
final class IOSSpeechBridgeTests: XCTestCase {
    private func fixture(block: Bool = false, queryGate: Signal? = nil, includeTts: Bool = true) throws -> Fixture {
        let f = try Fixture(block: block, queryGate: queryGate, includeTts: includeTts)
        addTeardownBlock { await f.shutdown() }
        return f
    }

    func testCancelledRecognitionRetainsLeaseUntilDrain() async throws {
        let f = try fixture(block: true)
        let token = try f.store.importAudio(from: f.source)
        let lease = try f.store.acquire(reference: token)
        let copy = lease.url
        lease.release()
        let callback = AsrCapture()
        callback.ended = {
            XCTAssertFalse(FileManager.default.fileExists(atPath: copy.path))
            let next = TtsCapture()
            _ = f.bridge.speak(request: f.ttsRequest, selection: f.defaultSelection, callback: next)
            XCTAssertNil(next.failure, "slot must be clear inside the terminal callback")
        }
        let task = f.bridge.recognize(request: f.request(token), selection: f.defaultSelection, callback: callback)
        await fulfillment(of: [f.asr.started], timeout: 3)
        f.store.remove(reference: token)
        task.cancel()
        await fulfillment(of: [f.asr.draining], timeout: 3)
        XCTAssertTrue(FileManager.default.fileExists(atPath: copy.path))
        XCTAssertEqual(callback.terminals, 0)
        let busy = TtsCapture()
        _ = f.bridge.speak(request: f.ttsRequest, selection: f.defaultSelection, callback: busy)
        XCTAssertEqual(busy.failure?.code, .busy)
        await f.asr.drainGate.open()
        await fulfillment(of: [callback.done], timeout: 3)
        XCTAssertEqual(callback.terminals, 1)
        XCTAssertTrue(callback.cancelled)
        XCTAssertNil(callback.result, "late native success must not replace cancellation")
    }

    func testReplacedInputDeletedOnlyAfterLastLease() throws {
        let f = try fixture()
        let token = try f.store.importAudio(from: f.source)
        let a = try f.store.acquire(reference: token)
        let b = try f.store.acquire(reference: token)
        let replacement = try f.store.importAudio(from: f.source)
        f.store.remove(reference: token)
        XCTAssertThrowsError(try f.store.acquire(reference: token))
        a.release(); a.release()
        XCTAssertTrue(FileManager.default.fileExists(atPath: b.url.path))
        f.store.close()
        XCTAssertThrowsError(try f.store.acquire(reference: replacement))
        XCTAssertTrue(FileManager.default.fileExists(atPath: b.url.path))
        b.release()
        XCTAssertFalse(FileManager.default.fileExists(atPath: b.url.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: f.source.path), "never delete the original")
    }

    func testUnknownTokenAndFileOnlyMicrophoneAreRejected() async throws {
        let f = try fixture()
        for (input, code) in [(SpeechBridgeInputAudioFile(reference: "unknown") as any SpeechBridgeInput,
                              SpeechBridgeFailureCode.invalidInput),
                             (SpeechBridgeInputMicrophone.shared, .unsupportedInput)] {
            let callback = AsrCapture()
            let task = f.bridge.recognize(request: .init(locale: "en-US", input: input),
                                   selection: f.defaultSelection, callback: callback)
            if input is SpeechBridgeInputMicrophone {
                XCTAssertTrue(task is any SpeechBridgeMicrophoneTask)
                await fulfillment(of: [callback.done], timeout: 3)
            }
            XCTAssertEqual(callback.failure?.code, code)
            XCTAssertEqual(callback.terminals, 1)
        }
    }

    func testMicrophoneMetadataComesFromNativeAndNeverAcquiresFileLease() async throws {
        let f = try microphoneFixture()
        let query = BackendsCapture()
        _ = f.bridge.queryBackends(capability: .asr, callback: query)
        await fulfillment(of: [query.done], timeout: 3)
        XCTAssertEqual(query.backends?.first?.supportedInputs, [.audioFile, .microphone])
        let callback = AsrCapture()
        let task = f.bridge.recognize(request: f.request, selection: f.selection, callback: callback)
        let microphone = try XCTUnwrap(task as? any SpeechBridgeMicrophoneTask)
        microphone.finishInput(); microphone.finishInput()
        await fulfillment(of: [f.backend.native.finished], timeout: 3)
        XCTAssertEqual(callback.terminals, 0)
        await f.backend.native.complete()
        await fulfillment(of: [callback.done], timeout: 3)
        XCTAssertNotNil(callback.result)
        XCTAssertEqual(f.inputs.acquires, 0)
        let finishes = await f.backend.native.finishCount
        XCTAssertEqual(finishes, 1)
    }

    func testFinishBeforeSuspendedNativeStartAndOldHandleCannotAffectReentry() async throws {
        let f = try microphoneFixture(holdStart: true)
        let first = AsrCapture()
        let task = try XCTUnwrap(f.bridge.recognize(request: f.request, selection: f.selection,
            callback: first) as? any SpeechBridgeMicrophoneTask)
        await fulfillment(of: [f.backend.started], timeout: 3)
        task.finishInput()
        await f.backend.startGate.open()
        await fulfillment(of: [f.backend.native.finished], timeout: 3)
        await f.backend.native.complete()
        await fulfillment(of: [first.done], timeout: 3)
        let nextNative = MicNativeTask()
        await f.backend.replace(nextNative)
        let next = AsrCapture()
        let nextTask = try XCTUnwrap(f.bridge.recognize(request: f.request, selection: f.selection,
            callback: next) as? any SpeechBridgeMicrophoneTask)
        task.finishInput(); task.cancel()
        nextTask.finishInput()
        await fulfillment(of: [nextNative.finished], timeout: 3)
        await nextNative.complete()
        await fulfillment(of: [next.done], timeout: 3)
        XCTAssertNotNil(next.result)
        let cancels = await nextNative.cancelCount
        XCTAssertEqual(cancels, 0)
    }

    func testCancelBeforeWorkerSuppressesPendingFinishAndNeverStartsNative() async throws {
        let f = try microphoneFixture()
        let callback = AsrCapture()
        let task = try XCTUnwrap(f.bridge.recognize(request: f.request, selection: f.selection,
            callback: callback) as? any SpeechBridgeMicrophoneTask)
        task.finishInput(); task.cancel(); task.finishInput()
        await fulfillment(of: [callback.done], timeout: 3)
        XCTAssertTrue(callback.cancelled)
        let starts = await f.backend.startCount
        XCTAssertEqual(starts, 0)
    }

    func testCancelDuringNativeStartJoinsDrainAndConcurrentClose() async throws {
        let f = try microphoneFixture(holdStart: true, holdDrain: true)
        let callback = AsrCapture()
        let task = try XCTUnwrap(f.bridge.recognize(request: f.request, selection: f.selection,
            callback: callback) as? any SpeechBridgeMicrophoneTask)
        await fulfillment(of: [f.backend.started], timeout: 3)
        task.finishInput(); task.cancel()
        let close = CloseCapture(), another = CloseCapture()
        f.bridge.close(callback: close); f.bridge.close(callback: another)
        await f.backend.startGate.open()
        await fulfillment(of: [f.backend.native.cancelled, f.backend.draining], timeout: 3)
        XCTAssertEqual(callback.terminals + close.count + another.count, 0)
        let finishes = await f.backend.native.finishCount
        XCTAssertEqual(finishes, 0)
        await f.backend.drainGate.open()
        await fulfillment(of: [callback.done, close.done, another.done], timeout: 3)
        XCTAssertTrue(callback.cancelled)
        XCTAssertEqual(callback.terminals, 1)
    }

    func testCancelAfterFinishDiscardsLateFinalAndHoldsSlotUntilDrain() async throws {
        let f = try microphoneFixture(holdDrain: true)
        let callback = AsrCapture()
        callback.ended = {
            let next = AsrCapture()
            let returned = f.bridge.recognize(request: .init(locale: " ", input: SpeechBridgeInputMicrophone.shared),
                selection: f.selection, callback: next)
            XCTAssertFalse(returned is any SpeechBridgeMicrophoneTask)
            XCTAssertEqual(next.failure?.code, .invalidInput, "terminal callback must permit reentry")
        }
        let task = try XCTUnwrap(f.bridge.recognize(request: f.request, selection: f.selection,
            callback: callback) as? any SpeechBridgeMicrophoneTask)
        task.finishInput()
        await fulfillment(of: [f.backend.native.finished], timeout: 3)
        task.cancel()
        await fulfillment(of: [f.backend.native.cancelled], timeout: 3)
        await f.backend.native.complete()
        await fulfillment(of: [f.backend.draining], timeout: 3)
        let busy = AsrCapture()
        _ = f.bridge.recognize(request: f.request, selection: f.selection, callback: busy)
        XCTAssertEqual(busy.failure?.code, .busy)
        XCTAssertEqual(callback.terminals, 0)
        await f.backend.drainGate.open()
        await fulfillment(of: [callback.done], timeout: 3)
        XCTAssertTrue(callback.cancelled)
        XCTAssertNil(callback.result)
    }

    func testMicrophoneExplicitMissingSelectionDoesNotFallback() async throws {
        let f = try microphoneFixture()
        let callback = AsrCapture()
        _ = f.bridge.recognize(request: f.request,
            selection: SpeechBridgeSelectionExplicit(backendId: "missing"), callback: callback)
        await fulfillment(of: [callback.done], timeout: 3)
        XCTAssertEqual(callback.failure?.code, .backendNotRegistered)
        let starts = await f.backend.startCount
        XCTAssertEqual(starts, 0)
    }

    private func microphoneFixture(holdStart: Bool = false, holdDrain: Bool = false) throws -> MicFixture {
        let fixture = try MicFixture(holdStart: holdStart, holdDrain: holdDrain)
        addTeardownBlock { await fixture.shutdown() }
        return fixture
    }

    func testMissingTtsClientDoesNotBlockAsr() async throws {
        let f = try fixture(includeTts: false)
        let bridge = f.bridge
        let tts = TtsCapture()
        _ = bridge.speak(request: f.ttsRequest, selection: f.defaultSelection, callback: tts)
        XCTAssertEqual(tts.failure?.code, .backendUnavailable)
        let token = try f.store.importAudio(from: f.source)
        let asr = AsrCapture()
        _ = bridge.recognize(request: f.request(token), selection: f.defaultSelection, callback: asr)
        await fulfillment(of: [asr.done], timeout: 3)
        XCTAssertEqual(asr.result?.backendId, "fixture.asr")
        let closed = CloseCapture()
        bridge.close(callback: closed)
        await fulfillment(of: [closed.done], timeout: 3)
    }

    func testCloseJoinsNativeDrainAndConcurrentWaiters() async throws {
        let f = try fixture(block: true)
        let token = try f.store.importAudio(from: f.source)
        let asr = AsrCapture()
        _ = f.bridge.recognize(request: f.request(token), selection: f.defaultSelection, callback: asr)
        await fulfillment(of: [f.asr.started], timeout: 3)
        let one = CloseCapture(), two = CloseCapture()
        f.bridge.close(callback: one); f.bridge.close(callback: two)
        await fulfillment(of: [f.asr.draining], timeout: 3)
        XCTAssertEqual(one.count + two.count + asr.terminals, 0)
        let refused = BackendsCapture()
        _ = f.bridge.queryBackends(capability: .asr, callback: refused)
        XCTAssertEqual(refused.failure?.code, .closed)
        let refusedTts = TtsCapture(), refusedAsr = AsrCapture()
        _ = f.bridge.speak(request: f.ttsRequest, selection: f.defaultSelection, callback: refusedTts)
        _ = f.bridge.recognize(request: f.request(token), selection: f.defaultSelection, callback: refusedAsr)
        XCTAssertEqual(refusedTts.failure?.code, .closed)
        XCTAssertEqual(refusedAsr.failure?.code, .closed)
        await f.asr.drainGate.open()
        await fulfillment(of: [one.done, two.done, asr.done], timeout: 3)
        XCTAssertEqual(one.count, 1); XCTAssertEqual(two.count, 1)
        let closes = await f.asr.closeCount
        XCTAssertEqual(closes, 1)
        let again = CloseCapture()
        f.bridge.close(callback: again)
        await fulfillment(of: [again.done], timeout: 3)
        XCTAssertEqual(again.count, 1)
    }

    func testUnknownSwiftSelectionDoesNotBecomeDefault() throws {
        let f = try fixture()
        let callback = TtsCapture()
        _ = f.bridge.speak(request: f.ttsRequest, selection: UnknownSelection(), callback: callback)
        XCTAssertEqual(callback.failure?.code, .invalidInput)
    }

    func testSynchronousFailureReentryHasNoOccupiedSlot() async throws {
        let f = try fixture()
        let first = TtsCapture(), next = TtsCapture()
        first.ended = {
            _ = f.bridge.speak(request: f.ttsRequest, selection: f.defaultSelection, callback: next)
        }
        _ = f.bridge.speak(request: .init(text: " ", locale: "en-US", voiceId: nil),
                           selection: f.defaultSelection, callback: first)
        XCTAssertEqual(first.failure?.code, .invalidInput)
        await fulfillment(of: [next.done], timeout: 3)
        XCTAssertNotNil(next.result)
    }

    func testCancelledQueryDoesNotCloseClients() async throws {
        let gate = Signal()
        let f = try fixture(queryGate: gate)
        let callback = BackendsCapture()
        let task = f.bridge.queryBackends(capability: .tts, callback: callback)
        await fulfillment(of: [f.tts.queryStarted], timeout: 3)
        task.cancel()
        XCTAssertEqual(callback.terminals, 0)
        await gate.open()
        await fulfillment(of: [callback.done], timeout: 3)
        XCTAssertTrue(callback.cancelled)
        let next = TtsCapture()
        _ = f.bridge.speak(request: f.ttsRequest, selection: f.defaultSelection, callback: next)
        await fulfillment(of: [next.done], timeout: 3)
        XCTAssertNotNil(next.result)
        let closes = await f.tts.closeCount
        XCTAssertEqual(closes, 0)
    }

    func testMetadataAndPlatformFailureMapWithoutTranscript() async throws {
        let f = try fixture()
        let query = BackendsCapture()
        _ = f.bridge.queryBackends(capability: .asr, callback: query)
        await fulfillment(of: [query.done], timeout: 3)
        let descriptor = try XCTUnwrap(query.backends?.first)
        XCTAssertEqual(descriptor.backendId, "fixture.asr")
        XCTAssertEqual(descriptor.capability, .asr)
        XCTAssertEqual(descriptor.supportedInputs, [.audioFile])
        XCTAssertEqual(descriptor.availability.minimumOS, "15.0")
        XCTAssertTrue(descriptor.offline)
        await f.asr.setError(SpeechRecognitionError.platform(domain: "fixture.platform", code: 47))
        let token = try f.store.importAudio(from: f.source)
        let result = AsrCapture()
        _ = f.bridge.recognize(request: f.request(token), selection: f.defaultSelection, callback: result)
        await fulfillment(of: [result.done], timeout: 3)
        XCTAssertEqual(result.failure?.code, .platform)
        XCTAssertEqual(result.failure?.domain, "fixture.platform")
        XCTAssertEqual(result.failure?.platformCode?.intValue, 47)
        XCTAssertNil(result.failure?.reason)
    }

    func testExplicitUnavailableNeverFallsBackAndPreservesReason() async throws {
        let f = try fixture()
        await f.tts.setUnavailable()
        let query = BackendsCapture()
        _ = f.bridge.queryBackends(capability: .tts, callback: query)
        await fulfillment(of: [query.done], timeout: 3)
        XCTAssertEqual(query.backends?.first?.availability.reason, "missingAssets")
        let unavailable = TtsCapture()
        _ = f.bridge.speak(request: f.ttsRequest,
                           selection: SpeechBridgeSelectionExplicit(backendId: "fixture.tts"), callback: unavailable)
        await fulfillment(of: [unavailable.done], timeout: 3)
        XCTAssertEqual(unavailable.failure?.code, .backendUnavailable)
        XCTAssertEqual(unavailable.failure?.reason, "missingAssets")
        let absent = TtsCapture()
        _ = f.bridge.speak(request: f.ttsRequest,
                           selection: SpeechBridgeSelectionExplicit(backendId: "absent"), callback: absent)
        await fulfillment(of: [absent.done], timeout: 3)
        XCTAssertEqual(absent.failure?.code, .backendNotRegistered)
        let calls = await f.tts.speakCount
        XCTAssertEqual(calls, 0)
    }

    func testStoreRejectsExistingDirectoryWithoutTouchingItsFiles() throws {
        let f = try fixture()
        let directory = f.root.appendingPathComponent("already-owned")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
        let sentinel = directory.appendingPathComponent("keep")
        try Data([5]).write(to: sentinel)
        XCTAssertThrowsError(try SpeechInputStore(directory: directory))
        XCTAssertEqual(try Data(contentsOf: sentinel), Data([5]))
    }

    func testCancelledBeforeWorkerStartsReleasesLeaseAndDoesNotRecognize() async throws {
        let f = try fixture()
        let token = try f.store.importAudio(from: f.source)
        let lease = try f.store.acquire(reference: token)
        let copy = lease.url
        lease.release()
        let callback = AsrCapture()
        let task = f.bridge.recognize(request: f.request(token), selection: f.defaultSelection, callback: callback)
        task.cancel()
        f.store.close()
        await fulfillment(of: [callback.done], timeout: 3)
        XCTAssertTrue(callback.cancelled)
        XCTAssertFalse(FileManager.default.fileExists(atPath: copy.path))
        let calls = await f.asr.recognizeCount
        XCTAssertEqual(calls, 0)
    }

    func testCloseWaitsForSuspendedQueryBeforeClosingClients() async throws {
        let gate = Signal()
        let f = try fixture(queryGate: gate)
        let query = BackendsCapture(), close = CloseCapture()
        _ = f.bridge.queryBackends(capability: .tts, callback: query)
        await fulfillment(of: [f.tts.queryStarted], timeout: 3)
        f.bridge.close(callback: close)
        XCTAssertEqual(close.count, 0)
        let before = await f.tts.closeCount
        XCTAssertEqual(before, 0)
        await gate.open()
        await fulfillment(of: [query.done, close.done], timeout: 3)
        XCTAssertTrue(query.cancelled)
        let after = await f.tts.closeCount
        XCTAssertEqual(after, 1)
    }

    func testUnknownErrorDoesNotLeakMessageOrPath() async throws {
        let f = try fixture()
        await f.asr.setError(NSError(domain: "private transcript", code: 99,
                                    userInfo: [NSLocalizedDescriptionKey: f.source.path]))
        let token = try f.store.importAudio(from: f.source)
        let result = AsrCapture()
        _ = f.bridge.recognize(request: f.request(token), selection: f.defaultSelection, callback: result)
        await fulfillment(of: [result.done], timeout: 3)
        XCTAssertEqual(result.failure?.code, .platform)
        XCTAssertEqual(result.failure?.domain, "ios.speech")
        XCTAssertEqual(result.failure?.platformCode?.intValue, 1)
        XCTAssertNil(result.failure?.reason)
    }
}

private actor Signal {
    private var opened = false
    private var waiters: [CheckedContinuation<Void, Never>] = []
    func wait() async {
        if opened { return }
        await withCheckedContinuation { waiters.append($0) }
    }
    func open() {
        opened = true
        let pending = waiters; waiters.removeAll()
        pending.forEach { $0.resume() }
    }
}

private actor MicNativeTask: SpeechMicrophoneRecognitionTask {
    nonisolated let finished = XCTestExpectation(description: "finish reached native")
    nonisolated let cancelled = XCTestExpectation(description: "cancel reached native")
    private let resultGate = Signal()
    private(set) var finishCount = 0
    private(set) var cancelCount = 0
    func finishInput() { finishCount += 1; if finishCount == 1 { finished.fulfill() } }
    func cancel() { cancelCount += 1; if cancelCount == 1 { cancelled.fulfill() } }
    func result() async -> SpeechRecognitionResult {
        await resultGate.wait()
        return .init(backendId: "fixture.mic", transcript: "synthetic", confidence: nil)
    }
    func complete() async { await resultGate.open() }
}

private actor MicBackend: SpeechMicrophoneRecognitionBackend {
    nonisolated let backendId = "fixture.mic"
    nonisolated let capability = SpeechCapability.asr
    nonisolated let supportedInputs: [SpeechRecognitionInputKind] = [.audioFile, .microphone]
    nonisolated let native = MicNativeTask()
    nonisolated let started = XCTestExpectation(description: "microphone start entered")
    nonisolated let draining = XCTestExpectation(description: "microphone drain entered")
    nonisolated let startGate = Signal(), drainGate = Signal()
    private let holdStart: Bool, holdDrain: Bool
    private var replacement: MicNativeTask?
    private(set) var startCount = 0
    init(holdStart: Bool, holdDrain: Bool) { self.holdStart = holdStart; self.holdDrain = holdDrain }
    func availability() -> CapabilityAvailability { .init(status: .available, backendId: backendId) }
    func recognize(_ request: SpeechRecognitionRequest) throws -> SpeechRecognitionResult { throw SpeechRecognitionError.noResult }
    func startMicrophone(_ request: SpeechMicrophoneRecognitionRequest) async -> any SpeechMicrophoneRecognitionTask {
        startCount += 1
        if startCount == 1 { started.fulfill() }
        if holdStart { await startGate.wait() }
        return replacement ?? native
    }
    func replace(_ task: MicNativeTask) { replacement = task }
    func drain() async { if holdDrain { draining.fulfill(); await drainGate.wait() } }
    func close() {}
    func unblock() async {
        await startGate.open(); await drainGate.open(); await native.complete(); await replacement?.complete()
    }
}

@MainActor
private final class RejectingMicInputs: IOSSpeechAudioInputProvider {
    var acquires = 0
    func acquire(reference: String) throws -> any IOSSpeechAudioInputLease {
        acquires += 1
        throw KitError.invalidInput("no file input")
    }
}

@MainActor
private final class MicFixture {
    let backend: MicBackend
    let inputs = RejectingMicInputs()
    let bridge: IOSSpeechBridge
    let selection = SpeechBridgeSelectionDefault.shared
    let request = SpeechBridgeAsrRequest(locale: "en-US", input: SpeechBridgeInputMicrophone.shared)
    init(holdStart: Bool, holdDrain: Bool) throws {
        backend = MicBackend(holdStart: holdStart, holdDrain: holdDrain)
        bridge = IOSSpeechBridge(tts: nil,
            asr: try SpeechRecognitionClient(defaultBackendId: backend.backendId, backends: [backend]), inputs: inputs)
    }
    func shutdown() async {
        await backend.unblock()
        let callback = CloseCapture()
        bridge.close(callback: callback)
        await withCheckedContinuation { continuation in
            if callback.count > 0 { continuation.resume() }
            else { callback.ended = { continuation.resume() } }
        }
    }
}

private actor AsrBackend: SpeechRecognitionBackend {
    nonisolated let backendId = "fixture.asr"
    nonisolated let capability = SpeechCapability.asr
    nonisolated let started = XCTestExpectation(description: "native ASR entered")
    nonisolated let draining = XCTestExpectation(description: "native drain entered")
    nonisolated let operationGate = Signal(), drainGate = Signal()
    let block: Bool
    var error: Error?
    private(set) var closeCount = 0
    private(set) var recognizeCount = 0
    init(block: Bool) { self.block = block }
    func availability() -> CapabilityAvailability {
        .init(status: .available, backendId: backendId, minOS: "15.0")
    }
    func recognize(_ request: SpeechRecognitionRequest) async throws -> SpeechRecognitionResult {
        recognizeCount += 1
        started.fulfill()
        if block {
            await withTaskCancellationHandler(operation: { await operationGate.wait() }, onCancel: {
                Task { await self.operationGate.open() }
            })
        }
        if let error { throw error }
        return .init(backendId: backendId, transcript: "fixture", confidence: 0.75)
    }
    func setError(_ error: Error) { self.error = error }
    func drain() async { if block { draining.fulfill(); await drainGate.wait() } }
    func close() { closeCount += 1 }
}

private actor TtsBackend: SpeechSynthesisBackend {
    nonisolated let backendId = "fixture.tts"
    nonisolated let capability = SpeechCapability.tts
    nonisolated let queryStarted = XCTestExpectation(description: "query entered")
    let queryGate: Signal?
    var unavailable = false
    private(set) var closeCount = 0
    private(set) var speakCount = 0
    init(queryGate: Signal?) { self.queryGate = queryGate }
    func availability() async -> CapabilityAvailability {
        if let queryGate { queryStarted.fulfill(); await queryGate.wait() }
        return .init(status: unavailable ? .requiresAssets : .available,
                     reason: unavailable ? .missingAssets : nil, backendId: backendId, minOS: "15.0")
    }
    func speak(_ request: SpeechSynthesisRequest) -> SpeechSynthesisResult {
        speakCount += 1
        return .init(backendId: backendId, voiceId: request.voiceId)
    }
    func setUnavailable() { unavailable = true }
    func close() { closeCount += 1 }
}

@MainActor
private final class Fixture {
    let store: SpeechInputStore
    let root: URL, source: URL
    let asr: AsrBackend, tts: TtsBackend
    let asrClient: SpeechRecognitionClient
    let bridge: IOSSpeechBridge
    let defaultSelection = SpeechBridgeSelectionDefault.shared
    let ttsRequest = SpeechBridgeTtsRequest(text: "fixture", locale: "en-US", voiceId: nil)
    init(block: Bool, queryGate: Signal?, includeTts: Bool) throws {
        root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        source = root.appendingPathComponent("source.wav")
        try Data([0, 1, 2]).write(to: source)
        store = try SpeechInputStore(directory: root.appendingPathComponent("inputs"))
        asr = AsrBackend(block: block); tts = TtsBackend(queryGate: queryGate)
        asrClient = try SpeechRecognitionClient(defaultBackendId: asr.backendId, backends: [asr])
        bridge = IOSSpeechBridge(tts: includeTts ? try SpeechSynthesisClient(defaultBackendId: tts.backendId, backends: [tts]) : nil,
                                 asr: asrClient, inputs: store)
    }
    func request(_ token: String) -> SpeechBridgeAsrRequest {
        .init(locale: "en-US", input: SpeechBridgeInputAudioFile(reference: token))
    }
    func shutdown() async {
        await asr.operationGate.open(); await asr.drainGate.open(); await tts.queryGate?.open()
        let callback = CloseCapture()
        bridge.close(callback: callback)
        await withCheckedContinuation { continuation in
            if callback.count > 0 { continuation.resume() }
            else { callback.ended = { continuation.resume() } }
        }
        store.close()
        try? FileManager.default.removeItem(at: root)
    }
}

private class Capture: NSObject {
    let done = XCTestExpectation(description: "terminal callback")
    var failure: SpeechBridgeFailure?
    var cancelled = false
    var terminals = 0
    var ended: (() -> Void)?
    func finish() {
        XCTAssertTrue(Thread.isMainThread)
        terminals += 1; ended?(); done.fulfill()
    }
    @objc(onFailureFailure:)
    func onFailure(failure: SpeechBridgeFailure) { self.failure = failure; finish() }
    @objc func onCancelled() { cancelled = true; finish() }
}
private final class TtsCapture: Capture, SpeechBridgeTtsCallback {
    var result: SpeechBridgeTtsResult?
    func onResult(synthesis: SpeechBridgeTtsResult) { result = synthesis; finish() }
}
private final class AsrCapture: Capture, SpeechBridgeAsrCallback {
    var result: SpeechBridgeAsrResult?
    func onResult(output: SpeechBridgeAsrResult) { result = output; finish() }
}
private final class BackendsCapture: Capture, SpeechBridgeBackendsCallback {
    var backends: [SpeechBridgeBackend]?
    func onResult(backends: [SpeechBridgeBackend]) { self.backends = backends; finish() }
}
private final class CloseCapture: NSObject, SpeechBridgeCloseCallback {
    let done = XCTestExpectation(description: "closed")
    var count = 0
    var ended: (() -> Void)?
    func onClosed() { XCTAssertTrue(Thread.isMainThread); count += 1; ended?(); done.fulfill() }
    func onFailure(failure: SpeechBridgeFailure) { XCTFail("unexpected close failure"); onClosed() }
}
private final class UnknownSelection: NSObject, SpeechBridgeSelection {}
