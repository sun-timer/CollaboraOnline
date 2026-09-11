// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, AppThemeMode) {
    AppThemeModeSystem = -1,
    AppThemeModeLight = 1,
    AppThemeModeDark = 2,
};

@interface AppThemeManager : NSObject

+ (AppThemeMode)currentMode;
+ (void)setMode:(AppThemeMode)mode;
+ (UIUserInterfaceStyle)currentInterfaceStyle;
+ (BOOL)isDarkModeActive;
+ (NSString *)displayNameForMode:(AppThemeMode)mode;
+ (void)applyToAllWindows;
+ (void)applyToWindow:(UIWindow *)window;

@end

NS_ASSUME_NONNULL_END
