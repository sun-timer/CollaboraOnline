// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AI/WriterAIComponents.h"
#import "TopToolbarController.h"

// Figma 145:3222 — done capsule 288×124 @2x → 72×31 pt; content row 56 pt.
static const CGFloat kTopToolbarContentHeight = 56.0;
static const CGFloat kTopToolbarDoneMinWidth = 72.0;
static const CGFloat kTopToolbarDoneHeight = 31.0;
static const CGFloat kTopToolbarIconButtonSize = 48.0;
static const CGFloat kTopToolbarCloseButtonSize = 40.0;
static const CGFloat kTopToolbarPreviewPaddingH = 12.0;
static const CGFloat kTopToolbarEditPaddingStart = 8.0;
static const CGFloat kTopToolbarEditPaddingEnd = 4.0;

static UIColor *topToolbarChromeColor(void)
{
    return [UIColor colorWithRed:242.0 / 255.0 green:242.0 / 255.0 blue:242.0 / 255.0 alpha:1.0];
}

static UIColor *topToolbarIconTintColor(void)
{
    return [UIColor colorWithRed:16.0 / 255.0 green:16.0 / 255.0 blue:16.0 / 255.0 alpha:1.0];
}

@interface IOSTopToolbarController ()
@property (nonatomic, strong, readwrite) UIView *view;
@property (nonatomic, weak) id<IOSTopToolbarControllerDelegate> delegate;
@property (nonatomic, strong) UIView *previewRow;
@property (nonatomic, strong) UIView *editRow;
@property (nonatomic, strong) UILabel *titleLabel;
@property (nonatomic, strong) UIButton *doneButton;
@property (nonatomic, strong) UIButton *undoButton;
@property (nonatomic, strong) UIButton *redoButton;
@property (nonatomic, strong) UIButton *commentButton;
@property (nonatomic, strong) UIButton *searchButton;
@property (nonatomic, strong) UIView *commentBadge;
@property (nonatomic, strong) UILabel *openDocsCountLabel;
@property (nonatomic, strong) UIButton *editDocumentsButton;
@property (nonatomic, strong) UILabel *editOpenDocsCountLabel;
@property (nonatomic, copy) NSString *documentType;
@end

static void attachOpenDocsCountLabel(UIButton *button, UILabel **labelOut)
{
    UILabel *label = [[UILabel alloc] init];
    label.translatesAutoresizingMaskIntoConstraints = NO;
    label.font = [UIFont systemFontOfSize:10 weight:UIFontWeightSemibold];
    label.textColor = topToolbarIconTintColor();
    label.textAlignment = NSTextAlignmentCenter;
    label.text = @"1";
    [button addSubview:label];
    [NSLayoutConstraint activateConstraints:@[
        [label.centerXAnchor constraintEqualToAnchor:button.centerXAnchor],
        [label.topAnchor constraintEqualToAnchor:button.topAnchor constant:18.0],
    ]];
    *labelOut = label;
}

static void configureIconButton(UIButton *button, UIImage *image, UIEdgeInsets contentInsets)
{
    button.translatesAutoresizingMaskIntoConstraints = NO;
    button.contentEdgeInsets = contentInsets;
    button.imageView.contentMode = UIViewContentModeScaleAspectFit;
    button.tintColor = topToolbarIconTintColor();
    if (image != nil) {
        [button setImage:image forState:UIControlStateNormal];
    }
}

static UIButton *toolbarIconButton(NSString *iconName,
                                   NSString *accessibilityLabel,
                                   UIEdgeInsets contentInsets)
{
    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    configureIconButton(button, [UIImage writerIconNamed:iconName], contentInsets);
    button.accessibilityLabel = accessibilityLabel;
    [button.widthAnchor constraintEqualToConstant:kTopToolbarIconButtonSize].active = YES;
    [button.heightAnchor constraintEqualToConstant:kTopToolbarIconButtonSize].active = YES;
    return button;
}

