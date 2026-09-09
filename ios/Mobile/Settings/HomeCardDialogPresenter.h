// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, HomeCardDialogConfirmStyle) {
    HomeCardDialogConfirmStylePrimary,
    HomeCardDialogConfirmStyleDestructive,
};

@interface HomeCardDialogPresenter : NSObject

+ (void)presentRenameFrom:(UIViewController *)host
              currentName:(NSString *)currentName
               completion:(void (^)(NSString *newName))completion;

+ (void)presentConfirmFrom:(UIViewController *)host
                     title:(NSString *)title
                   message:(NSString *)message
              confirmStyle:(HomeCardDialogConfirmStyle)confirmStyle
                completion:(void (^)(BOOL confirmed))completion;

+ (void)presentConfirmFrom:(UIViewController *)host
                     title:(NSString *)title
                   message:(NSString *)message
              confirmTitle:(nullable NSString *)confirmTitle
              confirmStyle:(HomeCardDialogConfirmStyle)confirmStyle
                completion:(void (^)(BOOL confirmed))completion;

+ (void)presentNicknameEditFrom:(UIViewController *)host
                    currentName:(NSString *)currentName
                     completion:(void (^)(NSString *nickname))completion;

+ (void)presentSetAvatarFrom:(UIViewController *)host
                   takePhoto:(void (^)(void))takePhoto
                 pickGallery:(void (^)(void))pickGallery;

@end

NS_ASSUME_NONNULL_END
