// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AIRequestSession.h"

@interface AIRequestSession ()
@property (copy, nonatomic, readwrite) NSString *requestId;
@property (copy, nonatomic, readwrite) NSString *documentSessionId;
@property (strong, nonatomic) NSURLSessionDataTask *task;
@property (strong, nonatomic) NSMutableData *lineBuffer;
@property (strong, nonatomic) NSMutableString *accumulatedText;
@property (assign, nonatomic) BOOL cancelled;
@property (assign, nonatomic) BOOL terminal;
@end

@implementation AIRequestSession

- (instancetype)initWithRequestId:(NSString *)requestId
                documentSessionId:(NSString *)documentSessionId {
    self = [super init];
    if (self) {
        _requestId = [requestId copy];
        _documentSessionId = [documentSessionId copy];
        _lineBuffer = [[NSMutableData alloc] init];
        _accumulatedText = [[NSMutableString alloc] init];
    }
    return self;
}

- (void)bindTask:(NSURLSessionDataTask *)task {
    @synchronized (self) {
        self.task = task;
        if (self.cancelled) {
            [task cancel];
        }
    }
}

- (void)cancel {
    NSURLSessionDataTask *task = nil;
    @synchronized (self) {
        if (self.cancelled) {
            return;
        }
        self.cancelled = YES;
        task = self.task;
    }
    [task cancel];
}

- (BOOL)isCancelled {
    @synchronized (self) {
        return self.cancelled;
    }
}

- (BOOL)canEmit {
    @synchronized (self) {
        return !self.cancelled && !self.terminal;
    }
}

- (BOOL)markTerminal {
    @synchronized (self) {
        if (self.cancelled || self.terminal) {
            return NO;
        }
        self.terminal = YES;
        return YES;
    }
}

- (NSArray<NSString *> *)consumeLinesFromData:(NSData *)data {
    if (data.length == 0) {
        return @[];
    }
    @synchronized (self) {
        [self.lineBuffer appendData:data];
        const uint8_t *bytes = (const uint8_t *)self.lineBuffer.bytes;
        NSUInteger start = 0;
        NSMutableArray<NSString *> *lines = [[NSMutableArray alloc] init];
        for (NSUInteger index = 0; index < self.lineBuffer.length; index++) {
            if (bytes[index] != '\n') {
                continue;
            }
            NSUInteger length = index - start;
            if (length > 0 && bytes[start + length - 1] == '\r') {
                length--;
            }
            NSData *lineData = [self.lineBuffer subdataWithRange:NSMakeRange(start, length)];
            NSString *line = [[NSString alloc] initWithData:lineData encoding:NSUTF8StringEncoding];
            if (line != nil) {
                [lines addObject:line];
            }
            start = index + 1;
        }
        if (start > 0) {
            [self.lineBuffer replaceBytesInRange:NSMakeRange(0, start) withBytes:NULL length:0];
        }
        return lines;
    }
}

- (NSArray<NSString *> *)consumePendingLines {
    @synchronized (self) {
        if (self.lineBuffer.length == 0) {
            return @[];
        }
        NSData *lineData = [self.lineBuffer copy];
        self.lineBuffer.length = 0;
        NSString *line = [[NSString alloc] initWithData:lineData encoding:NSUTF8StringEncoding];
        if (line.length > 0 && [line hasSuffix:@"\r"]) {
            line = [line substringToIndex:line.length - 1];
        }
        return line != nil ? @[line] : @[];
    }
}

- (void)appendDelta:(NSString *)delta {
    if (delta.length == 0) {
        return;
    }
    @synchronized (self) {
        [self.accumulatedText appendString:delta];
    }
}

- (NSString *)fullText {
    @synchronized (self) {
        return [self.accumulatedText copy];
    }
}

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
