// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "FeedbackViewController.h"

#import "FeedbackRecord.h"
#import "FeedbackStore.h"
#import "Settings/AppChromeHelper.h"
#import "Settings/AppToastPresenter.h"
#import "Settings/HomeCardDialogPresenter.h"

#import <PhotosUI/PhotosUI.h>
#import <objc/runtime.h>

static NSArray<NSString *> *FeedbackTypeLabels(void) {
    return @[ @"功能异常", @"产品建议", @"体验问题", @"其他" ];
}

@interface FeedbackViewController () <PHPickerViewControllerDelegate, UITextViewDelegate, UITableViewDataSource, UITableViewDelegate>
@property (strong, nonatomic) UIView *contentView;
@property (assign, nonatomic) NSInteger selectedTypeIndex;
@property (strong, nonatomic) NSMutableArray<NSString *> *imagePaths;
@property (assign, nonatomic) BOOL shareLog;
@property (strong, nonatomic) UITextView *descView;
@property (strong, nonatomic) UILabel *descCountLabel;
@property (strong, nonatomic) UITextField *contactField;
@property (strong, nonatomic) FeedbackRecord *currentDetail;
@end

@implementation FeedbackViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    [AppChromeHelper applySecondaryPageBackground:self.view];
    self.imagePaths = [NSMutableArray array];
    self.selectedTypeIndex = -1;

    self.contentView = [[UIView alloc] init];
    self.contentView.translatesAutoresizingMaskIntoConstraints = NO;
    [self.view addSubview:self.contentView];
    [NSLayoutConstraint activateConstraints:@[
        [self.contentView.topAnchor constraintEqualToAnchor:self.view.topAnchor],
        [self.contentView.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.contentView.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [self.contentView.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
    ]];
    [self showForm];
}

- (void)clearContent {
    for (UIView *sub in self.contentView.subviews) {
        [sub removeFromSuperview];
    }
}

- (UIButton *)headerBackButtonWithAction:(SEL)action {
    UIButton *back = [AppChromeHelper secondaryBackButtonWithTarget:self action:action];
    return back;
}

- (NSString *)formatTime:(NSTimeInterval)time {
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    formatter.dateFormat = @"yyyy/MM/dd HH:mm";
    return [formatter stringFromDate:[NSDate dateWithTimeIntervalSince1970:time]];
}

- (void)showForm {
    [self clearContent];
    UIButton *back = [self headerBackButtonWithAction:@selector(close)];
    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"问题反馈与建议";
    title.font = [UIFont boldSystemFontOfSize:18];
    UIButton *records = [UIButton buttonWithType:UIButtonTypeSystem];
    records.translatesAutoresizingMaskIntoConstraints = NO;
    [records setTitle:@"反馈记录" forState:UIControlStateNormal];
    [records addTarget:self action:@selector(showList) forControlEvents:UIControlEventTouchUpInside];

    UIStackView *chips = [[UIStackView alloc] init];
    chips.translatesAutoresizingMaskIntoConstraints = NO;
    chips.axis = UILayoutConstraintAxisHorizontal;
    chips.spacing = 8;
    chips.distribution = UIStackViewDistributionFillEqually;
    NSArray<NSString *> *types = FeedbackTypeLabels();
    for (NSUInteger i = 0; i < types.count; i++) {
        UIButton *chip = [UIButton buttonWithType:UIButtonTypeCustom];
        chip.tag = (NSInteger)i;
        chip.layer.cornerRadius = 16;
        chip.titleLabel.font = [UIFont systemFontOfSize:13];
        [chip setTitle:types[i] forState:UIControlStateNormal];
        [chip addTarget:self action:@selector(typeChipTapped:) forControlEvents:UIControlEventTouchUpInside];
        [chips addArrangedSubview:chip];
    }
    [self refreshTypeChipsInStack:chips];

    self.descView = [[UITextView alloc] init];
    self.descView.translatesAutoresizingMaskIntoConstraints = NO;
    self.descView.font = [UIFont systemFontOfSize:15];
    self.descView.layer.borderColor = [UIColor colorWithWhite:0.9 alpha:1].CGColor;
    self.descView.layer.borderWidth = 1;
    self.descView.layer.cornerRadius = 12;
    self.descView.delegate = self;
    self.descView.text = @"";

    self.descCountLabel = [[UILabel alloc] init];
    self.descCountLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.descCountLabel.text = @"0/500";
    self.descCountLabel.font = [UIFont systemFontOfSize:12];
    self.descCountLabel.textColor = [UIColor colorWithWhite:0.5 alpha:1];

    self.contactField = [[UITextField alloc] init];
    self.contactField.translatesAutoresizingMaskIntoConstraints = NO;
    self.contactField.placeholder = @"联系方式（选填）";
    self.contactField.borderStyle = UITextBorderStyleRoundedRect;

    UIButton *attach = [UIButton buttonWithType:UIButtonTypeSystem];
    attach.translatesAutoresizingMaskIntoConstraints = NO;
    [attach setTitle:@"添加图片（最多6张）" forState:UIControlStateNormal];
    [attach addTarget:self action:@selector(addImages) forControlEvents:UIControlEventTouchUpInside];

    UIButton *logRow = [UIButton buttonWithType:UIButtonTypeSystem];
    logRow.translatesAutoresizingMaskIntoConstraints = NO;
    [logRow setTitle:self.shareLog ? @"☑ 共享应用日志" : @"☐ 共享应用日志" forState:UIControlStateNormal];
    logRow.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeft;
    [logRow addTarget:self action:@selector(toggleShareLog:) forControlEvents:UIControlEventTouchUpInside];
    logRow.tag = 9001;

    UIButton *submit = [UIButton buttonWithType:UIButtonTypeSystem];
    submit.translatesAutoresizingMaskIntoConstraints = NO;
    submit.backgroundColor = [UIColor colorWithRed:0xFE / 255.0 green:0x3A / 255.0 blue:0x3A / 255.0 alpha:1];
    submit.layer.cornerRadius = 28;
    [submit setTitle:@"提交" forState:UIControlStateNormal];
    [submit setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    [submit addTarget:self action:@selector(submitFeedback) forControlEvents:UIControlEventTouchUpInside];

    for (UIView *v in @[ back, title, records, chips, self.descView, self.descCountLabel, self.contactField, attach, logRow, submit ]) {
        [self.contentView addSubview:v];
    }
    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:8],
        [back.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:12],
        [title.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [title.leadingAnchor constraintEqualToAnchor:back.trailingAnchor constant:8],
        [records.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [records.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor constant:-16],
        [chips.topAnchor constraintEqualToAnchor:back.bottomAnchor constant:20],
        [chips.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:16],
        [chips.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor constant:-16],
        [chips.heightAnchor constraintEqualToConstant:32],
        [self.descView.topAnchor constraintEqualToAnchor:chips.bottomAnchor constant:16],
        [self.descView.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:16],
        [self.descView.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor constant:-16],
        [self.descView.heightAnchor constraintEqualToConstant:140],
        [self.descCountLabel.topAnchor constraintEqualToAnchor:self.descView.bottomAnchor constant:4],
        [self.descCountLabel.trailingAnchor constraintEqualToAnchor:self.descView.trailingAnchor],
        [attach.topAnchor constraintEqualToAnchor:self.descCountLabel.bottomAnchor constant:12],
        [attach.leadingAnchor constraintEqualToAnchor:self.descView.leadingAnchor],
        [logRow.topAnchor constraintEqualToAnchor:attach.bottomAnchor constant:12],
        [logRow.leadingAnchor constraintEqualToAnchor:self.descView.leadingAnchor],
        [self.contactField.topAnchor constraintEqualToAnchor:logRow.bottomAnchor constant:12],
        [self.contactField.leadingAnchor constraintEqualToAnchor:self.descView.leadingAnchor],
        [self.contactField.trailingAnchor constraintEqualToAnchor:self.descView.trailingAnchor],
        [submit.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:48],
        [submit.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor constant:-48],
        [submit.bottomAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.bottomAnchor constant:-24],
        [submit.heightAnchor constraintEqualToConstant:56],
    ]];
}

- (void)refreshTypeChipsInStack:(UIStackView *)chips {
    NSArray<NSString *> *types = FeedbackTypeLabels();
    for (UIView *sub in chips.arrangedSubviews) {
        if (![sub isKindOfClass:[UIButton class]]) {
            continue;
        }
        UIButton *chip = (UIButton *)sub;
        BOOL selected = chip.tag == self.selectedTypeIndex;
        chip.backgroundColor = selected ? [UIColor colorWithRed:0xFE / 255.0 green:0x3A / 255.0 blue:0x3A / 255.0 alpha:1] : [UIColor colorWithWhite:0.95 alpha:1];
        [chip setTitleColor:selected ? UIColor.whiteColor : [UIColor colorWithWhite:0.2 alpha:1] forState:UIControlStateNormal];
        if (chip.tag >= 0 && (NSUInteger)chip.tag < types.count) {
            [chip setTitle:types[(NSUInteger)chip.tag] forState:UIControlStateNormal];
        }
    }
}

- (void)typeChipTapped:(UIButton *)sender {
    self.selectedTypeIndex = sender.tag;
    for (UIView *sub in self.contentView.subviews) {
        if ([sub isKindOfClass:[UIStackView class]]) {
            [self refreshTypeChipsInStack:(UIStackView *)sub];
            break;
        }
    }
}

- (void)toggleShareLog:(UIButton *)sender {
    self.shareLog = !self.shareLog;
    [sender setTitle:self.shareLog ? @"☑ 共享应用日志" : @"☐ 共享应用日志" forState:UIControlStateNormal];
}

- (void)textViewDidChange:(UITextView *)textView {
    if (textView.text.length > 500) {
        textView.text = [textView.text substringToIndex:500];
    }
    self.descCountLabel.text = [NSString stringWithFormat:@"%lu/500", (unsigned long)textView.text.length];
}

- (void)addImages {
    if (@available(iOS 14.0, *)) {
        PHPickerConfiguration *config = [[PHPickerConfiguration alloc] init];
        config.selectionLimit = MAX(1, 6 - (NSInteger)self.imagePaths.count);
        config.filter = [PHPickerFilter imagesFilter];
        PHPickerViewController *picker = [[PHPickerViewController alloc] initWithConfiguration:config];
        picker.delegate = self;
        [self presentViewController:picker animated:YES completion:nil];
    }
}

- (void)picker:(PHPickerViewController *)picker didFinishPicking:(NSArray<PHPickerResult *> *)results {
    [picker dismissViewControllerAnimated:YES completion:nil];
    NSURL *dir = [[[NSFileManager defaultManager] URLsForDirectory:NSApplicationSupportDirectory inDomains:NSUserDomainMask].lastObject
                  URLByAppendingPathComponent:@"feedback_images" isDirectory:YES];
    [[NSFileManager defaultManager] createDirectoryAtURL:dir withIntermediateDirectories:YES attributes:nil error:nil];
    for (PHPickerResult *result in results) {
        if (self.imagePaths.count >= 6) {
            break;
        }
        [result.itemProvider loadObjectOfClass:[UIImage class] completionHandler:^(id object, NSError *error) {
            UIImage *image = (UIImage *)object;
            if (![image isKindOfClass:[UIImage class]]) {
                return;
            }
            NSData *data = UIImageJPEGRepresentation(image, 0.85);
            if (data.length > 5 * 1024 * 1024) {
                return;
            }
            NSString *name = [[NSUUID UUID] UUIDString];
            NSURL *file = [dir URLByAppendingPathComponent:[name stringByAppendingPathExtension:@"jpg"]];
            [data writeToURL:file atomically:YES];
            dispatch_async(dispatch_get_main_queue(), ^{
                [self.imagePaths addObject:file.path];
            });
        }];
    }
}

- (void)submitFeedback {
    if (self.selectedTypeIndex < 0) {
        [self toast:@"请选择问题类型"];
        return;
    }
    NSString *content = [self.descView.text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (content.length < 10) {
        [self toast:@"描述至少 10 个字"];
        return;
    }
    FeedbackRecord *record = [[FeedbackRecord alloc] init];
    record.recordId = [FeedbackStore newFeedbackIdForDate:[NSDate date]];
    record.type = FeedbackTypeLabels()[(NSUInteger)self.selectedTypeIndex];
    record.submitTime = [NSDate date].timeIntervalSince1970;
    record.content = content;
    record.contact = self.contactField.text ?: @"";
    record.shareLog = self.shareLog;
    record.imagePaths = [self.imagePaths copy];
    record.status = FeedbackStatusProcessing;
    [FeedbackStore addRecord:record];
    [self.imagePaths removeAllObjects];
    [self showSuccess];
}

- (void)showSuccess {
    [self clearContent];
    UIButton *back = [self headerBackButtonWithAction:@selector(close)];
    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"提交成功";
    title.font = [UIFont boldSystemFontOfSize:20];
    UILabel *msg = [[UILabel alloc] init];
    msg.translatesAutoresizingMaskIntoConstraints = NO;
    msg.text = @"我们已收到您的反馈";
    msg.textAlignment = NSTextAlignmentCenter;
    UIButton *viewRecords = [UIButton buttonWithType:UIButtonTypeSystem];
    viewRecords.translatesAutoresizingMaskIntoConstraints = NO;
    [viewRecords setTitle:@"查看反馈记录" forState:UIControlStateNormal];
    [viewRecords addTarget:self action:@selector(showList) forControlEvents:UIControlEventTouchUpInside];
    for (UIView *v in @[ back, title, msg, viewRecords ]) {
        [self.contentView addSubview:v];
    }
    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:8],
        [back.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:12],
        [title.centerXAnchor constraintEqualToAnchor:self.contentView.centerXAnchor],
        [title.centerYAnchor constraintEqualToAnchor:self.contentView.centerYAnchor constant:-40],
        [msg.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:12],
        [msg.centerXAnchor constraintEqualToAnchor:self.contentView.centerXAnchor],
        [viewRecords.topAnchor constraintEqualToAnchor:msg.bottomAnchor constant:24],
        [viewRecords.centerXAnchor constraintEqualToAnchor:self.contentView.centerXAnchor],
    ]];
}

