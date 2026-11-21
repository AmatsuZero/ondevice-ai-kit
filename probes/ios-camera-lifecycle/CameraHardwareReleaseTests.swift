import AVFoundation
import XCTest

#if !targetEnvironment(simulator)
final class CameraHardwareReleaseTests: XCTestCase {
    func testPhysicalPauseResumePreservesSessionAndStopStartOrder() async throws {
        executionTimeAllowance = 45
        guard AVCaptureDevice.authorizationStatus(for: .video) == .authorized else {
            throw XCTSkip("The existing Demo camera authorization is required for physical capture validation")
        }
        let session = AVCaptureSession()
        let frameQueue = DispatchQueue(label: "camera.pause.test.frames")
        let camera = CameraSource(session: session, frameQueue: frameQueue)
        let counter = HardwareFrameCounter()
        let starts = HardwareFrameCounter()
        let stops = HardwareFrameCounter()
        let startObserver = NotificationCenter.default.addObserver(
            forName: AVCaptureSession.didStartRunningNotification, object: session, queue: nil
        ) { _ in _ = starts.increment() }
        let stopObserver = NotificationCenter.default.addObserver(
            forName: AVCaptureSession.didStopRunningNotification, object: session, queue: nil
        ) { _ in _ = stops.increment() }
        defer {
            NotificationCenter.default.removeObserver(startObserver)
            NotificationCenter.default.removeObserver(stopObserver)
        }
        camera.onFrame = { _ in _ = counter.increment() }
        do {
            camera.start()
            try await waitFor("initial capture callbacks") { counter.value >= 3 && starts.value == 1 }
            let inputs = session.inputs
            let outputs = session.outputs
            XCTAssertEqual(inputs.count, 1)
            XCTAssertEqual(outputs.count, 1)

            camera.stop()
            try await waitFor("paused session") { !session.isRunning && stops.value == 1 }
            // stop is a pause, not terminal close. Drain work already delivered to
            // the delegate before measuring the quiet interval.
            frameQueue.sync {}
            let pausedCount = counter.value
            try await Task.sleep(nanoseconds: 250_000_000)
            XCTAssertEqual(counter.value, pausedCount, "Paused capture must remain quiet")
            XCTAssertEqual(session.inputs, inputs, "Pause must preserve the configured input")
            XCTAssertEqual(session.outputs, outputs, "Pause must preserve the configured output")
            XCTAssertNotNil(camera.onFrame, "Pause must retain the consumer for resume")

            camera.start()
            try await waitFor("resumed capture callbacks") { counter.value >= pausedCount + 3 && starts.value == 2 }
            let resumedCount = counter.value
            XCTAssertTrue(session.isRunning)
            XCTAssertEqual(session.inputs, inputs)
            XCTAssertEqual(session.outputs, outputs)

            // Calling both without a wait catches an executor migration that
            // reorders start ahead of stop and leaves the camera paused.
            camera.stop()
            camera.start()
            try await waitFor("ordered stop/start notifications") { stops.value == 2 && starts.value == 3 }
            let orderedCount = counter.value
            try await waitFor("capture after ordered stop/start") { counter.value >= orderedCount + 3 }
            XCTAssertTrue(session.isRunning)
            XCTAssertEqual(session.inputs, inputs)
            XCTAssertEqual(session.outputs, outputs)

            await camera.close()
            let closedCount = counter.value
            try await Task.sleep(nanoseconds: 250_000_000)
            XCTAssertEqual(counter.value, closedCount)
            XCTAssertFalse(session.isRunning)
            XCTAssertTrue(session.inputs.isEmpty)
            XCTAssertTrue(session.outputs.isEmpty)
            XCTAssertNil(camera.onFrame)
            let record: [String: Any] = [
                "runID": UUID().uuidString, "pid": ProcessInfo.processInfo.processIdentifier,
                "validationStatus": "physical-device", "capturedImagesSaved": false,
                "callbacksAtPause": pausedCount, "callbacksAfterResume": resumedCount,
                "callbacksAfterOrderedPair": orderedCount, "callbacksAtClose": closedCount,
                "callbacksAfterClose": counter.value,
                "startNotifications": starts.value, "stopNotifications": stops.value,
                "configuredInputs": inputs.count, "configuredOutputs": outputs.count,
                "closedInputs": session.inputs.count, "closedOutputs": session.outputs.count
            ]
            let data = try JSONSerialization.data(withJSONObject: record, options: [.sortedKeys])
            let attachment = XCTAttachment(string: String(decoding: data, as: UTF8.self))
            attachment.name = "physical-camera-pause-resume-state"
            attachment.lifetime = .keepAlways
            add(attachment)
        } catch {
            await camera.close()
            throw error
        }
    }

