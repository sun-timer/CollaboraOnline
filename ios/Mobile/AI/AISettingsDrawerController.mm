// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AISettingsDrawerController.h"

#import "AIModelConfigStore.h"

#import <PhotosUI/PhotosUI.h>

static const CGFloat kDrawerWidth = 356.0;
static NSString *const kProfileNameKey = @"AI_PROFILE_NAME";
static NSString *const kAvatarFileName = @"ai_profile_avatar.jpg";

@interface AISettingsDrawerController () <PHPickerViewControllerDelegate, UITextFieldDelegate, UIGestureRecognizerDelegate>
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
@property (strong, nonatomic) NSLayoutConstraint *modelsBodyBottom;
@property (strong, nonatomic) UILabel *chevronLabel;
@property (strong, nonatomic) NSMutableDictionary<NSNumber *, UILabel *> *modelValueLabels;
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
    self.panelView.backgroundColor = UIColor.whiteColor;
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
    [self reloadModelRows];
}

- (UIColor *)accentColor {
    return [UIColor colorWithRed:250.0 / 255.0 green:98.0 / 255.0 blue:0 alpha:1];
}

- (void)buildDrawerContent {
    UILayoutGuide *safe = self.panelView.safeAreaLayoutGuide;

    UIView *profile = [[UIView alloc] init];
    profile.translatesAutoresizingMaskIntoConstraints = NO;
    profile.accessibilityIdentifier = @"aiDrawerProfile";
    [self.panelView addSubview:profile];
    UITapGestureRecognizer *editProfile = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(editProfile)];
    [profile addGestureRecognizer:editProfile];

    self.avatarView = [[UIImageView alloc] init];
    self.avatarView.translatesAutoresizingMaskIntoConstraints = NO;
    self.avatarView.backgroundColor = [self accentColor];
    self.avatarView.layer.cornerRadius = 20;
    self.avatarView.clipsToBounds = YES;
    self.avatarView.contentMode = UIViewContentModeScaleAspectFill;
    [profile addSubview:self.avatarView];

    self.nameLabel = [[UILabel alloc] init];
    self.nameLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.nameLabel.font = [UIFont systemFontOfSize:16 weight:UIFontWeightSemibold];
    self.nameLabel.textColor = [UIColor colorWithWhite:0.08 alpha:1];
    [profile addSubview:self.nameLabel];

    UIScrollView *scroll = [[UIScrollView alloc] init];
    scroll.translatesAutoresizingMaskIntoConstraints = NO;
    [self.panelView addSubview:scroll];

    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.layer.cornerRadius = 12;
    card.backgroundColor = [UIColor colorWithWhite:0.97 alpha:1];
    [scroll addSubview:card];

    UIButton *header = [UIButton buttonWithType:UIButtonTypeSystem];
    header.translatesAutoresizingMaskIntoConstraints = NO;
    header.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeft;
    [header addTarget:self action:@selector(toggleModels) forControlEvents:UIControlEventTouchUpInside];
    [card addSubview:header];

    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"AI 模型配置";
    title.font = [UIFont systemFontOfSize:16 weight:UIFontWeightMedium];
    title.textColor = [UIColor colorWithWhite:0.06 alpha:1];
    title.userInteractionEnabled = NO;
    [header addSubview:title];

    UILabel *desc = [[UILabel alloc] init];
    desc.translatesAutoresizingMaskIntoConstraints = NO;
    desc.text = @"点击展开以添加或编辑模型";
    desc.font = [UIFont systemFontOfSize:12];
    desc.textColor = [UIColor colorWithWhite:0.42 alpha:1];
    desc.numberOfLines = 2;
    desc.userInteractionEnabled = NO;
    [header addSubview:desc];

    self.chevronLabel = [[UILabel alloc] init];
    self.chevronLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.chevronLabel.text = @"▾";
    self.chevronLabel.font = [UIFont systemFontOfSize:20];
    self.chevronLabel.textColor = [UIColor colorWithWhite:0.4 alpha:1];
    [header addSubview:self.chevronLabel];

    self.modelsBody = [[UIView alloc] init];
    self.modelsBody.translatesAutoresizingMaskIntoConstraints = NO;
    self.modelsBody.clipsToBounds = YES;
    [card addSubview:self.modelsBody];

    UIStackView *rows = [[UIStackView alloc] init];
    rows.translatesAutoresizingMaskIntoConstraints = NO;
    rows.axis = UILayoutConstraintAxisVertical;
    [self.modelsBody addSubview:rows];

    NSArray *types = @[ @(AIModelTypeBase), @(AIModelTypeThink), @(AIModelTypeImage), @(AIModelTypeVision) ];
    for (NSNumber *boxed in types) {
        [rows addArrangedSubview:[self modelRowForType:(AIModelType)boxed.integerValue]];
    }

    UIView *local = [[UIView alloc] init];
    local.translatesAutoresizingMaskIntoConstraints = NO;
    local.alpha = 0.45;
    [rows addArrangedSubview:local];
    UILabel *localTitle = [[UILabel alloc] init];
    localTitle.translatesAutoresizingMaskIntoConstraints = NO;
    localTitle.text = @"本地模型";
    localTitle.font = [UIFont systemFontOfSize:15];
    [local addSubview:localTitle];
    UILabel *localStatus = [[UILabel alloc] init];
    localStatus.translatesAutoresizingMaskIntoConstraints = NO;
    localStatus.text = @"后续阶段接入";
    localStatus.font = [UIFont systemFontOfSize:12];
    localStatus.textColor = [UIColor colorWithWhite:0.45 alpha:1];
    [local addSubview:localStatus];
    local.userInteractionEnabled = NO;
    [NSLayoutConstraint activateConstraints:@[
        [local.heightAnchor constraintEqualToConstant:56],
        [localTitle.leadingAnchor constraintEqualToAnchor:local.leadingAnchor constant:4],
        [localTitle.topAnchor constraintEqualToAnchor:local.topAnchor constant:8],
        [localStatus.leadingAnchor constraintEqualToAnchor:localTitle.leadingAnchor],
        [localStatus.topAnchor constraintEqualToAnchor:localTitle.bottomAnchor constant:2],
    ]];

    UIView *footer = [[UIView alloc] init];
    footer.translatesAutoresizingMaskIntoConstraints = NO;
    [self.panelView addSubview:footer];
    UIButton *cache = [self footerButton:@"清理缓存" action:@selector(clearCache)];
    cache.accessibilityIdentifier = @"aiDrawerClearCache";
    UIButton *about = [self footerButton:@"关于" action:@selector(showAbout)];
    about.accessibilityIdentifier = @"aiDrawerAbout";
    [footer addSubview:cache];
    [footer addSubview:about];

    [NSLayoutConstraint activateConstraints:@[
        [profile.topAnchor constraintEqualToAnchor:safe.topAnchor constant:12],
        [profile.leadingAnchor constraintEqualToAnchor:self.panelView.leadingAnchor],
        [profile.trailingAnchor constraintEqualToAnchor:self.panelView.trailingAnchor],
        [profile.heightAnchor constraintEqualToConstant:64],
        [self.avatarView.leadingAnchor constraintEqualToAnchor:profile.leadingAnchor constant:16],
        [self.avatarView.centerYAnchor constraintEqualToAnchor:profile.centerYAnchor],
        [self.avatarView.widthAnchor constraintEqualToConstant:40],
        [self.avatarView.heightAnchor constraintEqualToConstant:40],
        [self.nameLabel.leadingAnchor constraintEqualToAnchor:self.avatarView.trailingAnchor constant:12],
        [self.nameLabel.centerYAnchor constraintEqualToAnchor:profile.centerYAnchor],
        [self.nameLabel.trailingAnchor constraintEqualToAnchor:profile.trailingAnchor constant:-16],
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
        [card.leadingAnchor constraintEqualToAnchor:scroll.leadingAnchor constant:12],
        [card.trailingAnchor constraintEqualToAnchor:scroll.trailingAnchor constant:-12],
        [card.bottomAnchor constraintEqualToAnchor:scroll.bottomAnchor constant:-12],
        [card.widthAnchor constraintEqualToAnchor:scroll.widthAnchor constant:-24],
        [header.topAnchor constraintEqualToAnchor:card.topAnchor constant:12],
        [header.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:12],
        [header.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-12],
        [title.topAnchor constraintEqualToAnchor:header.topAnchor],
        [title.leadingAnchor constraintEqualToAnchor:header.leadingAnchor],
        [title.trailingAnchor constraintEqualToAnchor:self.chevronLabel.leadingAnchor constant:-8],
        [desc.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:4],
        [desc.leadingAnchor constraintEqualToAnchor:title.leadingAnchor],
        [desc.trailingAnchor constraintEqualToAnchor:title.trailingAnchor],
        [desc.bottomAnchor constraintEqualToAnchor:header.bottomAnchor],
        [self.chevronLabel.centerYAnchor constraintEqualToAnchor:header.centerYAnchor],
        [self.chevronLabel.trailingAnchor constraintEqualToAnchor:header.trailingAnchor],
        [self.modelsBody.topAnchor constraintEqualToAnchor:header.bottomAnchor constant:8],
        [self.modelsBody.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:8],
        [self.modelsBody.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-8],
        [self.modelsBody.bottomAnchor constraintEqualToAnchor:card.bottomAnchor constant:-8],
        [rows.topAnchor constraintEqualToAnchor:self.modelsBody.topAnchor],
        [rows.leadingAnchor constraintEqualToAnchor:self.modelsBody.leadingAnchor],
        [rows.trailingAnchor constraintEqualToAnchor:self.modelsBody.trailingAnchor],
    ]];
    self.modelsBodyHeight = [self.modelsBody.heightAnchor constraintEqualToConstant:0];
    self.modelsBodyBottom = [self.modelsBody.bottomAnchor constraintEqualToAnchor:rows.bottomAnchor];
    self.modelsBodyHeight.active = YES;
    self.modelsBodyBottom.active = NO;
}

