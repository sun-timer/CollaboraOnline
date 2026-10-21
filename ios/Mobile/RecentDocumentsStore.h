// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface RecentDocumentItem : NSObject

@property (copy, nonatomic) NSString *title;
@property (copy, nonatomic) NSString *pathExtension;
@property (strong, nonatomic, nullable) NSDate *openedAt;
@property (strong, nonatomic, nullable) NSDate *lastModified;
@property (copy, nonatomic, nullable) NSString *path;
@property (copy, nonatomic, nullable) NSData *bookmark;

- (nullable NSURL *)resolvedURL;
- (NSString *)displaySubtitle;
- (NSString *)displayTitle;
+ (NSString *)formatModified:(nullable NSDate *)date;
+ (NSString *)stripExtensionFromFilename:(NSString *)filename;

@end

@interface RecentDocumentsStore : NSObject

- (NSArray<RecentDocumentItem *> *)items;
- (NSArray<RecentDocumentItem *> *)recentlyClosedItems;
/// Opened-tab session count. Zero when no document is in the session.
- (NSUInteger)openDocumentCount;
- (NSArray<RecentDocumentItem *> *)itemsMatchingQuery:(NSString *)query;
- (void)recordURL:(NSURL *)url;
/// Home open: session becomes exactly this URL. Does not remove other recent records.
- (void)beginSessionWithURL:(nullable NSURL *)url;
/// Tab open: insert this URL into the session if it is not already there.
- (void)noteSessionOpenedURL:(nullable NSURL *)url;
- (void)endSession;
- (NSArray<RecentDocumentItem *> *)sessionOpenItems;
/// Drop the row from the opened-tab session and prepend it to recently closed.
/// Home recents (`records`) are left in place.
- (void)removeSessionItem:(RecentDocumentItem *)item;
/** Re-read RECENT_* keys from NSUserDefaults (other screens persist via new store instances). */
- (void)reloadFromPersistentStorage;
- (void)importLocalTestFiles;
- (void)removeItem:(RecentDocumentItem *)item;
- (void)renameItem:(RecentDocumentItem *)item toTitle:(NSString *)title;
- (void)moveItemToRecentlyClosed:(RecentDocumentItem *)item;
- (void)restoreFromRecentlyClosed:(RecentDocumentItem *)item;
- (BOOL)item:(RecentDocumentItem *)item matchesURL:(NSURL *)url;

@end

NS_ASSUME_NONNULL_END
