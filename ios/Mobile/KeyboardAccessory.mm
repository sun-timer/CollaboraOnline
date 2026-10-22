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
@property (weak, nonatomic) UIToolbar *toolbar;
@property (strong, nonatomic) UIBarButtonItem *previousItem;
@property (strong, nonatomic) UIBarButtonItem *nextItem;
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
    UIToolbar *bar = [[UIToolbar alloc] initWithFrame:CGRectMake(0, 0, UIScreen.mainScreen.bounds.size.width, 44)];
    self.toolbar = bar;
    self.owner = input;
    self.previousItem = [[UIBarButtonItem alloc] initWithImage:[UIImage systemImageNamed:@"chevron.up"] style:UIBarButtonItemStylePlain target:self action:@selector(movePrevious)];
    self.nextItem = [[UIBarButtonItem alloc] initWithImage:[UIImage systemImageNamed:@"chevron.down"] style:UIBarButtonItemStylePlain target:self action:@selector(moveNext)];
    self.previousItem.accessibilityIdentifier = @"keyboard.previousInput";
    self.nextItem.accessibilityIdentifier = @"keyboard.nextInput";
    self.previousItem.accessibilityLabel = @"上一个输入框";
    self.nextItem.accessibilityLabel = @"下一个输入框";
    UIBarButtonItem *space = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemFlexibleSpace target:nil action:nil];
    UIBarButtonItem *done = [[UIBarButtonItem alloc] initWithImage:[UIImage systemImageNamed:@"checkmark"] style:UIBarButtonItemStyleDone target:self action:@selector(dismiss)];
    done.accessibilityIdentifier = @"keyboard.dismiss";
    done.accessibilityLabel = @"收起键盘";
    [bar setItems:@[self.previousItem, self.nextItem, space, done] animated:NO];
    if ([input isKindOfClass:UITextField.class]) ((UITextField *)input).inputAccessoryView = bar;
    else ((UITextView *)input).inputAccessoryView = bar;
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
    self.previousItem.enabled = index != NSNotFound && index > 0;
    self.nextItem.enabled = index != NSNotFound && index + 1 < inputs.count;
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
