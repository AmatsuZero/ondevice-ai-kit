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
        let cancel = app.descendants(matching: .any).matching(
            NSPredicate(format: "label == '取消' OR label == 'Cancel'")).firstMatch
        XCTAssertTrue(cancel.waitForExistence(timeout: 10)); cancel.tap()
        XCTAssertTrue(app.staticTexts["speech-audio-name"].exists)
        XCTAssertFalse(app.buttons["speech-recognize-file"].isEnabled)
    }

    func testMultimodalImageFixturePickerCancellationAndClear() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["ODAI_MULTIMODAL_FILE_UI_TEST"] == "1",
            "Requires the fixed public PNG in this Demo's Files folder")
        let app = XCUIApplication(); app.launch()
        let tab = app.buttons["multimodal-tab"]
        XCTAssertTrue(tab.waitForExistence(timeout: 15), "Multimodal tab must be present")
        tab.tap()
        selectMultimodalFixture(in: app)
        let selected = app.staticTexts["multimodal-input"]
        let imported = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label == 'fixture' OR label == 'fixture.png'"), object: selected)
        XCTAssertEqual(XCTWaiter.wait(for: [imported], timeout: 10), .completed)
        let selectedLabel = selected.label
        XCTAssertTrue(selectedLabel == "fixture" || selectedLabel == "fixture.png")

        tapMultimodalSelect(in: app)
        tapMultimodalPickerCancel(in: app)
        XCTAssertEqual(selected.label, selectedLabel, "Cancelling the picker must preserve the selected input")

        let clear = app.buttons["multimodal-clear-input"]
        XCTAssertTrue(clear.waitForExistence(timeout: 10))
        tapMultimodalClear(in: app)
        let cleared = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label == %@", "尚未选择输入"), object: selected)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleared], timeout: 10), .completed,
            "Clearing must release the selected input")
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

    func testVideoFixtureSuccessPlaybackSharingAndCleanup() throws {
        try requireVideoFixture()
        let app = XCUIApplication(); app.launch()
        openVideoTab(app)
        selectVideoFixture(in: app)
        let selected = app.staticTexts["video-input"]
        XCTAssertEqual(selected.label, "odai-video-ui-fixture.mp4")
        tapVideoSelect(in: app)
        let cancel = app.buttons.matching(NSPredicate(format: "label == '取消' OR label == 'Cancel'")).firstMatch
        XCTAssertTrue(cancel.waitForExistence(timeout: 10)); cancel.tap()
        XCTAssertEqual(selected.label, "odai-video-ui-fixture.mp4")
        tapVideoProcess(in: app)
        let output = app.staticTexts["video-output"]
        XCTAssertTrue(output.waitForExistence(timeout: 90), "The real Video Demo bridge must deliver output")
        XCTAssertTrue(output.label.contains("1280×720"))
        XCTAssertTrue(output.label.contains("48 帧"))
        XCTAssertTrue(output.label.contains("音轨 1"))
        tapRenderedControl("分享结果", in: app)
        dismissShareSheet(in: app)
        tapRenderedControl("播放结果", in: app)
        let closePlayer = app.buttons.matching(NSPredicate(format: "label == '完成' OR label == 'Done' OR label == '关闭' OR label == 'Close'")).firstMatch
        XCTAssertTrue(closePlayer.waitForExistence(timeout: 10)); closePlayer.tap()
        tapRenderedControl("清理结果", in: app)
        XCTAssertFalse(output.exists)
        XCTAssertEqual(selected.label, "odai-video-ui-fixture.mp4")
    }

    func testVideoFixtureCancellationReentryAndBackground() throws {
        try requireVideoFixture()
        let app = XCUIApplication(); app.launch()
        openVideoTab(app)
        selectVideoFixture(in: app)
        tapVideoProcess(in: app)
        tapVideoCancel(in: app)
        assertVideoCancelled(app)
        tapVideoProcess(in: app)
        XCTAssertTrue(app.staticTexts["video-output"].waitForExistence(timeout: 90), "Same input must reenter after cancellation drain")
        tapVideoProcess(in: app)
        XCTAssertTrue(app.buttons["video-cancel"].exists)
        XCUIDevice.shared.press(.home); app.activate()
        assertVideoCancelled(app)
        XCTAssertFalse(app.staticTexts["video-output"].exists)
    }

    private func requireVideoFixture() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["ODAI_VIDEO_FILE_UI_TEST"] == "1",
            "Requires the fixed public video + synthetic AAC fixture; see video-bridge-demo.md")
    }
    private func openVideoTab(_ app: XCUIApplication) {
        let tab = app.buttons["video-tab"]
        XCTAssertTrue(tab.waitForExistence(timeout: 15)); tab.tap()
    }
    private func tapVideoSelect(in app: XCUIApplication) {
        let select = app.buttons["video-select"]
        XCTAssertTrue(select.waitForExistence(timeout: 15), "Video picker control must be present")
        XCTAssertTrue(select.isEnabled, "Video picker control must be enabled")
        let scroll = app.scrollViews.firstMatch
        for _ in 0..<8 where !select.isHittable {
            scroll.swipeUp()
        }
        XCTAssertTrue(select.isHittable, "Video picker control must be hittable after scrolling")
        select.tap()
    }

    private func tapVideoProcess(in app: XCUIApplication) {
        let process = app.buttons["video-process"]
        XCTAssertTrue(process.waitForExistence(timeout: 15), "Video process control must be present")
        let scroll = app.scrollViews.firstMatch
        for _ in 0..<8 where !process.isHittable {
            scroll.swipeUp()
        }
        XCTAssertTrue(process.isHittable, "Video process control must be hittable after scrolling")
        process.tap()
    }

    private func tapMultimodalSelect(in app: XCUIApplication) {
        let select = app.buttons["multimodal-select-input"]
        XCTAssertTrue(select.waitForExistence(timeout: 15), "Multimodal picker control must be present")
        let scroll = app.scrollViews.firstMatch
        for _ in 0..<8 where !select.isHittable { scroll.swipeUp() }
        XCTAssertTrue(select.isHittable, "Multimodal picker control must be hittable after scrolling")
        // Compose's accessibility node can retain a stale frame after the tab/scroll
        // transition. Use the rendered label's current frame for the actual tap.
        tapRenderedControl("选择图片", in: app)
    }

    private func tapMultimodalClear(in app: XCUIApplication) {
        let clear = app.buttons["multimodal-clear-input"]
        let clearText = app.staticTexts["清除输入"]
        let scroll = app.scrollViews.firstMatch
        for _ in 0..<16 {
            if clear.exists {
                if clear.isHittable {
                    clear.tap()
                    return
                }
                let frame = clear.frame
                if frame.width > 0, frame.height > 0, app.frame.intersects(frame) {
                    clear.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
                    return
                }
            }
            if clearText.exists {
                if clearText.isHittable {
                    clearText.tap()
                    return
                }
            }
            scroll.swipeUp()
        }
        tapRenderedControl("清除输入", in: app)
    }

    private func tapMultimodalPickerCancel(in app: XCUIApplication) {
        let predicate = NSPredicate(format: "label == '取消' OR label == 'Cancel'")
        let button = app.buttons.matching(predicate).firstMatch
        if button.waitForExistence(timeout: 10) {
            if button.isHittable {
                button.tap()
            } else {
                button.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            }
            finishMultimodalPickerCancellation(in: app)
            return
        }
        let candidates = app.descendants(matching: .any).matching(predicate)
        XCTAssertTrue(candidates.firstMatch.waitForExistence(timeout: 10), "Second picker must expose a cancel control")
        for index in 0..<candidates.count {
            let candidate = candidates.element(boundBy: index)
            if candidate.isHittable {
                candidate.tap()
                finishMultimodalPickerCancellation(in: app)
                return
            }
            let frame = candidate.frame
            if frame.width > 0, frame.height > 0, app.frame.intersects(frame) {
                // Files exposes its visible cancel icon as an `Other` element
                // with a false hittable flag. The valid AX frame is still the
                // system-provided target, so tap that element rather than a
                // fixed screen coordinate.
                candidate.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
                finishMultimodalPickerCancellation(in: app)
                return
            }
        }
        // The Files extension can expose a stale AX node with {-1,-1} while the
        // visible cancel control is in a separate picker window. OCR remains
        // content-based and avoids hard-coded device coordinates.
        for label in ["取消", "Cancel"] where renderedPoint(label, in: app, edgeInset: 4) != nil {
            tapRenderedControl(label, in: app, edgeInset: 4)
            return
        }
        XCTFail("Second picker cancel control must be hittable")
    }

    private func finishMultimodalPickerCancellation(in app: XCUIApplication) {
        // A valid AX frame can open the Files view-options menu instead of
        // dismissing the picker. Two rendered cancel passes close that menu,
        // then the picker itself when both layers are present.
        for _ in 0..<3 {
            var tapped = false
            for label in ["取消", "Cancel"] where renderedPoint(label, in: app, edgeInset: 4) != nil {
                tapRenderedControl(label, in: app, edgeInset: 4)
                tapped = true
                Thread.sleep(forTimeInterval: 0.5)
                break
            }
            if !tapped { return }
        }
    }

    private func tapVideoCancel(in app: XCUIApplication) {
        let cancel = app.buttons["video-cancel"]
        XCTAssertTrue(cancel.waitForExistence(timeout: 15), "Video cancel control must be present")
        let scroll = app.scrollViews.firstMatch
        for _ in 0..<8 where !cancel.isHittable {
            scroll.swipeUp()
        }
        XCTAssertTrue(cancel.isHittable, "Video cancel control must be hittable after scrolling")
        cancel.tap()
    }

    private func assertVideoCancelled(_ app: XCUIApplication) {
        let cancelled = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == 'CANCELLED'"), object: app.staticTexts["video-phase"])
        XCTAssertEqual(XCTWaiter.wait(for: [cancelled], timeout: 30), .completed)
        XCTAssertFalse(app.buttons["video-cancel"].exists, "Foreground must not restart the video task")
    }
    private func dismissShareSheet(in app: XCUIApplication) {
        let dismiss = app.otherElements["PopoverDismissRegion"]
        XCTAssertTrue(dismiss.waitForExistence(timeout: 10), "Share sheet must present a dismiss region")
        XCTAssertTrue(dismiss.isHittable, "Share sheet dismiss region must be hittable")
        dismiss.tap()
        XCTAssertFalse(app.otherElements["ActivityListView"].exists, "Share sheet must dismiss before playback")
    }
    private func selectVideoFixture(in app: XCUIApplication) {
        tapVideoSelect(in: app)
        let browse = app.descendants(matching: .any).matching(
            NSPredicate(format: "label == '浏览' OR label == 'Browse'")).firstMatch
        if browse.waitForExistence(timeout: 3) { browse.tap() }
        let local = app.descendants(matching: .any).matching(NSPredicate(format: "label == '在我的 iPhone 上' OR label == '我的 iPhone' OR label == '我的iPhone' OR label == 'On My iPhone'")).firstMatch
        if local.waitForExistence(timeout: 3) { local.tap() }
        let folder = app.descendants(matching: .any).matching(NSPredicate(format: "label == 'SpeechDemo'")).firstMatch
        if folder.waitForExistence(timeout: 3) { folder.tap() }
        let file = app.cells.matching(NSPredicate(format: "label BEGINSWITH 'odai-video-ui-fixture'")).firstMatch
        XCTAssertTrue(file.waitForExistence(timeout: 10), "Select only the provisioned public fixture")
        file.tap()
    }

    private func selectMultimodalFixture(in app: XCUIApplication) {
        tapMultimodalSelect(in: app)
        let browse = app.descendants(matching: .any).matching(
            NSPredicate(format: "label == '浏览' OR label == 'Browse'")).firstMatch
        if browse.waitForExistence(timeout: 3) { browse.tap() }
        let local = app.descendants(matching: .any).matching(NSPredicate(format: "label == '在我的 iPhone 上' OR label == '我的 iPhone' OR label == '我的iPhone' OR label == 'On My iPhone'")).firstMatch
        if local.waitForExistence(timeout: 3) { local.tap() }
        let namedFolder = app.descendants(matching: .any).matching(NSPredicate(format: "label == 'SpeechDemoPicker'")).firstMatch
        if namedFolder.waitForExistence(timeout: 5) {
            namedFolder.tap()
        } else {
            let legacyFolder = app.descendants(matching: .any).matching(NSPredicate(format: "label == 'SpeechDemo'")).firstMatch
            if !legacyFolder.waitForExistence(timeout: 10) {
                let visibleLabels = app.descendants(matching: .any).allElementsBoundByIndex
                    .map(\.label)
                    .filter { !$0.isEmpty }
                print("MULTIMODAL_FILES_PICKER_DEBUG labels=\(visibleLabels)")
                XCTFail("A Demo Files container must be visible")
                return
            }
            legacyFolder.tap()
        }
        let file = app.cells.matching(
            NSPredicate(format: "label BEGINSWITH 'fixture' OR identifier BEGINSWITH 'fixture'"))
            .firstMatch
        XCTAssertTrue(file.waitForExistence(timeout: 30), "Select only the provisioned public PNG fixture")
        file.tap()
    }

    private func selectFixture(in app: XCUIApplication) {
        tapRenderedControl("选择音频文件", in: app)
        let browse = app.descendants(matching: .any).matching(
            NSPredicate(format: "label == '浏览' OR label == 'Browse'")).firstMatch
        if browse.waitForExistence(timeout: 3) { browse.tap() }
        let local = app.descendants(matching: .any).matching(NSPredicate(format: "label == '在我的 iPhone 上' OR label == '我的 iPhone' OR label == '我的iPhone' OR label == 'On My iPhone'")).firstMatch
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
        let normalizedLabel = label.replacingOccurrences(of: " ", with: "")
        let candidates = (request.results ?? []).compactMap { result -> (CGRect, Bool)? in
            guard let text = result.topCandidates(1).first?.string else { return nil }
            let normalizedText = text.replacingOccurrences(of: " ", with: "")
            guard normalizedText == normalizedLabel || normalizedText.contains(normalizedLabel) else { return nil }
            let bounds = result.boundingBox
            let frame = CGRect(x: bounds.minX * app.frame.width, y: (1 - bounds.maxY) * app.frame.height,
                width: bounds.width * app.frame.width, height: bounds.height * app.frame.height)
            guard viewport.contains(frame) else { return nil }
            return (frame, normalizedText == normalizedLabel)
        }
        for (frame, exact) in candidates where exact {
            return CGPoint(x: frame.midX, y: frame.midY)
        }
        if let (frame, _) = candidates.first {
            return CGPoint(x: frame.midX, y: frame.midY)
        }
        return nil
    }
}
