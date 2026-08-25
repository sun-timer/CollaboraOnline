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
#import <CoreImage/CoreImage.h>
#import "AI/AIService.h"

static NSString *const kSharePublicKey = @"SHARE_PUBLIC_ENABLED";
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
@property (strong, nonatomic) AIService *aiService;
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
        @{ @"title": @"AI 快速生成", @"template": @"", @"output": @"", @"basename": @"", @"ext": @"ai" },
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
        if ([spec[@"title"] isEqualToString:@"AI 快速生成"]) {
            [row setImage:[UIImage systemImageNamed:@"sparkles"] forState:UIControlStateNormal];
            row.tintColor = [UIColor colorWithRed:254.0 / 255.0 green:58.0 / 255.0 blue:58.0 / 255.0 alpha:1];
        } else {
            [row setImage:[self typeIconForPathExtension:spec[@"ext"]] forState:UIControlStateNormal];
        }
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
    if (sender.tag == 3) {
        [self presentCreateWizard];
    } else if (sender.tag >= 0 && sender.tag < (NSInteger)specs.count) {
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
- (void)presentCreateWizard {
    __weak typeof(self) weakSelf = self;

    UIViewController *page = [[UIViewController alloc] init];
    page.view.backgroundColor = UIColor.whiteColor;
    page.modalPresentationStyle = UIModalPresentationPageSheet;

    UIButton *back = [UIButton buttonWithType:UIButtonTypeSystem];
    back.translatesAutoresizingMaskIntoConstraints = NO;
    [back setTitle:@"‹ 返回" forState:UIControlStateNormal];
    [back addAction:[UIAction actionWithTitle:@"返回" handler:^(UIAction *action) {
        [page dismissViewControllerAnimated:YES completion:nil];
    }] forControlEvents:UIControlEventTouchUpInside];
    [page.view addSubview:back];

    UILabel *titleLabel = [[UILabel alloc] init];
    titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    titleLabel.text = @"AI 快速生成";
    titleLabel.font = [UIFont boldSystemFontOfSize:17];
    titleLabel.textColor = UIColor.blackColor;
    [page.view addSubview:titleLabel];

    UITextField *topicField = [[UITextField alloc] init];
    topicField.translatesAutoresizingMaskIntoConstraints = NO;
    topicField.placeholder = @"文档主题，如：年度总结报告";
    topicField.font = [UIFont systemFontOfSize:16];
    topicField.borderStyle = UITextBorderStyleRoundedRect;
    topicField.returnKeyType = UIReturnKeyDone;
    topicField.accessibilityIdentifier = @"wizardTopic";
    [topicField addTarget:topicField action:@selector(resignFirstResponder) forControlEvents:UIControlEventEditingDidEndOnExit];
    [page.view addSubview:topicField];

    UILabel *pageLabel = [[UILabel alloc] init];
    pageLabel.translatesAutoresizingMaskIntoConstraints = NO;
    pageLabel.text = @"页数";
    pageLabel.font = [UIFont systemFontOfSize:14];
    pageLabel.textColor = [UIColor colorWithWhite:0.42 alpha:1];
    [page.view addSubview:pageLabel];

    UISegmentedControl *pageControl = [[UISegmentedControl alloc] initWithItems:@[ @"1 页", @"2 页", @"3 页以上" ]];
    pageControl.translatesAutoresizingMaskIntoConstraints = NO;
    pageControl.selectedSegmentIndex = 1;
    pageControl.accessibilityIdentifier = @"wizardPages";
    [page.view addSubview:pageControl];

    UILabel *typeLabel = [[UILabel alloc] init];
    typeLabel.translatesAutoresizingMaskIntoConstraints = NO;
    typeLabel.text = @"文档类型";
    typeLabel.font = [UIFont systemFontOfSize:14];
    typeLabel.textColor = [UIColor colorWithWhite:0.42 alpha:1];
    [page.view addSubview:typeLabel];

    UISegmentedControl *typeControl = [[UISegmentedControl alloc] initWithItems:@[ @"文稿", @"表格", @"演示" ]];
    typeControl.translatesAutoresizingMaskIntoConstraints = NO;
    typeControl.selectedSegmentIndex = 0;
    typeControl.accessibilityIdentifier = @"wizardType";
    [page.view addSubview:typeControl];

    UIButton *generate = [UIButton buttonWithType:UIButtonTypeSystem];
    generate.translatesAutoresizingMaskIntoConstraints = NO;
    [generate setTitle:@"生成文档" forState:UIControlStateNormal];
    [generate setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    generate.backgroundColor = [UIColor colorWithRed:254.0 / 255.0 green:58.0 / 255.0 blue:58.0 / 255.0 alpha:1];
    generate.layer.cornerRadius = 8;
    [generate addAction:[UIAction actionWithTitle:@"生成文档" handler:^(UIAction *action) {
        NSString *topic = [topicField.text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
        if (topic.length == 0) {
            return;
        }
        NSArray<NSString *> *types = @[ @"writer", @"calc", @"impress" ];
        NSArray<NSString *> *pages = @[ @"1", @"2", @"3" ];
        [weakSelf runQuickCreateWithTopic:topic
                                pageCount:pages[(NSUInteger)MAX(0, pageControl.selectedSegmentIndex)]
                                   docType:types[(NSUInteger)MAX(0, typeControl.selectedSegmentIndex)]
                                    button:generate
                                      page:page];
    }] forControlEvents:UIControlEventTouchUpInside];
    [page.view addSubview:generate];

    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:page.view.safeAreaLayoutGuide.topAnchor constant:8],
        [back.leadingAnchor constraintEqualToAnchor:page.view.leadingAnchor constant:12],
        [titleLabel.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [titleLabel.centerXAnchor constraintEqualToAnchor:page.view.centerXAnchor],
        [topicField.topAnchor constraintEqualToAnchor:back.bottomAnchor constant:24],
        [topicField.leadingAnchor constraintEqualToAnchor:page.view.leadingAnchor constant:24],
        [topicField.trailingAnchor constraintEqualToAnchor:page.view.trailingAnchor constant:-24],
        [topicField.heightAnchor constraintEqualToConstant:44],
        [pageLabel.topAnchor constraintEqualToAnchor:topicField.bottomAnchor constant:24],
        [pageLabel.leadingAnchor constraintEqualToAnchor:page.view.leadingAnchor constant:24],
        [pageControl.topAnchor constraintEqualToAnchor:pageLabel.bottomAnchor constant:8],
        [pageControl.leadingAnchor constraintEqualToAnchor:page.view.leadingAnchor constant:24],
        [pageControl.trailingAnchor constraintEqualToAnchor:page.view.trailingAnchor constant:-24],
        [typeLabel.topAnchor constraintEqualToAnchor:pageControl.bottomAnchor constant:24],
        [typeLabel.leadingAnchor constraintEqualToAnchor:page.view.leadingAnchor constant:24],
        [typeControl.topAnchor constraintEqualToAnchor:typeLabel.bottomAnchor constant:8],
        [typeControl.leadingAnchor constraintEqualToAnchor:page.view.leadingAnchor constant:24],
        [typeControl.trailingAnchor constraintEqualToAnchor:page.view.trailingAnchor constant:-24],
        [generate.topAnchor constraintEqualToAnchor:typeControl.bottomAnchor constant:32],
        [generate.leadingAnchor constraintEqualToAnchor:page.view.leadingAnchor constant:64],
        [generate.trailingAnchor constraintEqualToAnchor:page.view.trailingAnchor constant:-64],
        [generate.heightAnchor constraintEqualToConstant:48],
    ]];
    [self presentViewController:page animated:YES completion:nil];
}

- (void)runQuickCreateWithTopic:(NSString *)topic
                      pageCount:(NSString *)pageCount
                        docType:(NSString *)docType
                         button:(UIButton *)button
                           page:(UIViewController *)page {
    if (self.aiService == nil) {
        self.aiService = [[AIService alloc] init];
    }
    [button setTitle:@"生成中..." forState:UIControlStateNormal];
    button.enabled = NO;
    NSString *requestId = [[NSUUID UUID] UUIDString];
    NSString *sessionId = [[NSUUID UUID] UUIDString];
    NSDictionary *payload = @{
        @"taskType": @"create_document",
        @"selection": topic,
        @"context": @{
            @"pageCount": pageCount ?: @"",
            @"audience": @"",
            @"docType": docType ?: @"writer",
        },
    };
    __block NSMutableString *accumulated = [NSMutableString string];
    __weak typeof(self) weakSelf = self;
    [self.aiService startRequest:payload
                       requestId:requestId
              documentSessionId:sessionId
                           emit:^(NSString *type, NSString *rid, NSString *dsid, NSDictionary *eventPayload) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if ([type isEqualToString:@"ai.stream"]) {
                NSString *delta = eventPayload[@"delta"];
                if ([delta isKindOfClass:[NSString class]]) {
                    [accumulated appendString:delta];
                }
            } else if ([type isEqualToString:@"ai.done"]) {
                NSString *fullText = eventPayload[@"fullText"];
                [weakSelf finishQuickCreate:fullText page:page button:button];
            } else if ([type isEqualToString:@"ai.error"]) {
                [button setTitle:@"生成文档" forState:UIControlStateNormal];
                button.enabled = YES;
                NSString *message = eventPayload[@"message"] ?: @"生成失败";
                UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"生成失败"
                                                                               message:message
                                                                        preferredStyle:UIAlertControllerStyleAlert];
                [alert addAction:[UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:nil]];
                [page presentViewController:alert animated:YES completion:nil];
            }
        });
    }];
}

