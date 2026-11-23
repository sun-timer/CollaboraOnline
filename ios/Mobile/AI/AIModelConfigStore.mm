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
        NSString *endpointModeKey = [self keyForType:type field:@"endpoint_mode"];
        if ([[NSUserDefaults standardUserDefaults] stringForKey:endpointModeKey].length == 0) {
            [[NSUserDefaults standardUserDefaults] setObject:@"automatic" forKey:endpointModeKey];
        }
        NSUserDefaults *defaults = NSUserDefaults.standardUserDefaults;
        NSString *versionKey = [self keyForType:type field:@"sampling_version"];
        if ([defaults integerForKey:versionKey] < 1) {
            NSDictionary *legacy = @{@"top_p": @0.5, @"temperature": @0.9,
                @"presence_penalty": @0, @"frequency_penalty": @0.8,
                @"max_tokens_ratio": @0.8, @"seed_ratio": @0.8};
            NSMutableDictionary *parameters = [NSMutableDictionary dictionary];
            for (NSString *field in legacy) {
                NSNumber *saved = [defaults objectForKey:[self keyForType:type field:field]];
                if (![saved isKindOfClass:NSNumber.class] || fabs(saved.doubleValue - [legacy[field] doubleValue]) < 0.00001) continue;
                NSString *name = field;
                NSNumber *value = saved;
                if ([field isEqualToString:@"max_tokens_ratio"]) {
                    name = @"max_tokens"; value = @(MAX(1, lround(saved.doubleValue * 8192)));
                } else if ([field isEqualToString:@"seed_ratio"]) {
                    if (saved.doubleValue <= 0.001) continue;
                    name = @"seed"; value = @(MAX(1, lround(saved.doubleValue * 999999)));
                }
                parameters[name] = value;
            }
            [defaults setObject:parameters forKey:[self keyForType:type field:@"sampling_parameters"]];
            [defaults setInteger:1 forKey:versionKey];
        }
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
    form.endpointMode = [self stringFromDefaults:defaults key:[self keyForType:modelType field:@"endpoint_mode"] fallback:@"automatic"];
    form.modelName = [self stringFromDefaults:defaults key:[self keyForType:modelType field:@"model_name"]
                                     fallback:[self defaultModelName:modelType]];
    form.samplingParameters = [defaults dictionaryForKey:[self keyForType:modelType field:@"sampling_parameters"]] ?: @{};
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
    [defaults setObject:[@[@"manual", @"automatic"] containsObject:form.endpointMode] ? form.endpointMode : @"automatic"
                   forKey:[self keyForType:modelType field:@"endpoint_mode"]];
    [defaults setObject:[self trimmed:form.modelName] forKey:[self keyForType:modelType field:@"model_name"]];
    [defaults setObject:form.samplingParameters ?: @{} forKey:[self keyForType:modelType field:@"sampling_parameters"]];
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

- (NSString *)defaultModelNameForModelType:(AIModelType)modelType {
    return [self defaultModelName:modelType];
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


- (NSString *)stringFromDefaults:(NSUserDefaults *)defaults key:(NSString *)key fallback:(NSString *)fallback {
    NSString *value = [defaults stringForKey:key];
    return value.length > 0 ? value : fallback;
}


- (NSString *)trimmed:(NSString *)value {
    return [[value isKindOfClass:[NSString class]] ? value : @"" stringByTrimmingCharactersInSet:
            [NSCharacterSet whitespaceAndNewlineCharacterSet]];
}


- (NSDictionary<NSString *, id> *)samplingBodyFieldsForForm:(AIModelConfigForm *)form {
    return form.samplingParameters ?: @{};
}

- (NSURL *)chatCompletionsURLForForm:(AIModelConfigForm *)form error:(NSError **)error {
    NSString *raw = [self trimmed:form.url];
    NSURLComponents *components = [NSURLComponents componentsWithString:raw];
    NSString *scheme = components.scheme.lowercaseString;
    if (components.host.length == 0 ||
        (![scheme isEqualToString:@"http"] && ![scheme isEqualToString:@"https"]) ||
        [raw rangeOfCharacterFromSet:NSCharacterSet.whitespaceAndNewlineCharacterSet].location != NSNotFound) {
        if (error != NULL) *error = [NSError errorWithDomain:AIModelConfigErrorDomain code:3
            userInfo:@{NSLocalizedDescriptionKey: @"AI endpoint is invalid"}];
        return nil;
    }
    BOOL manual = [form.endpointMode isEqualToString:@"manual"];
    NSString *path = components.path ?: @"";
    NSString *trimmedPath = path;
    while ([trimmedPath hasSuffix:@"/"] && trimmedPath.length > 0) {
        trimmedPath = [trimmedPath substringToIndex:trimmedPath.length - 1];
    }
    if (!manual && ![trimmedPath.lowercaseString hasSuffix:@"/chat/completions"]) {
        if (trimmedPath.length == 0) {
            path = @"/v1/chat/completions";
        } else if ([trimmedPath.lowercaseString hasSuffix:@"/v1"]) {
            path = [trimmedPath stringByAppendingString:@"/chat/completions"];
        } else {
            path = [trimmedPath stringByAppendingString:@"/chat/completions"];
        }
        components.path = path;
    }
    NSURL *url = components.URL;
    if (url == nil && error != NULL) *error = [NSError errorWithDomain:AIModelConfigErrorDomain code:4
        userInfo:@{NSLocalizedDescriptionKey: @"AI endpoint is invalid"}];
    return url;
}

@end
