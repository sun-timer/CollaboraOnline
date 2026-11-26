#import "ImpressTypesetOverlayController.h"
#import "ImpressTypesetPlan.h"
#import "AIService.h"

@interface ImpressTypesetGradientButton : UIButton
@property (strong) CAGradientLayer *gradientLayer;
@end

@implementation ImpressTypesetGradientButton
- (instancetype)initWithFrame:(CGRect)frame {
    if ((self = [super initWithFrame:frame])) {
        _gradientLayer = [CAGradientLayer layer];
        _gradientLayer.colors = @[(id)[UIColor colorWithRed:0x1d/255.0 green:0xa8/255.0 blue:1 alpha:1].CGColor,
                                  (id)[UIColor colorWithRed:0xdd/255.0 green:0 blue:0xce/255.0 alpha:1].CGColor];
        _gradientLayer.startPoint = CGPointMake(0, 0);
        _gradientLayer.endPoint = CGPointMake(1, 1);
        [self.layer insertSublayer:_gradientLayer atIndex:0];
    }
    return self;
}
- (void)layoutSubviews {
    [super layoutSubviews];
    self.gradientLayer.frame = self.bounds;
    self.gradientLayer.cornerRadius = self.layer.cornerRadius;
}
@end

@interface ImpressTypesetOverlayController () <UITextViewDelegate>
@property (weak) UIViewController *host;
@property (strong) AIService *service;
@property (copy) ImpressTypesetContextProvider provider;
@property (copy) ImpressTypesetApplyHandler apply;
@property (strong) UIView *overlay;
@property (strong) UIView *panel;
@property (strong) UITextView *input;
@property (strong) UILabel *status;
@property (strong) UILabel *result;
@property (strong) UILabel *inputPlaceholder;
@property (strong) UIButton *start;
@property (strong) UIImageView *startIconView;
@property (strong) UILabel *startLabel;
@property (strong) UIButton *regenerate;
@property (strong) UIButton *close;
@property (strong) UIImageView *closeIconView;
@property (strong) NSLayoutConstraint *centerY;
@property (strong) NSLayoutConstraint *bottomLimit;
@property (strong) NSLayoutConstraint *panelHeight;
@property (strong) NSDictionary *context;
@property (strong) NSDictionary *plan;
@property (copy) NSString *requestId;
@property (copy) NSString *sessionId;
@property (copy) NSString *requirement;
@property (copy) NSString *question;
@property (assign) BOOL busy;
@end

@implementation ImpressTypesetOverlayController
- (instancetype)initWithHost:(UIViewController *)host aiService:(AIService *)service
             contextProvider:(ImpressTypesetContextProvider)provider applyHandler:(ImpressTypesetApplyHandler)applyHandler {
    if ((self = [super init])) {
        _host = host;
        _service = service;
        _provider = [provider copy];
        _apply = [applyHandler copy];
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(keyboardChanged:)
            name:UIKeyboardWillChangeFrameNotification object:nil];
    }
    return self;
}

- (void)dealloc {
    [[NSNotificationCenter defaultCenter] removeObserver:self];
    if (self.requestId) [self.service cancelRequest:self.requestId documentSessionId:self.sessionId];
}

- (void)show {
    if (self.overlay || !self.host.view.window) return;
    self.plan = nil;
    self.context = nil;
    self.question = nil;
    self.requirement = nil;
    self.busy = NO;
    self.overlay = [[UIView alloc] init];
    self.overlay.backgroundColor = [UIColor colorWithWhite:0 alpha:0.3];
    self.overlay.translatesAutoresizingMaskIntoConstraints = NO;
    self.overlay.accessibilityViewIsModal = YES;
    [self.host.view addSubview:self.overlay];
    [NSLayoutConstraint activateConstraints:@[
        [self.overlay.leadingAnchor constraintEqualToAnchor:self.host.view.leadingAnchor],
        [self.overlay.trailingAnchor constraintEqualToAnchor:self.host.view.trailingAnchor],
        [self.overlay.topAnchor constraintEqualToAnchor:self.host.view.topAnchor],
        [self.overlay.bottomAnchor constraintEqualToAnchor:self.host.view.bottomAnchor]]];
    self.panel = [[UIView alloc] init];
    self.panel.backgroundColor = UIColor.whiteColor;
    self.panel.layer.cornerRadius = 12;
    self.panel.translatesAutoresizingMaskIntoConstraints = NO;
    [self.overlay addSubview:self.panel];
    self.centerY = [self.panel.centerYAnchor constraintEqualToAnchor:self.overlay.centerYAnchor];
    self.centerY.priority = UILayoutPriorityDefaultHigh;
    self.bottomLimit = [self.panel.bottomAnchor constraintLessThanOrEqualToAnchor:self.overlay.safeAreaLayoutGuide.bottomAnchor constant:-16];
    NSLayoutConstraint *width = [self.panel.widthAnchor constraintEqualToConstant:335];
    width.priority = UILayoutPriorityDefaultHigh;
    self.panelHeight = [self.panel.heightAnchor constraintEqualToConstant:318.5];
    self.panelHeight.priority = UILayoutPriorityDefaultHigh;
    [NSLayoutConstraint activateConstraints:@[self.centerY, self.bottomLimit, width, self.panelHeight,
        [self.panel.centerXAnchor constraintEqualToAnchor:self.overlay.centerXAnchor],
        [self.panel.widthAnchor constraintLessThanOrEqualToAnchor:self.overlay.widthAnchor constant:-40],
        [self.panel.topAnchor constraintGreaterThanOrEqualToAnchor:self.overlay.safeAreaLayoutGuide.topAnchor constant:16]]];
    UILabel *title = [[UILabel alloc] init];
    title.text = @"AI 排版";
    title.font = [UIFont systemFontOfSize:20 weight:UIFontWeightMedium];
    title.textColor = [UIColor colorWithWhite:0.1 alpha:1];
    title.textAlignment = NSTextAlignmentCenter;
    title.translatesAutoresizingMaskIntoConstraints = NO;
    [self.panel addSubview:title];
    self.close = [UIButton buttonWithType:UIButtonTypeCustom];
    self.close.translatesAutoresizingMaskIntoConstraints = NO;
    UIImage *closeImage = [[UIImage imageNamed:@"PptTypesetCloseFigma"] imageWithRenderingMode:UIImageRenderingModeAlwaysOriginal];
    self.closeIconView = [[UIImageView alloc] initWithImage:closeImage];
    self.closeIconView.translatesAutoresizingMaskIntoConstraints = NO;
    self.closeIconView.contentMode = UIViewContentModeScaleAspectFit;
    self.closeIconView.userInteractionEnabled = NO;
    [self.close addSubview:self.closeIconView];
    self.close.accessibilityLabel = @"关闭";
    [self.close addTarget:self action:@selector(dismiss) forControlEvents:UIControlEventTouchUpInside];
    [self.panel addSubview:self.close];
    UIScrollView *scroll = [[UIScrollView alloc] init];
    scroll.translatesAutoresizingMaskIntoConstraints = NO;
    scroll.keyboardDismissMode = UIScrollViewKeyboardDismissModeInteractive;
    [self.panel addSubview:scroll];
    UIStackView *content = [[UIStackView alloc] init];
    content.axis = UILayoutConstraintAxisVertical;
    content.spacing = 8;
    content.translatesAutoresizingMaskIntoConstraints = NO;
    [scroll addSubview:content];
    self.input = [[UITextView alloc] init];
    self.input.font = [UIFont systemFontOfSize:16];
    self.input.textColor = [UIColor colorWithWhite:0.08 alpha:1];
    self.input.backgroundColor = UIColor.whiteColor;
    self.input.textContainerInset = UIEdgeInsetsMake(10, 10, 10, 10);
    self.input.textContainer.lineFragmentPadding = 0;
    self.input.layer.borderColor = [UIColor colorWithRed:0xcb/255.0 green:0xd1/255.0 blue:0xd7/255.0 alpha:1].CGColor;
    self.input.layer.borderWidth = 0.5;
    self.input.layer.cornerRadius = 12;
    self.input.delegate = self;
    self.input.accessibilityLabel = @"排版要求";
    [self.input.heightAnchor constraintEqualToConstant:163.5].active = YES;
    [content addArrangedSubview:self.input];
    self.inputPlaceholder = [[UILabel alloc] init];
    self.inputPlaceholder.text = @"请输入要修改的排版内容";
    self.inputPlaceholder.font = self.input.font;
    self.inputPlaceholder.textColor = [UIColor colorWithWhite:0.6 alpha:1];
    self.inputPlaceholder.translatesAutoresizingMaskIntoConstraints = NO;
    self.inputPlaceholder.userInteractionEnabled = NO;
    [self.input addSubview:self.inputPlaceholder];
    [NSLayoutConstraint activateConstraints:@[
        [self.inputPlaceholder.leadingAnchor constraintEqualToAnchor:self.input.leadingAnchor constant:10],
        [self.inputPlaceholder.topAnchor constraintEqualToAnchor:self.input.topAnchor constant:10],
        [self.inputPlaceholder.trailingAnchor constraintLessThanOrEqualToAnchor:self.input.trailingAnchor constant:-10]]];
    self.status = [[UILabel alloc] init];
    self.status.font = [UIFont systemFontOfSize:14];
    self.status.textColor = [UIColor colorWithWhite:0.4 alpha:1];
    self.status.numberOfLines = 0;
    self.status.text = @"";
    self.status.hidden = YES;
    [content addArrangedSubview:self.status];
    [content setCustomSpacing:8 afterView:self.input];
    [content setCustomSpacing:8 afterView:self.status];
    self.result = [[UILabel alloc] init];
    self.result.font = [UIFont systemFontOfSize:16];
    self.result.textColor = [UIColor colorWithWhite:0.08 alpha:1];
    self.result.numberOfLines = 0;
    self.result.hidden = YES;
    [content addArrangedSubview:self.result];
    self.start = [[ImpressTypesetGradientButton alloc] initWithFrame:CGRectZero];
    self.start.translatesAutoresizingMaskIntoConstraints = NO;
    UIImage *generateImage = [[UIImage imageNamed:@"PptTypesetGenerateFigma"] imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
    self.startIconView = [[UIImageView alloc] initWithImage:generateImage];
    self.startIconView.translatesAutoresizingMaskIntoConstraints = NO;
    self.startIconView.contentMode = UIViewContentModeScaleAspectFit;
    self.startIconView.tintColor = UIColor.whiteColor;
    self.startIconView.userInteractionEnabled = NO;
    self.startLabel = [[UILabel alloc] init];
    self.startLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.startLabel.font = [UIFont systemFontOfSize:16];
    self.startLabel.textColor = UIColor.whiteColor;
    self.startLabel.text = @"开始生成";
    UIStackView *startContent = [[UIStackView alloc] initWithArrangedSubviews:@[self.startIconView, self.startLabel]];
    startContent.axis = UILayoutConstraintAxisHorizontal;
    startContent.alignment = UIStackViewAlignmentCenter;
    startContent.spacing = 4;
    startContent.translatesAutoresizingMaskIntoConstraints = NO;
    startContent.userInteractionEnabled = NO;
    [self.start addSubview:startContent];
    [NSLayoutConstraint activateConstraints:@[
        [self.startIconView.widthAnchor constraintEqualToConstant:20],
        [self.startIconView.heightAnchor constraintEqualToConstant:20],
        [startContent.centerXAnchor constraintEqualToAnchor:self.start.centerXAnchor],
        [startContent.centerYAnchor constraintEqualToAnchor:self.start.centerYAnchor],
        [startContent.leadingAnchor constraintGreaterThanOrEqualToAnchor:self.start.leadingAnchor constant:8],
        [startContent.trailingAnchor constraintLessThanOrEqualToAnchor:self.start.trailingAnchor constant:-8]]];
    self.start.accessibilityLabel = @"开始生成";
    self.start.backgroundColor = UIColor.clearColor;
    self.start.layer.cornerRadius = 17.5;
    self.start.clipsToBounds = YES;
    [self.start addTarget:self action:@selector(startPressed) forControlEvents:UIControlEventTouchUpInside];
    self.regenerate = [UIButton buttonWithType:UIButtonTypeSystem];
    [self.regenerate setTitle:@"重新生成" forState:UIControlStateNormal];
    [self.regenerate setTitleColor:[UIColor colorWithWhite:0.08 alpha:1] forState:UIControlStateNormal];
    self.regenerate.titleLabel.font = [UIFont systemFontOfSize:16];
    self.regenerate.backgroundColor = [UIColor colorWithWhite:0.94 alpha:1];
    self.regenerate.layer.cornerRadius = 17.5;
    [self.regenerate addTarget:self action:@selector(generate) forControlEvents:UIControlEventTouchUpInside];
    self.regenerate.hidden = YES;
    UIStackView *buttons = [[UIStackView alloc] initWithArrangedSubviews:@[self.regenerate, self.start]];
    buttons.axis = UILayoutConstraintAxisHorizontal;
    buttons.distribution = UIStackViewDistributionFillEqually;
    buttons.alignment = UIStackViewAlignmentCenter;
    buttons.spacing = 8;
    buttons.translatesAutoresizingMaskIntoConstraints = NO;
    [self.panel addSubview:buttons];
    [NSLayoutConstraint activateConstraints:@[
        [title.topAnchor constraintEqualToAnchor:self.panel.topAnchor constant:16],
        [title.leadingAnchor constraintEqualToAnchor:self.panel.leadingAnchor constant:50],
        [title.trailingAnchor constraintEqualToAnchor:self.panel.trailingAnchor constant:-50],
        [title.heightAnchor constraintEqualToConstant:28],
        [self.close.trailingAnchor constraintEqualToAnchor:self.panel.trailingAnchor constant:-16],
        [self.close.centerYAnchor constraintEqualToAnchor:title.centerYAnchor],
        [self.close.widthAnchor constraintEqualToConstant:40], [self.close.heightAnchor constraintEqualToConstant:40],
        [self.closeIconView.centerXAnchor constraintEqualToAnchor:self.close.centerXAnchor],
        [self.closeIconView.centerYAnchor constraintEqualToAnchor:self.close.centerYAnchor],
        [self.closeIconView.widthAnchor constraintEqualToConstant:40],
        [self.closeIconView.heightAnchor constraintEqualToConstant:40],
        [scroll.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:28],
        [scroll.leadingAnchor constraintEqualToAnchor:self.panel.leadingAnchor constant:12],
        [scroll.trailingAnchor constraintEqualToAnchor:self.panel.trailingAnchor constant:-12],
        [scroll.bottomAnchor constraintEqualToAnchor:buttons.topAnchor],
        [content.leadingAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.leadingAnchor],
        [content.trailingAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.trailingAnchor],
        [content.topAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.topAnchor],
        [content.bottomAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.bottomAnchor],
        [content.widthAnchor constraintEqualToAnchor:scroll.frameLayoutGuide.widthAnchor],
        [buttons.leadingAnchor constraintEqualToAnchor:self.panel.leadingAnchor constant:12],
        [buttons.trailingAnchor constraintEqualToAnchor:self.panel.trailingAnchor constant:-12],
        [buttons.bottomAnchor constraintEqualToAnchor:self.panel.bottomAnchor constant:-12],
        [buttons.heightAnchor constraintEqualToConstant:59],
        [self.start.heightAnchor constraintEqualToConstant:35],
        [self.regenerate.heightAnchor constraintEqualToConstant:35]]];
    [self.panel layoutIfNeeded];
    [self updateControls];
    UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, title);
}