- (void)showList {
    NSArray<FeedbackRecord *> *records = [FeedbackStore loadRecords];
    if (records.count == 0) {
        [self showEmpty];
        return;
    }
    [self clearContent];
    UIButton *back = [self headerBackButtonWithAction:@selector(showForm)];
    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"反馈记录";
    title.font = [UIFont boldSystemFontOfSize:18];
    UITableView *table = [[UITableView alloc] initWithFrame:CGRectZero style:UITableViewStylePlain];
    table.translatesAutoresizingMaskIntoConstraints = NO;
    table.dataSource = (id<UITableViewDataSource>)self;
    table.delegate = (id<UITableViewDelegate>)self;
    table.tag = 7001;
    objc_setAssociatedObject(table, @selector(showList), records, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    for (UIView *v in @[ back, title, table ]) {
        [self.contentView addSubview:v];
    }
    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:8],
        [back.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:12],
        [title.centerYAnchor constraintEqualToAnchor:back.centerYAnchor],
        [title.leadingAnchor constraintEqualToAnchor:back.trailingAnchor constant:8],
        [table.topAnchor constraintEqualToAnchor:back.bottomAnchor constant:12],
        [table.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor],
        [table.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor],
        [table.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
    ]];
}

- (void)showEmpty {
    [self clearContent];
    UIButton *back = [self headerBackButtonWithAction:@selector(showForm)];
    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = @"暂无反馈记录";
    title.textAlignment = NSTextAlignmentCenter;
    for (UIView *v in @[ back, title ]) {
        [self.contentView addSubview:v];
    }
    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:8],
        [back.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:12],
        [title.centerXAnchor constraintEqualToAnchor:self.contentView.centerXAnchor],
        [title.centerYAnchor constraintEqualToAnchor:self.contentView.centerYAnchor],
    ]];
}

