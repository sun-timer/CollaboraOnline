// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface AppChromeHelper : NSObject

+ (UIColor *)homePlateColor;
+ (UIColor *)primaryAccentColor;
+ (UIColor *)homeFabColor;

/// Gray header plate extending under the status bar (Home / primary chrome).
+ (NSLayoutConstraint *)pinHeaderPlate:(UIView *)plate
                                inView:(UIView *)view
                         contentHeight:(CGFloat)contentHeight;

/// Standard secondary page background (#f2f2f2).
+ (void)applySecondaryPageBackground:(UIView *)view;

/// Secondary page back button: chevron only (no "返回" text), 40x40pt hit area.
/// Figma 403:20284 「标题栏2」 → 80/返回: chevron 8x16pt, stroke 1.5pt, black @90%.
/// Layout height is 40pt (the old text button was ~20pt tall), so call sites that
/// anchor content to `back.bottomAnchor` offset it by 12pt less than before.
+ (UIButton *)secondaryBackButtonWithTarget:(id)target action:(SEL)action;

/// Tint used by the secondary back chevron (#000000 at 90%, Figma stroke black/opacity 0.9).
+ (UIColor *)secondaryBackChevronColor;

@end

NS_ASSUME_NONNULL_END
