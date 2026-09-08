// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <WebKit/WebKit.h>

@class AIService;

typedef NSString * (^NativeBridgeSessionIdProvider)(void);
typedef void (^NativeBridgeMessageEmitter)(NSDictionary *message);
typedef NSURL * _Nullable (^NativeBridgeDocumentURLProvider)(void);
typedef void (^NativeBridgeReloadDocumentHandler)(void);

@interface NativeBridgeHandler : NSObject <WKScriptMessageHandler>

@property (copy, nonatomic, nullable) NativeBridgeDocumentURLProvider documentFileURLProvider;
@property (copy, nonatomic, nullable) NativeBridgeDocumentURLProvider originalDocumentURLProvider;
@property (copy, nonatomic, nullable) NativeBridgeReloadDocumentHandler reloadDocumentHandler;

- (instancetype)initWithSessionIdProvider:(NativeBridgeSessionIdProvider)sessionIdProvider
                                  emitter:(NativeBridgeMessageEmitter)emitter;

- (instancetype)initWithSessionIdProvider:(NativeBridgeSessionIdProvider)sessionIdProvider
                                  emitter:(NativeBridgeMessageEmitter)emitter
                               aiService:(AIService *)aiService;

- (void)cancelAllRequests;

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
