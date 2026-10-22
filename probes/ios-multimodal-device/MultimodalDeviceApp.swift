import SwiftUI

@main struct MultimodalDeviceApp: App {
  @State private var status = "Running programmatic KMP host/VM validation"
  var body: some Scene {
    WindowGroup {
      Text(status).padding().task {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let report = await MultimodalDeviceSuite.run(documents: documents)
        do {
          let data = try JSONSerialization.data(
            withJSONObject: report, options: [.prettyPrinted, .sortedKeys])
          try data.write(
            to: documents.appendingPathComponent("device-result.json"), options: .atomic)
          status = report["status"] as? String ?? "failed"
        } catch { status = "report write failed" }
      }
    }
  }
}