- (void)keyboardChanged:(NSNotification *)notification {
    if (!self.overlay) return;
    CGRect keyboard = [self.overlay convertRect:[notification.userInfo[UIKeyboardFrameEndUserInfoKey] CGRectValue] fromView:nil];
    CGRect overlap = CGRectIntersection(self.overlay.bounds, keyboard);
    CGFloat height = CGRectIsNull(overlap) ? 0 : overlap.size.height;
    self.centerY.constant = -height / 2;
    self.bottomLimit.constant = -16 - height;
    [UIView animateWithDuration:[notification.userInfo[UIKeyboardAnimationDurationUserInfoKey] doubleValue]
        animations:^{ [self.overlay layoutIfNeeded]; }];
}

- (void)updateControls {
    self.panelHeight.constant = self.plan ? 480 : 318.5;
    self.input.editable = !self.busy && !self.plan;
    self.inputPlaceholder.hidden = self.input.text.length > 0;
    self.start.enabled = !self.busy;
    self.regenerate.enabled = !self.busy;
    self.close.enabled = !self.busy || !self.plan;
    self.result.hidden = !self.plan;
    self.regenerate.hidden = !self.plan;
    self.startLabel.text = self.plan ? @"应用排版" : @"开始生成";
    self.start.accessibilityLabel = self.startLabel.text;
    [self.panel layoutIfNeeded];
    self.status.hidden = self.status.text.length == 0;
}

