// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "LocalModelViewController.h"

#import "AI/WriterAIComponents.h"
#import "LocalModelStore.h"
#import "Settings/AppChromeHelper.h"
#import "Settings/AppToastPresenter.h"

#import <objc/runtime.h>

static char kLocalModelEntryAssociationKey;

static UIColor *LocalModelTitleColor(void) {
    return [UIColor colorWithRed:0x1F / 255.0 green:0x1F / 255.0 blue:0x1F / 255.0 alpha:1];
}

static UIColor *LocalModelBodyColor(void) {
    return [UIColor colorWithRed:0x33 / 255.0 green:0x33 / 255.0 blue:0x33 / 255.0 alpha:1];
}

static UIColor *LocalModelMutedColor(void) {
    return [UIColor colorWithRed:0x6A / 255.0 green:0x6A / 255.0 blue:0x6A / 255.0 alpha:1];
}

@interface LocalModelViewController () <UIGestureRecognizerDelegate>
@property (strong, nonatomic) LocalModelStore *store;
@property (strong, nonatomic) UIView *sheetView;
@property (strong, nonatomic) UILabel *statusLabel;
@property (strong, nonatomic) UILabel *deviceInfoLabel;
@property (strong, nonatomic) UILabel *deviceVerdictLabel;
@property (strong, nonatomic) UIProgressView *progressView;
@property (strong, nonatomic) UILabel *progressLabel;
@property (strong, nonatomic) UIView *urlRow;
@property (strong, nonatomic) UILabel *urlValueLabel;
@property (strong, nonatomic) UIButton *modelUrlCopyButton;
@property (strong, nonatomic) NSLayoutConstraint *divider2TopFromProgress;
@property (strong, nonatomic) NSLayoutConstraint *divider2TopFromUrl;
@property (strong, nonatomic) UISwitch *enableSwitch;
@property (strong, nonatomic) UIButton *chooseButton;
@property (strong, nonatomic) UIButton *downloadButton;
@property (strong, nonatomic) UIButton *cancelButton;
@property (strong, nonatomic) UIButton *deleteButton;
@property (strong, nonatomic) NSLayoutConstraint *downloadHeightConstraint;
@property (strong, nonatomic) NSLayoutConstraint *cancelHeightConstraint;
@property (strong, nonatomic) NSLayoutConstraint *deleteHeightConstraint;
@property (strong, nonatomic) UIView *listOverlay;
@property (strong, nonatomic) UIScrollView *listScrollView;
@property (strong, nonatomic) UIStackView *listStack;
@end

