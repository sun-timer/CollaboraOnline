#import "AiBackendRouter.h"
#import "LocalPromptBuilder.h"

#include <stdio.h>
#include <stdlib.h>

static int gFailures = 0;

static void expectTrue(BOOL value, const char *message) {
    if (!value) {
        fprintf(stderr, "FAIL: %s\n", message);
        gFailures += 1;
    }
}

static void expectEqual(NSString *a, NSString *b, const char *message) {
    BOOL ok = (a == b) || [a isEqualToString:b];
    if (!ok) {
        fprintf(stderr, "FAIL: %s (got '%s' expected '%s')\n", message,
                a.UTF8String ?: "(null)", b.UTF8String ?: "(null)");
        gFailures += 1;
    }
}

static AiBackendLocalModelState *readyLocalState(void) {
    AiBackendLocalModelState *state = [[AiBackendLocalModelState alloc] init];
    state.deviceSupported = YES;
    state.installed = YES;
    state.enabled = YES;
    return state;
}

static void testRouter(void) {
    AiBackendRouter *router = [[AiBackendRouter alloc] init];
    AiBackendLocalModelState *ready = readyLocalState();

    AiBackendResolvedRoute *route =
        [router resolveTaskType:@"image_generate" modelMode:@"base" docCharCount:0 localState:ready];
    expectTrue(route.backend == AiBackendKindCloud, "image_generate is cloud-only");
    expectEqual(route.reason, @"cloud_only_task", "image_generate reason");

    route = [router resolveTaskType:@"continue_writing" modelMode:@"base" docCharCount:0 localState:ready];
    expectTrue(route.backend == AiBackendKindLocal, "continue_writing uses local when ready");
    expectEqual(route.reason, @"local_ready", "local_ready reason");
    expectEqual(route.modelMode, @"local", "local modelMode");

    route = [router resolveTaskType:@"format_batch" modelMode:@"base" docCharCount:0 localState:ready];
    expectTrue(route.backend == AiBackendKindCloud, "format_batch stays cloud");

    route = [router resolveTaskType:@"doc_qa" modelMode:@"base" docCharCount:6000 localState:ready];
    expectTrue(route.backend == AiBackendKindCloud, "doc_qa too long for local");
    expectEqual(route.reason, @"doc_qa_too_long_for_local", "doc_qa length reason");

    route = [router resolveTaskType:@"doc_qa" modelMode:@"base" docCharCount:1000 localState:ready];
    expectTrue(route.backend == AiBackendKindLocal, "doc_qa within limit uses local");

    AiBackendLocalModelState *disabled = readyLocalState();
    disabled.enabled = NO;
    route = [router resolveTaskType:@"chat" modelMode:@"think" docCharCount:0 localState:disabled];
    expectTrue(route.backend == AiBackendKindCloud, "disabled local falls back cloud");
    expectEqual(route.reason, @"cloud_fallback", "cloud_fallback reason");

    expectTrue([AiBackendRouter isMultiTurnTask:@"chat"], "chat is multi-turn");
    expectTrue(![AiBackendRouter isMultiTurnTask:@"polish"], "polish is single-turn");
}

static void testPromptBuilder(void) {
    NSArray *history = @[
        @{@"role": @"system", @"content": @"sys"},
        @{@"role": @"user", @"content": @"u1"},
        @{@"role": @"assistant", @"content": @"a1"},
        @{@"role": @"user", @"content": @"u2"},
    ];
    NSArray *single = [LocalPromptBuilder buildPromptFromHistory:history
                                                     contextSize:4096
                                                   maxGenTokens:512
                                                      multiTurn:NO];
    expectTrue(single.count > 0, "single-turn prompt non-empty");

    NSArray *multi = [LocalPromptBuilder buildPromptFromHistory:history
                                                    contextSize:4096
                                                  maxGenTokens:512
                                                     multiTurn:YES];
    expectTrue(multi.count >= 2, "multi-turn keeps system + recent turns");
    expectTrue([multi.firstObject[@"role"] isEqualToString:@"system"], "system preserved");
}

int main(int argc, char **argv) {
    (void)argc;
    (void)argv;
    @autoreleasepool {
        testRouter();
        testPromptBuilder();
    }
    if (gFailures > 0) {
        fprintf(stderr, "%d test assertion(s) failed\n", gFailures);
        return 1;
    }
    printf("ai-logic-test: OK\n");
    return 0;
}
