/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "KeyboardAccessory.h"
#import <objc/runtime.h>

static const void *KeyboardAccessoryAssociationKey = &KeyboardAccessoryAssociationKey;

@interface KeyboardAccessory ()
@property (weak, nonatomic) UIView<UITextInput> *owner;
@property (strong, nonatomic) UIButton *previousButton;
@property (strong, nonatomic) UIButton *nextButton;
@end

@implementation KeyboardAccessory

+ (void)installOnInput:(UIView<UITextInput> *)input {
    if (![input isKindOfClass:UITextField.class] && ![input isKindOfClass:UITextView.class]) return;
    if (objc_getAssociatedObject(input, KeyboardAccessoryAssociationKey)) return;
    KeyboardAccessory *accessory = [KeyboardAccessory new];
    objc_setAssociatedObject(input, KeyboardAccessoryAssociationKey, accessory, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    [accessory configure:input];
}

- (void)configure:(UIView<UITextInput> *)input {
    // 用户于 2026-10-22 确认：此键盘栏必须保持为单个全宽白色胶囊，左侧灰色
    // 上下箭头、右侧黑色勾号；前后箭头切换输入框，勾号收起键盘。除修复明确
    // bug 外，禁止改成系统 Toolbar、分组按钮、玻璃效果或蓝色按钮；避免 iOS 26
    // 自动重排 UIBarButtonItem。按钮使用 UIButtonTypeCustom，不使用系统按钮外观。
    UIView *bar = [[UIView alloc] initWithFrame:CGRectMake(0, 0, UIScreen.mainScreen.bounds.size.width, 64)];
    bar.autoresizingMask = UIViewAutoresizingFlexibleWidth;
    bar.backgroundColor = UIColor.clearColor;
    bar.opaque = NO;
    self.owner = input;

    UIView *capsule = [[UIView alloc] initWithFrame:CGRectZero];
    capsule.translatesAutoresizingMaskIntoConstraints = NO;
    capsule.backgroundColor = UIColor.whiteColor;
    capsule.layer.cornerRadius = 24.0;
    capsule.layer.borderWidth = 1.0;
    capsule.layer.borderColor = [UIColor colorWithWhite:0.0 alpha:0.08].CGColor;
    capsule.layer.shadowColor = UIColor.blackColor.CGColor;
    capsule.layer.shadowOpacity = 0.08;
    capsule.layer.shadowRadius = 8.0;
    capsule.layer.shadowOffset = CGSizeMake(0, 2);
    [bar addSubview:capsule];

    UIImageSymbolConfiguration *chevronConfiguration = [UIImageSymbolConfiguration configurationWithPointSize:20.0 weight:UIImageSymbolWeightRegular];
    self.previousButton = [UIButton buttonWithType:UIButtonTypeCustom];
    self.previousButton.translatesAutoresizingMaskIntoConstraints = NO;
    [self.previousButton setImage:[[UIImage systemImageNamed:@"chevron.up"] imageWithConfiguration:chevronConfiguration] forState:UIControlStateNormal];
    self.previousButton.tintColor = [UIColor colorWithWhite:0.42 alpha:1.0];
    [self.previousButton addTarget:self action:@selector(movePrevious) forControlEvents:UIControlEventTouchUpInside];
    self.previousButton.accessibilityIdentifier = @"keyboard.previousInput";
    self.previousButton.accessibilityLabel = @"上一个输入框";

    self.nextButton = [UIButton buttonWithType:UIButtonTypeCustom];
    self.nextButton.translatesAutoresizingMaskIntoConstraints = NO;
    [self.nextButton setImage:[[UIImage systemImageNamed:@"chevron.down"] imageWithConfiguration:chevronConfiguration] forState:UIControlStateNormal];
    self.nextButton.tintColor = [UIColor colorWithWhite:0.42 alpha:1.0];
    [self.nextButton addTarget:self action:@selector(moveNext) forControlEvents:UIControlEventTouchUpInside];
    self.nextButton.accessibilityIdentifier = @"keyboard.nextInput";
    self.nextButton.accessibilityLabel = @"下一个输入框";

    UIButton *done = [UIButton buttonWithType:UIButtonTypeCustom];
    done.translatesAutoresizingMaskIntoConstraints = NO;
    UIImageSymbolConfiguration *checkConfiguration = [UIImageSymbolConfiguration configurationWithPointSize:22.0 weight:UIImageSymbolWeightRegular];
    [done setImage:[[UIImage systemImageNamed:@"checkmark"] imageWithConfiguration:checkConfiguration] forState:UIControlStateNormal];
    done.tintColor = UIColor.blackColor;
    [done addTarget:self action:@selector(dismiss) forControlEvents:UIControlEventTouchUpInside];
    done.accessibilityIdentifier = @"keyboard.dismiss";
    done.accessibilityLabel = @"收起键盘";

    [capsule addSubview:self.previousButton];
    [capsule addSubview:self.nextButton];
    [capsule addSubview:done];
    [NSLayoutConstraint activateConstraints:@[
        [capsule.topAnchor constraintEqualToAnchor:bar.topAnchor constant:8.0],
        [capsule.bottomAnchor constraintEqualToAnchor:bar.bottomAnchor constant:-8.0],
        [capsule.leadingAnchor constraintEqualToAnchor:bar.safeAreaLayoutGuide.leadingAnchor constant:16.0],
        [capsule.trailingAnchor constraintEqualToAnchor:bar.safeAreaLayoutGuide.trailingAnchor constant:-16.0],
        [self.previousButton.leadingAnchor constraintEqualToAnchor:capsule.leadingAnchor constant:12.0],
        [self.previousButton.centerYAnchor constraintEqualToAnchor:capsule.centerYAnchor],
        [self.previousButton.widthAnchor constraintEqualToConstant:44.0],
        [self.previousButton.heightAnchor constraintEqualToConstant:44.0],
        [self.nextButton.leadingAnchor constraintEqualToAnchor:self.previousButton.trailingAnchor constant:12.0],
        [self.nextButton.centerYAnchor constraintEqualToAnchor:capsule.centerYAnchor],
        [self.nextButton.widthAnchor constraintEqualToConstant:44.0],
        [self.nextButton.heightAnchor constraintEqualToConstant:44.0],
        [done.trailingAnchor constraintEqualToAnchor:capsule.trailingAnchor constant:-8.0],
        [done.centerYAnchor constraintEqualToAnchor:capsule.centerYAnchor],
        [done.widthAnchor constraintEqualToConstant:44.0],
        [done.heightAnchor constraintEqualToConstant:44.0],
    ]];
    if ([input isKindOfClass:UITextField.class]) ((UITextField *)input).inputAccessoryView = bar;
    else ((UITextView *)input).inputAccessoryView = bar;
    [self updateItems];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(editingChanged:) name:UITextFieldTextDidBeginEditingNotification object:input];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(editingChanged:) name:UITextViewTextDidBeginEditingNotification object:input];
}

