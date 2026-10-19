// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "LocalPromptBuilder.h"

static const NSInteger kSafetyMargin = 128;
static NSString * const kFinalAnswerOnlyInstruction =
    @"只输出给用户看的最终答案，不要展示分析、思考过程、草稿或内部推理；推理请在内部完成。 "
    @"Respond only with the final answer intended for the user. Do not expose analysis, "
    @"chain-of-thought, internal reasoning, or draft text.";

@interface LocalPromptBuilder ()
+ (NSArray<NSDictionary *> *)applyFinalAnswerOnlyMode:(NSArray<NSDictionary *> *)messages;
@end

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
        NSArray<NSDictionary *> *selected = [self truncateToTokenBudget:history maxTokens:budget];
        return [self applyFinalAnswerOnlyMode:selected];
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
    return [self applyFinalAnswerOnlyMode:selected];
}

+ (NSArray<NSDictionary *> *)applyFinalAnswerOnlyMode:(NSArray<NSDictionary *> *)messages {
    NSInteger firstSystemIndex = -1;
    for (NSUInteger i = 0; i < messages.count; i++) {
        NSString *role = [messages[i][@"role"] isKindOfClass:[NSString class]] ? messages[i][@"role"] : @"";
        if (firstSystemIndex < 0 && [role isEqualToString:@"system"]) {
            firstSystemIndex = (NSInteger)i;
        }
    }

    NSMutableArray<NSDictionary *> *result = [NSMutableArray arrayWithCapacity:messages.count + 1];
    if (firstSystemIndex < 0) {
        [result addObject:@{ @"role": @"system", @"content": kFinalAnswerOnlyInstruction }];
    }

    for (NSUInteger i = 0; i < messages.count; i++) {
        NSMutableDictionary *copy = [messages[i] mutableCopy];
        NSString *content = [copy[@"content"] isKindOfClass:[NSString class]] ? copy[@"content"] : @"";
        if ((NSInteger)i == firstSystemIndex) {
            copy[@"content"] = content.length > 0
                ? [NSString stringWithFormat:@"%@\n\n%@", content, kFinalAnswerOnlyInstruction]
                : kFinalAnswerOnlyInstruction;
        }
        [result addObject:copy];
    }
    return result;
}

@end
