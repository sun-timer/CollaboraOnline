#import "ImpressTypesetOverlayController.h"
#import "ImpressTypesetPlan.h"
#import "AIService.h"
#import "WriterAIComponents.h"

@interface ImpressTypesetOverlayController ()
@property (weak) UIViewController *host;
@property (strong) AIService *service;
@property (copy) ImpressTypesetContextProvider provider;
@property (copy) ImpressTypesetApplyHandler apply;
@property (strong) UIView *overlay;
@property (strong) UIView *panel;
@property (strong) UITextView *input;
@property (strong) UILabel *status;
@property (strong) UILabel *result;
@property (strong) UIButton *start;
@property (strong) UIButton *regenerate;
@property (strong) UIButton *close;
@property (strong) NSLayoutConstraint *centerY;
@property (strong) NSLayoutConstraint *bottomLimit;
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
    self.panel.layer.cornerRadius = 18;
    self.panel.translatesAutoresizingMaskIntoConstraints = NO;
    [self.overlay addSubview:self.panel];
    self.centerY = [self.panel.centerYAnchor constraintEqualToAnchor:self.overlay.safeAreaLayoutGuide.centerYAnchor];
    self.centerY.priority = UILayoutPriorityDefaultHigh;
    self.bottomLimit = [self.panel.bottomAnchor constraintLessThanOrEqualToAnchor:self.overlay.safeAreaLayoutGuide.bottomAnchor constant:-16];
    NSLayoutConstraint *width = [self.panel.widthAnchor constraintEqualToConstant:420];
    width.priority = UILayoutPriorityDefaultHigh;
    NSLayoutConstraint *height = [self.panel.heightAnchor constraintEqualToConstant:500];
    height.priority = UILayoutPriorityDefaultHigh;
    [NSLayoutConstraint activateConstraints:@[self.centerY, self.bottomLimit, width, height,
        [self.panel.centerXAnchor constraintEqualToAnchor:self.overlay.centerXAnchor],
        [self.panel.widthAnchor constraintLessThanOrEqualToAnchor:self.overlay.widthAnchor constant:-32],
        [self.panel.topAnchor constraintGreaterThanOrEqualToAnchor:self.overlay.safeAreaLayoutGuide.topAnchor constant:16]]];
    UILabel *title = [[UILabel alloc] init];
    title.text = @"AI 排版";
    title.font = [UIFont systemFontOfSize:20 weight:UIFontWeightMedium];
    title.textColor = [UIColor colorWithWhite:0.1 alpha:1];
    title.textAlignment = NSTextAlignmentCenter;
    title.translatesAutoresizingMaskIntoConstraints = NO;
    [self.panel addSubview:title];
    self.close = [WriterAICloseButton closeButtonWithTarget:self action:@selector(dismiss)];
    self.close.translatesAutoresizingMaskIntoConstraints = NO;
    [self.panel addSubview:self.close];
    UIScrollView *scroll = [[UIScrollView alloc] init];
    scroll.translatesAutoresizingMaskIntoConstraints = NO;
    scroll.keyboardDismissMode = UIScrollViewKeyboardDismissModeInteractive;
    [self.panel addSubview:scroll];
    UIStackView *content = [[UIStackView alloc] init];
    content.axis = UILayoutConstraintAxisVertical;
    content.spacing = 12;
    content.translatesAutoresizingMaskIntoConstraints = NO;
    [scroll addSubview:content];
    self.input = [[UITextView alloc] init];
    self.input.font = [UIFont systemFontOfSize:16];
    self.input.textColor = [UIColor colorWithWhite:0.08 alpha:1];
    self.input.backgroundColor = UIColor.whiteColor;
    self.input.layer.borderColor = [UIColor colorWithWhite:0.85 alpha:1].CGColor;
    self.input.layer.borderWidth = 1;
    self.input.layer.cornerRadius = 12;
    self.input.accessibilityLabel = @"排版要求";
    [self.input.heightAnchor constraintEqualToConstant:130].active = YES;
    [content addArrangedSubview:self.input];
    self.status = [[UILabel alloc] init];
    self.status.font = [UIFont systemFontOfSize:14];
    self.status.textColor = [UIColor colorWithWhite:0.4 alpha:1];
    self.status.numberOfLines = 0;
    self.status.text = @"请输入排版要求，例如缩小文字、正文缩小20%、让文字排得下。";
    [content addArrangedSubview:self.status];
    self.result = [[UILabel alloc] init];
    self.result.font = [UIFont systemFontOfSize:16];
    self.result.textColor = [UIColor colorWithWhite:0.08 alpha:1];
    self.result.numberOfLines = 0;
    self.result.hidden = YES;
    [content addArrangedSubview:self.result];
    self.start = [UIButton buttonWithType:UIButtonTypeSystem];
    [self.start setTitle:@"开始生成" forState:UIControlStateNormal];
    [self.start setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    self.start.backgroundColor = [UIColor colorWithRed:0.28 green:0.38 blue:0.95 alpha:1];
    self.start.layer.cornerRadius = 20;
    [self.start addTarget:self action:@selector(startPressed) forControlEvents:UIControlEventTouchUpInside];
    self.regenerate = [UIButton buttonWithType:UIButtonTypeSystem];
    [self.regenerate setTitle:@"重新生成" forState:UIControlStateNormal];
    [self.regenerate addTarget:self action:@selector(generate) forControlEvents:UIControlEventTouchUpInside];
    self.regenerate.hidden = YES;
    UIStackView *buttons = [[UIStackView alloc] initWithArrangedSubviews:@[self.regenerate, self.start]];
    buttons.axis = UILayoutConstraintAxisHorizontal;
    buttons.distribution = UIStackViewDistributionFillEqually;
    buttons.spacing = 10;
    buttons.translatesAutoresizingMaskIntoConstraints = NO;
    [self.panel addSubview:buttons];
    [NSLayoutConstraint activateConstraints:@[
        [title.topAnchor constraintEqualToAnchor:self.panel.topAnchor constant:18],
        [title.leadingAnchor constraintEqualToAnchor:self.panel.leadingAnchor constant:50],
        [title.trailingAnchor constraintEqualToAnchor:self.panel.trailingAnchor constant:-50],
        [title.heightAnchor constraintEqualToConstant:28],
        [self.close.trailingAnchor constraintEqualToAnchor:self.panel.trailingAnchor constant:-10],
        [self.close.centerYAnchor constraintEqualToAnchor:title.centerYAnchor],
        [self.close.widthAnchor constraintEqualToConstant:40], [self.close.heightAnchor constraintEqualToConstant:40],
        [scroll.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:16],
        [scroll.leadingAnchor constraintEqualToAnchor:self.panel.leadingAnchor constant:16],
        [scroll.trailingAnchor constraintEqualToAnchor:self.panel.trailingAnchor constant:-16],
        [scroll.bottomAnchor constraintEqualToAnchor:buttons.topAnchor constant:-12],
        [content.leadingAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.leadingAnchor],
        [content.trailingAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.trailingAnchor],
        [content.topAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.topAnchor],
        [content.bottomAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.bottomAnchor],
        [content.widthAnchor constraintEqualToAnchor:scroll.frameLayoutGuide.widthAnchor],
        [buttons.leadingAnchor constraintEqualToAnchor:self.panel.leadingAnchor constant:20],
        [buttons.trailingAnchor constraintEqualToAnchor:self.panel.trailingAnchor constant:-20],
        [buttons.bottomAnchor constraintEqualToAnchor:self.panel.bottomAnchor constant:-16],
        [buttons.heightAnchor constraintEqualToConstant:42]]];
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
    self.input.editable = !self.busy && !self.plan;
    self.start.enabled = !self.busy;
    self.regenerate.enabled = !self.busy;
    self.close.enabled = !self.busy || !self.plan;
    self.result.hidden = !self.plan;
    self.regenerate.hidden = !self.plan;
    [self.start setTitle:self.plan ? @"应用排版" : @"开始生成" forState:UIControlStateNormal];
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
    if (!input.length) { self.status.text = @"请输入排版要求"; return; }
    if (input.length > 2000) { self.status.text = @"排版要求不能超过2000字符"; return; }
    self.requirement = self.question.length ? [NSString stringWithFormat:@"%@\n待确认问题：%@\n用户补充：%@", self.requirement, self.question, input] : input;
    self.question = nil;
    self.plan = nil;
    NSError *error = nil;
    self.context = self.provider(&error);
    if (!self.context) { self.status.text = error.localizedDescription ?: @"文档尚未准备好"; [self updateControls]; return; }
    NSInteger part = [self.context[@"part"] integerValue];
    NSDictionary *slide = self.context[@"structure"][@"DocStructure"][@"Slides"][[NSString stringWithFormat:@"Slide %ld", (long)part]];
    if ([slide[@"TypesetVersion"] integerValue] < 2) { self.status.text = @"请先更新并重编 iOS 排版内核"; [self updateControls]; return; }
    self.requestId = [@"impress-typeset-" stringByAppendingString:NSUUID.UUID.UUIDString];
    self.sessionId = self.context[@"sessionId"];
    self.busy = YES;
    self.status.text = [NSString stringWithFormat:@"正在生成第 %ld 页排版方案…", (long)part + 1];
    [self.overlay endEditing:YES];
    [self updateControls];
    NSString *expected = self.requestId;
    __weak typeof(self) weakSelf = self;
    [self.service startRequest:@{@"taskType":@"impress_typeset", @"requirement":self.requirement, @"slide":slide, @"modelMode":@"base"}
        requestId:expected documentSessionId:self.sessionId emit:^(NSString *type, NSString *requestId, NSString *session, NSDictionary *payload) {
        dispatch_async(dispatch_get_main_queue(), ^{
            typeof(self) owner = weakSelf;
            if (!owner.overlay || ![owner.requestId isEqual:expected] || ![session isEqual:owner.sessionId]) return;
            if ([type isEqual:@"ai.error"]) {
                owner.busy = NO;
                owner.status.text = [payload[@"message"] isKindOfClass:NSString.class] ? payload[@"message"] : @"生成失败，请重试";
                [owner updateControls];
            } else if ([type isEqual:@"ai.done"]) {
                NSError *parseError = nil;
                NSString *response = [payload[@"fullText"] isKindOfClass:NSString.class] ? payload[@"fullText"] : @"";
                NSDictionary *plan = [ImpressTypesetPlan parseResponse:response slide:slide error:&parseError];
                owner.busy = NO;
                NSString *status = plan[@"status"] ?: @"ready";
                if (!plan) owner.status.text = parseError.localizedDescription;
                else if (![status isEqual:@"ready"]) {
                    owner.status.text = plan[@"message"];
                    if ([status isEqual:@"needs_clarification"]) { owner.question = plan[@"message"]; owner.input.text = @""; }
                } else {
                    owner.plan = plan;
                    owner.result.text = [ImpressTypesetPlan summary:plan slide:slide];
                    owner.status.text = [NSString stringWithFormat:@"仅应用于第 %ld 页，请确认排版方案", (long)part + 1];
                }
                [owner updateControls];
            }
        });
    }];
}

