import UIKit
import XCTest

final class AppBackgroundObservationTests: XCTestCase {
  @MainActor func testRecordsOrderedSnapshotsAndStopsOnlyOwnObservation() {
    let center = NotificationCenter()
    var phase = "RUNNING"
    let observation = AppBackgroundObservation(center: center) { ["phase": phase, "sequence": 0] }
    center.post(name: UIApplication.willResignActiveNotification, object: nil)
    phase = "CANCELLED"
    center.post(name: UIApplication.didEnterBackgroundNotification, object: nil)
    center.post(name: UIApplication.willEnterForegroundNotification, object: nil)
    center.post(name: UIApplication.didBecomeActiveNotification, object: nil)
    XCTAssertEqual(
      observation.events.compactMap { $0["name"] as? String },
      ["willResignActive", "didEnterBackground", "willEnterForeground", "didBecomeActive"])
    XCTAssertEqual(observation.events[0]["phase"] as? String, "RUNNING")
    XCTAssertEqual(observation.events[1]["phase"] as? String, "CANCELLED")
    observation.stop()
    center.post(name: UIApplication.willResignActiveNotification, object: nil)
    XCTAssertEqual(observation.events.count, 4)
  }
  @MainActor func testKeepsDuplicateNotificationsForStrictGateRejection() {
    let center = NotificationCenter()
    let observation = AppBackgroundObservation(center: center) { [:] }
    defer { observation.stop() }
    center.post(name: UIApplication.willResignActiveNotification, object: nil)
    center.post(name: UIApplication.willResignActiveNotification, object: nil)
    XCTAssertEqual(observation.events.count, 2)
  }
}
