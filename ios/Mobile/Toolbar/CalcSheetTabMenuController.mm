// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "CalcSheetTabMenuController.h"

static UIColor *CalcSheetMenuColor(CGFloat red, CGFloat green, CGFloat blue, CGFloat alpha) {
    return [UIColor colorWithRed:red / 255.0 green:green / 255.0 blue:blue / 255.0 alpha:alpha];
}

static UIImage *CalcSheetMenuIcon(BOOL copyIcon) {
    UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:CGSizeMake(24, 24)];
    return [renderer imageWithActions:^(UIGraphicsImageRendererContext *rendererContext) {
        CGContextRef context = rendererContext.CGContext;
        CGContextSaveGState(context);
        CGContextScaleCTM(context, 0.5, 0.5);

        UIColor *dark = copyIcon
            ? CalcSheetMenuColor(0x10, 0x10, 0x10, 1)
            : CalcSheetMenuColor(0x33, 0x33, 0x33, 1);
        UIColor *green = CalcSheetMenuColor(0x3B, 0x80, 0x40, 1);

        if (copyIcon) {
            UIBezierPath *frontSheet = [UIBezierPath bezierPath];
            [frontSheet moveToPoint:CGPointMake(42.08, 5.8)];
            [frontSheet addLineToPoint:CGPointMake(11.01, 5.8)];
            [frontSheet addCurveToPoint:CGPointMake(9.28, 7.52)
                          controlPoint1:CGPointMake(10.05, 5.8)
                          controlPoint2:CGPointMake(9.28, 6.57)];
            [frontSheet addLineToPoint:CGPointMake(9.28, 35.14)];
            [frontSheet addCurveToPoint:CGPointMake(11.01, 36.87)
                          controlPoint1:CGPointMake(9.28, 36.1)
                          controlPoint2:CGPointMake(10.05, 36.87)];
            [frontSheet addLineToPoint:CGPointMake(42.08, 36.87)];
            [frontSheet addCurveToPoint:CGPointMake(43.81, 35.14)
                          controlPoint1:CGPointMake(43.04, 36.87)
                          controlPoint2:CGPointMake(43.81, 36.1)];
            [frontSheet addLineToPoint:CGPointMake(43.81, 7.52)];
            [frontSheet addCurveToPoint:CGPointMake(42.08, 5.8)
                          controlPoint1:CGPointMake(43.81, 6.57)
                          controlPoint2:CGPointMake(43.04, 5.8)];
            [frontSheet closePath];
            frontSheet.lineWidth = 2.0;
            [dark setStroke];
            [frontSheet stroke];

            UIBezierPath *grid = [UIBezierPath bezierPath];
            [grid moveToPoint:CGPointMake(9.28, 16.15)];
            [grid addLineToPoint:CGPointMake(43.81, 16.15)];
            [grid moveToPoint:CGPointMake(20.93, 16.15)];
            [grid addLineToPoint:CGPointMake(20.93, 36.87)];
            [grid moveToPoint:CGPointMake(32.16, 16.15)];
            [grid addLineToPoint:CGPointMake(32.16, 36.87)];
            [grid moveToPoint:CGPointMake(9.28, 26.51)];
            [grid addLineToPoint:CGPointMake(43.81, 26.51)];
            grid.lineWidth = 2.0;
            grid.lineCapStyle = kCGLineCapRound;
            [dark setStroke];
            [grid stroke];

            UIBezierPath *backSheet = [UIBezierPath bezierPath];
            [backSheet moveToPoint:CGPointMake(36.75, 42.2)];
            [backSheet addLineToPoint:CGPointMake(5.67, 42.2)];
            [backSheet addCurveToPoint:CGPointMake(3.95, 40.48)
                         controlPoint1:CGPointMake(4.72, 42.2)
                         controlPoint2:CGPointMake(3.95, 41.43)];
            [backSheet addLineToPoint:CGPointMake(3.95, 12.86)];
            backSheet.lineWidth = 2.0;
            backSheet.lineCapStyle = kCGLineCapRound;
            [green setStroke];
            [backSheet stroke];
        } else {
            UIBezierPath *sheet = [UIBezierPath bezierPathWithRect:CGRectMake(7, 7, 34, 34)];
            sheet.lineWidth = 2.0;
            [dark setStroke];
            [sheet stroke];

            UIBezierPath *grid = [UIBezierPath bezierPath];
            [grid moveToPoint:CGPointMake(7, 30)];
            [grid addLineToPoint:CGPointMake(43, 30)];
            [grid moveToPoint:CGPointMake(31, 30)];
            [grid addLineToPoint:CGPointMake(31, 43)];
            [grid moveToPoint:CGPointMake(18, 30)];
            [grid addLineToPoint:CGPointMake(18, 43)];
            grid.lineWidth = 2.0;
            grid.lineCapStyle = kCGLineCapRound;
            [dark setStroke];
            [grid stroke];

            UIBezierPath *underline = [UIBezierPath bezierPath];
            [underline moveToPoint:CGPointMake(16, 25)];
            [underline addLineToPoint:CGPointMake(32, 25)];
            underline.lineWidth = 2.0;
            underline.lineCapStyle = kCGLineCapRound;
            [green setStroke];
            [underline stroke];

            UIBezierPath *pencil = [UIBezierPath bezierPath];
            [pencil moveToPoint:CGPointMake(17.78, 18.21)];
            [pencil addLineToPoint:CGPointMake(17.78, 21.44)];
            [pencil addLineToPoint:CGPointMake(21.03, 21.44)];
            [pencil addLineToPoint:CGPointMake(30.22, 12.25)];
            [pencil addLineToPoint:CGPointMake(26.98, 9)];
            [pencil closePath];
            pencil.lineWidth = 2.0;
            [green setStroke];
            [pencil stroke];
        }

        CGContextRestoreGState(context);
    }];
}

