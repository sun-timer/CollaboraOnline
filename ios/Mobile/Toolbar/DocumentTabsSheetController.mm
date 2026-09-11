// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "DocumentTabsSheetController.h"
#import "AI/WriterAIComponents.h"
#import "RecentDocumentsStore.h"

@interface DocumentTabsSheetController () <UITableViewDataSource, UITableViewDelegate>
@property (nonatomic, strong) RecentDocumentsStore *store;
@property (nonatomic, copy, nullable) NSURL *currentURL;
@property (nonatomic, assign) BOOL showingOpened;
@property (nonatomic, copy) NSArray<RecentDocumentItem *> *visibleItems;
@property (nonatomic, strong) UIButton *openedTabButton;
@property (nonatomic, strong) UIButton *closedTabButton;
@property (nonatomic, strong) UITableView *tableView;
@property (nonatomic, strong) UILabel *emptyLabel;
@end

@implementation DocumentTabsSheetController

+ (instancetype)presentFrom:(UIViewController *)host
                       store:(RecentDocumentsStore *)store
                 currentURL:(NSURL *)currentURL
                   delegate:(id<DocumentTabsSheetControllerDelegate>)delegate
{
    DocumentTabsSheetController *sheet = [[DocumentTabsSheetController alloc] init];
    sheet.store = store;
    sheet.currentURL = currentURL;
    sheet.actionDelegate = delegate;
    sheet.modalPresentationStyle = UIModalPresentationPageSheet;
    if (@available(iOS 15.0, *)) {
        UISheetPresentationController *presentation = sheet.sheetPresentationController;
        presentation.detents = @[
            [UISheetPresentationControllerDetent mediumDetent],
            [UISheetPresentationControllerDetent largeDetent],
        ];
        presentation.prefersGrabberVisible = YES;
        presentation.preferredCornerRadius = 24.0;
    }
    [host presentViewController:sheet animated:YES completion:nil];
    return sheet;
}

