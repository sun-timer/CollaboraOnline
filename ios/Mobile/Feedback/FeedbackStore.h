// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

@class FeedbackRecord;

NS_ASSUME_NONNULL_BEGIN

@interface FeedbackStore : NSObject

+ (NSArray<FeedbackRecord *> *)loadRecords;
+ (void)saveRecords:(NSArray<FeedbackRecord *> *)records;
+ (void)addRecord:(FeedbackRecord *)record;
+ (nullable FeedbackRecord *)findRecordWithId:(NSString *)recordId;
+ (void)updateRecord:(FeedbackRecord *)record;
+ (NSString *)newFeedbackIdForDate:(NSDate *)date;
+ (void)simulateReplyForRecord:(FeedbackRecord *)record;

@end

NS_ASSUME_NONNULL_END