- (void)editingChanged:(NSNotification *)note {
    [self updateItems];
}

- (NSArray<UIView<UITextInput> *> *)visibleInputs {
    UIView<UITextInput> *owner = self.owner;
    UIWindow *window = owner.window;
    UIResponder *responder = owner;
    UIViewController *controller = nil;
    while (responder && !controller) {
        if ([responder isKindOfClass:UIViewController.class]) controller = (UIViewController *)responder;
        responder = responder.nextResponder;
    }
    UIView *root = controller.view ?: window.rootViewController.view ?: window;
    NSMutableArray *inputs = [NSMutableArray array];
    [self collectInputsIn:root into:inputs];
    [inputs sortUsingComparator:^NSComparisonResult(UIView *a, UIView *b) {
        CGPoint pa = [a.superview convertPoint:a.frame.origin toView:root];
        CGPoint pb = [b.superview convertPoint:b.frame.origin toView:root];
        if (fabs(pa.y - pb.y) > 2) return pa.y < pb.y ? NSOrderedAscending : NSOrderedDescending;
        if (fabs(pa.x - pb.x) > 2) return pa.x < pb.x ? NSOrderedAscending : NSOrderedDescending;
        return NSOrderedSame;
    }];
    return inputs;
}

- (void)collectInputsIn:(UIView *)view into:(NSMutableArray *)result {
    if (view.hidden || view.alpha < 0.05 || !view.userInteractionEnabled) return;
    BOOL editable = NO;
    if ([view isKindOfClass:UITextField.class]) editable = ((UITextField *)view).enabled;
    else if ([view isKindOfClass:UITextView.class]) editable = ((UITextView *)view).editable;
    if (editable && view.window == self.owner.window) [result addObject:view];
    for (UIView *child in view.subviews) [self collectInputsIn:child into:result];
}

- (void)updateItems {
    NSArray *inputs = [self visibleInputs];
    NSUInteger index = [inputs indexOfObjectIdenticalTo:self.owner];
    self.previousButton.enabled = index != NSNotFound && index > 0;
    self.nextButton.enabled = index != NSNotFound && index + 1 < inputs.count;
    self.previousButton.alpha = self.previousButton.enabled ? 1.0 : 0.35;
    self.nextButton.alpha = self.nextButton.enabled ? 1.0 : 0.35;
}
- (void)movePrevious { [self move:-1]; }
- (void)moveNext { [self move:1]; }
- (void)move:(NSInteger)delta {
    NSArray *inputs = [self visibleInputs];
    NSUInteger index = [inputs indexOfObjectIdenticalTo:self.owner];
    NSInteger target = (NSInteger)index + delta;
    if (index != NSNotFound && target >= 0 && target < (NSInteger)inputs.count) {
        UIView<UITextInput> *targetInput = inputs[(NSUInteger)target];
        [self scrollInputIntoView:targetInput];
        [targetInput becomeFirstResponder];
    }
    [self updateItems];
}
- (void)scrollInputIntoView:(UIView *)input {
    UIView *ancestor = input.superview;
    while (ancestor) {
        if ([ancestor isKindOfClass:UIScrollView.class]) {
            CGRect rect = [input convertRect:input.bounds toView:ancestor];
            [(UIScrollView *)ancestor scrollRectToVisible:rect animated:NO];
        }
        ancestor = ancestor.superview;
    }
}
- (void)dismiss { [self.owner resignFirstResponder]; }
- (void)dealloc { [[NSNotificationCenter defaultCenter] removeObserver:self]; }
@end
