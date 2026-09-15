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
static const NSInteger kFillCellTag = 9;
static const NSInteger kMergeCellTag = 10;
static const NSInteger kSlideshowTag = 11;

static UIColor *bottomToolbarIconTintColor(void)
{
    return [UIColor colorWithRed:32.0 / 255.0 green:33.0 / 255.0 blue:36.0 / 255.0 alpha:1.0];
}

@interface IOSBottomToolbarController ()
@property (nonatomic, strong, readwrite) UIView *view;
@property (nonatomic, weak) id<IOSBottomToolbarControllerDelegate> delegate;
@property (nonatomic, strong) UIScrollView *scrollView;
@property (nonatomic, strong) UIStackView *itemsStack;
@property (nonatomic, strong, nullable) NSLayoutConstraint *previewEqualWidthConstraint;
@property (nonatomic, copy) NSString *documentType;
@property (nonatomic, assign, readwrite) CGFloat preferredHeight;
@end

static UIControl *toolbarItem(NSString *iconName, NSString *title, NSInteger tag)
{
    UIControl *item = [[UIControl alloc] init];
    item.translatesAutoresizingMaskIntoConstraints = NO;
    item.tag = tag;
    item.accessibilityLabel = title;
    item.accessibilityIdentifier = [NSString stringWithFormat:@"bottomToolbarItem_%@", title];

    UIStackView *content = [[UIStackView alloc] init];
    content.translatesAutoresizingMaskIntoConstraints = NO;
    content.axis = UILayoutConstraintAxisVertical;
    content.alignment = UIStackViewAlignmentCenter;
    content.spacing = 3.0;
    content.userInteractionEnabled = NO;

    UIImageView *icon = [[UIImageView alloc] initWithImage:[UIImage writerIconNamed:iconName]];
    icon.translatesAutoresizingMaskIntoConstraints = NO;
    icon.tintColor = bottomToolbarIconTintColor();
    icon.contentMode = UIViewContentModeScaleAspectFit;
    [icon.widthAnchor constraintEqualToConstant:24.0].active = YES;
    [icon.heightAnchor constraintEqualToConstant:24.0].active = YES;

    UILabel *label = [[UILabel alloc] init];
    label.translatesAutoresizingMaskIntoConstraints = NO;
    label.text = title;
    label.textColor = bottomToolbarIconTintColor();
    label.font = [UIFont systemFontOfSize:14.0];
    label.textAlignment = NSTextAlignmentCenter;

    [content addArrangedSubview:icon];
    [content addArrangedSubview:label];
    [item addSubview:content];
    [NSLayoutConstraint activateConstraints:@[
        [content.centerXAnchor constraintEqualToAnchor:item.centerXAnchor],
        [content.centerYAnchor constraintEqualToAnchor:item.centerYAnchor],
        [item.widthAnchor constraintGreaterThanOrEqualToConstant:72.0],
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
    _itemsStack.distribution = UIStackViewDistributionFillEqually;
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

- (NSArray<NSArray<NSString *> *> *)previewItems
{
    return @[
        @[@"mobile-preview", @"手机预览", [NSString stringWithFormat:@"%ld", (long)kMobilePreviewTag]],
        @[@"function", @"功能", [NSString stringWithFormat:@"%ld", (long)kFunctionTag]],
        @[@"ai-assistant", @"AI助手", [NSString stringWithFormat:@"%ld", (long)kAIAssistantTag]],
    ];
}

- (NSArray<NSArray<NSString *> *> *)sharedEditPrefixItems
{
    return @[
        @[@"mobile-preview", @"手机预览", [NSString stringWithFormat:@"%ld", (long)kMobilePreviewTag]],
        @[@"function", @"功能", [NSString stringWithFormat:@"%ld", (long)kFunctionTag]],
        @[@"ai-assistant", @"AI助手", [NSString stringWithFormat:@"%ld", (long)kAIAssistantTag]],
        @[@"ai-feature", @"AI功能", [NSString stringWithFormat:@"%ld", (long)kAIFeaturesTag]],
        @[@"keyboard", @"呼出键盘", [NSString stringWithFormat:@"%ld", (long)kKeyboardTag]],
        @[@"character", @"字符", [NSString stringWithFormat:@"%ld", (long)kCharacterTag]],
    ];
}

- (NSArray<NSArray<NSString *> *> *)editItemsForDocumentType
{
    BOOL isCalc = [self.documentType isEqualToString:@"spreadsheet"];
    BOOL isPresentation = [self.documentType isEqualToString:@"presentation"];

    if (isPresentation) {
        return @[
            @[@"function", @"功能", [NSString stringWithFormat:@"%ld", (long)kFunctionTag]],
            @[@"ai-assistant", @"AI助手", [NSString stringWithFormat:@"%ld", (long)kAIAssistantTag]],
            @[@"ai-feature", @"AI功能", [NSString stringWithFormat:@"%ld", (long)kAIFeaturesTag]],
            @[@"keyboard", @"呼出键盘", [NSString stringWithFormat:@"%ld", (long)kKeyboardTag]],
            @[@"character", @"字符", [NSString stringWithFormat:@"%ld", (long)kCharacterTag]],
            @[@"paragraph", @"段落", [NSString stringWithFormat:@"%ld", (long)kParagraphTag]],
            @[@"insert-image", @"插入图片", [NSString stringWithFormat:@"%ld", (long)kInsertImageTag]],
            @[@"slideshow-play", @"幻灯片播放", [NSString stringWithFormat:@"%ld", (long)kSlideshowTag]],
        ];
    }

    NSMutableArray<NSArray<NSString *> *> *items =
        [NSMutableArray arrayWithArray:[self sharedEditPrefixItems]];
    if (isCalc) {
        [items addObject:@[@"fill-cell", @"填充单元格", [NSString stringWithFormat:@"%ld", (long)kFillCellTag]]];
        [items addObject:@[@"merge-cell", @"合并单元格", [NSString stringWithFormat:@"%ld", (long)kMergeCellTag]]];
    } else {
        [items addObject:@[@"paragraph", @"段落", [NSString stringWithFormat:@"%ld", (long)kParagraphTag]]];
        [items addObject:@[@"insert-image", @"插入图片", [NSString stringWithFormat:@"%ld", (long)kInsertImageTag]]];
    }
    return items;
}

- (void)rebuildItems
{
    for (UIView *view in self.itemsStack.arrangedSubviews.copy) {
        [self.itemsStack removeArrangedSubview:view];
        [view removeFromSuperview];
    }

    BOOL isPreview = (self.mode == IOSBottomToolbarModePreview);
    NSArray<NSArray<NSString *> *> *items =
        isPreview ? [self previewItems] : [self editItemsForDocumentType];

    if (isPreview) {
        self.itemsStack.distribution = UIStackViewDistributionFillEqually;
        self.scrollView.scrollEnabled = NO;
        self.scrollView.contentInset = UIEdgeInsetsZero;
        if (self.previewEqualWidthConstraint == nil) {
            self.previewEqualWidthConstraint =
                [self.itemsStack.widthAnchor constraintEqualToAnchor:self.scrollView.frameLayoutGuide.widthAnchor];
        }
        self.previewEqualWidthConstraint.active = YES;
    } else {
        self.itemsStack.distribution = UIStackViewDistributionFill;
        self.scrollView.scrollEnabled = YES;
        self.scrollView.contentInset = UIEdgeInsetsMake(0.0, 10.0, 0.0, 10.0);
        if (self.previewEqualWidthConstraint != nil) {
            self.previewEqualWidthConstraint.active = NO;
        }
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
        case kFillCellTag:
            [self.delegate bottomToolbarDidPressFillCell];
            break;
        case kMergeCellTag:
            [self.delegate bottomToolbarDidPressMergeCell];
            break;
        case kSlideshowTag:
            [self.delegate bottomToolbarDidPressSlideshow];
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
