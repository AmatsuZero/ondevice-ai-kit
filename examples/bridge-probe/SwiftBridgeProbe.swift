import Foundation
import ODAIKitBinding
final class SwiftFaceBridge: NSObject, FaceBridge { func backendId() -> String { "apple.vision" } }
func verifySwiftBridge() { precondition(FaceBridgeKt.probeBackend(bridge: SwiftFaceBridge()) == "apple.vision") }
