// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AISettingsDrawerController.h"

#import "AIModelConfigStore.h"
#import "AISettingsDrawerIcons.h"
#import "Settings/AboutViewController.h"
#import "Settings/AppIcons.h"
#import "Settings/ClearCacheViewController.h"
#import "LocalModelStore.h"
#import "LocalModelViewController.h"
#import "Settings/ProfileSettingsViewController.h"

static const CGFloat kDrawerWidth = 320.0;

static UIColor *AIDrawerColorPanelBg(void) {
    return [UIColor colorWithRed:243.0 / 255.0 green:243.0 / 255.0 blue:243.0 / 255.0 alpha:1];
}
static UIColor *AIDrawerColorCardBg(void) {
    return [UIColor colorWithRed:242.0 / 255.0 green:243.0 / 255.0 blue:245.0 / 255.0 alpha:1];
}
static UIColor *AIDrawerColorOrange(void) {
    return [UIColor colorWithRed:250.0 / 255.0 green:98.0 / 255.0 blue:0 alpha:1];
}
static UIColor *AIDrawerColorTextPrimary(void) {
    return [UIColor colorWithRed:16.0 / 255.0 green:16.0 / 255.0 blue:16.0 / 255.0 alpha:1];
}
static UIColor *AIDrawerColorTextSecondary(void) {
    return [UIColor colorWithRed:106.0 / 255.0 green:106.0 / 255.0 blue:106.0 / 255.0 alpha:1];
}

static UIView *AIDrawerDivider(void) {
    UIView *line = [[UIView alloc] init];
    line.translatesAutoresizingMaskIntoConstraints = NO;
    line.backgroundColor = [UIColor colorWithWhite:0 alpha:0.1];
    [line.heightAnchor constraintEqualToConstant:1.0 / UIScreen.mainScreen.scale].active = YES;
    return line;
}

static UIView *AIDrawerHairline(void) {
    UIView *line = [[UIView alloc] init];
    line.translatesAutoresizingMaskIntoConstraints = NO;
    line.backgroundColor = [UIColor colorWithRed:216.0 / 255.0 green:216.0 / 255.0 blue:216.0 / 255.0 alpha:1];
    [line.heightAnchor constraintEqualToConstant:1.0 / UIScreen.mainScreen.scale].active = YES;
    return line;
}
static NSString *const kProfileNameKey = @"USER_PROFILE_NAME";
static NSString *const kAvatarFileName = @"ai_profile_avatar.jpg";

@interface AISettingsDrawerController () <UITextFieldDelegate, UIGestureRecognizerDelegate>
@property (weak, nonatomic) UIViewController *host;
@property (strong, nonatomic, readwrite) UIScreenEdgePanGestureRecognizer *edgePanGesture;
@property (strong, nonatomic) AIModelConfigStore *modelStore;
@property (assign, nonatomic) BOOL open;
@property (assign, nonatomic) BOOL modelsExpanded;
@property (assign, nonatomic) AIModelType editingType;
@property (strong, nonatomic) UIView *dimmingView;
@property (strong, nonatomic) UIView *panelView;
@property (strong, nonatomic) NSLayoutConstraint *panelLeading;
@property (strong, nonatomic) UIView *configPanel;
@property (strong, nonatomic) NSLayoutConstraint *configLeading;
@property (strong, nonatomic) UIImageView *avatarView;
@property (strong, nonatomic) UILabel *nameLabel;
@property (strong, nonatomic) UIView *modelsBody;
@property (strong, nonatomic) NSLayoutConstraint *modelsBodyHeight;
@property (strong, nonatomic) UIImageView *chevronView;
@property (strong, nonatomic) NSMutableDictionary<NSNumber *, UILabel *> *modelValueLabels;
@property (strong, nonatomic) NSMutableDictionary<NSNumber *, UIView *> *modelRowBackgrounds;
@property (strong, nonatomic) UIButton *profileEditButton;
@property (strong, nonatomic) UILabel *localStatusLabel;
@property (strong, nonatomic) UIButton *localRowButton;
@property (strong, nonatomic) UIStackView *configStack;
@property (strong, nonatomic) UIButton *configCancelButton;
@property (strong, nonatomic) UIButton *configSaveFooterButton;
@property (strong, nonatomic) UILabel *configTitleLabel;
@property (strong, nonatomic) UITextField *configNameField;
@property (strong, nonatomic) UITextField *providerField;
@property (strong, nonatomic) UITextField *urlField;
@property (strong, nonatomic) UITextField *apiKeyField;
@property (strong, nonatomic) UITextField *modelNameField;
@property (strong, nonatomic) UISlider *topPSlider;
@property (strong, nonatomic) UISlider *temperatureSlider;
@property (strong, nonatomic) UISlider *presenceSlider;
@property (strong, nonatomic) UISlider *frequencySlider;
@property (strong, nonatomic) UISlider *maxTokensSlider;
@property (strong, nonatomic) UISlider *seedSlider;
@property (strong, nonatomic) UILabel *topPValue;
@property (strong, nonatomic) UILabel *temperatureValue;
@property (strong, nonatomic) UILabel *presenceValue;
@property (strong, nonatomic) UILabel *frequencyValue;
@property (strong, nonatomic) UILabel *maxTokensValue;
@property (strong, nonatomic) UILabel *seedValue;
@end

@implementation AISettingsDrawerController

+ (instancetype)attachToHost:(UIViewController *)host {
    AISettingsDrawerController *drawer = [[AISettingsDrawerController alloc] init];
    drawer.host = host;
    drawer.modelStore = [[AIModelConfigStore alloc] init];
    drawer.modelValueLabels = [NSMutableDictionary dictionary];
    drawer.modelRowBackgrounds = [NSMutableDictionary dictionary];
    [host addChildViewController:drawer];
    drawer.view.frame = host.view.bounds;
    drawer.view.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    [host.view addSubview:drawer.view];
    [drawer didMoveToParentViewController:host];
    UIScreenEdgePanGestureRecognizer *edge = [[UIScreenEdgePanGestureRecognizer alloc]
        initWithTarget:drawer action:@selector(handleEdgePan:)];
    edge.edges = UIRectEdgeLeft;
    edge.delegate = drawer;
    drawer.edgePanGesture = edge;
    [host.view addGestureRecognizer:edge];
    return drawer;
}

- (void)requireFailureOfScrollViewGestures:(UIScrollView *)scrollView {
    if (scrollView == nil || self.edgePanGesture == nil) {
        return;
    }
    [scrollView.panGestureRecognizer requireGestureRecognizerToFail:self.edgePanGesture];
}

- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer
shouldRecognizeSimultaneouslyWithGestureRecognizer:(UIGestureRecognizer *)otherGestureRecognizer {
    return gestureRecognizer == self.edgePanGesture;
}

