// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AppToastPresenter.h"

@implementation AppToastPresenter

+ (void)showMessage:(NSString *)message from:(UIViewController *)host {
    if (message.length == 0 || host.view == nil) {
        return;
    }

    UIView *existing = [host.view viewWithTag:88001];
    [existing removeFromSuperview];

    UIView *toast = [[UIView alloc] init];
    toast.translatesAutoresizingMaskIntoConstraints = NO;
    toast.backgroundColor = [UIColor colorWithWhite:0 alpha:0.82];
    toast.layer.cornerRadius = 8;
    toast.clipsToBounds = YES;
    toast.alpha = 0;
    toast.tag = 88001;

    UILabel *label = [[UILabel alloc] init];
    label.translatesAutoresizingMaskIntoConstraints = NO;
    label.text = message;
    label.font = [UIFont systemFontOfSize:14];
    label.textColor = UIColor.whiteColor;
    label.textAlignment = NSTextAlignmentCenter;
    label.numberOfLines = 0;
    [toast addSubview:label];

    [host.view addSubview:toast];
    [NSLayoutConstraint activateConstraints:@[
        [toast.centerXAnchor constraintEqualToAnchor:host.view.centerXAnchor],
        [toast.bottomAnchor constraintEqualToAnchor:host.view.safeAreaLayoutGuide.bottomAnchor constant:-24],
        [toast.leadingAnchor constraintGreaterThanOrEqualToAnchor:host.view.leadingAnchor constant:32],
        [toast.trailingAnchor constraintLessThanOrEqualToAnchor:host.view.trailingAnchor constant:-32],
        [label.topAnchor constraintEqualToAnchor:toast.topAnchor constant:10],
        [label.bottomAnchor constraintEqualToAnchor:toast.bottomAnchor constant:-10],
        [label.leadingAnchor constraintEqualToAnchor:toast.leadingAnchor constant:16],
        [label.trailingAnchor constraintEqualToAnchor:toast.trailingAnchor constant:-16],
    ]];

    [UIView animateWithDuration:0.2 animations:^{
        toast.alpha = 1;
    } completion:^(BOOL finished) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.8 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            [UIView animateWithDuration:0.2 animations:^{
                toast.alpha = 0;
            } completion:^(BOOL finished2) {
                [toast removeFromSuperview];
            }];
        });
    }];
}

@end
