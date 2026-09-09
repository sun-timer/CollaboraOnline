// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "FeedbackStore.h"

#import "FeedbackRecord.h"

static NSString *const kFeedbackStoreKey = @"feedback_store_records";
static NSString *const kReplyText = @"感谢您的反馈，我们已经收到并转交相关同事处理。";

@implementation FeedbackStore

+ (NSArray<FeedbackRecord *> *)loadRecords {
    NSData *data = [[NSUserDefaults standardUserDefaults] dataForKey:kFeedbackStoreKey];
    if (data == nil) {
        return @[];
    }
    id json = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
    if (![json isKindOfClass:[NSArray class]]) {
        return @[];
    }
    NSMutableArray<FeedbackRecord *> *records = [NSMutableArray array];
    for (id item in (NSArray *)json) {
        if ([item isKindOfClass:[NSDictionary class]]) {
            [records addObject:[FeedbackRecord recordFromDictionary:item]];
        }
    }
    return records;
}

+ (void)saveRecords:(NSArray<FeedbackRecord *> *)records {
    NSMutableArray *payload = [NSMutableArray array];
    for (FeedbackRecord *record in records) {
        [payload addObject:record.dictionaryRepresentation];
    }
    NSData *data = [NSJSONSerialization dataWithJSONObject:payload options:0 error:nil];
    if (data != nil) {
        [[NSUserDefaults standardUserDefaults] setObject:data forKey:kFeedbackStoreKey];
    }
}

+ (void)addRecord:(FeedbackRecord *)record {
    NSMutableArray *records = [[self loadRecords] mutableCopy];
    [records insertObject:record atIndex:0];
    [self saveRecords:records];
}

+ (FeedbackRecord *)findRecordWithId:(NSString *)recordId {
    for (FeedbackRecord *record in [self loadRecords]) {
        if ([record.recordId isEqualToString:recordId]) {
            return record;
        }
    }
    return nil;
}

+ (void)updateRecord:(FeedbackRecord *)updated {
    NSMutableArray *records = [[self loadRecords] mutableCopy];
    for (NSUInteger i = 0; i < records.count; i++) {
        if ([records[i].recordId isEqualToString:updated.recordId]) {
            records[i] = updated;
            break;
        }
    }
    [self saveRecords:records];
}

+ (NSString *)newFeedbackIdForDate:(NSDate *)date {
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    formatter.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
    formatter.dateFormat = @"yyyyMMddHHmm";
    return [formatter stringFromDate:date ?: [NSDate date]];
}

+ (void)simulateReplyForRecord:(FeedbackRecord *)record {
    if (record.status == FeedbackStatusSubmitted || record.status == FeedbackStatusProcessing) {
        record.status = FeedbackStatusReplied;
        record.replyTime = [NSDate date].timeIntervalSince1970;
        record.replyText = kReplyText;
        [self updateRecord:record];
    }
}

@end