- (void)loadView {
    UIView *root = [[UIView alloc] init];
    root.backgroundColor = UIColor.clearColor;
    self.view = root;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.userInteractionEnabled = NO;

    self.dimmingView = [[UIView alloc] init];
    self.dimmingView.translatesAutoresizingMaskIntoConstraints = NO;
    self.dimmingView.backgroundColor = [[UIColor blackColor] colorWithAlphaComponent:0.35];
    self.dimmingView.alpha = 0;
    [self.view addSubview:self.dimmingView];
    UITapGestureRecognizer *tap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(closeDrawer)];
    [self.dimmingView addGestureRecognizer:tap];

    CGFloat width = MIN(kDrawerWidth, [UIScreen mainScreen].bounds.size.width * 0.92);
    self.panelView = [[UIView alloc] init];
    self.panelView.translatesAutoresizingMaskIntoConstraints = NO;
    self.panelView.backgroundColor = AIDrawerColorPanelBg();
    [self.view addSubview:self.panelView];

    self.configPanel = [[UIView alloc] init];
    self.configPanel.translatesAutoresizingMaskIntoConstraints = NO;
    self.configPanel.backgroundColor = UIColor.whiteColor;
    [self.view addSubview:self.configPanel];

    self.panelLeading = [self.panelView.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:-width];
    self.configLeading = [self.configPanel.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:-width];

    [NSLayoutConstraint activateConstraints:@[
        [self.dimmingView.topAnchor constraintEqualToAnchor:self.view.topAnchor],
        [self.dimmingView.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
        [self.dimmingView.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.dimmingView.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [self.panelView.topAnchor constraintEqualToAnchor:self.view.topAnchor],
        [self.panelView.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
        [self.panelView.widthAnchor constraintEqualToConstant:width],
        self.panelLeading,
        [self.configPanel.topAnchor constraintEqualToAnchor:self.view.topAnchor],
        [self.configPanel.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
        [self.configPanel.widthAnchor constraintEqualToConstant:width],
        self.configLeading,
    ]];

    [self buildDrawerContent];
    [self buildConfigContent];
    [self reloadProfile];
    [self reloadLocalModelStatus];
    [self reloadModelRows];
    self.modelsExpanded = YES;
    [self applyModelsExpandedStateAnimated:NO];
}

- (UIColor *)accentColor {
    return AIDrawerColorOrange();
}

- (void)buildDrawerContent {
    UILayoutGuide *safe = self.panelView.safeAreaLayoutGuide;

    UIView *profile = [[UIView alloc] init];
    profile.translatesAutoresizingMaskIntoConstraints = NO;
    profile.backgroundColor = UIColor.whiteColor;
    profile.accessibilityIdentifier = @"aiDrawerProfile";
    [self.panelView addSubview:profile];

    self.avatarView = [[UIImageView alloc] init];
    self.avatarView.translatesAutoresizingMaskIntoConstraints = NO;
    self.avatarView.backgroundColor = AIDrawerColorOrange();
    self.avatarView.layer.cornerRadius = 20;
    self.avatarView.clipsToBounds = YES;
    self.avatarView.contentMode = UIViewContentModeScaleAspectFill;
    [profile addSubview:self.avatarView];

    self.nameLabel = [[UILabel alloc] init];
    self.nameLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.nameLabel.font = [UIFont systemFontOfSize:16 weight:UIFontWeightSemibold];
    self.nameLabel.textColor = AIDrawerColorTextPrimary();
    [profile addSubview:self.nameLabel];

    self.profileEditButton = [UIButton buttonWithType:UIButtonTypeCustom];
    self.profileEditButton.translatesAutoresizingMaskIntoConstraints = NO;
    [self.profileEditButton setImage:[AISettingsDrawerIcons iconNamed:@"profile-edit" size:28]
                            forState:UIControlStateNormal];
    self.profileEditButton.accessibilityLabel = @"编辑昵称";
    [self.profileEditButton addTarget:self action:@selector(editProfile) forControlEvents:UIControlEventTouchUpInside];
    [profile addSubview:self.profileEditButton];

    UIView *profileDivider = AIDrawerHairline();
    [profile addSubview:profileDivider];

    UIScrollView *scroll = [[UIScrollView alloc] init];
    scroll.translatesAutoresizingMaskIntoConstraints = NO;
    [self.panelView addSubview:scroll];

    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.layer.cornerRadius = 8;
    card.backgroundColor = AIDrawerColorCardBg();
    [scroll addSubview:card];

    UIButton *header = [UIButton buttonWithType:UIButtonTypeCustom];
    header.translatesAutoresizingMaskIntoConstraints = NO;
    header.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeft;
    [header addTarget:self action:@selector(toggleModels) forControlEvents:UIControlEventTouchUpInside];
    [card addSubview:header];

    UIImageView *headerIcon = [[UIImageView alloc] initWithImage:[AISettingsDrawerIcons iconNamed:@"config-header" size:48]];
    headerIcon.translatesAutoresizingMaskIntoConstraints = NO;
    headerIcon.contentMode = UIViewContentModeScaleAspectFit;
    headerIcon.userInteractionEnabled = NO;
    [header addSubview:headerIcon];

    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"AI 模型配置";
    title.font = [UIFont systemFontOfSize:14 weight:UIFontWeightMedium];
    title.textColor = AIDrawerColorTextPrimary();
    title.userInteractionEnabled = NO;
    [header addSubview:title];

    UILabel *desc = [[UILabel alloc] init];
    desc.translatesAutoresizingMaskIntoConstraints = NO;
    desc.text = @"点击展开以添加或编辑模型";
    desc.font = [UIFont systemFontOfSize:10];
    desc.textColor = AIDrawerColorTextSecondary();
    desc.numberOfLines = 2;
    desc.userInteractionEnabled = NO;
    [header addSubview:desc];

    self.chevronView = [[UIImageView alloc] initWithImage:[AISettingsDrawerIcons iconNamed:@"chevron-up" size:20]];
    self.chevronView.translatesAutoresizingMaskIntoConstraints = NO;
    self.chevronView.contentMode = UIViewContentModeCenter;
    self.chevronView.userInteractionEnabled = NO;
    [header addSubview:self.chevronView];

    UIView *headerDivider = AIDrawerDivider();
    [card addSubview:headerDivider];

    self.modelsBody = [[UIView alloc] init];
    self.modelsBody.translatesAutoresizingMaskIntoConstraints = NO;
    self.modelsBody.clipsToBounds = YES;
    [card addSubview:self.modelsBody];

    UILabel *hint = [[UILabel alloc] init];
    hint.translatesAutoresizingMaskIntoConstraints = NO;
    hint.text = @"请选择要配置的模型";
    hint.font = [UIFont systemFontOfSize:12];
    hint.textColor = AIDrawerColorTextSecondary();
    [self.modelsBody addSubview:hint];

    UIStackView *rows = [[UIStackView alloc] init];
    rows.translatesAutoresizingMaskIntoConstraints = NO;
    rows.axis = UILayoutConstraintAxisVertical;
    rows.spacing = 4;
    [self.modelsBody addSubview:rows];

    NSArray *types = @[ @(AIModelTypeBase), @(AIModelTypeThink), @(AIModelTypeImage), @(AIModelTypeVision) ];
    for (NSNumber *boxed in types) {
        [rows addArrangedSubview:[self modelRowForType:(AIModelType)boxed.integerValue]];
    }

    UILabel *localHint = [[UILabel alloc] init];
    localHint.translatesAutoresizingMaskIntoConstraints = NO;
    localHint.text = @"本地模型";
    localHint.font = [UIFont systemFontOfSize:12];
    localHint.textColor = AIDrawerColorTextSecondary();
    [self.modelsBody addSubview:localHint];

    self.localRowButton = [UIButton buttonWithType:UIButtonTypeCustom];
    self.localRowButton.translatesAutoresizingMaskIntoConstraints = NO;
    self.localRowButton.backgroundColor = UIColor.whiteColor;
    self.localRowButton.layer.cornerRadius = 8;
    self.localRowButton.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeft;
    self.localRowButton.accessibilityIdentifier = @"aiDrawerLocalModel";
    [self.localRowButton addTarget:self action:@selector(openLocalModel) forControlEvents:UIControlEventTouchUpInside];
    [self.modelsBody addSubview:self.localRowButton];

    UIImageView *localIcon = [[UIImageView alloc] initWithImage:[AISettingsDrawerIcons iconNamed:@"model-vision" size:24]];
    localIcon.translatesAutoresizingMaskIntoConstraints = NO;
    localIcon.userInteractionEnabled = NO;
    [self.localRowButton addSubview:localIcon];

    UILabel *localTitle = [[UILabel alloc] init];
    localTitle.translatesAutoresizingMaskIntoConstraints = NO;
    localTitle.text = @"本地推理";
    localTitle.font = [UIFont systemFontOfSize:14];
    localTitle.textColor = AIDrawerColorTextPrimary();
    localTitle.userInteractionEnabled = NO;
    [self.localRowButton addSubview:localTitle];

    self.localStatusLabel = [[UILabel alloc] init];
    self.localStatusLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.localStatusLabel.font = [UIFont systemFontOfSize:12];
    self.localStatusLabel.textColor = AIDrawerColorTextSecondary();
    self.localStatusLabel.userInteractionEnabled = NO;
    [self.localRowButton addSubview:self.localStatusLabel];

    UIView *footer = [[UIView alloc] init];
    footer.translatesAutoresizingMaskIntoConstraints = NO;
    footer.backgroundColor = AIDrawerColorPanelBg();
    [self.panelView addSubview:footer];
    UIButton *cache = [self footerButton:@"清理缓存" action:@selector(clearCache)];
    cache.accessibilityIdentifier = @"aiDrawerClearCache";
    UIButton *about = [self footerButton:@"关于" action:@selector(showAbout)];
    about.accessibilityIdentifier = @"aiDrawerAbout";
    [footer addSubview:cache];
    [footer addSubview:about];

    [NSLayoutConstraint activateConstraints:@[
        [profile.topAnchor constraintEqualToAnchor:safe.topAnchor],
        [profile.leadingAnchor constraintEqualToAnchor:self.panelView.leadingAnchor],
        [profile.trailingAnchor constraintEqualToAnchor:self.panelView.trailingAnchor],
        [self.avatarView.leadingAnchor constraintEqualToAnchor:profile.leadingAnchor constant:20],
        [self.avatarView.topAnchor constraintEqualToAnchor:profile.topAnchor constant:16],
        [self.avatarView.widthAnchor constraintEqualToConstant:40],
        [self.avatarView.heightAnchor constraintEqualToConstant:40],
        [self.nameLabel.leadingAnchor constraintEqualToAnchor:self.avatarView.trailingAnchor constant:8],
        [self.nameLabel.centerYAnchor constraintEqualToAnchor:self.avatarView.centerYAnchor],
        [self.profileEditButton.trailingAnchor constraintEqualToAnchor:profile.trailingAnchor constant:-20],
        [self.profileEditButton.centerYAnchor constraintEqualToAnchor:self.avatarView.centerYAnchor],
        [self.profileEditButton.widthAnchor constraintEqualToConstant:28],
        [self.profileEditButton.heightAnchor constraintEqualToConstant:28],
        [profileDivider.leadingAnchor constraintEqualToAnchor:profile.leadingAnchor],
        [profileDivider.trailingAnchor constraintEqualToAnchor:profile.trailingAnchor],
        [profileDivider.topAnchor constraintEqualToAnchor:self.avatarView.bottomAnchor constant:16],
        [profileDivider.bottomAnchor constraintEqualToAnchor:profile.bottomAnchor],
        [footer.leadingAnchor constraintEqualToAnchor:self.panelView.leadingAnchor],
        [footer.trailingAnchor constraintEqualToAnchor:self.panelView.trailingAnchor],
        [footer.bottomAnchor constraintEqualToAnchor:safe.bottomAnchor],
        [footer.heightAnchor constraintEqualToConstant:96],
        [cache.topAnchor constraintEqualToAnchor:footer.topAnchor constant:8],
        [cache.leadingAnchor constraintEqualToAnchor:footer.leadingAnchor],
        [cache.trailingAnchor constraintEqualToAnchor:footer.trailingAnchor],
        [cache.heightAnchor constraintEqualToConstant:40],
        [about.topAnchor constraintEqualToAnchor:cache.bottomAnchor],
        [about.leadingAnchor constraintEqualToAnchor:footer.leadingAnchor],
        [about.trailingAnchor constraintEqualToAnchor:footer.trailingAnchor],
        [about.heightAnchor constraintEqualToConstant:40],
        [scroll.topAnchor constraintEqualToAnchor:profile.bottomAnchor],
        [scroll.leadingAnchor constraintEqualToAnchor:self.panelView.leadingAnchor],
        [scroll.trailingAnchor constraintEqualToAnchor:self.panelView.trailingAnchor],
        [scroll.bottomAnchor constraintEqualToAnchor:footer.topAnchor],
        [card.topAnchor constraintEqualToAnchor:scroll.topAnchor constant:12],
        [card.leadingAnchor constraintEqualToAnchor:scroll.leadingAnchor constant:20],
        [card.trailingAnchor constraintEqualToAnchor:scroll.trailingAnchor constant:-20],
        [card.bottomAnchor constraintEqualToAnchor:scroll.bottomAnchor constant:-12],
        [card.widthAnchor constraintEqualToAnchor:scroll.widthAnchor constant:-40],
        [header.topAnchor constraintEqualToAnchor:card.topAnchor constant:12],
        [header.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:12],
        [header.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-12],
        [headerIcon.leadingAnchor constraintEqualToAnchor:header.leadingAnchor],
        [headerIcon.topAnchor constraintEqualToAnchor:header.topAnchor],
        [headerIcon.widthAnchor constraintEqualToConstant:48],
        [headerIcon.heightAnchor constraintEqualToConstant:48],
        [title.leadingAnchor constraintEqualToAnchor:headerIcon.trailingAnchor constant:12],
        [title.topAnchor constraintEqualToAnchor:header.topAnchor constant:4],
        [title.trailingAnchor constraintEqualToAnchor:self.chevronView.leadingAnchor constant:-8],
        [desc.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:4],
        [desc.leadingAnchor constraintEqualToAnchor:title.leadingAnchor],
        [desc.trailingAnchor constraintEqualToAnchor:title.trailingAnchor],
        [desc.bottomAnchor constraintEqualToAnchor:header.bottomAnchor constant:-4],
        [self.chevronView.centerYAnchor constraintEqualToAnchor:headerIcon.centerYAnchor],
        [self.chevronView.trailingAnchor constraintEqualToAnchor:header.trailingAnchor],
        [self.chevronView.widthAnchor constraintEqualToConstant:20],
        [self.chevronView.heightAnchor constraintEqualToConstant:20],
        [headerDivider.topAnchor constraintEqualToAnchor:header.bottomAnchor constant:12],
        [headerDivider.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:12],
        [headerDivider.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-12],
        [self.modelsBody.topAnchor constraintEqualToAnchor:headerDivider.bottomAnchor],
        [self.modelsBody.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:12],
        [self.modelsBody.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-12],
        [self.modelsBody.bottomAnchor constraintEqualToAnchor:card.bottomAnchor constant:-12],
        [hint.topAnchor constraintEqualToAnchor:self.modelsBody.topAnchor constant:12],
        [hint.leadingAnchor constraintEqualToAnchor:self.modelsBody.leadingAnchor],
        [hint.trailingAnchor constraintEqualToAnchor:self.modelsBody.trailingAnchor],
        [rows.topAnchor constraintEqualToAnchor:hint.bottomAnchor constant:8],
        [rows.leadingAnchor constraintEqualToAnchor:self.modelsBody.leadingAnchor],
        [rows.trailingAnchor constraintEqualToAnchor:self.modelsBody.trailingAnchor],
        [localHint.topAnchor constraintEqualToAnchor:rows.bottomAnchor constant:12],
        [localHint.leadingAnchor constraintEqualToAnchor:self.modelsBody.leadingAnchor],
        [self.localRowButton.topAnchor constraintEqualToAnchor:localHint.bottomAnchor constant:8],
        [self.localRowButton.leadingAnchor constraintEqualToAnchor:self.modelsBody.leadingAnchor],
        [self.localRowButton.trailingAnchor constraintEqualToAnchor:self.modelsBody.trailingAnchor],
        [self.localRowButton.heightAnchor constraintEqualToConstant:55],
        [self.localRowButton.bottomAnchor constraintEqualToAnchor:self.modelsBody.bottomAnchor],
        [localIcon.leadingAnchor constraintEqualToAnchor:self.localRowButton.leadingAnchor constant:12],
        [localIcon.centerYAnchor constraintEqualToAnchor:self.localRowButton.centerYAnchor],
        [localIcon.widthAnchor constraintEqualToConstant:24],
        [localIcon.heightAnchor constraintEqualToConstant:24],
        [localTitle.leadingAnchor constraintEqualToAnchor:localIcon.trailingAnchor constant:12],
        [localTitle.centerYAnchor constraintEqualToAnchor:self.localRowButton.centerYAnchor],
        [self.localStatusLabel.trailingAnchor constraintEqualToAnchor:self.localRowButton.trailingAnchor constant:-12],
        [self.localStatusLabel.centerYAnchor constraintEqualToAnchor:self.localRowButton.centerYAnchor],
    ]];
    self.modelsBodyHeight = [self.modelsBody.heightAnchor constraintEqualToConstant:0];
    self.modelsBodyHeight.active = NO;
}

- (UIView *)modelRowForType:(AIModelType)type {
    UIButton *row = [UIButton buttonWithType:UIButtonTypeCustom];
    row.tag = type;
    row.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeft;
    [row addTarget:self action:@selector(openModel:) forControlEvents:UIControlEventTouchUpInside];
    row.accessibilityIdentifier = [NSString stringWithFormat:@"aiModelRow-%ld", (long)type];

    UIView *bg = [[UIView alloc] init];
    bg.translatesAutoresizingMaskIntoConstraints = NO;
    bg.userInteractionEnabled = NO;
    bg.layer.cornerRadius = 8;
    bg.backgroundColor = UIColor.whiteColor;
    if (type == AIModelTypeBase) {
        bg.layer.borderWidth = 2;
        bg.layer.borderColor = AIDrawerColorOrange().CGColor;
    }
    [row insertSubview:bg atIndex:0];
    self.modelRowBackgrounds[@(type)] = bg;

    UIImageView *icon = [[UIImageView alloc] initWithImage:
        [AISettingsDrawerIcons iconNamed:[AISettingsDrawerIcons modelIconNameForType:type] size:24]];
    icon.translatesAutoresizingMaskIntoConstraints = NO;
    icon.contentMode = UIViewContentModeScaleAspectFit;
    icon.userInteractionEnabled = NO;
    [row addSubview:icon];

    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = [self.modelStore defaultTitleForModelType:type];
    title.font = [UIFont systemFontOfSize:14];
    title.textColor = [UIColor colorWithRed:51.0 / 255.0 green:51.0 / 255.0 blue:51.0 / 255.0 alpha:1];
    title.userInteractionEnabled = NO;
    [row addSubview:title];

    UILabel *value = [[UILabel alloc] init];
    value.translatesAutoresizingMaskIntoConstraints = NO;
    value.font = [UIFont systemFontOfSize:12];
    value.textColor = AIDrawerColorTextSecondary();
    value.userInteractionEnabled = NO;
    [row addSubview:value];
    self.modelValueLabels[@(type)] = value;

    UIImageView *arrow = [[UIImageView alloc] initWithImage:[AISettingsDrawerIcons iconNamed:@"chevron-right" size:16]];
    arrow.translatesAutoresizingMaskIntoConstraints = NO;
    arrow.userInteractionEnabled = NO;
    [row addSubview:arrow];

    [NSLayoutConstraint activateConstraints:@[
        [row.heightAnchor constraintEqualToConstant:55],
        [bg.topAnchor constraintEqualToAnchor:row.topAnchor],
        [bg.bottomAnchor constraintEqualToAnchor:row.bottomAnchor],
        [bg.leadingAnchor constraintEqualToAnchor:row.leadingAnchor],
        [bg.trailingAnchor constraintEqualToAnchor:row.trailingAnchor],
        [icon.leadingAnchor constraintEqualToAnchor:row.leadingAnchor constant:12],
        [icon.centerYAnchor constraintEqualToAnchor:row.centerYAnchor],
        [icon.widthAnchor constraintEqualToConstant:24],
        [icon.heightAnchor constraintEqualToConstant:24],
        [title.leadingAnchor constraintEqualToAnchor:icon.trailingAnchor constant:12],
        [title.topAnchor constraintEqualToAnchor:row.topAnchor constant:10],
        [value.leadingAnchor constraintEqualToAnchor:title.leadingAnchor],
        [value.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:4],
        [arrow.centerYAnchor constraintEqualToAnchor:row.centerYAnchor],
        [arrow.trailingAnchor constraintEqualToAnchor:row.trailingAnchor constant:-12],
        [arrow.widthAnchor constraintEqualToConstant:16],
        [arrow.heightAnchor constraintEqualToConstant:16],
    ]];
    return row;
}

- (UIButton *)footerButton:(NSString *)title action:(SEL)action {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    [button setTitle:title forState:UIControlStateNormal];
    button.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeft;
    button.contentEdgeInsets = UIEdgeInsetsMake(0, 20, 0, 20);
    button.titleLabel.font = [UIFont systemFontOfSize:16];
    [button setTitleColor:[UIColor colorWithWhite:0.15 alpha:1] forState:UIControlStateNormal];
    [button addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];
    return button;
}

- (UIView *)makeSettingsCard {
    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.backgroundColor = UIColor.whiteColor;
    card.layer.cornerRadius = 8;
    card.layer.borderWidth = 1.0 / UIScreen.mainScreen.scale;
    card.layer.borderColor = [UIColor colorWithRed:203.0 / 255.0 green:209.0 / 255.0 blue:215.0 / 255.0 alpha:1].CGColor;
    return card;
}

- (UILabel *)makeSectionTitle:(NSString *)text icon:(NSString *)iconName {
    UILabel *label = [[UILabel alloc] init];
    label.translatesAutoresizingMaskIntoConstraints = NO;
    label.text = text;
    label.font = [UIFont systemFontOfSize:16 weight:UIFontWeightMedium];
    label.textColor = AIDrawerColorOrange();
    label.tag = 9001;
    return label;
}

- (void)buildConfigContent {
    UILayoutGuide *safe = self.configPanel.safeAreaLayoutGuide;

    UIView *header = [[UIView alloc] init];
    header.translatesAutoresizingMaskIntoConstraints = NO;
    header.backgroundColor = UIColor.whiteColor;
    [self.configPanel addSubview:header];

    UIButton *back = [UIButton buttonWithType:UIButtonTypeCustom];
    back.translatesAutoresizingMaskIntoConstraints = NO;
    [back setImage:[AISettingsDrawerIcons iconNamed:@"config-back" size:24] forState:UIControlStateNormal];
    back.accessibilityIdentifier = @"aiModelConfigBack";
    [back addTarget:self action:@selector(saveAndCloseConfig) forControlEvents:UIControlEventTouchUpInside];
    [header addSubview:back];

    self.configTitleLabel = [[UILabel alloc] init];
    self.configTitleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.configTitleLabel.font = [UIFont systemFontOfSize:20 weight:UIFontWeightSemibold];
    self.configTitleLabel.textColor = AIDrawerColorTextPrimary();
    [header addSubview:self.configTitleLabel];

    UIView *headerDivider = AIDrawerHairline();
    [self.configPanel addSubview:headerDivider];

    UIScrollView *scroll = [[UIScrollView alloc] init];
    scroll.translatesAutoresizingMaskIntoConstraints = NO;
    scroll.backgroundColor = UIColor.whiteColor;
    [self.configPanel addSubview:scroll];

    self.configStack = [[UIStackView alloc] init];
    self.configStack.translatesAutoresizingMaskIntoConstraints = NO;
    self.configStack.axis = UILayoutConstraintAxisVertical;
    self.configStack.spacing = 20;
    [scroll addSubview:self.configStack];

    UIView *connectionCard = [self makeSettingsCard];
    UIStackView *connectionStack = [[UIStackView alloc] init];
    connectionStack.translatesAutoresizingMaskIntoConstraints = NO;
    connectionStack.axis = UILayoutConstraintAxisVertical;
    connectionStack.spacing = 12;
    [connectionCard addSubview:connectionStack];

    UIStackView *connectionTitleRow = [[UIStackView alloc] init];
    connectionTitleRow.axis = UILayoutConstraintAxisHorizontal;
    connectionTitleRow.spacing = 8;
    connectionTitleRow.alignment = UIStackViewAlignmentCenter;
    UIImageView *connectionIcon = [[UIImageView alloc] initWithImage:[AISettingsDrawerIcons iconNamed:@"ai-connection" size:20]];
    [connectionTitleRow addArrangedSubview:connectionIcon];
    [connectionTitleRow addArrangedSubview:[self makeSectionTitle:@"连接配置" icon:@"ai-connection"]];
    [connectionStack addArrangedSubview:connectionTitleRow];

    self.configNameField = [self addField:@"配置名称" placeholder:@"我的 OpenAI 配置" to:connectionStack];
    self.providerField = [self addField:@"供应商" placeholder:@"OpenAI" to:connectionStack];
    self.urlField = [self addField:@"URL" placeholder:@"https://api.openai.com/v1/chat/completions" to:connectionStack];
    self.urlField.keyboardType = UIKeyboardTypeURL;
    self.apiKeyField = [self addField:@"API Key" placeholder:@"sk-..." to:connectionStack];
    self.apiKeyField.secureTextEntry = YES;
    self.modelNameField = [self addField:@"模型名称" placeholder:@"gpt-4o-mini" to:connectionStack];

    UIView *paramsCard = [self makeSettingsCard];
    UIStackView *paramsStack = [[UIStackView alloc] init];
    paramsStack.translatesAutoresizingMaskIntoConstraints = NO;
    paramsStack.axis = UILayoutConstraintAxisVertical;
    paramsStack.spacing = 14;
    [paramsCard addSubview:paramsStack];

    UIStackView *paramsTitleRow = [[UIStackView alloc] init];
    paramsTitleRow.axis = UILayoutConstraintAxisHorizontal;
    paramsTitleRow.spacing = 8;
    paramsTitleRow.alignment = UIStackViewAlignmentCenter;
    UIImageView *paramsIcon = [[UIImageView alloc] initWithImage:[AISettingsDrawerIcons iconNamed:@"ai-params" size:20]];
    [paramsTitleRow addArrangedSubview:paramsIcon];
    [paramsTitleRow addArrangedSubview:[self makeSectionTitle:@"参数配置" icon:@"ai-params"]];
    [paramsStack addArrangedSubview:paramsTitleRow];

    UILabel *topPValue = nil;
    UILabel *temperatureValue = nil;
    UILabel *presenceValue = nil;
    UILabel *frequencyValue = nil;
    UILabel *maxTokensValue = nil;
    UILabel *seedValue = nil;
    self.topPSlider = [self addSlider:@"top_p" valueLabel:&topPValue to:paramsStack];
    self.temperatureSlider = [self addSlider:@"temperature" valueLabel:&temperatureValue to:paramsStack];
    self.presenceSlider = [self addSlider:@"presence_penalty" valueLabel:&presenceValue to:paramsStack];
    self.frequencySlider = [self addSlider:@"frequency_penalty" valueLabel:&frequencyValue to:paramsStack];
    self.maxTokensSlider = [self addSlider:@"max_tokens" valueLabel:&maxTokensValue to:paramsStack];
    self.seedSlider = [self addSlider:@"seed" valueLabel:&seedValue to:paramsStack];
    self.topPValue = topPValue;
    self.temperatureValue = temperatureValue;
    self.presenceValue = presenceValue;
    self.frequencyValue = frequencyValue;
    self.maxTokensValue = maxTokensValue;
    self.seedValue = seedValue;

    [self.configStack addArrangedSubview:connectionCard];
    [self.configStack addArrangedSubview:paramsCard];

    [NSLayoutConstraint activateConstraints:@[
        [connectionStack.topAnchor constraintEqualToAnchor:connectionCard.topAnchor constant:16],
        [connectionStack.leadingAnchor constraintEqualToAnchor:connectionCard.leadingAnchor constant:16],
        [connectionStack.trailingAnchor constraintEqualToAnchor:connectionCard.trailingAnchor constant:-16],
        [connectionStack.bottomAnchor constraintEqualToAnchor:connectionCard.bottomAnchor constant:-16],
        [paramsStack.topAnchor constraintEqualToAnchor:paramsCard.topAnchor constant:16],
        [paramsStack.leadingAnchor constraintEqualToAnchor:paramsCard.leadingAnchor constant:16],
        [paramsStack.trailingAnchor constraintEqualToAnchor:paramsCard.trailingAnchor constant:-16],
        [paramsStack.bottomAnchor constraintEqualToAnchor:paramsCard.bottomAnchor constant:-16],
    ]];

    UIView *footerDivider = AIDrawerHairline();
    [self.configPanel addSubview:footerDivider];

    UIView *footer = [[UIView alloc] init];
    footer.translatesAutoresizingMaskIntoConstraints = NO;
    footer.backgroundColor = UIColor.whiteColor;
    [self.configPanel addSubview:footer];

    self.configCancelButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.configCancelButton.translatesAutoresizingMaskIntoConstraints = NO;
    [self.configCancelButton setTitle:@"取消" forState:UIControlStateNormal];
    [self.configCancelButton setTitleColor:[UIColor colorWithWhite:0 alpha:0.9] forState:UIControlStateNormal];
    self.configCancelButton.backgroundColor = UIColor.whiteColor;
    self.configCancelButton.layer.cornerRadius = 17.5;
    self.configCancelButton.layer.borderWidth = 1;
    self.configCancelButton.layer.borderColor = [UIColor colorWithWhite:0.85 alpha:1].CGColor;
    [self.configCancelButton addTarget:self action:@selector(cancelConfig) forControlEvents:UIControlEventTouchUpInside];
    [footer addSubview:self.configCancelButton];

    self.configSaveFooterButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.configSaveFooterButton.translatesAutoresizingMaskIntoConstraints = NO;
    [self.configSaveFooterButton setTitle:@"保存" forState:UIControlStateNormal];
    [self.configSaveFooterButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    self.configSaveFooterButton.backgroundColor = AIDrawerColorOrange();
    self.configSaveFooterButton.layer.cornerRadius = 17.5;
    self.configSaveFooterButton.accessibilityIdentifier = @"aiModelConfigSave";
    [self.configSaveFooterButton addTarget:self action:@selector(saveAndCloseConfig) forControlEvents:UIControlEventTouchUpInside];
    [footer addSubview:self.configSaveFooterButton];

    [NSLayoutConstraint activateConstraints:@[
        [header.topAnchor constraintEqualToAnchor:safe.topAnchor],
        [header.leadingAnchor constraintEqualToAnchor:self.configPanel.leadingAnchor],
        [header.trailingAnchor constraintEqualToAnchor:self.configPanel.trailingAnchor],
        [header.heightAnchor constraintEqualToConstant:56],
        [back.leadingAnchor constraintEqualToAnchor:header.leadingAnchor constant:8],
        [back.centerYAnchor constraintEqualToAnchor:header.centerYAnchor],
        [back.widthAnchor constraintEqualToConstant:40],
        [back.heightAnchor constraintEqualToConstant:40],
        [self.configTitleLabel.leadingAnchor constraintEqualToAnchor:back.trailingAnchor constant:8],
        [self.configTitleLabel.centerYAnchor constraintEqualToAnchor:header.centerYAnchor],
        [headerDivider.topAnchor constraintEqualToAnchor:header.bottomAnchor],
        [headerDivider.leadingAnchor constraintEqualToAnchor:self.configPanel.leadingAnchor],
        [headerDivider.trailingAnchor constraintEqualToAnchor:self.configPanel.trailingAnchor],
        [scroll.topAnchor constraintEqualToAnchor:headerDivider.bottomAnchor],
        [scroll.leadingAnchor constraintEqualToAnchor:self.configPanel.leadingAnchor],
        [scroll.trailingAnchor constraintEqualToAnchor:self.configPanel.trailingAnchor],
        [scroll.bottomAnchor constraintEqualToAnchor:footerDivider.topAnchor],
        [self.configStack.topAnchor constraintEqualToAnchor:scroll.topAnchor constant:16],
        [self.configStack.leadingAnchor constraintEqualToAnchor:scroll.leadingAnchor constant:16],
        [self.configStack.trailingAnchor constraintEqualToAnchor:scroll.trailingAnchor constant:-16],
        [self.configStack.bottomAnchor constraintEqualToAnchor:scroll.bottomAnchor constant:-16],
        [self.configStack.widthAnchor constraintEqualToAnchor:scroll.widthAnchor constant:-32],
        [footerDivider.leadingAnchor constraintEqualToAnchor:self.configPanel.leadingAnchor],
        [footerDivider.trailingAnchor constraintEqualToAnchor:self.configPanel.trailingAnchor],
        [footerDivider.bottomAnchor constraintEqualToAnchor:footer.topAnchor],
        [footer.leadingAnchor constraintEqualToAnchor:self.configPanel.leadingAnchor],
        [footer.trailingAnchor constraintEqualToAnchor:self.configPanel.trailingAnchor],
        [footer.bottomAnchor constraintEqualToAnchor:self.configPanel.bottomAnchor],
        [footer.heightAnchor constraintEqualToConstant:72],
        [self.configCancelButton.leadingAnchor constraintEqualToAnchor:footer.leadingAnchor constant:20],
        [self.configCancelButton.centerYAnchor constraintEqualToAnchor:footer.centerYAnchor constant:-8],
        [self.configCancelButton.heightAnchor constraintEqualToConstant:35],
        [self.configSaveFooterButton.trailingAnchor constraintEqualToAnchor:footer.trailingAnchor constant:-20],
        [self.configSaveFooterButton.centerYAnchor constraintEqualToAnchor:footer.centerYAnchor constant:-8],
        [self.configSaveFooterButton.heightAnchor constraintEqualToConstant:35],
        [self.configSaveFooterButton.leadingAnchor constraintEqualToAnchor:self.configCancelButton.trailingAnchor constant:20],
        [self.configCancelButton.widthAnchor constraintEqualToAnchor:self.configSaveFooterButton.widthAnchor],
    ]];
}

- (void)cancelConfig {
    [self hideConfigAnimated:YES];
}

- (UITextField *)addField:(NSString *)labelText placeholder:(NSString *)placeholder to:(UIStackView *)stack {
    UILabel *label = [[UILabel alloc] init];
    label.text = labelText;
    label.font = [UIFont systemFontOfSize:13];
    label.textColor = AIDrawerColorTextPrimary();
    [stack addArrangedSubview:label];
    UITextField *field = [[UITextField alloc] init];
    field.placeholder = placeholder;
    field.font = [UIFont systemFontOfSize:14];
    field.textColor = AIDrawerColorTextSecondary();
    field.delegate = self;
    field.translatesAutoresizingMaskIntoConstraints = NO;
    field.backgroundColor = AIDrawerColorCardBg();
    field.layer.cornerRadius = 8;
    field.leftView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, 12, 1)];
    field.leftViewMode = UITextFieldViewModeAlways;
    field.rightView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, 12, 1)];
    field.rightViewMode = UITextFieldViewModeAlways;
    [field.heightAnchor constraintEqualToConstant:40].active = YES;
    [stack addArrangedSubview:field];
    return field;
}

