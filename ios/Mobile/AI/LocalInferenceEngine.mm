// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "LocalInferenceEngine.h"

#import "LocalPromptBuilder.h"
#import "LlamaInferenceCore.h"

#import <atomic>
#import <vector>
#import <string>

@implementation LocalInferenceParams

+ (instancetype)defaults {
    LocalInferenceParams *p = [[LocalInferenceParams alloc] init];
    p.contextSize = 4096;
    p.maxTokens = 512;
    p.threads = 4;
    return p;
}

+ (instancetype)fromDevice {
    LocalInferenceParams *p = [LocalInferenceParams defaults];
    uint64_t ram = NSProcessInfo.processInfo.physicalMemory;
    BOOL highRam = ram >= 8ULL * 1024ULL * 1024ULL * 1024ULL;
    p.contextSize = highRam ? 4096 : 2048;
    p.maxTokens = highRam ? 512 : 256;
    return p;
}

@end

@interface LocalInferenceEngine ()
@property (strong, nonatomic) dispatch_queue_t queue;
@property (copy, nonatomic, nullable) NSString *loadedModelPath;
@property (strong, nonatomic, nullable) LocalInferenceParams *loadedParams;
@property (copy, nonatomic, nullable) NSString *executingRequestId;
@property (atomic, assign) BOOL cancelRequested;
@end

@implementation LocalInferenceEngine

+ (instancetype)shared {
    static LocalInferenceEngine *engine;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        engine = [[LocalInferenceEngine alloc] init];
    });
    return engine;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _queue = dispatch_queue_create("com.xunlong.xloffice.local-inference", DISPATCH_QUEUE_SERIAL);
    }
    return self;
}

+ (BOOL)isNativeAvailable {
    return xl_llama_is_available();
}

- (BOOL)isModelLoaded {
    return self.loadedModelPath.length > 0;
}

- (void)loadModelAtPath:(NSString *)path
                 params:(LocalInferenceParams *)params
             completion:(LocalInferenceLoadCompletion)completion {
    LocalInferenceParams *useParams = params ?: [LocalInferenceParams fromDevice];
    dispatch_async(self.queue, ^{
        if (![LocalInferenceEngine isNativeAvailable]) {
            if (completion) {
                completion(NO, @"local_jni_missing");
            }
            return;
        }
        BOOL ok = xl_llama_load_model(path.UTF8String, (int)useParams.contextSize, (int)useParams.threads);
        if (ok) {
            self.loadedModelPath = path;
            self.loadedParams = useParams;
            if (completion) {
                completion(YES, @"");
            }
        } else if (completion) {
            completion(NO, @"local_load_fail");
        }
    });
}

- (void)unloadModel {
    dispatch_async(self.queue, ^{
        xl_llama_unload_model();
        self.loadedModelPath = nil;
        self.loadedParams = nil;
    });
}

- (void)cancelRequestId:(NSString *)requestId {
    if (requestId.length == 0) {
        return;
    }
    dispatch_async(self.queue, ^{
        if ([self.executingRequestId isEqualToString:requestId]) {
            self.cancelRequested = YES;
        }
    });
}

- (void)generateWithRequestId:(NSString *)requestId
                     messages:(NSArray<NSDictionary *> *)messages
                    multiTurn:(BOOL)multiTurn
                       params:(LocalInferenceParams *)params
                    onToken:(LocalInferenceTokenBlock)onToken
                 onComplete:(LocalInferenceCompleteBlock)onComplete
                    onError:(LocalInferenceErrorBlock)onError {
    LocalInferenceParams *useParams = params ?: self.loadedParams ?: [LocalInferenceParams fromDevice];
    dispatch_async(self.queue, ^{
        self.executingRequestId = requestId;
        self.cancelRequested = NO;

        if (![LocalInferenceEngine isNativeAvailable]) {
            if (onError) {
                onError(@"local_jni_missing", @"本地推理库未链接");
            }
            self.executingRequestId = nil;
            return;
        }
        if (![self isModelLoaded]) {
            if (onError) {
                onError(@"local_not_loaded", @"本地模型未加载");
            }
            self.executingRequestId = nil;
            return;
        }

        NSArray<NSDictionary *> *promptMessages =
            [LocalPromptBuilder buildPromptFromHistory:messages
                                           contextSize:useParams.contextSize
                                         maxGenTokens:useParams.maxTokens
                                            multiTurn:multiTurn];

        NSMutableArray<NSString *> *roles = [NSMutableArray array];
        NSMutableArray<NSString *> *contents = [NSMutableArray array];
        for (NSDictionary *item in promptMessages) {
            [roles addObject:[item[@"role"] isKindOfClass:[NSString class]] ? item[@"role"] : @"user"];
            [contents addObject:[item[@"content"] isKindOfClass:[NSString class]] ? item[@"content"] : @""];
        }

        std::vector<std::string> roleStorage;
        std::vector<std::string> contentStorage;
        std::vector<const char *> rolePtrs;
        std::vector<const char *> contentPtrs;
        roleStorage.reserve(roles.count);
        contentStorage.reserve(contents.count);
        for (NSUInteger i = 0; i < roles.count; i++) {
            roleStorage.push_back(std::string(roles[i].UTF8String));
            contentStorage.push_back(std::string(contents[i].UTF8String));
        }
        for (size_t i = 0; i < roleStorage.size(); i++) {
            rolePtrs.push_back(roleStorage[i].c_str());
            contentPtrs.push_back(contentStorage[i].c_str());
        }

        NSTimeInterval start = [NSDate date].timeIntervalSince1970;
        if (!xl_llama_prefill_messages(rolePtrs.data(), contentPtrs.data(), (int)rolePtrs.size())) {
            if (onError) {
                onError(@"local_prefill_fail", @"本地推理 prefill 失败");
            }
            self.executingRequestId = nil;
            return;
        }

        NSMutableString *full = [NSMutableString string];
        NSTimeInterval firstTokenMs = 0;
        NSInteger tokenCount = 0;
        BOOL streamingSent = NO;

        for (NSInteger i = 0; i < useParams.maxTokens; i++) {
            if (self.cancelRequested) {
                break;
            }
            char piece[512];
            int sampleResult = xl_llama_sample_token(piece, sizeof(piece));
            if (sampleResult == 0) {
                break;
            }
            if (sampleResult == 2) {
                continue;
            }
            NSString *token = [NSString stringWithUTF8String:piece];
            if (token.length == 0) {
                continue;
            }
            if (tokenCount == 0) {
                firstTokenMs = ([NSDate date].timeIntervalSince1970 - start) * 1000.0;
            }
            tokenCount++;
            [full appendString:token];
            if (onToken) {
                if (!streamingSent) {
                    streamingSent = YES;
                }
                onToken(token);
            }
        }

        NSTimeInterval elapsed = MAX(0.001, [NSDate date].timeIntervalSince1970 - start);
        float tps = (float)(tokenCount / elapsed);
        if (onComplete) {
            onComplete(full, firstTokenMs, tps);
        }
        self.executingRequestId = nil;
    });
}

@end
