// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AIConfigurationStore.h"

#import "KeychainStore.h"

static NSString *const AIConfigurationEndpointKey = @"AI_OPENAI_ENDPOINT";
static NSString *const AIConfigurationModelKey = @"AI_OPENAI_MODEL";
static NSString *const AIConfigurationKeychainService = @"com.xunlong.xloffice.ai";
static NSString *const AIConfigurationKeychainAccount = @"api_key";
static NSString *const AIConfigurationErrorDomain = @"com.xunlong.xloffice.ai.configuration";
static NSString *const AIConfigurationDefaultEndpoint = @"https://api.openai.com/v1/chat/completions";
static NSString *const AIConfigurationDefaultModel = @"Orangepi-2.5-flash";

@implementation AIConfiguration
@end

@interface AIConfigurationStore ()
@property (strong, nonatomic) KeychainStore *keychainStore;
@end

@implementation AIConfigurationStore

- (instancetype)init {
    return [self initWithKeychainStore:[[KeychainStore alloc] init]];
}

- (instancetype)initWithKeychainStore:(KeychainStore *)keychainStore {
    self = [super init];
    if (self) {
        _keychainStore = keychainStore ?: [[KeychainStore alloc] init];
    }
    return self;
}

- (AIConfiguration *)configurationWithError:(NSError **)error {
    NSError *keychainError = nil;
    NSString *apiKey = [self.keychainStore stringForService:AIConfigurationKeychainService
                                                     account:AIConfigurationKeychainAccount
                                                       error:&keychainError];
    if (keychainError != nil && error != NULL) {
        *error = keychainError;
        return nil;
    }

    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    AIConfiguration *configuration = [[AIConfiguration alloc] init];
    configuration.endpoint = [self normalizedString:[defaults stringForKey:AIConfigurationEndpointKey]
                                          fallback:AIConfigurationDefaultEndpoint];
    configuration.model = [self normalizedString:[defaults stringForKey:AIConfigurationModelKey]
                                        fallback:AIConfigurationDefaultModel];
    configuration.apiKey = [self normalizedString:apiKey fallback:@""];
    return configuration;
}

- (BOOL)isConfigured {
    AIConfiguration *configuration = [self configurationWithError:nil];
    return configuration.endpoint.length > 0
        && configuration.model.length > 0
        && configuration.apiKey.length > 0;
}

- (BOOL)saveEndpoint:(NSString *)endpoint
               model:(NSString *)model
              apiKey:(NSString *)apiKey
               error:(NSError **)error {
    NSString *normalizedEndpoint = [self normalizedString:endpoint fallback:@""];
    NSString *normalizedModel = [self normalizedString:model fallback:@""];
    NSString *normalizedApiKey = [self normalizedString:apiKey fallback:@""];
    if (normalizedEndpoint.length == 0 || normalizedModel.length == 0) {
        if (error != NULL) {
            *error = [NSError errorWithDomain:AIConfigurationErrorDomain
                                         code:1
                                     userInfo:@{
                                         NSLocalizedDescriptionKey:
                                             @"AI endpoint and model are required",
                                     }];
        }
        return NO;
    }

    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    [defaults setObject:normalizedEndpoint forKey:AIConfigurationEndpointKey];
    [defaults setObject:normalizedModel forKey:AIConfigurationModelKey];
    if (![self.keychainStore setString:normalizedApiKey
                            forService:AIConfigurationKeychainService
                               account:AIConfigurationKeychainAccount
                                 error:error]) {
        return NO;
    }
    return [defaults synchronize];
}

- (NSString *)normalizedString:(NSString *)value fallback:(NSString *)fallback {
    NSString *normalized = [value isKindOfClass:[NSString class]] ? [value stringByTrimmingCharactersInSet:
        [NSCharacterSet whitespaceAndNewlineCharacterSet]] : @"";
    return normalized.length > 0 ? normalized : fallback;
}

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
