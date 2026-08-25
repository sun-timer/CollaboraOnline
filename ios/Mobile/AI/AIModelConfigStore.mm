// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AIModelConfigStore.h"

#import "AIConfigurationStore.h"
#import "KeychainStore.h"

#import <math.h>

static NSString *const AIModelConfigKeychainService = @"com.xunlong.xloffice.ai.models";
static NSString *const AIModelConfigErrorDomain = @"com.xunlong.xloffice.ai.modelconfig";
static NSString *const AIModelDefaultEndpoint = @"https://api.openai.com/v1/chat/completions";

@implementation AIModelConfigForm
@end

@interface AIModelConfigStore ()
@property (strong, nonatomic) KeychainStore *keychainStore;
@property (strong, nonatomic) AIConfigurationStore *runtimeStore;
@end

@implementation AIModelConfigStore

- (instancetype)init {
    self = [super init];
    if (self) {
        _keychainStore = [[KeychainStore alloc] init];
        _runtimeStore = [[AIConfigurationStore alloc] init];
        [self ensureDefaults];
    }
    return self;
}

- (void)ensureDefaults {
    NSArray<NSNumber *> *types = @[
        @(AIModelTypeBase), @(AIModelTypeThink), @(AIModelTypeImage), @(AIModelTypeVision)
    ];
    for (NSNumber *boxed in types) {
        AIModelType type = (AIModelType)boxed.integerValue;
        NSString *modelKey = [self keyForType:type field:@"model_name"];
        if ([[NSUserDefaults standardUserDefaults] stringForKey:modelKey].length == 0) {
            [[NSUserDefaults standardUserDefaults] setObject:[self defaultModelName:type] forKey:modelKey];
        }
        NSString *nameKey = [self keyForType:type field:@"config_name"];
        if ([[NSUserDefaults standardUserDefaults] stringForKey:nameKey].length == 0) {
            [[NSUserDefaults standardUserDefaults] setObject:[NSString stringWithFormat:@"%@配置", [self defaultTitleForModelType:type]]
                                                      forKey:nameKey];
        }
        NSString *providerKey = [self keyForType:type field:@"provider"];
        if ([[NSUserDefaults standardUserDefaults] stringForKey:providerKey].length == 0) {
            [[NSUserDefaults standardUserDefaults] setObject:@"OpenAI" forKey:providerKey];
        }
        NSString *urlKey = [self keyForType:type field:@"url"];
        if ([[NSUserDefaults standardUserDefaults] stringForKey:urlKey].length == 0) {
            [[NSUserDefaults standardUserDefaults] setObject:AIModelDefaultEndpoint forKey:urlKey];
        }
        [self setFloatIfMissing:0.5f type:type field:@"top_p"];
        [self setFloatIfMissing:0.9f type:type field:@"temperature"];
        [self setFloatIfMissing:0.0f type:type field:@"presence_penalty"];
        [self setFloatIfMissing:0.8f type:type field:@"frequency_penalty"];
        [self setFloatIfMissing:0.8f type:type field:@"max_tokens_ratio"];
        [self setFloatIfMissing:0.8f type:type field:@"seed_ratio"];
    }

    NSString *runtimeEndpoint = [[NSUserDefaults standardUserDefaults] stringForKey:@"AI_OPENAI_ENDPOINT"];
    NSString *runtimeModel = [[NSUserDefaults standardUserDefaults] stringForKey:@"AI_OPENAI_MODEL"];
    if (runtimeEndpoint.length > 0) {
        [[NSUserDefaults standardUserDefaults] setObject:runtimeEndpoint
                                                  forKey:[self keyForType:AIModelTypeBase field:@"url"]];
    }
    if (runtimeModel.length > 0) {
        [[NSUserDefaults standardUserDefaults] setObject:runtimeModel
                                                  forKey:[self keyForType:AIModelTypeBase field:@"model_name"]];
    }
    if (self.runtimeStore.isConfigured) {
        AIConfiguration *runtime = [self.runtimeStore configurationWithError:nil];
        if (runtime.apiKey.length > 0) {
            [self.keychainStore setString:runtime.apiKey
                               forService:AIModelConfigKeychainService
                                  account:[self keyForType:AIModelTypeBase field:@"api_key"]
                                    error:nil];
        }
    }
}

- (AIModelConfigForm *)loadForm:(AIModelType)modelType {
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    AIModelConfigForm *form = [[AIModelConfigForm alloc] init];
    form.configName = [self stringFromDefaults:defaults key:[self keyForType:modelType field:@"config_name"]
                                      fallback:[NSString stringWithFormat:@"%@配置", [self defaultTitleForModelType:modelType]]];
    form.provider = [self stringFromDefaults:defaults key:[self keyForType:modelType field:@"provider"] fallback:@"OpenAI"];
    form.url = [self stringFromDefaults:defaults key:[self keyForType:modelType field:@"url"] fallback:AIModelDefaultEndpoint];
    form.modelName = [self stringFromDefaults:defaults key:[self keyForType:modelType field:@"model_name"]
                                     fallback:[self defaultModelName:modelType]];
    form.topP = [self floatFromDefaults:defaults key:[self keyForType:modelType field:@"top_p"] fallback:0.5f];
    form.temperature = [self floatFromDefaults:defaults key:[self keyForType:modelType field:@"temperature"] fallback:0.9f];
    form.presencePenalty = [self floatFromDefaults:defaults key:[self keyForType:modelType field:@"presence_penalty"] fallback:0.0f];
    form.frequencyPenalty = [self floatFromDefaults:defaults key:[self keyForType:modelType field:@"frequency_penalty"] fallback:0.8f];
    form.maxTokensRatio = [self floatFromDefaults:defaults key:[self keyForType:modelType field:@"max_tokens_ratio"] fallback:0.8f];
    form.seedRatio = [self floatFromDefaults:defaults key:[self keyForType:modelType field:@"seed_ratio"] fallback:0.8f];
    form.apiKey = [self.keychainStore stringForService:AIModelConfigKeychainService
                                               account:[self keyForType:modelType field:@"api_key"]
                                                 error:nil] ?: @"";
    if (modelType == AIModelTypeBase && form.apiKey.length == 0) {
        AIConfiguration *runtime = [self.runtimeStore configurationWithError:nil];
        form.apiKey = runtime.apiKey ?: @"";
    }
    return form;
}

