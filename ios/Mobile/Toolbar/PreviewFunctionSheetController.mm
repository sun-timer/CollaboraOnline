// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "PreviewFunctionSheetController.h"
#import "AI/WriterAIComponents.h"

static UIColor *PreviewFunctionSheetColorTitle(void)
{
    return [UIColor colorWithRed:0.082 green:0.090 blue:0.102 alpha:1.0]; // #15171a
}

static UIColor *PreviewFunctionSheetColorRowText(void)
{
    return [UIColor colorWithRed:0.2 green:0.2 blue:0.2 alpha:1.0]; // #333333
}

@interface PreviewFunctionSheetController ()
@property (nonatomic, assign) BOOL showWordCount;
@property (nonatomic, strong) UIButton *fileTabButton;
@property (nonatomic, strong) UIButton *reviewTabButton;
@property (nonatomic, strong) UIStackView *fileStack;
@property (nonatomic, strong) UIStackView *reviewStack;
@end

@implementation PreviewFunctionSheetController

+ (instancetype)presentFrom:(UIViewController *)host
                   delegate:(id<PreviewFunctionSheetControllerDelegate>)delegate
{
    return [self presentFrom:host delegate:delegate showWordCount:YES];
}

+ (instancetype)presentFrom:(UIViewController *)host
                   delegate:(id<PreviewFunctionSheetControllerDelegate>)delegate
              showWordCount:(BOOL)showWordCount
{
    PreviewFunctionSheetController *sheet = [[PreviewFunctionSheetController alloc] init];
    sheet.actionDelegate = delegate;
    sheet.showWordCount = showWordCount;
    sheet.modalPresentationStyle = UIModalPresentationPageSheet;
    if (@available(iOS 15.0, *)) {
        UISheetPresentationController *presentation = sheet.sheetPresentationController;
        presentation.detents = @[
            [UISheetPresentationControllerDetent mediumDetent],
            [UISheetPresentationControllerDetent largeDetent],
        ];
        presentation.prefersGrabberVisible = YES;
        presentation.preferredCornerRadius = 24.0;
    }
    [host presentViewController:sheet animated:YES completion:nil];
    return sheet;
}

