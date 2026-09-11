// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AIQuickCreateViewController.h"

#import "AI/AIService.h"
#import "DocumentPresentation.h"
#import "Settings/AppChromeHelper.h"

static NSString *const kQuickCreateSessionId = @"home-ai-quick-create";

@interface AIQuickCreateViewController ()
@property (strong, nonatomic) AIService *aiService;
@property (strong, nonatomic) UITextField *topicField;
@property (strong, nonatomic) UITextField *pageCountField;
@property (strong, nonatomic) UITextField *audienceField;
@property (strong, nonatomic) UISegmentedControl *docTypeControl;
@property (strong, nonatomic) UIButton *generateButton;
@property (strong, nonatomic) UILabel *statusLabel;
@property (copy, nonatomic) NSString *pendingDocType;
@end

@implementation AIQuickCreateViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    [AppChromeHelper applySecondaryPageBackground:self.view];
    self.aiService = [[AIService alloc] init];

    UIButton *back = [AppChromeHelper secondaryBackButtonWithTarget:self action:@selector(close)];
    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"AI 快速创建";
    title.font = [UIFont boldSystemFontOfSize:20];

    self.docTypeControl = [[UISegmentedControl alloc] initWithItems:@[ @"文档", @"表格", @"演示" ]];
    self.docTypeControl.translatesAutoresizingMaskIntoConstraints = NO;
    self.docTypeControl.selectedSegmentIndex = 2;

    self.topicField = [self fieldWithPlaceholder:@"文档主题 / 大纲"];
    self.pageCountField = [self fieldWithPlaceholder:@"页数（选填，如 8）"];
    self.audienceField = [self fieldWithPlaceholder:@"目标读者（选填）"];

    self.statusLabel = [[UILabel alloc] init];
    self.statusLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.statusLabel.font = [UIFont systemFontOfSize:14];
    self.statusLabel.textColor = [UIColor colorWithWhite:0.45 alpha:1];
    self.statusLabel.numberOfLines = 0;

    self.generateButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.generateButton.translatesAutoresizingMaskIntoConstraints = NO;
    self.generateButton.backgroundColor = [AppChromeHelper primaryAccentColor];
    self.generateButton.layer.cornerRadius = 28;
    [self.generateButton setTitle:@"生成并打开" forState:UIControlStateNormal];
    [self.generateButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    [self.generateButton addTarget:self action:@selector(generate) forControlEvents:UIControlEventTouchUpInside];

    for (UIView *v in @[ back, title, self.docTypeControl, self.topicField, self.pageCountField,
                         self.audienceField, self.statusLabel, self.generateButton ]) {
        [self.view addSubview:v];
    }

    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:8],
        [back.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:12],
        [title.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [title.leadingAnchor constraintEqualToAnchor:back.trailingAnchor constant:8],
        [self.docTypeControl.topAnchor constraintEqualToAnchor:back.bottomAnchor constant:24],
        [self.docTypeControl.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:24],
        [self.docTypeControl.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor constant:-24],
        [self.topicField.topAnchor constraintEqualToAnchor:self.docTypeControl.bottomAnchor constant:16],
        [self.topicField.leadingAnchor constraintEqualToAnchor:self.docTypeControl.leadingAnchor],
        [self.topicField.trailingAnchor constraintEqualToAnchor:self.docTypeControl.trailingAnchor],
        [self.pageCountField.topAnchor constraintEqualToAnchor:self.topicField.bottomAnchor constant:12],
        [self.pageCountField.leadingAnchor constraintEqualToAnchor:self.topicField.leadingAnchor],
        [self.pageCountField.trailingAnchor constraintEqualToAnchor:self.topicField.trailingAnchor],
        [self.audienceField.topAnchor constraintEqualToAnchor:self.pageCountField.bottomAnchor constant:12],
        [self.audienceField.leadingAnchor constraintEqualToAnchor:self.topicField.leadingAnchor],
        [self.audienceField.trailingAnchor constraintEqualToAnchor:self.topicField.trailingAnchor],
        [self.statusLabel.topAnchor constraintEqualToAnchor:self.audienceField.bottomAnchor constant:16],
        [self.statusLabel.leadingAnchor constraintEqualToAnchor:self.topicField.leadingAnchor],
        [self.statusLabel.trailingAnchor constraintEqualToAnchor:self.topicField.trailingAnchor],
        [self.generateButton.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:48],
        [self.generateButton.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor constant:-48],
        [self.generateButton.bottomAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.bottomAnchor constant:-24],
        [self.generateButton.heightAnchor constraintEqualToConstant:56],
    ]];
}

- (UITextField *)fieldWithPlaceholder:(NSString *)placeholder {
    UITextField *field = [[UITextField alloc] init];
    field.translatesAutoresizingMaskIntoConstraints = NO;
    field.placeholder = placeholder;
    field.borderStyle = UITextBorderStyleRoundedRect;
    field.font = [UIFont systemFontOfSize:16];
    [field.heightAnchor constraintEqualToConstant:44].active = YES;
    return field;
}

- (NSString *)selectedDocType {
    switch (self.docTypeControl.selectedSegmentIndex) {
        case 1:
            return @"calc";
        case 2:
            return @"impress";
        default:
            return @"writer";
    }
}

- (void)extensionForDocType:(NSString *)docType basename:(NSString * __autoreleasing *)basename {
    if ([docType isEqualToString:@"calc"]) {
        if (basename) {
            *basename = @"表格";
        }
        return;
    }
    if ([docType isEqualToString:@"impress"]) {
        if (basename) {
            *basename = @"演示";
        }
        return;
    }
    if (basename) {
        *basename = @"文档";
    }
}

- (NSString *)outputExtensionForDocType:(NSString *)docType {
    if ([docType isEqualToString:@"calc"]) {
        return @"ods";
    }
    if ([docType isEqualToString:@"impress"]) {
        return @"odp";
    }
    return @"odt";
}

- (void)generate {
    NSString *topic = [self.topicField.text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (topic.length < 4) {
        self.statusLabel.text = @"请输入至少 4 个字的主题。";
        return;
    }
    self.pendingDocType = [self selectedDocType];
    self.generateButton.enabled = NO;
    self.statusLabel.text = @"正在生成…";
    NSString *requestId = [[NSUUID UUID] UUIDString];
    NSDictionary *payload = @{
        @"taskType": @"create_document",
        @"text": topic,
        @"context": @{
            @"docType": self.pendingDocType,
            @"pageCount": self.pageCountField.text ?: @"",
            @"audience": self.audienceField.text ?: @"",
        },
    };
    __weak __typeof(self) weakSelf = self;
    [self.aiService startRequest:payload
                       requestId:requestId
              documentSessionId:kQuickCreateSessionId
                          emit:^(NSString *type, NSString *reqId, NSString *sessionId, NSDictionary *p) {
        dispatch_async(dispatch_get_main_queue(), ^{
            [weakSelf handleAIEvent:type payload:p];
        });
    }];
}

- (void)handleAIEvent:(NSString *)type payload:(NSDictionary *)payload {
    if ([type isEqualToString:@"ai.delta"]) {
        self.statusLabel.text = @"正在生成…";
        return;
    }
    if ([type isEqualToString:@"ai.error"]) {
        self.generateButton.enabled = YES;
        self.statusLabel.text = payload[@"message"] ?: @"生成失败";
        return;
    }
    if (![type isEqualToString:@"ai.done"]) {
        return;
    }
    NSString *fullText = [payload[@"fullText"] isKindOfClass:[NSString class]] ? payload[@"fullText"] : @"";
    if (fullText.length > 0) {
        [UIPasteboard generalPasteboard].string = fullText;
    }
    NSString *basename = @"文档";
    [self extensionForDocType:self.pendingDocType basename:&basename];
    NSString *ext = [self outputExtensionForDocType:self.pendingDocType];
    NSError *error = nil;
    NSURL *url = [DocumentPresentation createBlankDocumentWithExtension:ext basename:basename error:&error];
    self.generateButton.enabled = YES;
    if (url == nil) {
        self.statusLabel.text = error.localizedDescription ?: @"无法创建文档";
        return;
    }
    self.statusLabel.text = @"已创建文档，AI 正文已复制到剪贴板，可在编辑器中粘贴。";
    __weak __typeof(self) weakSelf = self;
    [self dismissViewControllerAnimated:YES completion:^{
        UIViewController *presenter = weakSelf.presentingViewController;
        while (presenter.presentedViewController != nil) {
            presenter = presenter.presentedViewController;
        }
        [DocumentPresentation presentDocumentAtURL:url from:presenter];
    }];
}

- (void)close {
    [self dismissViewControllerAnimated:YES completion:nil];
}

@end