- (UISlider *)addSlider:(NSString *)title valueLabel:(UILabel **)valueLabel to:(UIStackView *)stack {
    UIView *row = [[UIView alloc] init];
    row.translatesAutoresizingMaskIntoConstraints = NO;
    UILabel *name = [[UILabel alloc] init];
    name.translatesAutoresizingMaskIntoConstraints = NO;
    name.text = title;
    name.font = [UIFont systemFontOfSize:13];
    name.textColor = AIDrawerColorTextPrimary();
    [row addSubview:name];
    UILabel *value = [[UILabel alloc] init];
    value.translatesAutoresizingMaskIntoConstraints = NO;
    value.font = [UIFont monospacedDigitSystemFontOfSize:12 weight:UIFontWeightRegular];
    value.textAlignment = NSTextAlignmentRight;
    [row addSubview:value];
    *valueLabel = value;
    UISlider *slider = [[UISlider alloc] init];
    slider.translatesAutoresizingMaskIntoConstraints = NO;
    slider.minimumValue = 0;
    slider.maximumValue = 1;
    slider.tintColor = [self accentColor];
    [slider addTarget:self action:@selector(sliderChanged:) forControlEvents:UIControlEventValueChanged];
    [row addSubview:slider];
    [NSLayoutConstraint activateConstraints:@[
        [row.heightAnchor constraintEqualToConstant:48],
        [name.leadingAnchor constraintEqualToAnchor:row.leadingAnchor],
        [name.topAnchor constraintEqualToAnchor:row.topAnchor],
        [value.trailingAnchor constraintEqualToAnchor:row.trailingAnchor],
        [value.centerYAnchor constraintEqualToAnchor:name.centerYAnchor],
        [slider.leadingAnchor constraintEqualToAnchor:row.leadingAnchor],
        [slider.trailingAnchor constraintEqualToAnchor:row.trailingAnchor],
        [slider.bottomAnchor constraintEqualToAnchor:row.bottomAnchor],
    ]];
    [stack addArrangedSubview:row];
    return slider;
}

- (void)sliderChanged:(UISlider *)slider {
    NSString *text = [NSString stringWithFormat:@"%.2f", slider.value];
    if (slider == self.topPSlider) self.topPValue.text = text;
    else if (slider == self.temperatureSlider) self.temperatureValue.text = text;
    else if (slider == self.presenceSlider) self.presenceValue.text = text;
    else if (slider == self.frequencySlider) self.frequencyValue.text = text;
    else if (slider == self.maxTokensSlider) self.maxTokensValue.text = text;
    else if (slider == self.seedSlider) self.seedValue.text = text;
}

- (void)handleEdgePan:(UIScreenEdgePanGestureRecognizer *)gesture {
    CGFloat width = self.panelView.bounds.size.width;
    CGFloat x = [gesture translationInView:self.host.view].x;
    if (gesture.state == UIGestureRecognizerStateBegan) {
        self.view.userInteractionEnabled = YES;
        [self.host.view bringSubviewToFront:self.view];
    } else if (gesture.state == UIGestureRecognizerStateChanged) {
        CGFloat leading = MIN(0, -width + x);
        self.panelLeading.constant = leading;
        self.dimmingView.alpha = MIN(1.0, MAX(0.0, (width + leading) / width));
    } else if (gesture.state == UIGestureRecognizerStateEnded || gesture.state == UIGestureRecognizerStateCancelled) {
        CGFloat vx = [gesture velocityInView:self.host.view].x;
        if (self.panelLeading.constant > -width * 0.5 || vx > 400) {
            [self openDrawer];
        } else {
            [self closeDrawer];
        }
    }
}

- (void)openDrawer {
    [self.host.view bringSubviewToFront:self.view];
    self.view.userInteractionEnabled = YES;
    self.open = YES;
    [self reloadProfile];
    [self reloadLocalModelStatus];
    [self reloadModelRows];
    [self.view layoutIfNeeded];
    [UIView animateWithDuration:0.25 animations:^{
        self.panelLeading.constant = 0;
        self.dimmingView.alpha = 1;
        [self.view layoutIfNeeded];
    }];
}

- (void)closeDrawer {
    [self hideConfigAnimated:NO];
    CGFloat width = self.panelView.bounds.size.width;
    [UIView animateWithDuration:0.25 animations:^{
        self.panelLeading.constant = -width;
        self.dimmingView.alpha = 0;
        [self.view layoutIfNeeded];
    } completion:^(BOOL finished) {
        self.open = NO;
        self.view.userInteractionEnabled = NO;
    }];
}

- (void)toggleModels {
    self.modelsExpanded = !self.modelsExpanded;
    [self applyModelsExpandedStateAnimated:YES];
}

- (void)applyModelsExpandedStateAnimated:(BOOL)animated {
    self.modelsBodyHeight.active = !self.modelsExpanded;
    self.chevronView.image = [AISettingsDrawerIcons iconNamed:self.modelsExpanded ? @"chevron-up" : @"chevron-down"
                                                         size:20];
    void (^work)(void) = ^{
        [self.view layoutIfNeeded];
    };
    if (animated) {
        [UIView animateWithDuration:0.2 animations:work];
    } else {
        work();
    }
}

