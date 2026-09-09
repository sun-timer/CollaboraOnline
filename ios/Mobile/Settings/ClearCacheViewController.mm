// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "ClearCacheViewController.h"

#import "ClearCacheHelper.h"
#import "Settings/AppChromeHelper.h"
#import "Settings/AppToastPresenter.h"
#import "Settings/HomeCardDialogPresenter.h"

typedef NS_ENUM(NSInteger, ClearCacheUiState) {
    ClearCacheUiStateIdle,
    ClearCacheUiStateClearing,
    ClearCacheUiStateCompleted,
};

@interface ClearCacheViewController ()
@property (assign, nonatomic) ClearCacheCleanMode selectedMode;
@property (assign, nonatomic) BOOL hasSelectedMode;
@property (strong, nonatomic) ClearCacheStorageInfo *storageInfo;
@property (assign, nonatomic) ClearCacheUiState uiState;
@property (strong, nonatomic) UIScrollView *scrollView;
@property (strong, nonatomic) UIStackView *contentStack;
@property (strong, nonatomic) UILabel *usedSpaceLabel;
@property (strong, nonatomic) UIStackView *categoryStack;
@property (strong, nonatomic) UIButton *quickModeButton;
@property (strong, nonatomic) UIButton *deepModeButton;
@property (strong, nonatomic) UIButton *actionButton;
@end

@implementation ClearCacheViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    [AppChromeHelper applySecondaryPageBackground:self.view];
    self.selectedMode = ClearCacheCleanModeQuick;

    UIButton *back = [AppChromeHelper secondaryBackButtonWithTarget:self action:@selector(close)];
    [self.view addSubview:back];

    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"清理缓存";
    title.font = [UIFont boldSystemFontOfSize:17];
    [self.view addSubview:title];

    self.scrollView = [[UIScrollView alloc] init];
    self.scrollView.translatesAutoresizingMaskIntoConstraints = NO;
    [self.view addSubview:self.scrollView];

    self.contentStack = [[UIStackView alloc] init];
    self.contentStack.translatesAutoresizingMaskIntoConstraints = NO;
    self.contentStack.axis = UILayoutConstraintAxisVertical;
    self.contentStack.spacing = 16;
    [self.scrollView addSubview:self.contentStack];

    self.usedSpaceLabel = [[UILabel alloc] init];
    self.usedSpaceLabel.font = [UIFont systemFontOfSize:15 weight:UIFontWeightSemibold];
    self.usedSpaceLabel.textColor = [UIColor colorWithWhite:0.2 alpha:1];
    [self.contentStack addArrangedSubview:self.usedSpaceLabel];

    self.categoryStack = [[UIStackView alloc] init];
    self.categoryStack.axis = UILayoutConstraintAxisVertical;
    self.categoryStack.spacing = 8;
    [self.contentStack addArrangedSubview:self.categoryStack];

    UILabel *modeTitle = [[UILabel alloc] init];
    modeTitle.text = @"清理模式";
    modeTitle.font = [UIFont systemFontOfSize:15];
    modeTitle.textColor = [UIColor colorWithWhite:0.42 alpha:1];
    [self.contentStack addArrangedSubview:modeTitle];

    self.quickModeButton = [self modeButtonWithTitle:@"快速清理" subtitle:@"临时文件、图片缓存、AI 预览"];
    self.deepModeButton = [self modeButtonWithTitle:@"深度清理" subtitle:@"含聊天历史与离线模型"];
    [self.quickModeButton addTarget:self action:@selector(selectQuick) forControlEvents:UIControlEventTouchUpInside];
    [self.deepModeButton addTarget:self action:@selector(selectDeep) forControlEvents:UIControlEventTouchUpInside];
    [self.contentStack addArrangedSubview:self.quickModeButton];
    [self.contentStack addArrangedSubview:self.deepModeButton];

    self.actionButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.actionButton.translatesAutoresizingMaskIntoConstraints = NO;
    self.actionButton.backgroundColor = [UIColor colorWithWhite:0.8 alpha:1];
    self.actionButton.layer.cornerRadius = 28;
    [self.actionButton setTitle:@"清理" forState:UIControlStateNormal];
    [self.actionButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    self.actionButton.enabled = NO;
    [self.actionButton addTarget:self action:@selector(runClear) forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:self.actionButton];

    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:8],
        [back.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:12],
        [title.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [title.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [self.scrollView.topAnchor constraintEqualToAnchor:back.bottomAnchor constant:16],
        [self.scrollView.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:32],
        [self.scrollView.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor constant:-32],
        [self.scrollView.bottomAnchor constraintEqualToAnchor:self.actionButton.topAnchor constant:-16],
        [self.contentStack.topAnchor constraintEqualToAnchor:self.scrollView.topAnchor],
        [self.contentStack.leadingAnchor constraintEqualToAnchor:self.scrollView.leadingAnchor],
        [self.contentStack.trailingAnchor constraintEqualToAnchor:self.scrollView.trailingAnchor],
        [self.contentStack.bottomAnchor constraintEqualToAnchor:self.scrollView.bottomAnchor],
        [self.contentStack.widthAnchor constraintEqualToAnchor:self.scrollView.widthAnchor],
        [self.actionButton.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:64],
        [self.actionButton.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor constant:-64],
        [self.actionButton.bottomAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.bottomAnchor constant:-24],
        [self.actionButton.heightAnchor constraintEqualToConstant:56],
        [self.quickModeButton.heightAnchor constraintEqualToConstant:72],
        [self.deepModeButton.heightAnchor constraintEqualToConstant:72],
    ]];

    [self reloadStorage];
}

