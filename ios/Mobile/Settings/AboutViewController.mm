// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AboutViewController.h"

#import "Settings/AppChromeHelper.h"
#import "Settings/AppIcons.h"
#import "AI/AISettingsDrawerIcons.h"
#import "Feedback/FeedbackViewController.h"
#import "Settings/HTMLAssetViewController.h"

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

    // 设计 233:7442：这里是应用 logo（Orange Office 图标），放在 100×100 的白色圆角底板里
    UIView *logoPlate = [[UIView alloc] init];
    logoPlate.translatesAutoresizingMaskIntoConstraints = NO;
    logoPlate.backgroundColor = UIColor.whiteColor;
    logoPlate.layer.cornerRadius = 24;
    [self.view addSubview:logoPlate];

    UIImageView *logo = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"AboutAppLogo"]];
    logo.translatesAutoresizingMaskIntoConstraints = NO;
    logo.contentMode = UIViewContentModeScaleAspectFit;
    [logoPlate addSubview:logo];

    UILabel *appName = [[UILabel alloc] init];
    appName.translatesAutoresizingMaskIntoConstraints = NO;
    appName.text = @"Orange Office";
    appName.font = [UIFont systemFontOfSize:20 weight:UIFontWeightMedium];
    appName.textColor = [UIColor colorWithWhite:0x10 / 255.0 alpha:1];
    appName.textAlignment = NSTextAlignmentCenter;
    [self.view addSubview:appName];

    NSString *version = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleShortVersionString"] ?: @"";
    NSString *build = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleVersion"] ?: @"";
    NSString *versionValue = version.length > 0
        ? (build.length > 0 ? [NSString stringWithFormat:@"%@ (%@)", version, build] : version)
        : @"";

    UIStackView *rows = [[UIStackView alloc] init];
    rows.translatesAutoresizingMaskIntoConstraints = NO;
    rows.axis = UILayoutConstraintAxisVertical;
    rows.spacing = 12;
    [self.view addSubview:rows];

    [rows addArrangedSubview:[self valueCardWithTitle:@"版本信息" value:versionValue]];
    [rows addArrangedSubview:[self menuCardWithTitle:@"问题和建议" action:@selector(openFeedback)]];
    [rows addArrangedSubview:[self menuCardWithTitle:@"开源许可" action:@selector(openLicense)]];
    [rows addArrangedSubview:[self menuCardWithTitle:@"法律声明" action:@selector(openNotice)]];

    UILabel *footer = [[UILabel alloc] init];
    footer.translatesAutoresizingMaskIntoConstraints = NO;
    footer.text = @"版权所有 ©2025-2026 深圳市迅龙科技有限公司。保留一切权利。";
    footer.font = [UIFont systemFontOfSize:12];
    footer.textColor = [UIColor colorWithWhite:0xB5 / 255.0 alpha:1];
    footer.textAlignment = NSTextAlignmentCenter;
    footer.numberOfLines = 0;
    [self.view addSubview:footer];

    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:0],
        [back.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:12],
        [header.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [header.leadingAnchor constraintEqualToAnchor:back.trailingAnchor constant:4],
        [logoPlate.topAnchor constraintEqualToAnchor:back.bottomAnchor constant:36],
        [logoPlate.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [logoPlate.widthAnchor constraintEqualToConstant:100],
        [logoPlate.heightAnchor constraintEqualToConstant:100],
        [logo.widthAnchor constraintEqualToConstant:62],
        [logo.heightAnchor constraintEqualToConstant:62],
        [logo.centerXAnchor constraintEqualToAnchor:logoPlate.centerXAnchor],
        [logo.centerYAnchor constraintEqualToAnchor:logoPlate.centerYAnchor],
        [appName.topAnchor constraintEqualToAnchor:logoPlate.bottomAnchor constant:23],
        [appName.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [rows.topAnchor constraintEqualToAnchor:appName.bottomAnchor constant:90],
        [rows.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:16],
        [rows.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor constant:-16],
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

- (UIView *)valueCardWithTitle:(NSString *)title value:(NSString *)value {
    UIView *card = [self cardContainer];
    UILabel *label = [[UILabel alloc] init];
    label.translatesAutoresizingMaskIntoConstraints = NO;
    label.text = title;
    label.font = [UIFont systemFontOfSize:16];
    label.textColor = [UIColor colorWithWhite:0 alpha:0.9];
    [card addSubview:label];

    UILabel *detail = [[UILabel alloc] init];
    detail.translatesAutoresizingMaskIntoConstraints = NO;
    detail.text = value;
    detail.font = [UIFont systemFontOfSize:12];
    detail.textColor = [UIColor colorWithWhite:0xB5 / 255.0 alpha:1];
    detail.textAlignment = NSTextAlignmentRight;
    [card addSubview:detail];

    [NSLayoutConstraint activateConstraints:@[
        [label.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:12],
        [label.centerYAnchor constraintEqualToAnchor:card.centerYAnchor],
        [detail.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-12],
        [detail.centerYAnchor constraintEqualToAnchor:card.centerYAnchor],
        [detail.leadingAnchor constraintGreaterThanOrEqualToAnchor:label.trailingAnchor constant:8],
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
    card.contentEdgeInsets = UIEdgeInsetsMake(0, 12, 0, 12);
    [card.heightAnchor constraintEqualToConstant:55].active = YES;
    [card addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];

    UILabel *label = [[UILabel alloc] init];
    label.translatesAutoresizingMaskIntoConstraints = NO;
    label.text = title;
    label.font = [UIFont systemFontOfSize:16];
    label.textColor = [UIColor colorWithWhite:0 alpha:0.9];
    label.userInteractionEnabled = NO;
    [card addSubview:label];

    UILabel *detail = nil;
    if (subtitle.length > 0) {
        detail = [[UILabel alloc] init];
        detail.translatesAutoresizingMaskIntoConstraints = NO;
        detail.text = subtitle;
        detail.font = [UIFont systemFontOfSize:12];
        detail.textColor = [UIColor colorWithWhite:0xB5 / 255.0 alpha:1];
        detail.userInteractionEnabled = NO;
        [card addSubview:detail];
    }

    // 设计稿是一枚细箭头图标，不是「›」字形
    UIImageView *chevron = [[UIImageView alloc] initWithImage:
        [AISettingsDrawerIcons iconNamed:@"chevron-right" size:16]];
    chevron.translatesAutoresizingMaskIntoConstraints = NO;
    chevron.userInteractionEnabled = NO;
    [card addSubview:chevron];

    NSMutableArray *constraints = [NSMutableArray arrayWithObjects:
        [label.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:12],
        [label.centerYAnchor constraintEqualToAnchor:card.centerYAnchor],
        [chevron.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-12],
        [chevron.centerYAnchor constraintEqualToAnchor:card.centerYAnchor],
        [chevron.widthAnchor constraintEqualToConstant:16],
        [chevron.heightAnchor constraintEqualToConstant:16],
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
    vc.modalPresentationStyle = UIModalPresentationFullScreen;
    [self presentViewController:vc animated:YES completion:nil];
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
