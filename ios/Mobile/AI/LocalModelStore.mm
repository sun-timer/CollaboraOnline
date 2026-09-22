// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "LocalModelStore.h"

#import "AiBackendRouter.h"
#import "LocalInferenceEngine.h"

#import <CommonCrypto/CommonDigest.h>
#import <sys/sysctl.h>

#import <os/proc.h>

static NSString *const kEnabledKey = @"AI_MODEL_LOCAL_enabled";
static NSString *const kModelIdKey = @"AI_MODEL_LOCAL_model_id";
static NSString *const kModelPathKey = @"AI_MODEL_LOCAL_model_path";
static NSString *const kSha256Key = @"AI_MODEL_LOCAL_sha256";
static NSString *const kDownloadStateKey = @"AI_MODEL_LOCAL_download_state";
static NSString *const kDownloadingModelIdKey = @"AI_MODEL_LOCAL_downloading_model_id";
static NSString *const kDownloadProgressKey = @"AI_MODEL_LOCAL_download_progress";

static const uint64_t kMinDeviceRamBytes = 4ULL * 1024ULL * 1024ULL * 1024ULL;
static const int64_t kMinStorageHeadroomBytes = 500LL * 1024LL * 1024LL;
static const uint64_t kMinAvailableMemoryForInference = 1200ULL * 1024ULL * 1024ULL;
static const int64_t kMinModelFileBytes = 50LL * 1024LL * 1024LL;

NSString *const LocalModelStateIdle = @"idle";
NSString *const LocalModelStateDownloading = @"downloading";
NSString *const LocalModelStateReady = @"ready";
NSString *const LocalModelStateError = @"error";

@implementation LocalModelCatalogEntry

- (instancetype)initWithId:(NSString *)entryId
               displayName:(NSString *)displayName
                  fileName:(NSString *)fileName
                 sizeBytes:(int64_t)sizeBytes
              downloadURLs:(NSArray<NSString *> *)downloadURLs
            expectedSha256:(NSString *)expectedSha256 {
    self = [super init];
    if (self) {
        _entryId = [entryId copy];
        _displayName = [displayName copy];
        _fileName = [fileName copy];
        _sizeBytes = sizeBytes;
        _downloadURLs = [downloadURLs copy] ?: @[];
        _expectedSha256 = [expectedSha256 copy] ?: @"";
    }
    return self;
}

@end

@interface LocalModelStore () <NSURLSessionDataDelegate>
@property (strong, nonatomic) NSUserDefaults *defaults;
@property (strong, nonatomic) NSOperationQueue *downloadDelegateQueue;
@property (strong, nonatomic) NSURLSession *downloadSession;
@property (strong, nonatomic, nullable) NSURLSessionDataTask *downloadTask;
@property (assign, nonatomic) BOOL cancelDownloadRequested;
@property (strong, nonatomic, nullable) LocalModelCatalogEntry *activeDownloadEntry;
@property (strong, nonatomic, nullable) NSURL *activePartURL;
@property (strong, nonatomic, nullable) NSFileHandle *activePartHandle;
@property (assign, nonatomic) int64_t activeDownloadedBytes;
@property (assign, nonatomic) int64_t activeTotalBytes;
@property (assign, nonatomic) NSUInteger activeUrlIndex;
@property (copy, nonatomic, nullable) LocalModelDownloadProgressBlock activeProgressBlock;
@property (copy, nonatomic, nullable) LocalModelDownloadCompletionBlock activeCompletionBlock;
@end

@implementation LocalModelStore

+ (instancetype)shared {
    static LocalModelStore *store;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        store = [[LocalModelStore alloc] init];
    });
    return store;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _defaults = NSUserDefaults.standardUserDefaults;
        _downloadDelegateQueue = [[NSOperationQueue alloc] init];
        _downloadDelegateQueue.maxConcurrentOperationCount = 1;
        _downloadDelegateQueue.name = @"com.xunlong.xloffice.local-model-download";
        [self reconcileInterruptedDownloadProgress];
    }
    return self;
}

+ (NSArray<LocalModelCatalogEntry *> *)catalogEntries {
    static NSArray<LocalModelCatalogEntry *> *entries;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        entries = @[
            [[LocalModelCatalogEntry alloc] initWithId:@"qwen3-0.6b-q4"
                                           displayName:@"Qwen3-0.6B"
                                              fileName:@"Qwen3-0.6B-Q4_K_M.gguf"
                                             sizeBytes:397000000
                                          downloadURLs:@[
                @"https://hf-mirror.com/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf",
                @"https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf",
            ]
                                          expectedSha256:@""],
            [[LocalModelCatalogEntry alloc] initWithId:@"qwen2.5-1.5b-q4"
                                           displayName:@"Qwen2.5-1.5B-Instruct"
                                              fileName:@"Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
                                             sizeBytes:1100000000
                                          downloadURLs:@[
                @"https://hf-mirror.com/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                @"https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            ]
                                          expectedSha256:@""],
            [[LocalModelCatalogEntry alloc] initWithId:@"qwen3-1.7b-q4"
                                           displayName:@"Qwen3-1.7B"
                                              fileName:@"Qwen3-1.7B-Q4_K_M.gguf"
                                             sizeBytes:1107000000
                                          downloadURLs:@[
                @"https://hf-mirror.com/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
                @"https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
            ]
                                          expectedSha256:@""],
            [[LocalModelCatalogEntry alloc] initWithId:@"gemma-3-1b-q4"
                                           displayName:@"Gemma 3 1B Instruct"
                                              fileName:@"gemma-3-1b-it-Q4_K_M.gguf"
                                             sizeBytes:806000000
                                          downloadURLs:@[
                @"https://hf-mirror.com/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
                @"https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
            ]
                                          expectedSha256:@""],
            [[LocalModelCatalogEntry alloc] initWithId:@"gemma-3-4b-q4"
                                           displayName:@"Gemma 3 4B Instruct"
                                              fileName:@"gemma-3-4b-it-Q4_K_M.gguf"
                                             sizeBytes:2800000000
                                          downloadURLs:@[
                @"https://hf-mirror.com/ggml-org/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
                @"https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
            ]
                                          expectedSha256:@""],
        ];
    });
    return entries;
}

+ (LocalModelCatalogEntry *)defaultCatalogEntry {
    return [self catalogEntries][1];
}

+ (NSString *)primaryDownloadURLForEntry:(LocalModelCatalogEntry *)entry {
    if (entry == nil || entry.downloadURLs.count == 0) {
        return nil;
    }
    return entry.downloadURLs.firstObject;
}

- (NSString *)primaryDownloadURLForInstalledEntry {
    return [LocalModelStore primaryDownloadURLForEntry:[self installedEntry]];
}

+ (NSString *)machineIdentifier {
    char machine[256] = {0};
    size_t size = sizeof(machine);
    sysctlbyname("hw.machine", machine, &size, NULL, 0);
    return [NSString stringWithUTF8String:machine] ?: @"Unknown";
}

+ (NSString *)formatBytesShort:(int64_t)bytes {
    if (bytes <= 0) {
        return @"0 B";
    }
    double value = (double)bytes;
    NSArray<NSString *> *units = @[ @"B", @"KB", @"MB", @"GB", @"TB" ];
    NSInteger unitIndex = 0;
    while (value >= 1024.0 && unitIndex < (NSInteger)units.count - 1) {
        value /= 1024.0;
        unitIndex++;
    }
    if (unitIndex == 0) {
        return [NSString stringWithFormat:@"%lld B", bytes];
    }
    return [NSString stringWithFormat:@"%.1f %@", value, units[unitIndex]];
}

+ (int64_t)freeStorageBytes {
    NSURL *support = [[NSFileManager.defaultManager URLsForDirectory:NSApplicationSupportDirectory
                                                           inDomains:NSUserDomainMask] lastObject];
    if (support == nil) {
        return 0;
    }
    NSDictionary *values = [support resourceValuesForKeys:@[ NSURLVolumeAvailableCapacityForImportantUsageKey ] error:nil];
    NSNumber *free = values[NSURLVolumeAvailableCapacityForImportantUsageKey];
    return free.longLongValue;
}

+ (int64_t)minRamBytesForModel:(LocalModelCatalogEntry *)entry {
    if (entry == nil) {
        return 6LL * 1024LL * 1024LL * 1024LL;
    }
    if ([entry.entryId isEqualToString:@"qwen3-0.6b-q4"]) {
        return 4LL * 1024LL * 1024LL * 1024LL;
    }
    if ([entry.entryId isEqualToString:@"gemma-3-4b-q4"]) {
        return 8LL * 1024LL * 1024LL * 1024LL;
    }
    return 6LL * 1024LL * 1024LL * 1024LL;
}

+ (BOOL)isDeviceSupported {
    return NSProcessInfo.processInfo.physicalMemory >= kMinDeviceRamBytes;
}

+ (BOOL)isMemoryReadyForLocalInference {
#if TARGET_OS_IOS
    if (@available(iOS 15.0, *)) {
        uint64_t available = os_proc_available_memory();
        if (available > 0 && available < kMinAvailableMemoryForInference) {
            return NO;
        }
    }
#endif
    return YES;
}

