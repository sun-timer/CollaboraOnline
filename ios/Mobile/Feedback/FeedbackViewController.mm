// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "FeedbackViewController.h"

#import "FeedbackAppearance.h"
#import "FeedbackRecord.h"
#import "FeedbackStore.h"
#import "Settings/AppChromeHelper.h"
#import "Settings/AppToastPresenter.h"
#import "Settings/HomeCardDialogPresenter.h"

#import <PhotosUI/PhotosUI.h>
#import <objc/runtime.h>

static NSArray<NSString *> *FeedbackTypeLabels(void) {
    return @[ @"功能异常", @"产品建议与改进", @"使用体验问题", @"其他" ];
}

static const NSInteger kAttachAddTag = 8801;
static const NSInteger kLogCheckTag = 8802;
static const NSInteger kChipBaseTag = 8900;

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
        [FeedbackAppearance styleInputContainer:card cornerRadius:12];
        [self.contentView addSubview:card];

        self.typeLabel = [[UILabel alloc] init];
        self.typeLabel.translatesAutoresizingMaskIntoConstraints = NO;
        self.typeLabel.font = [UIFont boldSystemFontOfSize:18];
        self.typeLabel.textColor = [FeedbackAppearance textPrimaryColor];

        self.timeLabel = [[UILabel alloc] init];
        self.timeLabel.translatesAutoresizingMaskIntoConstraints = NO;
        self.timeLabel.font = [UIFont systemFontOfSize:14];
        self.timeLabel.textColor = [FeedbackAppearance textMutedColor];

        UIStackView *statusRow = [[UIStackView alloc] init];
        statusRow.translatesAutoresizingMaskIntoConstraints = NO;
        statusRow.axis = UILayoutConstraintAxisHorizontal;
        statusRow.spacing = 4;
        statusRow.alignment = UIStackViewAlignmentCenter;

        self.statusDot = [[UIView alloc] init];
        self.statusDot.translatesAutoresizingMaskIntoConstraints = NO;
        self.statusDot.layer.cornerRadius = 4;
        self.statusDot.clipsToBounds = YES;
        [NSLayoutConstraint activateConstraints:@[
            [self.statusDot.widthAnchor constraintEqualToConstant:8],
            [self.statusDot.heightAnchor constraintEqualToConstant:8],
        ]];
        self.statusLabel = [[UILabel alloc] init];
        self.statusLabel.font = [UIFont systemFontOfSize:14];
        [statusRow addArrangedSubview:self.statusDot];
        [statusRow addArrangedSubview:self.statusLabel];

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

        UIStackView *topRow = [[UIStackView alloc] init];
        topRow.translatesAutoresizingMaskIntoConstraints = NO;
        topRow.axis = UILayoutConstraintAxisHorizontal;
        topRow.alignment = UIStackViewAlignmentCenter;
        topRow.spacing = 8;

        UIStackView *leftCol = [[UIStackView alloc] init];
        leftCol.axis = UILayoutConstraintAxisVertical;
        leftCol.spacing = 2;
        [leftCol addArrangedSubview:self.typeLabel];
        [leftCol addArrangedSubview:self.timeLabel];

        [topRow addArrangedSubview:leftCol];
        [topRow addArrangedSubview:statusRow];
        [topRow addArrangedSubview:arrow];

        UIView *contentBlock = [[UIView alloc] init];
        contentBlock.translatesAutoresizingMaskIntoConstraints = NO;
        [FeedbackAppearance styleContentBlock:contentBlock];

        self.contentLabel = [[UILabel alloc] init];
        self.contentLabel.translatesAutoresizingMaskIntoConstraints = NO;
        self.contentLabel.font = [UIFont systemFontOfSize:14];
        self.contentLabel.textColor = [FeedbackAppearance textSecondaryColor];
        self.contentLabel.numberOfLines = 2;
        [contentBlock addSubview:self.contentLabel];

        UIStackView *stack = [[UIStackView alloc] init];
        stack.translatesAutoresizingMaskIntoConstraints = NO;
        stack.axis = UILayoutConstraintAxisVertical;
        stack.spacing = 8;
        [stack addArrangedSubview:topRow];
        [stack addArrangedSubview:contentBlock];
        [card addSubview:stack];

        [NSLayoutConstraint activateConstraints:@[
            [card.topAnchor constraintEqualToAnchor:self.contentView.topAnchor],
            [card.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:16],
            [card.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor constant:-16],
            [card.bottomAnchor constraintEqualToAnchor:self.contentView.bottomAnchor constant:-8],
            [stack.topAnchor constraintEqualToAnchor:card.topAnchor constant:8],
            [stack.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:12],
            [stack.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-12],
            [stack.bottomAnchor constraintEqualToAnchor:card.bottomAnchor constant:-8],
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
    self.contentLabel.text = record.content;
    self.statusDot.backgroundColor = [FeedbackAppearance statusDotColorForStatus:record.status];
    self.statusLabel.text = [FeedbackAppearance statusTextForStatus:record.status];
    self.statusLabel.textColor = [FeedbackAppearance statusTextColorForStatus:record.status];
}

@end

@interface FeedbackViewController () <PHPickerViewControllerDelegate, UITextViewDelegate, UITableViewDataSource, UITableViewDelegate>
@property (strong, nonatomic) UIView *contentView;
@property (assign, nonatomic) NSInteger selectedTypeIndex;
@property (strong, nonatomic) NSMutableArray<NSString *> *imagePaths;
@property (assign, nonatomic) BOOL shareLog;
@property (strong, nonatomic) UITextView *descView;
@property (strong, nonatomic) UILabel *descCountLabel;
@property (strong, nonatomic) UITextField *contactField;
@property (strong, nonatomic) UIStackView *attachRow;
@property (strong, nonatomic) FeedbackRecord *currentDetail;
@property (copy, nonatomic) NSArray<FeedbackRecord *> *listRecords;
@end

@implementation FeedbackViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    [AppChromeHelper applySecondaryPageBackground:self.view];
    self.imagePaths = [NSMutableArray array];
    self.selectedTypeIndex = -1;

    self.contentView = [[UIView alloc] init];
    self.contentView.translatesAutoresizingMaskIntoConstraints = NO;
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
}

