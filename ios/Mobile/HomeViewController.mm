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
        self.nameLabel.textColor = [UIColor colorWithWhite:0.2 alpha:1];
        self.nameLabel.lineBreakMode = NSLineBreakByTruncatingTail;
        [self.contentView addSubview:self.nameLabel];

        self.dateLabel = [[UILabel alloc] init];
        self.dateLabel.translatesAutoresizingMaskIntoConstraints = NO;
        self.dateLabel.font = [UIFont systemFontOfSize:12];
        self.dateLabel.textColor = [UIColor colorWithWhite:0 alpha:0.4];
        [self.contentView addSubview:self.dateLabel];

        self.moreButton = [UIButton buttonWithType:UIButtonTypeSystem];
        self.moreButton.translatesAutoresizingMaskIntoConstraints = NO;
        [self.moreButton setImage:[UIImage imageNamed:@"HomeMoreDots"] forState:UIControlStateNormal];
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
            [self.moreButton.widthAnchor constraintEqualToConstant:36],
            [self.moreButton.heightAnchor constraintEqualToConstant:36],
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
            [self.fileIconView.widthAnchor constraintEqualToConstant:48],
            [self.fileIconView.heightAnchor constraintEqualToConstant:48],
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
@property (strong, nonatomic) UIView *fabOverlay;
@property (strong, nonatomic) UIView *fabMenuCard;
@property (assign, nonatomic) BOOL fabMenuOpen;
@property (strong, nonatomic) NSLayoutConstraint *contentTopToHeader;
@property (strong, nonatomic) NSLayoutConstraint *contentTopToTopBar;
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
    return [UIColor colorWithRed:0xF2 / 255.0 green:0xF2 / 255.0 blue:0xF2 / 255.0 alpha:1];
}

- (void)buildTopBar {
    self.topBar = [[UIView alloc] init];
    self.topBar.translatesAutoresizingMaskIntoConstraints = NO;
    self.topBar.backgroundColor = [self chromePlateColor];
    [self.view addSubview:self.topBar];

    self.avatarButton = [UIButton buttonWithType:UIButtonTypeCustom];
    self.avatarButton.translatesAutoresizingMaskIntoConstraints = NO;
    self.avatarButton.layer.cornerRadius = 18;
    self.avatarButton.clipsToBounds = YES;
    self.avatarButton.accessibilityIdentifier = @"homeAvatarButton";
    self.avatarButton.accessibilityLabel = @"打开设置";
    [self.avatarButton setImage:[UIImage imageNamed:@"HomeAvatar"] forState:UIControlStateNormal];
    self.avatarButton.imageView.contentMode = UIViewContentModeScaleAspectFill;
    [self.avatarButton addTarget:self action:@selector(openDrawer) forControlEvents:UIControlEventTouchUpInside];
    [self.topBar addSubview:self.avatarButton];

    self.searchBox = [[UIView alloc] init];
    self.searchBox.translatesAutoresizingMaskIntoConstraints = NO;
    self.searchBox.backgroundColor = UIColor.whiteColor;
    self.searchBox.layer.cornerRadius = 18;
    [self.topBar addSubview:self.searchBox];

    UIImageView *searchIcon = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"HomeSearch"]];
    searchIcon.translatesAutoresizingMaskIntoConstraints = NO;
    searchIcon.contentMode = UIViewContentModeScaleAspectFit;
    searchIcon.tintColor = [UIColor colorWithWhite:0.6 alpha:1];
    [self.searchBox addSubview:searchIcon];

    self.searchField = [[UITextField alloc] init];
    self.searchField.translatesAutoresizingMaskIntoConstraints = NO;
    self.searchField.placeholder = @"搜索";
    self.searchField.font = [UIFont systemFontOfSize:16];
    self.searchField.textColor = [UIColor colorWithWhite:0.2 alpha:1];
    self.searchField.delegate = self;
    self.searchField.accessibilityIdentifier = @"homeSearchField";
    self.searchField.clearButtonMode = UITextFieldViewModeWhileEditing;
    self.searchField.returnKeyType = UIReturnKeySearch;
    [self.searchField addTarget:self action:@selector(searchChanged) forControlEvents:UIControlEventEditingChanged];
    [self.searchBox addSubview:self.searchField];

    self.openFileButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.openFileButton.translatesAutoresizingMaskIntoConstraints = NO;
    [self.openFileButton setImage:[UIImage imageNamed:@"HomeFolder"] forState:UIControlStateNormal];
    self.openFileButton.tintColor = [UIColor colorWithWhite:0.26 alpha:1];
    self.openFileButton.accessibilityIdentifier = @"homeOpenFileButton";
    self.openFileButton.accessibilityLabel = @"打开";
    [self.openFileButton addTarget:self action:@selector(openFile) forControlEvents:UIControlEventTouchUpInside];
    [self.topBar addSubview:self.openFileButton];

    [NSLayoutConstraint activateConstraints:@[
        [self.avatarButton.widthAnchor constraintEqualToConstant:36],
        [self.avatarButton.heightAnchor constraintEqualToConstant:36],
        [searchIcon.leadingAnchor constraintEqualToAnchor:self.searchBox.leadingAnchor constant:12],
        [searchIcon.centerYAnchor constraintEqualToAnchor:self.searchBox.centerYAnchor],
        [searchIcon.widthAnchor constraintEqualToConstant:18],
        [searchIcon.heightAnchor constraintEqualToConstant:18],
        [self.searchField.leadingAnchor constraintEqualToAnchor:searchIcon.trailingAnchor constant:8],
        [self.searchField.trailingAnchor constraintEqualToAnchor:self.searchBox.trailingAnchor constant:-12],
        [self.searchField.centerYAnchor constraintEqualToAnchor:self.searchBox.centerYAnchor],
        [self.openFileButton.widthAnchor constraintEqualToConstant:40],
        [self.openFileButton.heightAnchor constraintEqualToConstant:40],
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
    self.recentsHeaderLabel.textColor = [UIColor colorWithRed:111 / 255.0 green:115 / 255.0 blue:120 / 255.0 alpha:1];
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
    self.tableView.rowHeight = 64;
    self.tableView.separatorInset = UIEdgeInsetsMake(0, 16, 0, 16);
    self.tableView.tableFooterView = [[UIView alloc] init];
    self.tableView.accessibilityIdentifier = @"homeRecentsTable";
    [self.tableView registerClass:[HomeRecentCell class] forCellReuseIdentifier:kHomeRecentCellId];
    [self.view addSubview:self.tableView];

    UICollectionViewFlowLayout *layout = [[UICollectionViewFlowLayout alloc] init];
    layout.minimumInteritemSpacing = 8;
    layout.minimumLineSpacing = 12;
    layout.sectionInset = UIEdgeInsetsMake(8, 16, 96, 16);
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

    UIImageView *imageView = [[UIImageView alloc] initWithImage:[UIImage imageNamed:imageName]];
    imageView.translatesAutoresizingMaskIntoConstraints = NO;
    imageView.contentMode = UIViewContentModeScaleAspectFit;
    imageView.alpha = [imageName isEqualToString:@"HomeEmptySearch"] ? 0.45 : 1.0;
    [container addSubview:imageView];

    UILabel *titleLabel = [[UILabel alloc] init];
    titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    titleLabel.text = title;
    titleLabel.font = [UIFont systemFontOfSize:22 weight:UIFontWeightBold];
    titleLabel.textColor = [UIColor colorWithRed:0xB0 / 255.0 green:0xB3 / 255.0 blue:0xB8 / 255.0 alpha:1];
    titleLabel.textAlignment = NSTextAlignmentCenter;
    [container addSubview:titleLabel];

    UILabel *subtitleLabel = [[UILabel alloc] init];
    subtitleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    subtitleLabel.text = subtitle;
    subtitleLabel.font = [UIFont systemFontOfSize:16];
    subtitleLabel.textColor = [UIColor colorWithRed:0xB0 / 255.0 green:0xB3 / 255.0 blue:0xB8 / 255.0 alpha:1];
    subtitleLabel.textAlignment = NSTextAlignmentCenter;
    subtitleLabel.numberOfLines = 0;
    subtitleLabel.hidden = subtitle.length == 0;
    [container addSubview:subtitleLabel];

    UIButton *retry = [UIButton buttonWithType:UIButtonTypeSystem];
    retry.translatesAutoresizingMaskIntoConstraints = NO;
    [retry setTitle:@"重试" forState:UIControlStateNormal];
    [retry setTitleColor:[UIColor colorWithWhite:0.13 alpha:1] forState:UIControlStateNormal];
    retry.titleLabel.font = [UIFont systemFontOfSize:18];
    retry.backgroundColor = [UIColor colorWithWhite:0.93 alpha:1];
    retry.layer.cornerRadius = 12;
    retry.hidden = !retryVisible;
    retry.accessibilityIdentifier = @"homeSearchRetry";
    [retry addTarget:self action:@selector(retrySearch) forControlEvents:UIControlEventTouchUpInside];
    [container addSubview:retry];

    [NSLayoutConstraint activateConstraints:@[
        [imageView.centerXAnchor constraintEqualToAnchor:container.centerXAnchor],
        [imageView.centerYAnchor constraintEqualToAnchor:container.centerYAnchor constant:-48],
        [imageView.widthAnchor constraintEqualToConstant:210],
        [imageView.heightAnchor constraintEqualToConstant:210],
        [titleLabel.topAnchor constraintEqualToAnchor:imageView.bottomAnchor constant:16],
        [titleLabel.leadingAnchor constraintEqualToAnchor:container.leadingAnchor constant:24],
        [titleLabel.trailingAnchor constraintEqualToAnchor:container.trailingAnchor constant:-24],
        [subtitleLabel.topAnchor constraintEqualToAnchor:titleLabel.bottomAnchor constant:8],
        [subtitleLabel.leadingAnchor constraintEqualToAnchor:titleLabel.leadingAnchor],
        [subtitleLabel.trailingAnchor constraintEqualToAnchor:titleLabel.trailingAnchor],
        [retry.topAnchor constraintEqualToAnchor:titleLabel.bottomAnchor constant:30],
        [retry.centerXAnchor constraintEqualToAnchor:container.centerXAnchor],
        [retry.widthAnchor constraintEqualToConstant:190],
        [retry.heightAnchor constraintEqualToConstant:56],
    ]];
    return container;
}

- (void)buildEmptyStates {
    self.emptyRecentState = [self buildEmptyContainerWithImage:@"HomeEmptyRecent"
                                                         title:@"没有最近的文档"
                                                      subtitle:@"创建或导入文件以开始使用"
                                                  retryVisible:NO];
    self.emptyRecentState.accessibilityIdentifier = @"homeEmptyRecent";
    [self.view addSubview:self.emptyRecentState];

    self.emptySearchState = [self buildEmptyContainerWithImage:@"HomeEmptySearch"
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
    self.fabMenuCard.layer.cornerRadius = 14;
    self.fabMenuCard.layer.shadowColor = UIColor.blackColor.CGColor;
    self.fabMenuCard.layer.shadowOpacity = 0.16;
    self.fabMenuCard.layer.shadowRadius = 12;
    self.fabMenuCard.layer.shadowOffset = CGSizeMake(0, 4);
    self.fabMenuCard.hidden = YES;
    [self.view addSubview:self.fabMenuCard];

    UIStackView *stack = [[UIStackView alloc] init];
    stack.translatesAutoresizingMaskIntoConstraints = NO;
    stack.axis = UILayoutConstraintAxisVertical;
    [self.fabMenuCard addSubview:stack];

    [stack addArrangedSubview:[self newDocRowWithTitle:@"文本文档"
                                                  icon:@"HomeFileWriter"
                                                action:@selector(createWriter)]];
    [stack addArrangedSubview:[self newDocRowWithTitle:@"电子表格"
                                                  icon:@"HomeFileCalc"
                                                action:@selector(createCalc)]];
    [stack addArrangedSubview:[self newDocRowWithTitle:@"演示文稿"
                                                  icon:@"HomeFileImpress"
                                                action:@selector(createImpress)]];

    [NSLayoutConstraint activateConstraints:@[
        [stack.topAnchor constraintEqualToAnchor:self.fabMenuCard.topAnchor constant:4],
        [stack.leadingAnchor constraintEqualToAnchor:self.fabMenuCard.leadingAnchor],
        [stack.trailingAnchor constraintEqualToAnchor:self.fabMenuCard.trailingAnchor],
        [stack.bottomAnchor constraintEqualToAnchor:self.fabMenuCard.bottomAnchor constant:-4],
        [self.fabMenuCard.widthAnchor constraintEqualToConstant:196],
    ]];

    self.fabButton = [UIButton buttonWithType:UIButtonTypeCustom];
    self.fabButton.translatesAutoresizingMaskIntoConstraints = NO;
    [self.fabButton setImage:[UIImage imageNamed:@"HomeFab"] forState:UIControlStateNormal];
    self.fabButton.accessibilityIdentifier = @"homeFab";
    self.fabButton.accessibilityLabel = @"新建文档";
    [self.fabButton addTarget:self action:@selector(toggleFabMenu) forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:self.fabButton];
}

- (UIView *)newDocRowWithTitle:(NSString *)title icon:(NSString *)icon action:(SEL)action {
    UIControl *row = [[UIControl alloc] init];
    row.translatesAutoresizingMaskIntoConstraints = NO;
    [row.heightAnchor constraintEqualToConstant:52].active = YES;
    [row addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];

    UIImageView *iconView = [[UIImageView alloc] initWithImage:[UIImage imageNamed:icon]];
    iconView.translatesAutoresizingMaskIntoConstraints = NO;
    iconView.contentMode = UIViewContentModeScaleAspectFit;
    [row addSubview:iconView];

    UILabel *label = [[UILabel alloc] init];
    label.translatesAutoresizingMaskIntoConstraints = NO;
    label.text = title;
    label.font = [UIFont systemFontOfSize:15];
    label.textColor = [UIColor colorWithWhite:0.2 alpha:1];
    [row addSubview:label];

    [NSLayoutConstraint activateConstraints:@[
        [iconView.leadingAnchor constraintEqualToAnchor:row.leadingAnchor constant:14],
        [iconView.centerYAnchor constraintEqualToAnchor:row.centerYAnchor],
        [iconView.widthAnchor constraintEqualToConstant:28],
        [iconView.heightAnchor constraintEqualToConstant:28],
        [label.leadingAnchor constraintEqualToAnchor:iconView.trailingAnchor constant:12],
        [label.trailingAnchor constraintEqualToAnchor:row.trailingAnchor constant:-14],
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
        [self.avatarButton.leadingAnchor constraintEqualToAnchor:safe.leadingAnchor constant:16],
        [self.avatarButton.bottomAnchor constraintEqualToAnchor:self.topBar.bottomAnchor constant:-8],
        [self.openFileButton.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-8],
        [self.openFileButton.centerYAnchor constraintEqualToAnchor:self.avatarButton.centerYAnchor],
        [self.searchBox.leadingAnchor constraintEqualToAnchor:self.avatarButton.trailingAnchor constant:12],
        [self.searchBox.trailingAnchor constraintEqualToAnchor:self.openFileButton.leadingAnchor constant:-4],
        [self.searchBox.centerYAnchor constraintEqualToAnchor:self.avatarButton.centerYAnchor],
        [self.searchBox.heightAnchor constraintEqualToConstant:36],
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
        [self.fabButton.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-24],
        [self.fabButton.bottomAnchor constraintEqualToAnchor:safe.bottomAnchor constant:-24],
        [self.fabButton.widthAnchor constraintEqualToConstant:56],
        [self.fabButton.heightAnchor constraintEqualToConstant:56],
        [self.fabMenuCard.trailingAnchor constraintEqualToAnchor:self.fabButton.trailingAnchor],
        [self.fabMenuCard.bottomAnchor constraintEqualToAnchor:self.fabButton.topAnchor constant:-12],
    ]];
}

- (void)reloadAvatar {
    NSURL *support = [[[NSFileManager defaultManager] URLsForDirectory:NSApplicationSupportDirectory inDomains:NSUserDomainMask] lastObject];
    NSURL *custom = [support URLByAppendingPathComponent:@"ai_profile_avatar.jpg"];
    UIImage *image = [UIImage imageWithContentsOfFile:custom.path] ?: [UIImage imageNamed:@"HomeAvatar"];
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
        return [UIImage imageNamed:@"HomeFileCalc"];
    }
    if ([ext isEqualToString:@"odp"] || [ext isEqualToString:@"pptx"] || [ext isEqualToString:@"ppt"]) {
        return [UIImage imageNamed:@"HomeFileImpress"];
    }
    return [UIImage imageNamed:@"HomeFileWriter"];
}

- (NSString *)formatOpenedAt:(NSDate *)date {
    if (date == nil) {
        return @"";
    }
    NSCalendar *calendar = [NSCalendar currentCalendar];
    NSDate *startOfToday = [calendar startOfDayForDate:[NSDate date]];
    NSDate *startOfYesterday = [calendar dateByAddingUnit:NSCalendarUnitDay value:-1 toDate:startOfToday options:0];
    NSDateFormatter *timeFormat = [[NSDateFormatter alloc] init];
    timeFormat.dateFormat = @"HH:mm";
    NSTimeInterval opened = date.timeIntervalSince1970;
    if (opened >= startOfToday.timeIntervalSince1970) {
        return [timeFormat stringFromDate:date];
    }
    if (opened >= startOfYesterday.timeIntervalSince1970) {
        return [NSString stringWithFormat:@"昨天 %@", [timeFormat stringFromDate:date]];
    }
    NSDateFormatter *dateFormat = [[NSDateFormatter alloc] init];
    dateFormat.dateFormat = @"yyyy/M/d";
    return [dateFormat stringFromDate:date];
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

- (void)toggleFabMenu {
    if (self.fabMenuOpen) {
        [self closeFabMenu];
    } else {
        [self openFabMenu];
    }
}

- (void)openFabMenu {
    self.fabMenuOpen = YES;
    self.fabOverlay.hidden = NO;
    self.fabMenuCard.hidden = NO;
    [self.fabButton setImage:[UIImage imageNamed:@"HomeFabClose"] forState:UIControlStateNormal];
    [self.view bringSubviewToFront:self.fabOverlay];
    [self.view bringSubviewToFront:self.fabMenuCard];
    [self.view bringSubviewToFront:self.fabButton];
}

- (void)closeFabMenu {
    self.fabMenuOpen = NO;
    self.fabOverlay.hidden = YES;
    self.fabMenuCard.hidden = YES;
    [self.fabButton setImage:[UIImage imageNamed:@"HomeFab"] forState:UIControlStateNormal];
}

- (void)createWriter {
    [self closeFabMenu];
    [self createBlankDocumentWithExtension:@"odt" basename:@"文档"];
}

- (void)createCalc {
    [self closeFabMenu];
    [self createBlankDocumentWithExtension:@"ods" basename:@"表格"];
}

- (void)createImpress {
    [self closeFabMenu];
    [self createBlankDocumentWithExtension:@"odp" basename:@"演示"];
}

- (void)createBlankDocumentWithExtension:(NSString *)outputExtension basename:(NSString *)basename {
    NSError *error = nil;
    NSURL *url = [DocumentPresentation createBlankDocumentWithExtension:outputExtension
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

- (void)showActionsForItem:(RecentDocumentItem *)item sourceView:(UIView *)sourceView {
    UIAlertController *sheet = [UIAlertController alertControllerWithTitle:item.title
                                                                   message:nil
                                                            preferredStyle:UIAlertControllerStyleActionSheet];
    [sheet addAction:[UIAlertAction actionWithTitle:@"分享" style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
        NSURL *url = [item resolvedURL];
        if (url == nil) {
            return;
        }
        UIActivityViewController *activity = [[UIActivityViewController alloc] initWithActivityItems:@[ url ]
                                                                               applicationActivities:nil];
        activity.popoverPresentationController.sourceView = sourceView;
        [self presentViewController:activity animated:YES completion:nil];
    }]];
    [sheet addAction:[UIAlertAction actionWithTitle:@"从最近移除" style:UIAlertActionStyleDestructive handler:^(UIAlertAction *action) {
        [self.recentStore removeItem:item];
        [self reloadRecents];
    }]];
    [sheet addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];
    sheet.popoverPresentationController.sourceView = sourceView;
    sheet.popoverPresentationController.sourceRect = sourceView.bounds;
    [self presentViewController:sheet animated:YES completion:nil];
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
    cell.dateLabel.text = [self formatOpenedAt:item.openedAt];
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
    cell.dateLabel.text = [self formatOpenedAt:item.openedAt];
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
    return CGSizeMake(MAX(140, width), 120);
}

- (BOOL)textFieldShouldReturn:(UITextField *)textField {
    [textField resignFirstResponder];
    return YES;
}

@end
