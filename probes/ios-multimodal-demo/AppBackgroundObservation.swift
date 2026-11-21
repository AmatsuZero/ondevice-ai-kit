import Foundation
import UIKit

@MainActor final class AppBackgroundObservation {
  private let center: NotificationCenter
  private let snapshot: () -> [String: Any]
  private(set) var events = [[String: Any]]()
  private var tokens = [NSObjectProtocol]()
  private let names: [(Notification.Name, String)] = [
    (UIApplication.willResignActiveNotification, "willResignActive"),
    (UIApplication.didEnterBackgroundNotification, "didEnterBackground"),
    (UIApplication.willEnterForegroundNotification, "willEnterForeground"),
    (UIApplication.didBecomeActiveNotification, "didBecomeActive"),
  ]

  init(center: NotificationCenter = .default, snapshot: @escaping () -> [String: Any]) {
    self.center = center
    self.snapshot = snapshot
    for (name, label) in names {
      tokens.append(
        center.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
          MainActor.assumeIsolated {
            guard let self else { return }
            let monotonicSeconds = ProcessInfo.processInfo.systemUptime
            let applicationState = UIApplication.shared.applicationState.rawValue
            var value = self.snapshot()
            value["name"] = label
            value["monotonicSeconds"] = monotonicSeconds
            value["applicationState"] = applicationState
            self.events.append(value)
          }
        })
    }
  }
  func stop() {
    tokens.forEach(center.removeObserver)
    tokens.removeAll()
  }
  deinit { tokens.forEach(center.removeObserver) }
}
