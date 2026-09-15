// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

@class AIService;

NS_ASSUME_NONNULL_BEGIN

@interface ImpressOutlineOverlayController : NSObject

- (instancetype)initWithHostViewController:(UIViewController *)host
                                 aiService:(AIService *)aiService;
- (void)showWithPrefill:(nullable NSString *)prefill;
- (void)dismiss;
- (BOOL)isVisible;

@end

NS_ASSUME_NONNULL_END
