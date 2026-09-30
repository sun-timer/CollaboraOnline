// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "FeedbackApi.h"

#import "FeedbackApiException.h"
#import "FeedbackClient.h"
#import "FeedbackConfig.h"
#import "FeedbackIdentityStore.h"
#import "FeedbackLogExporter.h"
#import "FeedbackRecord.h"

static NSUInteger feedbackContentLength(NSString *string) {
    if (string.length == 0) {
        return 0;
    }
    __block NSUInteger count = 0;
    [string enumerateSubstringsInRange:NSMakeRange(0, string.length)
                               options:NSStringEnumerationByComposedCharacterSequences
                            usingBlock:^(__unused NSString *substring, __unused NSRange substringRange,
                                         __unused NSRange enclosingRange, __unused BOOL *stop) {
                                count++;
                            }];
    return count;
}

static dispatch_queue_t feedbackApiBackgroundQueue(void) {
    static dispatch_queue_t queue;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        queue = dispatch_queue_create("org.collabora.feedback.api", DISPATCH_QUEUE_SERIAL);
    });
    return queue;
}

@implementation FeedbackApiListPage
@end

@implementation FeedbackApi

+ (BOOL)isConfigured {
    return [FeedbackConfig isConfigured];
}

+ (void)submitFormWithFeedbackType:(NSString *)feedbackType
                           content:(NSString *)content
                           contact:(NSString *)contact
                        imagePaths:(NSArray<NSString *> *)imagePaths
                 shareLogRequested:(BOOL)shareLogRequested
                        completion:(void (^)(FeedbackRecord *_Nullable, NSError *_Nullable))completion {
    if (![self isConfigured]) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion(nil, [FeedbackApiException errorWithReason:@"feedback_api_not_configured" message:@""]);
            }
        });
        return;
    }
    if (shareLogRequested && ![FeedbackConfig isLogUploadEnabled]) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion(nil, [FeedbackApiException errorWithReason:@"feedback_log_upload_pending" message:@""]);
            }
        });
        return;
    }
    NSString *trimmed = [content stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]] ?: @"";
    NSUInteger len = feedbackContentLength(trimmed);
    if (len < 10 || len > 500) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion(nil, [FeedbackApiException errorWithReason:@"feedback_validation" message:@"content length"]);
            }
        });
        return;
    }
    NSArray<NSString *> *paths = imagePaths ?: @[];
    if (paths.count > [FeedbackConfig maxUploadImageCount]) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion(nil, [FeedbackApiException errorWithReason:@"feedback_validation" message:@"too many images"]);
            }
        });
        return;
    }

    dispatch_async(feedbackApiBackgroundQueue(), ^{
        NSError *error = nil;
        NSString *userId = [FeedbackIdentityStore getOrCreateUserId];
        NSString *nickname = [FeedbackIdentityStore getNickname];
        NSString *avatar = [FeedbackIdentityStore getAvatarServerPath];
        NSURL *localAvatar = [FeedbackIdentityStore localAvatarFileURL];
        if (avatar.length == 0 && localAvatar != nil
            && [NSFileManager.defaultManager fileExistsAtPath:localAvatar.path]) {
            NSError *avatarError = nil;
            NSString *uploadedAvatar = [FeedbackClient uploadAvatarWithUserId:userId
                                                                     filePath:localAvatar.path
                                                                        error:&avatarError];
            if (uploadedAvatar.length > 0) {
                avatar = uploadedAvatar;
                [FeedbackIdentityStore setAvatarServerPath:avatar];
            }
            // 头像上传失败不阻断反馈提交（与资料页异步上传解耦）
            (void)avatarError;
        }

        NSArray<NSString *> *uploadedPaths = @[];
        if (paths.count > 0) {
            uploadedPaths = [FeedbackClient uploadImagesWithFilePaths:paths error:&error];
            if (error != nil) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    if (completion) {
                        completion(nil, error);
                    }
                });
                return;
            }
        }

        NSString *logPath = nil;
        if (shareLogRequested) {
            NSURL *logFile = [FeedbackLogExporter exportLogFileURLWithError:&error];
            if (error != nil) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    if (completion) {
                        completion(nil, error);
                    }
                });
                return;
            }
            logPath = [FeedbackClient uploadLogWithUserId:userId fileURL:logFile error:&error];
            if (logFile != nil) {
                [NSFileManager.defaultManager removeItemAtURL:logFile error:nil];
            }
            if (error != nil) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    if (completion) {
                        completion(nil, error);
                    }
                });
                return;
            }
        }

        FeedbackRecord *record = [FeedbackClient submitWithUserId:userId
                                                         nickname:nickname
                                                           avatar:avatar
                                                     feedbackType:feedbackType
                                                          content:trimmed
                                                          contact:contact ?: @""
                                                       imagePaths:uploadedPaths
                                                          logPath:logPath
                                                       appVersion:[FeedbackClient appVersion]
                                                      deviceModel:[FeedbackClient deviceModel]
                                                        osVersion:[FeedbackClient osVersion]
                                                            error:&error];
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion(record, error);
            }
        });
    });
}

