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

#import "config.h"

#import <cstdio>
#import <string>
#import <vector>

#import <objc/message.h>
#import <objc/runtime.h>

#import <poll.h>
#import <sys/stat.h>

#import "ios.h"
#import "FakeSocket.hpp"
#import "COOLWSD.hpp"
#import "Log.hpp"
#import "MobileApp.hpp"
#import "SigUtil.hpp"
#import "Util.hpp"
#import "Clipboard.hpp"
#import "CoolURLSchemeHandler.h"
#import "AI/AIConfigurationStore.h"
#import "AI/AIModelConfigStore.h"
#import "AI/AIService.h"
#import "AI/AISettingsDrawerController.h"
#import "Bridge/NativeBridgeHandler.h"
#import "Toolbar/BottomToolbarController.h"
#import "Toolbar/TopToolbarController.h"

#import "DocumentViewController.h"

#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>
#import <Poco/MemoryStream.h>
#import <PhotosUI/PhotosUI.h>

@interface DocumentViewController() <WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler, WKScriptMessageHandlerWithReply, UIScrollViewDelegate, UIDocumentPickerDelegate, UIFontPickerViewControllerDelegate, PHPickerViewControllerDelegate, IOSTopToolbarControllerDelegate, IOSBottomToolbarControllerDelegate> {
    int closeNotificationPipeForForwardingThread[2];
    NSURL *downloadAsTmpURL;
    NativeBridgeHandler *nativeBridgeHandler;
    AIService *aiService;
    IOSTopToolbarController *topToolbarController;
    IOSBottomToolbarController *bottomToolbarController;
    NSLayoutConstraint *webViewTopConstraint;
    NSLayoutConstraint *webViewBottomConstraint;
    NSLayoutConstraint *bottomToolbarBottomConstraint;
    NSLayoutConstraint *bottomToolbarHeightConstraint;
    BOOL nativeEditMode;
    NSString *nativeDocumentType;
    AISettingsDrawerController *aiDrawer;
}

@end

// From https://gist.github.com/myell0w/d8dfabde43f8da543f9c
static BOOL isExternalKeyboardAttached()
{
    BOOL externalKeyboardAttached = NO;

    @try {
        NSString *keyboardClassName = [@[@"UI", @"Key", @"boa", @"rd", @"Im", @"pl"] componentsJoinedByString:@""];
        Class c = NSClassFromString(keyboardClassName);
        SEL sharedInstanceSEL = NSSelectorFromString(@"sharedInstance");
        if (c == Nil || ![c respondsToSelector:sharedInstanceSEL]) {
            return NO;
        }

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Warc-performSelector-leaks"
        id sharedKeyboardInstance = [c performSelector:sharedInstanceSEL];
#pragma clang diagnostic pop

        if (![sharedKeyboardInstance isKindOfClass:NSClassFromString(keyboardClassName)]) {
            return NO;
        }

        NSString *externalKeyboardSelectorName = [@[@"is", @"InH", @"ardw", @"areK", @"eyb", @"oard", @"Mode"] componentsJoinedByString:@""];
        SEL externalKeyboardSEL = NSSelectorFromString(externalKeyboardSelectorName);
        if (![sharedKeyboardInstance respondsToSelector:externalKeyboardSEL]) {
            return NO;
        }

        externalKeyboardAttached = ((BOOL ( *)(id, SEL))objc_msgSend)(sharedKeyboardInstance, externalKeyboardSEL);
    } @catch(__unused NSException *ex) {
        externalKeyboardAttached = NO;
    }

    return externalKeyboardAttached;
}

@implementation DocumentViewController

static IMP standardImpOfInputAccessoryView = nil;

