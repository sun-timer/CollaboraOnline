// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, CreateFileDocKind) {
    CreateFileDocKindWriter = 0,
    CreateFileDocKindCalc,
    CreateFileDocKindImpress,
};

@interface CreateFileBottomSheetController : UIViewController

- (instancetype)initWithDocKind:(CreateFileDocKind)docKind
                     completion:(void (^)(NSString *basename,
                                          BOOL aiEnabled,
                                          NSString *aiUserDescription))completion;

@end

NS_ASSUME_NONNULL_END
