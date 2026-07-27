package org.libreoffice.androidlib.ai;

import org.json.JSONArray;

public interface AiBackend {
    int BACKEND_CLOUD = 0;
    int BACKEND_LOCAL = 1;

    void execute(String requestId, JSONArray messages, AiCloudParams cloudParams, boolean multiTurn,
            AiRequestSession session, AiRequestManager.Callback callback);

    boolean isAvailable();
}
