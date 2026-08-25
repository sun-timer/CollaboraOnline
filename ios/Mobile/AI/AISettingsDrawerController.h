// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface AISettingsDrawerController : UIViewController

+ (instancetype)attachToHost:(UIViewController *)host;
- (void)openDrawer;
- (void)closeDrawer;

@end

NS_ASSUME_NONNULL_END
