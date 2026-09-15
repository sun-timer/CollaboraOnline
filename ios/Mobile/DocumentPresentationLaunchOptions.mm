// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "DocumentPresentationLaunchOptions.h"

@implementation DocumentPresentationLaunchOptions

+ (instancetype)optionsWithAutoGenerate:(BOOL)autoGenerate
                               aiPrompt:(NSString *)aiPrompt
                        userDescription:(NSString *)userDescription
                      calcNewTable:(BOOL)calcNewTable {
    DocumentPresentationLaunchOptions *options = [[DocumentPresentationLaunchOptions alloc] init];
    options.autoGenerateAiContent = autoGenerate;
    options.autoOpenImpressOutline = NO;
    options.autoAiPrompt = [aiPrompt copy];
    options.autoUserDescription = [userDescription copy];
    options.autoIsCalcNewTable = calcNewTable;
    return options;
}

@end
