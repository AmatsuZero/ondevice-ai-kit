import XCTest

final class PlaybackEnhancementUITests: XCTestCase {
  func testEmptyPlaybackLayoutAdaptsToLandscape() {
      continueAfterFailure = false

      let app = XCUIApplication()
      app.launchArguments += ["--smoke-blank"]
      app.launch()

      // Start from a real portrait app frame before any portrait-coordinate assertion.
      XCUIDevice.shared.orientation = .portrait
      let portraitReady = expectation(for: NSPredicate { _, _ in
          app.frame.width < app.frame.height
      }, evaluatedWith: nil, handler: nil)
      wait(for: [portraitReady], timeout: 5)

      func element(_ id: String) -> XCUIElement {
          app.descendants(matching: .any)[id]
      }

      func backButton() -> XCUIElement {
          app.buttons["unified-back"]
      }

      // 空状态下宿主把原生 MTKView 设为 isHidden，布局断言应针对共享包装容器，
      // 而不是已经隐藏的原生 playback-preview。
      func requireEmptyContainerRatio(_ file: StaticString = #filePath, _ line: UInt = #line) {
          let container = element("playback-preview-container")
          XCTAssertTrue(container.exists, "playback-preview-container 必须存在", file: file, line: line)
          let f = container.frame
          XCTAssertTrue(f.width.isFinite && f.height.isFinite && f.width > 0 && f.height > 0, "playback-preview-container frame 必须有限且为正", file: file, line: line)
          XCTAssertEqual(f.width / f.height, 16.0 / 9.0, accuracy: 0.08, "playback-preview-container 宽高比应约为 16:9", file: file, line: line)
      }

      // 标题必须相对整屏几何中心居中，不能按返回按钮之后的剩余宽度居中（那样会右偏）。
      func requireHeaderCentered(_ file: StaticString = #filePath, _ line: UInt = #line) {
          let title = app.staticTexts.matching(NSPredicate(format: "label == %@", "播放时增强")).firstMatch
          let back = backButton()
          XCTAssertTrue(title.exists, "标题 播放时增强 应存在", file: file, line: line)
          XCTAssertTrue(back.exists, "返回按钮应存在", file: file, line: line)
          let tf = title.frame
          let bf = back.frame
          let af = app.frame
          XCTAssertTrue(tf.width.isFinite && tf.height.isFinite && tf.minX.isFinite && tf.minY.isFinite, "标题 bounds 必须有限", file: file, line: line)
          XCTAssertTrue(bf.width.isFinite && bf.height.isFinite && bf.minX.isFinite && bf.minY.isFinite, "返回按钮 bounds 必须有限", file: file, line: line)
          XCTAssertGreaterThanOrEqual(tf.minX, af.minX - 2, "标题应在 app 水平范围内", file: file, line: line)
          XCTAssertLessThanOrEqual(tf.maxX, af.maxX + 2, "标题应在 app 水平范围内", file: file, line: line)
          XCTAssertGreaterThanOrEqual(tf.minY, af.minY - 2, "标题应在 app 垂直范围内", file: file, line: line)
          XCTAssertLessThanOrEqual(tf.maxY, af.maxY + 2, "标题应在 app 垂直范围内", file: file, line: line)
          XCTAssertGreaterThanOrEqual(bf.minX, af.minX - 2, "返回按钮应在 app 水平范围内", file: file, line: line)
          XCTAssertLessThanOrEqual(bf.maxX, af.maxX + 2, "返回按钮应在 app 水平范围内", file: file, line: line)
          XCTAssertGreaterThanOrEqual(bf.minY, af.minY - 2, "返回按钮应在 app 垂直范围内", file: file, line: line)
          XCTAssertLessThanOrEqual(bf.maxY, af.maxY + 2, "返回按钮应在 app 垂直范围内", file: file, line: line)
          XCTAssertEqual(tf.midX, af.midX, accuracy: 2, "标题应按整屏几何中心居中，不得按剩余宽度右移", file: file, line: line)
          let overlapX = min(tf.maxX, bf.maxX) - max(tf.minX, bf.minX)
          let overlapY = min(tf.maxY, bf.maxY) - max(tf.minY, bf.minY)
          XCTAssertFalse(overlapX > 0 && overlapY > 0, "标题与返回按钮不应重叠", file: file, line: line)
      }

      let entry = element("capability-playback_enhancement")
      XCTAssertTrue(entry.waitForExistence(timeout: 5), "未找到 capability-playback_enhancement")
      let video = app.buttons["视频"]
      video.tap()
      entry.tap()

      let openPlayer = app.buttons["open-realtime-player"]
      let togglePlayer = app.buttons["toggle-realtime-player"]
      let presentedMode = element("playback-presented-mode")
      let selectedMode = element("playback-selected-mode")
      let saveRecord = app.buttons["save-record"]

      XCTAssertTrue(openPlayer.waitForExistence(timeout: 5))

      // 等待宿主状态稳定到“尚未选择”，避免与 CMP 250ms 轮询产生竞态
      let hostStatus = app.staticTexts["playback-host-status"]
      let hostStatusReady = expectation(for: NSPredicate { _, _ in
          hostStatus.label.contains("尚未选择")
      }, evaluatedWith: nil, handler: nil)
      wait(for: [hostStatusReady], timeout: 10)
      XCTAssertTrue(hostStatus.label.contains("尚未选择"), "playback-host-status 应包含 尚未选择, app=\(app.debugDescription)")

      XCTAssertTrue(openPlayer.isEnabled, "open-realtime-player 应可用")

      // 等待 toggle-realtime-player 落入禁用态，避免布局/状态尚未稳定时误判
      let toggleDisabled = expectation(for: NSPredicate { _, _ in
          togglePlayer.exists && !togglePlayer.isEnabled
      }, evaluatedWith: nil, handler: nil)
      wait(for: [toggleDisabled], timeout: 5)

      XCTAssertTrue(togglePlayer.exists, "toggle-realtime-player 应存在, app=\(app.debugDescription)")
      XCTAssertFalse(togglePlayer.isEnabled, "toggle-realtime-player 应不可用, app=\(app.debugDescription)")
      XCTAssertTrue(presentedMode.exists, "playback-presented-mode 应存在")
      XCTAssertTrue(presentedMode.label.contains("尚未播放"), "playback-presented-mode 应包含 尚未播放")
      XCTAssertTrue(selectedMode.exists, "playback-selected-mode 应存在")
      XCTAssertFalse(saveRecord.exists, "空状态不应出现 save-record")

      requireEmptyContainerRatio()
      requireHeaderCentered()
      let portraitBack = backButton()
      XCTAssertTrue(portraitBack.exists, "返回按钮应存在")
      XCTAssertLessThanOrEqual(portraitBack.frame.minY - app.frame.minY, 100, "竖屏返回按钮应靠近顶部")

      let portraitShot = XCTAttachment(screenshot: app.screenshot())
      portraitShot.name = "playback-layout-portrait"
      portraitShot.lifetime = .keepAlways
      add(portraitShot)

      XCUIDevice.shared.orientation = .landscapeLeft
      defer {
          // Restore and wait for a real portrait frame so the following tests start isolated.
          XCUIDevice.shared.orientation = .portrait
          let portraitRestored = expectation(for: NSPredicate { _, _ in
              app.frame.width < app.frame.height
          }, evaluatedWith: nil, handler: nil)
          wait(for: [portraitRestored], timeout: 5)
      }

      let landscapeReady = expectation(for: NSPredicate { _, _ in
          app.frame.width > app.frame.height
      }, evaluatedWith: nil, handler: nil)
      wait(for: [landscapeReady], timeout: 5)

      requireEmptyContainerRatio()
      requireHeaderCentered()

      let landscapeShot = XCTAttachment(screenshot: app.screenshot())
      landscapeShot.name = "playback-layout-landscape"
      landscapeShot.lifetime = .keepAlways
      add(landscapeShot)

      let landscapeBack = backButton()
      XCTAssertTrue(landscapeBack.isHittable, "横屏返回按钮应可点击")

      var swipes = 0
      while !openPlayer.isHittable && swipes < 6 {
          app.swipeUp()
          swipes += 1
          Thread.sleep(forTimeInterval: 1.0)
      }
      XCTAssertTrue(openPlayer.isHittable, "滚动后 open-realtime-player 应可点击")
      XCTAssertTrue(openPlayer.isEnabled, "open-realtime-player 应可用")
  }

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