- (NSString *)formatTime:(NSTimeInterval)time {
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    formatter.dateFormat = @"yyyy/MM/dd HH:mm";
    return [formatter stringFromDate:[NSDate dateWithTimeIntervalSince1970:time]];
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
        [header.heightAnchor constraintGreaterThanOrEqualToConstant:62],
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

- (UIView *)attachAddButton {
    UIButton *add = [UIButton buttonWithType:UIButtonTypeCustom];
    add.translatesAutoresizingMaskIntoConstraints = NO;
    add.tag = kAttachAddTag;
    add.backgroundColor = UIColor.whiteColor;
    add.layer.cornerRadius = 8;
    add.layer.borderWidth = 1;
    add.layer.borderColor = [UIColor colorWithWhite:0.88 alpha:1].CGColor;
    [add addTarget:self action:@selector(addImages) forControlEvents:UIControlEventTouchUpInside];
    if (@available(iOS 13.0, *)) {
        UIImage *plus = [UIImage systemImageNamed:@"plus"];
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
        thumb.layer.cornerRadius = 8;
        thumb.layer.borderWidth = 1;
        thumb.layer.borderColor = [UIColor colorWithWhite:0.88 alpha:1].CGColor;
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

    UIView *header = [self buildHeaderWithBackAction:@selector(close)
                                               title:@"问题和建议"
                                         rightAction:@selector(showList)
                                          rightTitle:@"反馈记录"];
    UIScrollView *scroll = [[UIScrollView alloc] init];
    scroll.translatesAutoresizingMaskIntoConstraints = NO;
    scroll.alwaysBounceVertical = YES;

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
    for (NSUInteger i = 0; i < types.count; i++) {
        UIButton *chip = [FeedbackAppearance typeChipWithTitle:types[i]
                                                           tag:(NSInteger)i + kChipBaseTag
                                                        target:self
                                                        action:@selector(typeChipTapped:)];
        if (i < 2) {
            [chipRow0 addArrangedSubview:chip];
        } else {
            [chipRow1 addArrangedSubview:chip];
        }
    }
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
    [FeedbackAppearance styleInputContainer:descBox cornerRadius:12];

    self.descView = [[UITextView alloc] init];
    self.descView.translatesAutoresizingMaskIntoConstraints = NO;
    self.descView.font = [UIFont systemFontOfSize:14];
    self.descView.textColor = [FeedbackAppearance textPrimaryColor];
    self.descView.backgroundColor = UIColor.clearColor;
    self.descView.delegate = self;
    self.descView.textContainerInset = UIEdgeInsetsZero;

    self.descCountLabel = [[UILabel alloc] init];
    self.descCountLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.descCountLabel.text = @"0/500";
    self.descCountLabel.font = [UIFont systemFontOfSize:14];
    self.descCountLabel.textColor = [FeedbackAppearance textMutedColor];
    self.descCountLabel.textAlignment = NSTextAlignmentRight;

    [descBox addSubview:self.descView];
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
    self.contactField.layer.cornerRadius = 8;
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
    [mainStack addArrangedSubview:logRow];
    [body addSubview:mainStack];

    UIView *bottomBar = [[UIView alloc] init];
    bottomBar.translatesAutoresizingMaskIntoConstraints = NO;
    bottomBar.backgroundColor = [FeedbackAppearance pageBackgroundColor];
    UIButton *submit = [FeedbackAppearance primaryButtonWithTitle:@"提交"
                                                         target:self
                                                         action:@selector(submitFeedback)];
    [bottomBar addSubview:submit];

    [self.contentView addSubview:header];
    [self.contentView addSubview:scroll];
    [self.contentView addSubview:bottomBar];
    [scroll addSubview:body];

    [NSLayoutConstraint activateConstraints:@[
        [header.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor],
        [header.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [header.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [scroll.topAnchor constraintEqualToAnchor:header.bottomAnchor],
        [scroll.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [scroll.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [scroll.bottomAnchor constraintEqualToAnchor:bottomBar.topAnchor],
        [bottomBar.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [bottomBar.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [bottomBar.bottomAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.bottomAnchor],
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
    if (textView.text.length > 500) {
        textView.text = [textView.text substringToIndex:500];
    }
    self.descCountLabel.text = [NSString stringWithFormat:@"%lu/500", (unsigned long)textView.text.length];
}

#pragma mark - Images

- (void)addImages {
    if (@available(iOS 14.0, *)) {
        if (self.imagePaths.count >= 6) {
            [self toast:@"最多可添加6张图片"];
            return;
        }
        PHPickerConfiguration *config = [[PHPickerConfiguration alloc] init];
        config.selectionLimit = MAX(1, 6 - (NSInteger)self.imagePaths.count);
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
        if (self.imagePaths.count >= 6) {
            dispatch_async(dispatch_get_main_queue(), ^{
                [self toast:@"最多可添加6张图片"];
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
    if (content.length < 10) {
        [self toast:@"请至少输入10个字"];
        return;
    }
    FeedbackRecord *record = [[FeedbackRecord alloc] init];
    record.recordId = [FeedbackStore newFeedbackIdForDate:[NSDate date]];
    record.type = FeedbackTypeLabels()[(NSUInteger)self.selectedTypeIndex];
    record.submitTime = [NSDate date].timeIntervalSince1970;
    record.content = content;
    record.contact = self.contactField.text ?: @"";
    record.shareLog = self.shareLog;
    record.imagePaths = [self.imagePaths copy];
    record.status = FeedbackStatusProcessing;
    [FeedbackStore addRecord:record];
    [self.imagePaths removeAllObjects];
    [self showSuccess];
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

    [NSLayoutConstraint activateConstraints:@[
        [header.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor],
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
        [bottomBar.bottomAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.bottomAnchor],
        [viewRecords.topAnchor constraintEqualToAnchor:bottomBar.topAnchor constant:6],
        [viewRecords.leadingAnchor constraintEqualToAnchor:bottomBar.leadingAnchor constant:40],
        [viewRecords.trailingAnchor constraintEqualToAnchor:bottomBar.trailingAnchor constant:-40],
        [viewRecords.bottomAnchor constraintEqualToAnchor:bottomBar.bottomAnchor constant:-12],
    ]];
}

#pragma mark - List / Empty

- (void)showList {
    self.listRecords = [FeedbackStore loadRecords];
    if (self.listRecords.count == 0) {
        [self showEmpty];
        return;
    }
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

    [self.contentView addSubview:header];
    [self.contentView addSubview:table];
    [NSLayoutConstraint activateConstraints:@[
        [header.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor],
        [header.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [header.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [table.topAnchor constraintEqualToAnchor:header.bottomAnchor constant:8],
        [table.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [table.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [table.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
    ]];
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

    [NSLayoutConstraint activateConstraints:@[
        [header.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor],
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
        [bottomBar.bottomAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.bottomAnchor],
        [backBtn.topAnchor constraintEqualToAnchor:bottomBar.topAnchor constant:6],
        [backBtn.leadingAnchor constraintEqualToAnchor:bottomBar.leadingAnchor constant:106],
        [backBtn.trailingAnchor constraintEqualToAnchor:bottomBar.trailingAnchor constant:-106],
        [backBtn.bottomAnchor constraintEqualToAnchor:bottomBar.bottomAnchor constant:-12],
    ]];
}

#pragma mark - Detail

- (void)showDetail:(NSString *)recordId {
    FeedbackRecord *record = [FeedbackStore findRecordWithId:recordId];
    if (record == nil) {
        [self showList];
        return;
    }
    if ((record.status == FeedbackStatusSubmitted || record.status == FeedbackStatusProcessing)
        && [NSDate date].timeIntervalSince1970 - record.submitTime > 30) {
        [FeedbackStore simulateReplyForRecord:record];
        record = [FeedbackStore findRecordWithId:recordId];
    }
    self.currentDetail = record;
    [self clearContent];

    UIView *header = [[UIView alloc] init];
    header.translatesAutoresizingMaskIntoConstraints = NO;
    header.backgroundColor = [FeedbackAppearance pageBackgroundColor];

    UIButton *back = [AppChromeHelper secondaryBackButtonWithTarget:self action:@selector(showList)];
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
    metaRow.spacing = 4;
    metaRow.alignment = UIStackViewAlignmentCenter;

    UIStackView *titleCol = [[UIStackView alloc] initWithArrangedSubviews:@[ noLabel, metaRow ]];
    titleCol.translatesAutoresizingMaskIntoConstraints = NO;
    titleCol.axis = UILayoutConstraintAxisVertical;
    titleCol.spacing = 2;

    [header addSubview:back];
    [header addSubview:titleCol];

    UIScrollView *scroll = [[UIScrollView alloc] init];
    scroll.translatesAutoresizingMaskIntoConstraints = NO;

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
    myContent.text = record.content;
    myContent.font = [UIFont systemFontOfSize:14];
    myContent.numberOfLines = 0;
    myContent.textColor = [FeedbackAppearance textPrimaryColor];
    [myContentBlock addSubview:myContent];

    UILabel *myTime = [[UILabel alloc] init];
    myTime.translatesAutoresizingMaskIntoConstraints = NO;
    myTime.text = [NSString stringWithFormat:@"%@ 反馈成功", [self formatTime:record.submitTime]];
    myTime.font = [UIFont systemFontOfSize:14];
    myTime.textColor = [FeedbackAppearance textSecondaryColor];

    UIStackView *myInner = [[UIStackView alloc] initWithArrangedSubviews:@[ myType, myContentBlock, myTime ]];
    myInner.translatesAutoresizingMaskIntoConstraints = NO;
    myInner.axis = UILayoutConstraintAxisVertical;
    myInner.spacing = 8;
    [myBubble addSubview:myInner];

    UILabel *meAvatar = [FeedbackAppearance avatarLabelWithText:@"我" accent:YES];

    UIStackView *myRow = [[UIStackView alloc] initWithArrangedSubviews:@[ myBubble, meAvatar ]];
    myRow.translatesAutoresizingMaskIntoConstraints = NO;
    myRow.axis = UILayoutConstraintAxisHorizontal;
    myRow.spacing = 12;
    myRow.alignment = UIStackViewAlignmentTop;

    UIStackView *scrollStack = [[UIStackView alloc] init];
    scrollStack.translatesAutoresizingMaskIntoConstraints = NO;
    scrollStack.axis = UILayoutConstraintAxisVertical;
    scrollStack.spacing = 20;
    [scrollStack addArrangedSubview:myRow];

    if (record.status == FeedbackStatusReplied) {
        UILabel *serviceAvatar = [FeedbackAppearance avatarLabelWithText:@"客" accent:NO];
        UIView *replyBubble = [[UIView alloc] init];
        replyBubble.translatesAutoresizingMaskIntoConstraints = NO;
        [FeedbackAppearance styleInputContainer:replyBubble cornerRadius:12];

        UILabel *replyText = [[UILabel alloc] init];
        replyText.translatesAutoresizingMaskIntoConstraints = NO;
        replyText.text = record.replyText;
        replyText.font = [UIFont systemFontOfSize:16];
        replyText.numberOfLines = 0;
        replyText.textColor = [FeedbackAppearance textPrimaryColor];

        UILabel *replyTime = [[UILabel alloc] init];
        replyTime.translatesAutoresizingMaskIntoConstraints = NO;
        replyTime.text = [NSString stringWithFormat:@"%@ 已回复", [self formatTime:record.replyTime]];
        replyTime.font = [UIFont systemFontOfSize:14];
        replyTime.textColor = [FeedbackAppearance textSecondaryColor];

        UIStackView *replyInner = [[UIStackView alloc] initWithArrangedSubviews:@[ replyText, replyTime ]];
        replyInner.translatesAutoresizingMaskIntoConstraints = NO;
        replyInner.axis = UILayoutConstraintAxisVertical;
        replyInner.spacing = 8;
        [replyBubble addSubview:replyInner];

        UIStackView *replyRow = [[UIStackView alloc] initWithArrangedSubviews:@[ serviceAvatar, replyBubble ]];
        replyRow.axis = UILayoutConstraintAxisHorizontal;
        replyRow.spacing = 10;
        replyRow.alignment = UIStackViewAlignmentTop;
        [scrollStack addArrangedSubview:replyRow];

        [NSLayoutConstraint activateConstraints:@[
            [replyInner.topAnchor constraintEqualToAnchor:replyBubble.topAnchor constant:8],
            [replyInner.leadingAnchor constraintEqualToAnchor:replyBubble.leadingAnchor constant:12],
            [replyInner.trailingAnchor constraintEqualToAnchor:replyBubble.trailingAnchor constant:-12],
            [replyInner.bottomAnchor constraintEqualToAnchor:replyBubble.bottomAnchor constant:-8],
        ]];
    }

    UIView *bottomBar = [[UIView alloc] init];
    bottomBar.translatesAutoresizingMaskIntoConstraints = NO;
    bottomBar.backgroundColor = UIColor.whiteColor;

    BOOL processing = record.status == FeedbackStatusSubmitted || record.status == FeedbackStatusProcessing;
    BOOL replied = record.status == FeedbackStatusReplied;
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
    } else if (replied) {
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

    [scroll addSubview:scrollStack];
    [self.contentView addSubview:header];
    [self.contentView addSubview:scroll];
    [self.contentView addSubview:bottomBar];

    [NSLayoutConstraint activateConstraints:@[
        [header.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor],
        [header.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [header.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [header.heightAnchor constraintGreaterThanOrEqualToConstant:62],
        [back.leadingAnchor constraintEqualToAnchor:header.leadingAnchor constant:15],
        [back.centerYAnchor constraintEqualToAnchor:header.centerYAnchor],
        [titleCol.leadingAnchor constraintEqualToAnchor:back.trailingAnchor constant:4],
        [titleCol.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [scroll.topAnchor constraintEqualToAnchor:header.bottomAnchor],
        [scroll.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [scroll.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [scroll.bottomAnchor constraintEqualToAnchor:bottomBar.topAnchor],
        [bottomBar.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [bottomBar.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [bottomBar.bottomAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.bottomAnchor],
        [scrollStack.topAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.topAnchor constant:8],
        [scrollStack.leadingAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.leadingAnchor constant:16],
        [scrollStack.trailingAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.trailingAnchor constant:-16],
        [scrollStack.bottomAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.bottomAnchor constant:-16],
        [scrollStack.widthAnchor constraintEqualToAnchor:scroll.frameLayoutGuide.widthAnchor constant:-32],
        [myInner.topAnchor constraintEqualToAnchor:myBubble.topAnchor constant:8],
        [myInner.leadingAnchor constraintEqualToAnchor:myBubble.leadingAnchor constant:12],
        [myInner.trailingAnchor constraintEqualToAnchor:myBubble.trailingAnchor constant:-12],
        [myInner.bottomAnchor constraintEqualToAnchor:myBubble.bottomAnchor constant:-8],
        [myContent.topAnchor constraintEqualToAnchor:myContentBlock.topAnchor constant:10],
        [myContent.leadingAnchor constraintEqualToAnchor:myContentBlock.leadingAnchor constant:12],
        [myContent.trailingAnchor constraintEqualToAnchor:myContentBlock.trailingAnchor constant:-12],
        [myContent.bottomAnchor constraintEqualToAnchor:myContentBlock.bottomAnchor constant:-10],
    ]];
}

- (void)closeFeedback {
    __weak __typeof(self) weakSelf = self;
    [HomeCardDialogPresenter presentConfirmFrom:self
                                          title:@"关闭反馈"
                                        message:@"问题已解决是否确认关闭该反馈"
                                   confirmStyle:HomeCardDialogConfirmStylePrimary
                                     completion:^(BOOL confirmed) {
        if (!confirmed) {
            return;
        }
        weakSelf.currentDetail.status = FeedbackStatusClosed;
        [FeedbackStore updateRecord:weakSelf.currentDetail];
        [weakSelf showDetail:weakSelf.currentDetail.recordId];
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

#pragma mark - Toast / Close

- (void)toast:(NSString *)message {
    [AppToastPresenter showMessage:message from:self];
}

- (void)close {
    [self dismissViewControllerAnimated:YES completion:nil];
}

@end
