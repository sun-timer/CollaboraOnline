// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "FeedbackStore.h"

static NSString *const kFeedbackStoreKey = @"feedback_store_records";
static NSString *const kLegacyClearedKey = @"feedback_store_legacy_mock_cleared_v1";

@implementation FeedbackStore

+ (void)clearLegacyMockIfNeeded {
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    if ([defaults boolForKey:kLegacyClearedKey]) {
        return;
    }
    [defaults removeObjectForKey:kFeedbackStoreKey];
    [defaults setBool:YES forKey:kLegacyClearedKey];
}

@end
