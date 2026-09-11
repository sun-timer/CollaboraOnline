// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "FeedbackAppearance.h"

#import "Settings/AppChromeHelper.h"

@implementation FeedbackAppearance

+ (UIColor *)pageBackgroundColor {
    return [AppChromeHelper homePlateColor];
}

+ (UIColor *)primaryAccentColor {
    return [AppChromeHelper homeFabColor];
}

+ (UIColor *)textPrimaryColor {
    return [UIColor colorWithRed:0x33 / 255.0 green:0x33 / 255.0 blue:0x33 / 255.0 alpha:1];
}

+ (UIColor *)textSecondaryColor {
    return [UIColor colorWithRed:0x6A / 255.0 green:0x6A / 255.0 blue:0x6A / 255.0 alpha:1];
}

+ (UIColor *)textMutedColor {
    return [UIColor colorWithRed:0x99 / 255.0 green:0x99 / 255.0 blue:0x99 / 255.0 alpha:1];
}

+ (UIColor *)inputBackgroundColor {
    return UIColor.whiteColor;
}

+ (UIColor *)contentBlockBackgroundColor {
    return [UIColor colorWithRed:0xF7 / 255.0 green:0xF7 / 255.0 blue:0xF7 / 255.0 alpha:1];
}

+ (UIView *)requiredDotView {
    UIView *dot = [[UIView alloc] init];
    dot.translatesAutoresizingMaskIntoConstraints = NO;
    dot.backgroundColor = [UIColor colorWithRed:0xD8 / 255.0 green:0x1E / 255.0 blue:0x06 / 255.0 alpha:1];
    dot.layer.cornerRadius = 2.5;
    [NSLayoutConstraint activateConstraints:@[
        [dot.widthAnchor constraintEqualToConstant:5],
        [dot.heightAnchor constraintEqualToConstant:5],
    ]];
    return dot;
}

+ (UIStackView *)sectionHeaderRowWithText:(NSString *)text required:(BOOL)required {
    UIStackView *row = [[UIStackView alloc] init];
    row.axis = UILayoutConstraintAxisHorizontal;
    row.spacing = 4;
    row.alignment = UIStackViewAlignmentCenter;
    row.translatesAutoresizingMaskIntoConstraints = NO;
    if (required) {
        [row addArrangedSubview:[self requiredDotView]];
    }
    UILabel *label = [[UILabel alloc] init];
    label.text = text;
    label.font = [UIFont systemFontOfSize:14];
    label.textColor = [self textSecondaryColor];
    [row addArrangedSubview:label];
    return row;
}

+ (UIButton *)typeChipWithTitle:(NSString *)title tag:(NSInteger)tag target:(id)target action:(SEL)action {
    UIButton *chip = [UIButton buttonWithType:UIButtonTypeCustom];
    chip.translatesAutoresizingMaskIntoConstraints = NO;
    chip.tag = tag;
    chip.titleLabel.font = [UIFont systemFontOfSize:14];
    chip.contentEdgeInsets = UIEdgeInsetsMake(0, 12, 0, 12);
    chip.layer.cornerRadius = 18.5;
    chip.clipsToBounds = YES;
    [chip setTitle:title forState:UIControlStateNormal];
    [chip addTarget:target action:action forControlEvents:UIControlEventTouchUpInside];
    [chip.heightAnchor constraintEqualToConstant:37].active = YES;
    [self applyChipStyle:chip selected:NO];
    return chip;
}

+ (void)applyChipStyle:(UIButton *)chip selected:(BOOL)selected {
    chip.backgroundColor = selected ? [self primaryAccentColor] : UIColor.whiteColor;
    [chip setTitleColor:selected ? UIColor.whiteColor : [self textPrimaryColor] forState:UIControlStateNormal];
}

+ (void)styleInputContainer:(UIView *)container cornerRadius:(CGFloat)radius {
    container.backgroundColor = [self inputBackgroundColor];
    container.layer.cornerRadius = radius;
    container.clipsToBounds = YES;
}

+ (void)styleContentBlock:(UIView *)view {
    view.backgroundColor = [self contentBlockBackgroundColor];
    view.layer.cornerRadius = 8;
    view.clipsToBounds = YES;
}

