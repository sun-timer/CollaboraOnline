#include "LlamaInferenceCore.h"

#include <TargetConditionals.h>
#include <cstdarg>
#include <os/log.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "ggml-backend.h"
#include "llama.h"

#define LO_TAG "LocalLlama"

static os_log_t g_log;

static void ensure_log(void) {
    if (g_log == NULL) {
        g_log = os_log_create("com.xunlong.xloffice", LO_TAG);
    }
}

static void xl_log_info(const char *fmt, ...) {
    ensure_log();
    char buf[768];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    os_log_info(g_log, "%{public}s", buf);
}

#define BATCH_SIZE 128

static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static llama_sampler *g_sampler = nullptr;
static llama_batch g_batch = {};
static llama_pos g_n_past = 0;
static bool g_batch_ready = false;
static bool g_backend_inited = false;

// Per-token decode timing, to separate native decode cost from JNI/Java streaming overhead.
static long long g_gen_decode_us = 0;
static int g_gen_decode_count = 0;

// Accumulate UTF-8 bytes until a complete character (tokens may split multibyte sequences).
static std::string g_utf8_pending;

// Last prompt tokens for doc_qa KV prefix reuse across turns.
static std::vector<llama_token> g_last_prompt_tokens;

static size_t utf8_complete_bytes(const char *s, size_t n) {
    size_t i = 0;
    while (i < n) {
        unsigned char c = (unsigned char) s[i];
        size_t len;
        if (c < 0x80) {
            len = 1;
        } else if ((c & 0xE0) == 0xC0) {
            len = 2;
        } else if ((c & 0xF0) == 0xE0) {
            len = 3;
        } else if ((c & 0xF8) == 0xF0) {
            len = 4;
        } else {
            len = 1;
        }
        if (i + len > n) {
            break;
        }
        bool ok = true;
        for (size_t k = 1; k < len; k++) {
            if (((unsigned char) s[i + k] & 0xC0) != 0x80) {
                ok = false;
                break;
            }
        }
        if (!ok) {
            len = 1;
        }
        i += len;
    }
    return i;
}

static void log_fail(const char *reason, const char *detail) {
    ensure_log();
    os_log_error(g_log, "local_core_fail reason=%{public}s detail=%{public}s", reason,
                 detail == nullptr ? "" : detail);
}

// Route llama.cpp internal logs (model load / backend init errors) to logcat,
// otherwise llama_model_load_from_file returns null with no diagnostic at all.
static void llama_ios_log(enum ggml_log_level level, const char *text, void * /*user_data*/) {
    if (text == nullptr || text[0] == '\0') {
        return;
    }
    ensure_log();
    std::string msg(text);
    while (!msg.empty() && (msg.back() == '\n' || msg.back() == '\r')) {
        msg.pop_back();
    }
    if (level == GGML_LOG_LEVEL_ERROR) {
        os_log_error(g_log, "llama_internal %{public}s", msg.c_str());
    } else if (level == GGML_LOG_LEVEL_WARN) {
        os_log_fault(g_log, "llama_internal %{public}s", msg.c_str());
    } else {
        os_log_info(g_log, "llama_internal %{public}s", msg.c_str());
    }
}

static int resolve_thread_count(int requested) {
    const long cpu_count = sysconf(_SC_NPROCESSORS_ONLN);
    int threads = requested > 0 ? requested : 2;
    if (cpu_count > 2) {
        threads = std::min(threads, (int) cpu_count - 2);
    }
    return std::max(2, std::min(threads, 4));
}

static void log_backend_regs() {
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        ggml_backend_reg_t reg = ggml_backend_reg_get(i);
        xl_log_info( "local_backend_reg name=%s",
                            ggml_backend_reg_name(reg));
    }
}

static void log_system_info() {
    const char *info = llama_print_system_info();
    if (info != nullptr) {
        xl_log_info( "local_sysinfo %s", info);
    }
}

