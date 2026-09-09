// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "HTMLAssetViewController.h"

#import "AppChromeHelper.h"

#import <WebKit/WebKit.h>

@interface HTMLAssetViewController ()
@property (copy, nonatomic) NSString *pageTitle;
@property (copy, nonatomic) NSString *resourceName;
@property (copy, nonatomic) NSString *resourceExtension;
@property (strong, nonatomic) WKWebView *webView;
@end

@implementation HTMLAssetViewController

- (instancetype)initWithTitle:(NSString *)title resourceName:(NSString *)resourceName resourceExtension:(NSString *)resourceExtension {
    self = [super initWithNibName:nil bundle:nil];
    if (self) {
        _pageTitle = [title copy];
        _resourceName = [resourceName copy];
        _resourceExtension = [resourceExtension copy];
    }
    return self;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    [AppChromeHelper applySecondaryPageBackground:self.view];

    UIButton *back = [AppChromeHelper secondaryBackButtonWithTarget:self action:@selector(close)];
    [self.view addSubview:back];

    UILabel *titleLabel = [[UILabel alloc] init];
    titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    titleLabel.text = self.pageTitle;
    titleLabel.font = [UIFont boldSystemFontOfSize:20];
    titleLabel.textColor = [UIColor colorWithWhite:0.06 alpha:1];
    [self.view addSubview:titleLabel];

    self.webView = [[WKWebView alloc] initWithFrame:CGRectZero];
    self.webView.translatesAutoresizingMaskIntoConstraints = NO;
    [self.view addSubview:self.webView];

    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:8],
        [back.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:12],
        [titleLabel.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [titleLabel.leadingAnchor constraintEqualToAnchor:back.trailingAnchor constant:8],
        [self.webView.topAnchor constraintEqualToAnchor:back.bottomAnchor constant:12],
        [self.webView.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.webView.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [self.webView.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
    ]];

    NSURL *url = [[NSBundle mainBundle] URLForResource:self.resourceName withExtension:self.resourceExtension];
    if (url != nil) {
        if ([self.resourceExtension isEqualToString:@"txt"]) {
            NSString *text = [NSString stringWithContentsOfURL:url encoding:NSUTF8StringEncoding error:nil];
            NSString *html = [NSString stringWithFormat:@"<html><body style='font-family:-apple-system;padding:16px;white-space:pre-wrap;'>%@</body></html>", text ?: @""];
            [self.webView loadHTMLString:html baseURL:nil];
        } else {
            [self.webView loadFileURL:url allowingReadAccessToURL:[url URLByDeletingLastPathComponent]];
        }
        return;
    }
    NSString *fallback = [NSString stringWithFormat:@"<html><body style='font-family:-apple-system;padding:16px;'><h2>%@</h2><p>内容资源暂未打包。</p></body></html>", self.pageTitle];
    [self.webView loadHTMLString:fallback baseURL:nil];
}

- (void)close {
    if (self.navigationController.viewControllers.count > 1) {
        [self.navigationController popViewControllerAnimated:YES];
    } else {
        [self dismissViewControllerAnimated:YES completion:nil];
    }
}

@end
