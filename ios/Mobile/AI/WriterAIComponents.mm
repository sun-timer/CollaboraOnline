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

        UIImageView *icon = [[UIImageView alloc] initWithImage:[UIImage writerIconNamed:systemIcon]];
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
        [close setImage:[UIImage writerIconNamed:@"close"] forState:UIControlStateNormal];
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
                       [self tile:@"ai-continue" title:@"AI 续写" taskType:@"continue_write" enabled:YES],
                       [self tile:@"ai-polish" title:@"文案润色" taskType:@"polish" enabled:YES],
                       [self tile:@"ai-outline" title:@"总结大纲" taskType:@"summarize" enabled:YES],
                   ]
                   to:content];
        [self addSection:@"文案处理"
                   tiles:@[
                       [self tile:@"ai-translate" title:@"翻译" taskType:@"translate" enabled:YES],
                       [self tile:@"ai-expand" title:@"扩写" taskType:@"expand" enabled:YES],
                       [self tile:@"ai-condense" title:@"缩写" taskType:@"condense" enabled:YES],
                       [self tile:@"ai-rewrite" title:@"改写" taskType:@"rewrite" enabled:NO],
                   ]
                   to:content];
        [self addSection:@"其他"
                   tiles:@[
                       [self tile:@"ai-leave" title:@"请假申请" taskType:@"leave_apply" enabled:NO],
                       [self tile:@"ai-notice" title:@"通知模板" taskType:@"general_notice" enabled:NO],
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

// ---------------------------------------------------------------------------
#pragma mark - UIImage (WriterAIIcons) — Figma/Android-sourced shared icons

#import "WriterAIIconPNG.h"

@implementation UIImage (WriterAIIcons)

// Minimal SVG-path command parser (M/L/H/V/C/S/Q/T/Z, absolute + relative).
// Rendering uses the raw viewBox coordinates; the caller applies a scale.
static BOOL WriterAIIconAppendPath(UIBezierPath *path, NSString *d)
{
    const char *p = d.UTF8String;
    if (!p) {
        return NO;
    }
    char cmd = 0;
    double cx = 0, cy = 0, startX = 0, startY = 0;
    BOOL firstPointOfCommand = YES;

    while (*p) {
        while (*p == ' ' || *p == ',' || *p == '\t' || *p == '\n' || *p == '\r') {
            p++;
        }
        if (!*p) {
            break;
        }
        if (isalpha((unsigned char)*p)) {
            cmd = *p++;
            if (cmd == 'Z' || cmd == 'z') {
                [path closePath];
                cx = startX;
                cy = startY;
                firstPointOfCommand = YES;
                continue;
            }
            firstPointOfCommand = YES;
        }
        if (!cmd) {
            return NO;
        }

        int argCount = 0;
        switch (cmd) {
            case 'M': case 'm': case 'L': case 'l': case 'T': case 't': argCount = 2; break;
            case 'H': case 'h': case 'V': case 'v': argCount = 1; break;
            case 'C': case 'c': case 'S': case 's': argCount = 6; break;
            case 'Q': case 'q': argCount = 4; break;
            case 'A': case 'a':
                // Arc commands are not used by the bundled icon set; refuse loudly.
                return NO;
            default:
                return NO;
        }
        double args[6];
        for (int i = 0; i < argCount; i++) {
            while (*p == ' ' || *p == ',' || *p == '\t' || *p == '\n' || *p == '\r') {
                p++;
            }
            if (!*p) {
                return NO;
            }
            char *end = NULL;
            args[i] = strtod(p, &end);
            if (end == p) {
                return NO;
            }
            p = end;
        }

        BOOL relative = islower((unsigned char)cmd);
        double rx = 0, ry = 0;
        switch (cmd) {
            case 'M': case 'm': {
                double x = args[0], y = args[1];
                double nx = relative ? cx + x : x;
                double ny = relative ? cy + y : y;
                if (firstPointOfCommand) {
                    [path moveToPoint:CGPointMake(nx, ny)];
                    startX = nx;
                    startY = ny;
                    firstPointOfCommand = NO;
                } else {
                    [path addLineToPoint:CGPointMake(nx, ny)];
                }
                cx = nx;
                cy = ny;
                break;
            }
            case 'L': case 'l': {
                double x = args[0], y = args[1];
                [path addLineToPoint:CGPointMake(relative ? cx + x : x, relative ? cy + y : y)];
                cx = relative ? cx + x : x;
                cy = relative ? cy + y : y;
                break;
            }
            case 'H': case 'h': {
                double x = args[0];
                double nx = relative ? cx + x : x;
                [path addLineToPoint:CGPointMake(nx, cy)];
                cx = nx;
                break;
            }
            case 'V': case 'v': {
                double y = args[0];
                double ny = relative ? cy + y : y;
                [path addLineToPoint:CGPointMake(cx, ny)];
                cy = ny;
                break;
            }
            case 'C': case 'c': {
                double c1x = args[0], c1y = args[1], c2x = args[2], c2y = args[3], x = args[4], y = args[5];
                if (relative) {
                    c1x += cx; c1y += cy; c2x += cx; c2y += cy; x += cx; y += cy;
                }
                [path addCurveToPoint:CGPointMake(x, y)
                        controlPoint1:CGPointMake(c1x, c1y)
                        controlPoint2:CGPointMake(c2x, c2y)];
                cx = x; cy = y;
                break;
            }
            case 'S': case 's': {
                double c2x = args[0], c2y = args[1], x = args[2], y = args[3];
                if (relative) {
                    c2x += cx; c2y += cy; x += cx; y += cy;
                }
                // Reflect the previous control point when the previous command was C/S.
                [path addCurveToPoint:CGPointMake(x, y)
                        controlPoint1:CGPointMake(cx, cy)
                        controlPoint2:CGPointMake(c2x, c2y)];
                cx = x; cy = y;
                break;
            }
            case 'Q': case 'q': {
                double qx = args[0], qy = args[1], x = args[2], y = args[3];
                if (relative) {
                    qx += cx; qy += cy; x += cx; y += cy;
                }
                [path addQuadCurveToPoint:CGPointMake(x, y)
                              controlPoint:CGPointMake(qx, qy)];
                cx = x; cy = y;
                break;
            }
            case 'T': case 't': {
                double x = args[0], y = args[1];
                if (relative) {
                    x += cx; y += cy;
                }
                [path addQuadCurveToPoint:CGPointMake(x, y)
                              controlPoint:CGPointMake(cx, cy)];
                cx = x; cy = y;
                break;
            }
            default:
                (void)rx; (void)ry; (void)firstPointOfCommand;
                return NO;
        }
    }
    return YES;
}

static UIColor *WriterAIIconColor(NSString *hex)
{
    static NSMutableDictionary<NSString *, UIColor *> *cache;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        cache = [NSMutableDictionary dictionary];
    });
    UIColor *cached = cache[hex];
    if (cached) {
        return cached;
    }
    NSString *clean = [hex stringByReplacingOccurrencesOfString:@"#" withString:@""];
    unsigned int value = 0;
    NSScanner *scanner = [NSScanner scannerWithString:clean];
    if (![scanner scanHexInt:&value] || clean.length != 6) {
        return UIColor.blackColor;
    }
    UIColor *color = [UIColor colorWithRed:((value >> 16) & 0xFF) / 255.0
                                     green:((value >> 8) & 0xFF) / 255.0
                                      blue:(value & 0xFF) / 255.0
                                     alpha:1.0];
    cache[hex] = color;
    return color;
}

