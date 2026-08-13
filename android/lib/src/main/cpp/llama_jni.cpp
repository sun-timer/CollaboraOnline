#include <jni.h>
#include <android/log.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "ggml-backend.h"
#include "llama.h"

#define LO_TAG "LOActivity"
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

static void log_fail(const char *reason, const char *detail) {
    __android_log_print(ANDROID_LOG_ERROR, LO_TAG, "local_core_fail reason=%s detail=%s", reason,
                        detail == nullptr ? "" : detail);
}

// Route llama.cpp internal logs (model load / backend init errors) to logcat,
// otherwise llama_model_load_from_file returns null with no diagnostic at all.
static void llama_android_log(enum ggml_log_level level, const char *text, void * /*user_data*/) {
    if (text == nullptr || text[0] == '\0') {
        return;
    }
    int prio = ANDROID_LOG_DEBUG;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR:
            prio = ANDROID_LOG_ERROR;
            break;
        case GGML_LOG_LEVEL_WARN:
            prio = ANDROID_LOG_WARN;
            break;
        case GGML_LOG_LEVEL_INFO:
            prio = ANDROID_LOG_INFO;
            break;
        default:
            break;
    }
    std::string msg(text);
    while (!msg.empty() && (msg.back() == '\n' || msg.back() == '\r')) {
        msg.pop_back();
    }
    __android_log_print(prio, LO_TAG, "llama_internal %s", msg.c_str());
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
        __android_log_print(ANDROID_LOG_INFO, LO_TAG, "local_backend_reg name=%s",
                            ggml_backend_reg_name(reg));
    }
}

static void log_system_info() {
    const char *info = llama_print_system_info();
    if (info != nullptr) {
        __android_log_print(ANDROID_LOG_INFO, LO_TAG, "local_sysinfo %s", info);
    }
}

static bool ensure_backend(JNIEnv *env, jstring jnativeLibDir) {
    if (g_backend_inited) {
        return true;
    }
    if (jnativeLibDir == nullptr) {
        log_fail("local_backend_fail", "null_lib_dir");
        return false;
    }
    llama_log_set(llama_android_log, nullptr);
    const char *lib_dir = env->GetStringUTFChars(jnativeLibDir, nullptr);
    if (lib_dir == nullptr) {
        log_fail("local_backend_fail", "lib_dir_utf");
        return false;
    }
    __android_log_print(ANDROID_LOG_INFO, LO_TAG, "local_backend_load path=%s", lib_dir);
    ggml_backend_load_all_from_path(lib_dir);
    env->ReleaseStringUTFChars(jnativeLibDir, lib_dir);
    llama_backend_init();
    g_backend_inited = true;
    const size_t reg_count = ggml_backend_reg_count();
    __android_log_print(ANDROID_LOG_INFO, LO_TAG, "local_backend_ok reg_count=%zu", reg_count);
    log_backend_regs();
    if (reg_count == 0) {
        log_fail("local_backend_fail", "no_backend_registered");
    }
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
            // 用 wait_for + 条件变量：done 时立即唤醒，避免 join() 死等满 5 秒
            cv.wait_for(lock, std::chrono::seconds(5), [&] { return done.load(); });
            if (done.load()) {
                break;
            }
            elapsed_s += 5;
            __android_log_print(ANDROID_LOG_INFO, LO_TAG,
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
        __android_log_print(ANDROID_LOG_INFO, LO_TAG, "local_prefill_batch start offset=%d count=%d",
                            i, cur);
        const auto batch_start = std::chrono::steady_clock::now();
        if (decode_with_heartbeat("prefill", i, cur) != 0) {
            log_fail("local_infer_fail", "prefill_decode");
            return false;
        }
        const auto batch_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                                      std::chrono::steady_clock::now() - batch_start)
                                      .count();
        __android_log_print(ANDROID_LOG_INFO, LO_TAG,
                            "local_prefill_batch ok offset=%d count=%d ms=%lld", i, cur,
                            (long long) batch_ms);
    }

    g_n_past += (llama_pos) tokens.size();
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
    __android_log_print(ANDROID_LOG_INFO, LO_TAG, "local_smoke_ok tokens=%d ms=%lld", n_tokens,
                        (long long) smoke_ms);
    return true;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_org_libreoffice_androidlib_ai_LocalInferenceEngine_nativeLoadModel(JNIEnv *env, jclass,
                                                                        jstring jpath,
                                                                        jint contextSize, jint threads,
                                                                        jstring jnativeLibDir) {
    if (g_ctx != nullptr && g_model != nullptr) {
        return JNI_TRUE;
    }

    if (!ensure_backend(env, jnativeLibDir)) {
        return JNI_FALSE;
    }

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (path == nullptr) {
        log_fail("local_load_fail", "null_path");
        return JNI_FALSE;
    }

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);

    if (g_model == nullptr) {
        log_fail("local_load_fail", "model_load");
        return JNI_FALSE;
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
        return JNI_FALSE;
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
        return JNI_FALSE;
    }

    __android_log_print(ANDROID_LOG_INFO, LO_TAG, "local_load_ok ctx=%u batch=%u threads=%d",
                        cparams.n_ctx, cparams.n_batch, n_threads);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_org_libreoffice_androidlib_ai_LocalInferenceEngine_nativeUnloadModel(JNIEnv *, jclass) {
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
    if (g_backend_inited) {
        llama_backend_free();
        g_backend_inited = false;
    }
    __android_log_print(ANDROID_LOG_INFO, LO_TAG, "local_unload_ok");
}

