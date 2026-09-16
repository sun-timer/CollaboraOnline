// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "RecentDocumentsStore.h"

static NSString *const RecentDocumentsStoreKey = @"RECENT_DOCUMENTS_BOOKMARKS";
static NSString *const RecentlyClosedDocumentsStoreKey = @"RECENTLY_CLOSED_DOCUMENTS_BOOKMARKS";
static const NSUInteger RecentDocumentsStoreMaxItems = 30;

@implementation RecentDocumentItem

- (NSURL *)resolvedURL {
    BOOL stale = NO;
    NSError *error = nil;
    if (self.bookmark.length > 0) {
        NSURL *url = [NSURL URLByResolvingBookmarkData:self.bookmark
                                               options:NSURLBookmarkResolutionWithoutUI
                                         relativeToURL:nil
                                   bookmarkDataIsStale:&stale
                                                 error:&error];
        if (url != nil) {
            return url;
        }
        url = [NSURL URLByResolvingBookmarkData:self.bookmark
                                        options:0
                                  relativeToURL:nil
                            bookmarkDataIsStale:&stale
                                          error:&error];
        if (url != nil) {
            return url;
        }
    }
    if (self.path.length > 0 && [[NSFileManager defaultManager] fileExistsAtPath:self.path]) {
        return [NSURL fileURLWithPath:self.path];
    }
    return nil;
}

- (NSDate *)effectiveLastModified {
    NSError *error = nil;
    NSURL *url = [self resolvedURL];
    if (url != nil) {
        NSDate *modified = nil;
        if ([url getResourceValue:&modified forKey:NSURLContentModificationDateKey error:&error] && modified != nil) {
            self.lastModified = modified;
            return modified;
        }
    }
    if (self.lastModified != nil) {
        return self.lastModified;
    }
    return self.openedAt ?: [NSDate date];
}

- (NSDate *)effectiveDisplayDate {
    if (self.openedAt != nil) {
        return self.openedAt;
    }
    return [self effectiveLastModified];
}

- (NSString *)displaySubtitle {
    return [RecentDocumentItem formatModified:[self effectiveDisplayDate]];
}

- (NSString *)displayTitle {
    return [RecentDocumentItem stripExtensionFromFilename:self.title ?: @""];
}

+ (NSString *)stripExtensionFromFilename:(NSString *)filename {
    if (filename.length == 0) {
        return filename;
    }
    NSString *ext = filename.pathExtension.lowercaseString;
    NSSet<NSString *> *known = [NSSet setWithArray:@[
        @"odt", @"ods", @"odp", @"odg", @"odf",
        @"doc", @"docx", @"xls", @"xlsx", @"ppt", @"pptx",
        @"pdf", @"txt", @"rtf", @"csv",
    ]];
    if (ext.length == 0 || ![known containsObject:ext]) {
        return filename;
    }
    return filename.stringByDeletingPathExtension;
}

+ (NSString *)formatModified:(NSDate *)date {
    if (date == nil) {
        date = [NSDate date];
    }
    NSDateFormatter *timeFormatter = [[NSDateFormatter alloc] init];
    timeFormatter.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
    timeFormatter.dateFormat = @"HH:mm";
    NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
    dateFormatter.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
    dateFormatter.dateFormat = @"yyyy/M/d";
    NSCalendar *calendar = [NSCalendar currentCalendar];
    if ([calendar isDateInToday:date]) {
        return [timeFormatter stringFromDate:date];
    }
    if ([calendar isDateInYesterday:date]) {
        return [@"昨天 " stringByAppendingString:[timeFormatter stringFromDate:date]];
    }
    return [dateFormatter stringFromDate:date];
}

@end

@interface RecentDocumentsStore ()
@property (strong, nonatomic) NSMutableArray<RecentDocumentItem *> *records;
@property (strong, nonatomic) NSMutableArray<RecentDocumentItem *> *closedRecords;
@end

@implementation RecentDocumentsStore

- (instancetype)init {
    self = [super init];
    if (self) {
        _records = [[self loadRecordsForKey:RecentDocumentsStoreKey] mutableCopy];
        _closedRecords = [[self loadRecordsForKey:RecentlyClosedDocumentsStoreKey] mutableCopy];
    }
    return self;
}

- (NSArray<RecentDocumentItem *> *)recentlyClosedItems {
    return [self.closedRecords copy];
}

- (NSUInteger)openDocumentCount {
    return MAX((NSUInteger)1, self.records.count);
}

- (NSArray<RecentDocumentItem *> *)items {
    return [self.records copy];
}

