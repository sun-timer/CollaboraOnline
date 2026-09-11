// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "ProfileSettingsViewController.h"

#import "HomeCardDialogPresenter.h"
#import "Settings/AppChromeHelper.h"
#import "Settings/AppIcons.h"

#import <PhotosUI/PhotosUI.h>

static NSString *const kProfileNameKey = @"USER_PROFILE_NAME";
static NSString *const kAvatarPathKey = @"USER_PROFILE_AVATAR_PATH";
static NSString *const kAvatarFileName = @"ai_profile_avatar.jpg";

@interface ProfileSettingsViewController () <PHPickerViewControllerDelegate, UIImagePickerControllerDelegate, UINavigationControllerDelegate>
@property (strong, nonatomic) UIImageView *avatarView;
@property (strong, nonatomic) UILabel *nicknameLabel;
@property (assign, nonatomic) BOOL profileChanged;
@end

@implementation ProfileSettingsViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    [AppChromeHelper applySecondaryPageBackground:self.view];

    UIButton *back = [AppChromeHelper secondaryBackButtonWithTarget:self action:@selector(close)];
    [self.view addSubview:back];

    UILabel *header = [[UILabel alloc] init];
    header.translatesAutoresizingMaskIntoConstraints = NO;
    header.text = @"修改头像昵称";
    header.font = [UIFont boldSystemFontOfSize:20];
    header.textColor = [UIColor colorWithRed:0x10 / 255.0 green:0x10 / 255.0 blue:0x10 / 255.0 alpha:1];
    [self.view addSubview:header];

    UIView *card = [[UIView alloc] init];
    card.translatesAutoresizingMaskIntoConstraints = NO;
    card.backgroundColor = [UIColor colorWithRed:0xF2 / 255.0 green:0xF3 / 255.0 blue:0xF5 / 255.0 alpha:1];
    card.layer.cornerRadius = 12;
    card.clipsToBounds = YES;
    [self.view addSubview:card];

    UIView *avatarRow = [self settingsRowWithTitle:@"头像" valueView:nil action:@selector(editAvatar)];
    UIView *nicknameRow = [self settingsRowWithTitle:@"昵称" valueView:nil action:@selector(editNickname)];
    self.avatarView = (UIImageView *)[avatarRow viewWithTag:9001];
    self.nicknameLabel = (UILabel *)[nicknameRow viewWithTag:9002];

    UIView *divider = [[UIView alloc] init];
    divider.translatesAutoresizingMaskIntoConstraints = NO;
    divider.backgroundColor = [UIColor colorWithRed:0xE2 / 255.0 green:0xE3 / 255.0 blue:0xE4 / 255.0 alpha:1];

    UIStackView *rows = [[UIStackView alloc] initWithArrangedSubviews:@[ avatarRow, divider, nicknameRow ]];
    rows.translatesAutoresizingMaskIntoConstraints = NO;
    rows.axis = UILayoutConstraintAxisVertical;
    rows.spacing = 0;
    [card addSubview:rows];

    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:8],
        [back.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:16],
        [header.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [header.leadingAnchor constraintEqualToAnchor:back.trailingAnchor constant:8],
        [card.topAnchor constraintEqualToAnchor:back.bottomAnchor constant:16],
        [card.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:20],
        [card.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor constant:-20],
        [rows.topAnchor constraintEqualToAnchor:card.topAnchor],
        [rows.leadingAnchor constraintEqualToAnchor:card.leadingAnchor],
        [rows.trailingAnchor constraintEqualToAnchor:card.trailingAnchor],
        [rows.bottomAnchor constraintEqualToAnchor:card.bottomAnchor],
        [avatarRow.heightAnchor constraintEqualToConstant:55],
        [nicknameRow.heightAnchor constraintEqualToConstant:55],
        [divider.heightAnchor constraintEqualToConstant:1],
    ]];

    [self reloadProfile];
}

