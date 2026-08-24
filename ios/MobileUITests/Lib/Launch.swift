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

import Foundation
import XCTest
import SafariServices

final class Launch {   
    /// Copy a test file to the correct places for `Launch.testFile` to find it
    ///
    /// To copy, this will launch the app with some specially-crafted parameters, and both terminates the app and resets parameters after running. Therefore, it's advisable to run this early on as it'll destroy your test state when it runs
    ///
    /// - Parameters:
    ///   - app: The XCUI app object
    ///   - filename: The basename of a file
    static func precopyTestFile(app: XCUIApplication, filename: String) {
        app.launchArguments = ["-copyTestFile", filename]
        app.launch()
       
        app.launchArguments = [String]()
        app.terminate()
    }

    /// Open a specified test file from the custom home recents list
    static func testFile(app: XCUIApplication, filename: String) {
        app.activate()

        let homeSearch = app.textFields["homeSearchField"]
        XCTAssert(homeSearch.waitForExistence(timeout: 10), "Home screen did not appear")

        let recent = app.cells["homeRecent-\(filename)"]
        Input.tapWithTimeout(element: recent, timeout: 5)

        let webview = app.webViews.containing(.other, identifier: "Online Editor").firstMatch;
        XCTAssert(webview.waitForExistence(timeout: 30), "App did not open editor in time")
        let loading = webview.staticTexts["Loading…"]
        XCTAssert(loading.waitForNonExistence(timeout: 30), "App did not finish loading in time")
    }

    /// Launch the app to the home screen
    static func fileBrowser(app: XCUIApplication) {
        app.launch()
        XCTAssert(app.textFields["homeSearchField"].waitForExistence(timeout: 10), "Home screen did not appear")
    }
}
