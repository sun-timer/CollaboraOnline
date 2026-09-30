// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "FeedbackLogExporter.h"

#import "FeedbackApiException.h"
#import "FeedbackClient.h"
#import "FeedbackConfig.h"

#import <unistd.h>

#if __has_include(<OSLog/OSLog.h>)
#import <OSLog/OSLog.h>
#define FEEDBACK_HAS_OSLOG 1
#else
#define FEEDBACK_HAS_OSLOG 0
#endif

static NSString *feedbackLogLevelLabel(OSLogEntryLog *log) {
    switch (log.level) {
        case OSLogEntryLogLevelFault:
            return @"F";
        case OSLogEntryLogLevelError:
            return @"E";
        case OSLogEntryLogLevelDebug:
            return @"D";
        case OSLogEntryLogLevelInfo:
            return @"I";
        case OSLogEntryLogLevelNotice:
            return @"N";
        default:
            return @"-";
    }
}

@implementation FeedbackLogExporter

+ (BOOL)appendUnifiedLogToText:(NSMutableString *)text byteCount:(NSUInteger *)byteCount error:(NSError **)error {
#if FEEDBACK_HAS_OSLOG
    if (@available(iOS 15.0, *)) {
        NSError *storeError = nil;
        OSLogStore *store = [OSLogStore storeWithScope:OSLogStoreCurrentProcessIdentifier
                                                   error:&storeError];
        if (store == nil) {
            return NO;
        }
        NSDate *since = [NSDate dateWithTimeIntervalSinceNow:-3600];
        OSLogPosition *position = [store positionWithDate:since];
        if (position == nil) {
            return NO;
        }
        NSError *enumError = nil;
        OSLogEnumerator *enumerator = [store entriesEnumeratorWithOptions:OSLogEnumeratorReverse
                                                                 position:position
                                                                predicate:nil
                                                                    error:&enumError];
        if (enumerator == nil) {
            return NO;
        }

        NSDateFormatter *lineTime = [[NSDateFormatter alloc] init];
        lineTime.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
        lineTime.dateFormat = @"yyyy-MM-dd HH:mm:ss.SSS";

        const NSUInteger maxBytes = [FeedbackConfig maxLogBytes];
        NSUInteger written = *byteCount;
        NSUInteger lineCount = 0;

        for (OSLogEntry *entry in enumerator) {
            if (written >= maxBytes) {
                break;
            }
            if (![entry isKindOfClass:[OSLogEntryLog class]]) {
                continue;
            }
            OSLogEntryLog *log = (OSLogEntryLog *)entry;
            NSString *message = log.composedMessage;
            if (message.length == 0) {
                continue;
            }
            NSString *line = [NSString stringWithFormat:@"%@ %@[%@] %@ %@\n",
                              [lineTime stringFromDate:entry.date],
                              feedbackLogLevelLabel(log),
                              log.category ?: @"",
                              log.subsystem ?: @"",
                              message];
            NSUInteger lineBytes = [line lengthOfBytesUsingEncoding:NSUTF8StringEncoding];
            if (written + lineBytes > maxBytes) {
                break;
            }
            [text appendString:line];
            written += lineBytes;
            lineCount++;
        }
        *byteCount = written;
        return lineCount > 0;
    }
#else
    (void)text;
    (void)byteCount;
    (void)error;
#endif
    return NO;
}

+ (NSURL *)exportLogFileURLWithError:(NSError **)error {
    NSURL *cache = [NSFileManager.defaultManager URLsForDirectory:NSCachesDirectory
                                                        inDomains:NSUserDomainMask].lastObject;
    if (cache == nil) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_log_export" message:@"no cache dir"];
        }
        return nil;
    }
    NSDateFormatter *stamp = [[NSDateFormatter alloc] init];
    stamp.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
    stamp.dateFormat = @"yyyyMMdd_HHmmss";
    NSString *name = [NSString stringWithFormat:@"feedback_log_%@.txt", [stamp stringFromDate:[NSDate date]]];
    NSURL *fileURL = [cache URLByAppendingPathComponent:name];

    NSMutableString *text = [NSMutableString string];
    [text appendFormat:@"Orange Office iOS unified log export\nappVersion=%@\ndevice=%@\nos=%@\npid=%d\n\n",
                      [FeedbackClient appVersion],
                      [FeedbackClient deviceModel],
                      [FeedbackClient osVersion],
                      (int)getpid()];

    NSUInteger byteCount = [text lengthOfBytesUsingEncoding:NSUTF8StringEncoding];
    BOOL hasLogLines = [self appendUnifiedLogToText:text byteCount:&byteCount error:error];

    if (!hasLogLines) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_log_empty" message:@"no log lines"];
        }
        return nil;
    }
    if (byteCount == 0 || byteCount > [FeedbackConfig maxLogBytes]) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_log_upload" message:@"file size"];
        }
        return nil;
    }
    NSData *data = [text dataUsingEncoding:NSUTF8StringEncoding];
    if (data.length == 0) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_log_empty" message:@"no log lines"];
        }
        return nil;
    }
    if (![data writeToURL:fileURL options:NSDataWritingAtomic error:error]) {
        if (error && *error == nil) {
            *error = [FeedbackApiException errorWithReason:@"feedback_log_export" message:@"write failed"];
        }
        return nil;
    }
    return fileURL;
}

@end
