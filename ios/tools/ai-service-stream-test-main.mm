#import <Foundation/Foundation.h>
#import "AIService.h"
#import "AIRequestSession.h"
#import "AIModelConfigStore.h"
#import "AiBackendRouter.h"
#import "ImpressModelJson.h"

@interface AIService (OutlinePromptTest)
- (NSArray *)messagesForPayload:(NSDictionary *)payload error:(NSError **)error;
- (NSUInteger)unicodeCodePointCount:(NSString *)text;
- (NSString *)outlineTemplateForType:(NSString *)outlineType;
- (NSDictionary *)continuationSamplingOverridesForMaxTokens:(NSInteger)configuredMaxTokens;
@end

static void check(BOOL condition, NSString *message) {
    if (!condition) { NSLog(@"FAIL: %@", message); exit(1); }
}
static void runCase(NSArray<NSData *> *chunks, BOOL cancel, NSString *expected) {
    // Deliberately skip service initialization: this tests URLSession callbacks
    // without reading credentials, creating a network task, or loading models.
    AIService *service = [AIService alloc];
    AIRequestSession *session = [[AIRequestSession alloc] initWithRequestId:@"req" documentSessionId:@"doc"];
    id request = [NSClassFromString(@"AIServiceRequest") new];
    [request setValue:session forKey:@"session"];
    NSMutableArray *events = [NSMutableArray array];
    [request setValue:[^(NSString *type, NSString *requestId, NSString *dsid, NSDictionary *payload) {
        [events addObject:@{@"type": type, @"payload": payload}];
    } copy] forKey:@"emitter"];
    NSURLSessionDataTask *task = [[NSURLSession sharedSession] dataTaskWithURL:[NSURL URLWithString:@"http://127.0.0.1/unused"]];
    [service setValue:[NSMutableDictionary dictionaryWithObject:request forKey:task] forKey:@"requestsByTask"];
    [service setValue:[NSMutableDictionary dictionaryWithObject:request forKey:@"req"] forKey:@"requestsById"];
    for (NSData *chunk in chunks) [service URLSession:[NSURLSession sharedSession] dataTask:task didReceiveData:chunk];
    if (cancel) {
        [session cancel];
        [service URLSession:[NSURLSession sharedSession] dataTask:task didReceiveData:
            [@"data: {\"choices\":[{\"delta\":{\"content\":\"late\"}}]}\n\n" dataUsingEncoding:NSUTF8StringEncoding]];
    }
    [service URLSession:[NSURLSession sharedSession] task:task didCompleteWithError:nil];
    NSDate *deadline = [NSDate dateWithTimeIntervalSinceNow:0.05];
    while ([deadline timeIntervalSinceNow] > 0) [[NSRunLoop currentRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.005]];
    NSInteger doneCount = 0;
    for (NSDictionary *event in events) if ([event[@"type"] isEqual:@"ai.done"]) {
        doneCount++;
        check([event[@"payload"][@"fullText"] isEqual:expected], @"full text preserved");
    }
    check(doneCount == (cancel ? 0 : 1), @"exactly one terminal event (none after cancel)");
    check([[service valueForKey:@"requestsById"] count] == 0, @"request cleaned up");
}
static void testImpressOutlinePromptContract(void) {
    AIService *service = [AIService alloc];
    NSError *error = nil;
    NSArray *messages = [service messagesForPayload:@{
        @"taskType": @"impress_outline",
        @"context": @{
            @"inputType": @"quick", @"userInput": @"sample topic",
            @"pageRange": @8, @"audience": @"大众", @"style": @"商务",
        },
    } error:&error];
    check(error == nil && messages.count == 2, @"impress outline creates system/user messages");
    NSString *system = messages.firstObject[@"content"];
    NSString *user = messages.lastObject[@"content"];
    check([system containsString:@"title 和 content 的值必须用英文双引号包住"],
          @"outline prompt forbids unquoted JSON values");
    check([system containsString:@"content 必须是字符串"],
          @"outline prompt requires string content");
    check([user containsString:@"sample topic"], @"outline request includes the supplied topic");
}
static void testLengthFinishReasonCapture(void) {
    AIService *service = [AIService alloc];
    AIRequestSession *session = [[AIRequestSession alloc] initWithRequestId:@"impress-outline-length"
                                                        documentSessionId:@"doc"];
    id request = [NSClassFromString(@"AIServiceRequest") new];
    [request setValue:session forKey:@"session"];
    NSMutableArray *events = [NSMutableArray array];
    [request setValue:[^(NSString *type, NSString *requestId, NSString *dsid, NSDictionary *payload) {
        [events addObject:@{@"type": type, @"payload": payload}];
    } copy] forKey:@"emitter"];
    NSURLSessionDataTask *task = [[NSURLSession sharedSession] dataTaskWithURL:[NSURL URLWithString:@"http://127.0.0.1/unused"]];
    [service setValue:[NSMutableDictionary dictionaryWithObject:request forKey:task] forKey:@"requestsByTask"];
    [service setValue:[NSMutableDictionary dictionaryWithObject:request forKey:@"impress-outline-length"] forKey:@"requestsById"];
    NSString *chunk = @"data: {\"choices\":[{\"delta\":{\"content\":\"partial JSON\"},\"finish_reason\":\"length\"}]}\n\n";
    [service URLSession:[NSURLSession sharedSession] dataTask:task
         didReceiveData:[chunk dataUsingEncoding:NSUTF8StringEncoding]];
    NSDate *earlyDeadline = [NSDate dateWithTimeIntervalSinceNow:0.05];
    while ([earlyDeadline timeIntervalSinceNow] > 0) [[NSRunLoop currentRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.005]];
    NSInteger earlyDoneCount = 0;
    for (NSDictionary *event in events) if ([event[@"type"] isEqual:@"ai.done"]) earlyDoneCount++;
    check(earlyDoneCount == 1, @"finish_reason completes the stream before EOF");
    [service URLSession:[NSURLSession sharedSession] task:task didCompleteWithError:nil];
    NSDate *deadline = [NSDate dateWithTimeIntervalSinceNow:0.05];
    while ([deadline timeIntervalSinceNow] > 0) [[NSRunLoop currentRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.005]];
    NSDictionary *done = nil;
    for (NSDictionary *event in events) if ([event[@"type"] isEqual:@"ai.done"]) done = event[@"payload"];
    check([done[@"finishReason"] isEqual:@"length"], @"length finish reason reaches ai.done payload");
    check([done[@"fullText"] isEqual:@"partial JSON"], @"final frame content is retained before terminal event");
    check([done[@"truncated"] boolValue] && [done[@"warning"] isKindOfClass:NSString.class],
          @"length completion exposes a usable partial result and warning");
    NSInteger doneCount = 0;
    for (NSDictionary *event in events) if ([event[@"type"] isEqual:@"ai.done"]) doneCount++;
    check(doneCount == 1, @"finish_reason frame emits exactly one terminal event");
}