- (void)viewDidLoad
{
    [super viewDidLoad];
    self.view.backgroundColor = UIColor.whiteColor;
    self.view.accessibilityIdentifier = @"previewFunctionSheet";
    self.view.layer.cornerRadius = 24.0;
    self.view.layer.maskedCorners = kCALayerMinXMinYCorner | kCALayerMaxXMinYCorner;
    self.view.layer.masksToBounds = YES;
    self.view.layer.shadowColor = UIColor.blackColor.CGColor;
    self.view.layer.shadowOpacity = 0.28;
    self.view.layer.shadowRadius = 52.0;
    self.view.layer.shadowOffset = CGSizeMake(0.0, -1.0);

    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"功能";
    title.font = [UIFont systemFontOfSize:18 weight:UIFontWeightSemibold];
    title.textColor = PreviewFunctionSheetColorTitle();
    title.textAlignment = NSTextAlignmentCenter;

    WriterAICloseButton *close = [WriterAICloseButton closeButtonWithTarget:self action:@selector(closeTapped)];

    UIView *tabTrack = [[UIView alloc] init];
    tabTrack.translatesAutoresizingMaskIntoConstraints = NO;
    tabTrack.backgroundColor = [UIColor colorWithRed:0.949 green:0.953 blue:0.961 alpha:1.0]; // #f2f3f5
    tabTrack.layer.cornerRadius = 12.0;

    self.fileTabButton = [self tabButtonWithTitle:@"文件操作" tag:0];
    self.reviewTabButton = [self tabButtonWithTitle:@"审阅" tag:1];
    [self setTabSelected:YES forButton:self.fileTabButton];
    [self setTabSelected:NO forButton:self.reviewTabButton];

    UIStackView *tabStack = [[UIStackView alloc] initWithArrangedSubviews:@[
        self.fileTabButton, self.reviewTabButton,
    ]];
    tabStack.translatesAutoresizingMaskIntoConstraints = NO;
    tabStack.axis = UILayoutConstraintAxisHorizontal;
    tabStack.spacing = 4.0;
    tabStack.distribution = UIStackViewDistributionFillEqually;
    [tabTrack addSubview:tabStack];

    self.fileStack = [self buildActionStack:@[
        @[ @"function-save", @"保存", @"save" ],
        @[ @"function-export-pdf", @"导出为", @"export" ],
        @[ @"function-print", @"打印", @"print" ],
    ]];
    self.reviewStack = [self buildActionStack:[self reviewActionDefs]];
    self.reviewStack.hidden = YES;

    [self.view addSubview:title];
    [self.view addSubview:close];
    [self.view addSubview:tabTrack];
    [self.view addSubview:self.fileStack];
    [self.view addSubview:self.reviewStack];

    UILayoutGuide *safe = self.view.safeAreaLayoutGuide;
    [NSLayoutConstraint activateConstraints:@[
        [title.topAnchor constraintEqualToAnchor:safe.topAnchor constant:15],
        [title.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [close.centerYAnchor constraintEqualToAnchor:title.centerYAnchor],
        [close.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-12],
        [tabTrack.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:11],
        [tabTrack.leadingAnchor constraintEqualToAnchor:safe.leadingAnchor constant:16],
        [tabTrack.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-16],
        [tabTrack.heightAnchor constraintEqualToConstant:38],
        [tabStack.topAnchor constraintEqualToAnchor:tabTrack.topAnchor constant:2],
        [tabStack.leadingAnchor constraintEqualToAnchor:tabTrack.leadingAnchor constant:4],
        [tabStack.trailingAnchor constraintEqualToAnchor:tabTrack.trailingAnchor constant:-4],
        [tabStack.bottomAnchor constraintEqualToAnchor:tabTrack.bottomAnchor constant:-2],
        [self.fileStack.topAnchor constraintEqualToAnchor:tabTrack.bottomAnchor constant:11],
        [self.fileStack.leadingAnchor constraintEqualToAnchor:safe.leadingAnchor constant:16],
        [self.fileStack.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-16],
        [self.fileStack.bottomAnchor constraintLessThanOrEqualToAnchor:safe.bottomAnchor constant:-34],
        [self.reviewStack.topAnchor constraintEqualToAnchor:self.fileStack.topAnchor],
        [self.reviewStack.leadingAnchor constraintEqualToAnchor:self.fileStack.leadingAnchor],
        [self.reviewStack.trailingAnchor constraintEqualToAnchor:self.fileStack.trailingAnchor],
    ]];
}

- (UIButton *)tabButtonWithTitle:(NSString *)title tag:(NSInteger)tag
{
    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    button.tag = tag;
    button.layer.cornerRadius = 8.0;
    button.titleLabel.font = [UIFont systemFontOfSize:16 weight:UIFontWeightRegular];
    [button setTitle:title forState:UIControlStateNormal];
    [button addTarget:self action:@selector(tabTapped:) forControlEvents:UIControlEventTouchUpInside];
    [button.heightAnchor constraintEqualToConstant:30].active = YES;
    return button;
}

- (void)setTabSelected:(BOOL)selected forButton:(UIButton *)button
{
    button.backgroundColor = selected ? UIColor.whiteColor : UIColor.clearColor;
    [button setTitleColor:(selected ? UIColor.blackColor : [UIColor colorWithWhite:0.42 alpha:1.0])
                 forState:UIControlStateNormal];
}

- (void)tabTapped:(UIButton *)sender
{
    BOOL file = (sender.tag == 0);
    [self setTabSelected:file forButton:self.fileTabButton];
    [self setTabSelected:!file forButton:self.reviewTabButton];
    self.fileStack.hidden = !file;
    self.reviewStack.hidden = file;
}

- (NSArray<NSArray<NSString *> *> *)reviewActionDefs
{
    if (self.showWordCount) {
        return @[
            @[ @"list", @"字数统计", @"wordcount" ],
            @[ @"search", @"查找替换", @"find" ],
        ];
    }
    return @[ @[ @"search", @"查找替换", @"find" ] ];
}

- (UIStackView *)buildActionStack:(NSArray<NSArray<NSString *> *> *)defs
{
    UIStackView *stack = [[UIStackView alloc] init];
    stack.translatesAutoresizingMaskIntoConstraints = NO;
    stack.axis = UILayoutConstraintAxisVertical;
    stack.spacing = 0;
    for (NSUInteger index = 0; index < defs.count; index++) {
        NSArray<NSString *> *def = defs[index];
        UIButton *row = [UIButton buttonWithType:UIButtonTypeCustom];
        row.translatesAutoresizingMaskIntoConstraints = NO;
        row.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeading;
        row.contentEdgeInsets = UIEdgeInsetsMake(16, 16, 16, 16);
        row.accessibilityIdentifier = [NSString stringWithFormat:@"previewFunction_%@", def[2]];

        UIImageView *iconView = [[UIImageView alloc] initWithImage:[UIImage writerIconNamed:def[0]]];
        iconView.translatesAutoresizingMaskIntoConstraints = NO;
        iconView.tintColor = PreviewFunctionSheetColorRowText();
        iconView.contentMode = UIViewContentModeScaleAspectFit;

        UILabel *label = [[UILabel alloc] init];
        label.translatesAutoresizingMaskIntoConstraints = NO;
        label.text = def[1];
        label.font = [UIFont systemFontOfSize:18];
        label.textColor = PreviewFunctionSheetColorRowText();

        UIView *divider = nil;
        if (index + 1 < defs.count) {
            divider = [[UIView alloc] init];
            divider.translatesAutoresizingMaskIntoConstraints = NO;
            divider.backgroundColor = [UIColor colorWithWhite:0 alpha:0.08];
        }

        UIStackView *rowStack = [[UIStackView alloc] init];
        rowStack.translatesAutoresizingMaskIntoConstraints = NO;
        rowStack.axis = UILayoutConstraintAxisVertical;
        rowStack.spacing = 0;
        rowStack.userInteractionEnabled = NO;

        UIStackView *content = [[UIStackView alloc] initWithArrangedSubviews:@[ iconView, label ]];
        content.translatesAutoresizingMaskIntoConstraints = NO;
        content.axis = UILayoutConstraintAxisHorizontal;
        content.spacing = 12.0;
        content.alignment = UIStackViewAlignmentCenter;
        [rowStack addArrangedSubview:content];
        if (divider != nil) {
            [rowStack addArrangedSubview:divider];
            [divider.heightAnchor constraintEqualToConstant:1.0].active = YES;
        }
        [row addSubview:rowStack];
        [NSLayoutConstraint activateConstraints:@[
            [iconView.widthAnchor constraintEqualToConstant:32],
            [iconView.heightAnchor constraintEqualToConstant:32],
            [rowStack.topAnchor constraintEqualToAnchor:row.topAnchor],
            [rowStack.leadingAnchor constraintEqualToAnchor:row.leadingAnchor],
            [rowStack.trailingAnchor constraintEqualToAnchor:row.trailingAnchor],
            [rowStack.bottomAnchor constraintEqualToAnchor:row.bottomAnchor],
            [row.heightAnchor constraintEqualToConstant:64],
        ]];

        if ([def[2] isEqualToString:@"save"]) {
            [row addTarget:self action:@selector(saveTapped) forControlEvents:UIControlEventTouchUpInside];
        } else if ([def[2] isEqualToString:@"export"]) {
            [row addTarget:self action:@selector(exportTapped) forControlEvents:UIControlEventTouchUpInside];
        } else if ([def[2] isEqualToString:@"print"]) {
            [row addTarget:self action:@selector(printTapped) forControlEvents:UIControlEventTouchUpInside];
        } else if ([def[2] isEqualToString:@"find"]) {
            [row addTarget:self action:@selector(findTapped) forControlEvents:UIControlEventTouchUpInside];
        } else if ([def[2] isEqualToString:@"wordcount"]) {
            [row addTarget:self action:@selector(wordCountTapped) forControlEvents:UIControlEventTouchUpInside];
        }
        [stack addArrangedSubview:row];
    }
    return stack;
}

- (void)closeTapped
{
    [self dismissViewControllerAnimated:YES completion:nil];
}

- (void)saveTapped
{
    [self dismissViewControllerAnimated:YES completion:^{
        [self.actionDelegate previewFunctionSheetDidRequestSave];
    }];
}

- (void)exportTapped
{
    UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"导出为"
                                                                   message:nil
                                                            preferredStyle:UIAlertControllerStyleActionSheet];
    NSArray<NSString *> *formats = @[ @"pdf", @"odt", @"docx" ];
    NSArray<NSString *> *labels = @[
        @"PDF (.pdf)",
        @"ODF 文本文档 (.odt)",
        @"Word 文档 (.docx)",
    ];
    for (NSUInteger i = 0; i < formats.count; i++) {
        NSString *format = formats[i];
        NSString *label = labels[i];
        [alert addAction:[UIAlertAction actionWithTitle:label
                                                  style:UIAlertActionStyleDefault
                                                handler:^(UIAlertAction *action) {
            [self dismissViewControllerAnimated:YES completion:^{
                [self.actionDelegate previewFunctionSheetDidRequestExportAs:format];
            }];
        }]];
    }
    [alert addAction:[UIAlertAction actionWithTitle:@"取消"
                                              style:UIAlertActionStyleCancel
                                            handler:nil]];
    [self presentViewController:alert animated:YES completion:nil];
}

- (void)printTapped
{
    [self dismissViewControllerAnimated:YES completion:^{
        [self.actionDelegate previewFunctionSheetDidRequestPrint];
    }];
}

- (void)findTapped
{
    [self dismissViewControllerAnimated:YES completion:^{
        [self.actionDelegate previewFunctionSheetDidRequestFindReplace];
    }];
}

- (void)wordCountTapped
{
    [self dismissViewControllerAnimated:YES completion:^{
        [self.actionDelegate previewFunctionSheetDidRequestWordCount];
    }];
}

@end
