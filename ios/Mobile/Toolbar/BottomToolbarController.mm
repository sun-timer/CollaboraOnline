// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AI/WriterAIComponents.h"
#import "BottomToolbarController.h"

static const NSInteger kMobilePreviewTag = 1;
static const NSInteger kFunctionTag = 2;
static const NSInteger kAIAssistantTag = 3;
static const NSInteger kAIFeaturesTag = 4;
static const NSInteger kKeyboardTag = 5;
static const NSInteger kCharacterTag = 6;
static const NSInteger kParagraphTag = 7;
static const NSInteger kInsertImageTag = 8;

@interface IOSBottomToolbarController ()
@property (nonatomic, strong, readwrite) UIView *view;
@property (nonatomic, weak) id<IOSBottomToolbarControllerDelegate> delegate;
@property (nonatomic, strong) UIScrollView *scrollView;
@property (nonatomic, strong) UIStackView *itemsStack;
@property (nonatomic, copy) NSString *documentType;
@property (nonatomic, assign, readwrite) CGFloat preferredHeight;
@end

static UIControl *toolbarItem(NSString *iconName, NSString *title, NSInteger tag)
{
    UIControl *item = [[UIControl alloc] init];
    item.translatesAutoresizingMaskIntoConstraints = NO;
    item.tag = tag;
    item.accessibilityLabel = title;

    UIStackView *content = [[UIStackView alloc] init];
    content.translatesAutoresizingMaskIntoConstraints = NO;
    content.axis = UILayoutConstraintAxisVertical;
    content.alignment = UIStackViewAlignmentCenter;
    content.spacing = 3.0;
    content.userInteractionEnabled = NO;

    UIImageView *icon = [[UIImageView alloc] initWithImage:[UIImage writerIconNamed:iconName]];
    icon.translatesAutoresizingMaskIntoConstraints = NO;
    icon.tintColor = [UIColor colorWithRed:0.12 green:0.12 blue:0.13 alpha:1.0];
    icon.contentMode = UIViewContentModeScaleAspectFit;
    [icon.widthAnchor constraintEqualToConstant:24.0].active = YES;
    [icon.heightAnchor constraintEqualToConstant:24.0].active = YES;

    UILabel *label = [[UILabel alloc] init];
    label.translatesAutoresizingMaskIntoConstraints = NO;
    label.text = title;
    label.textColor = [UIColor colorWithRed:0.12 green:0.12 blue:0.13 alpha:1.0];
    label.font = [UIFont systemFontOfSize:14.0];
    label.textAlignment = NSTextAlignmentCenter;

    [content addArrangedSubview:icon];
    [content addArrangedSubview:label];
    [item addSubview:content];
    [NSLayoutConstraint activateConstraints:@[
        [content.centerXAnchor constraintEqualToAnchor:item.centerXAnchor],
        [content.centerYAnchor constraintEqualToAnchor:item.centerYAnchor],
        [item.widthAnchor constraintGreaterThanOrEqualToConstant:92.0],
        [item.heightAnchor constraintEqualToConstant:82.0],
    ]];
    return item;
}

@implementation IOSBottomToolbarController

- (instancetype)initWithDelegate:(id<IOSBottomToolbarControllerDelegate>)delegate
{
    self = [super init];
    if (!self) {
        return nil;
    }

    _delegate = delegate;
    _mode = IOSBottomToolbarModePreview;
    _documentType = @"text";
    _preferredHeight = 82.0;

    _view = [[UIView alloc] init];
    _view.translatesAutoresizingMaskIntoConstraints = NO;
    _view.backgroundColor = UIColor.whiteColor;
    _view.layer.shadowColor = UIColor.blackColor.CGColor;
    _view.layer.shadowOpacity = 0.10;
    _view.layer.shadowRadius = 4.0;
    _view.layer.shadowOffset = CGSizeMake(0.0, -1.0);

    _scrollView = [[UIScrollView alloc] init];
    _scrollView.translatesAutoresizingMaskIntoConstraints = NO;
    _scrollView.showsHorizontalScrollIndicator = NO;
    _scrollView.alwaysBounceVertical = NO;
    _scrollView.alwaysBounceHorizontal = YES;
    [_view addSubview:_scrollView];

    _itemsStack = [[UIStackView alloc] init];
    _itemsStack.translatesAutoresizingMaskIntoConstraints = NO;
    _itemsStack.axis = UILayoutConstraintAxisHorizontal;
    _itemsStack.alignment = UIStackViewAlignmentFill;
    _itemsStack.distribution = UIStackViewDistributionFill;
    [_scrollView addSubview:_itemsStack];

    [NSLayoutConstraint activateConstraints:@[
        [_scrollView.topAnchor constraintEqualToAnchor:_view.topAnchor],
        [_scrollView.leadingAnchor constraintEqualToAnchor:_view.leadingAnchor],
        [_scrollView.trailingAnchor constraintEqualToAnchor:_view.trailingAnchor],
        [_scrollView.bottomAnchor constraintEqualToAnchor:_view.bottomAnchor],
        [_itemsStack.topAnchor constraintEqualToAnchor:_scrollView.contentLayoutGuide.topAnchor],
        [_itemsStack.leadingAnchor constraintEqualToAnchor:_scrollView.contentLayoutGuide.leadingAnchor],
        [_itemsStack.trailingAnchor constraintEqualToAnchor:_scrollView.contentLayoutGuide.trailingAnchor],
        [_itemsStack.bottomAnchor constraintEqualToAnchor:_scrollView.contentLayoutGuide.bottomAnchor],
        [_itemsStack.heightAnchor constraintEqualToAnchor:_scrollView.frameLayoutGuide.heightAnchor],
    ]];

    [self rebuildItems];
    return self;
}