static void testEndpointResolution(void) {
    AIModelConfigStore *store = [AIModelConfigStore alloc]; // no defaults or keychain access
    AIModelConfigForm *form = [[AIModelConfigForm alloc] init];
    form.endpointMode = @"automatic";
    form.url = @"https://example.test?tenant=acme";
    check([[store chatCompletionsURLForForm:form error:nil].absoluteString
           isEqual:@"https://example.test/v1/chat/completions?tenant=acme"], @"root endpoint gains /v1/chat/completions and keeps query");
    form.url = @"https://example.test/v1?tenant=acme";
    check([[store chatCompletionsURLForForm:form error:nil].absoluteString
           isEqual:@"https://example.test/v1/chat/completions?tenant=acme"], @"/v1 endpoint gains chat route");
    form.url = @"https://example.test/gateway/v2/";
    check([[store chatCompletionsURLForForm:form error:nil].absoluteString
           isEqual:@"https://example.test/gateway/v2/chat/completions"], @"custom prefix gains chat route");
    form.url = @"https://example.test/v1/chat/completions/?tenant=acme";
    check([[store chatCompletionsURLForForm:form error:nil].absoluteString
           isEqual:form.url], @"complete route with trailing slash is preserved without duplication");
    form.endpointMode = @"manual";
    form.url = @"https://example.test/proxy/completion?deployment=x";
    check([[store chatCompletionsURLForForm:form error:nil].absoluteString isEqual:form.url], @"manual endpoint is used verbatim");
}

