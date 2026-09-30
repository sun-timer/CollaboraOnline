// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface FeedbackLogExporter : NSObject

/** 缓存目录下的 .txt；上传成功后由调用方删除 */
+ (nullable NSURL *)exportLogFileURLWithError:(NSError **)error;

@end

NS_ASSUME_NONNULL_END
