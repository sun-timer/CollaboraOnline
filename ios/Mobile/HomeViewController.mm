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
static NSString *const ProfileNameKey = @"USER_PROFILE_NAME";
static NSString *const ProfileAvatarKey = @"USER_PROFILE_AVATAR_PATH";

@interface HomeViewController () <UITableViewDataSource, UITableViewDelegate, UIDocumentPickerDelegate, UITextFieldDelegate, UIGestureRecognizerDelegate, UIImagePickerControllerDelegate, UINavigationControllerDelegate>
@property (strong, nonatomic) RecentDocumentsStore *recentStore;
@property (strong, nonatomic) AISettingsDrawerController *drawer;
@property (strong, nonatomic) NSArray<RecentDocumentItem *> *visibleItems;
@property (strong, nonatomic) UITableView *tableView;
@property (strong, nonatomic) UITextField *searchField;
@property (strong, nonatomic) UIButton *avatarButton;
@property (strong, nonatomic) UIView *emptyContainer;
@property (strong, nonatomic) UILabel *emptyTitleLabel;
@property (strong, nonatomic) UIStackView *emptyStack;
@property (strong, nonatomic, nullable) UIView *splashView;
@property (strong, nonatomic, nullable) RecentDocumentItem *activeMoreItem;
@property (strong, nonatomic, nullable) UIView *moreOverlay;
@property (strong, nonatomic, nullable) UIButton *profileAvatarEditButton;
@property (strong, nonatomic, nullable) UIView *createOverlay;
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
    self.avatarButton.backgroundColor = [UIColor colorWithRed:254.0 / 255.0 green:58.0 / 255.0 blue:58.0 / 255.0 alpha:1];
    self.avatarButton.layer.cornerRadius = 32;
    self.avatarButton.clipsToBounds = YES;
    self.avatarButton.accessibilityIdentifier = @"homeAvatarButton";
    self.avatarButton.accessibilityLabel = @"修改资料";
    [self.avatarButton addTarget:self action:@selector(presentProfileEditor) forControlEvents:UIControlEventTouchUpInside];
    [topBar addSubview:self.avatarButton];

    UIView *searchBox = [[UIView alloc] init];
    searchBox.translatesAutoresizingMaskIntoConstraints = NO;
    searchBox.backgroundColor = [UIColor colorWithWhite:0.0 alpha:0.08];
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
    [openFile setImage:[[UIImage systemImageNamed:@"clock"] imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate] forState:UIControlStateNormal];
    openFile.tintColor = [UIColor colorWithWhite:0.0 alpha:0.4];
    openFile.accessibilityLabel = @"最近打开";
    openFile.accessibilityIdentifier = @"homeOpenFileButton";
    [openFile addTarget:self action:@selector(openFile) forControlEvents:UIControlEventTouchUpInside];
    [topBar addSubview:openFile];

    UILabel *recentsHeader = [[UILabel alloc] init];
    recentsHeader.translatesAutoresizingMaskIntoConstraints = NO;
    recentsHeader.text = @"最近打开";
    recentsHeader.font = [UIFont systemFontOfSize:13 weight:UIFontWeightBold];
    recentsHeader.textColor = [UIColor colorWithWhite:0.0 alpha:0.4];
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

    self.emptyContainer = [[UIView alloc] init];
    self.emptyContainer.translatesAutoresizingMaskIntoConstraints = NO;
    self.emptyContainer.hidden = YES;
    [self.view addSubview:self.emptyContainer];

    UIImageView *emptyIcon = [[UIImageView alloc] initWithImage:[UIImage systemImageNamed:@"doc.plaintext"]];
    emptyIcon.translatesAutoresizingMaskIntoConstraints = NO;
    emptyIcon.tintColor = [UIColor colorWithWhite:0.75 alpha:1];
    emptyIcon.contentMode = UIViewContentModeScaleAspectFit;

    self.emptyTitleLabel = [[UILabel alloc] init];
    self.emptyTitleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.emptyTitleLabel.text = @"还没有最近文档";
    self.emptyTitleLabel.font = [UIFont systemFontOfSize:13 weight:UIFontWeightBold];
    self.emptyTitleLabel.textColor = [UIColor colorWithWhite:0.0 alpha:0.4];
    self.emptyTitleLabel.textAlignment = NSTextAlignmentCenter;

    UIButton *emptyOpen = [UIButton buttonWithType:UIButtonTypeSystem];
    emptyOpen.translatesAutoresizingMaskIntoConstraints = NO;
    [emptyOpen setTitle:@"打开本地文件" forState:UIControlStateNormal];
    emptyOpen.titleLabel.font = [UIFont systemFontOfSize:15];
    emptyOpen.backgroundColor = [UIColor colorWithWhite:0.95 alpha:1];
    emptyOpen.layer.cornerRadius = 8;
    emptyOpen.accessibilityIdentifier = @"homeEmptyOpen";
    [emptyOpen addTarget:self action:@selector(openFile) forControlEvents:UIControlEventTouchUpInside];

    UIButton *emptyImport = [UIButton buttonWithType:UIButtonTypeSystem];
    emptyImport.translatesAutoresizingMaskIntoConstraints = NO;
    [emptyImport setTitle:@"导入" forState:UIControlStateNormal];
    emptyImport.titleLabel.font = [UIFont systemFontOfSize:15];
    emptyImport.backgroundColor = [UIColor colorWithWhite:0.95 alpha:1];
    emptyImport.layer.cornerRadius = 8;
    emptyImport.accessibilityIdentifier = @"homeEmptyImport";
    [emptyImport addTarget:self action:@selector(openFile) forControlEvents:UIControlEventTouchUpInside];

    self.emptyStack = [[UIStackView alloc] initWithArrangedSubviews:@[ emptyIcon, self.emptyTitleLabel, emptyOpen, emptyImport ]];
    self.emptyStack.translatesAutoresizingMaskIntoConstraints = NO;
    self.emptyStack.axis = UILayoutConstraintAxisVertical;
    self.emptyStack.alignment = UIStackViewAlignmentCenter;
    self.emptyStack.spacing = 12;
    [self.emptyContainer addSubview:self.emptyStack];

    [emptyIcon.widthAnchor constraintEqualToConstant:80].active = YES;
    [emptyIcon.heightAnchor constraintEqualToConstant:80].active = YES;
    [emptyOpen.widthAnchor constraintEqualToConstant:140].active = YES;
    [emptyOpen.heightAnchor constraintEqualToConstant:44].active = YES;
    [emptyImport.widthAnchor constraintEqualToConstant:140].active = YES;
    [emptyImport.heightAnchor constraintEqualToConstant:44].active = YES;
    [self.emptyStack.centerXAnchor constraintEqualToAnchor:self.emptyContainer.centerXAnchor].active = YES;
    [self.emptyStack.centerYAnchor constraintEqualToAnchor:self.emptyContainer.centerYAnchor].active = YES;

    UIButton *fab = [UIButton buttonWithType:UIButtonTypeSystem];
    fab.translatesAutoresizingMaskIntoConstraints = NO;
    [fab setTitle:@"+" forState:UIControlStateNormal];
    fab.titleLabel.font = [UIFont systemFontOfSize:28 weight:UIFontWeightMedium];
    [fab setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    fab.backgroundColor = [UIColor colorWithRed:254.0 / 255.0 green:58.0 / 255.0 blue:58.0 / 255.0 alpha:1];
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
        [topBar.bottomAnchor constraintEqualToAnchor:safe.topAnchor constant:100],
        [self.avatarButton.leadingAnchor constraintEqualToAnchor:safe.leadingAnchor constant:16],
        [self.avatarButton.bottomAnchor constraintEqualToAnchor:topBar.bottomAnchor constant:-8],
        [self.avatarButton.widthAnchor constraintEqualToConstant:64],
        [self.avatarButton.heightAnchor constraintEqualToConstant:64],
        [openFile.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-12],
        [openFile.widthAnchor constraintEqualToConstant:44],
        [openFile.heightAnchor constraintEqualToConstant:44],
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
        [self.emptyContainer.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [self.emptyContainer.centerYAnchor constraintEqualToAnchor:self.view.centerYAnchor],
        [fab.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-24],
        [fab.bottomAnchor constraintEqualToAnchor:safe.bottomAnchor constant:-24],
        [fab.widthAnchor constraintEqualToConstant:56],
        [fab.heightAnchor constraintEqualToConstant:56],
    ]];

    self.drawer = [AISettingsDrawerController attachToHost:self];
    [self showSplash];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [self.recentStore importLocalTestFiles];
    [self refreshAvatarButton];
    [self reloadRecents];
}