static UIButton *toolbarCloseButton(NSString *accessibilityLabel)
{
    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    configureIconButton(button,
                        [UIImage writerIconNamed:@"close"],
                        UIEdgeInsetsMake(8.0, 8.0, 8.0, 8.0));
    button.accessibilityLabel = accessibilityLabel;
    [button.widthAnchor constraintEqualToConstant:kTopToolbarCloseButtonSize].active = YES;
    [button.heightAnchor constraintEqualToConstant:kTopToolbarCloseButtonSize].active = YES;
    return button;
}

static UIView *toolbarSpacer(void)
{
    UIView *spacer = [[UIView alloc] init];
    spacer.translatesAutoresizingMaskIntoConstraints = NO;
    return spacer;
}

@implementation IOSTopToolbarController

- (instancetype)initWithDelegate:(id<IOSTopToolbarControllerDelegate>)delegate
{
    self = [super init];
    if (!self) {
        return nil;
    }

    _delegate = delegate;
    _mode = IOSTopToolbarModePreview;
    _documentTitle = @"文档";

    _view = [[UIView alloc] init];
    _view.translatesAutoresizingMaskIntoConstraints = NO;
    _view.backgroundColor = topToolbarChromeColor();
    _view.layer.shadowColor = UIColor.blackColor.CGColor;
    _view.layer.shadowOpacity = 0.08;
    _view.layer.shadowRadius = 3.0;
    _view.layer.shadowOffset = CGSizeMake(0.0, 1.0);

    _previewRow = [[UIView alloc] init];
    _previewRow.translatesAutoresizingMaskIntoConstraints = NO;
    [_view addSubview:_previewRow];

    UIButton *backButton = toolbarIconButton(@"back",
                                             @"返回",
                                             UIEdgeInsetsMake(12.0, 16.0, 12.0, 8.0));
    [backButton addTarget:self action:@selector(backPressed:) forControlEvents:UIControlEventTouchUpInside];
    [_previewRow addSubview:backButton];

    _titleLabel = [[UILabel alloc] init];
    _titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    _titleLabel.text = _documentTitle;
    _titleLabel.textColor = topToolbarIconTintColor();
    _titleLabel.font = [UIFont systemFontOfSize:18.0];
    _titleLabel.lineBreakMode = NSLineBreakByTruncatingTail;
    [_previewRow addSubview:_titleLabel];

    _searchButton = toolbarIconButton(@"search",
                                      @"查找替换",
                                      UIEdgeInsetsMake(12.0, 12.0, 12.0, 12.0));
    [_searchButton addTarget:self action:@selector(searchPressed:)
            forControlEvents:UIControlEventTouchUpInside];
    [_previewRow addSubview:_searchButton];

    UIButton *shareButton = toolbarIconButton(@"share",
                                              @"分享",
                                              UIEdgeInsetsMake(12.0, 12.0, 12.0, 12.0));
    [shareButton addTarget:self action:@selector(sharePressed:) forControlEvents:UIControlEventTouchUpInside];
    [_previewRow addSubview:shareButton];

    UIButton *previewDocumentsButton = toolbarIconButton(@"open-docs",
                                                         @"已打开文档",
                                                         UIEdgeInsetsMake(12.0, 12.0, 12.0, 12.0));
    [previewDocumentsButton addTarget:self action:@selector(documentsPressed:)
                     forControlEvents:UIControlEventTouchUpInside];
    [_previewRow addSubview:previewDocumentsButton];
    UILabel *previewOpenDocsCountLabel = nil;
    attachOpenDocsCountLabel(previewDocumentsButton, &previewOpenDocsCountLabel);
    _openDocsCountLabel = previewOpenDocsCountLabel;

    _editRow = [[UIView alloc] init];
    _editRow.translatesAutoresizingMaskIntoConstraints = NO;
    [_view addSubview:_editRow];

    _doneButton = [UIButton buttonWithType:UIButtonTypeCustom];
    _doneButton.translatesAutoresizingMaskIntoConstraints = NO;
    [_doneButton setTitle:@"完成" forState:UIControlStateNormal];
    [_doneButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    _doneButton.titleLabel.font = [UIFont boldSystemFontOfSize:16.0];
    _doneButton.contentEdgeInsets = UIEdgeInsetsMake(6.0, 20.0, 6.0, 20.0);
    _doneButton.layer.cornerRadius = kTopToolbarDoneHeight / 2.0;
    _doneButton.clipsToBounds = YES;
    [_doneButton addTarget:self action:@selector(donePressed:) forControlEvents:UIControlEventTouchUpInside];
    [_editRow addSubview:_doneButton];

    _undoButton = toolbarIconButton(@"undo",
                                    @"撤销",
                                    UIEdgeInsetsMake(8.0, 8.0, 8.0, 8.0));
    [_undoButton addTarget:self action:@selector(undoPressed:) forControlEvents:UIControlEventTouchUpInside];
    [_editRow addSubview:_undoButton];

    _redoButton = toolbarIconButton(@"redo",
                                    @"重做",
                                    UIEdgeInsetsMake(8.0, 8.0, 8.0, 8.0));
    [_redoButton addTarget:self action:@selector(redoPressed:) forControlEvents:UIControlEventTouchUpInside];
    [_editRow addSubview:_redoButton];

    _commentButton = toolbarIconButton(@"comment",
                                       @"批注",
                                       UIEdgeInsetsMake(8.0, 8.0, 8.0, 8.0));
    [_commentButton addTarget:self action:@selector(commentPressed:) forControlEvents:UIControlEventTouchUpInside];
    [_editRow addSubview:_commentButton];

    _commentBadge = [[UIView alloc] init];
    _commentBadge.translatesAutoresizingMaskIntoConstraints = NO;
    _commentBadge.backgroundColor = [UIColor colorWithRed:1.0 green:59.0 / 255.0 blue:48.0 / 255.0 alpha:1.0];
    _commentBadge.layer.cornerRadius = 4.0;
    _commentBadge.hidden = YES;
    [_editRow addSubview:_commentBadge];

    _editDocumentsButton = toolbarIconButton(@"open-docs",
                                             @"已打开文档",
                                             UIEdgeInsetsMake(12.0, 12.0, 12.0, 12.0));
    [_editDocumentsButton addTarget:self action:@selector(documentsPressed:)
                   forControlEvents:UIControlEventTouchUpInside];
    [_editRow addSubview:_editDocumentsButton];
    UILabel *editOpenDocsCountLabel = nil;
    attachOpenDocsCountLabel(_editDocumentsButton, &editOpenDocsCountLabel);
    _editOpenDocsCountLabel = editOpenDocsCountLabel;

    UIButton *closeButton = toolbarCloseButton(@"关闭编辑");
    [closeButton addTarget:self action:@selector(closePressed:) forControlEvents:UIControlEventTouchUpInside];
    [_editRow addSubview:closeButton];

    [self installConstraintsForPreviewRow:backButton
                                    search:_searchButton
                                     share:shareButton
                                 documents:previewDocumentsButton];
    [self installConstraintsForEditRow:closeButton];
    [self setDocumentType:@"text"];
    [self updateVisibleRow];
    return self;
}

- (void)installConstraintsForPreviewRow:(UIButton *)backButton
                                  search:(UIButton *)searchButton
                                   share:(UIButton *)shareButton
                               documents:(UIButton *)documentsButton
{
    [NSLayoutConstraint activateConstraints:@[
        [_previewRow.topAnchor constraintEqualToAnchor:_view.topAnchor],
        [_previewRow.leadingAnchor constraintEqualToAnchor:_view.leadingAnchor],
        [_previewRow.trailingAnchor constraintEqualToAnchor:_view.trailingAnchor],
        [_previewRow.bottomAnchor constraintEqualToAnchor:_view.bottomAnchor],
        [_previewRow.heightAnchor constraintEqualToConstant:kTopToolbarContentHeight],
        [backButton.leadingAnchor constraintEqualToAnchor:_previewRow.leadingAnchor
                                               constant:kTopToolbarPreviewPaddingH],
        [backButton.centerYAnchor constraintEqualToAnchor:_previewRow.centerYAnchor],
        [_titleLabel.leadingAnchor constraintEqualToAnchor:backButton.trailingAnchor],
        [_titleLabel.centerYAnchor constraintEqualToAnchor:_previewRow.centerYAnchor],
        [_titleLabel.trailingAnchor constraintEqualToAnchor:searchButton.leadingAnchor constant:-8.0],
        [searchButton.centerYAnchor constraintEqualToAnchor:_previewRow.centerYAnchor],
        [shareButton.leadingAnchor constraintEqualToAnchor:searchButton.trailingAnchor],
        [shareButton.centerYAnchor constraintEqualToAnchor:_previewRow.centerYAnchor],
        [documentsButton.leadingAnchor constraintEqualToAnchor:shareButton.trailingAnchor],
        [documentsButton.trailingAnchor constraintEqualToAnchor:_previewRow.trailingAnchor
                                                       constant:-kTopToolbarPreviewPaddingH],
        [documentsButton.centerYAnchor constraintEqualToAnchor:_previewRow.centerYAnchor],
    ]];
}

- (void)installConstraintsForEditRow:(UIButton *)closeButton
{
    UIView *leftSpacer = toolbarSpacer();
    UIView *rightSpacer = toolbarSpacer();
    [_editRow addSubview:leftSpacer];
    [_editRow addSubview:rightSpacer];

    [NSLayoutConstraint activateConstraints:@[
        [_editRow.topAnchor constraintEqualToAnchor:_view.topAnchor],
        [_editRow.leadingAnchor constraintEqualToAnchor:_view.leadingAnchor],
        [_editRow.trailingAnchor constraintEqualToAnchor:_view.trailingAnchor],
        [_editRow.bottomAnchor constraintEqualToAnchor:_view.bottomAnchor],
        [_editRow.heightAnchor constraintEqualToConstant:kTopToolbarContentHeight],
        [_doneButton.leadingAnchor constraintEqualToAnchor:_editRow.leadingAnchor
                                                   constant:kTopToolbarEditPaddingStart],
        [_doneButton.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [_doneButton.widthAnchor constraintGreaterThanOrEqualToConstant:kTopToolbarDoneMinWidth],
        [_doneButton.heightAnchor constraintEqualToConstant:kTopToolbarDoneHeight],
        [leftSpacer.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [leftSpacer.heightAnchor constraintEqualToConstant:1.0],
        [rightSpacer.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [rightSpacer.heightAnchor constraintEqualToConstant:1.0],
        [_undoButton.leadingAnchor constraintEqualToAnchor:leftSpacer.trailingAnchor],
        [_undoButton.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [_redoButton.leadingAnchor constraintEqualToAnchor:_undoButton.trailingAnchor],
        [_redoButton.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [rightSpacer.leadingAnchor constraintEqualToAnchor:_redoButton.trailingAnchor],
        [_commentButton.leadingAnchor constraintEqualToAnchor:rightSpacer.trailingAnchor],
        [_commentButton.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [_editDocumentsButton.leadingAnchor constraintEqualToAnchor:_commentButton.trailingAnchor],
        [_editDocumentsButton.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [closeButton.leadingAnchor constraintEqualToAnchor:_editDocumentsButton.trailingAnchor],
        [closeButton.trailingAnchor constraintEqualToAnchor:_editRow.trailingAnchor
                                                   constant:-kTopToolbarEditPaddingEnd],
        [closeButton.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [leftSpacer.leadingAnchor constraintEqualToAnchor:_doneButton.trailingAnchor],
        [rightSpacer.widthAnchor constraintEqualToAnchor:leftSpacer.widthAnchor],
        [_commentBadge.topAnchor constraintEqualToAnchor:_commentButton.topAnchor constant:4.0],
        [_commentBadge.trailingAnchor constraintEqualToAnchor:_commentButton.trailingAnchor constant:-4.0],
        [_commentBadge.widthAnchor constraintEqualToConstant:8.0],
        [_commentBadge.heightAnchor constraintEqualToConstant:8.0],
    ]];
}

- (void)updateVisibleRow
{
    self.previewRow.hidden = self.mode != IOSTopToolbarModePreview;
    self.editRow.hidden = self.mode != IOSTopToolbarModeEdit;
}

- (void)setMode:(IOSTopToolbarMode)mode
{
    _mode = mode;
    [self updateVisibleRow];
}

- (void)setEditMode:(BOOL)editMode
{
    self.mode = editMode ? IOSTopToolbarModeEdit : IOSTopToolbarModePreview;
}

- (void)setDocumentTitle:(NSString *)documentTitle
{
    _documentTitle = [documentTitle copy];
    self.titleLabel.text = _documentTitle.length > 0 ? _documentTitle : @"文档";
}

- (void)setUndoEnabled:(BOOL)undoEnabled
{
    _undoEnabled = undoEnabled;
    self.undoButton.enabled = undoEnabled;
    self.undoButton.alpha = undoEnabled ? 1.0 : 0.35;
}

- (void)setRedoEnabled:(BOOL)redoEnabled
{
    _redoEnabled = redoEnabled;
    self.redoButton.enabled = redoEnabled;
    self.redoButton.alpha = redoEnabled ? 1.0 : 0.35;
}

- (void)recordUndoableNativeEdit:(NSString *)reason
{
    self.undoEnabled = YES;
    self.redoEnabled = NO;
    NSLog(@"IOSTopToolbar undo_redo_record_edit reason=%@", reason ?: @"native_edit");
}

- (void)setCommentCount:(NSInteger)commentCount
{
    _commentCount = commentCount;
    self.commentBadge.hidden = commentCount <= 0;
}

- (void)setOpenDocumentCount:(NSInteger)openDocumentCount
{
    _openDocumentCount = openDocumentCount;
    NSInteger displayCount = MAX(openDocumentCount, 1);
    NSString *text = [NSString stringWithFormat:@"%ld", (long)displayCount];
    self.openDocsCountLabel.text = text;
    self.editOpenDocsCountLabel.text = text;
}

- (void)setDocumentType:(NSString *)documentType
{
    _documentType = [documentType copy];
    UIColor *color = [UIColor colorWithRed:26.0 / 255.0 green:115.0 / 255.0 blue:232.0 / 255.0 alpha:1.0];
    if ([_documentType isEqualToString:@"spreadsheet"]) {
        color = [UIColor colorWithRed:59.0 / 255.0 green:128.0 / 255.0 blue:64.0 / 255.0 alpha:1.0];
    } else if ([_documentType isEqualToString:@"presentation"]) {
        color = [UIColor colorWithRed:236.0 / 255.0 green:93.0 / 255.0 blue:31.0 / 255.0 alpha:1.0];
    }
    self.doneButton.backgroundColor = color;
    BOOL hideSearch = [_documentType isEqualToString:@"presentation"];
    self.searchButton.hidden = hideSearch;
    self.searchButton.userInteractionEnabled = !hideSearch;
    for (NSLayoutConstraint *constraint in self.searchButton.constraints) {
        if (constraint.firstAttribute == NSLayoutAttributeWidth) {
            constraint.constant = hideSearch ? 0.0 : kTopToolbarIconButtonSize;
        }
    }
}

- (void)relayout
{
    [self.view setNeedsLayout];
    [self.view layoutIfNeeded];
}

- (void)backPressed:(id)sender
{
    [self.delegate topToolbarDidPressBack];
}

- (void)donePressed:(id)sender
{
    [self.delegate topToolbarDidPressDone];
}

- (void)undoPressed:(id)sender
{
    [self.delegate topToolbarDidPressUndo];
}

- (void)redoPressed:(id)sender
{
    [self.delegate topToolbarDidPressRedo];
}

- (void)searchPressed:(id)sender
{
    [self.delegate topToolbarDidPressSearch];
}

- (void)sharePressed:(id)sender
{
    [self.delegate topToolbarDidPressShare];
}

- (void)documentsPressed:(id)sender
{
    [self.delegate topToolbarDidPressDocuments];
}

- (void)closePressed:(id)sender
{
    [self.delegate topToolbarDidPressClose];
}

- (void)commentPressed:(id)sender
{
    [self.delegate topToolbarDidPressComment];
}

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
