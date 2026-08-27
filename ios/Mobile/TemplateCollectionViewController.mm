// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

#import "AppDelegate.h"
#import "DocumentPresentation.h"
#import "L10n.h"
#import "TemplateCollectionViewController.h"
#import "TemplateSectionHeaderView.h"

#import "svtools/strings.hrc"

static NSString *mapTemplateExtensionToActual(NSString *templateName) {
    NSString *baseName = [templateName stringByDeletingPathExtension];
    NSString *extension = [templateName substringFromIndex:baseName.length];

    if ([extension isEqualToString:@".ott"] || [extension isEqualToString:@".odt"])
        return [baseName stringByAppendingString:@".odt"];
    else if ([extension isEqualToString:@".ots"] || [extension isEqualToString:@".ods"])
        return [baseName stringByAppendingString:@".ods"];
    else if ([extension isEqualToString:@".otp"] || [extension isEqualToString:@".odp"])
        return [baseName stringByAppendingString:@".odp"];
    else
        assert(false);
}

static NSMutableArray<NSURL *> *blankTemplatesForExtension(NSString *ext) {
    NSMutableArray<NSURL *> *result = [NSMutableArray array];
    NSURL *url = [[NSBundle mainBundle] URLForResource:@"untitled"
                                         withExtension:ext
                                          subdirectory:@"Templates"];
    if (url == nil) {
        url = [[NSBundle mainBundle] URLForResource:@"untitled" withExtension:ext];
    }
    if (url != nil) {
        [result addObject:url];
    }
    return result;
}

@implementation TemplateCollectionViewController

- (void)viewDidLoad {

    // Partial fix for issue #1962 Dismiss view by tapping outside of the view
    // Setting modalInPresentation to YES will ignore all events outside of
    // the view so set self.modalInPresentation to NO.
    self.modalInPresentation = NO;

    static NSString *downloadedTemplates = [[NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) objectAtIndex:0] stringByAppendingString:@"/downloadedTemplates/"];

    // Scan for available templates. First downloaded ones.
    NSDirectoryEnumerator<NSString *> *e = [[NSFileManager defaultManager] enumeratorAtPath:downloadedTemplates];

    templates[0] = [@[] mutableCopy];
    templates[1] = [@[] mutableCopy];
    templates[2] = [@[] mutableCopy];

    NSString *subPath;
    while ((subPath = [e nextObject]) != nil) {
        NSString *path = [downloadedTemplates stringByAppendingString:subPath];
        NSString *pathExt = [[path pathExtension] lowercaseString];
        if ([pathExt isEqualToString:@"ott"] || [pathExt isEqualToString:@"odt"]) {
            [templates[0] addObject:[NSURL fileURLWithPath:path]];
        } else if ([pathExt isEqualToString:@"ots"] || [pathExt isEqualToString:@"ods"]) {
            [templates[1] addObject:[NSURL fileURLWithPath:path]];
        } else if ([pathExt isEqualToString:@"otp"] || [pathExt isEqualToString:@"odp"]) {
            [templates[2] addObject:[NSURL fileURLWithPath:path]];
        }
    }

    // Prefer blank untitled ODF copies (Android-aligned). Fall back to ott/ots/otp if present.
    if ([templates[0] count] == 0) {
        templates[0] = blankTemplatesForExtension(@"odt");
        if ([templates[0] count] == 0) {
            templates[0] = [[[NSBundle mainBundle] URLsForResourcesWithExtension:@".ott" subdirectory:@"Templates"] mutableCopy] ?: [@[] mutableCopy];
        }
    }
    if ([templates[1] count] == 0) {
        templates[1] = blankTemplatesForExtension(@"ods");
        if ([templates[1] count] == 0) {
            templates[1] = [[[NSBundle mainBundle] URLsForResourcesWithExtension:@".ots" subdirectory:@"Templates"] mutableCopy] ?: [@[] mutableCopy];
        }
    }
    if ([templates[2] count] == 0) {
        templates[2] = blankTemplatesForExtension(@"odp");
        if ([templates[2] count] == 0) {
            templates[2] = [[[NSBundle mainBundle] URLsForResourcesWithExtension:@".otp" subdirectory:@"Templates"] mutableCopy] ?: [@[] mutableCopy];
        }
    }
}

- (void)viewDidDisappear:(BOOL)animated {
    // Partial fix for issue #1962 Invoke import handler when view is dismissed
    // If the import handler has not already been invoked, invoke it or else
    // -[DocumentBrowserViewController
    // documentBrowser:didRequestDocumentCreationWithHandler:] will never be
    // hcalled again.
    if (self.importHandler) {
        self.importHandler(nil, UIDocumentBrowserImportModeNone);
        self.importHandler = nil;
    }
}

- (NSInteger)numberOfSectionsInCollectionView:(UICollectionView *)collectionView {
    // Three sections: Document, Spreadsheet, and Presentation
    return 3;
}

- (NSInteger)collectionView:(UICollectionView *)collectionView numberOfItemsInSection:(NSInteger)section {
    assert(section >= 0 && section <= 2);
    return templates[section].count;
}

