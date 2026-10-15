import SwiftUI
import ODAIKitCore
@main
struct NativeConsumerApp: App {
    var body: some Scene { WindowGroup { Text("OnDevice AI Kit: \(CapabilityStatus.available.rawValue)") } }
}
