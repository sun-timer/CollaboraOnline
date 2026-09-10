// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

#import "FeedbackRecord.h"

NS_ASSUME_NONNULL_BEGIN

@interface FeedbackAppearance : NSObject

+ (UIColor *)pageBackgroundColor;
+ (UIColor *)primaryAccentColor;
+ (UIColor *)textPrimaryColor;
+ (UIColor *)textSecondaryColor;
+ (UIColor *)textMutedColor;
+ (UIColor *)inputBackgroundColor;
+ (UIColor *)contentBlockBackgroundColor;

+ (UIStackView *)sectionHeaderRowWithText:(NSString *)text required:(BOOL)required;
+ (UIButton *)typeChipWithTitle:(NSString *)title tag:(NSInteger)tag target:(id)target action:(SEL)action;
+ (void)applyChipStyle:(UIButton *)chip selected:(BOOL)selected;
+ (void)styleInputContainer:(UIView *)container cornerRadius:(CGFloat)radius;
+ (void)styleContentBlock:(UIView *)view;
+ (UIButton *)primaryButtonWithTitle:(NSString *)title target:(id)target action:(SEL)action;
+ (UIButton *)grayButtonWithTitle:(NSString *)title target:(id)target action:(SEL)action;
+ (UIButton *)whiteRoundedButtonWithTitle:(NSString *)title target:(id)target action:(SEL)action;
+ (UIColor *)statusDotColorForStatus:(FeedbackStatus)status;
+ (UIView *)statusDotForStatus:(FeedbackStatus)status;
+ (NSString *)statusTextForStatus:(FeedbackStatus)status;
+ (UIColor *)statusTextColorForStatus:(FeedbackStatus)status;
+ (UILabel *)avatarLabelWithText:(NSString *)text accent:(BOOL)accent;
+ (UIImageView *)checkboxImageViewChecked:(BOOL)checked;

@end

NS_ASSUME_NONNULL_END
