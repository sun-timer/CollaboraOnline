// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "TopToolbarController.h"

@interface IOSTopToolbarController ()
@property (nonatomic, strong, readwrite) UIView *view;
@property (nonatomic, weak) id<IOSTopToolbarControllerDelegate> delegate;
@property (nonatomic, strong) UIView *previewRow;
@property (nonatomic, strong) UIView *editRow;
@property (nonatomic, strong) UILabel *titleLabel;
@property (nonatomic, strong) UIButton *doneButton;
@property (nonatomic, strong) UIButton *undoButton;
@property (nonatomic, strong) UIButton *redoButton;
@property (nonatomic, copy) NSString *documentType;
@end

static UIButton *toolbarButton(NSString *symbolName, NSString *accessibilityLabel)
{
    UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
    UIImage *image = [UIImage systemImageNamed:symbolName];
    if (image != nil) {
        [button setImage:image forState:UIControlStateNormal];
    }
    button.accessibilityLabel = accessibilityLabel;
    button.tintColor = [UIColor colorWithRed:0.15 green:0.15 blue:0.16 alpha:1.0];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    [button.widthAnchor constraintEqualToConstant:48.0].active = YES;
    [button.heightAnchor constraintEqualToConstant:48.0].active = YES;
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
    _view.backgroundColor = UIColor.whiteColor;
    _view.layer.shadowColor = UIColor.blackColor.CGColor;
    _view.layer.shadowOpacity = 0.08;
    _view.layer.shadowRadius = 3.0;
    _view.layer.shadowOffset = CGSizeMake(0.0, 1.0);

    _previewRow = [[UIView alloc] init];
    _previewRow.translatesAutoresizingMaskIntoConstraints = NO;
    [_view addSubview:_previewRow];

    UIButton *backButton = toolbarButton(@"chevron.left", @"返回");
    [backButton addTarget:self action:@selector(backPressed:) forControlEvents:UIControlEventTouchUpInside];
    [_previewRow addSubview:backButton];

    _titleLabel = [[UILabel alloc] init];
    _titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    _titleLabel.text = _documentTitle;
    _titleLabel.textColor = [UIColor colorWithRed:0.06 green:0.06 blue:0.06 alpha:1.0];
    _titleLabel.font = [UIFont systemFontOfSize:18.0];
    _titleLabel.lineBreakMode = NSLineBreakByTruncatingTail;
    [_previewRow addSubview:_titleLabel];

    UIButton *searchButton = toolbarButton(@"magnifyingglass", @"查找替换");
    [searchButton addTarget:self action:@selector(searchPressed:) forControlEvents:UIControlEventTouchUpInside];
    [_previewRow addSubview:searchButton];

    UIButton *shareButton = toolbarButton(@"square.and.arrow.up", @"分享");
    [shareButton addTarget:self action:@selector(sharePressed:) forControlEvents:UIControlEventTouchUpInside];
    [_previewRow addSubview:shareButton];

    UIButton *previewDocumentsButton = toolbarButton(@"rectangle.stack", @"已打开文档");
    [previewDocumentsButton addTarget:self action:@selector(documentsPressed:)
                     forControlEvents:UIControlEventTouchUpInside];
    [_previewRow addSubview:previewDocumentsButton];

    _editRow = [[UIView alloc] init];
    _editRow.translatesAutoresizingMaskIntoConstraints = NO;
    [_view addSubview:_editRow];

    _doneButton = [UIButton buttonWithType:UIButtonTypeSystem];
    _doneButton.translatesAutoresizingMaskIntoConstraints = NO;
    [_doneButton setTitle:@"完成" forState:UIControlStateNormal];
    [_doneButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    _doneButton.titleLabel.font = [UIFont boldSystemFontOfSize:16.0];
    _doneButton.layer.cornerRadius = 6.0;
    [_doneButton addTarget:self action:@selector(donePressed:) forControlEvents:UIControlEventTouchUpInside];
    [_editRow addSubview:_doneButton];

    _undoButton = toolbarButton(@"arrow.uturn.left", @"撤销");
    [_undoButton addTarget:self action:@selector(undoPressed:) forControlEvents:UIControlEventTouchUpInside];
    [_editRow addSubview:_undoButton];

    _redoButton = toolbarButton(@"arrow.uturn.right", @"重做");
    [_redoButton addTarget:self action:@selector(redoPressed:) forControlEvents:UIControlEventTouchUpInside];
    [_editRow addSubview:_redoButton];

    UIButton *editDocumentsButton = toolbarButton(@"rectangle.stack", @"已打开文档");
    [editDocumentsButton addTarget:self action:@selector(documentsPressed:)
                  forControlEvents:UIControlEventTouchUpInside];
    [_editRow addSubview:editDocumentsButton];

    UIButton *closeButton = toolbarButton(@"xmark", @"关闭编辑");
    [closeButton addTarget:self action:@selector(closePressed:) forControlEvents:UIControlEventTouchUpInside];
    [_editRow addSubview:closeButton];

    [self installConstraintsForPreviewRow:backButton
                                    search:searchButton
                                     share:shareButton
                                 documents:previewDocumentsButton];
    [self installConstraintsForEditRow:editDocumentsButton close:closeButton];
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
        [backButton.leadingAnchor constraintEqualToAnchor:_previewRow.leadingAnchor constant:4.0],
        [backButton.centerYAnchor constraintEqualToAnchor:_previewRow.centerYAnchor],
        [_titleLabel.leadingAnchor constraintEqualToAnchor:backButton.trailingAnchor],
        [_titleLabel.centerYAnchor constraintEqualToAnchor:_previewRow.centerYAnchor],
        [_titleLabel.trailingAnchor constraintEqualToAnchor:searchButton.leadingAnchor],
        [searchButton.centerYAnchor constraintEqualToAnchor:_previewRow.centerYAnchor],
        [shareButton.leadingAnchor constraintEqualToAnchor:searchButton.trailingAnchor],
        [shareButton.centerYAnchor constraintEqualToAnchor:_previewRow.centerYAnchor],
        [documentsButton.leadingAnchor constraintEqualToAnchor:shareButton.trailingAnchor],
        [documentsButton.trailingAnchor constraintEqualToAnchor:_previewRow.trailingAnchor constant:-4.0],
        [documentsButton.centerYAnchor constraintEqualToAnchor:_previewRow.centerYAnchor],
    ]];
}

