// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// App-level icon lookup: Home Assets catalog first, then Writer catalog / PNG fallbacks.
@interface AppIcons : NSObject

+ (nullable UIImage *)iconNamed:(NSString *)name;
+ (nullable UIImage *)iconNamed:(NSString *)name size:(CGFloat)size;

@end

NS_ASSUME_NONNULL_END
