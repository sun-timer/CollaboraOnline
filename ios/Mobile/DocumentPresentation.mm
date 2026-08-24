// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#define LIBO_INTERNAL_ONLY
#import <LibreOfficeKit/LibreOfficeKitInit.h>

#import "ios.h"
#import "CODocument.h"
#import "DocumentPresentation.h"
#import "DocumentViewController.h"
#import "RecentDocumentsStore.h"

static NSString *const DocumentPresentationErrorDomain = @"com.xunlong.xloffice.document";

@implementation DocumentPresentation

+ (void)presentDocumentAtURL:(NSURL *)documentURL from:(UIViewController *)presenter {
    if (documentURL == nil || presenter == nil) {
        return;
    }

    [documentURL startAccessingSecurityScopedResource];
    RecentDocumentsStore *store = [[RecentDocumentsStore alloc] init];
    [store recordURL:documentURL];

    UIStoryboard *storyBoard = [UIStoryboard storyboardWithName:@"Main" bundle:nil];
    DocumentViewController *documentViewController = [storyBoard instantiateViewControllerWithIdentifier:@"DocumentViewController"];
    documentViewController.document = [[CODocument alloc] initWithFileURL:documentURL];
    documentViewController.document->fakeClientFd = -1;
    documentViewController.document->readOnly = false;
    documentViewController.document.viewController = documentViewController;
    documentViewController.modalPresentationStyle = UIModalPresentationFullScreen;
    [presenter presentViewController:documentViewController animated:YES completion:nil];
}

+ (NSURL *)createDocumentFromTemplateExtension:(NSString *)templateExtension
                               outputExtension:(NSString *)outputExtension
                                     basename:(NSString *)basename
                                        error:(NSError **)error {
    if (lo_kit == nullptr) {
        if (error != NULL) {
            *error = [NSError errorWithDomain:DocumentPresentationErrorDomain
                                         code:1
                                     userInfo:@{NSLocalizedDescriptionKey: @"Office engine is not ready"}];
        }
        return nil;
    }

    NSArray<NSURL *> *templates = [[NSBundle mainBundle] URLsForResourcesWithExtension:templateExtension
                                                                          subdirectory:@"Templates"];
    NSURL *templateURL = templates.firstObject;
    if (templateURL == nil) {
        if (error != NULL) {
            *error = [NSError errorWithDomain:DocumentPresentationErrorDomain
                                         code:2
                                     userInfo:@{NSLocalizedDescriptionKey: @"No template is available"}];
        }
        return nil;
    }

    NSURL *documents = [[[NSFileManager defaultManager] URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask] lastObject];
    NSString *fileName = [NSString stringWithFormat:@"%@.%@", basename, outputExtension];
    NSURL *destination = [documents URLByAppendingPathComponent:fileName];
    NSUInteger suffix = 2;
    while ([[NSFileManager defaultManager] fileExistsAtPath:destination.path]) {
        fileName = [NSString stringWithFormat:@"%@ %lu.%@", basename, (unsigned long)suffix, outputExtension];
        destination = [documents URLByAppendingPathComponent:fileName];
        suffix++;
    }

    LibreOfficeKitDocument *doc = lo_kit->pClass->documentLoad(lo_kit, [[templateURL absoluteString] UTF8String]);
    if (doc == nullptr) {
        if (error != NULL) {
            *error = [NSError errorWithDomain:DocumentPresentationErrorDomain
                                         code:3
                                     userInfo:@{NSLocalizedDescriptionKey: @"Failed to load template"}];
        }
        return nil;
    }
    doc->pClass->saveAs(doc, [[destination absoluteString] UTF8String], nullptr, nullptr);
    doc->pClass->destroy(doc);
    return destination;
}

@end