- (void)setStatusText:(NSString *)text {
    self.status.text = text ?: @"";
    self.status.hidden = self.status.text.length == 0;
}

- (BOOL)textView:(UITextView *)textView shouldChangeTextInRange:(NSRange)range replacementText:(NSString *)text {
    if (textView != self.input) return YES;
    NSUInteger length = textView.text.length - range.length + text.length;
    return length <= 2000;
}

- (void)textViewDidChange:(UITextView *)textView {
    if (textView == self.input) self.inputPlaceholder.hidden = textView.text.length > 0;
}

- (void)dismiss {
    if (self.requestId) [self.service cancelRequest:self.requestId documentSessionId:self.sessionId];
    self.requestId = nil;
    [self.overlay endEditing:YES];
    [self.overlay removeFromSuperview];
    self.overlay = nil;
    self.context = nil;
    self.plan = nil;
    self.busy = NO;
}

- (void)startPressed {
    if (self.busy) return;
    if (self.plan) [self applyPlan];
    else [self generate];
}

- (void)generate {
    if (self.busy || !self.overlay) return;
    NSString *input = [self.input.text stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
    if (!input.length) { [self setStatusText:@"请输入排版要求"]; return; }
    if (input.length > 2000) { [self setStatusText:@"排版要求不能超过2000字符"]; return; }
    self.requirement = self.question.length ? [NSString stringWithFormat:@"%@\n待确认问题：%@\n用户补充：%@", self.requirement, self.question, input] : input;
    self.question = nil;
    self.plan = nil;
    NSError *error = nil;
    self.context = self.provider(&error);
    if (!self.context) { [self setStatusText:error.localizedDescription ?: @"文档尚未准备好"]; [self updateControls]; return; }
    NSInteger part = [self.context[@"part"] integerValue];
    NSDictionary *slide = self.context[@"structure"][@"DocStructure"][@"Slides"][[NSString stringWithFormat:@"Slide %ld", (long)part]];
    if ([slide[@"TypesetVersion"] integerValue] < 2) { [self setStatusText:@"请先更新并重编 iOS 排版内核"]; [self updateControls]; return; }
    self.requestId = [@"impress-typeset-" stringByAppendingString:NSUUID.UUID.UUIDString];
    self.sessionId = self.context[@"sessionId"];
    self.busy = YES;
    [self setStatusText:[NSString stringWithFormat:@"正在生成第 %ld 页排版方案…", (long)part + 1]];
    [self.overlay endEditing:YES];
    [self updateControls];
    NSString *expected = self.requestId;
    __weak __typeof(self) weakSelf = self;
    [self.service startRequest:@{@"taskType":@"impress_typeset", @"requirement":self.requirement, @"slide":slide, @"modelMode":@"base"}
        requestId:expected documentSessionId:self.sessionId emit:^(NSString *type, NSString *requestId, NSString *session, NSDictionary *payload) {
        dispatch_async(dispatch_get_main_queue(), ^{
            __typeof(self) owner = weakSelf;
            if (!owner.overlay || ![owner.requestId isEqual:expected] || ![session isEqual:owner.sessionId]) return;
            if ([type isEqual:@"ai.error"]) {
                owner.busy = NO;
                [owner setStatusText:[payload[@"message"] isKindOfClass:NSString.class] ? payload[@"message"] : @"生成失败，请重试"];
                [owner updateControls];
            } else if ([type isEqual:@"ai.done"]) {
                NSError *parseError = nil;
                NSString *response = [payload[@"fullText"] isKindOfClass:NSString.class] ? payload[@"fullText"] : @"";
                NSDictionary *plan = [ImpressTypesetPlan parseResponse:response slide:slide error:&parseError];
                owner.busy = NO;
                NSString *status = plan[@"status"] ?: @"ready";
                if (!plan) [owner setStatusText:parseError.localizedDescription];
                else if (![status isEqual:@"ready"]) {
                    [owner setStatusText:plan[@"message"]];
                    if ([status isEqual:@"needs_clarification"]) { owner.question = plan[@"message"]; owner.input.text = @""; }
                } else {
                    owner.plan = plan;
                    owner.result.text = [ImpressTypesetPlan summary:plan slide:slide];
                    [owner setStatusText:[NSString stringWithFormat:@"仅应用于第 %ld 页，请确认排版方案", (long)part + 1]];
                }
                [owner updateControls];
            }
        });
    }];
}

- (void)applyPlan {
    if (!self.plan || self.busy) return;
    self.busy = YES;
    [self setStatusText:@"正在应用当前页排版…"];
    [self updateControls];
    NSString *expected = self.requestId;
    NSDictionary *transform = [ImpressTypesetPlan transform:self.plan part:[self.context[@"part"] integerValue] requestId:expected];
    __weak __typeof(self) weakSelf = self;
    self.apply(self.context, transform, expected, ^(BOOL success, NSString *message) {
        dispatch_async(dispatch_get_main_queue(), ^{
            __typeof(self) owner = weakSelf;
            if (!owner.overlay || ![owner.requestId isEqual:expected] || !owner.busy) return;
            owner.busy = NO;
            owner.plan = nil;
            if (success) {
                [owner setStatusText:@"已应用排版，可一次撤销本次修改"];
                owner.input.text = @"";
                owner.requirement = nil;
                owner.question = nil;
            } else [owner setStatusText:message ?: @"内核未确认应用成功，请检查文档后重新生成"];
            [owner updateControls];
        });
    });
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 15 * NSEC_PER_SEC), dispatch_get_main_queue(), ^{
        __typeof(self) owner = weakSelf;
        if (!owner.overlay || ![owner.requestId isEqual:expected] || !owner.busy) return;
        owner.busy = NO;
        owner.plan = nil;
        [owner setStatusText:@"未收到内核确认，请检查文档后重新生成"];
        [owner updateControls];
    });
}
@end