JNIEXPORT void JNICALL
Java_org_libreoffice_androidlib_ai_LocalInferenceEngine_nativeClearKvCache(JNIEnv *, jclass) {
    if (g_ctx != nullptr) {
        llama_memory_clear(llama_get_memory(g_ctx), false);
        g_n_past = 0;
    }
    g_gen_decode_us = 0;
    g_gen_decode_count = 0;
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

    __android_log_print(ANDROID_LOG_INFO, LO_TAG, "local_prefill_tokens n=%d promptChars=%zu",
                        n_tokens, strlen(prompt));
    const bool ok = decode_prompt_tokens(tokens);
    if (ok) {
        __android_log_print(ANDROID_LOG_INFO, LO_TAG, "local_prefill_native_ok n_past=%d", (int) g_n_past);
    }
    return ok;
}

JNIEXPORT jboolean JNICALL
Java_org_libreoffice_androidlib_ai_LocalInferenceEngine_nativePrefill(JNIEnv *env, jclass, jstring jprompt) {
    if (g_ctx == nullptr || g_model == nullptr || jprompt == nullptr) {
        log_fail("local_infer_fail", "not_loaded");
        return JNI_FALSE;
    }

    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    if (prompt == nullptr) {
        return JNI_FALSE;
    }
    const bool ok = prefill_prompt_text(prompt);
    env->ReleaseStringUTFChars(jprompt, prompt);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_libreoffice_androidlib_ai_LocalInferenceEngine_nativePrefillMessages(JNIEnv *env, jclass,
                                                                              jobjectArray jroles,
                                                                              jobjectArray jcontents) {
    if (g_ctx == nullptr || g_model == nullptr || jroles == nullptr || jcontents == nullptr) {
        log_fail("local_infer_fail", "not_loaded");
        return JNI_FALSE;
    }

    const jsize n_msg = env->GetArrayLength(jroles);
    if (n_msg <= 0 || n_msg != env->GetArrayLength(jcontents)) {
        log_fail("local_infer_fail", "invalid_messages");
        return JNI_FALSE;
    }

    std::vector<std::string> role_storage((size_t) n_msg);
    std::vector<std::string> content_storage((size_t) n_msg);
    std::vector<llama_chat_message> chat((size_t) n_msg);

    for (jsize i = 0; i < n_msg; i++) {
        auto role_obj = (jstring) env->GetObjectArrayElement(jroles, i);
        auto content_obj = (jstring) env->GetObjectArrayElement(jcontents, i);
        if (role_obj == nullptr || content_obj == nullptr) {
            log_fail("local_infer_fail", "null_message");
            return JNI_FALSE;
        }
        const char *role = env->GetStringUTFChars(role_obj, nullptr);
        const char *content = env->GetStringUTFChars(content_obj, nullptr);
        if (role == nullptr || content == nullptr) {
            if (role != nullptr) {
                env->ReleaseStringUTFChars(role_obj, role);
            }
            if (content != nullptr) {
                env->ReleaseStringUTFChars(content_obj, content);
            }
            log_fail("local_infer_fail", "message_utf");
            return JNI_FALSE;
        }
        role_storage[(size_t) i] = role;
        content_storage[(size_t) i] = content;
        chat[(size_t) i].role = role_storage[(size_t) i].c_str();
        chat[(size_t) i].content = content_storage[(size_t) i].c_str();
        env->ReleaseStringUTFChars(role_obj, role);
        env->ReleaseStringUTFChars(content_obj, content);
        env->DeleteLocalRef(role_obj);
        env->DeleteLocalRef(content_obj);
    }

    std::string formatted(16384, '\0');
    int32_t needed = llama_chat_apply_template(nullptr, chat.data(), (size_t) n_msg, true,
                                               formatted.data(), (int32_t) formatted.size());
    if (needed < 0) {
        log_fail("local_infer_fail", "chat_template");
        return JNI_FALSE;
    }
    if (needed > (int32_t) formatted.size()) {
        formatted.resize((size_t) needed);
        needed = llama_chat_apply_template(nullptr, chat.data(), (size_t) n_msg, true,
                                           formatted.data(), (int32_t) formatted.size());
        if (needed < 0) {
            log_fail("local_infer_fail", "chat_template");
            return JNI_FALSE;
        }
    }
    formatted.resize((size_t) needed);
    __android_log_print(ANDROID_LOG_INFO, LO_TAG, "local_chat_template_ok msgs=%d promptChars=%zu",
                        (int) n_msg, formatted.size());

    return prefill_prompt_text(formatted.c_str()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_org_libreoffice_androidlib_ai_LocalInferenceEngine_nativeSampleToken(JNIEnv *env, jclass) {
    if (g_ctx == nullptr || g_model == nullptr || g_sampler == nullptr) {
        return nullptr;
    }

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    llama_token next = llama_sampler_sample(g_sampler, g_ctx, -1);
    llama_sampler_accept(g_sampler, next);

    if (llama_vocab_is_eog(vocab, next)) {
        return env->NewStringUTF("");
    }

    char piece[512];
    int piece_len = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, true);
    if (piece_len <= 0) {
        return env->NewStringUTF("");
    }

    batch_clear();
    batch_add(next, g_n_past, true);
    const auto decode_t0 = std::chrono::steady_clock::now();
    if (llama_decode(g_ctx, g_batch) != 0) {
        log_fail("local_infer_fail", "gen_decode");
        return env->NewStringUTF("");
    }
    const auto decode_us = std::chrono::duration_cast<std::chrono::microseconds>(
                                   std::chrono::steady_clock::now() - decode_t0)
                                   .count();
    g_gen_decode_us += decode_us;
    g_gen_decode_count++;
    if (g_gen_decode_count % 32 == 0) {
        __android_log_print(ANDROID_LOG_INFO, LO_TAG,
                            "local_decode_perf count=%d avg_ms=%.1f last_ms=%.3f",
                            g_gen_decode_count, (double) g_gen_decode_us / 1000.0 / g_gen_decode_count,
                            decode_us / 1000.0);
    }
    g_n_past++;

    return env->NewStringUTF(std::string(piece, (size_t) piece_len).c_str());
}

} // extern "C"
