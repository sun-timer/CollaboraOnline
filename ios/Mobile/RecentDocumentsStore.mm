// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "RecentDocumentsStore.h"

static NSString *const RecentDocumentsStoreKey = @"RECENT_DOCUMENTS_BOOKMARKS";
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

@end

@interface RecentDocumentsStore ()
@property (strong, nonatomic) NSMutableArray<RecentDocumentItem *> *records;
@end

@implementation RecentDocumentsStore

- (instancetype)init {
    self = [super init];
    if (self) {
        _records = [[self loadRecords] mutableCopy];
    }
    return self;
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
    [self persist];
}

- (void)removeItem:(RecentDocumentItem *)item {
    [self.records removeObject:item];
    [self persist];
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
    NSArray *raw = [[NSUserDefaults standardUserDefaults] arrayForKey:RecentDocumentsStoreKey];
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
    NSMutableArray *raw = [NSMutableArray array];
    for (RecentDocumentItem *item in self.records) {
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
        if (item.bookmark.length > 0) {
            dict[@"bookmark"] = [item.bookmark base64EncodedStringWithOptions:0];
        }
        [raw addObject:dict];
    }
    [[NSUserDefaults standardUserDefaults] setObject:raw forKey:RecentDocumentsStoreKey];
}

@end
