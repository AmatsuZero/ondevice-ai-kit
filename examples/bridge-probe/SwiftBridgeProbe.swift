import Foundation
import ODAIKitBinding
final class ProbeTask: NSObject, BridgeTask { func cancel() {} }
final class SwiftFaceBridge: NSObject, FaceBridge {
    func backendId() -> String { "apple.vision" }
    func availability() -> BridgeAvailability { BridgeAvailability(available: true, reason: nil, backendId: backendId(), minimumOS: "15.0") }
    func detect(inputId: String, selection: BridgeBackendSelection, callback: FaceCallback) -> BridgeTask { callback.onCancelled(); return ProbeTask() }
    func close() {}
}
func verifySwiftBridge() { precondition(FaceBridgeKt.probeBackend(bridge: SwiftFaceBridge()) == "apple.vision") }
