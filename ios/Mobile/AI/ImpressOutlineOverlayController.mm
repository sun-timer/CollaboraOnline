// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 *
 * Native Impress outline overlay aligned with Android LOActivity
 * setupImpressOutlineDialog: input / generating / cards / template / PPT fill.
 */

#import "ImpressOutlineOverlayController.h"

#import "AIService.h"
#import "../Typeset/PptxTemplateService.h"
#import "../Typeset/TypesetService.h"
#import "../RecentDocumentsStore.h"

#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>
#import <objc/runtime.h>

typedef NS_ENUM(NSInteger, ImpressOutlineState) {
    ImpressOutlineStateInput = 0,
    ImpressOutlineStateGenerating = 1,
    ImpressOutlineStateCompleted = 2,
    ImpressOutlineStateError = 3,
    ImpressOutlineStateGeneratingPpt = 4,
    ImpressOutlineStateTemplateSelect = 5,
};

static UIColor *ImpressOrange(void) {
    return [UIColor colorWithRed:0xEC / 255.0 green:0x5D / 255.0 blue:0x1F / 255.0 alpha:1];
}

static NSString *PptxSafeType(id value) {
    return [value isKindOfClass:[NSString class]] ? value : @"section";
}

@interface ImpressOutlineOverlayController () <UIDocumentPickerDelegate, UITextViewDelegate>
@property (weak, nonatomic) UIViewController *host;
@property (strong, nonatomic) AIService *aiService;
@property (strong, nonatomic) UIView *overlay;
@property (strong, nonatomic) UIView *panel;
@property (strong, nonatomic) UILabel *titleLabel;
@property (strong, nonatomic) UIButton *backButton;
@property (strong, nonatomic) UIButton *closeButton;
@property (strong, nonatomic) UIView *inputGroup;
@property (strong, nonatomic) UIButton *modeCard;
@property (strong, nonatomic) UILabel *modeLabel;
@property (strong, nonatomic) UITextView *quickInput;
@property (strong, nonatomic) UITextView *pasteInput;
@property (strong, nonatomic) UIView *docGroup;
@property (strong, nonatomic) UILabel *docLabel;
@property (strong, nonatomic) UIButton *pagePill;
@property (strong, nonatomic) UIButton *audiencePill;
@property (strong, nonatomic) UIButton *stylePill;
@property (strong, nonatomic) UIButton *generateBtn;
@property (strong, nonatomic) UIView *generatingGroup;
@property (strong, nonatomic) UILabel *generatingQuery;
@property (strong, nonatomic) UIActivityIndicatorView *spinner;
@property (strong, nonatomic) UIButton *stopBtn;
@property (strong, nonatomic) UIView *completedGroup;
@property (strong, nonatomic) UIScrollView *completedScroll;
@property (strong, nonatomic) UIStackView *cardStack;
@property (strong, nonatomic) UIButton *copyBtn;
@property (strong, nonatomic) UIButton *regenerateBtn;
@property (strong, nonatomic) UIButton *templateBtn;
@property (strong, nonatomic) UIView *errorGroup;
@property (strong, nonatomic) UILabel *errorText;
@property (strong, nonatomic) UIButton *errorRetryBtn;
@property (strong, nonatomic) UIView *templateGroup;
@property (strong, nonatomic) UIScrollView *templateScroll;
@property (strong, nonatomic) UIStackView *templateStack;
@property (strong, nonatomic) UIButton *templateGenerateBtn;
@property (strong, nonatomic) UIView *generatingPptGroup;
@property (strong, nonatomic) UILabel *generatingPptText;
@property (strong, nonatomic) UIProgressView *pptProgress;
@property (strong, nonatomic) UIButton *pptCancelBtn;
@property (copy, nonatomic) NSString *inputType;
@property (assign, nonatomic) NSInteger pageIndex;
@property (assign, nonatomic) NSInteger audienceIndex;
@property (assign, nonatomic) NSInteger styleIndex;
@property (assign, nonatomic) ImpressOutlineState state;
@property (copy, nonatomic) NSString *lastInput;
@property (copy, nonatomic) NSString *docContent;
@property (copy, nonatomic) NSString *docDisplayName;
@property (copy, nonatomic) NSString *activeRequestId;
@property (copy, nonatomic) NSString *generateRequestId;
@property (copy, nonatomic) NSString *selectedTemplateId;
@property (strong, nonatomic) NSMutableArray<NSMutableDictionary *> *slides;
@property (strong, nonatomic) NSMutableDictionary<NSNumber *, NSDictionary *> *generatedByIndex;
@property (assign, nonatomic) NSInteger generateTotal;
@property (assign, nonatomic) NSInteger generateCurrent;
@property (assign, nonatomic) BOOL cancelled;
@property (assign, nonatomic) BOOL lastErrorWasPpt;
@property (assign, nonatomic) BOOL panelPinned;
@end

@implementation ImpressOutlineOverlayController

- (instancetype)initWithHostViewController:(UIViewController *)host aiService:(AIService *)aiService {
    self = [super init];
    if (self) {
        _host = host;
        _aiService = aiService;
        _inputType = @"quick";
        _pageIndex = 1;
        _audienceIndex = 0;
        _styleIndex = 4;
        _slides = [NSMutableArray array];
        _generatedByIndex = [NSMutableDictionary dictionary];
        [self buildUi];
    }
    return self;
}

- (NSArray<NSString *> *)pageOptions {
    return @[@"1-10页", @"11-20页", @"21-30页", @"30+页"];
}

- (NSArray<NSString *> *)audienceOptions {
    return @[@"大众", @"投资者", @"学生", @"教师", @"领导"];
}

- (NSArray<NSString *> *)styleOptions {
    return @[@"通用", @"行业调研", @"市场推广", @"工作汇报", @"学术报告", @"教学课件"];
}

- (NSArray<NSString *> *)modeLabels {
    return @[@"快速生成", @"粘贴大纲", @"文档生成"];
}

- (NSArray<NSString *> *)modeTypes {
    return @[@"quick", @"outline", @"document"];
}

- (NSInteger)pageRangeValue {
    if (self.pageIndex == 1) return 20;
    if (self.pageIndex == 2) return 30;
    if (self.pageIndex >= 3) return 40;
    return 10;
}

- (NSString *)typeLabelForKey:(NSString *)key {
    if ([key isEqualToString:@"cover"]) return @"封面";
    if ([key isEqualToString:@"toc"]) return @"目录";
    if ([key isEqualToString:@"section_divider"]) return @"章节";
    if ([key isEqualToString:@"end"]) return @"结尾";
    return @"内容";
}

- (void)buildUi {
    self.overlay = [[UIView alloc] init];
    self.overlay.backgroundColor = [[UIColor blackColor] colorWithAlphaComponent:0.45];
    self.overlay.hidden = YES;
    self.overlay.translatesAutoresizingMaskIntoConstraints = YES;
    UITapGestureRecognizer *tap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(dismiss)];
    [self.overlay addGestureRecognizer:tap];

    self.panel = [[UIView alloc] init];
    self.panel.backgroundColor = [UIColor whiteColor];
    self.panel.layer.cornerRadius = 16;
    self.panel.clipsToBounds = YES;
    self.panel.translatesAutoresizingMaskIntoConstraints = NO;
    UITapGestureRecognizer *eat = [[UITapGestureRecognizer alloc] initWithTarget:nil action:nil];
    [self.panel addGestureRecognizer:eat];
    [self.overlay addSubview:self.panel];

    self.backButton = [UIButton buttonWithType:UIButtonTypeSystem];
    [self.backButton setTitle:@"返回" forState:UIControlStateNormal];
    [self.backButton addTarget:self action:@selector(onBack) forControlEvents:UIControlEventTouchUpInside];
    self.titleLabel = [[UILabel alloc] init];
    self.titleLabel.font = [UIFont boldSystemFontOfSize:18];
    self.titleLabel.text = @"生成PPT";
    self.titleLabel.textAlignment = NSTextAlignmentCenter;
    self.closeButton = [UIButton buttonWithType:UIButtonTypeSystem];
    [self.closeButton setTitle:@"关闭" forState:UIControlStateNormal];
    [self.closeButton addTarget:self action:@selector(dismiss) forControlEvents:UIControlEventTouchUpInside];

    UIStackView *header = [[UIStackView alloc] initWithArrangedSubviews:@[self.backButton, self.titleLabel, self.closeButton]];
    header.axis = UILayoutConstraintAxisHorizontal;
    header.alignment = UIStackViewAlignmentCenter;
    header.distribution = UIStackViewDistributionFill;
    header.translatesAutoresizingMaskIntoConstraints = NO;
    [self.backButton.widthAnchor constraintEqualToConstant:52].active = YES;
    [self.closeButton.widthAnchor constraintEqualToConstant:52].active = YES;

    self.inputGroup = [[UIView alloc] init];
    self.modeCard = [UIButton buttonWithType:UIButtonTypeSystem];
    self.modeCard.backgroundColor = [UIColor colorWithWhite:0.96 alpha:1];
    self.modeCard.layer.cornerRadius = 8;
    [self.modeCard addTarget:self action:@selector(pickMode) forControlEvents:UIControlEventTouchUpInside];
    self.modeLabel = [[UILabel alloc] init];
    self.modeLabel.text = @"快速生成";
    self.modeLabel.font = [UIFont systemFontOfSize:15];
    self.modeLabel.translatesAutoresizingMaskIntoConstraints = NO;
    [self.modeCard addSubview:self.modeLabel];
    [NSLayoutConstraint activateConstraints:@[
        [self.modeLabel.leadingAnchor constraintEqualToAnchor:self.modeCard.leadingAnchor constant:12],
        [self.modeLabel.centerYAnchor constraintEqualToAnchor:self.modeCard.centerYAnchor],
        [self.modeCard.heightAnchor constraintEqualToConstant:40],
    ]];

    self.quickInput = [self makeTextViewPlaceholder:@"请输入PPT主题和要求"];
    self.pasteInput = [self makeTextViewPlaceholder:@"粘贴已有大纲文本..."];
    self.pasteInput.hidden = YES;
    self.docGroup = [[UIView alloc] init];
    self.docGroup.hidden = YES;
    self.docGroup.backgroundColor = [UIColor colorWithWhite:0.96 alpha:1];
    self.docGroup.layer.cornerRadius = 8;
    self.docLabel = [[UILabel alloc] init];
    self.docLabel.text = @"点击选择文件\n支持 Doc、PDF、ODT 等";
    self.docLabel.numberOfLines = 0;
    self.docLabel.textAlignment = NSTextAlignmentCenter;
    self.docLabel.font = [UIFont systemFontOfSize:14];
    self.docLabel.textColor = [UIColor colorWithWhite:0.4 alpha:1];
    self.docLabel.translatesAutoresizingMaskIntoConstraints = NO;
    [self.docGroup addSubview:self.docLabel];
    UITapGestureRecognizer *docTap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(pickDocument)];
    [self.docGroup addGestureRecognizer:docTap];
    [NSLayoutConstraint activateConstraints:@[
        [self.docLabel.centerXAnchor constraintEqualToAnchor:self.docGroup.centerXAnchor],
        [self.docLabel.centerYAnchor constraintEqualToAnchor:self.docGroup.centerYAnchor],
        [self.docGroup.heightAnchor constraintEqualToConstant:88],
    ]];

    self.pagePill = [self pillWithTitle:self.pageOptions[self.pageIndex] action:@selector(pickPage)];
    self.audiencePill = [self pillWithTitle:self.audienceOptions[self.audienceIndex] action:@selector(pickAudience)];
    self.stylePill = [self pillWithTitle:self.styleOptions[self.styleIndex] action:@selector(pickStyle)];
    UIStackView *pills = [[UIStackView alloc] initWithArrangedSubviews:@[self.pagePill, self.audiencePill, self.stylePill]];
    pills.axis = UILayoutConstraintAxisHorizontal;
    pills.spacing = 8;
    pills.distribution = UIStackViewDistributionFillEqually;

    self.generateBtn = [self primaryButton:@"开始生成" action:@selector(onGenerate)];

    UIStackView *inputStack = [[UIStackView alloc] initWithArrangedSubviews:@[
        self.modeCard, self.quickInput, self.pasteInput, self.docGroup, pills, self.generateBtn
    ]];
    inputStack.axis = UILayoutConstraintAxisVertical;
    inputStack.spacing = 10;
    inputStack.translatesAutoresizingMaskIntoConstraints = NO;
    [self.inputGroup addSubview:inputStack];
    [NSLayoutConstraint activateConstraints:@[
        [inputStack.topAnchor constraintEqualToAnchor:self.inputGroup.topAnchor],
        [inputStack.leadingAnchor constraintEqualToAnchor:self.inputGroup.leadingAnchor],
        [inputStack.trailingAnchor constraintEqualToAnchor:self.inputGroup.trailingAnchor],
        [inputStack.bottomAnchor constraintEqualToAnchor:self.inputGroup.bottomAnchor],
        [self.quickInput.heightAnchor constraintEqualToConstant:96],
        [self.pasteInput.heightAnchor constraintEqualToConstant:96],
    ]];

    self.generatingGroup = [[UIView alloc] init];
    self.generatingQuery = [[UILabel alloc] init];
    self.generatingQuery.numberOfLines = 3;
    self.generatingQuery.font = [UIFont systemFontOfSize:15];
    self.spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleMedium];
    UILabel *status = [[UILabel alloc] init];
    status.text = @"大纲生成中";
    status.textColor = ImpressOrange();
    status.font = [UIFont systemFontOfSize:15 weight:UIFontWeightMedium];
    self.stopBtn = [self secondaryButton:@"停止生成" action:@selector(onStop)];
    UIStackView *genStack = [[UIStackView alloc] initWithArrangedSubviews:@[self.generatingQuery, self.spinner, status, self.stopBtn]];
    genStack.axis = UILayoutConstraintAxisVertical;
    genStack.alignment = UIStackViewAlignmentCenter;
    genStack.spacing = 10;
    genStack.translatesAutoresizingMaskIntoConstraints = NO;
    [self.generatingGroup addSubview:genStack];
    [NSLayoutConstraint activateConstraints:@[
        [genStack.topAnchor constraintEqualToAnchor:self.generatingGroup.topAnchor],
        [genStack.leadingAnchor constraintEqualToAnchor:self.generatingGroup.leadingAnchor],
        [genStack.trailingAnchor constraintEqualToAnchor:self.generatingGroup.trailingAnchor],
        [genStack.bottomAnchor constraintEqualToAnchor:self.generatingGroup.bottomAnchor],
    ]];

    self.completedScroll = [[UIScrollView alloc] init];
    self.cardStack = [[UIStackView alloc] init];
    self.cardStack.axis = UILayoutConstraintAxisVertical;
    self.cardStack.spacing = 8;
    self.cardStack.translatesAutoresizingMaskIntoConstraints = NO;
    [self.completedScroll addSubview:self.cardStack];
    [NSLayoutConstraint activateConstraints:@[
        [self.cardStack.topAnchor constraintEqualToAnchor:self.completedScroll.contentLayoutGuide.topAnchor],
        [self.cardStack.leadingAnchor constraintEqualToAnchor:self.completedScroll.contentLayoutGuide.leadingAnchor],
        [self.cardStack.trailingAnchor constraintEqualToAnchor:self.completedScroll.contentLayoutGuide.trailingAnchor],
        [self.cardStack.bottomAnchor constraintEqualToAnchor:self.completedScroll.contentLayoutGuide.bottomAnchor],
        [self.cardStack.widthAnchor constraintEqualToAnchor:self.completedScroll.frameLayoutGuide.widthAnchor],
    ]];
    self.copyBtn = [self secondaryButton:@"复制" action:@selector(copyOutline)];
    self.regenerateBtn = [self secondaryButton:@"重新生成" action:@selector(onGenerate)];
    self.templateBtn = [self primaryButton:@"选择模板" action:@selector(openTemplateSelect)];
    UIStackView *doneBar = [[UIStackView alloc] initWithArrangedSubviews:@[self.copyBtn, self.regenerateBtn, self.templateBtn]];
    doneBar.axis = UILayoutConstraintAxisHorizontal;
    doneBar.spacing = 8;
    doneBar.distribution = UIStackViewDistributionFillEqually;
    UIStackView *completedCol = [[UIStackView alloc] initWithArrangedSubviews:@[self.completedScroll, doneBar]];
    completedCol.axis = UILayoutConstraintAxisVertical;
    completedCol.spacing = 10;
    completedCol.translatesAutoresizingMaskIntoConstraints = NO;
    self.completedGroup = [[UIView alloc] init];
    [self.completedGroup addSubview:completedCol];
    [NSLayoutConstraint activateConstraints:@[
        [completedCol.topAnchor constraintEqualToAnchor:self.completedGroup.topAnchor],
        [completedCol.leadingAnchor constraintEqualToAnchor:self.completedGroup.leadingAnchor],
        [completedCol.trailingAnchor constraintEqualToAnchor:self.completedGroup.trailingAnchor],
        [completedCol.bottomAnchor constraintEqualToAnchor:self.completedGroup.bottomAnchor],
        [self.completedScroll.heightAnchor constraintEqualToConstant:220],
    ]];

    self.errorGroup = [[UIView alloc] init];
    self.errorText = [[UILabel alloc] init];
    self.errorText.numberOfLines = 0;
    self.errorText.font = [UIFont systemFontOfSize:14];
    self.errorRetryBtn = [self primaryButton:@"重试" action:@selector(onErrorRetry)];
    UIStackView *errStack = [[UIStackView alloc] initWithArrangedSubviews:@[self.errorText, self.errorRetryBtn]];
    errStack.axis = UILayoutConstraintAxisVertical;
    errStack.spacing = 10;
    errStack.translatesAutoresizingMaskIntoConstraints = NO;
    [self.errorGroup addSubview:errStack];
    [NSLayoutConstraint activateConstraints:@[
        [errStack.topAnchor constraintEqualToAnchor:self.errorGroup.topAnchor],
        [errStack.leadingAnchor constraintEqualToAnchor:self.errorGroup.leadingAnchor],
        [errStack.trailingAnchor constraintEqualToAnchor:self.errorGroup.trailingAnchor],
        [errStack.bottomAnchor constraintEqualToAnchor:self.errorGroup.bottomAnchor],
    ]];

    self.templateGroup = [[UIView alloc] init];
    self.templateScroll = [[UIScrollView alloc] init];
    self.templateStack = [[UIStackView alloc] init];
    self.templateStack.axis = UILayoutConstraintAxisVertical;
    self.templateStack.spacing = 8;
    self.templateStack.translatesAutoresizingMaskIntoConstraints = NO;
    [self.templateScroll addSubview:self.templateStack];
    self.templateGenerateBtn = [self primaryButton:@"生成PPT" action:@selector(onTemplateSelected)];
    UIStackView *tmplCol = [[UIStackView alloc] initWithArrangedSubviews:@[self.templateScroll, self.templateGenerateBtn]];
    tmplCol.axis = UILayoutConstraintAxisVertical;
    tmplCol.spacing = 10;
    tmplCol.translatesAutoresizingMaskIntoConstraints = NO;
    [self.templateGroup addSubview:tmplCol];
    [NSLayoutConstraint activateConstraints:@[
        [tmplCol.topAnchor constraintEqualToAnchor:self.templateGroup.topAnchor],
        [tmplCol.leadingAnchor constraintEqualToAnchor:self.templateGroup.leadingAnchor],
        [tmplCol.trailingAnchor constraintEqualToAnchor:self.templateGroup.trailingAnchor],
        [tmplCol.bottomAnchor constraintEqualToAnchor:self.templateGroup.bottomAnchor],
        [self.templateStack.topAnchor constraintEqualToAnchor:self.templateScroll.contentLayoutGuide.topAnchor],
        [self.templateStack.leadingAnchor constraintEqualToAnchor:self.templateScroll.contentLayoutGuide.leadingAnchor],
        [self.templateStack.trailingAnchor constraintEqualToAnchor:self.templateScroll.contentLayoutGuide.trailingAnchor],
        [self.templateStack.bottomAnchor constraintEqualToAnchor:self.templateScroll.contentLayoutGuide.bottomAnchor],
        [self.templateStack.widthAnchor constraintEqualToAnchor:self.templateScroll.frameLayoutGuide.widthAnchor],
        [self.templateScroll.heightAnchor constraintEqualToConstant:240],
    ]];

    self.generatingPptGroup = [[UIView alloc] init];
    self.generatingPptText = [[UILabel alloc] init];
    self.generatingPptText.font = [UIFont systemFontOfSize:15];
    self.generatingPptText.textAlignment = NSTextAlignmentCenter;
    self.generatingPptText.numberOfLines = 2;
    self.pptProgress = [[UIProgressView alloc] initWithProgressViewStyle:UIProgressViewStyleDefault];
    self.pptProgress.progressTintColor = ImpressOrange();
    self.pptCancelBtn = [self secondaryButton:@"取消" action:@selector(cancelPptGeneration)];
    UIStackView *pptStack = [[UIStackView alloc] initWithArrangedSubviews:@[self.generatingPptText, self.pptProgress, self.pptCancelBtn]];
    pptStack.axis = UILayoutConstraintAxisVertical;
    pptStack.spacing = 12;
    pptStack.translatesAutoresizingMaskIntoConstraints = NO;
    [self.generatingPptGroup addSubview:pptStack];
    [NSLayoutConstraint activateConstraints:@[
        [pptStack.topAnchor constraintEqualToAnchor:self.generatingPptGroup.topAnchor],
        [pptStack.leadingAnchor constraintEqualToAnchor:self.generatingPptGroup.leadingAnchor],
        [pptStack.trailingAnchor constraintEqualToAnchor:self.generatingPptGroup.trailingAnchor],
        [pptStack.bottomAnchor constraintEqualToAnchor:self.generatingPptGroup.bottomAnchor],
    ]];

    UIStackView *body = [[UIStackView alloc] initWithArrangedSubviews:@[
        self.inputGroup, self.generatingGroup, self.completedGroup, self.errorGroup,
        self.templateGroup, self.generatingPptGroup
    ]];
    body.axis = UILayoutConstraintAxisVertical;
    body.translatesAutoresizingMaskIntoConstraints = NO;
    [self.panel addSubview:header];
    [self.panel addSubview:body];
    [NSLayoutConstraint activateConstraints:@[
        [header.topAnchor constraintEqualToAnchor:self.panel.topAnchor constant:12],
        [header.leadingAnchor constraintEqualToAnchor:self.panel.leadingAnchor constant:12],
        [header.trailingAnchor constraintEqualToAnchor:self.panel.trailingAnchor constant:-12],
        [header.heightAnchor constraintEqualToConstant:36],
        [body.topAnchor constraintEqualToAnchor:header.bottomAnchor constant:12],
        [body.leadingAnchor constraintEqualToAnchor:self.panel.leadingAnchor constant:16],
        [body.trailingAnchor constraintEqualToAnchor:self.panel.trailingAnchor constant:-16],
        [body.bottomAnchor constraintEqualToAnchor:self.panel.bottomAnchor constant:-16],
    ]];
}

