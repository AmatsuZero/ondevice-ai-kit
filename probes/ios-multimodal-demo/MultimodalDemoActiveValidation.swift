import Foundation
import ODAIKitAppleMLXVLM
import ODAIKitDemo
import UIKit

/// Real shared-control callbacks only. Never retries an accepted active action.
@MainActor enum MultimodalDemoActiveValidation {
  struct Failure: Error { let label: String }

  static func run(host: MultimodalHost, roots: @escaping () -> [NSObject]) async throws -> [String:
    Any]
  {
    let model = host.model
    var scenarios = [[String: Any]]()
    var gates = [String: Bool]()
    for name in ["cancel", "reentry", "leave", "returnReentry"] {
      let active = name == "cancel" || name == "leave"
      model.setTokenBudget(text: active ? "256" : "32")
      try require(model.state.canGenerate, name + " admission")
      let accepted = try await AppAccessibility.activateWhenEnabled(
        identifier: "multimodal-generate", roots: roots)
      try require(accepted, name + " generate activation")
      try await wait(name + " generation started", seconds: 20) {
        model.state.phase == .running
          || (model.state.phase == .succeeded && model.state.output != nil)
          || (model.state.phase == .failed && model.state.failure != nil)
      }
      var item: [String: Any] = ["name": name, "actionAccepted": accepted]
      if active {
        try await wait(name + " first delivered update", seconds: 120) {
          model.state.phase != .running || model.state.update != nil
        }
        try require(
          model.state.phase == .running && model.state.update != nil, name + " active update")
        let observed = model.state.update!.sequence
        let actionId = name == "cancel" ? "multimodal-cancel" : "speech-tab"
        try await wait(name + " enabled action", seconds: 10) {
          guard let node = try AppAccessibility.find(identifier: actionId, roots: roots()) else {
            return false
          }
          return !node.accessibilityTraits.contains(.notEnabled)
        }
        // Capture immediately before the synchronous AX action, with no intervening suspension.
        try require(
          model.state.phase == .running && model.state.update != nil, name + " action still active")
        item["observedSequence"] = Int(observed)
        item["actionSequence"] = Int(model.state.update!.sequence)
        item["actionPhase"] = model.state.phase.name
        let actionAccepted = try AppAccessibility.activate(identifier: actionId, roots: roots())
        item["actionAccepted"] = actionAccepted
        try require(actionAccepted, name + " cancel/leave activation")
        try await wait(name + " cancelled terminal", seconds: 120) { terminal(model.state.phase) }
        try require(
          model.state.phase == .cancelled && model.state.output == nil
            && model.state.failure == nil, name + " cancelled no output")
        item["terminal"] = model.state.phase.name
        gates[name == "cancel" ? "activeCancel" : "activeLeave"] = true
        if name == "leave" {
          let returned = try await AppAccessibility.activateWhenEnabled(
            identifier: "multimodal-tab", roots: roots)
          try require(returned, "return page activation")
          let sequence = model.state.update?.sequence
          let deadline = ProcessInfo.processInfo.systemUptime + 0.3
          while ProcessInfo.processInfo.systemUptime < deadline {
            try require(
              model.state.phase == .cancelled && model.state.output == nil
                && model.state.update?.sequence == sequence,
              "return no automatic generation")
            try await Task.sleep(nanoseconds: 1_000_000)
          }
          gates["noAutoRestart"] = true
        }
      } else {
        try await wait(name + " success terminal", seconds: 120) { terminal(model.state.phase) }
        guard model.state.phase == .succeeded, let output = model.state.output else {
          throw Failure(label: name + " output")
        }
        let identity = SmolVLM2Model.identity
        try require(
          !output.text.isEmpty && output.backendId == SmolVLM2Model.backendId
            && output.model.modelId == identity.modelId
            && output.model.modelRevision == identity.modelRevision
            && output.model.runtimeRevision == identity.runtimeRevision, name + " identity")
        item["terminal"] = model.state.phase.name
        item["output"] = [
          "text": output.text, "backendId": output.backendId, "modelId": output.model.modelId,
          "modelRevision": output.model.modelRevision,
          "runtimeRevision": output.model.runtimeRevision,
        ]
        gates[name] = true
      }
      scenarios.append(item)
    }
    return ["gates": gates, "scenarios": scenarios]
  }

  private static func terminal(_ phase: MultimodalPhase) -> Bool {
    phase == .succeeded || phase == .failed || phase == .cancelled
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
