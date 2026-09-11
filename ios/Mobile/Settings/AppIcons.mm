// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AppIcons.h"

#import "AI/WriterAIComponents.h"

@implementation AppIcons

+ (nullable UIImage *)iconNamed:(NSString *)name {
    if (name.length == 0) {
        return nil;
    }
    static NSDictionary<NSString *, NSString *> *homeAssets;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        homeAssets = @{
            @"search": @"HomeSearch",
            @"folder": @"HomeFolder",
            @"more": @"HomeMoreDots",
            @"fab": @"HomeFab",
            @"fab-close": @"HomeFabClose",
            @"avatar": @"HomeAvatar",
            @"file-writer": @"HomeFileWriter",
            @"file-calc": @"HomeFileCalc",
            @"file-impress": @"HomeFileImpress",
            @"empty-recent": @"HomeEmptyRecent",
            @"empty-search": @"HomeEmptySearch",
            @"model-base": @"AiModelBase",
            @"model-think": @"AiModelThink",
            @"model-image": @"AiModelImage",
            @"config-header": @"AiConfigHeader",
        };
    });
    NSString *asset = homeAssets[name];
    if (asset.length > 0) {
        UIImage *image = [UIImage imageNamed:asset];
        if (image != nil) {
            return image;
        }
    }
    if ([name isEqualToString:@"action-share"]) {
        return [UIImage writerIconNamed:@"share"];
    }
    return [UIImage writerIconNamed:name];
}

+ (nullable UIImage *)iconNamed:(NSString *)name size:(CGFloat)size {
    UIImage *image = [self iconNamed:name];
    if (image == nil || size <= 0) {
        return image;
    }
    if (fabs(image.size.width - size) < 0.5 && fabs(image.size.height - size) < 0.5) {
        return image;
    }
    UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:CGSizeMake(size, size)];
    return [renderer imageWithActions:^(UIGraphicsImageRendererContext *context) {
        [image drawInRect:CGRectMake(0, 0, size, size)];
    }];
}

@end
