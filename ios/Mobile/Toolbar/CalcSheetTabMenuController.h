// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

typedef void (^CalcSheetTabRenameHandler)(NSInteger tabIndex, NSString *currentName);
typedef void (^CalcSheetTabCopyHandler)(NSInteger tabIndex);
typedef void (^CalcSheetTabDeleteHandler)(NSInteger tabIndex, NSString *name);

/// Presents the Calc sheet rename and copy actions anchored to a tab.
@interface CalcSheetTabMenuController : UIViewController

@property (assign, nonatomic, readonly) NSInteger tabIndex;
@property (copy, nonatomic, readonly) NSString *sheetName;
@property (assign, nonatomic, readonly, getter=isProtected) BOOL protectedSheet;
@property (assign, nonatomic, readonly) BOOL canDelete;
@property (assign, nonatomic, readonly) CGRect anchorRect;

- (instancetype)initWithTabIndex:(NSInteger)tabIndex
                       sheetName:(NSString *)sheetName
                     isProtected:(BOOL)isProtected
                        canDelete:(BOOL)canDelete
                       anchorRect:(CGRect)anchorRect
                         onRename:(nullable CalcSheetTabRenameHandler)onRename
                           onCopy:(nullable CalcSheetTabCopyHandler)onCopy
                         onDelete:(nullable CalcSheetTabDeleteHandler)onDelete NS_DESIGNATED_INITIALIZER;

- (instancetype)initWithNibName:(nullable NSString *)nibNameOrNil
                         bundle:(nullable NSBundle *)nibBundleOrNil NS_UNAVAILABLE;
- (instancetype)initWithCoder:(NSCoder *)coder NS_UNAVAILABLE;

/// Present the floating menu using anchorRect in sourceView coordinates.
- (void)presentFromViewController:(UIViewController *)host
                       sourceView:(UIView *)sourceView
                         animated:(BOOL)animated;

/// Invoke the existing operation callbacks. Protected sheets cannot be renamed or deleted.
- (void)performRename;
- (void)performCopy;
/// For a non-deletable sheet the owner may keep the current explanatory notice behavior.
- (void)performDelete;

@end

NS_ASSUME_NONNULL_END
