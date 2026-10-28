import XCTest

final class PhotoSandboxDeviceUITests: XCTestCase {
    func testExifTwoUsesSandboxControllerPath() { assertFixture(2, expected: "80x40:white,black,white,black") }
    func testExifSixUsesSandboxControllerPath() { assertFixture(6, expected: "40x80:black,black,white,white") }
    func testExifSevenUsesSandboxControllerPath() { assertFixture(7, expected: "40x80:white,white,black,black") }

    private func assertFixture(_ orientation: Int, expected: String, file: StaticString = #filePath, line: UInt = #line) {
        let app = XCUIApplication()
        app.launchArguments = ["--sandbox-exif", "\(orientation)"]
        app.launch()
        let status = app.staticTexts["sandbox-status"]
        XCTAssertTrue(status.waitForExistence(timeout: 15), file: file, line: line)
        XCTAssertEqual(status.label, "sandbox-\(orientation)", file: file, line: line)
        let signature = app.staticTexts["photo-pixel-signature"]
        let imageExpectation = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", expected), object: signature)
        XCTAssertEqual(XCTWaiter.wait(for: [imageExpectation], timeout: 20), .completed,
                       "actual signature: \(signature.label)", file: file, line: line)
        let dimensions = expected.split(separator: ":").first!
        let vision = app.staticTexts["vision-result"]
        let visionExpectation = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", "apple.vision.face:\(dimensions):faces=0"), object: vision)
        XCTAssertEqual(XCTWaiter.wait(for: [visionExpectation], timeout: 30), .completed,
                       "actual Vision result: \(vision.label)", file: file, line: line)
    }
}
