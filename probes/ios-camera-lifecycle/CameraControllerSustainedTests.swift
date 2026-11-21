import AVFoundation
import Darwin
import UIKit
import XCTest

#if !targetEnvironment(simulator)
@MainActor final class CameraControllerSustainedTests: XCTestCase {
    func testControllerDeliversNativeResultsForTwoMinutesThenStopsAndReleases() async throws {
        executionTimeAllowance = 180
        guard AVCaptureDevice.authorizationStatus(for: .video) == .authorized else {
            throw XCTSkip("Existing Demo camera authorization is required; this test does not request new access")
        }
        guard UIApplication.shared.applicationState == .active else {
            throw XCTSkip("The native validation host must be foreground")
        }
        let runID = UUID().uuidString
        let started = ProcessInfo.processInfo.systemUptime
        var owner: DemoController? = DemoController()
        weak var releasedOwner = owner
        let bridge = try XCTUnwrap(owner?.bridge)
        defer { owner?.stopCamera(); owner = nil; bridge.close() }
        try record(phase: "before-start", runID: runID, started: started, frames: 0)
        owner?.startCamera()
        for _ in 0..<150 {
            if (owner?.cameraFrameCount ?? 0) >= 3 { break }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        guard let initial = owner?.cameraFrameCount, initial >= 3 else {
            XCTFail("The real controller/bridge/Vision pipeline did not produce three successful results")
            return
        }
        let activeStarted = ProcessInfo.processInfo.systemUptime
        var previous = initial
        try record(phase: "warmup-complete", runID: runID, started: started, frames: initial)
        for sample in 1...4 {
            try await Task.sleep(nanoseconds: 30_000_000_000)
            let current = try XCTUnwrap(owner?.cameraFrameCount)
            try record(phase: "active-\(sample)", runID: runID, started: started, frames: current,
                       extra: ["delta": current - previous,
                               "activeElapsedSeconds": ProcessInfo.processInfo.systemUptime - activeStarted])
            XCTAssertEqual(UIApplication.shared.applicationState, .active, "Foreground validation was interrupted")
            XCTAssertGreaterThanOrEqual(current - previous, 10, "Each30-second window needs at least10 successful native results")
            previous = current
        }
        XCTAssertGreaterThanOrEqual(ProcessInfo.processInfo.systemUptime - activeStarted, 120)

        owner?.stopCamera()
        let stopped = try XCTUnwrap(owner?.cameraFrameCount)
        let pending = Array(bridge.jobs.values)
        for job in pending { await job.value }
        try await Task.sleep(nanoseconds: 5_000_000_000)
        XCTAssertEqual(owner?.cameraFrameCount, stopped, "Stop must prevent subsequent successful results")
        XCTAssertTrue(bridge.jobs.isEmpty)
        XCTAssertTrue(bridge.inputs.isEmpty)
        try record(phase: "stopped-5s", runID: runID, started: started, frames: owner?.cameraFrameCount ?? -1,
                   extra: ["framesAtStop": stopped, "pendingJobs": bridge.jobs.count,
                           "pendingInputs": bridge.inputs.count])

        owner = nil
        XCTAssertNil(releasedOwner)
        for _ in 0..<200 {
            if !bridge.availability().available, await bridge.detector.availability().reason == .closed { break }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        let detector = await bridge.detector.availability()
        XCTAssertFalse(bridge.availability().available)
        XCTAssertEqual(detector.reason, .closed)
        XCTAssertTrue(bridge.inputs.isEmpty)
        XCTAssertTrue(bridge.jobs.isEmpty)
        try record(phase: "owner-released", runID: runID, started: started, frames: stopped,
                   extra: ["ownerReleased": releasedOwner == nil,
                           "bridgeClosed": !bridge.availability().available,
                           "detectorClosed": detector.reason == .closed,
                           "pendingJobs": bridge.jobs.count, "pendingInputs": bridge.inputs.count])
    }

    private func record(phase: String, runID: String, started: TimeInterval, frames: Int,
                        extra: [String: Any] = [:]) throws {
        let resident: Any = residentBytes() as Any? ?? NSNull()
        var value: [String: Any] = [
            "runID": runID, "pid": ProcessInfo.processInfo.processIdentifier,
            "phase": phase, "elapsedSeconds": ProcessInfo.processInfo.systemUptime - started,
            "successfulNativeResults": frames, "residentBytes": resident,
            "memoryMethod": "mach_task_basic_info.resident_size snapshot; not peak or footprint",
            "thermalState": ProcessInfo.processInfo.thermalState.rawValue,
            "applicationState": UIApplication.shared.applicationState.rawValue,
            "capturedImagesSaved": false, "scope": "native-controller-bridge-Vision; no Compose UI"
        ]
        value.merge(extra) { _, new in new }
        let data = try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys])
        let text = String(decoding: data, as: UTF8.self)
        print("ODAI_SUSTAINED_CAMERA \(text)")
        let attachment = XCTAttachment(string: text)
        attachment.name = "sustained-camera-\(phase)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func residentBytes() -> UInt64? {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size / MemoryLayout<natural_t>.size)
        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), $0, &count)
            }
        }
        return result == KERN_SUCCESS ? UInt64(info.resident_size) : nil
    }
}
#endif