- (void)showDetail:(NSString *)recordId {
    FeedbackRecord *record = [FeedbackStore findRecordWithId:recordId];
    if (record == nil) {
        [self showList];
        return;
    }
    if ((record.status == FeedbackStatusSubmitted || record.status == FeedbackStatusProcessing)
        && [NSDate date].timeIntervalSince1970 - record.submitTime > 30) {
        [FeedbackStore simulateReplyForRecord:record];
        record = [FeedbackStore findRecordWithId:recordId];
    }
    self.currentDetail = record;
    [self clearContent];

    UIButton *back = [self headerBackButtonWithAction:@selector(showList)];
    UILabel *title = [[UILabel alloc] init];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    title.text = [NSString stringWithFormat:@"编号 %@", record.recordId];
    title.font = [UIFont boldSystemFontOfSize:16];
    UILabel *content = [[UILabel alloc] init];
    content.translatesAutoresizingMaskIntoConstraints = NO;
    content.numberOfLines = 0;
    content.text = [NSString stringWithFormat:@"%@\n\n%@", record.type, record.content];
    UILabel *reply = [[UILabel alloc] init];
    reply.translatesAutoresizingMaskIntoConstraints = NO;
    reply.numberOfLines = 0;
    reply.textColor = [UIColor colorWithRed:0 green:0.4 blue:1 alpha:1];
    if (record.status == FeedbackStatusReplied) {
        reply.text = [NSString stringWithFormat:@"客服回复：%@", record.replyText];
    }
    UIButton *closeBtn = [UIButton buttonWithType:UIButtonTypeSystem];
    closeBtn.translatesAutoresizingMaskIntoConstraints = NO;
    [closeBtn setTitle:@"关闭反馈" forState:UIControlStateNormal];
    [closeBtn addTarget:self action:@selector(closeFeedback) forControlEvents:UIControlEventTouchUpInside];
    closeBtn.hidden = record.status == FeedbackStatusClosed;
    for (UIView *v in @[ back, title, content, reply, closeBtn ]) {
        [self.contentView addSubview:v];
    }
    [NSLayoutConstraint activateConstraints:@[
        [back.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:8],
        [back.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:12],
        [title.topAnchor constraintEqualToAnchor:back.bottomAnchor constant:16],
        [title.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:16],
        [content.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:16],
        [content.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:16],
        [content.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor constant:-16],
        [reply.topAnchor constraintEqualToAnchor:content.bottomAnchor constant:16],
        [reply.leadingAnchor constraintEqualToAnchor:content.leadingAnchor],
        [reply.trailingAnchor constraintEqualToAnchor:content.trailingAnchor],
        [closeBtn.bottomAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.bottomAnchor constant:-24],
        [closeBtn.centerXAnchor constraintEqualToAnchor:self.contentView.centerXAnchor],
    ]];
}