- (UIButton *)modeButtonWithTitle:(NSString *)title subtitle:(NSString *)subtitle {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    button.backgroundColor = UIColor.whiteColor;
    button.layer.cornerRadius = 12;
    button.layer.borderWidth = 1;
    button.layer.borderColor = [UIColor colorWithWhite:0.9 alpha:1].CGColor;
    button.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeft;
    button.contentEdgeInsets = UIEdgeInsetsMake(12, 16, 12, 16);
    NSString *text = [NSString stringWithFormat:@"%@\n%@", title, subtitle];
    NSMutableAttributedString *attr = [[NSMutableAttributedString alloc] initWithString:text];
    [attr addAttribute:NSFontAttributeName value:[UIFont boldSystemFontOfSize:15] range:NSMakeRange(0, title.length)];
    [attr addAttribute:NSFontAttributeName value:[UIFont systemFontOfSize:12] range:NSMakeRange(title.length + 1, subtitle.length)];
    [attr addAttribute:NSForegroundColorAttributeName value:[UIColor colorWithWhite:0.2 alpha:1] range:NSMakeRange(0, title.length)];
    [attr addAttribute:NSForegroundColorAttributeName value:[UIColor colorWithWhite:0.5 alpha:1] range:NSMakeRange(title.length + 1, subtitle.length)];
    [button setAttributedTitle:attr forState:UIControlStateNormal];
    button.titleLabel.numberOfLines = 2;
    return button;
}

- (UIView *)categoryRowWithTitle:(NSString *)title bytes:(int64_t)bytes {
    UIView *row = [[UIView alloc] init];
    row.backgroundColor = [UIColor colorWithRed:1 green:0.95 blue:0.92 alpha:1];
    row.layer.cornerRadius = 12;
    UILabel *left = [[UILabel alloc] init];
    left.translatesAutoresizingMaskIntoConstraints = NO;
    left.text = title;
    left.font = [UIFont systemFontOfSize:15];
    UILabel *right = [[UILabel alloc] init];
    right.translatesAutoresizingMaskIntoConstraints = NO;
    right.text = [ClearCacheHelper formatSize:bytes];
    right.font = [UIFont systemFontOfSize:14];
    right.textColor = [UIColor colorWithWhite:0.45 alpha:1];
    [row addSubview:left];
    [row addSubview:right];
    [NSLayoutConstraint activateConstraints:@[
        [row.heightAnchor constraintEqualToConstant:48],
        [left.leadingAnchor constraintEqualToAnchor:row.leadingAnchor constant:16],
        [left.centerYAnchor constraintEqualToAnchor:row.centerYAnchor],
        [right.trailingAnchor constraintEqualToAnchor:row.trailingAnchor constant:-16],
        [right.centerYAnchor constraintEqualToAnchor:row.centerYAnchor],
    ]];
    return row;
}

- (void)reloadStorage {
    self.storageInfo = [ClearCacheHelper scan];
    ClearCacheCategorySize *c = self.storageInfo.categories;
    self.usedSpaceLabel.text = [NSString stringWithFormat:@"应用缓存 %@ · 手机已用 %@ / %@",
                                [ClearCacheHelper formatSize:c.totalAppBytes],
                                [ClearCacheHelper formatSize:self.storageInfo.phoneUsedBytes],
                                [ClearCacheHelper formatSize:self.storageInfo.phoneTotalBytes]];
    for (UIView *sub in self.categoryStack.arrangedSubviews) {
        [self.categoryStack removeArrangedSubview:sub];
        [sub removeFromSuperview];
    }
    [self.categoryStack addArrangedSubview:[self categoryRowWithTitle:@"临时文件" bytes:c.tempFilesBytes]];
    [self.categoryStack addArrangedSubview:[self categoryRowWithTitle:@"图片缓存" bytes:c.imageCacheBytes]];
    [self.categoryStack addArrangedSubview:[self categoryRowWithTitle:@"AI 预览" bytes:c.aiPreviewBytes]];
    [self.categoryStack addArrangedSubview:[self categoryRowWithTitle:@"聊天历史" bytes:c.chatHistoryBytes]];
    [self.categoryStack addArrangedSubview:[self categoryRowWithTitle:@"离线模型" bytes:c.offlineModelBytes]];
}

- (void)refreshModeSelection {
    UIColor *accent = [AppChromeHelper homeFabColor];
    self.quickModeButton.layer.borderColor = (self.selectedMode == ClearCacheCleanModeQuick ? accent : [UIColor colorWithWhite:0.9 alpha:1]).CGColor;
    self.deepModeButton.layer.borderColor = (self.selectedMode == ClearCacheCleanModeDeep ? accent : [UIColor colorWithWhite:0.9 alpha:1]).CGColor;
    self.actionButton.backgroundColor = self.hasSelectedMode ? accent : [UIColor colorWithWhite:0.8 alpha:1];
    self.actionButton.enabled = self.hasSelectedMode && self.uiState == ClearCacheUiStateIdle;
}

- (void)selectQuick {
    self.selectedMode = ClearCacheCleanModeQuick;
    self.hasSelectedMode = YES;
    [self refreshModeSelection];
}

- (void)selectDeep {
    self.selectedMode = ClearCacheCleanModeDeep;
    self.hasSelectedMode = YES;
    [self refreshModeSelection];
}

- (int64_t)clearableBytesForSelectedMode {
    if (self.storageInfo.categories == nil) {
        return 0;
    }
    return [self.storageInfo.categories clearableBytesForMode:self.selectedMode];
}

- (void)runClear {
    if (!self.hasSelectedMode || self.uiState != ClearCacheUiStateIdle) {
        return;
    }
    int64_t clearableBytes = [self clearableBytesForSelectedMode];
    NSString *sizeText = [ClearCacheHelper formatSize:clearableBytes];
    NSString *message = [NSString stringWithFormat:@"即将清理 %@缓存，是否继续？", sizeText];
    __weak __typeof(self) weakSelf = self;
    [HomeCardDialogPresenter presentConfirmFrom:self
                                          title:@"清理缓存"
                                        message:message
                                   confirmTitle:@"清理"
                                   confirmStyle:HomeCardDialogConfirmStylePrimary
                                     completion:^(BOOL confirmed) {
        if (!confirmed) {
            return;
        }
        [weakSelf performClearWithExpectedBytes:clearableBytes];
    }];
}

- (void)performClearWithExpectedBytes:(int64_t)expectedBytes {
    self.uiState = ClearCacheUiStateClearing;
    [self.actionButton setTitle:@"清理中..." forState:UIControlStateNormal];
    self.actionButton.enabled = NO;
    ClearCacheCleanMode mode = self.selectedMode;
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        int64_t freedBytes = [ClearCacheHelper clearWithMode:mode];
        int64_t reportBytes = freedBytes > 0 ? freedBytes : expectedBytes;
        dispatch_async(dispatch_get_main_queue(), ^{
            self.uiState = ClearCacheUiStateCompleted;
            [self.actionButton setTitle:@"清理完毕" forState:UIControlStateNormal];
            self.actionButton.backgroundColor = [UIColor colorWithRed:59.0 / 255.0 green:128.0 / 255.0 blue:64.0 / 255.0 alpha:1];
            [self reloadStorage];
            NSString *toast = [NSString stringWithFormat:@"已清理 %@缓存，存储空间已释放",
                               [ClearCacheHelper formatSize:reportBytes]];
            [AppToastPresenter showMessage:toast from:self];
        });
    });
}

- (void)close {
    [self dismissViewControllerAnimated:YES completion:nil];
}

@end
