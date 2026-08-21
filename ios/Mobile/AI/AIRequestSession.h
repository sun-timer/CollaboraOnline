// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface AIRequestSession : NSObject

@property (copy, nonatomic, readonly) NSString *requestId;
@property (copy, nonatomic, readonly) NSString *documentSessionId;

- (instancetype)initWithRequestId:(NSString *)requestId
                documentSessionId:(NSString *)documentSessionId;

- (void)bindTask:(NSURLSessionDataTask *)task;
- (void)cancel;
- (BOOL)isCancelled;
- (BOOL)canEmit;
- (BOOL)markTerminal;

- (NSArray<NSString *> *)consumeLinesFromData:(NSData *)data;
- (NSArray<NSString *> *)consumePendingLines;
- (void)appendDelta:(NSString *)delta;
- (NSString *)fullText;

@end

NS_ASSUME_NONNULL_END

// vim:set shiftwidth=4 softtabstop=4 expandtab:
