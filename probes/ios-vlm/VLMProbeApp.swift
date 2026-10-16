import SwiftUI

@main
struct VLMProbeApp: App {
    @StateObject private var model = ProbeAppModel()

    var body: some Scene {
        WindowGroup {
            ProbeContentView(model: model)
        }
    }
}

@MainActor
final class ProbeAppModel: ObservableObject {
    @Published private(set) var status = "Ready"
    @Published private(set) var output = ""
    private var task: Task<Void, Never>?
    private var cancellationTimer: Task<Void, Never>?
    private let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]

    func start(arguments: [String] = ProcessInfo.processInfo.arguments) {
        guard task == nil else { return }
        let configuration = ProbeLaunchConfiguration.from(arguments: arguments, documents: documents)
        task = Task { [weak self] in
            await self?.run(configuration: configuration)
        }
        if let delay = configuration.cancelAfterMs {
            cancellationTimer = Task { [weak self] in
                try? await Task.sleep(for: .milliseconds(delay))
                guard !Task.isCancelled else { return }
                self?.cancel()
            }
        }
    }

    func cancel() {
        task?.cancel()
        status = "Cancelling…"
    }

    private func run(configuration: ProbeLaunchConfiguration) async {
        defer { cancellationTimer?.cancel(); cancellationTimer = nil; task = nil }
        do {
            let manifest = try ProbeAssetVerifier.verify(
                manifest: configuration.manifest,
                modelDirectory: configuration.modelDirectory)
            let result: VLMRunResult
#if canImport(MLXVLM) && canImport(MLXLMCommon) && canImport(MLXHuggingFace) && canImport(Tokenizers)
            result = try await VLMProbe(backend: MLXSmolVLMBackend(modelDirectory: configuration.modelDirectory))
                .run(input: configuration.input, manifest: manifest)
#else
            result = try await VLMProbe(backend: UnavailableBackend()).run(
                input: configuration.input, manifest: manifest)
#endif
            _ = try ProbeResultStore(directory: documents).write(result)
            status = result.status.rawValue
            output = result.output
        } catch is CancellationError {
            let result = VLMRunResult(
                status: .cancelled,
                actualBackend: "MLXVLM",
                modelRevision: VLMManifest.candidate.modelRevision,
                runtimeRevision: VLMManifest.candidate.runtimeRevision,
                fixtureHash: "",
                firstTokenMs: nil,
                totalMs: nil,
                peakMemory: nil,
                cancelled: true,
                output: "")
            _ = try? ProbeResultStore(directory: documents).write(result)
            status = "cancelled"
        } catch {
            let result = VLMRunResult(
                status: .unavailable,
                actualBackend: "MLXVLM",
                modelRevision: VLMManifest.candidate.modelRevision,
                runtimeRevision: VLMManifest.candidate.runtimeRevision,
                fixtureHash: "",
                firstTokenMs: nil,
                totalMs: nil,
                peakMemory: nil,
                cancelled: false,
                output: "",
                errorReason: String(describing: error))
            _ = try? ProbeResultStore(directory: documents).write(result)
            status = "unavailable"
            output = String(describing: error)
        }
    }
}

private struct ProbeContentView: View {
    @ObservedObject var model: ProbeAppModel

    var body: some View {
        VStack(spacing: 16) {
            Text("P9 VLM Probe").font(.title2)
            Text(model.status).accessibilityIdentifier("probe-status")
            ScrollView { Text(model.output).frame(maxWidth: .infinity, alignment: .leading) }
            HStack {
                Button("Run") { model.start() }
                Button("Cancel") { model.cancel() }
            }
        }
        .padding()
        .onAppear {
            if ProcessInfo.processInfo.arguments.contains("--p9-auto-run") { model.start() }
        }
    }
}
