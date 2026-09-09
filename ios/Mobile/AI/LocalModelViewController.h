// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface LocalModelViewController : UIViewController

@property (copy, nonatomic, nullable) void (^onDismiss)(void);

@end

NS_ASSUME_NONNULL_END
