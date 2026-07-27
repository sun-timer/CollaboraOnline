package org.libreoffice.androidlib.ai;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

public final class LocalLlamaBackend implements AiBackend {
    private static final String TAG = "LOActivity";

    private final Context appContext;
    private final LocalInferenceEngine engine;

    public LocalLlamaBackend(Context context) {
        this.appContext = context.getApplicationContext();
        this.engine = LocalInferenceEngine.getInstance();
    }

    @Override
    public void execute(String requestId, JSONArray messages, AiCloudParams cloudParams, boolean multiTurn,
            AiRequestSession session, AiRequestManager.Callback callback) {
        LocalModelManager manager = LocalModelManager.getInstance(appContext);
        if (!manager.isModelLoadedInEngine() && manager.isInstalled()) {
            LocalInferenceParams params = LocalInferenceParams.fromDevice(appContext);
            engine.loadModel(appContext, manager.getModelPath(), params, (success, message) -> {
                if (success) {
                    manager.markEngineLoaded(true);
                    runGenerate(requestId, messages, multiTurn, session, callback);
                } else {
                    callback.onError(requestId, "local_load_fail", message);
                }
            });
            return;
        }
        runGenerate(requestId, messages, multiTurn, session, callback);
    }

    private void runGenerate(String requestId, JSONArray messages, boolean multiTurn, AiRequestSession session,
            AiRequestManager.Callback callback) {
        engine.generate(requestId, messages, multiTurn, new LocalInferenceEngine.GenerateCallback() {
            private final StringBuilder fullText = new StringBuilder();
            private boolean streamingSent = false;

            @Override
            public void onToken(String token) {
                if (session.isCancelled()) {
                    return;
                }
                try {
                    if (!streamingSent) {
                        callback.onStreamingState(requestId);
                        streamingSent = true;
                    }
                    fullText.append(token);
                    callback.onStreamDelta(requestId, token);
                } catch (JSONException e) {
                    Log.w(TAG, "local_stream_fail requestId=" + requestId, e);
                }
            }

            @Override
            public void onComplete(String full, long ttftMs, float tokensPerSecond) {
                if (session.isCancelled()) {
                    try {
                        callback.onError(requestId, "cancelled", "local_cancelled");
                    } catch (Exception ignored) {
                    }
                    return;
                }
                try {
                    String sanitized = callback.sanitizePayload(requestId, fullText.toString(), "done_payload");
                    callback.onDone(requestId, sanitized);
                } catch (JSONException e) {
                    callback.onError(requestId, "local_done_fail", e.getMessage());
                }
            }

            @Override
            public void onError(String code, String message) {
                callback.onError(requestId, code, message);
            }
        });
    }

    @Override
    public boolean isAvailable() {
        return LocalModelManager.getInstance(appContext).canUseLocalInference();
    }
}
