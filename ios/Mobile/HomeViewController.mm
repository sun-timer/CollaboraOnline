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
#import "DocumentPresentationLaunchOptions.h"
#import "Settings/AppChromeHelper.h"
#import "Settings/AppIcons.h"
#import "Settings/CreateFileBottomSheetController.h"
#import "Settings/HomeCardDialogPresenter.h"

#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>
#import "AI/WriterAIComponents.h"

static NSString *const kHomeRecentCellId = @"homeRecentCell";
static NSString *const kHomeGridCellId = @"homeGridCell";
static NSString *const kHomeGridModeKey = @"HOME_RECENT_GRID_MODE";

@interface HomeRecentCell : UITableViewCell
@property (strong, nonatomic) UIImageView *fileIconView;
@property (strong, nonatomic) UILabel *nameLabel;
@property (strong, nonatomic) UILabel *dateLabel;
@property (strong, nonatomic) UIButton *moreButton;
@property (copy, nonatomic) void (^moreAction)(void);
@end

@implementation HomeRecentCell

- (instancetype)initWithStyle:(UITableViewCellStyle)style reuseIdentifier:(NSString *)reuseIdentifier {
    self = [super initWithStyle:style reuseIdentifier:reuseIdentifier];
    if (self) {
        self.selectionStyle = UITableViewCellSelectionStyleDefault;
        self.fileIconView = [[UIImageView alloc] init];
        self.fileIconView.translatesAutoresizingMaskIntoConstraints = NO;
        self.fileIconView.contentMode = UIViewContentModeScaleAspectFit;
        [self.contentView addSubview:self.fileIconView];

        self.nameLabel = [[UILabel alloc] init];
        self.nameLabel.translatesAutoresizingMaskIntoConstraints = NO;
        self.nameLabel.font = [UIFont systemFontOfSize:16 weight:UIFontWeightBold];
        self.nameLabel.textColor = [UIColor colorWithRed:0x33 / 255.0 green:0x33 / 255.0 blue:0x33 / 255.0 alpha:1];
        self.nameLabel.lineBreakMode = NSLineBreakByTruncatingTail;
        [self.contentView addSubview:self.nameLabel];

        self.dateLabel = [[UILabel alloc] init];
        self.dateLabel.translatesAutoresizingMaskIntoConstraints = NO;
        self.dateLabel.font = [UIFont systemFontOfSize:12];
        self.dateLabel.textColor = [UIColor colorWithRed:0 green:0 blue:0 alpha:0.4];
        [self.contentView addSubview:self.dateLabel];

        self.moreButton = [UIButton buttonWithType:UIButtonTypeSystem];
        self.moreButton.translatesAutoresizingMaskIntoConstraints = NO;
        [self.moreButton setImage:[AppIcons iconNamed:@"more"] forState:UIControlStateNormal];
        self.moreButton.tintColor = [UIColor colorWithWhite:0 alpha:0.35];
        [self.moreButton addTarget:self action:@selector(moreTapped) forControlEvents:UIControlEventTouchUpInside];
        [self.contentView addSubview:self.moreButton];

        [NSLayoutConstraint activateConstraints:@[
            [self.fileIconView.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:16],
            [self.fileIconView.centerYAnchor constraintEqualToAnchor:self.contentView.centerYAnchor],
            [self.fileIconView.widthAnchor constraintEqualToConstant:39],
            [self.fileIconView.heightAnchor constraintEqualToConstant:39],
            [self.moreButton.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor constant:-12],
            [self.moreButton.centerYAnchor constraintEqualToAnchor:self.contentView.centerYAnchor],
            [self.moreButton.widthAnchor constraintEqualToConstant:28],
            [self.moreButton.heightAnchor constraintEqualToConstant:28],
            [self.nameLabel.leadingAnchor constraintEqualToAnchor:self.fileIconView.trailingAnchor constant:12],
            [self.nameLabel.trailingAnchor constraintEqualToAnchor:self.moreButton.leadingAnchor constant:-8],
            [self.nameLabel.topAnchor constraintEqualToAnchor:self.contentView.topAnchor constant:12],
            [self.dateLabel.leadingAnchor constraintEqualToAnchor:self.nameLabel.leadingAnchor],
            [self.dateLabel.trailingAnchor constraintEqualToAnchor:self.nameLabel.trailingAnchor],
            [self.dateLabel.topAnchor constraintEqualToAnchor:self.nameLabel.bottomAnchor constant:4],
            [self.dateLabel.bottomAnchor constraintLessThanOrEqualToAnchor:self.contentView.bottomAnchor constant:-12],
        ]];
    }
    return self;
}

- (void)moreTapped {
    if (self.moreAction) {
        self.moreAction();
    }
}

@end

@interface HomeGridCell : UICollectionViewCell
@property (strong, nonatomic) UIImageView *fileIconView;
@property (strong, nonatomic) UILabel *nameLabel;
@property (strong, nonatomic) UILabel *dateLabel;
@end

@implementation HomeGridCell

- (instancetype)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    if (self) {
        self.fileIconView = [[UIImageView alloc] init];
        self.fileIconView.translatesAutoresizingMaskIntoConstraints = NO;
        self.fileIconView.contentMode = UIViewContentModeScaleAspectFit;
        [self.contentView addSubview:self.fileIconView];

        self.nameLabel = [[UILabel alloc] init];
        self.nameLabel.translatesAutoresizingMaskIntoConstraints = NO;
        self.nameLabel.font = [UIFont systemFontOfSize:14 weight:UIFontWeightBold];
        self.nameLabel.textColor = [UIColor colorWithWhite:0.2 alpha:1];
        self.nameLabel.textAlignment = NSTextAlignmentCenter;
        self.nameLabel.lineBreakMode = NSLineBreakByTruncatingTail;
        [self.contentView addSubview:self.nameLabel];

        self.dateLabel = [[UILabel alloc] init];
        self.dateLabel.translatesAutoresizingMaskIntoConstraints = NO;
        self.dateLabel.font = [UIFont systemFontOfSize:11];
        self.dateLabel.textColor = [UIColor colorWithWhite:0 alpha:0.4];
        self.dateLabel.textAlignment = NSTextAlignmentCenter;
        [self.contentView addSubview:self.dateLabel];

        [NSLayoutConstraint activateConstraints:@[
            [self.fileIconView.topAnchor constraintEqualToAnchor:self.contentView.topAnchor constant:12],
            [self.fileIconView.centerXAnchor constraintEqualToAnchor:self.contentView.centerXAnchor],
            [self.fileIconView.widthAnchor constraintEqualToConstant:67],
            [self.fileIconView.heightAnchor constraintEqualToConstant:67],
            [self.nameLabel.topAnchor constraintEqualToAnchor:self.fileIconView.bottomAnchor constant:8],
            [self.nameLabel.leadingAnchor constraintEqualToAnchor:self.contentView.leadingAnchor constant:8],
            [self.nameLabel.trailingAnchor constraintEqualToAnchor:self.contentView.trailingAnchor constant:-8],
            [self.dateLabel.topAnchor constraintEqualToAnchor:self.nameLabel.bottomAnchor constant:4],
            [self.dateLabel.leadingAnchor constraintEqualToAnchor:self.nameLabel.leadingAnchor],
            [self.dateLabel.trailingAnchor constraintEqualToAnchor:self.nameLabel.trailingAnchor],
        ]];
    }
    return self;
}

