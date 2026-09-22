// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, AiBackendKind) {
    AiBackendKindCloud = 0,
    AiBackendKindLocal = 1,
};

@interface AiBackendResolvedRoute : NSObject
@property (assign, nonatomic) AiBackendKind backend;
@property (copy, nonatomic) NSString *modelMode;
@property (copy, nonatomic) NSString *reason;
@end

@interface AiBackendLocalModelState : NSObject
@property (assign, nonatomic) BOOL deviceSupported;
@property (assign, nonatomic) BOOL installed;
@property (assign, nonatomic) BOOL enabled;
- (BOOL)isReady;
@end

@interface AiBackendRouter : NSObject

+ (BOOL)localDocQaEnabled;
+ (NSInteger)localDocQaMaxChars;

- (AiBackendResolvedRoute *)resolveTaskType:(NSString *)taskType
                                  modelMode:(NSString *)modelMode
                                docCharCount:(NSInteger)docCharCount
                                  localState:(nullable AiBackendLocalModelState *)state;

+ (BOOL)isCloudOnlyTask:(NSString *)taskType;
+ (BOOL)isLocalTextTask:(NSString *)taskType;
+ (BOOL)isMultiTurnTask:(NSString *)taskType;

@end

NS_ASSUME_NONNULL_END