- (UICollectionViewCell *)collectionView:(UICollectionView *)collectionView cellForItemAtIndexPath:(NSIndexPath *)indexPath {
    assert(indexPath.length == 2);
    assert([indexPath indexAtPosition:0] <= 2);
    assert([indexPath indexAtPosition:1] < templates[[indexPath indexAtPosition:0]].count);

    UICollectionViewCell *cell = [collectionView dequeueReusableCellWithReuseIdentifier:@"Cell" forIndexPath:indexPath];

    UIImageView *image = (UIImageView *)[cell viewWithTag:1];
    UILabel *title = (UILabel *)[cell viewWithTag:2];

    NSString *templateThumbnail = [[templates[[indexPath indexAtPosition:0]][[indexPath indexAtPosition:1]] path] stringByAppendingString:@".png"];
    UIImage *thumbnail;
    if ([NSFileManager.defaultManager fileExistsAtPath:templateThumbnail])
        thumbnail = [UIImage imageWithContentsOfFile:templateThumbnail];
    else
        thumbnail = [UIImage imageNamed:@"AppIcon"];

    image.image = thumbnail;

    NSString *fileName = [templates[[indexPath indexAtPosition:0]][[indexPath indexAtPosition:1]] lastPathComponent];

    title.text = [fileName stringByDeletingPathExtension];

    return cell;
}

- (CGSize)collectionView:(UICollectionView *)collectionView layout:(UICollectionViewLayout*)collectionViewLayout sizeForItemAtIndexPath:(NSIndexPath *)indexPath {
    return CGSizeMake(150, 150);
}

- (UICollectionReusableView *)collectionView:(UICollectionView *)collectionView viewForSupplementaryElementOfKind:(NSString *)kind atIndexPath:(NSIndexPath *)indexPath {
    assert(kind == UICollectionElementKindSectionHeader);

    assert(indexPath.length == 2);
    assert([indexPath indexAtPosition:1] == 0);

    NSUInteger index = [indexPath indexAtPosition:0];
    assert(index <= 2);

    TemplateSectionHeaderView *header = [collectionView dequeueReusableSupplementaryViewOfKind:UICollectionElementKindSectionHeader withReuseIdentifier:@"SectionHeaderView" forIndexPath:indexPath];

    char *translatedHeader;

    if (index == 0)
        translatedHeader = _(STR_DESCRIPTION_FACTORY_WRITER, "svt");
    else if (index == 1)
        translatedHeader = _(STR_DESCRIPTION_FACTORY_CALC, "svt");
    else if (index == 2)
        translatedHeader = _(STR_DESCRIPTION_FACTORY_IMPRESS, "svt");
    else
        abort();

    header.title.text = [NSString stringWithUTF8String:translatedHeader];

    free(translatedHeader);

    return header;
}

- (BOOL)collectionView:(UICollectionView *)collectionView shouldSelectItemAtIndexPath:(NSIndexPath *)indexPath {
    NSURL *selectedTemplate = templates[[indexPath indexAtPosition:0]][[indexPath indexAtPosition:1]];
    NSString *outputName = mapTemplateExtensionToActual(selectedTemplate.lastPathComponent);
    NSString *outputExt = [[outputName pathExtension] lowercaseString];
    NSString *selectedExt = [[selectedTemplate pathExtension] lowercaseString];

    NSURL *newURL = nil;
    NSError *error = nil;

    // Never call lo_kit documentLoad from the UI thread (crashes after a prior document).
    if ([selectedExt isEqualToString:@"odt"] || [selectedExt isEqualToString:@"ods"]
        || [selectedExt isEqualToString:@"odp"]) {
        NSURL *documents = [[[NSFileManager defaultManager] URLsForDirectory:NSDocumentDirectory
                                                                   inDomains:NSUserDomainMask] lastObject];
        NSString *base = [outputName stringByDeletingPathExtension];
        NSString *fileName = [NSString stringWithFormat:@"%@.%@", base, outputExt];
        newURL = [documents URLByAppendingPathComponent:fileName];
        NSUInteger suffix = 2;
        while ([[NSFileManager defaultManager] fileExistsAtPath:newURL.path]) {
            fileName = [NSString stringWithFormat:@"%@ %lu.%@", base, (unsigned long)suffix, outputExt];
            newURL = [documents URLByAppendingPathComponent:fileName];
            suffix++;
        }
        if (![[NSFileManager defaultManager] copyItemAtURL:selectedTemplate toURL:newURL error:&error]) {
            return NO;
        }
    } else {
        // ott/ots/otp without LOK conversion: fall back to blank untitled ODF.
        newURL = [DocumentPresentation createBlankDocumentWithExtension:outputExt
                                                              basename:[outputName stringByDeletingPathExtension]
                                                                 error:&error];
        if (newURL == nil) {
            return NO;
        }
    }

    // Partial fix for issue #1962 Set import handler to nil after use
    if (self.importHandler) {
        self.importHandler(newURL, UIDocumentBrowserImportModeMove);
        self.importHandler = nil;
    }

    [self dismissViewControllerAnimated:YES completion:nil];

    return YES;
}

- (void)cancel {
    // Partial fix for issue #1962 Set import handler to nil after use
    if (self.importHandler) {
        self.importHandler(nil, UIDocumentBrowserImportModeNone);
        self.importHandler = nil;
    }

    [self dismissViewControllerAnimated:YES completion:nil];
}

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
