// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "CommentListSheetController.h"
#import "AI/WriterAIComponents.h"

@implementation WriterCommentListItem
@end

@interface CommentListSheetController () <UITableViewDataSource, UITableViewDelegate>
@property (nonatomic, copy) NSArray<WriterCommentListItem *> *comments;
@property (nonatomic, strong) UITableView *tableView;
@property (nonatomic, strong) UILabel *emptyLabel;
@end

@implementation CommentListSheetController

+ (instancetype)presentFrom:(UIViewController *)host
                   comments:(NSArray<WriterCommentListItem *> *)comments
                   delegate:(id<CommentListSheetControllerDelegate>)delegate
{
    CommentListSheetController *sheet = [[CommentListSheetController alloc] init];
    sheet.comments = comments ?: @[];
    sheet.actionDelegate = delegate;
    sheet.modalPresentationStyle = UIModalPresentationPageSheet;
    if (@available(iOS 15.0, *)) {
        UISheetPresentationController *presentation = sheet.sheetPresentationController;
        presentation.detents = @[
            [UISheetPresentationControllerDetent mediumDetent],
            [UISheetPresentationControllerDetent largeDetent],
        ];
        presentation.prefersGrabberVisible = YES;
    }
    [host presentViewController:sheet animated:YES completion:nil];
    return sheet;
}

- (void)viewDidLoad
{
    [super viewDidLoad];
    self.view.backgroundColor = UIColor.whiteColor;
    self.view.accessibilityIdentifier = @"writerCommentListSheet";

    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"批注";
    title.font = [UIFont systemFontOfSize:17 weight:UIFontWeightSemibold];
    title.textAlignment = NSTextAlignmentCenter;

    WriterAICloseButton *close = [WriterAICloseButton closeButtonWithTarget:self action:@selector(closeTapped)];

    self.tableView = [[UITableView alloc] initWithFrame:CGRectZero style:UITableViewStylePlain];
    self.tableView.translatesAutoresizingMaskIntoConstraints = NO;
    self.tableView.dataSource = self;
    self.tableView.delegate = self;
    self.tableView.separatorInset = UIEdgeInsetsMake(0, 16, 0, 16);
    self.tableView.rowHeight = UITableViewAutomaticDimension;
    self.tableView.estimatedRowHeight = 72.0;

    self.emptyLabel = [[UILabel alloc] init];
    self.emptyLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.emptyLabel.text = @"暂无批注";
    self.emptyLabel.textColor = [UIColor colorWithWhite:0.5 alpha:1];
    self.emptyLabel.font = [UIFont systemFontOfSize:15];
    self.emptyLabel.textAlignment = NSTextAlignmentCenter;
    self.emptyLabel.hidden = self.comments.count > 0;

    [self.view addSubview:title];
    [self.view addSubview:close];
    [self.view addSubview:self.tableView];
    [self.view addSubview:self.emptyLabel];

    UILayoutGuide *safe = self.view.safeAreaLayoutGuide;
    [NSLayoutConstraint activateConstraints:@[
        [title.topAnchor constraintEqualToAnchor:safe.topAnchor constant:16],
        [title.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [close.centerYAnchor constraintEqualToAnchor:title.centerYAnchor],
        [close.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-12],
        [self.tableView.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:12],
        [self.tableView.leadingAnchor constraintEqualToAnchor:safe.leadingAnchor],
        [self.tableView.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor],
        [self.tableView.bottomAnchor constraintEqualToAnchor:safe.bottomAnchor],
        [self.emptyLabel.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [self.emptyLabel.centerYAnchor constraintEqualToAnchor:self.view.centerYAnchor constant:24],
    ]];
}

- (void)closeTapped
{
    [self dismissViewControllerAnimated:YES completion:nil];
}

#pragma mark - UITableViewDataSource

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    return self.comments.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
    static NSString *const cellId = @"CommentCell";
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:cellId];
    if (cell == nil) {
        cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:cellId];
    }
    WriterCommentListItem *item = self.comments[indexPath.row];
    cell.textLabel.numberOfLines = 2;
    cell.textLabel.font = [UIFont systemFontOfSize:15 weight:UIFontWeightSemibold];
    cell.textLabel.text = item.author.length > 0 ? item.author : @"批注";
    cell.detailTextLabel.numberOfLines = 3;
    cell.detailTextLabel.textColor = [UIColor colorWithWhite:0.35 alpha:1];
    cell.detailTextLabel.font = [UIFont systemFontOfSize:14];
    cell.detailTextLabel.text = item.text.length > 0 ? item.text : @"(无内容)";
    cell.accessoryType = UITableViewCellAccessoryDisclosureIndicator;
    cell.selectionStyle = UITableViewCellSelectionStyleDefault;
    return cell;
}

#pragma mark - UITableViewDelegate

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
    WriterCommentListItem *item = self.comments[indexPath.row];
    [self dismissViewControllerAnimated:YES completion:^{
        [self.actionDelegate commentListSheetDidSelectCommentId:item.commentId];
    }];
}

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