  func testLoadedVideoPreviewKeepsRatioAcrossOrientations() {
    let app = openVideo("odai-sr-motion-540-v2,")
    waitForOriginal(app.staticTexts["playback-host-status"])
    // 竖屏：原生预览存在且帧渲染后，assertMoving 已校验唯一性与 16:9 比例。
    assertMoving(app, name: "loaded-portrait-motion")

    defer {
      // 恢复并等待真实竖屏 frame，避免横屏方向泄漏到后续测试。
      XCUIDevice.shared.orientation = .portrait
      let portraitRestored = expectation(for: NSPredicate { _, _ in
          app.frame.width < app.frame.height
      }, evaluatedWith: nil, handler: nil)
      wait(for: [portraitRestored], timeout: 5)
    }

    XCUIDevice.shared.orientation = .landscapeLeft
    let landscapeReady = expectation(for: NSPredicate { _, _ in
        app.frame.width > app.frame.height
    }, evaluatedWith: nil, handler: nil)
    wait(for: [landscapeReady], timeout: 5)
    // 横屏保留运动检测与截图证据；assertMoving 在帧渲染后校验原生 16:9 比例。
    assertMoving(app, name: "loaded-landscape-motion")
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
    // 显式从竖屏启动并同步真实 frame，避免其它测试遗留的横屏方向泄漏到这里。
    XCUIDevice.shared.orientation = .portrait
    app.launch()
    let portraitReady = expectation(for: NSPredicate { _, _ in
        app.frame.width < app.frame.height
    }, evaluatedWith: nil, handler: nil)
    wait(for: [portraitReady], timeout: 5)
    // 先切到“视频”分类，再等待 LazyColumn 条目，确保 tap 的是分类筛选后的入口。
    let videoTab = app.buttons["视频"]
    XCTAssertTrue(videoTab.waitForExistence(timeout: 20), app.debugDescription)
    videoTab.tap()
    let entry = app.descendants(matching: .any)["capability-playback_enhancement"]
    XCTAssertTrue(entry.waitForExistence(timeout: 20), app.debugDescription)
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

  private func requireNativeRatio(_ app: XCUIApplication, _ file: StaticString = #filePath, _ line: UInt = #line) {
    let previews = app.descendants(matching: .any).matching(
      NSPredicate(format: "identifier == %@", "playback-preview"))
    XCTAssertEqual(previews.count, 1, "原生 playback-preview 应唯一", file: file, line: line)
    let f = previews.firstMatch.frame
    XCTAssertTrue(f.width.isFinite && f.height.isFinite && f.width > 0 && f.height > 0,
                  "原生 playback-preview frame 必须有限且为正", file: file, line: line)
    XCTAssertEqual(f.width / f.height, 16.0 / 9.0, accuracy: 0.08,
                   "原生 playback-preview 宽高比应约为 16:9", file: file, line: line)
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
    // 帧已实际渲染，校验原生 playback-preview 唯一、边界有限且保持 16:9。
    requireNativeRatio(app)
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
