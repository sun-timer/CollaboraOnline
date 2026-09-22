// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef void (^LocalInferenceLoadCompletion)(BOOL success, NSString * _Nullable message);
typedef void (^LocalInferenceTokenBlock)(NSString *token);
typedef void (^LocalInferenceCompleteBlock)(NSString *fullText, NSTimeInterval ttftMs, float tokensPerSecond);
typedef void (^LocalInferenceErrorBlock)(NSString *code, NSString *message);

@interface LocalInferenceParams : NSObject
@property (assign, nonatomic) NSInteger contextSize;
@property (assign, nonatomic) NSInteger maxTokens;
@property (assign, nonatomic) NSInteger threads;
+ (instancetype)defaults;
+ (instancetype)fromDevice;
@end

@interface LocalInferenceEngine : NSObject

+ (instancetype)shared;
+ (BOOL)isNativeAvailable;
- (BOOL)isModelLoaded;

- (void)loadModelAtPath:(NSString *)path
                 params:(LocalInferenceParams *)params
             completion:(LocalInferenceLoadCompletion)completion;

- (void)unloadModel;

- (void)cancelRequestId:(NSString *)requestId;

- (void)generateWithRequestId:(NSString *)requestId
                     messages:(NSArray<NSDictionary *> *)messages
                    multiTurn:(BOOL)multiTurn
                       params:(LocalInferenceParams *)params
                    onToken:(LocalInferenceTokenBlock)onToken
                 onComplete:(LocalInferenceCompleteBlock)onComplete
                    onError:(LocalInferenceErrorBlock)onError;

@end

NS_ASSUME_NONNULL_END