+ (UIButton *)primaryButtonWithTitle:(NSString *)title target:(id)target action:(SEL)action {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    button.backgroundColor = [self primaryAccentColor];
    button.layer.cornerRadius = 22.5;
    button.titleLabel.font = [UIFont boldSystemFontOfSize:18];
    [button setTitle:title forState:UIControlStateNormal];
    [button setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    [button addTarget:target action:action forControlEvents:UIControlEventTouchUpInside];
    [button.heightAnchor constraintEqualToConstant:45].active = YES;
    return button;
}

+ (UIButton *)grayButtonWithTitle:(NSString *)title target:(id)target action:(SEL)action {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    button.backgroundColor = [UIColor colorWithRed:0xE5 / 255.0 green:0xE5 / 255.0 blue:0xE5 / 255.0 alpha:1];
    button.layer.cornerRadius = 17.5;
    button.titleLabel.font = [UIFont systemFontOfSize:16];
    [button setTitle:title forState:UIControlStateNormal];
    [button setTitleColor:[UIColor colorWithWhite:0 alpha:0.89] forState:UIControlStateNormal];
    [button addTarget:target action:action forControlEvents:UIControlEventTouchUpInside];
    [button.heightAnchor constraintEqualToConstant:35].active = YES;
    return button;
}

+ (UIButton *)whiteRoundedButtonWithTitle:(NSString *)title target:(id)target action:(SEL)action {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    button.backgroundColor = UIColor.whiteColor;
    button.layer.cornerRadius = 12;
    button.layer.borderWidth = 1;
    button.layer.borderColor = [UIColor colorWithWhite:0.88 alpha:1].CGColor;
    button.titleLabel.font = [UIFont systemFontOfSize:16];
    [button setTitle:title forState:UIControlStateNormal];
    [button setTitleColor:[self textPrimaryColor] forState:UIControlStateNormal];
    [button addTarget:target action:action forControlEvents:UIControlEventTouchUpInside];
    [button.heightAnchor constraintEqualToConstant:54].active = YES;
    return button;
}

+ (UIColor *)statusDotColorForStatus:(FeedbackStatus)status {
    switch (status) {
        case FeedbackStatusReplied:
            return [UIColor colorWithRed:0x00 / 255.0 green:0x66 / 255.0 blue:0xFF / 255.0 alpha:1];
        case FeedbackStatusProcessing:
            return [self primaryAccentColor];
        default:
            return [UIColor colorWithRed:0x6A / 255.0 green:0x6A / 255.0 blue:0x6A / 255.0 alpha:1];
    }
}

+ (UIView *)statusDotForStatus:(FeedbackStatus)status {
    UIView *dot = [[UIView alloc] init];
    dot.translatesAutoresizingMaskIntoConstraints = NO;
    dot.layer.cornerRadius = 4;
    dot.clipsToBounds = YES;
    dot.backgroundColor = [self statusDotColorForStatus:status];
    [NSLayoutConstraint activateConstraints:@[
        [dot.widthAnchor constraintEqualToConstant:8],
        [dot.heightAnchor constraintEqualToConstant:8],
    ]];
    return dot;
}

+ (NSString *)statusTextForStatus:(FeedbackStatus)status {
    switch (status) {
        case FeedbackStatusReplied:
            return @"已回复";
        case FeedbackStatusClosed:
            return @"已关闭";
        case FeedbackStatusProcessing:
            return @"处理中";
        case FeedbackStatusSubmitted:
        default:
            return @"已提交";
    }
}

+ (UIColor *)statusTextColorForStatus:(FeedbackStatus)status {
    switch (status) {
        case FeedbackStatusReplied:
            return [UIColor colorWithRed:0x00 / 255.0 green:0x66 / 255.0 blue:0xFF / 255.0 alpha:1];
        case FeedbackStatusProcessing:
            return [self primaryAccentColor];
        default:
            return [self textSecondaryColor];
    }
}

+ (UILabel *)avatarLabelWithText:(NSString *)text accent:(BOOL)accent {
    UILabel *avatar = [[UILabel alloc] init];
    avatar.translatesAutoresizingMaskIntoConstraints = NO;
    avatar.text = text;
    avatar.textAlignment = NSTextAlignmentCenter;
    avatar.font = [UIFont boldSystemFontOfSize:16];
    avatar.textColor = UIColor.whiteColor;
    avatar.backgroundColor = accent ? [self primaryAccentColor] : [UIColor colorWithRed:0x00 / 255.0 green:0x66 / 255.0 blue:0xFF / 255.0 alpha:1];
    avatar.layer.cornerRadius = 20;
    avatar.clipsToBounds = YES;
    [NSLayoutConstraint activateConstraints:@[
        [avatar.widthAnchor constraintEqualToConstant:40],
        [avatar.heightAnchor constraintEqualToConstant:40],
    ]];
    return avatar;
}

+ (UIImageView *)checkboxImageViewChecked:(BOOL)checked {
    UIImageView *imageView = [[UIImageView alloc] init];
    imageView.translatesAutoresizingMaskIntoConstraints = NO;
    imageView.contentMode = UIViewContentModeScaleAspectFit;
    if (@available(iOS 13.0, *)) {
        NSString *symbol = checked ? @"checkmark.square.fill" : @"square";
        UIImage *image = [UIImage systemImageNamed:symbol];
        imageView.image = [image imageWithTintColor:checked ? [self primaryAccentColor] : [self textMutedColor]
                                     renderingMode:UIImageRenderingModeAlwaysOriginal];
    }
    [NSLayoutConstraint activateConstraints:@[
        [imageView.widthAnchor constraintEqualToConstant:24],
        [imageView.heightAnchor constraintEqualToConstant:24],
    ]];
    return imageView;
}

@end
