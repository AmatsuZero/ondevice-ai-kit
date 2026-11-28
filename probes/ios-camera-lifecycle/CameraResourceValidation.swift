import Foundation

#if canImport(AVFoundation) && canImport(QuartzCore) && canImport(UIKit)
import AVFoundation
import Darwin
import QuartzCore
import UIKit
#endif

enum CameraResourceFailureReport {
    static func persist(
        runID: String,
        probeFailure: String,
        recordFailure: () throws -> Void,
        logger: (String) -> Void = { NSLog("%@", $0) }
    ) {
        do {
            try recordFailure()
        } catch {
            logger("ODAI_CAMERA_RESOURCE_REPORT_FAILURE run=\(runID.prefix(128)) "
                + "probe=\(probeFailure.prefix(512)) report=\(String(describing: error).prefix(512))")
        }
    }
}

/// Compiled only into the generated validation app, never the ordinary Demo.
#if canImport(AVFoundation) && canImport(QuartzCore) && canImport(UIKit)
@MainActor enum CameraResourceValidation {
    private static var task: Task<Void, Never>?

    static func start(controller: DemoController) {
        let arguments = ProcessInfo.processInfo.arguments
        guard task == nil, let index = arguments.firstIndex(of: "--camera-resource-run-id"),
              arguments.indices.contains(index + 1), let uuid = UUID(uuidString: arguments[index + 1]) else { return }
        let run = Recording(runID: uuid.uuidString, controller: controller)
        task = Task { await run.execute() }
    }

    @MainActor private final class Recording {
        let runID: String
        let controller: DemoController
        let started = ProcessInfo.processInfo.systemUptime
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        var samples: [[String: Any]] = []

        init(runID: String, controller: DemoController) { self.runID = runID; self.controller = controller }

        func execute() async {
            defer { controller.stopCamera() }
            do {
                try require(AVCaptureDevice.authorizationStatus(for: .video) == .authorized, "camera-not-authorized")
                // SwiftUI onAppear can precede didBecomeActive during a cold launch.
                // Wait only for startup readiness; later foreground loss still fails the run.
                for _ in 0..<100 {
                    if UIApplication.shared.applicationState == .active { break }
                    try await Task.sleep(nanoseconds: 100_000_000)
                }
                try record("waiting-for-trace")
                let trigger = documents.appendingPathComponent("camera-resource-start-\(runID).txt")
                var triggered = false
                for _ in 0..<1200 {
                    if (try? String(contentsOf: trigger, encoding: .utf8).trimmingCharacters(in: .whitespacesAndNewlines)) == runID {
                        triggered = true; break
                    }
                    try await Task.sleep(nanoseconds: 100_000_000)
                }
                try require(triggered, "trace-trigger-timeout")
                try record("trace-triggered")
                try await Task.sleep(nanoseconds: 10_000_000_000)
                try record("baseline-complete")
                try require(renderState().metalLayers > 0, "no-visible-metal-layer")
                controller.startCamera()
                for _ in 0..<150 {
                    if controller.cameraFrameCount >= 3 { break }
                    try await Task.sleep(nanoseconds: 100_000_000)
                }
                try require(controller.cameraFrameCount >= 3, "native-warmup-timeout")
                let activeStarted = ProcessInfo.processInfo.systemUptime
                var previous = controller.cameraFrameCount
                try record("warmup-complete")
                for index in 1...4 {
                    try await Task.sleep(nanoseconds: 30_000_000_000)
                    let current = controller.cameraFrameCount
                    try record("active-\(index)", extra: ["delta": current - previous,
                        "activeElapsedSeconds": ProcessInfo.processInfo.systemUptime - activeStarted])
                    try require(current - previous >= 10, "native-window-stalled")
                    previous = current
                }
                try require(ProcessInfo.processInfo.systemUptime - activeStarted >= 120, "short-active-window")
                controller.stopCamera()
                let stopped = controller.cameraFrameCount
                for job in Array(controller.bridge.jobs.values) { await job.value }
                try record("stopped", extra: ["framesAtStop": stopped])
                try await Task.sleep(nanoseconds: 30_000_000_000)
                try require(controller.cameraFrameCount == stopped, "results-after-stop")
                try require(controller.bridge.jobs.isEmpty && controller.bridge.inputs.isEmpty, "pending-after-stop")
                try record("stopped-30s", extra: ["framesAtStop": stopped])
                try record("complete", status: "passed")
            } catch {
                controller.stopCamera()
                let probeFailure = String(describing: error)
                CameraResourceFailureReport.persist(runID: runID, probeFailure: probeFailure) {
                    try record("failed", status: "failed", extra: ["failure": probeFailure], requireForeground: false)
                }
            }
        }

