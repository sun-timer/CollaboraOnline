// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Mirrors Android LOActivity extras for new-document AI auto-generate.
@interface DocumentPresentationLaunchOptions : NSObject

@property (assign, nonatomic) BOOL autoGenerateAiContent;
@property (assign, nonatomic) BOOL autoOpenImpressOutline;
@property (copy, nonatomic, nullable) NSString *autoAiPrompt;
@property (copy, nonatomic, nullable) NSString *autoUserDescription;
@property (assign, nonatomic) BOOL autoIsCalcNewTable;

+ (instancetype)optionsWithAutoGenerate:(BOOL)autoGenerate
                               aiPrompt:(nullable NSString *)aiPrompt
                        userDescription:(nullable NSString *)userDescription
                      calcNewTable:(BOOL)calcNewTable;

@end

NS_ASSUME_NONNULL_END
