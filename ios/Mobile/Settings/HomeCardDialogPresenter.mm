// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "HomeCardDialogPresenter.h"

#import "AI/WriterAIComponents.h"

static const CGFloat kCardMaxWidth = 335.0;
static const CGFloat kCardRadius = 12.0;
static const CGFloat kHeaderHeight = 60.0;
static const CGFloat kButtonHeight = 35.0;
static const CGFloat kButtonGap = 28.0;

static UIColor *CardTitleColor(void) {
    return [UIColor colorWithRed:0x1F / 255.0 green:0x1F / 255.0 blue:0x1F / 255.0 alpha:1];
}

static UIColor *CardPrimaryAccent(void) {
    return [UIColor colorWithRed:0xFA / 255.0 green:0x62 / 255.0 blue:0x00 / 255.0 alpha:1];
}

static UIColor *CardDestructiveAccent(void) {
    return [UIColor colorWithRed:0xFE / 255.0 green:0x3A / 255.0 blue:0x3A / 255.0 alpha:1];
}

@interface HomeCardShellViewController : UIViewController <UITextFieldDelegate, UIGestureRecognizerDelegate>
@property (copy, nonatomic) void (^onDismiss)(void);
@property (strong, nonatomic) UIView *cardView;
@property (strong, nonatomic, nullable) UITextField *inputField;
@end

@implementation HomeCardShellViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor colorWithWhite:0 alpha:0.35];
    UITapGestureRecognizer *dismissTap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(dismissShell)];
    dismissTap.delegate = self;
    [self.view addGestureRecognizer:dismissTap];
}

- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer shouldReceiveTouch:(UITouch *)touch {
    return touch.view == self.view;
}

- (void)dismissShell {
    [self dismissViewControllerAnimated:YES completion:self.onDismiss];
}

- (UIView *)buildCardShellWithTitle:(NSString *)title {
    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.backgroundColor = UIColor.whiteColor;
    card.layer.cornerRadius = kCardRadius;
    card.layer.shadowColor = UIColor.blackColor.CGColor;
    card.layer.shadowOpacity = 0.12;
    card.layer.shadowRadius = 8;
    card.layer.shadowOffset = CGSizeMake(0, 4);
    [self.view addSubview:card];
    self.cardView = card;

    UILabel *titleLabel = [[UILabel alloc] init];
    titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    titleLabel.text = title;
    titleLabel.font = [UIFont systemFontOfSize:20 weight:UIFontWeightMedium];
    titleLabel.textColor = CardTitleColor();
    titleLabel.textAlignment = NSTextAlignmentCenter;
    [card addSubview:titleLabel];

    UIButton *close = [UIButton buttonWithType:UIButtonTypeSystem];
    close.translatesAutoresizingMaskIntoConstraints = NO;
    UIImage *closeIcon = [[UIImage writerIconNamed:@"close"] imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
    [close setImage:closeIcon forState:UIControlStateNormal];
    close.tintColor = [UIColor colorWithWhite:0.35 alpha:1];
    [close addTarget:self action:@selector(dismissShell) forControlEvents:UIControlEventTouchUpInside];
    [card addSubview:close];

    [NSLayoutConstraint activateConstraints:@[
        [card.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [card.centerYAnchor constraintEqualToAnchor:self.view.centerYAnchor],
        [card.leadingAnchor constraintGreaterThanOrEqualToAnchor:self.view.leadingAnchor constant:20],
        [card.trailingAnchor constraintLessThanOrEqualToAnchor:self.view.trailingAnchor constant:-20],
        [card.widthAnchor constraintEqualToConstant:kCardMaxWidth],
        [titleLabel.topAnchor constraintEqualToAnchor:card.topAnchor],
        [titleLabel.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:16],
        [titleLabel.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-16],
        [titleLabel.heightAnchor constraintEqualToConstant:kHeaderHeight],
        [close.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-16],
        [close.centerYAnchor constraintEqualToAnchor:titleLabel.centerYAnchor],
        [close.widthAnchor constraintEqualToConstant:40],
        [close.heightAnchor constraintEqualToConstant:40],
    ]];
    return titleLabel;
}

- (UITextField *)buildInputFieldBelow:(UIView *)anchor inCard:(UIView *)card {
    UITextField *field = [[UITextField alloc] init];
    field.translatesAutoresizingMaskIntoConstraints = NO;
    field.font = [UIFont systemFontOfSize:16];
    field.textColor = [UIColor colorWithRed:0x10 / 255.0 green:0x10 / 255.0 blue:0x10 / 255.0 alpha:1];
    field.backgroundColor = UIColor.whiteColor;
    field.layer.cornerRadius = 8;
    field.layer.borderWidth = 1;
    field.layer.borderColor = [UIColor colorWithRed:0xA2 / 255.0 green:0xA9 / 255.0 blue:0xB2 / 255.0 alpha:1].CGColor;
    field.leftView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, 10, 1)];
    field.leftViewMode = UITextFieldViewModeAlways;
    field.rightView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, 10, 1)];
    field.rightViewMode = UITextFieldViewModeAlways;
    field.returnKeyType = UIReturnKeyDone;
    field.delegate = self;
    field.clearButtonMode = UITextFieldViewModeWhileEditing;
    [card addSubview:field];
    self.inputField = field;

    [NSLayoutConstraint activateConstraints:@[
        [field.topAnchor constraintEqualToAnchor:anchor.bottomAnchor],
        [field.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:12],
        [field.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-12],
        [field.heightAnchor constraintEqualToConstant:43],
    ]];
    return field;
}

- (UIStackView *)buildButtonRowBelow:(UIView *)anchor
                              inCard:(UIView *)card
                         cancelTitle:(NSString *)cancelTitle
                        confirmTitle:(NSString *)confirmTitle
                        confirmStyle:(HomeCardDialogConfirmStyle)confirmStyle
                         onCancel:(void (^)(void))onCancel
                        onConfirm:(void (^)(void))onConfirm {
    UIButton *cancel = [self pillButtonWithTitle:cancelTitle
                                 backgroundColor:[UIColor colorWithWhite:0 alpha:0.06]
                                       textColor:[UIColor colorWithWhite:0 alpha:0.9]];
    UIButton *confirm = [self pillButtonWithTitle:confirmTitle
                                 backgroundColor:confirmStyle == HomeCardDialogConfirmStyleDestructive
                                     ? CardDestructiveAccent()
                                     : CardPrimaryAccent()
                                       textColor:UIColor.whiteColor];
    [cancel addAction:[UIAction actionWithHandler:^(__kindof UIAction *action) {
        if (onCancel) {
            onCancel();
        }
    }] forControlEvents:UIControlEventTouchUpInside];
    [confirm addAction:[UIAction actionWithHandler:^(__kindof UIAction *action) {
        if (onConfirm) {
            onConfirm();
        }
    }] forControlEvents:UIControlEventTouchUpInside];

    UIStackView *row = [[UIStackView alloc] initWithArrangedSubviews:@[ cancel, confirm ]];
    row.translatesAutoresizingMaskIntoConstraints = NO;
    row.axis = UILayoutConstraintAxisHorizontal;
    row.spacing = kButtonGap;
    row.distribution = UIStackViewDistributionFillEqually;
    [card addSubview:row];

    [NSLayoutConstraint activateConstraints:@[
        [row.topAnchor constraintEqualToAnchor:anchor.bottomAnchor constant:12],
        [row.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:20],
        [row.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-20],
        [row.bottomAnchor constraintEqualToAnchor:card.bottomAnchor constant:-12],
        [cancel.heightAnchor constraintEqualToConstant:kButtonHeight],
        [confirm.heightAnchor constraintEqualToConstant:kButtonHeight],
    ]];
    return row;
}

- (UIButton *)pillButtonWithTitle:(NSString *)title
                  backgroundColor:(UIColor *)backgroundColor
                        textColor:(UIColor *)textColor {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    [button setTitle:title forState:UIControlStateNormal];
    [button setTitleColor:textColor forState:UIControlStateNormal];
    button.titleLabel.font = [UIFont systemFontOfSize:16];
    button.backgroundColor = backgroundColor;
    button.layer.cornerRadius = kButtonHeight / 2.0;
    button.clipsToBounds = YES;
    return button;
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    if (self.inputField != nil) {
        [self.inputField becomeFirstResponder];
        UITextPosition *end = [self.inputField endOfDocument];
        self.inputField.selectedTextRange = [self.inputField textRangeFromPosition:end toPosition:end];
    }
}

- (BOOL)textFieldShouldReturn:(UITextField *)textField {
    [textField resignFirstResponder];
    return YES;
}

@end

@implementation HomeCardDialogPresenter

+ (void)presentShellFrom:(UIViewController *)host setup:(void (^)(HomeCardShellViewController *shell))setup {
    HomeCardShellViewController *shell = [[HomeCardShellViewController alloc] init];
    shell.modalPresentationStyle = UIModalPresentationOverFullScreen;
    shell.modalTransitionStyle = UIModalTransitionStyleCrossDissolve;
    if (setup) {
        setup(shell);
    }
    [host presentViewController:shell animated:YES completion:nil];
}

+ (void)presentRenameFrom:(UIViewController *)host
              currentName:(NSString *)currentName
               completion:(void (^)(NSString *))completion {
    [self presentShellFrom:host setup:^(HomeCardShellViewController *shell) {
        UIView *titleLabel = [shell buildCardShellWithTitle:@"重命名"];
        UITextField *field = [shell buildInputFieldBelow:titleLabel inCard:shell.cardView];
        field.text = currentName;
        NSString *original = [currentName copy];
        [shell buildButtonRowBelow:field
                            inCard:shell.cardView
                       cancelTitle:@"取消"
                      confirmTitle:@"确定"
                      confirmStyle:HomeCardDialogConfirmStylePrimary
                          onCancel:^{ [shell dismissShell]; }
                         onConfirm:^{
            NSString *value = [field.text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
            if (value.length == 0) {
                field.layer.borderColor = CardDestructiveAccent().CGColor;
                return;
            }
            if ([value isEqualToString:original]) {
                [shell dismissShell];
                return;
            }
            [shell dismissViewControllerAnimated:YES completion:^{
                if (completion) {
                    completion(value);
                }
            }];
        }];
    }];
}

+ (void)presentConfirmFrom:(UIViewController *)host
                     title:(NSString *)title
                   message:(NSString *)message
              confirmStyle:(HomeCardDialogConfirmStyle)confirmStyle
                completion:(void (^)(BOOL))completion {
    [self presentConfirmFrom:host title:title message:message confirmTitle:nil confirmStyle:confirmStyle completion:completion];
}

+ (void)presentConfirmFrom:(UIViewController *)host
                     title:(NSString *)title
                   message:(NSString *)message
              confirmTitle:(NSString *)confirmTitle
              confirmStyle:(HomeCardDialogConfirmStyle)confirmStyle
                completion:(void (^)(BOOL))completion {
    NSString *resolvedConfirmTitle = confirmTitle.length > 0 ? confirmTitle : @"确定";
    [self presentShellFrom:host setup:^(HomeCardShellViewController *shell) {
        UIView *titleLabel = [shell buildCardShellWithTitle:title];
        UILabel *body = [[UILabel alloc] init];
        body.translatesAutoresizingMaskIntoConstraints = NO;
        body.text = message;
        body.font = [UIFont systemFontOfSize:16];
        body.textColor = [UIColor colorWithRed:0x33 / 255.0 green:0x33 / 255.0 blue:0x33 / 255.0 alpha:1];
        body.numberOfLines = 0;
        [shell.cardView addSubview:body];
        [NSLayoutConstraint activateConstraints:@[
            [body.topAnchor constraintEqualToAnchor:titleLabel.bottomAnchor constant:24],
            [body.leadingAnchor constraintEqualToAnchor:shell.cardView.leadingAnchor constant:12],
            [body.trailingAnchor constraintEqualToAnchor:shell.cardView.trailingAnchor constant:-12],
        ]];
        [shell buildButtonRowBelow:body
                            inCard:shell.cardView
                       cancelTitle:@"取消"
                      confirmTitle:resolvedConfirmTitle
                      confirmStyle:confirmStyle
                          onCancel:^{
            [shell dismissViewControllerAnimated:YES completion:^{
                if (completion) {
                    completion(NO);
                }
            }];
        } onConfirm:^{
            [shell dismissViewControllerAnimated:YES completion:^{
                if (completion) {
                    completion(YES);
                }
            }];
        }];
    }];
}

+ (void)presentNicknameEditFrom:(UIViewController *)host
                    currentName:(NSString *)currentName
                     completion:(void (^)(NSString *))completion {
    [self presentShellFrom:host setup:^(HomeCardShellViewController *shell) {
        UIView *titleLabel = [shell buildCardShellWithTitle:@"昵称"];
        UITextField *field = [shell buildInputFieldBelow:titleLabel inCard:shell.cardView];
        field.text = currentName;
        field.placeholder = @"请输入新昵称";
        [shell buildButtonRowBelow:field
                            inCard:shell.cardView
                       cancelTitle:@"取消"
                      confirmTitle:@"确定"
                      confirmStyle:HomeCardDialogConfirmStylePrimary
                          onCancel:^{ [shell dismissShell]; }
                         onConfirm:^{
            NSString *value = [field.text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
            if (value.length == 0) {
                value = @"orangepi";
            }
            [shell dismissViewControllerAnimated:YES completion:^{
                if (completion) {
                    completion(value);
                }
            }];
        }];
    }];
}

+ (void)presentSetAvatarFrom:(UIViewController *)host
                   takePhoto:(void (^)(void))takePhoto
                 pickGallery:(void (^)(void))pickGallery {
    [self presentShellFrom:host setup:^(HomeCardShellViewController *shell) {
        UIView *titleLabel = [shell buildCardShellWithTitle:@"设置头像"];
        UIStackView *options = [[UIStackView alloc] init];
        options.translatesAutoresizingMaskIntoConstraints = NO;
        options.axis = UILayoutConstraintAxisVertical;
        options.spacing = 0;

        UIButton *photo = [self optionButtonWithTitle:@"拍照"];
        UIButton *gallery = [self optionButtonWithTitle:@"从相册选择"];
        [photo addAction:[UIAction actionWithHandler:^(__kindof UIAction *action) {
            [shell dismissViewControllerAnimated:YES completion:takePhoto];
        }] forControlEvents:UIControlEventTouchUpInside];
        [gallery addAction:[UIAction actionWithHandler:^(__kindof UIAction *action) {
            [shell dismissViewControllerAnimated:YES completion:pickGallery];
        }] forControlEvents:UIControlEventTouchUpInside];
        [options addArrangedSubview:photo];
        [options addArrangedSubview:gallery];
        [shell.cardView addSubview:options];

        UIButton *cancel = [shell pillButtonWithTitle:@"取消"
                                      backgroundColor:[UIColor colorWithWhite:0 alpha:0.06]
                                            textColor:[UIColor colorWithWhite:0 alpha:0.9]];
        [cancel addTarget:shell action:@selector(dismissShell) forControlEvents:UIControlEventTouchUpInside];
        [shell.cardView addSubview:cancel];

        [NSLayoutConstraint activateConstraints:@[
            [options.topAnchor constraintEqualToAnchor:titleLabel.bottomAnchor],
            [options.leadingAnchor constraintEqualToAnchor:shell.cardView.leadingAnchor constant:12],
            [options.trailingAnchor constraintEqualToAnchor:shell.cardView.trailingAnchor constant:-12],
            [photo.heightAnchor constraintEqualToConstant:48],
            [gallery.heightAnchor constraintEqualToConstant:48],
            [cancel.topAnchor constraintEqualToAnchor:options.bottomAnchor constant:12],
            [cancel.leadingAnchor constraintEqualToAnchor:shell.cardView.leadingAnchor constant:20],
            [cancel.trailingAnchor constraintEqualToAnchor:shell.cardView.trailingAnchor constant:-20],
            [cancel.bottomAnchor constraintEqualToAnchor:shell.cardView.bottomAnchor constant:-12],
            [cancel.heightAnchor constraintEqualToConstant:kButtonHeight],
        ]];
    }];
}

+ (UIButton *)optionButtonWithTitle:(NSString *)title {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    button.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeading;
    [button setTitle:title forState:UIControlStateNormal];
    [button setTitleColor:[UIColor colorWithRed:0x10 / 255.0 green:0x10 / 255.0 blue:0x10 / 255.0 alpha:1]
                 forState:UIControlStateNormal];
    button.titleLabel.font = [UIFont systemFontOfSize:16];
    return button;
}

@end
