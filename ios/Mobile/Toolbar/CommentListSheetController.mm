// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "CommentListSheetController.h"
#import "AI/WriterAIComponents.h"
#import <objc/runtime.h>

static const void *CommentInfoOverlayAssociationKey = &CommentInfoOverlayAssociationKey;

@implementation WriterCommentListItem
@end

@interface CommentInfoCardView : UIView
@property (nonatomic, copy) CommentInfoOverlayAction editHandler;
@property (nonatomic, copy) CommentInfoOverlayAction deleteHandler;
- (instancetype)initWithComment:(NSDictionary<NSString *, id> *)comment
                  ownAuthorName:(NSString *)ownAuthorName
                         avatar:(nullable UIImage *)avatar
                        canEdit:(BOOL)canEdit
                         onEdit:(CommentInfoOverlayAction)onEdit
                       onDelete:(CommentInfoOverlayAction)onDelete;
@end

@implementation CommentInfoCardView

- (instancetype)initWithComment:(NSDictionary<NSString *,id> *)comment
                  ownAuthorName:(NSString *)ownAuthorName
                         avatar:(UIImage *)avatar
                        canEdit:(BOOL)canEdit
                         onEdit:(CommentInfoOverlayAction)onEdit
                       onDelete:(CommentInfoOverlayAction)onDelete
{
    self = [super initWithFrame:CGRectZero];
    if (!self) return nil;
    self.translatesAutoresizingMaskIntoConstraints = NO;
    self.backgroundColor = [UIColor colorWithRed:242.0/255.0 green:243.0/255.0 blue:245.0/255.0 alpha:1];
    self.layer.cornerRadius = 12.0;
    self.clipsToBounds = YES;
    self.editHandler = onEdit;
    self.deleteHandler = onDelete;
    NSString *commentId = [comment[@"id"] isKindOfClass:[NSString class]] ? comment[@"id"] : @"";
    NSString *commentText = [comment[@"text"] isKindOfClass:[NSString class]] ? comment[@"text"] : @"";
    BOOL isRevision = [comment[@"kind"] isEqual:@"revision"];
    NSString *revisionType = [comment[@"revisionType"] isKindOfClass:[NSString class]] ? comment[@"revisionType"] : @"";
    NSString *description = [comment[@"description"] isKindOfClass:[NSString class]] ? comment[@"description"] : @"";
    NSString *author = [comment[@"author"] isKindOfClass:[NSString class]] ? comment[@"author"] : @"";
    NSString *dateTime = [comment[@"dateTime"] isKindOfClass:[NSString class]] ? comment[@"dateTime"] : @"";

    UIImageView *avatarView = [[UIImageView alloc] init];
    avatarView.translatesAutoresizingMaskIntoConstraints = NO;
    avatarView.contentMode = UIViewContentModeScaleAspectFill;
    avatarView.clipsToBounds = YES;
    avatarView.layer.cornerRadius = 16.0;
    UILabel *initials = [[UILabel alloc] init];
    initials.translatesAutoresizingMaskIntoConstraints = NO;
    initials.textAlignment = NSTextAlignmentCenter;
    initials.font = [UIFont systemFontOfSize:13 weight:UIFontWeightSemibold];
    initials.textColor = [UIColor colorWithWhite:0.38 alpha:1];
    initials.backgroundColor = [UIColor colorWithWhite:1 alpha:0.75];
    initials.clipsToBounds = YES;
    initials.layer.cornerRadius = 16.0;
    NSString *trimmedAuthor = [author stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    BOOL isOwnAuthor = trimmedAuthor.length > 0 && ownAuthorName.length > 0 &&
        [trimmedAuthor isEqualToString:[ownAuthorName stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]]];
    if (isOwnAuthor && avatar) {
        avatarView.image = avatar;
        initials.hidden = YES;
    } else {
        NSRange firstCharacter = trimmedAuthor.length > 0
            ? [trimmedAuthor rangeOfComposedCharacterSequenceAtIndex:0]
            : NSMakeRange(NSNotFound, 0);
        initials.text = firstCharacter.location != NSNotFound
            ? [trimmedAuthor substringWithRange:firstCharacter] : @"?";
        avatarView.hidden = YES;
    }
    UILabel *authorLabel = [[UILabel alloc] init];
    authorLabel.translatesAutoresizingMaskIntoConstraints = NO;
    authorLabel.font = [UIFont systemFontOfSize:14 weight:UIFontWeightMedium];
    authorLabel.textColor = [UIColor colorWithRed:51.0/255.0 green:51.0/255.0 blue:51.0/255.0 alpha:1];
    authorLabel.text = author.length > 0 ? author : @"批注";
    UILabel *dateLabel = [[UILabel alloc] init];
    dateLabel.translatesAutoresizingMaskIntoConstraints = NO;
    dateLabel.font = [UIFont systemFontOfSize:10];
    dateLabel.textColor = [UIColor colorWithWhite:0.45 alpha:1];
    NSDate *date = nil;
    if (dateTime.length > 0) {
        NSISO8601DateFormatter *isoFormatter = [[NSISO8601DateFormatter alloc] init];
        isoFormatter.formatOptions = NSISO8601DateFormatWithInternetDateTime | NSISO8601DateFormatWithFractionalSeconds;
        date = [isoFormatter dateFromString:dateTime];
        if (date == nil) {
            isoFormatter.formatOptions = NSISO8601DateFormatWithInternetDateTime;
            date = [isoFormatter dateFromString:dateTime];
        }
        if (date == nil) {
            NSDateFormatter *sourceFormatter = [[NSDateFormatter alloc] init];
            sourceFormatter.locale = [[NSLocale alloc] initWithLocaleIdentifier:@"en_US_POSIX"];
            sourceFormatter.dateFormat = @"yyyy-MM-dd HH:mm:ss";
            date = [sourceFormatter dateFromString:dateTime];
        }
    }
    if (date != nil) {
        NSDateFormatter *displayFormatter = [[NSDateFormatter alloc] init];
        displayFormatter.locale = [[NSLocale alloc] initWithLocaleIdentifier:@"en_US_POSIX"];
        displayFormatter.timeZone = NSTimeZone.localTimeZone;
        displayFormatter.dateFormat = @"yyyy-MM-dd HH:mm";
        dateLabel.text = [displayFormatter stringFromDate:date];
    } else {
        dateLabel.text = dateTime;
    }
    UIButton *edit = [UIButton buttonWithType:UIButtonTypeCustom];
    edit.translatesAutoresizingMaskIntoConstraints = NO;
    [edit setImage:[UIImage imageNamed:@"CommentInfoEdit"] forState:UIControlStateNormal];
    edit.accessibilityLabel = @"编辑批注";
    UIButton *deleteButton = [UIButton buttonWithType:UIButtonTypeCustom];
    deleteButton.translatesAutoresizingMaskIntoConstraints = NO;
    [deleteButton setImage:[UIImage imageNamed:@"CommentInfoDelete"] forState:UIControlStateNormal];
    deleteButton.accessibilityLabel = @"删除批注";
    UILabel *text = [[UILabel alloc] init];
    text.translatesAutoresizingMaskIntoConstraints = NO;
    text.font = [UIFont systemFontOfSize:14];
    text.textColor = [UIColor colorWithRed:51.0/255.0 green:51.0/255.0 blue:51.0/255.0 alpha:1];
    text.numberOfLines = 0;
    if (isRevision) {
        NSMutableArray<NSString *> *revisionDetails = [NSMutableArray array];
        if (revisionType.length > 0) [revisionDetails addObject:revisionType];
        if (description.length > 0) [revisionDetails addObject:description];
        if (commentText.length > 0) [revisionDetails addObject:commentText];
        text.text = revisionDetails.count > 0 ? [revisionDetails componentsJoinedByString:@"\n"] : @"修订信息不可用";
    } else {
        text.text = commentText.length > 0 ? commentText : @"(无内容)";
    }
    [self addSubview:avatarView];
    [self addSubview:initials];
    [self addSubview:authorLabel];
    [self addSubview:dateLabel];
    [self addSubview:edit];
    [self addSubview:deleteButton];
    [self addSubview:text];

    id resolvedValue = comment[@"resolved"];
    BOOL resolved = [resolvedValue isEqual:@YES] ||
        ([resolvedValue isKindOfClass:[NSString class]] && [resolvedValue caseInsensitiveCompare:@"true"] == NSOrderedSame);
    id ownAuthorValue = comment[@"isOwnAuthor"];
    BOOL commentIsOwnAuthor = [ownAuthorValue isKindOfClass:[NSNumber class]]
        ? [ownAuthorValue boolValue] : isOwnAuthor;
    BOOL commentCanEdit = [comment[@"canEdit"] isKindOfClass:[NSNumber class]]
        ? [comment[@"canEdit"] boolValue] : canEdit;
    BOOL canEditComment = !isRevision && commentCanEdit && commentIsOwnAuthor && !resolved;
    edit.hidden = !canEditComment;
    deleteButton.hidden = !canEditComment;
    edit.enabled = canEditComment;
    deleteButton.enabled = canEditComment;
    __weak CommentInfoCardView *weakSelf = self;
    [edit addAction:[UIAction actionWithHandler:^(__kindof UIAction *action) {
        CommentInfoCardView *strongSelf = weakSelf;
        if (strongSelf.editHandler && commentId.length > 0) strongSelf.editHandler(commentId, commentText);
    }] forControlEvents:UIControlEventTouchUpInside];
    [deleteButton addAction:[UIAction actionWithHandler:^(__kindof UIAction *action) {
        CommentInfoCardView *strongSelf = weakSelf;
        if (strongSelf.deleteHandler && commentId.length > 0) strongSelf.deleteHandler(commentId, commentText);
    }] forControlEvents:UIControlEventTouchUpInside];

    [NSLayoutConstraint activateConstraints:@[
        [avatarView.topAnchor constraintEqualToAnchor:self.topAnchor constant:12],
        [avatarView.leadingAnchor constraintEqualToAnchor:self.leadingAnchor constant:12],
        [avatarView.widthAnchor constraintEqualToConstant:32],
        [avatarView.heightAnchor constraintEqualToConstant:32],
        [initials.topAnchor constraintEqualToAnchor:avatarView.topAnchor],
        [initials.leadingAnchor constraintEqualToAnchor:avatarView.leadingAnchor],
        [initials.widthAnchor constraintEqualToAnchor:avatarView.widthAnchor],
        [initials.heightAnchor constraintEqualToAnchor:avatarView.heightAnchor],
        [authorLabel.topAnchor constraintEqualToAnchor:avatarView.topAnchor],
        [authorLabel.leadingAnchor constraintEqualToAnchor:avatarView.trailingAnchor constant:8],
        [authorLabel.trailingAnchor constraintLessThanOrEqualToAnchor:edit.leadingAnchor constant:-8],
        [dateLabel.topAnchor constraintEqualToAnchor:authorLabel.bottomAnchor constant:2],
        [dateLabel.leadingAnchor constraintEqualToAnchor:authorLabel.leadingAnchor],
        [dateLabel.trailingAnchor constraintLessThanOrEqualToAnchor:edit.leadingAnchor constant:-8],
        [edit.trailingAnchor constraintEqualToAnchor:deleteButton.leadingAnchor constant:-4],
        [edit.centerYAnchor constraintEqualToAnchor:avatarView.centerYAnchor],
        [edit.widthAnchor constraintEqualToConstant:canEditComment ? 28 : 0],
        [edit.heightAnchor constraintEqualToConstant:32],
        [deleteButton.trailingAnchor constraintEqualToAnchor:self.trailingAnchor constant:-8],
        [deleteButton.centerYAnchor constraintEqualToAnchor:avatarView.centerYAnchor],
        [deleteButton.widthAnchor constraintEqualToConstant:canEditComment ? 28 : 0],
        [deleteButton.heightAnchor constraintEqualToConstant:32],
        [text.topAnchor constraintEqualToAnchor:avatarView.bottomAnchor constant:10],
        [text.leadingAnchor constraintEqualToAnchor:self.leadingAnchor constant:12],
        [text.trailingAnchor constraintEqualToAnchor:self.trailingAnchor constant:-12],
        [text.bottomAnchor constraintEqualToAnchor:self.bottomAnchor constant:-12],
    ]];
    return self;
}