- (void)viewDidLoad {
    [super viewDidLoad];

    WKWebViewConfiguration *configuration = [[WKWebViewConfiguration alloc] init];
    WKUserContentController *userContentController = [[WKUserContentController alloc] init];

    [userContentController addScriptMessageHandler:self name:@"debug"];
    [userContentController addScriptMessageHandler:self name:@"lok"];
    [userContentController addScriptMessageHandler:self name:@"error"];
    __weak DocumentViewController *weakSelf = self;
    aiService = [[AIService alloc] init];
    nativeBridgeHandler = [[NativeBridgeHandler alloc]
        initWithSessionIdProvider:^NSString * {
            DocumentViewController *strongSelf = weakSelf;
            return strongSelf
                ? [NSString stringWithFormat:@"ios-document-%u", strongSelf.document->appDocId]
                : nil;
        }
        emitter:^(NSDictionary *message) {
            DocumentViewController *strongSelf = weakSelf;
            if (!strongSelf.webView) {
                return;
            }
            NSData *data = [NSJSONSerialization dataWithJSONObject:message options:0 error:nil];
            NSString *json = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
            if (!json) {
                return;
            }
            NSString *script = [NSString stringWithFormat:
                @"if(window.NativeBridge&&typeof window.NativeBridge.onMessage==='function'){window.NativeBridge.onMessage(%@);}",
                json];
            [strongSelf.webView evaluateJavaScript:script completionHandler:nil];
        }
        aiService:aiService];
    [userContentController addScriptMessageHandler:nativeBridgeHandler name:@"nativeBridge"];
    [userContentController addScriptMessageHandlerWithReply:self contentWorld:[WKContentWorld pageWorld] name:@"clipboard"];
    [userContentController addScriptMessageHandlerWithReply:self contentWorld:[WKContentWorld pageWorld] name:@"aiConfiguration"];

    configuration.userContentController = userContentController;
    
    CoolURLSchemeHandler * schemeHandler = [[CoolURLSchemeHandler alloc] initWithDocument:self.document];
    [configuration setURLSchemeHandler:schemeHandler forURLScheme:@"cool"];
    self.schemeHandler = (__bridge void*)schemeHandler;
    
    self.webView = [[WKWebView alloc] initWithFrame:CGRectZero configuration:configuration];
    self.webView.translatesAutoresizingMaskIntoConstraints = NO;
    self.webView.allowsLinkPreview = NO;

    // Prevent the WebView from scrolling. Sadly I couldn't figure out how to do it in the JS,
    // so the problem is still there when using Online from Mobile Safari.
    self.webView.scrollView.scrollEnabled = NO;

    // Reenable debugging from Safari
    // The new WKWebView.inspectable property must be set to YES in order
    // for Safari to connect to a debug version of the iOS app whether the
    // app is running on an iOS device or on macOS.
    if (@available(macOS 13.3, iOS 16.4, tvOS 16.4, *)) {
#if ENABLE_DEBUG == 1
        self.webView.inspectable = YES;
#else
        self.webView.inspectable = NO;
#endif
    }

    // Prevent the user from zooming the WebView by assigning ourselves as the delegate, and
    // stopping any zoom attempt in scrollViewWillBeginZooming: below. (The zooming of the document
    // contents is handled fully in JavaScript, the WebView has no knowledge of that.)
    self.webView.scrollView.delegate = self;

    self.view.backgroundColor = UIColor.whiteColor;
    [self.view addSubview:self.webView];

    UIView *topToolbarContainer = [[UIView alloc] init];
    topToolbarContainer.translatesAutoresizingMaskIntoConstraints = NO;
    topToolbarContainer.backgroundColor = UIColor.whiteColor;
    [self.view addSubview:topToolbarContainer];

    UIView *bottomToolbarContainer = [[UIView alloc] init];
    bottomToolbarContainer.translatesAutoresizingMaskIntoConstraints = NO;
    bottomToolbarContainer.backgroundColor = UIColor.whiteColor;
    [self.view addSubview:bottomToolbarContainer];

    nativeEditMode = NO;
    NSURL *documentURL = self.document->copyFileURL;
    NSString *extension = documentURL.pathExtension.lowercaseString;
    if ([extension isEqualToString:@"xls"] || [extension isEqualToString:@"xlsx"]
        || [extension isEqualToString:@"ods"] || [extension isEqualToString:@"csv"]) {
        nativeDocumentType = @"spreadsheet";
    } else if ([extension isEqualToString:@"ppt"] || [extension isEqualToString:@"pptx"]
               || [extension isEqualToString:@"odp"]) {
        nativeDocumentType = @"presentation";
    } else {
        nativeDocumentType = @"text";
    }
    topToolbarController = [[IOSTopToolbarController alloc] initWithDelegate:self];
    bottomToolbarController = [[IOSBottomToolbarController alloc] initWithDelegate:self];
    [topToolbarController setDocumentTitle:
        [[documentURL.lastPathComponent stringByDeletingPathExtension] copy]];
    [topToolbarController setDocumentType:nativeDocumentType];
    [bottomToolbarController setDocumentType:nativeDocumentType];
    [topToolbarContainer addSubview:topToolbarController.view];
    [bottomToolbarContainer addSubview:bottomToolbarController.view];

    [NSLayoutConstraint activateConstraints:@[
        [topToolbarContainer.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor],
        [topToolbarContainer.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [topToolbarContainer.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [topToolbarContainer.heightAnchor constraintEqualToConstant:56.0],
        [topToolbarController.view.topAnchor constraintEqualToAnchor:topToolbarContainer.topAnchor],
        [topToolbarController.view.leadingAnchor constraintEqualToAnchor:topToolbarContainer.leadingAnchor],
        [topToolbarController.view.trailingAnchor constraintEqualToAnchor:topToolbarContainer.trailingAnchor],
        [topToolbarController.view.bottomAnchor constraintEqualToAnchor:topToolbarContainer.bottomAnchor],
        [bottomToolbarContainer.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [bottomToolbarContainer.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [bottomToolbarController.view.topAnchor constraintEqualToAnchor:bottomToolbarContainer.topAnchor],
        [bottomToolbarController.view.leadingAnchor constraintEqualToAnchor:bottomToolbarContainer.leadingAnchor],
        [bottomToolbarController.view.trailingAnchor constraintEqualToAnchor:bottomToolbarContainer.trailingAnchor],
        [bottomToolbarController.view.bottomAnchor constraintEqualToAnchor:bottomToolbarContainer.bottomAnchor],
    ]];
    bottomToolbarBottomConstraint = [bottomToolbarContainer.bottomAnchor
        constraintEqualToAnchor:self.view.safeAreaLayoutGuide.bottomAnchor];
    bottomToolbarBottomConstraint.active = YES;
    bottomToolbarHeightConstraint = [bottomToolbarContainer.heightAnchor
        constraintEqualToConstant:bottomToolbarController.preferredHeight];
    bottomToolbarHeightConstraint.active = YES;

    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(keyboardWillChangeFrame:)
                                                 name:UIKeyboardWillChangeFrameNotification
                                               object:nil];

    self.webView.navigationDelegate = self;
    self.webView.UIDelegate = self;

    // Hack for tdf#129380: Don't show the "shortcut bar" if a hardware keyboard is used.

    // From https://inneka.com/programming/objective-c/hide-shortcut-keyboard-bar-for-uiwebview-in-ios-9/
    Class webBrowserClass = NSClassFromString(@"WKContentView");
    Method method = class_getInstanceMethod(webBrowserClass, @selector(inputAccessoryView));

    if (isExternalKeyboardAttached()) {
        IMP newImp = imp_implementationWithBlock(^(id _s) {
                if ([self.webView respondsToSelector:@selector(inputAssistantItem)]) {
                    UITextInputAssistantItem *inputAssistantItem = [self.webView inputAssistantItem];
                    inputAssistantItem.leadingBarButtonGroups = @[];
                    inputAssistantItem.trailingBarButtonGroups = @[];
                }
                return nil;
            });

        IMP oldImp = method_setImplementation(method, newImp);
        if (standardImpOfInputAccessoryView == nil)
            standardImpOfInputAccessoryView = oldImp;
    } else {
        // If the external keyboard has been disconnected, restore the normal behaviour.
        if (standardImpOfInputAccessoryView != nil) {
            method_setImplementation(method, standardImpOfInputAccessoryView);
        }

        // Hack to make the on-screen keyboard pop up more eagerly when focus set to the textarea
        // using JavaScript.

        // From https://stackoverflow.com/questions/32449870/programmatically-focus-on-a-form-in-a-webview-wkwebview/32845699

        static bool doneThisAlready = false;
        if (!doneThisAlready) {
            const char * methodSignature;
            doneThisAlready = true;

            if ([[NSProcessInfo processInfo] isOperatingSystemAtLeastVersion: (NSOperatingSystemVersion){13, 0, 0}]) {
                methodSignature = "_elementDidFocus:userIsInteracting:blurPreviousNode:activityStateChanges:userObject:";
            } else {
                methodSignature = "_elementDidFocus:userIsInteracting:blurPreviousNode:changingActivityState:userObject:";
            }

            // Override that internal method with an own wrapper that always passes the
            // userIsInteracting parameter as TRUE. That will cause the on-screen keyboard to pop up
            // when we call the focus() method on the textarea element in JavaScript.
            SEL selector = sel_getUid(methodSignature);
            Method method = class_getInstanceMethod(webBrowserClass, selector);
            if (method != nil) {
                IMP original = method_getImplementation(method);
                IMP override = imp_implementationWithBlock(^void(id me, void* arg0, BOOL arg1, BOOL arg2, BOOL arg3, id arg4) {
                        ((void (*)(id, SEL, void*, BOOL, BOOL, BOOL, id))original)(me, selector, arg0, TRUE, arg2, arg3, arg4);
                    });
                method_setImplementation(method, override);
            }
        }
    }

    webViewTopConstraint = [self.webView.topAnchor constraintEqualToAnchor:topToolbarContainer.bottomAnchor];
    webViewBottomConstraint = [self.webView.bottomAnchor constraintEqualToAnchor:bottomToolbarContainer.topAnchor];
    [NSLayoutConstraint activateConstraints:@[
        [self.webView.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.webView.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        webViewTopConstraint,
        webViewBottomConstraint,
    ]];
    aiDrawer = [AISettingsDrawerController attachToHost:self];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];

    // When the user uses the camer to insert a photo, when the camera is displayed, this view is
    // removed. After the photo is taken it is then added back to the hierarchy. Our Document object
    // is still there intact, however, so no need to re-open the document when we re-appear.

    // Check whether the Document object is an already initialised one.
    if (self.document->fakeClientFd >= 0)
        return;

    [self.document openWithCompletionHandler:^(BOOL success) {
        if (success) {
            // Display the content of the document
        } else {
            // Make sure to handle the failed import appropriately, e.g., by presenting an error message to the user.
        }
    }];
}

- (IBAction)dismissDocumentViewController {
    [nativeBridgeHandler cancelAllRequests];
    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                    name:UIKeyboardWillChangeFrameNotification
                                                  object:nil];
    [self dismissViewControllerAnimated:YES completion:^ {
            [self.document closeWithCompletionHandler:^(BOOL success){
                    LOG_TRC("close completion handler gets " << (success?"YES":"NO"));
                    [self.webView.configuration.userContentController removeScriptMessageHandlerForName:@"debug"];
                    [self.webView.configuration.userContentController removeScriptMessageHandlerForName:@"lok"];
                    [self.webView.configuration.userContentController removeScriptMessageHandlerForName:@"error"];
                    [self.webView.configuration.userContentController removeScriptMessageHandlerForName:@"nativeBridge"];
                    [self.webView.configuration.userContentController removeScriptMessageHandlerForName:@"aiConfiguration"];
                    self->nativeBridgeHandler = nil;
                    self->aiService = nil;
                    // Don't set webView.configuration.userContentController to
                    // nil as it generates a "nil not allowed" compiler warning
                    [self.webView removeFromSuperview];
                    self.webView = nil;
                    }];
    }];
}

- (void)webView:(WKWebView *)webView didCommitNavigation:(WKNavigation *)navigation {
    LOG_TRC("didCommitNavigation: " << [[navigation description] UTF8String]);
}

- (void)webView:(WKWebView *)webView didFailNavigation:(WKNavigation *)navigation withError:(NSError *)error {
    LOG_TRC("didFailNavigation: " << [[navigation description] UTF8String]);
}

- (void)webView:(WKWebView *)webView didFailProvisionalNavigation:(WKNavigation *)navigation withError:(NSError *)error {
    LOG_TRC("didFailProvisionalNavigation: " << [[navigation description] UTF8String]);
}

- (void)webView:(WKWebView *)webView didFinishNavigation:(WKNavigation *)navigation {
    LOG_TRC("didFinishNavigation: " << [[navigation description] UTF8String]);
}

- (void)webView:(WKWebView *)webView didReceiveServerRedirectForProvisionalNavigation:(WKNavigation *)navigation {
    LOG_TRC("didReceiveServerRedirectForProvisionalNavigation: " << [[navigation description] UTF8String]);
}

- (void)webView:(WKWebView *)webView didStartProvisionalNavigation:(WKNavigation *)navigation {
    LOG_TRC("didStartProvisionalNavigation: " << [[navigation description] UTF8String]);
}

- (void)webView:(WKWebView *)webView decidePolicyForNavigationAction:(WKNavigationAction *)navigationAction decisionHandler:(void (^)(WKNavigationActionPolicy))decisionHandler {
    LOG_TRC("decidePolicyForNavigationAction: " << [[navigationAction description] UTF8String]);
    decisionHandler(WKNavigationActionPolicyAllow);
}

- (void)webView:(WKWebView *)webView decidePolicyForNavigationResponse:(WKNavigationResponse *)navigationResponse decisionHandler:(void (^)(WKNavigationResponsePolicy))decisionHandler {
    LOG_TRC("decidePolicyForNavigationResponse: " << [[navigationResponse description] UTF8String]);
    decisionHandler(WKNavigationResponsePolicyAllow);
}

- (WKWebView *)webView:(WKWebView *)webView createWebViewWithConfiguration:(WKWebViewConfiguration *)configuration forNavigationAction:(WKNavigationAction *)navigationAction windowFeatures:(WKWindowFeatures *)windowFeatures {
    LOG_TRC("createWebViewWithConfiguration");
    return webView;
}

- (void)webView:(WKWebView *)webView runJavaScriptAlertPanelWithMessage:(NSString *)message initiatedByFrame:(WKFrameInfo *)frame completionHandler:(void (^)(void))completionHandler {
    LOG_TRC("runJavaScriptAlertPanelWithMessage: " << [message UTF8String]);
    //    UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@""
    //                                                    message:message
    //                                                   delegate:nil
    //                                          cancelButtonTitle:nil
    //                                          otherButtonTitles:@"OK", nil];
    //    [alert show];
    completionHandler();
}

- (void)webView:(WKWebView *)webView runJavaScriptConfirmPanelWithMessage:(NSString *)message initiatedByFrame:(WKFrameInfo *)frame completionHandler:(void (^)(BOOL result))completionHandler {
    LOG_TRC("runJavaScriptConfirmPanelWithMessage: " << [message UTF8String]);
    completionHandler(YES);
}

- (void)webView:(WKWebView *)webView runJavaScriptTextInputPanelWithPrompt:(NSString *)prompt defaultText:(NSString *)defaultText initiatedByFrame:(WKFrameInfo *)frame completionHandler:(void (^)(NSString *result))completionHandler {
    LOG_TRC("runJavaScriptTextInputPanelWithPrompt: " << [prompt UTF8String]);
    completionHandler(@"Something happened.");
}

- (void)webViewWebContentProcessDidTerminate:(WKWebView *)webView {
    // Fix issue #5876 by closing the document if the content process dies
    [self bye];
    LOG_ERR("WebContent process terminated! Is closing the document enough?");
}

// This is the same method as Java_org_libreoffice_androidlib_LOActivity_getClipboardContent, with minimal editing to work with objective C
- (bool)getClipboardContent:(out NSMutableDictionary *)content {
    const char** mimeTypes = nullptr;
    size_t outCount = 0;
    char  **outMimeTypes = nullptr;
    size_t *outSizes = nullptr;
    char  **outStreams = nullptr;
    bool bResult = false;

    if (DocumentData::get(self.document->appDocId).loKitDocument->getClipboard(mimeTypes,
                                                     &outCount, &outMimeTypes,
                                                     &outSizes, &outStreams))
    {
        // return early
        if (outCount == 0)
            return bResult;

        for (size_t i = 0; i < outCount; ++i)
        {
            NSString * identifier = [NSString stringWithUTF8String:outMimeTypes[i]];

            // For interop with other apps, if this mime-type is known we can export it
            UTType * uti = [UTType typeWithMIMEType:identifier];
            if (uti != nil && !uti.dynamic) {
                if ([uti conformsToType:UTTypePlainText]) {
                    [content setValue:outStreams[i] == NULL ? @"" : [NSString stringWithUTF8String:outStreams[i]] forKey:uti.identifier];
                } else if (uti != nil && [uti conformsToType:UTTypeImage]) {
                    [content setValue:[UIImage imageWithData:[NSData dataWithBytes:outStreams[i] length:outSizes[i]]] forKey:uti.identifier];
                } else {
                    [content setValue:[NSData dataWithBytes:outStreams[i] length:outSizes[i]] forKey:uti.identifier];
                }
            }
            
            // But to preserve the data we need, we'll always also export the raw, unaltered bytes
            [content setValue:[NSData dataWithBytes:outStreams[i] length:outSizes[i]] forKey:identifier];
        }
        bResult = true;
    }
    else
        LOG_DBG("failed to fetch mime-types");

    const char* mimeTypesHTML[] = { "text/plain;charset=utf-8", "text/html", nullptr };

    if (DocumentData::get(self.document->appDocId).loKitDocument->getClipboard(mimeTypesHTML,
                                                     &outCount, &outMimeTypes,
                                                     &outSizes, &outStreams))
    {
        // return early
        if (outCount == 0)
            return bResult;

        for (size_t i = 0; i < outCount; ++i)
        {
            NSString * identifier = [NSString stringWithUTF8String:outMimeTypes[i]];

            // For interop with other apps, if this mime-type is known we can export it
            UTType * uti = [UTType typeWithMIMEType:identifier];
            if (uti != nil && !uti.dynamic) {
                if ([uti conformsToType:UTTypePlainText]) {
                    [content setValue:outStreams[i] == NULL ? @"" : [NSString stringWithUTF8String:outStreams[i]] forKey:uti.identifier];
                } else if (uti != nil && [uti conformsToType:UTTypeImage]) {
                    [content setValue:[UIImage imageWithData:[NSData dataWithBytes:outStreams[i] length:outSizes[i]]] forKey:uti.identifier];
                } else {
                    [content setValue:[NSData dataWithBytes:outStreams[i] length:outSizes[i]] forKey:uti.identifier];
                }
            }
            
            // But to preserve the data we need, we'll always also export the raw, unaltered bytes
            [content setValue:[NSData dataWithBytes:outStreams[i] length:outSizes[i]] forKey:identifier];
        }
        bResult = true;
    }
    else
        LOG_DBG("failed to fetch mime-types");

    return bResult;
}

- (void)setClipboardContent:(UIPasteboard *)pasteboard {
    NSMutableDictionary * pasteboardItems = [NSMutableDictionary new];
    
    if (pasteboard.numberOfItems != 0) {
        for (NSString * identifier in pasteboard.items[0])
        {
            UTType * uti = [UTType typeWithIdentifier:identifier];
            NSString * mime = identifier;
            
            if (uti != nil) {
                mime = uti.preferredMIMEType;
            }
            
            if (mime == nil) {
                LOG_WRN("UTI " << [identifier UTF8String] << " did not have associated mime type when deserializing clipboard, skipping...");
                continue;
            }
            
            NSData * value = [pasteboard dataForPasteboardType:identifier];
            
            if (uti != nil && [pasteboardItems objectForKey:mime] != nil) {
                // We export both mime and UTI keys, don't overwrite the mime-type ones with the UTI ones
                continue;
            }
            
            if (value != nil) {
                [pasteboardItems setObject:value forKey:mime];
            }
        }
    }
    
    const char * pInMimeTypes[pasteboardItems.count];
    size_t pInSizes[pasteboardItems.count];
    const char * pInStreams[pasteboardItems.count];
    
    size_t i = 0;
    
    for (NSString * mime in pasteboardItems) {
        pInMimeTypes[i] = [mime UTF8String];
        pInStreams[i] = (const char*)[pasteboardItems[mime] bytes];
        pInSizes[i] = [pasteboardItems[mime] length];
        i++;
    }
    
    DocumentData::get(self.document->appDocId).loKitDocument->setClipboard(pasteboardItems.count, pInMimeTypes, pInSizes, pInStreams);
}

- (void)userContentController:(WKUserContentController *)userContentController didReceiveScriptMessage:(WKScriptMessage *)message replyHandler:(nonnull void (^)(id _Nullable, NSString * _Nullable))replyHandler {

    if ([message.name isEqualToString:@"aiConfiguration"]) {
        AIConfigurationStore *store = [[AIConfigurationStore alloc] init];
        NSDictionary *body = [message.body isKindOfClass:[NSDictionary class]]
            ? message.body : @{};
        NSString *action = [body[@"action"] isKindOfClass:[NSString class]]
            ? body[@"action"] : @"";
        if ([action isEqualToString:@"get"]) {
            NSError *error = nil;
            AIConfiguration *configuration = [store configurationWithError:&error];
            replyHandler(@{
                @"configured": @(configuration != nil && store.isConfigured),
                @"endpoint": configuration.endpoint ?: @"",
                @"model": configuration.model ?: @"",
            }, nil);
        } else if ([action isEqualToString:@"save"]) {
            NSString *endpoint = [body[@"endpoint"] isKindOfClass:[NSString class]]
                ? body[@"endpoint"] : @"";
            NSString *model = [body[@"model"] isKindOfClass:[NSString class]]
                ? body[@"model"] : @"";
            NSString *apiKey = [body[@"apiKey"] isKindOfClass:[NSString class]]
                ? body[@"apiKey"] : @"";
            NSError *error = nil;
            AIModelConfigStore *modelStore = [[AIModelConfigStore alloc] init];
            AIModelConfigForm *form = [modelStore loadForm:AIModelTypeBase];
            form.url = endpoint;
            form.modelName = model;
            form.apiKey = apiKey;
            BOOL saved = [modelStore saveForm:form modelType:AIModelTypeBase error:&error];
            replyHandler(@{
                @"configured": @(saved && store.isConfigured),
            }, saved ? nil : (error.localizedDescription ?: @"Failed to save AI configuration"));
        } else {
            replyHandler(nil, @"Unsupported AI configuration action");
        }
    } else if ([message.name isEqualToString:@"clipboard"]) {
        if ([message.body isEqualToString:@"read"]) {
            UIPasteboard * pasteboard = [UIPasteboard generalPasteboard];
            
            [self setClipboardContent:pasteboard];
            
            replyHandler(@"(internal)", nil);
        } else if ([message.body isEqualToString:@"write"]) {
            NSMutableDictionary * pasteboardItem = [NSMutableDictionary dictionaryWithCapacity:2];
            bool success = [self getClipboardContent:pasteboardItem];
            
            if (!success) {
                replyHandler(nil, @"Failed to get clipboard contents...");
                return;
            }
            
            UIPasteboard * pasteboard = [UIPasteboard generalPasteboard];

            [pasteboard setItems:[NSArray arrayWithObject:pasteboardItem]];
            
            replyHandler(nil, nil);
        } else if ([message.body hasPrefix:@"sendToInternal "]) {
            ClipboardData data;
            NSString * content = [message.body substringFromIndex:[@"sendToInternal " length]];
            std::vector<char> html;
            
            size_t nInCount;
            
            if ([content hasPrefix:@"<!DOCTYPE html>"]) {
                // Content is just HTML
                const char * _Nullable content_cstr = [content cStringUsingEncoding:NSUTF8StringEncoding];
                html = std::vector(content_cstr, content_cstr + [content lengthOfBytesUsingEncoding:NSUTF8StringEncoding]);
                nInCount = 1;
            } else {
                Poco::MemoryInputStream stream([content cStringUsingEncoding:NSUTF8StringEncoding], [content lengthOfBytesUsingEncoding:NSUTF8StringEncoding]);
                data.read(stream);
                nInCount = data.size();
            }
            
            std::vector<size_t> pInSizes(nInCount);
            std::vector<const char*> pInMimeTypes(nInCount);
            std::vector<const char*> pInStreams(nInCount);
            
            if (html.empty()) {
                for (size_t i = 0; i < nInCount; ++i) {
                    pInSizes[i] = data._content[i].length();
                    pInStreams[i] = data._content[i].c_str();
                    pInMimeTypes[i] = data._mimeTypes[i].c_str();
                }
            } else {
                pInSizes[0] = html.size();
                pInStreams[0] = html.data();
                pInMimeTypes[0] = "text/html";
            }
            
            if (!DocumentData::get(self.document->appDocId).loKitDocument->setClipboard(nInCount, pInMimeTypes.data(), pInSizes.data(),
                                                                                        pInStreams.data())) {
                LOG_ERR("set clipboard returned failure");
                replyHandler(nil, @"set clipboard returned failure");
            } else {
                LOG_TRC("set clipboard succeeded");
                replyHandler(nil, nil);
            }
        } else {
            replyHandler(nil, [NSString stringWithFormat:@"Invalid clipboard action %@", message.body]);
        }
    } else {
        LOG_ERR("Unrecognized kind of message received from WebView: " << [message.name UTF8String] << ":" << [message.body UTF8String]);
        replyHandler(nil, [NSString stringWithFormat:@"Message of type %@ does not exist or is not replyable", message.name]);
    }
}

- (void)userContentController:(WKUserContentController *)userContentController didReceiveScriptMessage:(WKScriptMessage *)message {
    int rc;
    struct pollfd p;

    if ([message.name isEqualToString:@"error"]) {
        LOG_ERR("Error from WebView: " << [message.body UTF8String]);
    } else if ([message.name isEqualToString:@"debug"]) {
        std::cerr << "==> " << [message.body UTF8String] << std::endl;
    } else if ([message.name isEqualToString:@"lok"]) {
        NSString *subBody = [message.body substringToIndex:std::min(100ul, ((NSString*)message.body).length)];
        if (subBody.length < ((NSString*)message.body).length)
            subBody = [subBody stringByAppendingString:@"..."];

        LOG_DBG("To Online: " << [subBody UTF8String]);

#if 0
        static int n = 0;

        if ((n++ % 10) == 0) {
            auto enumerator = [[NSFileManager defaultManager] enumeratorAtPath:NSHomeDirectory()];
            NSString *file;
            long long total = 0;
            while ((file = [enumerator nextObject])) {
                if ([enumerator fileAttributes][NSFileType] == NSFileTypeRegular)
                    total += [[enumerator fileAttributes][NSFileSize] longLongValue];
            }
            NSLog(@"==== Total size of app home directory: %lld", total);
        }
#endif

        if ([message.body isEqualToString:@"HULLO"]) {
            // Now we know that the JS has started completely

            // Contact the permanently (during app lifetime) listening COOLWSD server
            // "public" socket
            assert(coolwsd_server_socket_fd != -1);
            rc = fakeSocketConnect(self.document->fakeClientFd, coolwsd_server_socket_fd);
            assert(rc != -1);

            // Create a socket pair to notify the below thread when the document has been closed
            fakeSocketPipe2(closeNotificationPipeForForwardingThread);

            // Start another thread to read responses and forward them to the JavaScript
            dispatch_async(dispatch_get_global_queue( DISPATCH_QUEUE_PRIORITY_DEFAULT, 0),
                           ^{
                               Util::setThreadName("app2js");
                               while (true) {
                                   struct pollfd p[2];
                                   p[0].fd = self.document->fakeClientFd;
                                   p[0].events = POLLIN;
                                   p[1].fd = self->closeNotificationPipeForForwardingThread[1];
                                   p[1].events = POLLIN;
                                   if (fakeSocketPoll(p, 2, -1) > 0) {
                                       if (p[1].revents == POLLIN) {
                                           // The code below handling the "BYE" fake Websocket
                                           // message has closed the other end of the
                                           // closeNotificationPipeForForwardingThread. Let's close
                                           // the other end too just for cleanliness, even if a
                                           // FakeSocket as such is not a system resource so nothing
                                           // is saved by closing it.
                                           fakeSocketClose(self->closeNotificationPipeForForwardingThread[1]);

                                           // Close our end of the fake socket connection to the
                                           // ClientSession thread, so that it terminates
                                           fakeSocketClose(self.document->fakeClientFd);

                                           return;
                                       }
                                       if (p[0].revents == POLLIN) {
                                           int n = fakeSocketAvailableDataLength(self.document->fakeClientFd);
                                           // I don't want to check for n being -1 here, even if
                                           // that will lead to a crash (std::length_error from the
                                           // below std::vector constructor), as n being -1 is a
                                           // sign of something being wrong elsewhere anyway, and I
                                           // prefer to fix the root cause. Let's see how well this
                                           // works out. See tdf#122543 for such a case.
                                           if (n == 0)
                                               return;
                                           std::vector<char> buf(n);
                                           n = fakeSocketRead(self.document->fakeClientFd, buf.data(), n);
                                           [self.document send2JS:buf.data() length:n];
                                       }
                                   }
                                   else
                                       break;
                               }
                               assert(false);
                           });

            // First we simply send the Online C++ parts the URL and the appDocId. This corresponds
            // to the GET request with Upgrade to WebSocket.
            std::string url([[self.document->copyFileURL absoluteString] UTF8String]);
            p.fd = self.document->fakeClientFd;
            p.events = POLLOUT;
            fakeSocketPoll(&p, 1, -1);

            // This is read in the iOS-specific code in ClientRequestDispatcher::handleIncomingMessage() in COOLWSD.cpp
            std::string message(url + " " + std::to_string(self.document->appDocId));
            fakeSocketWrite(self.document->fakeClientFd, message.c_str(), message.size());

            return;
        } else if ([message.body isEqualToString:@"BYE"]) {
            LOG_TRC("Document window terminating on JavaScript side. Closing our end of the socket.");

            [self bye];
            return;
        } else if ([message.body isEqualToString:@"PRINT"]) {

            // Create the PDF to print.

            std::string printFile = FileUtil::createRandomTmpDir() + "/print.pdf";
            NSURL *printURL = [NSURL fileURLWithPath:[NSString stringWithUTF8String:printFile.c_str()] isDirectory:NO];
            DocumentData::get(self.document->appDocId).loKitDocument->saveAs([[printURL absoluteString] UTF8String], "pdf", nullptr);

            UIPrintInteractionController *pic = [UIPrintInteractionController sharedPrintController];
            UIPrintInfo *printInfo = [UIPrintInfo printInfo];
            printInfo.outputType = UIPrintInfoOutputGeneral;
            printInfo.orientation = UIPrintInfoOrientationPortrait; // FIXME Check the document?
            printInfo.jobName = @"Document"; // FIXME

            pic.printInfo = printInfo;
            pic.printingItem = printURL;

            [pic presentFromRect:CGRectZero
                          inView:self.webView
                        animated:YES
               completionHandler:^(UIPrintInteractionController *pic, BOOL completed, NSError *error) {
                    LOG_TRC("print completion handler gets " << (completed?"YES":"NO"));
                    std::remove(printFile.c_str());
                }];

            return;
        } else if ([message.body isEqualToString:@"FOCUSIFHWKBD"]) {
            if (isExternalKeyboardAttached()) {
                NSString *hwKeyboardMagic = @"{"
                    "    if (window.MagicToGetHWKeyboardWorking) {"
                    "        window.MagicToGetHWKeyboardWorking();"
                    "    }"
                    "}";
                [self.webView evaluateJavaScript:hwKeyboardMagic
                               completionHandler:^(id _Nullable obj, NSError * _Nullable error)
                     {
                         if (error) {
                             LOG_ERR("Error after " << [hwKeyboardMagic UTF8String] << ": " << [[error localizedDescription] UTF8String]);
                             NSString *jsException = error.userInfo[@"WKJavaScriptExceptionMessage"];
                             if (jsException != nil)
                                 LOG_ERR("JavaScript exception: " << [jsException UTF8String]);
                         }
                     }
                 ];
            }

            return;
        } else if ([message.body hasPrefix:@"EDITMODE "]) {
            [self applyNativeEditMode:[message.body isEqualToString:@"EDITMODE on"]];
            return;
        } else if ([message.body hasPrefix:@"UNDOREDO "]) {
            [self applyNativeUndoRedoState:message.body];
            return;
        } else if ([message.body hasPrefix:@"CALC_CELL_TAP"]) {
            // Android-only diagnostic/gesture bridge message.  Older shared
            // Browser bundles may still emit it; never send it to Core.
            LOG_TRC("Ignoring Android-only CALC_CELL_TAP message from WebView");
            return;
        } else if ([message.body hasPrefix:@"OPENLINK"] || [message.body hasPrefix:@"HYPERLINK"]) {
            NSArray *messageBodyItems = [message.body componentsSeparatedByString:@" "];
            if ([messageBodyItems count] >= 2) {
                NSURL *url = [[NSURL alloc] initWithString:messageBodyItems[1]];
                if (url != nil) {
                    UIApplication *application = [UIApplication sharedApplication];
                    [application openURL:url options:@{} completionHandler:nil];
                }
            }
            // Consume the native link command even when its payload is malformed;
            // it must never fall through to the Core/FakeSocket protocol.
            return;
        } else if ([message.body isEqualToString:@"FONTPICKER"]) {
            UIFontPickerViewControllerConfiguration *configuration = [[UIFontPickerViewControllerConfiguration alloc] init];
            configuration.includeFaces = NO;
            UIFontPickerViewController *picker = [[UIFontPickerViewController alloc] initWithConfiguration:configuration];
            picker.delegate = self;
            [self presentViewController:picker
                               animated:YES
                             completion:nil];
            return;
        } else if ([message.body hasPrefix:@"downloadas "]) {
            NSArray<NSString*> *messageBodyItems = [message.body componentsSeparatedByString:@" "];
            NSString *format = nil;
            if ([messageBodyItems count] >= 2) {
                for (int i = 1; i < [messageBodyItems count]; i++) {
                    if ([messageBodyItems[i] hasPrefix:@"format="])
                        format = [messageBodyItems[i] substringFromIndex:[@"format=" length]];
                }

                if (format == nil)
                    return;     // Warn?

                // Handle special "direct-" formats
                NSRange range = [format rangeOfString:@"direct-"];
                if (range.location == 0)
                    format = [format substringFromIndex:range.length];

                // First save it in the requested format to a temporary location. First remove any
                // leftover identically named temporary file.

                NSURL *tmpFileDirectory = [[NSFileManager.defaultManager temporaryDirectory] URLByAppendingPathComponent:@"export"];
                if (![NSFileManager.defaultManager createDirectoryAtURL:tmpFileDirectory withIntermediateDirectories:YES attributes:nil error:nil]) {
                    LOG_ERR("Could not create directory " << [[tmpFileDirectory path] UTF8String]);
                    return;
                }
                NSString *tmpFileName = [[[self.document->copyFileURL lastPathComponent] stringByDeletingPathExtension] stringByAppendingString:[@"." stringByAppendingString:format]];
                downloadAsTmpURL = [tmpFileDirectory URLByAppendingPathComponent:tmpFileName];

                std::remove([[downloadAsTmpURL path] UTF8String]);

                DocumentData::get(self.document->appDocId).loKitDocument->saveAs([[downloadAsTmpURL absoluteString] UTF8String], [format UTF8String], nullptr);

                // Then verify that it indeed was saved, and then use an
                // UIDocumentPickerViewController to ask the user where to store the exported
                // document.

                struct stat statBuf;
                if (stat([[downloadAsTmpURL path] UTF8String], &statBuf) == -1) {
                    LOG_ERR("Could apparently not save to '" <<  [[downloadAsTmpURL path] UTF8String] << "'");
                    return;
                }
                UIDocumentPickerViewController *picker =
                    [[UIDocumentPickerViewController alloc] initForExportingURLs:[NSArray arrayWithObject:downloadAsTmpURL] asCopy:YES];
                picker.delegate = self;
                [self presentViewController:picker
                                   animated:YES
                                 completion:nil];
                return;
            }
        }

        const char *buf = [message.body UTF8String];
        fakeSocketWriteQueue(self.document->fakeClientFd, buf, strlen(buf));
    } else {
        LOG_ERR("Unrecognized kind of message received from WebView: " << [message.name UTF8String] << ":" << [message.body UTF8String]);
    }
}

- (void)documentPicker:(UIDocumentPickerViewController *)controller didPickDocumentsAtURLs:(NSArray<NSURL *> *)urls {
    std::remove([[downloadAsTmpURL path] UTF8String]);
    std::remove([[[downloadAsTmpURL URLByDeletingLastPathComponent] path] UTF8String]);
}

- (void)documentPickerWasCancelled:(UIDocumentPickerViewController *)controller {
    std::remove([[downloadAsTmpURL path] UTF8String]);
    std::remove([[[downloadAsTmpURL URLByDeletingLastPathComponent] path] UTF8String]);
}

- (void)scrollViewWillBeginZooming:(UIScrollView *)scrollView withView:(UIView *)view {
    scrollView.pinchGestureRecognizer.enabled = NO;
}

- (void)keyboardWillChangeFrame:(NSNotification *)notification
{
    CGRect keyboardFrame = [notification.userInfo[UIKeyboardFrameEndUserInfoKey] CGRectValue];
    CGRect localKeyboardFrame = [self.view convertRect:keyboardFrame fromView:nil];
    CGFloat safeBottom = CGRectGetMaxY(self.view.bounds) - self.view.safeAreaInsets.bottom;
    BOOL keyboardVisible = CGRectGetMinY(localKeyboardFrame) < CGRectGetMaxY(self.view.bounds);
    CGFloat bottomOffset = keyboardVisible ? MIN(0.0, CGRectGetMinY(localKeyboardFrame) - safeBottom) : 0.0;
    NSTimeInterval duration = [notification.userInfo[UIKeyboardAnimationDurationUserInfoKey] doubleValue];
    UIViewAnimationOptions options =
        ([notification.userInfo[UIKeyboardAnimationCurveUserInfoKey] integerValue] << 16)
        | UIViewAnimationOptionBeginFromCurrentState;

    bottomToolbarController.compact = keyboardVisible;
    bottomToolbarHeightConstraint.constant = bottomToolbarController.preferredHeight;
    bottomToolbarBottomConstraint.constant = bottomOffset;
    [UIView animateWithDuration:duration
                          delay:0.0
                        options:options
                     animations:^{
        [self.view layoutIfNeeded];
    }
                     completion:nil];
}

- (void)viewSafeAreaInsetsDidChange
{
    [super viewSafeAreaInsetsDidChange];
    [topToolbarController relayout];
    [bottomToolbarController relayout];
    [self sendToolbarJavaScript:@"if(window.app&&app.map&&typeof app.map.invalidateSize==='function'){app.map.invalidateSize();}"];
}

- (void)viewWillTransitionToSize:(CGSize)size
       withTransitionCoordinator:(id<UIViewControllerTransitionCoordinator>)coordinator
{
    [super viewWillTransitionToSize:size withTransitionCoordinator:coordinator];
    [coordinator animateAlongsideTransition:^(id<UIViewControllerTransitionCoordinatorContext> context) {
        [topToolbarController relayout];
        [bottomToolbarController relayout];
    } completion:^(id<UIViewControllerTransitionCoordinatorContext> context) {
        [self sendToolbarJavaScript:
            @"window.dispatchEvent(new Event('resize'));"
             "if(window.app&&app.map&&typeof app.map.invalidateSize==='function'){app.map.invalidateSize();}"];
    }];
}

- (void)fontPickerViewControllerDidPickFont:(UIFontPickerViewController *)viewController {
    // Partial fix #5885 Close the font picker when a font is tapped
    // This matches the behavior of Apple apps such as Pages and Mail.
    [viewController dismissViewControllerAnimated:YES completion:nil];

    // NSLog(@"Picked font: %@", [viewController selectedFontDescriptor]);
    NSDictionary<UIFontDescriptorAttributeName, id> *attribs = [[viewController selectedFontDescriptor] fontAttributes];
    NSString *family = attribs[UIFontDescriptorFamilyAttribute];
    if (family && [family length] > 0) {
        NSString *js = [[@"window.MagicFontNameCallback('" stringByAppendingString:family] stringByAppendingString:@"');"];
        [self.webView evaluateJavaScript:js
                       completionHandler:^(id _Nullable obj, NSError * _Nullable error)
             {
                 if (error) {
                     LOG_ERR("Error after " << [js UTF8String] << ": " << [[error localizedDescription] UTF8String]);
                     NSString *jsException = error.userInfo[@"WKJavaScriptExceptionMessage"];
                     if (jsException != nil)
                         LOG_ERR("JavaScript exception: " << [jsException UTF8String]);
                 }
             }
         ];
    }
}

- (void)sendToolbarJavaScript:(NSString *)script
{
    [self.webView evaluateJavaScript:script
                   completionHandler:^(id _Nullable obj, NSError * _Nullable error) {
        if (error) {
            LOG_ERR("Toolbar JavaScript failed: " << [[error localizedDescription] UTF8String]);
        }
    }];
}

- (void)applyNativeEditMode:(BOOL)editMode
{
    nativeEditMode = editMode;
    [topToolbarController setEditMode:editMode];
    [bottomToolbarController setEditMode:editMode];
    [bottomToolbarController setCompact:NO];
}

- (void)applyNativeUndoRedoState:(NSString *)message
{
    BOOL undoEnabled = NO;
    BOOL redoEnabled = NO;
    for (NSString *part in [message componentsSeparatedByString:@" "]) {
        if ([part hasPrefix:@"undo="]) {
            undoEnabled = [[part substringFromIndex:5] boolValue];
        } else if ([part hasPrefix:@"redo="]) {
            redoEnabled = [[part substringFromIndex:5] boolValue];
        }
    }
    topToolbarController.undoEnabled = undoEnabled;
    topToolbarController.redoEnabled = redoEnabled;
}

- (void)saveAfterReadOnlyTransition
{
    if (self.document->fakeClientFd < 0) {
        return;
    }
    const char *saveMessage = "save dontTerminateEdit=1 dontSaveIfUnmodified=0";
    fakeSocketWriteQueue(self.document->fakeClientFd, saveMessage, strlen(saveMessage));
}

- (void)finishNativeEditing
{
    NSString *script = @"(function(){"
                        "if(window.app&&app.map){"
                        "if(typeof app.map.setPermission==='function'){"
                        "app.map.setPermission('readonly');"
                        "}else if(typeof app.map.fire==='function'){"
                        "app.map.fire('readonlymode');"
                        "}"
                        "}"
                        "})();";
    [self sendToolbarJavaScript:script];
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.5 * NSEC_PER_SEC)),
                   dispatch_get_main_queue(), ^{
        [self saveAfterReadOnlyTransition];
    });
}