- (void)closeFeedback {
    __weak __typeof(self) weakSelf = self;
    [HomeCardDialogPresenter presentConfirmFrom:self
                                          title:@"关闭反馈"
                                        message:@"问题已解决是否确认关闭该反馈"
                                   confirmStyle:HomeCardDialogConfirmStylePrimary
                                     completion:^(BOOL confirmed) {
        if (!confirmed) {
            return;
        }
        weakSelf.currentDetail.status = FeedbackStatusClosed;
        [FeedbackStore updateRecord:weakSelf.currentDetail];
        [weakSelf showDetail:weakSelf.currentDetail.recordId];
    }];
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    NSArray *records = objc_getAssociatedObject(tableView, @selector(showList));
    return (NSInteger)[records count];
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:@"fb"];
    if (cell == nil) {
        cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:@"fb"];
    }
    NSArray<FeedbackRecord *> *records = objc_getAssociatedObject(tableView, @selector(showList));
    FeedbackRecord *record = records[indexPath.row];
    cell.textLabel.text = record.type;
    cell.detailTextLabel.text = record.content;
    cell.accessoryType = UITableViewCellAccessoryDisclosureIndicator;
    return cell;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
    NSArray<FeedbackRecord *> *records = objc_getAssociatedObject(tableView, @selector(showList));
    [self showDetail:records[indexPath.row].recordId];
}

- (void)toast:(NSString *)message {
    [AppToastPresenter showMessage:message from:self];
}

- (void)close {
    [self dismissViewControllerAnimated:YES completion:nil];
}

@end
