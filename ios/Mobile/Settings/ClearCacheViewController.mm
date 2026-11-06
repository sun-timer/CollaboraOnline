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

// 设计稿 403:20606「清理缓存」/ 417:21872「清理缓存-选中清理模式」/ 414:21297「清理缓存-弹窗」（750×1624 @2x）
static const CGFloat kPageInset = 16.0;      // 686px / 2
static const CGFloat kBarHeight = 16.0;      // 32px
static const CGFloat kBarRadius = 4.0;       // 设计稿 $r4 = 8px
static const CGFloat kLegendChip = 16.0;     // 32px
static const CGFloat kRowHeight = 55.0;      // 110px
static const CGFloat kCardRadius = 10.0;      // 设计稿 $r3 = 20px
static const CGFloat kActionHeight = 35.0;   // 70px
static const CGFloat kRadioSize = 22.0;

static UIColor *BarTrackColor(void) {
    return [UIColor colorWithRed:0xE3 / 255.0 green:0xE3 / 255.0 blue:0xE5 / 255.0 alpha:1];
}

static UIColor *PhoneUsedColor(void) {
    return [UIColor colorWithRed:0x50 / 255.0 green:0x69 / 255.0 blue:0xFF / 255.0 alpha:1];
}

// 设计稿 token：主文字 #000000e5（黑 90%）、次文字 #0000008f（黑 56%）、说明文字 #6a6a6a
static UIColor *TextPrimary(void) {
    return [UIColor colorWithWhite:0.0 alpha:0.9];
}

static UIColor *TextBody(void) {
    return [UIColor colorWithWhite:0.0 alpha:0.56];
}

static UIColor *TextSecondary(void) {
    return [UIColor colorWithRed:0x6A / 255.0 green:0x6A / 255.0 blue:0x6A / 255.0 alpha:1];
}

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
@property (strong, nonatomic) UIRefreshControl *refreshControl;
@property (strong, nonatomic) UIStackView *contentStack;
@property (strong, nonatomic) UILabel *usedSpaceValueLabel;
@property (strong, nonatomic) UIView *aiOfficeBarSegment;
@property (strong, nonatomic) UIView *phoneUsedBarSegment;
@property (strong, nonatomic) NSLayoutConstraint *aiOfficeBarWidth;
@property (strong, nonatomic) NSLayoutConstraint *phoneUsedBarWidth;
@property (strong, nonatomic) NSArray<UILabel *> *primaryValueLabels;
@property (strong, nonatomic) NSArray<UILabel *> *secondaryValueLabels;
@property (strong, nonatomic) UIButton *quickModeButton;
@property (strong, nonatomic) UIButton *deepModeButton;
@property (strong, nonatomic) UIView *quickRadio;
@property (strong, nonatomic) UIView *deepRadio;
@property (strong, nonatomic) UIButton *actionButton;
@property (strong, nonatomic) UIView *modelSpinner;
@property (assign, nonatomic) BOOL modelSizePending;
@end

@implementation ClearCacheViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    [AppChromeHelper applySecondaryPageBackground:self.view];
    self.selectedMode = ClearCacheCleanModeQuick;

    UIButton *back = [AppChromeHelper secondaryBackButtonWithTarget:self action:@selector(close)];
    [self.view addSubview:back];

    // 标题按「标题栏2」规格左对齐、紧挨返回图标（标注：标题靠左，与返回图标挨着）
    UILabel *title = [self labelWithText:@"清理缓存" font:20.0 weight:UIFontWeightMedium color:TextPrimary()];
    [self.view addSubview:title];

    self.scrollView = [[UIScrollView alloc] init];
    self.scrollView.translatesAutoresizingMaskIntoConstraints = NO;
    self.scrollView.showsVerticalScrollIndicator = NO;
    self.scrollView.alwaysBounceVertical = YES;
    [self.view addSubview:self.scrollView];

    // 设计稿 417:22072「清理缓存-刷新页面」：下拉重新统计各分类占用
    self.refreshControl = [[UIRefreshControl alloc] init];
    self.refreshControl.tintColor = [UIColor colorWithWhite:0.6 alpha:1];
    [self.refreshControl addTarget:self action:@selector(handleRefresh:) forControlEvents:UIControlEventValueChanged];
    self.scrollView.refreshControl = self.refreshControl;

    self.contentStack = [[UIStackView alloc] init];
    self.contentStack.translatesAutoresizingMaskIntoConstraints = NO;
    self.contentStack.axis = UILayoutConstraintAxisVertical;
    self.contentStack.spacing = 0;
    [self.scrollView addSubview:self.contentStack];

    UIView *bar = [self buildStorageBar];
    UIView *legend = [self buildLegend];
    UILabel *usedTitle = [self labelWithText:@"AI Office已用空间" font:14.0 weight:UIFontWeightRegular color:UIColor.blackColor];
    self.usedSpaceValueLabel = [self labelWithText:@"—" font:31.5 weight:UIFontWeightSemibold color:UIColor.blackColor];

    NSMutableArray<UILabel *> *primaryValues = [NSMutableArray array];
    UIView *primaryCard = [self cardWithTitles:@[ @"临时文件", @"图片缓存", @"AI预览图" ]
                                  valueLabels:primaryValues
                                    loadingRow:NO];
    self.primaryValueLabels = primaryValues;

    NSMutableArray<UILabel *> *secondaryValues = [NSMutableArray array];
    UIView *secondaryCard = [self cardWithTitles:@[ @"历史对话记录", @"离线模型缓存" ]
                                     valueLabels:secondaryValues
                                       loadingRow:YES];
    self.secondaryValueLabels = secondaryValues;

    UILabel *modeHeading = [self labelWithText:@"选择清理模式" font:14.0 weight:UIFontWeightRegular color:TextSecondary()];
    UIView *modeCard = [[UIView alloc] init];
    modeCard.translatesAutoresizingMaskIntoConstraints = NO;
    modeCard.backgroundColor = UIColor.whiteColor;
    modeCard.layer.cornerRadius = kCardRadius;
    modeCard.clipsToBounds = YES;

    self.quickModeButton = [self modeRowWithTitle:@"快速清理"
                                         subtitle:@"仅清理临时文件、图片缓存、AI生成结果的本地预览图"
                                            radio:&_quickRadio];
    self.deepModeButton = [self modeRowWithTitle:@"深度清理"
                                        subtitle:@"快速清理项目+历史对话记录、离线模型缓存"
                                           radio:&_deepRadio];
    [self.quickModeButton addTarget:self action:@selector(selectQuick) forControlEvents:UIControlEventTouchUpInside];
    [self.deepModeButton addTarget:self action:@selector(selectDeep) forControlEvents:UIControlEventTouchUpInside];
    [modeCard addSubview:self.quickModeButton];
    [modeCard addSubview:self.deepModeButton];
    [NSLayoutConstraint activateConstraints:@[
        [self.quickModeButton.topAnchor constraintEqualToAnchor:modeCard.topAnchor],
        [self.quickModeButton.leadingAnchor constraintEqualToAnchor:modeCard.leadingAnchor],
        [self.quickModeButton.trailingAnchor constraintEqualToAnchor:modeCard.trailingAnchor],
        [self.deepModeButton.topAnchor constraintEqualToAnchor:self.quickModeButton.bottomAnchor],
        [self.deepModeButton.leadingAnchor constraintEqualToAnchor:modeCard.leadingAnchor],
        [self.deepModeButton.trailingAnchor constraintEqualToAnchor:modeCard.trailingAnchor],
        [self.deepModeButton.bottomAnchor constraintEqualToAnchor:modeCard.bottomAnchor],
    ]];

    for (UIView *v in @[ bar, legend, usedTitle, self.usedSpaceValueLabel,
                         primaryCard, secondaryCard, modeHeading, modeCard ]) {
        [self.contentStack addArrangedSubview:v];
    }
    [self.contentStack setCustomSpacing:14 afterView:bar];
    [self.contentStack setCustomSpacing:24 afterView:legend];
    [self.contentStack setCustomSpacing:16 afterView:usedTitle];
    [self.contentStack setCustomSpacing:27 afterView:self.usedSpaceValueLabel];
    [self.contentStack setCustomSpacing:4 afterView:primaryCard];
    [self.contentStack setCustomSpacing:42 afterView:secondaryCard];

    self.actionButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.actionButton.translatesAutoresizingMaskIntoConstraints = NO;
    self.actionButton.backgroundColor = [UIColor colorWithWhite:0.8 alpha:1];
    self.actionButton.layer.cornerRadius = kActionHeight / 2.0;
    self.actionButton.titleLabel.font = [UIFont systemFontOfSize:16];
    [self.actionButton setTitle:@"立即清理" forState:UIControlStateNormal];
    [self.actionButton setTitleColor:TextSecondary() forState:UIControlStateNormal];
    self.actionButton.enabled = NO;
    [self.actionButton addTarget:self action:@selector(runClear) forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:self.actionButton];

    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:0],
        [back.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:12],
        [title.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [title.leadingAnchor constraintEqualToAnchor:back.trailingAnchor constant:4],
        [title.trailingAnchor constraintLessThanOrEqualToAnchor:self.view.trailingAnchor constant:-16],
        [self.scrollView.topAnchor constraintEqualToAnchor:back.bottomAnchor constant:16],
        [self.scrollView.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:kPageInset],
        [self.scrollView.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor constant:-kPageInset],
        [self.scrollView.bottomAnchor constraintEqualToAnchor:self.actionButton.topAnchor constant:-16],
        [self.contentStack.topAnchor constraintEqualToAnchor:self.scrollView.contentLayoutGuide.topAnchor],
        [self.contentStack.leadingAnchor constraintEqualToAnchor:self.scrollView.contentLayoutGuide.leadingAnchor],
        [self.contentStack.trailingAnchor constraintEqualToAnchor:self.scrollView.contentLayoutGuide.trailingAnchor],
        [self.contentStack.bottomAnchor constraintEqualToAnchor:self.scrollView.contentLayoutGuide.bottomAnchor],
        [self.contentStack.widthAnchor constraintEqualToAnchor:self.scrollView.frameLayoutGuide.widthAnchor],
        [bar.heightAnchor constraintEqualToConstant:kBarHeight],
        [modeHeading.heightAnchor constraintEqualToConstant:20],
        [self.actionButton.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:32],
        [self.actionButton.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor constant:-32],
        [self.actionButton.bottomAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.bottomAnchor constant:-1],
        [self.actionButton.heightAnchor constraintEqualToConstant:kActionHeight],
    ]];

    [self reloadStorage];
}

#pragma mark - Small builders

- (UILabel *)labelWithText:(NSString *)text font:(CGFloat)size weight:(UIFontWeight)weight color:(UIColor *)color {
    UILabel *label = [[UILabel alloc] init];
    label.translatesAutoresizingMaskIntoConstraints = NO;
    label.text = text;
    label.font = [UIFont systemFontOfSize:size weight:weight];
    label.textColor = color;
    label.numberOfLines = 0;
    return label;
}

#pragma mark - Storage bar

- (UIView *)buildStorageBar {
    UIView *bar = [[UIView alloc] init];
    bar.translatesAutoresizingMaskIntoConstraints = NO;
    bar.backgroundColor = BarTrackColor();
    bar.layer.cornerRadius = kBarRadius;
    bar.clipsToBounds = YES;

    self.aiOfficeBarSegment = [[UIView alloc] init];
    self.aiOfficeBarSegment.translatesAutoresizingMaskIntoConstraints = NO;
    self.aiOfficeBarSegment.backgroundColor = [AppChromeHelper homeFabColor];
    [bar addSubview:self.aiOfficeBarSegment];

    self.phoneUsedBarSegment = [[UIView alloc] init];
    self.phoneUsedBarSegment.translatesAutoresizingMaskIntoConstraints = NO;
    self.phoneUsedBarSegment.backgroundColor = PhoneUsedColor();
    [bar addSubview:self.phoneUsedBarSegment];

    self.aiOfficeBarWidth = [self.aiOfficeBarSegment.widthAnchor constraintEqualToConstant:0];
    self.phoneUsedBarWidth = [self.phoneUsedBarSegment.widthAnchor constraintEqualToConstant:0];
    [NSLayoutConstraint activateConstraints:@[
        [self.aiOfficeBarSegment.leadingAnchor constraintEqualToAnchor:bar.leadingAnchor],
        [self.aiOfficeBarSegment.topAnchor constraintEqualToAnchor:bar.topAnchor],
        [self.aiOfficeBarSegment.bottomAnchor constraintEqualToAnchor:bar.bottomAnchor],
        self.aiOfficeBarWidth,
        [self.phoneUsedBarSegment.leadingAnchor constraintEqualToAnchor:self.aiOfficeBarSegment.trailingAnchor],
        [self.phoneUsedBarSegment.topAnchor constraintEqualToAnchor:bar.topAnchor],
        [self.phoneUsedBarSegment.bottomAnchor constraintEqualToAnchor:bar.bottomAnchor],
        self.phoneUsedBarWidth,
    ]];
    return bar;
}

- (UIView *)buildLegend {
    UIView *legend = [[UIView alloc] init];
    legend.translatesAutoresizingMaskIntoConstraints = NO;

    UIView *aiChip = [self legendChipWithColor:[AppChromeHelper homeFabColor]];
    UILabel *aiLabel = [self labelWithText:@"AI Office已用" font:12.0 weight:UIFontWeightRegular color:TextSecondary()];
    UIView *phoneChip = [self legendChipWithColor:PhoneUsedColor()];
    UILabel *phoneLabel = [self labelWithText:@"手机已用" font:12.0 weight:UIFontWeightRegular color:TextSecondary()];

    UIStackView *row = [[UIStackView alloc] initWithArrangedSubviews:@[ aiChip, aiLabel, phoneChip, phoneLabel ]];
    row.translatesAutoresizingMaskIntoConstraints = NO;
    row.axis = UILayoutConstraintAxisHorizontal;
    row.alignment = UIStackViewAlignmentCenter;
    row.spacing = 8;
    [row setCustomSpacing:24 afterView:aiLabel];
    [legend addSubview:row];

    [NSLayoutConstraint activateConstraints:@[
        [aiChip.widthAnchor constraintEqualToConstant:kLegendChip],
        [aiChip.heightAnchor constraintEqualToConstant:kLegendChip],
        [phoneChip.widthAnchor constraintEqualToConstant:kLegendChip],
        [phoneChip.heightAnchor constraintEqualToConstant:kLegendChip],
        [row.leadingAnchor constraintEqualToAnchor:legend.leadingAnchor],
        [row.trailingAnchor constraintLessThanOrEqualToAnchor:legend.trailingAnchor],
        [row.topAnchor constraintEqualToAnchor:legend.topAnchor],
        [row.bottomAnchor constraintEqualToAnchor:legend.bottomAnchor],
    ]];
    return legend;
}

- (UIView *)legendChipWithColor:(UIColor *)color {
    UIView *chip = [[UIView alloc] init];
    chip.translatesAutoresizingMaskIntoConstraints = NO;
    chip.backgroundColor = color;
    chip.layer.cornerRadius = 4;
    return chip;
}

#pragma mark - Category cards

- (UIView *)cardWithTitles:(NSArray<NSString *> *)titles
               valueLabels:(NSMutableArray<UILabel *> *)valueLabels
                 loadingRow:(BOOL)loadingRow {
    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.backgroundColor = UIColor.whiteColor;
    card.layer.cornerRadius = kCardRadius;
    card.clipsToBounds = YES;

    UIStackView *stack = [[UIStackView alloc] init];
    stack.translatesAutoresizingMaskIntoConstraints = NO;
    stack.axis = UILayoutConstraintAxisVertical;
    stack.spacing = 0;
    [card addSubview:stack];
    [NSLayoutConstraint activateConstraints:@[
        [stack.topAnchor constraintEqualToAnchor:card.topAnchor],
        [stack.leadingAnchor constraintEqualToAnchor:card.leadingAnchor],
        [stack.trailingAnchor constraintEqualToAnchor:card.trailingAnchor],
        [stack.bottomAnchor constraintEqualToAnchor:card.bottomAnchor],
    ]];

    BOOL spinnerAdded = NO;
    for (NSString *text in titles) {
        UIView *row = [[UIView alloc] init];
        UILabel *left = [self labelWithText:text font:16.0 weight:UIFontWeightRegular color:TextPrimary()];
        UILabel *right = [self labelWithText:@"—" font:14.0 weight:UIFontWeightRegular color:TextBody()];
        right.textAlignment = NSTextAlignmentRight;
        [valueLabels addObject:right];
        [row addSubview:left];

        NSMutableArray<NSLayoutConstraint *> *constraints = [NSMutableArray arrayWithArray:@[
            [row.heightAnchor constraintEqualToConstant:kRowHeight],
            [left.leadingAnchor constraintEqualToAnchor:row.leadingAnchor constant:16],
            [left.centerYAnchor constraintEqualToAnchor:row.centerYAnchor],
        ]];

        // 只有「离线模型缓存」这一行带 loading 圆弧；圆弧放进横向栈里，
        // 隐藏时栈自动收拢，数值仍与其它行一样贴右边界 →16pt，保证整列右对齐。
        if (loadingRow && !spinnerAdded && [titles.lastObject isEqualToString:text]) {
            spinnerAdded = YES;
            UIView *spinner = [self loadingArcView];
            spinner.hidden = YES;
            self.modelSpinner = spinner;

            UIStackView *valueStack = [[UIStackView alloc] initWithArrangedSubviews:@[ right, spinner ]];
            valueStack.translatesAutoresizingMaskIntoConstraints = NO;
            valueStack.axis = UILayoutConstraintAxisHorizontal;
            valueStack.alignment = UIStackViewAlignmentCenter;
            valueStack.spacing = 4;
            [row addSubview:valueStack];

            [constraints addObjectsFromArray:@[
                [spinner.widthAnchor constraintEqualToConstant:16],
                [spinner.heightAnchor constraintEqualToConstant:16],
                [valueStack.trailingAnchor constraintEqualToAnchor:row.trailingAnchor constant:-16],
                [valueStack.centerYAnchor constraintEqualToAnchor:row.centerYAnchor],
                [valueStack.leadingAnchor constraintGreaterThanOrEqualToAnchor:left.trailingAnchor constant:8],
            ]];
        } else {
            [row addSubview:right];
            [constraints addObjectsFromArray:@[
                [right.trailingAnchor constraintEqualToAnchor:row.trailingAnchor constant:-16],
                [right.centerYAnchor constraintEqualToAnchor:row.centerYAnchor],
                [right.leadingAnchor constraintGreaterThanOrEqualToAnchor:left.trailingAnchor constant:8],
            ]];
        }
        [NSLayoutConstraint activateConstraints:constraints];
        [stack addArrangedSubview:row];
    }
    return card;
}

#pragma mark - Mode rows

- (UIButton *)modeRowWithTitle:(NSString *)title
                      subtitle:(NSString *)subtitle
                         radio:(UIView *__strong *)radioOut {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    button.backgroundColor = UIColor.clearColor;

    UILabel *titleLabel = [self labelWithText:title font:16.0 weight:UIFontWeightRegular color:TextPrimary()];
    UILabel *subtitleLabel = [self labelWithText:subtitle font:14.0 weight:UIFontWeightRegular color:TextBody()];

    UIView *radio = [[UIView alloc] init];
    radio.translatesAutoresizingMaskIntoConstraints = NO;
    radio.layer.cornerRadius = kRadioSize / 2.0;
    radio.layer.borderWidth = 2.0;
    radio.layer.borderColor = [UIColor colorWithWhite:0x99 / 255.0 alpha:1].CGColor;
    radio.userInteractionEnabled = NO;

    [button addSubview:titleLabel];
    [button addSubview:subtitleLabel];
    [button addSubview:radio];

    [NSLayoutConstraint activateConstraints:@[
        [titleLabel.topAnchor constraintEqualToAnchor:button.topAnchor constant:10],
        [titleLabel.leadingAnchor constraintEqualToAnchor:button.leadingAnchor constant:16],
        [titleLabel.trailingAnchor constraintLessThanOrEqualToAnchor:radio.leadingAnchor constant:-12],
        [subtitleLabel.topAnchor constraintEqualToAnchor:titleLabel.bottomAnchor constant:3.5],
        [subtitleLabel.leadingAnchor constraintEqualToAnchor:titleLabel.leadingAnchor],
        [subtitleLabel.trailingAnchor constraintEqualToAnchor:radio.leadingAnchor constant:-12],
        [subtitleLabel.bottomAnchor constraintEqualToAnchor:button.bottomAnchor constant:-10],
        [radio.trailingAnchor constraintEqualToAnchor:button.trailingAnchor constant:-16],
        [radio.centerYAnchor constraintEqualToAnchor:button.centerYAnchor],
        [radio.widthAnchor constraintEqualToConstant:kRadioSize],
        [radio.heightAnchor constraintEqualToConstant:kRadioSize],
    ]];

    if (radioOut != NULL) {
        *radioOut = radio;
    }
    return button;
}

/// 设计稿 403:20606「离线模型缓存」右端的 loading 圆弧：16pt 开口圆环 + 旋转
- (UIView *)loadingArcView {
    UIView *view = [[UIView alloc] init];
    view.translatesAutoresizingMaskIntoConstraints = NO;
    view.userInteractionEnabled = NO;

    CAShapeLayer *arc = [CAShapeLayer layer];
    arc.frame = CGRectMake(0, 0, 16, 16);
    UIBezierPath *path = [UIBezierPath bezierPathWithArcCenter:CGPointMake(8, 8)
                                                        radius:6.5
                                                    startAngle:-M_PI_2
                                                      endAngle:M_PI_2 * 1.1
                                                     clockwise:YES];
    arc.path = path.CGPath;
    arc.fillColor = [UIColor clearColor].CGColor;
    arc.strokeColor = [UIColor colorWithWhite:0x99 / 255.0 alpha:1].CGColor;
    arc.lineWidth = 2.5;
    arc.lineCap = kCALineCapRound;
    [view.layer addSublayer:arc];

    CABasicAnimation *spin = [CABasicAnimation animationWithKeyPath:@"transform.rotation.z"];
    spin.fromValue = @(0.0);
    spin.toValue = @(M_PI * 2.0);
    spin.duration = 0.9;
    spin.repeatCount = HUGE_VALF;
    [view.layer addAnimation:spin forKey:@"arcSpin"];
    return view;
}

#pragma mark - Data

- (void)reloadStorage {
    self.storageInfo = [ClearCacheHelper scan];
    self.modelSizePending = YES;
    [self applyStorageInfo];
    if (self.modelSpinner != nil) {
        self.modelSpinner.hidden = NO;
    }

    __weak __typeof(self) weakSelf = self;
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        int64_t modelBytes = [ClearCacheHelper offlineModelBytes];
        dispatch_async(dispatch_get_main_queue(), ^{
            __typeof(self) strongSelf = weakSelf;
            if (strongSelf == nil) {
                return;
            }
            strongSelf.storageInfo.categories.offlineModelBytes = modelBytes;
            strongSelf.modelSizePending = NO;
            strongSelf.modelSpinner.hidden = YES;
            [strongSelf applyStorageInfo];
        });
    });
}

/// 把 storageInfo 落到 UI（离线模型大小未知时，该行保持「计算中...」）
- (void)applyStorageInfo {
    ClearCacheCategorySize *c = self.storageInfo.categories;
    int64_t total = self.storageInfo.phoneTotalBytes;
    int64_t phoneUsed = self.storageInfo.phoneUsedBytes;
    int64_t appUsed = c.totalAppBytes;

    self.usedSpaceValueLabel.text = [ClearCacheHelper formatStorageSize:appUsed];

    // 占用条：橙 = AI Office 已用，蓝 = 手机其余已用，灰色轨道 = 手机总容量
    CGFloat appShare = total > 0 ? (CGFloat)appUsed / (CGFloat)total : 0;
    CGFloat phoneShare = total > 0 ? (CGFloat)phoneUsed / (CGFloat)total : 0;
    appShare = MAX(0.0, MIN(appShare, 1.0));
    phoneShare = MAX(appShare, MIN(phoneShare, 1.0));
    UIView *bar = self.aiOfficeBarSegment.superview;
    if (bar != nil) {
        self.aiOfficeBarWidth.active = NO;
        self.phoneUsedBarWidth.active = NO;
        self.aiOfficeBarWidth = [self.aiOfficeBarSegment.widthAnchor constraintEqualToAnchor:bar.widthAnchor
                                                                                multiplier:appShare];
        self.phoneUsedBarWidth = [self.phoneUsedBarSegment.widthAnchor constraintEqualToAnchor:bar.widthAnchor
                                                                                   multiplier:(phoneShare - appShare)];
        self.aiOfficeBarWidth.active = YES;
        self.phoneUsedBarWidth.active = YES;
    }

    NSArray<NSNumber *> *primary = @[ @(c.tempFilesBytes), @(c.imageCacheBytes), @(c.aiPreviewBytes) ];
    for (NSUInteger i = 0; i < MIN(primary.count, self.primaryValueLabels.count); i++) {
        self.primaryValueLabels[i].text = [ClearCacheHelper formatSize:primary[i].longLongValue];
    }
    NSArray<NSNumber *> *secondary = @[ @(c.chatHistoryBytes), @(c.offlineModelBytes) ];
    for (NSUInteger i = 0; i < MIN(secondary.count, self.secondaryValueLabels.count); i++) {
        BOOL pendingModelRow = (i == secondary.count - 1) && self.modelSizePending;
        self.secondaryValueLabels[i].text = pendingModelRow
            ? @"计算中..."
            : [ClearCacheHelper formatSize:secondary[i].longLongValue];
    }
    [self refreshModeSelection];
}

- (void)handleRefresh:(UIRefreshControl *)control {
    [self reloadStorage];
    [control endRefreshing];
    // 设计稿 417:22252「清理缓存-刷新提示」
    [AppToastPresenter showMessage:@"已刷新" from:self];
}

#pragma mark - Selection

- (void)refreshModeSelection {
    UIColor *accent = [AppChromeHelper homeFabColor];
    BOOL quick = self.selectedMode == ClearCacheCleanModeQuick;
    [self applyRadioSelected:quick accent:accent radio:self.quickRadio];
    [self applyRadioSelected:!quick accent:accent radio:self.deepRadio];

    BOOL enabled = self.hasSelectedMode && self.uiState == ClearCacheUiStateIdle;
    self.actionButton.enabled = enabled;
    self.actionButton.backgroundColor = enabled ? accent : [UIColor colorWithWhite:0.8 alpha:1];
    [self.actionButton setTitleColor:enabled ? UIColor.whiteColor : TextSecondary()
                            forState:UIControlStateNormal];
}

- (void)applyRadioSelected:(BOOL)selected accent:(UIColor *)accent radio:(UIView *)radio {
    radio.layer.borderColor = (selected ? accent : [UIColor colorWithWhite:0x99 / 255.0 alpha:1]).CGColor;
    radio.layer.borderWidth = selected ? 6.5 : 2.0;
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
    if (self.modelSizePending) {
        // 深度清理会把离线模型算进去，确认前必须有准确数值
        self.storageInfo.categories.offlineModelBytes = [ClearCacheHelper offlineModelBytes];
        self.modelSizePending = NO;
        self.modelSpinner.hidden = YES;
        [self applyStorageInfo];
    }
    int64_t clearableBytes = [self clearableBytesForSelectedMode];
    NSString *sizeText = [ClearCacheHelper formatSize:clearableBytes];
    NSString *message = [NSString stringWithFormat:@"即将清理 %@缓存，是否继续？", sizeText];
    __weak __typeof(self) weakSelf = self;
    [HomeCardDialogPresenter presentConfirmFrom:self
                                          title:@"清理缓存"
                                        message:message
                                   confirmTitle:@"确定"
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