@interface CalcSheetTabMenuController () <UIGestureRecognizerDelegate>
@property (assign, nonatomic, readwrite) NSInteger tabIndex;
@property (copy, nonatomic, readwrite) NSString *sheetName;
@property (assign, nonatomic, readwrite, getter=isProtected) BOOL protectedSheet;
@property (assign, nonatomic, readwrite) BOOL canDelete;
@property (assign, nonatomic, readwrite) CGRect anchorRect;
@property (copy, nonatomic) CalcSheetTabRenameHandler onRename;
@property (copy, nonatomic) CalcSheetTabCopyHandler onCopy;
@property (copy, nonatomic) CalcSheetTabDeleteHandler onDelete;
@property (weak, nonatomic) UIView *anchorSourceView;
@property (strong, nonatomic) UIView *menuCard;
@property (strong, nonatomic) UIView *separator;
@property (strong, nonatomic) UIButton *renameButton;
@property (strong, nonatomic) NSLayoutConstraint *menuLeading;
@property (strong, nonatomic) NSLayoutConstraint *menuTop;
@property (strong, nonatomic) NSLayoutConstraint *menuHeight;
@property (assign, nonatomic) BOOL finishing;
@end

@implementation CalcSheetTabMenuController

- (instancetype)initWithTabIndex:(NSInteger)tabIndex
                       sheetName:(NSString *)sheetName
                     isProtected:(BOOL)isProtected
                        canDelete:(BOOL)canDelete
                       anchorRect:(CGRect)anchorRect
                         onRename:(CalcSheetTabRenameHandler)onRename
                           onCopy:(CalcSheetTabCopyHandler)onCopy
                         onDelete:(CalcSheetTabDeleteHandler)onDelete {
    self = [super initWithNibName:nil bundle:nil];
    if (self) {
        _tabIndex = tabIndex;
        _sheetName = [sheetName copy];
        _protectedSheet = isProtected;
        _canDelete = canDelete;
        _anchorRect = anchorRect;
        _onRename = [onRename copy];
        _onCopy = [onCopy copy];
        _onDelete = [onDelete copy];
        self.modalPresentationStyle = UIModalPresentationOverFullScreen;
        self.modalTransitionStyle = UIModalTransitionStyleCrossDissolve;
    }
    return self;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = UIColor.clearColor;
    self.view.accessibilityIdentifier = @"calcSheetTabMenu";

    UITapGestureRecognizer *outsideTap = [[UITapGestureRecognizer alloc] initWithTarget:self
                                                                                  action:@selector(dismissMenu)];
    outsideTap.delegate = self;
    outsideTap.cancelsTouchesInView = NO;
    [self.view addGestureRecognizer:outsideTap];

    self.menuCard = [[UIView alloc] init];
    self.menuCard.translatesAutoresizingMaskIntoConstraints = NO;
    self.menuCard.backgroundColor = UIColor.whiteColor;
    self.menuCard.layer.cornerRadius = 12.0;
    self.menuCard.layer.shadowColor = UIColor.blackColor.CGColor;
    self.menuCard.layer.shadowOpacity = 0.14;
    self.menuCard.layer.shadowRadius = 12.0;
    self.menuCard.layer.shadowOffset = CGSizeMake(0, 5);
    self.menuCard.accessibilityIdentifier = @"calcSheetTabMenu.card";
    [self.view addSubview:self.menuCard];

    self.renameButton = [self actionButtonWithTitle:@"重命名"
                                              icon:CalcSheetMenuIcon(NO)
                                           action:@selector(performRename)];
    self.renameButton.accessibilityIdentifier = @"calcSheetTabMenu.rename";
    self.renameButton.hidden = self.isProtected;
    [self.menuCard addSubview:self.renameButton];

    self.separator = [[UIView alloc] init];
    self.separator.translatesAutoresizingMaskIntoConstraints = NO;
    self.separator.backgroundColor = CalcSheetMenuColor(0xE5, 0xE5, 0xE5, 1);
    self.separator.hidden = self.isProtected;
    [self.menuCard addSubview:self.separator];

    UIButton *copyButton = [self actionButtonWithTitle:@"复制"
                                                  icon:CalcSheetMenuIcon(YES)
                                               action:@selector(performCopy)];
    copyButton.accessibilityIdentifier = @"calcSheetTabMenu.copy";
    [self.menuCard addSubview:copyButton];

    self.menuLeading = [self.menuCard.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor];
    self.menuTop = [self.menuCard.topAnchor constraintEqualToAnchor:self.view.topAnchor];
    self.menuHeight = [self.menuCard.heightAnchor constraintEqualToConstant:self.isProtected ? 56.0 : 112.0];

    [NSLayoutConstraint activateConstraints:@[
        self.menuLeading,
        self.menuTop,
        [self.menuCard.widthAnchor constraintEqualToConstant:208.0],
        self.menuHeight,

        [self.renameButton.topAnchor constraintEqualToAnchor:self.menuCard.topAnchor],
        [self.renameButton.leadingAnchor constraintEqualToAnchor:self.menuCard.leadingAnchor],
        [self.renameButton.trailingAnchor constraintEqualToAnchor:self.menuCard.trailingAnchor],
        [self.renameButton.heightAnchor constraintEqualToConstant:56.0],

        [self.separator.leadingAnchor constraintEqualToAnchor:self.menuCard.leadingAnchor constant:12.0],
        [self.separator.trailingAnchor constraintEqualToAnchor:self.menuCard.trailingAnchor constant:-12.0],
        [self.separator.topAnchor constraintEqualToAnchor:self.menuCard.topAnchor constant:55.5],
        [self.separator.heightAnchor constraintEqualToConstant:0.5],

        [copyButton.leadingAnchor constraintEqualToAnchor:self.menuCard.leadingAnchor],
        [copyButton.trailingAnchor constraintEqualToAnchor:self.menuCard.trailingAnchor],
        [copyButton.bottomAnchor constraintEqualToAnchor:self.menuCard.bottomAnchor],
        [copyButton.heightAnchor constraintEqualToConstant:56.0],
    ]];

    self.menuCard.layer.shadowPath = [UIBezierPath bezierPathWithRoundedRect:CGRectMake(0, 0, 208, self.isProtected ? 56 : 112)
                                                                 cornerRadius:12].CGPath;
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    [self updateMenuPosition];
    self.menuCard.layer.shadowPath = [UIBezierPath bezierPathWithRoundedRect:self.menuCard.bounds
                                                                 cornerRadius:self.menuCard.layer.cornerRadius].CGPath;
}