static bool ensure_backend(void) {
    if (g_backend_inited) {
        return true;
    }
    llama_log_set(llama_ios_log, nullptr);
    llama_backend_init();
    g_backend_inited = true;
    xl_log_info("local_backend_ok reg_count=%zu", ggml_backend_reg_count());
    log_backend_regs();
    log_system_info();
    return true;
}

static int decode_with_heartbeat(const char *phase, int offset, int count) {
    std::atomic<bool> done{false};
    std::mutex mtx;
    std::condition_variable cv;
    std::thread heartbeat([&]() {
        int elapsed_s = 0;
        std::unique_lock<std::mutex> lock(mtx);
        while (!done.load()) {
            // ( wait_for + a???done ??s$?M join() {I? 5 ?
            cv.wait_for(lock, std::chrono::seconds(5), [&] { return done.load(); });
            if (done.load()) {
                break;
            }
            elapsed_s += 5;
            xl_log_info(
                                "local_decode_heartbeat phase=%s offset=%d count=%d elapsed_s=%d",
                                phase, offset, count, elapsed_s);
        }
    });

    const int rc = llama_decode(g_ctx, g_batch);
    done.store(true);
    cv.notify_all();
    if (heartbeat.joinable()) {
        heartbeat.join();
    }
    return rc;
}

static void batch_clear() {
    g_batch.n_tokens = 0;
}

static void batch_add(llama_token id, llama_pos pos, bool logits) {
    g_batch.token[g_batch.n_tokens] = id;
    g_batch.pos[g_batch.n_tokens] = pos;
    g_batch.n_seq_id[g_batch.n_tokens] = 1;
    g_batch.seq_id[g_batch.n_tokens][0] = 0;
    g_batch.logits[g_batch.n_tokens] = logits;
    g_batch.n_tokens++;
}

static bool decode_prompt_tokens(const std::vector<llama_token> &tokens) {
    if (tokens.empty()) {
        return true;
    }

    for (int i = 0; i < (int) tokens.size(); i += BATCH_SIZE) {
        const int cur = std::min(BATCH_SIZE, (int) tokens.size() - i);
        batch_clear();
        for (int j = 0; j < cur; j++) {
            const bool want_logits = (i + j == (int) tokens.size() - 1);
            batch_add(tokens[(size_t) (i + j)], g_n_past + i + j, want_logits);
        }
        xl_log_info( "local_prefill_batch start offset=%d count=%d",
                            i, cur);
        const auto batch_start = std::chrono::steady_clock::now();
        if (decode_with_heartbeat("prefill", i, cur) != 0) {
            log_fail("local_infer_fail", "prefill_decode");
            return false;
        }
        const auto batch_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                                      std::chrono::steady_clock::now() - batch_start)
                                      .count();
        xl_log_info(
                            "local_prefill_batch ok offset=%d count=%d ms=%lld", i, cur,
                            (long long) batch_ms);
    }

    g_n_past += (llama_pos) tokens.size();
    return true;
}

// KV prefix reuse for multi-turn doc_qa when new prompt extends the previous one.
static bool prefill_tokens_with_kv_reuse(const std::vector<llama_token> &tokens) {
    bool reuse = false;
    size_t prefix = 0;
    if (!g_last_prompt_tokens.empty() && tokens.size() >= g_last_prompt_tokens.size()) {
        bool match = true;
        for (size_t i = 0; i < g_last_prompt_tokens.size(); i++) {
            if (tokens[i] != g_last_prompt_tokens[i]) {
                match = false;
                break;
            }
        }
        if (match) {
            prefix = g_last_prompt_tokens.size();
            reuse = true;
        }
    }

    if (reuse && prefix > 0) {
        llama_memory_t mem = llama_get_memory(g_ctx);
        llama_memory_seq_rm(mem, 0, (llama_pos) prefix, -1);
        g_n_past = (llama_pos) prefix;
        xl_log_info(
                            "local_kv_reuse prefix=%zu total=%zu delta=%zu",
                            prefix, tokens.size(), tokens.size() - prefix);
        std::vector<llama_token> delta(tokens.begin() + (long) prefix, tokens.end());
        if (!decode_prompt_tokens(delta)) {
            return false;
        }
    } else {
        if (reuse && prefix == 0) {
            xl_log_info( "local_kv_reuse skip_zero_prefix");
        }
        llama_memory_clear(llama_get_memory(g_ctx), false);
        g_n_past = 0;
        if (!decode_prompt_tokens(tokens)) {
            return false;
        }
    }

    g_last_prompt_tokens = tokens;
    return true;
}

