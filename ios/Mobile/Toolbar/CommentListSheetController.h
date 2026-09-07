// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface WriterCommentListItem : NSObject
@property (nonatomic, copy) NSString *commentId;
@property (nonatomic, copy) NSString *author;
@property (nonatomic, copy) NSString *text;
@property (nonatomic, copy) NSString *dateTime;
@end

@protocol CommentListSheetControllerDelegate <NSObject>
- (void)commentListSheetDidSelectCommentId:(NSString *)commentId;
@end

/// Writer 批注列表 sheet（Figma 3141:58043 语义）。
@interface CommentListSheetController : UIViewController

@property (nonatomic, weak, nullable) id<CommentListSheetControllerDelegate> actionDelegate;

+ (instancetype)presentFrom:(UIViewController *)host
                   comments:(NSArray<WriterCommentListItem *> *)comments
                   delegate:(id<CommentListSheetControllerDelegate>)delegate;

@end

NS_ASSUME_NONNULL_END

// vim:set shiftwidth=4 softtabstop=4 expandtab:
