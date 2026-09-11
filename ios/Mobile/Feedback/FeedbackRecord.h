// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, FeedbackStatus) {
    FeedbackStatusSubmitted,
    FeedbackStatusProcessing,
    FeedbackStatusReplied,
    FeedbackStatusClosed,
};

@interface FeedbackRecord : NSObject

@property (copy, nonatomic) NSString *recordId;
@property (copy, nonatomic) NSString *type;
@property (assign, nonatomic) NSTimeInterval submitTime;
@property (copy, nonatomic) NSString *content;
@property (copy, nonatomic) NSArray<NSString *> *imagePaths;
@property (copy, nonatomic) NSString *contact;
@property (assign, nonatomic) BOOL shareLog;
@property (assign, nonatomic) FeedbackStatus status;
@property (copy, nonatomic) NSString *replyText;
@property (assign, nonatomic) NSTimeInterval replyTime;

- (NSDictionary *)dictionaryRepresentation;
+ (nullable instancetype)recordFromDictionary:(NSDictionary *)dict;

@end

NS_ASSUME_NONNULL_END
