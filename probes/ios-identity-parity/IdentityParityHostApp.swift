import SwiftUI
import Foundation

// Public upright astronaut fixture SHA — the ONLY permitted input. Never a real identity/image.
private let kPublicAstronautSHA = "88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5"

private enum HostState: String { case idle, running, completed, failed, invalidRequest }

private struct Parsed { let runId: String; let imageSHA: String; let fingerprint: String }

private func matches(_ s: String, _ pattern: String) -> Bool {
    s.range(of: "^\(pattern)$", options: .regularExpression) != nil
}

// nil => missing/duplicate/malformed args or a non-public imageSHA: static gate, no SDK call.
private func parseArguments(_ args: [String]) -> Parsed? {
    guard args.contains("--parity-run") else { return nil }
    var runId: String?, imageSHA: String?, fingerprint: String?
    var i = 0
    while i < args.count {
        let a = args[i]
        if a == "--runId" {
            guard runId == nil, i + 1 < args.count else { return nil }
            runId = args[i + 1]; i += 2
        } else if a == "--imageSHA" {
            guard imageSHA == nil, i + 1 < args.count else { return nil }
            imageSHA = args[i + 1]; i += 2
        } else if a == "--sourceFingerprint" {
            guard fingerprint == nil, i + 1 < args.count else { return nil }
            fingerprint = args[i + 1]; i += 2
        } else {
            i += 1
        }
    }
    guard let r = runId, let s = imageSHA, let f = fingerprint else { return nil }
    guard matches(r, "[A-Za-z0-9]{1,40}"),
          matches(s, "[0-9a-f]{64}"),
          matches(f, "[A-Za-z0-9._:-]{1,256}"),
          s == kPublicAstronautSHA else { return nil }
    return Parsed(runId: r, imageSHA: s, fingerprint: f)
}

@MainActor
final class ParityHostModel: ObservableObject {
    @Published fileprivate private(set) var state: HostState = .idle
    private var started = false

    init() {
        guard let p = parseArguments(CommandLine.arguments) else {
            // No --parity-run => idle. Present but malformed/duplicate/missing => static failed.
            state = CommandLine.arguments.contains("--parity-run") ? .failed : .idle
            return
        }
        state = .running
        start(p)
    }

    // Started gate: runs the already-delivered probe exactly once, even across view reentry.
    private func start(_ p: Parsed) {
        guard !started else { return }
        started = true
        Task {
            do {
                let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
                let root = (docs ?? FileManager.default.temporaryDirectory)
                    .appendingPathComponent("odai-parity")
                    .appendingPathComponent(p.runId, isDirectory: true)
                let request = try IdentityParityRequest(
                    taskRoot: root, runId: p.runId,
                    expectedImageSHA256: p.imageSHA,
                    expectedSourceFingerprint: p.fingerprint,
                    publicFixtureOnly: true)
                let status = await IdentityParityProbe.run(request)
                state = status == .completed ? .completed : .failed
            } catch {
                state = .invalidRequest
            }
        }
    }
}

@main
struct ParityHostApp: App {
    @StateObject private var model = ParityHostModel()
    var body: some Scene {
        WindowGroup {
            ContentView().environmentObject(model)
        }
    }
}

struct ContentView: View {
    @EnvironmentObject private var model: ParityHostModel
    var body: some View {
        VStack(spacing: 12) {
            Text("Identity Parity Host").font(.headline)
            Text(model.state.rawValue)
                .accessibilityIdentifier("parityStatus")
                .accessibilityLabel("Parity status")
        }
        .padding()
    }
}