        private func require(_ condition: Bool, _ message: String) throws {
            if !condition { throw NSError(domain: "CameraResourceValidation", code: 1, userInfo: [NSLocalizedDescriptionKey: message]) }
        }

        private func record(_ phase: String, status: String = "running", extra: [String: Any] = [:], requireForeground: Bool = true) throws {
            if requireForeground { try require(UIApplication.shared.applicationState == .active, "host-not-foreground") }
            let render = renderState()
            var sample: [String: Any] = [
                "phase": phase, "elapsedSeconds": ProcessInfo.processInfo.systemUptime - started,
                "wallTime": ISO8601DateFormatter().string(from: Date()),
                "successfulNativeResults": controller.cameraFrameCount,
                "pendingInputs": controller.bridge.inputs.count, "pendingJobs": controller.bridge.jobs.count,
                "applicationState": UIApplication.shared.applicationState.rawValue,
                "thermalState": ProcessInfo.processInfo.thermalState.rawValue,
                "visibleMetalLayers": render.metalLayers, "viewControllerClasses": render.classes,
                "layerGeometry": render.geometry,
                "residentBytes": residentBytes() as Any? ?? NSNull()
            ]
            sample.merge(extra) { _, new in new }; samples.append(sample)
            let value: [String: Any] = ["runID": runID, "pid": ProcessInfo.processInfo.processIdentifier,
                "status": status, "samples": samples, "capturedImagesSaved": false,
                "scope": "production Demo UI rendered; controller-driven camera; no physical UI control assertion",
                "memoryMethod": "mach_task_basic_info.resident_size snapshot; not peak or footprint"]
            let data = try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys, .prettyPrinted])
            try data.write(to: documents.appendingPathComponent("camera-resource-\(runID).json"), options: .atomic)
            print("ODAI_CAMERA_RESOURCE \(runID) \(phase) \(status)")
        }

        private func renderState() -> (metalLayers: Int, classes: [String], geometry: [[String: Any]]) {
            var classes: [String] = []
            var geometry: [[String: Any]] = []
            func controllers(_ value: UIViewController) {
                classes.append(NSStringFromClass(type(of: value)))
                value.children.forEach(controllers)
            }
            func layers(_ layer: CALayer) -> Int {
                guard !layer.isHidden, layer.opacity > 0, layer.bounds.width > 0, layer.bounds.height > 0 else { return 0 }
                // Compose1.11.1 SurfaceMetalView uses its own CMPMetalLayer;
                // LegacyMetalView uses CAMetalLayer. Presence is not a GPU-work claim.
                let renderer = layer is CAMetalLayer || NSStringFromClass(type(of: layer)) == "CMPMetalLayer"
                return (renderer ? 1 : 0) + (layer.sublayers ?? []).reduce(0) { $0 + layers($1) }
            }
            let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
                .filter { $0.activationState == .foregroundActive }.flatMap(\.windows).filter { !$0.isHidden }
            func inspect(_ layer: CALayer, depth: Int) {
                geometry.append(["class": NSStringFromClass(type(of: layer)), "depth": depth,
                    "boundsWidth": layer.bounds.width, "boundsHeight": layer.bounds.height, "hidden": layer.isHidden,
                    "opacity": layer.opacity, "masksToBounds": layer.masksToBounds])
                (layer.sublayers ?? []).forEach { inspect($0, depth: depth + 1) }
            }
            windows.forEach { inspect($0.layer, depth: 0) }
            windows.compactMap(\.rootViewController).forEach(controllers)
            return (windows.reduce(0) { $0 + layers($1.layer) }, classes, geometry)
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
}
#endif