+ (void)fetchListPage:(NSInteger)pageNum
           completion:(void (^)(FeedbackApiListPage *_Nullable, NSError *_Nullable))completion {
    if (![self isConfigured]) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion(nil, [FeedbackApiException errorWithReason:@"feedback_api_not_configured" message:@""]);
            }
        });
        return;
    }
    dispatch_async(feedbackApiBackgroundQueue(), ^{
        NSError *error = nil;
        NSString *userId = [FeedbackIdentityStore getOrCreateUserId];
        NSString *nickname = [FeedbackIdentityStore getNickname];
        NSString *avatar = [FeedbackIdentityStore getAvatarServerPath];
        FeedbackListPage *raw = [FeedbackClient listWithUserId:userId
                                                      nickname:nickname
                                                        avatar:avatar
                                                       pageNum:pageNum
                                                      pageSize:[FeedbackConfig listPageSize]
                                                         error:&error];
        FeedbackApiListPage *page = nil;
        if (raw != nil) {
            page = [[FeedbackApiListPage alloc] init];
            page.pageNum = raw.pageNum;
            page.pageSize = raw.pageSize;
            page.total = raw.total;
            page.pages = raw.pages;
            page.list = raw.list ?: @[];
        }
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion(page, error);
            }
        });
    });
}

+ (void)fetchDetail:(NSString *)feedbackNo
         completion:(void (^)(FeedbackRecord *_Nullable, NSError *_Nullable))completion {
    if (![self isConfigured]) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion(nil, [FeedbackApiException errorWithReason:@"feedback_api_not_configured" message:@""]);
            }
        });
        return;
    }
    dispatch_async(feedbackApiBackgroundQueue(), ^{
        NSError *error = nil;
        NSString *userId = [FeedbackIdentityStore getOrCreateUserId];
        NSString *nickname = [FeedbackIdentityStore getNickname];
        NSString *avatar = [FeedbackIdentityStore getAvatarServerPath];
        FeedbackRecord *record = [FeedbackClient detailWithUserId:userId
                                                       nickname:nickname
                                                         avatar:avatar
                                                     feedbackNo:feedbackNo
                                                          error:&error];
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion(record, error);
            }
        });
    });
}

+ (void)closeFeedback:(NSString *)feedbackNo
           completion:(void (^)(NSError *_Nullable))completion {
    if (![self isConfigured]) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion([FeedbackApiException errorWithReason:@"feedback_api_not_configured" message:@""]);
            }
        });
        return;
    }
    dispatch_async(feedbackApiBackgroundQueue(), ^{
        NSError *error = nil;
        NSString *userId = [FeedbackIdentityStore getOrCreateUserId];
        NSString *nickname = [FeedbackIdentityStore getNickname];
        NSString *avatar = [FeedbackIdentityStore getAvatarServerPath];
        [FeedbackClient closeWithUserId:userId nickname:nickname avatar:avatar feedbackNo:feedbackNo error:&error];
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion(error);
            }
        });
    });
}

@end
