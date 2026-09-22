// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "LocalPromptBuilder.h"

static const NSInteger kSafetyMargin = 128;

@implementation LocalPromptBuilder

+ (NSInteger)estimateTokensForMessages:(NSArray<NSDictionary *> *)messages {
    if (messages.count == 0) {
        return 0;
    }
    NSInteger chars = 0;
    for (NSDictionary *item in messages) {
        if (![item isKindOfClass:[NSDictionary class]]) {
            continue;
        }
        NSString *role = [item[@"role"] isKindOfClass:[NSString class]] ? item[@"role"] : @"";
        NSString *content = [item[@"content"] isKindOfClass:[NSString class]] ? item[@"content"] : @"";
        chars += role.length + content.length;
    }
    return MAX(1, chars / 2);
}

+ (NSArray<NSDictionary *> *)truncateToTokenBudget:(NSArray<NSDictionary *> *)messages
                                         maxTokens:(NSInteger)maxTokens {
    if (messages.count == 0 || maxTokens <= 0) {
        return messages ?: @[];
    }
    if ([self estimateTokensForMessages:messages] <= maxTokens) {
        return messages;
    }

    NSDictionary *system = nil;
    NSMutableArray<NSDictionary *> *turns = [NSMutableArray array];
    for (NSDictionary *item in messages) {
        NSString *role = [item[@"role"] isKindOfClass:[NSString class]] ? item[@"role"] : @"";
        if ([role isEqualToString:@"system"] && system == nil) {
            system = item;
        } else {
            [turns addObject:item];
        }
    }

    NSArray<NSDictionary *> *trimmed = @[];
    if (system != nil) {
        trimmed = @[ system ];
    }
    for (NSInteger start = (NSInteger)turns.count - 1; start >= 0; start--) {
        NSMutableArray<NSDictionary *> *trial = [NSMutableArray array];
        if (system != nil) {
            [trial addObject:system];
        }
        for (NSInteger j = start; j < (NSInteger)turns.count; j++) {
            [trial addObject:turns[(NSUInteger)j]];
        }
        if ([self estimateTokensForMessages:trial] <= maxTokens) {
            trimmed = trial;
            break;
        }
    }
    if (trimmed.count == 0 && turns.count > 0) {
        trimmed = @[ turns.lastObject ];
    }
    return trimmed;
}

+ (NSArray<NSDictionary *> *)buildPromptFromHistory:(NSArray<NSDictionary *> *)history
                                        contextSize:(NSInteger)contextSize
                                      maxGenTokens:(NSInteger)maxGenTokens
                                         multiTurn:(BOOL)multiTurn {
    if (history.count == 0) {
        return @[];
    }
    NSInteger budget = MAX(256, contextSize - maxGenTokens - kSafetyMargin);
    if (!multiTurn) {
        return [self truncateToTokenBudget:history maxTokens:budget];
    }

    NSDictionary *system = nil;
    NSMutableArray<NSDictionary *> *turns = [NSMutableArray array];
    for (NSDictionary *item in history) {
        NSString *role = [item[@"role"] isKindOfClass:[NSString class]] ? item[@"role"] : @"";
        if ([role isEqualToString:@"system"] && system == nil) {
            system = item;
        } else {
            [turns addObject:item];
        }
    }

    NSArray<NSDictionary *> *selected = @[];
    for (NSInteger start = (NSInteger)turns.count - 1; start >= 0; start--) {
        NSMutableArray<NSDictionary *> *trial = [NSMutableArray array];
        if (system != nil) {
            [trial addObject:system];
        }
        for (NSInteger j = start; j < (NSInteger)turns.count; j++) {
            [trial addObject:turns[(NSUInteger)j]];
        }
        if ([self estimateTokensForMessages:trial] <= budget || selected.count == 0) {
            selected = trial;
        } else {
            break;
        }
    }
    if (selected.count == 0 && system != nil) {
        selected = @[ system ];
    }
    return selected;
}

@end