@end

@interface CommentInfoOverlay ()
@property (nonatomic, weak) UIViewController *host;
@property (nonatomic, strong) UIView *cardView;
@property (nonatomic, strong) UIButton *backButton;
@property (nonatomic, strong) UIScrollView *cardsScrollView;
@property (nonatomic, strong) UIStackView *cardsStack;
@property (nonatomic, strong) NSLayoutConstraint *cardHeightConstraint;
@property (nonatomic, copy) dispatch_block_t closeHandler;
@end

@implementation CommentInfoOverlay

+ (void)showInHostViewController:(UIViewController *)host
                         comment:(NSDictionary<NSString *,id> *)comment
                  ownAuthorName:(NSString *)ownAuthorName
                         avatar:(UIImage *)avatar
                         canEdit:(BOOL)canEdit
                          onClose:(dispatch_block_t)onClose
                           onEdit:(CommentInfoOverlayAction)onEdit
                         onDelete:(CommentInfoOverlayAction)onDelete
{
    if (!host.isViewLoaded || !host.view.window) return;
    CommentInfoOverlay *overlay = objc_getAssociatedObject(host, CommentInfoOverlayAssociationKey);
    if (!overlay) {
        overlay = [[CommentInfoOverlay alloc] initWithFrame:CGRectZero];
        overlay.translatesAutoresizingMaskIntoConstraints = NO;
        overlay.backgroundColor = UIColor.clearColor;
        overlay.host = host;
        [host.view addSubview:overlay];
        [NSLayoutConstraint activateConstraints:@[
            [overlay.topAnchor constraintEqualToAnchor:host.view.topAnchor],
            [overlay.leadingAnchor constraintEqualToAnchor:host.view.leadingAnchor],
            [overlay.trailingAnchor constraintEqualToAnchor:host.view.trailingAnchor],
            [overlay.bottomAnchor constraintEqualToAnchor:host.view.bottomAnchor],
        ]];
        objc_setAssociatedObject(host, CommentInfoOverlayAssociationKey, overlay, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    }
    overlay.closeHandler = onClose;
    [overlay updateComments:comment ownAuthorName:ownAuthorName avatar:avatar canEdit:canEdit onEdit:onEdit onDelete:onDelete];
    [host.view bringSubviewToFront:overlay];
}

+ (void)hideFromHostViewController:(UIViewController *)host
{
    CommentInfoOverlay *overlay = objc_getAssociatedObject(host, CommentInfoOverlayAssociationKey);
    [overlay removeFromSuperview];
    objc_setAssociatedObject(host, CommentInfoOverlayAssociationKey, nil, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

+ (BOOL)isVisibleInHostViewController:(UIViewController *)host
{
    CommentInfoOverlay *overlay = objc_getAssociatedObject(host, CommentInfoOverlayAssociationKey);
    return overlay != nil && !overlay.hidden && overlay.superview != nil;
}

- (instancetype)initWithFrame:(CGRect)frame
{
    self = [super initWithFrame:frame];
    if (!self) return nil;
    self.accessibilityIdentifier = @"writerCommentInfoOverlay";
    self.cardView = [[UIView alloc] initWithFrame:CGRectZero];
    self.cardView.translatesAutoresizingMaskIntoConstraints = NO;
    self.cardView.backgroundColor = UIColor.whiteColor;
    self.cardView.layer.cornerRadius = 16.0;
    self.cardView.layer.shadowColor = UIColor.blackColor.CGColor;
    self.cardView.layer.shadowOpacity = 0.08;
    self.cardView.layer.shadowRadius = 8.0;
    self.cardView.layer.shadowOffset = CGSizeMake(0, -2);
    [self addSubview:self.cardView];

    self.backButton = [UIButton buttonWithType:UIButtonTypeCustom];
    self.backButton.translatesAutoresizingMaskIntoConstraints = NO;
    [self.backButton setImage:[UIImage imageNamed:@"CommentInfoBack"] forState:UIControlStateNormal];
    [self.backButton setTitle:@"批注" forState:UIControlStateNormal];
    [self.backButton setTitleColor:[UIColor colorWithRed:51.0/255.0 green:51.0/255.0 blue:51.0/255.0 alpha:1] forState:UIControlStateNormal];
    self.backButton.titleLabel.font = [UIFont systemFontOfSize:15 weight:UIFontWeightMedium];
    self.backButton.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeft;
    self.backButton.imageView.contentMode = UIViewContentModeScaleAspectFit;
    self.backButton.imageEdgeInsets = UIEdgeInsetsMake(0, 0, 0, 4);
    self.backButton.titleEdgeInsets = UIEdgeInsetsMake(0, 8, 0, 0);
    [self.backButton addTarget:self action:@selector(closeTapped) forControlEvents:UIControlEventTouchUpInside];
    [self.cardView addSubview:self.backButton];
    UIView *container = [[UIView alloc] initWithFrame:CGRectZero];
    container.translatesAutoresizingMaskIntoConstraints = NO;
    [self.cardView addSubview:container];
    self.cardsScrollView = [[UIScrollView alloc] initWithFrame:CGRectZero];
    self.cardsScrollView.translatesAutoresizingMaskIntoConstraints = NO;
    self.cardsScrollView.alwaysBounceVertical = NO;
    [container addSubview:self.cardsScrollView];
    self.cardsStack = [[UIStackView alloc] initWithFrame:CGRectZero];
    self.cardsStack.translatesAutoresizingMaskIntoConstraints = NO;
    self.cardsStack.axis = UILayoutConstraintAxisVertical;
    self.cardsStack.spacing = 8;
    [self.cardsScrollView addSubview:self.cardsStack];

    self.cardHeightConstraint = [self.cardView.heightAnchor constraintEqualToConstant:280];
    [NSLayoutConstraint activateConstraints:@[
        [self.cardView.leadingAnchor constraintEqualToAnchor:self.leadingAnchor],
        [self.cardView.trailingAnchor constraintEqualToAnchor:self.trailingAnchor],
        [self.cardView.bottomAnchor constraintEqualToAnchor:self.safeAreaLayoutGuide.bottomAnchor],
        self.cardHeightConstraint,
        [self.backButton.topAnchor constraintEqualToAnchor:self.cardView.topAnchor constant:8],
        [self.backButton.leadingAnchor constraintEqualToAnchor:self.cardView.leadingAnchor constant:16],
        [self.backButton.heightAnchor constraintEqualToConstant:36],
        [self.backButton.widthAnchor constraintGreaterThanOrEqualToConstant:84],
        [container.topAnchor constraintEqualToAnchor:self.backButton.bottomAnchor constant:4],
        [container.leadingAnchor constraintEqualToAnchor:self.cardView.leadingAnchor constant:16],
        [container.trailingAnchor constraintEqualToAnchor:self.cardView.trailingAnchor constant:-16],
        [container.bottomAnchor constraintEqualToAnchor:self.cardView.bottomAnchor constant:-12],
        [self.cardsScrollView.topAnchor constraintEqualToAnchor:container.topAnchor],
        [self.cardsScrollView.leadingAnchor constraintEqualToAnchor:container.leadingAnchor],
        [self.cardsScrollView.trailingAnchor constraintEqualToAnchor:container.trailingAnchor],
        [self.cardsScrollView.bottomAnchor constraintEqualToAnchor:container.bottomAnchor],
        [self.cardsStack.topAnchor constraintEqualToAnchor:self.cardsScrollView.contentLayoutGuide.topAnchor],
        [self.cardsStack.leadingAnchor constraintEqualToAnchor:self.cardsScrollView.contentLayoutGuide.leadingAnchor],
        [self.cardsStack.trailingAnchor constraintEqualToAnchor:self.cardsScrollView.contentLayoutGuide.trailingAnchor],
        [self.cardsStack.bottomAnchor constraintEqualToAnchor:self.cardsScrollView.contentLayoutGuide.bottomAnchor],
        [self.cardsStack.widthAnchor constraintEqualToAnchor:self.cardsScrollView.frameLayoutGuide.widthAnchor],
    ]];
    return self;
}

- (void)layoutSubviews
{
    [super layoutSubviews];
    CGFloat maximumHeight = self.bounds.size.height * 0.45;
    if (maximumHeight > 0) self.cardHeightConstraint.constant = MIN(320.0, maximumHeight);
}

- (BOOL)pointInside:(CGPoint)point withEvent:(UIEvent *)event
{
    return CGRectContainsPoint(self.cardView.frame, point);
}

- (void)updateComments:(NSDictionary<NSString *,id> *)payload
        ownAuthorName:(NSString *)ownAuthorName
               avatar:(UIImage *)avatar
              canEdit:(BOOL)canEdit
               onEdit:(CommentInfoOverlayAction)onEdit
             onDelete:(CommentInfoOverlayAction)onDelete
{
    NSString *kind = [payload[@"kind"] isKindOfClass:[NSString class]] ? payload[@"kind"] : @"comment";
    NSString *title = [kind isEqualToString:@"revision"] ? @"修订" : @"批注";
    [self.backButton setTitle:title forState:UIControlStateNormal];
    [self.cardsStack.arrangedSubviews makeObjectsPerformSelector:@selector(removeFromSuperview)];
    NSArray *comments = [payload[@"comments"] isKindOfClass:[NSArray class]] ? payload[@"comments"] : @[payload];
    for (id item in comments) {
        if (![item isKindOfClass:[NSDictionary class]]) continue;
        NSMutableDictionary *comment = [item mutableCopy];
        if (comment[@"kind"] == nil) comment[@"kind"] = kind;
        NSString *commentId = [comment[@"id"] isKindOfClass:[NSString class]] ? comment[@"id"] : @"";
        if (commentId.length == 0) continue;
        CommentInfoCardView *card = [[CommentInfoCardView alloc] initWithComment:comment
            ownAuthorName:ownAuthorName avatar:avatar canEdit:canEdit onEdit:onEdit onDelete:onDelete];
        [self.cardsStack addArrangedSubview:card];
    }
    if (self.cardsStack.arrangedSubviews.count == 0) {
        CommentInfoCardView *card = [[CommentInfoCardView alloc] initWithComment:payload
            ownAuthorName:ownAuthorName avatar:avatar canEdit:canEdit onEdit:onEdit onDelete:onDelete];
        [self.cardsStack addArrangedSubview:card];
    }
    [self.cardsScrollView setContentOffset:CGPointZero animated:NO];
    [self setNeedsLayout];
}

- (void)closeTapped
{
    if (self.closeHandler) self.closeHandler();
    if (self.host) [CommentInfoOverlay hideFromHostViewController:self.host];
}

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
