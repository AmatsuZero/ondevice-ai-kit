import SwiftUI

/// Project-owned inert foreground companion. No media, permissions, models or network.
@main struct BackgroundApp: App {
  var body: some Scene {
    WindowGroup { Text("OnDeviceAI background validation companion").padding() }
  }
}
