package org.libreoffice.androidlib.ai;

import org.json.JSONArray;

public final class CloudAiBackend implements AiBackend {
    private final AiRequestManager requestManager;

    public CloudAiBackend(AiRequestManager requestManager) {
        this.requestManager = requestManager;
    }

    @Override
    public void execute(String requestId, JSONArray messages, AiCloudParams cloudParams, boolean multiTurn,
            AiRequestSession session, AiRequestManager.Callback callback) {
        requestManager.execute(requestId, cloudParams.endpoint, cloudParams.apiKey, cloudParams.model,
                messages, session, callback);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
