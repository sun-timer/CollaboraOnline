// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, ClearCacheCleanMode) {
    ClearCacheCleanModeQuick,
    ClearCacheCleanModeDeep,
};

@interface ClearCacheCategorySize : NSObject
@property (assign, nonatomic) int64_t tempFilesBytes;
@property (assign, nonatomic) int64_t imageCacheBytes;
@property (assign, nonatomic) int64_t aiPreviewBytes;
@property (assign, nonatomic) int64_t chatHistoryBytes;
@property (assign, nonatomic) int64_t offlineModelBytes;
- (int64_t)totalAppBytes;
- (int64_t)clearableBytesForMode:(ClearCacheCleanMode)mode;
@end

@interface ClearCacheStorageInfo : NSObject
@property (strong, nonatomic) ClearCacheCategorySize *categories;
@property (assign, nonatomic) int64_t phoneUsedBytes;
@property (assign, nonatomic) int64_t phoneTotalBytes;
@end

@interface ClearCacheHelper : NSObject

+ (ClearCacheStorageInfo *)scan;
+ (int64_t)clearWithMode:(ClearCacheCleanMode)mode;
+ (NSString *)formatSize:(int64_t)bytes;

@end

NS_ASSUME_NONNULL_END