- (void)openModel:(UIButton *)sender {
    [self showConfigForType:(AIModelType)sender.tag];
}

- (void)showConfigForType:(AIModelType)type {
    self.editingType = type;
    AIModelConfigForm *form = [self.modelStore loadForm:type];
    self.configTitleLabel.text = [self.modelStore defaultTitleForModelType:type];
    self.configNameField.text = form.configName;
    self.providerField.text = form.provider;
    self.urlField.text = form.url;
    self.apiKeyField.text = form.apiKey;
    self.modelNameField.text = form.modelName;
    self.topPSlider.value = form.topP;
    self.temperatureSlider.value = form.temperature;
    self.presenceSlider.value = form.presencePenalty;
    self.frequencySlider.value = form.frequencyPenalty;
    self.maxTokensSlider.value = form.maxTokensRatio;
    self.seedSlider.value = form.seedRatio;
    [self sliderChanged:self.topPSlider];
    [self sliderChanged:self.temperatureSlider];
    [self sliderChanged:self.presenceSlider];
    [self sliderChanged:self.frequencySlider];
    [self sliderChanged:self.maxTokensSlider];
    [self sliderChanged:self.seedSlider];

    CGFloat width = self.panelView.bounds.size.width;
    self.configLeading.constant = -width;
    [self.view layoutIfNeeded];
    [UIView animateWithDuration:0.25 animations:^{
        self.configLeading.constant = 0;
        [self.view layoutIfNeeded];
    }];
}

- (void)hideConfigAnimated:(BOOL)animated {
    CGFloat width = self.panelView.bounds.size.width;
    void (^work)(void) = ^{
        self.configLeading.constant = -width;
        [self.view layoutIfNeeded];
    };
    if (animated) {
        [UIView animateWithDuration:0.25 animations:work];
    } else {
        work();
    }
}

- (void)saveAndCloseConfig {
    AIModelConfigForm *form = [[AIModelConfigForm alloc] init];
    form.configName = self.configNameField.text;
    form.provider = self.providerField.text;
    form.url = self.urlField.text;
    form.apiKey = self.apiKeyField.text;
    form.modelName = self.modelNameField.text;
    form.topP = self.topPSlider.value;
    form.temperature = self.temperatureSlider.value;
    form.presencePenalty = self.presenceSlider.value;
    form.frequencyPenalty = self.frequencySlider.value;
    form.maxTokensRatio = self.maxTokensSlider.value;
    form.seedRatio = self.seedSlider.value;
    NSError *error = nil;
    if (![self.modelStore saveForm:form modelType:self.editingType error:&error]) {
        UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"保存失败"
                                                                       message:error.localizedDescription ?: @"请重试"
                                                                preferredStyle:UIAlertControllerStyleAlert];
        [alert addAction:[UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:nil]];
        [self presentViewController:alert animated:YES completion:nil];
        return;
    }
    [self reloadModelRows];
    [self hideConfigAnimated:YES];
}

