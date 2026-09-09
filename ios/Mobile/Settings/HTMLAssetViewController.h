// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface HTMLAssetViewController : UIViewController

- (instancetype)initWithTitle:(NSString *)title resourceName:(NSString *)resourceName resourceExtension:(NSString *)resourceExtension;

@end

NS_ASSUME_NONNULL_END
