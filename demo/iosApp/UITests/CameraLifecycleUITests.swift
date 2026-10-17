import XCTest

final class CameraLifecycleUITests: XCTestCase {
    func testPhotoPickerCancellationReturnsToDemo() {
        let app = XCUIApplication(); app.launch()
        let select = app.buttons["选择图片"]
        XCTAssertTrue(select.waitForExistence(timeout: 15)); select.tap()
        let cancel = app.buttons["取消"]
        XCTAssertTrue(cancel.waitForExistence(timeout: 10), "Photo picker should offer cancellation")
        cancel.tap()
        XCTAssertTrue(select.waitForExistence(timeout: 10))
        XCTAssertTrue(select.isEnabled)
        XCTAssertTrue(app.buttons["相机"].isEnabled)
    }

    func testPhotoPickerSelectionReturnsToDemo() {
        let app = XCUIApplication(); app.launch()
        let select = app.buttons["选择图片"]
        XCTAssertTrue(select.waitForExistence(timeout: 15)); select.tap()
        let firstPhoto = app.images.matching(identifier: "PXGGridLayout-Info").firstMatch
        XCTAssertTrue(firstPhoto.waitForExistence(timeout: 15), "Photo picker needs a photo on this device")
        firstPhoto.tap()
        XCTAssertTrue(select.waitForExistence(timeout: 15))
        let result = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "人脸 ")).firstMatch
        XCTAssertTrue(result.waitForExistence(timeout: 20), "Selected photo should reach the detector")
        XCTAssertTrue(select.isEnabled)
    }

    func testCameraKeepsControlsStableAcrossFrames() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-camera-test"]; app.launch()
        let camera = app.buttons["相机"]
        XCTAssertTrue(camera.waitForExistence(timeout: 15)); camera.tap()
        let counter = app.staticTexts["camera-frame-count"]
        XCTAssertTrue(counter.waitForExistence(timeout: 5)); waitForFrames(counter, after: 0)
        let result = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "人脸 ")).firstMatch
        for _ in 0..<3 {
            XCTAssertTrue(app.buttons["选择图片"].isEnabled)
            XCTAssertTrue(app.buttons["Benchmark"].isEnabled)
            XCTAssertTrue(result.exists, "Camera must keep the last result while detecting the next frame")
            XCTAssertEqual(app.progressIndicators.count, 0)
            waitForFrames(counter, after: Int(counter.label) ?? 0)
        }
        app.buttons["停止"].tap()
    }
    func testCameraResumesAfterBackgroundAndHonorsUserStop() {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-camera-test"]
        app.launch()
        let camera = app.buttons["相机"]
        XCTAssertTrue(camera.waitForExistence(timeout: 15))
        camera.tap()
        let counter = app.staticTexts["camera-frame-count"]
        XCTAssertTrue(counter.waitForExistence(timeout: 5))
        waitForFrames(counter, after: 0)
        for _ in 0..<2 {
            XCUIDevice.shared.press(.home)
            app.activate()
            let before = Int(counter.label) ?? 0
            waitForFrames(counter, after: before)
        }
        app.buttons["停止"].tap()
        let stopped = Int(counter.label) ?? 0
        XCUIDevice.shared.press(.home)
        app.activate()
        let resumed = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in (Int(counter.label) ?? 0) > stopped }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [resumed], timeout: 2), .timedOut, "User stop must survive activation")
        camera.tap()
        waitForFrames(counter, after: stopped)
        app.buttons["停止"].tap()
    }

    func testCameraPermissionDeniedOffersSettingsRecovery() {
        let app = XCUIApplication()
        app.launchArguments = ["--simulate-camera-permission-denied"]
        app.launch()
        let camera = app.buttons["相机"]
        XCTAssertTrue(camera.waitForExistence(timeout: 15))
        camera.tap()
        let settings = app.buttons["打开设置"]
        XCTAssertTrue(settings.waitForExistence(timeout: 10), "Denied camera permission should offer Settings recovery")
        XCTAssertTrue(settings.isEnabled)
        settings.tap()
        let preferences = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
        XCTAssertTrue(preferences.waitForExistence(timeout: 10), "Recovery button should open system Settings")
    }

    private func waitForFrames(_ counter: XCUIElement, after count: Int, file: StaticString = #filePath, line: UInt = #line) {
        let fresh = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in (Int(counter.label) ?? 0) > count + 2 }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [fresh], timeout: 10), .completed, "Camera inference must keep delivering frames", file: file, line: line)
    }
}
