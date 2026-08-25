// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, AIModelType) {
    AIModelTypeBase = 0,
    AIModelTypeThink = 1,
    AIModelTypeImage = 2,
    AIModelTypeVision = 3,
};

@interface AIModelConfigForm : NSObject

@property (copy, nonatomic) NSString *configName;
@property (copy, nonatomic) NSString *provider;
@property (copy, nonatomic) NSString *url;
@property (copy, nonatomic) NSString *apiKey;
@property (copy, nonatomic) NSString *modelName;
@property (assign, nonatomic) float topP;
@property (assign, nonatomic) float temperature;
@property (assign, nonatomic) float presencePenalty;
@property (assign, nonatomic) float frequencyPenalty;
@property (assign, nonatomic) float maxTokensRatio;
@property (assign, nonatomic) float seedRatio;

@end

@interface AIModelConfigStore : NSObject

- (void)ensureDefaults;
- (AIModelConfigForm *)loadForm:(AIModelType)modelType;
- (BOOL)saveForm:(AIModelConfigForm *)form modelType:(AIModelType)modelType error:(NSError * _Nullable * _Nullable)error;
- (NSString *)displayNameForModelType:(AIModelType)modelType;
- (NSString *)defaultTitleForModelType:(AIModelType)modelType;

@end

NS_ASSUME_NONNULL_END
