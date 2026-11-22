import XCTest

final class VideoFileSuperResolutionUITests: XCTestCase {
    func testDefaultDemoConnectsFileSuperResolutionAndSystemPicker() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--smoke-blank"]
        app.launch()
        let entry = app.descendants(matching: .any)["capability-file_super_resolution"]
        XCTAssertTrue(entry.waitForExistence(timeout: 20))
        app.buttons["视频"].tap()
        entry.tap()

        let select = app.buttons["video-select"]
        XCTAssertTrue(select.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(select.isEnabled)
        XCTAssertFalse(app.buttons["video-process"].isEnabled)
        XCTAssertEqual(app.staticTexts["video-input"].label, "尚未选择视频")
        let backend = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@", "apple.videotoolbox.sr")).firstMatch
        XCTAssertTrue(backend.waitForExistence(timeout: 10), app.debugDescription)
        #if targetEnvironment(simulator)
        XCTAssertTrue(backend.label.contains("unsupportedDevice"), backend.label)
        #endif

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
        app.buttons["unified-back"].tap()
        XCTAssertTrue(entry.waitForExistence(timeout: 10))
    }
}
