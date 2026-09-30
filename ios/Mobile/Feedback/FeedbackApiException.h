// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

FOUNDATION_EXPORT NSErrorDomain const FeedbackApiErrorDomain;
FOUNDATION_EXPORT NSString * const FeedbackApiErrorReasonKey;

@interface FeedbackApiException : NSObject

+ (NSError *)errorWithReason:(NSString *)reason message:(NSString *)message;
+ (NSError *)errorWithReason:(NSString *)reason
                     message:(NSString *)message
                    httpCode:(NSInteger)httpCode
                     apiCode:(NSInteger)apiCode;

+ (nullable NSString *)reasonFromError:(NSError *)error;
+ (NSString *)messageFromError:(NSError *)error;

@end

NS_ASSUME_NONNULL_END
