package org.libreoffice.androidlib.ai;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

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

    static {
        try {
            System.loadLibrary("llama_jni");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "local_engine reason=llama_jni_not_loaded " + e.getMessage());
        }
    }

    public static LocalInferenceEngine getInstance() {
        return INSTANCE;
    }

    public boolean isModelLoaded() {
        return loadedModelPath != null && !loadedModelPath.isEmpty();
    }

    public void loadModel(String path, LocalInferenceParams params, LoadCallback callback) {
        executor.execute(() -> {
            boolean ok = nativeLoadModel(path, params.contextSize, params.threads);
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
                nativeClearKvCache();
                JSONArray promptMessages = LocalPromptBuilder.buildPrompt(
                        messages, params.contextSize, params.maxTokens, multiTurn);
                String prompt = LocalPromptBuilder.formatPrompt(promptMessages);

                if (!nativePrefill(prompt)) {
                    notifyError(callback, "local_prefill_fail", "本地推理 prefill 失败");
                    return;
                }

                StringBuilder full = new StringBuilder();
                long firstTokenMs = 0L;
                int tokenCount = 0;

                for (int i = 0; i < params.maxTokens; i++) {
                    if (session.isCancelled()) {
                        Log.i(TAG, "local_infer_cancelled requestId=" + requestId);
                        break;
                    }
                    String piece = nativeSampleToken();
                    if (piece == null) {
                        notifyError(callback, "local_sample_fail", "本地推理采样失败");
                        return;
                    }
                    if (piece.isEmpty()) {
                        break;
                    }
                    if (tokenCount == 0) {
                        firstTokenMs = System.currentTimeMillis() - startMs;
                    }
                    tokenCount++;
                    full.append(piece);
                    if (callback != null) {
                        callback.onToken(piece);
                    }
                }

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

    private static native boolean nativeLoadModel(String path, int contextSize, int threads);

    private static native void nativeUnloadModel();

    private static native void nativeClearKvCache();

    private static native boolean nativePrefill(String prompt);

    private static native String nativeSampleToken();
}
