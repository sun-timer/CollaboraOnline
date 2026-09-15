// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface PptxTemplateInfo : NSObject
@property (copy, nonatomic) NSString *templateId;
@property (copy, nonatomic) NSString *name;
@property (copy, nonatomic) NSString *templateDescription;
@property (copy, nonatomic) NSString *coverImage;
@property (copy, nonatomic) NSString *file;
@property (assign, nonatomic) NSInteger slideCount;
@property (assign, nonatomic) NSInteger maxPoints;
@property (copy, nonatomic) NSArray<NSNumber *> *variants;
@end

@interface PptxTemplateService : NSObject

+ (NSArray<PptxTemplateInfo *> *)loadIndex;
+ (nullable PptxTemplateInfo *)templateWithId:(NSString *)templateId;
+ (nullable UIImage *)coverImageForTemplate:(PptxTemplateInfo *)info;
+ (nullable NSURL *)fillAndAssembleTemplateId:(NSString *)templateId
                                outlineSlides:(NSArray *)outlineSlides
                            generatedByIndex:(NSDictionary<NSNumber *, NSDictionary *> *)generatedByIndex
                                  outputName:(NSString *)outputName
                                       error:(NSError * _Nullable * _Nullable)error;

@end

NS_ASSUME_NONNULL_END