- (UITextView *)makeTextViewPlaceholder:(NSString *)placeholder {
    UITextView *view = [[UITextView alloc] init];
    view.font = [UIFont systemFontOfSize:15];
    view.layer.cornerRadius = 8;
    view.layer.borderWidth = 1;
    view.layer.borderColor = [UIColor colorWithWhite:0.9 alpha:1].CGColor;
    view.text = placeholder;
    view.textColor = [UIColor colorWithWhite:0.6 alpha:1];
    view.delegate = self;
    view.tag = 1;
    return view;
}

- (UIButton *)pillWithTitle:(NSString *)title action:(SEL)action {
    UIButton *btn = [UIButton buttonWithType:UIButtonTypeSystem];
    [btn setTitle:title forState:UIControlStateNormal];
    btn.titleLabel.font = [UIFont systemFontOfSize:13];
    btn.backgroundColor = [UIColor colorWithWhite:0.96 alpha:1];
    btn.layer.cornerRadius = 14;
    [btn addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];
    [btn.heightAnchor constraintEqualToConstant:32].active = YES;
    return btn;
}

- (UIButton *)primaryButton:(NSString *)title action:(SEL)action {
    UIButton *btn = [UIButton buttonWithType:UIButtonTypeSystem];
    [btn setTitle:title forState:UIControlStateNormal];
    [btn setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
    btn.backgroundColor = ImpressOrange();
    btn.layer.cornerRadius = 12;
    btn.titleLabel.font = [UIFont systemFontOfSize:16 weight:UIFontWeightSemibold];
    [btn addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];
    [btn.heightAnchor constraintEqualToConstant:48].active = YES;
    return btn;
}

- (UIButton *)secondaryButton:(NSString *)title action:(SEL)action {
    UIButton *btn = [UIButton buttonWithType:UIButtonTypeSystem];
    [btn setTitle:title forState:UIControlStateNormal];
    [btn setTitleColor:ImpressOrange() forState:UIControlStateNormal];
    btn.layer.cornerRadius = 12;
    btn.layer.borderWidth = 1;
    btn.layer.borderColor = ImpressOrange().CGColor;
    btn.titleLabel.font = [UIFont systemFontOfSize:15];
    [btn addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];
    [btn.heightAnchor constraintEqualToConstant:48].active = YES;
    return btn;
}

- (void)textViewDidBeginEditing:(UITextView *)textView {
    if (textView.tag == 1) {
        textView.text = @"";
        textView.textColor = [UIColor colorWithWhite:0.08 alpha:1];
        textView.tag = 0;
    }
}

- (NSString *)textFrom:(UITextView *)view {
    if (view.tag == 1) {
        return @"";
    }
    return [view.text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
}

- (void)showWithPrefill:(NSString *)prefill {
    UIView *hostView = self.host.view;
    if (self.overlay.superview != hostView) {
        self.overlay.frame = hostView.bounds;
        self.overlay.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
        [hostView addSubview:self.overlay];
        if (!self.panelPinned) {
            [NSLayoutConstraint activateConstraints:@[
                [self.panel.centerXAnchor constraintEqualToAnchor:self.overlay.centerXAnchor],
                [self.panel.centerYAnchor constraintEqualToAnchor:self.overlay.centerYAnchor],
                [self.panel.widthAnchor constraintEqualToAnchor:self.overlay.widthAnchor constant:-48],
                [self.panel.widthAnchor constraintLessThanOrEqualToConstant:670],
                [self.panel.heightAnchor constraintLessThanOrEqualToAnchor:self.overlay.heightAnchor multiplier:0.92],
            ]];
            self.panelPinned = YES;
        }
    }
    self.inputType = @"quick";
    self.docContent = @"";
    self.docDisplayName = @"";
    [self switchInputType:@"quick"];
    if (prefill.length > 0) {
        self.quickInput.text = prefill;
        self.quickInput.textColor = [UIColor colorWithWhite:0.08 alpha:1];
        self.quickInput.tag = 0;
    }
    [self setState:ImpressOutlineStateInput];
    self.overlay.hidden = NO;
}

- (BOOL)isVisible {
    return self.overlay.superview != nil && !self.overlay.hidden;
}

- (void)dismiss {
    [self cancelActiveRequests];
    self.overlay.hidden = YES;
    [self.overlay removeFromSuperview];
}

- (void)cancelActiveRequests {
    self.cancelled = YES;
    if (self.activeRequestId.length > 0) {
        [self.aiService cancelRequest:self.activeRequestId documentSessionId:@"impress-outline"];
        self.activeRequestId = @"";
    }
    if (self.generateRequestId.length > 0) {
        [self.aiService cancelRequest:self.generateRequestId documentSessionId:@"impress-generate"];
        self.generateRequestId = @"";
    }
}

- (void)onBack {
    if (self.state == ImpressOutlineStateTemplateSelect) {
        [self setState:ImpressOutlineStateCompleted];
        return;
    }
    [self setState:ImpressOutlineStateInput];
}

- (void)setState:(ImpressOutlineState)state {
    self.state = state;
    self.inputGroup.hidden = state != ImpressOutlineStateInput;
    self.generatingGroup.hidden = state != ImpressOutlineStateGenerating;
    self.completedGroup.hidden = state != ImpressOutlineStateCompleted;
    self.errorGroup.hidden = state != ImpressOutlineStateError;
    self.templateGroup.hidden = state != ImpressOutlineStateTemplateSelect;
    self.generatingPptGroup.hidden = state != ImpressOutlineStateGeneratingPpt;
    self.backButton.hidden = !(state == ImpressOutlineStateCompleted || state == ImpressOutlineStateTemplateSelect);
    if (state == ImpressOutlineStateTemplateSelect) {
        self.titleLabel.text = @"选择模板";
    } else if (state == ImpressOutlineStateGeneratingPpt) {
        self.titleLabel.text = @"正在生成PPT";
    } else if (state == ImpressOutlineStateCompleted) {
        self.titleLabel.text = @"生成大纲";
    } else {
        self.titleLabel.text = @"生成PPT";
    }
    if (state == ImpressOutlineStateGenerating) {
        [self.spinner startAnimating];
    } else {
        [self.spinner stopAnimating];
    }
}

- (void)switchInputType:(NSString *)type {
    self.inputType = type;
    NSArray *types = [self modeTypes];
    NSInteger idx = [types indexOfObject:type];
    if (idx != NSNotFound) {
        self.modeLabel.text = self.modeLabels[idx];
    }
    self.quickInput.hidden = ![type isEqualToString:@"quick"];
    self.pasteInput.hidden = ![type isEqualToString:@"outline"];
    self.docGroup.hidden = ![type isEqualToString:@"document"];
}

- (void)pickMode {
    [self presentPicker:self.modeLabels selected:[self.modeTypes indexOfObject:self.inputType] completion:^(NSInteger idx) {
        [self switchInputType:self.modeTypes[idx]];
    }];
}

- (void)pickPage {
    [self presentPicker:self.pageOptions selected:self.pageIndex completion:^(NSInteger idx) {
        self.pageIndex = idx;
        [self.pagePill setTitle:self.pageOptions[idx] forState:UIControlStateNormal];
    }];
}

- (void)pickAudience {
    [self presentPicker:self.audienceOptions selected:self.audienceIndex completion:^(NSInteger idx) {
        self.audienceIndex = idx;
        [self.audiencePill setTitle:self.audienceOptions[idx] forState:UIControlStateNormal];
    }];
}

- (void)pickStyle {
    [self presentPicker:self.styleOptions selected:self.styleIndex completion:^(NSInteger idx) {
        self.styleIndex = idx;
        [self.stylePill setTitle:self.styleOptions[idx] forState:UIControlStateNormal];
    }];
}

- (void)presentPicker:(NSArray<NSString *> *)items selected:(NSInteger)selected completion:(void (^)(NSInteger))completion {
    UIAlertController *sheet = [UIAlertController alertControllerWithTitle:nil message:nil preferredStyle:UIAlertControllerStyleActionSheet];
    [items enumerateObjectsUsingBlock:^(NSString *item, NSUInteger idx, BOOL *stop) {
        UIAlertAction *action = [UIAlertAction actionWithTitle:item style:UIAlertActionStyleDefault handler:^(UIAlertAction *a) {
            completion((NSInteger)idx);
        }];
        [sheet addAction:action];
    }];
    [sheet addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];
    UIPopoverPresentationController *pop = sheet.popoverPresentationController;
    pop.sourceView = self.panel;
    pop.sourceRect = CGRectMake(CGRectGetMidX(self.panel.bounds), 40, 1, 1);
    [self.host presentViewController:sheet animated:YES completion:nil];
}

- (void)pickDocument {
    NSArray<UTType *> *types = @[
        UTTypePlainText, UTTypePDF, UTTypeData,
    ];
    UIDocumentPickerViewController *picker =
        [[UIDocumentPickerViewController alloc] initForOpeningContentTypes:types asCopy:YES];
    picker.delegate = self;
    picker.allowsMultipleSelection = NO;
    [self.host presentViewController:picker animated:YES completion:nil];
}

- (void)documentPicker:(UIDocumentPickerViewController *)controller didPickDocumentsAtURLs:(NSArray<NSURL *> *)urls {
    NSURL *url = urls.firstObject;
    if (!url) {
        return;
    }
    [url startAccessingSecurityScopedResource];
    NSString *ext = url.pathExtension.lowercaseString;
    self.docDisplayName = url.lastPathComponent;
    NSString *content = @"";
    if ([ext isEqualToString:@"txt"] || [ext isEqualToString:@"md"]) {
        content = [NSString stringWithContentsOfURL:url encoding:NSUTF8StringEncoding error:nil] ?: @"";
    } else if ([ext isEqualToString:@"docx"]) {
        NSDictionary *extracted = [TypesetService extractStructuredFromFile:url];
        content = [extracted[@"fullText"] isKindOfClass:[NSString class]] ? extracted[@"fullText"] : @"";
    } else {
        content = [NSString stringWithContentsOfURL:url encoding:NSUTF8StringEncoding error:nil] ?: @"";
        if (content.length == 0) {
            self.docLabel.text = @"暂不支持该文件类型，请使用 TXT / DOCX";
            [url stopAccessingSecurityScopedResource];
            return;
        }
    }
    if (content.length > 150000) {
        content = [content substringToIndex:150000];
    }
    self.docContent = content;
    self.docLabel.text = [NSString stringWithFormat:@"已选择：%@\n%lu 字符", self.docDisplayName, (unsigned long)content.length];
    [url stopAccessingSecurityScopedResource];
}

- (void)onErrorRetry {
    if (self.lastErrorWasPpt && self.slides.count > 0) {
        [self setState:ImpressOutlineStateTemplateSelect];
        return;
    }
    [self onGenerate];
}

- (void)onGenerate {
    NSString *userInput = @"";
    if ([self.inputType isEqualToString:@"quick"]) {
        userInput = [self textFrom:self.quickInput];
    } else if ([self.inputType isEqualToString:@"document"]) {
        userInput = self.docContent ?: @"";
    } else {
        userInput = [self textFrom:self.pasteInput];
    }
    if (userInput.length == 0) {
        [self toast:[self.inputType isEqualToString:@"document"] ? @"请先选择文件" : @"请输入内容"];
        return;
    }
    self.lastInput = userInput;
    self.generatingQuery.text = userInput;
    self.cancelled = NO;
    [self setState:ImpressOutlineStateGenerating];
    NSString *requestId = [NSString stringWithFormat:@"impress-outline-%@", [NSUUID UUID].UUIDString];
    self.activeRequestId = requestId;
    NSDictionary *payload = @{
        @"taskType": @"impress_outline",
        @"selection": userInput,
        @"context": @{
            @"inputType": self.inputType,
            @"userInput": userInput,
            @"pageRange": @([self pageRangeValue]),
            @"audience": self.audienceOptions[self.audienceIndex],
            @"style": self.styleOptions[self.styleIndex],
        },
    };
    __weak ImpressOutlineOverlayController *weakSelf = self;
    [self.aiService startRequest:payload
                       requestId:requestId
              documentSessionId:@"impress-outline"
                           emit:^(NSString *type, NSString *rid, NSString *sessionId, NSDictionary *payload) {
        dispatch_async(dispatch_get_main_queue(), ^{
            [weakSelf handleOutlineEvent:type requestId:rid payload:payload];
        });
    }];
}

- (void)onStop {
    [self cancelActiveRequests];
    [self setState:ImpressOutlineStateInput];
}

- (void)handleOutlineEvent:(NSString *)type requestId:(NSString *)requestId payload:(NSDictionary *)payload {
    if (![requestId isEqualToString:self.activeRequestId]) {
        return;
    }
    if ([type isEqualToString:@"ai.done"]) {
        NSString *text = [payload[@"text"] isKindOfClass:[NSString class]] ? payload[@"text"] : @"";
        self.activeRequestId = @"";
        [self parseAndShowOutline:text];
    } else if ([type isEqualToString:@"ai.error"]) {
        NSString *message = [payload[@"message"] isKindOfClass:[NSString class]] ? payload[@"message"] : @"大纲生成失败";
        self.activeRequestId = @"";
        self.errorText.text = [NSString stringWithFormat:@"大纲生成失败：%@", message];
        self.lastErrorWasPpt = NO;
        [self setState:ImpressOutlineStateError];
    }
}

- (NSString *)stripFences:(NSString *)text {
    NSString *clean = [text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if ([clean hasPrefix:@"```"]) {
        NSRegularExpression *re = [NSRegularExpression regularExpressionWithPattern:@"^```(?:json)?\\s*|\\s*```$"
                                                                            options:NSRegularExpressionDotMatchesLineSeparators
                                                                              error:nil];
        clean = [re stringByReplacingMatchesInString:clean options:0 range:NSMakeRange(0, clean.length) withTemplate:@""];
    }
    return [clean stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
}

- (void)parseAndShowOutline:(NSString *)jsonText {
    NSString *clean = [self stripFences:jsonText];
    NSData *data = [clean dataUsingEncoding:NSUTF8StringEncoding];
    id obj = data ? [NSJSONSerialization JSONObjectWithData:data options:0 error:nil] : nil;
    NSArray *slides = nil;
    if ([obj isKindOfClass:[NSDictionary class]]) {
        slides = obj[@"slides"];
    }
    if (![slides isKindOfClass:[NSArray class]] || slides.count == 0) {
        self.errorText.text = [NSString stringWithFormat:@"大纲解析失败，AI返回原始文本：\n\n%@", clean];
        self.lastErrorWasPpt = NO;
        [self setState:ImpressOutlineStateError];
        return;
    }
    [self.slides removeAllObjects];
    for (id item in slides) {
        if ([item isKindOfClass:[NSDictionary class]]) {
            [self.slides addObject:[item mutableCopy]];
        }
    }
    [self renderCards];
    [self setState:ImpressOutlineStateCompleted];
}

- (void)renderCards {
    for (UIView *view in self.cardStack.arrangedSubviews) {
        [self.cardStack removeArrangedSubview:view];
        [view removeFromSuperview];
    }
    [self.slides enumerateObjectsUsingBlock:^(NSMutableDictionary *slide, NSUInteger idx, BOOL *stop) {
        UIView *card = [[UIView alloc] init];
        card.backgroundColor = [UIColor colorWithWhite:0.97 alpha:1];
        card.layer.cornerRadius = 8;
        UILabel *page = [[UILabel alloc] init];
        page.font = [UIFont systemFontOfSize:12 weight:UIFontWeightMedium];
        page.textColor = ImpressOrange();
        page.text = [NSString stringWithFormat:@"P%ld", (long)(idx + 1)];
        UILabel *type = [[UILabel alloc] init];
        type.font = [UIFont systemFontOfSize:12];
        type.text = [self typeLabelForKey:PptxSafeType(slide[@"type"])];
        UITextField *title = [[UITextField alloc] init];
        title.font = [UIFont systemFontOfSize:15 weight:UIFontWeightSemibold];
        title.text = slide[@"title"] ?: @"";
        title.tag = (NSInteger)idx;
        [title addTarget:self action:@selector(titleChanged:) forControlEvents:UIControlEventEditingChanged];
        UITextView *content = [[UITextView alloc] init];
        content.font = [UIFont systemFontOfSize:14];
        content.text = slide[@"content"] ?: @"";
        content.backgroundColor = [UIColor clearColor];
        content.tag = (NSInteger)idx + 1000;
        content.delegate = self;
        content.scrollEnabled = NO;
        [content.heightAnchor constraintGreaterThanOrEqualToConstant:36].active = YES;
        UIStackView *head = [[UIStackView alloc] initWithArrangedSubviews:@[page, type]];
        head.axis = UILayoutConstraintAxisHorizontal;
        head.spacing = 8;
        UIStackView *col = [[UIStackView alloc] initWithArrangedSubviews:@[head, title, content]];
        col.axis = UILayoutConstraintAxisVertical;
        col.spacing = 4;
        col.translatesAutoresizingMaskIntoConstraints = NO;
        [card addSubview:col];
        [NSLayoutConstraint activateConstraints:@[
            [col.topAnchor constraintEqualToAnchor:card.topAnchor constant:8],
            [col.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:10],
            [col.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-10],
            [col.bottomAnchor constraintEqualToAnchor:card.bottomAnchor constant:-8],
        ]];
        [self.cardStack addArrangedSubview:card];
    }];
}

- (void)titleChanged:(UITextField *)field {
    if (field.tag >= 0 && field.tag < (NSInteger)self.slides.count) {
        self.slides[field.tag][@"title"] = field.text ?: @"";
    }
}

- (void)textViewDidChange:(UITextView *)textView {
    if (textView.tag >= 1000) {
        NSInteger idx = textView.tag - 1000;
        if (idx >= 0 && idx < (NSInteger)self.slides.count) {
            self.slides[idx][@"content"] = textView.text ?: @"";
        }
    }
}

- (void)copyOutline {
    NSMutableString *sb = [NSMutableString string];
    [self.slides enumerateObjectsUsingBlock:^(NSMutableDictionary *slide, NSUInteger idx, BOOL *stop) {
        [sb appendFormat:@"P%ld %@\n%@\n\n", (long)(idx + 1), slide[@"title"] ?: @"", slide[@"content"] ?: @""];
    }];
    UIPasteboard.generalPasteboard.string = sb;
    [self toast:@"大纲已复制"];
}

- (UIButton *)templateGridCardForInfo:(PptxTemplateInfo *)info {
    UIButton *card = [UIButton buttonWithType:UIButtonTypeSystem];
    card.backgroundColor = [UIColor colorWithWhite:0.96 alpha:1];
    card.layer.cornerRadius = 12;
    card.clipsToBounds = YES;
    [card addTarget:self action:@selector(selectTemplateCard:) forControlEvents:UIControlEventTouchUpInside];
    UIImage *cover = [PptxTemplateService coverImageForTemplate:info];
    UIImageView *image = [[UIImageView alloc] initWithImage:cover];
    image.contentMode = UIViewContentModeScaleAspectFill;
    image.clipsToBounds = YES;
    image.backgroundColor = [UIColor colorWithWhite:0.9 alpha:1];
    image.translatesAutoresizingMaskIntoConstraints = NO;
    UILabel *name = [[UILabel alloc] init];
    name.text = info.name;
    name.font = [UIFont systemFontOfSize:13 weight:UIFontWeightMedium];
    name.textAlignment = NSTextAlignmentCenter;
    name.numberOfLines = 2;
    UIStackView *col = [[UIStackView alloc] initWithArrangedSubviews:@[image, name]];
    col.axis = UILayoutConstraintAxisVertical;
    col.spacing = 6;
    col.userInteractionEnabled = NO;
    col.translatesAutoresizingMaskIntoConstraints = NO;
    [card addSubview:col];
    [NSLayoutConstraint activateConstraints:@[
        [image.heightAnchor constraintEqualToConstant:72],
        [col.topAnchor constraintEqualToAnchor:card.topAnchor constant:8],
        [col.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:8],
        [col.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-8],
        [col.bottomAnchor constraintEqualToAnchor:card.bottomAnchor constant:-8],
        [card.heightAnchor constraintEqualToConstant:118],
    ]];
    objc_setAssociatedObject(card, @selector(selectTemplateCard:), info.templateId, OBJC_ASSOCIATION_COPY_NONATOMIC);
    return card;
}

- (void)openTemplateSelect {
    self.selectedTemplateId = @"";
    for (UIView *view in self.templateStack.arrangedSubviews) {
        [self.templateStack removeArrangedSubview:view];
        [view removeFromSuperview];
    }
    NSArray<PptxTemplateInfo *> *templates = [PptxTemplateService loadIndex];
    if (templates.count == 0) {
        UILabel *empty = [[UILabel alloc] init];
        empty.text = @"未找到模板资源";
        empty.textAlignment = NSTextAlignmentCenter;
        [self.templateStack addArrangedSubview:empty];
        [self updateTemplateGenerateEnabled];
        [self setState:ImpressOutlineStateTemplateSelect];
        return;
    }
    UIStackView * __block row = nil;
    [templates enumerateObjectsUsingBlock:^(PptxTemplateInfo *info, NSUInteger idx, BOOL *stop) {
        if (idx % 2 == 0) {
            row = [[UIStackView alloc] init];
            row.axis = UILayoutConstraintAxisHorizontal;
            row.spacing = 10;
            row.distribution = UIStackViewDistributionFillEqually;
            [self.templateStack addArrangedSubview:row];
        }
        [row addArrangedSubview:[self templateGridCardForInfo:info]];
    }];
    if (templates.count % 2 == 1) {
        UIView *pad = [[UIView alloc] init];
        pad.userInteractionEnabled = NO;
        [row addArrangedSubview:pad];
    }
    [self updateTemplateGenerateEnabled];
    [self setState:ImpressOutlineStateTemplateSelect];
}

- (NSArray<UIView *> *)templateCards {
    NSMutableArray<UIView *> *cards = [NSMutableArray array];
    for (UIView *row in self.templateStack.arrangedSubviews) {
        if ([row isKindOfClass:[UIStackView class]]) {
            [cards addObjectsFromArray:((UIStackView *)row).arrangedSubviews];
        } else {
            [cards addObject:row];
        }
    }
    return cards;
}

- (void)selectTemplateCard:(UIButton *)sender {
    self.selectedTemplateId = objc_getAssociatedObject(sender, @selector(selectTemplateCard:));
    for (UIView *view in [self templateCards]) {
        view.layer.borderWidth = 0;
    }
    sender.layer.borderWidth = 2;
    sender.layer.borderColor = ImpressOrange().CGColor;
    [self updateTemplateGenerateEnabled];
}

- (void)updateTemplateGenerateEnabled {
    BOOL enabled = self.selectedTemplateId.length > 0;
    self.templateGenerateBtn.enabled = enabled;
    self.templateGenerateBtn.alpha = enabled ? 1 : 0.45;
}

- (void)onTemplateSelected {
    if (self.selectedTemplateId.length == 0 || self.slides.count == 0) {
        [self toast:self.slides.count == 0 ? @"没有可生成的大纲" : @"请先选择模板"];
        return;
    }
    self.cancelled = NO;
    [self.generatedByIndex removeAllObjects];
    self.generateTotal = (NSInteger)self.slides.count;
    self.generateCurrent = 0;
    [self setState:ImpressOutlineStateGeneratingPpt];
    [self startBatch:0];
}

- (void)startBatch:(NSInteger)batchIndex {
    if (self.cancelled) {
        return;
    }
    if (batchIndex >= self.generateTotal) {
        [self finishPptFill];
        return;
    }
    self.generateCurrent = batchIndex;
    NSDictionary *slide = self.slides[batchIndex];
    NSString *title = slide[@"title"] ?: @"";
    self.generatingPptText.text = [NSString stringWithFormat:@"正在生成 %ld/%ld  %@", (long)(batchIndex + 1), (long)self.generateTotal, title];
    self.pptProgress.progress = (float)batchIndex / (float)MAX(self.generateTotal, 1);
    if ([PptxSafeType(slide[@"type"]) isEqualToString:@"section_divider"]) {
        self.generatedByIndex[@(batchIndex)] = slide;
        [self startBatch:batchIndex + 1];
        return;
    }
    NSString *requestId = [NSString stringWithFormat:@"impress-generate-%@", [NSUUID UUID].UUIDString];
    self.generateRequestId = requestId;
    NSDictionary *payload = @{
        @"taskType": @"impress_generate",
        @"selection": title,
        @"context": @{
            @"templateId": self.selectedTemplateId ?: @"",
            @"batchIndex": @(batchIndex),
            @"totalBatches": @(self.generateTotal),
            @"batchSlides": @[slide],
            @"outlineSlides": self.slides,
        },
    };
    __weak ImpressOutlineOverlayController *weakSelf = self;
    [self.aiService startRequest:payload
                       requestId:requestId
              documentSessionId:@"impress-generate"
                           emit:^(NSString *type, NSString *rid, NSString *sessionId, NSDictionary *payload) {
        dispatch_async(dispatch_get_main_queue(), ^{
            [weakSelf handleGenerateEvent:type requestId:rid payload:payload];
        });
    }];
}

- (void)handleGenerateEvent:(NSString *)type requestId:(NSString *)requestId payload:(NSDictionary *)payload {
    if (![requestId isEqualToString:self.generateRequestId] || self.cancelled) {
        return;
    }
    if ([type isEqualToString:@"ai.done"]) {
        NSString *text = [payload[@"text"] isKindOfClass:[NSString class]] ? payload[@"text"] : @"";
        self.generateRequestId = @"";
        NSString *clean = [self stripFences:text];
        NSData *data = [clean dataUsingEncoding:NSUTF8StringEncoding];
        id obj = data ? [NSJSONSerialization JSONObjectWithData:data options:0 error:nil] : nil;
        NSArray *slides = [obj isKindOfClass:[NSDictionary class]] ? obj[@"slides"] : nil;
        if (![slides isKindOfClass:[NSArray class]] || slides.count == 0) {
            [self failStop:@"生成失败：本页未返回有效内容"];
            return;
        }
        id first = slides.firstObject;
        if ([first isKindOfClass:[NSDictionary class]]) {
            self.generatedByIndex[@(self.generateCurrent)] = first;
        }
        [self startBatch:self.generateCurrent + 1];
    } else if ([type isEqualToString:@"ai.error"]) {
        self.generateRequestId = @"";
        NSString *message = [payload[@"message"] isKindOfClass:[NSString class]] ? payload[@"message"] : @"生成失败";
        [self failStop:message];
    }
}

- (void)failStop:(NSString *)message {
    [self cancelActiveRequests];
    self.lastErrorWasPpt = YES;
    self.errorText.text = [NSString stringWithFormat:@"PPT 生成已停止：%@", message];
    [self setState:ImpressOutlineStateError];
}

- (void)cancelPptGeneration {
    [self cancelActiveRequests];
    [self setState:ImpressOutlineStateTemplateSelect];
}

- (void)finishPptFill {
    self.pptProgress.progress = 1;
    self.generatingPptText.text = @"正在写入模板…";
    NSString *cover = self.slides.firstObject[@"title"] ?: @"AI生成PPT";
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        NSError *error = nil;
        NSURL *output = [PptxTemplateService fillAndAssembleTemplateId:self.selectedTemplateId
                                                         outlineSlides:self.slides
                                                     generatedByIndex:self.generatedByIndex
                                                           outputName:cover
                                                                error:&error];
        dispatch_async(dispatch_get_main_queue(), ^{
            if (!output) {
                [self failStop:error.localizedDescription ?: @"模板填充失败"];
                return;
            }
            RecentDocumentsStore *store = [[RecentDocumentsStore alloc] init];
            [store recordURL:output];
            [self setState:ImpressOutlineStateCompleted];
            UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"PPT 已生成"
                                                                           message:[NSString stringWithFormat:@"已保存到最近文件：%@\n请关闭当前文档后从首页打开。", output.lastPathComponent]
                                                                    preferredStyle:UIAlertControllerStyleAlert];
            [alert addAction:[UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
                [self dismiss];
            }]];
            [self.host presentViewController:alert animated:YES completion:nil];
        });
    });
}

- (void)toast:(NSString *)text {
    UIAlertController *alert = [UIAlertController alertControllerWithTitle:nil message:text preferredStyle:UIAlertControllerStyleAlert];
    [self.host presentViewController:alert animated:YES completion:^{
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            [alert dismissViewControllerAnimated:YES completion:nil];
        });
    }];
}

@end