    private func waitFor(_ operation: String, until predicate: () -> Bool) async throws {
        let deadline = DispatchTime.now().uptimeNanoseconds + 10_000_000_000
        while !predicate() {
            guard DispatchTime.now().uptimeNanoseconds < deadline else {
                throw NSError(domain: "CameraHardwareReleaseTests", code: 1,
                              userInfo: [NSLocalizedDescriptionKey: "Timed out waiting for \(operation)"])
            }
            try await Task.sleep(nanoseconds: 25_000_000)
        }
    }

    func testPhysicalCaptureCloseStopsFramesDetachesInputsAndRejectsRestart() async throws {
        executionTimeAllowance = 30
        guard AVCaptureDevice.authorizationStatus(for: .video) == .authorized else {
            throw XCTSkip("The existing Demo camera authorization is required for physical capture validation")
        }
        let session = AVCaptureSession()
        let camera = CameraSource(session: session)
        let counter = HardwareFrameCounter()
        let ready = expectation(description: "three real capture callbacks")
        camera.onFrame = { _ in if counter.increment() == 3 { ready.fulfill() } }
        camera.start()
        await fulfillment(of: [ready], timeout: 15)
        let startedInputs = session.inputs.count
        let startedOutputs = session.outputs.count
        XCTAssertGreaterThan(startedInputs, 0)
        XCTAssertGreaterThan(startedOutputs, 0)
        XCTAssertTrue(session.isRunning)

        await camera.close()
        let countAtClose = counter.value
        XCTAssertGreaterThanOrEqual(countAtClose, 3)
        XCTAssertFalse(session.isRunning)
        XCTAssertTrue(session.inputs.isEmpty)
        XCTAssertTrue(session.outputs.isEmpty)
        XCTAssertNil(camera.onFrame)
        try await Task.sleep(nanoseconds: 250_000_000)
        XCTAssertEqual(counter.value, countAtClose, "No capture callback may reach the consumer after close returns")

        // Both operations use the capture serial queue, so the second close settles
        // the attempted start before the terminal-state assertions below.
        camera.start()
        await camera.close()
        XCTAssertFalse(session.isRunning)
        XCTAssertTrue(session.inputs.isEmpty, "A terminal camera must reject restart")
        XCTAssertTrue(session.outputs.isEmpty)
        let record: [String: Any] = [
            "validationStatus": "physical-device", "capturedImagesSaved": false,
            "startedInputs": startedInputs, "startedOutputs": startedOutputs,
            "callbacksAtClose": countAtClose, "callbacksAfterClose": counter.value,
            "closedInputs": session.inputs.count, "closedOutputs": session.outputs.count,
            "runningAfterRestartAttempt": session.isRunning
        ]
        let data = try JSONSerialization.data(withJSONObject: record, options: [.sortedKeys])
        let attachment = XCTAttachment(string: String(decoding: data, as: UTF8.self))
        attachment.name = "physical-camera-close-state"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

private final class HardwareFrameCounter: @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0
    var value: Int { lock.lock(); defer { lock.unlock() }; return count }
    func increment() -> Int { lock.lock(); defer { lock.unlock() }; count += 1; return count }
}
#endif