- (void)setMode:(IOSBottomToolbarMode)mode
{
    _mode = mode;
    [self rebuildItems];
}

- (void)setEditMode:(BOOL)editMode
{
    self.mode = editMode ? IOSBottomToolbarModeEdit : IOSBottomToolbarModePreview;
}

- (void)setCompact:(BOOL)compact
{
    _compact = compact;
    self.preferredHeight = compact ? 48.0 : 82.0;
    [self applyCompactState];
    [self relayout];
}

- (void)setDocumentType:(NSString *)documentType
{
    _documentType = [documentType copy];
    [self rebuildItems];
}

- (void)rebuildItems
{
    for (UIView *view in self.itemsStack.arrangedSubviews.copy) {
        [self.itemsStack removeArrangedSubview:view];
        [view removeFromSuperview];
    }

    NSArray<NSArray<NSString *> *> *items = nil;
    if (self.mode == IOSBottomToolbarModePreview) {
        items = @[
            @[@"mobile-preview", @"适配手机", [NSString stringWithFormat:@"%ld", kMobilePreviewTag]],
            @[@"function", @"功能", [NSString stringWithFormat:@"%ld", kFunctionTag]],
            @[@"ai-assistant", @"AI 助手", [NSString stringWithFormat:@"%ld", kAIAssistantTag]],
            @[@"ai-feature", @"AI 功能", [NSString stringWithFormat:@"%ld", kAIFeaturesTag]],
            @[@"keyboard", @"呼出键盘", [NSString stringWithFormat:@"%ld", kKeyboardTag]],
        ];
    } else {
        NSMutableArray<NSArray<NSString *> *> *editItems = [NSMutableArray arrayWithArray:@[
            @[@"function", @"功能", [NSString stringWithFormat:@"%ld", kFunctionTag]],
            @[@"ai-feature", @"AI 功能", [NSString stringWithFormat:@"%ld", kAIFeaturesTag]],
            @[@"keyboard", @"呼出键盘", [NSString stringWithFormat:@"%ld", kKeyboardTag]],
            @[@"character", @"字符", [NSString stringWithFormat:@"%ld", kCharacterTag]],
        ]];
        if (![self.documentType isEqualToString:@"spreadsheet"]) {
            [editItems addObject:@[@"paragraph", @"段落", [NSString stringWithFormat:@"%ld", kParagraphTag]]];
            [editItems addObject:@[@"insert-image", @"插入图片", [NSString stringWithFormat:@"%ld", kInsertImageTag]]];
        }
        items = editItems;
    }

    for (NSArray<NSString *> *definition in items) {
        UIControl *item = toolbarItem(definition[0], definition[1], definition[2].integerValue);
        [item addTarget:self action:@selector(itemPressed:) forControlEvents:UIControlEventTouchUpInside];
        [self.itemsStack addArrangedSubview:item];
    }
    [self applyCompactState];
}

- (void)applyCompactState
{
    for (UIControl *item in self.itemsStack.arrangedSubviews) {
        UIStackView *content = item.subviews.firstObject;
        if ([content isKindOfClass:[UIStackView class]] && content.arrangedSubviews.count > 1) {
            UILabel *label = content.arrangedSubviews[1];
            label.hidden = self.compact;
        }
        for (NSLayoutConstraint *constraint in item.constraints) {
            if (constraint.firstAttribute == NSLayoutAttributeHeight) {
                constraint.constant = self.preferredHeight;
            }
        }
    }
}

- (void)itemPressed:(UIControl *)sender
{
    switch (sender.tag) {
        case kMobilePreviewTag:
            [self.delegate bottomToolbarDidPressMobilePreview];
            break;
        case kFunctionTag:
            [self.delegate bottomToolbarDidPressFunction];
            break;
        case kAIAssistantTag:
            [self.delegate bottomToolbarDidPressAIAssistant];
            break;
        case kAIFeaturesTag:
            [self.delegate bottomToolbarDidPressAIFeatures];
            break;
        case kKeyboardTag:
            [self.delegate bottomToolbarDidPressKeyboard];
            break;
        case kCharacterTag:
            [self.delegate bottomToolbarDidPressCharacter];
            break;
        case kParagraphTag:
            [self.delegate bottomToolbarDidPressParagraph];
            break;
        case kInsertImageTag:
            [self.delegate bottomToolbarDidPressInsertImage];
            break;
        default:
            break;
    }
}

- (void)relayout
{
    [self.view setNeedsLayout];
    [self.view layoutIfNeeded];
}

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