- (UIView *)settingsRowWithTitle:(NSString *)title valueView:(UIView *)valueView action:(SEL)action {
    UIControl *row = [[UIControl alloc] init];
    row.translatesAutoresizingMaskIntoConstraints = NO;
    [row addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];

    UILabel *titleLabel = [[UILabel alloc] init];
    titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    titleLabel.text = title;
    titleLabel.font = [UIFont systemFontOfSize:16];
    titleLabel.textColor = [UIColor colorWithRed:0x10 / 255.0 green:0x10 / 255.0 blue:0x10 / 255.0 alpha:1];

    UILabel *chevron = [[UILabel alloc] init];
    chevron.translatesAutoresizingMaskIntoConstraints = NO;
    chevron.text = @"›";
    chevron.font = [UIFont systemFontOfSize:18 weight:UIFontWeightMedium];
    chevron.textColor = [UIColor colorWithWhite:0.55 alpha:1];

    UIView *value = valueView;
    if ([title isEqualToString:@"头像"]) {
        UIImageView *avatar = [[UIImageView alloc] init];
        avatar.translatesAutoresizingMaskIntoConstraints = NO;
        avatar.tag = 9001;
        avatar.contentMode = UIViewContentModeScaleAspectFill;
        avatar.layer.cornerRadius = 18;
        avatar.clipsToBounds = YES;
        value = avatar;
    } else if ([title isEqualToString:@"昵称"]) {
        UILabel *name = [[UILabel alloc] init];
        name.translatesAutoresizingMaskIntoConstraints = NO;
        name.tag = 9002;
        name.font = [UIFont systemFontOfSize:14];
        name.textColor = [UIColor colorWithRed:0x6D / 255.0 green:0x72 / 255.0 blue:0x78 / 255.0 alpha:1];
        name.textAlignment = NSTextAlignmentRight;
        value = name;
    }

    [row addSubview:titleLabel];
    [row addSubview:value];
    [row addSubview:chevron];

    [NSLayoutConstraint activateConstraints:@[
        [titleLabel.leadingAnchor constraintEqualToAnchor:row.leadingAnchor constant:20],
        [titleLabel.centerYAnchor constraintEqualToAnchor:row.centerYAnchor],
        [chevron.trailingAnchor constraintEqualToAnchor:row.trailingAnchor constant:-20],
        [chevron.centerYAnchor constraintEqualToAnchor:row.centerYAnchor],
        [value.trailingAnchor constraintEqualToAnchor:chevron.leadingAnchor constant:-8],
        [value.centerYAnchor constraintEqualToAnchor:row.centerYAnchor],
    ]];
    if ([value isKindOfClass:[UIImageView class]]) {
        [NSLayoutConstraint activateConstraints:@[
            [[(UIImageView *)value widthAnchor] constraintEqualToConstant:36],
            [[(UIImageView *)value heightAnchor] constraintEqualToConstant:36],
        ]];
    } else {
        [value.leadingAnchor constraintGreaterThanOrEqualToAnchor:titleLabel.trailingAnchor constant:12].active = YES;
    }
    return row;
}

- (NSURL *)avatarURL {
    NSString *path = [[NSUserDefaults standardUserDefaults] stringForKey:kAvatarPathKey];
    if (path.length > 0) {
        return [NSURL fileURLWithPath:path];
    }
    NSURL *support = [[[NSFileManager defaultManager] URLsForDirectory:NSApplicationSupportDirectory inDomains:NSUserDomainMask] lastObject];
    [[NSFileManager defaultManager] createDirectoryAtURL:support withIntermediateDirectories:YES attributes:nil error:nil];
    return [support URLByAppendingPathComponent:kAvatarFileName];
}

- (void)reloadProfile {
    NSString *name = [[NSUserDefaults standardUserDefaults] stringForKey:kProfileNameKey];
    if (name.length == 0) {
        name = @"orangepi";
    }
    self.nicknameLabel.text = name;
    UIImage *image = [UIImage imageWithContentsOfFile:[self avatarURL].path];
    if (image == nil) {
        image = [AppIcons iconNamed:@"avatar"];
    }
    self.avatarView.image = image;
}

