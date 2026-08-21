// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

@class AIConfigurationStore;

typedef void (^AIServiceEventEmitter)(NSString * _Nonnull type,
                                      NSString * _Nonnull requestId,
                                      NSString * _Nonnull documentSessionId,
                                      NSDictionary * _Nonnull payload);

NS_ASSUME_NONNULL_BEGIN

@interface AIService : NSObject <NSURLSessionDataDelegate, NSURLSessionTaskDelegate>

- (instancetype)init;
- (instancetype)initWithConfigurationStore:(AIConfigurationStore *)configurationStore;

- (void)startRequest:(NSDictionary *)payload
           requestId:(NSString *)requestId
  documentSessionId:(NSString *)documentSessionId
               emit:(AIServiceEventEmitter)emit;

- (void)cancelRequest:(NSString *)requestId
   documentSessionId:(NSString *)documentSessionId;

- (void)cancelRequestsForDocumentSession:(NSString *)documentSessionId;

@end

NS_ASSUME_NONNULL_END

// vim:set shiftwidth=4 softtabstop=4 expandtab:
