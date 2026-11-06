// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "ClearCacheHelper.h"

#import <sys/mount.h>

@implementation ClearCacheCategorySize

- (int64_t)totalAppBytes {
    return self.tempFilesBytes + self.imageCacheBytes + self.aiPreviewBytes + self.chatHistoryBytes + self.offlineModelBytes;
}

- (int64_t)clearableBytesForMode:(ClearCacheCleanMode)mode {
    int64_t quick = self.tempFilesBytes + self.imageCacheBytes + self.aiPreviewBytes;
    if (mode == ClearCacheCleanModeQuick) {
        return quick;
    }
    return quick + self.chatHistoryBytes + self.offlineModelBytes;
}

@end

@implementation ClearCacheStorageInfo
@end

@implementation ClearCacheHelper

+ (int64_t)dirSizeAtURL:(NSURL *)dirURL {
    if (dirURL == nil) {
        return 0;
    }
    NSFileManager *fm = [NSFileManager defaultManager];
    BOOL isDir = NO;
    if (![fm fileExistsAtPath:dirURL.path isDirectory:&isDir] || !isDir) {
        return 0;
    }
    NSDirectoryEnumerator *enumerator = [fm enumeratorAtURL:dirURL
                               includingPropertiesForKeys:@[ NSURLIsRegularFileKey, NSURLFileSizeKey ]
                                                  options:NSDirectoryEnumerationSkipsHiddenFiles
                                             errorHandler:nil];
    int64_t total = 0;
    for (NSURL *url in enumerator) {
        NSNumber *isFile = nil;
        [url getResourceValue:&isFile forKey:NSURLIsRegularFileKey error:nil];
        if (!isFile.boolValue) {
            continue;
        }
        NSNumber *size = nil;
        [url getResourceValue:&size forKey:NSURLFileSizeKey error:nil];
        total += size.longLongValue;
    }
    return total;
}

+ (void)splitCacheDir:(NSURL *)dirURL temp:(int64_t *)temp image:(int64_t *)image preview:(int64_t *)preview {
    if (dirURL == nil) {
        return;
    }
    NSFileManager *fm = [NSFileManager defaultManager];
    NSArray<NSURL *> *children = [fm contentsOfDirectoryAtURL:dirURL includingPropertiesForKeys:@[ NSURLIsDirectoryKey ] options:0 error:nil];
    for (NSURL *child in children) {
        NSNumber *isDir = nil;
        [child getResourceValue:&isDir forKey:NSURLIsDirectoryKey error:nil];
        NSString *name = child.lastPathComponent.lowercaseString;
        if (isDir.boolValue) {
            if ([name containsString:@"preview"] || [name containsString:@"ai_gen"]) {
                *preview += [self dirSizeAtURL:child];
            } else {
                int64_t nestedTemp = 0, nestedImage = 0, nestedPreview = 0;
                [self splitCacheDir:child temp:&nestedTemp image:&nestedImage preview:&nestedPreview];
                *temp += nestedTemp;
                *image += nestedImage;
                *preview += nestedPreview;
            }
            continue;
        }
        NSNumber *size = nil;
        [child getResourceValue:&size forKey:NSURLFileSizeKey error:nil];
        int64_t len = size.longLongValue;
        if ([name hasPrefix:@"insert_img_"] || [name hasPrefix:@"avatar_"] || [name hasPrefix:@"text_extract_"]
            || [name hasSuffix:@".jpg"] || [name hasSuffix:@".jpeg"] || [name hasSuffix:@".png"] || [name hasSuffix:@".webp"]) {
            *image += len;
        } else if ([name containsString:@"ai_preview"] || [name hasPrefix:@"ai_gen_"]) {
            *preview += len;
        } else {
            *temp += len;
        }
    }
}

+ (ClearCacheStorageInfo *)scan {
    ClearCacheCategorySize *categories = [[ClearCacheCategorySize alloc] init];
    NSURL *cache = [NSFileManager.defaultManager URLsForDirectory:NSCachesDirectory inDomains:NSUserDomainMask].lastObject;
    NSURL *support = [NSFileManager.defaultManager URLsForDirectory:NSApplicationSupportDirectory inDomains:NSUserDomainMask].lastObject;
    int64_t temp = 0, image = 0, preview = 0;
    [self splitCacheDir:cache temp:&temp image:&image preview:&preview];
    [self splitCacheDir:[NSURL fileURLWithPath:NSTemporaryDirectory()] temp:&temp image:&image preview:&preview];
    categories.tempFilesBytes = temp;
    categories.imageCacheBytes = image;
    categories.aiPreviewBytes = preview;
    categories.chatHistoryBytes = [self dirSizeAtURL:[support URLByAppendingPathComponent:@"ai_history" isDirectory:YES]];
    categories.offlineModelBytes = 0; // 由 offlineModelBytes 异步补齐

    ClearCacheStorageInfo *info = [[ClearCacheStorageInfo alloc] init];
    info.categories = categories;
    [self fillPhoneStorageInto:info];
    return info;
}

