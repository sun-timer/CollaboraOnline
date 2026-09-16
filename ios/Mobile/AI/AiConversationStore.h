// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface AiConversationStore : NSObject

- (instancetype)initWithDocumentCopyURL:(nullable NSURL *)documentCopyURL
                   originalDocumentURL:(nullable NSURL *)originalDocumentURL
                             urlToLoad:(nullable NSString *)urlToLoad
                  loadDocumentMillis:(long long)loadDocumentMillis;

- (NSArray<NSDictionary *> *)loadHistoryForMode:(NSString *)mode;
- (void)saveHistoryForMode:(NSString *)mode messages:(NSArray<NSDictionary *> *)messages;
- (void)clearHistoryForMode:(NSString *)mode;
- (void)clearHistoriesForCurrentDocument;

- (NSString *)documentKey;

@end

NS_ASSUME_NONNULL_END

// vim:set shiftwidth=4 softtabstop=4 expandtab:
