// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface KeychainStore : NSObject

- (nullable NSString *)stringForService:(NSString *)service
                                account:(NSString *)account
                                  error:(NSError * _Nullable * _Nullable)error;

- (BOOL)setString:(NSString *)value
       forService:(NSString *)service
          account:(NSString *)account
            error:(NSError * _Nullable * _Nullable)error;

- (BOOL)removeStringForService:(NSString *)service
                       account:(NSString *)account
                         error:(NSError * _Nullable * _Nullable)error;

@end

NS_ASSUME_NONNULL_END

// vim:set shiftwidth=4 softtabstop=4 expandtab:
