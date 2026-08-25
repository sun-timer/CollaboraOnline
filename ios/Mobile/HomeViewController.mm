// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "HomeViewController.h"

#import "AI/AISettingsDrawerController.h"
#import "DocumentPresentation.h"
#import "RecentDocumentsStore.h"

#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>

@interface HomeViewController () <UITableViewDataSource, UITableViewDelegate, UIDocumentPickerDelegate, UITextFieldDelegate>
@property (strong, nonatomic) RecentDocumentsStore *recentStore;
@property (strong, nonatomic) AISettingsDrawerController *drawer;
@property (strong, nonatomic) NSArray<RecentDocumentItem *> *visibleItems;
@property (strong, nonatomic) UITableView *tableView;
@property (strong, nonatomic) UITextField *searchField;
@property (strong, nonatomic) UIButton *avatarButton;
@property (strong, nonatomic) UILabel *emptyLabel;
@end

@implementation HomeViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = UIColor.whiteColor;
    self.view.accessibilityIdentifier = @"homeRoot";
    self.recentStore = [[RecentDocumentsStore alloc] init];

    UIView *topBar = [[UIView alloc] init];
    topBar.translatesAutoresizingMaskIntoConstraints = NO;
    topBar.backgroundColor = [UIColor colorWithWhite:0.95 alpha:1];
    [self.view addSubview:topBar];

    self.avatarButton = [UIButton buttonWithType:UIButtonTypeCustom];
    self.avatarButton.translatesAutoresizingMaskIntoConstraints = NO;
    self.avatarButton.backgroundColor = [UIColor colorWithRed:250.0 / 255.0 green:98.0 / 255.0 blue:0 alpha:1];
    self.avatarButton.layer.cornerRadius = 18;
    self.avatarButton.clipsToBounds = YES;
    self.avatarButton.accessibilityIdentifier = @"homeAvatarButton";
    self.avatarButton.accessibilityLabel = @"打开设置";
    [self.avatarButton addTarget:self action:@selector(openDrawer) forControlEvents:UIControlEventTouchUpInside];
    [topBar addSubview:self.avatarButton];

    UIView *searchBox = [[UIView alloc] init];
    searchBox.translatesAutoresizingMaskIntoConstraints = NO;
    searchBox.backgroundColor = UIColor.whiteColor;
    searchBox.layer.cornerRadius = 18;
    [topBar addSubview:searchBox];

    self.searchField = [[UITextField alloc] init];
    self.searchField.translatesAutoresizingMaskIntoConstraints = NO;
    self.searchField.placeholder = @"搜索";
    self.searchField.font = [UIFont systemFontOfSize:16];
    self.searchField.delegate = self;
    self.searchField.accessibilityIdentifier = @"homeSearchField";
    self.searchField.clearButtonMode = UITextFieldViewModeWhileEditing;
    [self.searchField addTarget:self action:@selector(searchChanged) forControlEvents:UIControlEventEditingChanged];
    [searchBox addSubview:self.searchField];

    UIButton *openFile = [UIButton buttonWithType:UIButtonTypeSystem];
    openFile.translatesAutoresizingMaskIntoConstraints = NO;
    [openFile setTitle:@"打开" forState:UIControlStateNormal];
    openFile.accessibilityIdentifier = @"homeOpenFileButton";
    [openFile addTarget:self action:@selector(openFile) forControlEvents:UIControlEventTouchUpInside];
    [topBar addSubview:openFile];

    UILabel *recentsHeader = [[UILabel alloc] init];
    recentsHeader.translatesAutoresizingMaskIntoConstraints = NO;
    recentsHeader.text = @"最近打开";
    recentsHeader.font = [UIFont systemFontOfSize:16 weight:UIFontWeightBold];
    recentsHeader.textColor = [UIColor colorWithRed:111.0 / 255.0 green:115.0 / 255.0 blue:120.0 / 255.0 alpha:1];
    recentsHeader.accessibilityIdentifier = @"homeRecentsHeader";
    [self.view addSubview:recentsHeader];

    self.tableView = [[UITableView alloc] initWithFrame:CGRectZero style:UITableViewStylePlain];
    self.tableView.translatesAutoresizingMaskIntoConstraints = NO;
    self.tableView.dataSource = self;
    self.tableView.delegate = self;
    self.tableView.rowHeight = 64;
    self.tableView.separatorInset = UIEdgeInsetsMake(0, 16, 0, 16);
    self.tableView.accessibilityIdentifier = @"homeRecentsTable";
    [self.view addSubview:self.tableView];

    self.emptyLabel = [[UILabel alloc] init];
    self.emptyLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.emptyLabel.text = @"还没有最近文档";
    self.emptyLabel.textColor = [UIColor colorWithWhite:0.55 alpha:1];
    self.emptyLabel.textAlignment = NSTextAlignmentCenter;
    self.emptyLabel.hidden = YES;
    [self.view addSubview:self.emptyLabel];

    UIButton *fab = [UIButton buttonWithType:UIButtonTypeSystem];
    fab.translatesAutoresizingMaskIntoConstraints = NO;
    [fab setTitle:@"+" forState:UIControlStateNormal];
    fab.titleLabel.font = [UIFont systemFontOfSize:28 weight:UIFontWeightMedium];
    [fab setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    fab.backgroundColor = [UIColor colorWithRed:250.0 / 255.0 green:98.0 / 255.0 blue:0 alpha:1];
    fab.layer.cornerRadius = 28;
    fab.accessibilityIdentifier = @"homeFab";
    fab.accessibilityLabel = @"新建文档";
    [fab addTarget:self action:@selector(createDocument) forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:fab];

    UILayoutGuide *safe = self.view.safeAreaLayoutGuide;
    [NSLayoutConstraint activateConstraints:@[
        [topBar.topAnchor constraintEqualToAnchor:self.view.topAnchor],
        [topBar.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [topBar.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [topBar.bottomAnchor constraintEqualToAnchor:safe.topAnchor constant:56],
        [self.avatarButton.leadingAnchor constraintEqualToAnchor:safe.leadingAnchor constant:16],
        [self.avatarButton.bottomAnchor constraintEqualToAnchor:topBar.bottomAnchor constant:-8],
        [self.avatarButton.widthAnchor constraintEqualToConstant:36],
        [self.avatarButton.heightAnchor constraintEqualToConstant:36],
        [openFile.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-12],
        [openFile.centerYAnchor constraintEqualToAnchor:self.avatarButton.centerYAnchor],
        [searchBox.leadingAnchor constraintEqualToAnchor:self.avatarButton.trailingAnchor constant:12],
        [searchBox.trailingAnchor constraintEqualToAnchor:openFile.leadingAnchor constant:-8],
        [searchBox.centerYAnchor constraintEqualToAnchor:self.avatarButton.centerYAnchor],
        [searchBox.heightAnchor constraintEqualToConstant:36],
        [self.searchField.leadingAnchor constraintEqualToAnchor:searchBox.leadingAnchor constant:12],
        [self.searchField.trailingAnchor constraintEqualToAnchor:searchBox.trailingAnchor constant:-12],
        [self.searchField.centerYAnchor constraintEqualToAnchor:searchBox.centerYAnchor],
        [recentsHeader.topAnchor constraintEqualToAnchor:topBar.bottomAnchor],
        [recentsHeader.leadingAnchor constraintEqualToAnchor:safe.leadingAnchor constant:16],
        [recentsHeader.heightAnchor constraintEqualToConstant:56],
        [self.tableView.topAnchor constraintEqualToAnchor:recentsHeader.bottomAnchor],
        [self.tableView.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.tableView.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [self.tableView.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
        [self.emptyLabel.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [self.emptyLabel.centerYAnchor constraintEqualToAnchor:self.view.centerYAnchor],
        [fab.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-24],
        [fab.bottomAnchor constraintEqualToAnchor:safe.bottomAnchor constant:-24],
        [fab.widthAnchor constraintEqualToConstant:56],
        [fab.heightAnchor constraintEqualToConstant:56],
    ]];

    self.drawer = [AISettingsDrawerController attachToHost:self];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [self.recentStore importLocalTestFiles];
    [self reloadRecents];
}

- (void)openDrawer {
    [self.drawer openDrawer];
}

- (void)searchChanged {
    [self reloadRecents];
}

- (void)reloadRecents {
    self.visibleItems = [self.recentStore itemsMatchingQuery:self.searchField.text ?: @""];
    self.emptyLabel.hidden = self.visibleItems.count > 0;
    [self.tableView reloadData];
}

- (void)presentDocumentAtURL:(NSURL *)documentURL {
    [DocumentPresentation presentDocumentAtURL:documentURL from:self];
}

- (void)openFile {
    UIDocumentPickerViewController *picker =
        [[UIDocumentPickerViewController alloc] initForOpeningContentTypes:@[ UTTypeItem ] asCopy:NO];
    picker.delegate = self;
    picker.allowsMultipleSelection = NO;
    picker.shouldShowFileExtensions = YES;
    [self presentViewController:picker animated:YES completion:nil];
}

- (void)documentPicker:(UIDocumentPickerViewController *)controller didPickDocumentsAtURLs:(NSArray<NSURL *> *)urls {
    NSURL *url = urls.firstObject;
    if (url == nil) {
        return;
    }
    [self presentDocumentAtURL:url];
}

- (void)createDocument {
    UIAlertController *sheet = [UIAlertController alertControllerWithTitle:@"新建"
                                                                   message:nil
                                                            preferredStyle:UIAlertControllerStyleActionSheet];
    [sheet addAction:[UIAlertAction actionWithTitle:@"文本文档" style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
        [self createWithTemplateExtension:@"ott" outputExtension:@"odt" basename:@"文档"];
    }]];
    [sheet addAction:[UIAlertAction actionWithTitle:@"电子表格" style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
        [self createWithTemplateExtension:@"ots" outputExtension:@"ods" basename:@"表格"];
    }]];
    [sheet addAction:[UIAlertAction actionWithTitle:@"演示文稿" style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
        [self createWithTemplateExtension:@"otp" outputExtension:@"odp" basename:@"演示"];
    }]];
    [sheet addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];
    sheet.popoverPresentationController.sourceView = self.view;
    sheet.popoverPresentationController.sourceRect = CGRectMake(self.view.bounds.size.width - 52, self.view.bounds.size.height - 80, 1, 1);
    [self presentViewController:sheet animated:YES completion:nil];
}

- (void)createWithTemplateExtension:(NSString *)templateExtension
                    outputExtension:(NSString *)outputExtension
                          basename:(NSString *)basename {
    NSError *error = nil;
    NSURL *url = [DocumentPresentation createDocumentFromTemplateExtension:templateExtension
                                                           outputExtension:outputExtension
                                                                 basename:basename
                                                                    error:&error];
    if (url == nil) {
        UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"无法新建文档"
                                                                       message:error.localizedDescription
                                                                preferredStyle:UIAlertControllerStyleAlert];
        [alert addAction:[UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:nil]];
        [self presentViewController:alert animated:YES completion:nil];
        return;
    }
    [self presentDocumentAtURL:url];
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return (NSInteger)self.visibleItems.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:@"recent"];
    if (cell == nil) {
        cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:@"recent"];
    }
    RecentDocumentItem *item = self.visibleItems[indexPath.row];
    cell.textLabel.text = item.title;
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    formatter.dateStyle = NSDateFormatterMediumStyle;
    formatter.timeStyle = NSDateFormatterShortStyle;
    cell.detailTextLabel.text = item.openedAt != nil ? [formatter stringFromDate:item.openedAt] : @"";
    cell.accessibilityIdentifier = [NSString stringWithFormat:@"homeRecent-%@", item.title];
    NSString *ext = item.pathExtension.lowercaseString;
    if ([ext isEqualToString:@"ods"] || [ext isEqualToString:@"xlsx"] || [ext isEqualToString:@"xls"]) {
        cell.imageView.image = [UIImage systemImageNamed:@"tablecells"];
    } else if ([ext isEqualToString:@"odp"] || [ext isEqualToString:@"pptx"] || [ext isEqualToString:@"ppt"]) {
        cell.imageView.image = [UIImage systemImageNamed:@"rectangle.on.rectangle"];
    } else {
        cell.imageView.image = [UIImage systemImageNamed:@"doc.text"];
    }
    cell.imageView.tintColor = [UIColor colorWithRed:250.0 / 255.0 green:98.0 / 255.0 blue:0 alpha:1];
    return cell;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
    RecentDocumentItem *item = self.visibleItems[indexPath.row];
    NSURL *url = [item resolvedURL];
    if (url == nil) {
        UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"无法打开"
                                                                       message:@"该文件已不可用。"
                                                                preferredStyle:UIAlertControllerStyleAlert];
        [alert addAction:[UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:nil]];
        [self presentViewController:alert animated:YES completion:nil];
        return;
    }
    [self presentDocumentAtURL:url];
}

- (BOOL)textFieldShouldReturn:(UITextField *)textField {
    [textField resignFirstResponder];
    return YES;
}

@end
