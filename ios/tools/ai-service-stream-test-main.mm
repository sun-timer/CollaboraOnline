#import <Foundation/Foundation.h>
#import "AIService.h"
#import "AIRequestSession.h"
#import "AiBackendRouter.h"
#import "ImpressModelJson.h"

@interface AIService (OutlinePromptTest)
- (NSArray *)messagesForPayload:(NSDictionary *)payload error:(NSError **)error;
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
    if (cancel) [session cancel];
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
    NSString *chunk = [@"data: {\"choices\":[{\"delta\":{\"content\":\"{\"},\"finish_reason\":null}]}\n\n"
        stringByAppendingString:@"data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\n"];
    [service URLSession:[NSURLSession sharedSession] dataTask:task
         didReceiveData:[chunk dataUsingEncoding:NSUTF8StringEncoding]];
    [service URLSession:[NSURLSession sharedSession] task:task didCompleteWithError:nil];
    NSDate *deadline = [NSDate dateWithTimeIntervalSinceNow:0.05];
    while ([deadline timeIntervalSinceNow] > 0) [[NSRunLoop currentRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.005]];
    NSDictionary *done = nil;
    for (NSDictionary *event in events) if ([event[@"type"] isEqual:@"ai.done"]) done = event[@"payload"];
    check([done[@"finishReason"] isEqual:@"length"], @"length finish reason reaches ai.done payload");
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
    puts("AI stream callback and outline prompt tests passed");
} }
