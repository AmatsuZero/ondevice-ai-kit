import CryptoKit
import Foundation
import ODAIKitAppleMLXVLM
@preconcurrency import ODAIKitDemo
import ODAIKitModelStore
import ODAIKitMultimodal
import UIKit

@MainActor enum MultimodalDeviceSuite {
  static func run(documents: URL) async -> [String: Any] {
    let identity = SmolVLM2Model.identity
    var report: [String: Any] = [
      "schemaVersion": 1, "runId": UUID().uuidString,
      "pid": ProcessInfo.processInfo.processIdentifier,
      "sourceCommit": Bundle.main.object(forInfoDictionaryKey: "ODAISourceCommit") as? String
        ?? "missing",
      "os": UIDevice.current.systemVersion, "status": "failed",
      "identity": [
        "backendId": SmolVLM2Model.backendId, "modelId": identity.modelId,
        "modelRevision": identity.modelRevision, "runtimeRevision": identity.runtimeRevision,
      ],
    ]
    #if targetEnvironment(simulator)
      report["platform"] = "simulator"
    #else
      report["platform"] = "physical"
    #endif
    let trace = DeviceTrace()
    var gates = [String: Bool]()
    var scenarios = [String: Any]()
    var host: MultimodalHost?
    do {
      let fixture = documents.appendingPathComponent("fixture.png")
      let hash = SHA256.hash(data: try Data(contentsOf: fixture)).map { String(format: "%02x", $0) }
        .joined()
      report["fixtureHash"] = hash
      try check(
        hash == "60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606", "fixture hash")
      let os = ProcessInfo.processInfo.operatingSystemVersion
      let modelStore = try ModelStore(
        root: documents.appendingPathComponent("lifecycle-model-store"),
        compatibility: .init(
          backendId: SmolVLM2Model.backendId, backendVersion: "1.0.0",
          osVersion: "\(os.majorVersion).\(os.minorVersion).\(os.patchVersion)",
          architecture: "arm64"))
      let manifest = try SmolVLM2Model.manifest()
      try await modelStore.install(
        manifest: manifest, source: documents.appendingPathComponent("model"))
      let modelLease = try await modelStore.acquire(manifest.ref)
      let backend: MLXVLMBackend
      do { backend = try MLXVLMBackend(lease: modelLease) } catch {
        await modelLease.close()
        throw error
      }
      let client: MultimodalGenerationClient
      do {
        client = try MultimodalGenerationClient(
          defaultBackendId: backend.backendId, backends: [backend])
      } catch {
        await backend.close()
        throw error
      }
      let directory = FileManager.default.temporaryDirectory.appendingPathComponent(
        "odai-device-" + UUID().uuidString)
      let inputs: MultimodalInputStore
      do { inputs = try MultimodalInputStore(directory: directory) } catch {
        await client.close()
        throw error
      }
      let observed = DeviceInputs(store: inputs, trace: trace)
      let native = IOSMultimodalBridge(client: client, inputs: observed)
      let bridge = DeviceBridge(inner: native, trace: trace)
      let current = MultimodalHost(store: inputs, bridge: bridge, picker: { $0(fixture) })
      host = current
      current.model.selectBackend(
        selection: MultimodalBridgeSelectionExplicit(backendId: SmolVLM2Model.backendId))
      current.model.setPrompt(prompt: "Describe this image.")
      current.model.setTokenBudget(text: "32")
      current.model.refreshBackends()
      try await wait("availability") { !current.model.state.refreshing }
      try check(
        current.model.state.queryFailure == nil
          && current.model.state.backends.contains {
            $0.backendId == SmolVLM2Model.backendId && $0.availability.available
          }, "availability")
      gates["availability"] = true
      do {
        try await modelStore.remove(manifest.ref)
        throw GateFailure(label: "unprotected model")
      } catch ModelStoreError.inUse { gates["modelLeaseProtected"] = true }

      for scenario in ["success", "cancel", "reentry", "background", "close"] {
        trace.events = []
        current.selectImageFile()
        try check(current.model.state.inputReference != nil, "selected opaque input")
        let starts = bridge.starts
        var acted = false
        var actionValid = false
        bridge.firstUpdate = { [weak current] in
          guard let current, scenario != "success", scenario != "reentry" else { return }
          acted = true
          trace.add(scenario)
          switch scenario {
          case "cancel":
            current.model.cancel()
            current.clearImageFile()
          case "background": current.background()
          case "close": current.close()
          default: break
          }
          actionValid =
            current.model.state.phase == .cancelling && observed.active == 1
            && observed.currentFileExists
        }
        current.model.generate()
        try await wait(scenario + " terminal") {
          let state = current.model.state
          return state.phase != .running && state.phase != .cancelling && !state.closing
        }
        await client.drain()
        try check(
          bridge.starts == starts + 1 && observed.active == 0, "one request and released input")
        var item: [String: Any] = ["events": trace.events]
        if scenario == "success" || scenario == "reentry" {
          try check(current.model.state.phase == .succeeded, scenario + " succeeded")
          guard let output = current.model.state.output else {
            throw GateFailure(label: "output absent")
          }
          try check(
            !output.text.isEmpty && output.backendId == SmolVLM2Model.backendId
              && output.model.modelId == identity.modelId
              && output.model.modelRevision == identity.modelRevision
              && output.model.runtimeRevision == identity.runtimeRevision, "actual identity")
          item["output"] = [
            "text": output.text, "backendId": output.backendId, "modelId": output.model.modelId,
            "modelRevision": output.model.modelRevision,
            "runtimeRevision": output.model.runtimeRevision,
          ]
        } else {
          try check(
            acted && actionValid && current.model.state.phase == .cancelled
              && current.model.state.output == nil, scenario + " cancelled after update")
        }
        scenarios[scenario] = item
        gates[scenario] = true
        if scenario == "background" {
          current.onForeground()
          let before = bridge.starts
          try await Task.sleep(nanoseconds: 300_000_000)
          try check(
            bridge.starts == before && current.model.state.phase == .cancelled,
            "foreground no automatic restart")
        }
        if scenario != "close" { current.clearImageFile() }
      }
      current.close()
      try await wait("close finished") { !current.model.state.closing }
      try check(
        current.model.state.closed && current.model.state.closeFailure == nil && bridge.closes == 1,
        "idempotent close")
      try await modelStore.remove(manifest.ref)
      gates["modelLeaseReleased"] = true
      try check(
        observed.active == 0 && !FileManager.default.fileExists(atPath: directory.path),
        "input directory cleanup")
      gates["inputsReleased"] = true
      current.model.generate()
      try check(
        current.model.state.failure?.code == .closed && bridge.starts == 5, "VM closed rejection")
      let rejected = ClosedCallback()
      _ = native.generateMultimodal(
        request: MultimodalBridgeRequest(
          inputReference: "closed", prompt: "closed", maxOutputTokens: 1),
        selection: MultimodalBridgeSelectionDefault.shared, callback: rejected)
      try check(rejected.closed, "bridge closed rejection")
      gates["closedRejected"] = true
      report["status"] = "passed"
    } catch {
      report["failedGate"] = (error as? GateFailure)?.label ?? "platform operation"
      host?.close()
      if let host { try? await wait("failure cleanup") { !host.model.state.closing } }
    }
    report["gates"] = gates
    report["scenarios"] = scenarios
    return report
  }

