// -*- Mode: ObjC; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

@class AiBackendLocalModelState;

NS_ASSUME_NONNULL_BEGIN

extern NSString *const LocalModelStateIdle;
extern NSString *const LocalModelStateDownloading;
extern NSString *const LocalModelStateReady;
extern NSString *const LocalModelStateError;

@interface LocalModelCatalogEntry : NSObject
@property (copy, nonatomic, readonly) NSString *entryId;
@property (copy, nonatomic, readonly) NSString *displayName;
@property (copy, nonatomic, readonly) NSString *fileName;
@property (assign, nonatomic, readonly) int64_t sizeBytes;
@property (copy, nonatomic, readonly) NSArray<NSString *> *downloadURLs;
/** Lowercase hex SHA-256; empty string skips verification (same as Android catalog). */
@property (copy, nonatomic, readonly) NSString *expectedSha256;
- (instancetype)initWithId:(NSString *)entryId
               displayName:(NSString *)displayName
                  fileName:(NSString *)fileName
                 sizeBytes:(int64_t)sizeBytes
              downloadURLs:(NSArray<NSString *> *)downloadURLs
            expectedSha256:(NSString *)expectedSha256;
@end

typedef void (^LocalModelDownloadProgressBlock)(NSInteger percent);
typedef void (^LocalModelDownloadCompletionBlock)(BOOL success, NSString * _Nullable message);

@interface LocalModelStore : NSObject

+ (instancetype)shared;

+ (NSArray<LocalModelCatalogEntry *> *)catalogEntries;
+ (LocalModelCatalogEntry *)defaultCatalogEntry;
+ (nullable NSString *)primaryDownloadURLForEntry:(LocalModelCatalogEntry *)entry;

- (AiBackendLocalModelState *)backendRouterState;
- (nullable NSString *)primaryDownloadURLForInstalledEntry;

+ (BOOL)isDeviceSupported;
+ (BOOL)isDeviceLimited;
+ (BOOL)slowCpuWarning;
+ (NSString *)deviceInfoText;
+ (NSString *)deviceVerdictText;
+ (BOOL)isMemoryReadyForLocalInference;
+ (BOOL)canDownloadModel:(LocalModelCatalogEntry *)entry;
+ (BOOL)isModelRamMarginal:(LocalModelCatalogEntry *)entry;
+ (NSString *)modelCapabilityMessageForEntry:(LocalModelCatalogEntry *)entry;

- (NSString *)downloadState;
- (BOOL)isDownloadActive;
- (BOOL)isInstalled;
- (BOOL)hasAnyDownloadedModel;
- (BOOL)isEnabled;
- (void)setEnabled:(BOOL)enabled;
- (NSInteger)downloadProgressPercent;
- (nullable LocalModelCatalogEntry *)installedEntry;
- (nullable NSURL *)installedModelFileURL;
- (nullable LocalModelCatalogEntry *)downloadingEntry;
- (BOOL)isEntryDownloaded:(LocalModelCatalogEntry *)entry;
- (BOOL)isEntryActive:(LocalModelCatalogEntry *)entry;
- (BOOL)isEntryDownloading:(LocalModelCatalogEntry *)entry;
- (NSString *)drawerStatusText;

- (void)selectActiveModel:(LocalModelCatalogEntry *)entry;
- (void)deleteModel;
- (void)cancelDownload;
- (void)downloadModel:(LocalModelCatalogEntry *)entry
             progress:(LocalModelDownloadProgressBlock)progress
           completion:(LocalModelDownloadCompletionBlock)completion;

@end

NS_ASSUME_NONNULL_END