+ (NSArray<NSDictionary *> *)writerAIIconSpecs
{
    // Each spec: name, viewBox, stroke width (path units), optional fill,
    // optional tint (single-colour -> template), paths: (d, optional colour hex).
    return @[
        @{ @"name": @"back", @"vb": @48.0, @"fill": @YES, @"tint": @YES,
           @"paths": @[ @{ @"d": @"M31.5898 7.5C32.0391 7.50009 32.3844 7.64858 32.6641 7.94141L32.6729 7.9502C32.9214 8.19884 33.0645 8.51268 33.0947 8.92188C33.1069 9.29871 33.0042 9.64866 32.7734 9.98633L32.6729 10.0889L32.6699 10.0918L19.0889 23.8896L18.7422 24.2422L19.0908 24.5928L32.7461 38.3008V38.3018C33.0346 38.6042 33.1767 38.9585 33.1768 39.3926C33.1768 39.8173 33.0397 40.1648 32.7637 40.4629C32.3118 40.8304 31.9054 40.9805 31.5352 40.9805C31.1707 40.9804 30.8518 40.8372 30.5566 40.5L30.5459 40.4893L30.5361 40.4785L16.2236 25.9756L16.2207 25.9736L15.9785 25.7305C15.6613 25.3027 15.5 24.8127 15.5 24.2402C15.5 23.4965 15.7418 22.9416 16.2031 22.5234L16.2139 22.5137L16.2236 22.5029L30.5361 7.94727L30.542 7.94141C30.825 7.64502 31.1634 7.5 31.5898 7.5Z" } ] },
        @{ @"name": @"search", @"vb": @48.0, @"sw": @2.0, @"tint": @YES,
           @"paths": @[ @{ @"d": @"M20,34C27.732,34 34,27.732 34,20C34,12.268 27.732,6 20,6C12.268,6 6,12.268 6,20C6,27.732 12.268,34 20,34Z" },
                        @{ @"d": @"M30,30L42,42" } ] },
        @{ @"name": @"share", @"vb": @48.0, @"sw": @2.0, @"tint": @YES,
           @"paths": @[ @{ @"d": @"M28,6H42V20" },
                        @{ @"d": @"M25.8,22.2L41.1,6.9" },
                        @{ @"d": @"M42,28.5V36C42,39.314 39.314,42 36,42H12C8.686,42 6,39.314 6,36V12C6,8.686 8.686,6 12,6H19.5" } ] },
        @{ @"name": @"open-docs", @"vb": @48.0, @"sw": @2.0, @"tint": @YES,
           @"paths": @[ @{ @"d": @"M1.948,38.5L29.219,38.5C30.295,38.5 31.167,37.638 31.167,36.575L31.181,11.917L20.472,11.917L20.472,0L1.948,0C0.872,0 0,0.862 0,1.925L0,36.575C0,37.638 0.872,38.5 1.948,38.5Z" },
                        @{ @"d": @"M20.472,0L31.181,11.917" } ] },
        @{ @"name": @"undo", @"vb": @48.0, @"sw": @2.0, @"tint": @YES,
           @"paths": @[ @{ @"d": @"M12.9998 8 L6 14 L28.9938 14 C35.8768 14 41.7221 19.6204 41.9904 26.5 C42.2739 33.7696 36.2671 40 28.9938 40 L11.9984 40" } ] },
        @{ @"name": @"redo", @"vb": @48.0, @"sw": @2.0, @"tint": @YES,
           @"paths": @[ @{ @"d": @"M35.0002 8 L42 14 L19.0062 14 C12.1232 14 6.2779 19.6204 6.0096 26.5 C5.7261 33.7696 11.7329 40 19.0062 40 L36.0016 40" } ] },
        @{ @"name": @"comment", @"vb": @48.0, @"sw": @2.0, @"tint": @YES,
           @"paths": @[ @{ @"d": @"M25.4985 36H20.9985L10.9985 41V36H3.99854V6H43.9985V17" },
                        @{ @"d": @"M30.9676 41.0325L29.2535 36.7744L36.8857 18.8578L42.8579 21.4018L35.2257 39.3184L30.9676 41.0325Z" } ] },
        @{ @"name": @"close", @"vb": @48.0, @"sw": @2.0, @"tint": @YES,
           @"paths": @[ @{ @"d": @"M9.858,9.857L38.142,38.141M9.858,38.141L38.142,9.857" } ] },
        @{ @"name": @"keyboard", @"vb": @48.0, @"sw": @2.0, @"tint": @YES,
           @"paths": @[ @{ @"d": @"M42 7H6C4.89543 7 4 7.89543 4 9V37C4 38.1046 4.89543 39 6 39H42C43.1046 39 44 38.1046 44 37V9C44 7.89543 43.1046 7 42 7Z" },
                        @{ @"d": @"M12 19H14" },
                        @{ @"d": @"M21 19H23" },
                        @{ @"d": @"M29 19H36" },
                        @{ @"d": @"M12 28H36" } ] },
        @{ @"name": @"insert-image", @"vb": @48.0, @"sw": @2.0, @"tint": @YES,
           @"paths": @[ @{ @"d": @"M5.00146 10C5.00146 8.89543 5.89689 8 7.00146 8H41.0015C42.1061 8 43.0015 8.89543 43.0015 10V38C43.0015 39.1046 42.1061 40 41.0015 40H7.00146C5.89689 40 5.00146 39.1046 5.00146 38V10Z" },
                        @{ @"d": @"M14.4985 18.0005C15.3269 18.0005 15.9985 17.3289 15.9985 16.5005C15.9985 15.6721 15.3269 15.0005 14.4985 15.0005C13.6701 15.0005 12.9985 15.6721 12.9985 16.5005C12.9985 17.3289 13.6701 18.0005 14.4985 18.0005Z" },
                        @{ @"d": @"M15.0015 24.0005L20.0015 28.0005L26.0015 21.0005L43.0015 34.0005V38.0005C43.0015 39.1051 42.1061 40.0005 41.0015 40.0005H7.00146C5.89689 40.0005 5.00146 39.1051 5.00146 38.0005V34.0005L15.0015 24.0005Z" } ] },
        @{ @"name": @"empty-doc", @"vb": @40.0, @"sw": @2.0, @"tint": @YES,
           @"paths": @[ @{ @"d": @"M32.2704 19.0078V9.84437C32.2704 8.67649 31.3237 7.72974 30.1558 7.72974H6.82899C5.66111 7.72974 4.71436 8.67649 4.71436 9.84437V31.1712C4.71436 32.339 5.66111 33.2858 6.82899 33.2858H20.9924" },
                        @{ @"d": @"M25.5833 21.75L35.7916 27.2917L30.2499 27.875L27.3317 32.8333L25.5833 21.75Z" } ] },
        @{ @"name": @"ai-assistant", @"vb": @64.0, @"sw": @3.2,
           @"paths": @[ @{ @"d": @"M38.019,36.837H60.099", @"c": @"#EC5D1F" },
                        @{ @"d": @"M35.46,42.917H47.3", @"c": @"#EC5D1F" },
                        @{ @"d": @"M31.94,48.997H62.98", @"c": @"#EC5D1F" } ] },
        @{ @"name": @"ai-quick", @"vb": @48.0, @"sw": @2.0,
           @"paths": @[ @{ @"d": @"M29.5 4L34 8L39.0161 5.0549L36.5 10.5L41 14.5L35 14L32.75 19L31.5 13.5L25.5002 13L30.7541 9.825L29.5 4Z", @"c": @"#1278D9" },
                        @{ @"d": @"M24 21.0103L31.5 13.5", @"c": @"#1278D9" },
                        @{ @"d": @"M42.6667 32.4288V42.1333C42.6667 43.1643 41.622 44 40.3333 44H7.66667C6.378 44 5.33334 43.1643 5.33334 42.1333V8.53333C5.33334 7.50237 6.378 6.66666 7.66667 6.66666H19.3333", @"c": @"#101010" },
                        @{ @"d": @"M6 34.9996L16.6931 25.1976C17.4389 24.5139 18.5779 24.4949 19.3461 25.1534L32 35.9996", @"c": @"#101010" },
                        @{ @"d": @"M28 30.9999L32.7735 26.2264C33.4772 25.5227 34.5914 25.4435 35.3877 26.0407L42 30.9999", @"c": @"#101010" } ] },
        @{ @"name": @"ai-continue", @"vb": @48.0, @"sw": @2.0,
           @"paths": @[ @{ @"d": @"M24 24V19L39 4L44 9L29 24H24Z", @"c": @"#101010" },
                        @{ @"d": @"M15.998 24H8.99805C6.23663 24 3.99805 26.2386 3.99805 29C3.99805 31.7614 6.23663 34 8.99805 34H38.998C41.7594 34 43.998 36.2386 43.998 39C43.998 41.7614 41.7594 44 38.998 44H17.998", @"c": @"#1278D9" } ] },
        @{ @"name": @"ai-polish", @"vb": @48.0, @"sw": @2.0,
           @"paths": @[ @{ @"d": @"M20.1005 8.1005L24.3431 12.3431M30 4V10M39.8995 8.1005L35.6569 12.3431M44 18H38M39.8995 27.8995L35.6569 23.6569M30 32V26M16 18H22", @"c": @"#1278D9" },
                        @{ @"d": @"M20.3896 32.3171L7.81887 43.3165C7.81887 43.3165 6.64037 44.4951 5.46187 43.3165C4.28337 42.138 5.46187 40.9595 5.46187 40.9595L16.4613 28.3888L20.3896 32.3171Z", @"c": @"#101010" },
                        @{ @"d": @"M26.2833 26.6667L20.389 32.3174L16.4606 28.389L21.8283 22C21.8283 22 21.9603 22.1036 24.3174 24.4606C26.6744 26.8177 26.2833 26.6667 26.2833 26.6667Z", @"c": @"#101010" },
                        @{ @"d": @"M18.8174 33.692L17.246 35.0669L15.6747 36.4419", @"c": @"#101010" },
                        @{ @"d": @"M12.3351 33.103L13.7101 31.5317L15.085 29.9604", @"c": @"#101010" } ] },
        @{ @"name": @"ai-outline", @"vb": @48.0, @"sw": @2.0,
           @"paths": @[ @{ @"d": @"M3.99902 24H21.999", @"c": @"#101010" },
                        @{ @"d": @"M3.99902 37.9995H29.999", @"c": @"#101010" },
                        @{ @"d": @"M3.99902 10.0005H29.999", @"c": @"#101010" },
                        @{ @"d": @"M40.1105 20.4443L35.1105 24.8888L29.537 21.6164L32.3327 27.6666L27.3327 32.111L33.9994 31.5554L36.4994 37.111L37.8882 30.9999L44.5547 30.4443L38.717 26.9166L40.1105 20.4443Z", @"c": @"#1278D9" },
                        @{ @"d": @"M46.2217 39.3452L37.8883 31.0005", @"c": @"#1278D9" } ] },
        @{ @"name": @"ai-translate", @"vb": @48.0, @"sw": @2.0,
           @"paths": @[ @{ @"d": @"M28.2877 37H39.7163M28.2877 37L26.002 42M28.2877 37L34.002 24L39.7163 37M39.7163 37L42.002 42", @"c": @"#1278D9" },
                        @{ @"d": @"M15.998 6L16.998 9", @"c": @"#101010" },
                        @{ @"d": @"M6 10.9995H28", @"c": @"#101010" },
                        @{ @"d": @"M9.99805 16.0005C9.99805 16.0005 11.7875 22.2614 16.2612 25.7396C20.7348 29.2179 27.998 32.0005 27.998 32.0005", @"c": @"#101010" },
                        @{ @"d": @"M24 10.9995C24 10.9995 22.2105 19.2169 17.7368 23.7821C13.2632 28.3473 6 31.9995 6 31.9995", @"c": @"#101010" } ] },
        @{ @"name": @"ai-expand", @"vb": @48.0, @"sw": @2.0,
           @"paths": @[ @{ @"d": @"M39 6H9C7.34315 6 6 7.34315 6 9V39C6 40.6569 7.34315 42 9 42H39C40.6569 42 42 40.6569 42 39V9C42 7.34315 40.6569 6 39 6Z", @"c": @"#101010" },
                        @{ @"d": @"M34 24H14", @"c": @"#101010" },
                        @{ @"d": @"M34 15H14", @"c": @"#101010" },
                        @{ @"d": @"M34 33H14", @"c": @"#1278D9" } ] },
        @{ @"name": @"ai-condense", @"vb": @48.0, @"sw": @2.0,
           @"paths": @[ @{ @"d": @"M39 6H9C7.34315 6 6 7.34315 6 9V39C6 40.6569 7.34315 42 9 42H39C40.6569 42 42 40.6569 42 39V9C42 7.34315 40.6569 6 39 6Z", @"c": @"#101010" },
                        @{ @"d": @"M30 24H18", @"c": @"#1278D9" },
                        @{ @"d": @"M34 15H14", @"c": @"#101010" } ] },
        @{ @"name": @"ai-rewrite", @"vb": @48.0, @"sw": @2.0,
           @"paths": @[ @{ @"d": @"M30.999 8.99902L38.999 16.999", @"c": @"#101010" },
                        @{ @"d": @"M30.999 8.99902L38.999 16.999", @"c": @"#1278D9" },
                        @{ @"d": @"M8.99902 31.999L15.999 38.999", @"c": @"#1278D9" },
                        @{ @"d": @"M7.99904 31.999L35.9989 4L43.999 11.999L15.999 39.999L5.99902 41.999L7.99904 31.999Z", @"c": @"#101010" } ] },
        @{ @"name": @"ai-notice", @"vb": @48.0, @"sw": @2.0,
           @"paths": @[ @{ @"d": @"M24 9H42", @"c": @"#101010" },
                        @{ @"d": @"M24 19H42", @"c": @"#101010" },
                        @{ @"d": @"M6 29H42", @"c": @"#101010" },
                        @{ @"d": @"M6 39H42", @"c": @"#101010" },
                        @{ @"d": @"M6 19L7 17M7 17L11 9L15 17M7 17H15M16 19L15 17", @"c": @"#1278D9" } ] },
        @{ @"name": @"ai-leave", @"vb": @40.0, @"sw": @2.0, @"tint": @YES,
           @"paths": @[ @{ @"d": @"M32.2704 19.0078V9.84437C32.2704 8.67649 31.3237 7.72974 30.1558 7.72974H6.82899C5.66111 7.72974 4.71436 8.67649 4.71436 9.84437V31.1712C4.71436 32.339 5.66111 33.2858 6.82899 33.2858H20.9924", @"c": @"#101010" },
                        @{ @"d": @"M25.5833 21.75L35.7916 27.2917L30.2499 27.875L27.3317 32.8333L25.5833 21.75Z", @"c": @"#101010" } ] },
    ];
}

