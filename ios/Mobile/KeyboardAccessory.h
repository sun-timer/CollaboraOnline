/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface KeyboardAccessory : NSObject
+ (void)installOnInput:(UIView<UITextInput> *)input;
@end

NS_ASSUME_NONNULL_END
