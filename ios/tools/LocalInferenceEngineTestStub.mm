// Stub for ai-logic-test.sh (no llama.xcframework link).
#import "LocalInferenceEngine.h"

@implementation LocalInferenceParams
+ (instancetype)defaults {
    return [[LocalInferenceParams alloc] init];
}
+ (instancetype)fromDevice {
    return [self defaults];
}
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
+ (BOOL)isNativeAvailable {
    return YES;
}
- (BOOL)isModelLoaded {
    return NO;
}
- (void)loadModelAtPath:(NSString *)path
                 params:(LocalInferenceParams *)params
             completion:(LocalInferenceLoadCompletion)completion {
    if (completion) {
        completion(NO, @"stub");
    }
}
- (void)unloadModel {
}
- (void)cancelRequestId:(NSString *)requestId {
    (void)requestId;
}
- (void)generateWithRequestId:(NSString *)requestId
                     messages:(NSArray<NSDictionary *> *)messages
                    multiTurn:(BOOL)multiTurn
                       params:(LocalInferenceParams *)params
                      onToken:(LocalInferenceTokenBlock)onToken
                   onComplete:(LocalInferenceCompleteBlock)onComplete
                      onError:(LocalInferenceErrorBlock)onError {
    (void)requestId;
    (void)messages;
    (void)multiTurn;
    (void)params;
    (void)onToken;
    (void)onComplete;
    if (onError) {
        onError(@"stub", @"stub");
    }
}
@end
