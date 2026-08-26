// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 *
 * ObjC++ port of the phase3 MobileWriterAI components (AIPanelView /
 * AIResultModal / LanguagePicker / FloatingAIButton). Tailored to the
 * writer-editor-rebuild branch: UIKit only, no Swift, matching the
 * DocumentViewController integration call sites.
 */

#import "WriterAIComponents.h"

static UIColor *WriterAIColorTextPrimary(void) {
    return [UIColor colorWithWhite:0.08 alpha:1];
}
static UIColor *WriterAIColorTextSecondary(void) {
    return [UIColor colorWithWhite:0.42 alpha:1];
}
static UIColor *WriterAIColorAccent(void) {
    return [UIColor colorWithRed:254.0 / 255.0 green:58.0 / 255.0 blue:58.0 / 255.0 alpha:1];
}

// ---------------------------------------------------------------------------
#pragma mark - WriterAFloatingAIButton

@implementation WriterAFloatingAIButton {
    void (^_onAction)(NSString *action);
}

- (instancetype)initWithOnAction:(void (^)(NSString *action))onAction {
    self = [super initWithFrame:CGRectZero];
    if (self) {
        _onAction = [onAction copy];
        self.backgroundColor = WriterAIColorAccent();
        self.layer.cornerRadius = 28;
        [self setTitle:@"AI" forState:UIControlStateNormal];
        [self setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
        self.titleLabel.font = [UIFont boldSystemFontOfSize:17];
        self.accessibilityLabel = @"AI 助手";
        [self addTarget:self action:@selector(tapped) forControlEvents:UIControlEventTouchUpInside];
    }
    return self;
}

- (void)tapped {
    if (_onAction) {
        _onAction(@"tap");
    }
}

@end

// ---------------------------------------------------------------------------
#pragma mark - WriterAITileCard

@interface WriterAITileCard : UIButton
@property (nonatomic, copy) NSString *taskType;
@end

@implementation WriterAITileCard

- (instancetype)initWithIcon:(NSString *)systemIcon title:(NSString *)title taskType:(NSString *)taskType enabled:(BOOL)enabled {
    self = [super initWithFrame:CGRectZero];
    if (self) {
        _taskType = [taskType copy];
        self.enabled = enabled;
        self.alpha = enabled ? 1 : 0.45;
        self.layer.cornerRadius = 12;
        self.backgroundColor = [UIColor colorWithWhite:0.97 alpha:1];

        UIImageView *icon = [[UIImageView alloc] initWithImage:[UIImage systemImageNamed:systemIcon]];
        icon.translatesAutoresizingMaskIntoConstraints = NO;
        icon.tintColor = WriterAIColorTextPrimary();
        icon.contentMode = UIViewContentModeScaleAspectFit;
        [self addSubview:icon];

        UILabel *label = [[UILabel alloc] init];
        label.translatesAutoresizingMaskIntoConstraints = NO;
        label.text = title;
        label.font = [UIFont systemFontOfSize:12];
        label.textColor = WriterAIColorTextPrimary();
        label.textAlignment = NSTextAlignmentCenter;
        label.numberOfLines = 1;
        [self addSubview:label];

        [NSLayoutConstraint activateConstraints:@[
            [icon.centerXAnchor constraintEqualToAnchor:self.centerXAnchor],
            [icon.topAnchor constraintEqualToAnchor:self.topAnchor constant:10],
            [icon.widthAnchor constraintEqualToConstant:22],
            [icon.heightAnchor constraintEqualToConstant:22],
            [label.topAnchor constraintEqualToAnchor:icon.bottomAnchor constant:6],
            [label.leadingAnchor constraintEqualToAnchor:self.leadingAnchor constant:2],
            [label.trailingAnchor constraintEqualToAnchor:self.trailingAnchor constant:-2],
            [label.bottomAnchor constraintLessThanOrEqualToAnchor:self.bottomAnchor constant:-6],
        ]];
    }
    return self;
}

@end

// ---------------------------------------------------------------------------
#pragma mark - WriterAIPanelView

@implementation WriterAIPanelView {
    void (^_onTile)(NSString *taskType);
    void (^_onClose)(void);
    UIScrollView *_scrollView;
}

- (instancetype)initWithWidth:(CGFloat)width
                       onTile:(void (^)(NSString *taskType))onTile
                      onClose:(void (^)(void))onClose {
    self = [super initWithFrame:CGRectZero];
    if (self) {
        _onTile = [onTile copy];
        _onClose = [onClose copy];
        self.backgroundColor = UIColor.whiteColor;
        self.layer.cornerRadius = 24;
        self.layer.maskedCorners = kCALayerMinXMinYCorner | kCALayerMaxXMinYCorner;

        UILabel *title = [[UILabel alloc] init];
        title.translatesAutoresizingMaskIntoConstraints = NO;
        title.text = @"AI功能";
        title.font = [UIFont boldSystemFontOfSize:17];
        title.textColor = WriterAIColorTextPrimary();
        [self addSubview:title];

        UIButton *close = [UIButton buttonWithType:UIButtonTypeSystem];
        close.translatesAutoresizingMaskIntoConstraints = NO;
        [close setImage:[UIImage systemImageNamed:@"xmark"] forState:UIControlStateNormal];
        close.tintColor = WriterAIColorTextPrimary();
        close.accessibilityLabel = @"关闭";
        [close addTarget:self action:@selector(closeTapped) forControlEvents:UIControlEventTouchUpInside];
        [self addSubview:close];

        _scrollView = [[UIScrollView alloc] init];
        _scrollView.translatesAutoresizingMaskIntoConstraints = NO;
        [self addSubview:_scrollView];

        UIStackView *content = [[UIStackView alloc] init];
        content.translatesAutoresizingMaskIntoConstraints = NO;
        content.axis = UILayoutConstraintAxisVertical;
        content.spacing = 16;
        [_scrollView addSubview:content];

        [self addSection:@"文案生成"
                   tiles:@[
                       [self tile:@"doc.text" title:@"AI 续写" taskType:@"continue_write" enabled:YES],
                       [self tile:@"wand.and.stars" title:@"文案润色" taskType:@"polish" enabled:YES],
                       [self tile:@"text.bubble" title:@"总结大纲" taskType:@"summarize" enabled:YES],
                   ]
                   to:content];
        [self addSection:@"文案处理"
                   tiles:@[
                       [self tile:@"arrow.left.arrow.right" title:@"翻译" taskType:@"translate" enabled:YES],
                       [self tile:@"arrow.up.left.and.arrow.down.right" title:@"扩写" taskType:@"expand" enabled:YES],
                       [self tile:@"arrow.down.right.and.arrow.up.left" title:@"缩写" taskType:@"condense" enabled:YES],
                       [self tile:@"pencil.and.outline" title:@"改写" taskType:@"rewrite" enabled:NO],
                   ]
                   to:content];
        [self addSection:@"其他"
                   tiles:@[
                       [self tile:@"calendar" title:@"请假申请" taskType:@"leave_apply" enabled:NO],
                       [self tile:@"doc.richtext" title:@"通知模板" taskType:@"general_notice" enabled:NO],
                   ]
                   to:content];

        [NSLayoutConstraint activateConstraints:@[
            [title.topAnchor constraintEqualToAnchor:self.topAnchor constant:16],
            [title.leadingAnchor constraintEqualToAnchor:self.leadingAnchor constant:16],
            [close.centerYAnchor constraintEqualToAnchor:title.centerYAnchor],
            [close.trailingAnchor constraintEqualToAnchor:self.trailingAnchor constant:-16],
            [close.widthAnchor constraintEqualToConstant:44],
            [close.heightAnchor constraintEqualToConstant:44],
            [_scrollView.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:8],
            [_scrollView.leadingAnchor constraintEqualToAnchor:self.leadingAnchor],
            [_scrollView.trailingAnchor constraintEqualToAnchor:self.trailingAnchor],
            [_scrollView.bottomAnchor constraintEqualToAnchor:self.bottomAnchor],
            [content.topAnchor constraintEqualToAnchor:_scrollView.topAnchor constant:8],
            [content.leadingAnchor constraintEqualToAnchor:_scrollView.leadingAnchor constant:16],
            [content.trailingAnchor constraintEqualToAnchor:_scrollView.trailingAnchor constant:-16],
            [content.bottomAnchor constraintEqualToAnchor:_scrollView.bottomAnchor constant:-16],
            [content.widthAnchor constraintEqualToAnchor:_scrollView.widthAnchor constant:-32],
        ]];
    }
    return self;
}

- (WriterAITileCard *)tile:(NSString *)icon title:(NSString *)title taskType:(NSString *)taskType enabled:(BOOL)enabled {
    WriterAITileCard *card = [[WriterAITileCard alloc] initWithIcon:icon title:title taskType:taskType enabled:enabled];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    [card addTarget:self action:@selector(tileTapped:) forControlEvents:UIControlEventTouchUpInside];
    [card.widthAnchor constraintEqualToConstant:90].active = YES;
    [card.heightAnchor constraintEqualToConstant:72].active = YES;
    return card;
}

- (void)addSection:(NSString *)sectionTitle tiles:(NSArray<WriterAITileCard *> *)tiles to:(UIStackView *)stack {
    UILabel *head = [[UILabel alloc] init];
    head.text = sectionTitle;
    head.font = [UIFont systemFontOfSize:14 weight:UIFontWeightSemibold];
    head.textColor = WriterAIColorTextSecondary();
    [stack addArrangedSubview:head];

    UIStackView *row = [[UIStackView alloc] initWithArrangedSubviews:tiles];
    row.axis = UILayoutConstraintAxisHorizontal;
    row.spacing = 10;
    row.alignment = UIStackViewAlignmentTop;
    [stack addArrangedSubview:row];
}

- (void)tileTapped:(WriterAITileCard *)sender {
    if (_onTile) {
        _onTile(sender.taskType);
    }
}

- (void)closeTapped {
    if (_onClose) {
        _onClose();
    }
}

- (void)showIn:(UIView *)parent aboveBottomInset:(CGFloat)inset {
    self.translatesAutoresizingMaskIntoConstraints = NO;
    self.alpha = 0;
    [parent addSubview:self];
    [NSLayoutConstraint activateConstraints:@[
        [self.leadingAnchor constraintEqualToAnchor:parent.leadingAnchor],
        [self.trailingAnchor constraintEqualToAnchor:parent.trailingAnchor],
        [self.bottomAnchor constraintEqualToAnchor:parent.bottomAnchor constant:-inset],
        [self.heightAnchor constraintEqualToConstant:420],
    ]];
    [UIView animateWithDuration:0.28 animations:^{
        self.alpha = 1;
    }];
}

- (void)dismiss {
    [UIView animateWithDuration:0.2 animations:^{
        self.alpha = 0;
    } completion:^(BOOL finished) {
        [self removeFromSuperview];
    }];
}

@end

// ---------------------------------------------------------------------------
#pragma mark - WriterAAIResultModal

@implementation WriterAAIResultModal {
    NSString *_title;
    void (^_onClose)(void);
    void (^_onStop)(void);
    void (^_onRetry)(void);
    void (^_onInsert)(void);
    void (^_onCopy)(void);
    UIView *_card;
    UILabel *_statusLabel;
    UITextView *_textView;
    UIButton *_stopButton;
    UIButton *_retryButton;
    UIButton *_insertButton;
    UIButton *_copyButton;
}

- (instancetype)initWithTitle:(NSString *)title
                      onClose:(void (^)(void))onClose
                       onStop:(void (^)(void))onStop
                      onRetry:(void (^)(void))onRetry
                      onInsert:(void (^)(void))onInsert
                        onCopy:(void (^)(void))onCopy {
    self = [super initWithFrame:CGRectZero];
    if (self) {
        _title = [title copy];
        _onClose = [onClose copy];
        _onStop = [onStop copy];
        _onRetry = [onRetry copy];
        _onInsert = [onInsert copy];
        _onCopy = [onCopy copy];
    }
    return self;
}

- (void)showIn:(UIView *)parent {
    self.backgroundColor = [UIColor colorWithWhite:0 alpha:0.4];
    self.translatesAutoresizingMaskIntoConstraints = NO;
    self.alpha = 0;
    [parent addSubview:self];
    [NSLayoutConstraint activateConstraints:@[
        [self.topAnchor constraintEqualToAnchor:parent.topAnchor],
        [self.bottomAnchor constraintEqualToAnchor:parent.bottomAnchor],
        [self.leadingAnchor constraintEqualToAnchor:parent.leadingAnchor],
        [self.trailingAnchor constraintEqualToAnchor:parent.trailingAnchor],
    ]];

    _card = [[UIView alloc] init];
    _card.translatesAutoresizingMaskIntoConstraints = NO;
    _card.backgroundColor = UIColor.whiteColor;
    _card.layer.cornerRadius = 20;
    [self addSubview:_card];

    UILabel *titleLabel = [[UILabel alloc] init];
    titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    titleLabel.text = _title;
    titleLabel.font = [UIFont boldSystemFontOfSize:17];
    titleLabel.textColor = WriterAIColorTextPrimary();
    [_card addSubview:titleLabel];

    _statusLabel = [[UILabel alloc] init];
    _statusLabel.translatesAutoresizingMaskIntoConstraints = NO;
    _statusLabel.text = @"生成中…";
    _statusLabel.font = [UIFont systemFontOfSize:13];
    _statusLabel.textColor = WriterAIColorTextSecondary();
    [_card addSubview:_statusLabel];

    _textView = [[UITextView alloc] init];
    _textView.translatesAutoresizingMaskIntoConstraints = NO;
    _textView.editable = NO;
    _textView.font = [UIFont systemFontOfSize:15];
    _textView.backgroundColor = [UIColor colorWithWhite:0.97 alpha:1];
    _textView.layer.cornerRadius = 8;
    [_card addSubview:_textView];

    _stopButton = [self actionButton:@"停止" tint:WriterAIColorTextSecondary() selector:@selector(stopTapped)];
    _retryButton = [self actionButton:@"重试" tint:WriterAIColorTextSecondary() selector:@selector(retryTapped)];
    _insertButton = [self actionButton:@"插入文档" tint:UIColor.whiteColor selector:@selector(insertTapped)];
    _copyButton = [self actionButton:@"复制" tint:WriterAIColorTextPrimary() selector:@selector(copyTapped)];
    _insertButton.backgroundColor = WriterAIColorAccent();
    _insertButton.layer.cornerRadius = 8;
    _retryButton.hidden = YES;

    UIStackView *actionRow = [[UIStackView alloc] initWithArrangedSubviews:@[ _stopButton, _retryButton, _copyButton, _insertButton ]];
    actionRow.translatesAutoresizingMaskIntoConstraints = NO;
    actionRow.axis = UILayoutConstraintAxisHorizontal;
    actionRow.spacing = 10;
    actionRow.distribution = UIStackViewDistributionFillEqually;
    [_card addSubview:actionRow];

    [NSLayoutConstraint activateConstraints:@[
        [_card.centerXAnchor constraintEqualToAnchor:self.centerXAnchor],
        [_card.centerYAnchor constraintEqualToAnchor:self.centerYAnchor],
        [_card.widthAnchor constraintEqualToConstant:MIN(parent.bounds.size.width - 40, 420)],
        [titleLabel.topAnchor constraintEqualToAnchor:_card.topAnchor constant:16],
        [titleLabel.leadingAnchor constraintEqualToAnchor:_card.leadingAnchor constant:16],
        [_statusLabel.topAnchor constraintEqualToAnchor:titleLabel.bottomAnchor constant:4],
        [_statusLabel.leadingAnchor constraintEqualToAnchor:titleLabel.leadingAnchor],
        [_textView.topAnchor constraintEqualToAnchor:_statusLabel.bottomAnchor constant:8],
        [_textView.leadingAnchor constraintEqualToAnchor:_card.leadingAnchor constant:16],
        [_textView.trailingAnchor constraintEqualToAnchor:_card.trailingAnchor constant:-16],
        [_textView.heightAnchor constraintEqualToConstant:180],
        [actionRow.topAnchor constraintEqualToAnchor:_textView.bottomAnchor constant:12],
        [actionRow.leadingAnchor constraintEqualToAnchor:_card.leadingAnchor constant:16],
        [actionRow.trailingAnchor constraintEqualToAnchor:_card.trailingAnchor constant:-16],
        [actionRow.bottomAnchor constraintEqualToAnchor:_card.bottomAnchor constant:-16],
        [actionRow.heightAnchor constraintEqualToConstant:40],
    ]];

    _card.transform = CGAffineTransformMakeScale(0.92, 0.92);
    [UIView animateWithDuration:0.18 animations:^{
        self.alpha = 1;
        self->_card.transform = CGAffineTransformIdentity;
    }];
}

- (UIButton *)actionButton:(NSString *)title tint:(UIColor *)tint selector:(SEL)selector {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    [button setTitle:title forState:UIControlStateNormal];
    [button setTitleColor:tint forState:UIControlStateNormal];
    button.titleLabel.font = [UIFont systemFontOfSize:14];
    button.layer.borderColor = [UIColor colorWithWhite:0.85 alpha:1].CGColor;
    button.layer.borderWidth = 1;
    button.layer.cornerRadius = 8;
    [button addTarget:self action:selector forControlEvents:UIControlEventTouchUpInside];
    return button;
}

- (void)setStreamingText:(NSString *)text {
    _textView.text = text;
    _statusLabel.text = @"生成中…";
    _retryButton.hidden = YES;
}

- (void)setReadyWithFullText:(NSString *)text {
    _textView.text = text;
    _statusLabel.text = @"已生成";
    _retryButton.hidden = NO;
}

- (void)setErrorWithMessage:(NSString *)message {
    _statusLabel.text = @"生成失败";
    _statusLabel.textColor = WriterAIColorAccent();
    _textView.text = message;
}

- (void)stopTapped { if (_onStop) { _onStop(); } }
- (void)retryTapped { if (_onRetry) { _onRetry(); } }
- (void)insertTapped { if (_onInsert) { _onInsert(); } }
- (void)copyTapped { if (_onCopy) { _onCopy(); } }

- (void)dismiss {
    [UIView animateWithDuration:0.15 animations:^{
        self.alpha = 0;
    } completion:^(BOOL finished) {
        [self removeFromSuperview];
    }];
    if (_onClose) {
        _onClose();
    }
}

@end

// ---------------------------------------------------------------------------
#pragma mark - WriterALanguagePicker

@implementation WriterALanguagePicker {
    void (^_onSelect)(NSString *lang);
}

- (instancetype)initWithOnSelect:(void (^)(NSString *lang))onSelect {
    self = [super initWithFrame:CGRectZero];
    if (self) {
        _onSelect = [onSelect copy];
    }
    return self;
}

- (void)showIn:(UIView *)parent {
    self.backgroundColor = [UIColor colorWithWhite:0 alpha:0.4];
    self.translatesAutoresizingMaskIntoConstraints = NO;
    self.alpha = 0;
    [parent addSubview:self];
    [NSLayoutConstraint activateConstraints:@[
        [self.topAnchor constraintEqualToAnchor:parent.topAnchor],
        [self.bottomAnchor constraintEqualToAnchor:parent.bottomAnchor],
        [self.leadingAnchor constraintEqualToAnchor:parent.leadingAnchor],
        [self.trailingAnchor constraintEqualToAnchor:parent.trailingAnchor],
    ]];

    UIView *sheet = [[UIView alloc] init];
    sheet.translatesAutoresizingMaskIntoConstraints = NO;
    sheet.backgroundColor = UIColor.whiteColor;
    sheet.layer.cornerRadius = 24;
    sheet.layer.maskedCorners = kCALayerMinXMinYCorner | kCALayerMaxXMinYCorner;
    [self addSubview:sheet];

    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"选择翻译语言";
    title.font = [UIFont boldSystemFontOfSize:17];
    title.textColor = WriterAIColorTextPrimary();
    [sheet addSubview:title];

    NSArray<NSArray<NSString *> *> *languages = @[
        @[ @"自动检测", @"auto" ],
        @[ @"中文", @"zh" ],
        @[ @"英文", @"en" ],
        @[ @"日文", @"ja" ],
        @[ @"韩文", @"ko" ],
        @[ @"法文", @"fr" ],
        @[ @"德文", @"de" ],
        @[ @"西班牙文", @"es" ],
        @[ @"俄文", @"ru" ],
    ];
    UIStackView *rows = [[UIStackView alloc] init];
    rows.translatesAutoresizingMaskIntoConstraints = NO;
    rows.axis = UILayoutConstraintAxisVertical;
    rows.spacing = 2;
    [sheet addSubview:rows];

    for (NSArray<NSString *> *pair in languages) {
        UIButton *row = [UIButton buttonWithType:UIButtonTypeSystem];
        row.translatesAutoresizingMaskIntoConstraints = NO;
        [row setTitle:pair[0] forState:UIControlStateNormal];
        [row setTitleColor:WriterAIColorTextPrimary() forState:UIControlStateNormal];
        row.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeading;
        row.contentEdgeInsets = UIEdgeInsetsMake(0, 16, 0, 16);
        [row addTarget:self action:@selector(rowTapped:) forControlEvents:UIControlEventTouchUpInside];
        [row setAccessibilityValue:pair[1]];
        [rows addArrangedSubview:row];
        [row.heightAnchor constraintEqualToConstant:44].active = YES;
    }

    [NSLayoutConstraint activateConstraints:@[
        [sheet.leadingAnchor constraintEqualToAnchor:self.leadingAnchor],
        [sheet.trailingAnchor constraintEqualToAnchor:self.trailingAnchor],
        [sheet.bottomAnchor constraintEqualToAnchor:self.bottomAnchor],
        [title.topAnchor constraintEqualToAnchor:sheet.topAnchor constant:16],
        [title.leadingAnchor constraintEqualToAnchor:sheet.leadingAnchor constant:16],
        [rows.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:8],
        [rows.leadingAnchor constraintEqualToAnchor:sheet.leadingAnchor],
        [rows.trailingAnchor constraintEqualToAnchor:sheet.trailingAnchor],
        [rows.bottomAnchor constraintEqualToAnchor:sheet.bottomAnchor constant:-16],
    ]];

    [UIView animateWithDuration:0.2 animations:^{
        self.alpha = 1;
    }];
}

- (void)rowTapped:(UIButton *)sender {
    NSString *lang = sender.accessibilityValue;
    if (_onSelect && lang.length > 0) {
        _onSelect(lang);
    }
}

- (void)dismiss {
    [UIView animateWithDuration:0.15 animations:^{
        self.alpha = 0;
    } completion:^(BOOL finished) {
        [self removeFromSuperview];
    }];
}

@end