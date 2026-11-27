import XCTest

final class W6UnifiedVideoUITests: XCTestCase {
    private let videoCapabilityTag = "capability-video_understanding"
    private let videoSelectTag = "multimodal-select-video"
    private let promptTag = "multimodal-prompt"
    private let generateTag = "multimodal-generate"
    private let outputTag = "multimodal-output"
    private let backendTag = "multimodal-output-backend"
    private let identityTag = "multimodal-identity"
#if targetEnvironment(simulator)
    private let fixturesFolderName = "W6 Test Files"
#else
    private let fixturesFolderName = "W6 Fixtures"
#endif

    func testImportModelFolderAndRestore() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()

        func openModelDetail() {
            tapElement(app.descendants(matching: .any)["nav-模型"], in: app)
            let card = app.descendants(matching: .any)["model-video_understanding"]
            XCTAssertTrue(revealLazyItem(card, in: app, listTag: "models-list"))
            card.tap()
        }

        openModelDetail()
        let prepare = app.buttons["model-prepare-video_understanding"]
        XCTAssertTrue(waitUntilEnabled(prepare, in: app, timeout: 15), "Preparation should be enabled for the empty install")
        prepare.tap()

        let bar = app.navigationBars.firstMatch
        XCTAssertTrue(bar.waitForExistence(timeout: 15), "The system folder picker should open")

        let phoneTitles = ["在我的 iPhone 上", "我的 iPhone", "我的iPhone", "On My iPhone"]
        let knownTitles = phoneTitles + [fixturesFolderName, "W6 Model Import"]

        func atFolder(_ name: String) -> XCUIElement {
            let predicate = NSPredicate(
                format: "identifier BEGINSWITH %@ AND identifier ENDSWITH %@",
                "DOC.browsingRoot ",
                ", Title: \(name)"
            )
            return app.otherElements.matching(predicate).firstMatch
        }

        func folderMenu(_ name: String) -> XCUIElement {
            let labels = ["\(name), 操作菜单", "\(name), Actions Menu"]
            return app.buttons.matching(NSPredicate(format: "label IN %@", labels)).firstMatch
        }

        func folderCell(_ name: String) -> XCUIElement {
            let identifierPredicate = name == fixturesFolderName && fixturesFolderName == "W6 Fixtures"
                ? NSPredicate(format: "identifier IN %@", ["W6 Fixtures, Container", "W6 Fixtures, Folder"])
                : NSPredicate(format: "identifier == %@", "\(name), Folder")
            return app.cells.matching(identifierPredicate).firstMatch
        }

        func knownRoot(_ titles: [String]) -> XCUIElement {
            let titlePredicates = titles.map {
                NSPredicate(format: "identifier ENDSWITH %@", ", Title: \($0)")
            }
            let predicate = NSCompoundPredicate(andPredicateWithSubpredicates: [
                NSPredicate(format: "identifier BEGINSWITH %@", "DOC.browsingRoot "),
                NSCompoundPredicate(orPredicateWithSubpredicates: titlePredicates)
            ])
            return app.otherElements.matching(predicate).firstMatch
        }

        func currentKnownTitle() -> String {
            for name in ["W6 Model Import", fixturesFolderName] where folderMenu(name).exists {
                return name
            }
            let root = knownRoot(knownTitles)
            guard root.exists else { return "" }
            return knownTitles.first { root.identifier.hasSuffix(", Title: \($0)") } ?? ""
        }

        func waitForPickerState(timeout: TimeInterval) -> String {
            let deadline = Date().addingTimeInterval(timeout)
            let phone = app.cells.matching(NSPredicate(format: "label IN %@", phoneTitles)).firstMatch
            let fixtures = folderCell(fixturesFolderName)
            while Date() < deadline {
                let title = currentKnownTitle()
                if !title.isEmpty || phone.isHittable || fixtures.isHittable {
                    return title
                }
                RunLoop.current.run(until: Date().addingTimeInterval(0.1))
            }
            return currentKnownTitle()
        }

