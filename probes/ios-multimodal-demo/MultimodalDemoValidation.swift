import CryptoKit
import Foundation
import ODAIKitAppleMLXVLM
import ODAIKitDemo
import UIKit

/// Explicit validation-build hook. App-local AX actions do not prove touch or real file selection.
@MainActor enum MultimodalDemoValidation {
  private static var started = false

  static func start(preparing: @escaping () -> Bool, host: @escaping () -> MultimodalHost?) {
    let arguments = ProcessInfo.processInfo.arguments
    guard !started, arguments.contains("--multimodal-demo-validation"),
      arguments.contains("--multimodal-mlx-local-model")
    else { return }
    started = true
    Task {
      await run(
        preparing: preparing, host: host,
        active: arguments.contains("--multimodal-demo-active-validation"),
        background: arguments.contains("--multimodal-demo-os-background"))
    }
  }

  private static func run(
    preparing: () -> Bool, host: () -> MultimodalHost?, active: Bool, background: Bool
  ) async {
    var report: [String: Any] = [
      "schemaVersion": 1, "runId": UUID().uuidString,
      "pid": ProcessInfo.processInfo.processIdentifier, "os": UIDevice.current.systemVersion,
      "status": "unverified", "touchAcceptance": "unverified", "systemFileSelection": "unverified",
      "osBackground": "unverified",
    ]
    #if targetEnvironment(simulator)
      report["platform"] = "simulator"
    #else
      report["platform"] = "physical"
    #endif
    var gates = [String: Bool]()
    let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    var current: MultimodalHost?
    var stage = "metadata"
    do {
      guard
        let metadata = Bundle.main.url(forResource: "validation-metadata", withExtension: "json"),
        let values = try JSONSerialization.jsonObject(with: Data(contentsOf: metadata))
          as? [String: String],
        let commit = values["sourceCommit"],
        commit.range(of: "^[0-9a-f]{40}$", options: .regularExpression) != nil
      else { throw ValidationFailure() }
      report["sourceCommit"] = commit
      let fixture = documents.appendingPathComponent("fixture.png")
      let hash = SHA256.hash(data: try Data(contentsOf: fixture)).map { String(format: "%02x", $0) }
        .joined()
      report["fixtureHash"] = hash
      try require(hash == "60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606")
      stage = "bootstrap"
      try await wait(seconds: 120) { !preparing() }
      guard let ready = host() else { throw ValidationFailure() }
      current = ready
      gates["bootstrap"] = true
      ready.model.refreshBackends()
      try await wait(seconds: 20) { !ready.model.state.refreshing }
      stage = "availability"
      try require(
        ready.model.state.queryFailure == nil
          && ready.model.state.backends.contains {
            $0.backendId == SmolVLM2Model.backendId && $0.availability.available
          })
      gates["available"] = true
      stage = "page node"
      try await wait(seconds: 10) {
        try AppAccessibility.find(identifier: "multimodal-tab", roots: roots()) != nil
      }
      stage = "page activation"
      try require(try AppAccessibility.activate(identifier: "multimodal-tab", roots: roots()))
      try await wait(seconds: 10) {
        try AppAccessibility.find(identifier: "multimodal-select-input", roots: roots()) != nil
      }
      gates["pageOpened"] = true
      stage = "input activation"
      try require(
        try AppAccessibility.activate(identifier: "multimodal-select-input", roots: roots()))
      try await wait(seconds: 10) { picker() != nil }
      gates["inputAction"] = true
      stage = "injected picker reply"
      guard let filePicker = picker(), let delegate = filePicker.delegate as? MultimodalFilePicker
      else { throw ValidationFailure() }
      delegate.documentPicker(filePicker, didPickDocumentsAt: [fixture])
      filePicker.dismiss(animated: false)
      try require(
        ready.model.state.inputDisplayName == "fixture.png"
          && ready.model.state.inputReference != nil)
      gates["pickerReply"] = true
      if background {
        stage = "OS background lifecycle"
        let result = try await MultimodalDemoBackgroundValidation.run(
          host: ready, roots: roots,
          documents: documents, sourceCommit: commit, fixtureHash: hash,
          runId: report["runId"] as! String)
        if let extra = result["gates"] as? [String: Bool] {
          gates.merge(extra) { _, value in value }
        }
        for key in ["ready", "events", "cancel", "reentry"] { report[key] = result[key] }
        report["osBackground"] = "verified"
      } else if active {
        stage = "active shared controls"
        let result = try await MultimodalDemoActiveValidation.run(host: ready, roots: roots)
        if let extra = result["gates"] as? [String: Bool] {
          gates.merge(extra) { _, value in value }
        }
        report["scenarios"] = result["scenarios"]
      } else {
        stage = "generate activation"
        report["generateNodeInitiallyDisabled"] = try AppAccessibility.find(
          identifier: "multimodal-generate", roots: roots())?.accessibilityTraits.contains(
            .notEnabled)
        stage = "generate enabled node"
        try await wait(seconds: 10) {
          guard
            let node = try AppAccessibility.find(identifier: "multimodal-generate", roots: roots())
          else { return false }
          return !node.accessibilityTraits.contains(.notEnabled)
        }
        stage = "generate activation"
        report["canGenerateBeforeActivation"] = ready.model.state.canGenerate
        report["phaseBeforeActivation"] = ready.model.state.phase.name
        let accepted = try AppAccessibility.activate(
          identifier: "multimodal-generate", roots: roots())
        report["activationAccepted"] = accepted
        report["phaseAfterActivation"] = ready.model.state.phase.name
        try require(accepted)
        try await wait(seconds: 120) {
          ready.model.state.phase == .succeeded || ready.model.state.phase == .failed
            || ready.model.state.phase == .cancelled
        }
        report["phaseAfterTerminalWait"] = ready.model.state.phase.name
        report["generationFailureCode"] = ready.model.state.failure?.code.name ?? "none"
        try require(ready.model.state.phase == .succeeded)
        guard let output = ready.model.state.output else { throw ValidationFailure() }
        let identity = SmolVLM2Model.identity
        try require(
          !output.text.isEmpty && output.backendId == SmolVLM2Model.backendId
            && output.model.modelId == identity.modelId
            && output.model.modelRevision == identity.modelRevision
            && output.model.runtimeRevision == identity.runtimeRevision)
        report["output"] = [
          "text": output.text, "backendId": output.backendId, "modelId": output.model.modelId,
          "modelRevision": output.model.modelRevision,
          "runtimeRevision": output.model.runtimeRevision,
        ]
        gates["generated"] = true
        stage = "output AX reflection"
        try await wait(seconds: 10) {
          guard
            let text = try AppAccessibility.find(identifier: "multimodal-output", roots: roots()),
            let actualIdentity = try AppAccessibility.find(
              identifier: "multimodal-identity", roots: roots()),
            let phase = try AppAccessibility.find(identifier: "multimodal-phase", roots: roots())
          else { return false }
          return text.accessibilityLabel == output.text
            && actualIdentity.accessibilityLabel?.contains(identity.runtimeRevision) == true
            && phase.accessibilityLabel == "SUCCEEDED"
        }
        gates["outputRendered"] = true
        stage = "legacy idle navigation"
        for tab in ["speech-tab", "video-tab", "multimodal-tab"] {
          try require(try AppAccessibility.activate(identifier: tab, roots: roots()))
          try await Task.sleep(nanoseconds: 100_000_000)
        }
        gates["legacyTabs"] = true
      }
      report["status"] = "passed"
    } catch {
      report["failedGate"] =
        (error as? MultimodalDemoBackgroundValidation.Failure)?.label
        ?? (error as? MultimodalDemoActiveValidation.Failure)?.label ?? stage
    }
    if let current {
      current.close()
      do {
        try await wait(seconds: 30) { !current.model.state.closing }
        gates["closed"] = current.model.state.closed && current.model.state.closeFailure == nil
      } catch { gates["closed"] = false }
    }
    if gates["closed"] != true { report["status"] = "unverified" }
    report["gates"] = gates
    do {
      let data = try JSONSerialization.data(
        withJSONObject: report, options: [.prettyPrinted, .sortedKeys])
      try data.write(
        to: documents.appendingPathComponent(
          background
            ? "demo-background-integration.json"
            : (active ? "demo-active-integration.json" : "demo-integration.json")), options: .atomic
      )
    } catch {
      // No synthetic pass if the report cannot be persisted.
    }
  }

  private static func roots() -> [NSObject] {
    UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
  }
  private static func picker() -> UIDocumentPickerViewController? {
    for root in roots().compactMap({ ($0 as? UIWindow)?.rootViewController }) {
      var node: UIViewController? = root
      while let current = node {
        if let picker = current as? UIDocumentPickerViewController { return picker }
        node = current.presentedViewController
      }
    }
    return nil
  }
  private static func require(_ value: Bool) throws { if !value { throw ValidationFailure() } }
  private static func wait(seconds: Double, until ready: () throws -> Bool) async throws {
    let deadline = ProcessInfo.processInfo.systemUptime + seconds
    while try !ready() {
      try require(ProcessInfo.processInfo.systemUptime < deadline)
      try await Task.sleep(nanoseconds: 10_000_000)
    }
  }
  private struct ValidationFailure: Error {}
}