- (void)applyPlan {
    if (!self.plan || self.busy) return;
    self.busy = YES;
    self.status.text = @"正在应用当前页排版…";
    [self updateControls];
    NSString *expected = self.requestId;
    NSDictionary *transform = [ImpressTypesetPlan transform:self.plan part:[self.context[@"part"] integerValue] requestId:expected];
    __weak typeof(self) weakSelf = self;
    self.apply(self.context, transform, expected, ^(BOOL success, NSString *message) {
        dispatch_async(dispatch_get_main_queue(), ^{
            typeof(self) owner = weakSelf;
            if (!owner.overlay || ![owner.requestId isEqual:expected] || !owner.busy) return;
            owner.busy = NO;
            owner.plan = nil;
            if (success) {
                owner.status.text = @"已应用排版，可一次撤销本次修改";
                owner.input.text = @"";
                owner.requirement = nil;
                owner.question = nil;
            } else owner.status.text = message ?: @"内核未确认应用成功，请检查文档后重新生成";
            [owner updateControls];
        });
    });
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 15 * NSEC_PER_SEC), dispatch_get_main_queue(), ^{
        typeof(self) owner = weakSelf;
        if (!owner.overlay || ![owner.requestId isEqual:expected] || !owner.busy) return;
        owner.busy = NO;
        owner.plan = nil;
        owner.status.text = @"未收到内核确认，请检查文档后重新生成";
        [owner updateControls];
    });
}
@end
