// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AIService.h"

#import "AIConfigurationStore.h"
#import "AIRequestSession.h"

@interface AIServiceRequest : NSObject
@property (strong, nonatomic) AIRequestSession *session;
@property (copy, nonatomic) AIServiceEventEmitter emitter;
@property (assign, nonatomic) NSInteger statusCode;
@end

@implementation AIServiceRequest
@end

@interface AIService ()
@property (strong, nonatomic) AIConfigurationStore *configurationStore;
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

    NSError *configurationError = nil;
    AIConfiguration *configuration = [self.configurationStore configurationWithError:&configurationError];
    if (configuration == nil || !self.configurationStore.isConfigured) {
        NSString *message = configurationError.localizedDescription ?: @"AI service is not configured";
        [self emitType:@"ai.error"
              requestId:requestId
           documentSessionId:documentSessionId
                  payload:@{@"code": @"config_missing", @"message": message}
                   emitter:emit];
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

    NSURL *url = [NSURL URLWithString:configuration.endpoint];
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
    [request setValue:[NSString stringWithFormat:@"Bearer %@", configuration.apiKey]
        forHTTPHeaderField:@"Authorization"];

    NSDictionary *body = @{
        @"model": configuration.model,
        @"stream": @YES,
        @"messages": messages,
    };
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
    for (NSString *line in [serviceRequest.session consumePendingLines]) {
        if ([line hasPrefix:@"data:"]) {
            [self handleSSEData:[line substringFromIndex:5] request:serviceRequest];
        }
    }
    if ([serviceRequest.session canEmit]) {
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
    if (text.length == 0 && (!isConversation || conversationPrompt.length == 0)) {
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
    [messages addObject:@{@"role": @"user", @"content": userPrompt ?: @""}];
    return messages;
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

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
