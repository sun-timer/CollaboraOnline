// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "FeedbackRecord.h"

@implementation FeedbackRecord

- (instancetype)init {
    self = [super init];
    if (self) {
        _imagePaths = @[];
        _contact = @"";
        _status = FeedbackStatusSubmitted;
        _replyText = @"";
    }
    return self;
}

- (NSDictionary *)dictionaryRepresentation {
    return @{
        @"id": self.recordId ?: @"",
        @"type": self.type ?: @"",
        @"submitTime": @(self.submitTime),
        @"content": self.content ?: @"",
        @"imagePaths": self.imagePaths ?: @[],
        @"contact": self.contact ?: @"",
        @"shareLog": @(self.shareLog),
        @"status": @(self.status),
        @"replyText": self.replyText ?: @"",
        @"replyTime": @(self.replyTime),
    };
}

+ (instancetype)recordFromDictionary:(NSDictionary *)dict {
    FeedbackRecord *record = [[FeedbackRecord alloc] init];
    record.recordId = dict[@"id"];
    record.type = dict[@"type"];
    record.submitTime = [dict[@"submitTime"] doubleValue];
    record.content = dict[@"content"];
    id images = dict[@"imagePaths"];
    if ([images isKindOfClass:[NSArray class]]) {
        record.imagePaths = images;
    }
    record.contact = dict[@"contact"];
    record.shareLog = [dict[@"shareLog"] boolValue];
    record.status = (FeedbackStatus)[dict[@"status"] integerValue];
    record.replyText = dict[@"replyText"];
    record.replyTime = [dict[@"replyTime"] doubleValue];
    return record;
}

@end
