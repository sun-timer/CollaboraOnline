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

/// 快速扫描：临时文件 / 图片缓存 / AI 预览图 / 历史对话记录。
/// 离线模型缓存目录可能上万文件，单独走 offlineModelBytes 异步取，
/// 页面先显示「计算中...」再回填（对齐设计稿 403:20606 的加载态）。
+ (ClearCacheStorageInfo *)scan;

/// 只统计「离线模型缓存」目录，可能较慢，请在后台线程调用。
+ (int64_t)offlineModelBytes;

+ (int64_t)clearWithMode:(ClearCacheCleanMode)mode;
+ (NSString *)formatSize:(int64_t)bytes;

/// 手机整机容量口径，与「设置 → 通用 → iPhone 储存空间」一致（十进制 GB，
/// 可用空间用 NSURLVolumeAvailableCapacityForImportantUsageKey，含系统可回收空间）。
+ (NSString *)formatStorageSize:(int64_t)bytes;

@end

NS_ASSUME_NONNULL_END