@end

@interface HomeViewController () <UITableViewDataSource, UITableViewDelegate, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout, UIDocumentPickerDelegate, UITextFieldDelegate>
@property (strong, nonatomic) RecentDocumentsStore *recentStore;
@property (strong, nonatomic) AISettingsDrawerController *drawer;
@property (strong, nonatomic) NSArray<RecentDocumentItem *> *visibleItems;
@property (assign, nonatomic) NSUInteger totalCount;
@property (assign, nonatomic) BOOL gridMode;
@property (strong, nonatomic) UIView *topBar;
@property (strong, nonatomic) UIButton *avatarButton;
@property (strong, nonatomic) UIView *searchBox;
@property (strong, nonatomic) UITextField *searchField;
@property (strong, nonatomic) UIButton *openFileButton;
@property (strong, nonatomic) UIView *recentsHeaderRow;
@property (strong, nonatomic) UILabel *recentsHeaderLabel;
@property (strong, nonatomic) UIButton *layoutToggleButton;
@property (strong, nonatomic) UITableView *tableView;
@property (strong, nonatomic) UICollectionView *collectionView;
@property (strong, nonatomic) UIView *emptyRecentState;
@property (strong, nonatomic) UIView *emptySearchState;
@property (strong, nonatomic) UIButton *fabButton;
@property (strong, nonatomic) UIButton *fabCloseButton;
@property (strong, nonatomic) UIView *fabOverlay;
@property (strong, nonatomic) UIView *fabMenuCard;
@property (assign, nonatomic) BOOL fabMenuOpen;
@property (strong, nonatomic) NSLayoutConstraint *contentTopToHeader;
@property (strong, nonatomic) NSLayoutConstraint *contentTopToTopBar;
@property (strong, nonatomic) UIView *actionsDismissOverlay;
@property (strong, nonatomic) UIView *actionsPopup;
@property (strong, nonatomic) RecentDocumentItem *actionsPopupItem;
@end

@implementation HomeViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = UIColor.whiteColor;
    self.view.accessibilityIdentifier = @"homeRoot";
    self.recentStore = [[RecentDocumentsStore alloc] init];
    self.gridMode = [[NSUserDefaults standardUserDefaults] boolForKey:kHomeGridModeKey];
    self.visibleItems = @[];

    [self buildTopBar];
    [self buildRecentsHeader];
    [self buildContentViews];
    [self buildEmptyStates];
    [self buildFab];
    [self layoutChrome];

    self.drawer = [AISettingsDrawerController attachToHost:self];
    [self.drawer requireFailureOfScrollViewGestures:self.tableView];
    [self.drawer requireFailureOfScrollViewGestures:self.collectionView];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [self.recentStore importLocalTestFiles];
    [self reloadAvatar];
    [self reloadRecents];
}

- (UIColor *)chromePlateColor {
    return [AppChromeHelper homePlateColor];
}

- (UIColor *)fabAccentColor {
    return [AppChromeHelper homeFabColor];
}

- (UIImage *)scaledIconNamed:(NSString *)name size:(CGFloat)size {
    UIImage *icon = [AppIcons iconNamed:name];
    if (icon == nil) {
        return nil;
    }
    UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:CGSizeMake(size, size)];
    return [renderer imageWithActions:^(UIGraphicsImageRendererContext *context) {
        [icon drawInRect:CGRectMake(0, 0, size, size)];
    }];
}