- (void)showToolbarPlaceholder:(NSString *)message
{
    UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"提示"
                                                                     message:message
                                                              preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"确定"
                                               style:UIAlertActionStyleDefault
                                             handler:nil]];
    [self presentViewController:alert animated:YES completion:nil];
}

- (void)topToolbarDidPressBack
{
    if (nativeEditMode) {
        [self finishNativeEditing];
    } else {
        [self dismissDocumentViewController];
    }
}

- (void)topToolbarDidPressDone
{
    [self finishNativeEditing];
}

- (void)topToolbarDidPressUndo
{
    [self sendToolbarJavaScript:@"if(window.app&&app.socket){app.socket.sendMessage('uno .uno:Undo');}"];
}

- (void)topToolbarDidPressRedo
{
    [self sendToolbarJavaScript:@"if(window.app&&app.socket){app.socket.sendMessage('uno .uno:Redo');}"];
}

- (void)topToolbarDidPressSearch
{
    [self sendToolbarJavaScript:@"if(window.app&&app.socket){app.socket.sendMessage('uno .uno:SearchDialog');}"];
}

- (void)topToolbarDidPressShare
{
    NSURL *fileURL = self.document->copyFileURL;
    if (!fileURL) {
        return;
    }
    UIActivityViewController *activity = [[UIActivityViewController alloc] initWithActivityItems:@[fileURL]
                                                                            applicationActivities:nil];
    UIPopoverPresentationController *popover = activity.popoverPresentationController;
    popover.sourceView = self.view;
    popover.sourceRect = CGRectMake(CGRectGetMidX(self.view.bounds), 56.0, 1.0, 1.0);
    [self presentViewController:activity animated:YES completion:nil];
}