- (UIButton *)actionButtonWithTitle:(NSString *)title icon:(UIImage *)icon action:(SEL)action {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    button.backgroundColor = UIColor.clearColor;
    button.adjustsImageWhenHighlighted = NO;
    button.accessibilityLabel = title;
    [button addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];

    UIImageView *iconView = [[UIImageView alloc] initWithImage:icon];
    iconView.translatesAutoresizingMaskIntoConstraints = NO;
    iconView.contentMode = UIViewContentModeScaleAspectFit;
    iconView.userInteractionEnabled = NO;
    [button addSubview:iconView];

    UILabel *titleLabel = [[UILabel alloc] init];
    titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    titleLabel.text = title;
    titleLabel.font = [UIFont systemFontOfSize:16 weight:UIFontWeightRegular];
    titleLabel.textColor = CalcSheetMenuColor(0x1F, 0x1F, 0x1F, 1);
    titleLabel.userInteractionEnabled = NO;
    [button addSubview:titleLabel];

    [NSLayoutConstraint activateConstraints:@[
        [iconView.leadingAnchor constraintEqualToAnchor:button.leadingAnchor constant:22.0],
        [iconView.centerYAnchor constraintEqualToAnchor:button.centerYAnchor],
        [iconView.widthAnchor constraintEqualToConstant:24.0],
        [iconView.heightAnchor constraintEqualToConstant:24.0],
        [titleLabel.leadingAnchor constraintEqualToAnchor:iconView.trailingAnchor constant:10.0],
        [titleLabel.centerYAnchor constraintEqualToAnchor:button.centerYAnchor],
        [titleLabel.trailingAnchor constraintLessThanOrEqualToAnchor:button.trailingAnchor constant:-12.0],
    ]];
    return button;
}