- (void)buildTopBar {
    self.topBar = [[UIView alloc] init];
    self.topBar.translatesAutoresizingMaskIntoConstraints = NO;
    self.topBar.backgroundColor = [self chromePlateColor];
    [self.view addSubview:self.topBar];

    self.avatarButton = [UIButton buttonWithType:UIButtonTypeCustom];
    self.avatarButton.translatesAutoresizingMaskIntoConstraints = NO;
    self.avatarButton.layer.cornerRadius = 16;
    self.avatarButton.clipsToBounds = YES;
    self.avatarButton.accessibilityIdentifier = @"homeAvatarButton";
    self.avatarButton.accessibilityLabel = @"打开设置";
    [self.avatarButton setImage:[AppIcons iconNamed:@"avatar"] forState:UIControlStateNormal];
    self.avatarButton.imageView.contentMode = UIViewContentModeScaleAspectFill;
    [self.avatarButton addTarget:self action:@selector(openDrawer) forControlEvents:UIControlEventTouchUpInside];
    [self.topBar addSubview:self.avatarButton];

    self.searchBox = [[UIView alloc] init];
    self.searchBox.translatesAutoresizingMaskIntoConstraints = NO;
    self.searchBox.backgroundColor = UIColor.whiteColor;
    self.searchBox.layer.cornerRadius = 16;
    [self.topBar addSubview:self.searchBox];

    UIImageView *searchIcon = [[UIImageView alloc] initWithImage:[AppIcons iconNamed:@"search"]];
    searchIcon.translatesAutoresizingMaskIntoConstraints = NO;
    searchIcon.contentMode = UIViewContentModeScaleAspectFit;
    searchIcon.tintColor = [UIColor colorWithRed:0x99 / 255.0 green:0x99 / 255.0 blue:0x99 / 255.0 alpha:1];
    [self.searchBox addSubview:searchIcon];

    self.searchField = [[UITextField alloc] init];
    self.searchField.translatesAutoresizingMaskIntoConstraints = NO;
    self.searchField.font = [UIFont systemFontOfSize:16];
    self.searchField.textColor = [UIColor colorWithRed:0x33 / 255.0 green:0x33 / 255.0 blue:0x33 / 255.0 alpha:1];
    self.searchField.attributedPlaceholder = [[NSAttributedString alloc] initWithString:@"搜索"
                                                                             attributes:@{
        NSForegroundColorAttributeName: [UIColor colorWithRed:0x99 / 255.0 green:0x99 / 255.0 blue:0x99 / 255.0 alpha:1],
        NSFontAttributeName: [UIFont systemFontOfSize:16],
    }];
    self.searchField.delegate = self;
    self.searchField.accessibilityIdentifier = @"homeSearchField";
    self.searchField.clearButtonMode = UITextFieldViewModeWhileEditing;
    self.searchField.returnKeyType = UIReturnKeySearch;
    [self.searchField addTarget:self action:@selector(searchChanged) forControlEvents:UIControlEventEditingChanged];
    [self.searchBox addSubview:self.searchField];

    self.openFileButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.openFileButton.translatesAutoresizingMaskIntoConstraints = NO;
    UIImage *folderIcon = [[self scaledIconNamed:@"folder" size:24] imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
    [self.openFileButton setImage:folderIcon forState:UIControlStateNormal];
    self.openFileButton.tintColor = [UIColor colorWithRed:0x43 / 255.0 green:0x43 / 255.0 blue:0x43 / 255.0 alpha:1];
    self.openFileButton.accessibilityIdentifier = @"homeOpenFileButton";
    self.openFileButton.accessibilityLabel = @"打开";
    [self.openFileButton addTarget:self action:@selector(openFile) forControlEvents:UIControlEventTouchUpInside];
    [self.topBar addSubview:self.openFileButton];

    [NSLayoutConstraint activateConstraints:@[
        [self.avatarButton.widthAnchor constraintEqualToConstant:32],
        [self.avatarButton.heightAnchor constraintEqualToConstant:32],
        [searchIcon.leadingAnchor constraintEqualToAnchor:self.searchBox.leadingAnchor constant:12],
        [searchIcon.centerYAnchor constraintEqualToAnchor:self.searchBox.centerYAnchor],
        [searchIcon.widthAnchor constraintEqualToConstant:20],
        [searchIcon.heightAnchor constraintEqualToConstant:20],
        [self.searchField.leadingAnchor constraintEqualToAnchor:searchIcon.trailingAnchor constant:8],
        [self.searchField.trailingAnchor constraintEqualToAnchor:self.searchBox.trailingAnchor constant:-12],
        [self.searchField.centerYAnchor constraintEqualToAnchor:self.searchBox.centerYAnchor],
        [self.openFileButton.widthAnchor constraintEqualToConstant:48],
        [self.openFileButton.heightAnchor constraintEqualToConstant:48],
    ]];
}

- (void)buildRecentsHeader {
    self.recentsHeaderRow = [[UIView alloc] init];
    self.recentsHeaderRow.translatesAutoresizingMaskIntoConstraints = NO;
    self.recentsHeaderRow.backgroundColor = UIColor.whiteColor;
    [self.view addSubview:self.recentsHeaderRow];

    self.recentsHeaderLabel = [[UILabel alloc] init];
    self.recentsHeaderLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.recentsHeaderLabel.text = @"最近打开";
    self.recentsHeaderLabel.font = [UIFont systemFontOfSize:16 weight:UIFontWeightBold];
    self.recentsHeaderLabel.textColor = [UIColor colorWithRed:0x6F / 255.0 green:0x73 / 255.0 blue:0x78 / 255.0 alpha:1];
    self.recentsHeaderLabel.accessibilityIdentifier = @"homeRecentsHeader";
    [self.recentsHeaderRow addSubview:self.recentsHeaderLabel];

    self.layoutToggleButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.layoutToggleButton.translatesAutoresizingMaskIntoConstraints = NO;
    self.layoutToggleButton.tintColor = [UIColor colorWithRed:111 / 255.0 green:115 / 255.0 blue:120 / 255.0 alpha:1];
    self.layoutToggleButton.accessibilityIdentifier = @"homeLayoutToggle";
    [self.layoutToggleButton addTarget:self action:@selector(toggleLayoutMode) forControlEvents:UIControlEventTouchUpInside];
    [self.recentsHeaderRow addSubview:self.layoutToggleButton];
    [self updateLayoutToggleIcon];

    [NSLayoutConstraint activateConstraints:@[
        [self.recentsHeaderLabel.leadingAnchor constraintEqualToAnchor:self.recentsHeaderRow.leadingAnchor constant:16],
        [self.recentsHeaderLabel.centerYAnchor constraintEqualToAnchor:self.recentsHeaderRow.centerYAnchor],
        [self.layoutToggleButton.trailingAnchor constraintEqualToAnchor:self.recentsHeaderRow.trailingAnchor constant:-16],
        [self.layoutToggleButton.centerYAnchor constraintEqualToAnchor:self.recentsHeaderRow.centerYAnchor],
        [self.layoutToggleButton.widthAnchor constraintEqualToConstant:28],
        [self.layoutToggleButton.heightAnchor constraintEqualToConstant:28],
    ]];
}

- (void)buildContentViews {
    self.tableView = [[UITableView alloc] initWithFrame:CGRectZero style:UITableViewStylePlain];
    self.tableView.translatesAutoresizingMaskIntoConstraints = NO;
    self.tableView.dataSource = self;
    self.tableView.delegate = self;
    self.tableView.rowHeight = 59;
    self.tableView.contentInset = UIEdgeInsetsMake(0, 0, 140, 0);
    self.tableView.separatorInset = UIEdgeInsetsMake(0, 16, 0, 16);
    self.tableView.tableFooterView = [[UIView alloc] init];
    self.tableView.accessibilityIdentifier = @"homeRecentsTable";
    [self.tableView registerClass:[HomeRecentCell class] forCellReuseIdentifier:kHomeRecentCellId];
    [self.view addSubview:self.tableView];

    UICollectionViewFlowLayout *layout = [[UICollectionViewFlowLayout alloc] init];
    layout.minimumInteritemSpacing = 8;
    layout.minimumLineSpacing = 12;
    layout.sectionInset = UIEdgeInsetsMake(8, 16, 140, 16);
    self.collectionView = [[UICollectionView alloc] initWithFrame:CGRectZero collectionViewLayout:layout];
    self.collectionView.translatesAutoresizingMaskIntoConstraints = NO;
    self.collectionView.backgroundColor = UIColor.whiteColor;
    self.collectionView.dataSource = self;
    self.collectionView.delegate = self;
    self.collectionView.hidden = YES;
    self.collectionView.accessibilityIdentifier = @"homeRecentsGrid";
    [self.collectionView registerClass:[HomeGridCell class] forCellWithReuseIdentifier:kHomeGridCellId];
    [self.view addSubview:self.collectionView];
}

- (UIView *)buildEmptyContainerWithImage:(NSString *)imageName
                                   title:(NSString *)title
                                subtitle:(NSString *)subtitle
                            retryVisible:(BOOL)retryVisible {
    UIView *container = [[UIView alloc] init];
    container.translatesAutoresizingMaskIntoConstraints = NO;
    container.hidden = YES;

    UIImageView *imageView = [[UIImageView alloc] initWithImage:[AppIcons iconNamed:imageName] ?: [UIImage imageNamed:imageName]];
    imageView.translatesAutoresizingMaskIntoConstraints = NO;
    imageView.contentMode = UIViewContentModeScaleAspectFit;
    imageView.alpha = 1.0;
    [container addSubview:imageView];

    UILabel *titleLabel = [[UILabel alloc] init];
    titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    titleLabel.text = title;
    titleLabel.font = [UIFont systemFontOfSize:14];
    titleLabel.textColor = [UIColor colorWithRed:0 green:0 blue:0 alpha:0.3];
    titleLabel.textAlignment = NSTextAlignmentCenter;
    [container addSubview:titleLabel];

    UILabel *subtitleLabel = [[UILabel alloc] init];
    subtitleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    subtitleLabel.text = subtitle;
    subtitleLabel.font = [UIFont systemFontOfSize:14];
    subtitleLabel.textColor = [UIColor colorWithRed:0 green:0 blue:0 alpha:0.3];
    subtitleLabel.textAlignment = NSTextAlignmentCenter;
    subtitleLabel.numberOfLines = 0;
    subtitleLabel.hidden = subtitle.length == 0;
    [container addSubview:subtitleLabel];

    UIButton *retry = [UIButton buttonWithType:UIButtonTypeSystem];
    retry.translatesAutoresizingMaskIntoConstraints = NO;
    [retry setTitle:@"重试" forState:UIControlStateNormal];
    [retry setTitleColor:[UIColor colorWithRed:0 green:0 blue:0 alpha:0.9] forState:UIControlStateNormal];
    retry.titleLabel.font = [UIFont systemFontOfSize:16];
    retry.backgroundColor = [UIColor colorWithWhite:0 alpha:0.06];
    retry.layer.cornerRadius = 17.5;
    retry.hidden = !retryVisible;
    retry.accessibilityIdentifier = @"homeSearchRetry";
    [retry addTarget:self action:@selector(retrySearch) forControlEvents:UIControlEventTouchUpInside];
    [container addSubview:retry];

    [NSLayoutConstraint activateConstraints:@[
        [imageView.centerXAnchor constraintEqualToAnchor:container.centerXAnchor],
        [imageView.centerYAnchor constraintEqualToAnchor:container.centerYAnchor constant:-40],
        [imageView.widthAnchor constraintEqualToConstant:268],
        [imageView.heightAnchor constraintEqualToConstant:190],
        [titleLabel.topAnchor constraintEqualToAnchor:imageView.bottomAnchor constant:0],
        [titleLabel.leadingAnchor constraintEqualToAnchor:container.leadingAnchor constant:24],
        [titleLabel.trailingAnchor constraintEqualToAnchor:container.trailingAnchor constant:-24],
        [subtitleLabel.topAnchor constraintEqualToAnchor:titleLabel.bottomAnchor constant:4],
        [subtitleLabel.leadingAnchor constraintEqualToAnchor:titleLabel.leadingAnchor],
        [subtitleLabel.trailingAnchor constraintEqualToAnchor:titleLabel.trailingAnchor],
        [retry.topAnchor constraintEqualToAnchor:titleLabel.bottomAnchor constant:62],
        [retry.centerXAnchor constraintEqualToAnchor:container.centerXAnchor],
        [retry.widthAnchor constraintEqualToConstant:134],
        [retry.heightAnchor constraintEqualToConstant:35],
    ]];
    return container;
}

- (void)buildEmptyStates {
    self.emptyRecentState = [self buildEmptyContainerWithImage:@"empty-recent"
                                                         title:@"没有最近的文档"
                                                      subtitle:@"创建或导入文件以开始使用"
                                                  retryVisible:NO];
    self.emptyRecentState.accessibilityIdentifier = @"homeEmptyRecent";
    [self.view addSubview:self.emptyRecentState];

    self.emptySearchState = [self buildEmptyContainerWithImage:@"empty-search"
                                                         title:@"搜索结果为空"
                                                      subtitle:nil
                                                  retryVisible:YES];
    self.emptySearchState.accessibilityIdentifier = @"homeEmptySearch";
    [self.view addSubview:self.emptySearchState];
}

- (void)buildFab {
    self.fabOverlay = [[UIView alloc] init];
    self.fabOverlay.translatesAutoresizingMaskIntoConstraints = NO;
    self.fabOverlay.backgroundColor = UIColor.clearColor;
    self.fabOverlay.hidden = YES;
    UITapGestureRecognizer *tap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(closeFabMenu)];
    [self.fabOverlay addGestureRecognizer:tap];
    [self.view addSubview:self.fabOverlay];

    self.fabMenuCard = [[UIView alloc] init];
    self.fabMenuCard.translatesAutoresizingMaskIntoConstraints = NO;
    self.fabMenuCard.backgroundColor = UIColor.whiteColor;
    self.fabMenuCard.layer.cornerRadius = 12;
    self.fabMenuCard.layer.shadowColor = UIColor.blackColor.CGColor;
    self.fabMenuCard.layer.shadowOpacity = 0.16;
    self.fabMenuCard.layer.shadowRadius = 12;
    self.fabMenuCard.layer.shadowOffset = CGSizeMake(0, 4);
    self.fabMenuCard.hidden = YES;
    self.fabMenuCard.userInteractionEnabled = YES;
    [self.view addSubview:self.fabMenuCard];

    UIStackView *stack = [[UIStackView alloc] init];
    stack.translatesAutoresizingMaskIntoConstraints = NO;
    stack.axis = UILayoutConstraintAxisVertical;
    stack.layoutMargins = UIEdgeInsetsMake(4, 7, 4, 7);
    stack.layoutMarginsRelativeArrangement = YES;
    [self.fabMenuCard addSubview:stack];

    [stack addArrangedSubview:[self newDocRowWithTitle:@"文本文档"
                                                  icon:@"file-writer"
                                                action:@selector(createWriter)]];
    [stack addArrangedSubview:[self newDocRowWithTitle:@"电子表格"
                                                  icon:@"file-calc"
                                                action:@selector(createCalc)]];
    [stack addArrangedSubview:[self newDocRowWithTitle:@"演示文稿"
                                                  icon:@"file-impress"
                                                action:@selector(createImpress)]];

    [NSLayoutConstraint activateConstraints:@[
        [stack.topAnchor constraintEqualToAnchor:self.fabMenuCard.topAnchor],
        [stack.leadingAnchor constraintEqualToAnchor:self.fabMenuCard.leadingAnchor],
        [stack.trailingAnchor constraintEqualToAnchor:self.fabMenuCard.trailingAnchor],
        [stack.bottomAnchor constraintEqualToAnchor:self.fabMenuCard.bottomAnchor],
        [self.fabMenuCard.widthAnchor constraintEqualToConstant:180],
    ]];

    self.fabButton = [UIButton buttonWithType:UIButtonTypeCustom];
    self.fabButton.translatesAutoresizingMaskIntoConstraints = NO;
    self.fabButton.backgroundColor = [self fabAccentColor];
    self.fabButton.layer.cornerRadius = 30;
    self.fabButton.clipsToBounds = YES;
    UIImage *fabIcon = [[self scaledIconNamed:@"fab" size:17] imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
    [self.fabButton setImage:fabIcon forState:UIControlStateNormal];
    self.fabButton.tintColor = UIColor.whiteColor;
    self.fabButton.imageView.contentMode = UIViewContentModeScaleAspectFit;
    self.fabButton.accessibilityIdentifier = @"homeFab";
    self.fabButton.accessibilityLabel = @"新建文档";
    [self.fabButton addTarget:self action:@selector(openFabMenu) forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:self.fabButton];

    self.fabCloseButton = [UIButton buttonWithType:UIButtonTypeCustom];
    self.fabCloseButton.translatesAutoresizingMaskIntoConstraints = NO;
    self.fabCloseButton.backgroundColor = [self fabAccentColor];
    self.fabCloseButton.layer.cornerRadius = 30;
    self.fabCloseButton.clipsToBounds = YES;
    self.fabCloseButton.hidden = YES;
    self.fabCloseButton.accessibilityIdentifier = @"homeFabClose";
    self.fabCloseButton.accessibilityLabel = @"关闭新建菜单";
    UIImage *closeIcon = [[self scaledIconNamed:@"fab-close" size:17] imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
    [self.fabCloseButton setImage:closeIcon forState:UIControlStateNormal];
    self.fabCloseButton.tintColor = UIColor.whiteColor;
    self.fabCloseButton.imageView.contentMode = UIViewContentModeScaleAspectFit;
    [self.fabCloseButton addTarget:self action:@selector(closeFabMenu) forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:self.fabCloseButton];
}

- (UIView *)newDocRowWithTitle:(NSString *)title icon:(NSString *)icon action:(SEL)action {
    UIControl *row = [[UIControl alloc] init];
    row.translatesAutoresizingMaskIntoConstraints = NO;
    [row.heightAnchor constraintEqualToConstant:48].active = YES;
    [row addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];

    UIImageView *iconView = [[UIImageView alloc] initWithImage:[AppIcons iconNamed:icon] ?: [UIImage imageNamed:icon]];
    iconView.translatesAutoresizingMaskIntoConstraints = NO;
    iconView.contentMode = UIViewContentModeScaleAspectFit;
    [row addSubview:iconView];

    UILabel *label = [[UILabel alloc] init];
    label.translatesAutoresizingMaskIntoConstraints = NO;
    label.text = title;
    label.font = [UIFont systemFontOfSize:14];
    label.textColor = [UIColor colorWithRed:0x33 / 255.0 green:0x33 / 255.0 blue:0x33 / 255.0 alpha:1];
    [row addSubview:label];

    [NSLayoutConstraint activateConstraints:@[
        [iconView.leadingAnchor constraintEqualToAnchor:row.leadingAnchor constant:8],
        [iconView.centerYAnchor constraintEqualToAnchor:row.centerYAnchor],
        [iconView.widthAnchor constraintEqualToConstant:32],
        [iconView.heightAnchor constraintEqualToConstant:32],
        [label.leadingAnchor constraintEqualToAnchor:iconView.trailingAnchor constant:8],
        [label.trailingAnchor constraintEqualToAnchor:row.trailingAnchor constant:-8],
        [label.centerYAnchor constraintEqualToAnchor:row.centerYAnchor],
    ]];
    return row;
}

- (void)layoutChrome {
    UILayoutGuide *safe = self.view.safeAreaLayoutGuide;
    self.contentTopToHeader = [self.tableView.topAnchor constraintEqualToAnchor:self.recentsHeaderRow.bottomAnchor];
    self.contentTopToTopBar = [self.tableView.topAnchor constraintEqualToAnchor:self.topBar.bottomAnchor];
    self.contentTopToHeader.active = YES;

    [NSLayoutConstraint activateConstraints:@[
        [self.topBar.topAnchor constraintEqualToAnchor:self.view.topAnchor],
        [self.topBar.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.topBar.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [self.topBar.bottomAnchor constraintEqualToAnchor:safe.topAnchor constant:56],
        [self.avatarButton.leadingAnchor constraintEqualToAnchor:safe.leadingAnchor constant:18],
        [self.avatarButton.centerYAnchor constraintEqualToAnchor:safe.topAnchor constant:28],
        [self.openFileButton.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-20],
        [self.openFileButton.centerYAnchor constraintEqualToAnchor:self.avatarButton.centerYAnchor],
        [self.searchBox.leadingAnchor constraintEqualToAnchor:self.avatarButton.trailingAnchor constant:8],
        [self.searchBox.trailingAnchor constraintEqualToAnchor:self.openFileButton.leadingAnchor constant:-10],
        [self.searchBox.centerYAnchor constraintEqualToAnchor:self.avatarButton.centerYAnchor],
        [self.searchBox.heightAnchor constraintEqualToConstant:32],
        [self.recentsHeaderRow.topAnchor constraintEqualToAnchor:self.topBar.bottomAnchor],
        [self.recentsHeaderRow.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.recentsHeaderRow.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [self.recentsHeaderRow.heightAnchor constraintEqualToConstant:56],
        [self.tableView.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.tableView.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [self.tableView.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
        [self.collectionView.topAnchor constraintEqualToAnchor:self.tableView.topAnchor],
        [self.collectionView.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.collectionView.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [self.collectionView.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
        [self.emptyRecentState.topAnchor constraintEqualToAnchor:self.topBar.bottomAnchor],
        [self.emptyRecentState.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.emptyRecentState.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [self.emptyRecentState.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
        [self.emptySearchState.topAnchor constraintEqualToAnchor:self.topBar.bottomAnchor],
        [self.emptySearchState.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.emptySearchState.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [self.emptySearchState.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
        [self.fabOverlay.topAnchor constraintEqualToAnchor:self.view.topAnchor],
        [self.fabOverlay.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.fabOverlay.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [self.fabOverlay.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
        [self.fabButton.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-35],
        [self.fabButton.bottomAnchor constraintEqualToAnchor:safe.bottomAnchor constant:-40],
        [self.fabButton.widthAnchor constraintEqualToConstant:60],
        [self.fabButton.heightAnchor constraintEqualToConstant:60],
        [self.fabCloseButton.trailingAnchor constraintEqualToAnchor:self.fabButton.trailingAnchor],
        [self.fabCloseButton.bottomAnchor constraintEqualToAnchor:self.fabButton.bottomAnchor],
        [self.fabCloseButton.widthAnchor constraintEqualToAnchor:self.fabButton.widthAnchor],
        [self.fabCloseButton.heightAnchor constraintEqualToAnchor:self.fabButton.heightAnchor],
        [self.fabMenuCard.trailingAnchor constraintEqualToAnchor:self.fabButton.trailingAnchor],
        [self.fabMenuCard.bottomAnchor constraintEqualToAnchor:self.fabButton.topAnchor constant:-20],
    ]];
}

- (void)reloadAvatar {
    NSURL *support = [[[NSFileManager defaultManager] URLsForDirectory:NSApplicationSupportDirectory inDomains:NSUserDomainMask] lastObject];
    NSURL *custom = [support URLByAppendingPathComponent:@"ai_profile_avatar.jpg"];
    UIImage *image = [UIImage imageWithContentsOfFile:custom.path] ?: [AppIcons iconNamed:@"avatar"];
    [self.avatarButton setImage:image forState:UIControlStateNormal];
}

- (void)openDrawer {
    [self.drawer openDrawer];
}

- (void)searchChanged {
    [self reloadRecents];
}

- (void)retrySearch {
    self.searchField.text = @"";
    [self reloadRecents];
}

- (void)toggleLayoutMode {
    self.gridMode = !self.gridMode;
    [[NSUserDefaults standardUserDefaults] setBool:self.gridMode forKey:kHomeGridModeKey];
    [self updateLayoutToggleIcon];
    [self updateContentVisibility];
}

- (void)updateLayoutToggleIcon {
    NSString *icon = self.gridMode ? @"list" : @"function";
    UIImage *image = [UIImage writerIconNamed:icon];
    [self.layoutToggleButton setImage:image forState:UIControlStateNormal];
    self.layoutToggleButton.accessibilityLabel = self.gridMode ? @"列表视图" : @"网格视图";
}

- (NSString *)trimmedQuery {
    return [[self.searchField.text ?: @"" stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]] copy];
}

- (void)reloadRecents {
    self.totalCount = [self.recentStore items].count;
    self.visibleItems = [self.recentStore itemsMatchingQuery:self.searchField.text ?: @""];
    [self.tableView reloadData];
    [self.collectionView reloadData];
    [self updateContentVisibility];
}

- (void)updateContentVisibility {
    NSString *query = [self trimmedQuery];
    BOOL hasQuery = query.length > 0;
    // Align Android: hide「最近打开」when searching or when there are no recents.
    BOOL showHeader = !hasQuery && self.totalCount > 0;
    BOOL showEmptyRecent = !hasQuery && self.totalCount == 0;
    BOOL showEmptySearch = hasQuery && self.visibleItems.count == 0;
    BOOL showList = self.visibleItems.count > 0;

    self.recentsHeaderRow.hidden = !showHeader;
    self.contentTopToHeader.active = showHeader;
    self.contentTopToTopBar.active = !showHeader;

    self.emptyRecentState.hidden = !showEmptyRecent;
    self.emptySearchState.hidden = !showEmptySearch;

    BOOL useGrid = self.gridMode && showList;
    self.tableView.hidden = !(showList && !useGrid);
    self.collectionView.hidden = !useGrid;
}

- (UIImage *)iconForItem:(RecentDocumentItem *)item {
    NSString *ext = item.pathExtension.lowercaseString;
    if ([ext isEqualToString:@"ods"] || [ext isEqualToString:@"xlsx"] || [ext isEqualToString:@"xls"]
        || [ext isEqualToString:@"csv"]) {
        return [AppIcons iconNamed:@"file-calc"];
    }
    if ([ext isEqualToString:@"odp"] || [ext isEqualToString:@"pptx"] || [ext isEqualToString:@"ppt"]) {
        return [AppIcons iconNamed:@"file-impress"];
    }
    return [AppIcons iconNamed:@"file-writer"];
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

- (void)openFabMenu {
    if (self.fabMenuOpen) {
        return;
    }
    self.fabMenuOpen = YES;
    self.fabOverlay.hidden = NO;
    self.fabMenuCard.hidden = NO;
    self.fabButton.hidden = YES;
    self.fabCloseButton.hidden = NO;
    [self.view bringSubviewToFront:self.fabOverlay];
    [self.view bringSubviewToFront:self.fabMenuCard];
    [self.view bringSubviewToFront:self.fabCloseButton];
}

- (void)closeFabMenu {
    if (!self.fabMenuOpen) {
        return;
    }
    self.fabMenuOpen = NO;
    self.fabOverlay.hidden = YES;
    self.fabMenuCard.hidden = YES;
    self.fabButton.hidden = NO;
    self.fabCloseButton.hidden = YES;
}

- (void)createWriter {
    [self closeFabMenu];
    [self showCreateFileSheetForKind:CreateFileDocKindWriter extension:@"odt"];
}

- (void)createCalc {
    [self closeFabMenu];
    [self showCreateFileSheetForKind:CreateFileDocKindCalc extension:@"ods"];
}

- (void)createImpress {
    [self closeFabMenu];
    [self showCreateFileSheetForKind:CreateFileDocKindImpress extension:@"odp"];
}

- (void)showCreateFileSheetForKind:(CreateFileDocKind)kind extension:(NSString *)extension {
    __weak __typeof(self) weakSelf = self;
    CreateFileBottomSheetController *sheet = [[CreateFileBottomSheetController alloc] initWithDocKind:kind
                                                                                           completion:^(NSString *basename, BOOL aiEnabled, NSString *aiUserDescription) {
        [weakSelf finishCreateWithExtension:extension
                                   docKind:(NSInteger)kind
                                   basename:basename
                                  aiEnabled:aiEnabled
                          aiUserDescription:aiUserDescription];
    }];
    [self presentViewController:sheet animated:YES completion:nil];
}

- (void)finishCreateWithExtension:(NSString *)extension
                          docKind:(NSInteger)docKind
                         basename:(NSString *)basename
                        aiEnabled:(BOOL)aiEnabled
                aiUserDescription:(NSString *)aiUserDescription {
    NSError *error = nil;
    NSURL *url = [DocumentPresentation createBlankDocumentWithExtension:extension
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
    DocumentPresentationLaunchOptions *options = nil;
    if (aiEnabled) {
        NSString *prompt = [DocumentPresentation autoAiPromptForDocKind:docKind
                                                                  title:basename
                                                        userDescription:aiUserDescription ?: @""];
        options = [DocumentPresentationLaunchOptions optionsWithAutoGenerate:YES
                                                                  aiPrompt:prompt
                                                           userDescription:aiUserDescription
                                                                calcNewTable:(docKind == CreateFileDocKindCalc)];
    }
    [DocumentPresentation presentDocumentAtURL:url from:self options:options];
}

- (UIColor *)homeActionsPopupColor {
    return [UIColor colorWithRed:0xF0 / 255.0 green:0xF4 / 255.0 blue:0xF9 / 255.0 alpha:1];
}

- (void)dismissRecentActionsPopup {
    [self.actionsDismissOverlay removeFromSuperview];
    [self.actionsPopup removeFromSuperview];
    self.actionsDismissOverlay = nil;
    self.actionsPopup = nil;
    self.actionsPopupItem = nil;
}

- (UIButton *)actionsPopupRowButtonWithTitle:(NSString *)title iconName:(NSString *)iconName {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
    button.translatesAutoresizingMaskIntoConstraints = NO;
    button.contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeading;
    button.titleLabel.font = [UIFont systemFontOfSize:12];
    [button setTitle:title forState:UIControlStateNormal];
    [button setTitleColor:[UIColor colorWithRed:0x33 / 255.0 green:0x33 / 255.0 blue:0x33 / 255.0 alpha:1]
                 forState:UIControlStateNormal];
    UIImage *icon = [[AppIcons iconNamed:iconName] imageWithRenderingMode:UIImageRenderingModeAlwaysOriginal];
    [button setImage:icon forState:UIControlStateNormal];
    button.imageView.contentMode = UIViewContentModeScaleAspectFit;
    button.contentEdgeInsets = UIEdgeInsetsMake(8, 8, 8, 8);
    button.imageEdgeInsets = UIEdgeInsetsMake(0, 0, 0, 8);
    button.titleEdgeInsets = UIEdgeInsetsMake(0, 8, 0, 0);
    return button;
}

- (UIView *)actionsPopupDivider {
    UIView *divider = [[UIView alloc] init];
    divider.translatesAutoresizingMaskIntoConstraints = NO;
    divider.backgroundColor = [UIColor colorWithWhite:0 alpha:0.08];
    [divider.heightAnchor constraintEqualToConstant:1].active = YES;
    return divider;
}

- (void)showActionsForItem:(RecentDocumentItem *)item sourceView:(UIView *)sourceView {
    [self dismissRecentActionsPopup];
    self.actionsPopupItem = item;

    self.actionsDismissOverlay = [[UIView alloc] initWithFrame:self.view.bounds];
    self.actionsDismissOverlay.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    self.actionsDismissOverlay.backgroundColor = UIColor.clearColor;
    UITapGestureRecognizer *dismissTap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(dismissRecentActionsPopup)];
    [self.actionsDismissOverlay addGestureRecognizer:dismissTap];
    [self.view addSubview:self.actionsDismissOverlay];

    self.actionsPopup = [[UIView alloc] init];
    self.actionsPopup.translatesAutoresizingMaskIntoConstraints = NO;
    self.actionsPopup.backgroundColor = [self homeActionsPopupColor];
    self.actionsPopup.layer.cornerRadius = 24;
    self.actionsPopup.layer.shadowColor = UIColor.blackColor.CGColor;
    self.actionsPopup.layer.shadowOpacity = 0.12;
    self.actionsPopup.layer.shadowRadius = 8;
    self.actionsPopup.layer.shadowOffset = CGSizeMake(0, 4);
    [self.view addSubview:self.actionsPopup];

    UIButton *renameButton = [self actionsPopupRowButtonWithTitle:@"重命名" iconName:@"action-rename"];
    UIButton *shareButton = [self actionsPopupRowButtonWithTitle:@"分享" iconName:@"action-share"];
    UIButton *removeButton = [self actionsPopupRowButtonWithTitle:@"从列表中删除" iconName:@"action-remove"];
    [renameButton addTarget:self action:@selector(actionsPopupRenameTapped) forControlEvents:UIControlEventTouchUpInside];
    [shareButton addTarget:self action:@selector(actionsPopupShareTapped) forControlEvents:UIControlEventTouchUpInside];
    [removeButton addTarget:self action:@selector(actionsPopupRemoveTapped) forControlEvents:UIControlEventTouchUpInside];

    UIStackView *stack = [[UIStackView alloc] initWithArrangedSubviews:@[
        renameButton,
        [self actionsPopupDivider],
        shareButton,
        [self actionsPopupDivider],
        removeButton,
    ]];
    stack.translatesAutoresizingMaskIntoConstraints = NO;
    stack.axis = UILayoutConstraintAxisVertical;
    stack.spacing = 0;
    [self.actionsPopup addSubview:stack];

    [NSLayoutConstraint activateConstraints:@[
        [stack.topAnchor constraintEqualToAnchor:self.actionsPopup.topAnchor constant:2],
        [stack.leadingAnchor constraintEqualToAnchor:self.actionsPopup.leadingAnchor constant:7],
        [stack.trailingAnchor constraintEqualToAnchor:self.actionsPopup.trailingAnchor constant:-7],
        [stack.bottomAnchor constraintEqualToAnchor:self.actionsPopup.bottomAnchor constant:-2],
        [renameButton.heightAnchor constraintEqualToConstant:40],
        [shareButton.heightAnchor constraintEqualToConstant:40],
        [removeButton.heightAnchor constraintEqualToConstant:40],
        [self.actionsPopup.widthAnchor constraintEqualToConstant:180],
    ]];

    CGRect anchorRect = [sourceView convertRect:sourceView.bounds toView:self.view];
    CGFloat popupWidth = 180;
    CGFloat popupHeight = 124;
    CGFloat marginEnd = 16;
    CGFloat overlap = 8;
    CGFloat x = self.view.bounds.size.width - marginEnd - popupWidth;
    CGFloat yBelow = CGRectGetMaxY(anchorRect) - overlap;
    CGFloat yAbove = CGRectGetMinY(anchorRect) - popupHeight + overlap;
    CGFloat y = yBelow;
    if (yBelow + popupHeight > self.view.bounds.size.height - 16 && yAbove >= 16) {
        y = yAbove;
    }
    self.actionsPopup.frame = CGRectMake(x, y, popupWidth, popupHeight);
}

- (RecentDocumentItem *)currentActionsItem {
    return self.actionsPopupItem;
}

- (void)actionsPopupRenameTapped {
    RecentDocumentItem *item = [self currentActionsItem];
    [self dismissRecentActionsPopup];
    [self showRenameDialogForItem:item];
}

- (void)actionsPopupShareTapped {
    RecentDocumentItem *item = [self currentActionsItem];
    UIView *sourceView = self.view;
    [self dismissRecentActionsPopup];
    [self shareItem:item fromView:sourceView];
}

- (void)actionsPopupRemoveTapped {
    RecentDocumentItem *item = [self currentActionsItem];
    [self dismissRecentActionsPopup];
    [self showRemoveConfirmDialogForItem:item];
}

- (void)shareItem:(RecentDocumentItem *)item fromView:(UIView *)sourceView {
    NSURL *url = [item resolvedURL];
    if (url == nil) {
        return;
    }
    UIActivityViewController *activity = [[UIActivityViewController alloc] initWithActivityItems:@[ url ]
                                                                           applicationActivities:nil];
    activity.popoverPresentationController.sourceView = sourceView;
    [self presentViewController:activity animated:YES completion:nil];
}

- (void)showRenameDialogForItem:(RecentDocumentItem *)item {
    if (item == nil) {
        return;
    }
    NSString *currentName = item.title;
    __weak __typeof(self) weakSelf = self;
    [HomeCardDialogPresenter presentRenameFrom:self
                                   currentName:currentName
                                    completion:^(NSString *newName) {
        [weakSelf.recentStore renameItem:item toTitle:newName];
        [weakSelf reloadRecents];
    }];
}

- (void)showRemoveConfirmDialogForItem:(RecentDocumentItem *)item {
    if (item == nil) {
        return;
    }
    NSString *message = @"此操作仅从列表中移除记录，不会删除文档文件。文件仍然存在，您可以随时重新打开。";
    __weak __typeof(self) weakSelf = self;
    [HomeCardDialogPresenter presentConfirmFrom:self
                                          title:@"从列表中删除"
                                        message:message
                                   confirmStyle:HomeCardDialogConfirmStylePrimary
                                     completion:^(BOOL confirmed) {
        if (!confirmed) {
            return;
        }
        [weakSelf.recentStore removeItem:item];
        [weakSelf reloadRecents];
    }];
}

- (void)openItem:(RecentDocumentItem *)item {
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

#pragma mark - Table

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return (NSInteger)self.visibleItems.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    HomeRecentCell *cell = [tableView dequeueReusableCellWithIdentifier:kHomeRecentCellId forIndexPath:indexPath];
    RecentDocumentItem *item = self.visibleItems[indexPath.row];
    cell.nameLabel.text = item.title;
    cell.dateLabel.text = item.displaySubtitle;
    cell.fileIconView.image = [self iconForItem:item];
    cell.accessibilityIdentifier = [NSString stringWithFormat:@"homeRecent-%@", item.title];
    __weak __typeof(self) weakSelf = self;
    __weak __typeof(cell) weakCell = cell;
    cell.moreAction = ^{
        [weakSelf showActionsForItem:item sourceView:weakCell.moreButton];
    };
    return cell;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
    [self openItem:self.visibleItems[indexPath.row]];
}

#pragma mark - Collection

- (NSInteger)collectionView:(UICollectionView *)collectionView numberOfItemsInSection:(NSInteger)section {
    return (NSInteger)self.visibleItems.count;
}

- (__kindof UICollectionViewCell *)collectionView:(UICollectionView *)collectionView cellForItemAtIndexPath:(NSIndexPath *)indexPath {
    HomeGridCell *cell = [collectionView dequeueReusableCellWithReuseIdentifier:kHomeGridCellId forIndexPath:indexPath];
    RecentDocumentItem *item = self.visibleItems[indexPath.item];
    cell.nameLabel.text = item.title;
    cell.dateLabel.text = item.displaySubtitle;
    cell.fileIconView.image = [self iconForItem:item];
    return cell;
}

- (void)collectionView:(UICollectionView *)collectionView didSelectItemAtIndexPath:(NSIndexPath *)indexPath {
    [self openItem:self.visibleItems[indexPath.item]];
}

- (CGSize)collectionView:(UICollectionView *)collectionView
                  layout:(UICollectionViewLayout *)collectionViewLayout
  sizeForItemAtIndexPath:(NSIndexPath *)indexPath {
    CGFloat width = (collectionView.bounds.size.width - 16 * 2 - 8) / 2.0;
    return CGSizeMake(MAX(140, width), 130);
}

- (BOOL)textFieldShouldReturn:(UITextField *)textField {
    [textField resignFirstResponder];
    return YES;
}

@end
