// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

@class FeedbackRecord;

NS_ASSUME_NONNULL_BEGIN

@interface FeedbackApiListPage : NSObject
@property (assign, nonatomic) NSInteger pageNum;
@property (assign, nonatomic) NSInteger pageSize;
@property (assign, nonatomic) NSInteger total;
@property (assign, nonatomic) NSInteger pages;
@property (copy, nonatomic) NSArray<FeedbackRecord *> *list;
@end

@interface FeedbackApi : NSObject

+ (BOOL)isConfigured;

+ (void)submitFormWithFeedbackType:(NSString *)feedbackType
                           content:(NSString *)content
                           contact:(NSString *)contact
                        imagePaths:(NSArray<NSString *> *)imagePaths
                    shareLogRequested:(BOOL)shareLogRequested
                        completion:(void (^)(FeedbackRecord *_Nullable record, NSError *_Nullable error))completion;

+ (void)fetchListPage:(NSInteger)pageNum
           completion:(void (^)(FeedbackApiListPage *_Nullable page, NSError *_Nullable error))completion;

+ (void)fetchDetail:(NSString *)feedbackNo
         completion:(void (^)(FeedbackRecord *_Nullable record, NSError *_Nullable error))completion;

+ (void)closeFeedback:(NSString *)feedbackNo
           completion:(void (^)(NSError *_Nullable error))completion;

@end

NS_ASSUME_NONNULL_END