- (UIView *)modelRowForType:(AIModelType)type {
    UIButton *row = [UIButton buttonWithType:UIButtonTypeSystem];
    row.tag = type;
    row.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeft;
    [row addTarget:self action:@selector(openModel:) forControlEvents:UIControlEventTouchUpInside];
    row.accessibilityIdentifier = [NSString stringWithFormat:@"aiModelRow-%ld", (long)type];

    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = [self.modelStore defaultTitleForModelType:type];
    title.font = [UIFont systemFontOfSize:15];
    title.textColor = [UIColor colorWithWhite:0.1 alpha:1];
    title.userInteractionEnabled = NO;
    [row addSubview:title];

    UILabel *value = [[UILabel alloc] init];
    value.translatesAutoresizingMaskIntoConstraints = NO;
    value.font = [UIFont systemFontOfSize:12];
    value.textColor = [UIColor colorWithWhite:0.45 alpha:1];
    value.userInteractionEnabled = NO;
    [row addSubview:value];
    self.modelValueLabels[@(type)] = value;

    UILabel *arrow = [[UILabel alloc] init];
    arrow.translatesAutoresizingMaskIntoConstraints = NO;
    arrow.text = @">";
    arrow.textColor = [UIColor colorWithWhite:0.55 alpha:1];
    arrow.userInteractionEnabled = NO;
    [row addSubview:arrow];

    [NSLayoutConstraint activateConstraints:@[
        [row.heightAnchor constraintEqualToConstant:52],
        [title.leadingAnchor constraintEqualToAnchor:row.leadingAnchor constant:4],
        [title.topAnchor constraintEqualToAnchor:row.topAnchor constant:8],
        [value.leadingAnchor constraintEqualToAnchor:title.leadingAnchor],
        [value.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:2],
        [arrow.centerYAnchor constraintEqualToAnchor:row.centerYAnchor],
        [arrow.trailingAnchor constraintEqualToAnchor:row.trailingAnchor constant:-4],
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

- (void)buildConfigContent {
    UILayoutGuide *safe = self.configPanel.safeAreaLayoutGuide;
    UIButton *back = [UIButton buttonWithType:UIButtonTypeSystem];
    back.translatesAutoresizingMaskIntoConstraints = NO;
    [back setTitle:@"‹ 保存并返回" forState:UIControlStateNormal];
    back.accessibilityIdentifier = @"aiModelConfigBack";
    [back addTarget:self action:@selector(saveAndCloseConfig) forControlEvents:UIControlEventTouchUpInside];
    [self.configPanel addSubview:back];

    self.configTitleLabel = [[UILabel alloc] init];
    self.configTitleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.configTitleLabel.font = [UIFont systemFontOfSize:18 weight:UIFontWeightSemibold];
    [self.configPanel addSubview:self.configTitleLabel];

    UIScrollView *scroll = [[UIScrollView alloc] init];
    scroll.translatesAutoresizingMaskIntoConstraints = NO;
    [self.configPanel addSubview:scroll];
    UIStackView *stack = [[UIStackView alloc] init];
    stack.translatesAutoresizingMaskIntoConstraints = NO;
    stack.axis = UILayoutConstraintAxisVertical;
    stack.spacing = 10;
    [scroll addSubview:stack];

    self.configNameField = [self addField:@"配置名称" to:stack];
    self.providerField = [self addField:@"供应商" to:stack];
    self.urlField = [self addField:@"URL" to:stack];
    self.urlField.keyboardType = UIKeyboardTypeURL;
    self.apiKeyField = [self addField:@"API Key" to:stack];
    self.apiKeyField.secureTextEntry = YES;
    self.modelNameField = [self addField:@"模型名称" to:stack];

    UILabel *topPValue = nil;
    UILabel *temperatureValue = nil;
    UILabel *presenceValue = nil;
    UILabel *frequencyValue = nil;
    UILabel *maxTokensValue = nil;
    UILabel *seedValue = nil;
    self.topPSlider = [self addSlider:@"top_p" valueLabel:&topPValue to:stack];
    self.temperatureSlider = [self addSlider:@"temperature" valueLabel:&temperatureValue to:stack];
    self.presenceSlider = [self addSlider:@"presence_penalty" valueLabel:&presenceValue to:stack];
    self.frequencySlider = [self addSlider:@"frequency_penalty" valueLabel:&frequencyValue to:stack];
    self.maxTokensSlider = [self addSlider:@"max_tokens" valueLabel:&maxTokensValue to:stack];
    self.seedSlider = [self addSlider:@"seed" valueLabel:&seedValue to:stack];
    self.topPValue = topPValue;
    self.temperatureValue = temperatureValue;
    self.presenceValue = presenceValue;
    self.frequencyValue = frequencyValue;
    self.maxTokensValue = maxTokensValue;
    self.seedValue = seedValue;

    UIButton *save = [UIButton buttonWithType:UIButtonTypeSystem];
    [save setTitle:@"保存" forState:UIControlStateNormal];
    save.backgroundColor = [self accentColor];
    [save setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    save.layer.cornerRadius = 8;
    save.accessibilityIdentifier = @"aiModelConfigSave";
    [save addTarget:self action:@selector(saveAndCloseConfig) forControlEvents:UIControlEventTouchUpInside];
    save.translatesAutoresizingMaskIntoConstraints = NO;
    [stack addArrangedSubview:save];
    [save.heightAnchor constraintEqualToConstant:44].active = YES;

    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:safe.topAnchor constant:8],
        [back.leadingAnchor constraintEqualToAnchor:self.configPanel.leadingAnchor constant:12],
        [self.configTitleLabel.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [self.configTitleLabel.leadingAnchor constraintEqualToAnchor:back.trailingAnchor constant:12],
        [scroll.topAnchor constraintEqualToAnchor:back.bottomAnchor constant:8],
        [scroll.leadingAnchor constraintEqualToAnchor:self.configPanel.leadingAnchor],
        [scroll.trailingAnchor constraintEqualToAnchor:self.configPanel.trailingAnchor],
        [scroll.bottomAnchor constraintEqualToAnchor:self.configPanel.bottomAnchor],
        [stack.topAnchor constraintEqualToAnchor:scroll.topAnchor constant:12],
        [stack.leadingAnchor constraintEqualToAnchor:scroll.leadingAnchor constant:16],
        [stack.trailingAnchor constraintEqualToAnchor:scroll.trailingAnchor constant:-16],
        [stack.bottomAnchor constraintEqualToAnchor:scroll.bottomAnchor constant:-24],
        [stack.widthAnchor constraintEqualToAnchor:scroll.widthAnchor constant:-32],
    ]];
}

- (UITextField *)addField:(NSString *)placeholder to:(UIStackView *)stack {
    UILabel *label = [[UILabel alloc] init];
    label.text = placeholder;
    label.font = [UIFont systemFontOfSize:12];
    label.textColor = [UIColor colorWithWhite:0.4 alpha:1];
    [stack addArrangedSubview:label];
    UITextField *field = [[UITextField alloc] init];
    field.borderStyle = UITextBorderStyleRoundedRect;
    field.font = [UIFont systemFontOfSize:15];
    field.delegate = self;
    field.translatesAutoresizingMaskIntoConstraints = NO;
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
    self.modelsBodyHeight.active = !self.modelsExpanded;
    self.modelsBodyBottom.active = self.modelsExpanded;
    self.chevronLabel.text = self.modelsExpanded ? @"▴" : @"▾";
    [UIView animateWithDuration:0.2 animations:^{
        [self.view layoutIfNeeded];
    }];
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
    NSURL *support = [[[NSFileManager defaultManager] URLsForDirectory:NSApplicationSupportDirectory inDomains:NSUserDomainMask] lastObject];
    [[NSFileManager defaultManager] createDirectoryAtURL:support withIntermediateDirectories:YES attributes:nil error:nil];
    return [support URLByAppendingPathComponent:kAvatarFileName];
}

- (void)reloadProfile {
    NSString *name = [[NSUserDefaults standardUserDefaults] stringForKey:kProfileNameKey];
    self.nameLabel.text = name.length > 0 ? name : @"用户";
    UIImage *image = [UIImage imageWithContentsOfFile:[self avatarURL].path];
    if (image == nil) {
        image = [UIImage imageNamed:@"HomeAvatar"];
    }
    self.avatarView.image = image;
}

- (void)editProfile {
    UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"编辑头像和昵称"
                                                                   message:nil
                                                            preferredStyle:UIAlertControllerStyleAlert];
    [alert addTextFieldWithConfigurationHandler:^(UITextField *field) {
        field.text = [[NSUserDefaults standardUserDefaults] stringForKey:kProfileNameKey];
        field.placeholder = @"昵称";
    }];
    [alert addAction:[UIAlertAction actionWithTitle:@"更换头像" style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
        NSString *name = alert.textFields.firstObject.text;
        if (name.length > 0) {
            [[NSUserDefaults standardUserDefaults] setObject:name forKey:kProfileNameKey];
        }
        PHPickerConfiguration *config = [[PHPickerConfiguration alloc] init];
        config.selectionLimit = 1;
        config.filter = [PHPickerFilter imagesFilter];
        PHPickerViewController *picker = [[PHPickerViewController alloc] initWithConfiguration:config];
        picker.delegate = self;
        [self presentViewController:picker animated:YES completion:nil];
    }]];
    [alert addAction:[UIAlertAction actionWithTitle:@"保存昵称" style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
        NSString *name = alert.textFields.firstObject.text ?: @"";
        [[NSUserDefaults standardUserDefaults] setObject:name forKey:kProfileNameKey];
        [self reloadProfile];
    }]];
    [alert addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];
    [self presentViewController:alert animated:YES completion:nil];
}

- (void)picker:(PHPickerViewController *)picker didFinishPicking:(NSArray<PHPickerResult *> *)results {
    [picker dismissViewControllerAnimated:YES completion:nil];
    PHPickerResult *result = results.firstObject;
    if (result == nil) {
        [self reloadProfile];
        return;
    }
    [result.itemProvider loadObjectOfClass:[UIImage class] completionHandler:^(id object, NSError *error) {
        UIImage *image = (UIImage *)object;
        if (![image isKindOfClass:[UIImage class]]) {
            return;
        }
        NSData *data = UIImageJPEGRepresentation(image, 0.85);
        [data writeToURL:[self avatarURL] atomically:YES];
        dispatch_async(dispatch_get_main_queue(), ^{
            [self reloadProfile];
        });
    }];
}

- (void)clearCache {
    UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"清理缓存"
                                                                   message:@"将删除临时文件和缓存目录中的内容。"
                                                            preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];
    [alert addAction:[UIAlertAction actionWithTitle:@"清理" style:UIAlertActionStyleDestructive handler:^(UIAlertAction *action) {
        NSFileManager *fm = [NSFileManager defaultManager];
        NSArray<NSURL *> *roots = @[
            [fm URLsForDirectory:NSCachesDirectory inDomains:NSUserDomainMask].lastObject,
            [NSURL fileURLWithPath:NSTemporaryDirectory()],
        ];
        for (NSURL *root in roots) {
            NSArray<NSURL *> *children = [fm contentsOfDirectoryAtURL:root includingPropertiesForKeys:nil options:0 error:nil];
            for (NSURL *child in children) {
                [fm removeItemAtURL:child error:nil];
            }
        }
    }]];
    [self presentViewController:alert animated:YES completion:nil];
}

- (void)showAbout {
    NSString *version = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleShortVersionString"] ?: @"";
    NSString *build = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleVersion"] ?: @"";
    NSString *message = [NSString stringWithFormat:@"Orange Office %@ (%@)", version, build];
    UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"关于"
                                                                   message:message
                                                            preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:nil]];
    [self presentViewController:alert animated:YES completion:nil];
}

- (BOOL)textFieldShouldReturn:(UITextField *)textField {
    [textField resignFirstResponder];
    return YES;
}

@end
