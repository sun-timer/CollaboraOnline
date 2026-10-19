package org.libreoffice.androidlib.ai;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalInferenceEngine {
    private static final String TAG = "LOActivity";

    public interface LoadCallback {
        void onLoaded(boolean success, String message);
    }

    public interface GenerateCallback {
        void onToken(String token);

        void onComplete(String fullText, long ttftMs, float tokensPerSecond);

        void onError(String code, String message);
    }

    private static final LocalInferenceEngine INSTANCE = new LocalInferenceEngine();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile InferenceSession executingSession;
    private volatile LocalInferenceParams loadedParams;
    private volatile String loadedModelPath = "";
    private static volatile boolean nativeLibraryLoaded;

    static {
        try {
            System.loadLibrary("llama_jni");
            nativeLibraryLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            nativeLibraryLoaded = false;
            Log.w(TAG, "local_engine reason=llama_jni_not_loaded " + e.getMessage());
        }
    }

    public static boolean isNativeAvailable() {
        return nativeLibraryLoaded;
    }

    public static LocalInferenceEngine getInstance() {
        return INSTANCE;
    }

    public boolean isModelLoaded() {
        return loadedModelPath != null && !loadedModelPath.isEmpty();
    }

    public void loadModel(Context context, String path, LocalInferenceParams params, LoadCallback callback) {
        final String nativeLibDir =
                context.getApplicationContext().getApplicationInfo().nativeLibraryDir;
        executor.execute(() -> {
            if (!nativeLibraryLoaded) {
                notifyLoadFailed(callback, "local_jni_missing");
                return;
            }
            boolean ok = nativeLoadModel(path, params.contextSize, params.threads, nativeLibDir);
            if (ok) {
                loadedModelPath = path;
                loadedParams = params;
                if (callback != null) {
                    callback.onLoaded(true, "");
                }
            } else if (callback != null) {
                callback.onLoaded(false, "local_load_fail");
            }
        });
    }

    private static void notifyLoadFailed(LoadCallback callback, String reason) {
        Log.e(TAG, "local_infer_fail reason=" + reason);
        if (callback != null) {
            callback.onLoaded(false, reason);
        }
    }

    public void unloadModel() {
        executor.execute(() -> {
            nativeUnloadModel();
            loadedModelPath = "";
            loadedParams = null;
        });
    }

    public void cancel(String requestId) {
        executor.execute(() -> {
            InferenceSession session = executingSession;
            if (session != null && session.requestId.equals(requestId)) {
                session.requestCancel();
                Log.i(TAG, "local_cancel_request requestId=" + requestId);
            }
        });
    }

    public void generate(String requestId, JSONArray messages, boolean multiTurn, GenerateCallback callback) {
        executor.execute(() -> {
            InferenceSession session = new InferenceSession(requestId);
            session.resetCancelled();
            executingSession = session;

            if (!nativeLibraryLoaded) {
                notifyError(callback, "local_jni_missing", "本地推理库未打包，请重新编译 libllama_jni");
                executingSession = null;
                return;
            }

            if (!isModelLoaded()) {
                notifyError(callback, "local_not_loaded", "本地模型未加载");
                executingSession = null;
                return;
            }

            LocalInferenceParams params = loadedParams != null ? loadedParams : LocalInferenceParams.defaults();
            long startMs = System.currentTimeMillis();
            long[] pssBefore = LocalMemoryProbe.samplePssBytes();
            Log.i(TAG, "local_infer_start requestId=" + requestId + " nativePss=" + pssBefore[0]);

            try {
                // KV 保留/清理由 native 自动判断：新输入以旧输入为前缀（doc_qa 多轮）→ 增量，
                // 否则全量。不再无条件清 KV，否则破坏增量复用。
                boolean qwen3Model = loadedModelPath != null
                        && loadedModelPath.toLowerCase(Locale.ROOT).contains("qwen3");
                JSONArray promptMessages = LocalPromptBuilder.buildPrompt(
                        messages, params.contextSize, params.maxTokens, multiTurn);
                String[] roles = new String[promptMessages.length()];
                String[] contents = new String[promptMessages.length()];
                for (int i = 0; i < promptMessages.length(); i++) {
                    JSONObject item = promptMessages.getJSONObject(i);
                    roles[i] = item.optString("role", "user");
                    contents[i] = item.optString("content", "");
                }

                Log.i(TAG, "local_prefill_start requestId=" + requestId
                        + " msgs=" + promptMessages.length());
                long prefillStartMs = System.currentTimeMillis();
                if (!nativePrefillMessages(roles, contents, qwen3Model)) {
                    notifyError(callback, "local_prefill_fail", "本地推理 prefill 失败");
                    return;
                }
                Log.i(TAG, "local_prefill_ok requestId=" + requestId
                        + " ms=" + (System.currentTimeMillis() - prefillStartMs));

                StringBuilder full = new StringBuilder();
                ThinkContentFilter outputFilter = new ThinkContentFilter();
                long firstTokenMs = 0L;
                int tokenCount = 0;

                for (int i = 0; i < params.maxTokens; i++) {
                    if (session.isCancelled()) {
                        Log.i(TAG, "local_infer_cancelled requestId=" + requestId);
                        break;
                    }
                    String piece = nativeSampleToken();
                    if (piece == null) {
                        // null = EOG 或采样失败（native 已打日志），正常结束
                        break;
                    }
                    if (piece.isEmpty()) {
                        // 多字节字符被 token 拆开，本次无完整字符，等下一个 token 补全
                        continue;
                    }
                    if (tokenCount == 0) {
                        firstTokenMs = System.currentTimeMillis() - startMs;
                        Log.i(TAG, "local_first_token requestId=" + requestId + " ttft_ms=" + firstTokenMs);
                    }
                    tokenCount++;
                    emitVisibleText(outputFilter.append(piece), full, callback);
                }

                emitVisibleText(outputFilter.finish(), full, callback);

                long elapsed = Math.max(1L, System.currentTimeMillis() - startMs);
                float tps = tokenCount * 1000f / elapsed;
                long[] pssAfter = LocalMemoryProbe.samplePssBytes();
                Log.i(TAG, "local_infer_done requestId=" + requestId + " ttft_ms=" + firstTokenMs
                        + " tps=" + tps + " tokens=" + tokenCount
                        + " nativePss=" + pssAfter[0] + " totalPss=" + pssAfter[1]);

                if (callback != null) {
                    callback.onComplete(full.toString(), firstTokenMs, tps);
                }
            } catch (JSONException e) {
                notifyError(callback, "local_prompt_fail", e.getMessage());
            } finally {
                executingSession = null;
            }
        });
    }

    private static void notifyError(GenerateCallback callback, String code, String message) {
        Log.e(TAG, "local_infer_fail reason=" + code + " msg=" + message);
        if (callback != null) {
            callback.onError(code, message);
        }
    }

    private static void emitVisibleText(String text, StringBuilder full, GenerateCallback callback) {
        if (text == null || text.isEmpty()) {
            return;
        }
        full.append(text);
        if (callback != null) {
            callback.onToken(text);
        }
    }

    /** Suppresses explicit reasoning blocks before they reach streaming or completion callbacks. */
    private static final class ThinkContentFilter {
        private static final String START_TAG = "<think>";
        private static final String END_TAG = "</think>";

        private final StringBuilder pending = new StringBuilder();
        private boolean insideThink;

        String append(String text) {
            if (text != null && !text.isEmpty()) {
                pending.append(text);
            }
            return drain(false);
        }

        String finish() {
            return drain(true);
        }

        private String drain(boolean endOfInput) {
            StringBuilder visible = new StringBuilder();
            while (true) {
                String marker = insideThink ? END_TAG : START_TAG;
                int markerIndex = indexOfIgnoreCase(pending, marker);
                if (markerIndex >= 0) {
                    if (!insideThink && markerIndex > 0) {
                        visible.append(pending, 0, markerIndex);
                    }
                    pending.delete(0, markerIndex + marker.length());
                    insideThink = !insideThink;
                    continue;
                }

                if (insideThink) {
                    if (endOfInput) {
                        pending.setLength(0);
                    } else {
                        int keep = suffixPrefixLength(pending, END_TAG);
                        if (pending.length() > keep) {
                            pending.delete(0, pending.length() - keep);
                        }
                    }
                    return visible.toString();
                }

                if (endOfInput) {
                    visible.append(pending);
                    pending.setLength(0);
                    return visible.toString();
                }
                int keep = suffixPrefixLength(pending, START_TAG);
                int safeLength = pending.length() - keep;
                if (safeLength > 0) {
                    visible.append(pending, 0, safeLength);
                    pending.delete(0, safeLength);
                }
                return visible.toString();
            }
        }

        private static int indexOfIgnoreCase(StringBuilder text, String needle) {
            return text.toString().toLowerCase(Locale.ROOT).indexOf(needle);
        }

        private static int suffixPrefixLength(StringBuilder text, String marker) {
            String value = text.toString();
            int max = Math.min(value.length(), marker.length() - 1);
            for (int length = max; length > 0; length--) {
                if (value.regionMatches(true, value.length() - length, marker, 0, length)) {
                    return length;
                }
            }
            return 0;
        }
    }

    private static native boolean nativeLoadModel(String path, int contextSize, int threads,
            String nativeLibDir);

    private static native void nativeUnloadModel();

    private static native void nativeClearKvCache();

    private static native boolean nativePrefill(String prompt);

    private static native boolean nativePrefillMessages(String[] roles, String[] contents, boolean disableThinking);

    private static native String nativeSampleToken();
}
