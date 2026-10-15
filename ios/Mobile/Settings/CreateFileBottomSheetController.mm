// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "CreateFileBottomSheetController.h"
#import "../Typeset/TypesetService.h"
#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>

static UIColor *CreateFileInputBorderColor(void) {
    return [UIColor colorWithRed:0xA2 / 255.0 green:0xA9 / 255.0 blue:0xB2 / 255.0 alpha:1];
}

static UIColor *CreateFileButtonColorForKind(CreateFileDocKind kind) {
    switch (kind) {
        case CreateFileDocKindCalc:
            return [UIColor colorWithRed:0x3B / 255.0 green:0x80 / 255.0 blue:0x40 / 255.0 alpha:1];
        case CreateFileDocKindImpress:
            return [UIColor colorWithRed:0xEC / 255.0 green:0x5D / 255.0 blue:0x1F / 255.0 alpha:1];
        default:
            return [UIColor colorWithRed:0x12 / 255.0 green:0x78 / 255.0 blue:0xD9 / 255.0 alpha:1];
    }
}

@interface CreateFileBottomSheetController () <UIGestureRecognizerDelegate, UITextFieldDelegate, UITextViewDelegate, UIDocumentPickerDelegate>
@property (assign, nonatomic) CreateFileDocKind docKind;
@property (copy, nonatomic) void (^completion)(NSString *, BOOL, NSString *, NSDictionary *);
@property (strong, nonatomic) UITextField *fileNameField;
@property (strong, nonatomic) UITextField *aiPromptField;
@property (assign, nonatomic) BOOL aiSwitchOn;
@property (strong, nonatomic) UIView *aiSwitchTrack;
@property (strong, nonatomic) UIView *aiSwitchThumb;
@property (strong, nonatomic) NSLayoutConstraint *aiSwitchThumbLeading;
@property (strong, nonatomic) NSLayoutConstraint *aiSwitchThumbTrailing;
@property (strong, nonatomic) NSLayoutConstraint *aiPromptHeight;
@property (strong, nonatomic) NSLayoutConstraint *createButtonTopToPrompt;
@property (strong, nonatomic) NSLayoutConstraint *cardBottomConstraint;
@property (strong, nonatomic) UITextView *impressPromptView;
@property (strong, nonatomic) UILabel *impressPlaceholder;
@property (strong, nonatomic) UIStackView *impressOptionsView;
@property (strong, nonatomic) UISwitch *impressAiSwitch;
@property (strong, nonatomic) UIButton *impressCreateButton;
@property (strong, nonatomic) UIButton *impressSourceButton;
@property (strong, nonatomic) CAGradientLayer *impressGradient;
@property (assign, nonatomic) NSInteger impressMode;
@property (assign, nonatomic) NSInteger impressPage;
@property (assign, nonatomic) NSInteger impressAudience;
@property (assign, nonatomic) NSInteger impressStyle;
@property (assign, nonatomic) BOOL impressSourceLoading;
@end

@implementation CreateFileBottomSheetController

- (instancetype)initWithDocKind:(CreateFileDocKind)docKind
                     completion:(void (^)(NSString *, BOOL, NSString *, NSDictionary *))completion {
    self = [super initWithNibName:nil bundle:nil];
    if (self) {
        _docKind = docKind;
        _completion = [completion copy];
        self.modalPresentationStyle = UIModalPresentationOverFullScreen;
        self.modalTransitionStyle = UIModalTransitionStyleCrossDissolve;
    }
    return self;
}

- (NSString *)defaultBasename {
    switch (self.docKind) {
        case CreateFileDocKindCalc:
            return @"表格";
        case CreateFileDocKindImpress:
            return @"演示";
        default:
            return @"文档";
    }
}

- (NSString *)sheetTitle {
    switch (self.docKind) {
        case CreateFileDocKindCalc:
            return @"新建电子表格";
        case CreateFileDocKindImpress:
            return @"新建演示文稿";
        default:
            return @"新建文本文档";
    }
}

