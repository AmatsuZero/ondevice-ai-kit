import Foundation
import ODAIKitAppleMLXVLM
import ODAIKitDemo
import UIKit

@MainActor enum MultimodalDemoBackgroundValidation {
  struct Failure: Error { let label: String }
  static func run(
    host: MultimodalHost, roots: @escaping () -> [NSObject], documents: URL,
    sourceCommit: String, fixtureHash: String, runId: String
  ) async throws -> [String: Any] {
    let model = host.model
    model.setTokenBudget(text: "256")
    try require(model.state.canGenerate, "background admission")
    let accepted = try await AppAccessibility.activateWhenEnabled(
      identifier: "multimodal-generate", roots: roots)
    try require(accepted, "background generate activation")
    try await wait("background running", seconds: 20) {
      model.state.phase == .running && model.state.update != nil
    }
    let ready: [String: Any] = [
      "pid": ProcessInfo.processInfo.processIdentifier, "sourceCommit": sourceCommit,
      "runId": runId, "phase": model.state.phase.name,
      "sequence": Int(model.state.update!.sequence),
      "fixtureHash": fixtureHash,
    ]
    let observation = AppBackgroundObservation {
      [
        "state": UIApplication.shared.applicationState.rawValue, "phase": model.state.phase.name,
        "sequence": Int(model.state.update?.sequence ?? -1),
      ]
    }
    defer { observation.stop() }
    let readyData = try JSONSerialization.data(withJSONObject: ready, options: [.sortedKeys])
    try readyData.write(
      to: documents.appendingPathComponent("demo-background-ready.json"), options: .atomic)

    try await wait("OS lifecycle notifications", seconds: 180) { observation.events.count >= 4 }
    let events = observation.events
    try require(events[0]["phase"] as? String == "RUNNING", "running before background")
    try await wait("background cancellation", seconds: 120) { model.state.phase == .cancelled }
    try require(
      model.state.output == nil && model.state.failure == nil, "background cancelled output")
    let cancelled: [String: Any] = [
      "phase": model.state.phase.name, "output": NSNull(), "failure": NSNull(),
      "sequence": Int(model.state.update?.sequence ?? -1),
    ]
    let returnSequence = model.state.update?.sequence
    let quietDeadline = ProcessInfo.processInfo.systemUptime + 0.3
    while ProcessInfo.processInfo.systemUptime < quietDeadline {
      try require(
        model.state.phase == .cancelled && model.state.output == nil
          && model.state.update?.sequence == returnSequence, "foreground no restart")
      try await Task.sleep(nanoseconds: 1_000_000)
    }
    model.setTokenBudget(text: "32")
    try require(model.state.canGenerate, "reentry admission")
    let reentryAccepted = try await AppAccessibility.activateWhenEnabled(
      identifier: "multimodal-generate", roots: roots)
    try require(reentryAccepted, "reentry activation")
    try await wait("reentry terminal", seconds: 120) {
      model.state.phase == .succeeded || model.state.phase == .failed
    }
    guard model.state.phase == .succeeded, let output = model.state.output else {
      throw Failure(label: "reentry output")
    }
    let identity = SmolVLM2Model.identity
    try require(
      !output.text.isEmpty && output.backendId == SmolVLM2Model.backendId
        && output.model.modelId == identity.modelId
        && output.model.modelRevision == identity.modelRevision
        && output.model.runtimeRevision == identity.runtimeRevision, "reentry identity")
    return [
      "ready": ready, "events": events, "cancel": cancelled,
      "reentry": [
        "terminal": model.state.phase.name,
        "output": [
          "text": output.text, "backendId": output.backendId, "modelId": output.model.modelId,
          "modelRevision": output.model.modelRevision,
          "runtimeRevision": output.model.runtimeRevision,
        ],
      ],
      "gates": [
        "backgroundReady": true, "osWillResignActive": true, "osDidEnterBackground": true,
        "osWillEnterForeground": true, "osDidBecomeActive": true, "activeCancel": true,
        "noAutoRestart": true, "reentry": true,
      ],
    ]
  }
  private static func require(_ value: Bool, _ label: String) throws {
    if !value { throw Failure(label: label) }
  }
  private static func wait(_ label: String, seconds: Double, until ready: () throws -> Bool)
    async throws
  {
    let deadline = ProcessInfo.processInfo.systemUptime + seconds
    while try !ready() {
      try require(ProcessInfo.processInfo.systemUptime < deadline, label + " timeout")
      try await Task.sleep(nanoseconds: 1_000_000)
    }
  }
}
