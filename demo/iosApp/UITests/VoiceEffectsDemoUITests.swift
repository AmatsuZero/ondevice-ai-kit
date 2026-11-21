import XCTest

final class VoiceEffectsDemoUITests: XCTestCase {
    func testPageOpensAndReturnsWithoutStartingMicrophone() {
        continueAfterFailure = false
        let app = XCUIApplication(); app.launchArguments = ["--smoke-blank"]; app.launch()
        let entry = app.descendants(matching: .any)["capability-voice_effects"]
        XCTAssertTrue(entry.waitForExistence(timeout:15)); entry.tap()
        let importer = app.buttons["voice-import"]
        XCTAssertTrue(importer.waitForExistence(timeout:15)); XCTAssertTrue(importer.isEnabled)
        XCTAssertTrue(app.buttons["voice-record"].exists)
        XCTAssertFalse(app.buttons["voice-process"].isEnabled)
        XCTAssertFalse(app.buttons["voice-stop-record"].exists)
        app.buttons["unified-back"].tap()
        XCTAssertTrue(entry.waitForExistence(timeout:10))
    }
}
