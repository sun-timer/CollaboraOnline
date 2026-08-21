// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "KeychainStore.h"

#import <Security/Security.h>

static NSString *const KeychainStoreErrorDomain = @"com.xunlong.xloffice.keychain";

@implementation KeychainStore

- (NSDictionary *)queryForService:(NSString *)service account:(NSString *)account {
    return @{
        (__bridge id)kSecClass: (__bridge id)kSecClassGenericPassword,
        (__bridge id)kSecAttrService: service ?: @"",
        (__bridge id)kSecAttrAccount: account ?: @"",
    };
}

- (NSError *)errorWithStatus:(OSStatus)status operation:(NSString *)operation {
    NSString *message = [NSString stringWithFormat:@"%@ failed (%d)", operation, (int)status];
    return [NSError errorWithDomain:KeychainStoreErrorDomain
                                code:status
                            userInfo:@{NSLocalizedDescriptionKey: message}];
}

- (NSString *)stringForService:(NSString *)service
                        account:(NSString *)account
                          error:(NSError **)error {
    NSMutableDictionary *query = [[self queryForService:service account:account] mutableCopy];
    query[(__bridge id)kSecReturnData] = @YES;
    query[(__bridge id)kSecMatchLimit] = (__bridge id)kSecMatchLimitOne;

    CFTypeRef result = NULL;
    OSStatus status = SecItemCopyMatching((__bridge CFDictionaryRef)query, &result);
    if (status == errSecItemNotFound) {
        return nil;
    }
    if (status != errSecSuccess) {
        if (error != NULL) {
            *error = [self errorWithStatus:status operation:@"Keychain read"];
        }
        return nil;
    }

    NSData *data = CFBridgingRelease(result);
    NSString *value = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    if (value == nil && error != NULL) {
        *error = [NSError errorWithDomain:KeychainStoreErrorDomain
                                     code:errSecDecode
                                 userInfo:@{
                                     NSLocalizedDescriptionKey: @"Keychain value is not UTF-8 text",
                                 }];
    }
    return value;
}

- (BOOL)setString:(NSString *)value
       forService:(NSString *)service
          account:(NSString *)account
            error:(NSError **)error {
    if (value == nil) {
        return [self removeStringForService:service account:account error:error];
    }

    NSData *data = [value dataUsingEncoding:NSUTF8StringEncoding];
    NSMutableDictionary *query = [[self queryForService:service account:account] mutableCopy];
    NSDictionary *attributes = @{(__bridge id)kSecValueData: data};
    OSStatus status = SecItemUpdate((__bridge CFDictionaryRef)query,
                                    (__bridge CFDictionaryRef)attributes);
    if (status == errSecItemNotFound) {
        query[(__bridge id)kSecValueData] = data;
        status = SecItemAdd((__bridge CFDictionaryRef)query, NULL);
    }
    if (status != errSecSuccess && error != NULL) {
        *error = [self errorWithStatus:status operation:@"Keychain write"];
    }
    return status == errSecSuccess;
}

- (BOOL)removeStringForService:(NSString *)service
                       account:(NSString *)account
                         error:(NSError **)error {
    NSDictionary *query = [self queryForService:service account:account];
    OSStatus status = SecItemDelete((__bridge CFDictionaryRef)query);
    if (status == errSecItemNotFound) {
        return YES;
    }
    if (status != errSecSuccess && error != NULL) {
        *error = [self errorWithStatus:status operation:@"Keychain delete"];
    }
    return status == errSecSuccess;
}

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