- (void)finishQuickCreate:(NSString *)fullText page:(UIViewController *)page button:(UIButton *)button {
    if (fullText.length == 0) {
        [button setTitle:@"生成文档" forState:UIControlStateNormal];
        button.enabled = YES;
        return;
    }
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    formatter.dateFormat = @"yyyyMMddHHmmss";
    NSString *name = [NSString stringWithFormat:@"AI文档-%@.txt", [formatter stringFromDate:[NSDate date]]];
    NSURL *dir = [[[NSFileManager defaultManager] URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask] lastObject];
    NSURL *file = [dir URLByAppendingPathComponent:name];
    NSError *error = nil;
    if (![fullText writeToURL:file atomically:YES encoding:NSUTF8StringEncoding error:&error]) {
        UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"生成失败"
                                                                       message:error.localizedDescription ?: @"无法保存文档"
                                                                preferredStyle:UIAlertControllerStyleAlert];
        [alert addAction:[UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:nil]];
        [page presentViewController:alert animated:YES completion:nil];
        return;
    }
    [page dismissViewControllerAnimated:YES completion:^{
        [self presentDocumentAtURL:file];
    }];
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
    NSInteger index = sender.tag;
    RecentDocumentItem *item = self.activeMoreItem;
    [self dismissMoreMenu];
    if (index == 0) {
        [self presentRenameAlertForItem:item];
    } else if (index == 2) {
        [self presentDeleteConfirmationForItem:item];
    } else {
        [self presentSharePanelForItem:item];
    }
}