        func selectFolder(_ name: String) {
            let folder = folderCell(name)
            XCTAssertTrue(folder.waitForExistence(timeout: 10) && folder.isHittable, "\(name) should be the intended Files folder cell")
            if folder.identifier == "W6 Fixtures, Container" {
                let folderImage = folder.images.firstMatch
                XCTAssertTrue(folderImage.waitForExistence(timeout: 10) && folderImage.isHittable, "\(name) folder icon should be hittable")
                folderImage.tap()
            } else {
                folder.tap()
            }
            XCTAssertTrue(folderMenu(name).exists || atFolder(name).waitForExistence(timeout: 10), "Files should open \(name)")
        }

        let browsingRoot = app.otherElements.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "DOC.browsingRoot ")
        ).firstMatch
        let browse = app.buttons.matching(NSPredicate(format: "label IN %@", ["浏览", "Browse"])).firstMatch
        let initialRootVisible = browsingRoot.waitForExistence(timeout: 5)
        let initialFolderMenuVisible = folderMenu(fixturesFolderName).exists || folderMenu("W6 Model Import").exists
        XCTAssertTrue(initialRootVisible || initialFolderMenuVisible || browse.waitForExistence(timeout: 5), "Files should show a browsing root or Browse navigation")

        var title = currentKnownTitle()
        let phone = app.cells.matching(NSPredicate(format: "label IN %@", phoneTitles)).firstMatch
        let fixtures = folderCell(fixturesFolderName)
        if !knownTitles.contains(title) && !phone.isHittable && !fixtures.isHittable {
            let back = bar.buttons.matching(NSPredicate(format: "label IN %@", ["返回", "Back"])).firstMatch
            if back.exists && back.isHittable {
                back.tap()
                title = waitForPickerState(timeout: 5)
            }
        }

        if !knownTitles.contains(title) && !phone.isHittable && !fixtures.isHittable {
            XCTAssertTrue(browse.waitForExistence(timeout: 5) && browse.isHittable, "Files should provide Browse navigation")
            if browse.isHittable {
                browse.tap()
                title = waitForPickerState(timeout: 10)
            }
        }

        if !knownTitles.contains(title) {
            if fixtures.isHittable {
                selectFolder(fixturesFolderName)
                title = fixturesFolderName
            } else if phone.exists && phone.isHittable {
                phone.tap()
                let phoneRoot = knownRoot(phoneTitles)
                XCTAssertTrue(phoneRoot.waitForExistence(timeout: 10), "Files should open an On My iPhone root")
                title = phoneTitles.first { phoneRoot.identifier.hasSuffix(", Title: \($0)") } ?? ""
            }
        }

        XCTAssertTrue(knownTitles.contains(title), "Files should reach the intended folder or On My iPhone location")
        if phoneTitles.contains(title) {
            selectFolder(fixturesFolderName)
            title = fixturesFolderName
        }
        if title == fixturesFolderName {
            selectFolder("W6 Model Import")
            title = "W6 Model Import"
        }

        XCTAssertEqual(title, "W6 Model Import", "Confirm only the intended folder")
        XCTAssertTrue(folderMenu("W6 Model Import").exists || atFolder("W6 Model Import").exists, "Files should be browsing the exact model folder")
        let selectedFolder = XCTAttachment(string: title)
        selectedFolder.name = "selected-model-folder"
        selectedFolder.lifetime = .keepAlways
        add(selectedFolder)

        let open = bar.buttons["DOCPicker.actionButton"]
        XCTAssertTrue(open.waitForExistence(timeout: 5) && open.isHittable, "Confirm the selected folder in Files")
        open.tap()

        let status = app.staticTexts["model-status-video_understanding"]
        let ready = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label == %@", "模型已就绪，可以选择视频。"),
            object: status
        )
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 150), .completed, "The imported model should become ready")
        let evidence = XCTAttachment(string: status.label)
        evidence.name = "model-ready-status"
        evidence.lifetime = .keepAlways
        add(evidence)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "model-import-ready"
        screenshot.lifetime = .keepAlways
        add(screenshot)

        app.terminate()
        app.launch()
        openModelDetail()
        let restoredStatus = app.staticTexts["model-status-video_understanding"]
        let restoredReady = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label == %@", "模型已就绪，可以选择视频。"),
            object: restoredStatus
        )
        XCTAssertEqual(XCTWaiter.wait(for: [restoredReady], timeout: 150), .completed, "Ready status should restore from the installed ModelStore")

        let back = app.buttons["unified-back"]
        XCTAssertTrue(reveal(back, in: app))
        back.tap()
        tapElement(app.descendants(matching: .any)["nav-探索"], in: app)
        openVideoUnderstanding(in: app)
        let selectVideo = app.buttons[videoSelectTag]
        XCTAssertTrue(reveal(selectVideo, in: app) && selectVideo.isEnabled, "Video selection should be available")
        selectVideo.tap()
        XCTAssertTrue(app.navigationBars.firstMatch.waitForExistence(timeout: 10), "Video selection should open Files")
    }
    func testLeavingDuringGenerationCancelsAndRestoresInput() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        openVideoUnderstanding(in: app)
        let select = app.buttons[videoSelectTag]
        XCTAssertTrue(waitUntilEnabled(select, in: app, timeout: 120))
        selectFixture("w6-yuv-clip-01", using: select, in: app)
        enterPrompt("Write a long numbered list of thirty observations about the colors and their order in this video. Explain each observation in its own sentence.", in: app)
        let generate = app.buttons[generateTag]
        XCTAssertTrue(reveal(generate, in: app)); generate.tap()
        let update = app.staticTexts["multimodal-update"]
        XCTAssertTrue(update.waitForExistence(timeout: 90), "Wait for an actual decoded token before leaving")
        let firstText = update.label
        XCTAssertFalse(firstText.isEmpty)

        tapElement(app.buttons["unified-back"], in: app)
        XCTAssertTrue(app.buttons["实验室"].waitForExistence(timeout: 20))
        openVideoUnderstanding(in: app)

        let phase = app.staticTexts["multimodal-phase"]
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == 'CANCELLED'"), object: phase)], timeout: 20), .completed)
        XCTAssertFalse(app.staticTexts[outputTag].exists)
        XCTAssertTrue(app.staticTexts["multimodal-input"].label.contains("w6-yuv-clip-01"))
        let attachment = XCTAttachment(string: "visible text before leaving: " + firstText)
        attachment.name = "first-visible-text"; attachment.lifetime = .keepAlways; add(attachment)

        enterPrompt("What color is the screen in the first half, and what color is it in the second half? Answer with the two color names in order.", in: app)
        runGeneration(in: app)
        _ = assertResult("red, blue", in: app, screenshotName: "w6-restored-answer")
    }
    func testMissingModelAndPickerCancellation() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()

        tapElement(app.descendants(matching: .any)["nav-模型"], in: app)
        let modelCard = app.descendants(matching: .any)["model-video_understanding"]
        XCTAssertTrue(revealLazyItem(modelCard, in: app, listTag: "models-list"), "Video understanding model entry should be available")
        modelCard.tap()

        let prepare = app.buttons["model-prepare-video_understanding"]
        XCTAssertTrue(reveal(prepare, in: app), "Model preparation should be available")
        XCTAssertTrue(prepare.isEnabled, "Model preparation should be retryable")
        prepare.tap()
        XCTAssertTrue(app.navigationBars.firstMatch.waitForExistence(timeout: 15), "The system folder picker should open")

        let bar = app.navigationBars.firstMatch
        let nav = XCTAttachment(string: bar.debugDescription)
        nav.name = "folder-picker-navigation"; nav.lifetime = .keepAlways; add(nav)
        let cancel = bar.descendants(matching: .any).matching(NSPredicate(format: "label IN %@ OR identifier IN %@", ["关闭", "取消", "Close", "Cancel"], ["Close", "Cancel", "UICloseButton"])).firstMatch
        if cancel.exists && cancel.frame.width > 0 && bar.frame.contains(CGPoint(x: cancel.frame.midX, y: cancel.frame.midY)) {
            cancel.coordinate(withNormalizedOffset: CGVector(dx: 0.5,dy: 0.5)).tap()
        } else {
            let start = bar.coordinate(withNormalizedOffset: CGVector(dx: 0.5,dy: 0.2))
            start.press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5,dy: 0.92)))
        }
        XCTAssertTrue(waitUntilEnabled(prepare, in: app, timeout: 12), "Model preparation should recover after dismissing the real picker")

        let back = app.buttons["unified-back"]
        XCTAssertTrue(reveal(back, in: app), "Model detail should provide navigation back")
        back.tap()
        tapElement(app.descendants(matching: .any)["nav-探索"], in: app)
        openVideoUnderstanding(in: app)

        XCTAssertFalse(app.buttons["multimodal-select-input"].exists, "Video understanding must not expose image selection")
        let generate = app.buttons[generateTag]
        if generate.exists {
            XCTAssertFalse(generate.isEnabled, "Generation must not be available without model weights")
        }
    }

    func testRealMovieAnswer() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        openVideoUnderstanding(in: app)

        let selectVideo = app.buttons[videoSelectTag]
        XCTAssertTrue(waitUntilEnabled(selectVideo, in: app, timeout: 180), "A ready video backend should enable video selection")

        let prompt = "What color is the screen in the first half, and what color is it in the second half? Answer with the two color names in order."
        selectFixture("w6-yuv-clip-01", using: selectVideo, in: app)
        enterPrompt(prompt, in: app)
        runGeneration(in: app)
        let first = assertResult("red, blue", in: app, screenshotName: "w6-clip-01-result")

        XCTAssertTrue(selectVideo.isEnabled, "Video selection should remain available for the second fixture")
        selectFixture("w6-yuv-clip-02", using: selectVideo, in: app)
        enterPrompt(prompt, in: app)
        runGeneration(in: app)
        let second = assertResult("blue, red", in: app, screenshotName: "w6-clip-02-result")

        XCTAssertEqual(second.modelID, first.modelID, "Both clips should use the same model")
        XCTAssertEqual(second.modelRevision, first.modelRevision, "Both clips should use the same model revision")
        XCTAssertEqual(second.runtimeRevision, first.runtimeRevision, "Both clips should use the same runtime revision")
        XCTAssertEqual(second.backendID, first.backendID, "Both clips should use the same backend")
    }

    func testVideoAnswerProcessMemory() throws {
        let mode = ProcessInfo.processInfo.environment["W6_MEASURE_MEMORY"]
        try XCTSkipUnless(mode == "1" || mode == "series", "Set W6_MEASURE_MEMORY=1 or W6_MEASURE_MEMORY=series in the test runner to profile the prepared W6 fixture.")
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        openVideoUnderstanding(in: app)
        let select = app.buttons[videoSelectTag]
        XCTAssertTrue(waitUntilEnabled(select, in: app, timeout: 180))

        let clips: [(name: String, answer: String)] = [
            ("w6-yuv-clip-01", "red, blue"),
            ("w6-yuv-clip-02", "blue, red")
        ]

        let series = mode == "series"
        let recordedCount = series ? 6 : 1
        var iteration = 0
        let options = XCTMeasureOptions()
        options.iterationCount = recordedCount
        options.invocationOptions = [.manuallyStart, .manuallyStop]

        measure(metrics: [XCTMemoryMetric(application: app), XCTClockMetric()], options: options) {
            guard iteration < recordedCount + 1 else {
                XCTFail("Unexpected additional measurement iteration")
                return
            }

            let index = iteration
            let clip = clips[index % clips.count]
            iteration += 1

            func checkThermal(_ stage: String) {
                guard series else { return }
                let state = ProcessInfo.processInfo.thermalState
                let readableState: String
                switch state {
                case .nominal:
                    readableState = "nominal"
                case .fair:
                    readableState = "fair"
                case .serious:
                    readableState = "serious"
                case .critical:
                    readableState = "critical"
                @unknown default:
                    readableState = "unknown"
                }
                XCTContext.runActivity(named: "\(stage) - \(readableState)") { _ in
                    XCTAssertTrue(state == .nominal || state == .fair, "Thermal state \(readableState) is not acceptable for \(stage)")
                }
            }

            checkThermal("before_\(index)")

            self.selectFixture(clip.name, using: select, in: app)
            self.enterPrompt("What color is the screen in the first half, and what color is it in the second half? Answer with the two color names in order.", in: app)

            let generate = app.buttons[generateTag]
            let output = app.staticTexts[outputTag]

            XCTAssertFalse(output.exists)
            XCTAssertTrue(self.reveal(generate, in: app))
            XCTAssertTrue(generate.isEnabled)

            self.startMeasuring()
            generate.tap()
            let appeared = output.waitForExistence(timeout: 180)
            self.stopMeasuring()

            checkThermal("after_\(index)")

            XCTAssertTrue(appeared)
            let sample = index == 0 ? "warmup" : "sample-\(index)"
            let screenshotName = series ? "\(sample)-\(clip.name)" : clip.name
            _ = self.assertResult(clip.answer, in: app, screenshotName: screenshotName)
        }

        XCTAssertEqual(iteration, recordedCount + 1)
    }

    func testCancelAfterVisibleTextAndAskAgain() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        openVideoUnderstanding(in: app)
        let select = app.buttons[videoSelectTag]
        XCTAssertTrue(waitUntilEnabled(select, in: app, timeout: 120))
        selectFixture("w6-yuv-clip-01", using: select, in: app)
        enterPrompt("Write a long numbered list of thirty observations about the colors and their order in this video. Explain each observation in its own sentence.", in: app)
        let generate = app.buttons[generateTag]
        XCTAssertTrue(reveal(generate, in: app)); generate.tap()
        let update = app.staticTexts["multimodal-update"]
        XCTAssertTrue(update.waitForExistence(timeout: 90), "Wait for an actual decoded token before cancellation")
        let firstText = update.label
        XCTAssertFalse(firstText.isEmpty)
        let cancel = app.buttons["multimodal-cancel"]
        XCTAssertTrue(reveal(cancel, in: app)); cancel.tap()
        let phase = app.staticTexts["multimodal-phase"]
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == 'CANCELLED'"), object: phase)], timeout: 20), .completed)
        XCTAssertFalse(app.staticTexts[outputTag].exists)
        let attachment = XCTAttachment(string: "visible text before cancellation: " + firstText)
        attachment.name = "first-visible-text"; attachment.lifetime = .keepAlways; add(attachment)
        enterPrompt("What color is the screen in the first half, and what color is it in the second half? Answer with the two color names in order.", in: app)
        runGeneration(in: app); _ = assertResult("red, blue", in: app, screenshotName: "w6-resumed-answer")
    }

    func testUndecodableMovieReportsInputFailure() {
        continueAfterFailure = false
        let app = XCUIApplication(); app.launch(); openVideoUnderstanding(in: app)
        let select = app.buttons[videoSelectTag]
        XCTAssertTrue(waitUntilEnabled(select, in: app, timeout: 120))
        selectFixture("w6-clip-01", using: select, in: app)
        let generate = app.buttons[generateTag]
        XCTAssertTrue(reveal(generate, in: app)); generate.tap()
        let reason = app.staticTexts["multimodal-failure"]
        XCTAssertTrue(reason.waitForExistence(timeout: 20))
        XCTAssertTrue(reason.label.contains("无法解码该视频")); XCTAssertTrue(select.isEnabled)
    }

    private func openVideoUnderstanding(in app: XCUIApplication) {
        let lab = app.buttons["实验室"]
        XCTAssertTrue(lab.waitForExistence(timeout: 15), "The lab category should be available")
        tapElement(lab, in: app)
        let entry = app.descendants(matching: .any)[videoCapabilityTag]
        XCTAssertTrue(revealLazyItem(entry, in: app, listTag: "explore-list"), "Video understanding should be listed in the lab")
        entry.tap()
    }

    private func selectFixture(_ name: String, using selectButton: XCUIElement, in app: XCUIApplication) {
        XCTAssertTrue(reveal(selectButton, in: app), "Video selection should be visible")
        selectButton.tap()

        let browse = app.descendants(matching: .any).matching(
            NSPredicate(format: "label IN %@", ["浏览", "Browse"])
        ).firstMatch
        if browse.waitForExistence(timeout: 3), browse.isHittable {
            browse.tap()
        }

        let onMyPhone = app.descendants(matching: .any).matching(
            NSPredicate(format: "label IN %@", ["在我的 iPhone 上", "我的 iPhone", "我的iPhone", "On My iPhone"])
        ).firstMatch
        if onMyPhone.waitForExistence(timeout: 4), onMyPhone.isHittable {
            onMyPhone.tap()
        }

        let folderIdentifierPredicate = fixturesFolderName == "W6 Fixtures"
            ? NSPredicate(format: "identifier IN %@", ["W6 Fixtures, Container", "W6 Fixtures, Folder"])
            : NSPredicate(format: "identifier == %@", "\(fixturesFolderName), Folder")
        let fixturesFolder = app.cells.matching(folderIdentifierPredicate).firstMatch
        if fixturesFolder.waitForExistence(timeout: 4), fixturesFolder.isHittable {
            if fixturesFolder.identifier == "W6 Fixtures, Container" {
                let folderImage = fixturesFolder.images.firstMatch
                if folderImage.waitForExistence(timeout: 4), folderImage.isHittable {
                    folderImage.tap()
                }
            } else {
                fixturesFolder.tap()
            }
        }

        let file = fixtureElement(named: name, in: app)
        if !file.waitForExistence(timeout: 5) {
            let search = app.searchFields.firstMatch
            if search.exists, search.isHittable {
                search.tap()
                search.typeText(name)
            }
        }
        XCTAssertTrue(file.waitForExistence(timeout: 15), "Expected fixture \(name) in the system Files picker")
        XCTAssertTrue(file.isHittable, "Expected fixture \(name) to be selectable")
        file.tap()

        let open = app.buttons.matching(NSPredicate(format: "label IN %@", ["打开", "Open"])).firstMatch
        if open.waitForExistence(timeout: 2), open.isHittable {
            open.tap()
        }

        let input = app.staticTexts["multimodal-input"]
        XCTAssertTrue(input.waitForExistence(timeout: 20), "The app should return from the Files picker")
        let selected = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label CONTAINS %@", name), object: input)
        XCTAssertEqual(XCTWaiter.wait(for: [selected], timeout: 15), .completed, "The selected movie must reach the host")
    }

    private func fixtureElement(named name: String, in app: XCUIApplication) -> XCUIElement {
        let filenamePrefix = name + "."
        return app.cells.matching(
            NSPredicate(format: "label BEGINSWITH[c] %@ OR identifier BEGINSWITH[c] %@", name, filenamePrefix)
        ).firstMatch
    }

    private func enterPrompt(_ prompt: String, in app: XCUIApplication) {
        let field = app.descendants(matching: .any)[promptTag]
        XCTAssertTrue(reveal(field, in: app), "Prompt field should be visible")
        field.tap()

        if (field.value as? String) != prompt {
            // Compose keeps its own edit selection. Move to the logical end, then verify deletion in the actual text control.
            for _ in 0..<3 {
                let old = (field.value as? String) ?? ""
                if old.isEmpty || old == "原始 prompt" { break }
                for _ in 0..<(old.count + 1) { field.typeKey(.rightArrow, modifierFlags: []) }
                field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: old.count + 8))
            }
            field.typeText(prompt)
        }
        XCTAssertEqual(field.value as? String, prompt, "The requested prompt should be entered exactly")

        let done = app.keyboards.buttons.matching(NSPredicate(format: "label IN %@", ["Done", "完成", "done"])).firstMatch
        if done.waitForExistence(timeout: 2) {
            done.tap()
        } else {
            field.typeText("\n")
        }
        XCTAssertFalse(app.keyboards.firstMatch.exists, "The keyboard should be hidden before generation")
    }

    private func runGeneration(in app: XCUIApplication) {
        let generate = app.buttons[generateTag]
        XCTAssertTrue(reveal(generate, in: app), "Generation control should be visible")
        XCTAssertTrue(generate.isEnabled, "Generation should be enabled after selecting a video and entering a prompt")
        generate.tap()
        XCTAssertTrue(app.staticTexts[outputTag].waitForExistence(timeout: 180), "The video answer should be rendered")
    }

    private func assertResult(_ expected: String, in app: XCUIApplication, screenshotName: String) -> ResultIdentity {
        let output = app.staticTexts[outputTag]
        XCTAssertTrue(output.waitForExistence(timeout: 30), "An answer should be present")
        XCTAssertEqual(output.label.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(), expected,
                       "The answer should match the clip's color order")

        let backendElement = app.staticTexts[backendTag]
        let identityElement = app.staticTexts[identityTag]
        XCTAssertTrue(backendElement.exists, "The app should report the backend used")
        XCTAssertTrue(identityElement.exists, "The app should report model identity")

        let backendID = backendElement.label.components(separatedBy: "：").last?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        XCTAssertEqual(backendID, "llama.wemm-video-2b")
        XCTAssertTrue(app.staticTexts["multimodal-backends"].label.contains("\(backendID) · 可用"),
                      "The reported backend should be listed as available")

        let fields = identityElement.label.components(separatedBy: .newlines)
        let modelID = value(after: "model=", in: fields)
        let modelRevision = value(after: "modelRevision=", in: fields)
        let runtimeRevision = value(after: "runtime=", in: fields)
        XCTAssertEqual(modelID, "tencent/WeMM-Video-2B-GGUF")
        XCTAssertEqual(modelRevision, "395e99f987a73915273943d4510e23004df7cede")
        XCTAssertFalse(runtimeRevision.isEmpty, "The reported runtime revision should not be empty")

        let attachment = XCTAttachment(image: app.screenshot().image)
        attachment.name = screenshotName
        attachment.lifetime = .keepAlways
        add(attachment)

        return ResultIdentity(modelID: modelID, modelRevision: modelRevision,
                              runtimeRevision: runtimeRevision, backendID: backendID)
    }

    private func value(after prefix: String, in lines: [String]) -> String {
        guard let line = lines.first(where: { $0.hasPrefix(prefix) }) else { return "" }
        return String(line.dropFirst(prefix.count)).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func waitUntilEnabled(_ element: XCUIElement, in app: XCUIApplication, timeout: TimeInterval) -> Bool {
        let expectation = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == true AND isEnabled == true"),
            object: element
        )
        return XCTWaiter.wait(for: [expectation], timeout: timeout) == .completed
    }

    private func tapElement(_ element: XCUIElement, in app: XCUIApplication) {
        XCTAssertTrue(element.waitForExistence(timeout: 15), "Expected control should exist")
        XCTAssertTrue(reveal(element, in: app), "Expected control should be reachable")
        element.tap()
    }

    private func reveal(_ element: XCUIElement, in app: XCUIApplication) -> Bool {
        for _ in 0..<6 {
            if element.exists && element.isHittable { return true }
            guard element.exists else { return false }
            let frame = element.frame
            if frame.maxY > app.frame.maxY - 40 {
                scroll(in: app, upward: true)
            } else if frame.minY < app.frame.minY + 80 {
                scroll(in: app, upward: false)
            } else {
                return false
            }
        }
        return element.exists && element.isHittable
    }

    private func revealLazyItem(_ element: XCUIElement, in app: XCUIApplication, listTag: String) -> Bool {
        let list = app.descendants(matching: .any)[listTag]
        for _ in 0..<6 {
            if element.exists {
                if element.isHittable { return true }
                let frame = element.frame
                if frame.maxY > app.frame.maxY - 40 {
                    list.exists ? list.swipeUp() : app.swipeUp()
                } else if frame.minY < app.frame.minY + 80 {
                    list.exists ? list.swipeDown() : app.swipeDown()
                } else {
                    return false
                }
            } else {
                list.exists ? list.swipeUp() : app.swipeUp()
            }
        }
        return element.exists && element.isHittable
    }

    private func scroll(in app: XCUIApplication, upward: Bool) {
        let scrollView = app.scrollViews.firstMatch
        if scrollView.exists && scrollView.isHittable {
            upward ? scrollView.swipeUp() : scrollView.swipeDown()
        } else {
            upward ? app.swipeUp() : app.swipeDown()
        }
    }

    private struct ResultIdentity {
        let modelID: String
        let modelRevision: String
        let runtimeRevision: String
        let backendID: String
    }
}
