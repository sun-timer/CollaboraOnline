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
        _replyImagePaths = @[];
        _contact = @"";
        _status = FeedbackStatusSubmitted;
        _replyText = @"";
    }
    return self;
}

- (BOOL)canClose {
    return self.status == FeedbackStatusReplied || self.status == FeedbackStatusResolved;
}

+ (FeedbackStatus)statusFromApiLabel:(NSString *)label {
    if ([label isEqualToString:@"处理中"]) {
        return FeedbackStatusProcessing;
    }
    if ([label isEqualToString:@"已回复"]) {
        return FeedbackStatusReplied;
    }
    if ([label isEqualToString:@"已解决"]) {
        return FeedbackStatusResolved;
    }
    if ([label isEqualToString:@"关闭"] || [label isEqualToString:@"已关闭"]) {
        return FeedbackStatusClosed;
    }
    return FeedbackStatusSubmitted;
}

+ (NSArray<NSString *> *)stringArrayFromJSON:(id)value {
    if (![value isKindOfClass:[NSArray class]]) {
        return @[];
    }
    NSMutableArray<NSString *> *list = [NSMutableArray array];
    for (id item in (NSArray *)value) {
        if ([item isKindOfClass:[NSString class]]) {
            [list addObject:item];
        } else if (item != nil) {
            [list addObject:[item description]];
        }
    }
    return [list copy];
}

+ (NSArray<NSString *> *)imagePathsFromDetailField:(NSDictionary *)data {
    NSArray<NSString *> *paths = [self stringArrayFromJSON:data[@"imagePaths"]];
    if (paths.count > 0) {
        return paths;
    }
    paths = [self stringArrayFromJSON:data[@"images"]];
    if (paths.count > 0) {
        return paths;
    }
    return [self stringArrayFromJSON:data[@"imageList"]];
}

+ (instancetype)recordFromDetailDictionary:(NSDictionary *)data {
    FeedbackRecord *record = [[FeedbackRecord alloc] init];
    record.recordId = data[@"feedbackNo"] ?: @"";
    record.type = data[@"feedbackType"] ?: @"";
    NSString *content = data[@"content"];
    if (content.length == 0) {
        content = data[@"feedbackContent"];
    }
    if (content.length == 0) {
        content = data[@"contentSummary"];
    }
    record.content = content ?: @"";
    record.contact = data[@"contact"] ?: @"";
    record.status = [self statusFromApiLabel:data[@"status"]];
    record.submitTime = [self parseServerTime:data[@"submitTime"]];
    record.imagePaths = [self imagePathsFromDetailField:data];
    NSDictionary *reply = data[@"reply"];
    if ([reply isKindOfClass:[NSDictionary class]]) {
        NSString *replyText = reply[@"replyContent"];
        if (replyText.length == 0) {
            replyText = reply[@"content"];
        }
        record.replyText = replyText ?: @"";
        record.replyTime = [self parseServerTime:reply[@"replyTime"]];
        record.replyImagePaths = [self imagePathsFromDetailField:reply];
    }
    return record;
}

+ (NSTimeInterval)parseServerTime:(NSString *)text {
    if (text.length == 0) {
        return [[NSDate date] timeIntervalSince1970];
    }
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    formatter.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
    formatter.timeZone = [NSTimeZone timeZoneWithName:@"GMT+8"];
    formatter.dateFormat = @"yyyy-MM-dd HH:mm:ss";
    NSDate *date = [formatter dateFromString:text];
    return date ? date.timeIntervalSince1970 : [[NSDate date] timeIntervalSince1970];
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
        @"replyImagePaths": self.replyImagePaths ?: @[],
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
    id replyImages = dict[@"replyImagePaths"];
    if ([replyImages isKindOfClass:[NSArray class]]) {
        record.replyImagePaths = replyImages;
    }
    return record;
}

@end
