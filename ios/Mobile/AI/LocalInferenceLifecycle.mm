// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "LocalInferenceLifecycle.h"

#import "LocalInferenceEngine.h"

#import <UIKit/UIKit.h>

@implementation LocalInferenceLifecycle

+ (void)install {
    NSNotificationCenter *center = NSNotificationCenter.defaultCenter;
    [center addObserver:self
               selector:@selector(handleMemoryWarning)
                   name:UIApplicationDidReceiveMemoryWarningNotification
                 object:nil];
    [center addObserver:self
               selector:@selector(handleDidEnterBackground)
                   name:UIApplicationDidEnterBackgroundNotification
                 object:nil];
}

+ (void)handleMemoryWarning {
    [self unloadEngineForMemoryPressure];
}

+ (void)handleDidEnterBackground {
    [self unloadEngineForMemoryPressure];
}

+ (void)unloadEngineForMemoryPressure {
    [[LocalInferenceEngine shared] unloadModel];
}

@end
