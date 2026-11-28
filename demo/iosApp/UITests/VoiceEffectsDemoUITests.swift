import XCTest

final class VoiceEffectsDemoUITests: XCTestCase {

    /// 选自身应用内面积最小的可见 ScrollView（嵌入的 CMP），只滚动这个容器。
    private func innerScroller(_ app: XCUIApplication) -> XCUIElement? {
        app.scrollViews.allElementsBoundByIndex
            .filter { $0.exists && $0.frame.width > 0 && $0.frame.height > 0 &&
                      !$0.frame.intersection(app.frame).isEmpty }
            .min { $0.frame.width * $0.frame.height < $1.frame.width * $1.frame.height }
    }

    /// 静态标签/波形只要求非空且完整落在 viewport 内；按钮才额外要求 isHittable。
    private func reveal(_ element: XCUIElement, app: XCUIApplication) -> Bool {
        // 异步状态（如播放后的停止按钮）刚出现时 frame 尚未稳定，先给一帧时间。
        Thread.sleep(forTimeInterval: 0.1)
        let container = innerScroller(app)

        func ready(_ e: XCUIElement) -> Bool {
            guard e.exists else { return false }
            let f = e.frame
            guard f.width > 0, f.height > 0 else { return false }
            if let c = container {
                // 实际 viewport 是容器与 app.frame 的交集。
                let viewport = c.frame.intersection(app.frame)
                guard f.minX >= viewport.minX - 1, f.maxX <= viewport.maxX + 1,
                      f.minY >= viewport.minY - 1, f.maxY <= viewport.maxY + 1 else { return false }
            }
            if e.elementType == .button { return e.isHittable }
            return true
        }

        // unified-back 头部在嵌入滚动视图之外（在其上方），套用内嵌 viewport 约束永远为 false。
        // 直接要求整个 frame 落在 app.frame 内且可点击，不滚动。
        if element.exists && element.identifier == "unified-back" {
            guard element.exists else { return false }
            let f = element.frame
            guard f.width > 0, f.height > 0 else { return false }
            guard f.minX >= app.frame.minX - 1, f.maxX <= app.frame.maxX + 1,
                  f.minY >= app.frame.minY - 1, f.maxY <= app.frame.maxY + 1 else { return false }
            return element.isHittable
        }

        func swipe(_ up: Bool) {
            if let c = container, c.exists {
                // Apple 公开 API：在容器内用 press-thenDragTo 滚动，避免 swipeUp 默认兼容差异。
                let start = c.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: up ? 0.8 : 0.2))
                let end = c.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: up ? 0.2 : 0.8))
                start.press(forDuration: 0.05, thenDragTo: end)
            } else if up {
                app.swipeUp()
            } else {
                app.swipeDown()
            }
        }

        if ready(element) { return true }

        // 已知 frame：按目标相对 viewport 的位置定向滑动；部分可见（越过边界）也算，不盲目滚到顶。
        if let c = container, element.exists {
            let viewport = c.frame.intersection(app.frame)
            let f = element.frame
            if f.height > 0 && f.maxY > viewport.maxY {
                for _ in 0..<8 { if ready(element) { return true }; swipe(true) }
                return ready(element)
            }
            if f.height > 0 && f.minY < viewport.minY {
                for _ in 0..<8 { if ready(element) { return true }; swipe(false) }
                return ready(element)
            }
        }
        // 未知 frame：有界双向兜底。
        for _ in 0..<6 { if ready(element) { return true }; swipe(true) }
        for _ in 0..<12 { if ready(element) { return true }; swipe(false) }
        return ready(element)
    }

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
            XCTAssertTrue(reveal(choice, app: app), app.debugDescription)
            XCTAssertTrue(choice.waitForExistence(timeout: 15))
            choice.tap()
            XCTAssertFalse(app.buttons["voice-export"].exists)
            let convert = app.buttons["voice-process"]
            XCTAssertTrue(reveal(convert, app: app), app.debugDescription)
            XCTAssertEqual(convert.label, "转换为" + title)
            convert.tap()
            // 先按已知 identifier 等 voice-phase，再轮询 label 到目标文案；
            // 不对尚未出现的完成阶段做 18 次盲目滑动。保留 120s 转换预算。
            let phaseText = app.staticTexts["voice-phase"].firstMatch
            let targetPhase = title + "转换完成"
            let deadline = Date().addingTimeInterval(120)
            while Date() < deadline {
                if phaseText.exists && phaseText.label == targetPhase { break }
                Thread.sleep(forTimeInterval: 0.25)
            }
            XCTAssertTrue(phaseText.waitForExistence(timeout: 5), app.debugDescription)
            XCTAssertEqual(phaseText.label, targetPhase, phaseText.label)
            XCTAssertTrue(reveal(phaseText, app: app), app.debugDescription)
            let output = app.staticTexts["voice-output"]
            XCTAssertTrue(reveal(output, app: app), app.debugDescription)
            XCTAssertTrue(output.label.contains("22050 Hz"))
            let export = app.buttons["voice-export"]
            XCTAssertTrue(reveal(export, app: app), app.debugDescription)
            XCTAssertTrue(export.isEnabled)
            let play = app.buttons["voice-play-result"]
            XCTAssertTrue(reveal(play, app: app), app.debugDescription)
            play.tap()
            Thread.sleep(forTimeInterval: 0.4)
            let stop = app.buttons["停止试听"].firstMatch
            XCTAssertTrue(reveal(stop, app: app), app.debugDescription)
            XCTAssertTrue(stop.waitForExistence(timeout: 3), app.debugDescription)
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

        // 先用内置示例转换一次，作为切换后必须保留的既有输出。
        let convert = app.buttons["voice-process"]
        XCTAssertTrue(reveal(convert, app: app), app.debugDescription)
        XCTAssertTrue(convert.waitForExistence(timeout: 15), app.debugDescription)
        convert.tap()
        let output = app.staticTexts["voice-output"]
        XCTAssertTrue(reveal(output, app: app), app.debugDescription)
        XCTAssertTrue(output.waitForExistence(timeout: 60), app.debugDescription)
        XCTAssertFalse(output.label.isEmpty, app.debugDescription)
        let convertedLabel = output.label
        let input = app.staticTexts["voice-input"]
        XCTAssertTrue(reveal(input, app: app), app.debugDescription)
        let inputLabel = input.label
        XCTAssertFalse(inputLabel.isEmpty, app.debugDescription)

        // 进入实时模式，逐个点选角色芯片；全程不按通话按钮，不申请麦克风。
        if let scroller = innerScroller(app), scroller.exists {
            for _ in 0..<4 {
                let top = scroller.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
                let bottom = scroller.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8))
                top.press(forDuration: 0.05, thenDragTo: bottom)
            }
        }
        Thread.sleep(forTimeInterval: 0.25)
        let live = app.descendants(matching: .any)["voice-mode-live"].firstMatch
        XCTAssertTrue(reveal(live, app: app), app.debugDescription)
        XCTAssertTrue(live.waitForExistence(timeout: 10), app.debugDescription)
        live.tap()
        let press = app.descendants(matching: .any)["voice-live-press"].firstMatch
        XCTAssertTrue(reveal(press, app: app), app.debugDescription)
        XCTAssertTrue(press.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(press.isEnabled, app.debugDescription)
        XCTAssertFalse(app.buttons["voice-stop-record"].exists, app.debugDescription)
        for role in ["UNCLE", "GIRL", "ANNOUNCER"] {
            let chip = app.descendants(matching: .any)["voice-live-role-" + role].firstMatch
            XCTAssertTrue(reveal(chip, app: app), role + " " + app.debugDescription)
            XCTAssertTrue(chip.waitForExistence(timeout: 10), role + " " + app.debugDescription)
            chip.tap()
            XCTAssertTrue(press.exists, app.debugDescription)
            XCTAssertFalse(app.buttons["voice-stop-record"].exists, app.debugDescription)
        }

        // 回到文件模式，输入选择与既有输出不得被实时模式切换清掉。
        if let scroller = innerScroller(app), scroller.exists {
            for _ in 0..<4 {
                let top = scroller.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
                let bottom = scroller.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8))
                top.press(forDuration: 0.05, thenDragTo: bottom)
            }
        }
        Thread.sleep(forTimeInterval: 0.25)
        let file = app.descendants(matching: .any)["voice-mode-file"].firstMatch
        XCTAssertTrue(reveal(file, app: app), app.debugDescription)
        XCTAssertTrue(file.waitForExistence(timeout: 10), app.debugDescription)
        file.tap()
        let inputAfter = app.staticTexts["voice-input"]
        XCTAssertTrue(reveal(inputAfter, app: app), app.debugDescription)
        XCTAssertEqual(inputAfter.label, inputLabel, app.debugDescription)
        let outputAfter = app.staticTexts["voice-output"]
        XCTAssertTrue(reveal(outputAfter, app: app), app.debugDescription)
        XCTAssertTrue(outputAfter.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertEqual(outputAfter.label, convertedLabel, app.debugDescription)
        XCTAssertFalse(app.buttons["voice-stop-record"].exists, app.debugDescription)
    }

    func testReferenceVoiceConvertsSampleAndClearsInputs() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--smoke-blank"]
        app.launch()

        func attach(_ name: String) {
            let attachment = XCTAttachment(screenshot: app.screenshot())
            attachment.name = name
            attachment.lifetime = .keepAlways
            self.add(attachment)
        }

        let entry = app.descendants(matching: .any)["capability-voice_effects"]
        XCTAssertTrue(entry.waitForExistence(timeout: 15), app.debugDescription)
        entry.tap()

        let submode = app.descendants(matching: .any)["voice-submode-reference"].firstMatch
        XCTAssertTrue(reveal(submode, app: app), app.debugDescription)
        submode.tap()

        let example = app.descendants(matching: .any)["voice-reference-example"].firstMatch
        XCTAssertTrue(reveal(example, app: app), app.debugDescription)
        example.tap()

        // 等样例真正加载完（process 可见且 enabled），再读取 isEnabled。
        let process = app.buttons["voice-reference-process"].firstMatch
        XCTAssertTrue(reveal(process, app: app), app.debugDescription)
        let loaded = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == true AND enabled == true"),
                                               object: process)
        XCTAssertEqual(XCTWaiter().wait(for: [loaded], timeout: 30), .completed, app.debugDescription)

        // 行内按钮必须先全部可见，再无条件比较尺寸。
        let row = ["voice-reference-import", "voice-reference-record", "voice-reference-clear"].map { app.buttons[$0].firstMatch }
        for button in row { XCTAssertTrue(reveal(button, app: app), app.debugDescription) }
        XCTAssertTrue(row.allSatisfy({ $0.exists && $0.isHittable }), app.debugDescription)
        let f = row.map { $0.frame }
        XCTAssertEqual(f[0].width, f[1].width, accuracy: 1)
        XCTAssertEqual(f[1].width, f[2].width, accuracy: 1)
        XCTAssertEqual(f[0].height, f[1].height, accuracy: 1)
        XCTAssertEqual(f[1].height, f[2].height, accuracy: 1)
        for tag in ["voice-reference-import-wave", "voice-content-import-wave"] {
            let wave = app.descendants(matching: .any)[tag].firstMatch
            XCTAssertTrue(reveal(wave, app: app), app.debugDescription)
            XCTAssertFalse(wave.label.isEmpty, tag)
        }
        attach("reference-sample-page")

        // 看过波形后 process 又被挤出屏幕：再次 reveal 再点。
        XCTAssertTrue(reveal(process, app: app), app.debugDescription)
        process.tap()

        // 先按已知 identifier 等 voice-phase 出现并轮询 label 到完成，
        // 不对尚未存在的完成文案做有界 18 次滑动。
        let phase = app.staticTexts["voice-phase"].firstMatch
        let deadline = Date().addingTimeInterval(120)
        while Date() < deadline {
            if phase.exists && phase.label.contains("完成") { break }
            Thread.sleep(forTimeInterval: 0.25)
        }
        XCTAssertTrue(phase.waitForExistence(timeout: 5), app.debugDescription)
        XCTAssertTrue(phase.label.contains("完成"), phase.label)
        XCTAssertTrue(reveal(phase, app: app), app.debugDescription)
        let result = app.staticTexts["voice-reference-result"].firstMatch
        XCTAssertTrue(reveal(result, app: app), app.debugDescription)
        XCTAssertTrue(result.label.contains("22050 Hz"), result.label)
        attach("converted-page")

        let play = app.buttons["voice-play-result"].firstMatch
        XCTAssertTrue(reveal(play, app: app), app.debugDescription)
        play.tap()
        Thread.sleep(forTimeInterval: 0.4)
        let stop = app.buttons["停止试听"].firstMatch
        XCTAssertTrue(reveal(stop, app: app), app.debugDescription)
        XCTAssertTrue(stop.waitForExistence(timeout: 5), app.debugDescription)
        stop.tap()

        // 真实点击波形中心（50%），断言播放状态而不仅是标签。
        let wave = app.descendants(matching: .any)["voice-compare-output"].firstMatch
        XCTAssertTrue(reveal(wave, app: app), app.debugDescription)
        let wf = wave.frame
        XCTAssertGreaterThan(wf.width, 0)
        XCTAssertGreaterThan(wf.height, 0)
        wave.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        Thread.sleep(forTimeInterval: 0.4)
        let seekingStop = app.buttons["停止试听"].firstMatch
        XCTAssertTrue(reveal(seekingStop, app: app), app.debugDescription)
        XCTAssertTrue(seekingStop.waitForExistence(timeout: 5), app.debugDescription)
        seekingStop.tap()

        let export = app.buttons["voice-export"].firstMatch
        XCTAssertTrue(reveal(export, app: app), app.debugDescription)
        XCTAssertTrue(export.isEnabled, app.debugDescription)

        // 点击清空前先把 innerScroller 滚回顶部，避免内容变化后 clear 的幽灵 frame。
        // 复用本方法已有的 4 次有界向下中心拖拽（已验证）。
        if let scroller = innerScroller(app), scroller.exists {
            for _ in 0..<4 {
                let top = scroller.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
                let bottom = scroller.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8))
                top.press(forDuration: 0.05, thenDragTo: bottom)
            }
        }
        Thread.sleep(forTimeInterval: 0.25)

        // 滚动后重新查询 clear，要求 enabled、hittable，点击前后各留截图。
        let clear = app.buttons["voice-reference-clear"].firstMatch
        XCTAssertTrue(reveal(clear, app: app), app.debugDescription)
        XCTAssertTrue(clear.exists && clear.isEnabled && clear.isHittable, app.debugDescription)
        attach("before-clear")
        clear.tap()

        // 清空后引用名回到占位文案（VoiceInputCard: name ?: "请选择音频"）。
        let clearedName = app.descendants(matching: .any)["voice-reference-import-name"].firstMatch
        let nameCleared = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", "请选择音频"),
                                                    object: clearedName)
        XCTAssertEqual(XCTWaiter().wait(for: [nameCleared], timeout: 30), .completed, app.debugDescription)
        attach("after-clear")

        XCTAssertTrue(reveal(process, app: app), app.debugDescription)
        XCTAssertFalse(process.isEnabled, app.debugDescription)
        XCTAssertFalse(result.exists, app.debugDescription)

        // 清空后内容缩短，旧（幽灵）voice-reference-example frame 可能仍在缓存里被 reveal 信任。
        // 先把嵌入的 innerScroller 滚到顶部：4 次有界向下中心拖拽（覆盖本页 <3 屏），
        // 头部保持固定，不用应用边缘或 Home 手势。
        if let scroller = innerScroller(app), scroller.exists {
            for _ in 0..<4 {
                let top = scroller.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
                let bottom = scroller.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8))
                top.press(forDuration: 0.05, thenDragTo: bottom)
            }
        }
        Thread.sleep(forTimeInterval: 0.25)

        // 清空后重新加载样例并等 process enabled，使离开返回真正覆盖清理逻辑。
        let exampleAgain = app.descendants(matching: .any)["voice-reference-example"].firstMatch
        XCTAssertTrue(reveal(exampleAgain, app: app), app.debugDescription)
        exampleAgain.tap()

        // 滚到 process 之前先确认样例名已更新为内置示例。
        let referenceName = app.descendants(matching: .any)["voice-reference-import-name"].firstMatch
        XCTAssertTrue(reveal(referenceName, app: app), app.debugDescription)
        let nameReady = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", "内置普通话示例"),
                                                  object: referenceName)
        XCTAssertEqual(XCTWaiter().wait(for: [nameReady], timeout: 30), .completed, app.debugDescription)
        XCTAssertEqual(referenceName.label, "内置普通话示例", referenceName.label)

        XCTAssertTrue(reveal(process, app: app), app.debugDescription)
        let reloaded = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == true AND enabled == true"),
                                                 object: process)
        XCTAssertEqual(XCTWaiter().wait(for: [reloaded], timeout: 30), .completed, app.debugDescription)

        // 离开再返回：引用输入必须被清空（覆盖 leaveEmbeddedPage 修复）。
        let back = app.buttons["unified-back"].firstMatch
        XCTAssertTrue(reveal(back, app: app), app.debugDescription)
        back.tap()
        XCTAssertTrue(entry.waitForExistence(timeout: 10), app.debugDescription)
        entry.tap()
        let submodeAgain = app.descendants(matching: .any)["voice-submode-reference"].firstMatch
        XCTAssertTrue(reveal(submodeAgain, app: app), app.debugDescription)
        submodeAgain.tap()
        XCTAssertTrue(reveal(process, app: app), app.debugDescription)
        XCTAssertFalse(process.isEnabled, app.debugDescription)
        XCTAssertFalse(app.staticTexts["voice-reference-result"].exists, app.debugDescription)

        // 固定与实时入口确实可达（不打开麦克风）。
        let fixed = app.descendants(matching: .any)["voice-submode-fixed"].firstMatch
        XCTAssertTrue(reveal(fixed, app: app), app.debugDescription)
        fixed.tap()
        let live = app.descendants(matching: .any)["voice-mode-live"].firstMatch
        XCTAssertTrue(reveal(live, app: app), app.debugDescription)
        live.tap()
    }
}
