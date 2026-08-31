// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

typedef NS_ENUM(NSInteger, IOSBottomToolbarMode) {
    IOSBottomToolbarModePreview,
    IOSBottomToolbarModeEdit,
};

@protocol IOSBottomToolbarControllerDelegate <NSObject>
- (void)bottomToolbarDidPressMobilePreview;
- (void)bottomToolbarDidPressFunction;
- (void)bottomToolbarDidPressAIAssistant;
- (void)bottomToolbarDidPressAIFeatures;
- (void)bottomToolbarDidPressKeyboard;
- (void)bottomToolbarDidPressCharacter;
- (void)bottomToolbarDidPressParagraph;
- (void)bottomToolbarDidPressInsertImage;
- (void)bottomToolbarDidPressFillCell;
- (void)bottomToolbarDidPressMergeCell;
@end

@interface IOSBottomToolbarController : NSObject

@property (nonatomic, readonly) UIView *view;
@property (nonatomic, assign) IOSBottomToolbarMode mode;
@property (nonatomic, assign, getter=isCompact) BOOL compact;
@property (nonatomic, readonly) CGFloat preferredHeight;

- (instancetype)initWithDelegate:(id<IOSBottomToolbarControllerDelegate>)delegate;
- (void)setEditMode:(BOOL)editMode;
- (void)setDocumentType:(NSString *)documentType;
- (void)relayout;

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