static void testNativeSelectionLimitAndOutlineTemplates(void) {
    AIService *service = [AIService alloc];
    // Build exactly 5000 scalar values; NSString's length counts surrogate pairs twice.
    NSMutableString *fiveThousand = [NSMutableString string];
    for (NSUInteger i = 0; i < 5000; i++) [fiveThousand appendString:@"😀"];
    NSError *error = nil;
    NSArray *messages = [service messagesForPayload:@{@"taskType": @"rewrite", @"selection": fiveThousand} error:&error];
    check(messages != nil && error == nil, @"exactly 5000 Unicode code points are accepted");
    [fiveThousand appendString:@"😀"];
    messages = [service messagesForPayload:@{@"taskType": @"rewrite", @"selection": fiveThousand} error:&error];
    check(messages == nil && [error.localizedDescription isEqual:@"选中内容不可超过5000字符"], @"selection over 5000 code points returns exact Chinese error");
    check([[service outlineTemplateForType:@"general"] containsString:@"主题"], @"general outline template covers theme");
    check([[service outlineTemplateForType:@"meeting"] containsString:@"待办事项"], @"meeting outline template covers todos");
    check([[service outlineTemplateForType:@"summary"] containsString:@"下一步计划"], @"summary outline template covers next steps");
    for (NSString *type in @[@"general", @"meeting", @"summary"]) {
        messages = [service messagesForPayload:@{@"taskType": @"outline", @"selection": @"主题与背景",
            @"context": @{@"outlineType": type}} error:&error];
        NSString *combinedPrompt = [NSString stringWithFormat:@"%@\n%@", messages[0][@"content"], messages[1][@"content"]];
        check([combinedPrompt containsString:@"待补充"] && [combinedPrompt containsString:@"主题与背景"],
              [NSString stringWithFormat:@"%@ outline prompt includes template and user facts", type]);
    }
    messages = [service messagesForPayload:@{@"taskType": @"outline", @"selection": @"研究主题",
        @"context": @{@"outlineType": @"paper"}} error:&error];
    NSString *legacyPaperSystem = messages.firstObject[@"content"];
    check([legacyPaperSystem containsString:@"论文含摘要/引言/方法/结果/结论"] &&
          ![legacyPaperSystem containsString:@"一、主题\n1. 核心主题"],
          @"legacy paper outline retains academic structure without general-only template");
    messages = [service messagesForPayload:@{@"taskType": @"continue", @"selection": @"续写源文末尾。"} error:&error];
    check([messages[0][@"content"] containsString:@"续写一至三个自然段"] &&
          [messages[0][@"content"] containsString:@"自然收束并停止"] &&
          [messages[1][@"content"] containsString:@"续写源文末尾。"],
          @"continuation prompt preserves source and asks for a natural stopping point");
    NSDictionary *lowerContinuation = [service continuationSamplingOverridesForMaxTokens:640];
    NSDictionary *cappedContinuation = [service continuationSamplingOverridesForMaxTokens:8192];
    check([lowerContinuation[@"max_tokens"] integerValue] == 640 &&
          [cappedContinuation[@"max_tokens"] integerValue] == 1024 &&
          [lowerContinuation[@"temperature"] floatValue] == 0.5f &&
          [lowerContinuation[@"presence_penalty"] integerValue] == 0 &&
          [lowerContinuation[@"frequency_penalty"] integerValue] == 0,
          @"continuation sampling enforces its values and respects lower token config");
}

