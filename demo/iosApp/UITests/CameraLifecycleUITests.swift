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
        let onboardingClose = app.buttons["关闭"].firstMatch
        if onboardingClose.waitForExistence(timeout: 3) {
            onboardingClose.tap()
        }
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

    func testCameraKeepsDeliveringFramesForTwoMinutesAndStops() {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-camera-test"]
        app.launch()
        let camera = app.buttons["相机"]
        XCTAssertTrue(camera.waitForExistence(timeout: 15))
        camera.tap()
        var stoppedByTest = false
        defer {
            if !stoppedByTest, app.buttons["停止"].exists {
                app.buttons["停止"].tap()
            }
        }
        let counter = app.staticTexts["camera-frame-count"]
        XCTAssertTrue(counter.waitForExistence(timeout: 5))
        waitForFrames(counter, after: 0)
        let started = ProcessInfo.processInfo.systemUptime
        var previous = Int(counter.label) ?? 0
        let minimumSuccessfulResultsPerWindow = 10
        for sample in 1...4 {
            Thread.sleep(forTimeInterval: 30)
            let current = Int(counter.label) ?? 0
            XCTAssertGreaterThanOrEqual(
                current - previous,
                minimumSuccessfulResultsPerWindow,
                "Each 30-second sample window must add at least 10 successful inference results"
            )
            XCTAssertTrue(app.buttons["选择图片"].isEnabled)
            XCTAssertTrue(app.buttons["Benchmark"].isEnabled)
            XCTAssertEqual(app.progressIndicators.count, 0)
            let elapsed = ProcessInfo.processInfo.systemUptime - started
            let evidence = XCTAttachment(string: "sample=\(sample) elapsedSeconds=\(elapsed) successfulFrames=\(current) delta=\(current - previous)")
            evidence.name = "camera-sustained-sample-\(sample)"
            evidence.lifetime = .keepAlways
            add(evidence)
            previous = current
        }
        let totalElapsed = ProcessInfo.processInfo.systemUptime - started
        XCTAssertGreaterThanOrEqual(totalElapsed, 120, "The sustained camera window must cover at least two minutes")
        app.buttons["停止"].tap()
        stoppedByTest = true
        let stopped = Int(counter.label) ?? 0
        let advancing = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            (Int(counter.label) ?? 0) > stopped
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [advancing], timeout: 5), .timedOut, "Stopped Demo must not publish more successful inference results to the UI")
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