- (NSArray<RecentDocumentItem *> *)itemsMatchingQuery:(NSString *)query {
    NSString *trimmed = [query stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (trimmed.length == 0) {
        return [self items];
    }
    NSMutableArray<RecentDocumentItem *> *filtered = [NSMutableArray array];
    for (RecentDocumentItem *item in self.records) {
        if ([item.title rangeOfString:trimmed options:NSCaseInsensitiveSearch].location != NSNotFound) {
            [filtered addObject:item];
        }
    }
    return filtered;
}

- (void)reloadFromPersistentStorage {
    self.records = [[self loadRecordsForKey:RecentDocumentsStoreKey] mutableCopy];
    self.closedRecords = [[self loadRecordsForKey:RecentlyClosedDocumentsStoreKey] mutableCopy];
}

- (void)recordURL:(NSURL *)url {
    if (url == nil || !url.isFileURL) {
        return;
    }
    NSString *path = url.path.stringByStandardizingPath;
    RecentDocumentItem *item = [[RecentDocumentItem alloc] init];
    item.title = url.lastPathComponent;
    item.pathExtension = url.pathExtension.lowercaseString ?: @"";
    item.openedAt = [NSDate date];
    item.path = path;
    NSError *error = nil;
    item.bookmark = [url bookmarkDataWithOptions:NSURLBookmarkCreationMinimalBookmark
                  includingResourceValuesForKeys:nil
                                   relativeToURL:nil
                                           error:&error];
    NSDate *modified = nil;
    BOOL scopedAccess = [url startAccessingSecurityScopedResource];
    [url getResourceValue:&modified forKey:NSURLContentModificationDateKey error:nil];
    item.lastModified = modified;
    if (scopedAccess) {
        [url stopAccessingSecurityScopedResource];
    }

    NSMutableArray<RecentDocumentItem *> *next = [NSMutableArray array];
    [next addObject:item];
    for (RecentDocumentItem *existing in self.records) {
        if (![self item:existing matchesPath:path]) {
            [next addObject:existing];
        }
    }
    while (next.count > RecentDocumentsStoreMaxItems) {
        [next removeLastObject];
    }
    self.records = next;
    [self persistRecords:self.records forKey:RecentDocumentsStoreKey];
}

- (void)removeItem:(RecentDocumentItem *)item {
    [self.records removeObject:item];
    [self persistRecords:self.records forKey:RecentDocumentsStoreKey];
}

- (void)moveItemToRecentlyClosed:(RecentDocumentItem *)item {
    if (item == nil) {
        return;
    }
    NSMutableArray<RecentDocumentItem *> *nextOpen = [NSMutableArray array];
    for (RecentDocumentItem *existing in self.records) {
        if (existing != item && ![self item:existing matchesItem:item]) {
            [nextOpen addObject:existing];
        }
    }
    self.records = nextOpen;

    NSMutableArray<RecentDocumentItem *> *nextClosed = [NSMutableArray arrayWithObject:item];
    for (RecentDocumentItem *existing in self.closedRecords) {
        if (![self item:existing matchesItem:item]) {
            [nextClosed addObject:existing];
        }
    }
    while (nextClosed.count > RecentDocumentsStoreMaxItems) {
        [nextClosed removeLastObject];
    }
    self.closedRecords = nextClosed;
    [self persistRecords:self.records forKey:RecentDocumentsStoreKey];
    [self persistRecords:self.closedRecords forKey:RecentlyClosedDocumentsStoreKey];
}

- (void)restoreFromRecentlyClosed:(RecentDocumentItem *)item {
    if (item == nil) {
        return;
    }
    [self.closedRecords removeObject:item];
    NSMutableArray<RecentDocumentItem *> *nextClosed = [NSMutableArray array];
    for (RecentDocumentItem *existing in self.closedRecords) {
        if (![self item:existing matchesItem:item]) {
            [nextClosed addObject:existing];
        }
    }
    self.closedRecords = nextClosed;
    [self persistRecords:self.closedRecords forKey:RecentlyClosedDocumentsStoreKey];
    NSURL *url = [item resolvedURL];
    if (url != nil) {
        [self recordURL:url];
    }
}

- (BOOL)item:(RecentDocumentItem *)item matchesURL:(NSURL *)url {
    if (item == nil || url == nil) {
        return NO;
    }
    NSURL *resolved = [item resolvedURL];
    if (resolved == nil) {
        return NO;
    }
    return [resolved.path.stringByStandardizingPath isEqualToString:url.path.stringByStandardizingPath];
}

- (BOOL)item:(RecentDocumentItem *)left matchesItem:(RecentDocumentItem *)right {
    if (left.path.length > 0 && right.path.length > 0) {
        return [left.path.stringByStandardizingPath isEqualToString:right.path.stringByStandardizingPath];
    }
    return [left.title isEqualToString:right.title];
}
- (void)renameItem:(RecentDocumentItem *)item toTitle:(NSString *)title {
    NSString *trimmed = [title stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (trimmed.length == 0) {
        return;
    }
    NSURL *url = [item resolvedURL];
    if (url != nil) {
        NSString *ext = url.pathExtension;
        NSString *newName = trimmed;
        if (ext.length > 0 && trimmed.pathExtension.length == 0) {
            newName = [trimmed stringByAppendingPathExtension:ext];
        }
        NSURL *dir = [url URLByDeletingLastPathComponent];
        NSURL *newURL = [dir URLByAppendingPathComponent:newName];
        if (![newURL.path isEqualToString:url.path]) {
            NSFileManager *fm = [NSFileManager defaultManager];
            NSError *error = nil;
            if ([fm moveItemAtURL:url toURL:newURL error:&error]) {
                item.title = newName;
                item.path = newURL.path.stringByStandardizingPath;
                item.bookmark = [newURL bookmarkDataWithOptions:NSURLBookmarkCreationMinimalBookmark
                                   includingResourceValuesForKeys:nil
                                                    relativeToURL:nil
                                                            error:nil];
                [self persistRecords:self.records forKey:RecentDocumentsStoreKey];
                return;
            }
        }
    }
    // 文件不可用时仅更新记录标题
    item.title = trimmed;
    [self persistRecords:self.records forKey:RecentDocumentsStoreKey];
}

- (void)importLocalTestFiles {
    NSURL *documents = [[[NSFileManager defaultManager] URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask] lastObject];
    NSURL *testFiles = [documents URLByAppendingPathComponent:@"TestFiles" isDirectory:YES];
    NSArray<NSURL *> *contents = [[NSFileManager defaultManager] contentsOfDirectoryAtURL:testFiles
                                                               includingPropertiesForKeys:@[NSURLIsRegularFileKey]
                                                                                  options:NSDirectoryEnumerationSkipsHiddenFiles
                                                                                    error:nil];
    for (NSURL *fileURL in contents) {
        NSNumber *isFile = nil;
        [fileURL getResourceValue:&isFile forKey:NSURLIsRegularFileKey error:nil];
        if (!isFile.boolValue) {
            continue;
        }
        BOOL already = NO;
        NSString *path = fileURL.path.stringByStandardizingPath;
        for (RecentDocumentItem *existing in self.records) {
            if ([self item:existing matchesPath:path]) {
                already = YES;
                break;
            }
        }
        if (!already) {
            [self recordURL:fileURL];
        }
    }
}

- (BOOL)item:(RecentDocumentItem *)item matchesPath:(NSString *)path {
    if (item.path.length > 0 && [item.path.stringByStandardizingPath isEqualToString:path]) {
        return YES;
    }
    NSURL *resolved = [item resolvedURL];
    return [resolved.path.stringByStandardizingPath isEqualToString:path];
}

- (NSArray<RecentDocumentItem *> *)loadRecords {
    return [self loadRecordsForKey:RecentDocumentsStoreKey];
}

- (NSArray<RecentDocumentItem *> *)loadRecordsForKey:(NSString *)key {
    NSArray *raw = [[NSUserDefaults standardUserDefaults] arrayForKey:key];
    NSMutableArray<RecentDocumentItem *> *items = [NSMutableArray array];
    if (![raw isKindOfClass:[NSArray class]]) {
        return items;
    }
    for (NSDictionary *dict in raw) {
        if (![dict isKindOfClass:[NSDictionary class]]) {
            continue;
        }
        RecentDocumentItem *item = [[RecentDocumentItem alloc] init];
        item.title = [dict[@"title"] isKindOfClass:[NSString class]] ? dict[@"title"] : @"";
        item.pathExtension = [dict[@"ext"] isKindOfClass:[NSString class]] ? dict[@"ext"] : @"";
        item.path = [dict[@"path"] isKindOfClass:[NSString class]] ? dict[@"path"] : nil;
        NSNumber *opened = dict[@"openedAt"];
        if ([opened isKindOfClass:[NSNumber class]]) {
            item.openedAt = [NSDate dateWithTimeIntervalSince1970:opened.doubleValue];
        }
        NSNumber *modified = dict[@"modified"];
        if ([modified isKindOfClass:[NSNumber class]]) {
            item.lastModified = [NSDate dateWithTimeIntervalSince1970:modified.doubleValue];
        }
        NSString *bookmarkB64 = dict[@"bookmark"];
        if ([bookmarkB64 isKindOfClass:[NSString class]] && bookmarkB64.length > 0) {
            item.bookmark = [[NSData alloc] initWithBase64EncodedString:bookmarkB64 options:0];
        }
        if (item.title.length > 0) {
            [items addObject:item];
        }
    }
    return items;
}

- (void)persist {
    [self persistRecords:self.records forKey:RecentDocumentsStoreKey];
}

- (void)persistRecords:(NSArray<RecentDocumentItem *> *)records forKey:(NSString *)key {
    NSMutableArray *raw = [NSMutableArray array];
    for (RecentDocumentItem *item in records) {
        NSMutableDictionary *dict = [@{
            @"title": item.title ?: @"",
            @"ext": item.pathExtension ?: @"",
        } mutableCopy];
        if (item.path.length > 0) {
            dict[@"path"] = item.path;
        }
        if (item.openedAt != nil) {
            dict[@"openedAt"] = @(item.openedAt.timeIntervalSince1970);
        }
        if (item.lastModified != nil) {
            dict[@"modified"] = @(item.lastModified.timeIntervalSince1970);
        }
        if (item.bookmark.length > 0) {
            dict[@"bookmark"] = [item.bookmark base64EncodedStringWithOptions:0];
        }
        [raw addObject:dict];
    }
    [[NSUserDefaults standardUserDefaults] setObject:raw forKey:key];
}

@end