+ (BOOL)isDeviceLimited {
    if (![self isDeviceSupported]) {
        return NO;
    }
    return NSProcessInfo.processInfo.physicalMemory < 6ULL * 1024ULL * 1024ULL * 1024ULL;
}

+ (BOOL)slowCpuWarning {
    return NSProcessInfo.processInfo.processorCount < 4;
}

+ (NSString *)deviceInfoText {
    uint64_t ramBytes = NSProcessInfo.processInfo.physicalMemory;
    return [NSString stringWithFormat:@"芯片：%@\n架构：arm64\n内存：%@\nCPU 核心：%ld\n可用存储：%@",
            [self machineIdentifier],
            [self formatBytesShort:(int64_t)ramBytes],
            (long)NSProcessInfo.processInfo.processorCount,
            [self formatBytesShort:[self freeStorageBytes]]];
}

+ (NSString *)deviceVerdictText {
    NSMutableString *verdict = [NSMutableString string];
    if (![self isDeviceSupported]) {
        [verdict appendString:@"当前设备不满足最低要求（需 arm64 架构且内存 4GB 以上）"];
    } else if ([self isDeviceLimited]) {
        [verdict appendString:@"支持本地模型（建议使用 ≤1.7B 模型）"];
    } else {
        [verdict appendString:@"支持本地模型"];
    }
    if ([self slowCpuWarning]) {
        [verdict appendString:@"\nCPU 核心较少，本地推理可能较慢"];
    }
    return verdict;
}

+ (BOOL)canDownloadModel:(LocalModelCatalogEntry *)entry {
    if (![self isDeviceSupported] || entry == nil) {
        return NO;
    }
    uint64_t ramBytes = NSProcessInfo.processInfo.physicalMemory;
    if (ramBytes < (uint64_t)[self minRamBytesForModel:entry]) {
        return NO;
    }
    int64_t requiredStorage = (int64_t)(entry.sizeBytes * 1.5f) + kMinStorageHeadroomBytes;
    return [self freeStorageBytes] >= requiredStorage;
}

+ (BOOL)isModelRamMarginal:(LocalModelCatalogEntry *)entry {
    if (entry == nil || ![self isDeviceSupported]) {
        return NO;
    }
    return NSProcessInfo.processInfo.physicalMemory < (uint64_t)[self minRamBytesForModel:entry];
}

+ (NSString *)modelCapabilityMessageForEntry:(LocalModelCatalogEntry *)entry {
    if (entry == nil) {
        return @"当前设备不适合该模型";
    }
    uint64_t ramBytes = NSProcessInfo.processInfo.physicalMemory;
    int64_t minRam = [self minRamBytesForModel:entry];
    if (ramBytes < (uint64_t)minRam) {
        return [NSString stringWithFormat:@"内存不足，建议使用 %@ 以上设备运行 %@",
                [self formatBytesShort:minRam], entry.displayName];
    }
    int64_t requiredStorage = (int64_t)(entry.sizeBytes * 1.5f) + kMinStorageHeadroomBytes;
    if ([self freeStorageBytes] < requiredStorage) {
        return [NSString stringWithFormat:@"存储空间不足，请清理后再下载 %@", entry.displayName];
    }
    return @"当前设备不适合该模型";
}

- (NSString *)downloadState {
    return [self.defaults stringForKey:kDownloadStateKey] ?: LocalModelStateIdle;
}

- (BOOL)isDownloadActive {
    return self.downloadTask != nil && [[self downloadState] isEqualToString:LocalModelStateDownloading];
}

- (AiBackendLocalModelState *)backendRouterState {
    AiBackendLocalModelState *state = [[AiBackendLocalModelState alloc] init];
    state.deviceSupported = [LocalModelStore isDeviceSupported];
    state.installed = [self isInstalled];
    state.enabled = [self isEnabled];
    return state;
}

- (BOOL)isEnabled {
    return [self.defaults boolForKey:kEnabledKey];
}

- (void)setEnabled:(BOOL)enabled {
    [self.defaults setBool:enabled forKey:kEnabledKey];
}

- (NSInteger)downloadProgressPercent {
    return [self.defaults integerForKey:kDownloadProgressKey];
}

- (nullable LocalModelCatalogEntry *)entryForId:(NSString *)entryId {
    if (entryId.length == 0) {
        return nil;
    }
    for (LocalModelCatalogEntry *entry in [LocalModelStore catalogEntries]) {
        if ([entry.entryId isEqualToString:entryId]) {
            return entry;
        }
    }
    return nil;
}