static bool smoke_decode_test() {
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens(32);
    const int n_tokens =
            llama_tokenize(vocab, "hi", 2, tokens.data(), (int32_t) tokens.size(), true, true);
    if (n_tokens <= 0) {
        log_fail("local_smoke_fail", "tokenize");
        return false;
    }

    batch_clear();
    for (int j = 0; j < n_tokens; j++) {
        batch_add(tokens[(size_t) j], j, j == n_tokens - 1);
    }

    const auto smoke_start = std::chrono::steady_clock::now();
    if (decode_with_heartbeat("smoke", 0, n_tokens) != 0) {
        log_fail("local_smoke_fail", "decode");
        return false;
    }
    const auto smoke_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                                  std::chrono::steady_clock::now() - smoke_start)
                                  .count();
    llama_memory_clear(llama_get_memory(g_ctx), false);
    g_n_past = 0;
    xl_log_info( "local_smoke_ok tokens=%d ms=%lld", n_tokens,
                        (long long) smoke_ms);
    return true;
}

static bool prefill_prompt_text(const char *prompt) {
    if (prompt == nullptr || prompt[0] == '\0') {
        log_fail("local_infer_fail", "empty_prompt");
        return false;
    }

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens(8192);
    int n_tokens = llama_tokenize(vocab, prompt, (int32_t) strlen(prompt), tokens.data(),
                                  (int32_t) tokens.size(), true, true);
    if (n_tokens < 0) {
        tokens.resize((size_t) (-n_tokens));
        n_tokens = llama_tokenize(vocab, prompt, (int32_t) strlen(prompt), tokens.data(),
                                  (int32_t) tokens.size(), true, true);
    }

    if (n_tokens <= 0) {
        log_fail("local_infer_fail", "tokenize");
        return false;
    }
    tokens.resize((size_t) n_tokens);

    return prefill_tokens_with_kv_reuse(tokens);
}

