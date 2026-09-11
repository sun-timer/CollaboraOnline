// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "LocalModelStore.h"

#import <sys/sysctl.h>

static NSString *const kEnabledKey = @"AI_MODEL_LOCAL_enabled";
static NSString *const kModelIdKey = @"AI_MODEL_LOCAL_model_id";
static NSString *const kModelPathKey = @"AI_MODEL_LOCAL_model_path";
static NSString *const kDownloadStateKey = @"AI_MODEL_LOCAL_download_state";
static NSString *const kDownloadingModelIdKey = @"AI_MODEL_LOCAL_downloading_model_id";
static NSString *const kDownloadProgressKey = @"AI_MODEL_LOCAL_download_progress";

static const uint64_t kMinDeviceRamBytes = 4ULL * 1024ULL * 1024ULL * 1024ULL;
static const int64_t kMinStorageHeadroomBytes = 500LL * 1024LL * 1024LL;

NSString *const LocalModelStateIdle = @"idle";
NSString *const LocalModelStateDownloading = @"downloading";
NSString *const LocalModelStateReady = @"ready";
NSString *const LocalModelStateError = @"error";

@implementation LocalModelCatalogEntry

- (instancetype)initWithId:(NSString *)entryId
               displayName:(NSString *)displayName
                  fileName:(NSString *)fileName
                 sizeBytes:(int64_t)sizeBytes {
    self = [super init];
    if (self) {
        _entryId = [entryId copy];
        _displayName = [displayName copy];
        _fileName = [fileName copy];
        _sizeBytes = sizeBytes;
    }
    return self;
}

@end

@interface LocalModelStore ()
@property (strong, nonatomic) NSUserDefaults *defaults;
@property (strong, nonatomic, nullable) dispatch_source_t downloadTimer;
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
                                             sizeBytes:397000000],
            [[LocalModelCatalogEntry alloc] initWithId:@"qwen2.5-1.5b-q4"
                                           displayName:@"Qwen2.5-1.5B-Instruct"
                                              fileName:@"Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
                                             sizeBytes:1100000000],
            [[LocalModelCatalogEntry alloc] initWithId:@"qwen3-1.7b-q4"
                                           displayName:@"Qwen3-1.7B"
                                              fileName:@"Qwen3-1.7B-Q4_K_M.gguf"
                                             sizeBytes:1107000000],
            [[LocalModelCatalogEntry alloc] initWithId:@"gemma-3-1b-q4"
                                           displayName:@"Gemma 3 1B Instruct"
                                              fileName:@"gemma-3-1b-it-Q4_K_M.gguf"
                                             sizeBytes:900000000],
            [[LocalModelCatalogEntry alloc] initWithId:@"gemma-3-4b-q4"
                                           displayName:@"Gemma 3 4B Instruct"
                                              fileName:@"gemma-3-4b-it-Q4_K_M.gguf"
                                             sizeBytes:2800000000],
        ];
    });
    return entries;
}

+ (LocalModelCatalogEntry *)defaultCatalogEntry {
    return [self catalogEntries][1];
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
    return self.downloadTimer != nil && [[self downloadState] isEqualToString:LocalModelStateDownloading];
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
    return [NSFileManager.defaultManager fileExistsAtPath:url.path];
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
    LocalModelCatalogEntry *installed = [self installedEntry];
    if (installed != nil) {
        return [self isEnabled] ? installed.displayName : @"已下载";
    }
    return @"未安装";
}

- (void)selectActiveModel:(LocalModelCatalogEntry *)entry {
    if (![self isEntryDownloaded:entry]) {
        return;
    }
    NSURL *url = [self fileURLForEntry:entry];
    [self.defaults setObject:entry.entryId forKey:kModelIdKey];
    [self.defaults setObject:url.path forKey:kModelPathKey];
    [self.defaults setObject:LocalModelStateReady forKey:kDownloadStateKey];
}

- (void)deleteModel {
    [self cancelDownload];
    for (LocalModelCatalogEntry *entry in [LocalModelStore catalogEntries]) {
        [[NSFileManager defaultManager] removeItemAtURL:[self fileURLForEntry:entry] error:nil];
    }
    [self.defaults removeObjectForKey:kModelIdKey];
    [self.defaults removeObjectForKey:kModelPathKey];
    [self.defaults setObject:LocalModelStateIdle forKey:kDownloadStateKey];
    [self setEnabled:NO];
}

- (void)cancelDownload {
    if (self.downloadTimer) {
        dispatch_source_cancel(self.downloadTimer);
        self.downloadTimer = nil;
    }
    LocalModelCatalogEntry *downloading = [self downloadingEntry];
    if (downloading != nil) {
        NSURL *part = [[self modelsDirectory] URLByAppendingPathComponent:[downloading.fileName stringByAppendingString:@".part"]];
        [[NSFileManager defaultManager] removeItemAtURL:part error:nil];
    }
    [self.defaults removeObjectForKey:kDownloadingModelIdKey];
    [self.defaults setObject:LocalModelStateIdle forKey:kDownloadStateKey];
    [self.defaults setInteger:0 forKey:kDownloadProgressKey];
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
    [self.defaults setObject:entry.entryId forKey:kDownloadingModelIdKey];
    [self.defaults setObject:LocalModelStateDownloading forKey:kDownloadStateKey];
    [self.defaults setInteger:0 forKey:kDownloadProgressKey];

    __block NSInteger percent = 0;
    dispatch_queue_t queue = dispatch_get_main_queue();
    self.downloadTimer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0, 0, queue);
    dispatch_source_set_timer(self.downloadTimer, dispatch_time(DISPATCH_TIME_NOW, 0), (uint64_t)(0.25 * NSEC_PER_SEC), (uint64_t)(0.05 * NSEC_PER_SEC));
    __weak __typeof(self) weakSelf = self;
    dispatch_source_set_event_handler(self.downloadTimer, ^{
        percent += 4;
        if (percent > 100) {
            percent = 100;
        }
        [weakSelf.defaults setInteger:percent forKey:kDownloadProgressKey];
        if (progress) {
            progress(percent);
        }
        if (percent >= 100) {
            if (weakSelf.downloadTimer) {
                dispatch_source_cancel(weakSelf.downloadTimer);
                weakSelf.downloadTimer = nil;
            }
            NSURL *dest = [weakSelf fileURLForEntry:entry];
            [@"" writeToURL:dest atomically:YES encoding:NSUTF8StringEncoding error:nil];
            [weakSelf selectActiveModel:entry];
            [weakSelf.defaults removeObjectForKey:kDownloadingModelIdKey];
            if (completion) {
                completion(YES, @"模型下载完成。");
            }
        }
    });
    dispatch_resume(self.downloadTimer);
}

@end