- (void)topToolbarDidPressDocuments
{
    [self showToolbarPlaceholder:@"已打开文档列表将在后续阶段接入。"];
}

- (void)topToolbarDidPressClose
{
    [self dismissDocumentViewController];
}

- (void)topToolbarDidPressComment
{
    [self sendToolbarJavaScript:@"if(window.app&&app.socket){app.socket.sendMessage('uno .uno:InsertAnnotation');}"];
}

- (void)bottomToolbarDidPressMobilePreview
{
    [self showToolbarPlaceholder:@"手机预览将在后续阶段接入。"];
}

- (void)bottomToolbarDidPressFunction
{
    [self sendToolbarJavaScript:@"if(window.__coolWriterEditorPanel){window.__coolWriterEditorPanel.open();}"];
}

- (void)bottomToolbarDidPressAIAssistant
{
    [self sendToolbarJavaScript:@"if(window.__coolWriterAiPanel){window.__coolWriterAiPanel.openAssistant();}"];
}

- (void)bottomToolbarDidPressAIFeatures
{
    [self sendToolbarJavaScript:@"if(window.__coolWriterAiPanel){window.__coolWriterAiPanel.openOperationSheet();}"];
}

- (void)bottomToolbarDidPressKeyboard
{
    [self sendToolbarJavaScript:@"if(window.app&&app.map&&typeof app.map.focus==='function'){app.map.focus(true);}"];
}