+ (nullable UIImage *)writerIconNamed:(NSString *)name
{
    static NSMutableDictionary<NSString *, UIImage *> *cache;
    static NSCache *pngBase64Cache;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        cache = [NSMutableDictionary dictionary];
        pngBase64Cache = [[NSCache alloc] init];
    });

    UIImage *cached = cache[name];
    if (cached) {
        return cached;
    }

    // Bitmap icons (Android Figma exports).
    NSDictionary<NSString *, NSString *> *pngSources = @{
        @"mobile-preview": WriterAIIconPNG_mobile_preview,
        @"function": WriterAIIconPNG_function,
        @"ai-feature": WriterAIIconPNG_ai_feature,
        @"character": WriterAIIconPNG_character,
        @"paragraph": WriterAIIconPNG_paragraph,
        @"recent": WriterAIIconPNG_recent,
    };
    NSString *pngBase64 = pngSources[name];
    if (pngBase64.length > 0) {
        UIImage *bitmap = [pngBase64Cache objectForKey:name];
        if (!bitmap) {
            NSData *data = [[NSData alloc] initWithBase64EncodedString:pngBase64 options:0];
            bitmap = [UIImage imageWithData:data];
            if (bitmap) {
                bitmap = [bitmap imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
                [pngBase64Cache setObject:bitmap forKey:name];
            }
        }
        if (bitmap) {
            cache[name] = bitmap;
            return bitmap;
        }
    }

    // Hand-drawn "more" ellipsis (generic three dots; Figma has no export for it).
    if ([name isEqualToString:@"more"]) {
        UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:CGSizeMake(24, 24)];
        UIImage *more = [renderer imageWithActions:^(UIGraphicsImageRendererContext *ctx) {
            [UIColor.blackColor setFill];
            for (CGFloat x = 3; x <= 17; x += 7) {
                [[UIBezierPath bezierPathWithOvalInRect:CGRectMake(x, 10, 4, 4)] fill];
            }
        }];
        more = [more imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
        cache[name] = more;
        return more;
    }

    NSDictionary *spec = nil;
    for (NSDictionary *candidate in [self writerAIIconSpecs]) {
        if ([candidate[@"name"] isEqualToString:name]) {
            spec = candidate;
            break;
        }
    }
    if (!spec) {
        return nil;
    }

    double viewBox = [spec[@"vb"] doubleValue];
    double scale = 24.0 / viewBox;
    double strokeWidth = [spec[@"sw"] doubleValue] * scale;
    BOOL fill = [spec[@"fill"] boolValue];
    BOOL tint = [spec[@"tint"] boolValue];

    UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:CGSizeMake(24, 24)];
    UIImage *image = [renderer imageWithActions:^(UIGraphicsImageRendererContext *ctx) {
        CGContextRef c = ctx.CGContext;
        CGContextSetLineCap(c, kCGLineCapRound);
        CGContextSetLineJoin(c, kCGLineJoinRound);
        for (NSDictionary *pathSpec in spec[@"paths"]) {
            UIBezierPath *path = [UIBezierPath bezierPath];
            if (!WriterAIIconAppendPath(path, pathSpec[@"d"])) {
                continue;
            }
            [path applyTransform:CGAffineTransformMakeScale(scale, scale)];
            NSString *hex = pathSpec[@"c"] ?: @"#101010";
            UIColor *color = WriterAIIconColor(hex);
            if (fill) {
                [color setFill];
                [path fill];
            } else {
                path.lineWidth = strokeWidth;
                [color setStroke];
                [path stroke];
            }
        }
    }];
    if (tint) {
        image = [image imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
    }
    if (image) {
        cache[name] = image;
    }
    return image;
}

@end