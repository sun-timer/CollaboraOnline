// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "CalcSheetRenameController.h"
#import "../KeyboardAccessory.h"

static UIColor *CalcSheetRenameColor(CGFloat red, CGFloat green, CGFloat blue) {
    return [UIColor colorWithRed:red / 255.0 green:green / 255.0 blue:blue / 255.0 alpha:1.0];
}

@interface CalcSheetRenameController () <UITextFieldDelegate>
@property (copy, nonatomic) NSString *currentName;
@property (copy, nonatomic) NSArray<NSString *> *existingNames;
@property (copy, nonatomic) CalcSheetRenameHandler onConfirm;
@property (strong, nonatomic) UIView *cardView;
@property (strong, nonatomic) UITextField *nameField;
@property (strong, nonatomic) UILabel *validationLabel;
@property (strong, nonatomic) NSLayoutConstraint *validationHeight;
@property (strong, nonatomic) NSLayoutConstraint *cardCenterY;
@property (assign, nonatomic) CGFloat keyboardTopInView;
@property (assign, nonatomic) BOOL finishing;
@end

@implementation CalcSheetRenameController

- (instancetype)initWithCurrentName:(NSString *)currentName
                      existingNames:(NSArray<NSString *> *)existingNames
                          onConfirm:(CalcSheetRenameHandler)onConfirm {
    self = [super initWithNibName:nil bundle:nil];
    if (self) {
        _currentName = [currentName copy] ?: @"";
        _existingNames = [existingNames copy] ?: @[];
        _onConfirm = [onConfirm copy];
        _keyboardTopInView = CGFLOAT_MAX;
        self.modalPresentationStyle = UIModalPresentationOverFullScreen;
        self.modalTransitionStyle = UIModalTransitionStyleCrossDissolve;
    }
    return self;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor colorWithWhite:0 alpha:0.35];

    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(keyboardWillChangeFrame:)
                                                 name:UIKeyboardWillChangeFrameNotification
                                               object:nil];

    self.cardView = [[UIView alloc] init];
    self.cardView.translatesAutoresizingMaskIntoConstraints = NO;
    self.cardView.backgroundColor = UIColor.whiteColor;
    self.cardView.layer.cornerRadius = 18.0;
    self.cardView.clipsToBounds = YES;
    [self.view addSubview:self.cardView];

    UILabel *titleLabel = [[UILabel alloc] init];
    titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    titleLabel.text = @"重命名工作表";
    titleLabel.font = [UIFont systemFontOfSize:20 weight:UIFontWeightMedium];
    titleLabel.textColor = CalcSheetRenameColor(0x1F, 0x1F, 0x1F);
    titleLabel.textAlignment = NSTextAlignmentCenter;
    [self.cardView addSubview:titleLabel];

    UIButton *closeButton = [UIButton buttonWithType:UIButtonTypeSystem];
    closeButton.translatesAutoresizingMaskIntoConstraints = NO;
    [closeButton setImage:[UIImage systemImageNamed:@"xmark"] forState:UIControlStateNormal];
    closeButton.tintColor = CalcSheetRenameColor(0x1F, 0x1F, 0x1F);
    closeButton.accessibilityLabel = @"关闭";
    [closeButton addTarget:self action:@selector(cancelTapped) forControlEvents:UIControlEventTouchUpInside];
    [self.cardView addSubview:closeButton];

    self.nameField = [[UITextField alloc] init];
    self.nameField.translatesAutoresizingMaskIntoConstraints = NO;
    self.nameField.text = self.currentName;
    self.nameField.font = [UIFont systemFontOfSize:16];
    self.nameField.textColor = CalcSheetRenameColor(0x1F, 0x1F, 0x1F);
    self.nameField.borderStyle = UITextBorderStyleNone;
    self.nameField.layer.cornerRadius = 8.0;
    self.nameField.layer.borderWidth = 1.0;
    self.nameField.layer.borderColor = CalcSheetRenameColor(0xA2, 0xA9, 0xB2).CGColor;
    self.nameField.clearButtonMode = UITextFieldViewModeWhileEditing;
    self.nameField.returnKeyType = UIReturnKeyDone;
    self.nameField.delegate = self;
    self.nameField.accessibilityIdentifier = @"calcSheetRename.name";
    self.nameField.leftView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, 12, 1)];
    self.nameField.leftViewMode = UITextFieldViewModeAlways;
    self.nameField.rightView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, 12, 1)];
    self.nameField.rightViewMode = UITextFieldViewModeAlways;
    [KeyboardAccessory installOnInput:self.nameField];
    [self.nameField addTarget:self action:@selector(nameChanged) forControlEvents:UIControlEventEditingChanged];
    [self.cardView addSubview:self.nameField];

    self.validationLabel = [[UILabel alloc] init];
    self.validationLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.validationLabel.font = [UIFont systemFontOfSize:12];
    self.validationLabel.textColor = CalcSheetRenameColor(0xD9, 0x30, 0x25);
    self.validationLabel.numberOfLines = 1;
    self.validationLabel.hidden = YES;
    [self.cardView addSubview:self.validationLabel];

    UIButton *cancelButton = [self buttonWithTitle:@"取消"
                                       background:CalcSheetRenameColor(0xF0, 0xF0, 0xF0)
                                         titleColor:CalcSheetRenameColor(0x1F, 0x1F, 0x1F)
                                           action:@selector(cancelTapped)];
    cancelButton.accessibilityIdentifier = @"calcSheetRename.cancel";
    [self.cardView addSubview:cancelButton];

    UIButton *confirmButton = [self buttonWithTitle:@"确定"
                                         background:CalcSheetRenameColor(0x3B, 0x80, 0x40)
                                           titleColor:UIColor.whiteColor
                                             action:@selector(confirmTapped)];
    confirmButton.accessibilityIdentifier = @"calcSheetRename.confirm";
    [self.cardView addSubview:confirmButton];

    self.validationHeight = [self.validationLabel.heightAnchor constraintEqualToConstant:0];
    self.cardCenterY = [self.cardView.centerYAnchor constraintEqualToAnchor:self.view.centerYAnchor];
    NSLayoutConstraint *preferredCardWidth = [self.cardView.widthAnchor constraintEqualToConstant:335];
    preferredCardWidth.priority = UILayoutPriorityDefaultHigh;

    [NSLayoutConstraint activateConstraints:@[
        [self.cardView.widthAnchor constraintLessThanOrEqualToConstant:335],
        preferredCardWidth,
        [self.cardView.leadingAnchor constraintGreaterThanOrEqualToAnchor:self.view.leadingAnchor constant:16],
        [self.cardView.trailingAnchor constraintLessThanOrEqualToAnchor:self.view.trailingAnchor constant:-16],
        [self.cardView.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        self.cardCenterY,

        [titleLabel.topAnchor constraintEqualToAnchor:self.cardView.topAnchor constant:20],
        [titleLabel.leadingAnchor constraintEqualToAnchor:self.cardView.leadingAnchor constant:44],
        [titleLabel.trailingAnchor constraintEqualToAnchor:self.cardView.trailingAnchor constant:-44],
        [titleLabel.heightAnchor constraintEqualToConstant:24],
        [closeButton.centerYAnchor constraintEqualToAnchor:titleLabel.centerYAnchor],
        [closeButton.trailingAnchor constraintEqualToAnchor:self.cardView.trailingAnchor constant:-8],
        [closeButton.widthAnchor constraintEqualToConstant:36],
        [closeButton.heightAnchor constraintEqualToConstant:36],

        [self.nameField.topAnchor constraintEqualToAnchor:titleLabel.bottomAnchor constant:16],
        [self.nameField.leadingAnchor constraintEqualToAnchor:self.cardView.leadingAnchor constant:12],
        [self.nameField.trailingAnchor constraintEqualToAnchor:self.cardView.trailingAnchor constant:-12],
        [self.nameField.heightAnchor constraintEqualToConstant:44],

        [self.validationLabel.topAnchor constraintEqualToAnchor:self.nameField.bottomAnchor constant:4],
        [self.validationLabel.leadingAnchor constraintEqualToAnchor:self.nameField.leadingAnchor],
        [self.validationLabel.trailingAnchor constraintEqualToAnchor:self.nameField.trailingAnchor],
        self.validationHeight,

        [cancelButton.topAnchor constraintEqualToAnchor:self.validationLabel.bottomAnchor constant:16],
        [cancelButton.leadingAnchor constraintEqualToAnchor:self.nameField.leadingAnchor],
        [confirmButton.leadingAnchor constraintEqualToAnchor:cancelButton.trailingAnchor constant:12],
        [confirmButton.trailingAnchor constraintEqualToAnchor:self.nameField.trailingAnchor],
        [confirmButton.widthAnchor constraintEqualToAnchor:cancelButton.widthAnchor],
        [cancelButton.heightAnchor constraintEqualToConstant:36],
        [confirmButton.heightAnchor constraintEqualToConstant:36],
        [cancelButton.bottomAnchor constraintEqualToAnchor:self.cardView.bottomAnchor constant:-20],
    ]];
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [self.nameField becomeFirstResponder];
}

- (UIButton *)buttonWithTitle:(NSString *)title
                   background:(UIColor *)background
                   titleColor:(UIColor *)titleColor
                       action:(SEL)action {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    button.backgroundColor = background;
    button.layer.cornerRadius = 18.0;
    button.titleLabel.font = [UIFont systemFontOfSize:16];
    [button setTitle:title forState:UIControlStateNormal];
    [button setTitleColor:titleColor forState:UIControlStateNormal];
    [button addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];
    return button;
}

- (BOOL)textFieldShouldReturn:(UITextField *)textField {
    [self confirmTapped];
    return NO;
}

- (void)nameChanged {
    if (!self.validationLabel.hidden) {
        self.validationLabel.hidden = YES;
        self.validationHeight.constant = 0;
        self.nameField.layer.borderColor = CalcSheetRenameColor(0xA2, 0xA9, 0xB2).CGColor;
        [UIView animateWithDuration:0.2 animations:^{ [self.cardView layoutIfNeeded]; } completion:^(BOOL finished) {
            [self updateCardPositionForKeyboard];
            [UIView animateWithDuration:0.2 animations:^{ [self.view layoutIfNeeded]; }];
        }];
    }
}

- (NSString *)trimmedName {
    return [self.nameField.text stringByTrimmingCharactersInSet:
        [NSCharacterSet whitespaceAndNewlineCharacterSet]] ?: @"";
}

