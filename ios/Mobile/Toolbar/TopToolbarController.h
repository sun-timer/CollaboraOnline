// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

typedef NS_ENUM(NSInteger, IOSTopToolbarMode) {
    IOSTopToolbarModePreview,
    IOSTopToolbarModeEdit,
};

@protocol IOSTopToolbarControllerDelegate <NSObject>
- (void)topToolbarDidPressBack;
- (void)topToolbarDidPressDone;
- (void)topToolbarDidPressUndo;
- (void)topToolbarDidPressRedo;
- (void)topToolbarDidPressSearch;
- (void)topToolbarDidPressShare;
- (void)topToolbarDidPressDocuments;
- (void)topToolbarDidPressComment;
- (void)topToolbarDidPressClose;
@end

@interface IOSTopToolbarController : NSObject

@property (nonatomic, readonly) UIView *view;
@property (nonatomic, assign) IOSTopToolbarMode mode;
@property (nonatomic, copy) NSString *documentTitle;
@property (nonatomic, assign) BOOL undoEnabled;
@property (nonatomic, assign) BOOL redoEnabled;

- (instancetype)initWithDelegate:(id<IOSTopToolbarControllerDelegate>)delegate;
- (void)setEditMode:(BOOL)editMode;
- (void)setDocumentType:(NSString *)documentType;
- (void)relayout;

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
