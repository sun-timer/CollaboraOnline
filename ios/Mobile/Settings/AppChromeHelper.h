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

/// Back button styled for secondary pages.
+ (UIButton *)secondaryBackButtonWithTarget:(id)target action:(SEL)action;

@end

NS_ASSUME_NONNULL_END
