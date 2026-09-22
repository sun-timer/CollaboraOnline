// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AiBackendRouter.h"

#import "LocalInferenceEngine.h"
#import "LocalModelStore.h"

@implementation AiBackendResolvedRoute
@end

@implementation AiBackendLocalModelState

- (BOOL)isReady {
    return self.deviceSupported && self.installed && self.enabled && [LocalInferenceEngine isNativeAvailable];
}

@end

@implementation AiBackendRouter

+ (BOOL)localDocQaEnabled {
    return YES;
}

+ (NSInteger)localDocQaMaxChars {
    return 5000;
}

+ (BOOL)isCloudOnlyTask:(NSString *)taskType {
    return [taskType isEqualToString:@"image_generate"] || [taskType isEqualToString:@"text_extract"];
}

+ (BOOL)isLocalTextTask:(NSString *)taskType {
    if (taskType.length == 0) {
        return NO;
    }
    if ([self isCloudOnlyTask:taskType]) {
        return NO;
    }
    if ([taskType isEqualToString:@"format_batch"]) {
        return NO;
    }
    if ([taskType isEqualToString:@"doc_qa"]) {
        return [self localDocQaEnabled];
    }
    return YES;
}

+ (BOOL)isMultiTurnTask:(NSString *)taskType {
    return [taskType isEqualToString:@"chat"] || [taskType isEqualToString:@"doc_qa"];
}

- (AiBackendResolvedRoute *)resolveTaskType:(NSString *)taskType
                                  modelMode:(NSString *)modelMode
                                docCharCount:(NSInteger)docCharCount
                                  localState:(AiBackendLocalModelState *)state {
    AiBackendResolvedRoute *route = [[AiBackendResolvedRoute alloc] init];
    route.modelMode = modelMode.length > 0 ? modelMode : @"base";
    route.reason = @"";

    if ([AiBackendRouter isCloudOnlyTask:taskType]) {
        route.backend = AiBackendKindCloud;
        route.reason = @"cloud_only_task";
        return route;
    }

    if ([taskType isEqualToString:@"doc_qa"]) {
        if (![AiBackendRouter localDocQaEnabled]) {
            route.backend = AiBackendKindCloud;
            route.modelMode = @"base";
            route.reason = @"doc_qa_default_cloud";
            return route;
        }
        if (docCharCount > [AiBackendRouter localDocQaMaxChars]) {
            route.backend = AiBackendKindCloud;
            route.modelMode = @"base";
            route.reason = @"doc_qa_too_long_for_local";
            return route;
        }
    }

    if (state != nil && [state isReady] && [AiBackendRouter isLocalTextTask:taskType]) {
        if (![LocalModelStore isMemoryReadyForLocalInference]) {
            route.backend = AiBackendKindCloud;
            route.modelMode = route.modelMode;
            route.reason = @"local_low_memory";
            return route;
        }
        route.backend = AiBackendKindLocal;
        route.modelMode = @"local";
        route.reason = @"local_ready";
        return route;
    }

    route.backend = AiBackendKindCloud;
    route.reason = @"cloud_fallback";
    return route;
}

@end
