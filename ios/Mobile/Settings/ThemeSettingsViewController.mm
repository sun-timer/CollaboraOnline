// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "ThemeSettingsViewController.h"

#import "AppChromeHelper.h"
#import "AppThemeManager.h"

@interface ThemeSettingsViewController ()
@property (strong, nonatomic) UIStackView *stack;
@property (strong, nonatomic) NSMutableArray<UIButton *> *optionButtons;
@end

@implementation ThemeSettingsViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    [AppChromeHelper applySecondaryPageBackground:self.view];
    self.optionButtons = [NSMutableArray array];

    UIButton *back = [AppChromeHelper secondaryBackButtonWithTarget:self action:@selector(close)];
    [self.view addSubview:back];

    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"主题设置";
    title.font = [UIFont boldSystemFontOfSize:20];
    [self.view addSubview:title];

    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.backgroundColor = UIColor.whiteColor;
    card.layer.cornerRadius = 12;
    [self.view addSubview:card];

    self.stack = [[UIStackView alloc] init];
    self.stack.translatesAutoresizingMaskIntoConstraints = NO;
    self.stack.axis = UILayoutConstraintAxisVertical;
    self.stack.spacing = 0;
    [card addSubview:self.stack];

    NSArray<NSNumber *> *modes = @[ @(AppThemeModeLight), @(AppThemeModeDark), @(AppThemeModeSystem) ];
    for (NSNumber *modeNumber in modes) {
        AppThemeMode mode = (AppThemeMode)modeNumber.integerValue;
        UIButton *row = [self optionRowForMode:mode];
        [self.stack addArrangedSubview:row];
        [self.optionButtons addObject:row];
        if (mode != AppThemeModeSystem) {
            UIView *divider = [[UIView alloc] init];
            divider.translatesAutoresizingMaskIntoConstraints = NO;
            divider.backgroundColor = [UIColor colorWithWhite:0 alpha:0.08];
            [divider.heightAnchor constraintEqualToConstant:1].active = YES;
            [self.stack addArrangedSubview:divider];
        }
    }
    [self refreshSelection];

    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:8],
        [back.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:12],
        [title.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [title.leadingAnchor constraintEqualToAnchor:back.trailingAnchor constant:8],
        [card.topAnchor constraintEqualToAnchor:back.bottomAnchor constant:24],
        [card.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:32],
        [card.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor constant:-32],
        [self.stack.topAnchor constraintEqualToAnchor:card.topAnchor],
        [self.stack.leadingAnchor constraintEqualToAnchor:card.leadingAnchor],
        [self.stack.trailingAnchor constraintEqualToAnchor:card.trailingAnchor],
        [self.stack.bottomAnchor constraintEqualToAnchor:card.bottomAnchor],
    ]];
}

- (UIButton *)optionRowForMode:(AppThemeMode)mode {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    button.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeft;
    button.contentEdgeInsets = UIEdgeInsetsMake(16, 24, 16, 24);
    button.tag = mode;
    [button setTitle:[AppThemeManager displayNameForMode:mode] forState:UIControlStateNormal];
    [button setTitleColor:[UIColor colorWithWhite:0.2 alpha:1] forState:UIControlStateNormal];
    button.titleLabel.font = [UIFont systemFontOfSize:16];
    [button.heightAnchor constraintEqualToConstant:55].active = YES;
    [button addTarget:self action:@selector(optionTapped:) forControlEvents:UIControlEventTouchUpInside];
    return button;
}

- (void)refreshSelection {
    AppThemeMode current = [AppThemeManager currentMode];
    for (UIButton *button in self.optionButtons) {
        BOOL selected = button.tag == current;
        button.accessibilityTraits = selected ? UIAccessibilityTraitSelected : UIAccessibilityTraitButton;
        button.backgroundColor = selected ? [UIColor colorWithRed:1 green:0.95 blue:0.92 alpha:1] : UIColor.clearColor;
    }
}

- (void)optionTapped:(UIButton *)sender {
    [AppThemeManager setMode:(AppThemeMode)sender.tag];
    [self refreshSelection];
}

- (void)close {
    if (self.navigationController.viewControllers.count > 1) {
        [self.navigationController popViewControllerAnimated:YES];
    } else {
        [self dismissViewControllerAnimated:YES completion:nil];
    }
}

@end