- (nullable LocalModelCatalogEntry *)installedEntry {
    LocalModelCatalogEntry *entry = [self entryForId:[self.defaults stringForKey:kModelIdKey]];
    if (entry == nil) {
        return nil;
    }
    return [self isEntryDownloaded:entry] ? entry : nil;
}

- (nullable NSURL *)installedModelFileURL {
    LocalModelCatalogEntry *entry = [self installedEntry];
    return entry != nil ? [self fileURLForEntry:entry] : nil;
}

- (nullable LocalModelCatalogEntry *)downloadingEntry {
    return [self entryForId:[self.defaults stringForKey:kDownloadingModelIdKey]];
}

- (BOOL)isInstalled {
    return [self installedEntry] != nil;
}

- (BOOL)hasAnyDownloadedModel {
    for (LocalModelCatalogEntry *entry in [LocalModelStore catalogEntries]) {
        if ([self isEntryDownloaded:entry]) {
            return YES;
        }
    }
    return NO;
}

- (NSURL *)modelsDirectory {
    NSURL *support = [[NSFileManager.defaultManager URLsForDirectory:NSApplicationSupportDirectory
                                                           inDomains:NSUserDomainMask] lastObject];
    NSURL *dir = [support URLByAppendingPathComponent:@"local_models" isDirectory:YES];
    [NSFileManager.defaultManager createDirectoryAtURL:dir withIntermediateDirectories:YES attributes:nil error:nil];
    [dir setResourceValue:@YES forKey:NSURLIsExcludedFromBackupKey error:nil];
    return dir;
}

- (NSURL *)fileURLForEntry:(LocalModelCatalogEntry *)entry {
    return [[self modelsDirectory] URLByAppendingPathComponent:entry.fileName];
}

- (BOOL)isEntryDownloaded:(LocalModelCatalogEntry *)entry {
    if (entry == nil) {
        return NO;
    }
    NSURL *url = [self fileURLForEntry:entry];
    if (![NSFileManager.defaultManager fileExistsAtPath:url.path]) {
        return NO;
    }
    NSString *message = nil;
    return [self validateModelFileAtURL:url forEntry:entry message:&message];
}

- (BOOL)isEntryActive:(LocalModelCatalogEntry *)entry {
    LocalModelCatalogEntry *installed = [self installedEntry];
    return installed != nil && [installed.entryId isEqualToString:entry.entryId];
}

- (BOOL)isEntryDownloading:(LocalModelCatalogEntry *)entry {
    return [self isDownloadActive] && [self isEntryDownloadingState:entry];
}

- (BOOL)isEntryDownloadingState:(LocalModelCatalogEntry *)entry {
    LocalModelCatalogEntry *downloading = [self downloadingEntry];
    return downloading != nil && [downloading.entryId isEqualToString:entry.entryId]
        && [[self downloadState] isEqualToString:LocalModelStateDownloading];
}

- (NSString *)drawerStatusText {
    if (![LocalModelStore isDeviceSupported]) {
        return @"不支持";
    }
    if ([self isDownloadActive]) {
        return [NSString stringWithFormat:@"下载中 %ld%%", (long)[self downloadProgressPercent]];
    }
    if ([[self downloadState] isEqualToString:LocalModelStateDownloading]) {
        return @"下载中断";
    }
    LocalModelCatalogEntry *installed = [self installedEntry];
    if (installed != nil) {
        return [self isEnabled] ? @"本地推理已启用" : @"本地推理未启用";
    }
    if ([self hasAnyDownloadedModel]) {
        return @"待选用";
    }
    return @"未安装";
}

- (void)selectActiveModel:(LocalModelCatalogEntry *)entry {
    if (![self isEntryDownloaded:entry]) {
        return;
    }
    [[LocalInferenceEngine shared] unloadModel];
    NSURL *url = [self fileURLForEntry:entry];
    [self.defaults setObject:entry.entryId forKey:kModelIdKey];
    [self.defaults setObject:url.path forKey:kModelPathKey];
    if (entry.expectedSha256.length > 0) {
        [self.defaults setObject:entry.expectedSha256 forKey:kSha256Key];
    } else {
        [self.defaults removeObjectForKey:kSha256Key];
    }
    [self.defaults setObject:LocalModelStateReady forKey:kDownloadStateKey];
}

- (void)deleteModel {
    [self cancelDownload];
    LocalModelCatalogEntry *active = [self installedEntry];
    if (active == nil) {
        NSString *path = [self.defaults stringForKey:kModelPathKey];
        if (path.length > 0) {
            [[NSFileManager defaultManager] removeItemAtPath:path error:nil];
        }
    } else {
        [[NSFileManager defaultManager] removeItemAtURL:[self fileURLForEntry:active] error:nil];
    }
    [self.defaults removeObjectForKey:kModelIdKey];
    [self.defaults removeObjectForKey:kModelPathKey];
    [self.defaults removeObjectForKey:kSha256Key];
    if (![[self downloadState] isEqualToString:LocalModelStateDownloading]) {
        [self.defaults setObject:LocalModelStateIdle forKey:kDownloadStateKey];
    }
    [self setEnabled:NO];
    [[LocalInferenceEngine shared] unloadModel];
}

- (void)cancelDownload {
    self.cancelDownloadRequested = YES;
    if ([self isDownloadActive]) {
        [self.downloadTask cancel];
    } else {
        [self cleanupInterruptedDownload];
    }
}

- (void)downloadModel:(LocalModelCatalogEntry *)entry
             progress:(LocalModelDownloadProgressBlock)progress
           completion:(LocalModelDownloadCompletionBlock)completion {
    if (entry == nil || ![LocalModelStore isDeviceSupported]) {
        if (completion) {
            completion(NO, @"当前设备不支持本地推理（需 arm64 且内存 4GB 以上）。");
        }
        return;
    }
    if (![LocalModelStore canDownloadModel:entry]) {
        if (completion) {
            completion(NO, [LocalModelStore modelCapabilityMessageForEntry:entry]);
        }
        return;
    }
    if ([self isDownloadActive]) {
        if (completion) {
            completion(NO, @"已有下载任务");
        }
        return;
    }
    if (entry.downloadURLs.count == 0) {
        if (completion) {
            completion(NO, @"模型下载地址未配置");
        }
        return;
    }
    if ([self isEntryDownloaded:entry]) {
        if (completion) {
            completion(YES, @"模型已存在。");
        }
        return;
    }

    NSURL *partURL = [self partURLForEntry:entry];
    int64_t existingBytes = [self fileSizeAtURL:partURL];
    [self.defaults setObject:entry.entryId forKey:kDownloadingModelIdKey];
    [self.defaults setObject:LocalModelStateDownloading forKey:kDownloadStateKey];
    if (entry.sizeBytes > 0 && existingBytes > 0) {
        NSInteger percent = (NSInteger)MIN(99, (existingBytes * 100) / entry.sizeBytes);
        [self.defaults setInteger:percent forKey:kDownloadProgressKey];
    } else {
        [self.defaults setInteger:0 forKey:kDownloadProgressKey];
    }
    self.cancelDownloadRequested = NO;

    self.activeDownloadEntry = entry;
    self.activePartURL = partURL;
    self.activeUrlIndex = 0;
    self.activeProgressBlock = [progress copy];
    self.activeCompletionBlock = [completion copy];
    self.activeDownloadedBytes = existingBytes;
    self.activeTotalBytes = entry.sizeBytes > 0 ? entry.sizeBytes : 0;
    [self startDownloadForActiveEntry];
}

- (NSURL *)partURLForEntry:(LocalModelCatalogEntry *)entry {
    return [[self modelsDirectory] URLByAppendingPathComponent:[entry.fileName stringByAppendingString:@".part"]];
}

- (int64_t)fileSizeAtURL:(NSURL *)url {
    if (url == nil) {
        return 0;
    }
    NSDictionary *attrs = [NSFileManager.defaultManager attributesOfItemAtPath:url.path error:nil];
    return [attrs[NSFileSize] longLongValue];
}

- (void)reconcileInterruptedDownloadProgress {
    if (![[self downloadState] isEqualToString:LocalModelStateDownloading] || [self isDownloadActive]) {
        return;
    }
    LocalModelCatalogEntry *entry = [self downloadingEntry];
    if (entry == nil) {
        [self.defaults setObject:LocalModelStateIdle forKey:kDownloadStateKey];
        [self.defaults removeObjectForKey:kDownloadingModelIdKey];
        return;
    }
    int64_t existing = [self fileSizeAtURL:[self partURLForEntry:entry]];
    if (existing <= 0) {
        return;
    }
    if (entry.sizeBytes > 0) {
        NSInteger percent = (NSInteger)MIN(99, (existing * 100) / entry.sizeBytes);
        [self.defaults setInteger:percent forKey:kDownloadProgressKey];
    }
}

- (void)cleanupInterruptedDownload {
    LocalModelCatalogEntry *downloading = [self downloadingEntry];
    if (downloading != nil) {
        [[NSFileManager defaultManager] removeItemAtURL:[self partURLForEntry:downloading] error:nil];
    }
    [self closeActivePartHandle];
    self.downloadTask = nil;
    [self.defaults removeObjectForKey:kDownloadingModelIdKey];
    [self.defaults setObject:LocalModelStateIdle forKey:kDownloadStateKey];
    [self.defaults setInteger:0 forKey:kDownloadProgressKey];
    self.cancelDownloadRequested = NO;
    [self clearActiveDownload];
}

- (void)closeActivePartHandle {
    if (self.activePartHandle != nil) {
        [self.activePartHandle closeFile];
        self.activePartHandle = nil;
    }
}

- (void)notifyDownloadProgress {
    int64_t total = self.activeTotalBytes;
    int64_t downloaded = self.activeDownloadedBytes;
    NSInteger percent = 0;
    if (total > 0) {
        percent = (NSInteger)MIN(100, (downloaded * 100) / total);
    }
    [self.defaults setInteger:percent forKey:kDownloadProgressKey];
    if (self.activeProgressBlock) {
        dispatch_async(dispatch_get_main_queue(), ^{
            self.activeProgressBlock(percent);
        });
    }
}

- (NSURLSession *)downloadSession {
    if (_downloadSession == nil) {
        NSURLSessionConfiguration *config = [NSURLSessionConfiguration defaultSessionConfiguration];
        _downloadSession = [NSURLSession sessionWithConfiguration:config
                                                         delegate:self
                                                    delegateQueue:self.downloadDelegateQueue];
    }
    return _downloadSession;
}

- (void)startDownloadForActiveEntry {
    [self closeActivePartHandle];
    LocalModelCatalogEntry *entry = self.activeDownloadEntry;
    NSURL *partURL = self.activePartURL;
    if (entry == nil || partURL == nil) {
        return;
    }
    if (self.activeUrlIndex >= entry.downloadURLs.count) {
        [self closeActivePartHandle];
        self.downloadTask = nil;
        [self failDownloadWithMessage:@"模型下载失败，请检查网络"];
        return;
    }
    NSURL *url = [NSURL URLWithString:entry.downloadURLs[self.activeUrlIndex]];
    if (url == nil) {
        self.activeUrlIndex += 1;
        [self startDownloadForActiveEntry];
        return;
    }

    int64_t existing = [self fileSizeAtURL:partURL];
    self.activeDownloadedBytes = existing;
    if (self.activeTotalBytes <= 0 && entry.sizeBytes > 0) {
        self.activeTotalBytes = entry.sizeBytes;
    }

    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
    request.HTTPMethod = @"GET";
    request.timeoutInterval = 120.0;
    if (existing > 0) {
        [request setValue:[NSString stringWithFormat:@"bytes=%lld-", existing] forHTTPHeaderField:@"Range"];
    }

    self.downloadTask = [[self downloadSession] dataTaskWithRequest:request];
    [self.downloadTask resume];
}

- (void)failDownloadWithMessage:(NSString *)message {
    [self.defaults setObject:LocalModelStateError forKey:kDownloadStateKey];
    [self.defaults removeObjectForKey:kDownloadingModelIdKey];
    [self closeActivePartHandle];
    self.downloadTask = nil;
    LocalModelDownloadCompletionBlock completion = self.activeCompletionBlock;
    [self clearActiveDownload];
    if (completion) {
        dispatch_async(dispatch_get_main_queue(), ^{
            completion(NO, message);
        });
    }
}

- (void)clearActiveDownload {
    self.activeDownloadEntry = nil;
    self.activePartURL = nil;
    self.activeUrlIndex = 0;
    self.activeDownloadedBytes = 0;
    self.activeTotalBytes = 0;
    self.activeProgressBlock = nil;
    self.activeCompletionBlock = nil;
}

- (void)handleDownloadHttpFailureTryNextUrl {
    [self closeActivePartHandle];
    self.downloadTask = nil;
    if (self.activePartURL != nil) {
        [[NSFileManager defaultManager] removeItemAtURL:self.activePartURL error:nil];
    }
    self.activeDownloadedBytes = 0;
    self.activeUrlIndex += 1;
    if (self.cancelDownloadRequested) {
        [self cleanupInterruptedDownload];
        return;
    }
    [self startDownloadForActiveEntry];
}

- (void)finishSuccessfulDownload {
    LocalModelCatalogEntry *entry = self.activeDownloadEntry;
    NSURL *partURL = self.activePartURL;
    if (entry == nil || partURL == nil) {
        [self failDownloadWithMessage:@"下载失败"];
        return;
    }
    NSString *validateMessage = nil;
    if (![self validateModelFileAtURL:partURL forEntry:entry message:&validateMessage]) {
        [[NSFileManager defaultManager] removeItemAtURL:partURL error:nil];
        [self failDownloadWithMessage:validateMessage ?: @"模型校验失败"];
        return;
    }
    NSURL *dest = [self fileURLForEntry:entry];
    NSFileManager *fm = NSFileManager.defaultManager;
    [fm removeItemAtURL:dest error:nil];
    NSError *moveError = nil;
    [fm moveItemAtURL:partURL toURL:dest error:&moveError];
    if (moveError != nil) {
        [self failDownloadWithMessage:@"无法完成模型安装"];
        return;
    }
    [self selectActiveModel:entry];
    [self.defaults removeObjectForKey:kDownloadingModelIdKey];
    [self.defaults setInteger:100 forKey:kDownloadProgressKey];
    LocalModelDownloadProgressBlock progress = self.activeProgressBlock;
    LocalModelDownloadCompletionBlock completion = self.activeCompletionBlock;
    [self closeActivePartHandle];
    self.downloadTask = nil;
    [self clearActiveDownload];
    if (progress) {
        dispatch_async(dispatch_get_main_queue(), ^{
            progress(100);
        });
    }
    if (completion) {
        dispatch_async(dispatch_get_main_queue(), ^{
            completion(YES, @"模型下载完成。");
        });
    }
}

- (BOOL)openPartFileAppend:(BOOL)append {
    NSURL *partURL = self.activePartURL;
    if (partURL == nil) {
        return NO;
    }
    NSFileManager *fm = NSFileManager.defaultManager;
    if (!append) {
        [fm createFileAtPath:partURL.path contents:nil attributes:nil];
    } else if (![fm fileExistsAtPath:partURL.path]) {
        [fm createFileAtPath:partURL.path contents:nil attributes:nil];
    }
    self.activePartHandle = [NSFileHandle fileHandleForWritingAtPath:partURL.path];
    if (self.activePartHandle == nil) {
        return NO;
    }
    if (append) {
        [self.activePartHandle seekToEndOfFile];
    }
    return YES;
}

- (void)parseTotalBytesFromContentRange:(NSString *)contentRange fallbackExisting:(int64_t)existing {
    if (contentRange.length == 0) {
        return;
    }
    NSRange slash = [contentRange rangeOfString:@"/" options:NSBackwardsSearch];
    if (slash.location == NSNotFound || slash.location + 1 >= contentRange.length) {
        return;
    }
    NSString *totalText = [contentRange substringFromIndex:slash.location + 1];
    self.activeTotalBytes = totalText.longLongValue;
    if (self.activeTotalBytes <= 0 && self.activeDownloadEntry.sizeBytes > 0) {
        self.activeTotalBytes = self.activeDownloadEntry.sizeBytes;
    }
    (void)existing;
}

- (NSString *)sha256HexForFileURL:(NSURL *)url {
    NSInputStream *stream = [NSInputStream inputStreamWithURL:url];
    if (stream == nil) {
        return @"";
    }
    [stream open];
    CC_SHA256_CTX context;
    CC_SHA256_Init(&context);
    uint8_t buffer[256 * 1024];
    while (true) {
        NSInteger read = [stream read:buffer maxLength:sizeof(buffer)];
        if (read <= 0) {
            break;
        }
        CC_SHA256_Update(&context, buffer, (CC_LONG)read);
    }
    [stream close];
    unsigned char digest[CC_SHA256_DIGEST_LENGTH];
    CC_SHA256_Final(digest, &context);
    NSMutableString *hex = [NSMutableString stringWithCapacity:CC_SHA256_DIGEST_LENGTH * 2];
    for (int i = 0; i < CC_SHA256_DIGEST_LENGTH; i++) {
        [hex appendFormat:@"%02x", digest[i]];
    }
    return hex;
}

- (BOOL)validateModelFileAtURL:(NSURL *)url
                       forEntry:(LocalModelCatalogEntry *)entry
                        message:(NSString **)message {
    if (url == nil || entry == nil) {
        if (message) {
            *message = @"invalid_model";
        }
        return NO;
    }
    NSDictionary *attrs = [NSFileManager.defaultManager attributesOfItemAtPath:url.path error:nil];
    NSNumber *fileSize = attrs[NSFileSize];
    int64_t bytes = fileSize.longLongValue;
    if (bytes < kMinModelFileBytes) {
        if (message) {
            *message = @"模型文件过小或已损坏";
        }
        return NO;
    }
    if (entry.sizeBytes > 0 && bytes < (entry.sizeBytes * 85 / 100)) {
        if (message) {
            *message = @"模型文件大小异常，请重新下载";
        }
        return NO;
    }
    if (entry.expectedSha256.length > 0) {
        NSString *actual = [self sha256HexForFileURL:url];
        if (![entry.expectedSha256.lowercaseString isEqualToString:actual.lowercaseString]) {
            if (message) {
                *message = @"模型校验失败（SHA-256 不匹配）";
            }
            return NO;
        }
    }
    return YES;
}

- (void)URLSession:(NSURLSession *)session
          dataTask:(NSURLSessionDataTask *)dataTask
didReceiveResponse:(NSURLResponse *)response
 completionHandler:(void (^)(NSURLSessionResponseDisposition))completionHandler {
    if (dataTask != self.downloadTask) {
        completionHandler(NSURLSessionResponseCancel);
        return;
    }
    if (self.cancelDownloadRequested) {
        completionHandler(NSURLSessionResponseCancel);
        return;
    }

    NSHTTPURLResponse *http = [response isKindOfClass:[NSHTTPURLResponse class]] ? (NSHTTPURLResponse *)response : nil;
    NSInteger code = http != nil ? http.statusCode : 0;
    int64_t existing = self.activeDownloadedBytes;

    if (existing > 0) {
        if (code == 206) {
            NSString *contentRange = http.allHeaderFields[@"Content-Range"];
            if ([contentRange isKindOfClass:[NSString class]]) {
                [self parseTotalBytesFromContentRange:contentRange fallbackExisting:existing];
            }
            if (![self openPartFileAppend:YES]) {
                completionHandler(NSURLSessionResponseCancel);
                [self handleDownloadHttpFailureTryNextUrl];
                return;
            }
        } else if (code == 200) {
            existing = 0;
            self.activeDownloadedBytes = 0;
            [[NSFileManager defaultManager] removeItemAtURL:self.activePartURL error:nil];
            if (![self openPartFileAppend:NO]) {
                completionHandler(NSURLSessionResponseCancel);
                [self handleDownloadHttpFailureTryNextUrl];
                return;
            }
            long long contentLength = response.expectedContentLength;
            if (contentLength > 0) {
                self.activeTotalBytes = contentLength;
            }
        } else {
            completionHandler(NSURLSessionResponseCancel);
            [self handleDownloadHttpFailureTryNextUrl];
            return;
        }
    } else {
        if (code != 200) {
            completionHandler(NSURLSessionResponseCancel);
            [self handleDownloadHttpFailureTryNextUrl];
            return;
        }
        if (![self openPartFileAppend:NO]) {
            completionHandler(NSURLSessionResponseCancel);
            [self handleDownloadHttpFailureTryNextUrl];
            return;
        }
        long long contentLength = response.expectedContentLength;
        if (contentLength > 0) {
            self.activeTotalBytes = contentLength;
        } else if (self.activeDownloadEntry.sizeBytes > 0) {
            self.activeTotalBytes = self.activeDownloadEntry.sizeBytes;
        }
    }

    [self notifyDownloadProgress];
    completionHandler(NSURLSessionResponseAllow);
}

- (void)URLSession:(NSURLSession *)session
          dataTask:(NSURLSessionDataTask *)dataTask
    didReceiveData:(NSData *)data {
    if (dataTask != self.downloadTask || data.length == 0 || self.cancelDownloadRequested) {
        return;
    }
    if (self.activePartHandle == nil) {
        return;
    }
    @try {
        [self.activePartHandle writeData:data];
    } @catch (NSException *exception) {
        [self handleDownloadHttpFailureTryNextUrl];
        return;
    }
    self.activeDownloadedBytes += (int64_t)data.length;
    [self notifyDownloadProgress];
}

- (void)URLSession:(NSURLSession *)session
              task:(NSURLSessionTask *)task
didCompleteWithError:(NSError *)error {
    if (task != self.downloadTask) {
        return;
    }
    if (self.cancelDownloadRequested) {
        LocalModelDownloadCompletionBlock completion = self.activeCompletionBlock;
        [self cleanupInterruptedDownload];
        if (completion) {
            dispatch_async(dispatch_get_main_queue(), ^{
                completion(NO, @"下载已取消");
            });
        }
        return;
    }
    if (error != nil) {
        [self handleDownloadHttpFailureTryNextUrl];
        return;
    }
    [self closeActivePartHandle];
    [self finishSuccessfulDownload];
}

@end
