// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "FeedbackApiException.h"

NSErrorDomain const FeedbackApiErrorDomain = @"FeedbackApiError";
NSString * const FeedbackApiErrorReasonKey = @"FeedbackApiErrorReason";

@implementation FeedbackApiException

+ (NSError *)errorWithReason:(NSString *)reason message:(NSString *)message {
    return [self errorWithReason:reason message:message httpCode:0 apiCode:0];
}

+ (NSError *)errorWithReason:(NSString *)reason
                     message:(NSString *)message
                    httpCode:(NSInteger)httpCode
                     apiCode:(NSInteger)apiCode {
    NSMutableDictionary *info = [NSMutableDictionary dictionary];
    if (reason.length > 0) {
        info[FeedbackApiErrorReasonKey] = reason;
    }
    if (message.length > 0) {
        info[NSLocalizedDescriptionKey] = message;
    }
    if (httpCode != 0) {
        info[@"FeedbackApiHttpCode"] = @(httpCode);
    }
    if (apiCode != 0) {
        info[@"FeedbackApiCode"] = @(apiCode);
    }
    return [NSError errorWithDomain:FeedbackApiErrorDomain code:apiCode userInfo:info];
}

+ (NSString *)reasonFromError:(NSError *)error {
    if (error == nil) {
        return @"";
    }
    NSString *reason = error.userInfo[FeedbackApiErrorReasonKey];
    return reason ?: @"";
}

+ (NSString *)messageFromError:(NSError *)error {
    if (error == nil) {
        return @"";
    }
    NSString *msg = error.userInfo[NSLocalizedDescriptionKey];
    return msg ?: @"";
}

@end
