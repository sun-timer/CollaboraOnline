// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "ClearCacheHelper.h"

#import <sys/statfs.h>

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
    categories.offlineModelBytes = [self dirSizeAtURL:[support URLByAppendingPathComponent:@"models" isDirectory:YES]];

    ClearCacheStorageInfo *info = [[ClearCacheStorageInfo alloc] init];
    info.categories = categories;
    struct statfs stats;
    if (statfs(NSHomeDirectory().UTF8String, &stats) == 0) {
        info.phoneTotalBytes = (int64_t)stats.f_blocks * stats.f_bsize;
        info.phoneUsedBytes = info.phoneTotalBytes - ((int64_t)stats.f_bavail * stats.f_bsize);
    }
    return info;
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

@end
