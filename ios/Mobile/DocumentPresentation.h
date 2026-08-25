// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface DocumentPresentation : NSObject

+ (void)presentDocumentAtURL:(NSURL *)documentURL from:(UIViewController *)presenter;
+ (nullable NSURL *)createDocumentFromTemplateExtension:(NSString *)templateExtension
                                        outputExtension:(NSString *)outputExtension
                                              basename:(NSString *)basename
                                                 error:(NSError * _Nullable * _Nullable)error;

@end

NS_ASSUME_NONNULL_END
