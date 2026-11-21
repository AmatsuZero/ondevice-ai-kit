import XCTest

final class PhotoPickerOrientationUITests: XCTestCase {
    func testExifTwoPreservesMirroredPixels() {
        selectOnlySyntheticPhoto(expected: "80x40:white,black,white,black")
    }

    func testExifSixPreservesRotatedPixels() {
        selectOnlySyntheticPhoto(expected: "40x80:black,black,white,white")
    }

    func testExifSevenPreservesRotatedMirroredPixels() {
        selectOnlySyntheticPhoto(expected: "40x80:white,white,black,black")
    }

    private func selectOnlySyntheticPhoto(expected: String, file: StaticString = #filePath, line: UInt = #line) {
        continueAfterFailure = false
        guard let fixtureLabel = ProcessInfo.processInfo.environment["ODAI_PICKER_FIXTURE_LABEL"], !fixtureLabel.isEmpty else {
            XCTFail("An exact fixture label from the isolated simulator inventory is required", file: file, line: line)
            return
        }
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        let select = app.buttons["select-synthetic-photo"]
        XCTAssertTrue(select.waitForExistence(timeout: 15), file: file, line: line)
        XCTAssertEqual(app.staticTexts["photo-pixel-signature"].label, "no-selection", file: file, line: line)
        select.tap()
        let close = app.buttons["Close"].firstMatch
        if close.waitForExistence(timeout: 2) { close.tap() }
        let photos = app.images.matching(identifier: "PXGGridLayout-Info")
        XCTAssertTrue(photos.firstMatch.waitForExistence(timeout: 15), file: file, line: line)
        let grid = XCTAttachment(string: photos.allElementsBoundByIndex.map { $0.label }.joined(separator: "\n"))
        grid.name = "isolated-picker-grid"
        grid.lifetime = .keepAlways
        add(grid)
        // A new simulator includes Apple's stock photos. Never fall back to a
        // positional match: select only the externally verified synthetic asset.
        let fixture = photos.matching(NSPredicate(format: "label == %@", fixtureLabel))
        XCTAssertEqual(fixture.count, 1, "The exact synthetic fixture must be unique", file: file, line: line)
        fixture.firstMatch.tap()
        let signature = app.staticTexts["photo-pixel-signature"]
        let received = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", expected), object: signature)
        XCTAssertEqual(XCTWaiter.wait(for: [received], timeout: 15), .completed,
                       "Real picker output: \(signature.exists ? signature.label : "missing")", file: file, line: line)
        let evidence = XCTAttachment(string: "source=real-PHPicker controller=production expected=\(expected) actual=\(signature.label) inference=not-asserted")
        evidence.name = "synthetic-picker-orientation"
        evidence.lifetime = .keepAlways
        add(evidence)
    }
}