- (NSString *)createButtonTitle {
    switch (self.docKind) {
        case CreateFileDocKindCalc:
            return @"创建电子表格";
        case CreateFileDocKindImpress:
            return @"创建演示文稿";
        default:
            return @"创建文本文档";
    }
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor colorWithWhite:0 alpha:0.35];
    if (self.docKind == CreateFileDocKindImpress) {
        [self buildImpressCreationUi];
        return;
    }

    UITapGestureRecognizer *dismissTap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(dismissSheet)];
    dismissTap.cancelsTouchesInView = NO;
    dismissTap.delegate = self;
    [self.view addGestureRecognizer:dismissTap];

    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(keyboardWillChangeFrame:)
                                                 name:UIKeyboardWillChangeFrameNotification
                                               object:nil];

    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.backgroundColor = UIColor.whiteColor;
    card.layer.cornerRadius = 24;
    card.layer.maskedCorners = kCALayerMinXMinYCorner | kCALayerMaxXMinYCorner;
    card.clipsToBounds = YES;
    [self.view addSubview:card];

    UIButton *close = [UIButton buttonWithType:UIButtonTypeSystem];
    close.translatesAutoresizingMaskIntoConstraints = NO;
    [close setTitle:@"✕" forState:UIControlStateNormal];
    [close setTitleColor:[UIColor colorWithWhite:0.25 alpha:1] forState:UIControlStateNormal];
    close.titleLabel.font = [UIFont systemFontOfSize:20 weight:UIFontWeightMedium];
    [close addTarget:self action:@selector(dismissSheet) forControlEvents:UIControlEventTouchUpInside];

    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = [self sheetTitle];
    title.font = [UIFont boldSystemFontOfSize:20];
    title.textColor = [UIColor colorWithRed:0x1F / 255.0 green:0x23 / 255.0 blue:0x29 / 255.0 alpha:1];
    title.textAlignment = NSTextAlignmentCenter;

    UILabel *nameLabel = [[UILabel alloc] init];
    nameLabel.translatesAutoresizingMaskIntoConstraints = NO;
    nameLabel.text = @"文件名";
    nameLabel.font = [UIFont systemFontOfSize:18];
    nameLabel.textColor = title.textColor;

    self.fileNameField = [self borderedFieldWithPlaceholder:@"请输入文件名"];
    self.fileNameField.text = [self defaultBasename];
    self.fileNameField.delegate = self;
    self.fileNameField.returnKeyType = UIReturnKeyDone;

    UIView *aiRow = [[UIView alloc] init];
    aiRow.translatesAutoresizingMaskIntoConstraints = NO;
    aiRow.backgroundColor = [UIColor colorWithWhite:0.97 alpha:1];
    aiRow.layer.cornerRadius = 8;

    UILabel *aiLabel = [[UILabel alloc] init];
    aiLabel.translatesAutoresizingMaskIntoConstraints = NO;
    aiLabel.text = @"使用 AI 生成内容";
    aiLabel.font = [UIFont systemFontOfSize:16];
    aiLabel.textColor = title.textColor;

    self.aiSwitchTrack = [[UIView alloc] init];
    self.aiSwitchTrack.translatesAutoresizingMaskIntoConstraints = NO;
    self.aiSwitchTrack.backgroundColor = [UIColor colorWithRed:0xCB / 255.0 green:0xD1 / 255.0 blue:0xD7 / 255.0 alpha:1];
    self.aiSwitchTrack.layer.cornerRadius = 10.5;
    self.aiSwitchTrack.userInteractionEnabled = YES;
    [self.aiSwitchTrack addGestureRecognizer:[[UITapGestureRecognizer alloc] initWithTarget:self
                                                                                     action:@selector(toggleAiSwitch)]];

    self.aiSwitchThumb = [[UIView alloc] init];
    self.aiSwitchThumb.translatesAutoresizingMaskIntoConstraints = NO;
    self.aiSwitchThumb.backgroundColor = UIColor.whiteColor;
    self.aiSwitchThumb.layer.cornerRadius = 8;
    self.aiSwitchThumb.layer.shadowColor = UIColor.blackColor.CGColor;
    self.aiSwitchThumb.layer.shadowOpacity = 0.15;
    self.aiSwitchThumb.layer.shadowRadius = 2;
    self.aiSwitchThumb.layer.shadowOffset = CGSizeMake(0, 1);
    [self.aiSwitchTrack addSubview:self.aiSwitchThumb];

    self.aiPromptField = [self borderedFieldWithPlaceholder:@"描述你想生成的文档内容..."];
    self.aiPromptField.delegate = self;
    self.aiPromptField.returnKeyType = UIReturnKeyDone;
    self.aiPromptField.hidden = YES;

    UIButton *createButton = [UIButton buttonWithType:UIButtonTypeSystem];
    createButton.translatesAutoresizingMaskIntoConstraints = NO;
    createButton.backgroundColor = CreateFileButtonColorForKind(self.docKind);
    createButton.layer.cornerRadius = 8;
    [createButton setTitle:[self createButtonTitle] forState:UIControlStateNormal];
    [createButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    createButton.titleLabel.font = [UIFont boldSystemFontOfSize:18];
    [createButton addTarget:self action:@selector(createTapped) forControlEvents:UIControlEventTouchUpInside];

    for (UIView *v in @[ card, close, title, nameLabel, self.fileNameField, aiRow, aiLabel,
                         self.aiSwitchTrack, self.aiPromptField, createButton ]) {
        if (v != card) {
            [card addSubview:v];
        }
    }
    [aiRow addSubview:aiLabel];
    [aiRow addSubview:self.aiSwitchTrack];

    self.aiSwitchThumbLeading = [self.aiSwitchThumb.leadingAnchor constraintEqualToAnchor:self.aiSwitchTrack.leadingAnchor
                                                                                 constant:2.5];
    self.aiSwitchThumbTrailing = [self.aiSwitchThumb.trailingAnchor constraintEqualToAnchor:self.aiSwitchTrack.trailingAnchor
                                                                                 constant:-2.5];
    self.aiSwitchThumbLeading.active = YES;

    self.cardBottomConstraint = [card.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor];
    [NSLayoutConstraint activateConstraints:@[
        [card.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [card.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        self.cardBottomConstraint,

        [close.topAnchor constraintEqualToAnchor:card.topAnchor constant:11],
        [close.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-15],
        [close.widthAnchor constraintEqualToConstant:40],
        [close.heightAnchor constraintEqualToConstant:40],
        [title.centerYAnchor constraintEqualToAnchor:close.centerYAnchor],
        [title.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:55],
        [title.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-55],

        [nameLabel.topAnchor constraintEqualToAnchor:close.bottomAnchor constant:8],
        [nameLabel.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:16],
        [self.fileNameField.topAnchor constraintEqualToAnchor:nameLabel.bottomAnchor constant:8],
        [self.fileNameField.leadingAnchor constraintEqualToAnchor:nameLabel.leadingAnchor],
        [self.fileNameField.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-16],
        [self.fileNameField.heightAnchor constraintEqualToConstant:44],

        [aiRow.topAnchor constraintEqualToAnchor:self.fileNameField.bottomAnchor constant:20],
        [aiRow.leadingAnchor constraintEqualToAnchor:self.fileNameField.leadingAnchor],
        [aiRow.trailingAnchor constraintEqualToAnchor:self.fileNameField.trailingAnchor],
        [aiRow.heightAnchor constraintEqualToConstant:45],
        [aiLabel.leadingAnchor constraintEqualToAnchor:aiRow.leadingAnchor constant:12],
        [aiLabel.centerYAnchor constraintEqualToAnchor:aiRow.centerYAnchor],
        [self.aiSwitchTrack.trailingAnchor constraintEqualToAnchor:aiRow.trailingAnchor constant:-12],
        [self.aiSwitchTrack.centerYAnchor constraintEqualToAnchor:aiRow.centerYAnchor],
        [self.aiSwitchTrack.widthAnchor constraintEqualToConstant:41],
        [self.aiSwitchTrack.heightAnchor constraintEqualToConstant:21],
        [self.aiSwitchThumb.centerYAnchor constraintEqualToAnchor:self.aiSwitchTrack.centerYAnchor],
        [self.aiSwitchThumb.widthAnchor constraintEqualToConstant:16],
        [self.aiSwitchThumb.heightAnchor constraintEqualToConstant:16],

        [self.aiPromptField.topAnchor constraintEqualToAnchor:aiRow.bottomAnchor constant:12],
        [self.aiPromptField.leadingAnchor constraintEqualToAnchor:self.fileNameField.leadingAnchor],
        [self.aiPromptField.trailingAnchor constraintEqualToAnchor:self.fileNameField.trailingAnchor],
    ]];
    self.aiPromptHeight = [self.aiPromptField.heightAnchor constraintEqualToConstant:0];
    self.aiPromptHeight.active = YES;
    self.createButtonTopToPrompt = [createButton.topAnchor constraintEqualToAnchor:self.aiPromptField.bottomAnchor constant:20];
    self.createButtonTopToPrompt.active = YES;
    [NSLayoutConstraint activateConstraints:@[
        [createButton.leadingAnchor constraintEqualToAnchor:self.fileNameField.leadingAnchor],
        [createButton.trailingAnchor constraintEqualToAnchor:self.fileNameField.trailingAnchor],
        [createButton.heightAnchor constraintEqualToConstant:44],
        [createButton.bottomAnchor constraintEqualToAnchor:card.safeAreaLayoutGuide.bottomAnchor constant:-24],
    ]];

    [self updateAiSwitchUi];
}

- (void)dealloc {
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer shouldReceiveTouch:(UITouch *)touch {
    return touch.view == self.view;
}

- (BOOL)textFieldShouldReturn:(UITextField *)textField {
    [textField resignFirstResponder];
    return YES;
}

- (void)keyboardWillChangeFrame:(NSNotification *)notification {
    CGRect keyboardFrame = [notification.userInfo[UIKeyboardFrameEndUserInfoKey] CGRectValue];
    CGRect localKeyboardFrame = [self.view convertRect:keyboardFrame fromView:nil];
    CGFloat overlap = MAX(0.0, CGRectGetMaxY(self.view.bounds) - CGRectGetMinY(localKeyboardFrame));
    NSTimeInterval duration = [notification.userInfo[UIKeyboardAnimationDurationUserInfoKey] doubleValue];
    UIViewAnimationOptions options =
        ([notification.userInfo[UIKeyboardAnimationCurveUserInfoKey] integerValue] << 16)
        | UIViewAnimationOptionBeginFromCurrentState;
    self.cardBottomConstraint.constant = -overlap;
    [UIView animateWithDuration:duration
                          delay:0.0
                        options:options
                     animations:^{
        [self.view layoutIfNeeded];
    }
                     completion:nil];
}

- (UITextField *)borderedFieldWithPlaceholder:(NSString *)placeholder {
    UITextField *field = [[UITextField alloc] init];
    field.translatesAutoresizingMaskIntoConstraints = NO;
    field.placeholder = placeholder;
    field.font = [UIFont systemFontOfSize:16];
    field.borderStyle = UITextBorderStyleNone;
    field.layer.cornerRadius = 8;
    field.layer.borderWidth = 1;
    field.layer.borderColor = CreateFileInputBorderColor().CGColor;
    field.leftView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, 12, 1)];
    field.leftViewMode = UITextFieldViewModeAlways;
    field.rightView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, 12, 1)];
    field.rightViewMode = UITextFieldViewModeAlways;
    return field;
}

- (void)toggleAiSwitch {
    self.aiSwitchOn = !self.aiSwitchOn;
    self.aiPromptField.hidden = !self.aiSwitchOn;
    self.aiPromptHeight.constant = self.aiSwitchOn ? 44 : 0;
    if (self.aiSwitchOn && self.docKind == CreateFileDocKindCalc) {
        self.aiPromptField.placeholder = @"描述你想生成的表格内容，例如：2024年各季度销售数据表";
    } else if (self.aiSwitchOn) {
        self.aiPromptField.placeholder = @"描述你想生成的文档内容...";
    }
    [self updateAiSwitchUi];
}

- (void)updateAiSwitchUi {
    self.aiSwitchThumbLeading.active = !self.aiSwitchOn;
    self.aiSwitchThumbTrailing.active = self.aiSwitchOn;
    [self.view layoutIfNeeded];
}

- (void)dismissSheet {
    [self.view endEditing:YES];
    [self dismissViewControllerAnimated:YES completion:nil];
}

- (void)createTapped {
    NSString *name = [self.fileNameField.text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (name.length == 0) {
        self.fileNameField.layer.borderColor = [UIColor colorWithRed:0xD9 / 255.0 green:0x30 / 255.0 blue:0x25 / 255.0 alpha:1].CGColor;
        return;
    }
    NSString *input = self.docKind == CreateFileDocKindImpress ? self.impressPromptView.text : self.aiPromptField.text;
    NSString *desc = [input stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]] ?: @"";
    if (self.docKind == CreateFileDocKindImpress && self.aiSwitchOn) {
        if (self.impressSourceLoading || desc.length == 0) {
            self.impressPromptView.layer.borderWidth = 1;
            self.impressPromptView.layer.borderColor = UIColor.systemRedColor.CGColor;
            [self.impressPromptView becomeFirstResponder];
            return;
        }
    }
    if (self.aiSwitchOn && self.docKind == CreateFileDocKindCalc && desc.length == 0) {
        self.aiPromptField.layer.borderColor = [UIColor colorWithRed:0xD9 / 255.0 green:0x30 / 255.0 blue:0x25 / 255.0 alpha:1].CGColor;
        return;
    }
    BOOL aiOn = self.aiSwitchOn;
    NSDictionary *options = self.docKind == CreateFileDocKindImpress ? @{
        @"title": name,
        @"inputType": @[@"quick", @"outline", @"document"][self.impressMode],
        @"pageIndex": @(self.impressPage),
        @"audienceIndex": @(self.impressAudience),
        @"styleIndex": @(self.impressStyle),
    } : @{};
    void (^block)(NSString *, BOOL, NSString *, NSDictionary *) = self.completion;
    [self dismissViewControllerAnimated:YES completion:^{
        if (block) {
            block(name, aiOn, desc, options);
        }
    }];
}


- (UIButton *)impressOptionButton:(NSString *)title tag:(NSInteger)tag {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
    [button setTitle:[title stringByAppendingString:@"  ⌄"] forState:UIControlStateNormal];
    [button setTitleColor:[UIColor colorWithWhite:0.12 alpha:1] forState:UIControlStateNormal];
    button.titleLabel.font = [UIFont systemFontOfSize:13];
    button.backgroundColor = [UIColor colorWithWhite:0.96 alpha:1];
    button.layer.cornerRadius = 8;
    button.tag = tag;
    [button.heightAnchor constraintEqualToConstant:36].active = YES;
    [button addTarget:self action:@selector(pickImpressOption:) forControlEvents:UIControlEventTouchUpInside];
    return button;
}

- (void)buildImpressCreationUi {
    self.impressPage = 1;
    self.impressStyle = 4;
    self.aiSwitchOn = YES;
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(keyboardWillChangeFrame:)
                                               name:UIKeyboardWillChangeFrameNotification object:nil];
    UITapGestureRecognizer *tap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(dismissSheet)];
    tap.delegate = self;
    tap.cancelsTouchesInView = NO;
    [self.view addGestureRecognizer:tap];

    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.backgroundColor = UIColor.whiteColor;
    card.layer.cornerRadius = 24;
    card.clipsToBounds = YES;
    [self.view addSubview:card];
    UIScrollView *scroll = [[UIScrollView alloc] init];
    scroll.translatesAutoresizingMaskIntoConstraints = NO;
    scroll.keyboardDismissMode = UIScrollViewKeyboardDismissModeInteractive;
    [card addSubview:scroll];
    UIStackView *stack = [[UIStackView alloc] init];
    stack.translatesAutoresizingMaskIntoConstraints = NO;
    stack.axis = UILayoutConstraintAxisVertical;
    stack.spacing = 12;
    [scroll addSubview:stack];
    UILabel *title = [[UILabel alloc] init];
    title.text = @"创建演示";
    title.font = [UIFont boldSystemFontOfSize:18];
    title.textAlignment = NSTextAlignmentCenter;
    UIButton *close = [UIButton buttonWithType:UIButtonTypeSystem];
    [close setTitle:@"✕" forState:UIControlStateNormal];
    [close setTitleColor:UIColor.labelColor forState:UIControlStateNormal];
    [close.widthAnchor constraintEqualToConstant:40].active = YES;
    [close addTarget:self action:@selector(dismissSheet) forControlEvents:UIControlEventTouchUpInside];
    UIView *spacer = [[UIView alloc] init];
    [spacer.widthAnchor constraintEqualToConstant:40].active = YES;
    UIStackView *header = [[UIStackView alloc] initWithArrangedSubviews:@[spacer, title, close]];
    [header.heightAnchor constraintEqualToConstant:40].active = YES;
    [stack addArrangedSubview:header];
    UILabel *name = [[UILabel alloc] init];
    name.text = @"标题";
    name.font = [UIFont systemFontOfSize:16];
    [stack addArrangedSubview:name];
    self.fileNameField = [self borderedFieldWithPlaceholder:@"请输入文件名称"];
    self.fileNameField.text = @"演示";
    self.fileNameField.delegate = self;
    [self.fileNameField.heightAnchor constraintEqualToConstant:44].active = YES;
    [stack addArrangedSubview:self.fileNameField];

    UIStackView *aiCard = [[UIStackView alloc] init];
    aiCard.axis = UILayoutConstraintAxisVertical;
    aiCard.spacing = 8;
    aiCard.backgroundColor = [UIColor colorWithWhite:0.95 alpha:1];
    aiCard.layer.cornerRadius = 12;
    aiCard.layoutMarginsRelativeArrangement = YES;
    aiCard.layoutMargins = UIEdgeInsetsMake(10, 10, 10, 10);
    UILabel *aiLabel = [[UILabel alloc] init];
    aiLabel.text = @"✨ 内容使用AI生成";
    aiLabel.font = [UIFont systemFontOfSize:14];
    self.impressAiSwitch = [[UISwitch alloc] init];
    self.impressAiSwitch.on = YES;
    self.impressAiSwitch.onTintColor = [UIColor colorWithRed:0.07 green:0.47 blue:0.85 alpha:1];
    [self.impressAiSwitch addTarget:self action:@selector(impressAiChanged) forControlEvents:UIControlEventValueChanged];
    UIStackView *aiHeader = [[UIStackView alloc] initWithArrangedSubviews:@[aiLabel, self.impressAiSwitch]];
    aiHeader.alignment = UIStackViewAlignmentCenter;
    [aiCard addArrangedSubview:aiHeader];

    self.impressOptionsView = [[UIStackView alloc] init];
    self.impressOptionsView.axis = UILayoutConstraintAxisVertical;
    self.impressOptionsView.spacing = 8;
    UIButton *mode = [self impressOptionButton:@"快速生成" tag:0];
    mode.backgroundColor = UIColor.whiteColor;
    [self.impressOptionsView addArrangedSubview:mode];
    self.impressSourceButton = [UIButton buttonWithType:UIButtonTypeSystem];
    [self.impressSourceButton setTitle:@"选择 TXT / DOCX 文件" forState:UIControlStateNormal];
    self.impressSourceButton.titleLabel.font = [UIFont systemFontOfSize:13];
    self.impressSourceButton.hidden = YES;
    [self.impressSourceButton.heightAnchor constraintEqualToConstant:36].active = YES;
    [self.impressSourceButton addTarget:self action:@selector(pickImpressSource) forControlEvents:UIControlEventTouchUpInside];
    [self.impressOptionsView addArrangedSubview:self.impressSourceButton];

    UIView *inputCard = [[UIView alloc] init];
    inputCard.backgroundColor = UIColor.whiteColor;
    inputCard.layer.cornerRadius = 10;
    self.impressPromptView = [[UITextView alloc] init];
    self.impressPromptView.translatesAutoresizingMaskIntoConstraints = NO;
    self.impressPromptView.font = [UIFont systemFontOfSize:14];
    self.impressPromptView.backgroundColor = UIColor.clearColor;
    self.impressPromptView.delegate = self;
    self.impressPromptView.textContainerInset = UIEdgeInsetsMake(10, 6, 10, 6);
    self.impressPromptView.accessibilityLabel = @"PPT主题和要求";
    [inputCard addSubview:self.impressPromptView];
    self.impressPlaceholder = [[UILabel alloc] init];
    self.impressPlaceholder.translatesAutoresizingMaskIntoConstraints = NO;
    self.impressPlaceholder.text = @"请输入PPT主题和要求";
    self.impressPlaceholder.textColor = UIColor.placeholderTextColor;
    self.impressPlaceholder.font = [UIFont systemFontOfSize:14];
    self.impressPlaceholder.userInteractionEnabled = NO;
    [inputCard addSubview:self.impressPlaceholder];
    UIButton *pages = [self impressOptionButton:@"11-20页" tag:1];
    UIButton *audience = [self impressOptionButton:@"大众" tag:2];
    UIButton *style = [self impressOptionButton:@"学术报告" tag:3];
    UIStackView *pills = [[UIStackView alloc] initWithArrangedSubviews:@[pages, audience, style]];
    pills.translatesAutoresizingMaskIntoConstraints = NO;
    pills.distribution = UIStackViewDistributionFillEqually;
    pills.spacing = 6;
    [inputCard addSubview:pills];
    [NSLayoutConstraint activateConstraints:@[
        [self.impressPromptView.topAnchor constraintEqualToAnchor:inputCard.topAnchor],
        [self.impressPromptView.leadingAnchor constraintEqualToAnchor:inputCard.leadingAnchor],
        [self.impressPromptView.trailingAnchor constraintEqualToAnchor:inputCard.trailingAnchor],
        [self.impressPromptView.heightAnchor constraintEqualToConstant:82],
        [self.impressPlaceholder.topAnchor constraintEqualToAnchor:inputCard.topAnchor constant:10],
        [self.impressPlaceholder.leadingAnchor constraintEqualToAnchor:inputCard.leadingAnchor constant:11],
        [pills.topAnchor constraintEqualToAnchor:self.impressPromptView.bottomAnchor constant:4],
        [pills.leadingAnchor constraintEqualToAnchor:inputCard.leadingAnchor constant:8],
        [pills.trailingAnchor constraintEqualToAnchor:inputCard.trailingAnchor constant:-8],
        [pills.bottomAnchor constraintEqualToAnchor:inputCard.bottomAnchor constant:-8],
    ]];
    [self.impressOptionsView addArrangedSubview:inputCard];
    [aiCard addArrangedSubview:self.impressOptionsView];
    [stack addArrangedSubview:aiCard];
    self.impressCreateButton = [UIButton buttonWithType:UIButtonTypeSystem];
    [self.impressCreateButton setTitle:@"生成和创建" forState:UIControlStateNormal];
    [self.impressCreateButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    self.impressCreateButton.titleLabel.font = [UIFont boldSystemFontOfSize:16];
    self.impressCreateButton.layer.cornerRadius = 8;
    self.impressCreateButton.clipsToBounds = YES;
    [self.impressCreateButton.heightAnchor constraintEqualToConstant:44].active = YES;
    [self.impressCreateButton addTarget:self action:@selector(createTapped) forControlEvents:UIControlEventTouchUpInside];
    self.impressGradient = [CAGradientLayer layer];
    self.impressGradient.colors = @[(id)[UIColor colorWithRed:0.26 green:0.55 blue:1 alpha:1].CGColor,
                                   (id)[UIColor colorWithRed:0.55 green:0.31 blue:1 alpha:1].CGColor,
                                   (id)[UIColor colorWithRed:0.9 green:0 blue:0.92 alpha:1].CGColor];
    self.impressGradient.startPoint = CGPointMake(0, 0.5);
    self.impressGradient.endPoint = CGPointMake(1, 0.5);
    [self.impressCreateButton.layer insertSublayer:self.impressGradient atIndex:0];
    [stack addArrangedSubview:self.impressCreateButton];
    self.cardBottomConstraint = [card.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor];
    NSLayoutConstraint *width = [card.widthAnchor constraintEqualToAnchor:self.view.widthAnchor];
    width.priority = 750;
    NSLayoutConstraint *height = [card.heightAnchor constraintEqualToAnchor:stack.heightAnchor constant:28];
    height.priority = 749;
    [NSLayoutConstraint activateConstraints:@[
        [card.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor], width, height,
        [card.widthAnchor constraintLessThanOrEqualToConstant:540],
        [card.widthAnchor constraintLessThanOrEqualToAnchor:self.view.widthAnchor],
        [card.topAnchor constraintGreaterThanOrEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:8],
        self.cardBottomConstraint,
        [scroll.topAnchor constraintEqualToAnchor:card.topAnchor],
        [scroll.leadingAnchor constraintEqualToAnchor:card.leadingAnchor],
        [scroll.trailingAnchor constraintEqualToAnchor:card.trailingAnchor],
        [scroll.bottomAnchor constraintEqualToAnchor:card.safeAreaLayoutGuide.bottomAnchor],
        [stack.topAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.topAnchor constant:8],
        [stack.leadingAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.leadingAnchor constant:16],
        [stack.trailingAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.trailingAnchor constant:-16],
        [stack.widthAnchor constraintEqualToAnchor:scroll.frameLayoutGuide.widthAnchor constant:-32],
        [stack.bottomAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.bottomAnchor constant:-20],
    ]];
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    self.impressGradient.frame = self.impressCreateButton.bounds;
}

- (void)impressAiChanged {
    self.aiSwitchOn = self.impressAiSwitch.isOn;
    self.impressOptionsView.hidden = !self.aiSwitchOn;
    self.impressGradient.hidden = !self.aiSwitchOn;
    self.impressCreateButton.backgroundColor = self.aiSwitchOn ? UIColor.clearColor : CreateFileButtonColorForKind(self.docKind);
    [self.impressCreateButton setTitle:self.aiSwitchOn ? @"生成和创建" : @"创建演示" forState:UIControlStateNormal];
}

- (void)textViewDidChange:(UITextView *)textView {
    self.impressPlaceholder.hidden = textView.text.length > 0;
}

- (BOOL)textView:(UITextView *)textView shouldChangeTextInRange:(NSRange)range replacementText:(NSString *)text {
    return textView.text.length - range.length + text.length <= 150000;
}

- (void)pickImpressOption:(UIButton *)button {
    NSArray *groups = @[@[@"快速生成", @"粘贴大纲", @"文档生成"],
                        @[@"1-10页", @"11-20页", @"21-30页", @"30+页"],
                        @[@"大众", @"投资者", @"学生", @"教师", @"领导"],
                        @[@"通用", @"行业调研", @"市场推广", @"工作汇报", @"学术报告", @"教学课件"]];
    UIAlertController *picker = [UIAlertController alertControllerWithTitle:nil message:nil preferredStyle:UIAlertControllerStyleActionSheet];
    [groups[button.tag] enumerateObjectsUsingBlock:^(NSString *title, NSUInteger index, BOOL *stop) {
        [picker addAction:[UIAlertAction actionWithTitle:title style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
            if (button.tag == 0) {
                self.impressMode = index;
                self.impressPromptView.text = @"";
                self.impressSourceButton.hidden = index != 2;
                self.impressPlaceholder.text = index == 0 ? @"请输入PPT主题和要求" : index == 1 ? @"粘贴已有大纲文本…" : @"选择来源文档后可编辑正文";
                [self textViewDidChange:self.impressPromptView];
            } else if (button.tag == 1) self.impressPage = index;
            else if (button.tag == 2) self.impressAudience = index;
            else self.impressStyle = index;
            [button setTitle:[title stringByAppendingString:@"  ⌄"] forState:UIControlStateNormal];
        }]];
    }];
    [picker addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];
    picker.popoverPresentationController.sourceView = button;
    picker.popoverPresentationController.sourceRect = button.bounds;
    [self presentViewController:picker animated:YES completion:nil];
}

