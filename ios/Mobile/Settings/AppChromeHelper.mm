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

+ (UIColor *)secondaryBackChevronColor {
    // Figma「标题栏2」返回 chevron：stroke black / opacity 0.9
    return [UIColor colorWithWhite:0.0 alpha:0.9];
}

+ (UIButton *)secondaryBackButtonWithTarget:(id)target action:(SEL)action {
    UIButton *back = [UIButton buttonWithType:UIButtonTypeSystem];
    back.translatesAutoresizingMaskIntoConstraints = NO;
    // Figma 只有 chevron（画布 64×64 → 32pt，chevron 本体 8×16pt、描边 1.5pt），不再带「返回」两个字。
    UIImage *chevron = [[UIImage imageNamed:@"NavBackChevron"]
        imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
    [back setImage:chevron forState:UIControlStateNormal];
    back.tintColor = [self secondaryBackChevronColor];
    back.accessibilityLabel = @"返回";
    [back addTarget:target action:action forControlEvents:UIControlEventTouchUpInside];
    // 点击热区按 Figma 的 80×80px = 40×40pt 给；chevron 居中出现，视觉左边距 27pt（设计稿 26.75pt）。
    [NSLayoutConstraint activateConstraints:@[
        [back.widthAnchor constraintEqualToConstant:40.0],
        [back.heightAnchor constraintEqualToConstant:40.0],
    ]];
    return back;
}

@end
