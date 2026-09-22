// Minimal host smoke test for LlamaInferenceCore (macOS slice of llama.xcframework).
// Usage: see ios/scripts/llama-smoke-test.sh

#include "../Mobile/AI/LlamaInferenceCore.h"

#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

static const char *kRoles[] = {"user"};
static const char *kContents[] = {"Say hi in one short English sentence."};

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "Usage: %s /path/to/model.gguf\n", argv[0]);
        return 2;
    }
    const char *modelPath = argv[1];

    if (!xl_llama_is_available()) {
        fprintf(stderr, "xl_llama_is_available() is false on this target\n");
        return 3;
    }

    if (!xl_llama_load_model(modelPath, 2048, 4)) {
        fprintf(stderr, "xl_llama_load_model failed\n");
        return 4;
    }

    if (!xl_llama_prefill_messages(kRoles, kContents, 1)) {
        fprintf(stderr, "xl_llama_prefill_messages failed\n");
        xl_llama_unload_model();
        return 5;
    }

    std::string out;
    for (int i = 0; i < 64; i++) {
        char piece[512];
        int r = xl_llama_sample_token(piece, sizeof(piece));
        if (r == 0) {
            break;
        }
        if (r == 2) {
            continue;
        }
        out.append(piece);
    }

    xl_llama_unload_model();
    printf("--- output ---\n%s\n--- end ---\n", out.c_str());
    return out.empty() ? 6 : 0;
}
