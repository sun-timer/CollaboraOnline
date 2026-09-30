// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface FeedbackIdentityStore : NSObject

+ (NSString *)getOrCreateUserId;
+ (NSString *)getNickname;
+ (NSString *)getAvatarServerPath;
+ (void)setAvatarServerPath:(NSString *)relativePath;

+ (nullable NSURL *)localAvatarFileURL;

+ (void)uploadAvatarFromLocalFileIfNeededWithCompletion:(void (^)(NSError *_Nullable error))completion;
+ (void)uploadAvatarFromLocalFileWithCompletion:(void (^)(NSError *_Nullable error))completion;

@end

NS_ASSUME_NONNULL_END