- (void)presentRenameAlertForItem:(RecentDocumentItem *)item {
    if (item == nil) {
        return;
    }
    UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"重命名"
                                                                   message:nil
                                                            preferredStyle:UIAlertControllerStyleAlert];
    [alert addTextFieldWithConfigurationHandler:^(UITextField *field) {
        NSString *title = item.title;
        NSString *ext = title.pathExtension;
        field.text = ext.length > 0 ? [title stringByDeletingPathExtension] : title;
        field.placeholder = @"文件名";
    }];
    [alert addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];
    [alert addAction:[UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
        NSString *name = alert.textFields.firstObject.text ?: @"";
        [self.recentStore renameItem:item toTitle:name];
        [self reloadRecents];
    }]];
    [self presentViewController:alert animated:YES completion:nil];
}
- (void)presentDeleteConfirmationForItem:(RecentDocumentItem *)item {
    if (item == nil) {
        return;
    }
    UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"从列表中删除"
                                                                   message:@"仅从最近列表移除，不会删除文件。"
                                                            preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];
    [alert addAction:[UIAlertAction actionWithTitle:@"删除" style:UIAlertActionStyleDestructive handler:^(UIAlertAction *action) {
        [self.recentStore removeItem:item];
        [self reloadRecents];
    }]];
    [self presentViewController:alert animated:YES completion:nil];
}
- (void)presentSharePanelForItem:(RecentDocumentItem *)item {
    if (item == nil) {
        return;
    }
    __weak typeof(self) weakSelf = self;

    UIViewController *page = [[UIViewController alloc] init];
    page.view.backgroundColor = UIColor.whiteColor;
    page.modalPresentationStyle = UIModalPresentationPageSheet;

    UIButton *back = [UIButton buttonWithType:UIButtonTypeSystem];
    back.translatesAutoresizingMaskIntoConstraints = NO;
    [back setTitle:@"‹ 返回" forState:UIControlStateNormal];
    [back addAction:[UIAction actionWithTitle:@"返回" handler:^(UIAction *action) {
        [page dismissViewControllerAnimated:YES completion:nil];
    }] forControlEvents:UIControlEventTouchUpInside];
    [page.view addSubview:back];

    UILabel *titleLabel = [[UILabel alloc] init];
    titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    titleLabel.text = @"分享";
    titleLabel.font = [UIFont boldSystemFontOfSize:17];
    titleLabel.textColor = UIColor.blackColor;
    [page.view addSubview:titleLabel];

    UILabel *fileName = [[UILabel alloc] init];
    fileName.translatesAutoresizingMaskIntoConstraints = NO;
    fileName.text = item.title;
    fileName.font = [UIFont systemFontOfSize:14];
    fileName.textColor = [UIColor colorWithWhite:0.42 alpha:1];
    fileName.numberOfLines = 1;
    fileName.lineBreakMode = NSLineBreakByTruncatingMiddle;
    [page.view addSubview:fileName];

    UILabel *publicLabel = [[UILabel alloc] init];
    publicLabel.translatesAutoresizingMaskIntoConstraints = NO;
    publicLabel.text = @"公开分享";
    publicLabel.font = [UIFont systemFontOfSize:16];
    publicLabel.textColor = UIColor.blackColor;
    [page.view addSubview:publicLabel];

    UISwitch *publicSwitch = [[UISwitch alloc] init];
    publicSwitch.translatesAutoresizingMaskIntoConstraints = NO;
    publicSwitch.on = [[NSUserDefaults standardUserDefaults] boolForKey:kSharePublicKey];
    [publicSwitch addAction:[UIAction actionWithTitle:@"切换" handler:^(UIAction *action) {
        [[NSUserDefaults standardUserDefaults] setBool:publicSwitch.isOn forKey:kSharePublicKey];
    }] forControlEvents:UIControlEventValueChanged];
    [page.view addSubview:publicSwitch];

    UIView *qrCard = [[UIView alloc] init];
    qrCard.translatesAutoresizingMaskIntoConstraints = NO;
    qrCard.backgroundColor = UIColor.whiteColor;
    qrCard.layer.cornerRadius = 16;
    qrCard.layer.borderColor = [UIColor colorWithWhite:0.0 alpha:0.1].CGColor;
    qrCard.layer.borderWidth = 1;
    [page.view addSubview:qrCard];

    UIImageView *qrView = [[UIImageView alloc] init];
    qrView.translatesAutoresizingMaskIntoConstraints = NO;
    qrView.contentMode = UIViewContentModeScaleAspectFit;
    qrView.image = [self qrImageForString:item.title size:140];
    [qrCard addSubview:qrView];

    UILabel *qrHint = [[UILabel alloc] init];
    qrHint.translatesAutoresizingMaskIntoConstraints = NO;
    qrHint.text = @"扫描二维码打开文档";
    qrHint.font = [UIFont systemFontOfSize:12];
    qrHint.textColor = [UIColor colorWithWhite:0.45 alpha:1];
    [page.view addSubview:qrHint];

    UIButton *shareMore = [UIButton buttonWithType:UIButtonTypeSystem];
    shareMore.translatesAutoresizingMaskIntoConstraints = NO;
    [shareMore setTitle:@"更多发送方式" forState:UIControlStateNormal];
    [shareMore setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    shareMore.backgroundColor = [UIColor colorWithRed:254.0 / 255.0 green:58.0 / 255.0 blue:58.0 / 255.0 alpha:1];
    shareMore.layer.cornerRadius = 8;
    [shareMore addAction:[UIAction actionWithTitle:@"更多发送方式" handler:^(UIAction *action) {
        [weakSelf presentShareSheetForItem:item];
    }] forControlEvents:UIControlEventTouchUpInside];
    [page.view addSubview:shareMore];

    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:page.view.safeAreaLayoutGuide.topAnchor constant:8],
        [back.leadingAnchor constraintEqualToAnchor:page.view.leadingAnchor constant:12],
        [titleLabel.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [titleLabel.centerXAnchor constraintEqualToAnchor:page.view.centerXAnchor],
        [fileName.topAnchor constraintEqualToAnchor:back.bottomAnchor constant:16],
        [fileName.leadingAnchor constraintEqualToAnchor:page.view.leadingAnchor constant:24],
        [fileName.trailingAnchor constraintEqualToAnchor:page.view.trailingAnchor constant:-24],
        [publicLabel.topAnchor constraintEqualToAnchor:fileName.bottomAnchor constant:20],
        [publicLabel.leadingAnchor constraintEqualToAnchor:page.view.leadingAnchor constant:24],
        [publicSwitch.centerYAnchor constraintEqualToAnchor:publicLabel.centerYAnchor],
        [publicSwitch.trailingAnchor constraintEqualToAnchor:page.view.trailingAnchor constant:-24],
        [qrCard.topAnchor constraintEqualToAnchor:publicLabel.bottomAnchor constant:20],
        [qrCard.centerXAnchor constraintEqualToAnchor:page.view.centerXAnchor],
        [qrCard.widthAnchor constraintEqualToConstant:176],
        [qrCard.heightAnchor constraintEqualToConstant:176],
        [qrView.centerXAnchor constraintEqualToAnchor:qrCard.centerXAnchor],
        [qrView.centerYAnchor constraintEqualToAnchor:qrCard.centerYAnchor],
        [qrView.widthAnchor constraintEqualToConstant:140],
        [qrView.heightAnchor constraintEqualToConstant:140],
        [qrHint.topAnchor constraintEqualToAnchor:qrCard.bottomAnchor constant:8],
        [qrHint.centerXAnchor constraintEqualToAnchor:page.view.centerXAnchor],
        [shareMore.topAnchor constraintEqualToAnchor:qrHint.bottomAnchor constant:24],
        [shareMore.leadingAnchor constraintEqualToAnchor:page.view.leadingAnchor constant:64],
        [shareMore.trailingAnchor constraintEqualToAnchor:page.view.trailingAnchor constant:-64],
        [shareMore.heightAnchor constraintEqualToConstant:48],
    ]];
    [self presentViewController:page animated:YES completion:nil];
}

