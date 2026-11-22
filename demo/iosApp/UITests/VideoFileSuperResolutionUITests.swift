import XCTest

final class VideoFileSuperResolutionUITests: XCTestCase {
    func testDefaultDemoConnectsFileSuperResolutionAndSystemPicker() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--smoke-blank"]
        app.launch()

        // Start from and synchronize to a real portrait app frame before any
        // portrait-coordinate assertion.
        XCUIDevice.shared.orientation = .portrait
        let portraitReady = expectation(for: NSPredicate { _, _ in
            app.frame.width < app.frame.height
        }, evaluatedWith: nil, handler: nil)
        wait(for: [portraitReady], timeout: 5)

        // Select the Video category first so the target entry is inside the
        // LazyColumn's current semantic viewport, then wait for the entry.
        app.buttons["视频"].tap()
        let entry = app.descendants(matching: .any)["capability-file_super_resolution"]
        XCTAssertTrue(entry.waitForExistence(timeout: 20), app.debugDescription)
        entry.tap()

        let back = app.buttons["unified-back"]
        let select = app.buttons["video-select"]
        XCTAssertTrue(back.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(select.waitForExistence(timeout: 10), app.debugDescription)

        // First detail entry: header/safe-area layout must keep controls on screen.
        let backOffset = back.frame.minY - app.frame.minY
        let selectVisibleBeforeScroll = select.frame.maxY < app.frame.maxY - 24
        if backOffset > 100 || !selectVisibleBeforeScroll {
            let attachment = XCTAttachment(image: app.screenshot().image)
            attachment.name = "detail-entry-layout"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        XCTAssertLessThanOrEqual(backOffset, 100, "back button too low: \(backOffset)")
        XCTAssertTrue(selectVisibleBeforeScroll,
                      "select not visible before scroll: maxY=\(select.frame.maxY) screenMaxY=\(app.frame.maxY)")

        // Empty input state.
        XCTAssertTrue(select.isEnabled)
        XCTAssertFalse(app.buttons["video-process"].isEnabled)
        XCTAssertEqual(app.staticTexts["video-input"].label, "尚未选择视频")

        // System picker cancellation, preserving empty-input/process-disabled state.
        for _ in 0..<5 where !select.isHittable { app.swipeUp() }
        XCTAssertTrue(select.isHittable)
        select.tap()
        let picker = app.navigationBars["FullDocumentManagerViewControllerNavigationBar"]
        XCTAssertTrue(picker.waitForExistence(timeout: 10), app.debugDescription)
        let cancel = picker.buttons.matching(
            NSPredicate(format: "label IN %@", ["取消", "Cancel"])).firstMatch
        XCTAssertTrue(cancel.waitForExistence(timeout: 5), app.debugDescription)
        cancel.tap()
        XCTAssertTrue(select.waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["video-input"].label, "尚未选择视频")
        XCTAssertFalse(app.buttons["video-process"].isEnabled)

        // No output was produced, so there is nothing to save.
        XCTAssertFalse(app.buttons["save-record"].exists, "save-record should not exist without output")

        // Backend stays hidden until the advanced controls are revealed.
        let backend = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@", "apple.videotoolbox.sr")).firstMatch
        let advancedToggle = app.buttons["video-advanced-toggle"]
        XCTAssertTrue(advancedToggle.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertFalse(backend.exists, "video-backend should not be exposed before the advanced toggle")

        for _ in 0..<6 where !advancedToggle.isHittable {
            app.swipeUp()
            Thread.sleep(forTimeInterval: 1.0)
        }
        XCTAssertTrue(advancedToggle.isHittable)
        advancedToggle.tap()

        // Backend assertions (reordered after picker cancel + advanced tap).
        XCTAssertTrue(backend.waitForExistence(timeout: 10), app.debugDescription)
        #if targetEnvironment(simulator)
        XCTAssertTrue(backend.label.contains("unsupportedDevice"), backend.label)
        #endif

        // Return to the entry list: scroll down until the back button is hittable.
        for _ in 0..<6 where !back.isHittable {
            app.swipeDown()
            Thread.sleep(forTimeInterval: 1.0)
        }
        XCTAssertTrue(back.isHittable)
        back.tap()
        XCTAssertTrue(entry.waitForExistence(timeout: 10))
    }
}