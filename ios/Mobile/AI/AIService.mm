// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AIService.h"

#import "AIConfigurationStore.h"
#import "AIModelConfigStore.h"
#import "AIRequestSession.h"

@interface AIServiceRequest : NSObject
@property (strong, nonatomic) AIRequestSession *session;
@property (copy, nonatomic) AIServiceEventEmitter emitter;
@property (assign, nonatomic) NSInteger statusCode;
/** Non-streaming image generation accumulates the raw JSON response. */
@property (assign, nonatomic) BOOL imageGeneration;
@property (strong, nonatomic) NSMutableData *responseData;
@end

@implementation AIServiceRequest
@end

@interface AIService ()
@property (strong, nonatomic) AIConfigurationStore *configurationStore;
@property (strong, nonatomic) AIModelConfigStore *modelStore;
@property (strong, nonatomic) NSURLSession *urlSession;
@property (strong, nonatomic) NSOperationQueue *delegateQueue;
@property (strong, nonatomic) NSMutableDictionary<NSURLSessionTask *, AIServiceRequest *> *requestsByTask;
@property (strong, nonatomic) NSMutableDictionary<NSString *, AIServiceRequest *> *requestsById;
@end

@implementation AIService

- (instancetype)init {
    return [self initWithConfigurationStore:[[AIConfigurationStore alloc] init]];
}

- (instancetype)initWithConfigurationStore:(AIConfigurationStore *)configurationStore {
    self = [super init];
    if (self) {
        _configurationStore = configurationStore ?: [[AIConfigurationStore alloc] init];
        _modelStore = [[AIModelConfigStore alloc] init];
        _delegateQueue = [[NSOperationQueue alloc] init];
        _delegateQueue.maxConcurrentOperationCount = 1;
        _requestsByTask = [[NSMutableDictionary alloc] init];
        _requestsById = [[NSMutableDictionary alloc] init];
        _urlSession = [NSURLSession sessionWithConfiguration:[NSURLSessionConfiguration defaultSessionConfiguration]
                                                     delegate:self
                                                delegateQueue:_delegateQueue];
    }
    return self;
}

- (void)startRequest:(NSDictionary *)payload
           requestId:(NSString *)requestId
  documentSessionId:(NSString *)documentSessionId
               emit:(AIServiceEventEmitter)emit {
    if (requestId.length == 0 || documentSessionId.length == 0 || emit == nil) {
        return;
    }

    [self cancelRequest:requestId documentSessionId:documentSessionId];
    AIModelType modelType = [self defaultModelTypeForTask:
        [payload[@"taskType"] isKindOfClass:[NSString class]]
            ? payload[@"taskType"] : @""];
    id rawType = payload[@"modelType"];
    if ([rawType isKindOfClass:[NSNumber class]]) {
        NSInteger typeValue = ((NSNumber *)rawType).integerValue;
        if (typeValue >= AIModelTypeBase && typeValue <= AIModelTypeVision) {
            modelType = (AIModelType)typeValue;
        }
    }
    NSString *taskType = [payload[@"taskType"] isKindOfClass:[NSString class]]
        ? payload[@"taskType"] : @"";
    BOOL isImageGeneration = [taskType isEqualToString:@"image_generate"];
    AIModelConfigForm *form = [self.modelStore loadForm:modelType];
    NSString *endpoint = form.url;
    NSString *model = form.modelName;
    NSString *apiKey = form.apiKey;
    if (endpoint.length == 0 || model.length == 0 || apiKey.length == 0) {
        // 回退旧配置存储
        NSError *configurationError = nil;
        AIConfiguration *configuration = [self.configurationStore configurationWithError:&configurationError];
        if (configuration != nil && configuration.endpoint.length > 0) {
            endpoint = configuration.endpoint;
            model = configuration.model ?: model;
            apiKey = configuration.apiKey ?: apiKey;
        }
    }
    if (endpoint.length == 0 || model.length == 0 || apiKey.length == 0) {
        NSString *message = @"AI service is not configured";
        [self emitType:@"ai.error"
              requestId:requestId
           documentSessionId:documentSessionId
                  payload:@{@"code": @"config_missing", @"message": message}
                   emitter:emit];
        return;
    }

    if (isImageGeneration) {
        [self startImageGenerationWithEndpoint:endpoint
                                         model:model
                                        apiKey:apiKey
                                       payload:payload
                                     requestId:requestId
                            documentSessionId:documentSessionId
                                        emit:emit];
        return;
    }


    NSError *messagesError = nil;
    NSArray *messages = [self messagesForPayload:payload error:&messagesError];
    if (messages == nil) {
        NSString *errorCode = messagesError.code == 2
            ? @"unsupported_task_type" : @"invalid_payload";
        [self emitType:@"ai.error"
              requestId:requestId
           documentSessionId:documentSessionId
                  payload:@{
                      @"code": errorCode,
                      @"message": messagesError.localizedDescription ?: @"Invalid AI payload",
                  }
                   emitter:emit];
        return;
    }

    NSURL *url = [NSURL URLWithString:endpoint];
    if (url == nil || url.scheme.length == 0 || url.host.length == 0) {
        [self emitType:@"ai.error"
              requestId:requestId
           documentSessionId:documentSessionId
                  payload:@{@"code": @"config_invalid", @"message": @"AI endpoint is invalid"}
                   emitter:emit];
        return;
    }

    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
    request.HTTPMethod = @"POST";
    request.timeoutInterval = 30.0;
    [request setValue:@"application/json; charset=UTF-8" forHTTPHeaderField:@"Content-Type"];
    [request setValue:@"text/event-stream" forHTTPHeaderField:@"Accept"];
    [request setValue:[NSString stringWithFormat:@"Bearer %@", apiKey]
        forHTTPHeaderField:@"Authorization"];

    NSMutableDictionary *body = [@{
        @"model": model,
        @"stream": @YES,
        @"messages": messages,
    } mutableCopy];
    if (form.temperature > 0) {
        body[@"temperature"] = @(form.temperature);
    }
    if (form.topP > 0) {
        body[@"top_p"] = @(form.topP);
    }
    if (form.presencePenalty != 0) {
        body[@"presence_penalty"] = @(form.presencePenalty);
    }
    if (form.frequencyPenalty != 0) {
        body[@"frequency_penalty"] = @(form.frequencyPenalty);
    }
    [body addEntriesFromDictionary:[self.modelStore samplingBodyFieldsForForm:form]];
    request.HTTPBody = [NSJSONSerialization dataWithJSONObject:body options:0 error:nil];

    AIServiceRequest *serviceRequest = [[AIServiceRequest alloc] init];
    serviceRequest.session = [[AIRequestSession alloc] initWithRequestId:requestId
                                                       documentSessionId:documentSessionId];
    serviceRequest.emitter = [emit copy];
    NSURLSessionDataTask *task = [self.urlSession dataTaskWithRequest:request];
    [serviceRequest.session bindTask:task];
    self.requestsByTask[task] = serviceRequest;
    self.requestsById[requestId] = serviceRequest;

    [self emitType:@"ai.state"
          requestId:requestId
       documentSessionId:documentSessionId
              payload:@{@"state": @"loading"}
               emitter:emit];
    [task resume];
}

- (void)startImageGenerationWithEndpoint:(NSString *)endpoint
                                   model:(NSString *)model
                                  apiKey:(NSString *)apiKey
                                 payload:(NSDictionary *)payload
                               requestId:(NSString *)requestId
                      documentSessionId:(NSString *)documentSessionId
                                  emit:(AIServiceEventEmitter)emit {
    NSURL *endpointURL = [NSURL URLWithString:endpoint];
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:endpointURL];
    request.HTTPMethod = @"POST";
    request.timeoutInterval = 60.0;
    [request setValue:@"application/json; charset=UTF-8" forHTTPHeaderField:@"Content-Type"];
    [request setValue:@"application/json" forHTTPHeaderField:@"Accept"];
    [request setValue:[NSString stringWithFormat:@"Bearer %@", apiKey]
        forHTTPHeaderField:@"Authorization"];
    NSString *size = [payload[@"size"] isKindOfClass:[NSString class]]
        ? payload[@"size"] : @"1024x1024";
    NSString *prompt = [payload[@"prompt"] isKindOfClass:[NSString class]]
        ? payload[@"prompt"] : @"";
    NSDictionary *body = @{
        @"model": model,
        @"prompt": prompt,
        @"size": size.length > 0 ? size : @"1024x1024",
        @"response_format": @"b64_json",
        @"n": @1,
    };

    request.HTTPBody = [NSJSONSerialization dataWithJSONObject:body options:0 error:nil];

    AIServiceRequest *serviceRequest = [[AIServiceRequest alloc] init];
    serviceRequest.session = [[AIRequestSession alloc] initWithRequestId:requestId
                                                       documentSessionId:documentSessionId];
    serviceRequest.emitter = [emit copy];
    serviceRequest.imageGeneration = YES;
    serviceRequest.responseData = [[NSMutableData alloc] init];
    NSURLSessionDataTask *task = [self.urlSession dataTaskWithRequest:request];
    [serviceRequest.session bindTask:task];
    self.requestsByTask[task] = serviceRequest;
    self.requestsById[requestId] = serviceRequest;

    [self emitType:@"ai.state"
          requestId:requestId
       documentSessionId:documentSessionId
              payload:@{@"state": @"loading"}
               emitter:emit];
    [task resume];
}

- (AIModelType)defaultModelTypeForTask:(NSString *)taskType {
    if ([taskType isEqualToString:@"image_generate"]) {
        return AIModelTypeImage;
    }
    if ([taskType isEqualToString:@"text_extract"]) {
        return AIModelTypeVision;
    }
    return AIModelTypeBase;
}

- (void)cancelRequest:(NSString *)requestId
   documentSessionId:(NSString *)documentSessionId {
    AIServiceRequest *serviceRequest = self.requestsById[requestId];
    if (serviceRequest == nil
        || ![serviceRequest.session.documentSessionId isEqualToString:documentSessionId]) {
        return;
    }

    [serviceRequest.session cancel];
    [self.requestsById removeObjectForKey:requestId];
    [self emitType:@"ai.state"
          requestId:requestId
       documentSessionId:documentSessionId
              payload:@{@"state": @"cancelled"}
               emitter:serviceRequest.emitter];
}

- (void)cancelRequestsForDocumentSession:(NSString *)documentSessionId {
    NSArray<AIServiceRequest *> *requests = [self.requestsById.allValues copy];
    for (AIServiceRequest *serviceRequest in requests) {
        if ([serviceRequest.session.documentSessionId isEqualToString:documentSessionId]) {
            [self cancelRequest:serviceRequest.session.requestId
            documentSessionId:documentSessionId];
        }
    }
}

- (void)URLSession:(NSURLSession *)session
          dataTask:(NSURLSessionDataTask *)dataTask
didReceiveResponse:(NSURLResponse *)response
 completionHandler:(void (^)(NSURLSessionResponseDisposition disposition))completionHandler {
    AIServiceRequest *serviceRequest = self.requestsByTask[dataTask];
    NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
    serviceRequest.statusCode = httpResponse.statusCode;
    if (httpResponse.statusCode < 200 || httpResponse.statusCode >= 300) {
        completionHandler(NSURLSessionResponseCancel);
        [self finishErrorForRequest:serviceRequest code:[NSString stringWithFormat:@"http_%ld",
                                                         (long)httpResponse.statusCode]
                            message:@"AI request failed"];
        return;
    }
    completionHandler(NSURLSessionResponseAllow);
}

- (void)URLSession:(NSURLSession *)session
          dataTask:(NSURLSessionDataTask *)dataTask
    didReceiveData:(NSData *)data {
    AIServiceRequest *serviceRequest = self.requestsByTask[dataTask];
    if (serviceRequest == nil || ![serviceRequest.session canEmit]) {
        return;
    }
    if (serviceRequest.imageGeneration) {
        [serviceRequest.responseData appendData:data];
        return;
    }
    for (NSString *line in [serviceRequest.session consumeLinesFromData:data]) {
        if ([line hasPrefix:@"data:"]) {
            [self handleSSEData:[line substringFromIndex:5] request:serviceRequest];
        }
    }
}

- (void)URLSession:(NSURLSession *)session
              task:(NSURLSessionTask *)task
didCompleteWithError:(NSError *)error {
    AIServiceRequest *serviceRequest = self.requestsByTask[task];
    if (serviceRequest == nil) {
        return;
    }

    if (serviceRequest.session.isCancelled) {
        [self removeRequest:serviceRequest];
        return;
    }
    if (error != nil && ![error.domain isEqualToString:NSURLErrorDomain]) {
        [self finishErrorForRequest:serviceRequest code:@"request_failed"
                            message:@"AI request failed"];
        return;
    }
    if (error != nil && error.code != NSURLErrorCancelled) {
        [self finishErrorForRequest:serviceRequest code:@"request_failed"
                            message:@"AI request failed"];
        return;
    }
    if (serviceRequest.imageGeneration) {
        [self finishImageGenerationForRequest:serviceRequest];
        return;
    }
    for (NSString *line in [serviceRequest.session consumePendingLines]) {
        if ([line hasPrefix:@"data:"]) {
            [self handleSSEData:[line substringFromIndex:5] request:serviceRequest];
        }
        [self finishDoneForRequest:serviceRequest];
    }
}

- (void)handleSSEData:(NSString *)rawData request:(AIServiceRequest *)serviceRequest {
    NSString *data = [rawData stringByTrimmingCharactersInSet:
        [NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (data.length == 0 || ![serviceRequest.session canEmit]) {
        return;
    }
    if ([data isEqualToString:@"[DONE]"]) {
        [self finishDoneForRequest:serviceRequest];
        return;
    }

    NSData *jsonData = [data dataUsingEncoding:NSUTF8StringEncoding];
    NSDictionary *chunk = [NSJSONSerialization JSONObjectWithData:jsonData options:0 error:nil];
    NSArray *choices = [chunk isKindOfClass:[NSDictionary class]] ? chunk[@"choices"] : nil;
    NSDictionary *choice = choices.count > 0 && [choices[0] isKindOfClass:[NSDictionary class]]
        ? choices[0] : nil;
    NSDictionary *delta = [choice[@"delta"] isKindOfClass:[NSDictionary class]]
        ? choice[@"delta"] : nil;
    NSString *text = [delta[@"content"] isKindOfClass:[NSString class]] ? delta[@"content"] : nil;
    if (text.length == 0 && [choice[@"text"] isKindOfClass:[NSString class]]) {
        text = choice[@"text"];
    }
    if (text.length == 0) {
        return;
    }

    [serviceRequest.session appendDelta:text];
    [self emitType:@"ai.state"
          requestId:serviceRequest.session.requestId
       documentSessionId:serviceRequest.session.documentSessionId
              payload:@{@"state": @"streaming"}
               emitter:serviceRequest.emitter];
    [self emitType:@"ai.stream"
          requestId:serviceRequest.session.requestId
       documentSessionId:serviceRequest.session.documentSessionId
              payload:@{@"state": @"streaming", @"delta": text}
               emitter:serviceRequest.emitter];
}

- (void)finishImageGenerationForRequest:(AIServiceRequest *)serviceRequest {
    if (![serviceRequest.session markTerminal]) {
        return;
    }
    NSError *jsonError = nil;
    NSDictionary *response = nil;
    if (serviceRequest.responseData.length > 0) {
        response = [NSJSONSerialization JSONObjectWithData:serviceRequest.responseData
                                                   options:0
                                                     error:&jsonError];
    }
    if (jsonError != nil || ![response isKindOfClass:[NSDictionary class]]) {
        [self emitType:@"ai.error"
              requestId:serviceRequest.session.requestId
           documentSessionId:serviceRequest.session.documentSessionId
                  payload:@{@"code": @"image_response_invalid",
                            @"message": @"图片生成响应解析失败"}
                   emitter:serviceRequest.emitter];
        [self removeRequest:serviceRequest];
        return;
    }
    NSString *base64Image = nil;
    NSArray *dataArray = [response[@"data"] isKindOfClass:[NSArray class]]
        ? response[@"data"] : nil;
    for (id item in dataArray) {
        if ([item isKindOfClass:[NSDictionary class]]
            && [item[@"b64_json"] isKindOfClass:[NSString class]]) {
            NSString *candidate = item[@"b64_json"];
            if (candidate.length > 0) {
                base64Image = candidate;
                break;
            }
        }
    }
    if (base64Image == nil) {
        [self emitType:@"ai.error"
              requestId:serviceRequest.session.requestId
           documentSessionId:serviceRequest.session.documentSessionId
                  payload:@{@"code": @"no_b64", @"message": @"返回数据未包含 b64_json 字段"}
                   emitter:serviceRequest.emitter];
        [self removeRequest:serviceRequest];
        return;
    }
    [self emitType:@"ai.done"
          requestId:serviceRequest.session.requestId
       documentSessionId:serviceRequest.session.documentSessionId
              payload:@{@"state": @"ready", @"imageBase64": base64Image}
               emitter:serviceRequest.emitter];
    [self removeRequest:serviceRequest];
}

- (void)finishDoneForRequest:(AIServiceRequest *)serviceRequest {
    if (![serviceRequest.session markTerminal]) {
        return;
    }
    [self emitType:@"ai.done"
          requestId:serviceRequest.session.requestId
       documentSessionId:serviceRequest.session.documentSessionId
              payload:@{@"state": @"ready", @"fullText": serviceRequest.session.fullText}
               emitter:serviceRequest.emitter];
    [self removeRequest:serviceRequest];
}

- (void)finishErrorForRequest:(AIServiceRequest *)serviceRequest
                         code:(NSString *)code
                       message:(NSString *)message {
    if (![serviceRequest.session markTerminal]) {
        return;
    }
    [self emitType:@"ai.error"
          requestId:serviceRequest.session.requestId
       documentSessionId:serviceRequest.session.documentSessionId
              payload:@{@"code": code ?: @"request_failed",
                        @"message": message ?: @"AI request failed"}
               emitter:serviceRequest.emitter];
    [self removeRequest:serviceRequest];
}

- (void)removeRequest:(AIServiceRequest *)serviceRequest {
    NSArray<NSURLSessionTask *> *tasks = [self.requestsByTask allKeysForObject:serviceRequest];
    for (NSURLSessionTask *task in tasks) {
        [self.requestsByTask removeObjectForKey:task];
    }
    [self.requestsById removeObjectForKey:serviceRequest.session.requestId];
}

- (void)emitType:(NSString *)type
       requestId:(NSString *)requestId
    documentSessionId:(NSString *)documentSessionId
           payload:(NSDictionary *)payload
            emitter:(AIServiceEventEmitter)emitter {
    if (emitter == nil) {
        return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        emitter(type, requestId, documentSessionId, payload ?: @{});
    });
}

- (NSArray *)messagesForPayload:(NSDictionary *)payload error:(NSError **)error {
    NSString *taskType = [payload[@"taskType"] isKindOfClass:[NSString class]]
        ? payload[@"taskType"] : @"";
    NSString *text = [payload[@"selection"] isKindOfClass:[NSString class]]
        ? [payload[@"selection"] stringByTrimmingCharactersInSet:
            [NSCharacterSet whitespaceAndNewlineCharacterSet]] : @"";
    NSDictionary *context = [payload[@"context"] isKindOfClass:[NSDictionary class]]
        ? payload[@"context"] : @{};
    BOOL isConversation = [taskType isEqualToString:@"chat"]
        || [taskType isEqualToString:@"doc_qa"];
    NSString *conversationPrompt = [context[@"prompt"] isKindOfClass:[NSString class]]
        ? [context[@"prompt"] stringByTrimmingCharactersInSet:
            [NSCharacterSet whitespaceAndNewlineCharacterSet]] : @"";
    BOOL isDocumentLevelTask = [taskType isEqualToString:@"outline"]
        || [taskType isEqualToString:@"article_generate"]
        || [taskType isEqualToString:@"text_extract"]
        || [taskType isEqualToString:@"impress_outline"]
        || [taskType isEqualToString:@"impress_generate"];
    if (text.length == 0 && !isDocumentLevelTask
        && (!isConversation || conversationPrompt.length == 0)) {
        if (error != NULL) {
            *error = [NSError errorWithDomain:@"com.xunlong.xloffice.ai"
                                         code:1
                                     userInfo:@{NSLocalizedDescriptionKey: @"Request text is empty"}];
        }
        return nil;
    }

    NSString *systemPrompt = nil;
    NSString *userPrompt = nil;
    if (isConversation) {
        systemPrompt = [taskType isEqualToString:@"doc_qa"]
            ? @"你是文档问答助手。只根据提供的文档上下文回答问题；如果上下文不足，请明确说明。"
            : @"你是办公助手，请清晰、准确地回答用户消息。";
        NSMutableString *prompt = [NSMutableString stringWithString:conversationPrompt];
        if (text.length > 0) {
            [prompt insertString:[NSString stringWithFormat:@"\n\n文档上下文：\n---\n%@\n---", text]
                          atIndex:0];
        }
        userPrompt = prompt;
    } else if ([taskType isEqualToString:@"calc_formula"]) {
        NSString *cellAddress = [context[@"cellAddress"] isKindOfClass:[NSString class]]
            ? [context[@"cellAddress"] stringByTrimmingCharactersInSet:
                [NSCharacterSet whitespaceAndNewlineCharacterSet]] : @"";
        NSMutableString *sys = [NSMutableString stringWithString:
            @"你是 Excel/Calc 公式生成助手。根据用户用自然语言描述的公式需求，生成对应的电子表格函数公式。\n"
             "要求：\n"
             "1. 只返回公式本身（如 =AVERAGE(A1:A10)），不要包含任何解释或额外内容\n"
             "2. 公式必须以 = 开头\n"
             "3. 注意单元格引用语法，非中文函数的 region 使用英文函数名\n"
             "4. 如果用户指定了筛选条件（如「大于 10」），请确保公式语法正确\n"];
        if (cellAddress.length > 0) {
            [sys appendFormat:@"\n当前选中单元格：%@，注意相对引用。", cellAddress];
        }
        [sys appendString:
            @"\n\n示例：\n"
             "用户：计算 A1 到 A10 的平均值\n"
             "公式：=AVERAGE(A1:A10)\n"
             "用户：计算 B 列的和\n"
             "公式：=SUM(B:B)"];
        systemPrompt = sys;
        userPrompt = text;
    } else if ([taskType isEqualToString:@"calc_data_analysis"]) {
        NSString *cellRange = [context[@"cellRange"] isKindOfClass:[NSString class]]
            ? [context[@"cellRange"] stringByTrimmingCharactersInSet:
                [NSCharacterSet whitespaceAndNewlineCharacterSet]] : @"";
        NSString *cellData = [context[@"cellData"] isKindOfClass:[NSString class]]
            ? context[@"cellData"] : @"";
        systemPrompt =
            @"你是电子表格数据分析助手。根据用户提供的表格数据和问题，进行数据分析。\n"
             "数据只作为分析参考，不要输出 JSON，直接用中文给出分析结论。\n"
             "分析内容包括（根据数据情况选择性提供）：\n"
             "- 数据概览：总行数、列数、关键字段\n"
             "- 统计摘要：合计、平均值、最大值、最小值（针对数值列）\n"
             "- 数据分布：是否有异常值、空值、重复\n"
             "- 业务洞察：基于数据内容的发现和建议\n"
             "- 回答用户的具体问题\n\n"
             "格式要求：\n"
             "- 用中文，简明扼要\n"
             "- 重要数据用数字突出\n"
             "- 不加 Markdown 代码块\n";
        userPrompt = [NSString stringWithFormat:
            @"选中数据（范围：%@）：\n%@\n\n用户问题：%@",
            cellRange.length > 0 ? cellRange : @"未知",
            cellData.length > 0 ? cellData : @"（无数据）",
            text];
    } else if ([taskType isEqualToString:@"calc_cond_format"]) {
        NSString *cellRange = [context[@"cellRange"] isKindOfClass:[NSString class]]
            ? [context[@"cellRange"] stringByTrimmingCharactersInSet:
                [NSCharacterSet whitespaceAndNewlineCharacterSet]] : @"";
        NSString *cellData = [context[@"cellData"] isKindOfClass:[NSString class]]
            ? context[@"cellData"] : @"";
        systemPrompt = [NSString stringWithFormat:
            @"你是 Excel/Calc 条件格式助手。根据选中数据和用户需求，只返回 JSON（不要 Markdown 代码块）。\n"
             "选中范围：%@\n选中数据：\n%@\n"
             "JSON 字段：conditionType(greater|less|equal|between|top_n|bottom_n|above_average|below_average|duplicate|unique|contains_text|formula|clear),"
             "value,value2,range,format{backgroundColor,fontColor,fontBold,fontItalic},description。\n"
             "clear 表示清除条件/直接格式。颜色用 #RRGGBB。",
            cellRange.length > 0 ? cellRange : @"未知",
            cellData.length > 0 ? cellData : @"（无数据）"];
        userPrompt = text;
    } else if ([taskType isEqualToString:@"calc_data_process"]) {
        NSString *cellRange = [context[@"cellRange"] isKindOfClass:[NSString class]]
            ? [context[@"cellRange"] stringByTrimmingCharactersInSet:
                [NSCharacterSet whitespaceAndNewlineCharacterSet]] : @"";
        NSString *cellData = [context[@"cellData"] isKindOfClass:[NSString class]]
            ? context[@"cellData"] : @"";
        systemPrompt = [NSString stringWithFormat:
            @"你是电子表格数据处理专家。只返回 JSON："
             "{\"description\":\"...\",\"actions\":[{\"type\":\"set_formula|set_value|sort|filter|clear_formatting|merge_cells|bold|calculate\","
             "\"range\":\"A1:C10\",\"value\":\"...\",\"ascending\":true}]}。\n"
             "选中范围：%@\n数据样本：\n%@\n不要 Markdown 代码块。",
            cellRange.length > 0 ? cellRange : @"未知",
            cellData.length > 0 ? cellData : @"（无数据）"];
        userPrompt = text;
    } else if ([taskType isEqualToString:@"calc_chart"]) {
        NSString *cellRange = [context[@"cellRange"] isKindOfClass:[NSString class]]
            ? [context[@"cellRange"] stringByTrimmingCharactersInSet:
                [NSCharacterSet whitespaceAndNewlineCharacterSet]] : @"";
        NSString *cellData = [context[@"cellData"] isKindOfClass:[NSString class]]
            ? context[@"cellData"] : @"";
        systemPrompt = [NSString stringWithFormat:
            @"你是 Calc 图表专家。只返回 JSON："
             "{\"preprocess\":[],\"chart\":{\"dataRange\":\"$Sheet1.$A$1:$B$10\","
             "\"chartType\":\"pie|bar|column|line\",\"title\":\"...\"}}。\n"
             "选中范围：%@\n数据样本：\n%@\n不要多余解释。",
            cellRange.length > 0 ? cellRange : @"未知",
            cellData.length > 0 ? cellData : @"（无数据）"];
        userPrompt = text;
    } else if ([taskType isEqualToString:@"calc_new_table"]) {
        systemPrompt =
            @"你是电子表格数据生成助手。只返回纯 JSON："
             "{\"columns\":[\"列1\",\"列2\"],\"data\":[[\"a\",1],[\"b\",2]]}。"
             "至少 8 行，不要 Markdown 代码块。";
        userPrompt = text.length > 0 ? text : @"生成一份示例数据表";
    } else if ([taskType isEqualToString:@"polish"]) {
        NSString *style = context[@"polishStyle"] ?: @"quick";
        NSDictionary *styles = @{
            @"formal": @[@"更正式、更书面化，使用规范用语，避免口语表达", @"更正式"],
            @"lively": @[@"更活泼生动，语气轻松有活力，增强感染力", @"更活泼"],
            @"party_govt": @[@"党政公文风格，用语规范严谨，符合党政机关行文习惯", @"党政风"],
            @"colloquial": @[@"更口语化，贴近日常交流，自然亲切", @"口语化"],
            @"academic": @[@"更学术化，用词严谨准确，逻辑清晰，符合学术写作规范", @"更学术"],
            @"internet": @[@"网络话术风格，生动有趣，适当使用网络流行表达", @"网络话术"],
            @"quick": @[@"快速润色，修正语病、提升流畅度，保持原意", @"快速润色"],
        };
        NSArray *styleData = styles[style] ?: styles[@"quick"];
        systemPrompt = [NSString stringWithFormat:
            @"你是中文文案润色专家，请将用户提供的文案润色得%@。只返回润色后的全文。",
            styleData[0]];
        userPrompt = [NSString stringWithFormat:@"请将以下文案润色成%@风格：\n\n---\n%@\n---",
                       styleData[1], text];
    } else if ([taskType isEqualToString:@"translate"]) {
        NSString *source = context[@"sourceLang"] ?: @"auto";
        NSString *target = context[@"targetLang"] ?: @"zh";
        NSDictionary *labels = @{
            @"zh": @"中文", @"en": @"英文", @"ja": @"日文", @"ko": @"韩文",
            @"fr": @"法文", @"de": @"德文", @"es": @"西班牙文", @"ru": @"俄文",
        };
        NSString *targetLabel = labels[target] ?: @"目标语言";
        if ([source isEqualToString:@"auto"]) {
            systemPrompt = [NSString stringWithFormat:
                @"你是专业翻译，请自动识别用户提供的文本语言，并将其翻译成%@，自然流畅、准确传达原意。只返回译文。",
                targetLabel];
        } else {
            NSString *sourceLabel = labels[source] ?: @"指定语言";
            systemPrompt = [NSString stringWithFormat:
                @"你是专业翻译，请将用户提供的%@文本翻译成%@，自然流畅、准确传达原意。只返回译文。",
                sourceLabel, targetLabel];
        }
        userPrompt = [NSString stringWithFormat:@"请将以下文本翻译成%@：\n\n---\n%@\n---",
                       targetLabel, text];
    } else if ([taskType isEqualToString:@"expand"]) {
        systemPrompt = @"你是中文文案扩写专家，请将用户提供的文本扩展得更详细丰富，增加细节、例证和论述。只返回扩写后的全文。";
        userPrompt = [self promptWithText:text prefix:@"请将以下内容扩展得更详细丰富：" context:context];
    } else if ([taskType isEqualToString:@"condense"]) {
        systemPrompt = @"你是中文文案缩写专家，请压缩用户提供的文本，保留关键信息，去除冗余，缩减至原长度的一半左右。只返回缩写后的全文。";
        userPrompt = [self promptWithText:text prefix:@"请压缩以下文本，保留关键信息：" context:context];
    } else if ([taskType isEqualToString:@"rewrite"]) {
        systemPrompt = @"You are a versatile Chinese writer. Rewrite in a fresh way while preserving original meaning.";
        userPrompt = [self promptWithText:text prefix:@"请用不同的表达方式和句式重写以下内容，保持原意不变：" context:context];
    } else if ([taskType isEqualToString:@"continue"]) {
        systemPrompt = @"You are a creative Chinese writer. Continue naturally in the same style and tone. Return only the continuation.";
        userPrompt = [NSString stringWithFormat:@"请自然流畅地接续以下文本，保持一致的风格和语气：\n\n---\n%@\n---", text];
    } else if ([taskType isEqualToString:@"summarize"]) {
        systemPrompt = @"You are a concise summarizer. Extract key points precisely. Return only the summary.";
        userPrompt = [NSString stringWithFormat:@"请用简洁的语言概括以下内容的核心要点：\n\n---\n%@\n---", text];
    } else if ([taskType isEqualToString:@"outline"]) {
        NSString *typeKey = [context[@"outlineType"] isKindOfClass:[NSString class]]
            ? context[@"outlineType"] : @"general";
        NSDictionary *typeLabels = @{
            @"paper": @"学术论文",
            @"report": @"工作报告",
            @"speech": @"演讲稿",
            @"event": @"活动策划",
            @"general": @"通用文档",
        };
        NSString *typeLabel = typeLabels[typeKey] ?: @"通用文档";
        NSString *requirement = [context[@"requirement"] isKindOfClass:[NSString class]]
            ? [context[@"requirement"] stringByTrimmingCharactersInSet:
                [NSCharacterSet whitespaceAndNewlineCharacterSet]] : @"";
        systemPrompt =
            @"你是专业的大纲生成助手。请根据用户提供的文档类型、参考内容和补充说明，"
             "生成一份结构清晰、层次分明的大纲。\n\n"
             "要求：\n"
             "1. 使用中文编号：一级用「一、二、三…」，二级用「1. 2. 3.」，三级用「(1) (2) (3)」\n"
             "2. 每个一级标题下给出必要的二级要点，三级按需展开\n"
             "3. 标题简洁明确，要点可附一句简要说明\n"
             "4. 覆盖该类型文档的完整结构（如论文含摘要/引言/方法/结果/结论）\n"
             "5. 只输出大纲本身，不要输出前言、解释或额外说明";
        NSMutableString *prompt = [NSMutableString stringWithFormat:@"请生成一份【%@】大纲。", typeLabel];
        if (text.length > 0) {
            [prompt appendFormat:@"\n\n参考内容：\n%@\n", text];
        }
        if (requirement.length > 0) {
            [prompt appendFormat:@"\n补充说明：%@\n", requirement];
        }
        [prompt appendString:@"\n请直接输出大纲。"];
        userPrompt = prompt;
    } else if ([taskType isEqualToString:@"article_generate"]) {
        NSString *templateKey = [context[@"template"] isKindOfClass:[NSString class]]
            ? context[@"template"] : @"";
        NSDictionary *tpl = [self articleTemplateForKey:templateKey];
        if (tpl == nil) {
            if (error != NULL) {
                *error = [NSError errorWithDomain:@"com.xunlong.xloffice.ai"
                                             code:2
                                         userInfo:@{NSLocalizedDescriptionKey: @"Unknown article template"}];
            }
            return nil;
        }
        NSArray *values = [context[@"variables"] isKindOfClass:[NSArray class]]
            ? context[@"variables"] : @[];
        NSArray *hints = tpl[@"hints"];
        NSMutableString *prompt =
            [NSMutableString stringWithString:tpl[@"promptTemplate"]];
        for (NSUInteger i = 0; i < hints.count; i++) {
            NSString *placeholder =
                [NSString stringWithFormat:@"{变量%lu}", (unsigned long)(i + 1)];
            NSString *value = @"";
            if (i < values.count && [values[i] isKindOfClass:[NSString class]]) {
                value = [values[i] stringByTrimmingCharactersInSet:
                    [NSCharacterSet whitespaceAndNewlineCharacterSet]];
            }
            if (value.length == 0) {
                value = hints[i];
            }
            [prompt replaceOccurrencesOfString:placeholder
                                    withString:value
                                       options:0
                                         range:NSMakeRange(0, prompt.length)];
        }
        systemPrompt = [NSString stringWithFormat:
            @"你是中文文案写作专家，请根据用户提供的要素撰写一份规范、得体的%@。"
             "只输出正文内容，不要输出解释或标题前缀。",
            tpl[@"subTypeLabel"]];
        userPrompt = prompt;
    } else if ([taskType isEqualToString:@"create_document"]) {
        NSString *docType = [context[@"docType"] isKindOfClass:[NSString class]]
            ? context[@"docType"] : @"writer";
        NSString *pageCount = [context[@"pageCount"] isKindOfClass:[NSString class]]
            ? [context[@"pageCount"] stringByTrimmingCharactersInSet:
                [NSCharacterSet whitespaceAndNewlineCharacterSet]] : @"";
        NSString *audience = [context[@"audience"] isKindOfClass:[NSString class]]
            ? [context[@"audience"] stringByTrimmingCharactersInSet:
                [NSCharacterSet whitespaceAndNewlineCharacterSet]] : @"";
        NSMutableString *sys = [NSMutableString stringWithString:
            @"你是办公文档写作助手。根据用户给出的主题生成一份结构完整、内容翔实的文档。\n"
             "只返回文档正文内容，不要包含任何解释、前言或 Markdown 代码块标记。\n"];
        if ([docType isEqualToString:@"calc"]) {
            [sys appendString:@"用户需要的是表格类文档：用清晰的表格(如 Markdown 表格)组织数据。\n"];
        } else if ([docType isEqualToString:@"impress"]) {
            [sys appendString:@"用户需要的是演示文稿：按幻灯片结构组织内容，用「## 第N页」分隔每一页。\n"];
        }
        if (pageCount.length > 0) {
            [sys appendFormat:@"文档篇幅控制在约 %@ 页。\n", pageCount];
        }
        if (audience.length > 0) {
            [sys appendFormat:@"目标读者：%@。\n", audience];
        }
        systemPrompt = sys;
        userPrompt = [NSString stringWithFormat:@"文档主题：%@", text];
    } else if ([taskType isEqualToString:@"typeset"]) {
        NSString *typesetType = [context[@"typesetType"] isKindOfClass:[NSString class]]
            ? context[@"typesetType"] : @"general";
        NSString *typesetVersion = [context[@"typesetVersion"] isKindOfClass:[NSString class]]
            ? context[@"typesetVersion"] : @"v1";
        BOOL paragraphMode = [context[@"paragraphMode"] boolValue];
        NSDictionary *prompts = nil;
        if (paragraphMode) {
            prompts = [self typesetParagraphPromptsForType:typesetType paragraphText:text];
        } else if ([typesetVersion isEqualToString:@"v2"]) {
            prompts = [self typesetV2PromptsForType:typesetType fullText:text];
        } else {
            prompts = [self typesetPromptsForType:typesetType fullText:text];
        }
        systemPrompt = prompts[@"system"];
        userPrompt = prompts[@"user"];
    } else if ([taskType isEqualToString:@"impress_outline"]) {
        NSString *inputType = [context[@"inputType"] isKindOfClass:[NSString class]]
            ? context[@"inputType"] : @"quick";
        NSString *userInput = [context[@"userInput"] isKindOfClass:[NSString class]]
            ? [context[@"userInput"] stringByTrimmingCharactersInSet:
                [NSCharacterSet whitespaceAndNewlineCharacterSet]] : text;
        NSInteger pageRange = [context[@"pageRange"] respondsToSelector:@selector(integerValue)]
            ? [context[@"pageRange"] integerValue] : 10;
        if (pageRange <= 0) {
            pageRange = 10;
        }
        NSString *audience = [context[@"audience"] isKindOfClass:[NSString class]]
            ? context[@"audience"] : @"大众";
        NSString *style = [context[@"style"] isKindOfClass:[NSString class]]
            ? context[@"style"] : @"通用";
        NSDictionary *prompts = [self impressOutlinePromptsForInputType:inputType
                                                             userInput:userInput
                                                             pageRange:pageRange
                                                              audience:audience
                                                                 style:style];
        systemPrompt = prompts[@"system"];
        userPrompt = prompts[@"user"];
        if (userPrompt.length == 0) {
            if (error != NULL) {
                *error = [NSError errorWithDomain:@"com.xunlong.xloffice.ai"
                                             code:1
                                         userInfo:@{NSLocalizedDescriptionKey: @"Request text is empty"}];
            }
            return nil;
        }
    } else if ([taskType isEqualToString:@"impress_generate"]) {
        NSDictionary *prompts = [self impressGeneratePromptsForContext:context];
        systemPrompt = prompts[@"system"];
        userPrompt = prompts[@"user"];
    } else if ([taskType isEqualToString:@"text_extract"]) {
        systemPrompt =
            @"你是文字识别专家。请识别并提取图片中的所有文字，保持原始排版和段落结构，只返回提取的文字内容，不要添加解释。";
        userPrompt = @"请识别这张图片中的所有文字并提取出来：";
    } else if (error != NULL) {
        *error = [NSError errorWithDomain:@"com.xunlong.xloffice.ai"
                                     code:2
                                 userInfo:@{NSLocalizedDescriptionKey: @"Unsupported AI taskType"}];
        return nil;
    }

    NSMutableArray *messages = [NSMutableArray arrayWithObject:
        @{@"role": @"system", @"content": systemPrompt ?: @""}];
    NSArray *history = [payload[@"history"] isKindOfClass:[NSArray class]]
        ? payload[@"history"] : @[];
    for (NSDictionary *item in history) {
        if (![item isKindOfClass:[NSDictionary class]]) {
            continue;
        }
        NSString *role = [item[@"role"] isKindOfClass:[NSString class]] ? item[@"role"] : @"";
        NSString *content = [item[@"content"] isKindOfClass:[NSString class]]
            ? item[@"content"] : @"";
        if (([role isEqualToString:@"user"] || [role isEqualToString:@"assistant"])
            && content.length > 0) {
            [messages addObject:@{@"role": role, @"content": content}];
        }
    }
    NSArray *images = [payload[@"images"] isKindOfClass:[NSArray class]]
        ? payload[@"images"] : nil;
    NSMutableArray *imageParts = nil;
    for (id rawImage in images) {
        if (![rawImage isKindOfClass:[NSString class]]
            || ((NSString *)rawImage).length == 0) {
            continue;
        }
        if (imageParts == nil) {
            imageParts = [[NSMutableArray alloc] init];
        }
        [imageParts addObject:@{
            @"type": @"image_url",
            @"image_url": @{
                @"url": [@"data:image/png;base64," stringByAppendingString:rawImage],
            },
        }];
    }
    id userContent = @{@"role": @"user", @"content": userPrompt ?: @""};
    if (imageParts != nil) {
        NSMutableArray *contentParts = [[NSMutableArray alloc] init];
        [contentParts addObject:@{@"type": @"text", @"text": userPrompt ?: @""}];
        [contentParts addObjectsFromArray:imageParts];
        userContent = @{@"role": @"user", @"content": contentParts};
    }
    [messages addObject:userContent];
    return messages;
}

- (NSArray<NSString *> *)typesetSectionKeysForType:(NSString *)typesetType {
    if ([typesetType isEqualToString:@"paper"]) {
        return @[@"title", @"abstract", @"keywords", @"introduction", @"heading1", @"heading2",
                 @"heading3", @"body", @"conclusion_body", @"ack_body"];
    }
    if ([typesetType isEqualToString:@"gov"]) {
        return @[@"recipient", @"body", @"signature_org", @"signature_date", @"notes"];
    }
    if ([typesetType isEqualToString:@"contract"]) {
        return @[@"title", @"contract_number", @"party_a", @"party_a_id", @"party_b", @"party_b_id",
                 @"preamble", @"clause_title", @"clause_subtitle", @"clause_body"];
    }
    return @[@"title", @"heading1", @"heading2", @"heading3", @"body"];
}

- (NSDictionary *)impressOutlinePromptsForInputType:(NSString *)inputType
                                         userInput:(NSString *)userInput
                                         pageRange:(NSInteger)pageRange
                                          audience:(NSString *)audience
                                             style:(NSString *)style {
    NSString *inputLabel = @"大纲";
    if ([inputType isEqualToString:@"quick"]) {
        inputLabel = @"主题";
    } else if ([inputType isEqualToString:@"document"]) {
        inputLabel = @"文档内容";
    }
    NSString *systemPrompt = [NSString stringWithFormat:
        @"你是一个专业PPT大纲生成助手。根据用户提供的%@、页数范围、听众类型和风格，生成结构化JSON大纲。\n\n"
         "输出格式要求（严格JSON，不要额外文字）：\n"
         "{\n"
         "  \"slides\": [\n"
         "    {\"page\": 1, \"type\": \"cover\", \"title\": \"标题\", \"content\": \"副标题/附加信息\"},\n"
         "    {\"page\": 2, \"type\": \"toc\", \"title\": \"目录\", \"content\": \"1. XX\\n2. XX\\n3. XX\"},\n"
         "    {\"page\": 3, \"type\": \"section_divider\", \"title\": \"第一章标题\", \"content\": \"本章概述（1-2句）\"},\n"
         "    {\"page\": 4, \"type\": \"section\", \"title\": \"章节标题\", \"content\": \"• 要点1\\n• 要点2\"},\n"
         "    {\"page\": \"N\", \"type\": \"end\", \"title\": \"谢谢\", \"content\": \"结束语\"}\n"
         "  ]\n"
         "}\n\n"
         "type枚举：cover(封面)、toc(目录)、section_divider(章节分割页)、section(章节正文)、end(结尾)\n"
         "每章结构：先一条 section_divider（title=章名，content=本章概述），再一条或多条 section（正文页）\n"
         "title: 每页标题（简洁有力）\n"
         "content: 内容要点（Markdown格式，用•开头的列表）\n"
         "页数不超过%ld页\n"
         "风格：%@\n"
         "听众：%@",
        inputLabel, (long)pageRange, style ?: @"通用", audience ?: @"大众"];
    NSString *userPrompt = nil;
    if ([inputType isEqualToString:@"quick"]) {
        userPrompt = [NSString stringWithFormat:@"请为主题生成PPT大纲：\n%@", userInput ?: @""];
    } else if ([inputType isEqualToString:@"document"]) {
        userPrompt = [NSString stringWithFormat:@"请根据以下文档内容生成PPT大纲：\n%@", userInput ?: @""];
    } else {
        userPrompt = [NSString stringWithFormat:@"请根据以下大纲整理为PPT结构：\n%@", userInput ?: @""];
    }
    return @{@"system": systemPrompt, @"user": userPrompt};
}

- (NSDictionary *)impressGeneratePromptsForContext:(NSDictionary *)context {
    NSString *templateId = [context[@"templateId"] isKindOfClass:[NSString class]]
        ? context[@"templateId"] : @"";
    NSInteger batchIndex = [context[@"batchIndex"] respondsToSelector:@selector(integerValue)]
        ? [context[@"batchIndex"] integerValue] : 0;
    NSInteger totalBatches = [context[@"totalBatches"] respondsToSelector:@selector(integerValue)]
        ? [context[@"totalBatches"] integerValue] : 1;
    if (totalBatches <= 0) {
        totalBatches = 1;
    }
    id outlineSlides = context[@"outlineSlides"];
    id batchSlides = context[@"batchSlides"];
    NSString *outlineJson = @"";
    NSString *batchJson = @"";
    if ([NSJSONSerialization isValidJSONObject:outlineSlides]) {
        NSData *data = [NSJSONSerialization dataWithJSONObject:outlineSlides options:NSJSONWritingPrettyPrinted error:nil];
        if (data) {
            outlineJson = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding] ?: @"";
        }
    } else if ([outlineSlides isKindOfClass:[NSString class]]) {
        outlineJson = outlineSlides;
    }
    if ([NSJSONSerialization isValidJSONObject:batchSlides]) {
        NSData *data = [NSJSONSerialization dataWithJSONObject:batchSlides options:NSJSONWritingPrettyPrinted error:nil];
        if (data) {
            batchJson = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding] ?: @"";
        }
    } else if ([batchSlides isKindOfClass:[NSString class]]) {
        batchJson = batchSlides;
    }
    NSString *systemPrompt =
        @"你是专业PPT内容生成助手。根据用户大纲，生成当前批次的详细内容。\n\n"
         "输出格式（严格JSON，不要额外文字，不要代码块）：\n"
         "{\n"
         "  \"slides\": [\n"
         "    {\n"
         "      \"page\": 1,\n"
         "      \"type\": \"cover|toc|section_divider|section|end\",\n"
         "      \"title\": \"页面标题\",\n"
         "      \"subtitle\": \"副标题字符串\",\n"
         "      \"content_points\": [\"要点1\", \"要点2\"],\n"
         "      \"detailed_content\": [\"详细内容1\", \"详细内容2\"]\n"
         "    }\n"
         "  ]\n"
         "}\n\n"
         "要求：\n"
         "1. slides 数组长度必须恰好为 1，只输出本批次那一页\n"
         "2. subtitle 必须是字符串；无副标题时写 \"subtitle\": \"\"，禁止 \"subtitle\":, 或省略值\n"
         "3. content_points 数量必须与模板该页要点槽位一致（2/3/4 等），优先 3 或 4 个\n"
         "4. detailed_content 与 content_points 一一对应，每个要点展开1-3句详细说明\n"
         "5. 内容要丰富、专业、有深度，不要笼统空泛\n"
         "6. cover页只输出title+subtitle，content_points 和 detailed_content 用 []\n"
         "7. toc页的content_points列出目录项，detailed_content 用 []\n"
         "8. end页的content_points为致谢信息，subtitle 可为联系方式\n"
         "9. section页必须输出 section_title 与 title 相同\n"
         "10. 每个要点以换行符\\n分隔（在 JSON 字符串中用 \\n 表示）";
    NSString *userPrompt = [NSString stringWithFormat:
        @"当前批次 %ld/%ld\n模板：%@\n\n完整大纲（仅供参考，不要为其他页生成内容）：\n%@\n\n本批次必须生成的页（只输出这一页）：\n%@",
        (long)(batchIndex + 1), (long)totalBatches, templateId, outlineJson, batchJson];
    return @{@"system": systemPrompt, @"user": userPrompt};
}

- (NSDictionary *)typesetV2PromptsForType:(NSString *)typesetType fullText:(NSString *)fullText {
    NSString *text = [fullText stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    NSArray<NSString *> *keys = [self typesetSectionKeysForType:typesetType ?: @"general"];
    NSMutableString *sectionList = [NSMutableString string];
    for (NSString *key in keys) {
        [sectionList appendFormat:@"%@, ", key];
    }
    NSString *systemPrompt =
        @"你是专业的文档排版助手。请从用户提供的原始文档中提取内容，"
         "将原文各部分填入模板对应的分区中，以 JSON 格式返回。\n"
         "重要原则：保持原文内容不变，不要改写、扩写、缩写或润色原文。"
         "仅进行结构化拆分——把原文各部分分配到对应的模板分区。\n"
         "返回格式：{\"sections\": {\"key\": \"content\", ...}}，不要包含 markdown 代码块。";
    NSString *userPrompt = [NSString stringWithFormat:
        @"请将以下原始文档内容按模板分区进行结构化拆分，返回 JSON。"
         "不要修改原文内容，仅将各部分填入对应分区。\n\n"
         "注意：原文中的图片已标记为[图1]、[图2]等占位符，请保留这些标记。\n\n"
         "分区列表：%@\n\n原始文档内容：\n---\n%@\n---\n\n请直接返回 JSON。",
        sectionList, text];
    return @{@"system": systemPrompt, @"user": userPrompt};
}

- (NSDictionary *)typesetParagraphPromptsForType:(NSString *)typesetType paragraphText:(NSString *)paragraphText {
    NSArray<NSString *> *keys = [self typesetSectionKeysForType:typesetType ?: @"general"];
    NSMutableString *sectionList = [NSMutableString string];
    for (NSString *key in keys) {
        [sectionList appendFormat:@"%@, ", key];
    }
    NSString *systemPrompt =
        @"你是文档段落分类助手。请判断每个段落属于模板中的哪个分区。"
         "只返回 JSON 数组：[{\"paraIndex\":0,\"section\":\"body\"}, ...]，不要 markdown 代码块。";
    NSString *userPrompt = [NSString stringWithFormat:
        @"请为每个段落判断它属于模板中的哪个分区。\n\n可用分区：%@\n\n段落内容：\n---\n%@\n---",
        sectionList, paragraphText ?: @""];
    return @{@"system": systemPrompt, @"user": userPrompt};
}

- (NSDictionary *)typesetPromptsForType:(NSString *)typesetType fullText:(NSString *)fullText {
    NSString *text = [fullText stringByTrimmingCharactersInSet:
        [NSCharacterSet whitespaceAndNewlineCharacterSet]];
    NSString *systemPrompt = nil;
    NSString *userPrompt = nil;

    if ([typesetType isEqualToString:@"paper"]) {
        systemPrompt =
            @"你是学术论文排版专家。你的任务是将用户提供的论文全文内容按照标准学术论文格式进行排版，并返回完整的 HTML 格式结果。\n\n"
             "排版规范：\n"
             "1. 标题层级：使用 <h1> 作为论文标题，<h2> 作为章节标题，<h3> 作为小节标题\n"
             "2. 摘要：用 <p><strong>摘要：</strong> 包裹摘要内容\n"
             "3. 关键词：用 <p><strong>关键词：</strong> 列出关键词，用顿号分隔\n"
             "4. 正文：用 <p> 包裹段落，段首不缩进\n"
             "5. 图表：用 <table> 制作表格，<caption> 作为表格标题\n"
             "6. 参考文献：用 <ol> 编号列表，每个文献用 <li> 包裹\n"
             "7. 公式：简单公式用 <sub>/<sup>，复杂公式用文本描述\n\n"
             "请只返回排版后的 HTML，不要包含任何其他说明文字或代码块标记。不要使用 CSS 样式，只用 HTML 语义化标签。";
        userPrompt = [NSString stringWithFormat:
            @"请将以下论文内容按照标准学术论文格式排版，返回完整的 HTML：\n\n---\n%@\n---\n\n请直接返回排版后的 HTML，不要包含任何其他说明文字。",
            text];
    } else if ([typesetType isEqualToString:@"gov"]) {
        systemPrompt =
            @"你是党政公文排版专家。你的任务是将用户提供的公文内容按照标准党政公文格式（GB/T 9704-2012）进行排版，并返回完整的 HTML 格式结果。\n\n"
             "排版规范：\n"
             "1. 发文机关标志：用 <div align=\"center\"><h1> 发文机关名称 </h1></div>\n"
             "2. 发文字号：用 <div align=\"center\"><p> ××发〔2026〕×号 </p></div>\n"
             "3. 标题：用 <div align=\"center\"><h2> 公文标题 </h2></div>\n"
             "4. 主送机关：用 <p><strong>×××：</strong></p>，顶格\n"
             "5. 正文：用 <p> 包裹段落，首行不缩进\n"
             "6. 附件说明：用 <p> 附件：1.××× </p>\n"
             "7. 发文机关署名：用 <div align=\"right\"><p> ×××局 </p></div>\n"
             "8. 成文日期：用 <div align=\"right\"><p> 2026年6月18日 </p></div>\n"
             "9. 版记：用分隔线 <hr>，抄送用 <p>\n\n"
             "请只返回排版后的 HTML，不要使用 CSS，只用 HTML 属性（align, font size）和语义化标签。";
        userPrompt = [NSString stringWithFormat:
            @"请按照标准党政公文格式排版以下内容，返回完整的 HTML：\n\n---\n%@\n---\n\n请直接返回排版后的 HTML。",
            text];
    } else if ([typesetType isEqualToString:@"contract"]) {
        systemPrompt =
            @"你是合同协议排版专家。你的任务是将用户提供的合同内容按照标准合同格式进行排版，并返回完整的 HTML 格式结果。\n\n"
             "排版规范：\n"
             "1. 合同标题：用 <h1> 合同名称 </h1>，居中\n"
             "2. 合同编号：用 <p> 合同编号：××× </p>\n"
             "3. 甲乙双方：用 <p> 甲方：××× </p> 和 <p> 乙方：××× </p>\n"
             "4. 日期地点：用 <p> 签订日期：×××年××月××日 </p> 和 <p> 签订地点：××× </p>\n"
             "5. 条款标题：用 <h3> 第一条 ××× </h3>，或用 <ol> 编号列表\n"
             "6. 条款内容：用 <p> 包裹每一条款内容\n"
             "7. 子项：用 <ul> 或 <ol> 列表\n"
             "8. 签名区：用 <hr> 分隔，然后用 <div align=\"right\"><p> 甲方（签字）：_________ </p></div>\n\n"
             "请只返回排版后的 HTML，不使用 CSS。";
        userPrompt = [NSString stringWithFormat:
            @"请按照合同协议标准格式排版以下内容，返回完整的 HTML：\n\n---\n%@\n---\n\n请直接返回排版后的 HTML。",
            text];
    } else {
        systemPrompt =
            @"你是通用文档排版专家。你的任务是将用户提供的文档内容进行清晰的格式化排版，并返回完整的 HTML 格式结果。\n\n"
             "排版原则：\n"
             "1. 自动识别标题层级，将短小且独立的行设为 <h2> 或 <h3>\n"
             "2. 正常段落用 <p>\n"
             "3. 列表项用 <ul> 或 <ol>\n"
             "4. 表格用 <table>\n"
             "5. 强调内容用 <strong> 或 <em>\n"
             "6. 保持原有内容顺序，不增删内容\n"
             "7. 使文档结构清晰、易于阅读\n\n"
             "请只返回排版后的 HTML，不使用 CSS。";
        userPrompt = [NSString stringWithFormat:
            @"请对以下内容进行清晰的格式化排版，返回完整的 HTML：\n\n---\n%@\n---\n\n请直接返回排版后的 HTML。",
            text];
    }

    return @{@"system": systemPrompt ?: @"", @"user": userPrompt ?: @""};
}

- (NSString *)promptWithText:(NSString *)text
                       prefix:(NSString *)prefix
                      context:(NSDictionary *)context {
    NSMutableString *prompt = [NSMutableString stringWithFormat:@"%@\n\n---\n%@\n---", prefix, text];
    NSString *requirement = [context[@"requirement"] isKindOfClass:[NSString class]]
        ? [context[@"requirement"] stringByTrimmingCharactersInSet:
            [NSCharacterSet whitespaceAndNewlineCharacterSet]] : @"";
    if (requirement.length > 0) {
        [prompt appendFormat:@"\n\n额外要求：%@", requirement];
    }
    return prompt;
}

- (NSDictionary *)articleTemplateForKey:(NSString *)key {
    static NSDictionary *templates = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        templates = @{
            @"general_notice": @{
                @"subTypeLabel": @"通用通知",
                @"promptTemplate":
                    @"请撰写一则通知，通知的主要内容为{变量1}，通知时间是{变量2}。",
                @"hints": @[@"公司今晚聚餐", @"2025年01月01日"],
            },
            @"meeting_notice": @{
                @"subTypeLabel": @"会议通知",
                @"promptTemplate":
                    @"请撰写一则会议通知，通知的主要内容为{变量1}，会议时间是{变量2}，参会人员包括{变量3}。",
                @"hints": @[@"2025年研发计划", @"2025年01月01日 17:00", @"软件研发人员"],
            },
            @"holiday_notice": @{
                @"subTypeLabel": @"放假通知",
                @"promptTemplate":
                    @"请撰写一则放假通知，假期名称为{变量1}，接收方是{变量2}，发送方是{变量3}，放假时间是{变量4}。",
                @"hints": @[@"元旦节", @"全体员工", @"橙子云计算（深圳）有限公司", @"2025年01月01日"],
            },
            @"interview_notice": @{
                @"subTypeLabel": @"面试通知",
                @"promptTemplate":
                    @"请撰写一则面试通知，面试人员是{变量1}，面试时间为{变量2}，面试地点是{变量3}，面试单位为{变量4}。",
                @"hints": @[@"小王", @"2025年01月01日 17:00", @"名优大厦A座1区101", @"橙子云计算"],
            },
            @"activity_notice": @{
                @"subTypeLabel": @"活动通知",
                @"promptTemplate":
                    @"请撰写一则活动通知，活动主题是{变量1}，活动时间是{变量2}，活动地点为{变量3}。",
                @"hints": @[@"员工羽毛球大赛", @"2025年01月01日 17:00", @"羽毛球馆"],
            },
            @"training_notice": @{
                @"subTypeLabel": @"培训通知",
                @"promptTemplate":
                    @"请撰写一则培训通知，培训主要内容为{变量1}，培训人员是{变量2}，培训日期是{变量3}。",
                @"hints": @[@"如何使用AI Office提效", @"全体员工", @"2025年01月01日 17:00"],
            },
            @"general_apply": @{
                @"subTypeLabel": @"通用申请",
                @"promptTemplate":
                    @"请撰写一则申请，申请人是{变量1}，申请事项是{变量2}，申请时间是{变量3}。",
                @"hints": @[@"小王", @"外出参加会议", @"2025年01月01日"],
            },
            @"leave_apply": @{
                @"subTypeLabel": @"请假申请",
                @"promptTemplate":
                    @"请撰写一则请假条，请假人为{变量1}，请假原因是{变量2}，请假天数为{变量3}，请假起始日期是{变量4}。",
                @"hints": @[@"小王", @"身体不适", @"3天", @"2025年01月01日"],
            },
            @"resign_apply": @{
                @"subTypeLabel": @"离职申请",
                @"promptTemplate":
                    @"请撰写一则离职申请，申请人是{变量1}，离职原因是{变量2}，离职时间是{变量3}。",
                @"hints": @[@"小王", @"身体长期不适", @"2025年01月01日"],
            },
            @"general_cert": @{
                @"subTypeLabel": @"通用证明",
                @"promptTemplate":
                    @"请撰写一则证明，被证明人是{变量1}，证明主要内容是{变量2}，证明单位为{变量3}，证明时间是{变量4}。",
                @"hints": @[@"小王", @"小王是公司的员工", @"橙子云计算（深圳）有限公司", @"2025年01月01日"],
            },
            @"work_cert": @{
                @"subTypeLabel": @"工作证明",
                @"promptTemplate":
                    @"请撰写一则工作证明，被证明人是{变量1}，工作时间是{变量2}，工作单位是{变量3}，工作岗位是{变量4}。",
                @"hints": @[@"小王", @"2020年01月01日至2025年01月01日", @"橙子云计算（深圳）有限公司", @"软件研发工程师"],
            },
            @"income_cert": @{
                @"subTypeLabel": @"收入证明",
                @"promptTemplate":
                    @"请撰写一则收入证明，被证明人是{变量1}，收入为{变量2}，工作单位是{变量3}，工作岗位是{变量4}。",
                @"hints": @[@"小王", @"年收入10万元", @"橙子云计算（深圳）有限公司", @"软件研发工程师"],
            },
            @"resign_cert": @{
                @"subTypeLabel": @"离职证明",
                @"promptTemplate":
                    @"请撰写一则离职证明，被证明人是{变量1}，离职原因为{变量2}，离职时间是{变量3}，证明单位为{变量4}，证明时间是{变量5}。",
                @"hints": @[@"小王", @"员工个人原因", @"2025年01月01日", @"橙子云计算（深圳）有限公司", @"2025年01月01日"],
            },
            @"xiaohongshu": @{
                @"subTypeLabel": @"小红书种草文",
                @"promptTemplate":
                    @"请撰写一篇小红书种草文，种草对象是{变量1}，目标受众是{变量2}，核心卖点是{变量3}，文章长度{变量4}，使用{变量5}的文案风格，",
                @"hints": @[@"最新复古游戏掌机", @"喜欢游戏机的年轻人", @"畅玩复古游戏", @"500字左右", @"幽默风趣"],
            },
            @"ad_soft": @{
                @"subTypeLabel": @"产品广告软文",
                @"promptTemplate":
                    @"请撰写一篇产品广告软文，产品名称是{变量1}，品牌是{变量2}，核心卖点是{变量3}，目标受众是{变量4}，投放平台是{变量5}，营销节点是{变量6}，文案风格是{变量7}",
                @"hints": @[@"最新复古游戏掌机", @"香橙派", @"畅玩复古游戏", @"爱玩游戏的年轻人", @"微博", @"情人节", @"幽默风趣"],
            },
            @"douyin_script": @{
                @"subTypeLabel": @"抖音视频脚本",
                @"promptTemplate":
                    @"请撰写一篇抖音视频脚本，视频的主题内容是{变量1}，目标受众是{变量2}，视频风格是{变量3}，视频时长是{变量4}",
                @"hints": @[@"旅游攻略", @"旅游爱好者", @"搞笑幽默", @"三分钟左右"],
            },
        };
    });
    return templates[key];
}

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