- (BOOL)saveForm:(AIModelConfigForm *)form modelType:(AIModelType)modelType error:(NSError **)error {
    if (form == nil) {
        return NO;
    }
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    [defaults setObject:[self trimmed:form.configName] forKey:[self keyForType:modelType field:@"config_name"]];
    [defaults setObject:[self trimmed:form.provider] forKey:[self keyForType:modelType field:@"provider"]];
    [defaults setObject:[self trimmed:form.url] forKey:[self keyForType:modelType field:@"url"]];
    [defaults setObject:[self trimmed:form.modelName] forKey:[self keyForType:modelType field:@"model_name"]];
    [defaults setFloat:[self clamp:form.topP] forKey:[self keyForType:modelType field:@"top_p"]];
    [defaults setFloat:[self clamp:form.temperature] forKey:[self keyForType:modelType field:@"temperature"]];
    [defaults setFloat:[self clamp:form.presencePenalty] forKey:[self keyForType:modelType field:@"presence_penalty"]];
    [defaults setFloat:[self clamp:form.frequencyPenalty] forKey:[self keyForType:modelType field:@"frequency_penalty"]];
    [defaults setFloat:[self clamp:form.maxTokensRatio] forKey:[self keyForType:modelType field:@"max_tokens_ratio"]];
    [defaults setFloat:[self clamp:form.seedRatio] forKey:[self keyForType:modelType field:@"seed_ratio"]];
    if (![self.keychainStore setString:[self trimmed:form.apiKey]
                            forService:AIModelConfigKeychainService
                               account:[self keyForType:modelType field:@"api_key"]
                                 error:error]) {
        return NO;
    }
    if (modelType == AIModelTypeBase) {
        if (![self.runtimeStore saveEndpoint:[self trimmed:form.url]
                                       model:[self trimmed:form.modelName]
                                      apiKey:[self trimmed:form.apiKey]
                                       error:error]) {
            return NO;
        }
    }
    return [defaults synchronize];
}

- (NSString *)displayNameForModelType:(AIModelType)modelType {
    NSString *name = [[NSUserDefaults standardUserDefaults] stringForKey:[self keyForType:modelType field:@"model_name"]];
    if (name.length == 0) {
        return @"尚未配置";
    }
    return name;
}

- (NSString *)defaultTitleForModelType:(AIModelType)modelType {
    switch (modelType) {
        case AIModelTypeThink:
            return @"思考模型";
        case AIModelTypeImage:
            return @"图像生成模型";
        case AIModelTypeVision:
            return @"视觉模型";
        case AIModelTypeBase:
        default:
            return @"基础模型";
    }
}

- (NSString *)defaultModelName:(AIModelType)modelType {
    switch (modelType) {
        case AIModelTypeThink:
            return @"Orangepi-2.0-pro-exp";
        case AIModelTypeImage:
            return @"Orangepi-3.0-generate-001";
        case AIModelTypeVision:
        case AIModelTypeBase:
        default:
            return @"Orangepi-2.5-flash";
    }
}

- (NSString *)prefixForType:(AIModelType)modelType {
    switch (modelType) {
        case AIModelTypeThink:
            return @"AI_MODEL_THINK";
        case AIModelTypeImage:
            return @"AI_MODEL_IMAGE";
        case AIModelTypeVision:
            return @"AI_MODEL_VISION";
        case AIModelTypeBase:
        default:
            return @"AI_MODEL_BASE";
    }
}

- (NSString *)keyForType:(AIModelType)modelType field:(NSString *)field {
    return [NSString stringWithFormat:@"%@_%@", [self prefixForType:modelType], field];
}

- (void)setFloatIfMissing:(float)value type:(AIModelType)type field:(NSString *)field {
    NSString *key = [self keyForType:type field:field];
    if ([[NSUserDefaults standardUserDefaults] objectForKey:key] == nil) {
        [[NSUserDefaults standardUserDefaults] setFloat:value forKey:key];
    }
}

- (NSString *)stringFromDefaults:(NSUserDefaults *)defaults key:(NSString *)key fallback:(NSString *)fallback {
    NSString *value = [defaults stringForKey:key];
    return value.length > 0 ? value : fallback;
}

- (float)floatFromDefaults:(NSUserDefaults *)defaults key:(NSString *)key fallback:(float)fallback {
    if ([defaults objectForKey:key] == nil) {
        return fallback;
    }
    return [defaults floatForKey:key];
}

- (NSString *)trimmed:(NSString *)value {
    return [[value isKindOfClass:[NSString class]] ? value : @"" stringByTrimmingCharactersInSet:
            [NSCharacterSet whitespaceAndNewlineCharacterSet]];
}

- (float)clamp:(float)value {
    if (isnan(value)) {
        return 0;
    }
    return MAX(0.0f, MIN(1.0f, value));
}

@end