extern "C" {

bool xl_llama_is_available(void) {
#if TARGET_OS_SIMULATOR
#if defined(XL_LLAMA_SIMULATOR_DEBUG)
    return true;
#else
    return false;
#endif
#else
    return true;
#endif
}

bool xl_llama_load_model(const char *path, int contextSize, int threads) {
    if (g_ctx != nullptr && g_model != nullptr) {
        return true;
    }
    if (!xl_llama_is_available()) {
        log_fail("local_load_fail", "simulator");
        return false;
    }
    if (!ensure_backend()) {
        return false;
    }
    if (path == nullptr) {
        log_fail("local_load_fail", "null_path");
        return false;
    }
    llama_model_params mparams = llama_model_default_params();
#if TARGET_OS_SIMULATOR
    mparams.n_gpu_layers = 0;
#else
    mparams.n_gpu_layers = 99;
#endif
    g_model = llama_model_load_from_file(path, mparams);
    if (g_model == nullptr) {
        log_fail("local_load_fail", "model_load");
        return false;
    }
    const int n_threads = resolve_thread_count(threads);
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = contextSize > 0 ? (uint32_t) contextSize : 4096;
    cparams.n_batch = BATCH_SIZE;
    cparams.n_ubatch = BATCH_SIZE;
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;
    g_ctx = llama_init_from_model(g_model, cparams);
    if (g_ctx == nullptr) {
        log_fail("local_load_fail", "ctx_init");
        llama_model_free(g_model);
        g_model = nullptr;
        return false;
    }
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.3f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    g_batch_ready = true;
    g_n_past = 0;
    if (!smoke_decode_test()) {
        if (g_batch_ready) {
            llama_batch_free(g_batch);
            g_batch = {};
            g_batch_ready = false;
        }
        if (g_sampler != nullptr) {
            llama_sampler_free(g_sampler);
            g_sampler = nullptr;
        }
        llama_free(g_ctx);
        g_ctx = nullptr;
        llama_model_free(g_model);
        g_model = nullptr;
        return false;
    }
    g_last_prompt_tokens.clear();
    return true;
}

void xl_llama_unload_model(void) {
    if (g_batch_ready) {
        llama_batch_free(g_batch);
        g_batch = {};
        g_batch_ready = false;
    }
    if (g_sampler != nullptr) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_n_past = 0;
    g_utf8_pending.clear();
    g_last_prompt_tokens.clear();
    if (g_backend_inited) {
        llama_backend_free();
        g_backend_inited = false;
    }
}

bool xl_llama_prefill_messages(const char **roles, const char **contents, int count) {
    if (g_ctx == nullptr || g_model == nullptr || roles == nullptr || contents == nullptr || count <= 0) {
        log_fail("local_infer_fail", "not_loaded");
        return false;
    }
    std::vector<std::string> role_storage((size_t) count);
    std::vector<std::string> content_storage((size_t) count);
    std::vector<llama_chat_message> chat((size_t) count);
    for (int i = 0; i < count; i++) {
        if (roles[i] == nullptr || contents[i] == nullptr) {
            log_fail("local_infer_fail", "null_message");
            return false;
        }
        role_storage[(size_t) i] = roles[i];
        content_storage[(size_t) i] = contents[i];
        chat[(size_t) i].role = role_storage[(size_t) i].c_str();
        chat[(size_t) i].content = content_storage[(size_t) i].c_str();
    }
    std::string formatted(16384, '\0');
    int32_t needed = llama_chat_apply_template(nullptr, chat.data(), (size_t) count, true,
                                               formatted.data(), (int32_t) formatted.size());
    if (needed < 0) {
        log_fail("local_infer_fail", "chat_template");
        return false;
    }
    if (needed > (int32_t) formatted.size()) {
        formatted.resize((size_t) needed);
        needed = llama_chat_apply_template(nullptr, chat.data(), (size_t) count, true,
                                           formatted.data(), (int32_t) formatted.size());
        if (needed < 0) {
            log_fail("local_infer_fail", "chat_template");
            return false;
        }
    }
    formatted.resize((size_t) needed);
    return prefill_prompt_text(formatted.c_str());
}

int xl_llama_sample_token(char *buf, size_t buf_len) {
    if (buf == nullptr || buf_len == 0) {
        return 0;
    }
    buf[0] = '\0';
    if (g_ctx == nullptr || g_model == nullptr || g_sampler == nullptr) {
        return 0;
    }
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    llama_token next = llama_sampler_sample(g_sampler, g_ctx, -1);
    llama_sampler_accept(g_sampler, next);
    if (llama_vocab_is_eog(vocab, next)) {
        g_utf8_pending.clear();
        return 0;
    }
    char piece[512];
    int piece_len = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, true);
    if (piece_len <= 0) {
        return 0;
    }
    batch_clear();
    batch_add(next, g_n_past, true);
    if (llama_decode(g_ctx, g_batch) != 0) {
        log_fail("local_infer_fail", "gen_decode");
        return 0;
    }
    g_n_past++;
    g_utf8_pending.append(piece, (size_t) piece_len);
    const size_t complete = utf8_complete_bytes(g_utf8_pending.data(), g_utf8_pending.size());
    if (complete == 0) {
        return 2;
    }
    size_t copy_len = complete;
    if (copy_len >= buf_len) {
        copy_len = buf_len - 1;
    }
    memcpy(buf, g_utf8_pending.data(), copy_len);
    buf[copy_len] = '\0';
    g_utf8_pending.erase(0, complete);
    return 1;
}

} // extern "C"