static void testEmptyOutlineReturnsTemplate(void) {
    AIService *service = [AIService alloc];
    NSMutableArray *events = [NSMutableArray array];
    [service startRequest:@{@"taskType": @"outline", @"selection": @"", @"context": @{@"outlineType": @"meeting"}}
                requestId:@"empty-outline" documentSessionId:@"doc"
                     emit:^(NSString *type, NSString *requestId, NSString *dsid, NSDictionary *payload) {
        [events addObject:@{@"type": type, @"payload": payload}];
    }];
    NSDate *deadline = [NSDate dateWithTimeIntervalSinceNow:0.05];
    while ([deadline timeIntervalSinceNow] > 0) [[NSRunLoop currentRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.005]];
    check(events.count == 1 && [events[0][@"type"] isEqual:@"ai.done"], @"empty outline completes without requesting a model");
    check([events[0][@"payload"][@"fullText"] containsString:@"会议信息"], @"empty outline returns selected template");
}

static void testSSEProviderError(void) {
    AIService *service = [AIService alloc];
    AIRequestSession *session = [[AIRequestSession alloc] initWithRequestId:@"provider-error" documentSessionId:@"doc"];
    id request = [NSClassFromString(@"AIServiceRequest") new];
    [request setValue:session forKey:@"session"];
    NSMutableArray *events = [NSMutableArray array];
    [request setValue:[^(NSString *type, NSString *requestId, NSString *dsid, NSDictionary *payload) {
        [events addObject:@{@"type": type, @"payload": payload}];
    } copy] forKey:@"emitter"];
    NSURLSessionDataTask *task = [[NSURLSession sharedSession] dataTaskWithURL:[NSURL URLWithString:@"http://127.0.0.1/unused"]];
    [service setValue:[NSMutableDictionary dictionaryWithObject:request forKey:task] forKey:@"requestsByTask"];
    [service setValue:[NSMutableDictionary dictionaryWithObject:request forKey:@"provider-error"] forKey:@"requestsById"];
    NSString *data = @"data: {\"error\":{\"message\":\"bad key\"}}\n\n";
    [service URLSession:[NSURLSession sharedSession] dataTask:task didReceiveData:[data dataUsingEncoding:NSUTF8StringEncoding]];
    NSDate *deadline = [NSDate dateWithTimeIntervalSinceNow:0.05];
    while ([deadline timeIntervalSinceNow] > 0) [[NSRunLoop currentRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.005]];
    check(events.count == 1 && [events[0][@"type"] isEqual:@"ai.error"], @"SSE provider error is surfaced as terminal error");

    [events removeAllObjects];
    session = [[AIRequestSession alloc] initWithRequestId:@"empty-done" documentSessionId:@"doc"];
    request = [NSClassFromString(@"AIServiceRequest") new];
    [request setValue:session forKey:@"session"];
    [request setValue:[^(NSString *type, NSString *requestId, NSString *dsid, NSDictionary *payload) {
        [events addObject:@{@"type": type, @"payload": payload}];
    } copy] forKey:@"emitter"];
    task = [[NSURLSession sharedSession] dataTaskWithURL:[NSURL URLWithString:@"http://127.0.0.1/unused"]];
    [service setValue:[NSMutableDictionary dictionaryWithObject:request forKey:task] forKey:@"requestsByTask"];
    [service setValue:[NSMutableDictionary dictionaryWithObject:request forKey:@"empty-done"] forKey:@"requestsById"];
    [service URLSession:[NSURLSession sharedSession] dataTask:task
         didReceiveData:[@"data: [DONE]\n\n" dataUsingEncoding:NSUTF8StringEncoding]];
    deadline = [NSDate dateWithTimeIntervalSinceNow:0.05];
    while ([deadline timeIntervalSinceNow] > 0) [[NSRunLoop currentRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.005]];
    check(events.count == 1 && [events[0][@"type"] isEqual:@"ai.error"] &&
          [events[0][@"payload"][@"message"] isEqual:@"AI 未返回可用内容，请重试"],
          @"empty DONE sentinel returns a readable terminal error");
}
static void testOutlineJsonParsing(void) {
    BOOL truncated = NO;
    NSError *error = nil;
    NSArray *pages = @[@{@"page": @1, @"title": @"第一页", @"content": @"内容含\"引号\"与{括号}"},
                       @{@"page": @2, @"title": @"第二页", @"content": @"保留第二页"}];
    NSData *data = [NSJSONSerialization dataWithJSONObject:pages options:0 error:nil];
    NSString *json = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    id parsed = ImpressParseModelJson(json, &truncated, &error);
    check([parsed isEqual:pages] && !truncated, @"complete array keeps every slide and quoted content");
    NSString *wrapped = [NSString stringWithFormat:@"说明\n```json\n%@\n```", json];
    parsed = ImpressParseModelJson(wrapped, &truncated, &error);
    check([parsed isEqual:pages], @"fenced array is parsed as a whole");
    parsed = ImpressParseModelJson([NSString stringWithFormat:@"<think>先分析{结构}</think>\n%@", json], &truncated, &error);
    check([parsed isEqual:pages], @"leading reasoning cannot hide the final JSON root");
    NSString *broken = @"{\"slides\":[{\"title\":\"完整页\",\"content\":\"要点\"},{\"title\":\"未完成";
    check(ImpressParseModelJson(broken, &truncated, &error) == nil && truncated,
          @"incomplete later slide cannot silently turn into successful partial outline");
    parsed = ImpressParseModelJson(@"{\"slides\":[{\"title\":\"换行\",\"content\":\"一\n二\",},]}", &truncated, &error);
    check([parsed[@"slides"] count] == 1 && !truncated, @"raw line break and trailing comma formatting can be repaired");
    check(ImpressParseModelJson(@"", &truncated, &error) == nil && truncated, @"empty reply is not an outline");
}
int main() { @autoreleasepool {
    NSString *line = @"data: {\"choices\":[{\"delta\":{\"content\":\"润色完成\"}}]}";
    NSData *(^bytes)(NSString *) = ^NSData *(NSString *value) { return [value dataUsingEncoding:NSUTF8StringEncoding]; };
    runCase(@[bytes([line stringByAppendingString:@"\n\n"])], NO, @"润色完成");
    runCase(@[bytes(line)], NO, @"润色完成");
    runCase(@[bytes([line stringByAppendingString:@"\n\ndata: [DONE]\n\n"])], NO, @"润色完成");
    NSData *whole = bytes([line stringByAppendingString:@"\r\n\r\n"]);
    runCase(@[[whole subdataWithRange:NSMakeRange(0, 46)], [whole subdataWithRange:NSMakeRange(46, whole.length - 46)]], NO, @"润色完成");
    runCase(@[bytes([line stringByAppendingString:@"\n\n"])], YES, @"润色完成");
    runCase(@[bytes(@"{\"choices\":[{\"message\":{\"content\":\"兼容普通JSON回复\"},\"finish_reason\":\"stop\"}]}")], NO, @"兼容普通JSON回复");
    testOutlineJsonParsing();
    testImpressOutlinePromptContract();
    testLengthFinishReasonCapture();
    testEndpointResolution();
    testNativeSelectionLimitAndOutlineTemplates();
    testEmptyOutlineReturnsTemplate();
    testSSEProviderError();
    puts("AI stream callback and outline prompt tests passed");
} }
