// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@protocol PreviewFunctionSheetControllerDelegate <NSObject>
- (void)previewFunctionSheetDidRequestSave;
- (void)previewFunctionSheetDidRequestExportPDF;
- (void)previewFunctionSheetDidRequestPrint;
- (void)previewFunctionSheetDidRequestFindReplace;
@end

/// Preview-mode「功能」sheet: 文件操作 + 审阅 (Android lolib_sheet_functions parity).
@interface PreviewFunctionSheetController : UIViewController

@property (nonatomic, weak, nullable) id<PreviewFunctionSheetControllerDelegate> actionDelegate;

+ (instancetype)presentFrom:(UIViewController *)host
                   delegate:(id<PreviewFunctionSheetControllerDelegate>)delegate;

@end

NS_ASSUME_NONNULL_END
