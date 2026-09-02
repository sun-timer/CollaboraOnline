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

    AIModelType modelType = AIModelTypeBase;
    id rawType = payload[@"modelType"];
    if ([rawType isKindOfClass:[NSNumber class]]) {
        NSInteger typeValue = ((NSNumber *)rawType).integerValue;
        if (typeValue >= AIModelTypeBase && typeValue <= AIModelTypeVision) {
            modelType = (AIModelType)typeValue;
        }
    }
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
    BOOL isDocumentLevelTask = [taskType isEqualToString:@"outline"]
        || [taskType isEqualToString:@"article_generate"];
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