- (void)presentFromViewController:(UIViewController *)host
                       sourceView:(UIView *)sourceView
                         animated:(BOOL)animated {
    self.anchorSourceView = sourceView;
    self.modalPresentationStyle = UIModalPresentationOverFullScreen;
    [host presentViewController:self animated:animated completion:nil];
}

- (void)updateMenuPosition {
    if (!self.anchorSourceView || !self.menuCard) {
        return;
    }

    CGRect anchor = [self.anchorSourceView convertRect:self.anchorRect toView:self.view];
    UIEdgeInsets safeInsets = self.view.safeAreaInsets;
    CGFloat margin = 8.0;
    CGFloat width = 208.0;
    CGFloat height = self.isProtected ? 56.0 : 112.0;
    CGFloat minX = safeInsets.left + margin;
    CGFloat maxX = MAX(minX, CGRectGetWidth(self.view.bounds) - safeInsets.right - margin - width);
    CGFloat minY = safeInsets.top + margin;
    CGFloat maxY = MAX(minY, CGRectGetHeight(self.view.bounds) - safeInsets.bottom - margin - height);

    CGFloat left = CGRectGetMidX(anchor) - width / 2.0;
    left = MIN(MAX(left, minX), maxX);

    CGFloat top = CGRectGetMinY(anchor) - height - 8.0;
    if (top < minY) {
        top = CGRectGetMaxY(anchor) + 8.0;
    }
    top = MIN(MAX(top, minY), maxY);

    self.menuLeading.constant = left;
    self.menuTop.constant = top;
}

- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer shouldReceiveTouch:(UITouch *)touch {
    CGPoint point = [touch locationInView:self.view];
    return !CGRectContainsPoint(self.menuCard.frame, point);
}

- (void)dismissMenu {
    if (self.finishing) {
        return;
    }
    self.finishing = YES;
    [self dismissViewControllerAnimated:YES completion:nil];
}

- (void)dismissThenPerform:(dispatch_block_t)operation {
    if (self.finishing) {
        return;
    }
    self.finishing = YES;
    if (self.presentingViewController) {
        [self dismissViewControllerAnimated:YES completion:operation];
    } else if (operation) {
        operation();
    }
}

- (void)performRename {
    if (self.isProtected) {
        return;
    }
    CalcSheetTabRenameHandler handler = self.onRename;
    NSInteger tabIndex = self.tabIndex;
    NSString *sheetName = self.sheetName;
    [self dismissThenPerform:^{ if (handler) handler(tabIndex, sheetName); }];
}

- (void)performCopy {
    CalcSheetTabCopyHandler handler = self.onCopy;
    NSInteger tabIndex = self.tabIndex;
    [self dismissThenPerform:^{ if (handler) handler(tabIndex); }];
}

- (void)performDelete {
    if (self.isProtected) {
        return;
    }
    CalcSheetTabDeleteHandler handler = self.onDelete;
    NSInteger tabIndex = self.tabIndex;
    NSString *sheetName = self.sheetName;
    [self dismissThenPerform:^{ if (handler) handler(tabIndex, sheetName); }];
}

@end
