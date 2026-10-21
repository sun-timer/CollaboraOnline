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

extern const char *user_name;
void refreshIOSUserName();
// Update only a validated live document view; historical comments are untouched.
bool setIOSViewAuthor(lok::Document *document, int viewId, const char *name);
#ifdef __OBJC__
@class NSString;
// Settings nickname (USER_PROFILE_NAME). Never a LOK LocalUser#N placeholder.
NSString *iosSettingsUserName(void);
#endif

extern int coolwsd_server_socket_fd;

extern LibreOfficeKit *lo_kit;

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
