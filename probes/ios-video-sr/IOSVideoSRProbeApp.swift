import Foundation
import SwiftUI
import UIKit

#if !targetEnvironment(simulator)
import VideoToolbox
#endif

@main
struct IOSVideoSRProbeApp: App {
    @StateObject private var model = VideoSRProbeModel()

    var body: some Scene {
        WindowGroup {
            VStack(spacing: 12) {
                Text("iOS Video SR Probe").font(.title2)
                Text(model.status).accessibilityIdentifier("video-sr-status")
                Button("查询系统模型") { model.inspect() }
                    .disabled(model.isInspecting)
                Button("下载系统模型") { model.download() }
                    .disabled(!model.canDownload)
                Text(model.reportPath ?? "")
                    .font(.caption)
                    .textSelection(.enabled)
            }
            .padding()
            .onAppear { model.inspect() }
        }
    }
}

@MainActor
final class VideoSRProbeModel: ObservableObject {
    @Published private(set) var status = "Ready"
    @Published private(set) var reportPath: String?
    @Published private(set) var isInspecting = false
    @Published private(set) var canDownload = false

    func inspect() {
        guard !isInspecting else { return }
        isInspecting = true
        status = "Inspecting"
        Task { await inspectAvailability() }
    }

    func download() {
#if !targetEnvironment(simulator)
        guard #available(iOS 26.0, *) else { return }
        guard let configuration = makeConfiguration() else { return }
        canDownload = false
        status = "Downloading"
        configuration.downloadConfigurationModel { [weak self] error in
            Task { @MainActor in
                self?.status = error.map { "Download failed: \($0.localizedDescription)" } ?? "Download complete"
                self?.inspect()
            }
        }
#endif
    }

    private func inspectAvailability() async {
        defer { isInspecting = false }
        var report: [String: Any] = [
            "schemaVersion": 1,
            "runId": UUID().uuidString,
            "device": UIDevice.current.model,
            "OS": UIDevice.current.systemVersion,
            "validationStatus": "device",
            "backendId": "apple.videotoolbox.sr",
            "input": ["width": 320, "height": 180, "frameCount": 48, "fps": 24],
        ]
#if targetEnvironment(simulator)
        report["state"] = "unavailable"
        report["reason"] = "VideoToolbox super-resolution API is excluded from simulator builds"
#else
        if #available(iOS 26.0, *) {
            let supported = VTSuperResolutionScalerConfiguration.isSupported
            let factors = VTSuperResolutionScalerConfiguration.supportedScaleFactors
            report["supported"] = supported
            report["defaultRevision"] = VTSuperResolutionScalerConfiguration.defaultRevision.rawValue
            report["supportedRevisions"] = VTSuperResolutionScalerConfiguration.supportedRevisions.map { $0 }
            report["supportedScaleFactors"] = factors
            if !supported {
                report["state"] = "unavailable"
                report["reason"] = "system reports processor unsupported"
                status = "Unsupported"
            } else if !factors.contains(4) {
                report["state"] = "unavailable"
                report["reason"] = "4x scale factor unsupported"
                status = "Unsupported scale"
            } else if let configuration = makeConfiguration() {
                let modelRawStatus = configuration.configurationModelStatus.rawValue
                report["state"] = "supported"
                report["modelStatus"] = modelStatus(modelRawStatus)
                report["modelPercentage"] = configuration.configurationModelPercentageAvailable
                report["configurationRevision"] = configuration.revision.rawValue
                report["configurationScaleFactor"] = configuration.scaleFactor
                report["pixelFormats"] = configuration.__frameSupportedPixelFormats.map { $0.intValue }
                canDownload = modelRawStatus == VTSuperResolutionScalerConfiguration.ModelStatus.downloadRequired.rawValue
                status = canDownload ? "Model download required" : "Ready to probe"
            } else {
                report["state"] = "unavailable"
                report["reason"] = "configuration rejected 320x180 video input with 4x revision 1"
                status = "Unavailable"
            }
        } else {
            report["state"] = "unavailable"
            report["reason"] = "requires iOS 26 or later"
            status = "Unavailable"
        }
#endif
        writeReport(report)
    }

#if !targetEnvironment(simulator)
    @available(iOS 26.0, *)
    private func makeConfiguration() -> VTSuperResolutionScalerConfiguration? {
        VTSuperResolutionScalerConfiguration(
            frameWidth: 320,
            frameHeight: 180,
            scaleFactor: 4,
            inputType: .video,
            usePrecomputedFlow: false,
            qualityPrioritization: .normal,
            revision: .revision1
        )
    }

    @available(iOS 26.0, *)
    private func modelStatus(_ rawValue: Int) -> String {
        switch rawValue {
        case VTSuperResolutionScalerConfiguration.ModelStatus.downloadRequired.rawValue: return "downloadRequired"
        case VTSuperResolutionScalerConfiguration.ModelStatus.downloading.rawValue: return "downloading"
        case VTSuperResolutionScalerConfiguration.ModelStatus.ready.rawValue: return "ready"
        default: return "unknown"
        }
    }
#endif

    private func writeReport(_ report: [String: Any]) {
        do {
            let data = try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys])
            let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let file = documents.appendingPathComponent("ios-video-sr-probe-\(UUID().uuidString).json")
            try data.write(to: file, options: .atomic)
            reportPath = file.lastPathComponent
        } catch {
            status = "Report failed: \(error.localizedDescription)"
        }
    }
}