  private static func check(_ value: Bool, _ label: String) throws {
    if !value { throw GateFailure(label: label) }
  }
  private static func wait(_ label: String, until ready: () -> Bool) async throws {
    let deadline = ProcessInfo.processInfo.systemUptime + 120
    while !ready() {
      try check(ProcessInfo.processInfo.systemUptime < deadline, label + " timeout")
      try await Task.sleep(nanoseconds: 1_000_000)
    }
  }
}

private struct GateFailure: Error { let label: String }

@MainActor private final class DeviceTrace {
  var events = [[String: Any]]()
  func add(_ event: String, _ fields: [String: Any] = [:]) {
    events.append(fields.merging(["event": event]) { _, v in v })
  }
}

@MainActor private final class DeviceInputs: IOSMultimodalInputProvider {
  let store: MultimodalInputStore
  let trace: DeviceTrace
  var active = 0
  var current: URL?
  var currentFileExists: Bool {
    current.map { FileManager.default.fileExists(atPath: $0.path) } ?? false
  }
  init(store: MultimodalInputStore, trace: DeviceTrace) {
    self.store = store
    self.trace = trace
  }
  func acquire(reference: String) throws -> any IOSMultimodalInputLease {
    let lease = try store.acquire(reference: reference)
    active += 1
    current = lease.url
    return DeviceLease(underlying: lease) { [self] in
      active -= 1
      trace.add("inputReleased")
    }
  }
}