/// 手机整机容量：跟「设置 → 通用 → iPhone 储存空间」对齐。
/// statfs 的 f_bavail 只算「立刻能写」的空间，不含系统可清除的缓存/快照，
/// 于是「已用」会虚高几十 GB（实测 160.2 GiB vs 设置里的 130.69 GB）；
/// 这里改用卷资源值，available 取 AvailableCapacityForImportantUsage（含可清除空间）。
+ (void)fillPhoneStorageInto:(ClearCacheStorageInfo *)info {
    NSURL *home = [NSURL fileURLWithPath:NSHomeDirectory() isDirectory:YES];
    NSDictionary<NSURLResourceKey, id> *values =
        [home resourceValuesForKeys:@[ NSURLVolumeTotalCapacityKey,
                                       NSURLVolumeAvailableCapacityForImportantUsageKey ]
                              error:nil];
    int64_t total = [values[NSURLVolumeTotalCapacityKey] longLongValue];
    int64_t available = [values[NSURLVolumeAvailableCapacityForImportantUsageKey] longLongValue];
    if (total > 0 && available >= 0) {
        info.phoneTotalBytes = total;
        info.phoneUsedBytes = MAX(total - available, (int64_t)0);
        return;
    }
    // 兜底：资源值拿不到时退回 statfs（free 用 f_bfree，不含 root 保留块）
    struct statfs stats;
    if (statfs(NSHomeDirectory().UTF8String, &stats) == 0) {
        info.phoneTotalBytes = (int64_t)stats.f_blocks * stats.f_bsize;
        info.phoneUsedBytes = MAX(info.phoneTotalBytes - (int64_t)stats.f_bfree * stats.f_bsize, (int64_t)0);
    }
}

+ (int64_t)deleteDirContents:(NSURL *)dirURL {
    if (dirURL == nil) {
        return 0;
    }
    NSFileManager *fm = [NSFileManager defaultManager];
    NSArray<NSURL *> *children = [fm contentsOfDirectoryAtURL:dirURL includingPropertiesForKeys:@[ NSURLFileSizeKey ] options:0 error:nil];
    int64_t freed = 0;
    for (NSURL *child in children) {
        NSNumber *isDir = nil;
        [child getResourceValue:&isDir forKey:NSURLIsDirectoryKey error:nil];
        if (isDir.boolValue) {
            freed += [self deleteDirContents:child];
        } else {
            NSNumber *size = nil;
            [child getResourceValue:&size forKey:NSURLFileSizeKey error:nil];
            freed += size.longLongValue;
        }
        [fm removeItemAtURL:child error:nil];
    }
    return freed;
}

+ (int64_t)clearWithMode:(ClearCacheCleanMode)mode {
    int64_t freed = 0;
    NSURL *cache = [NSFileManager.defaultManager URLsForDirectory:NSCachesDirectory inDomains:NSUserDomainMask].lastObject;
    freed += [self deleteDirContents:cache];
    freed += [self deleteDirContents:[NSURL fileURLWithPath:NSTemporaryDirectory()]];
    if (mode == ClearCacheCleanModeDeep) {
        NSURL *support = [NSFileManager.defaultManager URLsForDirectory:NSApplicationSupportDirectory inDomains:NSUserDomainMask].lastObject;
        freed += [self deleteDirContents:[support URLByAppendingPathComponent:@"ai_history" isDirectory:YES]];
        freed += [self deleteDirContents:[support URLByAppendingPathComponent:@"models" isDirectory:YES]];
    }
    return freed;
}

+ (int64_t)offlineModelBytes {
    NSURL *support = [NSFileManager.defaultManager URLsForDirectory:NSApplicationSupportDirectory inDomains:NSUserDomainMask].lastObject;
    return [self dirSizeAtURL:[support URLByAppendingPathComponent:@"models" isDirectory:YES]];
}

+ (NSString *)formatSize:(int64_t)bytes {
    if (bytes <= 0) {
        return @"0 MB";
    }
    double mb = bytes / (1024.0 * 1024.0);
    if (mb >= 1024.0) {
        return [NSString stringWithFormat:@"%.1f GB", mb / 1024.0];
    }
    if (mb >= 100.0) {
        return [NSString stringWithFormat:@"%.0f MB", mb];
    }
    return [NSString stringWithFormat:@"%.1f MB", mb];
}

+ (NSString *)trimWhole:(double)value {
    NSString *text = [NSString stringWithFormat:@"%.1f", value];
    if ([text hasSuffix:@".0"]) {
        text = [text substringToIndex:text.length - 2];
    }
    return text;
}

+ (NSString *)formatStorageSize:(int64_t)bytes {
    // 十进制（1 GB = 1000³ B），跟 iOS 设置里的「已使用 130.69 GB/256 GB」写法一致；
    // 整 GB 时去掉小数尾巴，256.0 GB → 256 GB。
    if (bytes <= 0) {
        return @"0 MB";
    }
    if (bytes >= (int64_t)1e9) {
        double gb = bytes / 1e9;
        return [NSString stringWithFormat:@"%@ GB", [self trimWhole:gb]];
    }
    double mb = bytes / 1e6;
    return [NSString stringWithFormat:@"%@ MB", [self trimWhole:mb]];
}

@end
