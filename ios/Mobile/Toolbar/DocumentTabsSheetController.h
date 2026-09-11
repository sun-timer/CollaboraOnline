// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

@class RecentDocumentsStore;

NS_ASSUME_NONNULL_BEGIN

@protocol DocumentTabsSheetControllerDelegate <NSObject>
- (nullable NSURL *)currentDocumentURLForDocumentTabsSheet;
- (void)documentTabsSheetDidSelectURL:(NSURL *)url;
- (void)documentTabsSheetDidRequestOpenDocument;
- (void)documentTabsSheetDidChangeDocumentList;
- (void)documentTabsSheetDidDismiss;
@end

/// Preview-mode document tabs sheet: opened + recently closed lists.
@interface DocumentTabsSheetController : UIViewController

@property (nonatomic, weak, nullable) id<DocumentTabsSheetControllerDelegate> actionDelegate;

+ (instancetype)presentFrom:(UIViewController *)host
                       store:(RecentDocumentsStore *)store
                 currentURL:(nullable NSURL *)currentURL
                   delegate:(id<DocumentTabsSheetControllerDelegate>)delegate;

@end

NS_ASSUME_NONNULL_END

// vim:set shiftwidth=4 softtabstop=4 expandtab:
