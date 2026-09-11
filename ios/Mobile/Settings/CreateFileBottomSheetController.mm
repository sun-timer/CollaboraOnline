// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "CreateFileBottomSheetController.h"

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

@interface CreateFileBottomSheetController ()
@property (assign, nonatomic) CreateFileDocKind docKind;
@property (copy, nonatomic) void (^completion)(NSString *, BOOL, NSString *);
@property (strong, nonatomic) UITextField *fileNameField;
@property (strong, nonatomic) UITextField *aiPromptField;
@property (assign, nonatomic) BOOL aiSwitchOn;
@property (strong, nonatomic) UIView *aiSwitchTrack;
@property (strong, nonatomic) UIView *aiSwitchThumb;
@property (strong, nonatomic) NSLayoutConstraint *aiSwitchThumbLeading;
@property (strong, nonatomic) NSLayoutConstraint *aiSwitchThumbTrailing;
@property (strong, nonatomic) NSLayoutConstraint *aiPromptHeight;
@property (strong, nonatomic) NSLayoutConstraint *createButtonTopToPrompt;
@end

@implementation CreateFileBottomSheetController

- (instancetype)initWithDocKind:(CreateFileDocKind)docKind
                     completion:(void (^)(NSString *, BOOL, NSString *))completion {
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

    UITapGestureRecognizer *dismissTap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(dismissSheet)];
    [self.view addGestureRecognizer:dismissTap];

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

    [NSLayoutConstraint activateConstraints:@[
        [card.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [card.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [card.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],

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
    [self dismissViewControllerAnimated:YES completion:nil];
}

- (void)createTapped {
    NSString *name = [self.fileNameField.text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (name.length == 0) {
        self.fileNameField.layer.borderColor = [UIColor colorWithRed:0xD9 / 255.0 green:0x30 / 255.0 blue:0x25 / 255.0 alpha:1].CGColor;
        return;
    }
    NSString *desc = [self.aiPromptField.text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (self.aiSwitchOn && self.docKind == CreateFileDocKindCalc && desc.length == 0) {
        self.aiPromptField.layer.borderColor = [UIColor colorWithRed:0xD9 / 255.0 green:0x30 / 255.0 blue:0x25 / 255.0 alpha:1].CGColor;
        return;
    }
    BOOL aiOn = self.aiSwitchOn;
    void (^block)(NSString *, BOOL, NSString *) = self.completion;
    [self dismissViewControllerAnimated:YES completion:^{
        if (block) {
            block(name, aiOn, desc);
        }
    }];
}

@end
