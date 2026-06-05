package org.libreoffice.androidlib.ai;

import java.net.HttpURLConnection;

public class AiRequestSession {
    private volatile boolean cancelled = false;
    private volatile boolean stateSentStreaming = false;
    private HttpURLConnection connection;

    public void bindConnection(HttpURLConnection httpURLConnection) {
        connection = httpURLConnection;
    }

    public void cancel() {
        cancelled = true;
        if (connection != null) {
            connection.disconnect();
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean shouldEmitStreamingState() {
        if (stateSentStreaming) {
            return false;
        }
        stateSentStreaming = true;
        return true;
    }
}