- (UIImage *)qrImageForString:(NSString *)string size:(CGFloat)size {
    NSData *data = [string dataUsingEncoding:NSISOTF8StringEncoding];
    if (data == nil) {
        return nil;
    }
    CIFilter *filter = [CIFilter filterWithName:@"CIQRCodeGenerator"];
    [filter setValue:data forKey:@"inputMessage"];
    [filter setValue:@"M" forKey:@"inputCorrectionLevel"];
    CIImage *output = filter.outputImage;
    if (output == nil) {
        return nil;
    }
    CGFloat scale = size / MAX(output.extent.size.width, 1.0);
    CIImage *scaled = [output imageByApplyingTransform:CGAffineTransformMakeScale(scale, scale)];
    UIImage *ciImage = [UIImage imageWithCIImage:scaled];
    UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:CGSizeMake(size, size)];
    return [renderer imageWithActions:^(UIGraphicsImageRendererContext *context) {
        [ciImage drawInRect:CGRectMake(0, 0, size, size)];
    }];
}

- (void)presentShareSheetForItem:(RecentDocumentItem *)item {
    NSURL *url = [item resolvedURL];
    if (url == nil) {
        UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"无法分享"
                                                                       message:@"该文件已不可用。"
                                                                preferredStyle:UIAlertControllerStyleAlert];
        [alert addAction:[UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:nil]];
        [self presentViewController:alert animated:YES completion:nil];
        return;
    }
    UIActivityViewController *activity = [[UIActivityViewController alloc] initWithActivityItems:@[ url ] applicationActivities:nil];
    activity.popoverPresentationController.sourceView = self.view;
    [self presentViewController:activity animated:YES completion:nil];
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
