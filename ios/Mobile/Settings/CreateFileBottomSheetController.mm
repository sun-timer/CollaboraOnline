// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "CreateFileBottomSheetController.h"
#import "../KeyboardAccessory.h"
#import "../Typeset/TypesetService.h"
#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>
#import <PDFKit/PDFKit.h>

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

static UIColor *ImpressTextColor(void) {
    return [UIColor colorWithRed:0x1F / 255.0 green:0x23 / 255.0 blue:0x29 / 255.0 alpha:1];
}

static UIColor *ImpressHintColor(void) {
    return [UIColor colorWithRed:0x9A / 255.0 green:0xA0 / 255.0 blue:0xA8 / 255.0 alpha:1];
}

static UIColor *ImpressCardColor(void) {
    return [UIColor colorWithRed:0xF5 / 255.0 green:0xF6 / 255.0 blue:0xF8 / 255.0 alpha:1];
}

static UIColor *ImpressChipColor(void) {
    return [UIColor colorWithRed:0xF3 / 255.0 green:0xF4 / 255.0 blue:0xF6 / 255.0 alpha:1];
}

static UIColor *ImpressSwitchOnColor(void) {
    return [UIColor colorWithRed:0x12 / 255.0 green:0x78 / 255.0 blue:0xD9 / 255.0 alpha:1];
}

static UIColor *ImpressSparkleColor(void) {
    return [UIColor colorWithRed:0x7C / 255.0 green:0x5C / 255.0 blue:0xF5 / 255.0 alpha:1];
}

static UIColor *ImpressErrorColor(void) {
    return [UIColor colorWithRed:0xD9 / 255.0 green:0x30 / 255.0 blue:0x25 / 255.0 alpha:1];
}

static void ImpressStyleGradient(CAGradientLayer *layer) {
    layer.colors = @[(id)[UIColor colorWithRed:0.26 green:0.55 blue:1 alpha:1].CGColor,
                     (id)[UIColor colorWithRed:0.55 green:0.31 blue:1 alpha:1].CGColor,
                     (id)[UIColor colorWithRed:0.9 green:0 blue:0.92 alpha:1].CGColor];
    layer.startPoint = CGPointMake(0, 0.5);
    layer.endPoint = CGPointMake(1, 0.5);
}

static NSString *ImpressModeTitle(NSInteger mode) {
    NSArray<NSString *> *titles = @[@"快速生成", @"文档生成", @"粘贴大纲"];
    return titles[MAX(0, MIN((NSInteger)titles.count - 1, mode))];
}

static NSString *ImpressTrimmed(NSString *text) {
    return [text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]] ?: @"";
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
@property (strong, nonatomic) UIButton *createButton;
@property (strong, nonatomic) CAGradientLayer *createGradient;
@property (strong, nonatomic) NSLayoutConstraint *cardBottomConstraint;
@property (strong, nonatomic) UITextView *impressPromptView;
@property (strong, nonatomic) UILabel *impressPlaceholder;
@property (strong, nonatomic) UIView *impressInputBox;
@property (strong, nonatomic) UIButton *impressModeButton;
@property (strong, nonatomic) UIView *impressDocZone;
@property (strong, nonatomic) UILabel *impressDocTitle;
@property (strong, nonatomic) UILabel *impressDocHint;
@property (strong, nonatomic) CAShapeLayer *impressDocBorder;
@property (strong, nonatomic) UIStackView *impressOptionsView;
@property (strong, nonatomic) UISwitch *impressAiSwitch;
@property (strong, nonatomic) UIButton *impressCreateButton;
@property (strong, nonatomic) CAGradientLayer *impressGradient;
@property (strong, nonatomic) UIView *impressPasteBox;
@property (strong, nonatomic) UITextView *impressPasteView;
@property (strong, nonatomic) UILabel *impressPasteHint;
@property (copy, nonatomic) NSString *impressPasteText;
@property (copy, nonatomic) NSString *impressDocContent;
@property (copy, nonatomic) NSString *impressDocName;
@property (assign, nonatomic) NSInteger impressMode;
@property (assign, nonatomic) NSInteger impressPage;
@property (assign, nonatomic) NSInteger impressAudience;
@property (assign, nonatomic) NSInteger impressStyle;
@property (assign, nonatomic) BOOL impressSourceLoading;
@property (assign, nonatomic) NSUInteger impressSourceGeneration;
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
            return @"创建文档";
    }
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    if (self.docKind == CreateFileDocKindWriter) {
        self.aiSwitchOn = NO;
        if (self.isViewLoaded) {
            self.aiPromptField.hidden = YES;
            self.aiPromptHeight.constant = 0;
            [self updateAiSwitchUi];
        }
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

    // A newly presented Calc sheet always starts in blank-sheet mode.
    if (self.docKind == CreateFileDocKindCalc) {
        self.aiSwitchOn = NO;
    }

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

    self.createButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.createButton.translatesAutoresizingMaskIntoConstraints = NO;
    self.createButton.backgroundColor = CreateFileButtonColorForKind(self.docKind);
    self.createButton.layer.cornerRadius = 8;
    self.createButton.clipsToBounds = YES;
    [self.createButton setTitle:[self createButtonTitle] forState:UIControlStateNormal];
    [self.createButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    self.createButton.titleLabel.font = [UIFont boldSystemFontOfSize:18];
    [self.createButton addTarget:self action:@selector(createTapped) forControlEvents:UIControlEventTouchUpInside];
    if (self.docKind == CreateFileDocKindWriter || self.docKind == CreateFileDocKindCalc) {
        self.createGradient = [CAGradientLayer layer];
        ImpressStyleGradient(self.createGradient);
        [self.createButton.layer insertSublayer:self.createGradient atIndex:0];
    }

    for (UIView *v in @[ card, close, title, nameLabel, self.fileNameField, aiRow, aiLabel,
                         self.aiSwitchTrack, self.aiPromptField, self.createButton ]) {
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
    self.createButtonTopToPrompt = [self.createButton.topAnchor constraintEqualToAnchor:self.aiPromptField.bottomAnchor constant:20];
    self.createButtonTopToPrompt.active = YES;
    [NSLayoutConstraint activateConstraints:@[
        [self.createButton.leadingAnchor constraintEqualToAnchor:self.fileNameField.leadingAnchor],
        [self.createButton.trailingAnchor constraintEqualToAnchor:self.fileNameField.trailingAnchor],
        [self.createButton.heightAnchor constraintEqualToConstant:44],
        [self.createButton.bottomAnchor constraintEqualToAnchor:card.safeAreaLayoutGuide.bottomAnchor constant:-24],
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
    [KeyboardAccessory installOnInput:field];
    return field;
}

- (void)toggleAiSwitch {
    self.aiSwitchOn = !self.aiSwitchOn;
    if (self.aiSwitchOn && self.docKind == CreateFileDocKindCalc) {
        self.aiPromptField.placeholder = @"描述你想生成的表格内容，例如：2024年各季度销售数据表";
    } else if (self.aiSwitchOn) {
        self.aiPromptField.placeholder = @"描述你想生成的文档内容...";
    }
    if (!self.aiSwitchOn && self.docKind == CreateFileDocKindCalc) {
        [self.aiPromptField resignFirstResponder];
    }
    [self updateAiSwitchUi];
}

- (void)updateAiSwitchUi {
    self.aiSwitchThumbLeading.active = !self.aiSwitchOn;
    self.aiSwitchThumbTrailing.active = self.aiSwitchOn;
    self.aiPromptField.hidden = !self.aiSwitchOn;
    self.aiPromptHeight.constant = self.aiSwitchOn ? 44 : 0;
    if (self.docKind == CreateFileDocKindCalc) {
        self.aiSwitchTrack.backgroundColor = self.aiSwitchOn
            ? CreateFileButtonColorForKind(CreateFileDocKindCalc)
            : [UIColor colorWithRed:0xCB / 255.0 green:0xD1 / 255.0 blue:0xD7 / 255.0 alpha:1];
    } else if (self.docKind == CreateFileDocKindWriter) {
        self.aiSwitchTrack.backgroundColor = self.aiSwitchOn
            ? ImpressSwitchOnColor()
            : [UIColor colorWithRed:0xCB / 255.0 green:0xD1 / 255.0 blue:0xD7 / 255.0 alpha:1];
    }
    if (self.docKind == CreateFileDocKindWriter || self.docKind == CreateFileDocKindCalc) {
        self.createGradient.hidden = !self.aiSwitchOn;
        self.createButton.backgroundColor = self.aiSwitchOn ? UIColor.clearColor : CreateFileButtonColorForKind(self.docKind);
        [self.createButton setTitle:self.aiSwitchOn ? @"生成和创建" : [self createButtonTitle] forState:UIControlStateNormal];
    }
    [self.view layoutIfNeeded];
}

- (void)dismissSheet {
    ++self.impressSourceGeneration;
    [self.view endEditing:YES];
    [self dismissViewControllerAnimated:YES completion:nil];
}

- (void)createTapped {
    NSString *name = [self.fileNameField.text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (name.length == 0) {
        self.fileNameField.layer.borderColor = [UIColor colorWithRed:0xD9 / 255.0 green:0x30 / 255.0 blue:0x25 / 255.0 alpha:1].CGColor;
        return;
    }
    if (self.docKind == CreateFileDocKindImpress) {
        [self createImpressPresentationWithName:name];
        return;
    }
    NSString *desc = [self.aiPromptField.text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]] ?: @"";
    if (self.aiSwitchOn && self.docKind == CreateFileDocKindCalc && desc.length == 0) {
        self.aiPromptField.layer.borderColor = [UIColor colorWithRed:0xD9 / 255.0 green:0x30 / 255.0 blue:0x25 / 255.0 alpha:1].CGColor;
        return;
    }
    BOOL aiOn = self.aiSwitchOn;
    void (^block)(NSString *, BOOL, NSString *, NSDictionary *) = self.completion;
    [self dismissViewControllerAnimated:YES completion:^{
        if (block) {
            block(name, aiOn, desc, @{});
        }
    }];
}

- (UIButton *)impressDropdownButtonWithTitle:(NSString *)title
                                         tag:(NSInteger)tag
                                      height:(CGFloat)height
                                    fontSize:(CGFloat)fontSize
                                  background:(UIColor *)background {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    button.tag = tag;
    button.backgroundColor = background;
    button.layer.cornerRadius = 8;
    button.titleLabel.font = [UIFont systemFontOfSize:fontSize];
    button.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeading;
    button.contentEdgeInsets = UIEdgeInsetsMake(0, 12, 0, 26);
    [button setTitle:title forState:UIControlStateNormal];
    [button setTitleColor:ImpressTextColor() forState:UIControlStateNormal];
    [button.heightAnchor constraintEqualToConstant:height].active = YES;

    UIImageView *chevron = [[UIImageView alloc] initWithImage:
        [[UIImage systemImageNamed:@"chevron.down"] imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate]];
    chevron.tintColor = [UIColor colorWithWhite:0.45 alpha:1];
    chevron.contentMode = UIViewContentModeScaleAspectFit;
    chevron.translatesAutoresizingMaskIntoConstraints = NO;
    chevron.userInteractionEnabled = NO;
    [button addSubview:chevron];
    [NSLayoutConstraint activateConstraints:@[
        [chevron.trailingAnchor constraintEqualToAnchor:button.trailingAnchor constant:-10],
        [chevron.centerYAnchor constraintEqualToAnchor:button.centerYAnchor],
        [chevron.widthAnchor constraintEqualToConstant:12],
        [chevron.heightAnchor constraintEqualToConstant:12],
    ]];
    [button addTarget:self action:@selector(pickImpressOption:) forControlEvents:UIControlEventTouchUpInside];
    return button;
}

- (UIView *)impressHeaderWithTitle:(NSString *)titleText closeAction:(SEL)closeAction {
    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = titleText;
    title.font = [UIFont systemFontOfSize:17 weight:UIFontWeightSemibold];
    title.textColor = ImpressTextColor();
    title.textAlignment = NSTextAlignmentCenter;

    UIButton *close = [UIButton buttonWithType:UIButtonTypeCustom];
    close.translatesAutoresizingMaskIntoConstraints = NO;
    [close setImage:[[UIImage systemImageNamed:@"xmark"] imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate]
           forState:UIControlStateNormal];
    close.tintColor = ImpressTextColor();
    [close addTarget:self action:closeAction forControlEvents:UIControlEventTouchUpInside];

    UIView *header = [[UIView alloc] init];
    header.translatesAutoresizingMaskIntoConstraints = NO;
    [header addSubview:title];
    [header addSubview:close];
    [NSLayoutConstraint activateConstraints:@[
        [header.heightAnchor constraintEqualToConstant:44],
        [title.centerXAnchor constraintEqualToAnchor:header.centerXAnchor],
        [title.centerYAnchor constraintEqualToAnchor:header.centerYAnchor],
        [close.trailingAnchor constraintEqualToAnchor:header.trailingAnchor constant:-8],
        [close.centerYAnchor constraintEqualToAnchor:header.centerYAnchor],
        [close.widthAnchor constraintEqualToConstant:44],
        [close.heightAnchor constraintEqualToConstant:44],
    ]];
    return header;
}

- (void)buildImpressCreationUi {
    self.impressPage = 1;
    self.impressStyle = 4;
    self.impressAudience = 0;
    self.impressMode = 0;
    // "内容使用AI生成" defaults OFF on iOS: the sheet only creates a blank deck until the user opts in.
    self.aiSwitchOn = NO;

    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(keyboardWillChangeFrame:)
                                                 name:UIKeyboardWillChangeFrameNotification
                                               object:nil];
    UITapGestureRecognizer *tap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(dismissSheet)];
    tap.delegate = self;
    tap.cancelsTouchesInView = NO;
    [self.view addGestureRecognizer:tap];

    // Same presentation as Writer/Calc: dimmed overlay + content-height card
    // pinned to the bottom. Do not pin the card to the top — that stretched
    // Impress into a full-screen page with a large empty region under the button.
    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.backgroundColor = UIColor.whiteColor;
    card.layer.cornerRadius = 24;
    card.layer.maskedCorners = kCALayerMinXMinYCorner | kCALayerMaxXMinYCorner;
    card.clipsToBounds = YES;
    [self.view addSubview:card];

    // Header: 创建演示 (centered) with close on the trailing edge.
    UIView *header = [self impressHeaderWithTitle:@"创建演示" closeAction:@selector(dismissSheet)];

    UILabel *nameLabel = [[UILabel alloc] init];
    nameLabel.text = @"标题";
    nameLabel.font = [UIFont systemFontOfSize:16 weight:UIFontWeightMedium];
    nameLabel.textColor = ImpressTextColor();

    self.fileNameField = [self borderedFieldWithPlaceholder:@"请输入文件名称"];
    self.fileNameField.delegate = self;
    self.fileNameField.returnKeyType = UIReturnKeyDone;
    [self.fileNameField.heightAnchor constraintEqualToConstant:44].active = YES;

    // AI card: sparkle + label + switch (row), then the mode/input options when the switch is ON.
    UIImageView *sparkle = [[UIImageView alloc] initWithImage:
        [[UIImage systemImageNamed:@"sparkles"] imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate]];
    sparkle.tintColor = ImpressSparkleColor();
    sparkle.contentMode = UIViewContentModeScaleAspectFit;
    [sparkle.widthAnchor constraintEqualToConstant:18].active = YES;
    [sparkle.heightAnchor constraintEqualToConstant:18].active = YES;

    UILabel *aiLabel = [[UILabel alloc] init];
    aiLabel.text = @"内容使用AI生成";
    aiLabel.font = [UIFont systemFontOfSize:15];
    aiLabel.textColor = ImpressTextColor();

    self.impressAiSwitch = [[UISwitch alloc] init];
    self.impressAiSwitch.on = NO;
    self.impressAiSwitch.onTintColor = ImpressSwitchOnColor();
    self.impressAiSwitch.transform = CGAffineTransformMakeScale(0.85, 0.85);
    [self.impressAiSwitch addTarget:self action:@selector(impressAiChanged) forControlEvents:UIControlEventValueChanged];

    UIView *aiSpacer = [[UIView alloc] init];
    [aiSpacer setContentHuggingPriority:UILayoutPriorityDefaultLow forAxis:UILayoutConstraintAxisHorizontal];
    UIStackView *aiRow = [[UIStackView alloc] initWithArrangedSubviews:@[sparkle, aiLabel, aiSpacer, self.impressAiSwitch]];
    aiRow.axis = UILayoutConstraintAxisHorizontal;
    aiRow.spacing = 6;
    aiRow.alignment = UIStackViewAlignmentCenter;
    [aiRow.heightAnchor constraintEqualToConstant:44].active = YES;

    // Mode dropdown (快速生成 / 文档生成 / 粘贴大纲).
    self.impressModeButton = [self impressDropdownButtonWithTitle:ImpressModeTitle(self.impressMode)
                                                              tag:0
                                                           height:50
                                                         fontSize:16
                                                       background:UIColor.whiteColor];
    self.impressModeButton.layer.borderWidth = 1;
    self.impressModeButton.layer.borderColor = [UIColor colorWithRed:0xE3 / 255.0 green:0xE6 / 255.0 blue:0xEB / 255.0 alpha:1].CGColor;

    // Quick input box: multi-line prompt with the three pills inside the bottom edge.
    self.impressInputBox = [[UIView alloc] init];
    self.impressInputBox.backgroundColor = UIColor.whiteColor;
    self.impressInputBox.layer.cornerRadius = 8;
    self.impressInputBox.clipsToBounds = YES;

    self.impressPromptView = [[UITextView alloc] init];
    [KeyboardAccessory installOnInput:self.impressPromptView];
    self.impressPromptView.translatesAutoresizingMaskIntoConstraints = NO;
    self.impressPromptView.font = [UIFont systemFontOfSize:15];
    self.impressPromptView.backgroundColor = UIColor.clearColor;
    self.impressPromptView.textColor = ImpressTextColor();
    self.impressPromptView.delegate = self;
    self.impressPromptView.textContainerInset = UIEdgeInsetsMake(10, 8, 0, 8);
    self.impressPromptView.accessibilityLabel = @"PPT主题和要求";
    [self.impressInputBox addSubview:self.impressPromptView];

    self.impressPlaceholder = [[UILabel alloc] init];
    self.impressPlaceholder.translatesAutoresizingMaskIntoConstraints = NO;
    self.impressPlaceholder.text = @"请输入PPT主题和要求";
    self.impressPlaceholder.textColor = ImpressHintColor();
    self.impressPlaceholder.font = [UIFont systemFontOfSize:15];
    self.impressPlaceholder.userInteractionEnabled = NO;
    [self.impressInputBox addSubview:self.impressPlaceholder];

    UIButton *pages = [self impressDropdownButtonWithTitle:@"11-20页" tag:1 height:30 fontSize:13 background:ImpressChipColor()];
    UIButton *audience = [self impressDropdownButtonWithTitle:@"大众" tag:2 height:30 fontSize:13 background:ImpressChipColor()];
    UIButton *style = [self impressDropdownButtonWithTitle:@"学术报告" tag:3 height:30 fontSize:13 background:ImpressChipColor()];
    UIStackView *pills = [[UIStackView alloc] initWithArrangedSubviews:@[pages, audience, style]];
    pills.translatesAutoresizingMaskIntoConstraints = NO;
    pills.axis = UILayoutConstraintAxisHorizontal;
    pills.distribution = UIStackViewDistributionFillEqually;
    pills.spacing = 10;
    [self.impressInputBox addSubview:pills];

    [NSLayoutConstraint activateConstraints:@[
        [self.impressInputBox.heightAnchor constraintEqualToConstant:113],
        [self.impressPromptView.topAnchor constraintEqualToAnchor:self.impressInputBox.topAnchor],
        [self.impressPromptView.leadingAnchor constraintEqualToAnchor:self.impressInputBox.leadingAnchor],
        [self.impressPromptView.trailingAnchor constraintEqualToAnchor:self.impressInputBox.trailingAnchor],
        [self.impressPromptView.bottomAnchor constraintEqualToAnchor:pills.topAnchor constant:-4],
        [self.impressPlaceholder.topAnchor constraintEqualToAnchor:self.impressInputBox.topAnchor constant:10],
        [self.impressPlaceholder.leadingAnchor constraintEqualToAnchor:self.impressInputBox.leadingAnchor constant:13],
        [pills.leadingAnchor constraintEqualToAnchor:self.impressInputBox.leadingAnchor constant:10],
        [pills.trailingAnchor constraintEqualToAnchor:self.impressInputBox.trailingAnchor constant:-10],
        [pills.bottomAnchor constraintEqualToAnchor:self.impressInputBox.bottomAnchor constant:-11],
    ]];

    // Document mode: dashed drop zone that opens the file picker.
    self.impressDocZone = [[UIView alloc] init];
    self.impressDocZone.userInteractionEnabled = YES;
    [self.impressDocZone.heightAnchor constraintEqualToConstant:113].active = YES;
    self.impressDocBorder = [CAShapeLayer layer];
    self.impressDocBorder.fillColor = UIColor.clearColor.CGColor;
    self.impressDocBorder.strokeColor = [UIColor colorWithRed:0xC4 / 255.0 green:0xC9 / 255.0 blue:0xD1 / 255.0 alpha:1].CGColor;
    self.impressDocBorder.lineWidth = 1;
    self.impressDocBorder.lineDashPattern = @[@5, @4];
    [self.impressDocZone.layer addSublayer:self.impressDocBorder];
    UITapGestureRecognizer *docTap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(pickImpressSource)];
    [self.impressDocZone addGestureRecognizer:docTap];

    self.impressDocTitle = [[UILabel alloc] init];
    self.impressDocTitle.font = [UIFont systemFontOfSize:16];
    self.impressDocTitle.textColor = ImpressTextColor();
    self.impressDocTitle.textAlignment = NSTextAlignmentCenter;
    self.impressDocTitle.lineBreakMode = NSLineBreakByTruncatingMiddle;

    self.impressDocHint = [[UILabel alloc] init];
    self.impressDocHint.font = [UIFont systemFontOfSize:12];
    self.impressDocHint.textColor = ImpressHintColor();
    self.impressDocHint.textAlignment = NSTextAlignmentCenter;
    self.impressDocHint.numberOfLines = 0;

    UIStackView *docStack = [[UIStackView alloc] initWithArrangedSubviews:@[self.impressDocTitle, self.impressDocHint]];
    docStack.axis = UILayoutConstraintAxisVertical;
    docStack.alignment = UIStackViewAlignmentFill;
    docStack.spacing = 8;
    docStack.translatesAutoresizingMaskIntoConstraints = NO;
    [self.impressDocZone addSubview:docStack];
    [NSLayoutConstraint activateConstraints:@[
        [docStack.centerYAnchor constraintEqualToAnchor:self.impressDocZone.centerYAnchor],
        [docStack.leadingAnchor constraintEqualToAnchor:self.impressDocZone.leadingAnchor constant:16],
        [docStack.trailingAnchor constraintEqualToAnchor:self.impressDocZone.trailingAnchor constant:-16],
    ]];
    [self updateImpressDocZoneUi];

    // Paste-outline stays on this same sheet (not a second modal).
    self.impressPasteBox = [[UIView alloc] init];
    self.impressPasteBox.backgroundColor = UIColor.whiteColor;
    self.impressPasteBox.layer.cornerRadius = 8;
    self.impressPasteBox.layer.borderWidth = 1;
    self.impressPasteBox.layer.borderColor = [UIColor colorWithRed:0xE3 / 255.0 green:0xE6 / 255.0 blue:0xEB / 255.0 alpha:1].CGColor;
    self.impressPasteBox.clipsToBounds = YES;
    [self.impressPasteBox.heightAnchor constraintEqualToConstant:160].active = YES;

    self.impressPasteView = [[UITextView alloc] init];
    self.impressPasteView.translatesAutoresizingMaskIntoConstraints = NO;
    self.impressPasteView.font = [UIFont systemFontOfSize:15];
    self.impressPasteView.backgroundColor = UIColor.clearColor;
    self.impressPasteView.textColor = ImpressTextColor();
    self.impressPasteView.delegate = self;
    self.impressPasteView.textContainerInset = UIEdgeInsetsMake(10, 8, 8, 8);
    self.impressPasteView.accessibilityLabel = @"粘贴大纲内容";
    [self.impressPasteBox addSubview:self.impressPasteView];

    self.impressPasteHint = [[UILabel alloc] init];
    self.impressPasteHint.translatesAutoresizingMaskIntoConstraints = NO;
    self.impressPasteHint.text = @"在此处输入或粘贴大纲，大纲层级尽量不超过4级，AI将为你整理幻灯片大纲";
    self.impressPasteHint.font = [UIFont systemFontOfSize:15];
    self.impressPasteHint.textColor = ImpressHintColor();
    self.impressPasteHint.numberOfLines = 0;
    self.impressPasteHint.userInteractionEnabled = NO;
    [self.impressPasteBox addSubview:self.impressPasteHint];
    [NSLayoutConstraint activateConstraints:@[
        [self.impressPasteView.topAnchor constraintEqualToAnchor:self.impressPasteBox.topAnchor],
        [self.impressPasteView.leadingAnchor constraintEqualToAnchor:self.impressPasteBox.leadingAnchor],
        [self.impressPasteView.trailingAnchor constraintEqualToAnchor:self.impressPasteBox.trailingAnchor],
        [self.impressPasteView.bottomAnchor constraintEqualToAnchor:self.impressPasteBox.bottomAnchor],
        [self.impressPasteHint.topAnchor constraintEqualToAnchor:self.impressPasteBox.topAnchor constant:10],
        [self.impressPasteHint.leadingAnchor constraintEqualToAnchor:self.impressPasteBox.leadingAnchor constant:13],
        [self.impressPasteHint.trailingAnchor constraintEqualToAnchor:self.impressPasteBox.trailingAnchor constant:-13],
    ]];

    self.impressOptionsView = [[UIStackView alloc] initWithArrangedSubviews:
        @[self.impressModeButton, self.impressInputBox, self.impressDocZone, self.impressPasteBox]];
    self.impressOptionsView.axis = UILayoutConstraintAxisVertical;
    self.impressOptionsView.spacing = 8;
    self.impressInputBox.hidden = self.impressMode != 0;
    self.impressDocZone.hidden = self.impressMode != 1;
    self.impressPasteBox.hidden = self.impressMode != 2;

    UIStackView *aiCard = [[UIStackView alloc] initWithArrangedSubviews:@[aiRow, self.impressOptionsView]];
    aiCard.axis = UILayoutConstraintAxisVertical;
    aiCard.spacing = 0;
    aiCard.backgroundColor = ImpressCardColor();
    aiCard.layer.cornerRadius = 12;
    aiCard.layoutMarginsRelativeArrangement = YES;
    aiCard.layoutMargins = UIEdgeInsetsMake(0, 12, 12, 12);

    // Primary action: gradient when AI is on, solid orange "创建演示" when off.
    self.impressCreateButton = [UIButton buttonWithType:UIButtonTypeCustom];
    self.impressCreateButton.titleLabel.font = [UIFont systemFontOfSize:17 weight:UIFontWeightSemibold];
    [self.impressCreateButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    self.impressCreateButton.layer.cornerRadius = 8;
    self.impressCreateButton.clipsToBounds = YES;
    [self.impressCreateButton.heightAnchor constraintEqualToConstant:44].active = YES;
    [self.impressCreateButton addTarget:self action:@selector(createTapped) forControlEvents:UIControlEventTouchUpInside];
    self.impressGradient = [CAGradientLayer layer];
    ImpressStyleGradient(self.impressGradient);
    [self.impressCreateButton.layer insertSublayer:self.impressGradient atIndex:0];

    UIStackView *stack = [[UIStackView alloc] initWithArrangedSubviews:
        @[header, nameLabel, self.fileNameField, aiCard, self.impressCreateButton]];
    stack.axis = UILayoutConstraintAxisVertical;
    stack.spacing = 12;
    [stack setCustomSpacing:20 afterView:header];
    [stack setCustomSpacing:20 afterView:self.fileNameField];
    [stack setCustomSpacing:20 afterView:aiCard];
    stack.translatesAutoresizingMaskIntoConstraints = NO;
    // Required hugging: a vertical stack only resists stretching at priority 250,
    // so without this the bottom-pinned card can grow to the full screen.
    [stack setContentHuggingPriority:UILayoutPriorityRequired forAxis:UILayoutConstraintAxisVertical];
    [stack setContentCompressionResistancePriority:UILayoutPriorityRequired forAxis:UILayoutConstraintAxisVertical];
    [card addSubview:stack];

    self.cardBottomConstraint = [card.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor];
    [NSLayoutConstraint activateConstraints:@[
        [card.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [card.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        self.cardBottomConstraint,
        [stack.topAnchor constraintEqualToAnchor:card.topAnchor constant:8],
        [stack.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:16],
        [stack.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-16],
        [stack.bottomAnchor constraintEqualToAnchor:card.safeAreaLayoutGuide.bottomAnchor constant:-24],
    ]];

    [self impressAiChanged];
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    self.createGradient.frame = self.createButton.bounds;
    self.impressGradient.frame = self.impressCreateButton.bounds;
    if (self.impressDocBorder) {
        self.impressDocBorder.frame = self.impressDocZone.bounds;
        self.impressDocBorder.path = [UIBezierPath bezierPathWithRoundedRect:CGRectInset(self.impressDocZone.bounds, 0.5, 0.5)
                                                                cornerRadius:8].CGPath;
    }
}

- (void)impressAiChanged {
    self.aiSwitchOn = self.impressAiSwitch.isOn;
    self.impressOptionsView.hidden = !self.aiSwitchOn;
    [self updateImpressCreateButtonUi];
    [self.view layoutIfNeeded];
}

- (void)updateImpressCreateButtonUi {
    self.impressGradient.hidden = !self.aiSwitchOn;
    self.impressCreateButton.backgroundColor = self.aiSwitchOn ? UIColor.clearColor : CreateFileButtonColorForKind(CreateFileDocKindImpress);
    [self.impressCreateButton setTitle:self.aiSwitchOn ? @"生成和创建" : @"创建演示" forState:UIControlStateNormal];
}

- (void)updateImpressDocZoneUi {
    if (self.impressSourceLoading) {
        self.impressDocTitle.text = @"正在读取文档…";
        self.impressDocHint.text = @"";
    } else if (self.impressDocContent.length > 0) {
        self.impressDocTitle.text = self.impressDocName.length > 0 ? self.impressDocName : @"已选择文档";
        self.impressDocHint.text = @"点击重新选择";
    } else {
        self.impressDocTitle.text = @"点击选择文件";
        self.impressDocHint.text = @"支持Doc文档、PDF文档、思维导图、在线只能文档导入\n文档<120M，思维导入<5M，字数<15万字";
    }
}

- (void)pickImpressOption:(UIButton *)button {
    NSArray<NSString *> *titles = nil;
    switch (button.tag) {
        case 0:
            titles = @[@"快速生成", @"文档生成", @"粘贴大纲"];
            break;
        case 1:
            titles = @[@"1-10页", @"11-20页", @"21-30页", @"30+页"];
            break;
        case 2:
            titles = @[@"大众", @"投资者", @"学生", @"教师", @"领导"];
            break;
        default:
            titles = @[@"通用", @"行业调研", @"市场推广", @"工作汇报", @"学术报告", @"教学课件"];
            break;
    }
    UIAlertController *picker = [UIAlertController alertControllerWithTitle:nil message:nil preferredStyle:UIAlertControllerStyleActionSheet];
    [titles enumerateObjectsUsingBlock:^(NSString *title, NSUInteger index, BOOL *stop) {
        [picker addAction:[UIAlertAction actionWithTitle:title style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
            if (button.tag == 0) {
                [self selectImpressMode:(NSInteger)index];
            } else if (button.tag == 1) {
                self.impressPage = index + 1;
                [button setTitle:title forState:UIControlStateNormal];
            } else if (button.tag == 2) {
                self.impressAudience = index;
                [button setTitle:title forState:UIControlStateNormal];
            } else {
                self.impressStyle = index;
                [button setTitle:title forState:UIControlStateNormal];
            }
        }]];
    }];
    [picker addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];
    picker.popoverPresentationController.sourceView = button;
    picker.popoverPresentationController.sourceRect = button.bounds;
    [self presentViewController:picker animated:YES completion:nil];
}

- (void)selectImpressMode:(NSInteger)mode {
    // Switching modes invalidates any in-flight document read.
    ++self.impressSourceGeneration;
    self.impressSourceLoading = NO;
    if (self.impressMode == 2) {
        self.impressPasteText = ImpressTrimmed(self.impressPasteView.text);
    }
    self.impressMode = mode;
    [self.impressModeButton setTitle:ImpressModeTitle(mode) forState:UIControlStateNormal];
    self.impressInputBox.hidden = mode != 0;
    self.impressDocZone.hidden = mode != 1;
    self.impressPasteBox.hidden = mode != 2;
    [self updateImpressDocZoneUi];
    if (mode == 2) {
        self.impressPasteView.text = self.impressPasteText ?: @"";
        self.impressPasteHint.hidden = self.impressPasteView.text.length > 0;
        self.impressPasteBox.layer.borderColor =
            [UIColor colorWithRed:0xE3 / 255.0 green:0xE6 / 255.0 blue:0xEB / 255.0 alpha:1].CGColor;
    }
    [self.view layoutIfNeeded];
}

- (void)textViewDidChange:(UITextView *)textView {
    if (textView == self.impressPasteView) {
        self.impressPasteHint.hidden = textView.text.length > 0;
        self.impressPasteText = textView.text;
        self.impressPasteBox.layer.borderColor =
            [UIColor colorWithRed:0xE3 / 255.0 green:0xE6 / 255.0 blue:0xEB / 255.0 alpha:1].CGColor;
    } else if (textView == self.impressPromptView) {
        self.impressPlaceholder.hidden = textView.text.length > 0;
        self.impressInputBox.layer.borderWidth = 0;
    }
}

- (BOOL)textView:(UITextView *)textView shouldChangeTextInRange:(NSRange)range replacementText:(NSString *)text {
    return textView.text.length - range.length + text.length <= 150000;
}

- (void)pickImpressSource {
    UIDocumentPickerViewController *picker = [[UIDocumentPickerViewController alloc]
        initForOpeningContentTypes:@[UTTypePlainText, [UTType typeWithFilenameExtension:@"docx"], UTTypePDF] asCopy:YES];
    picker.delegate = self;
    picker.allowsMultipleSelection = NO;
    [self presentViewController:picker animated:YES completion:nil];
}

- (void)documentPicker:(UIDocumentPickerViewController *)controller didPickDocumentsAtURLs:(NSArray<NSURL *> *)urls {
    NSURL *url = urls.firstObject;
    if (!url) return;
    NSUInteger generation = ++self.impressSourceGeneration;
    self.impressSourceLoading = YES;
    [self updateImpressDocZoneUi];
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        BOOL scoped = [url startAccessingSecurityScopedResource];
        NSString *text = @"";
        if ([url.pathExtension.lowercaseString isEqualToString:@"docx"]) {
            NSDictionary *extracted = [TypesetService extractStructuredFromFile:url];
            text = [extracted[@"fullText"] isKindOfClass:NSString.class] ? extracted[@"fullText"] : @"";
        } else if ([url.pathExtension.lowercaseString isEqualToString:@"pdf"]) {
            PDFDocument *pdf = [[PDFDocument alloc] initWithURL:url];
            NSMutableString *pages = [NSMutableString string];
            for (NSUInteger page = 0; page < pdf.pageCount && pages.length < 150000; ++page) {
                NSString *pageText = [pdf pageAtIndex:page].string;
                if (pageText.length > 0) {
                    if (pages.length > 0) [pages appendString:@"\n\n"];
                    [pages appendString:pageText];
                }
            }
            text = pages;
        } else {
            text = [NSString stringWithContentsOfURL:url encoding:NSUTF8StringEncoding error:nil]
                ?: [NSString stringWithContentsOfURL:url usedEncoding:NULL error:nil] ?: @"";
        }
        if (scoped) [url stopAccessingSecurityScopedResource];
        NSString *content = [text substringToIndex:MIN(text.length, 150000)];
        dispatch_async(dispatch_get_main_queue(), ^{
            if (generation != self.impressSourceGeneration || self.impressMode != 1 || !self.view.window) return;
            self.impressSourceLoading = NO;
            self.impressDocContent = ImpressTrimmed(content);
            self.impressDocName = url.lastPathComponent ?: @"";
            [self updateImpressDocZoneUi];
            if (self.impressDocContent.length == 0) {
                self.impressDocName = @"";
                [self updateImpressDocZoneUi];
                [self showImpressInputError:[url.pathExtension.lowercaseString isEqualToString:@"pdf"]
                    ? @"未读取到文字。扫描版或加密 PDF 请先转换为可复制的文字，或选择 TXT / DOCX 文件。"
                    : @"未读取到文档正文，请选择包含文字的 TXT / DOCX / PDF 文件。"];
            }
        });
    });
}

- (void)showImpressInputError:(NSString *)message {
    UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"无法生成演示"
        message:message preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:nil]];
    [self presentViewController:alert animated:YES completion:nil];
}

- (void)createImpressPresentationWithName:(NSString *)name {
    NSString *input = @"";
    if (self.aiSwitchOn) {
        if (self.impressMode == 1) {
            if (self.impressSourceLoading) {
                [self showImpressInputError:@"文档正在读取，请稍候"];
                return;
            }
            input = self.impressDocContent ?: @"";
            if (input.length == 0) {
                [self showImpressInputError:@"请先选择来源文档"];
                return;
            }
        } else if (self.impressMode == 2) {
            input = ImpressTrimmed(self.impressPasteView.text);
            self.impressPasteText = input;
            if (input.length == 0) {
                self.impressPasteBox.layer.borderColor = ImpressErrorColor().CGColor;
                [self.impressPasteView becomeFirstResponder];
                return;
            }
        } else {
            input = ImpressTrimmed(self.impressPromptView.text);
            if (input.length == 0) {
                self.impressInputBox.layer.borderWidth = 1;
                self.impressInputBox.layer.borderColor = ImpressErrorColor().CGColor;
                [self.impressPromptView becomeFirstResponder];
                return;
            }
        }
    }
    NSDictionary *options = @{
        @"title": name,
        @"inputType": @[@"quick", @"document", @"outline"][self.impressMode],
        @"pageIndex": @(self.impressPage),
        @"audienceIndex": @(self.impressAudience),
        @"styleIndex": @(self.impressStyle),
    };
    BOOL aiOn = self.aiSwitchOn;
    void (^block)(NSString *, BOOL, NSString *, NSDictionary *) = self.completion;
    [self dismissViewControllerAnimated:YES completion:^{
        if (block) {
            block(name, aiOn, input, options);
        }
    }];
}

@end
