import XCTest

final class PlaybackEnhancementUITests: XCTestCase {
  func testUnifiedVideoContinuesRenderingAcrossModesAndBackground() {
    let app = openVideo("odai-sr-motion-540-v2,")
    let entry = app.descendants(matching: .any)["capability-playback_enhancement"]
    let status = app.staticTexts["playback-host-status"]
    waitForOriginal(status)
    assertMoving(app, name: "original-motion")
    for (mode, label) in [("auto", "AUTO"), ("cunny", "CUNNY"), ("spatial", "SPATIAL")] {
      tapVisible(app.buttons["playback-mode-\(mode)"], app)
      expectation(
        for: NSPredicate(format: "label CONTAINS %@", label),
        evaluatedWith: app.staticTexts["playback-selected-mode"])
      waitForExpectations(timeout: 10)
      assertMoving(app, name: "\(mode)-motion")
      print("SR_STATUS \(mode): \(status.label)")
      XCTAssertTrue(app.staticTexts["playback-presented-mode"].label.contains("等待播放器确认上屏"))
    }
    tapVisible(app.buttons["toggle-realtime-player"], app)
    expectation(for: NSPredicate(format: "label CONTAINS %@", "播放"), evaluatedWith: app.buttons["toggle-realtime-player"])
    waitForExpectations(timeout: 5)
    tapVisible(app.buttons["playback-mode-original"], app)
    tapVisible(app.buttons["toggle-realtime-player"], app)
    XCUIDevice.shared.press(.home)
    Thread.sleep(forTimeInterval: 1)
    app.activate()
    assertMoving(app, name: "foreground-motion")
    app.buttons["unified-back"].tap()
    XCTAssertTrue(entry.waitForExistence(timeout: 10))
    entry.tap()
    XCTAssertTrue(app.staticTexts["playback-host-status"].label.contains("尚未选择"))
    XCTAssertFalse(app.buttons["toggle-realtime-player"].isEnabled)
  }

  func testEndOfStreamCanReplayFromStart() {
    let app = openVideo("odai-sr-motion-540-short,")
    waitForOriginal(app.staticTexts["playback-host-status"])
    assertMoving(app, name: "before-eos")
    let toggle = app.buttons["toggle-realtime-player"]
    expectation(for: NSPredicate(format: "label CONTAINS %@", "播放"), evaluatedWith: toggle)
    waitForExpectations(timeout: 15)
    tapVisible(toggle, app)
    expectation(for: NSPredicate(format: "label CONTAINS %@", "暂停"), evaluatedWith: toggle)
    waitForExpectations(timeout: 10)
    assertMoving(app, name: "eos-replay-motion")
  }

  private func waitForOriginal(_ status: XCUIElement) {
    XCTAssertTrue(status.waitForExistence(timeout: 20))
    expectation(for: NSPredicate(format: "label CONTAINS %@", "模拟器渲染：original"), evaluatedWith: status)
    waitForExpectations(timeout: 20)
  }

  private func openVideo(_ name: String) -> XCUIApplication {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launchArguments = ["--smoke-blank"]
    app.launch()
    let entry = app.descendants(matching: .any)["capability-playback_enhancement"]
    XCTAssertTrue(entry.waitForExistence(timeout: 20))
    app.buttons["视频"].tap()
    entry.tap()
    XCTAssertTrue(
      app.buttons["open-realtime-player"].waitForExistence(timeout: 10), app.debugDescription)
    tapVisible(app.buttons["open-realtime-player"], app)
    let pickerShot = XCTAttachment(screenshot: app.screenshot())
    pickerShot.name = "picker-open"
    pickerShot.lifetime = .keepAlways
    add(pickerShot)
    XCTAssertTrue(
      app.navigationBars["FullDocumentManagerViewControllerNavigationBar"].waitForExistence(
        timeout: 10), app.debugDescription)
    let file = app.cells.matching(NSPredicate(format: "label BEGINSWITH %@", name))
      .firstMatch
    if !file.waitForExistence(timeout: 2) {
      let folder = app.cells.matching(NSPredicate(format: "label BEGINSWITH %@", "ODAI Synthetic,"))
        .firstMatch
      if !folder.exists {
        let browse = app.tabBars.buttons.matching(
          NSPredicate(format: "label IN %@", ["浏览", "Browse"])
        ).firstMatch
        if browse.exists { browse.tap() }
        let local = app.staticTexts.matching(
          NSPredicate(format: "label IN %@", ["我的iPhone", "我的 iPhone", "On My iPhone"])
        ).firstMatch
        if local.waitForExistence(timeout: 3) { local.tap() }
      }
      XCTAssertTrue(folder.waitForExistence(timeout: 10), app.debugDescription)
      folder.tap()
    }
    XCTAssertTrue(file.waitForExistence(timeout: 10), app.debugDescription)
    file.tap()
    return app
  }

  private func assertMoving(_ app: XCUIApplication, name: String) {
    let preview = app.descendants(matching: .any)["playback-preview"]
    XCTAssertTrue(preview.waitForExistence(timeout: 10), app.debugDescription)
    for _ in 0..<4 where !preview.isHittable { app.swipeDown() }
    let first = preview.screenshot()
    Thread.sleep(forTimeInterval: 1)
    let second = preview.screenshot()
    let a = first.image.cgImage!.dataProvider!.data! as Data
    let b = second.image.cgImage!.dataProvider!.data! as Data
    XCTAssertEqual(a.count, b.count)
    let changed = zip(a, b).filter { abs(Int($0) - Int($1)) > 12 }.count
    print("SR_MOTION \(name): \(changed)/\(a.count) bytes changed")
    XCTAssertGreaterThan(Double(changed) / Double(a.count), 0.005, "Video frame froze")
    let attachment = XCTAttachment(screenshot: second)
    attachment.name = name
    attachment.lifetime = .keepAlways
    add(attachment)
  }

  private func tapVisible(_ element: XCUIElement, _ app: XCUIApplication) {
    for _ in 0..<5 where !element.isHittable { app.swipeUp() }
    // Compose scroll momentum is not included in XCTest's UIKit idle detection.
    Thread.sleep(forTimeInterval: 1)
    XCTAssertTrue(element.isHittable, app.debugDescription)
    element.tap()
  }
}