@MainActor private final class DeviceLease: IOSMultimodalInputLease {
  let underlying: any IOSMultimodalInputLease
  private var released = false
  let onRelease: () -> Void
  var url: URL { underlying.url }
  init(underlying: any IOSMultimodalInputLease, onRelease: @escaping () -> Void) {
    self.underlying = underlying
    self.onRelease = onRelease
  }
  func release() {
    if !released {
      released = true
      underlying.release()
      onRelease()
    }
  }
}

@MainActor private final class DeviceBridge: NSObject, @preconcurrency MultimodalBridge {
  let inner: IOSMultimodalBridge
  let trace: DeviceTrace
  var starts = 0, closes = 0
  var firstUpdate: (() -> Void)?
  init(inner: IOSMultimodalBridge, trace: DeviceTrace) {
    self.inner = inner
    self.trace = trace
  }
  func queryMultimodalBackends(callback: any MultimodalBridgeBackendsCallback) -> any BridgeTask {
    inner.queryMultimodalBackends(callback: callback)
  }
  func generateMultimodal(
    request: MultimodalBridgeRequest, selection: any MultimodalBridgeSelection,
    callback: any MultimodalBridgeGenerateCallback
  ) -> any BridgeTask {
    starts += 1
    trace.add("request")
    return inner.generateMultimodal(
      request: request, selection: selection,
      callback: DeviceCallback(downstream: callback, trace: trace, firstUpdate: firstUpdate))
  }
  func closeMultimodal(callback: any MultimodalBridgeCloseCallback) {
    closes += 1
    inner.closeMultimodal(callback: callback)
  }
}

@MainActor
private final class DeviceCallback: NSObject, @preconcurrency MultimodalBridgeGenerateCallback {
  let downstream: any MultimodalBridgeGenerateCallback
  let trace: DeviceTrace
  var firstUpdate: (() -> Void)?
  init(
    downstream: any MultimodalBridgeGenerateCallback, trace: DeviceTrace, firstUpdate: (() -> Void)?
  ) {
    self.downstream = downstream
    self.trace = trace
    self.firstUpdate = firstUpdate
  }
  func onMultimodalUpdate(update: MultimodalBridgeUpdate) {
    downstream.onMultimodalUpdate(update: update)
    trace.add("update", ["sequence": Int(update.sequence)])
    let action = firstUpdate
    firstUpdate = nil
    action?()
  }
  func onMultimodalResult(output: MultimodalBridgeOutput) {
    downstream.onMultimodalResult(output: output)
    trace.add("terminal", ["phase": "SUCCEEDED"])
  }
  func onMultimodalFailure(failure: MultimodalBridgeFailure) {
    downstream.onMultimodalFailure(failure: failure)
    trace.add("terminal", ["phase": "FAILED"])
  }
  func onMultimodalCancelled() {
    downstream.onMultimodalCancelled()
    trace.add("terminal", ["phase": "CANCELLED"])
  }
}

@MainActor
private final class ClosedCallback: NSObject, @preconcurrency MultimodalBridgeGenerateCallback {
  var closed = false
  func onMultimodalUpdate(update: MultimodalBridgeUpdate) {}
  func onMultimodalResult(output: MultimodalBridgeOutput) {}
  func onMultimodalFailure(failure: MultimodalBridgeFailure) { closed = failure.code == .closed }
  func onMultimodalCancelled() {}
}
