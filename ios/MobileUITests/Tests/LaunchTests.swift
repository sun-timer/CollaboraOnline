// -*- Mode: swift; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import XCTest

final class LaunchTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = UIDeviceOrientation.landscapeLeft
    }
    
    @MainActor
    func testLaunchPerformance() throws {
        let app = XCUIApplication()

        measure(metrics: [XCTApplicationLaunchMetric()]) {
            Launch.fileBrowser(app: app)
            app.terminate()
        }
    }

    @MainActor
    func testOpenOdt() throws {
        let app = XCUIApplication()
        Launch.precopyTestFile(app: app, filename: "hello.odt")

        Launch.testFile(app: app, filename: "hello.odt")
            
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "Document"
        attachment.lifetime = .keepAlways
        add(attachment)
        
        app.terminate()
    }

    @MainActor
    func testHomeSearchKeyboardAccessoryAndDismiss() throws {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        Launch.fileBrowser(app: app)
        app.textFields["homeSearchField"].tap()
        let previous = app.buttons["keyboard.previousInput"]
        let next = app.buttons["keyboard.nextInput"]
        XCTAssert(previous.waitForExistence(timeout: 5), "Keyboard accessory did not appear for home search")
        XCTAssert(next.exists, "Keyboard next control was missing")
        XCTAssertFalse(previous.isEnabled, "Home search is the first and only editable input")
        XCTAssertFalse(next.isEnabled, "Home search is the first and only editable input")
        let dismiss = app.buttons["keyboard.dismiss"]
        XCTAssert(dismiss.exists, "Keyboard dismiss button was missing")
        dismiss.tap()
        XCTAssert(app.keyboards.firstMatch.waitForNonExistence(timeout: 3), "Dismiss button should end editing")
        app.terminate()
    }

    @MainActor
    func testModelDrawerFocusAndKeyboardNavigation() throws {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        Launch.fileBrowser(app: app)
        app.buttons["homeAvatarButton"].tap()
        let modelRow = app.buttons["aiModelRow-0"]
        XCTAssert(modelRow.waitForExistence(timeout: 5), "Settings drawer did not open")
        XCTAssertFalse(app.keyboards.firstMatch.exists, "Opening the drawer must not focus a model field")
        modelRow.tap()

        XCTAssert(app.buttons["aiModelConfigBack"].waitForExistence(timeout: 5), "Model detail did not open")
        XCTAssertFalse(app.keyboards.firstMatch.exists, "Opening model detail must not focus an input")
        let name = app.textFields["aiModelConfig.name"]
        XCTAssert(name.waitForExistence(timeout: 5), "Model name input was missing")
        name.tap()
        XCTAssert(app.buttons["keyboard.previousInput"].waitForExistence(timeout: 5), "Keyboard previous control was missing")
        XCTAssert(app.buttons["keyboard.nextInput"].isEnabled, "The model form has a following visible input")
        app.buttons["keyboard.nextInput"].tap()
        let provider = app.textFields["aiModelConfig.provider"]
        provider.typeText("codex-nav-test")
        XCTAssertEqual(provider.value as? String, "codex-nav-test", "Next should focus the provider input")
        app.buttons["keyboard.nextInput"].tap()
        let url = app.textFields["aiModelConfig.url"]
        let existingURL = url.value as? String ?? ""
        XCTAssertFalse(existingURL.isEmpty, "Model URL should retain the existing test-device configuration")
        url.typeText("-codex-nav-regression")
        XCTAssertEqual(url.value as? String, existingURL + "-codex-nav-regression", "Next should focus the existing URL input")

        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "Model input keyboard accessory"
        attachment.lifetime = .keepAlways
        add(attachment)
        app.buttons["keyboard.dismiss"].tap()
        XCTAssert(app.keyboards.firstMatch.waitForNonExistence(timeout: 3), "Dismiss button should end editing")
        app.terminate()
    }

    @MainActor
    func testDocumentPinchSmokeThenHomeKeyboard() throws {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        Launch.precopyTestFile(app: app, filename: "hello.odt")
        Launch.testFile(app: app, filename: "hello.odt")
        let editor = app.webViews.containing(.other, identifier: "Online Editor").firstMatch
        XCTAssert(editor.waitForExistence(timeout: 10), "Document editor was not available for pinch smoke")
        for _ in 0..<4 {
            editor.pinch(withScale: 1.5, velocity: 1.0)
            editor.pinch(withScale: 0.7, velocity: -1.0)
        }

        let backButton = app.buttons["返回"]
        XCTAssert(backButton.waitForExistence(timeout: 5), "Native document back button was missing")
        backButton.tap()
        XCTAssert(app.textFields["homeSearchField"].waitForExistence(timeout: 5), "Home did not appear after returning from the document")
        let search = app.textFields["homeSearchField"]
        search.tap()
        XCTAssert(app.buttons["keyboard.dismiss"].waitForExistence(timeout: 5), "Keyboard accessory did not return after document close")
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "Pinch then home keyboard smoke"
        attachment.lifetime = .keepAlways
        add(attachment)
        app.buttons["keyboard.dismiss"].tap()
        XCTAssert(app.keyboards.firstMatch.waitForNonExistence(timeout: 3), "Dismiss button should end editing")
        app.terminate()
    }

    @MainActor
    func testWriterSearchCaseSensitivityAndNoMatchOnRealCore() throws {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        Launch.precopyTestFile(app: app, filename: "mobile-search-regression.docx")
        Launch.testFile(app: app, filename: "mobile-search-regression.docx")

        let searchButton = app.buttons["查找替换"]
        XCTAssert(searchButton.waitForExistence(timeout: 10), "Document search toolbar button was missing")
        searchButton.tap()
        let webView = app.webViews.containing(.other, identifier: "Online Editor").firstMatch
        let query = webView.searchFields["请输入查找内容"]
        XCTAssert(query.waitForExistence(timeout: 10), "Find field did not appear")
        query.tap()
        query.typeText("let")
        webView.buttons["下一处"].tap()
        let matchAttachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        matchAttachment.name = "Real Core case-insensitive Let search for manual review"
        matchAttachment.lifetime = .keepAlways
        add(matchAttachment)

        webView.buttons["查找设置"].tap()
        let caseSensitive = webView.switches["区分大小写"]
        XCTAssert(caseSensitive.waitForExistence(timeout: 5), "Case-sensitive option was not accessible")
        caseSensitive.tap()
        webView.buttons["返回"].tap()
        XCTAssert(webView.staticTexts["未查到到关键词"].waitForExistence(timeout: 10), "Case-sensitive search should report a missing result")

        webView.buttons["查找设置"].tap()
        caseSensitive.tap()
        webView.buttons["返回"].tap()
        XCTAssert(webView.staticTexts["未查到到关键词"].waitForNonExistence(timeout: 5), "Turning case sensitivity off should clear the no-match message")

        webView.buttons["关闭"].tap()
        searchButton.tap()
        let freshQuery = webView.searchFields["请输入查找内容"]
        XCTAssert(freshQuery.waitForExistence(timeout: 5), "Find field did not reopen")
        freshQuery.tap()
        freshQuery.typeText("NoMatchXYZ")
        webView.buttons["下一处"].tap()
        XCTAssert(webView.staticTexts["未查到到关键词"].waitForExistence(timeout: 10), "Preview search should report a missing keyword")

        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "Real Core search case sensitivity"
        attachment.lifetime = .keepAlways
        add(attachment)
        app.terminate()
    }
    
    @MainActor
    func testWriterOpenPerformance() throws {
        let app = XCUIApplication()
        Launch.precopyTestFile(app: app, filename: "hello.odt")
        
        measure(metrics: [XCTClockMetric()]) {
            Launch.testFile(app: app, filename: "hello.odt")
            app.terminate()
        }
    }
    
    @MainActor
    func testCalcOpenPerformance() throws {
        let app = XCUIApplication()
        Launch.precopyTestFile(app: app, filename: "hello.ods")
        
        measure(metrics: [XCTClockMetric()]) {
            Launch.testFile(app: app, filename: "hello.ods")
            app.terminate()
        }
    }
    
    @MainActor
    func testImpressOpenPerformance() throws {
        let app = XCUIApplication()
        Launch.precopyTestFile(app: app, filename: "hello.odp")
        
        measure(metrics: [XCTClockMetric()]) {
            Launch.testFile(app: app, filename: "hello.odp")
            app.terminate()
        }
    }
    
    @MainActor
    func testWriterReopenPerformance() throws {
        let app = XCUIApplication()
        Launch.precopyTestFile(app: app, filename: "hello.odt")
        
        let measureOptions = XCTMeasureOptions()
        measureOptions.invocationOptions = [.manuallyStart, .manuallyStop]
        
        measure(
            metrics: [XCTClockMetric()],
            options: measureOptions
        ) {
            Launch.testFile(app: app, filename: "hello.odt")
            Lifecycle.closeDocument(app: app)
            
            startMeasuring()
            
            Launch.testFile(app: app, filename: "hello.odt")

            let webview = app.webViews.containing(.other, identifier: "Online Editor").firstMatch;
            XCTAssert(webview.waitForExistence(timeout: 10), "App did not open file in time")
            let loading = webview.staticTexts["Loading…"]
            XCTAssert(loading.waitForNonExistence(timeout: 120), "App did not finish loading in time")
            
            stopMeasuring()
            
            app.terminate()
        }
    }
}
