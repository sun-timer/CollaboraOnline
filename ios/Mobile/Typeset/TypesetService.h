// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface TypesetService : NSObject

+ (nullable NSDictionary *)extractStructuredFromFile:(NSURL *)fileURL;
+ (nullable NSURL *)fillTemplateWithType:(NSString *)typesetType
                               sections:(NSDictionary<NSString *, NSString *> *)sections
                             sourceName:(nullable NSString *)sourceName;
+ (BOOL)copyDocxToOriginalURL:(NSURL *)docxURL originalURL:(nullable NSURL *)originalURL;

@end

NS_ASSUME_NONNULL_END

// vim:set shiftwidth=4 softtabstop=4 expandtab:
