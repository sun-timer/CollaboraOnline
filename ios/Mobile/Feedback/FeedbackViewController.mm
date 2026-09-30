// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "FeedbackViewController.h"

#import "FeedbackAppearance.h"
#import "FeedbackApi.h"
#import "FeedbackApiException.h"
#import "FeedbackConfig.h"
#import "FeedbackRecord.h"
#import "FeedbackStore.h"
#import "Settings/AppChromeHelper.h"
#import "Settings/AppToastPresenter.h"
#import "Settings/HomeCardDialogPresenter.h"

#import <PhotosUI/PhotosUI.h>
#import <objc/runtime.h>
#include <stdio.h>

static NSArray<NSString *> *FeedbackTypeLabels(void) {
    return @[ @"功能异常", @"产品建议与改进", @"使用体验问题", @"其他" ];
}

static const NSInteger kAttachAddTag = 8801;
static const NSInteger kLogCheckTag = 8802;
static const NSInteger kChipBaseTag = 8900;

static NSUInteger feedbackUnicodeLength(NSString *string);
static NSString *feedbackFormatListSummary(NSString *summary);

static void XLFeedbackDetailLog(NSString *message) {
    if (message.length == 0) {
        return;
    }
    NSLog(@"%@", message);
    fprintf(stderr, "%s\n", message.UTF8String);
    fflush(stderr);
}

@interface FeedbackListCell : UITableViewCell
@property (strong, nonatomic) UILabel *typeLabel;
@property (strong, nonatomic) UILabel *timeLabel;
@property (strong, nonatomic) UIView *statusDot;
@property (strong, nonatomic) UILabel *statusLabel;
@property (strong, nonatomic) UILabel *contentLabel;
@end

@implementation FeedbackListCell

- (instancetype)initWithStyle:(UITableViewCellStyle)style reuseIdentifier:(NSString *)reuseIdentifier {
    self = [super initWithStyle:style reuseIdentifier:reuseIdentifier];
    if (self) {
        self.selectionStyle = UITableViewCellSelectionStyleNone;
        self.backgroundColor = UIColor.clearColor;
        self.contentView.backgroundColor = UIColor.clearColor;

        UIView *card = [[UIView alloc] init];
        card.translatesAutoresizingMaskIntoConstraints = NO;
        [FeedbackAppearance styleInputContainer:card cornerRadius:16];
        [self.contentView addSubview:card];

        self.typeLabel = [[UILabel alloc] init];
        self.typeLabel.translatesAutoresizingMaskIntoConstraints = NO;
        self.typeLabel.font = [UIFont boldSystemFontOfSize:18];
        self.typeLabel.textColor = [FeedbackAppearance textPrimaryColor];

        self.timeLabel = [[UILabel alloc] init];
        self.timeLabel.translatesAutoresizingMaskIntoConstraints = NO;
        self.timeLabel.font = [UIFont systemFontOfSize:14];
        self.timeLabel.textColor = [FeedbackAppearance textMutedColor];

        self.statusDot = [[UIView alloc] init];
        self.statusDot.translatesAutoresizingMaskIntoConstraints = NO;
        self.statusDot.layer.cornerRadius = 4;
        self.statusDot.clipsToBounds = YES;
        [NSLayoutConstraint activateConstraints:@[
            [self.statusDot.widthAnchor constraintEqualToConstant:8],
            [self.statusDot.heightAnchor constraintEqualToConstant:8],
        ]];

        self.statusLabel = [[UILabel alloc] init];
        self.statusLabel.translatesAutoresizingMaskIntoConstraints = NO;
        self.statusLabel.font = [UIFont systemFontOfSize:14];

        UIImageView *arrow = [[UIImageView alloc] init];
        arrow.translatesAutoresizingMaskIntoConstraints = NO;
        if (@available(iOS 13.0, *)) {
            arrow.image = [[UIImage systemImageNamed:@"chevron.right"]
                imageWithTintColor:[FeedbackAppearance textMutedColor]
                     renderingMode:UIImageRenderingModeAlwaysOriginal];
        }
        [NSLayoutConstraint activateConstraints:@[
            [arrow.widthAnchor constraintEqualToConstant:16],
            [arrow.heightAnchor constraintEqualToConstant:16],
        ]];

        UIView *contentBlock = [[UIView alloc] init];
        contentBlock.translatesAutoresizingMaskIntoConstraints = NO;
        [FeedbackAppearance styleContentBlock:contentBlock];

        self.contentLabel = [[UILabel alloc] init];
        self.contentLabel.translatesAutoresizingMaskIntoConstraints = NO;
        self.contentLabel.font = [UIFont systemFontOfSize:14];
        self.contentLabel.textColor = [FeedbackAppearance textSecondaryColor];
        self.contentLabel.numberOfLines = 2;
        self.contentLabel.lineBreakMode = NSLineBreakByTruncatingTail;
        [contentBlock addSubview:self.contentLabel];

        UIStackView *leftCol = [[UIStackView alloc] initWithArrangedSubviews:@[ self.typeLabel, self.timeLabel ]];
        leftCol.translatesAutoresizingMaskIntoConstraints = NO;
        leftCol.axis = UILayoutConstraintAxisVertical;
        leftCol.spacing = 2;
        leftCol.alignment = UIStackViewAlignmentLeading;

        [card addSubview:leftCol];
        [card addSubview:self.statusDot];
        [card addSubview:self.statusLabel];
        [card addSubview:arrow];
        [card addSubview:contentBlock];

        [NSLayoutConstraint activateConstraints:@[
            [card.topAnchor constraintEqualToAnchor:self.contentView.topAnchor],
            [card.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:16],
            [card.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor constant:-16],
            [card.bottomAnchor constraintEqualToAnchor:self.contentView.bottomAnchor constant:-8],
            [leftCol.topAnchor constraintEqualToAnchor:card.topAnchor constant:8],
            [leftCol.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:12],
            [leftCol.trailingAnchor constraintLessThanOrEqualToAnchor:self.statusDot.leadingAnchor constant:-8],
            [arrow.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-12],
            [arrow.centerYAnchor constraintEqualToAnchor:leftCol.centerYAnchor],
            [self.statusLabel.trailingAnchor constraintEqualToAnchor:arrow.leadingAnchor constant:-4],
            [self.statusLabel.centerYAnchor constraintEqualToAnchor:leftCol.centerYAnchor],
            [self.statusDot.trailingAnchor constraintEqualToAnchor:self.statusLabel.leadingAnchor constant:-4],
            [self.statusDot.centerYAnchor constraintEqualToAnchor:leftCol.centerYAnchor],
            [contentBlock.topAnchor constraintEqualToAnchor:leftCol.bottomAnchor constant:8],
            [contentBlock.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:12],
            [contentBlock.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-12],
            [contentBlock.bottomAnchor constraintEqualToAnchor:card.bottomAnchor constant:-8],
            [self.contentLabel.topAnchor constraintEqualToAnchor:contentBlock.topAnchor constant:8],
            [self.contentLabel.leadingAnchor constraintEqualToAnchor:contentBlock.leadingAnchor constant:12],
            [self.contentLabel.trailingAnchor constraintEqualToAnchor:contentBlock.trailingAnchor constant:-12],
            [self.contentLabel.bottomAnchor constraintEqualToAnchor:contentBlock.bottomAnchor constant:-8],
        ]];
    }
    return self;
}

- (void)configureWithRecord:(FeedbackRecord *)record timeText:(NSString *)timeText {
    self.typeLabel.text = record.type;
    self.timeLabel.text = timeText;
    self.contentLabel.text = feedbackFormatListSummary(record.content);
    self.statusDot.backgroundColor = [FeedbackAppearance statusDotColorForStatus:record.status];
    self.statusLabel.text = [FeedbackAppearance statusTextForStatus:record.status];
    self.statusLabel.textColor = [FeedbackAppearance statusTextColorForStatus:record.status];
}

@end

static const NSInteger kMaxAttachCount = 3;

static NSUInteger feedbackUnicodeLength(NSString *string) {
    if (string.length == 0) {
        return 0;
    }
    __block NSUInteger count = 0;
    [string enumerateSubstringsInRange:NSMakeRange(0, string.length)
                               options:NSStringEnumerationByComposedCharacterSequences
                            usingBlock:^(__unused NSString *substring, __unused NSRange substringRange,
                                         __unused NSRange enclosingRange, __unused BOOL *stop) {
                                count++;
                            }];
    return count;
}

static NSString *feedbackFormatListSummary(NSString *summary) {
    if (summary.length == 0) {
        return @"";
    }
    if (feedbackUnicodeLength(summary) >= 50 && ![summary hasSuffix:@"..."]) {
        return [summary stringByAppendingString:@"..."];
    }
    return summary;
}

static NSString *feedbackTruncateToCodePoints(NSString *string, NSUInteger maxPoints) {
    if (string.length == 0) {
        return @"";
    }
    __block NSUInteger count = 0;
    __block NSRange cutRange = NSMakeRange(0, 0);
    [string enumerateSubstringsInRange:NSMakeRange(0, string.length)
                               options:NSStringEnumerationByComposedCharacterSequences
                            usingBlock:^(NSString *substring, NSRange substringRange, __unused NSRange enclosingRange,
                                         BOOL *stop) {
                                if (count < maxPoints) {
                                    cutRange = NSUnionRange(cutRange, substringRange);
                                    count++;
                                } else {
                                    *stop = YES;
                                }
                            }];
    return [string substringWithRange:cutRange];
}

@interface FeedbackViewController () <PHPickerViewControllerDelegate, UITextViewDelegate, UITableViewDataSource, UITableViewDelegate>
@property (strong, nonatomic) UIView *contentView;
@property (assign, nonatomic) NSInteger selectedTypeIndex;
@property (strong, nonatomic) NSMutableArray<NSString *> *imagePaths;
@property (assign, nonatomic) BOOL shareLog;
@property (strong, nonatomic) UITextView *descView;
@property (strong, nonatomic) UILabel *descPlaceholderLabel;
@property (strong, nonatomic) UILabel *descCountLabel;
@property (strong, nonatomic) UITextField *contactField;
@property (strong, nonatomic) UIStackView *attachRow;
@property (strong, nonatomic) FeedbackRecord *currentDetail;
@property (strong, nonatomic) NSMutableArray<FeedbackRecord *> *listRecords;
@property (strong, nonatomic) UITableView *listTableView;
@property (assign, nonatomic) NSInteger listPageNum;
@property (assign, nonatomic) NSInteger listTotalPages;
@property (assign, nonatomic) BOOL listLoading;
@property (strong, nonatomic) UIRefreshControl *listRefreshControl;
@property (strong, nonatomic) UIView *listLoadingOverlay;
@property (strong, nonatomic) UIView *submitLoadingOverlay;
@property (weak, nonatomic) UIButton *submitButton;
@property (copy, nonatomic) NSString *detailLoadingRecordId;
@end

@implementation FeedbackViewController

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    if (self.navigationController != nil) {
        [self.navigationController setNavigationBarHidden:YES animated:animated];
    }
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    if (self.navigationController != nil && self.isBeingDismissed) {
        [self.navigationController setNavigationBarHidden:NO animated:animated];
    }
}

- (void)viewDidLoad {
    [super viewDidLoad];
    [FeedbackStore clearLegacyMockIfNeeded];
    [AppChromeHelper applySecondaryPageBackground:self.view];
    self.imagePaths = [NSMutableArray array];
    self.listRecords = [NSMutableArray array];
    self.selectedTypeIndex = -1;

    self.contentView = [[UIView alloc] init];
    self.contentView.translatesAutoresizingMaskIntoConstraints = NO;
    self.contentView.backgroundColor = [FeedbackAppearance pageBackgroundColor];
    [self.view addSubview:self.contentView];
    [NSLayoutConstraint activateConstraints:@[
        [self.contentView.topAnchor constraintEqualToAnchor:self.view.topAnchor],
        [self.contentView.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.contentView.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [self.contentView.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
    ]];
    [self showForm];
}

#pragma mark - Helpers

- (void)clearContent {
    for (UIView *sub in self.contentView.subviews) {
        [sub removeFromSuperview];
    }
    self.listTableView = nil;
    self.listRefreshControl = nil;
    [self.listLoadingOverlay removeFromSuperview];
    self.listLoadingOverlay = nil;
}

- (void)toastApiError:(NSError *)error {
    NSString *reason = [FeedbackApiException reasonFromError:error];
    NSString *message = [FeedbackApiException messageFromError:error];
    if ([reason isEqualToString:@"feedback_api_not_configured"]) {
        [self toast:@"服务未配置"];
    } else if ([reason isEqualToString:@"feedback_log_upload_pending"]) {
        [self toast:@"日志上传功能暂未开放"];
    } else if ([reason isEqualToString:@"feedback_api"] && message.length > 0) {
        [self toast:message];
    } else if ([reason isEqualToString:@"feedback_network"]) {
        [self toast:@"网络异常，请稍后重试"];
    } else if ([reason isEqualToString:@"feedback_parse"]) {
        if ([message containsString:@"非 JSON"] || [message isEqualToString:@"invalid json"]) {
            [self toast:@"反馈服务无有效响应，请检查网络或服务器地址"];
        } else if (message.length > 0) {
            [self toast:message];
        } else {
            [self toast:@"服务器响应异常，请稍后重试"];
        }
    } else if (message.length > 0) {
        [self toast:message];
    } else {
        [self toast:@"提交失败，请稍后重试"];
    }
}

- (void)showSubmitLoading:(BOOL)hasImages {
    [self dismissSubmitLoading];
    UIView *overlay = [[UIView alloc] initWithFrame:self.view.bounds];
    overlay.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    overlay.backgroundColor = [UIColor colorWithWhite:0 alpha:0.45];

    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.backgroundColor = UIColor.whiteColor;
    card.layer.cornerRadius = 12;
    card.clipsToBounds = YES;

    UIActivityIndicatorView *spinner;
    if (@available(iOS 13.0, *)) {
        spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleLarge];
    } else {
        spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleWhiteLarge];
    }
    spinner.translatesAutoresizingMaskIntoConstraints = NO;
    [spinner startAnimating];

    UILabel *label = [[UILabel alloc] init];
    label.translatesAutoresizingMaskIntoConstraints = NO;
    label.text = hasImages ? @"正在上传图片并提交…" : @"正在提交…";
    label.font = [UIFont systemFontOfSize:15];
    label.textColor = [FeedbackAppearance textPrimaryColor];
    label.textAlignment = NSTextAlignmentCenter;
    label.numberOfLines = 0;

    UIStackView *stack = [[UIStackView alloc] initWithArrangedSubviews:@[ spinner, label ]];
    stack.translatesAutoresizingMaskIntoConstraints = NO;
    stack.axis = UILayoutConstraintAxisVertical;
    stack.spacing = 12;
    stack.alignment = UIStackViewAlignmentCenter;
    [card addSubview:stack];
    [overlay addSubview:card];
    [self.view addSubview:overlay];

    [NSLayoutConstraint activateConstraints:@[
        [card.centerXAnchor constraintEqualToAnchor:overlay.centerXAnchor],
        [card.centerYAnchor constraintEqualToAnchor:overlay.centerYAnchor],
        [card.widthAnchor constraintEqualToConstant:280],
        [stack.topAnchor constraintEqualToAnchor:card.topAnchor constant:24],
        [stack.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:16],
        [stack.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-16],
        [stack.bottomAnchor constraintEqualToAnchor:card.bottomAnchor constant:-24],
    ]];
    self.submitLoadingOverlay = overlay;
}

- (void)dismissSubmitLoading {
    [self.submitLoadingOverlay removeFromSuperview];
    self.submitLoadingOverlay = nil;
}

- (NSString *)formatTime:(NSTimeInterval)time {
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    formatter.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
    formatter.dateFormat = @"yyyy-MM-dd HH:mm";
    return [formatter stringFromDate:[NSDate dateWithTimeIntervalSince1970:time]];
}

- (void)setListPageLoadingOverlayVisible:(BOOL)visible {
    if (!visible) {
        [self.listLoadingOverlay removeFromSuperview];
        self.listLoadingOverlay = nil;
        return;
    }
    if (self.listLoadingOverlay != nil) {
        return;
    }
    UIView *overlay = [[UIView alloc] init];
    overlay.translatesAutoresizingMaskIntoConstraints = NO;
    overlay.backgroundColor = UIColor.clearColor;
    UIActivityIndicatorView *spinner;
    if (@available(iOS 13.0, *)) {
        spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleLarge];
        spinner.color = [FeedbackAppearance primaryAccentColor];
    } else {
        spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleWhiteLarge];
    }
    spinner.translatesAutoresizingMaskIntoConstraints = NO;
    [spinner startAnimating];
    [overlay addSubview:spinner];
    [self.contentView addSubview:overlay];
    [NSLayoutConstraint activateConstraints:@[
        [overlay.topAnchor constraintEqualToAnchor:self.contentView.topAnchor],
        [overlay.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [overlay.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [overlay.bottomAnchor constraintEqualToAnchor:self.contentView.bottomAnchor],
        [spinner.centerXAnchor constraintEqualToAnchor:overlay.centerXAnchor],
        [spinner.centerYAnchor constraintEqualToAnchor:overlay.centerYAnchor],
    ]];
    self.listLoadingOverlay = overlay;
}

- (void)presentListTableIfNeeded {
    if (self.listTableView != nil && self.listTableView.superview != nil) {
        return;
    }
    self.listTableView = nil;
    self.listRefreshControl = nil;
    [self presentListTable];
}

- (UIView *)buildHeaderWithBackAction:(SEL)backAction
                                title:(NSString *)title
                          rightAction:(SEL _Nullable)rightAction
                           rightTitle:(NSString *_Nullable)rightTitle {
    UIView *header = [[UIView alloc] init];
    header.translatesAutoresizingMaskIntoConstraints = NO;
    header.backgroundColor = [FeedbackAppearance pageBackgroundColor];

    UIButton *back = [AppChromeHelper secondaryBackButtonWithTarget:self action:backAction];
    UILabel *titleLabel = [[UILabel alloc] init];
    titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    titleLabel.text = title;
    titleLabel.font = [UIFont boldSystemFontOfSize:20];
    titleLabel.textColor = [FeedbackAppearance textPrimaryColor];

    [header addSubview:back];
    [header addSubview:titleLabel];

    NSLayoutConstraint *trailing = [titleLabel.trailingAnchor constraintLessThanOrEqualToAnchor:header.trailingAnchor constant:-16];
    trailing.priority = UILayoutPriorityDefaultLow;

    NSMutableArray *constraints = [NSMutableArray arrayWithArray:@[
        [header.heightAnchor constraintEqualToConstant:62],
        [back.leadingAnchor constraintEqualToAnchor:header.leadingAnchor constant:15],
        [back.centerYAnchor constraintEqualToAnchor:header.centerYAnchor],
        [titleLabel.leadingAnchor constraintEqualToAnchor:back.trailingAnchor constant:4],
        [titleLabel.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        trailing,
    ]];

    if (rightAction != nil && rightTitle.length > 0) {
        UIButton *right = [UIButton buttonWithType:UIButtonTypeSystem];
        right.translatesAutoresizingMaskIntoConstraints = NO;
        [right setTitle:rightTitle forState:UIControlStateNormal];
        right.titleLabel.font = [UIFont systemFontOfSize:16];
        [right setTitleColor:[FeedbackAppearance textPrimaryColor] forState:UIControlStateNormal];
        [right addTarget:self action:rightAction forControlEvents:UIControlEventTouchUpInside];
        [header addSubview:right];
        [constraints addObjectsFromArray:@[
            [right.trailingAnchor constraintEqualToAnchor:header.trailingAnchor constant:-12],
            [right.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
            [titleLabel.trailingAnchor constraintLessThanOrEqualToAnchor:right.leadingAnchor constant:-8],
        ]];
    }

    [NSLayoutConstraint activateConstraints:constraints];
    return header;
}

- (UIView *)buildDetailHeaderForRecord:(FeedbackRecord *)record backAction:(SEL)backAction {
    UIView *header = [[UIView alloc] init];
    header.translatesAutoresizingMaskIntoConstraints = NO;
    header.backgroundColor = [FeedbackAppearance pageBackgroundColor];

    UIButton *back = [AppChromeHelper secondaryBackButtonWithTarget:self action:backAction];
    back.translatesAutoresizingMaskIntoConstraints = NO;

    UILabel *noLabel = [[UILabel alloc] init];
    noLabel.translatesAutoresizingMaskIntoConstraints = NO;
    noLabel.text = record.recordId;
    noLabel.font = [UIFont boldSystemFontOfSize:20];
    noLabel.textColor = [FeedbackAppearance textPrimaryColor];

    UILabel *timeLabel = [[UILabel alloc] init];
    timeLabel.translatesAutoresizingMaskIntoConstraints = NO;
    timeLabel.text = [self formatTime:record.submitTime];
    timeLabel.font = [UIFont systemFontOfSize:14];
    timeLabel.textColor = [FeedbackAppearance textSecondaryColor];

    UIView *statusDot = [FeedbackAppearance statusDotForStatus:record.status];
    UILabel *statusLabel = [[UILabel alloc] init];
    statusLabel.translatesAutoresizingMaskIntoConstraints = NO;
    statusLabel.text = [FeedbackAppearance statusTextForStatus:record.status];
    statusLabel.font = [UIFont systemFontOfSize:14];
    statusLabel.textColor = [FeedbackAppearance statusTextColorForStatus:record.status];

    UIStackView *metaRow = [[UIStackView alloc] initWithArrangedSubviews:@[ timeLabel, statusDot, statusLabel ]];
    metaRow.translatesAutoresizingMaskIntoConstraints = NO;
    metaRow.axis = UILayoutConstraintAxisHorizontal;
    metaRow.spacing = 6;
    metaRow.alignment = UIStackViewAlignmentCenter;

    UIStackView *titleCol = [[UIStackView alloc] initWithArrangedSubviews:@[ noLabel, metaRow ]];
    titleCol.translatesAutoresizingMaskIntoConstraints = NO;
    titleCol.axis = UILayoutConstraintAxisVertical;
    titleCol.spacing = 2;
    titleCol.alignment = UIStackViewAlignmentLeading;

    [header addSubview:back];
    [header addSubview:titleCol];

    NSLayoutConstraint *headerBottom = [header.bottomAnchor constraintEqualToAnchor:titleCol.bottomAnchor constant:8];
    headerBottom.priority = UILayoutPriorityRequired;
    [NSLayoutConstraint activateConstraints:@[
        [header.heightAnchor constraintGreaterThanOrEqualToConstant:62],
        [back.leadingAnchor constraintEqualToAnchor:header.leadingAnchor constant:15],
        [back.topAnchor constraintEqualToAnchor:header.topAnchor constant:11],
        [titleCol.leadingAnchor constraintEqualToAnchor:back.trailingAnchor constant:4],
        [titleCol.topAnchor constraintEqualToAnchor:header.topAnchor constant:8],
        [titleCol.trailingAnchor constraintEqualToAnchor:header.trailingAnchor constant:-12],
        headerBottom,
        [back.bottomAnchor constraintLessThanOrEqualToAnchor:header.bottomAnchor constant:-8],
    ]];
    return header;
}

- (void)configureFeedbackBubbleRow:(UIStackView *)row
                            bubble:(UIView *)bubble
                            avatar:(UIView *)avatar
                           spacing:(CGFloat)spacing {
    row.axis = UILayoutConstraintAxisHorizontal;
    row.alignment = UIStackViewAlignmentTop;
    row.spacing = spacing;
    row.distribution = UIStackViewDistributionFill;
    [bubble setContentHuggingPriority:UILayoutPriorityDefaultLow forAxis:UILayoutConstraintAxisHorizontal];
    [bubble setContentCompressionResistancePriority:UILayoutPriorityDefaultLow forAxis:UILayoutConstraintAxisHorizontal];
    [avatar setContentHuggingPriority:UILayoutPriorityRequired forAxis:UILayoutConstraintAxisHorizontal];
    [avatar setContentCompressionResistancePriority:UILayoutPriorityRequired forAxis:UILayoutConstraintAxisHorizontal];
}

- (UIView *)attachAddButton {
    UIButton *add = [UIButton buttonWithType:UIButtonTypeCustom];
    add.translatesAutoresizingMaskIntoConstraints = NO;
    add.tag = kAttachAddTag;
    add.backgroundColor = UIColor.whiteColor;
    add.layer.cornerRadius = 20;
    add.clipsToBounds = YES;
    [add addTarget:self action:@selector(addImages) forControlEvents:UIControlEventTouchUpInside];
    if (@available(iOS 13.0, *)) {
        UIImageSymbolConfiguration *config = [UIImageSymbolConfiguration configurationWithPointSize:32 weight:UIImageSymbolWeightRegular];
        UIImage *plus = [UIImage systemImageNamed:@"plus" withConfiguration:config];
        [add setImage:[plus imageWithTintColor:[FeedbackAppearance textMutedColor]
                                  renderingMode:UIImageRenderingModeAlwaysOriginal]
             forState:UIControlStateNormal];
    }
    [NSLayoutConstraint activateConstraints:@[
        [add.widthAnchor constraintEqualToConstant:80],
        [add.heightAnchor constraintEqualToConstant:80],
    ]];
    return add;
}

- (void)refreshAttachRow {
    if (self.attachRow == nil) {
        return;
    }
    for (UIView *sub in [self.attachRow.arrangedSubviews copy]) {
        if (sub.tag != kAttachAddTag) {
            [self.attachRow removeArrangedSubview:sub];
            [sub removeFromSuperview];
        }
    }
    for (NSString *path in self.imagePaths) {
        UIImageView *thumb = [[UIImageView alloc] initWithImage:[UIImage imageWithContentsOfFile:path]];
        thumb.translatesAutoresizingMaskIntoConstraints = NO;
        thumb.contentMode = UIViewContentModeScaleAspectFill;
        thumb.clipsToBounds = YES;
        thumb.layer.cornerRadius = 12;
        thumb.backgroundColor = [FeedbackAppearance pageBackgroundColor];
        thumb.userInteractionEnabled = YES;
        [NSLayoutConstraint activateConstraints:@[
            [thumb.widthAnchor constraintEqualToConstant:80],
            [thumb.heightAnchor constraintEqualToConstant:80],
        ]];
        UILongPressGestureRecognizer *press = [[UILongPressGestureRecognizer alloc]
            initWithTarget:self action:@selector(removeAttachment:)];
        [thumb addGestureRecognizer:press];
        objc_setAssociatedObject(thumb, @selector(removeAttachment:), path, OBJC_ASSOCIATION_COPY_NONATOMIC);
        [self.attachRow addArrangedSubview:thumb];
    }
}

- (void)removeAttachment:(UILongPressGestureRecognizer *)gesture {
    if (gesture.state != UIGestureRecognizerStateBegan) {
        return;
    }
    NSString *path = objc_getAssociatedObject(gesture.view, @selector(removeAttachment:));
    if (path.length > 0) {
        [self.imagePaths removeObject:path];
        [self refreshAttachRow];
    }
}

#pragma mark - Form

- (void)showForm {
    [self clearContent];
    self.shareLog = NO;

    UIView *header = [self buildHeaderWithBackAction:@selector(close)
                                               title:@"问题和建议"
                                         rightAction:@selector(showList)
                                          rightTitle:@"反馈记录"];
    UIScrollView *scroll = [[UIScrollView alloc] init];
    scroll.translatesAutoresizingMaskIntoConstraints = NO;
    scroll.alwaysBounceVertical = YES;
    if (@available(iOS 11.0, *)) {
        scroll.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentNever;
    }
    scroll.keyboardDismissMode = UIScrollViewKeyboardDismissModeOnDrag;

    UIView *body = [[UIView alloc] init];
    body.translatesAutoresizingMaskIntoConstraints = NO;

    UIStackView *typeSection = [[UIStackView alloc] init];
    typeSection.translatesAutoresizingMaskIntoConstraints = NO;
    typeSection.axis = UILayoutConstraintAxisVertical;
    typeSection.spacing = 8;
    [typeSection addArrangedSubview:[FeedbackAppearance sectionHeaderRowWithText:@"问题类型" required:YES]];

    UIStackView *chipRow0 = [[UIStackView alloc] init];
    chipRow0.axis = UILayoutConstraintAxisHorizontal;
    chipRow0.spacing = 8;
    UIStackView *chipRow1 = [[UIStackView alloc] init];
    chipRow1.axis = UILayoutConstraintAxisHorizontal;
    chipRow1.spacing = 8;
    NSArray<NSString *> *types = FeedbackTypeLabels();
    static const CGFloat kChipMinWidths[] = { 80, 122, 108, 52 };
    for (NSUInteger i = 0; i < types.count; i++) {
        CGFloat minW = i < sizeof(kChipMinWidths) / sizeof(kChipMinWidths[0]) ? kChipMinWidths[i] : 0;
        UIButton *chip = [FeedbackAppearance typeChipWithTitle:types[i]
                                                       minWidth:minW
                                                            tag:(NSInteger)i + kChipBaseTag
                                                         target:self
                                                         action:@selector(typeChipTapped:)];
        if (i < 2) {
            [chipRow0 addArrangedSubview:chip];
        } else {
            [chipRow1 addArrangedSubview:chip];
        }
    }
    [FeedbackAppearance finishTypeChipRow:chipRow0];
    [FeedbackAppearance finishTypeChipRow:chipRow1];
    [typeSection addArrangedSubview:chipRow0];
    [typeSection addArrangedSubview:chipRow1];
    [self refreshAllChipsInView:typeSection];

    UIStackView *descSection = [[UIStackView alloc] init];
    descSection.translatesAutoresizingMaskIntoConstraints = NO;
    descSection.axis = UILayoutConstraintAxisVertical;
    descSection.spacing = 8;
    [descSection addArrangedSubview:[FeedbackAppearance sectionHeaderRowWithText:@"问题描述或建议" required:YES]];

    UIView *descBox = [[UIView alloc] init];
    descBox.translatesAutoresizingMaskIntoConstraints = NO;
    [FeedbackAppearance styleInputContainer:descBox cornerRadius:16];

    self.descView = [[UITextView alloc] init];
    self.descView.translatesAutoresizingMaskIntoConstraints = NO;
    self.descView.font = [UIFont systemFontOfSize:14];
    self.descView.textColor = [FeedbackAppearance textPrimaryColor];
    self.descView.backgroundColor = UIColor.clearColor;
    self.descView.delegate = self;
    self.descView.textContainerInset = UIEdgeInsetsZero;
    self.descView.textContainer.lineFragmentPadding = 0;

    self.descPlaceholderLabel = [[UILabel alloc] init];
    self.descPlaceholderLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.descPlaceholderLabel.text = @"请详细描述您遇到的问题或建议（至少10个字）";
    self.descPlaceholderLabel.font = [UIFont systemFontOfSize:14];
    self.descPlaceholderLabel.textColor = [UIColor colorWithRed:0xCC / 255.0 green:0xCC / 255.0 blue:0xCC / 255.0 alpha:1];
    self.descPlaceholderLabel.numberOfLines = 0;
    self.descPlaceholderLabel.userInteractionEnabled = NO;

    self.descCountLabel = [[UILabel alloc] init];
    self.descCountLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.descCountLabel.text = @"0/500";
    self.descCountLabel.font = [UIFont systemFontOfSize:14];
    self.descCountLabel.textColor = [FeedbackAppearance textMutedColor];
    self.descCountLabel.textAlignment = NSTextAlignmentRight;

    [descBox addSubview:self.descView];
    [descBox addSubview:self.descPlaceholderLabel];
    [descBox addSubview:self.descCountLabel];

    self.attachRow = [[UIStackView alloc] init];
    self.attachRow.translatesAutoresizingMaskIntoConstraints = NO;
    self.attachRow.axis = UILayoutConstraintAxisHorizontal;
    self.attachRow.spacing = 10;
    [self.attachRow addArrangedSubview:[self attachAddButton]];
    [self refreshAttachRow];

    [descSection addArrangedSubview:descBox];
    [descSection addArrangedSubview:self.attachRow];

    UIStackView *contactSection = [[UIStackView alloc] init];
    contactSection.translatesAutoresizingMaskIntoConstraints = NO;
    contactSection.axis = UILayoutConstraintAxisVertical;
    contactSection.spacing = 8;
    UILabel *contactLabel = [[UILabel alloc] init];
    contactLabel.text = @"联系方式（选填）";
    contactLabel.font = [UIFont systemFontOfSize:14];
    contactLabel.textColor = [FeedbackAppearance textSecondaryColor];
    [contactSection addArrangedSubview:contactLabel];

    self.contactField = [[UITextField alloc] init];
    self.contactField.translatesAutoresizingMaskIntoConstraints = NO;
    self.contactField.font = [UIFont systemFontOfSize:14];
    self.contactField.placeholder = @"您的手机号 / 邮箱（方便我们联系您）";
    self.contactField.backgroundColor = UIColor.whiteColor;
    self.contactField.layer.cornerRadius = 12;
    self.contactField.leftView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, 12, 44)];
    self.contactField.leftViewMode = UITextFieldViewModeAlways;
    [self.contactField.heightAnchor constraintEqualToConstant:44].active = YES;

    [contactSection addArrangedSubview:self.contactField];

    UIStackView *logRow = [[UIStackView alloc] init];
    logRow.translatesAutoresizingMaskIntoConstraints = NO;
    logRow.axis = UILayoutConstraintAxisHorizontal;
    logRow.spacing = 6;
    logRow.alignment = UIStackViewAlignmentCenter;
    UIImageView *logCheck = [FeedbackAppearance checkboxImageViewChecked:self.shareLog];
    logCheck.tag = kLogCheckTag;
    UILabel *logLabel = [[UILabel alloc] init];
    logLabel.text = @"共享应用日志，以便更准确诊断问题";
    logLabel.font = [UIFont systemFontOfSize:12];
    logLabel.textColor = [FeedbackAppearance textSecondaryColor];
    logLabel.numberOfLines = 0;
    [logRow addArrangedSubview:logCheck];
    [logRow addArrangedSubview:logLabel];
    UIButton *logTap = [UIButton buttonWithType:UIButtonTypeCustom];
    logTap.translatesAutoresizingMaskIntoConstraints = NO;
    [logTap addTarget:self action:@selector(toggleShareLog) forControlEvents:UIControlEventTouchUpInside];
    [logRow addSubview:logTap];
    [logTap.topAnchor constraintEqualToAnchor:logRow.topAnchor].active = YES;
    [logTap.bottomAnchor constraintEqualToAnchor:logRow.bottomAnchor].active = YES;
    [logTap.leadingAnchor constraintEqualToAnchor:logRow.leadingAnchor].active = YES;
    [logTap.trailingAnchor constraintEqualToAnchor:logRow.trailingAnchor].active = YES;

    UIStackView *mainStack = [[UIStackView alloc] init];
    mainStack.translatesAutoresizingMaskIntoConstraints = NO;
    mainStack.axis = UILayoutConstraintAxisVertical;
    mainStack.spacing = 20;
    [mainStack addArrangedSubview:typeSection];
    [mainStack addArrangedSubview:descSection];
    [mainStack addArrangedSubview:contactSection];
    if ([FeedbackConfig isLogUploadEnabled]) {
        [mainStack addArrangedSubview:logRow];
    }
    [body addSubview:mainStack];

    UIView *bottomBar = [[UIView alloc] init];
    bottomBar.translatesAutoresizingMaskIntoConstraints = NO;
    bottomBar.backgroundColor = [FeedbackAppearance pageBackgroundColor];
    UIButton *submit = [FeedbackAppearance primaryButtonWithTitle:@"提交"
                                                         target:self
                                                         action:@selector(submitFeedback)];
    self.submitButton = submit;
    [bottomBar addSubview:submit];

    [self.contentView addSubview:header];
    [self.contentView addSubview:scroll];
    [self.contentView addSubview:bottomBar];
    [scroll addSubview:body];

    UILayoutGuide *pageSafe = self.contentView.safeAreaLayoutGuide;
    [NSLayoutConstraint activateConstraints:@[
        [header.topAnchor constraintEqualToAnchor:pageSafe.topAnchor],
        [header.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [header.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [scroll.topAnchor constraintEqualToAnchor:header.bottomAnchor],
        [scroll.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [scroll.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [scroll.bottomAnchor constraintEqualToAnchor:bottomBar.topAnchor],
        [bottomBar.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [bottomBar.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [bottomBar.bottomAnchor constraintEqualToAnchor:pageSafe.bottomAnchor],
        [submit.topAnchor constraintEqualToAnchor:bottomBar.topAnchor constant:6],
        [submit.leadingAnchor constraintEqualToAnchor:bottomBar.leadingAnchor constant:40],
        [submit.trailingAnchor constraintEqualToAnchor:bottomBar.trailingAnchor constant:-40],
        [submit.bottomAnchor constraintEqualToAnchor:bottomBar.bottomAnchor constant:-12],
        [body.topAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.topAnchor constant:6],
        [body.leadingAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.leadingAnchor constant:16],
        [body.trailingAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.trailingAnchor constant:-16],
        [body.bottomAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.bottomAnchor constant:-16],
        [body.widthAnchor constraintEqualToAnchor:scroll.frameLayoutGuide.widthAnchor constant:-32],
        [mainStack.topAnchor constraintEqualToAnchor:body.topAnchor],
        [mainStack.leadingAnchor constraintEqualToAnchor:body.leadingAnchor],
        [mainStack.trailingAnchor constraintEqualToAnchor:body.trailingAnchor],
        [mainStack.bottomAnchor constraintEqualToAnchor:body.bottomAnchor],
        [self.descView.topAnchor constraintEqualToAnchor:descBox.topAnchor constant:10],
        [self.descView.leadingAnchor constraintEqualToAnchor:descBox.leadingAnchor constant:8],
        [self.descView.trailingAnchor constraintEqualToAnchor:descBox.trailingAnchor constant:-8],
        [self.descView.heightAnchor constraintEqualToConstant:100],
        [self.descPlaceholderLabel.topAnchor constraintEqualToAnchor:self.descView.topAnchor],
        [self.descPlaceholderLabel.leadingAnchor constraintEqualToAnchor:self.descView.leadingAnchor],
        [self.descPlaceholderLabel.trailingAnchor constraintEqualToAnchor:self.descView.trailingAnchor],
        [self.descCountLabel.topAnchor constraintEqualToAnchor:self.descView.bottomAnchor constant:4],
        [self.descCountLabel.trailingAnchor constraintEqualToAnchor:descBox.trailingAnchor constant:-8],
        [self.descCountLabel.bottomAnchor constraintEqualToAnchor:descBox.bottomAnchor constant:-6],
    ]];
}

- (void)refreshAllChipsInView:(UIView *)root {
    for (UIView *sub in root.subviews) {
        if ([sub isKindOfClass:[UIButton class]] && sub.tag >= kChipBaseTag) {
            [FeedbackAppearance applyChipStyle:(UIButton *)sub selected:sub.tag - kChipBaseTag == self.selectedTypeIndex];
        }
        [self refreshAllChipsInView:sub];
    }
}

- (void)typeChipTapped:(UIButton *)sender {
    self.selectedTypeIndex = sender.tag - kChipBaseTag;
    [self refreshAllChipsInView:self.contentView];
}

- (void)toggleShareLog {
    self.shareLog = !self.shareLog;
    UIImageView *check = (UIImageView *)[self.contentView viewWithTag:kLogCheckTag];
    if ([check isKindOfClass:[UIImageView class]] && @available(iOS 13.0, *)) {
        NSString *symbol = self.shareLog ? @"checkmark.square.fill" : @"square";
        UIImage *image = [UIImage systemImageNamed:symbol];
        check.image = [image imageWithTintColor:self.shareLog ? [FeedbackAppearance primaryAccentColor]
                                                      : [FeedbackAppearance textMutedColor]
                                           renderingMode:UIImageRenderingModeAlwaysOriginal];
    }
}

- (void)textViewDidChange:(UITextView *)textView {
    if (feedbackUnicodeLength(textView.text) > 500) {
        textView.text = feedbackTruncateToCodePoints(textView.text, 500);
    }
    self.descCountLabel.text =
        [NSString stringWithFormat:@"%lu/500", (unsigned long)feedbackUnicodeLength(textView.text)];
    self.descPlaceholderLabel.hidden = textView.text.length > 0;
}

#pragma mark - Images

- (void)addImages {
    if (@available(iOS 14.0, *)) {
        if (self.imagePaths.count >= kMaxAttachCount) {
            [self toast:@"最多可添加3张图片"];
            return;
        }
        PHPickerConfiguration *config = [[PHPickerConfiguration alloc] init];
        config.selectionLimit = MAX(1, kMaxAttachCount - (NSInteger)self.imagePaths.count);
        config.filter = [PHPickerFilter imagesFilter];
        PHPickerViewController *picker = [[PHPickerViewController alloc] initWithConfiguration:config];
        picker.delegate = self;
        [self presentViewController:picker animated:YES completion:nil];
    }
}

- (void)picker:(PHPickerViewController *)picker didFinishPicking:(NSArray<PHPickerResult *> *)results {
    [picker dismissViewControllerAnimated:YES completion:nil];
    NSURL *dir = [[[NSFileManager defaultManager] URLsForDirectory:NSApplicationSupportDirectory inDomains:NSUserDomainMask].lastObject
                  URLByAppendingPathComponent:@"feedback_images" isDirectory:YES];
    [[NSFileManager defaultManager] createDirectoryAtURL:dir withIntermediateDirectories:YES attributes:nil error:nil];
    for (PHPickerResult *result in results) {
        if (self.imagePaths.count >= kMaxAttachCount) {
            dispatch_async(dispatch_get_main_queue(), ^{
                [self toast:@"最多可添加3张图片"];
            });
            break;
        }
        [result.itemProvider loadObjectOfClass:[UIImage class] completionHandler:^(id object, NSError *error) {
            UIImage *image = (UIImage *)object;
            if (![image isKindOfClass:[UIImage class]]) {
                return;
            }
            NSData *data = UIImageJPEGRepresentation(image, 0.85);
            if (data.length > 5 * 1024 * 1024) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    [self toast:@"图片超过5MB"];
                });
                return;
            }
            NSString *name = [[NSUUID UUID] UUIDString];
            NSURL *file = [dir URLByAppendingPathComponent:[name stringByAppendingPathExtension:@"jpg"]];
            [data writeToURL:file atomically:YES];
            dispatch_async(dispatch_get_main_queue(), ^{
                [self.imagePaths addObject:file.path];
                [self refreshAttachRow];
            });
        }];
    }
}

- (void)showImageViewerForPath:(NSString *)path {
    UIImage *image = [UIImage imageWithContentsOfFile:path];
    if (image == nil) {
        return;
    }
    UIViewController *viewer = [[UIViewController alloc] init];
    viewer.view.backgroundColor = UIColor.blackColor;
    viewer.modalPresentationStyle = UIModalPresentationFullScreen;

    UIImageView *imageView = [[UIImageView alloc] initWithImage:image];
    imageView.translatesAutoresizingMaskIntoConstraints = NO;
    imageView.contentMode = UIViewContentModeScaleAspectFit;
    [viewer.view addSubview:imageView];

    UIButton *close = [UIButton buttonWithType:UIButtonTypeSystem];
    close.translatesAutoresizingMaskIntoConstraints = NO;
    [close setTitle:@"关闭" forState:UIControlStateNormal];
    [close setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    [close addTarget:self action:@selector(dismissImageViewer) forControlEvents:UIControlEventTouchUpInside];
    [viewer.view addSubview:close];

    [NSLayoutConstraint activateConstraints:@[
        [imageView.topAnchor constraintEqualToAnchor:viewer.view.safeAreaLayoutGuide.topAnchor],
        [imageView.leadingAnchor constraintEqualToAnchor:viewer.view.leadingAnchor],
        [imageView.trailingAnchor constraintEqualToAnchor:viewer.view.trailingAnchor],
        [imageView.bottomAnchor constraintEqualToAnchor:viewer.view.bottomAnchor],
        [close.topAnchor constraintEqualToAnchor:viewer.view.safeAreaLayoutGuide.topAnchor constant:12],
        [close.trailingAnchor constraintEqualToAnchor:viewer.view.trailingAnchor constant:-16],
    ]];
    objc_setAssociatedObject(self, @selector(dismissImageViewer), viewer, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    [self presentViewController:viewer animated:YES completion:nil];
}

- (void)dismissImageViewer {
    UIViewController *viewer = objc_getAssociatedObject(self, @selector(dismissImageViewer));
    [viewer dismissViewControllerAnimated:YES completion:nil];
}

#pragma mark - Submit / Success

- (void)submitFeedback {
    if (self.selectedTypeIndex < 0) {
        [self toast:@"请选择问题类型"];
        return;
    }
    NSString *content = [self.descView.text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (feedbackUnicodeLength(content) < 10) {
        [self toast:@"请至少输入10个字"];
        return;
    }
    if (self.shareLog && ![FeedbackConfig isLogUploadEnabled]) {
        [self toast:@"日志上传功能暂未开放"];
        return;
    }
    if (![FeedbackApi isConfigured]) {
        [self toast:@"服务未配置"];
        return;
    }
    NSString *feedbackType = FeedbackTypeLabels()[(NSUInteger)self.selectedTypeIndex];
    self.submitButton.enabled = NO;
    [self showSubmitLoading:self.imagePaths.count > 0];
    __weak __typeof(self) weakSelf = self;
    [FeedbackApi submitFormWithFeedbackType:feedbackType
                                    content:content
                                    contact:self.contactField.text ?: @""
                                 imagePaths:[self.imagePaths copy]
                          shareLogRequested:self.shareLog
                                 completion:^(FeedbackRecord *record, NSError *error) {
        __strong __typeof(weakSelf) self = weakSelf;
        [self dismissSubmitLoading];
        self.submitButton.enabled = YES;
        if (error != nil) {
            [self toastApiError:error];
            return;
        }
        (void)record;
        [self.imagePaths removeAllObjects];
        [self showSuccess];
    }];
}

- (void)showSuccess {
    [self clearContent];

    UIView *header = [self buildHeaderWithBackAction:@selector(close)
                                               title:@"问题和建议"
                                         rightAction:@selector(showList)
                                          rightTitle:@"反馈记录"];
    UIImageView *art = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"FeedbackSuccessArt"]];
    art.translatesAutoresizingMaskIntoConstraints = NO;
    art.contentMode = UIViewContentModeScaleAspectFit;

    UILabel *msg = [[UILabel alloc] init];
    msg.translatesAutoresizingMaskIntoConstraints = NO;
    msg.text = @"感谢您的反馈，我们会尽快处理！";
    msg.font = [UIFont systemFontOfSize:16];
    msg.textColor = [FeedbackAppearance textPrimaryColor];
    msg.textAlignment = NSTextAlignmentCenter;
    msg.numberOfLines = 0;

    UIView *bottomBar = [[UIView alloc] init];
    bottomBar.translatesAutoresizingMaskIntoConstraints = NO;
    UIButton *viewRecords = [FeedbackAppearance primaryButtonWithTitle:@"查看我的反馈记录"
                                                               target:self
                                                               action:@selector(showList)];
    [bottomBar addSubview:viewRecords];

    [self.contentView addSubview:header];
    [self.contentView addSubview:art];
    [self.contentView addSubview:msg];
    [self.contentView addSubview:bottomBar];

    UILayoutGuide *pageSafe = self.contentView.safeAreaLayoutGuide;
    [NSLayoutConstraint activateConstraints:@[
        [header.topAnchor constraintEqualToAnchor:pageSafe.topAnchor],
        [header.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [header.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [art.topAnchor constraintEqualToAnchor:header.bottomAnchor constant:40],
        [art.centerXAnchor constraintEqualToAnchor:self.contentView.centerXAnchor],
        [art.widthAnchor constraintEqualToConstant:135],
        [art.heightAnchor constraintEqualToConstant:138],
        [msg.topAnchor constraintEqualToAnchor:art.bottomAnchor constant:24],
        [msg.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:32],
        [msg.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor constant:-32],
        [bottomBar.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [bottomBar.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [bottomBar.bottomAnchor constraintEqualToAnchor:pageSafe.bottomAnchor],
        [viewRecords.topAnchor constraintEqualToAnchor:bottomBar.topAnchor constant:6],
        [viewRecords.leadingAnchor constraintEqualToAnchor:bottomBar.leadingAnchor constant:40],
        [viewRecords.trailingAnchor constraintEqualToAnchor:bottomBar.trailingAnchor constant:-40],
        [viewRecords.bottomAnchor constraintEqualToAnchor:bottomBar.bottomAnchor constant:-12],
    ]];
}

#pragma mark - List / Empty

- (void)loadFeedbackListPage:(NSInteger)pageNum append:(BOOL)append {
    if (self.listLoading) {
        return;
    }
    self.listLoading = YES;
    if (!append) {
        [self presentListTableIfNeeded];
        [self setListPageLoadingOverlayVisible:YES];
    }
    __weak __typeof(self) weakSelf = self;
    [FeedbackApi fetchListPage:pageNum
                    completion:^(FeedbackApiListPage *page, NSError *error) {
        __strong __typeof(weakSelf) self = weakSelf;
        self.listLoading = NO;
        [self.listRefreshControl endRefreshing];
        [self setListPageLoadingOverlayVisible:NO];
        if (error != nil) {
            [self toastApiError:error];
            return;
        }
        if (!append) {
            [self.listRecords removeAllObjects];
        }
        [self.listRecords addObjectsFromArray:page.list ?: @[]];
        self.listPageNum = page.pageNum > 0 ? page.pageNum : pageNum;
        self.listTotalPages = page.pages > 0 ? page.pages : 1;
        if (!append && self.listRecords.count == 0) {
            [self showEmpty];
            return;
        }
        [self presentListTableIfNeeded];
        [self.listTableView reloadData];
    }];
}

- (void)presentListTable {
    [self clearContent];

    UIView *header = [self buildHeaderWithBackAction:@selector(showForm)
                                               title:@"反馈记录"
                                         rightAction:nil
                                          rightTitle:nil];
    UITableView *table = [[UITableView alloc] initWithFrame:CGRectZero style:UITableViewStylePlain];
    table.translatesAutoresizingMaskIntoConstraints = NO;
    table.backgroundColor = [FeedbackAppearance pageBackgroundColor];
    table.separatorStyle = UITableViewCellSeparatorStyleNone;
    table.dataSource = self;
    table.delegate = self;
    table.contentInset = UIEdgeInsetsMake(8, 0, 16, 0);
    if (@available(iOS 11.0, *)) {
        table.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentNever;
    }
    self.listTableView = table;

    self.listRefreshControl = [[UIRefreshControl alloc] init];
    [self.listRefreshControl addTarget:self action:@selector(refreshFeedbackList) forControlEvents:UIControlEventValueChanged];
    if (@available(iOS 10.0, *)) {
        table.refreshControl = self.listRefreshControl;
    }

    UILayoutGuide *pageSafe = self.contentView.safeAreaLayoutGuide;
    [self.contentView addSubview:header];
    [self.contentView addSubview:table];
    [NSLayoutConstraint activateConstraints:@[
        [header.topAnchor constraintEqualToAnchor:pageSafe.topAnchor],
        [header.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [header.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [table.topAnchor constraintEqualToAnchor:header.bottomAnchor],
        [table.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [table.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [table.bottomAnchor constraintEqualToAnchor:pageSafe.bottomAnchor],
    ]];
    [table reloadData];
}

- (void)refreshFeedbackList {
    [self loadFeedbackListPage:1 append:NO];
}

- (void)showList {
    self.currentDetail = nil;
    [self presentListTable];
    [self loadFeedbackListPage:1 append:NO];
}

- (void)showEmpty {
    [self clearContent];

    UIView *header = [self buildHeaderWithBackAction:@selector(showForm)
                                               title:@"我的反馈"
                                         rightAction:nil
                                          rightTitle:nil];
    UIImageView *art = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"FeedbackEmptyArt"]];
    art.translatesAutoresizingMaskIntoConstraints = NO;
    art.contentMode = UIViewContentModeScaleAspectFit;

    UILabel *msg = [[UILabel alloc] init];
    msg.translatesAutoresizingMaskIntoConstraints = NO;
    msg.text = @"您还没有提交过反馈";
    msg.font = [UIFont systemFontOfSize:14];
    msg.textColor = [FeedbackAppearance textMutedColor];

    UIView *bottomBar = [[UIView alloc] init];
    bottomBar.translatesAutoresizingMaskIntoConstraints = NO;
    UIButton *backBtn = [FeedbackAppearance grayButtonWithTitle:@"返回"
                                                        target:self
                                                        action:@selector(showForm)];
    [bottomBar addSubview:backBtn];

    [self.contentView addSubview:header];
    [self.contentView addSubview:art];
    [self.contentView addSubview:msg];
    [self.contentView addSubview:bottomBar];

    UILayoutGuide *pageSafe = self.contentView.safeAreaLayoutGuide;
    [NSLayoutConstraint activateConstraints:@[
        [header.topAnchor constraintEqualToAnchor:pageSafe.topAnchor],
        [header.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [header.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [art.topAnchor constraintEqualToAnchor:header.bottomAnchor constant:60],
        [art.centerXAnchor constraintEqualToAnchor:self.contentView.centerXAnchor],
        [art.widthAnchor constraintEqualToConstant:269],
        [art.heightAnchor constraintEqualToConstant:190],
        [msg.topAnchor constraintEqualToAnchor:art.bottomAnchor constant:16],
        [msg.centerXAnchor constraintEqualToAnchor:self.contentView.centerXAnchor],
        [bottomBar.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [bottomBar.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [bottomBar.bottomAnchor constraintEqualToAnchor:pageSafe.bottomAnchor],
        [backBtn.topAnchor constraintEqualToAnchor:bottomBar.topAnchor constant:6],
        [backBtn.leadingAnchor constraintEqualToAnchor:bottomBar.leadingAnchor constant:106],
        [backBtn.trailingAnchor constraintEqualToAnchor:bottomBar.trailingAnchor constant:-106],
        [backBtn.bottomAnchor constraintEqualToAnchor:bottomBar.bottomAnchor constant:-12],
    ]];
}

#pragma mark - Detail images

- (NSString *)resolvedImageSource:(NSString *)source {
    if (source.length == 0) {
        return @"";
    }
    if ([source hasPrefix:@"http://"] || [source hasPrefix:@"https://"]) {
        return source;
    }
    if ([source hasPrefix:@"/"] && [[NSFileManager defaultManager] fileExistsAtPath:source]) {
        return source;
    }
    if ([[NSFileManager defaultManager] fileExistsAtPath:source]) {
        return source;
    }
    return [FeedbackConfig assetUrl:source];
}

- (void)loadImageForSource:(NSString *)source completion:(void (^)(UIImage *_Nullable image))completion {
    NSString *resolved = [self resolvedImageSource:source];
    if (resolved.length == 0) {
        if (completion) {
            completion(nil);
        }
        return;
    }
    if ([resolved hasPrefix:@"http://"] || [resolved hasPrefix:@"https://"]) {
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
            NSURL *url = [NSURL URLWithString:resolved];
            NSData *data = url ? [NSData dataWithContentsOfURL:url] : nil;
            UIImage *image = data ? [UIImage imageWithData:data] : nil;
            dispatch_async(dispatch_get_main_queue(), ^{
                if (completion) {
                    completion(image);
                }
            });
        });
        return;
    }
    UIImage *image = [UIImage imageWithContentsOfFile:resolved];
    if (completion) {
        completion(image);
    }
}

- (UIImageView *)detailThumbnailForPath:(NSString *)path {
    UIImageView *thumb = [[UIImageView alloc] init];
    thumb.translatesAutoresizingMaskIntoConstraints = NO;
    thumb.contentMode = UIViewContentModeScaleAspectFill;
    thumb.clipsToBounds = YES;
    thumb.layer.cornerRadius = 8;
    thumb.backgroundColor = [FeedbackAppearance contentBlockBackgroundColor];
    thumb.userInteractionEnabled = YES;
    UITapGestureRecognizer *tap = [[UITapGestureRecognizer alloc] initWithTarget:self
                                                                          action:@selector(detailImageTapped:)];
    [thumb addGestureRecognizer:tap];
    objc_setAssociatedObject(thumb, @selector(detailImageTapped:), path, OBJC_ASSOCIATION_COPY_NONATOMIC);
    __weak UIImageView *weakThumb = thumb;
    [self loadImageForSource:path completion:^(UIImage *image) {
        if (image != nil) {
            weakThumb.image = image;
        }
    }];
    return thumb;
}

- (UIView *)horizontalImageRowForPaths:(NSArray<NSString *> *)paths {
    UIView *row = [[UIView alloc] init];
    row.translatesAutoresizingMaskIntoConstraints = NO;
    UIImageView *previous = nil;
    for (NSString *path in paths) {
        UIImageView *thumb = [self detailThumbnailForPath:path];
        [row addSubview:thumb];
        NSMutableArray<NSLayoutConstraint *> *thumbConstraints = [NSMutableArray arrayWithArray:@[
            [thumb.topAnchor constraintEqualToAnchor:row.topAnchor],
            [thumb.widthAnchor constraintEqualToConstant:72],
            [thumb.heightAnchor constraintEqualToConstant:72],
        ]];
        if (previous != nil) {
            [thumbConstraints addObject:[thumb.leadingAnchor constraintEqualToAnchor:previous.trailingAnchor constant:8]];
        } else {
            [thumbConstraints addObject:[thumb.leadingAnchor constraintEqualToAnchor:row.leadingAnchor]];
        }
        [NSLayoutConstraint activateConstraints:thumbConstraints];
        previous = thumb;
    }
    if (previous != nil) {
        NSLayoutConstraint *trail = [previous.trailingAnchor constraintEqualToAnchor:row.trailingAnchor];
        trail.priority = UILayoutPriorityDefaultLow;
        trail.active = YES;
    }
    [row.heightAnchor constraintEqualToConstant:72].active = YES;
    return row;
}

- (void)detailImageTapped:(UITapGestureRecognizer *)gesture {
    NSString *source = objc_getAssociatedObject(gesture.view, @selector(detailImageTapped:));
    [self showImageViewerForSource:source];
}

- (void)showImageViewerForSource:(NSString *)source {
    __weak __typeof(self) weakSelf = self;
    [self loadImageForSource:source completion:^(UIImage *image) {
        if (image == nil) {
            return;
        }
        UIViewController *viewer = [[UIViewController alloc] init];
        viewer.view.backgroundColor = UIColor.blackColor;
        viewer.modalPresentationStyle = UIModalPresentationFullScreen;
        UIImageView *imageView = [[UIImageView alloc] initWithImage:image];
        imageView.translatesAutoresizingMaskIntoConstraints = NO;
        imageView.contentMode = UIViewContentModeScaleAspectFit;
        [viewer.view addSubview:imageView];
        UIButton *close = [UIButton buttonWithType:UIButtonTypeSystem];
        close.translatesAutoresizingMaskIntoConstraints = NO;
        [close setTitle:@"关闭" forState:UIControlStateNormal];
        [close setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
        [close addTarget:weakSelf action:@selector(dismissImageViewer) forControlEvents:UIControlEventTouchUpInside];
        [viewer.view addSubview:close];
        [NSLayoutConstraint activateConstraints:@[
            [imageView.topAnchor constraintEqualToAnchor:viewer.view.safeAreaLayoutGuide.topAnchor],
            [imageView.leadingAnchor constraintEqualToAnchor:viewer.view.leadingAnchor],
            [imageView.trailingAnchor constraintEqualToAnchor:viewer.view.trailingAnchor],
            [imageView.bottomAnchor constraintEqualToAnchor:viewer.view.bottomAnchor],
            [close.topAnchor constraintEqualToAnchor:viewer.view.safeAreaLayoutGuide.topAnchor constant:12],
            [close.trailingAnchor constraintEqualToAnchor:viewer.view.trailingAnchor constant:-16],
        ]];
        objc_setAssociatedObject(weakSelf, @selector(dismissImageViewer), viewer, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
        [weakSelf presentViewController:viewer animated:YES completion:nil];
    }];
}

#pragma mark - Detail

- (void)showDetail:(NSString *)recordId {
    XLFeedbackDetailLog([NSString stringWithFormat:@"XLFeedbackDetail open id=%@", recordId ?: @""]);
    if (![FeedbackApi isConfigured]) {
        [self toast:@"服务未配置"];
        return;
    }
    if (recordId.length == 0) {
        [self showList];
        return;
    }
    if ([recordId isEqualToString:self.detailLoadingRecordId]) {
        return;
    }
    self.detailLoadingRecordId = recordId;
    [self clearContent];
    UIView *loadingHost = [[UIView alloc] initWithFrame:self.contentView.bounds];
    loadingHost.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    UIActivityIndicatorView *spinner;
    if (@available(iOS 13.0, *)) {
        spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleLarge];
    } else {
        spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleWhiteLarge];
    }
    spinner.translatesAutoresizingMaskIntoConstraints = NO;
    [spinner startAnimating];
    [loadingHost addSubview:spinner];
    [self.contentView addSubview:loadingHost];
    [NSLayoutConstraint activateConstraints:@[
        [spinner.centerXAnchor constraintEqualToAnchor:loadingHost.centerXAnchor],
        [spinner.centerYAnchor constraintEqualToAnchor:loadingHost.centerYAnchor],
    ]];
    __weak __typeof(self) weakSelf = self;
    [FeedbackApi fetchDetail:recordId
                  completion:^(FeedbackRecord *record, NSError *error) {
        __strong __typeof(weakSelf) self = weakSelf;
        self.detailLoadingRecordId = nil;
        [loadingHost removeFromSuperview];
        if (error != nil) {
            XLFeedbackDetailLog([NSString stringWithFormat:@"XLFeedbackDetail fetchError id=%@ err=%@",
                                 recordId, error.localizedDescription ?: @""]);
            [self toastApiError:error];
            [self showList];
            return;
        }
        self.currentDetail = record;
        [self bindDetailWithRecord:record];
    }];
}

- (void)bindDetailWithRecord:(FeedbackRecord *)record {
    [self clearContent];

    UIView *header = [self buildDetailHeaderForRecord:record backAction:@selector(showList)];

    UIScrollView *scroll = [[UIScrollView alloc] init];
    scroll.translatesAutoresizingMaskIntoConstraints = NO;
    scroll.alwaysBounceVertical = YES;
    if (@available(iOS 11.0, *)) {
        scroll.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentNever;
    }

    UIView *myBubble = [[UIView alloc] init];
    myBubble.translatesAutoresizingMaskIntoConstraints = NO;
    [FeedbackAppearance styleInputContainer:myBubble cornerRadius:12];

    UILabel *myType = [[UILabel alloc] init];
    myType.translatesAutoresizingMaskIntoConstraints = NO;
    myType.text = record.type;
    myType.font = [UIFont boldSystemFontOfSize:16];
    myType.textColor = [FeedbackAppearance textPrimaryColor];

    UIView *myContentBlock = [[UIView alloc] init];
    myContentBlock.translatesAutoresizingMaskIntoConstraints = NO;
    [FeedbackAppearance styleContentBlock:myContentBlock];
    UILabel *myContent = [[UILabel alloc] init];
    myContent.translatesAutoresizingMaskIntoConstraints = NO;
    myContent.text = record.content.length > 0 ? record.content : @"暂无文字描述";
    myContent.font = [UIFont systemFontOfSize:14];
    myContent.numberOfLines = 0;
    myContent.textColor = [FeedbackAppearance textPrimaryColor];
    [myContentBlock addSubview:myContent];

    UILabel *myTime = [[UILabel alloc] init];
    myTime.translatesAutoresizingMaskIntoConstraints = NO;
    myTime.text = [NSString stringWithFormat:@"%@ 反馈成功", [self formatTime:record.submitTime]];
    myTime.font = [UIFont systemFontOfSize:14];
    myTime.textColor = [FeedbackAppearance textSecondaryColor];

    UIView *myImagesView = nil;
    if (record.imagePaths.count > 0) {
        myImagesView = [self horizontalImageRowForPaths:record.imagePaths];
    }

    [myBubble addSubview:myType];
    [myBubble addSubview:myContentBlock];
    if (myImagesView != nil) {
        [myBubble addSubview:myImagesView];
    }
    [myBubble addSubview:myTime];

    UILabel *meAvatar = [FeedbackAppearance avatarLabelWithText:@"我" accent:YES];

    UIView *body = [[UIView alloc] init];
    body.translatesAutoresizingMaskIntoConstraints = NO;
    [body addSubview:myBubble];
    [body addSubview:meAvatar];

    BOOL hasReplyText = record.replyText.length > 0;
    BOOL hasReplyImages = record.replyImagePaths.count > 0;
    BOOL hasReply = hasReplyText || hasReplyImages;

    UILabel *serviceAvatar = nil;
    UIView *replyBubble = nil;
    UIView *replyImagesView = nil;
    UILabel *replyTextLabel = nil;
    UILabel *replyTimeLabel = nil;
    if (hasReply) {
        serviceAvatar = [FeedbackAppearance avatarLabelWithText:@"客" accent:NO];
        replyBubble = [[UIView alloc] init];
        replyBubble.translatesAutoresizingMaskIntoConstraints = NO;
        [FeedbackAppearance styleInputContainer:replyBubble cornerRadius:12];
        [body addSubview:serviceAvatar];
        [body addSubview:replyBubble];

        NSLayoutYAxisAnchor *replyTop = replyBubble.topAnchor;
        CGFloat replyPadTop = 8;
        if (hasReplyText) {
            replyTextLabel = [[UILabel alloc] init];
            replyTextLabel.translatesAutoresizingMaskIntoConstraints = NO;
            replyTextLabel.text = record.replyText;
            replyTextLabel.font = [UIFont systemFontOfSize:16];
            replyTextLabel.numberOfLines = 0;
            replyTextLabel.textColor = [FeedbackAppearance textPrimaryColor];
            [replyBubble addSubview:replyTextLabel];
            [NSLayoutConstraint activateConstraints:@[
                [replyTextLabel.topAnchor constraintEqualToAnchor:replyTop constant:replyPadTop],
                [replyTextLabel.leadingAnchor constraintEqualToAnchor:replyBubble.leadingAnchor constant:12],
                [replyTextLabel.trailingAnchor constraintEqualToAnchor:replyBubble.trailingAnchor constant:-12],
            ]];
            replyTop = replyTextLabel.bottomAnchor;
            replyPadTop = 8;
        }
        if (hasReplyImages) {
            replyImagesView = [self horizontalImageRowForPaths:record.replyImagePaths];
            [replyBubble addSubview:replyImagesView];
            [NSLayoutConstraint activateConstraints:@[
                [replyImagesView.topAnchor constraintEqualToAnchor:replyTop constant:replyPadTop],
                [replyImagesView.leadingAnchor constraintEqualToAnchor:replyBubble.leadingAnchor constant:12],
                [replyImagesView.trailingAnchor constraintEqualToAnchor:replyBubble.trailingAnchor constant:-12],
            ]];
            replyTop = replyImagesView.bottomAnchor;
            replyPadTop = 8;
        }
        if (record.replyTime > 0) {
            replyTimeLabel = [[UILabel alloc] init];
            replyTimeLabel.translatesAutoresizingMaskIntoConstraints = NO;
            replyTimeLabel.text = [NSString stringWithFormat:@"%@ 已回复", [self formatTime:record.replyTime]];
            replyTimeLabel.font = [UIFont systemFontOfSize:14];
            replyTimeLabel.textColor = [FeedbackAppearance textSecondaryColor];
            [replyBubble addSubview:replyTimeLabel];
            [NSLayoutConstraint activateConstraints:@[
                [replyTimeLabel.topAnchor constraintEqualToAnchor:replyTop constant:replyPadTop],
                [replyTimeLabel.leadingAnchor constraintEqualToAnchor:replyBubble.leadingAnchor constant:12],
                [replyTimeLabel.trailingAnchor constraintEqualToAnchor:replyBubble.trailingAnchor constant:-12],
                [replyTimeLabel.bottomAnchor constraintEqualToAnchor:replyBubble.bottomAnchor constant:-8],
            ]];
        } else {
            NSLayoutYAxisAnchor *replyBottomAnchor = replyImagesView != nil ? replyImagesView.bottomAnchor
                                                                          : replyTextLabel.bottomAnchor;
            [NSLayoutConstraint activateConstraints:@[
                [replyBottomAnchor constraintEqualToAnchor:replyBubble.bottomAnchor constant:-8],
            ]];
        }
    }

    XLFeedbackDetailLog([NSString stringWithFormat:@"XLFeedbackDetail bind no=%@ type=%@ contentLen=%lu images=%lu",
                         record.recordId, record.type, (unsigned long)record.content.length,
                         (unsigned long)record.imagePaths.count]);

    UIView *bottomBar = [[UIView alloc] init];
    bottomBar.translatesAutoresizingMaskIntoConstraints = NO;
    bottomBar.backgroundColor = [FeedbackAppearance pageBackgroundColor];

    BOOL processing = (record.status == FeedbackStatusSubmitted || record.status == FeedbackStatusProcessing) && !hasReply;
    BOOL canClose = [record canClose];
    BOOL closed = record.status == FeedbackStatusClosed;

    if (processing) {
        UILabel *hint = [[UILabel alloc] init];
        hint.translatesAutoresizingMaskIntoConstraints = NO;
        hint.text = @"已收到，感谢反馈";
        hint.textAlignment = NSTextAlignmentCenter;
        hint.font = [UIFont systemFontOfSize:16];
        hint.textColor = [UIColor colorWithWhite:0 alpha:0.89];
        hint.backgroundColor = [UIColor colorWithRed:0xF0 / 255.0 green:0xF0 / 255.0 blue:0xF0 / 255.0 alpha:1];
        hint.layer.cornerRadius = 12;
        hint.clipsToBounds = YES;
        [bottomBar addSubview:hint];
        [NSLayoutConstraint activateConstraints:@[
            [hint.topAnchor constraintEqualToAnchor:bottomBar.topAnchor constant:6],
            [hint.leadingAnchor constraintEqualToAnchor:bottomBar.leadingAnchor constant:16],
            [hint.trailingAnchor constraintEqualToAnchor:bottomBar.trailingAnchor constant:-16],
            [hint.bottomAnchor constraintEqualToAnchor:bottomBar.bottomAnchor constant:-6],
            [hint.heightAnchor constraintEqualToConstant:54],
        ]];
    } else if (canClose) {
        UIButton *resolved = [FeedbackAppearance whiteRoundedButtonWithTitle:@"问题已解决"
                                                                     target:self
                                                                     action:@selector(closeFeedback)];
        UIButton *closeFeedbackBtn = [FeedbackAppearance primaryButtonWithTitle:@"关闭反馈"
                                                                        target:self
                                                                        action:@selector(closeFeedback)];
        closeFeedbackBtn.layer.cornerRadius = 12;
        UIStackView *actions = [[UIStackView alloc] initWithArrangedSubviews:@[ resolved, closeFeedbackBtn ]];
        actions.translatesAutoresizingMaskIntoConstraints = NO;
        actions.axis = UILayoutConstraintAxisHorizontal;
        actions.spacing = 12;
        actions.distribution = UIStackViewDistributionFillEqually;
        [bottomBar addSubview:actions];
        [NSLayoutConstraint activateConstraints:@[
            [actions.topAnchor constraintEqualToAnchor:bottomBar.topAnchor constant:6],
            [actions.leadingAnchor constraintEqualToAnchor:bottomBar.leadingAnchor constant:16],
            [actions.trailingAnchor constraintEqualToAnchor:bottomBar.trailingAnchor constant:-16],
            [actions.bottomAnchor constraintEqualToAnchor:bottomBar.bottomAnchor constant:-6],
        ]];
    } else if (closed) {
        UILabel *hint = [[UILabel alloc] init];
        hint.translatesAutoresizingMaskIntoConstraints = NO;
        hint.text = @"该反馈已关闭";
        hint.textAlignment = NSTextAlignmentCenter;
        hint.font = [UIFont systemFontOfSize:16];
        hint.textColor = [UIColor colorWithWhite:0 alpha:0.89];
        hint.backgroundColor = [UIColor colorWithRed:0xF0 / 255.0 green:0xF0 / 255.0 blue:0xF0 / 255.0 alpha:1];
        hint.layer.cornerRadius = 12;
        hint.clipsToBounds = YES;
        [bottomBar addSubview:hint];
        [NSLayoutConstraint activateConstraints:@[
            [hint.topAnchor constraintEqualToAnchor:bottomBar.topAnchor constant:6],
            [hint.leadingAnchor constraintEqualToAnchor:bottomBar.leadingAnchor constant:16],
            [hint.trailingAnchor constraintEqualToAnchor:bottomBar.trailingAnchor constant:-16],
            [hint.bottomAnchor constraintEqualToAnchor:bottomBar.bottomAnchor constant:-6],
            [hint.heightAnchor constraintEqualToConstant:54],
        ]];
    }

    [scroll addSubview:body];
    [self.contentView addSubview:header];
    [self.contentView addSubview:scroll];
    [self.contentView addSubview:bottomBar];

    UILayoutGuide *pageSafe = self.contentView.safeAreaLayoutGuide;
    NSLayoutYAxisAnchor *bodyBottomAnchor = myBubble.bottomAnchor;
    NSMutableArray<NSLayoutConstraint *> *layout = [NSMutableArray arrayWithArray:@[
        [header.topAnchor constraintEqualToAnchor:pageSafe.topAnchor],
        [header.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [header.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [scroll.topAnchor constraintEqualToAnchor:header.bottomAnchor],
        [scroll.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [scroll.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [scroll.bottomAnchor constraintEqualToAnchor:bottomBar.topAnchor],
        [bottomBar.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [bottomBar.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [bottomBar.bottomAnchor constraintEqualToAnchor:pageSafe.bottomAnchor],
        [body.topAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.topAnchor constant:8],
        [body.leadingAnchor constraintEqualToAnchor:scroll.frameLayoutGuide.leadingAnchor constant:16],
        [body.trailingAnchor constraintEqualToAnchor:scroll.frameLayoutGuide.trailingAnchor constant:-16],
        [body.bottomAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.bottomAnchor constant:-16],
        [meAvatar.topAnchor constraintEqualToAnchor:body.topAnchor constant:8],
        [meAvatar.trailingAnchor constraintEqualToAnchor:body.trailingAnchor],
        [myBubble.topAnchor constraintEqualToAnchor:body.topAnchor constant:8],
        [myBubble.leadingAnchor constraintEqualToAnchor:body.leadingAnchor],
        [myBubble.trailingAnchor constraintEqualToAnchor:meAvatar.leadingAnchor constant:-12],
        [myType.topAnchor constraintEqualToAnchor:myBubble.topAnchor constant:8],
        [myType.leadingAnchor constraintEqualToAnchor:myBubble.leadingAnchor constant:12],
        [myType.trailingAnchor constraintEqualToAnchor:myBubble.trailingAnchor constant:-12],
        [myContentBlock.topAnchor constraintEqualToAnchor:myType.bottomAnchor constant:8],
        [myContentBlock.leadingAnchor constraintEqualToAnchor:myBubble.leadingAnchor constant:12],
        [myContentBlock.trailingAnchor constraintEqualToAnchor:myBubble.trailingAnchor constant:-12],
        [myContent.topAnchor constraintEqualToAnchor:myContentBlock.topAnchor constant:10],
        [myContent.leadingAnchor constraintEqualToAnchor:myContentBlock.leadingAnchor constant:12],
        [myContent.trailingAnchor constraintEqualToAnchor:myContentBlock.trailingAnchor constant:-12],
        [myContent.bottomAnchor constraintEqualToAnchor:myContentBlock.bottomAnchor constant:-10],
    ]];
    NSLayoutYAxisAnchor *myTimeTopAnchor = myContentBlock.bottomAnchor;
    if (myImagesView != nil) {
        [layout addObjectsFromArray:@[
            [myImagesView.topAnchor constraintEqualToAnchor:myContentBlock.bottomAnchor constant:8],
            [myImagesView.leadingAnchor constraintEqualToAnchor:myBubble.leadingAnchor constant:12],
            [myImagesView.trailingAnchor constraintEqualToAnchor:myBubble.trailingAnchor constant:-12],
        ]];
        myTimeTopAnchor = myImagesView.bottomAnchor;
    }
    [layout addObjectsFromArray:@[
        [myTime.topAnchor constraintEqualToAnchor:myTimeTopAnchor constant:8],
        [myTime.leadingAnchor constraintEqualToAnchor:myBubble.leadingAnchor constant:12],
        [myTime.trailingAnchor constraintEqualToAnchor:myBubble.trailingAnchor constant:-12],
        [myTime.bottomAnchor constraintEqualToAnchor:myBubble.bottomAnchor constant:-8],
    ]];
    if (hasReply) {
        [layout addObjectsFromArray:@[
            [serviceAvatar.topAnchor constraintEqualToAnchor:myBubble.bottomAnchor constant:20],
            [serviceAvatar.leadingAnchor constraintEqualToAnchor:body.leadingAnchor],
            [replyBubble.topAnchor constraintEqualToAnchor:myBubble.bottomAnchor constant:20],
            [replyBubble.leadingAnchor constraintEqualToAnchor:serviceAvatar.trailingAnchor constant:10],
            [replyBubble.trailingAnchor constraintEqualToAnchor:body.trailingAnchor],
        ]];
        bodyBottomAnchor = replyBubble.bottomAnchor;
    }
    [layout addObject:[bodyBottomAnchor constraintEqualToAnchor:body.bottomAnchor constant:-16]];
    [NSLayoutConstraint activateConstraints:layout];
    [scroll layoutIfNeeded];
    XLFeedbackDetailLog([NSString stringWithFormat:@"XLFeedbackDetail layout scrollFrameH=%.0f scrollContentH=%.0f bubbleH=%.0f headerH=%.0f",
                         scroll.bounds.size.height, scroll.contentSize.height, myBubble.bounds.size.height,
                         header.bounds.size.height]);
}

- (void)closeFeedback {
    if (self.currentDetail == nil || ![self.currentDetail canClose]) {
        return;
    }
    __weak __typeof(self) weakSelf = self;
    NSString *feedbackNo = self.currentDetail.recordId;
    [HomeCardDialogPresenter presentConfirmFrom:self
                                          title:@"关闭反馈"
                                        message:@"问题已解决是否确认关闭该反馈"
                                   confirmStyle:HomeCardDialogConfirmStylePrimary
                                     completion:^(BOOL confirmed) {
        if (!confirmed) {
            return;
        }
        [FeedbackApi closeFeedback:feedbackNo completion:^(NSError *error) {
            if (error != nil) {
                [weakSelf toastApiError:error];
                return;
            }
            [weakSelf showDetail:feedbackNo];
        }];
    }];
}

#pragma mark - Table

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return (NSInteger)self.listRecords.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    FeedbackListCell *cell = [tableView dequeueReusableCellWithIdentifier:@"FeedbackListCell"];
    if (cell == nil) {
        cell = [[FeedbackListCell alloc] initWithStyle:UITableViewCellStyleDefault reuseIdentifier:@"FeedbackListCell"];
    }
    FeedbackRecord *record = self.listRecords[(NSUInteger)indexPath.row];
    [cell configureWithRecord:record timeText:[self formatTime:record.submitTime]];
    return cell;
}

- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath {
    return UITableViewAutomaticDimension;
}

- (CGFloat)tableView:(UITableView *)tableView estimatedHeightForRowAtIndexPath:(NSIndexPath *)indexPath {
    return 120;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
    [self showDetail:self.listRecords[(NSUInteger)indexPath.row].recordId];
}

- (void)scrollViewDidScroll:(UIScrollView *)scrollView {
    if (scrollView != self.listTableView || self.listLoading) {
        return;
    }
    if (self.listPageNum >= self.listTotalPages) {
        return;
    }
    CGFloat offsetY = scrollView.contentOffset.y;
    CGFloat contentHeight = scrollView.contentSize.height;
    CGFloat frameHeight = scrollView.frame.size.height;
    if (contentHeight > 0 && offsetY > contentHeight - frameHeight - 120) {
        [self loadFeedbackListPage:self.listPageNum + 1 append:YES];
    }
}

#pragma mark - Toast / Close

- (void)toast:(NSString *)message {
    [AppToastPresenter showMessage:message from:self];
}

- (void)close {
    [self dismissViewControllerAnimated:YES completion:nil];
}

@end