- (void)reloadModelRows {
    for (NSNumber *key in self.modelValueLabels) {
        self.modelValueLabels[key].text = [self.modelStore displayNameForModelType:(AIModelType)key.integerValue];
    }
}

- (NSURL *)avatarURL {
    NSString *path = [[NSUserDefaults standardUserDefaults] stringForKey:@"USER_PROFILE_AVATAR_PATH"];
    if (path.length > 0) {
        return [NSURL fileURLWithPath:path];
    }
    NSURL *support = [[[NSFileManager defaultManager] URLsForDirectory:NSApplicationSupportDirectory inDomains:NSUserDomainMask] lastObject];
    [[NSFileManager defaultManager] createDirectoryAtURL:support withIntermediateDirectories:YES attributes:nil error:nil];
    return [support URLByAppendingPathComponent:kAvatarFileName];
}

- (void)reloadProfile {
    NSString *name = [[NSUserDefaults standardUserDefaults] stringForKey:kProfileNameKey];
    self.nameLabel.text = name.length > 0 ? name : @"orangepi";
    UIImage *image = [UIImage imageWithContentsOfFile:[self avatarURL].path];
    if (image == nil) {
        image = [AppIcons iconNamed:@"avatar"];
    }
    self.avatarView.image = image;
}

- (void)reloadLocalModelStatus {
    self.localStatusLabel.text = [[LocalModelStore shared] drawerStatusText];
    BOOL supported = [LocalModelStore isDeviceSupported];
    self.localRowButton.alpha = supported ? 1.0 : 0.72;
    self.localRowButton.enabled = supported;
}

- (void)openLocalModel {
    LocalModelViewController *page = [[LocalModelViewController alloc] init];
    __weak __typeof(self) weakSelf = self;
    page.onDismiss = ^{
        [weakSelf reloadLocalModelStatus];
    };
    [self presentViewController:page animated:YES completion:nil];
}

- (void)editProfile {
    ProfileSettingsViewController *page = [[ProfileSettingsViewController alloc] init];
    page.modalPresentationStyle = UIModalPresentationFullScreen;
    __weak __typeof(self) weakSelf = self;
    page.onProfileChanged = ^{
        [weakSelf reloadProfile];
    };
    [self presentViewController:page animated:YES completion:nil];
}

- (void)clearCache {
    ClearCacheViewController *page = [[ClearCacheViewController alloc] init];
    page.modalPresentationStyle = UIModalPresentationFullScreen;
    [self presentViewController:page animated:YES completion:nil];
}

- (void)showAbout {
    AboutViewController *page = [[AboutViewController alloc] init];
    page.modalPresentationStyle = UIModalPresentationFullScreen;
    [self presentViewController:page animated:YES completion:nil];
}

- (BOOL)textFieldShouldReturn:(UITextField *)textField {
    [textField resignFirstResponder];
    return YES;
}

@end