@implementation LocalModelViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.store = [LocalModelStore shared];
    self.view.backgroundColor = [UIColor colorWithWhite:0 alpha:0.35];
    self.modalPresentationStyle = UIModalPresentationOverFullScreen;
    self.modalTransitionStyle = UIModalTransitionStyleCrossDissolve;

    UITapGestureRecognizer *dismissTap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(close)];
    dismissTap.delegate = self;
    [self.view addGestureRecognizer:dismissTap];

    UIView *sheet = [[UIView alloc] init];
    sheet.translatesAutoresizingMaskIntoConstraints = NO;
    sheet.backgroundColor = UIColor.whiteColor;
    sheet.layer.cornerRadius = 16;
    sheet.layer.maskedCorners = kCALayerMinXMinYCorner | kCALayerMaxXMinYCorner;
    sheet.clipsToBounds = YES;
    [self.view addSubview:sheet];
    self.sheetView = sheet;

    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"本地模型";
    title.font = [UIFont systemFontOfSize:20 weight:UIFontWeightMedium];
    title.textColor = LocalModelTitleColor();
    title.textAlignment = NSTextAlignmentCenter;
    [sheet addSubview:title];

    UIButton *close = [UIButton buttonWithType:UIButtonTypeSystem];
    close.translatesAutoresizingMaskIntoConstraints = NO;
    UIImage *closeIcon = [[UIImage writerIconNamed:@"close"] imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
    [close setImage:closeIcon forState:UIControlStateNormal];
    close.tintColor = [UIColor colorWithWhite:0.35 alpha:1];
    [close addTarget:self action:@selector(close) forControlEvents:UIControlEventTouchUpInside];
    [sheet addSubview:close];

    UIView *divider = [[UIView alloc] init];
    divider.translatesAutoresizingMaskIntoConstraints = NO;
    divider.backgroundColor = [UIColor colorWithRed:0xD8 / 255.0 green:0xD8 / 255.0 blue:0xD8 / 255.0 alpha:1];
    [sheet addSubview:divider];

    UILabel *hint = [[UILabel alloc] init];
    hint.translatesAutoresizingMaskIntoConstraints = NO;
    hint.text = @"本地模式支持对话、续写、润色等；文档问答建议联网。";
    hint.font = [UIFont systemFontOfSize:12];
    hint.textColor = LocalModelMutedColor();
    hint.numberOfLines = 0;
    [sheet addSubview:hint];

    UILabel *deviceTitle = [[UILabel alloc] init];
    deviceTitle.translatesAutoresizingMaskIntoConstraints = NO;
    deviceTitle.text = @"本机配置";
    deviceTitle.font = [UIFont boldSystemFontOfSize:14];
    deviceTitle.textColor = LocalModelBodyColor();
    [sheet addSubview:deviceTitle];

    self.deviceInfoLabel = [[UILabel alloc] init];
    self.deviceInfoLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.deviceInfoLabel.font = [UIFont systemFontOfSize:12];
    self.deviceInfoLabel.textColor = LocalModelMutedColor();
    self.deviceInfoLabel.numberOfLines = 0;
    self.deviceInfoLabel.text = [LocalModelStore deviceInfoText];
    [sheet addSubview:self.deviceInfoLabel];

    self.deviceVerdictLabel = [[UILabel alloc] init];
    self.deviceVerdictLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.deviceVerdictLabel.font = [UIFont systemFontOfSize:13];
    self.deviceVerdictLabel.textColor = LocalModelBodyColor();
    self.deviceVerdictLabel.numberOfLines = 0;
    self.deviceVerdictLabel.text = [LocalModelStore deviceVerdictText];
    [sheet addSubview:self.deviceVerdictLabel];

    self.statusLabel = [[UILabel alloc] init];
    self.statusLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.statusLabel.font = [UIFont systemFontOfSize:14];
    self.statusLabel.textColor = LocalModelBodyColor();
    self.statusLabel.numberOfLines = 0;
    [sheet addSubview:self.statusLabel];

    self.progressView = [[UIProgressView alloc] initWithProgressViewStyle:UIProgressViewStyleDefault];
    self.progressView.translatesAutoresizingMaskIntoConstraints = NO;
    self.progressView.progressTintColor = [AppChromeHelper homeFabColor];
    self.progressView.hidden = YES;
    [sheet addSubview:self.progressView];

    self.progressLabel = [[UILabel alloc] init];
    self.progressLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.progressLabel.font = [UIFont systemFontOfSize:12];
    self.progressLabel.textColor = LocalModelMutedColor();
    self.progressLabel.hidden = YES;
    [sheet addSubview:self.progressLabel];

    self.urlRow = [[UIView alloc] init];
    self.urlRow.translatesAutoresizingMaskIntoConstraints = NO;
    self.urlRow.hidden = YES;
    [sheet addSubview:self.urlRow];

    UILabel *urlTitle = [[UILabel alloc] init];
    urlTitle.translatesAutoresizingMaskIntoConstraints = NO;
    urlTitle.text = @"URL";
    urlTitle.font = [UIFont systemFontOfSize:12 weight:UIFontWeightMedium];
    urlTitle.textColor = LocalModelMutedColor();
    [self.urlRow addSubview:urlTitle];

    self.urlValueLabel = [[UILabel alloc] init];
    self.urlValueLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.urlValueLabel.font = [UIFont systemFontOfSize:11];
    self.urlValueLabel.textColor = LocalModelBodyColor();
    self.urlValueLabel.numberOfLines = 2;
    self.urlValueLabel.lineBreakMode = NSLineBreakByTruncatingMiddle;
    [self.urlRow addSubview:self.urlValueLabel];

    self.modelUrlCopyButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.modelUrlCopyButton.translatesAutoresizingMaskIntoConstraints = NO;
    [self.modelUrlCopyButton setTitle:@"复制" forState:UIControlStateNormal];
    self.modelUrlCopyButton.titleLabel.font = [UIFont systemFontOfSize:13];
    [self.modelUrlCopyButton setTitleColor:[AppChromeHelper homeFabColor] forState:UIControlStateNormal];
    [self.modelUrlCopyButton addTarget:self action:@selector(copyModelUrl) forControlEvents:UIControlEventTouchUpInside];
    [self.urlRow addSubview:self.modelUrlCopyButton];

    UIView *divider2 = [[UIView alloc] init];
    divider2.translatesAutoresizingMaskIntoConstraints = NO;
    divider2.backgroundColor = divider.backgroundColor;
    [sheet addSubview:divider2];

    self.enableSwitch = [[UISwitch alloc] init];
    self.enableSwitch.translatesAutoresizingMaskIntoConstraints = NO;
    [self.enableSwitch addTarget:self action:@selector(toggleEnabled) forControlEvents:UIControlEventValueChanged];
    [sheet addSubview:self.enableSwitch];
    UILabel *enableLabel = [[UILabel alloc] init];
    enableLabel.translatesAutoresizingMaskIntoConstraints = NO;
    enableLabel.text = @"启用本地推理";
    enableLabel.font = [UIFont systemFontOfSize:14];
    enableLabel.textColor = LocalModelBodyColor();
    [sheet addSubview:enableLabel];

    self.chooseButton = [self textActionButton:@"选择模型" color:LocalModelBodyColor() action:@selector(showModelList)];
    self.downloadButton = [self textActionButton:@"下载推荐模型" color:LocalModelBodyColor() action:@selector(downloadDefault)];
    self.cancelButton = [self textActionButton:@"取消下载" color:[AppChromeHelper homeFabColor] action:@selector(cancelDownload)];
    self.deleteButton = [self textActionButton:@"删除模型" color:[UIColor colorWithRed:0xD9 / 255.0 green:0x30 / 255.0 blue:0x25 / 255.0 alpha:1] action:@selector(deleteModel)];

    [sheet addSubview:self.chooseButton];
    [sheet addSubview:self.downloadButton];
    [sheet addSubview:self.cancelButton];
    [sheet addSubview:self.deleteButton];

    UIButton *confirm = [UIButton buttonWithType:UIButtonTypeSystem];
    confirm.translatesAutoresizingMaskIntoConstraints = NO;
    confirm.backgroundColor = [AppChromeHelper homeFabColor];
    confirm.layer.cornerRadius = 22;
    [confirm setTitle:@"确定" forState:UIControlStateNormal];
    [confirm setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    confirm.titleLabel.font = [UIFont systemFontOfSize:16];
    [confirm addTarget:self action:@selector(close) forControlEvents:UIControlEventTouchUpInside];
    [sheet addSubview:confirm];

    [NSLayoutConstraint activateConstraints:@[
        [sheet.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [sheet.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [sheet.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
        [title.topAnchor constraintEqualToAnchor:sheet.topAnchor constant:18],
        [title.leadingAnchor constraintEqualToAnchor:sheet.leadingAnchor constant:16],
        [title.trailingAnchor constraintEqualToAnchor:sheet.trailingAnchor constant:-16],
        [close.centerYAnchor constraintEqualToAnchor:title.centerYAnchor],
        [close.trailingAnchor constraintEqualToAnchor:sheet.trailingAnchor constant:-12],
        [close.widthAnchor constraintEqualToConstant:40],
        [close.heightAnchor constraintEqualToConstant:40],
        [divider.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:18],
        [divider.leadingAnchor constraintEqualToAnchor:sheet.leadingAnchor],
        [divider.trailingAnchor constraintEqualToAnchor:sheet.trailingAnchor],
        [divider.heightAnchor constraintEqualToConstant:1],
        [hint.topAnchor constraintEqualToAnchor:divider.bottomAnchor constant:16],
        [hint.leadingAnchor constraintEqualToAnchor:sheet.leadingAnchor constant:20],
        [hint.trailingAnchor constraintEqualToAnchor:sheet.trailingAnchor constant:-20],
        [deviceTitle.topAnchor constraintEqualToAnchor:hint.bottomAnchor constant:16],
        [deviceTitle.leadingAnchor constraintEqualToAnchor:hint.leadingAnchor],
        [self.deviceInfoLabel.topAnchor constraintEqualToAnchor:deviceTitle.bottomAnchor constant:8],
        [self.deviceInfoLabel.leadingAnchor constraintEqualToAnchor:hint.leadingAnchor],
        [self.deviceInfoLabel.trailingAnchor constraintEqualToAnchor:hint.trailingAnchor],
        [self.deviceVerdictLabel.topAnchor constraintEqualToAnchor:self.deviceInfoLabel.bottomAnchor constant:8],
        [self.deviceVerdictLabel.leadingAnchor constraintEqualToAnchor:hint.leadingAnchor],
        [self.deviceVerdictLabel.trailingAnchor constraintEqualToAnchor:hint.trailingAnchor],
        [self.statusLabel.topAnchor constraintEqualToAnchor:self.deviceVerdictLabel.bottomAnchor constant:16],
        [self.statusLabel.leadingAnchor constraintEqualToAnchor:hint.leadingAnchor],
        [self.statusLabel.trailingAnchor constraintEqualToAnchor:hint.trailingAnchor],
        [self.progressView.topAnchor constraintEqualToAnchor:self.statusLabel.bottomAnchor constant:12],
        [self.progressView.leadingAnchor constraintEqualToAnchor:hint.leadingAnchor],
        [self.progressView.trailingAnchor constraintEqualToAnchor:hint.trailingAnchor],
        [self.progressLabel.topAnchor constraintEqualToAnchor:self.progressView.bottomAnchor constant:4],
        [self.progressLabel.leadingAnchor constraintEqualToAnchor:hint.leadingAnchor],
        [self.urlRow.topAnchor constraintEqualToAnchor:self.progressLabel.bottomAnchor constant:12],
        [self.urlRow.leadingAnchor constraintEqualToAnchor:hint.leadingAnchor],
        [self.urlRow.trailingAnchor constraintEqualToAnchor:hint.trailingAnchor],
        [urlTitle.topAnchor constraintEqualToAnchor:self.urlRow.topAnchor],
        [urlTitle.leadingAnchor constraintEqualToAnchor:self.urlRow.leadingAnchor],
        [self.modelUrlCopyButton.centerYAnchor constraintEqualToAnchor:urlTitle.centerYAnchor],
        [self.modelUrlCopyButton.trailingAnchor constraintEqualToAnchor:self.urlRow.trailingAnchor],
        [self.urlValueLabel.topAnchor constraintEqualToAnchor:urlTitle.bottomAnchor constant:4],
        [self.urlValueLabel.leadingAnchor constraintEqualToAnchor:self.urlRow.leadingAnchor],
        [self.urlValueLabel.trailingAnchor constraintEqualToAnchor:self.urlRow.trailingAnchor],
        [self.urlValueLabel.bottomAnchor constraintEqualToAnchor:self.urlRow.bottomAnchor],
        [divider2.leadingAnchor constraintEqualToAnchor:sheet.leadingAnchor],
        [divider2.trailingAnchor constraintEqualToAnchor:sheet.trailingAnchor],
        [divider2.heightAnchor constraintEqualToConstant:1],
        [enableLabel.leadingAnchor constraintEqualToAnchor:hint.leadingAnchor],
        [enableLabel.centerYAnchor constraintEqualToAnchor:self.enableSwitch.centerYAnchor],
        [self.enableSwitch.topAnchor constraintEqualToAnchor:divider2.bottomAnchor constant:16],
        [self.enableSwitch.trailingAnchor constraintEqualToAnchor:hint.trailingAnchor],
        [self.chooseButton.topAnchor constraintEqualToAnchor:self.enableSwitch.bottomAnchor constant:16],
        [self.chooseButton.leadingAnchor constraintEqualToAnchor:hint.leadingAnchor],
        [self.chooseButton.trailingAnchor constraintEqualToAnchor:hint.trailingAnchor],
        [self.downloadButton.topAnchor constraintEqualToAnchor:self.chooseButton.bottomAnchor constant:16],
        [self.downloadButton.leadingAnchor constraintEqualToAnchor:hint.leadingAnchor],
        [self.downloadButton.trailingAnchor constraintEqualToAnchor:hint.trailingAnchor],
        [self.cancelButton.topAnchor constraintEqualToAnchor:self.downloadButton.bottomAnchor constant:0],
        [self.cancelButton.leadingAnchor constraintEqualToAnchor:hint.leadingAnchor],
        [self.cancelButton.trailingAnchor constraintEqualToAnchor:hint.trailingAnchor],
        [self.deleteButton.topAnchor constraintEqualToAnchor:self.cancelButton.bottomAnchor constant:0],
        [self.deleteButton.leadingAnchor constraintEqualToAnchor:hint.leadingAnchor],
        [self.deleteButton.trailingAnchor constraintEqualToAnchor:hint.trailingAnchor],
        [confirm.topAnchor constraintEqualToAnchor:self.deleteButton.bottomAnchor constant:12],
        [confirm.leadingAnchor constraintEqualToAnchor:hint.leadingAnchor],
        [confirm.trailingAnchor constraintEqualToAnchor:hint.trailingAnchor],
        [confirm.heightAnchor constraintEqualToConstant:44],
        [confirm.bottomAnchor constraintEqualToAnchor:sheet.safeAreaLayoutGuide.bottomAnchor constant:-20],
    ]];
    self.downloadHeightConstraint = [self.downloadButton.heightAnchor constraintEqualToConstant:38];
    self.cancelHeightConstraint = [self.cancelButton.heightAnchor constraintEqualToConstant:38];
    self.deleteHeightConstraint = [self.deleteButton.heightAnchor constraintEqualToConstant:38];
    self.downloadHeightConstraint.active = YES;
    self.cancelHeightConstraint.active = YES;
    self.deleteHeightConstraint.active = YES;

    self.divider2TopFromProgress = [divider2.topAnchor constraintEqualToAnchor:self.progressLabel.bottomAnchor constant:16];
    self.divider2TopFromUrl = [divider2.topAnchor constraintEqualToAnchor:self.urlRow.bottomAnchor constant:16];
    self.divider2TopFromProgress.active = YES;
    self.divider2TopFromUrl.active = NO;

    [self buildListOverlay];
    [self refreshUi];
}

- (void)copyModelUrl {
    NSString *url = [self.store primaryDownloadURLForInstalledEntry];
    if (url.length == 0) {
        LocalModelCatalogEntry *entry = [self.store installedEntry];
        if (entry == nil) {
            entry = [LocalModelStore defaultCatalogEntry];
        }
        url = [LocalModelStore primaryDownloadURLForEntry:entry];
    }
    if (url.length == 0) {
        return;
    }
    [UIPasteboard generalPasteboard].string = url;
    [AppToastPresenter showMessage:@"URL 已复制" from:self];
}

- (UIButton *)textActionButton:(NSString *)title color:(UIColor *)color action:(SEL)action {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    button.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeading;
    button.contentEdgeInsets = UIEdgeInsetsMake(12, 0, 12, 0);
    [button setTitle:title forState:UIControlStateNormal];
    [button setTitleColor:color forState:UIControlStateNormal];
    button.titleLabel.font = [UIFont systemFontOfSize:14];
    [button addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];
    return button;
}

- (UIButton *)listActionPillWithTitle:(NSString *)title
                            textColor:(UIColor *)textColor
                      backgroundColor:(UIColor *)backgroundColor
                             enabled:(BOOL)enabled
                               action:(SEL)action
                                entry:(LocalModelCatalogEntry *)entry {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    button.contentEdgeInsets = UIEdgeInsetsMake(0, 12, 0, 12);
    [button setTitle:title forState:UIControlStateNormal];
    [button setTitleColor:textColor forState:UIControlStateNormal];
    button.titleLabel.font = [UIFont systemFontOfSize:14];
    button.backgroundColor = backgroundColor;
    button.layer.cornerRadius = 15.5;
    button.clipsToBounds = YES;
    button.enabled = enabled;
    if (action != NULL) {
        [button addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];
        objc_setAssociatedObject(button, &kLocalModelEntryAssociationKey, entry, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    }
    [NSLayoutConstraint activateConstraints:@[
        [button.heightAnchor constraintEqualToConstant:31],
        [button.widthAnchor constraintGreaterThanOrEqualToConstant:72],
    ]];
    return button;
}

- (void)buildListOverlay {
    self.listOverlay = [[UIView alloc] initWithFrame:self.view.bounds];
    self.listOverlay.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    self.listOverlay.backgroundColor = [UIColor colorWithWhite:0 alpha:0.35];
    self.listOverlay.hidden = YES;

    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.backgroundColor = UIColor.whiteColor;
    card.layer.cornerRadius = 12;
    card.layer.shadowColor = UIColor.blackColor.CGColor;
    card.layer.shadowOpacity = 0.12;
    card.layer.shadowRadius = 8;
    card.layer.shadowOffset = CGSizeMake(0, 4);
    [self.listOverlay addSubview:card];

    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"本地模型";
    title.font = [UIFont systemFontOfSize:20 weight:UIFontWeightMedium];
    title.textColor = LocalModelTitleColor();
    title.textAlignment = NSTextAlignmentCenter;
    [card addSubview:title];

    UIButton *closeList = [UIButton buttonWithType:UIButtonTypeSystem];
    closeList.translatesAutoresizingMaskIntoConstraints = NO;
    UIImage *closeIcon = [[UIImage writerIconNamed:@"close"] imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
    [closeList setImage:closeIcon forState:UIControlStateNormal];
    closeList.tintColor = [UIColor colorWithWhite:0.35 alpha:1];
    [closeList addTarget:self action:@selector(hideModelList) forControlEvents:UIControlEventTouchUpInside];
    [card addSubview:closeList];

    self.listScrollView = [[UIScrollView alloc] init];
    self.listScrollView.translatesAutoresizingMaskIntoConstraints = NO;
    [card addSubview:self.listScrollView];

    self.listStack = [[UIStackView alloc] init];
    self.listStack.translatesAutoresizingMaskIntoConstraints = NO;
    self.listStack.axis = UILayoutConstraintAxisVertical;
    self.listStack.spacing = 0;
    [self.listScrollView addSubview:self.listStack];

    UIButton *confirmList = [UIButton buttonWithType:UIButtonTypeSystem];
    confirmList.translatesAutoresizingMaskIntoConstraints = NO;
    confirmList.backgroundColor = [AppChromeHelper homeFabColor];
    confirmList.layer.cornerRadius = 17.5;
    [confirmList setTitle:@"确定" forState:UIControlStateNormal];
    [confirmList setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    confirmList.titleLabel.font = [UIFont systemFontOfSize:16];
    [confirmList addTarget:self action:@selector(hideModelList) forControlEvents:UIControlEventTouchUpInside];
    [card addSubview:confirmList];

    [NSLayoutConstraint activateConstraints:@[
        [card.centerXAnchor constraintEqualToAnchor:self.listOverlay.centerXAnchor],
        [card.centerYAnchor constraintEqualToAnchor:self.listOverlay.centerYAnchor],
        [card.leadingAnchor constraintGreaterThanOrEqualToAnchor:self.listOverlay.leadingAnchor constant:20],
        [card.trailingAnchor constraintLessThanOrEqualToAnchor:self.listOverlay.trailingAnchor constant:-20],
        [card.widthAnchor constraintEqualToConstant:335],
        [title.topAnchor constraintEqualToAnchor:card.topAnchor],
        [title.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:16],
        [title.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-16],
        [title.heightAnchor constraintEqualToConstant:60],
        [closeList.centerYAnchor constraintEqualToAnchor:title.centerYAnchor],
        [closeList.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-16],
        [closeList.widthAnchor constraintEqualToConstant:40],
        [closeList.heightAnchor constraintEqualToConstant:40],
        [self.listScrollView.topAnchor constraintEqualToAnchor:title.bottomAnchor],
        [self.listScrollView.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:12],
        [self.listScrollView.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-12],
        [self.listScrollView.heightAnchor constraintLessThanOrEqualToConstant:464],
        [confirmList.topAnchor constraintEqualToAnchor:self.listScrollView.bottomAnchor constant:12],
        [confirmList.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:20],
        [confirmList.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-20],
        [confirmList.heightAnchor constraintEqualToConstant:35],
        [confirmList.bottomAnchor constraintEqualToAnchor:card.bottomAnchor constant:-12],
        [self.listStack.topAnchor constraintEqualToAnchor:self.listScrollView.topAnchor constant:12],
        [self.listStack.leadingAnchor constraintEqualToAnchor:self.listScrollView.leadingAnchor],
        [self.listStack.trailingAnchor constraintEqualToAnchor:self.listScrollView.trailingAnchor],
        [self.listStack.bottomAnchor constraintEqualToAnchor:self.listScrollView.bottomAnchor constant:-12],
        [self.listStack.widthAnchor constraintEqualToAnchor:self.listScrollView.widthAnchor],
    ]];
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    if (self.listOverlay.superview == nil) {
        [self.view addSubview:self.listOverlay];
    }
}

- (void)setActionRow:(UIButton *)button hidden:(BOOL)hidden {
    button.hidden = hidden;
    NSLayoutConstraint *heightConstraint = nil;
    if (button == self.downloadButton) {
        heightConstraint = self.downloadHeightConstraint;
    } else if (button == self.cancelButton) {
        heightConstraint = self.cancelHeightConstraint;
    } else if (button == self.deleteButton) {
        heightConstraint = self.deleteHeightConstraint;
    }
    heightConstraint.constant = hidden ? 0 : 38;
}

- (void)updateUrlRowVisible:(BOOL)visible urlText:(NSString *)urlText {
    self.urlRow.hidden = !visible;
    self.divider2TopFromProgress.active = !visible;
    self.divider2TopFromUrl.active = visible;
    self.urlValueLabel.text = urlText ?: @"";
}

- (void)refreshUi {
    BOOL supported = [LocalModelStore isDeviceSupported];
    self.chooseButton.enabled = supported;
    self.downloadButton.enabled = supported;

    if (!supported) {
        [self updateUrlRowVisible:NO urlText:nil];
        self.statusLabel.text = @"当前设备不支持本地推理（需 arm64 且内存 4GB 以上）。";
        self.progressView.hidden = YES;
        self.progressLabel.hidden = YES;
        self.enableSwitch.enabled = NO;
        self.enableSwitch.on = NO;
        [self setActionRow:self.cancelButton hidden:YES];
        [self setActionRow:self.deleteButton hidden:YES];
        [self setActionRow:self.downloadButton hidden:YES];
        return;
    }

    NSString *state = [self.store downloadState];
    LocalModelCatalogEntry *installed = [self.store installedEntry];

    if ([state isEqualToString:LocalModelStateDownloading]) {
        [self updateUrlRowVisible:NO urlText:nil];
        if ([self.store isDownloadActive]) {
            NSInteger percent = [self.store downloadProgressPercent];
            self.statusLabel.text = @"正在下载模型…";
            self.progressView.hidden = NO;
            self.progressLabel.hidden = NO;
            self.progressView.progress = percent / 100.0f;
            self.progressLabel.text = [NSString stringWithFormat:@"下载中… %ld%%", (long)percent];
            [self setActionRow:self.cancelButton hidden:NO];
            [self setActionRow:self.deleteButton hidden:YES];
            [self setActionRow:self.downloadButton hidden:YES];
            self.enableSwitch.enabled = NO;
        } else {
            self.statusLabel.text = @"上次下载已中断，点击重新下载可续传";
            self.progressView.hidden = YES;
            self.progressLabel.hidden = YES;
            [self setActionRow:self.cancelButton hidden:YES];
            [self setActionRow:self.deleteButton hidden:YES];
            [self setActionRow:self.downloadButton hidden:NO];
            self.downloadButton.enabled = YES;
            [self.downloadButton setTitle:@"重新下载模型" forState:UIControlStateNormal];
            self.enableSwitch.enabled = NO;
            self.enableSwitch.on = NO;
        }
        return;
    }

    if (installed != nil) {
        self.statusLabel.text = [NSString stringWithFormat:@"已安装：%@", installed.displayName];
        self.progressView.hidden = YES;
        self.progressLabel.hidden = YES;
        [self updateUrlRowVisible:YES urlText:[LocalModelStore primaryDownloadURLForEntry:installed]];
        [self setActionRow:self.cancelButton hidden:YES];
        [self setActionRow:self.deleteButton hidden:NO];
        [self setActionRow:self.downloadButton hidden:NO];
        self.downloadButton.enabled = YES;
        [self.downloadButton setTitle:@"重新下载模型" forState:UIControlStateNormal];
        self.enableSwitch.enabled = YES;
        self.enableSwitch.on = [self.store isEnabled];
        return;
    }

    [self updateUrlRowVisible:NO urlText:nil];
    self.progressView.hidden = YES;
    self.progressLabel.hidden = YES;
    [self setActionRow:self.cancelButton hidden:YES];
    [self setActionRow:self.deleteButton hidden:YES];
    [self setActionRow:self.downloadButton hidden:NO];
    self.downloadButton.enabled = YES;
    [self.downloadButton setTitle:@"下载推荐模型" forState:UIControlStateNormal];

    if ([self.store hasAnyDownloadedModel]) {
        self.statusLabel.text = @"已有可用模型，请在列表中选择要使用的模型";
        self.enableSwitch.enabled = NO;
        self.enableSwitch.on = NO;
    } else {
        self.statusLabel.text = @"尚未安装本地模型。";
        self.enableSwitch.enabled = NO;
        self.enableSwitch.on = NO;
    }
}

- (void)refreshModelList {
    for (UIView *sub in self.listStack.arrangedSubviews) {
        [self.listStack removeArrangedSubview:sub];
        [sub removeFromSuperview];
    }

    UIColor *accent = [AppChromeHelper homeFabColor];
    UIColor *pillBg = [accent colorWithAlphaComponent:0.12];
    UIColor *muted = [UIColor colorWithRed:0x99 / 255.0 green:0x99 / 255.0 blue:0x99 / 255.0 alpha:1];

    for (LocalModelCatalogEntry *entry in [LocalModelStore catalogEntries]) {
        UIStackView *row = [[UIStackView alloc] init];
        row.axis = UILayoutConstraintAxisHorizontal;
        row.spacing = 16;
        row.alignment = UIStackViewAlignmentCenter;
        row.layoutMargins = UIEdgeInsetsMake(14, 10, 14, 10);
        row.layoutMarginsRelativeArrangement = YES;

        UILabel *name = [[UILabel alloc] init];
        name.text = entry.displayName;
        name.font = [UIFont systemFontOfSize:16];
        name.textColor = LocalModelBodyColor();
        name.numberOfLines = 2;
        [name setContentHuggingPriority:UILayoutPriorityDefaultLow forAxis:UILayoutConstraintAxisHorizontal];

        UIButton *action = nil;
        if ([self.store isEntryDownloading:entry]) {
            action = [self listActionPillWithTitle:[NSString stringWithFormat:@"下载中 %ld%%", (long)[self.store downloadProgressPercent]]
                                         textColor:accent
                                   backgroundColor:pillBg
                                           enabled:NO
                                            action:NULL
                                             entry:nil];
        } else if ([self.store isEntryActive:entry]) {
            action = [self listActionPillWithTitle:@"使用中"
                                         textColor:accent
                                   backgroundColor:UIColor.clearColor
                                           enabled:NO
                                            action:NULL
                                             entry:nil];
        } else if ([self.store isEntryDownloaded:entry]) {
            action = [self listActionPillWithTitle:@"可用"
                                         textColor:accent
                                   backgroundColor:UIColor.clearColor
                                           enabled:YES
                                            action:@selector(selectModelFromList:)
                                             entry:entry];
        } else if (![LocalModelStore canDownloadModel:entry]) {
            action = [self listActionPillWithTitle:@"配置偏低"
                                         textColor:muted
                                   backgroundColor:[UIColor colorWithWhite:0.94 alpha:1]
                                           enabled:YES
                                            action:@selector(showModelCapabilityToast:)
                                             entry:entry];
        } else {
            action = [self listActionPillWithTitle:@"下载"
                                         textColor:accent
                                   backgroundColor:pillBg
                                           enabled:YES
                                            action:@selector(downloadModelFromList:)
                                             entry:entry];
        }

        [row addArrangedSubview:name];
        [row addArrangedSubview:action];
        [self.listStack addArrangedSubview:row];
        [row.heightAnchor constraintEqualToConstant:59].active = YES;

        UIView *divider = [[UIView alloc] init];
        divider.translatesAutoresizingMaskIntoConstraints = NO;
        divider.backgroundColor = [UIColor colorWithRed:0xD8 / 255.0 green:0xD8 / 255.0 blue:0xD8 / 255.0 alpha:1];
        [self.listStack addArrangedSubview:divider];
        [divider.heightAnchor constraintEqualToConstant:1].active = YES;
    }
}

- (void)toggleEnabled {
    if (![self.store isInstalled]) {
        self.enableSwitch.on = NO;
        return;
    }
    if (!self.enableSwitch.on) {
        [self.store setEnabled:NO];
        return;
    }
    LocalModelCatalogEntry *active = [self.store installedEntry];
    if (active != nil && [LocalModelStore isModelRamMarginal:active]) {
        [AppToastPresenter showMessage:[LocalModelStore modelCapabilityMessageForEntry:active] from:self];
    } else if ([LocalModelStore slowCpuWarning]) {
        [AppToastPresenter showMessage:@"CPU 核心较少，本地推理可能较慢" from:self];
    }
    [self.store setEnabled:YES];
}

- (void)downloadDefault {
    [self startDownload:[LocalModelStore defaultCatalogEntry]];
}

- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer shouldReceiveTouch:(UITouch *)touch {
    return touch.view == self.view;
}

- (void)showModelCapabilityToast:(UIButton *)sender {
    LocalModelCatalogEntry *entry = objc_getAssociatedObject(sender, &kLocalModelEntryAssociationKey);
    [AppToastPresenter showMessage:[LocalModelStore modelCapabilityMessageForEntry:entry] from:self];
}

- (void)downloadModelFromList:(UIButton *)sender {
    LocalModelCatalogEntry *entry = objc_getAssociatedObject(sender, &kLocalModelEntryAssociationKey);
    [self hideModelList];
    [self startDownload:entry];
}

- (void)selectModelFromList:(UIButton *)sender {
    LocalModelCatalogEntry *entry = objc_getAssociatedObject(sender, &kLocalModelEntryAssociationKey);
    [self.store selectActiveModel:entry];
    [self hideModelList];
    [self refreshUi];
    [AppToastPresenter showMessage:[NSString stringWithFormat:@"已切换至 %@", entry.displayName] from:self];
}

- (void)startDownload:(LocalModelCatalogEntry *)entry {
    __weak __typeof(self) weakSelf = self;
    [self.store downloadModel:entry progress:^(NSInteger percent) {
        [weakSelf refreshUi];
        if (!weakSelf.listOverlay.hidden) {
            [weakSelf refreshModelList];
        }
    } completion:^(BOOL success, NSString *message) {
        [weakSelf refreshUi];
        if (!weakSelf.listOverlay.hidden) {
            [weakSelf refreshModelList];
        }
        if (message.length > 0) {
            [AppToastPresenter showMessage:message from:weakSelf];
        }
        if (weakSelf.onDismiss) {
            weakSelf.onDismiss();
        }
    }];
    [self refreshUi];
}

- (void)cancelDownload {
    [self.store cancelDownload];
    [self refreshUi];
    [AppToastPresenter showMessage:@"取消下载" from:self];
}

- (void)deleteModel {
    [self.store deleteModel];
    [self refreshUi];
    [AppToastPresenter showMessage:@"已删除本地模型。" from:self];
    if (self.onDismiss) {
        self.onDismiss();
    }
}

- (void)showModelList {
    [self refreshModelList];
    self.sheetView.hidden = YES;
    self.listOverlay.hidden = NO;
    [self.view bringSubviewToFront:self.listOverlay];
}

- (void)hideModelList {
    self.listOverlay.hidden = YES;
    self.sheetView.hidden = NO;
}

- (void)close {
    [self dismissViewControllerAnimated:YES completion:self.onDismiss];
}

@end
