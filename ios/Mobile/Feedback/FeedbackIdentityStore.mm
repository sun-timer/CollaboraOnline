// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "FeedbackIdentityStore.h"

#import "FeedbackApiException.h"
#import "FeedbackClient.h"
#import "FeedbackConfig.h"

static NSString *const kFeedbackUserIdKey = @"feedback_identity.user_id";
static NSString *const kFeedbackAvatarServerPathKey = @"feedback_identity.avatar_server_path";
static NSString *const kProfileNameKey = @"USER_PROFILE_NAME";
static NSString *const kAvatarPathKey = @"USER_PROFILE_AVATAR_PATH";
static NSString *const kAvatarFileName = @"ai_profile_avatar.jpg";

static NSUInteger feedbackUnicodeLength(NSString *string) {
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

static NSString *feedbackTruncateToCodePoints(NSString *string, NSUInteger maxPoints) {
    if (string.length == 0 || maxPoints == 0) {
        return @"";
    }
    __block NSUInteger count = 0;
    __block NSRange cutRange = NSMakeRange(0, 0);
    [string enumerateSubstringsInRange:NSMakeRange(0, string.length)
                               options:NSStringEnumerationByComposedCharacterSequences
                            usingBlock:^(NSString *substring, NSRange substringRange, __unused NSRange enclosingRange,
                                         BOOL *stop) {
                                if (count < maxPoints) {
                                    cutRange = NSUnionRange(cutRange, substringRange);
                                    count++;
                                } else {
                                    *stop = YES;
                                }
                            }];
    return [string substringWithRange:cutRange];
}

static dispatch_queue_t feedbackBackgroundQueue(void) {
    static dispatch_queue_t queue;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        queue = dispatch_queue_create("org.collabora.feedback.api", DISPATCH_QUEUE_SERIAL);
    });
    return queue;
}

@implementation FeedbackIdentityStore

+ (NSUserDefaults *)identityDefaults {
    return NSUserDefaults.standardUserDefaults;
}

+ (NSString *)getOrCreateUserId {
    @synchronized(self) {
        NSUserDefaults *prefs = [self identityDefaults];
        NSString *userId = [prefs stringForKey:kFeedbackUserIdKey];
        if (userId.length == 0) {
            NSString *uuid = [[NSUUID UUID] UUIDString];
            uuid = [uuid stringByReplacingOccurrencesOfString:@"-" withString:@""];
            userId = [NSString stringWithFormat:@"aio-%@", uuid];
            if (userId.length > 64) {
                userId = [userId substringToIndex:64];
            }
            [prefs setObject:userId forKey:kFeedbackUserIdKey];
        }
        return userId;
    }
}

+ (NSString *)getNickname {
    NSString *name = [NSUserDefaults.standardUserDefaults stringForKey:kProfileNameKey] ?: @"";
    name = [name stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (feedbackUnicodeLength(name) > 16) {
        name = feedbackTruncateToCodePoints(name, 16);
    }
    return name;
}

+ (NSString *)getAvatarServerPath {
    return [[self identityDefaults] stringForKey:kFeedbackAvatarServerPathKey] ?: @"";
}

+ (void)setAvatarServerPath:(NSString *)relativePath {
    [[self identityDefaults] setObject:relativePath ?: @"" forKey:kFeedbackAvatarServerPathKey];
}

+ (NSURL *)localAvatarFileURL {
    NSString *path = [NSUserDefaults.standardUserDefaults stringForKey:kAvatarPathKey];
    if (path.length > 0) {
        return [NSURL fileURLWithPath:path];
    }
    NSURL *support = [NSFileManager.defaultManager URLsForDirectory:NSApplicationSupportDirectory inDomains:NSUserDomainMask].lastObject;
    [NSFileManager.defaultManager createDirectoryAtURL:support withIntermediateDirectories:YES attributes:nil error:nil];
    return [support URLByAppendingPathComponent:kAvatarFileName];
}

+ (void)uploadAvatarFromLocalFileWithCompletion:(void (^)(NSError *_Nullable))completion {
    if (![FeedbackConfig isConfigured]) {
        if (completion) {
            dispatch_async(dispatch_get_main_queue(), ^{
                completion([FeedbackApiException errorWithReason:@"feedback_api_not_configured" message:@""]);
            });
        }
        return;
    }
    NSURL *localURL = [self localAvatarFileURL];
    if (localURL == nil || ![NSFileManager.defaultManager fileExistsAtPath:localURL.path]) {
        if (completion) {
            dispatch_async(dispatch_get_main_queue(), ^{
                completion(nil);
            });
        }
        return;
    }
    dispatch_async(feedbackBackgroundQueue(), ^{
        NSError *error = nil;
        NSString *userId = [self getOrCreateUserId];
        NSString *path = [FeedbackClient uploadAvatarWithUserId:userId filePath:localURL.path error:&error];
        if (path.length > 0) {
            [self setAvatarServerPath:path];
        }
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion(error);
            }
        });
    });
}

+ (void)uploadAvatarFromLocalFileIfNeededWithCompletion:(void (^)(NSError *_Nullable))completion {
    if (![FeedbackConfig isConfigured]) {
        if (completion) {
            dispatch_async(dispatch_get_main_queue(), ^{
                completion([FeedbackApiException errorWithReason:@"feedback_api_not_configured" message:@""]);
            });
        }
        return;
    }
    NSString *existing = [self getAvatarServerPath];
    if (existing.length > 0) {
        if (completion) {
            dispatch_async(dispatch_get_main_queue(), ^{
                completion(nil);
            });
        }
        return;
    }
    NSURL *localURL = [self localAvatarFileURL];
    if (localURL == nil || ![NSFileManager.defaultManager fileExistsAtPath:localURL.path]) {
        if (completion) {
            dispatch_async(dispatch_get_main_queue(), ^{
                completion(nil);
            });
        }
        return;
    }
    dispatch_async(feedbackBackgroundQueue(), ^{
        NSError *error = nil;
        NSString *userId = [self getOrCreateUserId];
        NSString *path = [FeedbackClient uploadAvatarWithUserId:userId filePath:localURL.path error:&error];
        if (path.length > 0) {
            [self setAvatarServerPath:path];
        }
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion(error);
            }
        });
    });
}

@end
