// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "FeedbackConfig.h"

/** 联调 cpolar 隧道；发布前改回 @"" */
static NSString * const kFeedbackAPIBaseURL = @"http://1695d68b.r9.cpolar.cn";

@implementation FeedbackConfig

+ (NSString *)APIBaseURL {
    return kFeedbackAPIBaseURL;
}

+ (BOOL)LOGUploadEnabled {
    return NO;
}

+ (BOOL)isLogUploadEnabled {
    return [self LOGUploadEnabled] && [self isConfigured];
}

+ (BOOL)isConfigured {
    NSString *base = [self APIBaseURL];
    return base.length > 0 && [[base stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]] length] > 0;
}

+ (NSString *)PATHUploadAvatar {
    return @"/v1.0/feedback/uploadAvatar";
}

+ (NSString *)PATHUploadImages {
    return @"/v1.0/feedback/uploadImages";
}

+ (NSString *)PATHUploadLog {
    return @"/v1.0/feedback/uploadLog";
}

+ (NSString *)PATHSubmit {
    return @"/v1.0/feedback/submit";
}

+ (NSString *)PATHList {
    return @"/v1.0/feedback/list";
}

+ (NSString *)PATHDetail {
    return @"/v1.0/feedback/detail";
}

+ (NSString *)PATHClose {
    return @"/v1.0/feedback/close";
}

+ (NSInteger)uploadImageMaxEdgePx {
    return 1920;
}

+ (CGFloat)uploadImageJPEGQuality {
    return 0.85;
}

+ (NSUInteger)maxUploadImageBytes {
    return 5 * 1024 * 1024;
}

+ (NSUInteger)maxLogBytes {
    return 5UL * 1024 * 1024;
}

+ (NSInteger)maxUploadImageCount {
    return 3;
}

+ (NSInteger)listPageSize {
    return 10;
}

+ (NSTimeInterval)connectTimeout {
    return 30;
}

+ (NSTimeInterval)readTimeout {
    return 60;
}

+ (NSString *)endpoint:(NSString *)path {
    NSString *base = [[self APIBaseURL] stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if ([base hasSuffix:@"/"]) {
        base = [base substringToIndex:base.length - 1];
    }
    return [base stringByAppendingString:path ?: @""];
}

+ (NSString *)assetUrl:(NSString *)relativePath {
    if (relativePath.length == 0) {
        return @"";
    }
    if ([relativePath hasPrefix:@"http://"] || [relativePath hasPrefix:@"https://"]) {
        return relativePath;
    }
    NSString *rel = [relativePath hasPrefix:@"/"] ? [relativePath substringFromIndex:1] : relativePath;
    return [self endpoint:[NSString stringWithFormat:@"/ai_office/%@", rel]];
}

@end
