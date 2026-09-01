// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "PreviewFunctionSheetController.h"

@interface PreviewFunctionSheetController ()
@property (nonatomic, strong) UISegmentedControl *tabs;
@property (nonatomic, strong) UIStackView *fileStack;
@property (nonatomic, strong) UIStackView *reviewStack;
@end

@implementation PreviewFunctionSheetController

+ (instancetype)presentFrom:(UIViewController *)host
                   delegate:(id<PreviewFunctionSheetControllerDelegate>)delegate
{
    PreviewFunctionSheetController *sheet = [[PreviewFunctionSheetController alloc] init];
    sheet.actionDelegate = delegate;
    sheet.modalPresentationStyle = UIModalPresentationPageSheet;
    if (@available(iOS 15.0, *)) {
        UISheetPresentationController *presentation = sheet.sheetPresentationController;
        presentation.detents = @[
            [UISheetPresentationControllerDetent mediumDetent],
            [UISheetPresentationControllerDetent largeDetent],
        ];
        presentation.prefersGrabberVisible = YES;
    }
    [host presentViewController:sheet animated:YES completion:nil];
    return sheet;
}

- (void)viewDidLoad
{
    [super viewDidLoad];
    self.view.backgroundColor = UIColor.whiteColor;
    self.view.accessibilityIdentifier = @"previewFunctionSheet";

    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"功能";
    title.font = [UIFont systemFontOfSize:17 weight:UIFontWeightSemibold];
    title.textAlignment = NSTextAlignmentCenter;

    UIButton *close = [UIButton buttonWithType:UIButtonTypeSystem];
    close.translatesAutoresizingMaskIntoConstraints = NO;
    [close setImage:[UIImage systemImageNamed:@"xmark"] forState:UIControlStateNormal];
    close.tintColor = [UIColor colorWithWhite:0.35 alpha:1];
    close.accessibilityLabel = @"关闭";
    [close addTarget:self action:@selector(closeTapped) forControlEvents:UIControlEventTouchUpInside];

    self.tabs = [[UISegmentedControl alloc] initWithItems:@[ @"文件操作", @"审阅" ]];
    self.tabs.translatesAutoresizingMaskIntoConstraints = NO;
    self.tabs.selectedSegmentIndex = 0;
    [self.tabs addTarget:self action:@selector(tabChanged) forControlEvents:UIControlEventValueChanged];

    self.fileStack = [self buildActionStack:@[
        @[ @"square.and.arrow.down", @"保存", @"save" ],
        @[ @"doc.badge.arrow.up", @"导出PDF", @"pdf" ],
        @[ @"printer", @"打印", @"print" ],
    ]];
    self.reviewStack = [self buildActionStack:@[
        @[ @"doc.text.magnifyingglass", @"查找替换", @"find" ],
    ]];
    self.reviewStack.hidden = YES;

    [self.view addSubview:title];
    [self.view addSubview:close];
    [self.view addSubview:self.tabs];
    [self.view addSubview:self.fileStack];
    [self.view addSubview:self.reviewStack];

    UILayoutGuide *safe = self.view.safeAreaLayoutGuide;
    [NSLayoutConstraint activateConstraints:@[
        [title.topAnchor constraintEqualToAnchor:safe.topAnchor constant:16],
        [title.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [close.centerYAnchor constraintEqualToAnchor:title.centerYAnchor],
        [close.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-16],
        [close.widthAnchor constraintEqualToConstant:36],
        [close.heightAnchor constraintEqualToConstant:36],
        [self.tabs.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:20],
        [self.tabs.leadingAnchor constraintEqualToAnchor:safe.leadingAnchor constant:20],
        [self.tabs.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-20],
        [self.fileStack.topAnchor constraintEqualToAnchor:self.tabs.bottomAnchor constant:20],
        [self.fileStack.leadingAnchor constraintEqualToAnchor:safe.leadingAnchor constant:20],
        [self.fileStack.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-20],
        [self.reviewStack.topAnchor constraintEqualToAnchor:self.fileStack.topAnchor],
        [self.reviewStack.leadingAnchor constraintEqualToAnchor:self.fileStack.leadingAnchor],
        [self.reviewStack.trailingAnchor constraintEqualToAnchor:self.fileStack.trailingAnchor],
    ]];
}

- (UIStackView *)buildActionStack:(NSArray<NSArray<NSString *> *> *)defs
{
    UIStackView *stack = [[UIStackView alloc] init];
    stack.translatesAutoresizingMaskIntoConstraints = NO;
    stack.axis = UILayoutConstraintAxisVertical;
    stack.spacing = 4;
    for (NSArray<NSString *> *def in defs) {
        UIButton *row = [UIButton buttonWithType:UIButtonTypeSystem];
        row.translatesAutoresizingMaskIntoConstraints = NO;
        [row setTitle:def[1] forState:UIControlStateNormal];
        [row setTitleColor:UIColor.blackColor forState:UIControlStateNormal];
        row.titleLabel.font = [UIFont systemFontOfSize:16];
        row.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeft;
        [row setImage:[[UIImage systemImageNamed:def[0]] imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate]
             forState:UIControlStateNormal];
        row.tintColor = [UIColor colorWithWhite:0.25 alpha:1];
        row.contentEdgeInsets = UIEdgeInsetsMake(14, 8, 14, 8);
        row.imageEdgeInsets = UIEdgeInsetsMake(0, 0, 0, 12);
        row.accessibilityIdentifier = [NSString stringWithFormat:@"previewFunction_%@", def[2]];
        if ([def[2] isEqualToString:@"save"]) {
            [row addTarget:self action:@selector(saveTapped) forControlEvents:UIControlEventTouchUpInside];
        } else if ([def[2] isEqualToString:@"pdf"]) {
            [row addTarget:self action:@selector(pdfTapped) forControlEvents:UIControlEventTouchUpInside];
        } else if ([def[2] isEqualToString:@"print"]) {
            [row addTarget:self action:@selector(printTapped) forControlEvents:UIControlEventTouchUpInside];
        } else if ([def[2] isEqualToString:@"find"]) {
            [row addTarget:self action:@selector(findTapped) forControlEvents:UIControlEventTouchUpInside];
        }
        [stack addArrangedSubview:row];
        [row.heightAnchor constraintEqualToConstant:52].active = YES;
    }
    return stack;
}

- (void)tabChanged
{
    BOOL file = (self.tabs.selectedSegmentIndex == 0);
    self.fileStack.hidden = !file;
    self.reviewStack.hidden = file;
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

- (void)pdfTapped
{
    [self dismissViewControllerAnimated:YES completion:^{
        [self.actionDelegate previewFunctionSheetDidRequestExportPDF];
    }];
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

@end
