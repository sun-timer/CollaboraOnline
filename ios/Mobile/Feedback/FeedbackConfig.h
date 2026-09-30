// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface FeedbackConfig : NSObject

+ (NSString *)APIBaseURL;
+ (BOOL)LOGUploadEnabled;
+ (BOOL)isLogUploadEnabled;
+ (BOOL)isConfigured;

+ (NSString *)PATHUploadAvatar;
+ (NSString *)PATHUploadImages;
+ (NSString *)PATHUploadLog;
+ (NSString *)PATHSubmit;
+ (NSString *)PATHList;
+ (NSString *)PATHDetail;
+ (NSString *)PATHClose;

+ (NSInteger)uploadImageMaxEdgePx;
+ (CGFloat)uploadImageJPEGQuality;
+ (NSUInteger)maxUploadImageBytes;
+ (NSUInteger)maxLogBytes;
+ (NSInteger)maxUploadImageCount;
+ (NSInteger)listPageSize;

+ (NSTimeInterval)connectTimeout;
+ (NSTimeInterval)readTimeout;

+ (NSString *)endpoint:(NSString *)path;
+ (NSString *)assetUrl:(NSString *)relativePath;

@end

NS_ASSUME_NONNULL_END