- (void)viewDidLoad
{
    [super viewDidLoad];
    self.view.backgroundColor = UIColor.whiteColor;
    self.view.accessibilityIdentifier = @"documentTabsSheet";
    self.showingOpened = YES;

    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"文档";
    title.font = [UIFont systemFontOfSize:18 weight:UIFontWeightSemibold];
    title.textAlignment = NSTextAlignmentCenter;

    WriterAICloseButton *close = [WriterAICloseButton closeButtonWithTarget:self action:@selector(closeTapped)];

    UIView *tabTrack = [[UIView alloc] init];
    tabTrack.translatesAutoresizingMaskIntoConstraints = NO;
    tabTrack.backgroundColor = [UIColor colorWithRed:0.949 green:0.953 blue:0.961 alpha:1.0];
    tabTrack.layer.cornerRadius = 12.0;

    self.openedTabButton = [self tabButtonWithTitle:@"已打开" tag:0];
    self.closedTabButton = [self tabButtonWithTitle:@"最近关闭" tag:1];
    [self styleTabButton:self.openedTabButton selected:YES];
    [self styleTabButton:self.closedTabButton selected:NO];

    UIStackView *tabStack = [[UIStackView alloc] initWithArrangedSubviews:@[
        self.openedTabButton, self.closedTabButton,
    ]];
    tabStack.translatesAutoresizingMaskIntoConstraints = NO;
    tabStack.axis = UILayoutConstraintAxisHorizontal;
    tabStack.spacing = 4.0;
    tabStack.distribution = UIStackViewDistributionFillEqually;
    [tabTrack addSubview:tabStack];

    self.tableView = [[UITableView alloc] initWithFrame:CGRectZero style:UITableViewStylePlain];
    self.tableView.translatesAutoresizingMaskIntoConstraints = NO;
    self.tableView.dataSource = self;
    self.tableView.delegate = self;
    self.tableView.rowHeight = 64.0;
    self.tableView.separatorInset = UIEdgeInsetsMake(0, 72, 0, 16);

    self.emptyLabel = [[UILabel alloc] init];
    self.emptyLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.emptyLabel.textColor = [UIColor colorWithWhite:0.5 alpha:1];
    self.emptyLabel.font = [UIFont systemFontOfSize:15];
    self.emptyLabel.textAlignment = NSTextAlignmentCenter;
    self.emptyLabel.hidden = YES;

    UIButton *openDocument = [UIButton buttonWithType:UIButtonTypeSystem];
    openDocument.translatesAutoresizingMaskIntoConstraints = NO;
    [openDocument setTitle:@"打开文档" forState:UIControlStateNormal];
    openDocument.titleLabel.font = [UIFont systemFontOfSize:16 weight:UIFontWeightSemibold];
    openDocument.backgroundColor = [UIColor colorWithRed:0.16 green:0.48 blue:0.78 alpha:1.0];
    [openDocument setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    openDocument.layer.cornerRadius = 12.0;
    [openDocument addTarget:self action:@selector(openDocumentTapped) forControlEvents:UIControlEventTouchUpInside];

    [self.view addSubview:title];
    [self.view addSubview:close];
    [self.view addSubview:tabTrack];
    [self.view addSubview:self.tableView];
    [self.view addSubview:self.emptyLabel];
    [self.view addSubview:openDocument];

    UILayoutGuide *safe = self.view.safeAreaLayoutGuide;
    [NSLayoutConstraint activateConstraints:@[
        [title.topAnchor constraintEqualToAnchor:safe.topAnchor constant:16],
        [title.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [close.centerYAnchor constraintEqualToAnchor:title.centerYAnchor],
        [close.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-12],
        [tabTrack.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:12],
        [tabTrack.leadingAnchor constraintEqualToAnchor:safe.leadingAnchor constant:16],
        [tabTrack.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-16],
        [tabTrack.heightAnchor constraintEqualToConstant:38],
        [tabStack.topAnchor constraintEqualToAnchor:tabTrack.topAnchor constant:4],
        [tabStack.leadingAnchor constraintEqualToAnchor:tabTrack.leadingAnchor constant:4],
        [tabStack.trailingAnchor constraintEqualToAnchor:tabTrack.trailingAnchor constant:-4],
        [tabStack.bottomAnchor constraintEqualToAnchor:tabTrack.bottomAnchor constant:-4],
        [self.tableView.topAnchor constraintEqualToAnchor:tabTrack.bottomAnchor constant:12],
        [self.tableView.leadingAnchor constraintEqualToAnchor:safe.leadingAnchor],
        [self.tableView.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor],
        [openDocument.topAnchor constraintEqualToAnchor:self.tableView.bottomAnchor constant:12],
        [openDocument.leadingAnchor constraintEqualToAnchor:safe.leadingAnchor constant:16],
        [openDocument.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-16],
        [openDocument.bottomAnchor constraintEqualToAnchor:safe.bottomAnchor constant:-16],
        [openDocument.heightAnchor constraintEqualToConstant:48],
        [self.emptyLabel.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [self.emptyLabel.centerYAnchor constraintEqualToAnchor:self.tableView.centerYAnchor],
    ]];

    [self reloadData];
}

- (UIButton *)tabButtonWithTitle:(NSString *)title tag:(NSInteger)tag
{
    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    button.tag = tag;
    button.layer.cornerRadius = 8.0;
    button.titleLabel.font = [UIFont systemFontOfSize:12];
    [button setTitle:title forState:UIControlStateNormal];
    [button addTarget:self action:@selector(tabTapped:) forControlEvents:UIControlEventTouchUpInside];
    return button;
}

- (void)styleTabButton:(UIButton *)button selected:(BOOL)selected
{
    button.backgroundColor = selected ? UIColor.whiteColor : UIColor.clearColor;
    [button setTitleColor:(selected ? [UIColor colorWithWhite:0.06 alpha:1] : [UIColor colorWithWhite:0.6 alpha:1])
                 forState:UIControlStateNormal];
}

- (void)tabTapped:(UIButton *)sender
{
    self.showingOpened = (sender.tag == 0);
    [self styleTabButton:self.openedTabButton selected:self.showingOpened];
    [self styleTabButton:self.closedTabButton selected:!self.showingOpened];
    [self reloadData];
}

- (void)reloadData
{
    NSUInteger openedCount = self.store.openDocumentCount;
    NSUInteger closedCount = self.store.recentlyClosedItems.count;
    [self.openedTabButton setTitle:[NSString stringWithFormat:@"已打开 (%lu)", (unsigned long)openedCount]
                          forState:UIControlStateNormal];
    [self.closedTabButton setTitle:[NSString stringWithFormat:@"最近关闭 (%lu)", (unsigned long)closedCount]
                          forState:UIControlStateNormal];

    self.visibleItems = self.showingOpened ? self.store.items : self.store.recentlyClosedItems;
    self.emptyLabel.text = self.showingOpened ? @"暂无最近文档" : @"暂无最近关闭文档";
    self.emptyLabel.hidden = self.visibleItems.count > 0;
    [self.tableView reloadData];
}

- (UIImage *)iconForItem:(RecentDocumentItem *)item
{
    NSString *ext = item.pathExtension.lowercaseString;
    if ([ext isEqualToString:@"ods"] || [ext isEqualToString:@"xlsx"] || [ext isEqualToString:@"xls"]
        || [ext isEqualToString:@"csv"]) {
        return [UIImage imageNamed:@"HomeFileCalc"];
    }
    if ([ext isEqualToString:@"odp"] || [ext isEqualToString:@"pptx"] || [ext isEqualToString:@"ppt"]) {
        return [UIImage imageNamed:@"HomeFileImpress"];
    }
    return [UIImage imageNamed:@"HomeFileWriter"];
}

- (BOOL)isCurrentItem:(RecentDocumentItem *)item
{
    return [self.store item:item matchesURL:self.currentURL];
}

- (void)closeTapped
{
    [self dismissViewControllerAnimated:YES completion:^{
        [self.actionDelegate documentTabsSheetDidDismiss];
    }];
}

- (void)openDocumentTapped
{
    [self dismissViewControllerAnimated:YES completion:^{
        [self.actionDelegate documentTabsSheetDidRequestOpenDocument];
    }];
}

#pragma mark - UITableViewDataSource

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    return (NSInteger)self.visibleItems.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
    static NSString *const cellId = @"DocumentTabCell";
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:cellId];
    if (cell == nil) {
        cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:cellId];
        cell.imageView.contentMode = UIViewContentModeScaleAspectFit;
    }

    RecentDocumentItem *item = self.visibleItems[(NSUInteger)indexPath.row];
    cell.imageView.image = [self iconForItem:item];
    cell.textLabel.font = [UIFont systemFontOfSize:16 weight:UIFontWeightSemibold];
    cell.textLabel.text = item.displayTitle;
    cell.detailTextLabel.text = item.displaySubtitle;
    cell.detailTextLabel.textColor = [UIColor colorWithWhite:0.45 alpha:1];
    cell.accessoryType = [self isCurrentItem:item] ? UITableViewCellAccessoryCheckmark : UITableViewCellAccessoryNone;

    if (self.showingOpened && ![self isCurrentItem:item]) {
        UIButton *remove = [UIButton buttonWithType:UIButtonTypeSystem];
        [remove setTitle:@"✕" forState:UIControlStateNormal];
        remove.titleLabel.font = [UIFont systemFontOfSize:16];
        remove.frame = CGRectMake(0, 0, 32, 32);
        remove.tag = indexPath.row;
        [remove addTarget:self action:@selector(removeTapped:) forControlEvents:UIControlEventTouchUpInside];
        cell.accessoryView = remove;
    } else {
        cell.accessoryView = nil;
    }

    return cell;
}

- (void)removeTapped:(UIButton *)sender
{
    if (sender.tag < 0 || sender.tag >= (NSInteger)self.visibleItems.count) {
        return;
    }
    RecentDocumentItem *item = self.visibleItems[(NSUInteger)sender.tag];
    [self.store moveItemToRecentlyClosed:item];
    [self reloadData];
    [self.actionDelegate documentTabsSheetDidChangeDocumentList];
}

#pragma mark - UITableViewDelegate

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
    RecentDocumentItem *item = self.visibleItems[(NSUInteger)indexPath.row];
    if ([self isCurrentItem:item]) {
        [self closeTapped];
        return;
    }
    NSURL *url = [item resolvedURL];
    if (url == nil) {
        return;
    }
    if (!self.showingOpened) {
        [self.store restoreFromRecentlyClosed:item];
    }
    [self dismissViewControllerAnimated:YES completion:^{
        [self.actionDelegate documentTabsSheetDidSelectURL:url];
    }];
}

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