- (void)bottomToolbarDidPressCharacter
{
    [self sendToolbarJavaScript:@"if(window.app&&app.socket){app.socket.sendMessage('uno .uno:Bold');}"];
}

- (void)bottomToolbarDidPressParagraph
{
    [self sendToolbarJavaScript:@"if(window.app&&app.socket){app.socket.sendMessage('uno .uno:LeftPara');}"];
}

- (void)bottomToolbarDidPressInsertImage
{
    PHPickerConfiguration *config = [[PHPickerConfiguration alloc] init];
    config.filter = [PHPickerFilter imagesFilter];
    config.selectionLimit = 1;
    PHPickerViewController *picker = [[PHPickerViewController alloc] initWithConfiguration:config];
    picker.delegate = self;
    [self presentViewController:picker animated:YES completion:nil];
}

- (void)picker:(PHPickerViewController *)picker didFinishPicking:(NSArray<PHPickerResult *> *)results
{
    [picker dismissViewControllerAnimated:YES completion:nil];
    PHPickerResult *result = results.firstObject;
    if (!result) {
        return;
    }
    [result.itemProvider loadDataRepresentationForTypeIdentifier:UTTypeImage.identifier
                                              completionHandler:^(NSData *data, NSError *error) {
        if (!data) {
            return;
        }
        NSString *base64 = [data base64EncodedStringWithOptions:0];
        NSString *name = [NSString stringWithFormat:@"image_%ld.png", (long)[[NSDate date] timeIntervalSince1970]];
        dispatch_async(dispatch_get_main_queue(), ^{
            NSString *js = [NSString stringWithFormat:@"if(window.postMobileMessage){window.postMobileMessage('insertfile name=%@ type=graphic data=%@');}", name, base64];
            [self sendToolbarJavaScript:js];
        });
    }];
}

- (void)bye {
    // Close one end of the socket pair, that will wake up the forwarding thread above
    fakeSocketClose(closeNotificationPipeForForwardingThread[0]);

    // DocumentData::deallocate(self.document->appDocId);

    if (![[NSFileManager defaultManager] removeItemAtURL:self.document->copyFileURL error:nil]) {
        LOG_SYS("Could not remove copy of document at " << [[self.document->copyFileURL path] UTF8String]);
    }

    // The dismissViewControllerAnimated must be done on the main queue.
    dispatch_async(dispatch_get_main_queue(),
                   ^{
                       [self dismissDocumentViewController];
                   });
}

- (void)exportFileURL:(NSURL *)fileURL {
    if (!fileURL || ![fileURL isFileURL])
        return;

    // Verify that a file was successfully exported
    BOOL bIsDir;
    if (![[NSFileManager defaultManager] fileExistsAtPath:[fileURL path] isDirectory:&bIsDir] || bIsDir) {
        LOG_ERR("Could apparently not export '" << [[fileURL path] UTF8String] << "'");
        return;
    }

    downloadAsTmpURL = fileURL;

    // Use a UIDocumentPickerViewController to ask the user where to store
    // the exported document and, when the picker is dismissed, have the
    // picker delete the original file.
    UIDocumentPickerViewController *picker =
        [[UIDocumentPickerViewController alloc] initForExportingURLs:[NSArray arrayWithObject:fileURL] asCopy:YES];
    picker.delegate = self;
    [self presentViewController:picker animated:YES completion:nil];
}

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
