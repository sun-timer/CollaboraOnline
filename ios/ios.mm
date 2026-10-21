// -*- Mode: C++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.

#include "ios.h"
#include <algorithm>
#include <vector>
#include <comphelper/solarmutex.hxx>
#include <sfx2/lokhelper.hxx>
#include <sfx2/viewsh.hxx>
#include <premac.h>

#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>
#include <postmac.h>

extern "C" {
#import <native-code.h>
}

const char *user_name = nullptr;

static BOOL isLokPlaceholderUserName(NSString *name)
{
    if (name.length == 0) {
        return YES;
    }
    // LocalStorage fills in LocalUser#<id> when the LOK user_name is empty.
    if (![name hasPrefix:@"LocalUser#"]) {
        return NO;
    }
    NSString *suffix = [name substringFromIndex:[@"LocalUser#" length]];
    if (suffix.length == 0) {
        return YES;
    }
    NSCharacterSet *nonDigits = [[NSCharacterSet decimalDigitCharacterSet] invertedSet];
    return [suffix rangeOfCharacterFromSet:nonDigits].location == NSNotFound;
}

NSString *iosSettingsUserName(void)
{
    NSUserDefaults *defaults = NSUserDefaults.standardUserDefaults;
    NSString *name = [defaults stringForKey:@"USER_PROFILE_NAME"];
    if (isLokPlaceholderUserName(name)) {
        id managedName = [defaults dictionaryForKey:@"com.apple.configuration.managed"][@"userName"];
        if ([managedName isKindOfClass:[NSString class]]) {
            name = managedName;
        }
    }
    if (isLokPlaceholderUserName(name)) {
        name = [defaults stringForKey:@"userName"];
    }
    if (isLokPlaceholderUserName(name)) {
        name = @"orangepi";
    }
    return [name copy];
}

void refreshIOSUserName()
{
    // Keep the UTF-8 storage alive for LocalStorage while the document is open.
    static NSString *currentUserName;
    // Publish the Settings nickname, never the LOK LocalUser#N placeholder.
    currentUserName = iosSettingsUserName();
    user_name = currentUserName.UTF8String;

    // Push the nickname into any open document's web layer (comment author).
    // Must match kCOIOSUserNameDidChangeNotification in DocumentViewController.mm.
    [[NSNotificationCenter defaultCenter] postNotificationName:@"COIOSUserNameDidChange"
                                                        object:nil
                                                      userInfo:@{@"name": currentUserName}];
}

bool setIOSViewAuthor(lok::Document *document, int viewId, const char *name)
{
    auto *mutex = comphelper::SolarMutex::get();
    if (!document || !mutex || viewId < 0 || !name || !*name) return false;
    osl::Guard<comphelper::SolarMutex> guard(mutex);
    std::vector<int> ids(std::max(1, document->getViewsCount()));
    if (!document->getViewIds(ids.data(), ids.size()) || std::find(ids.begin(), ids.end(), viewId) == ids.end()) return false;
    SfxViewShell *view = SfxLokHelper::getViewOfId(viewId);
    if (!view) return false;
    // Impress persists the view's author, ignoring InsertAnnotation.Author.
    view->SetLOKAuthor(OUString::fromUtf8(name));
    return true;
}

int coolwsd_server_socket_fd = -1;

LibreOfficeKit *lo_kit;

// vim:set shiftwidth=4 softtabstop=4 expandtab:
