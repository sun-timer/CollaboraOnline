// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "FeedbackClient.h"

#import "FeedbackApiException.h"
#import "FeedbackConfig.h"
#import "FeedbackRecord.h"

#import <UIKit/UIKit.h>
#import <sys/utsname.h>
#include <stdio.h>

static void XLFeedbackDetailLog(NSString *message) {
    if (message.length == 0) {
        return;
    }
    NSLog(@"%@", message);
    fprintf(stderr, "%s\n", message.UTF8String);
    fflush(stderr);
}

@implementation FeedbackListPage
@end

@implementation FeedbackClient

+ (NSURLSession *)session {
    static NSURLSession *session;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        NSURLSessionConfiguration *config = [NSURLSessionConfiguration ephemeralSessionConfiguration];
        config.timeoutIntervalForRequest = [FeedbackConfig readTimeout];
        config.timeoutIntervalForResource = [FeedbackConfig readTimeout];
        session = [NSURLSession sessionWithConfiguration:config];
    });
    return session;
}

+ (NSString *)newBoundary {
    return [NSString stringWithFormat:@"----aioffice-%.0f", [[NSDate date] timeIntervalSince1970] * 1000];
}

+ (NSString *)responsePreview:(NSData *)data {
    if (data.length == 0) {
        return @"";
    }
    NSString *text = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    if (text.length == 0) {
        return @"<binary>";
    }
    text = [text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (text.length > 120) {
        text = [[text substringToIndex:120] stringByAppendingString:@"…"];
    }
    return text;
}

+ (NSDictionary *)parseRootResponse:(NSData *)data httpCode:(NSInteger)httpCode error:(NSError **)error {
    if (data.length == 0) {
        if (error) {
            NSString *msg = httpCode > 0 ? [NSString stringWithFormat:@"HTTP %ld", (long)httpCode] : @"empty body";
            *error = [FeedbackApiException errorWithReason:@"feedback_parse" message:msg httpCode:httpCode apiCode:0];
        }
        return nil;
    }
    id json = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
    if (![json isKindOfClass:[NSDictionary class]]) {
        if (error) {
            NSString *preview = [self responsePreview:data];
            NSString *msg = @"invalid json";
            if ([preview hasPrefix:@"<"]) {
                msg = [NSString stringWithFormat:@"非 JSON 响应 (HTTP %ld)", (long)httpCode];
            } else if (preview.length > 0) {
                msg = preview;
            }
            *error = [FeedbackApiException errorWithReason:@"feedback_parse" message:msg httpCode:httpCode apiCode:0];
        }
        return nil;
    }
    NSDictionary *root = (NSDictionary *)json;
    NSInteger code = [root[@"code"] integerValue];
    NSString *msg = root[@"msg"] ?: @"";
    if (httpCode < 200 || httpCode >= 300 || code != 200) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_api" message:msg httpCode:httpCode apiCode:code];
        }
        return nil;
    }
    return root;
}

+ (NSDictionary *)postJSON:(NSDictionary *)body path:(NSString *)path error:(NSError **)error {
    NSData *payload = [NSJSONSerialization dataWithJSONObject:body options:0 error:nil];
    if (payload == nil) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_json_build" message:@"serialize failed"];
        }
        return nil;
    }
    return [self postBytes:payload path:path contentType:@"application/json; charset=UTF-8" error:error];
}

+ (NSDictionary *)postBytes:(NSData *)body path:(NSString *)path contentType:(NSString *)contentType error:(NSError **)error {
    NSURL *url = [NSURL URLWithString:[FeedbackConfig endpoint:path]];
    if (url == nil) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_network" message:@"bad url"];
        }
        return nil;
    }
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
    request.HTTPMethod = @"POST";
    request.timeoutInterval = [FeedbackConfig connectTimeout];
    [request setValue:contentType forHTTPHeaderField:@"Content-Type"];
    [request setValue:@"application/json" forHTTPHeaderField:@"Accept"];
    request.HTTPBody = body;

    dispatch_semaphore_t sem = dispatch_semaphore_create(0);
    __block NSData *responseData = nil;
    __block NSHTTPURLResponse *response = nil;
    __block NSError *transportError = nil;
    [[self session] dataTaskWithRequest:request
                      completionHandler:^(NSData *data, NSURLResponse *urlResponse, NSError *err) {
                          responseData = data;
                          if ([urlResponse isKindOfClass:[NSHTTPURLResponse class]]) {
                              response = (NSHTTPURLResponse *)urlResponse;
                          }
                          transportError = err;
                          dispatch_semaphore_signal(sem);
                      }]
        .resume;
    dispatch_semaphore_wait(sem, DISPATCH_TIME_FOREVER);

    if (transportError != nil) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_network" message:transportError.localizedDescription];
        }
        return nil;
    }
    return [self parseRootResponse:responseData httpCode:response.statusCode error:error];
}

+ (NSDictionary *)baseUserBody:(NSString *)userId nickname:(NSString *)nickname avatar:(NSString *)avatarPath {
    NSMutableDictionary *body = [NSMutableDictionary dictionary];
    body[@"userId"] = userId ?: @"";
    if (nickname.length > 0) {
        body[@"nickname"] = nickname;
    }
    if (avatarPath.length > 0) {
        body[@"avatar"] = avatarPath;
    }
    return body;
}

+ (void)appendField:(NSMutableData *)out boundary:(NSString *)boundary name:(NSString *)name value:(NSString *)value {
    [out appendData:[[NSString stringWithFormat:@"--%@\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];
    [out appendData:[[NSString stringWithFormat:@"Content-Disposition: form-data; name=\"%@\"\r\n\r\n", name]
                          dataUsingEncoding:NSUTF8StringEncoding]];
    [out appendData:[[NSString stringWithFormat:@"%@\r\n", value] dataUsingEncoding:NSUTF8StringEncoding]];
}

+ (void)appendFileField:(NSMutableData *)out
               boundary:(NSString *)boundary
                   name:(NSString *)fieldName
               fileName:(NSString *)fileName
                   mime:(NSString *)mime
                   data:(NSData *)bytes {
    [out appendData:[[NSString stringWithFormat:@"--%@\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];
    [out appendData:[[NSString stringWithFormat:@"Content-Disposition: form-data; name=\"%@\"; filename=\"%@\"\r\n",
                                                  fieldName, fileName]
                          dataUsingEncoding:NSUTF8StringEncoding]];
    [out appendData:[[NSString stringWithFormat:@"Content-Type: %@\r\n\r\n", mime] dataUsingEncoding:NSUTF8StringEncoding]];
    [out appendData:bytes];
    [out appendData:[@"\r\n" dataUsingEncoding:NSUTF8StringEncoding]];
}

+ (UIImage *)scaleImage:(UIImage *)image maxEdge:(CGFloat)maxEdge {
    CGFloat w = image.size.width;
    CGFloat h = image.size.height;
    CGFloat maxDim = MAX(w, h);
    if (maxDim <= maxEdge) {
        return image;
    }
    CGFloat scale = maxEdge / maxDim;
    CGSize size = CGSizeMake(MAX(1, round(w * scale)), MAX(1, round(h * scale)));
    UIGraphicsBeginImageContextWithOptions(size, YES, 1.0);
    [image drawInRect:CGRectMake(0, 0, size.width, size.height)];
    UIImage *scaled = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    return scaled ?: image;
}

+ (NSData *)prepareUploadImageDataFromPath:(NSString *)path error:(NSError **)error {
    UIImage *image = [UIImage imageWithContentsOfFile:path];
    if (image == nil) {
        NSData *raw = [NSData dataWithContentsOfFile:path];
        if (raw.length == 0) {
            if (error) {
                *error = [FeedbackApiException errorWithReason:@"feedback_read_uri" message:@"missing image"];
            }
            return nil;
        }
        if (raw.length > [FeedbackConfig maxUploadImageBytes]) {
            if (error) {
                *error = [FeedbackApiException errorWithReason:@"feedback_upload" message:@"file size"];
            }
            return nil;
        }
        return raw;
    }
    UIImage *scaled = [self scaleImage:image maxEdge:[FeedbackConfig uploadImageMaxEdgePx]];
    NSData *jpeg = UIImageJPEGRepresentation(scaled, [FeedbackConfig uploadImageJPEGQuality]);
    if (jpeg.length == 0) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_upload" message:@"compress fail"];
        }
        return nil;
    }
    if (jpeg.length > [FeedbackConfig maxUploadImageBytes]) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_upload" message:@"file size"];
        }
        return nil;
    }
    return jpeg;
}

+ (NSString *)uploadAvatarWithUserId:(NSString *)userId filePath:(NSString *)filePath error:(NSError **)error {
    NSError *prepError = nil;
    NSData *fileData = [self prepareUploadImageDataFromPath:filePath error:&prepError];
    if (fileData.length == 0) {
        fileData = [NSData dataWithContentsOfFile:filePath];
    }
    if (fileData.length == 0) {
        if (error) {
            *error = prepError ?: [FeedbackApiException errorWithReason:@"feedback_read_uri" message:@"missing file"];
        }
        return nil;
    }
    NSString *boundary = [self newBoundary];
    NSMutableData *body = [NSMutableData data];
    [self appendField:body boundary:boundary name:@"userId" value:userId ?: @""];
    NSString *fileName = @"avatar.jpg";
    [self appendFileField:body boundary:boundary name:@"file" fileName:fileName mime:@"image/jpeg" data:fileData];
    [body appendData:[[NSString stringWithFormat:@"--%@--\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];

    NSDictionary *root = [self postBytes:body
                                    path:[FeedbackConfig PATHUploadAvatar]
                             contentType:[NSString stringWithFormat:@"multipart/form-data; boundary=%@", boundary]
                                   error:error];
    if (root == nil) {
        return nil;
    }
    NSDictionary *data = root[@"data"];
    if (![data isKindOfClass:[NSDictionary class]]) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_upload_parse" message:@"missing data"];
        }
        return nil;
    }
    NSString *avatar = data[@"avatar"] ?: data[@"path"];
    return avatar;
}

+ (NSArray<NSString *> *)uploadImagesWithFilePaths:(NSArray<NSString *> *)filePaths error:(NSError **)error {
    if (filePaths.count == 0) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_upload_empty" message:@"files empty"];
        }
        return nil;
    }
    NSString *boundary = [self newBoundary];
    NSMutableData *body = [NSMutableData data];
    for (NSUInteger i = 0; i < filePaths.count; i++) {
        NSError *prepError = nil;
        NSData *bytes = [self prepareUploadImageDataFromPath:filePaths[i] error:&prepError];
        if (bytes == nil) {
            if (error) {
                *error = prepError;
            }
            return nil;
        }
        NSString *baseName = filePaths[i].lastPathComponent.length > 0 ? filePaths[i].lastPathComponent : @"upload.jpg";
        NSString *stem = [baseName stringByDeletingPathExtension];
        NSString *fileName = [NSString stringWithFormat:@"%@.jpg", stem];
        [self appendFileField:body boundary:boundary name:@"files" fileName:fileName mime:@"image/jpeg" data:bytes];
    }
    [body appendData:[[NSString stringWithFormat:@"--%@--\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];

    NSDictionary *root = [self postBytes:body
                                    path:[FeedbackConfig PATHUploadImages]
                             contentType:[NSString stringWithFormat:@"multipart/form-data; boundary=%@", boundary]
                                   error:error];
    if (root == nil) {
        return nil;
    }
    id data = root[@"data"];
    return [self stringArrayFromJSON:data];
}

+ (NSString *)uploadLogWithUserId:(NSString *)userId fileURL:(NSURL *)fileURL error:(NSError **)error {
    NSData *fileData = fileURL ? [NSData dataWithContentsOfURL:fileURL] : nil;
    if (fileData.length == 0) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_log_upload" message:@"missing file"];
        }
        return nil;
    }
    if (fileData.length > [FeedbackConfig maxLogBytes]) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_log_upload" message:@"file size"];
        }
        return nil;
    }
    NSString *boundary = [self newBoundary];
    NSMutableData *body = [NSMutableData data];
    [self appendField:body boundary:boundary name:@"userId" value:userId ?: @""];
    NSString *fileName = fileURL.lastPathComponent.length > 0 ? fileURL.lastPathComponent : @"feedback_log.txt";
    [self appendFileField:body boundary:boundary name:@"file" fileName:fileName mime:@"text/plain" data:fileData];
    [body appendData:[[NSString stringWithFormat:@"--%@--\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];

    NSDictionary *root = [self postBytes:body
                                    path:[FeedbackConfig PATHUploadLog]
                             contentType:[NSString stringWithFormat:@"multipart/form-data; boundary=%@", boundary]
                                   error:error];
    if (root == nil) {
        return nil;
    }
    id data = root[@"data"];
    if ([data isKindOfClass:[NSDictionary class]]) {
        NSDictionary *dict = (NSDictionary *)data;
        NSString *logPath = dict[@"logPath"] ?: dict[@"path"];
        if (logPath.length > 0) {
            return logPath;
        }
    }
    if (error) {
        *error = [FeedbackApiException errorWithReason:@"feedback_log_upload" message:@"missing data"];
    }
    return nil;
}

+ (NSArray<NSString *> *)stringArrayFromJSON:(id)value {
    if (![value isKindOfClass:[NSArray class]]) {
        return @[];
    }
    NSMutableArray<NSString *> *list = [NSMutableArray array];
    for (id item in (NSArray *)value) {
        if ([item isKindOfClass:[NSString class]]) {
            [list addObject:item];
        } else if (item != nil) {
            [list addObject:[item description]];
        }
    }
    return [list copy];
}

+ (FeedbackRecord *)submitWithUserId:(NSString *)userId
                            nickname:(NSString *)nickname
                              avatar:(NSString *)avatarPath
                        feedbackType:(NSString *)feedbackType
                             content:(NSString *)content
                             contact:(NSString *)contact
                          imagePaths:(NSArray<NSString *> *)imagePaths
                             logPath:(NSString *)logPath
                          appVersion:(NSString *)appVersion
                         deviceModel:(NSString *)deviceModel
                           osVersion:(NSString *)osVersion
                               error:(NSError **)error {
    NSMutableDictionary *body = [[self baseUserBody:userId nickname:nickname avatar:avatarPath] mutableCopy];
    body[@"feedbackType"] = feedbackType ?: @"";
    body[@"content"] = content ?: @"";
    if (contact.length > 0) {
        body[@"contact"] = contact;
    }
    if (imagePaths.count > 0) {
        body[@"imagePaths"] = imagePaths;
    }
    if (logPath.length > 0) {
        body[@"logPath"] = logPath;
    }
    if (appVersion.length > 0) {
        body[@"appVersion"] = appVersion;
    }
    if (deviceModel.length > 0) {
        body[@"deviceModel"] = deviceModel;
    }
    if (osVersion.length > 0) {
        body[@"osVersion"] = osVersion;
    }

    NSDictionary *root = [self postJSON:body path:[FeedbackConfig PATHSubmit] error:error];
    if (root == nil) {
        return nil;
    }
    NSDictionary *data = root[@"data"];
    if (![data isKindOfClass:[NSDictionary class]]) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_parse" message:@"missing data"];
        }
        return nil;
    }
    FeedbackRecord *record = [[FeedbackRecord alloc] init];
    record.recordId = data[@"feedbackNo"] ?: @"";
    record.type = feedbackType ?: @"";
    record.content = content ?: @"";
    record.contact = contact ?: @"";
    record.status = [FeedbackRecord statusFromApiLabel:data[@"status"] ?: @"已提交"];
    record.submitTime = [FeedbackRecord parseServerTime:data[@"submitTime"]];
    record.imagePaths = imagePaths ?: @[];
    return record;
}

+ (FeedbackListPage *)listWithUserId:(NSString *)userId
                            nickname:(NSString *)nickname
                              avatar:(NSString *)avatarPath
                             pageNum:(NSInteger)pageNum
                            pageSize:(NSInteger)pageSize
                               error:(NSError **)error {
    NSMutableDictionary *body = [[self baseUserBody:userId nickname:nickname avatar:avatarPath] mutableCopy];
    body[@"pageNum"] = @(pageNum);
    body[@"pageSize"] = @(pageSize);

    NSDictionary *root = [self postJSON:body path:[FeedbackConfig PATHList] error:error];
    if (root == nil) {
        return nil;
    }
    NSDictionary *data = root[@"data"];
    if (![data isKindOfClass:[NSDictionary class]]) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_parse" message:@"missing data"];
        }
        return nil;
    }
    FeedbackListPage *page = [[FeedbackListPage alloc] init];
    page.pageNum = [data[@"pageNum"] integerValue] ?: pageNum;
    page.pageSize = [data[@"pageSize"] integerValue] ?: pageSize;
    page.total = [data[@"total"] integerValue];
    page.pages = [data[@"pages"] integerValue];
    NSMutableArray<FeedbackRecord *> *list = [NSMutableArray array];
    id items = data[@"list"];
    if ([items isKindOfClass:[NSArray class]]) {
        for (id item in (NSArray *)items) {
            if (![item isKindOfClass:[NSDictionary class]]) {
                continue;
            }
            NSDictionary *dict = (NSDictionary *)item;
            FeedbackRecord *record = [[FeedbackRecord alloc] init];
            record.recordId = dict[@"feedbackNo"] ?: @"";
            record.type = dict[@"feedbackType"] ?: @"";
            record.content = dict[@"contentSummary"] ?: @"";
            record.status = [FeedbackRecord statusFromApiLabel:dict[@"status"]];
            record.submitTime = [FeedbackRecord parseServerTime:dict[@"submitTime"]];
            [list addObject:record];
        }
    }
    page.list = [list copy];
    return page;
}

+ (FeedbackRecord *)detailWithUserId:(NSString *)userId
                            nickname:(NSString *)nickname
                              avatar:(NSString *)avatarPath
                          feedbackNo:(NSString *)feedbackNo
                               error:(NSError **)error {
    NSMutableDictionary *body = [[self baseUserBody:userId nickname:nickname avatar:avatarPath] mutableCopy];
    body[@"feedbackNo"] = feedbackNo ?: @"";

    NSDictionary *root = [self postJSON:body path:[FeedbackConfig PATHDetail] error:error];
    if (root == nil) {
        return nil;
    }
    NSDictionary *data = root[@"data"];
    if (![data isKindOfClass:[NSDictionary class]]) {
        if (error) {
            *error = [FeedbackApiException errorWithReason:@"feedback_parse" message:@"missing data"];
        }
        return nil;
    }
    FeedbackRecord *record = [FeedbackRecord recordFromDetailDictionary:data];
    XLFeedbackDetailLog([NSString stringWithFormat:@"XLFeedbackDetail API no=%@ keys=%@ contentLen=%lu images=%lu",
                         feedbackNo, [data allKeys], (unsigned long)record.content.length,
                         (unsigned long)record.imagePaths.count]);
    return record;
}

+ (BOOL)closeWithUserId:(NSString *)userId
               nickname:(NSString *)nickname
                 avatar:(NSString *)avatarPath
             feedbackNo:(NSString *)feedbackNo
                  error:(NSError **)error {
    NSMutableDictionary *body = [[self baseUserBody:userId nickname:nickname avatar:avatarPath] mutableCopy];
    body[@"feedbackNo"] = feedbackNo ?: @"";
    NSDictionary *root = [self postJSON:body path:[FeedbackConfig PATHClose] error:error];
    return root != nil;
}

+ (NSString *)deviceModel {
    struct utsname systemInfo;
    uname(&systemInfo);
    NSString *code = [NSString stringWithCString:systemInfo.machine encoding:NSUTF8StringEncoding];
    if (code.length > 0) {
        return code;
    }
    return UIDevice.currentDevice.model;
}

+ (NSString *)osVersion {
    return [NSString stringWithFormat:@"iOS %@", UIDevice.currentDevice.systemVersion];
}

+ (NSString *)appVersion {
    NSString *version = NSBundle.mainBundle.infoDictionary[@"CFBundleShortVersionString"];
    return version ?: @"";
}

@end
