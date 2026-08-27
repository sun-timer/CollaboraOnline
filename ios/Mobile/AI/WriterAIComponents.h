// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

#pragma mark - Floating AI shortcut button

@interface WriterAFloatingAIButton : UIButton
- (instancetype)initWithOnAction:(void (^)(NSString *action))onAction;
@end

#pragma mark - AI function panel (bottom sheet)

@interface WriterAIPanelView : UIView
- (instancetype)initWithWidth:(CGFloat)width
                       onTile:(void (^)(NSString *taskType))onTile
                      onClose:(void (^)(void))onClose;
- (void)showIn:(UIView *)parent aboveBottomInset:(CGFloat)inset;
- (void)dismiss;
@end

#pragma mark - AI result modal (streaming / ready / error)

@interface WriterAAIResultModal : UIView
- (instancetype)initWithTitle:(NSString *)title
                      onClose:(void (^)(void))onClose
                       onStop:(void (^)(void))onStop
                      onRetry:(void (^)(void))onRetry
                      onInsert:(void (^)(void))onInsert
                        onCopy:(void (^)(void))onCopy;
- (void)showIn:(UIView *)parent;
- (void)setStreamingText:(NSString *)text;
- (void)setReadyWithFullText:(NSString *)text;
- (void)setErrorWithMessage:(NSString *)message;
- (void)dismiss;
@end

#pragma mark - Target language picker

@interface WriterALanguagePicker : UIView
- (instancetype)initWithOnSelect:(void (^)(NSString *lang))onSelect;
- (void)showIn:(UIView *)parent;
- (void)dismiss;
@end

#pragma mark - Shared icons (Figma-sourced; replaces SF Symbols)

@interface UIImage (WriterAIIcons)
+ (nullable UIImage *)writerIconNamed:(nonnull NSString *)name;
@end

NS_ASSUME_NONNULL_END