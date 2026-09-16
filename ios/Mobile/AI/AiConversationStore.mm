// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 *
 * Mirrors Android AiConversationStore: ai_history/{sha256}.docqa.json and .chat.json
 */

#import "AiConversationStore.h"

#import <CommonCrypto/CommonDigest.h>

static NSString *const kAiModeDocQa = @"doc_qa";
static NSString *const kAiModeChat = @"chat";
static NSString *const kAiHistoryDir = @"ai_history";

@implementation AiConversationStore {
    NSURL *_documentCopyURL;
    NSURL *_originalDocumentURL;
    NSString *_urlToLoad;
    long long _loadDocumentMillis;
    NSString *_documentKeyCache;
}

- (instancetype)initWithDocumentCopyURL:(NSURL *)documentCopyURL
                   originalDocumentURL:(NSURL *)originalDocumentURL
                             urlToLoad:(NSString *)urlToLoad
                  loadDocumentMillis:(long long)loadDocumentMillis {
    self = [super init];
    if (self) {
        _documentCopyURL = documentCopyURL;
        _originalDocumentURL = originalDocumentURL;
        _urlToLoad = [urlToLoad copy];
        _loadDocumentMillis = loadDocumentMillis;
        _documentKeyCache = @"";
    }
    return self;
}

- (NSArray<NSDictionary *> *)loadHistoryForMode:(NSString *)mode {
    NSURL *fileURL = [self historyFileURLForMode:mode];
    if (fileURL == nil || ![[NSFileManager defaultManager] fileExistsAtPath:fileURL.path]) {
        return @[];
    }
    NSData *data = [NSData dataWithContentsOfURL:fileURL];
    if (data == nil || data.length == 0) {
        return @[];
    }
    id json = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
    if (![json isKindOfClass:[NSArray class]]) {
        return @[];
    }
    NSMutableArray<NSDictionary *> *messages = [[NSMutableArray alloc] init];
    for (id item in (NSArray *)json) {
        if (![item isKindOfClass:[NSDictionary class]]) {
            continue;
        }
        NSString *role = [item[@"role"] isKindOfClass:[NSString class]] ? item[@"role"] : @"";
        NSString *content = [item[@"content"] isKindOfClass:[NSString class]] ? item[@"content"] : @"";
        if (role.length == 0) {
            continue;
        }
        [messages addObject:@{@"role": role, @"content": content}];
    }
    return messages;
}

- (void)saveHistoryForMode:(NSString *)mode messages:(NSArray<NSDictionary *> *)messages {
    NSURL *fileURL = [self historyFileURLForMode:mode];
    if (fileURL == nil) {
        return;
    }
    NSURL *parent = fileURL.URLByDeletingLastPathComponent;
    [[NSFileManager defaultManager] createDirectoryAtURL:parent
                             withIntermediateDirectories:YES
                                              attributes:nil
                                                   error:nil];
    NSData *data = [NSJSONSerialization dataWithJSONObject:messages ?: @[] options:0 error:nil];
    if (data == nil) {
        return;
    }
    [data writeToURL:fileURL atomically:YES];
}

- (void)clearHistoryForMode:(NSString *)mode {
    NSURL *fileURL = [self historyFileURLForMode:mode];
    if (fileURL == nil) {
        return;
    }
    if ([[NSFileManager defaultManager] fileExistsAtPath:fileURL.path]) {
        [[NSFileManager defaultManager] removeItemAtURL:fileURL error:nil];
    }
}

- (void)clearHistoriesForCurrentDocument {
    [self clearHistoryForMode:kAiModeDocQa];
    [self clearHistoryForMode:kAiModeChat];
}

- (NSString *)documentKey {
    if (_documentKeyCache.length > 0) {
        return _documentKeyCache;
    }
    NSString *raw = @"";
    if (_documentCopyURL != nil) {
        raw = _documentCopyURL.absoluteString ?: @"";
    }
    if (raw.length == 0 && _originalDocumentURL != nil) {
        raw = _originalDocumentURL.absoluteString ?: @"";
    }
    if (raw.length == 0 && _urlToLoad.length > 0) {
        raw = _urlToLoad;
    }
    if (raw.length == 0) {
        raw = [NSString stringWithFormat:@"doc-%lld", _loadDocumentMillis];
    }
    _documentKeyCache = [self sha256Hex:raw];
    return _documentKeyCache;
}

- (NSURL *)historyDirectoryURL {
    NSURL *base = [[NSFileManager defaultManager] URLsForDirectory:NSApplicationSupportDirectory
                                                         inDomains:NSUserDomainMask].firstObject;
    if (base == nil) {
        return nil;
    }
    return [base URLByAppendingPathComponent:kAiHistoryDir isDirectory:YES];
}

- (nullable NSURL *)historyFileURLForMode:(NSString *)mode {
    NSString *suffix = [mode isEqualToString:kAiModeDocQa] ? @"docqa" : @"chat";
    NSURL *dir = [self historyDirectoryURL];
    if (dir == nil) {
        return nil;
    }
    NSString *fileName = [NSString stringWithFormat:@"%@.%@.json", [self documentKey], suffix];
    return [dir URLByAppendingPathComponent:fileName];
}

- (NSString *)sha256Hex:(NSString *)value {
    NSData *data = [value dataUsingEncoding:NSUTF8StringEncoding];
    if (data == nil) {
        return [NSString stringWithFormat:@"%x", value.hash];
    }
    unsigned char digest[CC_SHA256_DIGEST_LENGTH];
    CC_SHA256(data.bytes, (CC_LONG)data.length, digest);
    NSMutableString *builder = [[NSMutableString alloc] initWithCapacity:CC_SHA256_DIGEST_LENGTH * 2];
    for (NSUInteger i = 0; i < CC_SHA256_DIGEST_LENGTH; i++) {
        [builder appendFormat:@"%02x", digest[i]];
    }
    return builder;
}

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