- (BOOL)nameAlreadyExists:(NSString *)candidate {
    for (NSString *existingName in self.existingNames) {
        NSString *existing = [existingName stringByTrimmingCharactersInSet:
            [NSCharacterSet whitespaceAndNewlineCharacterSet]];
        // The current sheet name is in the supplied list; it is not a duplicate of itself.
        if ([existing caseInsensitiveCompare:self.currentName] == NSOrderedSame) {
            continue;
        }
        if ([candidate caseInsensitiveCompare:existing] == NSOrderedSame) {
            return YES;
        }
    }
    return NO;
}

- (void)showValidationError:(NSString *)message {
    self.validationLabel.text = message;
    self.validationLabel.hidden = NO;
    self.validationHeight.constant = 16;
    self.nameField.layer.borderColor = CalcSheetRenameColor(0xD9, 0x30, 0x25).CGColor;
    UIAccessibilityPostNotification(UIAccessibilityAnnouncementNotification, message);
    [UIView animateWithDuration:0.2 animations:^{
        [self.cardView layoutIfNeeded];
    } completion:^(BOOL finished) {
        [self updateCardPositionForKeyboard];
        [UIView animateWithDuration:0.2 animations:^{ [self.view layoutIfNeeded]; }];
    }];
    // Keep editing active so the user can correct the name without reopening the keyboard.
}

- (void)cancelTapped {
    if (self.finishing) {
        return;
    }
    self.finishing = YES;
    [self.nameField resignFirstResponder];
    [self dismissViewControllerAnimated:YES completion:nil];
}

- (void)confirmTapped {
    if (self.finishing) {
        return;
    }
    NSString *newName = [self trimmedName];
    if (newName.length == 0) {
        [self showValidationError:@"请输入工作表名称"];
        return;
    }
    if (newName.length > 31 || [newName rangeOfCharacterFromSet:
        [NSCharacterSet characterSetWithCharactersInString:@"[]:*?/\\"]].location != NSNotFound
        || [newName hasPrefix:@"'"] || [newName hasSuffix:@"'"]) {
        [self showValidationError:@"名称不能超过31个字符或包含无效字符"];
        return;
    }
    if ([self nameAlreadyExists:newName]) {
        [self showValidationError:@"工作表名称已存在"];
        return;
    }

    self.finishing = YES;
    CalcSheetRenameHandler handler = self.onConfirm;
    [self.nameField resignFirstResponder];
    [self dismissViewControllerAnimated:YES completion:^{
        if (handler) {
            handler(newName);
        }
    }];
}

- (void)keyboardWillChangeFrame:(NSNotification *)notification {
    CGRect keyboardFrame = [notification.userInfo[UIKeyboardFrameEndUserInfoKey] CGRectValue];
    CGRect localKeyboardFrame = [self.view convertRect:keyboardFrame fromView:nil];
    self.keyboardTopInView = CGRectGetMinY(localKeyboardFrame) < CGRectGetMaxY(self.view.bounds)
        ? CGRectGetMinY(localKeyboardFrame)
        : CGFLOAT_MAX;
    NSTimeInterval duration = [notification.userInfo[UIKeyboardAnimationDurationUserInfoKey] doubleValue];
    UIViewAnimationOptions options =
        ([notification.userInfo[UIKeyboardAnimationCurveUserInfoKey] integerValue] << 16)
        | UIViewAnimationOptionBeginFromCurrentState;
    [self updateCardPositionForKeyboard];
    [UIView animateWithDuration:duration delay:0 options:options animations:^{
        [self.view layoutIfNeeded];
    } completion:nil];
}

- (void)updateCardPositionForKeyboard {
    CGFloat cardBottom = CGRectGetMidY(self.view.bounds) + CGRectGetHeight(self.cardView.bounds) / 2.0;
    CGFloat shift = self.keyboardTopInView < CGFLOAT_MAX
        ? MAX(0, cardBottom + 16.0 - self.keyboardTopInView)
        : 0;
    self.cardCenterY.constant = -shift;
}

- (void)dealloc {
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

@end
