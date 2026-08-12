// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "NativeBridgeHandler.h"

static const NSInteger kNativeBridgeProtocolVersion = 1;

@interface NativeBridgeHandler ()
@property (copy, nonatomic) NativeBridgeSessionIdProvider sessionIdProvider;
@property (copy, nonatomic) NativeBridgeMessageEmitter emitter;
@property (copy, nonatomic) NSString *nativeSessionId;
@property (strong, nonatomic) NSMutableDictionary<NSString *, NSString *> *requestSessions;
@end

@implementation NativeBridgeHandler

- (instancetype)initWithSessionIdProvider:(NativeBridgeSessionIdProvider)sessionIdProvider
                                  emitter:(NativeBridgeMessageEmitter)emitter {
    self = [super init];
    if (self) {
        _sessionIdProvider = [sessionIdProvider copy];
        _emitter = [emitter copy];
        _nativeSessionId = _sessionIdProvider ? [_sessionIdProvider() copy] : nil;
        if (_nativeSessionId.length == 0) {
            _nativeSessionId = [[NSUUID UUID] UUIDString];
        }
        _requestSessions = [[NSMutableDictionary alloc] init];
    }
    return self;
}

- (void)userContentController:(WKUserContentController *)userContentController
      didReceiveScriptMessage:(WKScriptMessage *)message {
    if (![NSThread isMainThread]) {
        __weak NativeBridgeHandler *weakSelf = self;
        dispatch_async(dispatch_get_main_queue(), ^{
            [weakSelf userContentController:userContentController didReceiveScriptMessage:message];
        });
        return;
    }

    NSDictionary *envelope = [self dictionaryFromMessageBody:message.body];
    if (envelope == nil) {
        [self emitErrorType:@"native.error"
                  requestId:nil
            documentSessionId:nil
                       code:@"invalid_payload"
                    message:@"NativeBridge payload must be a JSON object"];
        return;
    }

    NSInteger version = [envelope[@"protocolVersion"] integerValue];
    NSString *channel = [envelope[@"channel"] isKindOfClass:[NSString class]]
                            ? envelope[@"channel"]
                            : @"";
    NSString *type = [envelope[@"type"] isKindOfClass:[NSString class]]
                         ? envelope[@"type"]
                         : @"";
    NSString *requestId = [envelope[@"requestId"] isKindOfClass:[NSString class]]
                              ? envelope[@"requestId"]
                              : nil;
    NSString *documentSessionId = [envelope[@"documentSessionId"] isKindOfClass:[NSString class]]
                                      ? envelope[@"documentSessionId"]
                                      : nil;
    NSString *targetPlatform = [envelope[@"targetPlatform"] isKindOfClass:[NSString class]]
                                   ? envelope[@"targetPlatform"]
                                   : @"any";

    if (version != kNativeBridgeProtocolVersion) {
        [self emitErrorType:@"native.error"
                  requestId:requestId
            documentSessionId:documentSessionId
                       code:@"unsupported_version"
                    message:@"Unsupported NativeBridge protocol version"];
        return;
    }
    if (![channel isEqualToString:@"native"]) {
        [self emitErrorType:@"native.error"
                  requestId:requestId
            documentSessionId:documentSessionId
                       code:@"invalid_channel"
                    message:@"NativeBridge channel must be native"];
        return;
    }
    if ([targetPlatform isEqualToString:@"android"]) {
        [self emitErrorType:@"native.error"
                  requestId:requestId
            documentSessionId:documentSessionId
                       code:@"target_platform_mismatch"
                    message:@"Message targets Android"];
        return;
    }
    if (![targetPlatform isEqualToString:@"any"] && ![targetPlatform isEqualToString:@"ios"]) {
        [self emitErrorType:@"native.error"
                  requestId:requestId
            documentSessionId:documentSessionId
                       code:@"invalid_target_platform"
                    message:@"Invalid NativeBridge target platform"];
        return;
    }

    NSSet<NSString *> *supportedTypes = [NSSet setWithArray:@[
        @"native.ready", @"ai.request", @"ai.cancel", @"ai.accept",
        @"ai.state", @"ai.stream", @"ai.done", @"ai.error"
    ]];
    if (![supportedTypes containsObject:type]) {
        [self emitErrorType:@"native.error"
                  requestId:requestId
            documentSessionId:documentSessionId
                       code:@"unsupported_type"
                    message:@"Unsupported NativeBridge message type"];
        return;
    }
    if ([self containsCredential:envelope[@"payload"]]) {
        [self emitErrorType:@"ai.error"
                  requestId:requestId
            documentSessionId:documentSessionId
                       code:@"sensitive_field"
                    message:@"API credentials must remain in the native secure store"];
        return;
    }
    if ([type hasPrefix:@"ai."] && requestId.length == 0) {
        [self emitErrorType:@"native.error"
                  requestId:nil
            documentSessionId:documentSessionId
                       code:@"missing_request_id"
                    message:@"AI messages require requestId"];
        return;
    }

    if ([type isEqualToString:@"native.ready"]) {
        [self emitEnvelopeType:@"native.ready"
                      requestId:nil
                documentSessionId:self.nativeSessionId
                           payload:@{@"state": @"ready"}];
        return;
    }
    if ([type isEqualToString:@"ai.request"]) {
        if (![envelope[@"payload"] isKindOfClass:[NSDictionary class]]) {
            [self emitErrorType:@"ai.error"
                      requestId:requestId
                documentSessionId:documentSessionId
                           code:@"invalid_payload"
                        message:@"ai.request payload must be an object"];
            return;
        }
        NSString *effectiveSessionId = documentSessionId.length > 0
                                           ? documentSessionId
                                           : self.nativeSessionId;
        NSString *knownSessionId = self.requestSessions[requestId];
        if (knownSessionId.length > 0 && ![knownSessionId isEqualToString:effectiveSessionId]) {
            [self emitErrorType:@"ai.error"
                      requestId:requestId
                documentSessionId:effectiveSessionId
                           code:@"session_mismatch"
                        message:@"The document session does not match the request"];
            return;
        }
        self.requestSessions[requestId] = effectiveSessionId;
        [self emitEnvelopeType:@"ai.state"
                      requestId:requestId
                documentSessionId:effectiveSessionId
                           payload:@{@"state": @"loading"}];
        [self emitEnvelopeType:@"ai.stream"
                      requestId:requestId
                documentSessionId:effectiveSessionId
                           payload:@{@"state": @"streaming",
                                     @"delta": @"[iOS NativeBridge stub]"}];
        [self emitEnvelopeType:@"ai.done"
                      requestId:requestId
                documentSessionId:effectiveSessionId
                           payload:@{@"state": @"ready",
                                     @"fullText": @"[iOS NativeBridge stub]"}];
        [self.requestSessions removeObjectForKey:requestId];
        return;
    }
    if ([type isEqualToString:@"ai.cancel"] || [type isEqualToString:@"ai.accept"]) {
        NSString *knownSessionId = self.requestSessions[requestId];
        NSString *effectiveSessionId = documentSessionId.length > 0
                                           ? documentSessionId
                                           : knownSessionId;
        if (knownSessionId.length == 0 || effectiveSessionId.length == 0
            || ![knownSessionId isEqualToString:effectiveSessionId]) {
            [self emitErrorType:@"ai.error"
                      requestId:requestId
                documentSessionId:effectiveSessionId
                           code:@"session_mismatch"
                        message:@"The document session does not match the request"];
            return;
        }
        if ([type isEqualToString:@"ai.cancel"]) {
            [self emitEnvelopeType:@"ai.state"
                          requestId:requestId
                    documentSessionId:effectiveSessionId
                               payload:@{@"state": @"cancelled"}];
        } else {
            [self emitEnvelopeType:@"ai.state"
                          requestId:requestId
                    documentSessionId:effectiveSessionId
                               payload:@{@"state": @"ready", @"accepted": @YES}];
        }
        [self.requestSessions removeObjectForKey:requestId];
        return;
    }

    [self emitErrorType:@"native.error"
              requestId:requestId
        documentSessionId:documentSessionId
                   code:@"unsupported_request_type"
                message:@"This NativeBridge endpoint only accepts AI requests"];
}

