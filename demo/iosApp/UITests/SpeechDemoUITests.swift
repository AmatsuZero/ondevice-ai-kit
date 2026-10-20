import XCTest
import Vision

final class SpeechDemoUITests: XCTestCase {
    override func setUp() { super.setUp(); continueAfterFailure = false }
    func testTtsInputOpensKeyboard() {
        let app = XCUIApplication(); app.launch()
        let tab = app.buttons["speech-tab"]
        XCTAssertTrue(tab.waitForExistence(timeout: 15)); tab.tap()
        tapRenderedControl("朗读文本", in: app)
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
    }
    func testLocaleInputOpensKeyboard() {
        let app = XCUIApplication(); app.launch()
        let tab = app.buttons["speech-tab"]
        XCTAssertTrue(tab.waitForExistence(timeout: 15)); tab.tap()
        let locale = app.descendants(matching: .any)["speech-locale"]
        locale.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
    }
    func testSpeechEntryTtsCompletesCancelsAndReenters() throws {
        let app = XCUIApplication()
        app.launch()
        let tab = app.buttons["speech-tab"]
        XCTAssertTrue(tab.waitForExistence(timeout: 15)); tab.tap()
        tapRenderedControl("朗读文本", in: app)
        guard app.keyboards.firstMatch.waitForExistence(timeout: 5) else {
            XCTFail("Tapping the real field must open a keyboard"); return
        }
        app.typeText("Speech demo fixture.")
        dismissKeyboard(app)
        tapRenderedControl("播放TTS", in: app)
        let result = app.staticTexts["speech-tts-result"]
        XCTAssertTrue(result.waitForExistence(timeout: 30), "Native TTS must complete")
        tapRenderedControl("朗读文本", in: app)
        app.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 20))
        app.typeText(String(repeating: "This is an on device cancellation fixture. ", count: 12))
        dismissKeyboard(app)
        tapRenderedControl("播放TTS", in: app)
        let cancel = app.buttons["speech-cancel"]
        XCTAssertTrue(cancel.isEnabled); tapRenderedControl("取消语音", in: app, edgeInset: 0, settle: false)
        XCTAssertTrue(app.staticTexts["speech-cancelled"].waitForExistence(timeout: 20))
        XCUIDevice.shared.press(.home); app.activate()
        XCTAssertTrue(app.staticTexts["speech-cancelled"].exists, "Foreground must not restart speech")
        // Reentry is exercised after the real native cancellation terminal.
        tapRenderedControl("播放TTS", in: app)
        XCTAssertTrue(cancel.exists && cancel.isEnabled)
        XCUIDevice.shared.press(.home); app.activate()
        XCTAssertTrue(app.staticTexts["speech-cancelled"].waitForExistence(timeout: 20))
        XCTAssertFalse(cancel.exists, "Returning to the foreground must not restart speech")
    }

    func testFilePickerCancellationReturnsWithoutSelectingInput() {
        let app = XCUIApplication(); app.launch()
        let tab = app.buttons["speech-tab"]
        XCTAssertTrue(tab.waitForExistence(timeout: 15)); tab.tap()
        tapRenderedControl("选择音频文件", in: app)
        let cancel = app.buttons.matching(NSPredicate(format: "label == '取消' OR label == 'Cancel'")).firstMatch
        XCTAssertTrue(cancel.waitForExistence(timeout: 10)); cancel.tap()
        XCTAssertTrue(app.staticTexts["speech-audio-name"].exists)
        XCTAssertFalse(app.buttons["speech-recognize-file"].isEnabled)
    }

    func testFileRecognitionImportCancellationReentryAndBackground() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["ODAI_SPEECH_FILE_UI_TEST"] == "1",
            "Requires the fixed synthetic CAF in this Demo's Files folder; see speech-demo-bridge.md")
        let app = XCUIApplication(); app.launch()
        let tab = app.buttons["speech-tab"]
        XCTAssertTrue(tab.waitForExistence(timeout: 15)); tab.tap()
        selectFixture(in: app)
        let selected = app.staticTexts["speech-audio-name"]
        let imported = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label == %@", "odai-speech-ui-fixture.caf"), object: selected)
        XCTAssertEqual(XCTWaiter.wait(for: [imported], timeout: 10), .completed)
        XCTAssertEqual(selected.label, "odai-speech-ui-fixture.caf")
        tapRenderedControl("选择音频文件", in: app)
        let cancelPicker = app.buttons.matching(NSPredicate(format: "label == '取消' OR label == 'Cancel'")).firstMatch
        XCTAssertTrue(cancelPicker.waitForExistence(timeout: 10)); cancelPicker.tap()
        XCTAssertEqual(selected.label, "odai-speech-ui-fixture.caf", "Cancelling the picker must preserve the selected input")
        tapRenderedControl("识别音频文件", in: app)
        allowSpeechRecognitionIfNeeded(app)
        let result = app.staticTexts["speech-asr-result"]
        XCTAssertTrue(result.waitForExistence(timeout: 45), "The real on-device file ASR must finish")
        // Reuse the selected file after the native terminal/drain, without importing again.
        tapRenderedControl("识别音频文件", in: app)
        let cancel = app.buttons["speech-cancel"]
        XCTAssertTrue(cancel.exists && cancel.isEnabled, "Cancellation requires an admitted running task")
        tapRenderedControl("取消语音", in: app, edgeInset: 0, settle: false)
        XCTAssertTrue(app.staticTexts["speech-cancelled"].waitForExistence(timeout: 20))
        XCTAssertEqual(selected.label, "odai-speech-ui-fixture.caf")
        tapRenderedControl("识别音频文件", in: app)
        XCTAssertTrue(result.waitForExistence(timeout: 45), "Recognition must reenter after cancellation drain")
        tapRenderedControl("识别音频文件", in: app)
        XCTAssertTrue(cancel.exists && cancel.isEnabled)
        XCUIDevice.shared.press(.home); app.activate()
        XCTAssertTrue(app.staticTexts["speech-cancelled"].waitForExistence(timeout: 20))
        XCTAssertFalse(cancel.exists, "Foreground must not restart recognition")
    }

    private func selectFixture(in app: XCUIApplication) {
        tapRenderedControl("选择音频文件", in: app)
        let browse = app.buttons.matching(NSPredicate(format: "label == '浏览' OR label == 'Browse'")).firstMatch
        if browse.waitForExistence(timeout: 3) { browse.tap() }
        let local = app.descendants(matching: .any).matching(NSPredicate(format: "label == '在我的 iPhone 上' OR label == '我的iPhone' OR label == 'On My iPhone'")).firstMatch
        if local.waitForExistence(timeout: 3) { local.tap() }
        let folder = app.descendants(matching: .any).matching(NSPredicate(format: "label == 'SpeechDemo'")).firstMatch
        if folder.waitForExistence(timeout: 3) { folder.tap() }
        let file = app.cells.matching(NSPredicate(format: "label BEGINSWITH 'odai-speech-ui-fixture, caf'")).firstMatch
        XCTAssertTrue(file.waitForExistence(timeout: 10), "Provision only the fixed synthetic fixture in the Demo's Documents folder")
        file.tap()
    }

    private func allowSpeechRecognitionIfNeeded(_ app: XCUIApplication) {
        let predicate = NSPredicate(format: "label == '允许' OR label == 'Allow' OR label == '好' OR label == 'OK'")
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let deadline = ProcessInfo.processInfo.systemUptime + 15
        while ProcessInfo.processInfo.systemUptime < deadline {
            for alert in [app.alerts.firstMatch, springboard.alerts.firstMatch] {
                let speechPrompt = alert.staticTexts.matching(NSPredicate(format:
                    "label CONTAINS[c] '语音识别' OR label CONTAINS[c] 'speech recognition'")).firstMatch
                let allow = alert.buttons.matching(predicate).firstMatch
                if speechPrompt.exists && allow.exists { allow.tap(); return }
            }
            if app.staticTexts["speech-asr-result"].exists || app.staticTexts["speech-failure"].exists { return }
            Thread.sleep(forTimeInterval: 0.2)
        }
    }

    private func dismissKeyboard(_ app: XCUIApplication) {
        let done = app.keyboards.buttons.matching(NSPredicate(format: "label == 'Done' OR label == '完成'")).firstMatch
        XCTAssertTrue(done.waitForExistence(timeout: 3), "Text input must provide a Done action")
        if done.exists { done.tap() }
    }
    // Compose can retain offscreen AX nodes at stale, apparently hittable frames.
    // Locate fixed UI labels in the rendered image, then send a real screen tap.
    // Recognition stays on device and neither input nor OCR results are logged.
    private func tapRenderedControl(_ label: String, in app: XCUIApplication, edgeInset: CGFloat = 8, settle: Bool = true) {
        for direction in [true, false] {
            for _ in 0..<12 {
                let point = settle ? stableRenderedPoint(label, in: app, edgeInset: edgeInset)
                    : renderedPoint(label, in: app, edgeInset: edgeInset)
                if let point {
                    app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: point.x, dy: point.y)).tap()
                    return
                }
                let start = direction ? 0.88 : 0.70
                let end = direction ? 0.70 : 0.88
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: start))
                    .press(forDuration: 0.05,
                        thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: end)),
                        withVelocity: .slow, thenHoldForDuration: 0.3)
            }
        }
        XCTFail("Rendered control must be visible: " + label)
    }
    private func stableRenderedPoint(_ label: String, in app: XCUIApplication, edgeInset: CGFloat) -> CGPoint? {
        guard var previous = renderedPoint(label, in: app, edgeInset: edgeInset) else { return nil }
        var stableSince = ProcessInfo.processInfo.systemUptime
        let deadline = stableSince + 4
        while ProcessInfo.processInfo.systemUptime < deadline {
            guard let current = renderedPoint(label, in: app, edgeInset: edgeInset) else { return nil }
            let now = ProcessInfo.processInfo.systemUptime
            if abs(current.x - previous.x) > 1 || abs(current.y - previous.y) > 1 {
                previous = current; stableSince = now
            } else if now - stableSince >= 0.6 { return current }
        }
        return nil
    }
    private func renderedPoint(_ label: String, in app: XCUIApplication, edgeInset: CGFloat) -> CGPoint? {
        guard let image = app.screenshot().image.cgImage else { return nil }
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.recognitionLanguages = ["zh-Hans", "en-US"]
        guard (try? VNImageRequestHandler(cgImage: image).perform([request])) != nil else { return nil }
        let viewport = app.frame.insetBy(dx: 4, dy: edgeInset)
        for result in request.results ?? [] {
            guard result.topCandidates(1).first?.string.replacingOccurrences(of: " ", with: "").contains(label) == true else { continue }
            let bounds = result.boundingBox
            let frame = CGRect(x: bounds.minX * app.frame.width, y: (1 - bounds.maxY) * app.frame.height,
                width: bounds.width * app.frame.width, height: bounds.height * app.frame.height)
            if viewport.contains(frame) { return CGPoint(x: frame.midX, y: frame.midY) }
        }
        return nil
    }
}
