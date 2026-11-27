import XCTest

final class VoiceEffectsDemoUITests: XCTestCase {
    func testCoreMLListeningUsesSelectedBackend() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        let entry = app.descendants(matching: .any)["capability-voice_effects"]
        XCTAssertTrue(entry.waitForExistence(timeout: 10))
        entry.tap()
        let coreML = app.descendants(matching: .any)["voice-backend-coreml"].firstMatch
        XCTAssertTrue(coreML.waitForExistence(timeout: 10))
        coreML.tap()
        for voice in ["voiceA", "voiceB", "voiceC"] {
            app.descendants(matching: .any)["voice-effect-" + voice].firstMatch.tap()
            app.buttons["voice-process"].tap()
            let backend = app.staticTexts["voice-backend-result"]
            XCTAssertTrue(backend.waitForExistence(timeout: 120), app.debugDescription)
            XCTAssertTrue(backend.label.contains("Core ML"), backend.label)
            XCTContext.runActivity(named: voice + " · " + backend.label) { _ in }
            XCTAssertTrue(app.staticTexts["voice-output"].label.contains("22050 Hz"))
            app.buttons["voice-play-result"].tap()
            let stop = app.buttons["停止试听"]
            XCTAssertTrue(stop.waitForExistence(timeout: 5))
            stop.tap()
        }
        app.descendants(matching: .any)["voice-backend-cpu"].firstMatch.tap()
        XCTAssertFalse(app.buttons["voice-play-result"].exists)
        app.buttons["voice-process"].tap()
        let backend = app.staticTexts["voice-backend-result"]
        XCTAssertTrue(backend.waitForExistence(timeout: 60))
        XCTAssertEqual(backend.label, "ONNX Runtime · CPU")
        app.descendants(matching: .any)["voice-effect-high"].firstMatch.tap()
        XCTAssertFalse(coreML.exists)
    }

    func testConvertAndPlayThreeTimbresFromUnifiedDemo() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        let entry = app.descendants(matching: .any)["capability-voice_effects"]
        if !entry.waitForExistence(timeout: 5) { app.buttons["语音"].tap() }
        XCTAssertTrue(entry.waitForExistence(timeout: 10))
        entry.tap()
        for (key, title) in [("voiceA", "音色 A"), ("voiceB", "音色 B"), ("voiceC", "音色 C")] {
            let choice = app.descendants(matching: .any)["voice-effect-" + key].firstMatch
            XCTAssertTrue(choice.waitForExistence(timeout: 15))
            choice.tap()
            XCTAssertFalse(app.buttons["voice-export"].exists)
            let convert = app.buttons["voice-process"]
            XCTAssertEqual(convert.label, "转换为" + title)
            convert.tap()
            let completed = app.staticTexts.matching(NSPredicate(format: "identifier == %@ AND label == %@",
                "voice-phase", title + "转换完成")).firstMatch
            XCTAssertTrue(completed.waitForExistence(timeout: 60), app.debugDescription)
            XCTAssertTrue(app.staticTexts["voice-output"].label.contains("22050 Hz"))
            XCTAssertTrue(app.buttons["voice-export"].isEnabled)
            app.buttons["voice-play-result"].tap()
            let stop = app.buttons["停止试听"]
            XCTAssertTrue(stop.waitForExistence(timeout: 3))
            stop.tap()
        }
    }

    func testImportConvertedSpeechAndExportTimbre() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        let entry = app.descendants(matching: .any)["capability-voice_effects"]
        XCTAssertTrue(entry.waitForExistence(timeout: 10))
        entry.tap()
        let convert = app.buttons["voice-process"]
        let high = app.descendants(matching: .any)["voice-effect-high"].firstMatch
        XCTAssertTrue(high.waitForExistence(timeout: 15))
        high.tap()
        convert.tap()
        let export = app.buttons["voice-export"]
        XCTAssertTrue(export.waitForExistence(timeout: 40))
        export.tap()
        let save = app.buttons["DOCPicker.actionButton"]
        XCTAssertTrue(save.waitForExistence(timeout: 10))
        let pickerBar = app.navigationBars["FullDocumentManagerViewControllerNavigationBar"]
        let localPredicate = NSPredicate(format: "label BEGINSWITH[c] %@ OR label BEGINSWITH %@ OR label BEGINSWITH %@",
            "On My iPhone", "我的 iPhone", "我的iPhone")
        let localTitle = pickerBar.descendants(matching: .any).matching(localPredicate).firstMatch
        let back = pickerBar.buttons["BackButton"]
        let location = app.cells.matching(localPredicate).firstMatch
        for _ in 0..<28 {
            if localTitle.exists { break }
            if location.exists { location.tap(); break }
            if back.exists { back.tap() } else { break }
        }
        XCTAssertTrue(localTitle.waitForExistence(timeout: 4), "local Files folder")
        let name = "ODAI-Speech-" + UUID().uuidString.prefix(8)
        let field = app.textFields["DOCPicker.filenameTextField"]
        XCTAssertTrue(field.exists)
        field.tap()
        field.typeKey("a", modifierFlags: .command)
        field.typeText(name)
        save.tap()
        XCTAssertTrue(save.waitForNonExistence(timeout: 10))
        let importer = app.buttons["voice-import"]
        XCTAssertTrue(importer.waitForExistence(timeout: 15))
        importer.tap()
        let file = app.cells.matching(NSPredicate(format: "label BEGINSWITH %@", name)).firstMatch
        if !file.waitForExistence(timeout: 3) {
            let recents = app.buttons.matching(NSPredicate(format: "label IN %@", ["Recents", "最近项目", "最近項目"])).firstMatch
            if recents.exists { recents.tap() }
        }
        XCTAssertTrue(file.waitForExistence(timeout: 12))
        file.tap()
        let input = app.staticTexts.matching(NSPredicate(format: "identifier == %@ AND label BEGINSWITH %@", "voice-input", name)).firstMatch
        XCTAssertTrue(input.waitForExistence(timeout: 12))
        for (key, label) in [("voiceA", "音色 A"), ("voiceB", "音色 B")] {
            app.descendants(matching: .any)["voice-effect-" + key].firstMatch.tap()
            convert.tap()
            XCTAssertTrue(export.waitForExistence(timeout: 60))
            XCTAssertTrue(app.staticTexts["voice-output"].label.contains("22050 Hz"))
            app.buttons["voice-play-result"].tap()
            let stop = app.buttons["停止试听"]
            XCTAssertTrue(stop.waitForExistence(timeout: 3))
            stop.tap()
            export.tap()
            XCTAssertTrue(save.waitForExistence(timeout: 10))
            XCTAssertTrue(localTitle.exists)
            save.tap()
            XCTAssertTrue(save.waitForNonExistence(timeout: 10))
            XCTAssertTrue(convert.waitForExistence(timeout: 15))
            XCTAssertEqual(convert.label, "转换为" + label)
        }
    }

    func testPageOpensAndReturnsWithoutStartingMicrophone() {
        continueAfterFailure = false
        let app = XCUIApplication(); app.launchArguments = ["--smoke-blank"]; app.launch()
        let entry = app.descendants(matching: .any)["capability-voice_effects"]
        XCTAssertTrue(entry.waitForExistence(timeout:15)); entry.tap()
        let importer = app.buttons["voice-import"]
        XCTAssertTrue(importer.waitForExistence(timeout:15)); XCTAssertTrue(importer.isEnabled)
        XCTAssertTrue(app.buttons["voice-record"].exists)
        XCTAssertTrue(app.buttons["voice-process"].isEnabled)
        XCTAssertEqual(app.staticTexts["voice-input"].label, "内置普通话示例")
        XCTAssertFalse(app.buttons["voice-stop-record"].exists)
        let back = app.buttons["unified-back"]
        let title = app.staticTexts["离线变声"].firstMatch
        XCTAssertGreaterThanOrEqual(back.frame.width, 83)
        XCTAssertGreaterThanOrEqual(back.frame.height, 51)
        XCTAssertLessThanOrEqual(back.frame.maxX, title.frame.minX)
        XCTAssertEqual(title.frame.midX, app.frame.midX, accuracy: 2)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "离线变声-返回按钮"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        back.tap()
        XCTAssertTrue(entry.waitForExistence(timeout:10))
    }

    func testLiveVoiceRoleChipsDoNotStartMicrophoneAndPreserveFileOutput() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--smoke-blank"]
        app.launch()
        let entry = app.descendants(matching: .any)["capability-voice_effects"]
        XCTAssertTrue(entry.waitForExistence(timeout: 15), app.debugDescription)
        entry.tap()
        XCTAssertTrue(app.buttons["voice-import"].waitForExistence(timeout: 15), app.debugDescription)

        // 先用内置的非 ONNX 预设转换一次，作为切换后必须保留的既有输出。
        let convert = app.buttons["voice-process"]
        XCTAssertTrue(convert.waitForExistence(timeout: 15), app.debugDescription)
        convert.tap()
        let output = app.staticTexts["voice-output"]
        XCTAssertTrue(output.waitForExistence(timeout: 60), app.debugDescription)
        XCTAssertFalse(output.label.isEmpty, app.debugDescription)
        let convertedLabel = output.label
        let inputLabel = app.staticTexts["voice-input"].label
        XCTAssertFalse(inputLabel.isEmpty, app.debugDescription)

        // 进入实时模式，逐个点选角色芯片；全程不按通话按钮，不申请麦克风。
        let live = app.descendants(matching: .any)["voice-mode-live"].firstMatch
        XCTAssertTrue(live.waitForExistence(timeout: 10), app.debugDescription)
        live.tap()
        let press = app.descendants(matching: .any)["voice-live-press"].firstMatch
        XCTAssertTrue(press.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(press.isEnabled, app.debugDescription)
        XCTAssertFalse(app.buttons["voice-stop-record"].exists, app.debugDescription)
        for role in ["UNCLE", "GIRL", "ANNOUNCER"] {
            let chip = app.descendants(matching: .any)["voice-live-role-" + role].firstMatch
            XCTAssertTrue(chip.waitForExistence(timeout: 10), role + " " + app.debugDescription)
            chip.tap()
            XCTAssertTrue(press.exists, app.debugDescription)
            XCTAssertFalse(app.buttons["voice-stop-record"].exists, app.debugDescription)
        }

        // 回到文件模式，输入选择与既有输出不得被实时模式切换清掉。
        let file = app.descendants(matching: .any)["voice-mode-file"].firstMatch
        XCTAssertTrue(file.waitForExistence(timeout: 10), app.debugDescription)
        file.tap()
        XCTAssertEqual(app.staticTexts["voice-input"].label, inputLabel, app.debugDescription)
        XCTAssertTrue(app.staticTexts["voice-output"].waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertEqual(app.staticTexts["voice-output"].label, convertedLabel, app.debugDescription)
        XCTAssertFalse(app.buttons["voice-stop-record"].exists, app.debugDescription)
    }
}
