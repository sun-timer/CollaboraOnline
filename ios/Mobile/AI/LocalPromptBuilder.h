// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface LocalPromptBuilder : NSObject

+ (NSInteger)estimateTokensForMessages:(NSArray<NSDictionary *> *)messages;
+ (NSArray<NSDictionary *> *)buildPromptFromHistory:(NSArray<NSDictionary *> *)history
                                        contextSize:(NSInteger)contextSize
                                      maxGenTokens:(NSInteger)maxGenTokens
                                         multiTurn:(BOOL)multiTurn;

@end

NS_ASSUME_NONNULL_END