- (void)presentProfileEditor {
    __weak typeof(self) weakSelf = self;

    UIViewController *editor = [[UIViewController alloc] init];
    editor.view.backgroundColor = UIColor.whiteColor;
    editor.modalPresentationStyle = UIModalPresentationPageSheet;

    UILabel *titleLabel = [[UILabel alloc] init];
    titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    titleLabel.text = @"修改资料";
    titleLabel.font = [UIFont boldSystemFontOfSize:17];
    titleLabel.textColor = UIColor.blackColor;
    [editor.view addSubview:titleLabel];

    UITextField *nameField = [[UITextField alloc] init];
    nameField.translatesAutoresizingMaskIntoConstraints = NO;
    nameField.placeholder = @"名字";
    nameField.font = [UIFont systemFontOfSize:17];
    nameField.text = [[NSUserDefaults standardUserDefaults] stringForKey:ProfileNameKey];
    nameField.borderStyle = UITextBorderStyleNone;
    nameField.textAlignment = NSTextAlignmentCenter;
    nameField.returnKeyType = UIReturnKeyDone;
    nameField.accessibilityIdentifier = @"profileNameField";
    [nameField addTarget:nameField action:@selector(resignFirstResponder) forControlEvents:UIControlEventEditingDidEndOnExit];
    [editor.view addSubview:nameField];

    UIView *underline = [[UIView alloc] init];
    underline.translatesAutoresizingMaskIntoConstraints = NO;
    underline.backgroundColor = [UIColor colorWithWhite:0.0 alpha:0.12];
    [editor.view addSubview:underline];

    UIButton *avatarEdit = [UIButton buttonWithType:UIButtonTypeCustom];
    avatarEdit.translatesAutoresizingMaskIntoConstraints = NO;
    avatarEdit.layer.cornerRadius = 48;
    avatarEdit.clipsToBounds = YES;
    avatarEdit.accessibilityIdentifier = @"profileAvatarEdit";
    NSString *avatarPath = [[NSUserDefaults standardUserDefaults] stringForKey:ProfileAvatarKey];
    UIImage *avatarImage = (avatarPath.length > 0) ? [UIImage imageWithContentsOfFile:avatarPath] : nil;
    if (avatarImage != nil) {
        [avatarEdit setBackgroundImage:avatarImage forState:UIControlStateNormal];
    } else {
        avatarEdit.backgroundColor = [UIColor colorWithRed:254.0 / 255.0 green:58.0 / 255.0 blue:58.0 / 255.0 alpha:1];
        [avatarEdit setTitle:@"选择照片" forState:UIControlStateNormal];
        [avatarEdit setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
        avatarEdit.titleLabel.font = [UIFont systemFontOfSize:13];
    }
    [avatarEdit addTarget:self action:@selector(pickAvatarPhoto) forControlEvents:UIControlEventTouchUpInside];
    [editor.view addSubview:avatarEdit];
    self.profileAvatarEditButton = avatarEdit;

    UIButton *cancelButton = [UIButton buttonWithType:UIButtonTypeSystem];
    cancelButton.translatesAutoresizingMaskIntoConstraints = NO;
    [cancelButton addAction:[UIAction actionWithTitle:@"取消" handler:^(UIAction *action) {
        [editor dismissViewControllerAnimated:YES completion:^{
            weakSelf.profileAvatarEditButton = nil;
        }];
    }] forControlEvents:UIControlEventTouchUpInside];
    [editor.view addSubview:cancelButton];

    UIButton *saveButton = [UIButton buttonWithType:UIButtonTypeSystem];
    saveButton.translatesAutoresizingMaskIntoConstraints = NO;
    [saveButton addAction:[UIAction actionWithTitle:@"保存" handler:^(UIAction *action) {
        NSString *name = [nameField.text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
        [weakSelf saveProfileName:name];
        [editor dismissViewControllerAnimated:YES completion:^{
            weakSelf.profileAvatarEditButton = nil;
        }];
    }] forControlEvents:UIControlEventTouchUpInside];
    [editor.view addSubview:saveButton];

    [NSLayoutConstraint activateConstraints:@[
        [titleLabel.topAnchor constraintEqualToAnchor:editor.view.safeAreaLayoutGuide.topAnchor constant:16],
        [titleLabel.centerXAnchor constraintEqualToAnchor:editor.view.centerXAnchor],
        [cancelButton.leadingAnchor constraintEqualToAnchor:editor.view.leadingAnchor constant:16],
        [cancelButton.centerYAnchor constraintEqualToAnchor:titleLabel.centerYAnchor],
        [saveButton.trailingAnchor constraintEqualToAnchor:editor.view.trailingAnchor constant:-16],
        [saveButton.centerYAnchor constraintEqualToAnchor:titleLabel.centerYAnchor],
        [avatarEdit.topAnchor constraintEqualToAnchor:titleLabel.bottomAnchor constant:48],
        [avatarEdit.centerXAnchor constraintEqualToAnchor:editor.view.centerXAnchor],
        [avatarEdit.widthAnchor constraintEqualToConstant:96],
        [avatarEdit.heightAnchor constraintEqualToConstant:96],
        [nameField.topAnchor constraintEqualToAnchor:avatarEdit.bottomAnchor constant:40],
        [nameField.leadingAnchor constraintEqualToAnchor:editor.view.leadingAnchor constant:40],
        [nameField.trailingAnchor constraintEqualToAnchor:editor.view.trailingAnchor constant:-40],
        [nameField.heightAnchor constraintEqualToConstant:44],
        [underline.topAnchor constraintEqualToAnchor:nameField.bottomAnchor],
        [underline.leadingAnchor constraintEqualToAnchor:nameField.leadingAnchor],
        [underline.trailingAnchor constraintEqualToAnchor:nameField.trailingAnchor],
        [underline.heightAnchor constraintEqualToConstant:1],
    ]];
    UIButton *modelConfig = [UIButton buttonWithType:UIButtonTypeSystem];
    modelConfig.translatesAutoresizingMaskIntoConstraints = NO;
    [modelConfig setTitle:@"AI 模型配置 ›" forState:UIControlStateNormal];
    modelConfig.titleLabel.font = [UIFont systemFontOfSize:15];
    modelConfig.accessibilityIdentifier = @"profileModelConfig";
    [modelConfig addAction:[UIAction actionWithTitle:@"AI 模型配置" handler:^(UIAction *action) {
        [editor dismissViewControllerAnimated:YES completion:^{
            weakSelf.profileAvatarEditButton = nil;
            [weakSelf.drawer openDrawer];
        }];
    }] forControlEvents:UIControlEventTouchUpInside];
    [editor.view addSubview:modelConfig];
    [NSLayoutConstraint activateConstraints:@[
        [modelConfig.topAnchor constraintEqualToAnchor:underline.bottomAnchor constant:24],
        [modelConfig.centerXAnchor constraintEqualToAnchor:editor.view.centerXAnchor],
    ]];

    [self presentViewController:editor animated:YES completion:nil];
}
- (void)saveProfileName:(NSString *)name {
    if (name.length > 0) {
        [[NSUserDefaults standardUserDefaults] setObject:name forKey:ProfileNameKey];
    }
    [self refreshAvatarButton];
}

- (void)refreshAvatarButton {
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    NSString *avatarPath = [defaults stringForKey:ProfileAvatarKey];
    UIImage *avatar = (avatarPath.length > 0) ? [UIImage imageWithContentsOfFile:avatarPath] : nil;
    self.avatarButton.layer.cornerRadius = 32;
    self.avatarButton.clipsToBounds = YES;
    if (avatar != nil) {
        [self.avatarButton setImage:avatar forState:UIControlStateNormal];
        [self.avatarButton setTitle:@"" forState:UIControlStateNormal];
        self.avatarButton.backgroundColor = UIColor.clearColor;
    } else {
        [self.avatarButton setImage:nil forState:UIControlStateNormal];
        self.avatarButton.backgroundColor = [UIColor colorWithRed:254.0 / 255.0 green:58.0 / 255.0 blue:58.0 / 255.0 alpha:1];
        NSString *name = [defaults stringForKey:ProfileNameKey];
        NSString *initial = (name.length > 0) ? [name substringToIndex:1] : @"我";
        [self.avatarButton setTitle:initial forState:UIControlStateNormal];
        [self.avatarButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
        self.avatarButton.titleLabel.font = [UIFont boldSystemFontOfSize:24];
    }
}

- (void)pickAvatarPhoto {
    if (![UIImagePickerController isSourceTypeAvailable:UIImagePickerControllerSourceTypePhotoLibrary]) {
        return;
    }
    UIImagePickerController *picker = [[UIImagePickerController alloc] init];
    picker.sourceType = UIImagePickerControllerSourceTypePhotoLibrary;
    picker.allowsEditing = YES;
    picker.delegate = self;
    [self presentViewController:picker animated:YES completion:nil];
}

- (void)imagePickerController:(UIImagePickerController *)picker didFinishPickingMediaWithInfo:(NSDictionary<UIImagePickerControllerInfoKey, id> *)info {
    UIImage *image = info[UIImagePickerControllerEditedImage];
    if (image == nil) {
        image = info[UIImagePickerControllerOriginalImage];
    }
    if (image != nil) {
        NSData *data = UIImagePNGRepresentation(image);
        if (data != nil) {
            NSURL *dir = [[[NSFileManager defaultManager] URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask] lastObject];
            NSURL *file = [dir URLByAppendingPathComponent:@"profile_avatar.png"];
            if ([data writeToURL:file atomically:YES]) {
                [[NSUserDefaults standardUserDefaults] setObject:file.path forKey:ProfileAvatarKey];
                if (self.profileAvatarEditButton != nil) {
                    [self.profileAvatarEditButton setBackgroundImage:image forState:UIControlStateNormal];
                    [self.profileAvatarEditButton setTitle:@"" forState:UIControlStateNormal];
                }
                [self refreshAvatarButton];
            }
        }
    }
    [picker dismissViewControllerAnimated:YES completion:nil];
}

- (void)imagePickerControllerDidCancel:(UIImagePickerController *)picker {
    [picker dismissViewControllerAnimated:YES completion:nil];
}

- (void)searchChanged {
    [self reloadRecents];
}

- (void)reloadRecents {
    self.visibleItems = [self.recentStore itemsMatchingQuery:self.searchField.text ?: @""];
    BOOL hasItems = self.visibleItems.count > 0;
    BOOL searching = self.searchField.text.length > 0;
    self.emptyContainer.hidden = hasItems;
    self.emptyTitleLabel.text = searching ? @"未找到相关文档" : @"还没有最近文档";
    if (self.emptyStack.arrangedSubviews.count >= 4) {
        self.emptyStack.arrangedSubviews[2].hidden = searching;
        self.emptyStack.arrangedSubviews[3].hidden = searching;
    }
    [self.tableView reloadData];
}
- (void)showSplash {
    UIView *splash = [[UIView alloc] initWithFrame:self.view.bounds];
    splash.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    splash.backgroundColor = UIColor.whiteColor;
    [self.view addSubview:splash];
    self.splashView = splash;

    UIView *logo = [[UIView alloc] init];
    logo.translatesAutoresizingMaskIntoConstraints = NO;
    logo.backgroundColor = UIColor.whiteColor;
    logo.layer.cornerRadius = 60;
    logo.clipsToBounds = YES;
    logo.layer.shadowColor = [UIColor.blackColor CGColor];
    logo.layer.shadowOpacity = 0.08;
    logo.layer.shadowRadius = 12;
    logo.layer.shadowOffset = CGSizeMake(0, 4);
    [splash addSubview:logo];

    UILabel *brand = [[UILabel alloc] init];
    brand.translatesAutoresizingMaskIntoConstraints = NO;
    brand.text = @"AI Office";
    brand.font = [UIFont boldSystemFontOfSize:20];
    brand.textColor = [UIColor colorWithRed:254.0 / 255.0 green:58.0 / 255.0 blue:58.0 / 255.0 alpha:1];
    brand.textAlignment = NSTextAlignmentCenter;
    [logo addSubview:brand];

    UILabel *tagline = [[UILabel alloc] init];
    tagline.translatesAutoresizingMaskIntoConstraints = NO;
    tagline.text = @"智启办公，高效随行";
    tagline.font = [UIFont systemFontOfSize:12];
    tagline.textColor = [UIColor blackColor];
    tagline.textAlignment = NSTextAlignmentCenter;
    [splash addSubview:tagline];

    [NSLayoutConstraint activateConstraints:@[
        [logo.centerXAnchor constraintEqualToAnchor:splash.centerXAnchor],
        [logo.bottomAnchor constraintEqualToAnchor:splash.bottomAnchor constant:-140],
        [logo.widthAnchor constraintEqualToConstant:120],
        [logo.heightAnchor constraintEqualToConstant:120],
        [brand.centerXAnchor constraintEqualToAnchor:logo.centerXAnchor],
        [brand.centerYAnchor constraintEqualToAnchor:logo.centerYAnchor],
        [brand.leadingAnchor constraintEqualToAnchor:logo.leadingAnchor constant:8],
        [brand.trailingAnchor constraintEqualToAnchor:logo.trailingAnchor constant:-8],
        [tagline.centerXAnchor constraintEqualToAnchor:splash.centerXAnchor],
        [tagline.topAnchor constraintEqualToAnchor:logo.bottomAnchor constant:20],
    ]];

    [UIView animateWithDuration:0.3 delay:1.4 options:UIViewAnimationOptionCurveEaseOut animations:^{
        splash.alpha = 0;
    } completion:^(BOOL finished) {
        [splash removeFromSuperview];
        if (self.splashView == splash) {
            self.splashView = nil;
        }
    }];
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
    if (self.createOverlay != nil) {
        return;
    }
    UIView *overlay = [[UIView alloc] initWithFrame:self.view.bounds];
    overlay.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    overlay.backgroundColor = [UIColor colorWithWhite:0 alpha:0.4];
    UITapGestureRecognizer *tap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(dismissCreateOverlay)];
    [overlay addGestureRecognizer:tap];
    [self.view addSubview:overlay];
    self.createOverlay = overlay;

    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.backgroundColor = UIColor.whiteColor;
    card.layer.cornerRadius = 24;
    card.layer.cornerCurve = kCACornerCurveContinuous;
    card.layer.shadowColor = [UIColor.blackColor CGColor];
    card.layer.shadowOpacity = 0.1;
    card.layer.shadowRadius = 16;
    card.layer.shadowOffset = CGSizeMake(0, 4);
    [overlay addSubview:card];

    NSArray<NSDictionary *> *items = @[
        @{ @"title": @"新建文稿", @"template": @"ott", @"output": @"odt", @"basename": @"文档", @"ext": @"odt" },
        @{ @"title": @"新建表格", @"template": @"ots", @"output": @"ods", @"basename": @"表格", @"ext": @"ods" },
        @{ @"title": @"新建演示", @"template": @"otp", @"output": @"odp", @"basename": @"演示", @"ext": @"odp" },
    ];
    UIButton *previous = nil;
    for (NSDictionary *spec in items) {
        UIButton *row = [UIButton buttonWithType:UIButtonTypeCustom];
        row.translatesAutoresizingMaskIntoConstraints = NO;
        [row addTarget:self action:@selector(createMenuAction:) forControlEvents:UIControlEventTouchUpInside];
        [row setTitle:spec[@"title"] forState:UIControlStateNormal];
        [row setTitleColor:UIColor.blackColor forState:UIControlStateNormal];
        row.titleLabel.font = [UIFont systemFontOfSize:17];
        row.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeft;
        row.contentEdgeInsets = UIEdgeInsetsMake(0, 20, 0, 20);
        row.tag = (NSInteger)[items indexOfObject:spec];
        [row setImage:[self typeIconForPathExtension:spec[@"ext"]] forState:UIControlStateNormal];
        row.imageEdgeInsets = UIEdgeInsetsMake(0, 0, 0, 16);
        [card addSubview:row];
        [row.leadingAnchor constraintEqualToAnchor:card.leadingAnchor].active = YES;
        [row.trailingAnchor constraintEqualToAnchor:card.trailingAnchor].active = YES;
        [row.heightAnchor constraintEqualToConstant:80].active = YES;
        if (previous == nil) {
            [row.topAnchor constraintEqualToAnchor:card.topAnchor constant:16].active = YES;
        } else {
            [row.topAnchor constraintEqualToAnchor:previous.bottomAnchor].active = YES;
        }
        previous = row;
    }

    CGFloat cardWidth = fmin(360, self.view.bounds.size.width - 40);
    [NSLayoutConstraint activateConstraints:@[
        [card.centerXAnchor constraintEqualToAnchor:overlay.centerXAnchor],
        [card.centerYAnchor constraintEqualToAnchor:overlay.centerYAnchor],
        [card.widthAnchor constraintEqualToConstant:cardWidth],
        [card.bottomAnchor constraintEqualToAnchor:previous.bottomAnchor constant:16],
    ]];
}
- (void)createMenuAction:(UIButton *)sender {
    NSArray<NSArray<NSString *> *> *specs = @[
        @[ @"ott", @"odt", @"文档" ],
        @[ @"ots", @"ods", @"表格" ],
        @[ @"otp", @"odp", @"演示" ],
    ];
    [self dismissCreateOverlay];
    if (sender.tag >= 0 && sender.tag < (NSInteger)specs.count) {
        NSArray<NSString *> *spec = specs[(NSUInteger)sender.tag];
        [self createWithTemplateExtension:spec[0] outputExtension:spec[1] basename:spec[2]];
    }
}

- (void)dismissCreateOverlay {
    if (self.createOverlay != nil) {
        [self.createOverlay removeFromSuperview];
        self.createOverlay = nil;
    }
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
    cell.detailTextLabel.text = [item displaySubtitle];
    cell.accessibilityIdentifier = [NSString stringWithFormat:@"homeRecent-%@", item.title];
    cell.imageView.image = [self typeIconForPathExtension:item.pathExtension];
    UIButton *moreButton = [UIButton buttonWithType:UIButtonTypeCustom];
    moreButton.frame = CGRectMake(0, 0, 56, 56);
    moreButton.backgroundColor = [UIColor colorWithRed:240.0 / 255.0 green:244.0 / 255.0 blue:249.0 / 255.0 alpha:1];
    moreButton.layer.cornerRadius = 8;
    [moreButton setImage:[UIImage systemImageNamed:@"ellipsis"] forState:UIControlStateNormal];
    moreButton.tintColor = [UIColor colorWithWhite:0 alpha:0.6];
    moreButton.tag = indexPath.row;
    [moreButton addTarget:self action:@selector(moreButtonTapped:) forControlEvents:UIControlEventTouchUpInside];
    moreButton.accessibilityIdentifier = [NSString stringWithFormat:@"homeMore-%@", item.title];
    cell.accessoryView = moreButton;
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

- (void)moreButtonTapped:(UIButton *)sender {
    NSInteger row = sender.tag;
    if (row < 0 || row >= (NSInteger)self.visibleItems.count) {
        return;
    }
    self.activeMoreItem = self.visibleItems[(NSUInteger)row];
    [self presentMoreMenu];
}

- (void)presentMoreMenu {
    if (self.moreOverlay != nil) {
        return;
    }
    UIView *overlay = [[UIView alloc] initWithFrame:self.view.bounds];
    overlay.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    overlay.backgroundColor = [UIColor colorWithWhite:0 alpha:0.4];
    UITapGestureRecognizer *tap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(dismissMoreMenu)];
    tap.delegate = self;
    [overlay addGestureRecognizer:tap];
    [self.view addSubview:overlay];
    self.moreOverlay = overlay;

    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.backgroundColor = UIColor.whiteColor;
    card.layer.cornerRadius = 24;
    card.layer.cornerCurve = kCACornerCurveContinuous;
    card.layer.shadowColor = [UIColor.blackColor CGColor];
    card.layer.shadowOpacity = 0.1;
    card.layer.shadowRadius = 16;
    card.layer.shadowOffset = CGSizeMake(0, 4);
    [overlay addSubview:card];

    NSArray<NSString *> *titles = @[ @"重命名", @"分享", @"从列表中删除" ];
    UIButton *previous = nil;
    for (NSUInteger i = 0; i < titles.count; i++) {
        UIButton *item = [UIButton buttonWithType:UIButtonTypeSystem];
        item.translatesAutoresizingMaskIntoConstraints = NO;
        [item setTitle:titles[i] forState:UIControlStateNormal];
        item.titleLabel.font = [UIFont systemFontOfSize:17];
        item.tag = (NSInteger)i;
        [item addTarget:self action:@selector(moreMenuAction:) forControlEvents:UIControlEventTouchUpInside];
        [card addSubview:item];
        [item.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:24].active = YES;
        [item.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-24].active = YES;
        [item.heightAnchor constraintEqualToConstant:52].active = YES;
        if (previous == nil) {
            [item.topAnchor constraintEqualToAnchor:card.topAnchor constant:16].active = YES;
        } else {
            [item.topAnchor constraintEqualToAnchor:previous.bottomAnchor].active = YES;
        }
        previous = item;
    }

    CGFloat cardWidth = fmin(360, self.view.bounds.size.width - 40);
    [NSLayoutConstraint activateConstraints:@[
        [card.centerXAnchor constraintEqualToAnchor:overlay.centerXAnchor],
        [card.centerYAnchor constraintEqualToAnchor:overlay.centerYAnchor],
        [card.widthAnchor constraintEqualToConstant:cardWidth],
        [card.heightAnchor constraintEqualToConstant:200],
    ]];
}

- (void)dismissMoreMenu {
    if (self.moreOverlay != nil) {
        [self.moreOverlay removeFromSuperview];
        self.moreOverlay = nil;
    }
    self.activeMoreItem = nil;
}

- (void)moreMenuAction:(UIButton *)sender {
    NSString *title = [sender.titleLabel.text copy];
    RecentDocumentItem *item = self.activeMoreItem;
    [self dismissMoreMenu];
    // 重命名/分享/删除动作由票 10/11/12 绑定；此票只交付菜单入口。
    NSLog(@"more menu: %@ item=%@", title, item.title);
}
- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer shouldReceiveTouch:(UITouch *)touch {
    if (gestureRecognizer.view == self.moreOverlay) {
        return touch.view == self.moreOverlay;
    }
    if (gestureRecognizer.view == self.createOverlay) {
        return touch.view == self.createOverlay;
    }
    return YES;
}
- (UIImage *)typeIconForPathExtension:(NSString *)pathExtension {
    static NSMutableDictionary<NSString *, UIImage *> *cache = nil;
    if (cache == nil) {
        cache = [NSMutableDictionary dictionary];
    }
    NSString *ext = pathExtension.lowercaseString;
    UIImage *cached = cache[ext];
    if (cached != nil) {
        return cached;
    }
    UIColor *fill = nil;
    NSString *glyph = nil;
    if ([ext isEqualToString:@"ods"] || [ext isEqualToString:@"xls"] || [ext isEqualToString:@"xlsx"]) {
        fill = [UIColor colorWithRed:59.0 / 255.0 green:128.0 / 255.0 blue:64.0 / 255.0 alpha:1];
        glyph = @"∑";
    } else if ([ext isEqualToString:@"odp"] || [ext isEqualToString:@"ppt"] || [ext isEqualToString:@"pptx"]) {
        fill = [UIColor colorWithRed:236.0 / 255.0 green:93.0 / 255.0 blue:31.0 / 255.0 alpha:1];
        glyph = @"P";
    } else {
        fill = [UIColor colorWithRed:18.0 / 255.0 green:120.0 / 255.0 blue:217.0 / 255.0 alpha:1];
        glyph = @"W";
    }
    CGSize size = CGSizeMake(56, 56);
    UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:size];
    UIImage *image = [renderer imageWithActions:^(UIGraphicsImageRendererContext * _Nonnull context) {
        UIBezierPath *card = [UIBezierPath bezierPathWithRoundedRect:CGRectMake(0, 0, 56, 56) cornerRadius:8];
        [fill setFill];
        [card fill];
        NSDictionary *attrs = @{
            NSFontAttributeName: [UIFont boldSystemFontOfSize:34],
            NSForegroundColorAttributeName: [UIColor whiteColor],
        };
        CGSize glyphSize = [glyph sizeWithAttributes:attrs];
        [glyph drawAtPoint:CGPointMake((56 - glyphSize.width) / 2, (56 - glyphSize.height) / 2) withAttributes:attrs];
    }];
    cache[ext] = image;
    return image;
}

@end
