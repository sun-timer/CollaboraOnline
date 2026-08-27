// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

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

+ (NSURL *)createBlankDocumentWithExtension:(NSString *)outputExtension
                                  basename:(NSString *)basename
                                     error:(NSError **)error {
    NSString *ext = outputExtension.lowercaseString ?: @"";
    if ([ext hasPrefix:@"."]) {
        ext = [ext substringFromIndex:1];
    }
    if (!([ext isEqualToString:@"odt"] || [ext isEqualToString:@"ods"] || [ext isEqualToString:@"odp"])) {
        if (error != NULL) {
            *error = [NSError errorWithDomain:DocumentPresentationErrorDomain
                                         code:2
                                     userInfo:@{NSLocalizedDescriptionKey: @"Unsupported blank document type"}];
        }
        return nil;
    }

    NSString *resourceName = [NSString stringWithFormat:@"untitled.%@", ext];
    NSURL *templateURL = [[NSBundle mainBundle] URLForResource:[resourceName stringByDeletingPathExtension]
                                                 withExtension:ext
                                                  subdirectory:@"Templates"];
    if (templateURL == nil) {
        // Fallback: some builds flatten Templates into the bundle root.
        templateURL = [[NSBundle mainBundle] URLForResource:[resourceName stringByDeletingPathExtension]
                                              withExtension:ext];
    }
    if (templateURL == nil) {
        if (error != NULL) {
            *error = [NSError errorWithDomain:DocumentPresentationErrorDomain
                                         code:2
                                     userInfo:@{NSLocalizedDescriptionKey: @"No blank template is available"}];
        }
        return nil;
    }

    NSString *base = basename.length > 0 ? basename : @"文档";
    NSURL *documents = [[[NSFileManager defaultManager] URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask] lastObject];
    NSString *fileName = [NSString stringWithFormat:@"%@.%@", base, ext];
    NSURL *destination = [documents URLByAppendingPathComponent:fileName];
    NSUInteger suffix = 2;
    while ([[NSFileManager defaultManager] fileExistsAtPath:destination.path]) {
        fileName = [NSString stringWithFormat:@"%@ %lu.%@", base, (unsigned long)suffix, ext];
        destination = [documents URLByAppendingPathComponent:fileName];
        suffix++;
    }

    NSError *copyError = nil;
    if (![[NSFileManager defaultManager] copyItemAtURL:templateURL toURL:destination error:&copyError]) {
        if (error != NULL) {
            *error = copyError ?: [NSError errorWithDomain:DocumentPresentationErrorDomain
                                                      code:3
                                                  userInfo:@{NSLocalizedDescriptionKey: @"Failed to create document"}];
        }
        return nil;
    }
    return destination;
}

+ (NSURL *)createDocumentFromTemplateExtension:(NSString *)templateExtension
                               outputExtension:(NSString *)outputExtension
                                     basename:(NSString *)basename
                                        error:(NSError **)error {
    // Ignore ott/ots/otp — never call lo_kit from the home UI thread.
    NSString *ext = outputExtension;
    if (ext.length == 0) {
        NSString *tmpl = templateExtension.lowercaseString ?: @"";
        if ([tmpl isEqualToString:@"ott"]) {
            ext = @"odt";
        } else if ([tmpl isEqualToString:@"ots"]) {
            ext = @"ods";
        } else if ([tmpl isEqualToString:@"otp"]) {
            ext = @"odp";
        } else {
            ext = tmpl;
        }
    }
    return [self createBlankDocumentWithExtension:ext basename:basename error:error];
}

@end
