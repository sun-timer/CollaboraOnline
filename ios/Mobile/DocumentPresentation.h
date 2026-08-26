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

/// Copy a blank untitled.{odt|ods|odp} from the app bundle into Documents.
/// Does not call LibreOfficeKit — same approach as Android createNewFileAsync.
+ (nullable NSURL *)createBlankDocumentWithExtension:(NSString *)outputExtension
                                           basename:(NSString *)basename
                                              error:(NSError * _Nullable * _Nullable)error;

/// Deprecated path kept for call sites that still pass ott/ots/otp; maps to blank ODF copy.
+ (nullable NSURL *)createDocumentFromTemplateExtension:(NSString *)templateExtension
                                        outputExtension:(NSString *)outputExtension
                                              basename:(NSString *)basename
                                                 error:(NSError * _Nullable * _Nullable)error;

@end

NS_ASSUME_NONNULL_END