- (void)installConstraintsForEditRow:(UIButton *)documentsButton close:(UIButton *)closeButton
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
        [_doneButton.leadingAnchor constraintEqualToAnchor:_editRow.leadingAnchor constant:12.0],
        [_doneButton.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [_doneButton.widthAnchor constraintGreaterThanOrEqualToConstant:72.0],
        [_doneButton.heightAnchor constraintEqualToConstant:36.0],
        [leftSpacer.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [leftSpacer.heightAnchor constraintEqualToConstant:1.0],
        [rightSpacer.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [rightSpacer.heightAnchor constraintEqualToConstant:1.0],
        [_undoButton.leadingAnchor constraintEqualToAnchor:leftSpacer.trailingAnchor],
        [_undoButton.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [_redoButton.leadingAnchor constraintEqualToAnchor:_undoButton.trailingAnchor],
        [_redoButton.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [rightSpacer.leadingAnchor constraintEqualToAnchor:_redoButton.trailingAnchor],
        [documentsButton.leadingAnchor constraintEqualToAnchor:rightSpacer.trailingAnchor],
        [documentsButton.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [closeButton.leadingAnchor constraintEqualToAnchor:documentsButton.trailingAnchor],
        [closeButton.trailingAnchor constraintEqualToAnchor:_editRow.trailingAnchor constant:-4.0],
        [closeButton.centerYAnchor constraintEqualToAnchor:_editRow.centerYAnchor],
        [leftSpacer.leadingAnchor constraintEqualToAnchor:_doneButton.trailingAnchor],
        [rightSpacer.widthAnchor constraintEqualToAnchor:leftSpacer.widthAnchor],
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

- (void)setDocumentType:(NSString *)documentType
{
    _documentType = [documentType copy];
    UIColor *color = [UIColor colorWithRed:0.16 green:0.48 blue:0.78 alpha:1.0];
    if ([_documentType isEqualToString:@"spreadsheet"]) {
        color = [UIColor colorWithRed:0.23 green:0.50 blue:0.25 alpha:1.0];
    } else if ([_documentType isEqualToString:@"presentation"]) {
        color = [UIColor colorWithRed:0.80 green:0.35 blue:0.12 alpha:1.0];
    }
    self.doneButton.backgroundColor = color;
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

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
