// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

@class KeychainStore;

NS_ASSUME_NONNULL_BEGIN

@interface AIConfiguration : NSObject

@property (copy, nonatomic) NSString *endpoint;
@property (copy, nonatomic) NSString *model;
@property (copy, nonatomic) NSString *apiKey;

@end

@interface AIConfigurationStore : NSObject

- (instancetype)init;
- (instancetype)initWithKeychainStore:(KeychainStore *)keychainStore;

- (nullable AIConfiguration *)configurationWithError:(NSError * _Nullable * _Nullable)error;
- (BOOL)isConfigured;

- (BOOL)saveEndpoint:(NSString *)endpoint
               model:(NSString *)model
              apiKey:(NSString *)apiKey
               error:(NSError * _Nullable * _Nullable)error;

@end

NS_ASSUME_NONNULL_END

// vim:set shiftwidth=4 softtabstop=4 expandtab:
