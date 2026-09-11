// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AppThemeManager.h"

static NSString *const kNightModeKey = @"NIGHT_MODE";

@implementation AppThemeManager

+ (AppThemeMode)currentMode {
    if (![[NSUserDefaults standardUserDefaults] objectForKey:kNightModeKey]) {
        return AppThemeModeSystem;
    }
    NSInteger stored = [[NSUserDefaults standardUserDefaults] integerForKey:kNightModeKey];
    if (stored == AppThemeModeLight || stored == AppThemeModeDark || stored == AppThemeModeSystem) {
        return (AppThemeMode)stored;
    }
    return AppThemeModeSystem;
}

+ (void)setMode:(AppThemeMode)mode {
    [[NSUserDefaults standardUserDefaults] setInteger:mode forKey:kNightModeKey];
    [self applyToAllWindows];
}

+ (UIUserInterfaceStyle)currentInterfaceStyle {
    switch ([self currentMode]) {
        case AppThemeModeLight:
            return UIUserInterfaceStyleLight;
        case AppThemeModeDark:
            return UIUserInterfaceStyleDark;
        case AppThemeModeSystem:
        default:
            return UIUserInterfaceStyleUnspecified;
    }
}

+ (BOOL)isDarkModeActive {
    UIUserInterfaceStyle style = [self currentInterfaceStyle];
    if (style == UIUserInterfaceStyleDark) {
        return YES;
    }
    if (style == UIUserInterfaceStyleLight) {
        return NO;
    }
    if (@available(iOS 13.0, *)) {
        for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
            if (![scene isKindOfClass:[UIWindowScene class]]) {
                continue;
            }
            UIWindowScene *windowScene = (UIWindowScene *)scene;
            for (UIWindow *window in windowScene.windows) {
                if (window.isKeyWindow) {
                    return window.traitCollection.userInterfaceStyle == UIUserInterfaceStyleDark;
                }
            }
        }
    }
    return NO;
}

+ (NSString *)displayNameForMode:(AppThemeMode)mode {
    switch (mode) {
        case AppThemeModeLight:
            return @"浅色";
        case AppThemeModeDark:
            return @"深色";
        case AppThemeModeSystem:
        default:
            return @"跟随系统";
    }
}

+ (void)applyToAllWindows {
    if (@available(iOS 13.0, *)) {
        for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
            if (![scene isKindOfClass:[UIWindowScene class]]) {
                continue;
            }
            for (UIWindow *window in ((UIWindowScene *)scene).windows) {
                [self applyToWindow:window];
            }
        }
    }
}

+ (void)applyToWindow:(UIWindow *)window {
    if (window == nil) {
        return;
    }
    window.overrideUserInterfaceStyle = [self currentInterfaceStyle];
}

@end
