import XCTest
import Foundation

final class Apple65UITests: XCTestCase {
    func testAuthorizedSingleFileBatch() throws {
        continueAfterFailure = false
        try XCTSkipUnless(ProcessInfo.processInfo.environment["ODAI_APPLE65_RUN_ID"] == "w3-apple-punctuation-paired6-20261116-01", "Requires ODAI_APPLE65_RUN_ID=w3-apple-punctuation-paired6-20261116-01")
        let app = XCUIApplication(bundleIdentifier: "io.ondeviceai.probe.w3punctpair20261116")
        app.activate()

        XCTContext.runActivity(named: "initial") { activity in
            let tree = XCTAttachment(string: app.debugDescription)
            tree.lifetime = .keepAlways
            tree.name = "initial-debug-description"
            activity.add(tree)
            let shot = XCTAttachment(screenshot: app.screenshot())
            shot.lifetime = .keepAlways
            shot.name = "initial-screenshot"
            activity.add(shot)
        }

        let title = app.staticTexts["W3 punctuation paired6"]
        let noReport = app.staticTexts["Report does not exist yet."]
        let reportExists = app.staticTexts["Report exists: replay is refused."]
        XCTAssertTrue(title.waitForExistence(timeout: 10), "Missing title")
        XCTAssertTrue(noReport.waitForExistence(timeout: 10), "Missing initial no-report text")
        XCTAssertFalse(reportExists.exists, "Report exists before Run")

        let notDetermined = app.staticTexts["Speech: not-determined"]
        let authorized = app.staticTexts["Speech: authorized"]
        let denied = app.staticTexts["Speech: denied"]
        let restricted = app.staticTexts["Speech: restricted"]
        let setup = app.buttons["Speech authorization setup"]
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let speechAlertIdentifier = "kTCCServiceSpeechRecognition-alert"
        let speechAllowIdentifier = "kTCCServiceSpeechRecognition-allow-button"
        let speechTextPredicate = NSPredicate(format: "label CONTAINS[c] %@ OR label CONTAINS[c] %@", "speech recognition", "语音识别")
        var permissionHandled = false
        var needsAuthorizationPoll = false

        func currentSpeechAlert() -> XCUIElement? {
            let alertCandidates = [
                app.alerts[speechAlertIdentifier].firstMatch,
                springboard.alerts[speechAlertIdentifier].firstMatch
            ]
            for alert in alertCandidates {
                guard alert.exists else { continue }
                if speechTextPredicate.evaluate(with: alert.label) || alert.staticTexts.matching(speechTextPredicate).firstMatch.exists {
                    return alert
                }
            }
            return nil
        }

        func handleSpeechAlert(_ alert: XCUIElement) {
            guard !permissionHandled else { return }
            permissionHandled = true
            let allow = alert.buttons[speechAllowIdentifier].firstMatch
            guard allow.exists && allow.isEnabled && allow.isHittable else {
                XCTFail("Speech alert appeared without an exact allow button")
                return
            }
            allow.tap()
        }

        if let pendingAlert = currentSpeechAlert() {
            needsAuthorizationPoll = true
            handleSpeechAlert(pendingAlert)
        } else if authorized.exists {
            // already authorized
        } else if denied.exists || restricted.exists {
            XCTFail("Speech authorization denied or restricted before Run")
        } else if notDetermined.exists {
            XCTAssertTrue(setup.waitForExistence(timeout: 10), "Missing speech setup button")
            XCTAssertTrue(setup.isEnabled && setup.isHittable, "Speech setup button not actionable")
            setup.tap()
            needsAuthorizationPoll = true
        } else {
            XCTFail("Speech status is not visible before Run")
        }

        if needsAuthorizationPoll {
            let authDeadline = Date().addingTimeInterval(20)
            while Date() < authDeadline {
                if authorized.exists {
                    break
                }
                if denied.exists || restricted.exists {
                    XCTFail("Speech authorization denied or restricted")
                    break
                }
                if !permissionHandled, let alert = currentSpeechAlert() {
                    handleSpeechAlert(alert)
                }
                let authRemaining = authDeadline.timeIntervalSinceNow
                if authRemaining <= 0 { break }
                Thread.sleep(forTimeInterval: min(1, authRemaining))
            }
        }

        XCTAssertTrue(authorized.exists, "Speech is not authorized before Run")
        XCTAssertFalse(reportExists.exists, "Report exists before Run after authorization")

        XCTContext.runActivity(named: "authorization-ready") { activity in
            let shot = XCTAttachment(screenshot: app.screenshot())
            shot.lifetime = .keepAlways
            shot.name = "authorization-ready-screenshot"
            activity.add(shot)
            let tree = XCTAttachment(string: app.debugDescription)
            tree.lifetime = .keepAlways
            tree.name = "authorization-ready-debug-description"
            activity.add(tree)
        }

        let run = app.buttons["Run paired6 FILE control"]
        XCTAssertTrue(run.waitForExistence(timeout: 10), "Missing Run button")
        XCTAssertTrue(run.isEnabled && run.isHittable, "Run button not actionable")
        run.tap()

        XCTContext.runActivity(named: "started") { activity in
            let tree = XCTAttachment(string: app.debugDescription)
            tree.lifetime = .keepAlways
            tree.name = "started-debug-description"
            activity.add(tree)
        }

        let completed = app.staticTexts["Fixed FILE alias completed. No scoring, quality pass, budget pass, or W3 completion is claimed."]
        let stoppedLabels = [
            "Run stopped. Cleanup and final report are complete.",
            "Input integrity stopped the batch. Remaining rows stay not-run.",
            "Run cancelled. Cleanup and final report are complete."
        ]
        let stopped = stoppedLabels.map { app.staticTexts[$0] }
        let fatalPredicate = NSPredicate(format: "label CONTAINS[c] %@ OR label CONTAINS[c] %@ OR label CONTAINS[c] %@ OR label CONTAINS[c] %@ OR label CONTAINS[c] %@ OR label CONTAINS[c] %@ OR label CONTAINS[c] %@", "fatal", "failed", "cancelled", "refusing", "stopping", "report i/o failed", "scene inactive")
        let fatalPrefixes = ["fatal", "failed", "cancelled", "refusing", "stopping", "report i/o failed", "scene inactive"]
        var terminal = "timeout"
        let runDeadline = Date().addingTimeInterval(2400)
        while Date() < runDeadline {
            if completed.exists {
                terminal = "completed"
                break
            }
            if let hit = stopped.first(where: { $0.exists }) {
                terminal = "stopped: " + hit.label
                break
            }
            let candidates = app.staticTexts.matching(fatalPredicate).allElementsBoundByIndex
            if let hit = candidates.first(where: { element in
                let text = element.label.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                return fatalPrefixes.contains { text.hasPrefix($0) }
            }) {
                terminal = "fatal: " + hit.label
                break
            }
            let runRemaining = runDeadline.timeIntervalSinceNow
            if runRemaining <= 0 { break }
            Thread.sleep(forTimeInterval: min(1, runRemaining))
        }

        XCTContext.runActivity(named: "final") { activity in
            let tree = XCTAttachment(string: app.debugDescription)
            tree.lifetime = .keepAlways
            tree.name = "final-debug-description"
            activity.add(tree)
            let shot = XCTAttachment(screenshot: app.screenshot())
            shot.lifetime = .keepAlways
            shot.name = "final-screenshot"
            activity.add(shot)
        }

        XCTAssertTrue(completed.exists, "Expected completed message, terminal state: " + terminal)
    }
}