- (void)pickImpressSource {
    UIDocumentPickerViewController *picker = [[UIDocumentPickerViewController alloc]
        initForOpeningContentTypes:@[UTTypePlainText, [UTType typeWithFilenameExtension:@"docx"]] asCopy:YES];
    picker.delegate = self;
    picker.allowsMultipleSelection = NO;
    [self presentViewController:picker animated:YES completion:nil];
}

- (void)documentPicker:(UIDocumentPickerViewController *)controller didPickDocumentsAtURLs:(NSArray<NSURL *> *)urls {
    NSURL *url = urls.firstObject;
    if (!url) return;
    self.impressSourceLoading = YES;
    [self.impressSourceButton setTitle:@"正在读取文档…" forState:UIControlStateNormal];
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        BOOL scoped = [url startAccessingSecurityScopedResource];
        NSString *text = @"";
        if ([url.pathExtension.lowercaseString isEqualToString:@"docx"]) {
            NSDictionary *extracted = [TypesetService extractStructuredFromFile:url];
            text = [extracted[@"fullText"] isKindOfClass:NSString.class] ? extracted[@"fullText"] : @"";
        } else text = [NSString stringWithContentsOfURL:url encoding:NSUTF8StringEncoding error:nil] ?: @"";
        if (scoped) [url stopAccessingSecurityScopedResource];
        NSString *content = [text substringToIndex:MIN(text.length, 150000)];
        dispatch_async(dispatch_get_main_queue(), ^{
            self.impressSourceLoading = NO;
            if (self.impressMode != 2) return;
            self.impressPromptView.text = content;
            [self textViewDidChange:self.impressPromptView];
            [self.impressSourceButton setTitle:content.length > 0 ? url.lastPathComponent : @"读取失败，请选择 TXT / DOCX 文件" forState:UIControlStateNormal];
        });
    });
}

@end
