// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AboutViewController.h"

#import "Settings/AppChromeHelper.h"
#import "Settings/AppIcons.h"
#import "Settings/AppThemeManager.h"
#import "Feedback/FeedbackViewController.h"
#import "Settings/HTMLAssetViewController.h"
#import "Settings/ThemeSettingsViewController.h"

static UIColor *AboutCardColor(void) {
    return UIColor.whiteColor;
}

@interface AboutViewController ()
@property (strong, nonatomic) UINavigationController *innerNavigation;
@end

@implementation AboutViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    [AppChromeHelper applySecondaryPageBackground:self.view];

    UIButton *back = [AppChromeHelper secondaryBackButtonWithTarget:self action:@selector(close)];
    [self.view addSubview:back];

    UILabel *header = [[UILabel alloc] init];
    header.translatesAutoresizingMaskIntoConstraints = NO;
    header.text = @"关于";
    header.font = [UIFont boldSystemFontOfSize:20];
    header.textColor = [UIColor colorWithWhite:0.06 alpha:1];
    [self.view addSubview:header];

    UIImageView *logo = [[UIImageView alloc] initWithImage:[AppIcons iconNamed:@"avatar"]];
    logo.translatesAutoresizingMaskIntoConstraints = NO;
    logo.contentMode = UIViewContentModeScaleAspectFit;
    logo.layer.cornerRadius = 20;
    logo.clipsToBounds = YES;
    [self.view addSubview:logo];

    UILabel *appName = [[UILabel alloc] init];
    appName.translatesAutoresizingMaskIntoConstraints = NO;
    appName.text = @"Orange Office";
    appName.font = [UIFont boldSystemFontOfSize:20];
    appName.textAlignment = NSTextAlignmentCenter;
    [self.view addSubview:appName];

    NSString *version = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleShortVersionString"] ?: @"";
    NSString *build = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleVersion"] ?: @"";
    NSString *versionText = version.length > 0 ? [NSString stringWithFormat:@"版本 %@ (%@)", version, build] : @"版本信息";

    UIStackView *rows = [[UIStackView alloc] init];
    rows.translatesAutoresizingMaskIntoConstraints = NO;
    rows.axis = UILayoutConstraintAxisVertical;
    rows.spacing = 12;
    [self.view addSubview:rows];

    [rows addArrangedSubview:[self infoCardWithTitle:versionText action:nil]];
    [rows addArrangedSubview:[self menuCardWithTitle:@"问题和建议" action:@selector(openFeedback)]];
    [rows addArrangedSubview:[self menuCardWithTitle:@"主题设置" subtitle:[AppThemeManager displayNameForMode:[AppThemeManager currentMode]] action:@selector(openTheme)]];
    [rows addArrangedSubview:[self menuCardWithTitle:@"开源许可" action:@selector(openLicense)]];
    [rows addArrangedSubview:[self menuCardWithTitle:@"法律声明" action:@selector(openNotice)]];

    UILabel *footer = [[UILabel alloc] init];
    footer.translatesAutoresizingMaskIntoConstraints = NO;
    footer.text = @"© Collabora Productivity Ltd.";
    footer.font = [UIFont systemFontOfSize:12];
    footer.textColor = [UIColor colorWithWhite:0.71 alpha:1];
    footer.textAlignment = NSTextAlignmentCenter;
    [self.view addSubview:footer];

    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:8],
        [back.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:12],
        [header.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [header.leadingAnchor constraintEqualToAnchor:back.trailingAnchor constant:8],
        [logo.topAnchor constraintEqualToAnchor:back.bottomAnchor constant:48],
        [logo.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [logo.widthAnchor constraintEqualToConstant:100],
        [logo.heightAnchor constraintEqualToConstant:100],
        [appName.topAnchor constraintEqualToAnchor:logo.bottomAnchor constant:24],
        [appName.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [rows.topAnchor constraintEqualToAnchor:appName.bottomAnchor constant:48],
        [rows.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:32],
        [rows.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor constant:-32],
        [footer.bottomAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.bottomAnchor constant:-16],
        [footer.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:16],
        [footer.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor constant:-16],
    ]];
}

- (UIView *)cardContainer {
    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.backgroundColor = AboutCardColor();
    card.layer.cornerRadius = 12;
    [card.heightAnchor constraintEqualToConstant:55].active = YES;
    return card;
}

- (UIView *)infoCardWithTitle:(NSString *)title action:(SEL _Nullable)action {
    UIView *card = [self cardContainer];
    UILabel *label = [[UILabel alloc] init];
    label.translatesAutoresizingMaskIntoConstraints = NO;
    label.text = title;
    label.font = [UIFont systemFontOfSize:16];
    label.textColor = [UIColor colorWithWhite:0.2 alpha:1];
    [card addSubview:label];
    [NSLayoutConstraint activateConstraints:@[
        [label.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:24],
        [label.centerYAnchor constraintEqualToAnchor:card.centerYAnchor],
        [label.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-24],
    ]];
    return card;
}

- (UIView *)menuCardWithTitle:(NSString *)title action:(SEL)action {
    return [self menuCardWithTitle:title subtitle:nil action:action];
}

- (UIView *)menuCardWithTitle:(NSString *)title subtitle:(NSString * _Nullable)subtitle action:(SEL)action {
    UIButton *card = [UIButton buttonWithType:UIButtonTypeCustom];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.backgroundColor = AboutCardColor();
    card.layer.cornerRadius = 12;
    card.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeft;
    card.contentEdgeInsets = UIEdgeInsetsMake(0, 24, 0, 24);
    [card.heightAnchor constraintEqualToConstant:55].active = YES;
    [card addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];

    UILabel *label = [[UILabel alloc] init];
    label.translatesAutoresizingMaskIntoConstraints = NO;
    label.text = title;
    label.font = [UIFont systemFontOfSize:16];
    label.textColor = [UIColor colorWithWhite:0.2 alpha:1];
    label.userInteractionEnabled = NO;
    [card addSubview:label];

    UILabel *detail = nil;
    if (subtitle.length > 0) {
        detail = [[UILabel alloc] init];
        detail.translatesAutoresizingMaskIntoConstraints = NO;
        detail.text = subtitle;
        detail.font = [UIFont systemFontOfSize:14];
        detail.textColor = [UIColor colorWithWhite:0.5 alpha:1];
        detail.userInteractionEnabled = NO;
        [card addSubview:detail];
    }

    UILabel *chevron = [[UILabel alloc] init];
    chevron.translatesAutoresizingMaskIntoConstraints = NO;
    chevron.text = @"›";
    chevron.font = [UIFont systemFontOfSize:18];
    chevron.textColor = [UIColor colorWithWhite:0.6 alpha:1];
    chevron.userInteractionEnabled = NO;
    [card addSubview:chevron];

    NSMutableArray *constraints = [NSMutableArray arrayWithObjects:
        [label.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:24],
        [label.centerYAnchor constraintEqualToAnchor:card.centerYAnchor],
        [chevron.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-24],
        [chevron.centerYAnchor constraintEqualToAnchor:card.centerYAnchor],
        nil];
    if (detail != nil) {
        [constraints addObjectsFromArray:@[
            [detail.trailingAnchor constraintEqualToAnchor:chevron.leadingAnchor constant:-8],
            [detail.centerYAnchor constraintEqualToAnchor:card.centerYAnchor],
            [label.trailingAnchor constraintLessThanOrEqualToAnchor:detail.leadingAnchor constant:-8],
        ]];
    } else {
        [constraints addObject:[label.trailingAnchor constraintLessThanOrEqualToAnchor:chevron.leadingAnchor constant:-8]];
    }
    [NSLayoutConstraint activateConstraints:constraints];
    return card;
}

- (void)openFeedback {
    FeedbackViewController *vc = [[FeedbackViewController alloc] init];
    UINavigationController *nav = [[UINavigationController alloc] initWithRootViewController:vc];
    nav.modalPresentationStyle = UIModalPresentationFullScreen;
    [self presentViewController:nav animated:YES completion:nil];
}

- (void)openTheme {
    ThemeSettingsViewController *vc = [[ThemeSettingsViewController alloc] init];
    UINavigationController *nav = [[UINavigationController alloc] initWithRootViewController:vc];
    nav.modalPresentationStyle = UIModalPresentationFullScreen;
    [self presentViewController:nav animated:YES completion:nil];
}

- (void)openLicense {
    HTMLAssetViewController *vc = [[HTMLAssetViewController alloc] initWithTitle:@"开源许可"
                                                                   resourceName:@"license"
                                                              resourceExtension:@"html"];
    [self pushOrPresent:vc];
}

- (void)openNotice {
    HTMLAssetViewController *vc = [[HTMLAssetViewController alloc] initWithTitle:@"法律声明"
                                                                   resourceName:@"notice"
                                                              resourceExtension:@"txt"];
    [self pushOrPresent:vc];
}

- (void)pushOrPresent:(UIViewController *)vc {
    if (self.navigationController != nil) {
        [self.navigationController pushViewController:vc animated:YES];
        return;
    }
    UINavigationController *nav = [[UINavigationController alloc] initWithRootViewController:vc];
    nav.modalPresentationStyle = UIModalPresentationFullScreen;
    [self presentViewController:nav animated:YES completion:nil];
}

- (void)close {
    [self dismissViewControllerAnimated:YES completion:nil];
}

@end
