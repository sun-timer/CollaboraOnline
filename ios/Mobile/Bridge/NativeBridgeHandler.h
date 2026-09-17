// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <WebKit/WebKit.h>

@class AIService;
@class AiConversationStore;

typedef NSString * (^NativeBridgeSessionIdProvider)(void);
typedef AiConversationStore * _Nullable (^NativeBridgeConversationStoreProvider)(void);
typedef void (^NativeBridgeMessageEmitter)(NSDictionary *message);
typedef NSURL * _Nullable (^NativeBridgeDocumentURLProvider)(void);
typedef void (^NativeBridgeReloadDocumentHandler)(void);
typedef void (^NativeBridgeDocumentTextCompletion)(NSString * _Nullable text);
typedef void (^NativeBridgeDocumentTextExtractor)(NativeBridgeDocumentTextCompletion completion);

@interface NativeBridgeHandler : NSObject <WKScriptMessageHandler>

@property (copy, nonatomic, nullable) NativeBridgeDocumentURLProvider documentFileURLProvider;
@property (copy, nonatomic, nullable) NativeBridgeDocumentURLProvider originalDocumentURLProvider;
@property (copy, nonatomic, nullable) NativeBridgeReloadDocumentHandler reloadDocumentHandler;
@property (copy, nonatomic, nullable) NativeBridgeConversationStoreProvider conversationStoreProvider;
@property (copy, nonatomic, nullable) NativeBridgeDocumentTextExtractor documentTextExtractor;

- (instancetype)initWithSessionIdProvider:(NativeBridgeSessionIdProvider)sessionIdProvider
                                  emitter:(NativeBridgeMessageEmitter)emitter;

- (instancetype)initWithSessionIdProvider:(NativeBridgeSessionIdProvider)sessionIdProvider
                                  emitter:(NativeBridgeMessageEmitter)emitter
                               aiService:(AIService *)aiService;

- (void)cancelAllRequests;

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