- (NSDictionary *)dictionaryFromMessageBody:(id)body {
    if ([body isKindOfClass:[NSDictionary class]]) {
        return body;
    }
    if (![body isKindOfClass:[NSString class]]) {
        return nil;
    }
    NSData *data = [(NSString *)body dataUsingEncoding:NSUTF8StringEncoding];
    if (data == nil) {
        return nil;
    }
    id object = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
    return [object isKindOfClass:[NSDictionary class]] ? object : nil;
}

- (BOOL)containsCredential:(id)value {
    if ([value isKindOfClass:[NSDictionary class]]) {
        for (NSString *key in (NSDictionary *)value) {
            NSString *lowerKey = key.lowercaseString;
            if ([lowerKey containsString:@"apikey"]
                || [lowerKey containsString:@"authorization"]
                || [lowerKey containsString:@"accesstoken"]) {
                return YES;
            }
            if ([self containsCredential:value[key]]) {
                return YES;
            }
        }
    } else if ([value isKindOfClass:[NSArray class]]) {
        for (id item in (NSArray *)value) {
            if ([self containsCredential:item]) {
                return YES;
            }
        }
    }
    return NO;
}

- (void)emitErrorType:(NSString *)type
            requestId:(NSString *)requestId
      documentSessionId:(NSString *)documentSessionId
                 code:(NSString *)code
              message:(NSString *)message {
    NSMutableDictionary *payload = [@{
        @"code": code ?: @"native_error",
        @"message": message ?: @"NativeBridge error"
    } mutableCopy];
    [self emitEnvelopeType:type
                 requestId:requestId
           documentSessionId:documentSessionId
                      payload:payload];
}

- (void)emitEnvelopeType:(NSString *)type
               requestId:(NSString *)requestId
         documentSessionId:(NSString *)documentSessionId
                    payload:(NSDictionary *)payload {
    NSMutableDictionary *envelope = [@{
        @"protocolVersion": @(kNativeBridgeProtocolVersion),
        @"channel": @"native",
        @"type": type,
        @"targetPlatform": @"ios",
        @"payload": payload ?: @{}
    } mutableCopy];
    if (requestId.length > 0) {
        envelope[@"requestId"] = requestId;
    }
    if (documentSessionId.length > 0) {
        envelope[@"documentSessionId"] = documentSessionId;
    }
    if (self.emitter) {
        self.emitter(envelope);
    }
}

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
