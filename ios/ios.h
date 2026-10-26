/* -*- Mode: C++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

#define LIBO_INTERNAL_ONLY
#include <LibreOfficeKit/LibreOfficeKit.hxx>
#include <string>

extern const char *user_name;
void refreshIOSUserName();
// Update only a validated live document view; historical comments are untouched.
bool setIOSViewAuthor(lok::Document *document, int viewId, const char *name);

// Narrow Calc text-format API. Coordinates are zero-based and must match the
// current selection; this never converts numeric/formula cells into text.
struct IOSCalcTextRange {
    int sheet, col1, row1, col2, row2;
};
struct IOSCalcTextFormatResult {
    bool success = false;
    int changed = 0;
    const char *message = "未能修改文字格式";
};
IOSCalcTextFormatResult applyIOSCalcTextFormat(lok::Document *document, int viewId,
    const IOSCalcTextRange &range, const char *operation, int value, bool toggle);
struct IOSImpressShapeFormatResult {
    bool success = false;
    bool enabled = false;
    int changed = 0;
    const char *message = "";
};
IOSImpressShapeFormatResult toggleIOSImpressShapeFormat(lok::Document *document,
    int viewId, int part, const char *command, const char *selectionId);
bool setIOSImpressMasterBackground(lok::Document *document, int viewId, int part, bool visible);
struct IOSCalcFormatStates {
    bool success = false;
    std::string numberFormat;
    int rotation = 0;
    bool stacked = false;
    bool hasNumberFormat = false;
    bool hasRotation = false;
    bool hasStacked = false;
};
IOSCalcFormatStates getIOSCalcFormatStates(lok::Document *document, int viewId,
    const IOSCalcTextRange &range);
#ifdef __OBJC__
@class NSString;
// Settings nickname (USER_PROFILE_NAME). Never a LOK LocalUser#N placeholder.
NSString *iosSettingsUserName(void);
#endif

extern int coolwsd_server_socket_fd;

extern LibreOfficeKit *lo_kit;

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
