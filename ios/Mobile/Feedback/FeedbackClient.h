// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

@class FeedbackRecord;

NS_ASSUME_NONNULL_BEGIN

@interface FeedbackListPage : NSObject
@property (assign, nonatomic) NSInteger pageNum;
@property (assign, nonatomic) NSInteger pageSize;
@property (assign, nonatomic) NSInteger total;
@property (assign, nonatomic) NSInteger pages;
@property (copy, nonatomic) NSArray<FeedbackRecord *> *list;
@end

@interface FeedbackClient : NSObject

+ (NSString *)uploadAvatarWithUserId:(NSString *)userId filePath:(NSString *)filePath error:(NSError **)error;
+ (NSArray<NSString *> *)uploadImagesWithFilePaths:(NSArray<NSString *> *)filePaths error:(NSError **)error;
+ (nullable NSString *)uploadLogWithUserId:(NSString *)userId fileURL:(NSURL *)fileURL error:(NSError **)error;

+ (nullable FeedbackRecord *)submitWithUserId:(NSString *)userId
                                     nickname:(NSString *)nickname
                                       avatar:(NSString *)avatarPath
                                 feedbackType:(NSString *)feedbackType
                                      content:(NSString *)content
                                      contact:(NSString *)contact
                                   imagePaths:(NSArray<NSString *> *)imagePaths
                                      logPath:(nullable NSString *)logPath
                                   appVersion:(NSString *)appVersion
                                  deviceModel:(NSString *)deviceModel
                                    osVersion:(NSString *)osVersion
                                        error:(NSError **)error;

+ (nullable FeedbackListPage *)listWithUserId:(NSString *)userId
                                     nickname:(NSString *)nickname
                                       avatar:(NSString *)avatarPath
                                      pageNum:(NSInteger)pageNum
                                     pageSize:(NSInteger)pageSize
                                        error:(NSError **)error;

+ (nullable FeedbackRecord *)detailWithUserId:(NSString *)userId
                                       nickname:(NSString *)nickname
                                         avatar:(NSString *)avatarPath
                                     feedbackNo:(NSString *)feedbackNo
                                          error:(NSError **)error;

+ (BOOL)closeWithUserId:(NSString *)userId
               nickname:(NSString *)nickname
                 avatar:(NSString *)avatarPath
             feedbackNo:(NSString *)feedbackNo
                  error:(NSError **)error;

+ (NSData *)prepareUploadImageDataFromPath:(NSString *)path error:(NSError **)error;
+ (NSString *)deviceModel;
+ (NSString *)osVersion;
+ (NSString *)appVersion;

@end

NS_ASSUME_NONNULL_END