- (void)close {
    [self dismissViewControllerAnimated:YES completion:^{
        if (self.profileChanged && self.onProfileChanged) {
            self.onProfileChanged();
        }
    }];
}

- (void)editAvatar {
    __weak __typeof(self) weakSelf = self;
    [HomeCardDialogPresenter presentSetAvatarFrom:self
                                        takePhoto:^{
        [weakSelf openCamera];
    } pickGallery:^{
        [weakSelf openGallery];
    }];
}

- (void)editNickname {
    NSString *current = self.nicknameLabel.text ?: @"orangepi";
    __weak __typeof(self) weakSelf = self;
    [HomeCardDialogPresenter presentNicknameEditFrom:self
                                         currentName:current
                                          completion:^(NSString *nickname) {
        [[NSUserDefaults standardUserDefaults] setObject:nickname forKey:kProfileNameKey];
        weakSelf.profileChanged = YES;
        [weakSelf reloadProfile];
    }];
}

- (void)openGallery {
    PHPickerConfiguration *config = [[PHPickerConfiguration alloc] init];
    config.selectionLimit = 1;
    config.filter = [PHPickerFilter imagesFilter];
    PHPickerViewController *picker = [[PHPickerViewController alloc] initWithConfiguration:config];
    picker.delegate = self;
    [self presentViewController:picker animated:YES completion:nil];
}

- (void)openCamera {
    if (![UIImagePickerController isSourceTypeAvailable:UIImagePickerControllerSourceTypeCamera]) {
        UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"无法启动相机"
                                                                       message:nil
                                                                preferredStyle:UIAlertControllerStyleAlert];
        [alert addAction:[UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:nil]];
        [self presentViewController:alert animated:YES completion:nil];
        return;
    }
    UIImagePickerController *picker = [[UIImagePickerController alloc] init];
    picker.sourceType = UIImagePickerControllerSourceTypeCamera;
    picker.delegate = self;
    picker.allowsEditing = YES;
    [self presentViewController:picker animated:YES completion:nil];
}

- (void)saveAvatarImage:(UIImage *)image {
    if (image == nil) {
        return;
    }
    NSData *data = UIImageJPEGRepresentation(image, 0.85);
    NSURL *url = [self avatarURL];
    [data writeToURL:url atomically:YES];
    [[NSUserDefaults standardUserDefaults] setObject:url.path forKey:kAvatarPathKey];
    self.profileChanged = YES;
    [self reloadProfile];
}

- (void)picker:(PHPickerViewController *)picker didFinishPicking:(NSArray<PHPickerResult *> *)results {
    [picker dismissViewControllerAnimated:YES completion:nil];
    PHPickerResult *result = results.firstObject;
    if (result == nil) {
        return;
    }
    __weak __typeof(self) weakSelf = self;
    [result.itemProvider loadObjectOfClass:[UIImage class] completionHandler:^(id object, NSError *error) {
        UIImage *image = (UIImage *)object;
        if (![image isKindOfClass:[UIImage class]]) {
            return;
        }
        dispatch_async(dispatch_get_main_queue(), ^{
            [weakSelf saveAvatarImage:image];
        });
    }];
}

- (void)imagePickerController:(UIImagePickerController *)picker
didFinishPickingMediaWithInfo:(NSDictionary<UIImagePickerControllerInfoKey, id> *)info {
    UIImage *image = info[UIImagePickerControllerEditedImage];
    if (image == nil) {
        image = info[UIImagePickerControllerOriginalImage];
    }
    [picker dismissViewControllerAnimated:YES completion:^{
        [self saveAvatarImage:image];
    }];
}

- (void)imagePickerControllerDidCancel:(UIImagePickerController *)picker {
    [picker dismissViewControllerAnimated:YES completion:nil];
}

@end
