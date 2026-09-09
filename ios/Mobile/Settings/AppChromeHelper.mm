// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AppChromeHelper.h"

@implementation AppChromeHelper

+ (UIColor *)homePlateColor {
    return [UIColor colorWithRed:0xF2 / 255.0 green:0xF2 / 255.0 blue:0xF2 / 255.0 alpha:1];
}

+ (UIColor *)primaryAccentColor {
    return [self homeFabColor];
}

+ (UIColor *)homeFabColor {
    return [UIColor colorWithRed:0xFA / 255.0 green:0x62 / 255.0 blue:0x00 / 255.0 alpha:1];
}

+ (NSLayoutConstraint *)pinHeaderPlate:(UIView *)plate
                                inView:(UIView *)view
                         contentHeight:(CGFloat)contentHeight {
    plate.translatesAutoresizingMaskIntoConstraints = NO;
    plate.backgroundColor = [self homePlateColor];
    NSLayoutConstraint *bottom = [plate.bottomAnchor constraintEqualToAnchor:view.safeAreaLayoutGuide.topAnchor
                                                                    constant:contentHeight];
    [NSLayoutConstraint activateConstraints:@[
        [plate.topAnchor constraintEqualToAnchor:view.topAnchor],
        [plate.leadingAnchor constraintEqualToAnchor:view.leadingAnchor],
        [plate.trailingAnchor constraintEqualToAnchor:view.trailingAnchor],
        bottom,
    ]];
    return bottom;
}

+ (void)applySecondaryPageBackground:(UIView *)view {
    view.backgroundColor = [self homePlateColor];
}

+ (UIButton *)secondaryBackButtonWithTarget:(id)target action:(SEL)action {
    UIButton *back = [UIButton buttonWithType:UIButtonTypeSystem];
    back.translatesAutoresizingMaskIntoConstraints = NO;
    [back setTitle:@"‹ 返回" forState:UIControlStateNormal];
    [back addTarget:target action:action forControlEvents:UIControlEventTouchUpInside];
    return back;
}

@end
