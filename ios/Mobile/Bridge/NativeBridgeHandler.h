// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <WebKit/WebKit.h>

typedef NSString * (^NativeBridgeSessionIdProvider)(void);
typedef void (^NativeBridgeMessageEmitter)(NSDictionary *message);

@interface NativeBridgeHandler : NSObject <WKScriptMessageHandler>

- (instancetype)initWithSessionIdProvider:(NativeBridgeSessionIdProvider)sessionIdProvider
                                  emitter:(NativeBridgeMessageEmitter)emitter;

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
